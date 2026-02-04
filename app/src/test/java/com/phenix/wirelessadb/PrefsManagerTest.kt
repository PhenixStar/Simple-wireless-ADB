package com.phenix.wirelessadb

import android.content.Context
import android.content.SharedPreferences
import com.phenix.wirelessadb.model.ConnectionMode
import com.phenix.wirelessadb.theme.AccentColor
import com.phenix.wirelessadb.theme.ThemeMode
import com.phenix.wirelessadb.warpgate.WarpgateConfig
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PrefsManagerTest {

  private lateinit var mockContext: Context
  private lateinit var mockPrefs: SharedPreferences
  private lateinit var mockEditor: SharedPreferences.Editor

  @Before
  fun setup() {
    mockContext = mockk(relaxed = true)
    mockPrefs = mockk(relaxed = true)
    mockEditor = mockk(relaxed = true)

    every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
    every { mockPrefs.edit() } returns mockEditor
    every { mockEditor.putBoolean(any(), any()) } returns mockEditor
    every { mockEditor.putInt(any(), any()) } returns mockEditor
    every { mockEditor.putString(any(), any()) } returns mockEditor
    every { mockEditor.apply() } just Runs
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  // ============================================================
  // Enable on Boot tests
  // ============================================================

  @Test
  fun `isEnableOnBoot returns stored value`() {
    every { mockPrefs.getBoolean("enable_on_boot", false) } returns true
    assertTrue(PrefsManager.isEnableOnBoot(mockContext))
  }

  @Test
  fun `isEnableOnBoot returns false when not set`() {
    every { mockPrefs.getBoolean("enable_on_boot", false) } returns false
    assertFalse(PrefsManager.isEnableOnBoot(mockContext))
  }

  @Test
  fun `setEnableOnBoot saves boolean to prefs`() {
    PrefsManager.setEnableOnBoot(mockContext, true)

    verify { mockEditor.putBoolean("enable_on_boot", true) }
    verify { mockEditor.apply() }
  }

  // ============================================================
  // Port tests
  // ============================================================

  @Test
  fun `getPort returns stored port`() {
    every { mockPrefs.getInt("port", 5555) } returns 5556
    assertEquals(5556, PrefsManager.getPort(mockContext))
  }

  @Test
  fun `getPort returns default 5555 when not set`() {
    every { mockPrefs.getInt("port", 5555) } returns 5555
    assertEquals(5555, PrefsManager.getPort(mockContext))
  }

  @Test
  fun `setPort saves port to prefs`() {
    PrefsManager.setPort(mockContext, 5557)

    verify { mockEditor.putInt("port", 5557) }
    verify { mockEditor.apply() }
  }

  // ============================================================
  // Relay tests
  // ============================================================

  @Test
  fun `isRelayEnabled returns stored value`() {
    every { mockPrefs.getBoolean("relay_enabled", false) } returns true
    assertTrue(PrefsManager.isRelayEnabled(mockContext))
  }

  @Test
  fun `setRelayEnabled saves to prefs`() {
    PrefsManager.setRelayEnabled(mockContext, true)

    verify { mockEditor.putBoolean("relay_enabled", true) }
    verify { mockEditor.apply() }
  }

  @Test
  fun `getRelayPort returns stored port`() {
    every { mockPrefs.getInt("relay_port", 5556) } returns 5560
    assertEquals(5560, PrefsManager.getRelayPort(mockContext))
  }

  @Test
  fun `getRelayPort returns default 5556`() {
    every { mockPrefs.getInt("relay_port", 5556) } returns 5556
    assertEquals(5556, PrefsManager.getRelayPort(mockContext))
  }

  @Test
  fun `setRelayPort saves to prefs`() {
    PrefsManager.setRelayPort(mockContext, 5560)

    verify { mockEditor.putInt("relay_port", 5560) }
    verify { mockEditor.apply() }
  }

  // ============================================================
  // Connection Mode tests
  // ============================================================

  @Test
  fun `getConnectionMode returns stored mode`() {
    every { mockPrefs.getString("connection_mode", any()) } returns "TAILSCALE_DIRECT"
    assertEquals(ConnectionMode.TAILSCALE_DIRECT, PrefsManager.getConnectionMode(mockContext))
  }

  @Test
  fun `getConnectionMode returns LOCAL_WIFI as default`() {
    every { mockPrefs.getString("connection_mode", any()) } returns null
    assertEquals(ConnectionMode.LOCAL_WIFI, PrefsManager.getConnectionMode(mockContext))
  }

  @Test
  fun `getConnectionMode handles invalid value`() {
    every { mockPrefs.getString("connection_mode", any()) } returns "INVALID_MODE"
    assertEquals(ConnectionMode.LOCAL_WIFI, PrefsManager.getConnectionMode(mockContext))
  }

  @Test
  fun `setConnectionMode saves mode name to prefs`() {
    PrefsManager.setConnectionMode(mockContext, ConnectionMode.P2P_TOKEN)

    verify { mockEditor.putString("connection_mode", "P2P_TOKEN") }
    verify { mockEditor.apply() }
  }

  // ============================================================
  // Hide Dev Notification tests
  // ============================================================

  @Test
  fun `isHideDevNotification returns stored value`() {
    every { mockPrefs.getBoolean("hide_dev_notification", false) } returns true
    assertTrue(PrefsManager.isHideDevNotification(mockContext))
  }

  @Test
  fun `setHideDevNotification saves to prefs`() {
    PrefsManager.setHideDevNotification(mockContext, true)

    verify { mockEditor.putBoolean("hide_dev_notification", true) }
    verify { mockEditor.apply() }
  }

  // ============================================================
  // Theme Mode tests
  // ============================================================

  @Test
  fun `getThemeMode returns stored mode`() {
    every { mockPrefs.getInt("theme_mode", any()) } returns ThemeMode.DARK.ordinal
    assertEquals(ThemeMode.DARK, PrefsManager.getThemeMode(mockContext))
  }

  @Test
  fun `getThemeMode returns SYSTEM as default`() {
    every { mockPrefs.getInt("theme_mode", any()) } returns ThemeMode.SYSTEM.ordinal
    assertEquals(ThemeMode.SYSTEM, PrefsManager.getThemeMode(mockContext))
  }

  @Test
  fun `setThemeMode saves ordinal to prefs`() {
    PrefsManager.setThemeMode(mockContext, ThemeMode.LIGHT)

    verify { mockEditor.putInt("theme_mode", ThemeMode.LIGHT.ordinal) }
    verify { mockEditor.apply() }
  }

  // ============================================================
  // Accent Color tests
  // ============================================================

  @Test
  fun `getAccentColor returns stored color`() {
    every { mockPrefs.getInt("accent_color", any()) } returns AccentColor.ORANGE.ordinal
    assertEquals(AccentColor.ORANGE, PrefsManager.getAccentColor(mockContext))
  }

  @Test
  fun `setAccentColor saves ordinal to prefs`() {
    PrefsManager.setAccentColor(mockContext, AccentColor.PURPLE)

    verify { mockEditor.putInt("accent_color", AccentColor.PURPLE.ordinal) }
    verify { mockEditor.apply() }
  }

  // ============================================================
  // Warpgate Config tests
  // ============================================================

  @Test
  fun `getWarpgateConfig returns default config when not set`() {
    every { mockPrefs.getBoolean("warpgate_enabled", false) } returns false
    every { mockPrefs.getString("warpgate_host", "") } returns ""
    every { mockPrefs.getInt("warpgate_port", WarpgateConfig.DEFAULT_PORT) } returns WarpgateConfig.DEFAULT_PORT
    every { mockPrefs.getString("warpgate_username", "") } returns ""
    every { mockPrefs.getString("warpgate_password", "") } returns ""
    every { mockPrefs.getString("warpgate_target", "adb") } returns "adb"
    every { mockPrefs.getInt("warpgate_local_port", WarpgateConfig.DEFAULT_LOCAL_PORT) } returns WarpgateConfig.DEFAULT_LOCAL_PORT

    val config = PrefsManager.getWarpgateConfig(mockContext)

    assertFalse(config.enabled)
    assertEquals("", config.host)
    assertEquals(WarpgateConfig.DEFAULT_PORT, config.port)
    assertEquals("", config.username)
    assertEquals("", config.password)
    assertEquals("adb", config.targetName)
    assertEquals(WarpgateConfig.DEFAULT_LOCAL_PORT, config.localPort)
  }

  @Test
  fun `getWarpgateConfig returns stored config`() {
    every { mockPrefs.getBoolean("warpgate_enabled", false) } returns true
    every { mockPrefs.getString("warpgate_host", "") } returns "warpgate.example.com"
    every { mockPrefs.getInt("warpgate_port", WarpgateConfig.DEFAULT_PORT) } returns 8443
    every { mockPrefs.getString("warpgate_username", "") } returns "user"
    every { mockPrefs.getString("warpgate_password", "") } returns "pass"
    every { mockPrefs.getString("warpgate_target", "adb") } returns "my-device"
    every { mockPrefs.getInt("warpgate_local_port", WarpgateConfig.DEFAULT_LOCAL_PORT) } returns 5558

    val config = PrefsManager.getWarpgateConfig(mockContext)

    assertTrue(config.enabled)
    assertEquals("warpgate.example.com", config.host)
    assertEquals(8443, config.port)
    assertEquals("user", config.username)
    assertEquals("pass", config.password)
    assertEquals("my-device", config.targetName)
    assertEquals(5558, config.localPort)
  }

  @Test
  fun `setWarpgateConfig saves all fields`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "test.example.com",
      port = 9000,
      username = "testuser",
      password = "testpass",
      targetName = "testtarget",
      localPort = 5559
    )

    PrefsManager.setWarpgateConfig(mockContext, config)

    verify { mockEditor.putBoolean("warpgate_enabled", true) }
    verify { mockEditor.putString("warpgate_host", "test.example.com") }
    verify { mockEditor.putInt("warpgate_port", 9000) }
    verify { mockEditor.putString("warpgate_username", "testuser") }
    verify { mockEditor.putString("warpgate_password", "testpass") }
    verify { mockEditor.putString("warpgate_target", "testtarget") }
    verify { mockEditor.putInt("warpgate_local_port", 5559) }
    verify { mockEditor.apply() }
  }

  @Test
  fun `setWarpgateEnabled saves boolean`() {
    PrefsManager.setWarpgateEnabled(mockContext, true)

    verify { mockEditor.putBoolean("warpgate_enabled", true) }
    verify { mockEditor.apply() }
  }

  @Test
  fun `isWarpgateEnabled returns stored value`() {
    every { mockPrefs.getBoolean("warpgate_enabled", false) } returns true
    assertTrue(PrefsManager.isWarpgateEnabled(mockContext))
  }
}
