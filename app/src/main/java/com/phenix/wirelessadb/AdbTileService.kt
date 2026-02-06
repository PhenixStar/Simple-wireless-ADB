package com.phenix.wirelessadb

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
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
    // TODO: Update tile state when it becomes visible
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
    // TODO: Implement toggle logic
  }

  /**
   * Clean up resources when service is destroyed.
   */
  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }
}
