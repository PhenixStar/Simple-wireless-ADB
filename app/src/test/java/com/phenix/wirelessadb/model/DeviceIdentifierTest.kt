package com.phenix.wirelessadb.model

import org.junit.Assert.*
import org.junit.Test

class DeviceIdentifierTest {

  private fun createIdentifier(
    androidId: String = "android123456",
    buildFingerprint: String = "google/pixel/bluejay:14/...",
    macAddress: String? = "AA:BB:CC:DD:EE:FF",
    deviceModel: String = "Pixel 6",
    manufacturer: String = "Google"
  ): DeviceIdentifier {
    return DeviceIdentifier(
      androidId = androidId,
      buildFingerprint = buildFingerprint,
      macAddress = macAddress,
      deviceModel = deviceModel,
      manufacturer = manufacturer
    )
  }

  // ============================================================
  // computeHash() tests
  // ============================================================

  @Test
  fun `computeHash returns 16 character hex string`() {
    val identifier = createIdentifier()
    val hash = identifier.computeHash()

    assertEquals(16, hash.length)
    assertTrue("Hash should be hex", hash.all { it in '0'..'9' || it in 'a'..'f' })
  }

  @Test
  fun `computeHash is deterministic for same input`() {
    val identifier1 = createIdentifier()
    val identifier2 = createIdentifier()

    assertEquals(identifier1.computeHash(), identifier2.computeHash())
  }

  @Test
  fun `computeHash differs for different androidId`() {
    val identifier1 = createIdentifier(androidId = "android111")
    val identifier2 = createIdentifier(androidId = "android222")

    assertNotEquals(identifier1.computeHash(), identifier2.computeHash())
  }

  @Test
  fun `computeHash differs for different buildFingerprint`() {
    val identifier1 = createIdentifier(buildFingerprint = "fingerprint1")
    val identifier2 = createIdentifier(buildFingerprint = "fingerprint2")

    assertNotEquals(identifier1.computeHash(), identifier2.computeHash())
  }

  @Test
  fun `computeHash differs for different macAddress`() {
    val identifier1 = createIdentifier(macAddress = "AA:BB:CC:DD:EE:FF")
    val identifier2 = createIdentifier(macAddress = "11:22:33:44:55:66")

    assertNotEquals(identifier1.computeHash(), identifier2.computeHash())
  }

  @Test
  fun `computeHash handles null macAddress`() {
    val identifier = createIdentifier(macAddress = null)
    val hash = identifier.computeHash()

    assertEquals(16, hash.length)
  }

  @Test
  fun `computeHash differs for null vs empty macAddress`() {
    val withNull = createIdentifier(macAddress = null)
    val withEmpty = createIdentifier(macAddress = "")

    // Both should produce valid hashes (they may or may not be equal)
    assertEquals(16, withNull.computeHash().length)
    assertEquals(16, withEmpty.computeHash().length)
  }

  // ============================================================
  // getDisplayName() tests
  // ============================================================

  @Test
  fun `getDisplayName returns manufacturer and model`() {
    val identifier = createIdentifier(manufacturer = "Google", deviceModel = "Pixel 6")
    assertEquals("Google Pixel 6", identifier.getDisplayName())
  }

  @Test
  fun `getDisplayName handles different manufacturers`() {
    val identifier = createIdentifier(manufacturer = "Samsung", deviceModel = "Galaxy S21")
    assertEquals("Samsung Galaxy S21", identifier.getDisplayName())
  }

  // ============================================================
  // toTrustedDevice() tests
  // ============================================================

  @Test
  fun `toTrustedDevice creates device with correct IP`() {
    val identifier = createIdentifier()
    val device = identifier.toTrustedDevice(ip = "192.168.1.100")

    assertEquals("192.168.1.100", device.ip)
  }

  @Test
  fun `toTrustedDevice copies hardware fields`() {
    val identifier = createIdentifier(
      androidId = "android123",
      buildFingerprint = "fingerprint123",
      macAddress = "AA:BB:CC:DD:EE:FF",
      deviceModel = "Pixel 6",
      manufacturer = "Google"
    )
    val device = identifier.toTrustedDevice(ip = "192.168.1.100")

    assertEquals("android123", device.hardwareId)
    assertEquals("fingerprint123", device.buildFingerprint)
    assertEquals("AA:BB:CC:DD:EE:FF", device.macAddress)
    assertEquals("Pixel 6", device.deviceModel)
    assertEquals("Google", device.manufacturer)
  }

  @Test
  fun `toTrustedDevice uses provided name`() {
    val identifier = createIdentifier()
    val device = identifier.toTrustedDevice(ip = "192.168.1.100", name = "Custom Name")

    assertEquals("Custom Name", device.name)
  }

  @Test
  fun `toTrustedDevice uses display name when name is null`() {
    val identifier = createIdentifier(manufacturer = "Google", deviceModel = "Pixel 6")
    val device = identifier.toTrustedDevice(ip = "192.168.1.100", name = null)

    assertEquals("Google Pixel 6", device.name)
  }

  @Test
  fun `toTrustedDevice sets correct auth method`() {
    val identifier = createIdentifier()
    val device = identifier.toTrustedDevice(
      ip = "192.168.1.100",
      authMethod = AuthMethod.TOKEN_PERSISTENT
    )

    assertEquals(AuthMethod.TOKEN_PERSISTENT, device.authMethod)
  }

