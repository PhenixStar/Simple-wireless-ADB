package com.phenix.wirelessadb

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import android.net.NetworkCapabilities
import android.os.Build
import com.phenix.wirelessadb.relay.TailscaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

object AdbManager {

  private const val TAG = "AdbManager"
  private const val DEFAULT_PORT = 5555
  private const val MIN_PORT = 1024
  private const val MAX_PORT = 65535

  data class AdbStatus(
    val enabled: Boolean,
    val port: Int,
    val ip: String?,
    val tailscaleIp: String? = null,
    val relayEnabled: Boolean = false,
    val relayPort: Int = 5556,
    val trustedDeviceCount: Int = 0,
    val pendingApproval: String? = null
  )

  fun validatePort(port: Int): Boolean = port in MIN_PORT..MAX_PORT

  suspend fun getStatus(context: Context): AdbStatus = withContext(Dispatchers.IO) {
    val port = getCurrentPort()
    val enabled = port > 0
    val ip = if (enabled) getWifiIp(context) else null
    val tailscaleIp = TailscaleHelper.getTailscaleIp(context)
    Log.d("AdbManager", "getStatus: port=$port, enabled=$enabled, ip=$ip, tailscaleIp=$tailscaleIp")
    AdbStatus(
      enabled = enabled,
      port = if (enabled) port else DEFAULT_PORT,
      ip = ip,
      tailscaleIp = tailscaleIp
    )
  }

  suspend fun enable(port: Int = DEFAULT_PORT): Result<Unit> = withContext(Dispatchers.IO) {
    if (!validatePort(port)) {
      return@withContext Result.failure(IllegalArgumentException("Port must be $MIN_PORT-$MAX_PORT"))
    }
    val result = runRootCommand(
      "setprop persist.adb.tcp.port $port",
      "setprop service.adb.tcp.port $port",
      "stop adbd",
      "start adbd"
    )
    if (result.isSuccess) {
      // Verify socket is actually listening
      val verified = verifyAdbSocket(port)
      if (!verified) {
        Log.w(TAG, "enable: ADB socket not listening on port $port after enable, retrying...")
        runRootCommand("stop adbd", "start adbd")
        val retryOk = verifyAdbSocket(port)
        if (!retryOk) {
          Log.e(TAG, "enable: ADB socket still not listening after retry")
        }
      }
    }
    result
  }

  suspend fun disable(): Result<Unit> = withContext(Dispatchers.IO) {
    runRootCommand(
      "setprop persist.adb.tcp.port -1",
      "setprop service.adb.tcp.port -1",
      "stop adbd",
      "start adbd"
    )
  }

  suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
    try {
      val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
      val completed = process.waitFor(5, TimeUnit.SECONDS)
      if (!completed) {
        process.destroyForcibly()
        return@withContext false
      }
      if (process.exitValue() == 0) {
        process.inputStream.bufferedReader().use { reader ->
          val output = reader.readText()
          output.contains("uid=0")
        }
      } else {
        false
      }
    } catch (e: Exception) {
      false
    }
  }

  private fun getCurrentPort(): Int {
    return try {
      // Check ephemeral prop first, then persistent
      val ephemeral = getSystemProp("service.adb.tcp.port")
      if (ephemeral > 0) return ephemeral

      val persistent = getSystemProp("persist.adb.tcp.port")
      Log.d(TAG, "getCurrentPort: ephemeral=$ephemeral, persistent=$persistent")
      persistent
    } catch (e: Exception) {
      Log.e(TAG, "getCurrentPort: ERROR ${e.message}")
      -1
    }
  }

  private fun getSystemProp(prop: String): Int {
    return try {
      val process = Runtime.getRuntime().exec(arrayOf("getprop", prop))
      val completed = process.waitFor(3, TimeUnit.SECONDS)
      if (!completed) {
        process.destroyForcibly()
        Log.e(TAG, "getSystemProp($prop): TIMEOUT")
        return -1
      }
      val result = process.inputStream.bufferedReader().use { reader ->
        reader.readLine()?.trim()?.toIntOrNull() ?: -1
      }
      result
    } catch (e: Exception) {
      Log.e(TAG, "getSystemProp($prop): ERROR ${e.message}")
      -1
    }
  }

  /**
   * Verify ADB is actually listening on the given TCP port.
   * Polls up to 3 times with 500ms delay.
   */
  private fun verifyAdbSocket(port: Int, maxRetries: Int = 3): Boolean {
    repeat(maxRetries) { attempt ->
      try {
        val process = Runtime.getRuntime().exec(
          arrayOf("sh", "-c", "cat /proc/net/tcp6 /proc/net/tcp 2>/dev/null | grep -i ':${"%04X".format(port)}'")
        )
        val completed = process.waitFor(3, TimeUnit.SECONDS)
        if (completed) {
          val output = process.inputStream.bufferedReader().readText().trim()
          if (output.isNotEmpty()) {
            Log.d(TAG, "verifyAdbSocket: port $port listening (attempt ${attempt + 1})")
            return true
          }
        } else {
          process.destroyForcibly()
        }
      } catch (e: Exception) {
        Log.e(TAG, "verifyAdbSocket: ERROR ${e.message}")
      }
      if (attempt < maxRetries - 1) Thread.sleep(500)
    }
    Log.w(TAG, "verifyAdbSocket: port $port NOT listening after $maxRetries attempts")
    return false
  }

  /**
   * Get WiFi IP address, excluding VPN/Tailscale IPs.
   * Uses shell command first (reliable when VPN active), API as fallback.
   */
  private fun getWifiIp(context: Context): String? {
    // Method 1: Shell command (PRIMARY - reliable when VPN active)
    val shellResult = getWifiIpFromShell()
    if (shellResult != null) return shellResult

    // Method 2: Fallback to API (may return VPN IP when Tailscale active)
    val apiResult = getWifiIpFromApi(context)
    if (apiResult != null && !isTailscaleIp(apiResult)) return apiResult

    return null
  }

  private fun getWifiIpFromApi(context: Context): String? {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return null

    // Method A: Get ALL networks and find WiFi specifically
    try {
      val allNetworks = cm.allNetworks
      Log.d("AdbManager", "getWifiIpFromApi allNetworks count: ${allNetworks.size}")
      for (network in allNetworks) {
        val caps = cm.getNetworkCapabilities(network) ?: continue
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        Log.d("AdbManager", "getWifiIpFromApi network: wifi=$isWifi, vpn=$isVpn")
        if (isWifi && !isVpn) {
          val linkProps = cm.getLinkProperties(network) ?: continue
          val ip = linkProps.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .firstOrNull { !isTailscaleIp(it) }
          Log.d("AdbManager", "getWifiIpFromApi WiFi network IP: $ip")
          if (ip != null) return ip
        }
      }
    } catch (e: Exception) {
      Log.e("AdbManager", "getWifiIpFromApi allNetworks error: ${e.message}")
    }

    // Method B: Fallback to active network (may be VPN)
    val network = cm.activeNetwork ?: return null
    val caps = cm.getNetworkCapabilities(network) ?: return null
    Log.d("AdbManager", "getWifiIpFromApi active: hasWifi=${caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}")
    if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

    val linkProps = cm.getLinkProperties(network) ?: return null
    val result = linkProps.linkAddresses
      .map { it.address }
      .filterIsInstance<Inet4Address>()
      .mapNotNull { it.hostAddress }
      .firstOrNull { !isTailscaleIp(it) }
    Log.d("AdbManager", "getWifiIpFromApi active result: $result")
    return result
  }

  private fun getWifiIpFromShell(): String? {
    return try {
      val process = Runtime.getRuntime().exec(arrayOf("/system/bin/ip", "addr", "show", "wlan0"))
      val completed = process.waitFor(3, TimeUnit.SECONDS)
      if (!completed) {
        process.destroyForcibly()
        Log.e("AdbManager", "getWifiIpFromShell: TIMEOUT")
        return null
      }
      val output = process.inputStream.bufferedReader().readText()
      Log.d("AdbManager", "getWifiIpFromShell raw output: $output")
      val result = output.lines()
        .map { it.trim() }
        .filter { it.startsWith("inet ") }
        .map { it.substringAfter("inet ").substringBefore("/").trim() }
        .firstOrNull()
      Log.d("AdbManager", "getWifiIpFromShell result: $result")
      result
    } catch (e: Exception) {
      Log.e("AdbManager", "getWifiIpFromShell: ERROR ${e.message}")
      null
    }
  }

  /**
   * Check if IP is in Tailscale's CGNAT range (100.64.0.0/10).
   * This covers 100.64.x.x through 100.127.x.x.
   */
  private fun isTailscaleIp(ip: String?): Boolean {
    if (ip == null) return false
    val parts = ip.split(".")
    if (parts.size != 4) return false
    val first = parts[0].toIntOrNull() ?: return false
    val second = parts[1].toIntOrNull() ?: return false
    return first == 100 && second in 64..127
  }

  private fun runRootCommand(vararg commands: String): Result<Unit> {
    return try {
      Log.d(TAG, "runRootCommand: executing ${commands.size} commands")
      val process = Runtime.getRuntime().exec("su")
      DataOutputStream(process.outputStream).use { os ->
        commands.forEach { cmd ->
          Log.d(TAG, "runRootCommand: $cmd")
          os.writeBytes("$cmd\n")
        }
        os.writeBytes("exit\n")
        os.flush()
      }
      val completed = process.waitFor(10, TimeUnit.SECONDS)
      if (!completed) {
        process.destroyForcibly()
        Log.e(TAG, "runRootCommand: TIMEOUT after 10s")
        return Result.failure(RuntimeException("Root command timed out"))
      }
      val exitCode = process.exitValue()
      if (exitCode != 0) {
        val stderr = process.errorStream.bufferedReader().use { it.readText().trim() }
        Log.e(TAG, "runRootCommand: exit=$exitCode, stderr=$stderr")
        Result.failure(RuntimeException("Root command failed (exit $exitCode): $stderr"))
      } else {
        Log.d(TAG, "runRootCommand: SUCCESS")
        Result.success(Unit)
      }
    } catch (e: Exception) {
      Log.e(TAG, "runRootCommand: EXCEPTION ${e.message}")
      Result.failure(e)
    }
  }
}
