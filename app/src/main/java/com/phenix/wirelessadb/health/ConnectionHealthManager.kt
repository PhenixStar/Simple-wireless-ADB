package com.phenix.wirelessadb.health

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.phenix.wirelessadb.AdbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * ConnectionHealthManager coordinates health check scheduling and state management.
 *
 * This singleton manages periodic ADB health verification using WorkManager.
 * It schedules HealthCheckWorker to run at configurable intervals and maintains
 * the current health state for UI observation.
 *
 * Design:
 * - Singleton pattern (object) following AdbManager/PrefsManager conventions
 * - Uses WorkManager for battery-efficient periodic checks
 * - Exposes LiveData for UI observation
 * - Maintains last known ADB state to detect degradation
 *
 * @see HealthCheckWorker for health check implementation
 * @see AdbManager for ADB status checking
 */
object ConnectionHealthManager {

  private const val TAG = "ConnectionHealthManager"
  private const val WORK_NAME = "health_check_worker"
  private const val DEFAULT_CHECK_INTERVAL_SECONDS = 30L

  /**
   * Health state values for UI display and notification logic.
   */
  enum class HealthState {
    /** ADB is functioning normally */
    HEALTHY,
    /** ADB connection has degraded (IP/port changed) */
    DEGRADED,
    /** ADB is disabled or unreachable */
    FAILED,
    /** Health monitoring is not active */
    UNKNOWN
  }

  private val _healthState = MutableLiveData<HealthState>(HealthState.UNKNOWN)
  val healthState: LiveData<HealthState> = _healthState

  private var lastKnownEnabled = false
  private var lastKnownPort = 0
  private var lastKnownIp: String? = null

