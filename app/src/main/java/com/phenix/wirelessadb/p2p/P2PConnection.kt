package com.phenix.wirelessadb.p2p

import android.util.Log
import com.phenix.wirelessadb.model.NatType
import com.phenix.wirelessadb.model.StunResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer

/**
 * Manages a P2P connection with automatic NAT traversal.
 *
 * This class orchestrates the full P2P connection lifecycle:
 * 1. STUN discovery to find external endpoint
 * 2. Hole punching to establish UDP channel
 * 3. TCP-over-UDP tunneling for ADB traffic
 * 4. Keepalive and reconnection handling
 *
 * Usage:
 * ```
 * val connection = P2PConnection(context)
 *
 * // Start as initiator (generates token for peer)
 * val token = connection.startAsInitiator()
 * showTokenToPeer(token)
 *
 * // Or join with peer's token
 * connection.joinWithToken(peerToken)
 *
 * // Monitor connection state
 * connection.state.collect { state ->
 *   updateUI(state)
 * }
 *
 * // When done
 * connection.disconnect()
 * ```
 */
class P2PConnection {

  companion object {
    private const val TAG = "P2PConnection"

    /** Local TCP port for ADB tunnel endpoint */
    private const val LOCAL_TUNNEL_PORT = 15555

    /** Max packet size for UDP fragmentation */
    private const val MAX_UDP_PAYLOAD = 1400

    /** Timeout for various operations */
    private const val STUN_TIMEOUT_MS = 5000L
    private const val CONNECT_TIMEOUT_MS = 30000L
    private const val DATA_TIMEOUT_MS = 60000L
  }

  /**
   * Connection states.
   */
  enum class ConnectionState {
    /** Initial state, not connected */
    DISCONNECTED,

    /** Discovering external endpoint via STUN */
    DISCOVERING,

    /** Waiting for peer to connect */
    WAITING_FOR_PEER,

    /** Hole punching in progress */
    PUNCHING,

    /** Connected, tunnel active */
    CONNECTED,

    /** Connection error */
    ERROR
  }

  /**
   * Connection info to share with peer.
   */
  data class ConnectionInfo(
    val externalIp: String,
    val externalPort: Int,
    val localIp: String,
    val localPort: Int,
    val sessionId: String,
    val natType: NatType,
    val deviceHash: String
  ) {
    fun toExternalEndpoint(): InetSocketAddress {
      return InetSocketAddress(externalIp, externalPort)
    }

    fun toLocalEndpoint(): InetSocketAddress {
      return InetSocketAddress(localIp, localPort)
    }
  }

  /**
   * Connection event for callbacks.
   */
  sealed class ConnectionEvent {
    object Connecting : ConnectionEvent()
    object Connected : ConnectionEvent()
    data class Disconnected(val reason: String) : ConnectionEvent()
    data class Error(val message: String) : ConnectionEvent()
    data class DataReceived(val bytes: Int) : ConnectionEvent()
  }

  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  // State
  private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
  val state: StateFlow<ConnectionState> = _state

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error

  private val _bytesTransferred = MutableStateFlow(0L)
  val bytesTransferred: StateFlow<Long> = _bytesTransferred

  // Components
  private val stunClient = StunClient()
  private val holePuncher = HolePuncher()

  // Connection state
  private var stunResult: StunResult? = null
  private var punchSocket: DatagramSocket? = null
  private var remoteAddress: InetSocketAddress? = null
  private var sessionId: ByteArray = holePuncher.generateSessionId()
  private var tunnelJob: Job? = null
  private var keepaliveJob: Job? = null

  // Local tunnel server
  private var tunnelServer: ServerSocket? = null
  private var tunnelPort: Int = LOCAL_TUNNEL_PORT

  /**
   * Discover our external endpoint via STUN.
   * This is the first step before any P2P connection.
   */
  suspend fun discoverEndpoint(): StunResult? = withContext(Dispatchers.IO) {
    _state.value = ConnectionState.DISCOVERING
    _error.value = null

    try {
      val result = stunClient.discoverExternalEndpoint()
      stunResult = result
      Log.i(TAG, "STUN discovered: ${result.externalEndpoint}")
      result
    } catch (e: Exception) {
      Log.e(TAG, "STUN failed: ${e.message}")
      _error.value = "Failed to discover external endpoint: ${e.message}"
      _state.value = ConnectionState.ERROR
      null
    }
  }

