package com.phenix.wirelessadb

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings Tile for toggling Wireless ADB on/off.
 * Provides instant access to ADB control from the notification shade.
 */
class AdbTileService : TileService() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  companion object {
    private const val TAG = "AdbTileService"
  }

  /**
   * Called when the tile is added to Quick Settings and becomes visible.
   * This is where we should start observing ADB status and update the tile.
   */
  override fun onStartListening() {
    super.onStartListening()
    updateTileState()
  }

  /**
   * Called when the tile is removed from Quick Settings or becomes invisible.
   * Clean up any observers or listeners here.
   */
  override fun onStopListening() {
    super.onStopListening()
    // TODO: Clean up listeners if needed
  }

  /**
   * Called when the user taps the tile.
   * Toggle wireless ADB on/off.
   */
  override fun onClick() {
    super.onClick()
    serviceScope.launch {
      try {
        val currentStatus = AdbManager.getStatus(applicationContext)
        Log.d(TAG, "onClick: current enabled=${currentStatus.enabled}")

        val result = if (currentStatus.enabled) {
          AdbManager.disable()
        } else {
          AdbManager.enable()
        }

        result.fold(
          onSuccess = {
            Log.d(TAG, "onClick: toggle success")
            updateTileState()
          },
          onFailure = { error ->
            Log.e(TAG, "onClick: toggle failed - ${error.message}")
            updateTileState()
          }
        )
      } catch (e: Exception) {
        Log.e(TAG, "onClick: error - ${e.message}")
      }
    }
  }

  /**
   * Update the tile state based on current ADB status.
   * Updates the tile's state (active/inactive), label, and subtitle.
   */
  private fun updateTileState() {
    serviceScope.launch {
      try {
        val status = AdbManager.getStatus(applicationContext)
        Log.d(TAG, "updateTileState: enabled=${status.enabled}, port=${status.port}, ip=${status.ip}")

        qsTile?.apply {
          // Set tile state
          state = if (status.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

          // Set label
          label = "Wireless ADB"

          // Set subtitle with connection info
          subtitle = if (status.enabled) {
            val ip = status.ip ?: "No WiFi"
            "On - $ip:${status.port}"
          } else {
            "Off"
          }

          // Update the tile UI
          updateTile()
        }
      } catch (e: Exception) {
        Log.e(TAG, "updateTileState: error - ${e.message}")
        // Set error state
        qsTile?.apply {
          state = Tile.STATE_INACTIVE
          label = "Wireless ADB"
          subtitle = "Error"
          updateTile()
        }
      }
    }
  }

  /**
   * Clean up resources when service is destroyed.
   */
  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }
}
