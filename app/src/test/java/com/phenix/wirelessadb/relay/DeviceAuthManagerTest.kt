package com.phenix.wirelessadb.relay

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.phenix.wirelessadb.model.AuthMethod
import com.phenix.wirelessadb.model.DeviceIdentifier
import com.phenix.wirelessadb.model.TrustedDevice
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DeviceAuthManagerTest {

  private lateinit var mockContext: Context
  private lateinit var mockPrefs: SharedPreferences
  private lateinit var mockEditor: SharedPreferences.Editor
  private lateinit var deviceAuthManager: DeviceAuthManager
  private val gson = Gson()

  // Storage simulation
  private val storage = mutableMapOf<String, String>()

  @Before
  fun setup() {
    storage.clear()

    mockContext = mockk(relaxed = true)
    mockPrefs = mockk(relaxed = true)
    mockEditor = mockk(relaxed = true)

    every { mockContext.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE) } returns mockPrefs
    every { mockPrefs.edit() } returns mockEditor

    // Simulate storage operations
    every { mockEditor.putString(any(), any()) } answers {
      storage[firstArg()] = secondArg()
      mockEditor
    }
    every { mockEditor.remove(any()) } answers {
      storage.remove(firstArg())
      mockEditor
    }
    every { mockEditor.clear() } answers {
      storage.clear()
      mockEditor
    }
    every { mockEditor.apply() } just Runs

    every { mockPrefs.all } answers { storage.toMap() }
    every { mockPrefs.contains(any()) } answers { storage.containsKey(firstArg()) }
    every { mockPrefs.getString(any(), any()) } answers { storage[firstArg()] ?: secondArg() }

    deviceAuthManager = DeviceAuthManager(mockContext)
  }

  @After
  fun teardown() {
    unmockkAll()
    storage.clear()
  }

  // ============================================================
  // addTrustedDevice() tests
  // ============================================================

  @Test
  fun `addTrustedDevice with IP only creates device`() {
    deviceAuthManager.addTrustedDevice("192.168.1.100")

    verify { mockEditor.putString(any(), any()) }
    verify { mockEditor.apply() }

    // Verify device was saved
    assertEquals(1, storage.size)
  }

  @Test
  fun `addTrustedDevice with IP and name creates device`() {
    deviceAuthManager.addTrustedDevice("192.168.1.100", "Test Device")

    assertEquals(1, storage.size)

    // Parse the stored device
    val storedJson = storage.values.first()
    val device = gson.fromJson(storedJson, TrustedDevice::class.java)

    assertEquals("192.168.1.100", device.ip)
    assertEquals("Test Device", device.name)
    assertEquals(AuthMethod.IP_ADDRESS, device.authMethod)
  }

  @Test
  fun `addTrustedDevice with TrustedDevice object saves correctly`() {
    val device = TrustedDevice.fromIpOnly("192.168.1.100", "My Device")
    deviceAuthManager.addTrustedDevice(device)

    assertEquals(1, storage.size)

    val storedJson = storage[device.id]
    val stored = gson.fromJson(storedJson, TrustedDevice::class.java)

    assertEquals(device.id, stored.id)
    assertEquals("192.168.1.100", stored.ip)
    assertEquals("My Device", stored.name)
  }

  @Test
  fun `addTrustedDevice from identifier creates enhanced device`() {
    val identifier = DeviceIdentifier(
      androidId = "android123",
      buildFingerprint = "google/pixel/...",
      macAddress = "AA:BB:CC:DD:EE:FF",
      deviceModel = "Pixel 6",
      manufacturer = "Google"
    )

    val device = deviceAuthManager.addTrustedDevice(
      identifier = identifier,
      ip = "192.168.1.100",
      name = "My Pixel",
      authMethod = AuthMethod.TOKEN_PERSISTENT,
      persistentToken = "TOKEN-123"
    )

    assertEquals("android123", device.hardwareId)
    assertEquals("google/pixel/...", device.buildFingerprint)
    assertEquals("AA:BB:CC:DD:EE:FF", device.macAddress)
    assertEquals("Pixel 6", device.deviceModel)
    assertEquals("Google", device.manufacturer)
    assertEquals(AuthMethod.TOKEN_PERSISTENT, device.authMethod)
    assertEquals("TOKEN-123", device.persistentToken)
    assertNotNull(device.deviceHash)
  }

  // ============================================================
  // isDeviceTrusted() tests
  // ============================================================

  @Test
  fun `isDeviceTrusted returns true for trusted IP`() {
    deviceAuthManager.addTrustedDevice("192.168.1.100")
    assertTrue(deviceAuthManager.isDeviceTrusted("192.168.1.100"))
  }

  @Test
  fun `isDeviceTrusted returns false for unknown IP`() {
    assertFalse(deviceAuthManager.isDeviceTrusted("192.168.1.200"))
  }

  @Test
  fun `isDeviceTrusted with identifier returns true for trusted device`() {
    val identifier = DeviceIdentifier.fromPartial(androidId = "android123")
    deviceAuthManager.addTrustedDevice(identifier, ip = "192.168.1.100")

    assertTrue(deviceAuthManager.isDeviceTrusted(identifier))
  }

  @Test
  fun `isDeviceTrusted with identifier returns false for unknown device`() {
    val identifier = DeviceIdentifier.fromPartial(androidId = "unknown")
    assertFalse(deviceAuthManager.isDeviceTrusted(identifier))
  }

  // ============================================================
  // findDevice tests
  // ============================================================

  @Test
  fun `findDeviceByIp returns device when found`() {
    deviceAuthManager.addTrustedDevice("192.168.1.100", "Found Device")

    val found = deviceAuthManager.findDeviceByIp("192.168.1.100")

    assertNotNull(found)
    assertEquals("192.168.1.100", found?.ip)
    assertEquals("Found Device", found?.name)
  }

  @Test
  fun `findDeviceByIp returns null when not found`() {
    assertNull(deviceAuthManager.findDeviceByIp("192.168.1.200"))
  }

  @Test
  fun `findDeviceByIdentifier returns device when found`() {
    val identifier = DeviceIdentifier.fromPartial(androidId = "android123")
    deviceAuthManager.addTrustedDevice(identifier, ip = "192.168.1.100")

    val found = deviceAuthManager.findDeviceByIdentifier(identifier)

    assertNotNull(found)
    assertEquals("android123", found?.hardwareId)
  }

  @Test
  fun `findDeviceByIdentifier returns null when not found`() {
    val identifier = DeviceIdentifier.fromPartial(androidId = "unknown")
    assertNull(deviceAuthManager.findDeviceByIdentifier(identifier))
  }

  @Test
  fun `findDeviceByToken returns device when found`() {
    val identifier = DeviceIdentifier.fromPartial(androidId = "android123")
    deviceAuthManager.addTrustedDevice(
      identifier,
      ip = "192.168.1.100",
      persistentToken = "TOKEN-ABC"
    )

    val found = deviceAuthManager.findDeviceByToken("TOKEN-ABC")

    assertNotNull(found)
    assertEquals("TOKEN-ABC", found?.persistentToken)
  }

  @Test
  fun `findDeviceByToken returns null when not found`() {
    assertNull(deviceAuthManager.findDeviceByToken("UNKNOWN-TOKEN"))
  }

  // ============================================================
  // removeTrustedDevice() tests
  // ============================================================

  @Test
  fun `removeTrustedDevice removes device by IP`() {
    deviceAuthManager.addTrustedDevice("192.168.1.100")
    assertEquals(1, storage.size)

    deviceAuthManager.removeTrustedDevice("192.168.1.100")

    // Device should be removed
    assertNull(deviceAuthManager.findDeviceByIp("192.168.1.100"))
  }

  @Test
  fun `removeTrustedDeviceById removes device`() {
    val device = TrustedDevice.fromIpOnly("192.168.1.100")
    deviceAuthManager.addTrustedDevice(device)

    deviceAuthManager.removeTrustedDeviceById(device.id)

    assertNull(deviceAuthManager.findDeviceByIp("192.168.1.100"))
  }

  // ============================================================
  // getTrustedDevices() tests
  // ============================================================

  @Test
  fun `getTrustedDevices returns empty list initially`() {
    val devices = deviceAuthManager.getTrustedDevices()
    assertTrue(devices.isEmpty())
  }

  @Test
  fun `getTrustedDevices returns all added devices`() {
    deviceAuthManager.addTrustedDevice("192.168.1.100", "Device 1")
    deviceAuthManager.addTrustedDevice("192.168.1.101", "Device 2")
    deviceAuthManager.addTrustedDevice("192.168.1.102", "Device 3")

    val devices = deviceAuthManager.getTrustedDevices()

    assertEquals(3, devices.size)
  }

  @Test
  fun `getTrustedDevices returns devices sorted by lastSeen descending`() {
    // Add devices with different timestamps
    val device1 = TrustedDevice(
      id = "1",
      ip = "192.168.1.100",
      name = "Device 1",
      addedAt = 1000,
      lastSeen = 1000
    )
    val device2 = TrustedDevice(
      id = "2",
      ip = "192.168.1.101",
      name = "Device 2",
      addedAt = 2000,
      lastSeen = 3000 // Most recent
    )
    val device3 = TrustedDevice(
      id = "3",
      ip = "192.168.1.102",
      name = "Device 3",
      addedAt = 1500,
      lastSeen = 2000
    )

    deviceAuthManager.addTrustedDevice(device1)
    deviceAuthManager.addTrustedDevice(device2)
    deviceAuthManager.addTrustedDevice(device3)

    val devices = deviceAuthManager.getTrustedDevices()

    // Should be sorted: device2 (3000), device3 (2000), device1 (1000)
    assertEquals("2", devices[0].id)
    assertEquals("3", devices[1].id)
    assertEquals("1", devices[2].id)
  }

  // ============================================================
  // updateDevice tests
  // ============================================================

  @Test
  fun `updateDevice updates stored device`() {
    val device = TrustedDevice.fromIpOnly("192.168.1.100", "Original Name")
    deviceAuthManager.addTrustedDevice(device)

    val updated = device.copy(name = "Updated Name")
    deviceAuthManager.updateDevice(updated)

    val found = deviceAuthManager.findDeviceByIp("192.168.1.100")
    assertEquals("Updated Name", found?.name)
  }

  @Test
  fun `updateDeviceName updates name by IP`() {
    deviceAuthManager.addTrustedDevice("192.168.1.100", "Old Name")

    deviceAuthManager.updateDeviceName("192.168.1.100", "New Name")

    val found = deviceAuthManager.findDeviceByIp("192.168.1.100")
    assertEquals("New Name", found?.name)
  }

  @Test
  fun `updateLastSeen updates timestamp for IP`() {
    val before = System.currentTimeMillis()
    deviceAuthManager.addTrustedDevice("192.168.1.100")

    Thread.sleep(10) // Small delay to ensure different timestamp
    deviceAuthManager.updateLastSeen("192.168.1.100")

    val found = deviceAuthManager.findDeviceByIp("192.168.1.100")
    assertTrue(found!!.lastSeen >= before)
  }

  @Test
  fun `updateLastSeen with identifier updates timestamp`() {
    val identifier = DeviceIdentifier.fromPartial(androidId = "android123")
    val before = System.currentTimeMillis()
    deviceAuthManager.addTrustedDevice(identifier, ip = "192.168.1.100")

    Thread.sleep(10)
    deviceAuthManager.updateLastSeen(identifier)

    val found = deviceAuthManager.findDeviceByIdentifier(identifier)
    assertTrue(found!!.lastSeen >= before)
  }

  // ============================================================
  // upgradeToTokenAuth() tests
  // ============================================================

  @Test
  fun `upgradeToTokenAuth upgrades device auth method`() {
    val device = TrustedDevice.fromIpOnly("192.168.1.100")
    deviceAuthManager.addTrustedDevice(device)

    val upgraded = deviceAuthManager.upgradeToTokenAuth(device, "NEW-TOKEN")

    assertEquals(AuthMethod.TOKEN_PERSISTENT, upgraded.authMethod)
    assertEquals("NEW-TOKEN", upgraded.persistentToken)

    // Verify it's persisted
    val found = deviceAuthManager.findDeviceByIp("192.168.1.100")
    assertEquals(AuthMethod.TOKEN_PERSISTENT, found?.authMethod)
    assertEquals("NEW-TOKEN", found?.persistentToken)
  }

  // ============================================================
  // getTrustedDeviceCount() tests
  // ============================================================

  @Test
  fun `getTrustedDeviceCount returns correct count`() {
    assertEquals(0, deviceAuthManager.getTrustedDeviceCount())

    deviceAuthManager.addTrustedDevice("192.168.1.100")
    assertEquals(1, deviceAuthManager.getTrustedDeviceCount())

    deviceAuthManager.addTrustedDevice("192.168.1.101")
    assertEquals(2, deviceAuthManager.getTrustedDeviceCount())
  }

  // ============================================================
  // clearAllDevices() tests
  // ============================================================

  @Test
  fun `clearAllDevices removes all devices`() {
    deviceAuthManager.addTrustedDevice("192.168.1.100")
    deviceAuthManager.addTrustedDevice("192.168.1.101")
    assertEquals(2, storage.size)

    deviceAuthManager.clearAllDevices()

    assertTrue(storage.isEmpty())
    assertEquals(0, deviceAuthManager.getTrustedDeviceCount())
  }
}
