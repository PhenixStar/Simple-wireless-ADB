package com.phenix.wirelessadb.p2p

import com.phenix.wirelessadb.model.NatType

/**
 * A trusted P2P device that can auto-reconnect.
 *
 * When a user marks a device as "trusted", we save its connection
 * info for future auto-reconnection. This enables:
 * - One-time pairing (no token needed after first connect)
 * - Auto-reconnect on boot/network change
 * - Persistent device list in UI
 */
data class TrustedP2PDevice(
  /** Unique device identifier (hardware hash) */
  val deviceHash: String,

  /** Human-readable device name */
  val displayName: String,

  /** Last known local IP (for same-network reconnect) */
  val lastKnownLocalIp: String? = null,

  /** Last known local port */
  val lastKnownLocalPort: Int = 5555,

  /** Last known external IP (for cross-network reconnect) */
  val lastKnownExternalIp: String? = null,

  /** Last known external port */
  val lastKnownExternalPort: Int = 0,

  /** Persistent token for reconnection (never expires) */
  val persistentToken: String,

  /** NAT type last detected */
  val natType: NatType = NatType.UNKNOWN,

  /** When device was first trusted */
  val trustedAt: Long = System.currentTimeMillis(),

  /** Last successful connection */
  val lastConnected: Long = System.currentTimeMillis(),

  /** Last connection attempt (for retry backoff) */
  val lastAttempt: Long = 0,

  /** Number of failed connection attempts */
  val failedAttempts: Int = 0,

  /** Whether to auto-reconnect on boot/network change */
  val autoReconnect: Boolean = true,

  /** Whether device is currently reachable */
  val isOnline: Boolean = false,

  /** Android version of the device */
  val androidVersion: String? = null,

  /** RootADB app version on the device */
  val appVersion: String? = null,

  /** User-added notes about this device */
  val notes: String? = null
) {
  /** Unique identifier for this trusted device */
  val id: String get() = deviceHash

  /** Short display ID (last 4 chars of hash) */
  val shortId: String get() = deviceHash.takeLast(4).uppercase()

  /** Last known local endpoint */
  val localEndpoint: String? get() = lastKnownLocalIp?.let { "$it:$lastKnownLocalPort" }

  /** Last known external endpoint */
  val externalEndpoint: String? get() = lastKnownExternalIp?.let { "$it:$lastKnownExternalPort" }

  /** ADB connect command for local connection */
  val localConnectCommand: String? get() = localEndpoint?.let { "adb connect $it" }

  /** ADB connect command for external connection */
  val externalConnectCommand: String? get() = externalEndpoint?.let { "adb connect $it" }

  /** Human-readable time since last connection */
  fun getLastConnectedAgo(): String {
    val diff = System.currentTimeMillis() - lastConnected
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
      days > 0 -> "${days}d ago"
      hours > 0 -> "${hours}h ago"
      minutes > 0 -> "${minutes}m ago"
      else -> "Just now"
    }
  }

  /** Check if we should attempt reconnection (exponential backoff) */
  fun shouldAttemptReconnect(): Boolean {
    if (!autoReconnect) return false
    if (failedAttempts == 0) return true

    // Exponential backoff: 1min, 2min, 4min, 8min... max 1 hour
    val backoffMinutes = minOf(1 shl (failedAttempts - 1), 60)
    val backoffMs = backoffMinutes * 60 * 1000L
    return System.currentTimeMillis() - lastAttempt > backoffMs
  }

  /** Update with new connection info */
  fun withConnectionInfo(
    localIp: String?,
    localPort: Int,
    externalIp: String?,
    externalPort: Int,
    natType: NatType
  ): TrustedP2PDevice = copy(
    lastKnownLocalIp = localIp ?: lastKnownLocalIp,
    lastKnownLocalPort = localPort,
    lastKnownExternalIp = externalIp ?: lastKnownExternalIp,
    lastKnownExternalPort = externalPort,
    natType = natType,
    lastConnected = System.currentTimeMillis(),
    failedAttempts = 0,
    isOnline = true
  )

  /** Mark as connection attempt */
  fun withAttempt(success: Boolean): TrustedP2PDevice = copy(
    lastAttempt = System.currentTimeMillis(),
    failedAttempts = if (success) 0 else failedAttempts + 1,
    lastConnected = if (success) System.currentTimeMillis() else lastConnected,
    isOnline = success
  )

  /** Mark as offline */
  fun asOffline(): TrustedP2PDevice = copy(isOnline = false)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TrustedP2PDevice) return false
    return deviceHash == other.deviceHash
  }

  override fun hashCode(): Int = deviceHash.hashCode()
}