  /**
   * Generate connection info to share with peer.
   * Call discoverEndpoint() first.
   *
   * @param deviceHash This device's unique identifier
   */
  fun getConnectionInfo(deviceHash: String): ConnectionInfo? {
    val stun = stunResult ?: return null
    return ConnectionInfo(
      externalIp = stun.externalIp,
      externalPort = stun.externalPort,
      localIp = stun.localIp,
      localPort = stun.localPort,
      sessionId = sessionId.toHexString(),
      natType = stun.natType,
      deviceHash = deviceHash
    )
  }

  /**
   * Connect to a peer using their connection info.
   *
   * @param peerInfo Connection info from peer (via token)
   * @param isLocalNetwork True if peer is on same network (skip hole punch)
   */
  suspend fun connectToPeer(
    peerInfo: ConnectionInfo,
    isLocalNetwork: Boolean = false
  ): Boolean = withContext(Dispatchers.IO) {
    // Ensure we have our own STUN result
    if (stunResult == null) {
      discoverEndpoint() ?: return@withContext false
    }

    val localStun = stunResult!!

    // Use peer's session ID for the connection
    sessionId = peerInfo.sessionId.hexToByteArray()

    _state.value = ConnectionState.PUNCHING
    Log.i(TAG, "Connecting to peer: ${peerInfo.externalIp}:${peerInfo.externalPort}")

    try {
      val endpoint = if (isLocalNetwork) {
        // Same network - use local IP for faster connection
        peerInfo.toLocalEndpoint()
      } else {
        peerInfo.toExternalEndpoint()
      }

      // Try hole punching
      val punchResult = holePuncher.smartPunch(
        localStun = localStun,
        remoteEndpoint = endpoint,
        remoteNatType = peerInfo.natType,
        sessionId = sessionId
      )

      if (punchResult.success && punchResult.socket != null) {
        punchSocket = punchResult.socket
        remoteAddress = punchResult.remoteAddress
        _state.value = ConnectionState.CONNECTED

        // Start keepalive
        keepaliveJob = holePuncher.startKeepalive(
          punchResult.socket,
          punchResult.remoteAddress!!,
          sessionId,
          scope
        )

        // Start UDP tunnel for ADB traffic
        startTunnel()

        Log.i(TAG, "P2P connected successfully!")
        true
      } else {
        _error.value = punchResult.errorMessage ?: "Hole punch failed"
        _state.value = ConnectionState.ERROR
        false
      }
    } catch (e: Exception) {
      Log.e(TAG, "Connection failed: ${e.message}")
      _error.value = e.message
      _state.value = ConnectionState.ERROR
      false
    }
  }

