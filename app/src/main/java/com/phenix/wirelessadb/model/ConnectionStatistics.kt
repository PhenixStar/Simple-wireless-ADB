package com.phenix.wirelessadb.model

/**
 * Represents connection statistics and metrics for the ADB service.
 * Tracks device connectivity patterns, uptime, and reliability metrics.
 */
data class ConnectionStatistics(
  /**
   * Total uptime in milliseconds since first install.
   * Incremented whenever the ADB service is running.
   */
  val totalUptime: Long = 0L,

  /**
   * Total number of successful connections made to this device.
   * Incremented each time ADB service starts.
   */
  val totalConnections: Int = 0,

  /**
   * History of recent connections with timestamps and duration.
   * Limited to last 50 connections to prevent unbounded growth.
   */
  val connectionHistory: List<ConnectionRecord> = emptyList(),

  /**
   * Reliability score from 0.0 to 100.0.
   * Calculated based on uptime vs. expected uptime, connection success rate,
   * and service availability.
   */
  val reliabilityScore: Float = 0f,

  /**
   * Timestamp (milliseconds since epoch) of last statistics update.
   * Used to calculate time-based metrics.
   */
  val lastUpdated: Long = System.currentTimeMillis()
) {
  companion object {
    /**
     * Default/initial state with no statistics.
     */
    val DEFAULT = ConnectionStatistics()

    /**
     * Maximum number of connection records to keep in history.
     * Prevents unbounded growth of stored data.
     */
    const val MAX_HISTORY_SIZE = 50
  }
}

/**
 * Represents a single connection record in the history.
 */
data class ConnectionRecord(
  /**
   * Timestamp (milliseconds since epoch) when connection started.
   */
  val timestamp: Long,

  /**
   * Duration of the connection in milliseconds.
   * 0 if connection is still active.
   */
  val duration: Long = 0L,

  /**
   * Connection mode used for this connection.
   */
  val mode: ConnectionMode = ConnectionMode.LOCAL_WIFI,

  /**
   * Whether the connection was successful.
   * False if connection failed or was interrupted.
   */
  val successful: Boolean = true
)
