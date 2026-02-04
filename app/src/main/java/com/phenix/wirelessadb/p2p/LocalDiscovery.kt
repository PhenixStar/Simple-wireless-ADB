package com.phenix.wirelessadb.p2p

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.phenix.wirelessadb.model.DiscoveredDevice
import com.phenix.wirelessadb.model.DiscoveryState
import com.phenix.wirelessadb.util.DeviceFingerprint
import kotlinx.coroutines.*

/**
 * Local network device discovery using mDNS/NSD.
 *
 * Registers this device as a "_rootadb._tcp" service and discovers
 * other devices running RootADB Pro on the same network. This enables
 * fast, direct connections without NAT traversal.
 *
 * Usage:
 * ```
 * val discovery = LocalDiscovery(context)
 * discovery.start(port = 5555)
 * discovery.discoveredDevices.observe(this) { devices ->
 *   // Show nearby devices
 * }
 * discovery.stop()
 * ```
 */
class LocalDiscovery(private val context: Context) {

  companion object {
    private const val TAG = "LocalDiscovery"

    /** mDNS service type for RootADB Pro */
    const val SERVICE_TYPE = "_rootadb._tcp."

    /** TXT record keys */
    private const val TXT_DEVICE_HASH = "dh"
    private const val TXT_ADB_PORT = "port"
    private const val TXT_ADB_ENABLED = "adb"
    private const val TXT_ANDROID_VERSION = "av"
    private const val TXT_APP_VERSION = "ver"

    /** Discovery refresh interval */
    private const val REFRESH_INTERVAL_MS = 30_000L

    /** Max time to consider a device "alive" */
    private const val DEVICE_STALE_MS = 60_000L
  }

  private val nsdManager: NsdManager by lazy {
    context.getSystemService(Context.NSD_SERVICE) as NsdManager
  }

  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  // State
  private val _state = MutableLiveData(DiscoveryState.IDLE)
  val state: LiveData<DiscoveryState> = _state

  // Discovered devices
  private val _discoveredDevices = MutableLiveData<List<DiscoveredDevice>>(emptyList())
  val discoveredDevices: LiveData<List<DiscoveredDevice>> = _discoveredDevices

  // Internal device map (deviceHash -> device)
  private val devices = mutableMapOf<String, DiscoveredDevice>()
  private var registeredServiceName: String? = null

  // Listeners
  private var registrationListener: NsdManager.RegistrationListener? = null
  private var discoveryListener: NsdManager.DiscoveryListener? = null

  // Device info
  private val deviceHash: String by lazy { DeviceFingerprint.computeDeviceHash(context) }
  private val deviceName: String by lazy { "${Build.MANUFACTURER} ${Build.MODEL}" }

  /**
   * Start mDNS service registration and discovery.
   *
   * @param port ADB port to advertise
   * @param adbEnabled Whether ADB is currently enabled
   */
  fun start(port: Int, adbEnabled: Boolean = true) {
    if (_state.value == DiscoveryState.DISCOVERING) {
      Log.w(TAG, "Already discovering")
      return
    }

    Log.i(TAG, "Starting local discovery on port $port")
    _state.postValue(DiscoveryState.DISCOVERING)

    // Register our service
    registerService(port, adbEnabled)

    // Start discovering other services
    startDiscovery()

    // Periodic cleanup of stale devices
    scope.launch {
      while (isActive) {
        delay(REFRESH_INTERVAL_MS)
        cleanupStaleDevices()
      }
    }
  }

