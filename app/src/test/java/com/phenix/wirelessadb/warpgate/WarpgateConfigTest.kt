package com.phenix.wirelessadb.warpgate

import org.junit.Assert.*
import org.junit.Test

class WarpgateConfigTest {

  // ============================================================
  // Configuration validation tests
  // ============================================================

  @Test
  fun `isValid returns true for valid configuration`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "warpgate.example.com",
      port = 8443,
      username = "user",
      password = "pass",
      targetName = "adb",
      localPort = 5557
    )
    assertTrue("Valid config should pass validation", config.isValid())
  }

  @Test
  fun `isValid returns false for empty host`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "",
      username = "user"
    )
    assertFalse("Empty host should fail validation", config.isValid())
  }

  @Test
  fun `isValid returns false for blank host`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "   ",
      username = "user"
    )
    assertFalse("Blank host should fail validation", config.isValid())
  }

  @Test
  fun `isValid returns false for empty username`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "warpgate.example.com",
      username = ""
    )
    assertFalse("Empty username should fail validation", config.isValid())
  }

  @Test
  fun `isValid returns false for blank username`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "warpgate.example.com",
      username = "   "
    )
    assertFalse("Blank username should fail validation", config.isValid())
  }

  @Test
  fun `isValid returns false for port 0`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "warpgate.example.com",
      port = 0,
      username = "user"
    )
    assertFalse("Port 0 should fail validation", config.isValid())
  }

  @Test
  fun `isValid returns false for negative port`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "warpgate.example.com",
      port = -1,
      username = "user"
    )
    assertFalse("Negative port should fail validation", config.isValid())
  }

  @Test
  fun `isValid returns false for port above 65535`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "warpgate.example.com",
      port = 65536,
      username = "user"
    )
    assertFalse("Port > 65535 should fail validation", config.isValid())
  }

  @Test
  fun `isValid accepts port 1`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "warpgate.example.com",
      port = 1,
      username = "user"
    )
    assertTrue("Port 1 should be valid", config.isValid())
  }

  @Test
  fun `isValid accepts port 65535`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "warpgate.example.com",
      port = 65535,
      username = "user"
    )
    assertTrue("Port 65535 should be valid", config.isValid())
  }

  @Test
  fun `isValid returns false for localPort below 1024`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "warpgate.example.com",
      username = "user",
      localPort = 1023
    )
    assertFalse("localPort < 1024 should fail validation", config.isValid())
  }

  @Test
  fun `isValid accepts localPort 1024`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "warpgate.example.com",
      username = "user",
      localPort = 1024
    )
    assertTrue("localPort 1024 should be valid", config.isValid())
  }

  @Test
  fun `isValid accepts localPort 65535`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "warpgate.example.com",
      username = "user",
      localPort = 65535
    )
    assertTrue("localPort 65535 should be valid", config.isValid())
  }

  // ============================================================
  // getFullUsername() tests
  // ============================================================

  @Test
  fun `getFullUsername returns username with target`() {
    val config = WarpgateConfig(
      username = "myuser",
      targetName = "adb"
    )
    assertEquals("myuser#adb", config.getFullUsername())
  }

  @Test
  fun `getFullUsername returns username only when target is empty`() {
    val config = WarpgateConfig(
      username = "myuser",
      targetName = ""
    )
    assertEquals("myuser", config.getFullUsername())
  }

  @Test
  fun `getFullUsername handles custom target name`() {
    val config = WarpgateConfig(
      username = "admin",
      targetName = "android-device"
    )
    assertEquals("admin#android-device", config.getFullUsername())
  }

  // ============================================================
  // getAdbCommand() tests
  // ============================================================

  @Test
  fun `getAdbCommand returns correct format with default port`() {
    val config = WarpgateConfig(
      localPort = WarpgateConfig.DEFAULT_LOCAL_PORT
    )
    assertEquals("adb connect localhost:5557", config.getAdbCommand())
  }

  @Test
  fun `getAdbCommand returns correct format with custom port`() {
    val config = WarpgateConfig(
      localPort = 9999
    )
    assertEquals("adb connect localhost:9999", config.getAdbCommand())
  }

  // ============================================================
  // Default values tests
  // ============================================================

  @Test
  fun `default config has correct values`() {
    val config = WarpgateConfig()

    assertFalse("enabled should default to false", config.enabled)
    assertEquals("host should default to empty", "", config.host)
    assertEquals("port should default to 8443", WarpgateConfig.DEFAULT_PORT, config.port)
    assertEquals("username should default to empty", "", config.username)
    assertEquals("password should default to empty", "", config.password)
    assertEquals("targetName should default to adb", "adb", config.targetName)
    assertEquals("localPort should default to 5557", WarpgateConfig.DEFAULT_LOCAL_PORT, config.localPort)
  }

  @Test
  fun `DEFAULT_PORT constant is 8443`() {
    assertEquals(8443, WarpgateConfig.DEFAULT_PORT)
  }

  @Test
  fun `DEFAULT_LOCAL_PORT constant is 5557`() {
    assertEquals(5557, WarpgateConfig.DEFAULT_LOCAL_PORT)
  }

  // ============================================================
  // Edge cases and security tests
  // ============================================================

  @Test
  fun `isValid allows password to be empty`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "warpgate.example.com",
      username = "user",
      password = ""
    )
    assertTrue("Empty password should be valid (may use key auth)", config.isValid())
  }

  @Test
  fun `config handles special characters in username`() {
    val config = WarpgateConfig(
      username = "user@domain.com",
      targetName = "adb"
    )
    assertEquals("user@domain.com#adb", config.getFullUsername())
  }

  @Test
  fun `config handles special characters in target name`() {
    val config = WarpgateConfig(
      username = "user",
      targetName = "device-123_test"
    )
    assertEquals("user#device-123_test", config.getFullUsername())
  }

  @Test
  fun `config with IPv4 address as host is valid`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "192.168.1.100",
      username = "user"
    )
    assertTrue("IPv4 address should be valid host", config.isValid())
  }

  @Test
  fun `config with IPv6 address as host is valid`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "2001:db8::1",
      username = "user"
    )
    assertTrue("IPv6 address should be valid host", config.isValid())
  }

  @Test
  fun `config with domain name as host is valid`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "warpgate.example.com",
      username = "user"
    )
    assertTrue("Domain name should be valid host", config.isValid())
  }
}
