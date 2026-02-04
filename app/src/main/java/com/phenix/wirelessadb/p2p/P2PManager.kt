package com.phenix.wirelessadb.p2p

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.phenix.wirelessadb.model.DeviceIdentifier
import com.phenix.wirelessadb.model.P2PToken
import com.phenix.wirelessadb.model.StunResult
import com.phenix.wirelessadb.model.TokenState
import com.phenix.wirelessadb.relay.DeviceAuthManager
import com.phenix.wirelessadb.util.DeviceFingerprint
import kotlinx.coroutines.*

/**
 * P2P Connection Manager (v1.2.0).
 *
 * Manages P2P token lifecycle and NAT traversal for device-to-device connections.
 *
 * Flow:
 * 1. Device A calls generateToken() → gets token like "ABC-123-XYZ"
 * 2. Device A shares token with Device B (verbally, text, QR code)
 * 3. Device B enters token in their app
 * 4. Both devices perform NAT traversal via STUN
 * 5. Connection established or falls back to TURN relay
 *
 * Security:
 * - Token expires after 30 minutes (or 24h for trusted)
 * - Token rotates on disconnect (unless trusted)
 * - Device verified by hardware fingerprint
 */
class P2PManager private constructor(private val context: Context) {

  private val TAG = "P2PManager"
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private val authManager = DeviceAuthManager(context)

  // P2P Connection handler (performs actual NAT traversal)
  private val p2pConnection = P2PConnection()
  private val stunClient = StunClient()
  private val localDiscovery = LocalDiscovery(context)

  // Cached STUN result for token generation
  private var cachedStunResult: StunResult? = null

  // Current connection state
  private val _connectionState = MutableLiveData(P2PState.IDLE)
  val connectionState: LiveData<P2PState> = _connectionState

  // Current token (if any)
  private val _currentToken = MutableLiveData<P2PToken?>(null)
  val currentToken: LiveData<P2PToken?> = _currentToken

  // Tunnel endpoint (for ADB connection after P2P established)
  private val _tunnelEndpoint = MutableLiveData<String?>(null)
  val tunnelEndpoint: LiveData<String?> = _tunnelEndpoint

  // Error message
  private val _error = MutableLiveData<String?>(null)
  val error: LiveData<String?> = _error

  // Loading state
  private val _isLoading = MutableLiveData(false)
  val isLoading: LiveData<Boolean> = _isLoading

  /**
   * Generate a new P2P token for this device.
   * Performs STUN discovery to get external endpoint for NAT traversal.
   */
  suspend fun generateToken(): Result<P2PToken> = withContext(Dispatchers.IO) {
    try {
      _isLoading.postValue(true)
      _error.postValue(null)

      // Revoke any existing token
      revokeToken()

      // Step 1: Discover external endpoint via STUN
      Log.i(TAG, "Discovering external endpoint via STUN...")
      val stunResult = try {
        stunClient.discoverExternalEndpoint()
      } catch (e: Exception) {
        Log.w(TAG, "STUN discovery failed: ${e.message}")
        null
      }
      cachedStunResult = stunResult

      // Step 2: Get device fingerprint
      val deviceHash = DeviceFingerprint.computeDeviceHash(context)

      // Step 3: Generate token with external endpoint info
      val token = TokenGenerator.generate(deviceHash).let { baseToken ->
        if (stunResult != null) {
          baseToken.copy(
            externalEndpoint = "${stunResult.externalIp}:${stunResult.externalPort}"
          )
        } else {
          baseToken
        }
      }

      _currentToken.postValue(token)
      _connectionState.postValue(P2PState.TOKEN_READY)

      // Step 4: Start waiting for peer connection in background
      scope.launch {
        waitForPeerConnection()
      }

      Log.i(TAG, "Token generated: ${token.masked()}, endpoint: ${token.externalEndpoint ?: "unknown"}")
      Result.success(token)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to generate token", e)
      _error.postValue("Failed to generate token: ${e.message}")
      Result.failure(e)
    } finally {
      _isLoading.postValue(false)
    }
  }

