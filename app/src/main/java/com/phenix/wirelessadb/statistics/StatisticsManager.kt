package com.phenix.wirelessadb.statistics

import android.content.Context
import android.util.Log
import com.phenix.wirelessadb.PrefsManager
import com.phenix.wirelessadb.model.ConnectionMode
import com.phenix.wirelessadb.model.ConnectionRecord
import com.phenix.wirelessadb.model.ConnectionStatistics

/**
 * Singleton manager for tracking and managing connection statistics.
 *
 * Handles:
 * - Recording new connections
 * - Tracking connection duration
 * - Calculating uptime and reliability metrics
 * - Persisting statistics via PrefsManager
 *
 * Usage:
 * ```
 * // Record a new connection
 * StatisticsManager.recordConnection(context, ConnectionMode.LOCAL_WIFI)
 *
 * // Update uptime when service runs
 * StatisticsManager.updateUptime(context, durationMs)
 *
 * // Get current statistics
 * val stats = StatisticsManager.getStatistics(context)
 * ```
 */
object StatisticsManager {

  private const val TAG = "StatisticsManager"

  // Track active connection start time for duration calculation
  private var activeConnectionStart: Long? = null
  private var activeConnectionMode: ConnectionMode = ConnectionMode.LOCAL_WIFI

  /**
   * Records a new connection event.
   * Increments total connection count and adds to history.
   *
   * @param context Application context
   * @param mode Connection mode used (LOCAL_WIFI, P2P, WARPGATE)
   */
  fun recordConnection(context: Context, mode: ConnectionMode) {
    try {
      val currentStats = PrefsManager.getStatistics(context)
      activeConnectionStart = System.currentTimeMillis()
      activeConnectionMode = mode

      val newRecord = ConnectionRecord(
        timestamp = activeConnectionStart!!,
        duration = 0L, // Will be updated on disconnect
        mode = mode,
        successful = true
      )

      val updatedHistory = (currentStats.connectionHistory + newRecord)
        .takeLast(ConnectionStatistics.MAX_HISTORY_SIZE)

      val updatedStats = currentStats.copy(
        totalConnections = currentStats.totalConnections + 1,
        connectionHistory = updatedHistory,
        lastUpdated = System.currentTimeMillis()
      )

      PrefsManager.setStatistics(context, updatedStats)
      Log.i(TAG, "Recorded connection: mode=$mode, total=${updatedStats.totalConnections}")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to record connection", e)
    }
  }

  /**
   * Updates the duration of the most recent connection.
   * Call this when a connection ends to record its duration.
   *
   * @param context Application context
   * @param successful Whether the connection ended successfully
   */
  fun completeConnection(context: Context, successful: Boolean = true) {
    try {
      val connectionStart = activeConnectionStart ?: run {
        Log.w(TAG, "No active connection to complete")
        return
      }

      val duration = System.currentTimeMillis() - connectionStart
      val currentStats = PrefsManager.getStatistics(context)

      if (currentStats.connectionHistory.isEmpty()) {
        Log.w(TAG, "No connection history to update")
        return
      }

      // Update the most recent connection record
      val updatedHistory = currentStats.connectionHistory.toMutableList()
      val lastIndex = updatedHistory.lastIndex
      updatedHistory[lastIndex] = updatedHistory[lastIndex].copy(
        duration = duration,
        successful = successful
      )

      val updatedStats = currentStats.copy(
        connectionHistory = updatedHistory,
        lastUpdated = System.currentTimeMillis()
      )

      PrefsManager.setStatistics(context, updatedStats)
      activeConnectionStart = null

      Log.i(TAG, "Completed connection: duration=${duration}ms, successful=$successful")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to complete connection", e)
    }
  }

  /**
   * Updates the total uptime by adding the specified duration.
   * Call periodically while service is running to track uptime.
   *
   * @param context Application context
   * @param additionalTimeMs Additional uptime in milliseconds to add
   */
  fun updateUptime(context: Context, additionalTimeMs: Long) {
    try {
      val currentStats = PrefsManager.getStatistics(context)
      val updatedStats = currentStats.copy(
        totalUptime = currentStats.totalUptime + additionalTimeMs,
        lastUpdated = System.currentTimeMillis()
      )

      PrefsManager.setStatistics(context, updatedStats)
      Log.d(TAG, "Updated uptime: +${additionalTimeMs}ms, total=${updatedStats.totalUptime}ms")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to update uptime", e)
    }
  }

