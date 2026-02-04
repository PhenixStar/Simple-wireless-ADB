package com.phenix.wirelessadb.p2p

import android.util.Log
import com.phenix.wirelessadb.model.NatType
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * Encodes P2P connection info into compact, shareable tokens.
 *
 * Token format is designed to be:
 * - Compact: Fits in QR code and easy to type
 * - Human-readable: Base32 with ABC-123-XYZ grouping
 * - Self-verifying: CRC32 checksum prevents typos
 * - Versioned: Future format changes supported
 *
 * Token structure (binary, before encoding):
 * ```
 * Version (1 byte) | Flags (1 byte) | External IP (4 bytes) |
 * External Port (2 bytes) | Local IP (4 bytes) | Local Port (2 bytes) |
 * NAT Type (1 byte) | Session ID (8 bytes) | Device Hash (4 bytes) |
 * Expiry (4 bytes) | CRC32 (4 bytes)
 * ```
 *
 * Total: 35 bytes -> ~56 Base32 chars -> grouped as XXX-XXX-XXX-...-XXX
 *
 * Usage:
 * ```
 * val encoder = TokenEncoder()
 * val token = encoder.encode(connectionInfo)
 * // Share token with peer
 *
 * // Peer decodes
 * val info = encoder.decode(token)
 * ```
 */
class TokenEncoder {

  companion object {
    private const val TAG = "TokenEncoder"

    /** Current token version */
    private const val TOKEN_VERSION: Byte = 1

    /** Token flags */
    private const val FLAG_HAS_LOCAL_IP: Byte = 0x01
    private const val FLAG_PERSISTENT: Byte = 0x02
    private const val FLAG_ENCRYPTED: Byte = 0x04

    /** Default token validity (30 minutes) */
    private const val DEFAULT_EXPIRY_MINUTES = 30

    /** Persistent token validity (30 days) */
    private const val PERSISTENT_EXPIRY_DAYS = 30

    /** Base32 alphabet (RFC 4648 without padding, no 0/O 1/I confusion) */
    private const val BASE32_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Group size for human-readable display */
    private const val GROUP_SIZE = 3
  }