  /**
   * Wait for a peer to connect to us.
   * Used when we initiated and shared our token.
   */
  suspend fun waitForPeer(
    timeoutMs: Long = CONNECT_TIMEOUT_MS
  ): Boolean = withContext(Dispatchers.IO) {
    if (stunResult == null) {
      discoverEndpoint() ?: return@withContext false
    }

    _state.value = ConnectionState.WAITING_FOR_PEER

    try {
      withTimeout(timeoutMs) {
        // Keep the STUN-bound socket open and wait for incoming packets
        val socket = DatagramSocket(stunResult!!.localPort)
        socket.soTimeout = 1000

        var connected = false
        val startTime = System.currentTimeMillis()

        while (!connected && isActive) {
          try {
            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)

            // Check if it's a punch packet
            if (buffer[0] == 0x52.toByte() && // R
              buffer[1] == 0x41.toByte() && // A
              buffer[2] == 0x44.toByte() && // D
              buffer[3] == 0x42.toByte()    // B
            ) {
              Log.i(TAG, "Received punch from ${packet.address}:${packet.port}")

              // Send ACK back (not keepalive - ACK is expected by peer)
              val ack = holePuncher.buildAckPacket(sessionId)
              socket.send(DatagramPacket(ack, ack.size, packet.address, packet.port))

              punchSocket = socket
              remoteAddress = InetSocketAddress(packet.address, packet.port)
              _state.value = ConnectionState.CONNECTED
              connected = true

              // Start keepalive and tunnel
              keepaliveJob = holePuncher.startKeepalive(socket, remoteAddress!!, sessionId, scope)
              startTunnel()
            }
          } catch (e: SocketTimeoutException) {
            // Check timeout
            if (System.currentTimeMillis() - startTime > timeoutMs) break
          }
        }

        connected
      }
    } catch (e: TimeoutCancellationException) {
      _error.value = "Peer connection timed out"
      _state.value = ConnectionState.ERROR
      false
    } catch (e: Exception) {
      _error.value = e.message
      _state.value = ConnectionState.ERROR
      false
    }
  }

  /**
   * Start TCP-over-UDP tunnel for ADB traffic.
   *
   * Creates a local TCP server that accepts connections and
   * forwards data through the UDP hole-punched channel.
   */
  private fun startTunnel() {
    tunnelJob = scope.launch {
      try {
        // Find an available port
        tunnelServer = ServerSocket(0)
        tunnelPort = tunnelServer!!.localPort
        Log.i(TAG, "Tunnel server started on port $tunnelPort")

        while (isActive && !tunnelServer!!.isClosed) {
          try {
            val clientSocket = tunnelServer!!.accept()
            Log.d(TAG, "Tunnel client connected")

            // Handle this TCP connection
            launch {
              handleTunnelClient(clientSocket)
            }
          } catch (e: IOException) {
            if (isActive) {
              Log.w(TAG, "Tunnel accept error: ${e.message}")
            }
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Tunnel server error: ${e.message}")
        _error.value = "Tunnel failed: ${e.message}"
        _state.value = ConnectionState.ERROR
      }
    }
  }

  /**
   * Handle a TCP client connection, forwarding to UDP.
   */
  private suspend fun handleTunnelClient(tcpSocket: Socket) = withContext(Dispatchers.IO) {
    val udpSocket = punchSocket ?: return@withContext
    val remote = remoteAddress ?: return@withContext

    try {
      val tcpInput = tcpSocket.getInputStream()
      val tcpOutput = tcpSocket.getOutputStream()

      // Read from TCP, send over UDP
      val readJob = launch {
        val buffer = ByteArray(MAX_UDP_PAYLOAD)
        while (isActive && tcpSocket.isConnected) {
          val bytesRead = tcpInput.read(buffer)
          if (bytesRead <= 0) break

          val dataPacket = holePuncher.buildDataPacket(sessionId, buffer.copyOf(bytesRead))
          udpSocket.send(DatagramPacket(dataPacket, dataPacket.size, remote))
          _bytesTransferred.value += bytesRead
        }
      }

      // Read from UDP, write to TCP
      val writeJob = launch {
        val buffer = ByteArray(MAX_UDP_PAYLOAD + 100)
        while (isActive && tcpSocket.isConnected) {
          try {
            val packet = DatagramPacket(buffer, buffer.size)
            udpSocket.receive(packet)

            val data = holePuncher.extractData(buffer.copyOf(packet.length), sessionId)
            if (data != null && data.isNotEmpty()) {
              tcpOutput.write(data)
              tcpOutput.flush()
              _bytesTransferred.value += data.size
            }
          } catch (e: SocketTimeoutException) {
            // Normal timeout, continue
          }
        }
      }

      // Wait for either to complete
      readJob.join()
      writeJob.cancel()
    } catch (e: Exception) {
      Log.w(TAG, "Tunnel client error: ${e.message}")
    } finally {
      tcpSocket.close()
    }
  }

  /**
   * Get the local tunnel endpoint for ADB connection.
   * Returns "localhost:PORT" that can be used with `adb connect`.
   */
  fun getTunnelEndpoint(): String {
    return "localhost:$tunnelPort"
  }

  /**
   * Check if connected.
   */
  fun isConnected(): Boolean {
    return _state.value == ConnectionState.CONNECTED &&
      punchSocket != null &&
      !punchSocket!!.isClosed
  }

  /**
   * Disconnect and cleanup.
   */
  fun disconnect() {
    Log.i(TAG, "Disconnecting P2P connection")

    keepaliveJob?.cancel()
    tunnelJob?.cancel()

    tunnelServer?.close()
    punchSocket?.close()

    tunnelServer = null
    punchSocket = null
    remoteAddress = null
    stunResult = null

    _state.value = ConnectionState.DISCONNECTED
    _bytesTransferred.value = 0
  }

  /**
   * Clean up when done.
   */
  fun destroy() {
    disconnect()
    scope.cancel()
  }

  // Utility extensions
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
}
