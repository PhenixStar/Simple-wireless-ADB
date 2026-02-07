package com.phenix.wirelessadb.relay

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.widget.Toast
import android.net.NetworkCapabilities
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/**
 * Helper for detecting Tailscale network presence and IPs.
 * Tailscale uses the CGNAT range 100.64.0.0/10 (100.64.x.x - 100.127.x.x)
 */
object TailscaleHelper {

  // Tailscale uses CGNAT range starting with 100.
  private const val TAILSCALE_PREFIX = "100."
  private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
  private const val TAILSCALE_PLAY_STORE = "https://play.google.com/store/apps/details?id=com.tailscale.ipn"

  /**
   * Get the device's Tailscale IP address if connected.
   * Uses ConnectivityManager API (most reliable on modern Android).
   * @return Tailscale IP (100.x.x.x) or null if not connected
   */
  fun getTailscaleIp(context: Context? = null): String? {
    // Method 1: ConnectivityManager API (PRIMARY - works on modern Android)
    if (context != null) {
      val apiResult = getTailscaleIpFromApi(context)
      if (apiResult != null) return apiResult
    }

    // Method 2: Shell command (fallback)
    val shellResult = getTailscaleIpFromShell()
    if (shellResult != null) return shellResult

    // Method 3: NetworkInterface API (last resort)
    return getTailscaleIpFromNetworkInterface()
  }

  /**
   * Get Tailscale IP using ConnectivityManager API.
   * This is the most reliable method on modern Android.
   */
  @Suppress("DEPRECATION")
  private fun getTailscaleIpFromApi(context: Context): String? {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return null

    return try {
      val allNetworks = cm.allNetworks
      Log.d("TailscaleHelper", "getTailscaleIpFromApi allNetworks count: ${allNetworks.size}")
      for (network in allNetworks) {
        val caps = cm.getNetworkCapabilities(network) ?: continue
        val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        Log.d("TailscaleHelper", "getTailscaleIpFromApi network: vpn=$isVpn")
        if (isVpn) {
          val linkProps = cm.getLinkProperties(network) ?: continue
          val ip = linkProps.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .firstOrNull { isTailscaleIp(it) }
          Log.d("TailscaleHelper", "getTailscaleIpFromApi VPN network IP: $ip")
          if (ip != null) return ip
        }
      }
      null
    } catch (e: Exception) {
      Log.e("TailscaleHelper", "getTailscaleIpFromApi error: ${e.message}")
      null
    }
  }

  /**
   * Try to get Tailscale IP using Java's NetworkInterface API.
   * This method may not work on all Android devices for TUN interfaces.
   */
  private fun getTailscaleIpFromNetworkInterface(): String? {
    return try {
      NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
        if (!iface.isUp || iface.isLoopback) return@forEach

        iface.inetAddresses?.toList()?.forEach { addr ->
          if (addr is Inet4Address) {
            val ip = addr.hostAddress ?: return@forEach
            if (isTailscaleIp(ip)) {
              return ip
            }
          }
        }
      }
      null
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Get Tailscale IP by parsing shell command output.
   * This method is more reliable for TUN/VPN interfaces on Android.
   */
  private fun getTailscaleIpFromShell(): String? {
    return try {
      val process = Runtime.getRuntime().exec(arrayOf("/system/bin/ip", "addr", "show", "tun0"))
      val completed = process.waitFor(3, TimeUnit.SECONDS)
      if (!completed) {
        process.destroyForcibly()
        return null
      }
      process.inputStream.bufferedReader().use { reader ->
        reader.lineSequence()
          .map { it.trim() }
          .filter { it.startsWith("inet ") }
          .map { it.substringAfter("inet ").substringBefore("/").trim() }
          .filter { isTailscaleIp(it) }
          .firstOrNull()
      }
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Check if an IP address is in the Tailscale range.
   * @param ip IP address to check
   * @return true if IP is in Tailscale CGNAT range (100.64.0.0/10)
   */
  fun isTailscaleIp(ip: String): Boolean {
    if (!ip.startsWith(TAILSCALE_PREFIX)) return false

    // Verify it's in the CGNAT range (100.64.0.0 - 100.127.255.255)
    return try {
      val parts = ip.split(".")
      if (parts.size != 4) return false
      val secondOctet = parts[1].toInt()
      secondOctet in 64..127
    } catch (e: NumberFormatException) {
      false
    }
  }

  /**
   * Check if a client IP is from the Tailscale network.
   * Used to validate incoming relay connections.
   * @param clientIp IP address of connecting client
   * @return true if client is on Tailscale network
   */
  fun isFromTailscaleNetwork(clientIp: String): Boolean {
    return isTailscaleIp(clientIp)
  }

  /**
   * Check if Tailscale is currently active on this device.
   * @return true if device has a Tailscale IP
   */
  fun isTailscaleActive(): Boolean {
    return getTailscaleIp() != null
  }

  /**
   * Check if the Tailscale app is installed on this device.
   */
  fun isTailscaleInstalled(context: Context): Boolean {
    return try {
      context.packageManager.getPackageInfo(TAILSCALE_PACKAGE, 0)
      true
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Launch the Tailscale app, or open Play Store to install it.
   * @return true if Tailscale was launched, false if redirected to Play Store
   */
  fun launchTailscale(context: Context): Boolean {
    if (isTailscaleInstalled(context)) {
      val launchIntent = context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)
      if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
      }
    }

    // Not installed — open Play Store
    try {
      val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$TAILSCALE_PACKAGE"))
      storeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(storeIntent)
    } catch (e: ActivityNotFoundException) {
      val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(TAILSCALE_PLAY_STORE))
      webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(webIntent)
    }
    Toast.makeText(context, "Please install Tailscale to use relay mode", Toast.LENGTH_LONG).show()
    return false
  }
}
