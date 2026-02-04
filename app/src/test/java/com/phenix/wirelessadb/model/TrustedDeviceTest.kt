package com.phenix.wirelessadb.model

import org.junit.Assert.*
import org.junit.Test

class TrustedDeviceTest {

  private fun createDevice(
    id: String = "device-001",
    ip: String? = "192.168.1.100",
    name: String? = null,
    addedAt: Long = System.currentTimeMillis(),
    lastSeen: Long = System.currentTimeMillis(),
    hardwareId: String? = null,
    buildFingerprint: String? = null,
    macAddress: String? = null,
    deviceModel: String? = null,
    manufacturer: String? = null,
    persistentToken: String? = null,
    authMethod: AuthMethod = AuthMethod.IP_ADDRESS,
    deviceHash: String? = null
  ): TrustedDevice {
    return TrustedDevice(
      id = id,
      ip = ip,
      name = name,
      addedAt = addedAt,
      lastSeen = lastSeen,
      hardwareId = hardwareId,
      buildFingerprint = buildFingerprint,
      macAddress = macAddress,
      deviceModel = deviceModel,
      manufacturer = manufacturer,
      persistentToken = persistentToken,
      authMethod = authMethod,
      deviceHash = deviceHash
    )
  }

  // ============================================================
  // hasHardwareId() tests
  // ============================================================

  @Test
  fun `hasHardwareId returns true when hardwareId is set`() {
    val device = createDevice(hardwareId = "android123")
    assertTrue(device.hasHardwareId())
  }

  @Test
  fun `hasHardwareId returns true when buildFingerprint is set`() {
    val device = createDevice(buildFingerprint = "google/pixel/...")
    assertTrue(device.hasHardwareId())
  }

  @Test
  fun `hasHardwareId returns true when both are set`() {
    val device = createDevice(
      hardwareId = "android123",
      buildFingerprint = "google/pixel/..."
    )
    assertTrue(device.hasHardwareId())
  }

  @Test
  fun `hasHardwareId returns false when neither is set`() {
    val device = createDevice()
    assertFalse(device.hasHardwareId())
  }

  @Test
  fun `hasHardwareId returns false for empty strings`() {
    val device = createDevice(hardwareId = "", buildFingerprint = "")
    assertFalse(device.hasHardwareId())
  }

  // ============================================================
  // getDisplayName() tests
  // ============================================================

  @Test
  fun `getDisplayName returns user name when set`() {
    val device = createDevice(
      name = "My Phone",
      deviceModel = "Pixel 6",
      manufacturer = "Google"
    )
    assertEquals("My Phone", device.getDisplayName())
  }

  @Test
  fun `getDisplayName returns manufacturer and model when name is null`() {
    val device = createDevice(
      name = null,
      deviceModel = "Pixel 6",
      manufacturer = "Google"
    )
    assertEquals("Google Pixel 6", device.getDisplayName())
  }

  @Test
  fun `getDisplayName returns model only when manufacturer is null`() {
    val device = createDevice(
      name = null,
      deviceModel = "Pixel 6",
      manufacturer = null
    )
    assertEquals("Pixel 6", device.getDisplayName())
  }

  @Test
  fun `getDisplayName returns Unknown Device when no info available`() {
    val device = createDevice(
      name = null,
      deviceModel = null,
      manufacturer = null
    )
    assertEquals("Unknown Device", device.getDisplayName())
  }

  // ============================================================
  // getShortHash() tests
  // ============================================================

  @Test
  fun `getShortHash returns last 4 chars of device hash`() {
    val device = createDevice(deviceHash = "abcdef123456")
    assertEquals("3456", device.getShortHash())
  }

  @Test
  fun `getShortHash returns question marks when hash is null`() {
    val device = createDevice(deviceHash = null)
    assertEquals("????", device.getShortHash())
  }

  @Test
  fun `getShortHash handles short hash`() {
    val device = createDevice(deviceHash = "ab")
    assertEquals("ab", device.getShortHash())
  }

  @Test
  fun `getShortHash handles exactly 4 char hash`() {
    val device = createDevice(deviceHash = "1234")
    assertEquals("1234", device.getShortHash())
  }

  // ============================================================
  // matchesByHardware() tests
  // ============================================================

  @Test
  fun `matchesByHardware returns true for matching device hash`() {
    val device1 = createDevice(deviceHash = "hash12345678")
    val device2 = createDevice(deviceHash = "hash12345678")
    assertTrue(device1.matchesByHardware(device2))
  }

