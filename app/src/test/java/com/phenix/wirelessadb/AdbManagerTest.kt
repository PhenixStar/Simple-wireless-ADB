package com.phenix.wirelessadb

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AdbManager.
 *
 * Note: AdbManager's core methods (enable, disable, getStatus) require root
 * access and shell commands that can't easily be tested without a real device.
 * These tests focus on the pure logic methods.
 */
class AdbManagerTest {

  // ============================================================
  // validatePort() tests - Pure logic, no mocking needed
  // ============================================================

  @Test
  fun `validatePort returns true for minimum valid port`() {
    assertTrue(AdbManager.validatePort(1024))
  }

  @Test
  fun `validatePort returns true for maximum valid port`() {
    assertTrue(AdbManager.validatePort(65535))
  }

  @Test
  fun `validatePort returns true for default ADB port 5555`() {
    assertTrue(AdbManager.validatePort(5555))
  }

  @Test
  fun `validatePort returns true for common ports`() {
    assertTrue(AdbManager.validatePort(5556)) // Relay port
    assertTrue(AdbManager.validatePort(5557)) // Warpgate local
    assertTrue(AdbManager.validatePort(8080))
    assertTrue(AdbManager.validatePort(9999))
  }

  @Test
  fun `validatePort returns false for port below minimum`() {
    assertFalse(AdbManager.validatePort(1023))
    assertFalse(AdbManager.validatePort(1000))
    assertFalse(AdbManager.validatePort(80))
    assertFalse(AdbManager.validatePort(1))
    assertFalse(AdbManager.validatePort(0))
  }

  @Test
  fun `validatePort returns false for negative port`() {
    assertFalse(AdbManager.validatePort(-1))
    assertFalse(AdbManager.validatePort(-5555))
    assertFalse(AdbManager.validatePort(Int.MIN_VALUE))
  }

  @Test
  fun `validatePort returns false for port above maximum`() {
    assertFalse(AdbManager.validatePort(65536))
    assertFalse(AdbManager.validatePort(70000))
    assertFalse(AdbManager.validatePort(Int.MAX_VALUE))
  }

  // ============================================================
  // AdbStatus data class tests
  // ============================================================

  @Test
  fun `AdbStatus default values are correct`() {
    val status = AdbManager.AdbStatus(
      enabled = true,
      port = 5555,
      ip = "192.168.1.100"
    )

    assertTrue(status.enabled)
    assertEquals(5555, status.port)
    assertEquals("192.168.1.100", status.ip)
    assertNull(status.tailscaleIp)
    assertFalse(status.relayEnabled)
    assertEquals(5556, status.relayPort)
    assertEquals(0, status.trustedDeviceCount)
    assertNull(status.pendingApproval)
  }

  @Test
  fun `AdbStatus with all fields populated`() {
    val status = AdbManager.AdbStatus(
      enabled = true,
      port = 5557,
      ip = "192.168.1.100",
      tailscaleIp = "100.64.0.1",
      relayEnabled = true,
      relayPort = 5558,
      trustedDeviceCount = 5,
      pendingApproval = "192.168.1.101"
    )

    assertTrue(status.enabled)
    assertEquals(5557, status.port)
    assertEquals("192.168.1.100", status.ip)
    assertEquals("100.64.0.1", status.tailscaleIp)
    assertTrue(status.relayEnabled)
    assertEquals(5558, status.relayPort)
    assertEquals(5, status.trustedDeviceCount)
    assertEquals("192.168.1.101", status.pendingApproval)
  }

  @Test
  fun `AdbStatus disabled state`() {
    val status = AdbManager.AdbStatus(
      enabled = false,
      port = 5555,
      ip = null
    )

    assertFalse(status.enabled)
    assertNull(status.ip)
  }

  @Test
  fun `AdbStatus copy works correctly`() {
    val original = AdbManager.AdbStatus(
      enabled = true,
      port = 5555,
      ip = "192.168.1.100"
    )

    val updated = original.copy(enabled = false, ip = null)

    assertFalse(updated.enabled)
    assertNull(updated.ip)
    assertEquals(original.port, updated.port)
  }

  // ============================================================
  // Port boundary tests
  // ============================================================

  @Test
  fun `validatePort at exact boundaries`() {
    // At boundary
    assertTrue(AdbManager.validatePort(1024))
    assertTrue(AdbManager.validatePort(65535))

    // Just outside boundary
    assertFalse(AdbManager.validatePort(1023))
    assertFalse(AdbManager.validatePort(65536))
  }

  @Test
  fun `validatePort for well-known ports is false`() {
    // Well-known ports (0-1023) should be invalid
    assertFalse(AdbManager.validatePort(22))   // SSH
    assertFalse(AdbManager.validatePort(80))   // HTTP
    assertFalse(AdbManager.validatePort(443))  // HTTPS
    assertFalse(AdbManager.validatePort(21))   // FTP
  }

  @Test
  fun `validatePort for registered ports is true`() {
    // Registered ports (1024-49151) should be valid
    assertTrue(AdbManager.validatePort(3000))
    assertTrue(AdbManager.validatePort(8080))
    assertTrue(AdbManager.validatePort(8443))
  }

  @Test
  fun `validatePort for dynamic ports is true`() {
    // Dynamic/ephemeral ports (49152-65535) should be valid
    assertTrue(AdbManager.validatePort(49152))
    assertTrue(AdbManager.validatePort(50000))
    assertTrue(AdbManager.validatePort(60000))
  }
}
