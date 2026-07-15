package com.phenix.wirelessadb.theme

import androidx.annotation.ColorRes
import androidx.annotation.StyleRes
import com.phenix.wirelessadb.R

/**
 * Accent color presets for the app theme (v1.2.0).
 *
 * Each accent defines a tonal palette (night-qualified color resources) and a
 * theme overlay applied at Activity creation by [ThemeManager.applyAccent].
 * [DYNAMIC] uses Material You wallpaper colors on Android 12+ instead of an overlay.
 */
enum class AccentColor(
  val displayName: String,
  @ColorRes val primaryColor: Int,
  @ColorRes val primaryVariant: Int,
  @ColorRes val onPrimary: Int,
  @StyleRes val themeOverlay: Int
) {
  BLUE(
    displayName = "Blue",
    primaryColor = R.color.accent_blue_primary,
    primaryVariant = R.color.accent_blue_variant,
    onPrimary = R.color.accent_blue_on_primary,
    themeOverlay = R.style.ThemeOverlay_WirelessADB_Accent_Blue
  ),

  TEAL(
    displayName = "Teal",
    primaryColor = R.color.accent_teal_primary,
    primaryVariant = R.color.accent_teal_variant,
    onPrimary = R.color.accent_teal_on_primary,
    themeOverlay = R.style.ThemeOverlay_WirelessADB_Accent_Teal
  ),

  PURPLE(
    displayName = "Purple",
    primaryColor = R.color.accent_purple_primary,
    primaryVariant = R.color.accent_purple_variant,
    onPrimary = R.color.accent_purple_on_primary,
    themeOverlay = R.style.ThemeOverlay_WirelessADB_Accent_Purple
  ),

  ORANGE(
    displayName = "Orange",
    primaryColor = R.color.accent_orange_primary,
    primaryVariant = R.color.accent_orange_variant,
    onPrimary = R.color.accent_orange_on_primary,
    themeOverlay = R.style.ThemeOverlay_WirelessADB_Accent_Orange
  ),

  PINK(
    displayName = "Pink",
    primaryColor = R.color.accent_pink_primary,
    primaryVariant = R.color.accent_pink_variant,
    onPrimary = R.color.accent_pink_on_primary,
    themeOverlay = R.style.ThemeOverlay_WirelessADB_Accent_Pink
  ),

  GREEN(
    displayName = "Green",
    primaryColor = R.color.accent_green_primary,
    primaryVariant = R.color.accent_green_variant,
    onPrimary = R.color.accent_green_on_primary,
    themeOverlay = R.style.ThemeOverlay_WirelessADB_Accent_Green
  ),

  /**
   * Material You wallpaper-based colors (Android 12+ only). Falls back to
   * [DEFAULT] on older devices. Keep last: prefs store the ordinal.
   */
  DYNAMIC(
    displayName = "Dynamic",
    primaryColor = R.color.accent_teal_primary,
    primaryVariant = R.color.accent_teal_variant,
    onPrimary = R.color.accent_on_primary,
    themeOverlay = 0
  );

  companion object {
    val DEFAULT = TEAL

    fun fromOrdinal(ordinal: Int): AccentColor {
      return entries.getOrElse(ordinal) { DEFAULT }
    }
  }
}
