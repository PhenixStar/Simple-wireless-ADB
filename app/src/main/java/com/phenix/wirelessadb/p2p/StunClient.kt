package com.phenix.wirelessadb.p2p

import android.util.Log
import com.phenix.wirelessadb.model.NatType
import com.phenix.wirelessadb.model.StunError
import com.phenix.wirelessadb.model.StunResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.security.SecureRandom

/**
 * STUN Client implementing RFC 5389 for NAT traversal.
 *
 * STUN (Session Traversal Utilities for NAT) discovers the external
 * IP:port that a NAT assigns when sending outbound packets. This info
 * is needed for NAT hole punching in P2P connections.
 *
 * Usage:
 * ```
 * val client = StunClient()
 * val result = client.discoverExternalEndpoint()
 * println("External: ${result.externalEndpoint}")
 * ```
 */
class StunClient {

  companion object {
    private const val TAG = "StunClient"

    // STUN message constants (RFC 5389)
    private const val STUN_MAGIC_COOKIE = 0x2112A442
    private const val STUN_HEADER_SIZE = 20
    private const val STUN_BINDING_REQUEST: Short = 0x0001
    private const val STUN_BINDING_RESPONSE: Short = 0x0101

    // STUN attribute types
    private const val ATTR_MAPPED_ADDRESS: Short = 0x0001
    private const val ATTR_XOR_MAPPED_ADDRESS: Short = 0x0020
    private const val ATTR_ERROR_CODE: Short = 0x0009

    // Address families
    private const val ADDRESS_FAMILY_IPV4: Byte = 0x01
    private const val ADDRESS_FAMILY_IPV6: Byte = 0x02

    // Timeouts
    private const val DEFAULT_TIMEOUT_MS = 3000L
    private const val MAX_RETRIES = 2

    // Public STUN servers (free, no account required)
    val DEFAULT_STUN_SERVERS = listOf(
      StunServer("stun.l.google.com", 19302),
      StunServer("stun1.l.google.com", 19302),
      StunServer("stun.cloudflare.com", 3478),
      StunServer("stun.stunprotocol.org", 3478)
    )
  }

  data class StunServer(val host: String, val port: Int) {
    override fun toString() = "$host:$port"
  }

  private val random = SecureRandom()

  /**
   * Discover external endpoint using STUN.
   *
   * Tries multiple STUN servers until one succeeds.
   * Returns the external IP:port as seen by the STUN server.
   *
   * @param localPort Local port to bind (0 for random)
   * @param servers STUN servers to try
   * @param timeoutMs Timeout per server attempt
   * @return StunResult with external endpoint info
   * @throws StunError if all servers fail
   */
  suspend fun discoverExternalEndpoint(
    localPort: Int = 0,
    servers: List<StunServer> = DEFAULT_STUN_SERVERS,
    timeoutMs: Long = DEFAULT_TIMEOUT_MS
  ): StunResult = withContext(Dispatchers.IO) {
    val errors = mutableListOf<Exception>()

    for (server in servers) {
      try {
        val result = queryStunServer(server, localPort, timeoutMs)
        Log.i(TAG, "STUN success: ${result.externalEndpoint} via $server")
        return@withContext result
      } catch (e: Exception) {
        Log.w(TAG, "STUN server $server failed: ${e.message}")
        errors.add(e)
      }
    }

    throw StunError.AllServersFailed
  }

