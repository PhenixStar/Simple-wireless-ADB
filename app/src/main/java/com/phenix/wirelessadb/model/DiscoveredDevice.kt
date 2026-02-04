package com.phenix.wirelessadb.model

/**
 * A device discovered via mDNS/NSD on the local network.
 *
 * When RootADB Pro is running with ADB enabled, it broadcasts
 * its presence via mDNS (_rootadb._tcp). Other devices can
 * discover and connect directly without NAT traversal.
 */
data class DiscoveredDevice(
  /** Unique device identifier (hardware hash) */
  val deviceHash: String,

  /** Human-readable device name (e.g., "Pixel 7 Pro") */
  val displayName: String,

  /** Local IP address on the network */
  val ipAddress: String,

  /** ADB port (usually 5555) */
  val port: Int,

  /** When this device was discovered */
  val discoveredAt: Long = System.currentTimeMillis(),

  /** Whether ADB is currently enabled on this device */
  val adbEnabled: Boolean = true,

  /** Android version (if available) */
  val androidVersion: String? = null,

  /** App version (if available) */
  val appVersion: String? = null
) {
  /** Full endpoint for ADB connection */
  val endpoint: String get() = "$ipAddress:$port"

  /** ADB connect command */
  val connectCommand: String get() = "adb connect $endpoint"

  /** Check if discovery is stale (older than 60 seconds) */
  fun isStale(maxAgeMs: Long = 60_000): Boolean {
    return System.currentTimeMillis() - discoveredAt > maxAgeMs
  }

  /** Short identifier for display */
  val shortId: String get() = deviceHash.takeLast(4).uppercase()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DiscoveredDevice) return false
    return deviceHash == other.deviceHash
  }

  override fun hashCode(): Int = deviceHash.hashCode()
}

/**
 * State of the discovery service.
 */
enum class DiscoveryState {
  /** Not started */
  IDLE,

  /** Actively discovering */
  DISCOVERING,

  /** Registration/discovery error */
  ERROR,

  /** Stopped */
  STOPPED
}
