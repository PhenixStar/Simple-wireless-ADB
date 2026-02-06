package com.phenix.wirelessadb.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.phenix.wirelessadb.model.ConnectionRecord
import com.phenix.wirelessadb.model.ConnectionStatistics
import com.phenix.wirelessadb.statistics.StatisticsManager
import kotlinx.coroutines.launch

/**
 * ViewModel for the Statistics Dashboard.
 * Manages connection statistics data and exposes it to the UI.
 *
 * Provides:
 * - Total uptime (formatted)
 * - Total connection count
 * - Reliability score
 * - Connection history
 * - Statistics refresh and reset capabilities
 */
class StatisticsViewModel(application: Application) : AndroidViewModel(application) {

  private val context = application.applicationContext

  // Statistics data
  private val _statistics = MutableLiveData<ConnectionStatistics>()
  val statistics: LiveData<ConnectionStatistics> = _statistics

  // Individual statistics fields for easy UI binding
  private val _totalUptime = MutableLiveData<String>()
  val totalUptime: LiveData<String> = _totalUptime

  private val _totalConnections = MutableLiveData<Int>()
  val totalConnections: LiveData<Int> = _totalConnections

  private val _reliabilityScore = MutableLiveData<Float>()
  val reliabilityScore: LiveData<Float> = _reliabilityScore

  private val _connectionHistory = MutableLiveData<List<ConnectionRecord>>()
  val connectionHistory: LiveData<List<ConnectionRecord>> = _connectionHistory

  // Loading state
  private val _isLoading = MutableLiveData(false)
  val isLoading: LiveData<Boolean> = _isLoading

  // Error messages
  private val _error = MutableLiveData<String?>()
  val error: LiveData<String?> = _error

  init {
    loadStatistics()
  }

  /**
   * Loads current statistics from StatisticsManager.
   * Updates all LiveData fields.
   */
  fun loadStatistics() {
    viewModelScope.launch {
      try {
        _isLoading.value = true
        _error.value = null

        val stats = StatisticsManager.getStatistics(context)
        _statistics.value = stats
        _totalUptime.value = StatisticsManager.formatUptime(stats.totalUptime)
        _totalConnections.value = stats.totalConnections
        _reliabilityScore.value = stats.reliabilityScore
        _connectionHistory.value = stats.connectionHistory

      } catch (e: Exception) {
        _error.value = "Failed to load statistics: ${e.message}"
      } finally {
        _isLoading.value = false
      }
    }
  }

  /**
   * Refreshes statistics and recalculates reliability score.
   */
  fun refreshStatistics() {
    viewModelScope.launch {
      try {
        _isLoading.value = true
        _error.value = null

        // Recalculate reliability score
        StatisticsManager.calculateReliabilityScore(context)

        // Reload all statistics
        loadStatistics()

      } catch (e: Exception) {
        _error.value = "Failed to refresh statistics: ${e.message}"
        _isLoading.value = false
      }
    }
  }

  /**
   * Resets all statistics to default values.
   * Requires user confirmation in UI.
   */
  fun resetStatistics() {
    viewModelScope.launch {
      try {
        _isLoading.value = true
        _error.value = null

        StatisticsManager.resetStatistics(context)
        loadStatistics()

      } catch (e: Exception) {
        _error.value = "Failed to reset statistics: ${e.message}"
        _isLoading.value = false
      }
    }
  }

  /**
   * Formats a connection record's timestamp to a human-readable date/time.
   *
   * @param timestamp Timestamp in milliseconds since epoch
   * @return Formatted date/time string
   */
  fun formatTimestamp(timestamp: Long): String {
    return java.text.SimpleDateFormat(
      "MMM dd, yyyy HH:mm",
      java.util.Locale.getDefault()
    ).format(java.util.Date(timestamp))
  }

  /**
   * Formats a duration in milliseconds to a human-readable string.
   *
   * @param durationMs Duration in milliseconds
   * @return Formatted duration string (e.g., "2h 30m")
   */
  fun formatDuration(durationMs: Long): String {
    return StatisticsManager.formatUptime(durationMs)
  }

  /**
   * Gets a color resource ID based on reliability score.
   * Used for visual feedback in UI.
   *
   * @param score Reliability score (0.0 - 100.0)
   * @return Color resource ID
   */
  fun getReliabilityColor(score: Float): Int {
    return when {
      score >= 80f -> android.R.color.holo_green_dark
      score >= 50f -> android.R.color.holo_orange_dark
      else -> android.R.color.holo_red_dark
    }
  }

  /**
   * Clears the current error message.
   */
  fun clearError() {
    _error.value = null
  }
}
