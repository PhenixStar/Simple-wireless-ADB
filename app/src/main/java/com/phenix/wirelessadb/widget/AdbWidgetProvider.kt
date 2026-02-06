package com.phenix.wirelessadb.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
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
  }

  /**
   * Called when widget receives a broadcast.
   * Handles custom actions like toggle and copy.
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
        // TODO: Implement copy to clipboard (next subtask)
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

  /**
   * Called when the first widget instance is added.
   */
  override fun onEnabled(context: Context) {
    super.onEnabled(context)
    // Widget is now active - future: register broadcast receivers for real-time updates
  }

  /**
   * Called when the last widget instance is removed.
   */
  override fun onDisabled(context: Context) {
    super.onDisabled(context)
    // No more widgets - future: unregister broadcast receivers
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
   * Fetches current status, toggles ADB state, and updates all widgets.
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

        // Check result
        if (result.isSuccess) {
          Log.d(TAG, "handleToggleAdb: Toggle successful")
        } else {
          Log.e(TAG, "handleToggleAdb: Toggle failed - ${result.exceptionOrNull()?.message}")
        }

        // Update all widgets to reflect new state
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, AdbWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        Log.d(TAG, "handleToggleAdb: Updating ${appWidgetIds.size} widget(s)")
        for (appWidgetId in appWidgetIds) {
          updateWidget(context, appWidgetManager, appWidgetId)
        }
      } catch (e: Exception) {
        Log.e(TAG, "handleToggleAdb: Failed to toggle ADB", e)
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

          // TODO: Set up PendingIntent for copy button (next subtask)

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
