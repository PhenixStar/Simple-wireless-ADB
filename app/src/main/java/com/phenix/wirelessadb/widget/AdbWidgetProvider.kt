package com.phenix.wirelessadb.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.phenix.wirelessadb.AdbManager
import com.phenix.wirelessadb.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home screen widget provider for ADB status display and control.
 *
 * Features:
 * - Display current ADB status (enabled/disabled)
 * - Show IP:Port when ADB is enabled
 * - Toggle button to enable/disable ADB
 * - Copy button to copy connection command to clipboard
 */
class AdbWidgetProvider : AppWidgetProvider() {

  companion object {
    private const val TAG = "AdbWidgetProvider"
    const val ACTION_TOGGLE_ADB = "com.phenix.wirelessadb.widget.TOGGLE_ADB"
    const val ACTION_COPY_COMMAND = "com.phenix.wirelessadb.widget.COPY_COMMAND"
    const val ACTION_ADB_STATUS_CHANGED = "com.phenix.wirelessadb.ADB_STATUS_CHANGED"

    /**
     * Broadcasts ADB status change to all widget instances.
     * This should be called whenever ADB is enabled/disabled from any part of the app.
     */
    fun notifyStatusChanged(context: Context) {
      Log.d(TAG, "notifyStatusChanged: Broadcasting ADB status change")
      val intent = Intent(ACTION_ADB_STATUS_CHANGED)
      LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    /**
     * Manually updates all widget instances.
     * Useful for forcing a refresh from external components.
     */
    fun updateAllWidgets(context: Context) {
      Log.d(TAG, "updateAllWidgets: Manually updating all widgets")
      val appWidgetManager = AppWidgetManager.getInstance(context)
      val componentName = ComponentName(context, AdbWidgetProvider::class.java)
      val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
      val intent = Intent(context, AdbWidgetProvider::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
      }
      context.sendBroadcast(intent)
    }
  }

  /**
   * Called when widget receives a broadcast.
   * Handles custom actions like toggle, copy, and status change notifications.
   */
  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)

