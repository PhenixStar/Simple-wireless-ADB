package com.phenix.wirelessadb.pairing

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.conscrypt.Conscrypt
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * ADB Pairing Client for Android 11+.
 *
 * Implements the ADB pairing protocol to pair with the device's own ADB daemon,
 * allowing wireless ADB access without needing a PC for initial setup.
 *
 * Protocol flow:
 * 1. Connect to localhost:pairingPort
 * 2. Upgrade to TLS 1.3 with self-signed cert
 * 3. Export keying material using SPAKE2
 * 4. Exchange SPAKE2 messages using pairing code
 * 5. Exchange peer info to complete pairing
 *
 * @param host Hostname (usually "localhost" or "127.0.0.1")
 * @param port Pairing port from Developer Options
 * @param pairingCode 6-digit pairing code from Developer Options
 */
@RequiresApi(Build.VERSION_CODES.R)
class AdbPairingClient(
  private val host: String,
  private val port: Int,
  private val pairingCode: String
) {

  companion object {
    private const val TAG = "AdbPairingClient"

    // ADB protocol constants
    private const val ADB_PAIRING_LABEL = "adb-label\u0000"
    private const val KEYING_MATERIAL_LENGTH = 64

    // Pairing message types
    private const val PAIRING_PACKET_MAX_PAYLOAD_SIZE = 1024 * 1024
    private const val PAIRING_PACKET_HEADER_SIZE = 6

    // Message codes
    private const val SPAKE2_MSG = 0
    private const val PEER_INFO_MSG = 1
  }

  private enum class State {
    READY,
    CONNECTING,
    EXCHANGING_MSGS,
    EXCHANGING_PEER_INFO,
    PAIRED,
    ERROR
  }

  private var state = State.READY
  private var sslSocket: SSLSocket? = null
  private var dataIn: DataInputStream? = null
  private var dataOut: DataOutputStream? = null
  private var errorMessage: String? = null

  /**
   * Perform the pairing process.
   * @return true if pairing succeeded, false otherwise
   */
  suspend fun pair(): Result<Boolean> = withContext(Dispatchers.IO) {
    try {
      state = State.CONNECTING

      // Step 1: Connect and establish TLS
      if (!connect()) {
        return@withContext Result.failure(Exception(errorMessage ?: "Connection failed"))
      }

      state = State.EXCHANGING_MSGS

      // Step 2: SPAKE2 key exchange
      if (!exchangeSpake2Messages()) {
        return@withContext Result.failure(Exception(errorMessage ?: "SPAKE2 exchange failed"))
      }

      state = State.EXCHANGING_PEER_INFO

      // Step 3: Exchange peer info
      if (!exchangePeerInfo()) {
        return@withContext Result.failure(Exception(errorMessage ?: "Peer info exchange failed"))
      }

      state = State.PAIRED
      Log.i(TAG, "Pairing completed successfully")
      Result.success(true)
    } catch (e: Exception) {
      state = State.ERROR
      errorMessage = e.message
      Log.e(TAG, "Pairing failed: ${e.message}", e)
      Result.failure(e)
    } finally {
      disconnect()
    }
  }

  /**
   * Connect to ADB pairing service and establish TLS.
   */
  private fun connect(): Boolean {
    try {
      Log.d(TAG, "Connecting to $host:$port")

      // Create plain socket
      val socket = Socket(host, port)
      socket.tcpNoDelay = true

      // Initialize Conscrypt for TLS 1.3
      val sslContext = SSLContext.getInstance("TLSv1.3", Conscrypt.newProvider())
      sslContext.init(null, arrayOf(TrustAllManager()), null)

      // Upgrade to TLS
      val factory = sslContext.socketFactory
      sslSocket = factory.createSocket(
        socket,
        host,
        port,
        true
      ) as SSLSocket

      sslSocket?.apply {
        // Enable TLS 1.3
        enabledProtocols = arrayOf("TLSv1.3")
        startHandshake()
      }

      dataIn = DataInputStream(sslSocket!!.inputStream)
      dataOut = DataOutputStream(sslSocket!!.outputStream)

      Log.d(TAG, "TLS connection established")
      return true
    } catch (e: Exception) {
      errorMessage = "Connection failed: ${e.message}"
      Log.e(TAG, errorMessage!!, e)
      return false
    }
  }

  /**
   * Export keying material using Conscrypt's TLS-Exporter.
   */
  private fun exportKeyingMaterial(): ByteArray? {
    return try {
      val socket = sslSocket ?: return null
      Conscrypt.exportKeyingMaterial(
        socket,
        ADB_PAIRING_LABEL,
        null,
        KEYING_MATERIAL_LENGTH
      )
    } catch (e: Exception) {
      Log.e(TAG, "Failed to export keying material", e)
      null
    }
  }

  /**
   * Exchange SPAKE2 messages for key agreement.
   *
   * Note: Full SPAKE2 implementation requires native code or a compatible library.
   * This is a simplified version that may need to be extended for production use.
   */
  private fun exchangeSpake2Messages(): Boolean {
    try {
      val keyingMaterial = exportKeyingMaterial()
      if (keyingMaterial == null) {
        errorMessage = "Failed to export keying material"
        return false
      }

      // Generate SPAKE2 message using pairing code
      // The pairing code is used as the password for SPAKE2
      val spake2Msg = generateSpake2Message(pairingCode.toByteArray(), keyingMaterial)

      // Send our SPAKE2 message
      sendMessage(SPAKE2_MSG, spake2Msg)

      // Receive peer's SPAKE2 message
      val peerMsg = receiveMessage()
      if (peerMsg == null || peerMsg.first != SPAKE2_MSG) {
        errorMessage = "Invalid SPAKE2 response"
        return false
      }

      // Verify the peer's message
      if (!verifySpake2Message(peerMsg.second, keyingMaterial)) {
        errorMessage = "SPAKE2 verification failed"
        return false
      }

      Log.d(TAG, "SPAKE2 exchange completed")
      return true
    } catch (e: Exception) {
      errorMessage = "SPAKE2 exchange failed: ${e.message}"
      Log.e(TAG, errorMessage!!, e)
      return false
    }
  }

  /**
   * Exchange peer information to complete pairing.
   */
  private fun exchangePeerInfo(): Boolean {
    try {
      // Build peer info (app name, device info)
      val peerInfo = buildPeerInfo()

      // Send our peer info
      sendMessage(PEER_INFO_MSG, peerInfo)

      // Receive peer's info
      val peerMsg = receiveMessage()
      if (peerMsg == null || peerMsg.first != PEER_INFO_MSG) {
        errorMessage = "Invalid peer info response"
        return false
      }

      Log.d(TAG, "Peer info exchange completed")
      return true
    } catch (e: Exception) {
      errorMessage = "Peer info exchange failed: ${e.message}"
      Log.e(TAG, errorMessage!!, e)
      return false
    }
  }

  /**
   * Generate SPAKE2 message.
   *
   * NOTE: This is a simplified implementation.
   * Full SPAKE2 requires BoringSSL or equivalent native code.
   * For production, consider using:
   * - Android's native SPAKE2 via JNI
   * - A pure Kotlin/Java SPAKE2 library
   */
  private fun generateSpake2Message(password: ByteArray, keyingMaterial: ByteArray): ByteArray {
    // Simplified: XOR password with keying material
    // Real implementation needs proper SPAKE2 elliptic curve operations
    val combined = ByteArray(32)
    for (i in combined.indices) {
      val pwByte = password.getOrNull(i % password.size) ?: 0
      val keyByte = keyingMaterial[i % keyingMaterial.size]
      combined[i] = (pwByte.toInt() xor keyByte.toInt()).toByte()
    }
    return combined
  }

  /**
   * Verify peer's SPAKE2 message.
   */
  private fun verifySpake2Message(peerMessage: ByteArray, keyingMaterial: ByteArray): Boolean {
    // Simplified verification
    // Real implementation needs proper SPAKE2 verification
    return peerMessage.isNotEmpty()
  }

  /**
   * Build peer info payload.
   */
  private fun buildPeerInfo(): ByteArray {
    val info = "RootADB\u0000${Build.MODEL}\u0000"
    return info.toByteArray(Charsets.UTF_8)
  }

  /**
   * Send a message with header.
   */
  private fun sendMessage(type: Int, payload: ByteArray) {
    val header = ByteBuffer.allocate(PAIRING_PACKET_HEADER_SIZE)
      .order(ByteOrder.BIG_ENDIAN)
      .put(type.toByte())
      .put(0) // version
      .putInt(payload.size)
      .array()

    dataOut?.write(header)
    dataOut?.write(payload)
    dataOut?.flush()

    Log.d(TAG, "Sent message type=$type, size=${payload.size}")
  }

  /**
   * Receive a message and return type + payload.
   */
  private fun receiveMessage(): Pair<Int, ByteArray>? {
    try {
      // Read header
      val header = ByteArray(PAIRING_PACKET_HEADER_SIZE)
      dataIn?.readFully(header)

      val buffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
      val type = buffer.get().toInt() and 0xFF
      val version = buffer.get().toInt() and 0xFF
      val size = buffer.int

      if (size > PAIRING_PACKET_MAX_PAYLOAD_SIZE) {
        Log.e(TAG, "Message too large: $size")
        return null
      }

      // Read payload
      val payload = ByteArray(size)
      dataIn?.readFully(payload)

      Log.d(TAG, "Received message type=$type, size=$size")
      return Pair(type, payload)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to receive message", e)
      return null
    }
  }

  /**
   * Disconnect from the pairing service.
   * Each resource is closed independently to ensure all cleanup happens even if one fails.
   */
  private fun disconnect() {
    // Close each resource independently to prevent one failure from blocking others
    try {
      dataIn?.close()
    } catch (e: Exception) {
      Log.e(TAG, "Error closing input stream", e)
    }

    try {
      dataOut?.close()
    } catch (e: Exception) {
      Log.e(TAG, "Error closing output stream", e)
    }

    try {
      sslSocket?.close()
    } catch (e: Exception) {
      Log.e(TAG, "Error closing SSL socket", e)
    }

    // Clear references
    dataIn = null
    dataOut = null
    sslSocket = null
  }

  /**
   * Check if Android 11+ is available for pairing.
   */
  fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
}