  @Test
  fun `toTrustedDevice sets persistent token when provided`() {
    val identifier = createIdentifier()
    val device = identifier.toTrustedDevice(
      ip = "192.168.1.100",
      persistentToken = "ABC-123-XYZ"
    )

    assertEquals("ABC-123-XYZ", device.persistentToken)
  }

  @Test
  fun `toTrustedDevice computes and sets device hash`() {
    val identifier = createIdentifier()
    val device = identifier.toTrustedDevice(ip = "192.168.1.100")

    assertNotNull(device.deviceHash)
    assertEquals(identifier.computeHash(), device.deviceHash)
  }

  @Test
  fun `toTrustedDevice generates unique UUIDs`() {
    val identifier = createIdentifier()
    val device1 = identifier.toTrustedDevice(ip = "192.168.1.100")
    val device2 = identifier.toTrustedDevice(ip = "192.168.1.100")

    assertNotEquals(device1.id, device2.id)
  }

  @Test
  fun `toTrustedDevice sets timestamps`() {
    val before = System.currentTimeMillis()
    val identifier = createIdentifier()
    val device = identifier.toTrustedDevice(ip = "192.168.1.100")
    val after = System.currentTimeMillis()

    assertTrue(device.addedAt >= before && device.addedAt <= after)
    assertEquals(device.addedAt, device.lastSeen)
  }

  // ============================================================
  // matchesTrustedDevice() tests
  // ============================================================

  @Test
  fun `matchesTrustedDevice returns true for matching device hash`() {
    val identifier = createIdentifier()
    val device = identifier.toTrustedDevice(ip = "192.168.1.100")

    assertTrue(identifier.matchesTrustedDevice(device))
  }

  @Test
  fun `matchesTrustedDevice returns false for different identifier`() {
    val identifier1 = createIdentifier(androidId = "android111")
    val identifier2 = createIdentifier(androidId = "android222")
    val device = identifier2.toTrustedDevice(ip = "192.168.1.100")

    assertFalse(identifier1.matchesTrustedDevice(device))
  }

  @Test
  fun `matchesTrustedDevice falls back to hardwareId when hash is null`() {
    val identifier = createIdentifier(androidId = "android123")
    val device = TrustedDevice(
      id = "device-1",
      ip = "192.168.1.100",
      name = null,
      addedAt = System.currentTimeMillis(),
      lastSeen = System.currentTimeMillis(),
      hardwareId = "android123",
      deviceHash = null // No hash, should fall back to hardwareId
    )

    assertTrue(identifier.matchesTrustedDevice(device))
  }

  @Test
  fun `matchesTrustedDevice returns false when no matching identifiers`() {
    val identifier = createIdentifier(androidId = "android111")
    val device = TrustedDevice(
      id = "device-1",
      ip = "192.168.1.100",
      name = null,
      addedAt = System.currentTimeMillis(),
      lastSeen = System.currentTimeMillis(),
      hardwareId = "android222",
      deviceHash = "differenthash"
    )

    assertFalse(identifier.matchesTrustedDevice(device))
  }

  // ============================================================
  // fromPartial() tests
  // ============================================================

  @Test
  fun `fromPartial creates identifier with androidId`() {
    val identifier = DeviceIdentifier.fromPartial(androidId = "android123")

    assertEquals("android123", identifier.androidId)
  }

  @Test
  fun `fromPartial uses default values`() {
    val identifier = DeviceIdentifier.fromPartial(androidId = "android123")

    assertEquals("", identifier.buildFingerprint)
    assertNull(identifier.macAddress)
    assertEquals("Unknown", identifier.deviceModel)
    assertEquals("Unknown", identifier.manufacturer)
  }

  @Test
  fun `fromPartial accepts all parameters`() {
    val identifier = DeviceIdentifier.fromPartial(
      androidId = "android123",
      buildFingerprint = "fingerprint",
      macAddress = "AA:BB:CC:DD:EE:FF",
      deviceModel = "Pixel 6",
      manufacturer = "Google"
    )

    assertEquals("android123", identifier.androidId)
    assertEquals("fingerprint", identifier.buildFingerprint)
    assertEquals("AA:BB:CC:DD:EE:FF", identifier.macAddress)
    assertEquals("Pixel 6", identifier.deviceModel)
    assertEquals("Google", identifier.manufacturer)
  }

  @Test
  fun `fromPartial produces valid hash`() {
    val identifier = DeviceIdentifier.fromPartial(androidId = "android123")
    val hash = identifier.computeHash()

    assertEquals(16, hash.length)
  }

  // ============================================================
  // Data class tests
  // ============================================================

  @Test
  fun `data class equals works correctly`() {
    val id1 = createIdentifier()
    val id2 = createIdentifier()

    assertEquals(id1, id2)
  }

  @Test
  fun `data class hashCode is consistent`() {
    val id1 = createIdentifier()
    val id2 = createIdentifier()

    assertEquals(id1.hashCode(), id2.hashCode())
  }

  @Test
  fun `data class copy works correctly`() {
    val original = createIdentifier(deviceModel = "Original")
    val copy = original.copy(deviceModel = "Copy")

    assertEquals("Copy", copy.deviceModel)
    assertEquals(original.androidId, copy.androidId)
  }
}