  /**
   * Encoded token with metadata.
   */
  data class EncodedToken(
    val token: String,           // Formatted token string
    val rawBytes: ByteArray,     // Raw binary data
    val expiresAt: Long,         // Expiry timestamp
    val isPersistent: Boolean    // Whether token never expires
  ) {
    /** Check if token is expired */
    fun isExpired(): Boolean = !isPersistent && System.currentTimeMillis() > expiresAt

    /** Token for display (grouped) */
    val displayToken: String get() = token

    /** Token for QR code (no dashes) */
    val qrToken: String get() = token.replace("-", "")

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is EncodedToken) return false
      return token == other.token
    }

    override fun hashCode(): Int = token.hashCode()
  }

  /**
   * Decoded token info.
   */
  data class DecodedToken(
    val externalIp: String,
    val externalPort: Int,
    val localIp: String?,
    val localPort: Int,
    val natType: NatType,
    val sessionId: ByteArray,
    val deviceHash: String,
    val expiresAt: Long,
    val isPersistent: Boolean,
    val isValid: Boolean,
    val errorMessage: String? = null
  ) {
    fun toConnectionInfo(): P2PConnection.ConnectionInfo {
      return P2PConnection.ConnectionInfo(
        externalIp = externalIp,
        externalPort = externalPort,
        localIp = localIp ?: "0.0.0.0",
        localPort = localPort,
        sessionId = sessionId.toHexString(),
        natType = natType,
        deviceHash = deviceHash
      )
    }

    private fun ByteArray.toHexString(): String {
      return joinToString("") { "%02x".format(it) }
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is DecodedToken) return false
      return deviceHash == other.deviceHash && sessionId.contentEquals(other.sessionId)
    }

    override fun hashCode(): Int = deviceHash.hashCode()
  }

  /**
   * Encode connection info into a shareable token.
   *
   * @param externalIp External IP from STUN
   * @param externalPort External port from STUN
   * @param localIp Local network IP (optional)
   * @param localPort Local port
   * @param natType NAT type detected
   * @param sessionId 8-byte session identifier
   * @param deviceHash Device fingerprint
   * @param persistent Create a long-lived token for trusted devices
   */
  fun encode(
    externalIp: String,
    externalPort: Int,
    localIp: String? = null,
    localPort: Int,
    natType: NatType,
    sessionId: ByteArray,
    deviceHash: String,
    persistent: Boolean = false
  ): EncodedToken {
    // Calculate expiry
    val expiryMinutes = if (persistent) {
      PERSISTENT_EXPIRY_DAYS * 24 * 60
    } else {
      DEFAULT_EXPIRY_MINUTES
    }
    val expiresAt = System.currentTimeMillis() + (expiryMinutes * 60 * 1000)

    // Build binary payload
    val buffer = ByteBuffer.allocate(35)

    // Version
    buffer.put(TOKEN_VERSION)

    // Flags
    var flags: Byte = 0
    if (localIp != null) flags = (flags.toInt() or FLAG_HAS_LOCAL_IP.toInt()).toByte()
    if (persistent) flags = (flags.toInt() or FLAG_PERSISTENT.toInt()).toByte()
    buffer.put(flags)

    // External IP (4 bytes)
    buffer.put(ipToBytes(externalIp))

    // External Port (2 bytes)
    buffer.putShort(externalPort.toShort())

    // Local IP (4 bytes) - use 0.0.0.0 if not present
    buffer.put(ipToBytes(localIp ?: "0.0.0.0"))

    // Local Port (2 bytes)
    buffer.putShort(localPort.toShort())

    // NAT Type (1 byte)
    buffer.put(natType.ordinal.toByte())

    // Session ID (8 bytes)
    buffer.put(sessionId.copyOf(8))

    // Device Hash (4 bytes - truncated SHA256)
    val hashBytes = hashDeviceId(deviceHash)
    buffer.put(hashBytes.copyOf(4))

    // Expiry (4 bytes - minutes since epoch / 60000)
    buffer.putInt((expiresAt / 60000).toInt())

    // Calculate CRC32 of everything so far
    val dataWithoutCrc = buffer.array().copyOf(31)
    val crc = CRC32()
    crc.update(dataWithoutCrc)
    buffer.putInt(crc.value.toInt())

    val rawBytes = buffer.array()
    val token = encodeBase32(rawBytes)

    return EncodedToken(
      token = formatToken(token),
      rawBytes = rawBytes,
      expiresAt = expiresAt,
      isPersistent = persistent
    )
  }

  /**
   * Convenience method to encode from ConnectionInfo.
   */
  fun encode(
    info: P2PConnection.ConnectionInfo,
    persistent: Boolean = false
  ): EncodedToken {
    return encode(
      externalIp = info.externalIp,
      externalPort = info.externalPort,
      localIp = info.localIp,
      localPort = info.localPort,
      natType = info.natType,
      sessionId = info.sessionId.hexToByteArray(),
      deviceHash = info.deviceHash,
      persistent = persistent
    )
  }

  /**
   * Decode a token string back to connection info.
   *
   * @param token Token string (with or without dashes)
   * @return DecodedToken with connection info or error
   */
  fun decode(token: String): DecodedToken {
    try {
      // Remove dashes and whitespace
      val cleanToken = token.uppercase().replace("-", "").replace(" ", "")

      // Decode Base32
      val rawBytes = decodeBase32(cleanToken)
      if (rawBytes.size < 35) {
        return errorToken("Invalid token length")
      }

      val buffer = ByteBuffer.wrap(rawBytes)

      // Version check
      val version = buffer.get()
      if (version != TOKEN_VERSION) {
        return errorToken("Unsupported token version: $version")
      }

      // Flags
      val flags = buffer.get()
      val hasLocalIp = (flags.toInt() and FLAG_HAS_LOCAL_IP.toInt()) != 0
      val isPersistent = (flags.toInt() and FLAG_PERSISTENT.toInt()) != 0

      // External IP
      val externalIpBytes = ByteArray(4)
      buffer.get(externalIpBytes)
      val externalIp = bytesToIp(externalIpBytes)

      // External Port
      val externalPort = buffer.short.toInt() and 0xFFFF

      // Local IP
      val localIpBytes = ByteArray(4)
      buffer.get(localIpBytes)
      val localIp = if (hasLocalIp) bytesToIp(localIpBytes) else null

      // Local Port
      val localPort = buffer.short.toInt() and 0xFFFF

      // NAT Type
      val natTypeOrdinal = buffer.get().toInt()
      val natType = NatType.entries.getOrElse(natTypeOrdinal) { NatType.UNKNOWN }

      // Session ID
      val sessionId = ByteArray(8)
      buffer.get(sessionId)

      // Device Hash (4 bytes)
      val deviceHashBytes = ByteArray(4)
      buffer.get(deviceHashBytes)
      val deviceHash = deviceHashBytes.toHexString()

      // Expiry
      val expiryMinutes = buffer.int.toLong()
      val expiresAt = expiryMinutes * 60000

      // CRC32 verification
      val expectedCrc = buffer.int
      val dataWithoutCrc = rawBytes.copyOf(31)
      val crc = CRC32()
      crc.update(dataWithoutCrc)
      val actualCrc = crc.value.toInt()

      if (expectedCrc != actualCrc) {
        return errorToken("Token checksum invalid - may be corrupted")
      }

      // Check expiry
      val isExpired = !isPersistent && System.currentTimeMillis() > expiresAt

      return DecodedToken(
        externalIp = externalIp,
        externalPort = externalPort,
        localIp = localIp,
        localPort = localPort,
        natType = natType,
        sessionId = sessionId,
        deviceHash = deviceHash,
        expiresAt = expiresAt,
        isPersistent = isPersistent,
        isValid = !isExpired,
        errorMessage = if (isExpired) "Token expired" else null
      )
    } catch (e: Exception) {
      Log.e(TAG, "Token decode error: ${e.message}")
      return errorToken("Invalid token format: ${e.message}")
    }
  }

  /**
   * Validate a token without fully decoding.
   */
  fun isValidFormat(token: String): Boolean {
    val clean = token.uppercase().replace("-", "").replace(" ", "")
    // Should be 56 Base32 characters for 35 bytes
    return clean.length >= 50 && clean.all { it in BASE32_ALPHABET }
  }

  /**
   * Create a simplified "quick connect" token for same-network connections.
   * Only contains local IP:port and device hash.
   */
  fun encodeQuickConnect(
    localIp: String,
    localPort: Int,
    deviceHash: String
  ): String {
    val buffer = ByteBuffer.allocate(11)
    buffer.put(ipToBytes(localIp))
    buffer.putShort(localPort.toShort())
    buffer.put(hashDeviceId(deviceHash).copyOf(4))

    val crc = CRC32()
    crc.update(buffer.array(), 0, 10)
    buffer.put((crc.value and 0xFF).toByte())

    val base32 = encodeBase32(buffer.array())
    return "Q${formatToken(base32)}" // Q prefix for quick connect
  }

  /**
   * Decode a quick connect token.
   */
  fun decodeQuickConnect(token: String): Pair<String, Int>? {
    if (!token.startsWith("Q")) return null

    val clean = token.substring(1).replace("-", "")
    val bytes = decodeBase32(clean)
    if (bytes.size < 11) return null

    // Verify CRC
    val crc = CRC32()
    crc.update(bytes, 0, 10)
    if ((crc.value and 0xFF).toByte() != bytes[10]) return null

    val buffer = ByteBuffer.wrap(bytes)
    val ipBytes = ByteArray(4)
    buffer.get(ipBytes)
    val ip = bytesToIp(ipBytes)
    val port = buffer.short.toInt() and 0xFFFF

    return Pair(ip, port)
  }

  // Helper functions

  private fun ipToBytes(ip: String): ByteArray {
    val parts = ip.split(".")
    if (parts.size != 4) return ByteArray(4)
    return byteArrayOf(
      parts[0].toInt().toByte(),
      parts[1].toInt().toByte(),
      parts[2].toInt().toByte(),
      parts[3].toInt().toByte()
    )
  }

  private fun bytesToIp(bytes: ByteArray): String {
    return "${bytes[0].toInt() and 0xFF}.${bytes[1].toInt() and 0xFF}." +
      "${bytes[2].toInt() and 0xFF}.${bytes[3].toInt() and 0xFF}"
  }

  private fun hashDeviceId(deviceHash: String): ByteArray {
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(deviceHash.toByteArray())
  }

  private fun encodeBase32(data: ByteArray): String {
    val result = StringBuilder()
    var buffer = 0
    var bitsLeft = 0

    for (byte in data) {
      buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
      bitsLeft += 8

      while (bitsLeft >= 5) {
        val index = (buffer shr (bitsLeft - 5)) and 0x1F
        result.append(BASE32_ALPHABET[index])
        bitsLeft -= 5
      }
    }

    if (bitsLeft > 0) {
      val index = (buffer shl (5 - bitsLeft)) and 0x1F
      result.append(BASE32_ALPHABET[index])
    }

    return result.toString()
  }

  private fun decodeBase32(encoded: String): ByteArray {
    val result = mutableListOf<Byte>()
    var buffer = 0
    var bitsLeft = 0

    for (char in encoded) {
      val value = BASE32_ALPHABET.indexOf(char)
      if (value < 0) continue // Skip invalid chars

      buffer = (buffer shl 5) or value
      bitsLeft += 5

      if (bitsLeft >= 8) {
        result.add((buffer shr (bitsLeft - 8)).toByte())
        bitsLeft -= 8
      }
    }

    return result.toByteArray()
  }

  private fun formatToken(raw: String): String {
    return raw.chunked(GROUP_SIZE).joinToString("-")
  }

  private fun ByteArray.toHexString(): String {
    return joinToString("") { "%02x".format(it) }
  }

  private fun String.hexToByteArray(): ByteArray {
    val len = length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
      data[i / 2] = ((Character.digit(this[i], 16) shl 4) +
        Character.digit(this[i + 1], 16)).toByte()
      i += 2
    }
    return data
  }

  private fun errorToken(message: String): DecodedToken {
    return DecodedToken(
      externalIp = "0.0.0.0",
      externalPort = 0,
      localIp = null,
      localPort = 0,
      natType = NatType.UNKNOWN,
      sessionId = ByteArray(8),
      deviceHash = "",
      expiresAt = 0,
      isPersistent = false,
      isValid = false,
      errorMessage = message
    )
  }
}