    when (intent.action) {
      ACTION_TOGGLE_ADB -> {
        Log.d(TAG, "onReceive: Toggle ADB action received")
        handleToggleAdb(context)
      }
      ACTION_COPY_COMMAND -> {
        Log.d(TAG, "onReceive: Copy command action received")
        handleCopyCommand(context)
      }
      ACTION_ADB_STATUS_CHANGED -> {
        Log.d(TAG, "onReceive: ADB status changed - updating all widgets")
        updateAllWidgetsInstances(context)
      }
      "android.net.conn.CONNECTIVITY_CHANGE" -> {
        Log.d(TAG, "onReceive: Network connectivity changed - updating all widgets")
        updateAllWidgetsInstances(context)
      }
    }
  }

  /**
   * Called when widget needs to be updated.
   * Updates all instances with current ADB status.
   */
  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray
  ) {
    // Update each widget instance
    for (appWidgetId in appWidgetIds) {
      updateWidget(context, appWidgetManager, appWidgetId)
    }
  }

  // Broadcast receiver for real-time status updates
  private var statusChangeReceiver: BroadcastReceiver? = null
  private var networkChangeReceiver: BroadcastReceiver? = null

  /**
   * Called when the first widget instance is added.
   * Registers broadcast receiver for real-time ADB status updates.
   */
  override fun onEnabled(context: Context) {
    super.onEnabled(context)
    Log.d(TAG, "onEnabled: First widget added - registering receivers")

    // Register receiver for ADB status changes
    if (statusChangeReceiver == null) {
      statusChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          if (intent.action == ACTION_ADB_STATUS_CHANGED) {
            Log.d(TAG, "statusChangeReceiver: ADB status changed - updating widgets")
            updateAllWidgetsInstances(context)
          }
        }
      }
      LocalBroadcastManager.getInstance(context).registerReceiver(
        statusChangeReceiver!!,
        IntentFilter(ACTION_ADB_STATUS_CHANGED)
      )
      Log.d(TAG, "onEnabled: Status change receiver registered")
    }

    // Register receiver for network connectivity changes
    if (networkChangeReceiver == null) {
      networkChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          Log.d(TAG, "networkChangeReceiver: Network connectivity changed - updating widgets")
          updateAllWidgetsInstances(context)
        }
      }
      val networkFilter = IntentFilter().apply {
        addAction("android.net.conn.CONNECTIVITY_CHANGE")
      }
      context.registerReceiver(networkChangeReceiver!!, networkFilter)
      Log.d(TAG, "onEnabled: Network change receiver registered")
    }
  }

  /**
   * Called when the last widget instance is removed.
   * Unregisters broadcast receivers.
   */
  override fun onDisabled(context: Context) {
    super.onDisabled(context)
    Log.d(TAG, "onDisabled: Last widget removed - unregistering receivers")

    // Unregister status change receiver
    statusChangeReceiver?.let {
      try {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(it)
        Log.d(TAG, "onDisabled: Status change receiver unregistered")
      } catch (e: Exception) {
        Log.e(TAG, "onDisabled: Failed to unregister status receiver", e)
      }
      statusChangeReceiver = null
    }

    // Unregister network change receiver
    networkChangeReceiver?.let {
      try {
        context.unregisterReceiver(it)
        Log.d(TAG, "onDisabled: Network change receiver unregistered")
      } catch (e: Exception) {
        Log.e(TAG, "onDisabled: Failed to unregister network receiver", e)
      }
      networkChangeReceiver = null
    }
  }

  /**
   * Called when a widget instance is removed.
   */
  override fun onDeleted(context: Context, appWidgetIds: IntArray) {
    super.onDeleted(context, appWidgetIds)
    // Widget instance removed - cleanup if needed
  }

  /**
   * Handles toggle ADB button click.
   * Fetches current status, toggles ADB state, broadcasts change, and updates all widgets.
   */
  private fun handleToggleAdb(context: Context) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        // Get current status
        val status = AdbManager.getStatus(context)
        Log.d(TAG, "handleToggleAdb: Current status = enabled:${status.enabled}")

        // Toggle ADB state
        val result = if (status.enabled) {
          Log.d(TAG, "handleToggleAdb: Disabling ADB")
          AdbManager.disable()
        } else {
          Log.d(TAG, "handleToggleAdb: Enabling ADB on port ${status.port}")
          AdbManager.enable(status.port)
        }

        // Check result and broadcast change
        if (result.isSuccess) {
          Log.d(TAG, "handleToggleAdb: Toggle successful - broadcasting status change")
          // Broadcast status change to notify other components (like ViewModel, other widgets, etc.)
          notifyStatusChanged(context)
        } else {
          Log.e(TAG, "handleToggleAdb: Toggle failed - ${result.exceptionOrNull()?.message}")
        }

        // Update all widgets to reflect new state
        updateAllWidgetsInstances(context)
      } catch (e: Exception) {
        Log.e(TAG, "handleToggleAdb: Failed to toggle ADB", e)
      }
    }
  }

  /**
   * Updates all widget instances.
   * Called when ADB status changes or broadcast is received.
   */
  private fun updateAllWidgetsInstances(context: Context) {
    try {
      val appWidgetManager = AppWidgetManager.getInstance(context)
      val componentName = ComponentName(context, AdbWidgetProvider::class.java)
      val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

      Log.d(TAG, "updateAllWidgetsInstances: Updating ${appWidgetIds.size} widget(s)")
      for (appWidgetId in appWidgetIds) {
        updateWidget(context, appWidgetManager, appWidgetId)
      }
    } catch (e: Exception) {
      Log.e(TAG, "updateAllWidgetsInstances: Failed to update widgets", e)
    }
  }

  /**
   * Handles copy command button click.
   * Copies the ADB connect command to clipboard and shows a toast notification.
   */
  private fun handleCopyCommand(context: Context) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        // Get current status
        val status = AdbManager.getStatus(context)
        Log.d(TAG, "handleCopyCommand: Current status = enabled:${status.enabled}, ip:${status.ip}")

        // Check if ADB is enabled and has an IP address
        if (status.enabled && status.ip != null) {
          // Format the ADB connect command
          val command = "adb connect ${status.ip}:${status.port}"
          Log.d(TAG, "handleCopyCommand: Command = $command")

          // Copy to clipboard on main thread
          withContext(Dispatchers.Main) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("ADB Command", command)
            clipboard.setPrimaryClip(clip)

            // Show toast notification
            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "handleCopyCommand: Command copied to clipboard")
          }
        } else {
          // ADB is disabled or no WiFi connection - show warning
          withContext(Dispatchers.Main) {
            val message = if (!status.enabled) {
              "ADB is disabled"
            } else {
              "No WiFi connection"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "handleCopyCommand: Cannot copy - $message")
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "handleCopyCommand: Failed to copy command", e)
        withContext(Dispatchers.Main) {
          Toast.makeText(context, "Failed to copy command", Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  /**
   * Updates a single widget instance with current ADB status.
   * Fetches status asynchronously and updates UI on main thread.
   */
  private fun updateWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
  ) {
    CoroutineScope(Dispatchers.IO).launch {
      try {
        Log.d(TAG, "updateWidget: Fetching ADB status for widget $appWidgetId")

        // Fetch ADB status on IO thread
        val status = AdbManager.getStatus(context)
        Log.d(TAG, "updateWidget: Status = enabled:${status.enabled}, ip:${status.ip}, port:${status.port}")

        // Switch to main thread for UI updates
        withContext(Dispatchers.Main) {
          val views = RemoteViews(context.packageName, R.layout.widget_adb_status)

          // Update status icon and text
          if (status.enabled && status.ip != null) {
            // ADB is enabled and connected
            views.setImageViewResource(R.id.widgetStatusIcon, R.drawable.ic_indicator_wifi)
            views.setInt(R.id.widgetStatusIcon, "setColorFilter", context.getColor(R.color.status_active))
            views.setTextViewText(R.id.widgetStatusText, context.getString(R.string.status_connected))

            // Show IP:Port
            views.setViewVisibility(R.id.widgetConnectionInfo, View.VISIBLE)
            views.setViewVisibility(R.id.widgetPlaceholder, View.GONE)
            views.setTextViewText(R.id.widgetIpPortText, "${status.ip}:${status.port}")

            Log.d(TAG, "updateWidget: Widget shows connected state - ${status.ip}:${status.port}")
          } else if (status.enabled && status.ip == null) {
            // ADB is enabled but no WiFi connection
            views.setImageViewResource(R.id.widgetStatusIcon, R.drawable.ic_indicator_wifi)
            views.setInt(R.id.widgetStatusIcon, "setColorFilter", context.getColor(R.color.warning_orange))
            views.setTextViewText(R.id.widgetStatusText, context.getString(R.string.status_enabled))

            // Show port only
            views.setViewVisibility(R.id.widgetConnectionInfo, View.VISIBLE)
            views.setViewVisibility(R.id.widgetPlaceholder, View.GONE)
            views.setTextViewText(R.id.widgetIpPortText, "Port ${status.port}")

            Log.d(TAG, "updateWidget: Widget shows enabled (no WiFi) state - port:${status.port}")
          } else {
            // ADB is disabled
            views.setImageViewResource(R.id.widgetStatusIcon, R.drawable.ic_indicator_wifi)
            views.setInt(R.id.widgetStatusIcon, "setColorFilter", context.getColor(R.color.status_inactive))
            views.setTextViewText(R.id.widgetStatusText, context.getString(R.string.status_disabled))

            // Hide IP:Port, show placeholder
            views.setViewVisibility(R.id.widgetConnectionInfo, View.GONE)
            views.setViewVisibility(R.id.widgetPlaceholder, View.VISIBLE)

            Log.d(TAG, "updateWidget: Widget shows disabled state")
          }

          // Update toggle button icon (check for enabled, close for disabled)
          if (status.enabled) {
            views.setImageViewResource(R.id.widgetToggleButton, R.drawable.ic_check)
          } else {
            views.setImageViewResource(R.id.widgetToggleButton, R.drawable.ic_close)
          }

          // Set up PendingIntent for toggle button
          val toggleIntent = Intent(context, AdbWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE_ADB
          }
          val togglePendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
          )
          views.setOnClickPendingIntent(R.id.widgetToggleButton, togglePendingIntent)

          // Set up PendingIntent for copy button
          val copyIntent = Intent(context, AdbWidgetProvider::class.java).apply {
            action = ACTION_COPY_COMMAND
          }
          val copyPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
          )
          views.setOnClickPendingIntent(R.id.widgetCopyButton, copyPendingIntent)

          // Update widget on main thread
          appWidgetManager.updateAppWidget(appWidgetId, views)
          Log.d(TAG, "updateWidget: Widget $appWidgetId updated successfully")
        }
      } catch (e: Exception) {
        Log.e(TAG, "updateWidget: Failed to update widget $appWidgetId", e)

        // On error, show error state on main thread
        withContext(Dispatchers.Main) {
          try {
            val errorViews = RemoteViews(context.packageName, R.layout.widget_adb_status)
            errorViews.setImageViewResource(R.id.widgetStatusIcon, R.drawable.ic_warning)
            errorViews.setInt(R.id.widgetStatusIcon, "setColorFilter", context.getColor(R.color.error_red))
            errorViews.setTextViewText(R.id.widgetStatusText, context.getString(R.string.status_error))
            errorViews.setViewVisibility(R.id.widgetConnectionInfo, View.GONE)
            errorViews.setViewVisibility(R.id.widgetPlaceholder, View.VISIBLE)
            appWidgetManager.updateAppWidget(appWidgetId, errorViews)
          } catch (innerE: Exception) {
            Log.e(TAG, "updateWidget: Failed to show error state", innerE)
          }
        }
      }
    }
  }
}