  /**
   * Query a single STUN server.
   */
  private suspend fun queryStunServer(
    server: StunServer,
    localPort: Int,
    timeoutMs: Long
  ): StunResult = withContext(Dispatchers.IO) {
    var socket: DatagramSocket? = null

    try {
      // Create and bind socket
      socket = if (localPort > 0) {
        DatagramSocket(localPort)
      } else {
        DatagramSocket()
      }
      socket.soTimeout = timeoutMs.toInt()

      val localAddress = socket.localAddress.hostAddress ?: "0.0.0.0"
      val boundPort = socket.localPort

      // Resolve STUN server
      val serverAddress = InetAddress.getByName(server.host)
      val serverEndpoint = InetSocketAddress(serverAddress, server.port)

      // Generate transaction ID
      val transactionId = ByteArray(12)
      random.nextBytes(transactionId)

      // Build and send STUN Binding Request
      val request = buildBindingRequest(transactionId)
      val sendPacket = DatagramPacket(request, request.size, serverEndpoint)

      val startTime = System.currentTimeMillis()

      // Retry loop
      var response: ByteArray? = null
      for (attempt in 0 until MAX_RETRIES) {
        socket.send(sendPacket)

        // Receive response
        val receiveBuffer = ByteArray(1024)
        val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

        try {
          socket.receive(receivePacket)
          response = receiveBuffer.copyOf(receivePacket.length)
          break
        } catch (e: Exception) {
          if (attempt == MAX_RETRIES - 1) throw e
          Log.d(TAG, "Retry ${attempt + 1} for $server")
        }
      }

      val latency = System.currentTimeMillis() - startTime

      if (response == null) {
        throw StunError.Timeout(server.toString())
      }

      // Parse response
      val (externalIp, externalPort) = parseBindingResponse(response, transactionId)

      StunResult(
        externalIp = externalIp,
        externalPort = externalPort,
        localIp = localAddress,
        localPort = boundPort,
        stunServer = server.toString(),
        natType = determineNatType(localAddress, boundPort, externalIp, externalPort),
        latencyMs = latency
      )
    } finally {
      socket?.close()
    }
  }

  /**
   * Build a STUN Binding Request message (RFC 5389).
   *
   * Format:
   * ```
   * 0                   1                   2                   3
   * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
   * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   * |0 0|     STUN Message Type     |         Message Length        |
   * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   * |                         Magic Cookie                          |
   * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   * |                                                               |
   * |                     Transaction ID (96 bits)                  |
   * |                                                               |
   * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   * ```
   */
  private fun buildBindingRequest(transactionId: ByteArray): ByteArray {
    val buffer = ByteBuffer.allocate(STUN_HEADER_SIZE)

    // Message Type: Binding Request (0x0001)
    buffer.putShort(STUN_BINDING_REQUEST)

    // Message Length: 0 (no attributes in request)
    buffer.putShort(0)

    // Magic Cookie
    buffer.putInt(STUN_MAGIC_COOKIE)

    // Transaction ID (12 bytes)
    buffer.put(transactionId)

    return buffer.array()
  }

  /**
   * Parse STUN Binding Response to extract external endpoint.
   *
   * Looks for XOR-MAPPED-ADDRESS (preferred) or MAPPED-ADDRESS attribute.
   */
  private fun parseBindingResponse(
    response: ByteArray,
    expectedTransactionId: ByteArray
  ): Pair<String, Int> {
    if (response.size < STUN_HEADER_SIZE) {
      throw StunError.InvalidResponse("Response too short: ${response.size} bytes")
    }

    val buffer = ByteBuffer.wrap(response)

    // Check message type
    val messageType = buffer.short
    if (messageType != STUN_BINDING_RESPONSE) {
      throw StunError.InvalidResponse("Unexpected message type: $messageType")
    }

    // Message length
    val messageLength = buffer.short.toInt() and 0xFFFF

    // Verify magic cookie
    val magicCookie = buffer.int
    if (magicCookie != STUN_MAGIC_COOKIE) {
      throw StunError.InvalidResponse("Invalid magic cookie")
    }

    // Verify transaction ID
    val transactionId = ByteArray(12)
    buffer.get(transactionId)
    if (!transactionId.contentEquals(expectedTransactionId)) {
      throw StunError.InvalidResponse("Transaction ID mismatch")
    }

    // Parse attributes
    var externalIp: String? = null
    var externalPort: Int? = null
    var bytesRead = 0

    while (bytesRead < messageLength && buffer.remaining() >= 4) {
      val attrType = buffer.short
      val attrLength = buffer.short.toInt() and 0xFFFF

      when (attrType) {
        ATTR_XOR_MAPPED_ADDRESS -> {
          val (ip, port) = parseXorMappedAddress(buffer, attrLength)
          externalIp = ip
          externalPort = port
        }
        ATTR_MAPPED_ADDRESS -> {
          // Fallback if XOR-MAPPED-ADDRESS not present
          if (externalIp == null) {
            val (ip, port) = parseMappedAddress(buffer, attrLength)
            externalIp = ip
            externalPort = port
          } else {
            buffer.position(buffer.position() + attrLength)
          }
        }
        ATTR_ERROR_CODE -> {
          val errorClass = buffer.get().toInt() and 0x07
          val errorNumber = buffer.get().toInt() and 0xFF
          val errorCode = errorClass * 100 + errorNumber
          throw StunError.InvalidResponse("STUN error $errorCode")
        }
        else -> {
          // Skip unknown attribute
          buffer.position(buffer.position() + attrLength)
        }
      }

      // Pad to 4-byte boundary
      val padding = (4 - (attrLength % 4)) % 4
      bytesRead += 4 + attrLength + padding
      if (padding > 0 && buffer.remaining() >= padding) {
        buffer.position(buffer.position() + padding)
      }
    }

    if (externalIp == null || externalPort == null) {
      throw StunError.InvalidResponse("No mapped address in response")
    }

    return Pair(externalIp, externalPort)
  }

