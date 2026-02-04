package com.phenix.wirelessadb.p2p

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persistent storage for trusted P2P devices.
 *
 * Stores trusted device info as JSON for auto-reconnection.
 * Thread-safe with mutex for concurrent access.
 *
 * Usage:
 * ```
 * val storage = TrustedDeviceStorage(context)
 *
 * // Add trusted device
 * storage.addDevice(trustedDevice)
 *
 * // Get all devices for auto-reconnect
 * val devices = storage.getAutoReconnectDevices()
 *
 * // Update after successful connection
 * storage.updateDevice(device.withConnectionInfo(...))
 * ```
 */
class TrustedDeviceStorage(private val context: Context) {

  companion object {
    private const val TAG = "TrustedDeviceStorage"
    private const val FILENAME = "trusted_p2p_devices.json"
    private const val MAX_DEVICES = 50
  }

  private val mutex = Mutex()
  private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
  private val file: File by lazy { File(context.filesDir, FILENAME) }

  // In-memory cache
  private var cache: MutableMap<String, TrustedP2PDevice>? = null

  /**
   * Get all trusted devices.
   */
  suspend fun getAllDevices(): List<TrustedP2PDevice> = withContext(Dispatchers.IO) {
    mutex.withLock {
      loadDevices().values.toList()
    }
  }

  /**
   * Get device by hash.
   */
  suspend fun getDevice(deviceHash: String): TrustedP2PDevice? = withContext(Dispatchers.IO) {
    mutex.withLock {
      loadDevices()[deviceHash]
    }
  }

  /**
   * Get devices that should auto-reconnect.
   */
  suspend fun getAutoReconnectDevices(): List<TrustedP2PDevice> = withContext(Dispatchers.IO) {
    mutex.withLock {
      loadDevices().values
        .filter { it.autoReconnect && it.shouldAttemptReconnect() }
        .sortedByDescending { it.lastConnected }
    }
  }

  /**
   * Get recently connected devices (last 7 days).
   */
  suspend fun getRecentDevices(): List<TrustedP2PDevice> = withContext(Dispatchers.IO) {
    val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
    mutex.withLock {
      loadDevices().values
        .filter { it.lastConnected > weekAgo }
        .sortedByDescending { it.lastConnected }
    }
  }

  /**
   * Add or update a trusted device.
   */
  suspend fun addDevice(device: TrustedP2PDevice): Boolean = withContext(Dispatchers.IO) {
    mutex.withLock {
      val devices = loadDevices()

      // Enforce max devices limit
      if (devices.size >= MAX_DEVICES && !devices.containsKey(device.deviceHash)) {
        // Remove oldest unused device
        val oldest = devices.values
          .filter { !it.isOnline }
          .minByOrNull { it.lastConnected }
        if (oldest != null) {
          devices.remove(oldest.deviceHash)
          Log.i(TAG, "Removed oldest device to make room: ${oldest.shortId}")
        } else {
          Log.w(TAG, "Cannot add device: max limit reached and all devices are online")
          return@withContext false
        }
      }

      devices[device.deviceHash] = device
      saveDevices(devices)
      Log.i(TAG, "Added/updated trusted device: ${device.displayName} (${device.shortId})")
      true
    }
  }

  /**
   * Update an existing device.
   */
  suspend fun updateDevice(device: TrustedP2PDevice): Boolean = withContext(Dispatchers.IO) {
    mutex.withLock {
      val devices = loadDevices()
      if (!devices.containsKey(device.deviceHash)) {
        Log.w(TAG, "Device not found for update: ${device.shortId}")
        return@withContext false
      }
      devices[device.deviceHash] = device
      saveDevices(devices)
      true
    }
  }

  /**
   * Remove a trusted device.
   */
  suspend fun removeDevice(deviceHash: String): Boolean = withContext(Dispatchers.IO) {
    mutex.withLock {
      val devices = loadDevices()
      val removed = devices.remove(deviceHash)
      if (removed != null) {
        saveDevices(devices)
        Log.i(TAG, "Removed trusted device: ${removed.displayName}")
        true
      } else {
        false
      }
    }
  }

  /**
   * Check if device is trusted.
   */
  suspend fun isTrusted(deviceHash: String): Boolean = withContext(Dispatchers.IO) {
    mutex.withLock {
      loadDevices().containsKey(deviceHash)
    }
  }

  /**
   * Mark device as offline.
   */
  suspend fun markOffline(deviceHash: String) = withContext(Dispatchers.IO) {
    mutex.withLock {
      val devices = loadDevices()
      devices[deviceHash]?.let { device ->
        devices[deviceHash] = device.asOffline()
        saveDevices(devices)
      }
    }
  }

  /**
   * Mark all devices as offline.
   */
  suspend fun markAllOffline() = withContext(Dispatchers.IO) {
    mutex.withLock {
      val devices = loadDevices()
      devices.replaceAll { _, device -> device.asOffline() }
      saveDevices(devices)
    }
  }

  /**
   * Record connection attempt result.
   */
  suspend fun recordAttempt(deviceHash: String, success: Boolean) = withContext(Dispatchers.IO) {
    mutex.withLock {
      val devices = loadDevices()
      devices[deviceHash]?.let { device ->
        devices[deviceHash] = device.withAttempt(success)
        saveDevices(devices)
      }
    }
  }

  /**
   * Clear all trusted devices.
   */
  suspend fun clearAll() = withContext(Dispatchers.IO) {
    mutex.withLock {
      cache = mutableMapOf()
      if (file.exists()) {
        file.delete()
      }
      Log.i(TAG, "Cleared all trusted devices")
    }
  }

  /**
   * Get count of trusted devices.
   */
  suspend fun count(): Int = withContext(Dispatchers.IO) {
    mutex.withLock {
      loadDevices().size
    }
  }

  /**
   * Export devices as JSON string (for backup).
   */
  suspend fun exportAsJson(): String = withContext(Dispatchers.IO) {
    mutex.withLock {
      gson.toJson(loadDevices().values.toList())
    }
  }

  /**
   * Import devices from JSON string (for restore).
   */
  suspend fun importFromJson(json: String): Int = withContext(Dispatchers.IO) {
    mutex.withLock {
      try {
        val type = object : TypeToken<List<TrustedP2PDevice>>() {}.type
        val imported: List<TrustedP2PDevice> = gson.fromJson(json, type)
        val devices = loadDevices()

        var count = 0
        for (device in imported) {
          if (devices.size < MAX_DEVICES) {
            devices[device.deviceHash] = device
            count++
          }
        }

        saveDevices(devices)
        Log.i(TAG, "Imported $count devices")
        count
      } catch (e: Exception) {
        Log.e(TAG, "Failed to import devices: ${e.message}")
        0
      }
    }
  }

  // Load devices from file (or cache)
  private fun loadDevices(): MutableMap<String, TrustedP2PDevice> {
    cache?.let { return it }

    val devices = mutableMapOf<String, TrustedP2PDevice>()

    try {
      if (file.exists()) {
        val json = file.readText()
        val type = object : TypeToken<List<TrustedP2PDevice>>() {}.type
        val list: List<TrustedP2PDevice>? = gson.fromJson(json, type)
        list?.forEach { devices[it.deviceHash] = it }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load trusted devices: ${e.message}")
    }

    cache = devices
    return devices
  }

  // Save devices to file
  private fun saveDevices(devices: Map<String, TrustedP2PDevice>) {
    try {
      val json = gson.toJson(devices.values.toList())
      file.writeText(json)
      cache = devices.toMutableMap()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save trusted devices: ${e.message}")
    }
  }
}
