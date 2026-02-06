package com.phenix.wirelessadb

import android.content.Context
import android.content.SharedPreferences
import com.phenix.wirelessadb.model.ConnectionMode
import com.phenix.wirelessadb.theme.AccentColor
import com.phenix.wirelessadb.theme.ThemeMode
import com.phenix.wirelessadb.warpgate.WarpgateConfig

object PrefsManager {

  private const val PREFS_NAME = "wireless_adb_prefs"
  private const val KEY_ENABLE_ON_BOOT = "enable_on_boot"
  private const val KEY_PORT = "port"
  private const val KEY_RELAY_ENABLED = "relay_enabled"
  private const val KEY_RELAY_PORT = "relay_port"
  private const val DEFAULT_PORT = 5555
  private const val DEFAULT_RELAY_PORT = 5556

  // Connection mode
  private const val KEY_CONNECTION_MODE = "connection_mode"

  // Notification hiding
  private const val KEY_HIDE_DEV_NOTIFICATION = "hide_dev_notification"

  // Theme settings (v1.2.0)
  private const val KEY_THEME_MODE = "theme_mode"
  private const val KEY_ACCENT_COLOR = "accent_color"

  // Warpgate settings
  private const val KEY_WARPGATE_ENABLED = "warpgate_enabled"
  private const val KEY_WARPGATE_HOST = "warpgate_host"
  private const val KEY_WARPGATE_PORT = "warpgate_port"
  private const val KEY_WARPGATE_USERNAME = "warpgate_username"
  private const val KEY_WARPGATE_PASSWORD = "warpgate_password"
  private const val KEY_WARPGATE_TARGET = "warpgate_target"
  private const val KEY_WARPGATE_LOCAL_PORT = "warpgate_local_port"

  // Auto-reconnect settings
  private const val KEY_AUTO_RECONNECT_ENABLED = "auto_reconnect_enabled"
  private const val KEY_AUTO_RECONNECT_DELAY = "auto_reconnect_delay"
  private const val KEY_AUTO_RECONNECT_MAX_RETRIES = "auto_reconnect_max_retries"
  private const val DEFAULT_AUTO_RECONNECT_DELAY = 5000 // 5 seconds
  private const val DEFAULT_AUTO_RECONNECT_MAX_RETRIES = 3

  // Trusted networks settings
  private const val KEY_TRUSTED_NETWORKS = "trusted_networks"

  private fun getPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun isEnableOnBoot(context: Context): Boolean {
    return getPrefs(context).getBoolean(KEY_ENABLE_ON_BOOT, false)
  }

  fun setEnableOnBoot(context: Context, enabled: Boolean) {
    getPrefs(context).edit().putBoolean(KEY_ENABLE_ON_BOOT, enabled).apply()
  }

  fun getPort(context: Context): Int {
    return getPrefs(context).getInt(KEY_PORT, DEFAULT_PORT)
  }

  fun setPort(context: Context, port: Int) {
    getPrefs(context).edit().putInt(KEY_PORT, port).apply()
  }

  fun isRelayEnabled(context: Context): Boolean {
    return getPrefs(context).getBoolean(KEY_RELAY_ENABLED, false)
  }

  fun setRelayEnabled(context: Context, enabled: Boolean) {
    getPrefs(context).edit().putBoolean(KEY_RELAY_ENABLED, enabled).apply()
  }

  fun getRelayPort(context: Context): Int {
    return getPrefs(context).getInt(KEY_RELAY_PORT, DEFAULT_RELAY_PORT)
  }

  fun setRelayPort(context: Context, port: Int) {
    getPrefs(context).edit().putInt(KEY_RELAY_PORT, port).apply()
  }

  // Connection Mode
  fun getConnectionMode(context: Context): ConnectionMode {
    val name = getPrefs(context).getString(KEY_CONNECTION_MODE, ConnectionMode.LOCAL_WIFI.name)
    return try {
      ConnectionMode.valueOf(name ?: ConnectionMode.LOCAL_WIFI.name)
    } catch (e: IllegalArgumentException) {
      ConnectionMode.LOCAL_WIFI
    }
  }

  fun setConnectionMode(context: Context, mode: ConnectionMode) {
    getPrefs(context).edit().putString(KEY_CONNECTION_MODE, mode.name).apply()
  }

  // Developer Notification Hiding
  fun isHideDevNotification(context: Context): Boolean {
    return getPrefs(context).getBoolean(KEY_HIDE_DEV_NOTIFICATION, false)
  }

  fun setHideDevNotification(context: Context, hidden: Boolean) {
    getPrefs(context).edit().putBoolean(KEY_HIDE_DEV_NOTIFICATION, hidden).apply()
  }