  /**
   * Parse XOR-MAPPED-ADDRESS attribute (RFC 5389).
   *
   * The address and port are XORed with the magic cookie.
   */
  private fun parseXorMappedAddress(buffer: ByteBuffer, length: Int): Pair<String, Int> {
    buffer.get() // Reserved byte
    val family = buffer.get()
    val xorPort = buffer.short.toInt() and 0xFFFF
    val port = xorPort xor (STUN_MAGIC_COOKIE shr 16)

    val ip = when (family) {
      ADDRESS_FAMILY_IPV4 -> {
        val xorAddress = buffer.int
        val address = xorAddress xor STUN_MAGIC_COOKIE
        "${(address shr 24) and 0xFF}.${(address shr 16) and 0xFF}.${(address shr 8) and 0xFF}.${address and 0xFF}"
      }
      ADDRESS_FAMILY_IPV6 -> {
        // IPv6 not fully implemented for simplicity
        val bytes = ByteArray(16)
        buffer.get(bytes)
        // XOR with magic cookie + transaction ID
        "IPv6 not supported"
      }
      else -> throw StunError.InvalidResponse("Unknown address family: $family")
    }

    return Pair(ip, port)
  }

  /**
   * Parse MAPPED-ADDRESS attribute (legacy, non-XOR).
   */
  private fun parseMappedAddress(buffer: ByteBuffer, length: Int): Pair<String, Int> {
    buffer.get() // Reserved
    val family = buffer.get()
    val port = buffer.short.toInt() and 0xFFFF

    val ip = when (family) {
      ADDRESS_FAMILY_IPV4 -> {
        val bytes = ByteArray(4)
        buffer.get(bytes)
        bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
      }
      else -> throw StunError.InvalidResponse("Unknown address family: $family")
    }

    return Pair(ip, port)
  }

  /**
   * Simple NAT type detection based on address comparison.
   *
   * Full NAT type detection requires multiple STUN queries to different
   * servers/ports, which is more complex. This is a basic heuristic.
   */
  private fun determineNatType(
    localIp: String,
    localPort: Int,
    externalIp: String,
    externalPort: Int
  ): NatType {
    return when {
      // Same IP and port = no NAT (public IP)
      localIp == externalIp && localPort == externalPort -> NatType.NONE

      // Same IP but different port = port-only NAT (rare)
      localIp == externalIp -> NatType.PORT_RESTRICTED

      // Different IP = NAT present, type unknown without more tests
      localPort == externalPort -> NatType.FULL_CONE // Likely cone NAT

      // Different IP and port = could be symmetric or restricted
      else -> NatType.UNKNOWN
    }
  }

  /**
   * Quick check if external endpoint is reachable.
   * Useful for validating STUN result before hole punching.
   */
  suspend fun validateEndpoint(result: StunResult): Boolean = withContext(Dispatchers.IO) {
    try {
      // Do another STUN query and compare results
      val newResult = discoverExternalEndpoint(localPort = result.localPort)
      newResult.externalIp == result.externalIp &&
        newResult.externalPort == result.externalPort
    } catch (e: Exception) {
      false
    }
  }
}
