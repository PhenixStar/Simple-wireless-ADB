package com.phenix.wirelessadb.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.phenix.wirelessadb.PrefsManager

/**
 * Theme manager singleton (v1.2.0).
 *
 * Manages app theme mode (light/dark/system) and accent color.
 */
object ThemeManager {

  /**
   * Apply the saved theme settings.
   * Call this in Application.onCreate() or Activity.onCreate() before setContentView().
   */
  fun applyTheme(context: Context) {
    val mode = PrefsManager.getThemeMode(context)
    applyThemeMode(mode)
  }

  /**
   * Apply a specific theme mode.
   */
  fun applyThemeMode(mode: ThemeMode) {
    val nightMode = when (mode) {
      ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
      ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
      ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
    AppCompatDelegate.setDefaultNightMode(nightMode)
  }

  /**
   * Set and apply theme mode.
   */
  fun setThemeMode(context: Context, mode: ThemeMode) {
    PrefsManager.setThemeMode(context, mode)
    applyThemeMode(mode)
  }

  /**
   * Get current theme mode.
   */
  fun getThemeMode(context: Context): ThemeMode {
    return PrefsManager.getThemeMode(context)
  }

  /**
   * Apply the saved accent color to an Activity's theme.
   * Call after super.onCreate() and before setContentView().
   *
   * DYNAMIC uses Material You wallpaper colors (Android 12+); other accents
   * apply a static tonal overlay that works on every supported version.
   */
  fun applyAccent(activity: Activity) {
    val accent = getAccentColor(activity)
    if (accent == AccentColor.DYNAMIC && isDynamicColorAvailable()) {
      DynamicColors.applyToActivityIfAvailable(activity)
    } else {
      val effective = if (accent == AccentColor.DYNAMIC) AccentColor.DEFAULT else accent
      activity.theme.applyStyle(effective.themeOverlay, true)
    }
  }

  /**
   * Whether Material You dynamic color is available on this device.
   */
  fun isDynamicColorAvailable(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && DynamicColors.isDynamicColorAvailable()

  /**
   * Get current accent color.
   */
  fun getAccentColor(context: Context): AccentColor {
    return PrefsManager.getAccentColor(context)
  }

  /**
   * Set accent color (requires activity recreation to apply).
   */
  fun setAccentColor(context: Context, color: AccentColor) {
    PrefsManager.setAccentColor(context, color)
  }

  /**
   * Check if currently in dark mode.
   */
  fun isDarkMode(context: Context): Boolean {
    val mode = getThemeMode(context)
    return when (mode) {
      ThemeMode.DARK -> true
      ThemeMode.LIGHT -> false
      ThemeMode.SYSTEM -> {
        val nightModeFlags = context.resources.configuration.uiMode and
          Configuration.UI_MODE_NIGHT_MASK
        nightModeFlags == Configuration.UI_MODE_NIGHT_YES
      }
    }
  }

  /**
   * Toggle between light and dark mode.
   * If currently following system, switches to the opposite of current appearance.
   */
  fun toggleDarkMode(context: Context) {
    val newMode = if (isDarkMode(context)) ThemeMode.LIGHT else ThemeMode.DARK
    setThemeMode(context, newMode)
  }

  /**
   * Cycle through theme modes: System -> Light -> Dark -> System
   */
  fun cycleThemeMode(context: Context): ThemeMode {
    val current = getThemeMode(context)
    val next = when (current) {
      ThemeMode.SYSTEM -> ThemeMode.LIGHT
      ThemeMode.LIGHT -> ThemeMode.DARK
      ThemeMode.DARK -> ThemeMode.SYSTEM
    }
    setThemeMode(context, next)
    return next
  }
}
