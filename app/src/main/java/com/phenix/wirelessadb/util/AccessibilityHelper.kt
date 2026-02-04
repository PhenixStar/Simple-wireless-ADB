package com.phenix.wirelessadb.util

import android.os.Build
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * Helper utilities for accessibility (v1.2.0 Phase 4).
 *
 * Provides extension functions and utilities for:
 * - Screen reader announcements
 * - Touch exploration detection
 * - Content description updates
 * - Accessibility action handling
 */
object AccessibilityHelper {

  /**
   * Announce a message for screen readers.
   *
   * Usage: view.announceForAccessibility("Message")
   */
  fun View.announceForAccessibility(message: String) {
    val event = AccessibilityEvent.obtain().apply {
      eventType = AccessibilityEvent.TYPE_ANNOUNCEMENT
      text.add(message)
      contentDescription = message
    }
    sendAccessibilityEventUnchecked(event)
    event.recycle()
  }

  /**
   * Check if touch exploration is enabled (TalkBack or similar).
   */
  fun View.isTouchExplorationEnabled(): Boolean {
    val am = ContextCompat.getSystemService(context, AccessibilityManager::class.java)
    return am?.isTouchExplorationEnabled ?: false
  }

  /**
   * Check if any accessibility service is enabled.
   */
  fun View.isAccessibilityEnabled(): Boolean {
    val am = ContextCompat.getSystemService(context, AccessibilityManager::class.java)
    return am?.isEnabled ?: false
  }

  /**
   * Set accessibility live region for dynamic content.
   *
   * @param mode One of: ACCESSIBILITY_LIVE_REGION_POLITE, ACCESSIBILITY_LIVE_REGION_ASSERTIVE
   */
  fun View.setLiveRegion(mode: Int = View.ACCESSIBILITY_LIVE_REGION_POLITE) {
    accessibilityLiveRegion = mode
  }

  /**
   * Update content description with formatted string.
   *
   * Usage: view.updateContentDescription("Status: %s", "enabled")
   */
  fun View.updateContentDescription(format: String, vararg args: Any) {
    contentDescription = String.format(format, *args)
  }

  /**
   * Set content description with state for interactive elements.
   *
   * Usage: switch.setStateDescription("Wireless ADB", isChecked)
   */
  fun View.setStateDescription(
    label: String,
    isChecked: Boolean,
    stateOn: String = "on",
    stateOff: String = "off"
  ) {
    val state = if (isChecked) stateOn else stateOff
    contentDescription = "$label, $state"
  }

  /**
   * Get formatted state string for boolean values.
   */
  fun getStateString(isChecked: Boolean): String {
    return if (isChecked) "on" else "off"
  }

  /**
   * Get formatted enabled/disabled string.
   */
  fun getEnabledStateString(isEnabled: Boolean): String {
    return if (isEnabled) "enabled" else "disabled"
  }

  /**
   * Get formatted connected/disconnected string.
   */
  fun getConnectionStateString(isConnected: Boolean): String {
    return if (isConnected) "connected" else "disconnected"
  }

  /**
   * Announce error message to accessibility services.
   */
  fun View.announceError(message: String) {
    announceForAccessibility("Error: $message")
  }

  /**
   * Announce success message to accessibility services.
   */
  fun View.announceSuccess(message: String) {
    announceForAccessibility("Success: $message")
  }

  /**
   * Announce state change for toggle/switch components.
   *
   * Usage: switch.announceStateChange(isChecked, "enabled", "disabled")
   */
  fun View.announceStateChange(
    isChecked: Boolean,
    stateOn: String = "on",
    stateOff: String = "off"
  ) {
    val state = if (isChecked) stateOn else stateOff
    announceForAccessibility("Changed to $state")
  }

  /**
   * Request accessibility focus for a view.
   */
  fun View.requestAccessibilityFocus() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      accessibilityTraversalBefore = -1
      accessibilityTraversalAfter = -1
    }
    requestFocus()
  }

  /**
   * Set view as important for accessibility (or not).
   */
  fun View.setAccessibilityImportant(isImportant: Boolean) {
    importantForAccessibility = if (isImportant) {
      View.IMPORTANT_FOR_ACCESSIBILITY_YES
    } else {
      View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
  }

  // Tag ID for storing accessibility action labels
  private const val ACCESSIBILITY_ACTION_CLICK_TAG = 0x7F0B0001
}
