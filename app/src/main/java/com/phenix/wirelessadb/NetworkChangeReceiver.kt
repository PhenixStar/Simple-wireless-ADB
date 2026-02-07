package com.phenix.wirelessadb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Monitors network connectivity changes to automatically re-enable wireless ADB
 * when the device connects to a new WiFi network or when network changes occur.
 */
class NetworkChangeReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    Log.d(TAG, "onReceive: action=${intent.action}")

    when (intent.action) {
      ConnectivityManager.CONNECTIVITY_ACTION,
      "android.net.wifi.STATE_CHANGE",
      "android.net.wifi.WIFI_STATE_CHANGED" -> {
        handleNetworkChange(context)
      }
    }
  }

  private fun handleNetworkChange(context: Context) {
    Log.d(TAG, "handleNetworkChange: Network state changed")

    // Check if device is connected to WiFi
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (connectivityManager == null) {
      Log.e(TAG, "handleNetworkChange: ConnectivityManager not available")
      return
    }

    val network = connectivityManager.activeNetwork
    if (network == null) {
      Log.d(TAG, "handleNetworkChange: No active network")
      return
    }

    val capabilities = connectivityManager.getNetworkCapabilities(network)
    if (capabilities == null) {
      Log.d(TAG, "handleNetworkChange: No network capabilities")
      return
    }

    val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    Log.d(TAG, "handleNetworkChange: isWifi=$isWifi")

    if (isWifi) {
      Log.d(TAG, "handleNetworkChange: WiFi connected, checking auto-reconnect")

      // Check if auto-reconnect is enabled
      if (!PrefsManager.isAutoReconnectEnabled(context)) {
        Log.d(TAG, "handleNetworkChange: Auto-reconnect is disabled")
        return
      }

      // Check trusted networks if configured
      val trustedNetworks = PrefsManager.getTrustedNetworks(context)
      if (trustedNetworks.isNotEmpty()) {
        val currentSsid = getCurrentWifiSsid(context)
        Log.d(TAG, "handleNetworkChange: Current SSID=$currentSsid, trusted networks=$trustedNetworks")

        if (currentSsid == null) {
          Log.d(TAG, "handleNetworkChange: Cannot determine current SSID, skipping auto-reconnect")
          return
        }

        if (!PrefsManager.isTrustedNetwork(context, currentSsid)) {
          Log.d(TAG, "handleNetworkChange: Network '$currentSsid' is not trusted, skipping auto-reconnect")
          return
        }

        Log.d(TAG, "handleNetworkChange: Network '$currentSsid' is trusted, proceeding with auto-reconnect")
      } else {
        Log.d(TAG, "handleNetworkChange: No trusted networks configured, allowing all networks")
      }

      val port = PrefsManager.getPort(context)

      // Launch coroutine to check status and re-enable if needed
      CoroutineScope(Dispatchers.IO).launch {
        val status = AdbManager.getStatus(context)
        Log.d(TAG, "handleNetworkChange: Current ADB status - enabled=${status.enabled}, port=${status.port}")

        // Only re-enable if ADB was previously enabled
        if (status.enabled) {
          Log.d(TAG, "handleNetworkChange: Re-enabling ADB on port $port")
          AdbManager.enable(port)

          // Notify service to update notification with new IP
          AdbService.onNetworkChanged(context)
        } else {
          Log.d(TAG, "handleNetworkChange: ADB was not previously enabled, skipping auto-reconnect")
        }
      }
    }
  }

  /**
   * Get the current WiFi SSID, removing quotes that Android adds.
   * Returns null if not connected to WiFi or SSID cannot be determined.
   */
  private fun getCurrentWifiSsid(context: Context): String? {
    return try {
      val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
      if (wifiManager == null) {
        Log.e(TAG, "getCurrentWifiSsid: WifiManager not available")
        return null
      }

      val connectionInfo = wifiManager.connectionInfo
      if (connectionInfo == null) {
        Log.d(TAG, "getCurrentWifiSsid: No connection info")
        return null
      }

      val ssid = connectionInfo.ssid
      if (ssid == null || ssid == "<unknown ssid>" || ssid.isEmpty()) {
        Log.d(TAG, "getCurrentWifiSsid: SSID unknown or empty")
        return null
      }

      // Remove quotes that Android adds around SSID
      val cleanSsid = ssid.trim('"')
      Log.d(TAG, "getCurrentWifiSsid: raw='$ssid', clean='$cleanSsid'")
      cleanSsid
    } catch (e: Exception) {
      Log.e(TAG, "getCurrentWifiSsid: ERROR ${e.message}")
      null
    }
  }

  companion object {
    private const val TAG = "NetworkChangeReceiver"

    /**
     * Register a NetworkCallback for more granular network monitoring.
     * This is the modern approach compared to using broadcast receivers.
     */
    fun registerNetworkCallback(context: Context, callback: ConnectivityManager.NetworkCallback) {
      val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      if (connectivityManager == null) {
        Log.e(TAG, "registerNetworkCallback: ConnectivityManager not available")
        return
      }

      val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()

      try {
        connectivityManager.registerNetworkCallback(request, callback)
        Log.d(TAG, "registerNetworkCallback: NetworkCallback registered")
      } catch (e: Exception) {
        Log.e(TAG, "registerNetworkCallback: Failed to register callback", e)
      }
    }

    /**
     * Unregister a previously registered NetworkCallback.
     */
    fun unregisterNetworkCallback(context: Context, callback: ConnectivityManager.NetworkCallback) {
      val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      if (connectivityManager == null) {
        Log.e(TAG, "unregisterNetworkCallback: ConnectivityManager not available")
        return
      }

      try {
        connectivityManager.unregisterNetworkCallback(callback)
        Log.d(TAG, "unregisterNetworkCallback: NetworkCallback unregistered")
      } catch (e: Exception) {
        Log.e(TAG, "unregisterNetworkCallback: Failed to unregister callback", e)
      }
    }
  }
}