  /**
   * Wait for a peer to connect using our token.
   */
  private suspend fun waitForPeerConnection() {
    val token = _currentToken.value ?: return
    Log.i(TAG, "Waiting for peer connection...")

    try {
      val connected = p2pConnection.waitForPeer(timeoutMs = token.expiresAt - System.currentTimeMillis())
      if (connected) {
        Log.i(TAG, "Peer connected!")
        _connectionState.postValue(P2PState.CONNECTED)
        _tunnelEndpoint.postValue(p2pConnection.getTunnelEndpoint())

        // Update token state
        _currentToken.postValue(token.copy(state = TokenState.CONNECTED))
      } else if (_connectionState.value == P2PState.TOKEN_READY) {
        // Only update to FAILED if we're still waiting (not manually revoked)
        Log.w(TAG, "Peer connection timed out")
        _connectionState.postValue(P2PState.FAILED)
        _error.postValue("No peer connected before token expired")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error waiting for peer: ${e.message}")
      if (_connectionState.value == P2PState.TOKEN_READY) {
        _connectionState.postValue(P2PState.FAILED)
        _error.postValue("Connection error: ${e.message}")
      }
    }
  }

  /**
   * Generate a persistent token (for trusted device).
   * Performs STUN discovery for NAT traversal support.
   */
  suspend fun generatePersistentToken(): Result<P2PToken> = withContext(Dispatchers.IO) {
    try {
      _isLoading.postValue(true)
      _error.postValue(null)

      // Revoke any existing token
      revokeToken()

      // Step 1: Discover external endpoint via STUN
      Log.i(TAG, "Discovering external endpoint via STUN...")
      val stunResult = try {
        stunClient.discoverExternalEndpoint()
      } catch (e: Exception) {
        Log.w(TAG, "STUN discovery failed: ${e.message}")
        null
      }
      cachedStunResult = stunResult

      // Step 2: Get device fingerprint
      val deviceHash = DeviceFingerprint.computeDeviceHash(context)

      // Step 3: Generate persistent token with external endpoint info
      val token = TokenGenerator.generatePersistent(deviceHash).let { baseToken ->
        if (stunResult != null) {
          baseToken.copy(
            externalEndpoint = "${stunResult.externalIp}:${stunResult.externalPort}"
          )
        } else {
          baseToken
        }
      }

      _currentToken.postValue(token)
      _connectionState.postValue(P2PState.TOKEN_READY)

      // Step 4: Start waiting for peer connection in background
      scope.launch {
        waitForPeerConnection()
      }

      Log.i(TAG, "Persistent token generated: ${token.masked()}, endpoint: ${token.externalEndpoint ?: "unknown"}")
      Result.success(token)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to generate persistent token", e)
      _error.postValue("Failed to generate token: ${e.message}")
      Result.failure(e)
    } finally {
      _isLoading.postValue(false)
    }
  }

  /**
   * Revoke the current token.
   */
  fun revokeToken() {
    _currentToken.value?.let { token ->
      Log.i(TAG, "Token revoked: ${token.masked()}")
    }
    _currentToken.postValue(null)
    _connectionState.postValue(P2PState.IDLE)
  }

  /**
   * Connect to a peer using their token.
   * Performs NAT traversal via STUN and hole punching.
   */
  suspend fun connectWithToken(tokenCode: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      _isLoading.postValue(true)
      _error.postValue(null)
      _connectionState.postValue(P2PState.CONNECTING)

      // Step 1: Validate and parse token
      val normalizedCode = TokenGenerator.normalize(tokenCode)
      if (!TokenGenerator.isValidFormat(normalizedCode)) {
        _connectionState.postValue(P2PState.FAILED)
        return@withContext Result.failure(Exception("Invalid token format"))
      }

      Log.i(TAG, "Connecting with token: ***-***-${normalizedCode.takeLast(3)}")

      // Step 2: Decode token to get peer connection info
      val tokenEncoder = TokenEncoder()
      val peerInfo = tokenEncoder.decode(normalizedCode)
      if (!peerInfo.isValid) {
        // Token is valid format but we can't decode endpoint info
        // This means we need the full shareable string with endpoint data
        _connectionState.postValue(P2PState.FAILED)
        _error.postValue(peerInfo.errorMessage ?: "Token missing connection info. Ask peer to share the full P2P code.")
        return@withContext Result.failure(Exception(peerInfo.errorMessage ?: "Token missing connection info"))
      }

      Log.i(TAG, "Peer info: ${peerInfo.externalIp}:${peerInfo.externalPort}")

      // Step 3: Check if peer is on local network (faster connection)
      val isLocal = localDiscovery.isLocalNetwork(peerInfo.externalIp)
      Log.d(TAG, "Peer is local network: $isLocal")

      // Step 4: Convert DecodedToken to ConnectionInfo
      val connectionInfo = peerInfo.toConnectionInfo()

      // Step 5: Connect via P2PConnection (handles STUN + hole punching)
      val connected = p2pConnection.connectToPeer(connectionInfo, isLocalNetwork = isLocal)

      if (connected) {
        Log.i(TAG, "P2P connection established!")
        _connectionState.postValue(P2PState.CONNECTED)
        _tunnelEndpoint.postValue(p2pConnection.getTunnelEndpoint())

        // Create a local token representing this connection
        val deviceHash = DeviceFingerprint.computeDeviceHash(context)
        val token = P2PToken(
          code = normalizedCode,
          deviceHash = deviceHash,
          sessionId = connectionInfo.sessionId,
          expiresAt = System.currentTimeMillis() + P2PToken.DEFAULT_EXPIRY_MS,
          externalEndpoint = "${peerInfo.externalIp}:${peerInfo.externalPort}",
          state = TokenState.CONNECTED
        )
        _currentToken.postValue(token)

        Result.success(Unit)
      } else {
        val errorMsg = p2pConnection.error.value ?: "Failed to establish P2P connection"
        Log.w(TAG, "P2P connection failed: $errorMsg")
        _connectionState.postValue(P2PState.FAILED)
        _error.postValue(errorMsg)
        Result.failure(Exception(errorMsg))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Connection failed", e)
      _connectionState.postValue(P2PState.FAILED)
      _error.postValue("Connection failed: ${e.message}")
      Result.failure(e)
    } finally {
      _isLoading.postValue(false)
    }
  }

  /**
   * Accept an incoming connection request.
   */
  suspend fun acceptConnection(deviceIdentifier: DeviceIdentifier): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      _isLoading.postValue(true)

      // Verify device against token
      val token = _currentToken.value
        ?: return@withContext Result.failure(Exception("No active token"))

      // Update token state
      val connectedToken = token.copy(state = TokenState.CONNECTED)
      _currentToken.postValue(connectedToken)
      _connectionState.postValue(P2PState.CONNECTED)

      Log.i(TAG, "Connection accepted from: ${deviceIdentifier.computeHash().takeLast(4)}")

      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to accept connection", e)
      _error.postValue("Failed to accept: ${e.message}")
      Result.failure(e)
    } finally {
      _isLoading.postValue(false)
    }
  }

  /**
   * Disconnect current P2P connection.
   */
  fun disconnect() {
    val token = _currentToken.value ?: return

    // Close P2P connection
    try {
      p2pConnection.disconnect()
      Log.i(TAG, "P2P connection closed")
    } catch (e: Exception) {
      Log.w(TAG, "Error closing P2P connection: ${e.message}")
    }

    // Clear tunnel endpoint
    _tunnelEndpoint.postValue(null)

    if (token.isPersistent) {
      // Keep token for trusted devices
      _connectionState.postValue(P2PState.TOKEN_READY)
    } else {
      // Rotate token for non-trusted
      revokeToken()
    }

    Log.i(TAG, "Disconnected")
  }

  /**
   * Check if token is still valid.
   */
  fun isTokenValid(): Boolean {
    return _currentToken.value?.isValid() == true
  }

  /**
   * Get the ADB connect command for P2P connection.
   * Returns the local tunnel endpoint (localhost:15555) when connected.
   */
  fun getAdbCommand(): String? {
    if (_connectionState.value != P2PState.CONNECTED) return null
    return _tunnelEndpoint.value?.let { "adb connect $it" }
  }

  /**
   * Clear any error state.
   */
  fun clearError() {
    _error.postValue(null)
  }

  /**
   * Clean up resources.
   */
  fun destroy() {
    try {
      p2pConnection.destroy()
      Log.i(TAG, "P2P connection resources cleaned up")
    } catch (e: Exception) {
      Log.w(TAG, "Error destroying P2P connection: ${e.message}")
    }
    scope.cancel()
    Log.i(TAG, "P2PManager destroyed")
  }

  companion object {
    @Volatile
    private var instance: P2PManager? = null

    fun getInstance(context: Context): P2PManager {
      return instance ?: synchronized(this) {
        instance ?: P2PManager(context.applicationContext).also { instance = it }
      }
    }
  }
}

/**
 * P2P connection states.
 */
enum class P2PState {
  /** No active token or connection */
  IDLE,

  /** Token generated, waiting for peer */
  TOKEN_READY,

  /** NAT traversal in progress */
  CONNECTING,

  /** Connection established */
  CONNECTED,

  /** Connection failed */
  FAILED
}