  @Test
  fun `matchesByHardware returns false for different device hash`() {
    val device1 = createDevice(deviceHash = "hash12345678")
    val device2 = createDevice(deviceHash = "differenthash")
    assertFalse(device1.matchesByHardware(device2))
  }

  @Test
  fun `matchesByHardware falls back to hardwareId when hash is null`() {
    val device1 = createDevice(deviceHash = null, hardwareId = "android123")
    val device2 = createDevice(deviceHash = null, hardwareId = "android123")
    assertTrue(device1.matchesByHardware(device2))
  }

  @Test
  fun `matchesByHardware returns false when both hashes are null and hardwareIds differ`() {
    val device1 = createDevice(deviceHash = null, hardwareId = "android123")
    val device2 = createDevice(deviceHash = null, hardwareId = "android456")
    assertFalse(device1.matchesByHardware(device2))
  }

  @Test
  fun `matchesByHardware returns false when all identifying info is null`() {
    val device1 = createDevice(deviceHash = null, hardwareId = null)
    val device2 = createDevice(deviceHash = null, hardwareId = null)
    assertFalse(device1.matchesByHardware(device2))
  }

  @Test
  fun `matchesByHardware prefers deviceHash over hardwareId`() {
    val device1 = createDevice(deviceHash = "hash1", hardwareId = "hw1")
    val device2 = createDevice(deviceHash = "hash1", hardwareId = "hw2")
    // Should match by hash even though hardwareIds differ
    assertTrue(device1.matchesByHardware(device2))
  }

  // ============================================================
  // fromIpOnly() tests
  // ============================================================

  @Test
  fun `fromIpOnly creates device with IP address`() {
    val device = TrustedDevice.fromIpOnly("192.168.1.100")
    assertEquals("192.168.1.100", device.ip)
  }

  @Test
  fun `fromIpOnly creates device with name when provided`() {
    val device = TrustedDevice.fromIpOnly("192.168.1.100", name = "Test Device")
    assertEquals("Test Device", device.name)
  }

  @Test
  fun `fromIpOnly sets IP_ADDRESS auth method`() {
    val device = TrustedDevice.fromIpOnly("192.168.1.100")
    assertEquals(AuthMethod.IP_ADDRESS, device.authMethod)
  }

  @Test
  fun `fromIpOnly generates unique UUID for id`() {
    val device1 = TrustedDevice.fromIpOnly("192.168.1.100")
    val device2 = TrustedDevice.fromIpOnly("192.168.1.100")
    assertNotEquals(device1.id, device2.id)
  }

  @Test
  fun `fromIpOnly sets addedAt and lastSeen to current time`() {
    val before = System.currentTimeMillis()
    val device = TrustedDevice.fromIpOnly("192.168.1.100")
    val after = System.currentTimeMillis()

    assertTrue(device.addedAt >= before && device.addedAt <= after)
    assertTrue(device.lastSeen >= before && device.lastSeen <= after)
    assertEquals(device.addedAt, device.lastSeen)
  }

  @Test
  fun `fromIpOnly leaves hardware fields null`() {
    val device = TrustedDevice.fromIpOnly("192.168.1.100")
    assertNull(device.hardwareId)
    assertNull(device.buildFingerprint)
    assertNull(device.macAddress)
    assertNull(device.deviceModel)
    assertNull(device.manufacturer)
    assertNull(device.deviceHash)
  }

  // ============================================================
  // AuthMethod enum tests
  // ============================================================

  @Test
  fun `AuthMethod has all expected values`() {
    val methods = AuthMethod.entries
    assertEquals(3, methods.size)
    assertTrue(methods.contains(AuthMethod.IP_ADDRESS))
    assertTrue(methods.contains(AuthMethod.TOKEN_ONCE))
    assertTrue(methods.contains(AuthMethod.TOKEN_PERSISTENT))
  }

  // ============================================================
  // Data class tests
  // ============================================================

  @Test
  fun `data class copy works correctly`() {
    val original = createDevice(name = "Original")
    val copy = original.copy(name = "Copy")

    assertEquals("Copy", copy.name)
    assertEquals(original.id, copy.id)
    assertEquals(original.ip, copy.ip)
  }

  @Test
  fun `data class equals works correctly`() {
    val timestamp = 1000000L
    val device1 = createDevice(id = "same-id", name = "Device", addedAt = timestamp, lastSeen = timestamp)
    val device2 = createDevice(id = "same-id", name = "Device", addedAt = timestamp, lastSeen = timestamp)

    assertEquals(device1, device2)
  }
}
