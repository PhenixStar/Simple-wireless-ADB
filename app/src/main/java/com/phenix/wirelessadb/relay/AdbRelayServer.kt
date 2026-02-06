package com.phenix.wirelessadb.relay

import android.content.Context
import android.util.Log
import com.phenix.wirelessadb.model.TrustedDevice
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import java.net.InetSocketAddress

/**
 * ADB Relay Server that bridges remote Tailscale connections to local ADB.
 * Only accepts connections from Tailscale network (100.x.x.x).
 * First-time connections require approval, then remembered.
 */
class AdbRelayServer(
  private val context: Context,
  private val relayPort: Int = DEFAULT_RELAY_PORT,
  private val adbPort: Int = DEFAULT_ADB_PORT,
  private val onPendingAuth: ((String) -> Unit)? = null,
  private val onConnectionEstablished: ((String) -> Unit)? = null,
  private val onConnectionClosed: ((String) -> Unit)? = null
) {

  private val authManager = DeviceAuthManager(context)
  private var serverJob: Job? = null
  private var serverSocket: ServerSocket? = null
  private var selectorManager: SelectorManager? = null
  private val pendingConnections = mutableMapOf<String, Socket>()
  private val activeConnections = mutableSetOf<String>()

  val isRunning: Boolean
    get() = serverJob?.isActive == true

  val trustedDeviceCount: Int
    get() = authManager.getTrustedDeviceCount()

  val pendingApprovalIp: String?
    get() = pendingConnections.keys.firstOrNull()

  /**
   * Start the relay server.
   */
  suspend fun start() = withContext(Dispatchers.IO) {
    if (isRunning) {
      Log.w(TAG, "Relay server already running")
      return@withContext
    }

    try {
      selectorManager = SelectorManager(Dispatchers.IO)
      serverSocket = aSocket(selectorManager!!).tcp().bind("0.0.0.0", relayPort)
      Log.i(TAG, "Relay server started on port $relayPort")

      serverJob = CoroutineScope(Dispatchers.IO).launch {
        try {
          while (isActive) {
            try {
              val clientSocket = serverSocket?.accept() ?: break
              launch { handleConnection(clientSocket) }
            } catch (e: CancellationException) {
              break
            } catch (e: Exception) {
              Log.e(TAG, "Error accepting connection: ${e.message}", e)
            }
          }
        } finally {
          // Clean up resources when server job completes
          try {
            serverSocket?.close()
          } catch (_: Exception) {
            // Already closed or error - ignore
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start relay server on port $relayPort: ${e.message}", e)
      // Clean up on failure
      try {
        selectorManager?.close()
      } catch (_: Exception) {
        // Already closed or error - ignore
      }
      selectorManager = null
      throw e
    }
  }

  /**
   * Handle an incoming connection.
   * Ensures client socket is closed even if an exception occurs.
   */
  private suspend fun handleConnection(client: Socket) {
    val remoteAddress = client.remoteAddress
    val clientIp = remoteAddress.toString()
      .substringAfter("/")
      .substringBefore(":")
      .ifEmpty { "unknown" }

    Log.i(TAG, "Connection from: $clientIp")

    try {
      // Only accept Tailscale connections
      if (!TailscaleHelper.isFromTailscaleNetwork(clientIp)) {
        Log.w(TAG, "Rejected non-Tailscale connection from: $clientIp")
        return
      }

      // Check if trusted
      if (authManager.isDeviceTrusted(clientIp)) {
        // Trusted device - auto-connect
        Log.i(TAG, "Trusted device connected: $clientIp")
        authManager.updateLastSeen(clientIp)
        activeConnections.add(clientIp)
        onConnectionEstablished?.invoke(clientIp)
        try {
          bridgeToAdb(client, clientIp)
        } finally {
          activeConnections.remove(clientIp)
          onConnectionClosed?.invoke(clientIp)
        }
      } else {
        // New device - require approval
        Log.i(TAG, "New device requesting approval: $clientIp")
        pendingConnections[clientIp] = client
        onPendingAuth?.invoke(clientIp)

        // Wait for approval (60 second timeout)
        val approved = waitForApproval(clientIp)

        pendingConnections.remove(clientIp)

        if (approved) {
          Log.i(TAG, "Device approved: $clientIp")
          activeConnections.add(clientIp)
          onConnectionEstablished?.invoke(clientIp)
          try {
            bridgeToAdb(client, clientIp)
          } finally {
            activeConnections.remove(clientIp)
            onConnectionClosed?.invoke(clientIp)
          }
        } else {
          Log.w(TAG, "Device not approved, closing: $clientIp")
        }
      }
    } catch (e: CancellationException) {
      // Coroutine cancelled - rethrow and let cleanup happen in finally
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Error handling connection from $clientIp: ${e.message}", e)
    } finally {
      // Ensure client socket is always closed
      // ConnectionProxy will handle both sockets if bridgeToAdb() was called
      // Otherwise we need to close the client socket here
      try { client.close() } catch (_: Exception) {}
    }
  }

  /**
   * Wait for device approval with timeout.
   */
  private suspend fun waitForApproval(clientIp: String): Boolean {
    return withTimeoutOrNull(APPROVAL_TIMEOUT_MS) {
      while (!authManager.isDeviceTrusted(clientIp)) {
        delay(500)
      }
      true
    } ?: false
  }

  /**
   * Bridge the client socket to local ADB.
   * ConnectionProxy takes ownership of both sockets and handles cleanup.
   */
  private suspend fun bridgeToAdb(client: Socket, clientIp: String) {
    try {
      // Use the shared selectorManager instead of creating a new one for each connection
      val selector = selectorManager ?: throw IllegalStateException("Server not started")
      val adbSocket = aSocket(selector).tcp().connect("127.0.0.1", adbPort)
      Log.i(TAG, "Bridging $clientIp to ADB on port $adbPort")
      // ConnectionProxy.start() handles closing both sockets in its finally block
      ConnectionProxy(client, adbSocket).start()
    } catch (e: CancellationException) {
      // Coroutine cancelled - rethrow to allow proper cleanup
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to bridge to ADB for $clientIp: ${e.message}", e)
      // No need to close sockets here - either:
      // 1. ConnectionProxy was created and will handle cleanup in its finally block
      // 2. ConnectionProxy was not created, handleConnection's finally will close client socket
      throw e
    }
  }

  /**
   * Approve a pending device.
   */
  fun approveDevice(clientIp: String, name: String? = null) {
    authManager.addTrustedDevice(clientIp, name)
  }

  /**
   * Deny a pending device.
   */
  fun denyDevice(clientIp: String) {
    pendingConnections[clientIp]?.close()
    pendingConnections.remove(clientIp)
  }

  /**
   * Remove a trusted device.
   */
  fun removeTrustedDevice(clientIp: String) {
    authManager.removeTrustedDevice(clientIp)
  }

  /**
   * Get all trusted devices.
   */
  fun getTrustedDevices(): List<TrustedDevice> {
    return authManager.getTrustedDevices()
  }

  /**
   * Stop the relay server.
   * Ensures all resources are properly cleaned up.
   */
  fun stop() {
    // Cancel the server job first
    serverJob?.cancel()
    serverJob = null

    // Close all pending connections
    pendingConnections.values.forEach {
      try { it.close() } catch (_: Exception) {}
    }
    pendingConnections.clear()
    activeConnections.clear()

    // Close server socket if not already closed by job cancellation
    // The serverJob's finally block may also close it, so ignore errors
    try {
      serverSocket?.close()
    } catch (_: Exception) {
      // Already closed or error during close - ignore
    }
    serverSocket = null

    // Close selector manager
    try {
      selectorManager?.close()
    } catch (_: Exception) {
      // Already closed or error during close - ignore
    }
    selectorManager = null

    Log.i(TAG, "Relay server stopped")
  }

  companion object {
    private const val TAG = "AdbRelayServer"
    const val DEFAULT_RELAY_PORT = 5556
    const val DEFAULT_ADB_PORT = 5555
    private const val APPROVAL_TIMEOUT_MS = 60_000L
  }
}
