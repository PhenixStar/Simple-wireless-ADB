package com.phenix.wirelessadb.p2p

import android.util.Log
import com.phenix.wirelessadb.model.NatType
import com.phenix.wirelessadb.model.StunResult
import kotlinx.coroutines.*
import java.net.*
import java.nio.ByteBuffer
import java.security.SecureRandom

/**
 * NAT Hole Punching implementation for P2P connectivity.
 *
 * Hole punching allows two devices behind NAT to establish direct
 * connections by exploiting how NATs track "conversations":
 *
 * 1. Both devices discover their external endpoints via STUN
 * 2. Both send UDP packets to each other's external IP:port
 * 3. NATs see these as "outbound" and remember the mapping
 * 4. When peer's packet arrives, NAT sees it as "reply" and forwards it
 * 5. Bidirectional UDP channel established through the "holes"
 *
 * Usage:
 * ```
 * val puncher = HolePuncher()
 * val result = puncher.punchHole(
 *   localStun = myStunResult,
 *   remoteEndpoint = peerExternalEndpoint
 * )
 * if (result.success) {
 *   // Use result.socket for communication
 * }
 * ```
 */
class HolePuncher {

  companion object {
    private const val TAG = "HolePuncher"

    /** Magic bytes to identify our hole punch packets */
    private val MAGIC_HEADER = byteArrayOf(0x52, 0x41, 0x44, 0x42) // "RADB"

    /** Packet types */
    private const val PACKET_PUNCH: Byte = 0x01
    private const val PACKET_ACK: Byte = 0x02
    private const val PACKET_KEEPALIVE: Byte = 0x03
    private const val PACKET_DATA: Byte = 0x04

    /** Timing constants */
    private const val PUNCH_INTERVAL_MS = 100L
    private const val PUNCH_TIMEOUT_MS = 10_000L
    private const val MAX_PUNCH_ATTEMPTS = 100
    private const val KEEPALIVE_INTERVAL_MS = 5_000L
    private const val CONNECTION_TIMEOUT_MS = 30_000L

    /** Buffer sizes */
    private const val RECEIVE_BUFFER_SIZE = 1024
  }

  private val random = SecureRandom()

  /**
   * Result of hole punching attempt.
   */
  data class PunchResult(
    val success: Boolean,
    val socket: DatagramSocket? = null,
    val remoteAddress: InetSocketAddress? = null,
    val localPort: Int = 0,
    val latencyMs: Long = 0,
    val punchAttempts: Int = 0,
    val errorMessage: String? = null
  )

