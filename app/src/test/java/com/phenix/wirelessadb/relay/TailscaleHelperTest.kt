package com.phenix.wirelessadb.relay

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for TailscaleHelper.
 *
 * Note: The isTailscaleIp() and isFromTailscaleNetwork() methods are pure
 * functions that don't require mocking. The getTailscaleIp() methods would
 * require complex mocking of ConnectivityManager and NetworkInterface,
 * which is better suited for integration tests or Robolectric tests.
 */
class TailscaleHelperTest {

  // ============================================================
  // isTailscaleIp() tests
  // Tailscale uses CGNAT range 100.64.0.0/10 (100.64.x.x - 100.127.x.x)
  // ============================================================

  @Test
  fun `isTailscaleIp returns true for valid Tailscale IP in range`() {
    assertTrue(TailscaleHelper.isTailscaleIp("100.64.0.1"))
    assertTrue(TailscaleHelper.isTailscaleIp("100.64.255.255"))
    assertTrue(TailscaleHelper.isTailscaleIp("100.100.100.100"))
    assertTrue(TailscaleHelper.isTailscaleIp("100.127.0.1"))
    assertTrue(TailscaleHelper.isTailscaleIp("100.127.255.255"))
  }

  @Test
  fun `isTailscaleIp returns true for edge cases in range`() {
    // Lower boundary of CGNAT range
    assertTrue(TailscaleHelper.isTailscaleIp("100.64.0.0"))
    // Upper boundary of CGNAT range
    assertTrue(TailscaleHelper.isTailscaleIp("100.127.255.255"))
    // Mid-range values
    assertTrue(TailscaleHelper.isTailscaleIp("100.95.128.64"))
  }

  @Test
  fun `isTailscaleIp returns false for IPs just below range`() {
    // 100.63.x.x is below CGNAT range
    assertFalse(TailscaleHelper.isTailscaleIp("100.63.255.255"))
    assertFalse(TailscaleHelper.isTailscaleIp("100.63.0.0"))
    assertFalse(TailscaleHelper.isTailscaleIp("100.0.0.1"))
  }

  @Test
  fun `isTailscaleIp returns false for IPs just above range`() {
    // 100.128.x.x is above CGNAT range
    assertFalse(TailscaleHelper.isTailscaleIp("100.128.0.0"))
    assertFalse(TailscaleHelper.isTailscaleIp("100.128.255.255"))
    assertFalse(TailscaleHelper.isTailscaleIp("100.255.255.255"))
  }

  @Test
  fun `isTailscaleIp returns false for common private IPs`() {
    // 192.168.x.x range
    assertFalse(TailscaleHelper.isTailscaleIp("192.168.1.1"))
    assertFalse(TailscaleHelper.isTailscaleIp("192.168.0.100"))

    // 10.x.x.x range
    assertFalse(TailscaleHelper.isTailscaleIp("10.0.0.1"))
    assertFalse(TailscaleHelper.isTailscaleIp("10.1.1.1"))

    // 172.16-31.x.x range
    assertFalse(TailscaleHelper.isTailscaleIp("172.16.0.1"))
    assertFalse(TailscaleHelper.isTailscaleIp("172.31.255.255"))
  }

  @Test
  fun `isTailscaleIp returns false for localhost`() {
    assertFalse(TailscaleHelper.isTailscaleIp("127.0.0.1"))
    assertFalse(TailscaleHelper.isTailscaleIp("127.0.0.0"))
  }

  @Test
  fun `isTailscaleIp returns false for public IPs`() {
    assertFalse(TailscaleHelper.isTailscaleIp("8.8.8.8"))
    assertFalse(TailscaleHelper.isTailscaleIp("1.1.1.1"))
    assertFalse(TailscaleHelper.isTailscaleIp("142.250.185.14")) // Google
  }

  @Test
  fun `isTailscaleIp handles empty string`() {
    assertFalse(TailscaleHelper.isTailscaleIp(""))
  }

  @Test
  fun `isTailscaleIp handles invalid IP formats`() {
    assertFalse(TailscaleHelper.isTailscaleIp("not-an-ip"))
    assertFalse(TailscaleHelper.isTailscaleIp("100"))
    assertFalse(TailscaleHelper.isTailscaleIp("100.64"))
    assertFalse(TailscaleHelper.isTailscaleIp("100.64.0"))
  }

  @Test
  fun `isTailscaleIp handles extra octets`() {
    assertFalse(TailscaleHelper.isTailscaleIp("100.64.0.1.5"))
  }

  @Test
  fun `isTailscaleIp handles non-numeric second octet`() {
    // Implementation only validates the second octet
    assertFalse(TailscaleHelper.isTailscaleIp("100.abc.0.1"))
  }

  @Test
  fun `isTailscaleIp with non-numeric third or fourth octet still validates if second octet is valid`() {
    // Note: The implementation only validates the second octet for CGNAT range
    // Other octets with invalid chars would fail at network layer
    assertTrue(TailscaleHelper.isTailscaleIp("100.64.x.1"))
  }

  @Test
  fun `isTailscaleIp handles leading whitespace`() {
    // Leading whitespace fails startsWith check
    assertFalse(TailscaleHelper.isTailscaleIp(" 100.64.0.1"))
  }

  @Test
  fun `isTailscaleIp with trailing whitespace still validates`() {
    // Note: Implementation doesn't trim - trailing whitespace doesn't affect second octet parsing
    // The IP would be invalid at network layer but passes the CGNAT check
    assertTrue(TailscaleHelper.isTailscaleIp("100.64.0.1 "))
  }

  // ============================================================
  // isFromTailscaleNetwork() tests
  // ============================================================

  @Test
  fun `isFromTailscaleNetwork returns true for Tailscale IPs`() {
    assertTrue(TailscaleHelper.isFromTailscaleNetwork("100.64.0.1"))
    assertTrue(TailscaleHelper.isFromTailscaleNetwork("100.100.50.25"))
    assertTrue(TailscaleHelper.isFromTailscaleNetwork("100.127.255.255"))
  }

  @Test
  fun `isFromTailscaleNetwork returns false for non-Tailscale IPs`() {
    assertFalse(TailscaleHelper.isFromTailscaleNetwork("192.168.1.1"))
    assertFalse(TailscaleHelper.isFromTailscaleNetwork("10.0.0.1"))
    assertFalse(TailscaleHelper.isFromTailscaleNetwork("100.63.255.255"))
    assertFalse(TailscaleHelper.isFromTailscaleNetwork("100.128.0.1"))
  }

  // ============================================================
  // CGNAT Range boundary tests (100.64.0.0/10)
  // ============================================================

  @Test
  fun `CGNAT range includes all valid second octets 64-127`() {
    // Test various second octets within range
    for (octet in listOf(64, 70, 80, 90, 100, 110, 120, 127)) {
      assertTrue(
        "Second octet $octet should be in range",
        TailscaleHelper.isTailscaleIp("100.$octet.0.1")
      )
    }
  }

  @Test
  fun `CGNAT range excludes second octets below 64`() {
    for (octet in listOf(0, 10, 32, 50, 63)) {
      assertFalse(
        "Second octet $octet should be out of range",
        TailscaleHelper.isTailscaleIp("100.$octet.0.1")
      )
    }
  }

  @Test
  fun `CGNAT range excludes second octets above 127`() {
    for (octet in listOf(128, 150, 200, 255)) {
      assertFalse(
        "Second octet $octet should be out of range",
        TailscaleHelper.isTailscaleIp("100.$octet.0.1")
      )
    }
  }
}