  // Warpgate Configuration
  fun getWarpgateConfig(context: Context): WarpgateConfig {
    val prefs = getPrefs(context)
    return WarpgateConfig(
      enabled = prefs.getBoolean(KEY_WARPGATE_ENABLED, false),
      host = prefs.getString(KEY_WARPGATE_HOST, "") ?: "",
      port = prefs.getInt(KEY_WARPGATE_PORT, WarpgateConfig.DEFAULT_PORT),
      username = prefs.getString(KEY_WARPGATE_USERNAME, "") ?: "",
      password = prefs.getString(KEY_WARPGATE_PASSWORD, "") ?: "",
      targetName = prefs.getString(KEY_WARPGATE_TARGET, "adb") ?: "adb",
      localPort = prefs.getInt(KEY_WARPGATE_LOCAL_PORT, WarpgateConfig.DEFAULT_LOCAL_PORT)
    )
  }

  fun setWarpgateConfig(context: Context, config: WarpgateConfig) {
    getPrefs(context).edit().apply {
      putBoolean(KEY_WARPGATE_ENABLED, config.enabled)
      putString(KEY_WARPGATE_HOST, config.host)
      putInt(KEY_WARPGATE_PORT, config.port)
      putString(KEY_WARPGATE_USERNAME, config.username)
      putString(KEY_WARPGATE_PASSWORD, config.password)
      putString(KEY_WARPGATE_TARGET, config.targetName)
      putInt(KEY_WARPGATE_LOCAL_PORT, config.localPort)
      apply()
    }
  }

  fun setWarpgateEnabled(context: Context, enabled: Boolean) {
    getPrefs(context).edit().putBoolean(KEY_WARPGATE_ENABLED, enabled).apply()
  }

  fun isWarpgateEnabled(context: Context): Boolean {
    return getPrefs(context).getBoolean(KEY_WARPGATE_ENABLED, false)
  }

  // Theme Mode (v1.2.0)
  fun getThemeMode(context: Context): ThemeMode {
    val ordinal = getPrefs(context).getInt(KEY_THEME_MODE, ThemeMode.DEFAULT.ordinal)
    return ThemeMode.fromOrdinal(ordinal)
  }

  fun setThemeMode(context: Context, mode: ThemeMode) {
    getPrefs(context).edit().putInt(KEY_THEME_MODE, mode.ordinal).apply()
  }

  // Accent Color (v1.2.0)
  fun getAccentColor(context: Context): AccentColor {
    val ordinal = getPrefs(context).getInt(KEY_ACCENT_COLOR, AccentColor.DEFAULT.ordinal)
    return AccentColor.fromOrdinal(ordinal)
  }

  fun setAccentColor(context: Context, color: AccentColor) {
    getPrefs(context).edit().putInt(KEY_ACCENT_COLOR, color.ordinal).apply()
  }

  // Auto-reconnect Configuration
  fun isAutoReconnectEnabled(context: Context): Boolean {
    return getPrefs(context).getBoolean(KEY_AUTO_RECONNECT_ENABLED, true)
  }

  fun setAutoReconnectEnabled(context: Context, enabled: Boolean) {
    getPrefs(context).edit().putBoolean(KEY_AUTO_RECONNECT_ENABLED, enabled).apply()
  }

  fun getAutoReconnectDelay(context: Context): Int {
    return getPrefs(context).getInt(KEY_AUTO_RECONNECT_DELAY, DEFAULT_AUTO_RECONNECT_DELAY)
  }

  fun setAutoReconnectDelay(context: Context, delay: Int) {
    getPrefs(context).edit().putInt(KEY_AUTO_RECONNECT_DELAY, delay).apply()
  }

  fun getAutoReconnectMaxRetries(context: Context): Int {
    return getPrefs(context).getInt(KEY_AUTO_RECONNECT_MAX_RETRIES, DEFAULT_AUTO_RECONNECT_MAX_RETRIES)
  }

  fun setAutoReconnectMaxRetries(context: Context, retries: Int) {
    getPrefs(context).edit().putInt(KEY_AUTO_RECONNECT_MAX_RETRIES, retries).apply()
  }

  // Trusted Networks
  fun getTrustedNetworks(context: Context): Set<String> {
    return getPrefs(context).getStringSet(KEY_TRUSTED_NETWORKS, emptySet()) ?: emptySet()
  }

  fun setTrustedNetworks(context: Context, networks: Set<String>) {
    getPrefs(context).edit().putStringSet(KEY_TRUSTED_NETWORKS, networks).apply()
  }

  fun addTrustedNetwork(context: Context, ssid: String) {
    val networks = getTrustedNetworks(context).toMutableSet()
    networks.add(ssid)
    setTrustedNetworks(context, networks)
  }

  fun removeTrustedNetwork(context: Context, ssid: String) {
    val networks = getTrustedNetworks(context).toMutableSet()
    networks.remove(ssid)
    setTrustedNetworks(context, networks)
  }

  fun isTrustedNetwork(context: Context, ssid: String): Boolean {
    return getTrustedNetworks(context).contains(ssid)
  }
}