  /**
   * Attempt to punch a hole through NAT to reach the remote peer.
   *
   * Both devices must call this simultaneously (within ~10 seconds)
   * with each other's external endpoints from STUN.
   *
   * @param localStun Our STUN result (provides local port to bind)
   * @param remoteEndpoint Peer's external IP:port from their STUN result
   * @param sessionId Shared session ID for packet verification
   * @return PunchResult with socket if successful
   */
  suspend fun punchHole(
    localStun: StunResult,
    remoteEndpoint: InetSocketAddress,
    sessionId: ByteArray = generateSessionId()
  ): PunchResult = withContext(Dispatchers.IO) {
    Log.i(TAG, "Starting hole punch: local=${localStun.localEndpoint} -> remote=$remoteEndpoint")

    var socket: DatagramSocket? = null

    try {
      // Bind to the same local port used for STUN
      // This ensures we use the same NAT mapping
      socket = DatagramSocket(localStun.localPort)
      socket.soTimeout = PUNCH_INTERVAL_MS.toInt()
      socket.reuseAddress = true

      val startTime = System.currentTimeMillis()
      var attempts = 0
      var receivedAck = false
      var sentAck = false

      // Hole punching loop
      while (!receivedAck && attempts < MAX_PUNCH_ATTEMPTS) {
        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed > PUNCH_TIMEOUT_MS) {
          Log.w(TAG, "Hole punch timed out after ${elapsed}ms")
          break
        }

        // Send punch packet
        val punchPacket = buildPunchPacket(sessionId, attempts)
        val sendPacket = DatagramPacket(
          punchPacket,
          punchPacket.size,
          remoteEndpoint
        )
        socket.send(sendPacket)
        attempts++

        // Try to receive response
        val receiveBuffer = ByteArray(RECEIVE_BUFFER_SIZE)
        val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

        try {
          socket.receive(receivePacket)

          // Verify packet is from expected peer
          if (receivePacket.address == remoteEndpoint.address) {
            val packetType = parsePacket(receiveBuffer, sessionId)

            when (packetType) {
              PACKET_PUNCH -> {
                // Peer's punch packet received - send ACK
                Log.d(TAG, "Received peer's punch packet")
                if (!sentAck) {
                  val ackPacket = buildAckPacket(sessionId)
                  socket.send(DatagramPacket(ackPacket, ackPacket.size, remoteEndpoint))
                  sentAck = true
                }
              }
              PACKET_ACK -> {
                // Peer acknowledged our punch - hole is open!
                Log.i(TAG, "Received ACK - hole punched successfully!")
                receivedAck = true
              }
              else -> {
                Log.d(TAG, "Unknown packet type: $packetType")
              }
            }
          }
        } catch (e: SocketTimeoutException) {
          // Normal - just means no packet received in interval
          Log.v(TAG, "Punch attempt $attempts - waiting for response")
        }

        // Small delay between attempts
        if (!receivedAck) {
          delay(PUNCH_INTERVAL_MS)
        }
      }

      val latency = System.currentTimeMillis() - startTime

      if (receivedAck) {
        // Set longer timeout for actual communication
        socket.soTimeout = CONNECTION_TIMEOUT_MS.toInt()

        Log.i(TAG, "Hole punch successful after $attempts attempts (${latency}ms)")
        PunchResult(
          success = true,
          socket = socket,
          remoteAddress = remoteEndpoint,
          localPort = socket.localPort,
          latencyMs = latency,
          punchAttempts = attempts
        )
      } else {
        socket.close()
        Log.w(TAG, "Hole punch failed after $attempts attempts")
        PunchResult(
          success = false,
          punchAttempts = attempts,
          latencyMs = latency,
          errorMessage = "Failed to establish connection after $attempts attempts"
        )
      }
    } catch (e: Exception) {
      Log.e(TAG, "Hole punch error: ${e.message}")
      socket?.close()
      PunchResult(
        success = false,
        errorMessage = e.message
      )
    }
  }

  /**
   * Aggressive hole punching for symmetric NAT.
   *
   * Symmetric NATs assign different external ports for each destination.
   * This makes hole punching harder because the port peer sees is different
   * from what STUN reported. We try to predict the port pattern.
   *
   * @param localStun Our STUN result
   * @param remoteIp Peer's external IP
   * @param basePort Peer's external port from STUN (starting point)
   * @param portRange Range of ports to try around basePort
   */
  suspend fun aggressivePunch(
    localStun: StunResult,
    remoteIp: String,
    basePort: Int,
    portRange: IntRange = -10..10,
    sessionId: ByteArray = generateSessionId()
  ): PunchResult = withContext(Dispatchers.IO) {
    Log.i(TAG, "Aggressive punch to $remoteIp:$basePort (range: $portRange)")

    var socket: DatagramSocket? = null

    try {
      socket = DatagramSocket(localStun.localPort)
      socket.soTimeout = PUNCH_INTERVAL_MS.toInt()
      socket.reuseAddress = true

      val remoteAddress = InetAddress.getByName(remoteIp)
      val startTime = System.currentTimeMillis()
      var bestResult: PunchResult? = null

      // Try multiple ports in the range
      val portsToTry = portRange.map { basePort + it }.filter { it in 1024..65535 }

      for (round in 0 until 5) {
        for (port in portsToTry) {
          val elapsed = System.currentTimeMillis() - startTime
          if (elapsed > PUNCH_TIMEOUT_MS) break

          val endpoint = InetSocketAddress(remoteAddress, port)

          // Send punch packet to this port
          val punchPacket = buildPunchPacket(sessionId, round * portsToTry.size)
          socket.send(DatagramPacket(punchPacket, punchPacket.size, endpoint))

          // Quick check for response
          try {
            val receiveBuffer = ByteArray(RECEIVE_BUFFER_SIZE)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            socket.receive(receivePacket)

            val packetType = parsePacket(receiveBuffer, sessionId)
            if (packetType == PACKET_PUNCH || packetType == PACKET_ACK) {
              val actualEndpoint = InetSocketAddress(
                receivePacket.address,
                receivePacket.port
              )
              Log.i(TAG, "Found peer at $actualEndpoint!")

              // Send ACK and confirm
              val ackPacket = buildAckPacket(sessionId)
              socket.send(DatagramPacket(ackPacket, ackPacket.size, actualEndpoint))

              socket.soTimeout = CONNECTION_TIMEOUT_MS.toInt()
              return@withContext PunchResult(
                success = true,
                socket = socket,
                remoteAddress = actualEndpoint,
                localPort = socket.localPort,
                latencyMs = System.currentTimeMillis() - startTime,
                punchAttempts = round * portsToTry.size + portsToTry.indexOf(port)
              )
            }
          } catch (e: SocketTimeoutException) {
            // Continue trying
          }
        }
        delay(50)
      }

      socket.close()
      PunchResult(
        success = false,
        errorMessage = "Aggressive punch failed - symmetric NAT may be blocking"
      )
    } catch (e: Exception) {
      Log.e(TAG, "Aggressive punch error: ${e.message}")
      socket?.close()
      PunchResult(
        success = false,
        errorMessage = e.message
      )
    }
  }

  /**
   * Choose the best punching strategy based on NAT types.
   */
  suspend fun smartPunch(
    localStun: StunResult,
    remoteEndpoint: InetSocketAddress,
    remoteNatType: NatType,
    sessionId: ByteArray = generateSessionId()
  ): PunchResult {
    Log.i(TAG, "Smart punch: local NAT=${localStun.natType}, remote NAT=$remoteNatType")

    return when {
      // No NAT or full cone - direct punch should work
      localStun.natType == NatType.NONE ||
        localStun.natType == NatType.FULL_CONE ||
        remoteNatType == NatType.NONE ||
        remoteNatType == NatType.FULL_CONE -> {
        punchHole(localStun, remoteEndpoint, sessionId)
      }

      // Both symmetric - very hard, try aggressive
      localStun.natType == NatType.SYMMETRIC &&
        remoteNatType == NatType.SYMMETRIC -> {
        Log.w(TAG, "Both devices have symmetric NAT - low success chance")
        aggressivePunch(
          localStun,
          remoteEndpoint.address.hostAddress ?: "",
          remoteEndpoint.port,
          sessionId = sessionId
        )
      }

      // One side symmetric - try aggressive with wider range
      localStun.natType == NatType.SYMMETRIC ||
        remoteNatType == NatType.SYMMETRIC -> {
        aggressivePunch(
          localStun,
          remoteEndpoint.address.hostAddress ?: "",
          remoteEndpoint.port,
          portRange = -50..50,
          sessionId = sessionId
        )
      }

      // Port restricted - standard punch with more attempts
      else -> {
        punchHole(localStun, remoteEndpoint, sessionId)
      }
    }
  }

  /**
   * Build a hole punch packet.
   *
   * Format:
   * - 4 bytes: Magic header "RADB"
   * - 1 byte: Packet type (PUNCH)
   * - 8 bytes: Session ID
   * - 4 bytes: Sequence number
   * - 8 bytes: Timestamp
   */
  private fun buildPunchPacket(sessionId: ByteArray, sequence: Int): ByteArray {
    val buffer = ByteBuffer.allocate(25)
    buffer.put(MAGIC_HEADER)
    buffer.put(PACKET_PUNCH)
    buffer.put(sessionId.copyOf(8))
    buffer.putInt(sequence)
    buffer.putLong(System.currentTimeMillis())
    return buffer.array()
  }

  /**
   * Build an acknowledgment packet.
   */
  fun buildAckPacket(sessionId: ByteArray): ByteArray {
    val buffer = ByteBuffer.allocate(21)
    buffer.put(MAGIC_HEADER)
    buffer.put(PACKET_ACK)
    buffer.put(sessionId.copyOf(8))
    buffer.putLong(System.currentTimeMillis())
    return buffer.array()
  }

  /**
   * Build a keepalive packet.
   */
  fun buildKeepalivePacket(sessionId: ByteArray): ByteArray {
    val buffer = ByteBuffer.allocate(13)
    buffer.put(MAGIC_HEADER)
    buffer.put(PACKET_KEEPALIVE)
    buffer.put(sessionId.copyOf(8))
    return buffer.array()
  }

  /**
   * Build a data packet with payload.
   */
  fun buildDataPacket(sessionId: ByteArray, data: ByteArray): ByteArray {
    val buffer = ByteBuffer.allocate(13 + data.size)
    buffer.put(MAGIC_HEADER)
    buffer.put(PACKET_DATA)
    buffer.put(sessionId.copyOf(8))
    buffer.put(data)
    return buffer.array()
  }

  /**
   * Parse an incoming packet and return its type.
   * Returns -1 if invalid.
   */
  private fun parsePacket(data: ByteArray, expectedSessionId: ByteArray): Byte {
    if (data.size < 13) return -1

    // Check magic header
    if (data[0] != MAGIC_HEADER[0] ||
      data[1] != MAGIC_HEADER[1] ||
      data[2] != MAGIC_HEADER[2] ||
      data[3] != MAGIC_HEADER[3]) {
      return -1
    }

    val packetType = data[4]

    // Verify session ID
    val packetSessionId = data.copyOfRange(5, 13)
    if (!packetSessionId.contentEquals(expectedSessionId.copyOf(8))) {
      Log.w(TAG, "Session ID mismatch")
      return -1
    }

    return packetType
  }

  /**
   * Extract data payload from a data packet.
   */
  fun extractData(packet: ByteArray, sessionId: ByteArray): ByteArray? {
    if (parsePacket(packet, sessionId) != PACKET_DATA) return null
    if (packet.size <= 13) return byteArrayOf()
    return packet.copyOfRange(13, packet.size)
  }

  /**
   * Generate a random session ID for packet verification.
   */
  fun generateSessionId(): ByteArray {
    val id = ByteArray(8)
    random.nextBytes(id)
    return id
  }

  /**
   * Start keepalive loop for an established connection.
   */
  suspend fun startKeepalive(
    socket: DatagramSocket,
    remoteAddress: InetSocketAddress,
    sessionId: ByteArray,
    scope: CoroutineScope
  ): Job {
    return scope.launch(Dispatchers.IO) {
      while (isActive && !socket.isClosed) {
        try {
          val keepalive = buildKeepalivePacket(sessionId)
          socket.send(DatagramPacket(keepalive, keepalive.size, remoteAddress))
          delay(KEEPALIVE_INTERVAL_MS)
        } catch (e: Exception) {
          Log.w(TAG, "Keepalive failed: ${e.message}")
          break
        }
      }
    }
  }
}