  /**
   * Stop service registration and discovery.
   */
  fun stop() {
    Log.i(TAG, "Stopping local discovery")

    scope.coroutineContext.cancelChildren()

    // Unregister service
    registrationListener?.let { listener ->
      try {
        nsdManager.unregisterService(listener)
      } catch (e: Exception) {
        Log.w(TAG, "Error unregistering service: ${e.message}")
      }
    }
    registrationListener = null

    // Stop discovery
    discoveryListener?.let { listener ->
      try {
        nsdManager.stopServiceDiscovery(listener)
      } catch (e: Exception) {
        Log.w(TAG, "Error stopping discovery: ${e.message}")
      }
    }
    discoveryListener = null

    devices.clear()
    _discoveredDevices.postValue(emptyList())
    _state.postValue(DiscoveryState.STOPPED)
  }

  /**
   * Update the advertised ADB status.
   */
  fun updateAdbStatus(port: Int, enabled: Boolean) {
    // Re-register with new status
    registrationListener?.let {
      try {
        nsdManager.unregisterService(it)
      } catch (e: Exception) {
        Log.w(TAG, "Error unregistering for update: ${e.message}")
      }
    }
    registerService(port, enabled)
  }

  /**
   * Register this device as an mDNS service.
   */
  private fun registerService(port: Int, adbEnabled: Boolean) {
    val serviceInfo = NsdServiceInfo().apply {
      serviceName = "RootADB-${deviceHash.takeLast(4)}"
      serviceType = SERVICE_TYPE
      setPort(port)

      // Add TXT records with device info
      setAttribute(TXT_DEVICE_HASH, deviceHash)
      setAttribute(TXT_ADB_PORT, port.toString())
      setAttribute(TXT_ADB_ENABLED, if (adbEnabled) "1" else "0")
      setAttribute(TXT_ANDROID_VERSION, Build.VERSION.RELEASE)
      setAttribute(TXT_APP_VERSION, getAppVersion())
    }

    registrationListener = object : NsdManager.RegistrationListener {
      override fun onServiceRegistered(info: NsdServiceInfo) {
        registeredServiceName = info.serviceName
        Log.i(TAG, "Service registered: ${info.serviceName}")
      }

      override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
        Log.e(TAG, "Registration failed: error $errorCode")
        _state.postValue(DiscoveryState.ERROR)
      }

      override fun onServiceUnregistered(info: NsdServiceInfo) {
        Log.i(TAG, "Service unregistered: ${info.serviceName}")
        registeredServiceName = null
      }

      override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
        Log.e(TAG, "Unregistration failed: error $errorCode")
      }
    }

    try {
      nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to register service: ${e.message}")
      _state.postValue(DiscoveryState.ERROR)
    }
  }

  /**
   * Start discovering other RootADB Pro devices.
   */
  private fun startDiscovery() {
    discoveryListener = object : NsdManager.DiscoveryListener {
      override fun onDiscoveryStarted(serviceType: String) {
        Log.i(TAG, "Discovery started for $serviceType")
      }

      override fun onDiscoveryStopped(serviceType: String) {
        Log.i(TAG, "Discovery stopped for $serviceType")
      }

      override fun onServiceFound(service: NsdServiceInfo) {
        Log.d(TAG, "Service found: ${service.serviceName}")

        // Don't discover ourselves
        if (service.serviceName == registeredServiceName) {
          Log.d(TAG, "Ignoring self")
          return
        }

        // Resolve service to get details
        resolveService(service)
      }

      override fun onServiceLost(service: NsdServiceInfo) {
        Log.d(TAG, "Service lost: ${service.serviceName}")

        // Remove from discovered devices
        val deviceHashFromName = extractDeviceHash(service.serviceName)
        deviceHashFromName?.let { hash ->
          devices.remove(hash)
          _discoveredDevices.postValue(devices.values.toList())
        }
      }

      override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.e(TAG, "Discovery start failed: error $errorCode")
        _state.postValue(DiscoveryState.ERROR)
      }

      override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.e(TAG, "Discovery stop failed: error $errorCode")
      }
    }

    try {
      nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start discovery: ${e.message}")
      _state.postValue(DiscoveryState.ERROR)
    }
  }

  /**
   * Resolve a discovered service to get full details.
   */
  private fun resolveService(service: NsdServiceInfo) {
    val resolveListener = object : NsdManager.ResolveListener {
      override fun onServiceResolved(resolvedService: NsdServiceInfo) {
        Log.i(TAG, "Resolved: ${resolvedService.serviceName} at ${resolvedService.host?.hostAddress}:${resolvedService.port}")

        val device = serviceToDevice(resolvedService)
        if (device != null && device.deviceHash != deviceHash) {
          devices[device.deviceHash] = device
          _discoveredDevices.postValue(devices.values.toList())
        }
      }

      override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
        Log.w(TAG, "Resolve failed for ${service.serviceName}: error $errorCode")
      }
    }

    try {
      nsdManager.resolveService(service, resolveListener)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to resolve service: ${e.message}")
    }
  }

  /**
   * Convert NsdServiceInfo to DiscoveredDevice.
   */
  private fun serviceToDevice(service: NsdServiceInfo): DiscoveredDevice? {
    val ip = service.host?.hostAddress ?: return null
    val port = service.port

    // Extract TXT records
    val attributes = service.attributes
    val hash = attributes[TXT_DEVICE_HASH]?.decodeToString()
      ?: extractDeviceHash(service.serviceName)
      ?: return null
    val adbEnabled = attributes[TXT_ADB_ENABLED]?.decodeToString() == "1"
    val androidVersion = attributes[TXT_ANDROID_VERSION]?.decodeToString()
    val appVersion = attributes[TXT_APP_VERSION]?.decodeToString()

    return DiscoveredDevice(
      deviceHash = hash,
      displayName = service.serviceName.removePrefix("RootADB-"),
      ipAddress = ip,
      port = port,
      adbEnabled = adbEnabled,
      androidVersion = androidVersion,
      appVersion = appVersion
    )
  }

  /**
   * Extract device hash from service name (e.g., "RootADB-A1B2" -> "A1B2").
   */
  private fun extractDeviceHash(serviceName: String): String? {
    return if (serviceName.startsWith("RootADB-")) {
      serviceName.removePrefix("RootADB-")
    } else {
      null
    }
  }

  /**
   * Remove devices that haven't been seen recently.
   */
  private fun cleanupStaleDevices() {
    val staleDevices = devices.filter { it.value.isStale(DEVICE_STALE_MS) }
    staleDevices.keys.forEach { devices.remove(it) }
    if (staleDevices.isNotEmpty()) {
      _discoveredDevices.postValue(devices.values.toList())
      Log.d(TAG, "Cleaned up ${staleDevices.size} stale devices")
    }
  }

  /**
   * Get current app version.
   */
  private fun getAppVersion(): String {
    return try {
      val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
      pInfo.versionName ?: "unknown"
    } catch (e: Exception) {
      "unknown"
    }
  }

  /**
   * Check if a device with given hash is currently visible.
   */
  fun isDeviceVisible(deviceHash: String): Boolean {
    return devices[deviceHash]?.let { !it.isStale() } ?: false
  }

  /**
   * Get device by hash if visible.
   */
  fun getDevice(deviceHash: String): DiscoveredDevice? {
    return devices[deviceHash]?.takeIf { !it.isStale() }
  }

  /**
   * Check if an IP address is on the local network.
   *
   * @param ipAddress The IP address to check
   * @return true if the IP is in a private range (local network)
   */
  fun isLocalNetwork(ipAddress: String): Boolean {
    val parts = ipAddress.split(".").mapNotNull { it.toIntOrNull() }
    if (parts.size != 4) return false

    val first = parts[0]
    val second = parts[1]

    return when {
      // 10.0.0.0/8
      first == 10 -> true
      // 172.16.0.0/12
      first == 172 && second in 16..31 -> true
      // 192.168.0.0/16
      first == 192 && second == 168 -> true
      // 127.0.0.0/8 (localhost)
      first == 127 -> true
      // Not local
      else -> false
    }
  }
}
