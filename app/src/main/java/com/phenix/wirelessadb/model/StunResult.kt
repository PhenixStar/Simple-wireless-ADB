package com.phenix.wirelessadb.model

/**
 * Result from STUN query containing external endpoint info.
 *
 * When a device is behind NAT, its local IP (e.g., 192.168.1.100:5555)
 * isn't reachable from the internet. STUN discovers the external
 * IP:port that the NAT assigns for outgoing connections.
 */
data class StunResult(
  /** External IP address as seen by STUN server */
  val externalIp: String,

  /** External port mapped by NAT */
  val externalPort: Int,

  /** Local IP used for the STUN query */
  val localIp: String,

  /** Local port used for the STUN query */
  val localPort: Int,

  /** STUN server that provided this result */
  val stunServer: String,

  /** NAT type detected (if determinable) */
  val natType: NatType = NatType.UNKNOWN,

  /** Query latency in milliseconds */
  val latencyMs: Long = 0
) {
  /** External endpoint in IP:port format */
  val externalEndpoint: String get() = "$externalIp:$externalPort"

  /** Local endpoint in IP:port format */
  val localEndpoint: String get() = "$localIp:$localPort"

  /** Check if device has public IP (no NAT) */
  val isPublicIp: Boolean get() = externalIp == localIp && externalPort == localPort

  override fun toString(): String {
    return "StunResult(external=$externalEndpoint, local=$localEndpoint, nat=$natType)"
  }
}

/**
 * NAT type classification for connection strategy selection.
 *
 * Different NAT types have different hole-punching success rates:
 * - Full Cone: ~100% success (any external host can use mapped port)
 * - Restricted: ~90% success (must first send to that host)
 * - Port Restricted: ~70% success (must first send to that host:port)
 * - Symmetric: ~20% success (different port for each destination)
 */
enum class NatType {
  /** No NAT - device has public IP */
  NONE,

  /** Full Cone NAT - easiest to traverse */
  FULL_CONE,

  /** Restricted Cone NAT - needs outbound packet first */
  RESTRICTED,

  /** Port Restricted Cone NAT - needs outbound to specific port */
  PORT_RESTRICTED,

  /** Symmetric NAT - hardest to traverse, may need TURN relay */
  SYMMETRIC,

  /** Could not determine NAT type */
  UNKNOWN
}

/**
 * Error during STUN operation.
 */
sealed class StunError : Exception() {
  /** Network unreachable or socket error */
  data class NetworkError(override val message: String) : StunError()

  /** STUN server didn't respond in time */
  data class Timeout(val serverAddress: String) : StunError() {
    override val message: String = "STUN server $serverAddress timed out"
  }

  /** Invalid STUN response */
  data class InvalidResponse(override val message: String) : StunError()

  /** All STUN servers failed */
  object AllServersFailed : StunError() {
    override val message: String = "All STUN servers failed"
  }
}
