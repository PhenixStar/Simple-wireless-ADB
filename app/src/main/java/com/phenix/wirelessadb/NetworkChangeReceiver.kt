package com.phenix.wirelessadb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

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
      // Auto-reconnect logic will be implemented in subtask-2-2
      // For now, just log that WiFi is connected
      Log.d(TAG, "handleNetworkChange: WiFi connected, ready for auto-reconnect")
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
