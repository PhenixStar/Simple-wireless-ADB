package com.phenix.wirelessadb.health

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.phenix.wirelessadb.AdbManager
import com.phenix.wirelessadb.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * HealthCheckWorker verifies ADB connection status periodically.
 *
 * This worker runs every 30 seconds (configured by ConnectionHealthManager)
 * and checks if ADB is still accessible and functional. It detects connection
 * degradation and reports health status changes.
 *
 * Design:
 * - Runs as a PeriodicWorkRequest scheduled by WorkManager
 * - Calls AdbManager.getStatus() to verify current ADB state
 * - Detects connection issues (enabled -> disabled, IP changes)
 * - Battery efficient (uses WorkManager's optimized scheduling)
 *
 * @see ConnectionHealthManager for scheduling logic
 * @see AdbManager.getStatus for ADB status checking
 */
class HealthCheckWorker(
  context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {

  companion object {
    private const val TAG = "HealthCheckWorker"

    // Input data keys
    const val KEY_LAST_KNOWN_ENABLED = "last_known_enabled"
    const val KEY_LAST_KNOWN_PORT = "last_known_port"
    const val KEY_LAST_KNOWN_IP = "last_known_ip"

    // Output data keys
    const val KEY_CURRENT_ENABLED = "current_enabled"
    const val KEY_CURRENT_PORT = "current_port"
    const val KEY_CURRENT_IP = "current_ip"
    const val KEY_HEALTH_STATUS = "health_status"
    const val KEY_DEGRADATION_DETECTED = "degradation_detected"

    // Health status values
    const val HEALTH_HEALTHY = "healthy"
    const val HEALTH_DEGRADED = "degraded"
    const val HEALTH_FAILED = "failed"
  }

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    try {
      Log.d(TAG, "Starting ADB health check")

      // Get previous state from input data
      val lastKnownEnabled = inputData.getBoolean(KEY_LAST_KNOWN_ENABLED, false)
      val lastKnownPort = inputData.getInt(KEY_LAST_KNOWN_PORT, 0)
      val lastKnownIp = inputData.getString(KEY_LAST_KNOWN_IP)

      Log.d(TAG, "Last known state: enabled=$lastKnownEnabled, port=$lastKnownPort, ip=$lastKnownIp")

      // Check current ADB status
      val status = AdbManager.getStatus(applicationContext)
      Log.d(TAG, "Current status: enabled=${status.enabled}, port=${status.port}, ip=${status.ip}")

      // Detect connection degradation
      var degradationDetected = detectDegradation(
        lastKnownEnabled = lastKnownEnabled,
        lastKnownPort = lastKnownPort,
        lastKnownIp = lastKnownIp,
        currentEnabled = status.enabled,
        currentPort = status.port,
        currentIp = status.ip
      )

      // Auto-reconnect: if ADB was enabled but dropped, try to re-enable
      var currentEnabled = status.enabled
      var currentPort = status.port
      var currentIp = status.ip
      if (lastKnownEnabled && !status.enabled && PrefsManager.isAutoReconnectEnabled(applicationContext)) {
        val reconnectPort = if (lastKnownPort > 0) lastKnownPort else PrefsManager.getPort(applicationContext)
        Log.i(TAG, "Auto-reconnect: ADB dropped, re-enabling on port $reconnectPort")
        val result = AdbManager.enable(reconnectPort)
        if (result.isSuccess) {
          // Recheck status after re-enable
          val recheckStatus = AdbManager.getStatus(applicationContext)
          currentEnabled = recheckStatus.enabled
          currentPort = recheckStatus.port
          currentIp = recheckStatus.ip
          degradationDetected = !recheckStatus.enabled
          Log.i(TAG, "Auto-reconnect: success=$currentEnabled, port=$currentPort")
        } else {
          Log.w(TAG, "Auto-reconnect: failed - ${result.exceptionOrNull()?.message}")
        }
      }

      // Determine health status
      val healthStatus = when {
        !currentEnabled -> HEALTH_FAILED
        degradationDetected -> HEALTH_DEGRADED
        else -> HEALTH_HEALTHY
      }

      Log.d(TAG, "Health check complete: status=$healthStatus, degradation=$degradationDetected")

      // Return success with output data
      Result.success(
        androidx.work.workDataOf(
          KEY_CURRENT_ENABLED to currentEnabled,
          KEY_CURRENT_PORT to currentPort,
          KEY_CURRENT_IP to (currentIp ?: ""),
          KEY_HEALTH_STATUS to healthStatus,
          KEY_DEGRADATION_DETECTED to degradationDetected
        )
      )
    } catch (e: Exception) {
      Log.e(TAG, "Health check failed: ${e.message}", e)
      Result.failure()
    }
  }

  /**
   * Detects if the ADB connection has degraded since the last check.
   *
   * Degradation scenarios:
   * - ADB was enabled but is now disabled
   * - Port changed unexpectedly
   * - IP address changed (device switched networks)
   *
   * @return true if degradation detected, false otherwise
   */
  private fun detectDegradation(
    lastKnownEnabled: Boolean,
    lastKnownPort: Int,
    lastKnownIp: String?,
    currentEnabled: Boolean,
    currentPort: Int,
    currentIp: String?
  ): Boolean {
    // If we've never seen ADB enabled, no degradation
    if (!lastKnownEnabled) {
      return false
    }

    // ADB was enabled but is now disabled
    if (lastKnownEnabled && !currentEnabled) {
      Log.w(TAG, "Degradation: ADB was enabled but is now disabled")
      return true
    }

    // Port changed unexpectedly
    if (lastKnownPort > 0 && lastKnownPort != currentPort) {
      Log.w(TAG, "Degradation: Port changed from $lastKnownPort to $currentPort")
      return true
    }

    // IP changed (device switched networks or lost WiFi)
    if (!lastKnownIp.isNullOrEmpty() && lastKnownIp != currentIp) {
      Log.w(TAG, "Degradation: IP changed from $lastKnownIp to $currentIp")
      return true
    }

    return false
  }
}