  /**
   * Start health monitoring with the specified check interval.
   *
   * Schedules a PeriodicWorkRequest that runs HealthCheckWorker at the specified
   * interval. If monitoring is already active, this will update the interval.
   *
   * @param context Application context
   * @param intervalSeconds Time between health checks (default: 30 seconds, minimum: 15)
   */
  suspend fun startMonitoring(
    context: Context,
    intervalSeconds: Long = DEFAULT_CHECK_INTERVAL_SECONDS
  ) = withContext(Dispatchers.IO) {
    try {
      Log.d(TAG, "Starting health monitoring with interval: ${intervalSeconds}s")

      // Update last known state from current ADB status
      val status = AdbManager.getStatus(context)
      lastKnownEnabled = status.enabled
      lastKnownPort = status.port
      lastKnownIp = status.ip
      Log.d(TAG, "Initial state: enabled=$lastKnownEnabled, port=$lastKnownPort, ip=$lastKnownIp")

      // Enforce minimum interval (WorkManager constraint: 15 minutes)
      val minIntervalMinutes = 15L
      val actualInterval = maxOf(intervalSeconds, minIntervalMinutes * 60)
      if (actualInterval != intervalSeconds) {
        Log.w(TAG, "Requested interval ${intervalSeconds}s below minimum, using ${actualInterval}s")
      }

      // Build work request with constraints
      val workRequest = PeriodicWorkRequestBuilder<HealthCheckWorker>(
        actualInterval,
        TimeUnit.SECONDS
      )
        .setConstraints(
          Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // Run even without network
            .setRequiresBatteryNotLow(false) // Run even on low battery (important for monitoring)
            .build()
        )
        .setBackoffCriteria(
          BackoffPolicy.LINEAR,
          15L * 60L * 1000L, // 15 minutes in milliseconds
          TimeUnit.MILLISECONDS
        )
        .setInputData(
          workDataOf(
            HealthCheckWorker.KEY_LAST_KNOWN_ENABLED to lastKnownEnabled,
            HealthCheckWorker.KEY_LAST_KNOWN_PORT to lastKnownPort,
            HealthCheckWorker.KEY_LAST_KNOWN_IP to (lastKnownIp ?: "")
          )
        )
        .addTag(WORK_NAME)
        .build()

      // Schedule work (replace existing to update interval)
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK_NAME,
        ExistingPeriodicWorkPolicy.UPDATE,
        workRequest
      )

      // Set initial health state
      _healthState.postValue(if (status.enabled) HealthState.HEALTHY else HealthState.FAILED)

      // Observe work info to update health state
      observeWorkInfo(context)

      Log.d(TAG, "Health monitoring started successfully")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start health monitoring: ${e.message}", e)
      _healthState.postValue(HealthState.UNKNOWN)
    }
  }

  /**
   * Stop health monitoring.
   *
   * Cancels the scheduled PeriodicWorkRequest and resets health state to UNKNOWN.
   *
   * @param context Application context
   */
  suspend fun stopMonitoring(context: Context) = withContext(Dispatchers.IO) {
    try {
      Log.d(TAG, "Stopping health monitoring")
      WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
      _healthState.postValue(HealthState.UNKNOWN)
      Log.d(TAG, "Health monitoring stopped")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to stop health monitoring: ${e.message}", e)
    }
  }

  /**
   * Check if health monitoring is currently active.
   *
   * @param context Application context
   * @return true if monitoring is scheduled, false otherwise
   */
  suspend fun isMonitoring(context: Context): Boolean = withContext(Dispatchers.IO) {
    try {
      val workInfos = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(WORK_NAME)
        .get()
      val isRunning = workInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
      Log.d(TAG, "isMonitoring: $isRunning")
      isRunning
    } catch (e: Exception) {
      Log.e(TAG, "Failed to check monitoring status: ${e.message}", e)
      false
    }
  }

  /**
   * Update the last known ADB state.
   *
   * Should be called when ADB state changes intentionally (e.g., user enables/disables)
   * to prevent false degradation alerts.
   *
   * @param enabled Whether ADB is enabled
   * @param port ADB port
   * @param ip Device IP address
   */
  fun updateLastKnownState(enabled: Boolean, port: Int, ip: String?) {
    Log.d(TAG, "Updating last known state: enabled=$enabled, port=$port, ip=$ip")
    lastKnownEnabled = enabled
    lastKnownPort = port
    lastKnownIp = ip
  }

  /**
   * Observe WorkInfo updates to sync health state with worker results.
   */
  private fun observeWorkInfo(context: Context) {
    try {
      WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkLiveData(WORK_NAME)
        .observeForever { workInfos ->
          if (workInfos.isNullOrEmpty()) return@observeForever

          val workInfo = workInfos.firstOrNull() ?: return@observeForever
          if (workInfo.state != WorkInfo.State.SUCCEEDED) return@observeForever

          // Extract health status from output data
          val healthStatus = workInfo.outputData.getString(HealthCheckWorker.KEY_HEALTH_STATUS)
          val degradationDetected = workInfo.outputData.getBoolean(
            HealthCheckWorker.KEY_DEGRADATION_DETECTED,
            false
          )

          // Update state
          val newState = when (healthStatus) {
            HealthCheckWorker.HEALTH_HEALTHY -> HealthState.HEALTHY
            HealthCheckWorker.HEALTH_DEGRADED -> HealthState.DEGRADED
            HealthCheckWorker.HEALTH_FAILED -> HealthState.FAILED
            else -> HealthState.UNKNOWN
          }

          Log.d(TAG, "Health state updated: $newState (degradation=$degradationDetected)")
          _healthState.postValue(newState)

          // Update last known state if healthy
          if (newState == HealthState.HEALTHY) {
            val currentEnabled = workInfo.outputData.getBoolean(HealthCheckWorker.KEY_CURRENT_ENABLED, false)
            val currentPort = workInfo.outputData.getInt(HealthCheckWorker.KEY_CURRENT_PORT, 0)
            val currentIp = workInfo.outputData.getString(HealthCheckWorker.KEY_CURRENT_IP)
            updateLastKnownState(currentEnabled, currentPort, currentIp)
          }
        }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to observe work info: ${e.message}", e)
    }
  }

  /**
   * Get the current health state value directly (non-LiveData).
   *
   * @return Current HealthState
   */
  fun getCurrentHealthState(): HealthState {
    return _healthState.value ?: HealthState.UNKNOWN
  }
}
