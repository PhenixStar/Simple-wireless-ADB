package com.phenix.wirelessadb.warpgate

import android.content.Context
import android.util.Log
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties

/**
 * Manages SSH tunnel connections through Warpgate bastion host.
 *
 * Warpgate acts as a transparent SSH bastion that allows ADB connections
 * to be tunneled securely over the internet.
 *
 * Architecture:
 * 1. App connects to Warpgate via SSH
 * 2. Creates local port forward: localhost:5557 -> device:5555
 * 3. User connects: adb connect localhost:5557
 * 4. Traffic flows: PC -> Warpgate -> Device ADB
 */
object WarpgateManager {

  private const val TAG = "WarpgateManager"
  private const val CONNECT_TIMEOUT_MS = 15000
  private const val SESSION_TIMEOUT_MS = 0 // No timeout for session
  private const val KNOWN_HOSTS_FILE = "warpgate_known_hosts"

  private var sshSession: Session? = null
  private var currentConfig: WarpgateConfig? = null

  /**
   * Current connection status.
   */
  var isConnected: Boolean = false
    private set

  /**
   * Connect to Warpgate and establish SSH tunnel.
   *
   * @param config Warpgate configuration
   * @return Result indicating success or failure
   */
  suspend fun connect(context: Context, config: WarpgateConfig): Result<Unit> = withContext(Dispatchers.IO) {
    if (!config.isValid()) {
      return@withContext Result.failure(Exception("Invalid Warpgate configuration"))
    }

    // Disconnect existing session if any
    disconnect()

    try {
      Log.i(TAG, "Connecting to Warpgate: ${config.host}:${config.port}")

      val jsch = JSch()

      // Trust-on-first-use host key verification: remember the bastion's key
      // on first connect, reject if it changes later (MITM protection)
      val knownHosts = File(context.filesDir, KNOWN_HOSTS_FILE)
      if (!knownHosts.exists()) knownHosts.createNewFile()
      jsch.setKnownHosts(knownHosts.absolutePath)

      val session = jsch.getSession(
        config.getFullUsername(),
        config.host,
        config.port
      )

      // Set password authentication
      if (config.password.isNotEmpty()) {
        session.setPassword(config.password)
      }

      // Configure SSH session
      val sshConfig = Properties().apply {
        put("StrictHostKeyChecking", "accept-new")
        put("PreferredAuthentications", "password,publickey,keyboard-interactive")
      }
      session.setConfig(sshConfig)
      session.timeout = SESSION_TIMEOUT_MS

      // Connect
      session.connect(CONNECT_TIMEOUT_MS)

      if (!session.isConnected) {
        return@withContext Result.failure(Exception("SSH connection failed"))
      }

      // Create local port forwarding
      // Forward localhost:localPort to localhost:5555 (through Warpgate to target device)
      val localPort = session.setPortForwardingL(
        config.localPort,
        "127.0.0.1",
        5555 // Target device's ADB port
      )

      Log.i(TAG, "SSH tunnel established on port $localPort")

      sshSession = session
      currentConfig = config
      isConnected = true

      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to connect to Warpgate: ${e.message}")
      isConnected = false
      Result.failure(e)
    }
  }

  /**
   * Disconnect from Warpgate and close SSH tunnel.
   */
  fun disconnect() {
    try {
      sshSession?.let { session ->
        if (session.isConnected) {
          // Remove port forwarding
          currentConfig?.let { config ->
            try {
              session.delPortForwardingL(config.localPort)
            } catch (e: Exception) {
              Log.w(TAG, "Failed to remove port forwarding: ${e.message}")
            }
          }
          session.disconnect()
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error during disconnect: ${e.message}")
    } finally {
      sshSession = null
      currentConfig = null
      isConnected = false
      Log.i(TAG, "Disconnected from Warpgate")
    }
  }

  /**
   * Check if tunnel is still alive.
   */
  fun checkConnection(): Boolean {
    val session = sshSession ?: return false
    isConnected = session.isConnected
    return isConnected
  }

  /**
   * Get the ADB connect command for the current tunnel.
   */
  fun getAdbCommand(): String? {
    return if (isConnected && currentConfig != null) {
      currentConfig?.getAdbCommand()
    } else {
      null
    }
  }

  /**
   * Get current configuration.
   */
  fun getCurrentConfig(): WarpgateConfig? = currentConfig
}