  /**
   * Calculates and updates the reliability score based on current statistics.
   *
   * Score calculation (0-100):
   * - Base score: 50
   * - Uptime bonus: +30 points (scaled by uptime ratio)
   * - Success rate bonus: +20 points (based on successful connections)
   *
   * @param context Application context
   * @return Updated reliability score (0.0 - 100.0)
   */
  fun calculateReliabilityScore(context: Context): Float {
    try {
      val stats = PrefsManager.getStatistics(context)

      // Base score
      var score = 50f

      // Uptime bonus (up to 30 points)
      // Assumes device should be running 24/7 for ideal score
      val daysSinceFirstUse = (System.currentTimeMillis() - getFirstUseTimestamp(context)) / (24 * 60 * 60 * 1000f)
      val expectedUptime = daysSinceFirstUse * 24 * 60 * 60 * 1000 // ms
      if (expectedUptime > 0) {
        val uptimeRatio = (stats.totalUptime.toFloat() / expectedUptime).coerceIn(0f, 1f)
        score += uptimeRatio * 30f
      }

      // Success rate bonus (up to 20 points)
      if (stats.connectionHistory.isNotEmpty()) {
        val successfulConnections = stats.connectionHistory.count { it.successful }
        val successRate = successfulConnections.toFloat() / stats.connectionHistory.size
        score += successRate * 20f
      }

      val finalScore = score.coerceIn(0f, 100f)

      // Update statistics with new score
      val updatedStats = stats.copy(
        reliabilityScore = finalScore,
        lastUpdated = System.currentTimeMillis()
      )
      PrefsManager.setStatistics(context, updatedStats)

      Log.d(TAG, "Calculated reliability score: $finalScore")
      return finalScore
    } catch (e: Exception) {
      Log.e(TAG, "Failed to calculate reliability score", e)
      return 0f
    }
  }

  /**
   * Gets the current connection statistics.
   *
   * @param context Application context
   * @return Current ConnectionStatistics
   */
  fun getStatistics(context: Context): ConnectionStatistics {
    return PrefsManager.getStatistics(context)
  }

  /**
   * Resets all statistics to default values.
   * Useful for testing or user-requested reset.
   *
   * @param context Application context
   */
  fun resetStatistics(context: Context) {
    try {
      PrefsManager.setStatistics(context, ConnectionStatistics.DEFAULT)
      activeConnectionStart = null
      Log.i(TAG, "Statistics reset to default")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to reset statistics", e)
    }
  }

  /**
   * Gets the timestamp of first use.
   * Uses the lastUpdated field from saved statistics, or current time if none exist.
   *
   * @param context Application context
   * @return Timestamp in milliseconds
   */
  private fun getFirstUseTimestamp(context: Context): Long {
    val stats = PrefsManager.getStatistics(context)

    // If we have connection history, use the oldest record
    if (stats.connectionHistory.isNotEmpty()) {
      return stats.connectionHistory.first().timestamp
    }

    // If we have uptime but no history, estimate from lastUpdated
    if (stats.totalUptime > 0) {
      return stats.lastUpdated - stats.totalUptime
    }

    // No data yet, return current time
    return System.currentTimeMillis()
  }

  /**
   * Formats uptime duration into human-readable string.
   *
   * @param uptimeMs Uptime in milliseconds
   * @return Formatted string (e.g., "2d 5h 30m")
   */
  fun formatUptime(uptimeMs: Long): String {
    val seconds = uptimeMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
      days > 0 -> "${days}d ${hours % 24}h ${minutes % 60}m"
      hours > 0 -> "${hours}h ${minutes % 60}m"
      minutes > 0 -> "${minutes}m ${seconds % 60}s"
      else -> "${seconds}s"
    }
  }
}
