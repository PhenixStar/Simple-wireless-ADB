package com.phenix.wirelessadb.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.phenix.wirelessadb.AdbManager
import com.phenix.wirelessadb.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    const val ACTION_TOGGLE_ADB = "com.phenix.wirelessadb.widget.TOGGLE_ADB"
    const val ACTION_COPY_COMMAND = "com.phenix.wirelessadb.widget.COPY_COMMAND"
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
   * Updates a single widget instance with current ADB status.
   */
  private fun updateWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
  ) {
    CoroutineScope(Dispatchers.IO).launch {
      val status = AdbManager.getStatus(context)

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
      } else {
        // ADB is disabled
        views.setImageViewResource(R.id.widgetStatusIcon, R.drawable.ic_indicator_wifi)
        views.setInt(R.id.widgetStatusIcon, "setColorFilter", context.getColor(R.color.status_inactive))
        views.setTextViewText(R.id.widgetStatusText, context.getString(R.string.status_disabled))

        // Hide IP:Port, show placeholder
        views.setViewVisibility(R.id.widgetConnectionInfo, View.GONE)
        views.setViewVisibility(R.id.widgetPlaceholder, View.VISIBLE)
      }

      // Update toggle button icon
      if (status.enabled) {
        views.setImageViewResource(R.id.widgetToggleButton, R.drawable.ic_check)
      } else {
        views.setImageViewResource(R.id.widgetToggleButton, R.drawable.ic_check)
      }

      // TODO: Set up PendingIntents for button clicks (Phase 2)
      // - Toggle button should trigger ACTION_TOGGLE_ADB
      // - Copy button should trigger ACTION_COPY_COMMAND

      // Update widget
      appWidgetManager.updateAppWidget(appWidgetId, views)
    }
  }
}
