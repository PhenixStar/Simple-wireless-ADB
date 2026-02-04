package com.phenix.wirelessadb

import android.content.Context
import android.content.Intent
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Unit tests for AdbService.
 *
 * Tests companion object methods and intent creation.
 * Full service lifecycle tests would require more complex setup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdbServiceTest {

  private lateinit var context: Context

  @Before
  fun setup() {
    context = RuntimeEnvironment.getApplication()
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  // ============================================================
  // Constants tests
  // ============================================================

  @Test
  fun `ACTION_PENDING_AUTH has correct value`() {
    assertEquals("com.phenix.wirelessadb.PENDING_AUTH", AdbService.ACTION_PENDING_AUTH)
  }

  @Test
  fun `ACTION_APPROVE_DEVICE has correct value`() {
    assertEquals("com.phenix.wirelessadb.APPROVE_DEVICE", AdbService.ACTION_APPROVE_DEVICE)
  }

  @Test
  fun `ACTION_DENY_DEVICE has correct value`() {
    assertEquals("com.phenix.wirelessadb.DENY_DEVICE", AdbService.ACTION_DENY_DEVICE)
  }

  @Test
  fun `EXTRA_CLIENT_IP has correct value`() {
    assertEquals("client_ip", AdbService.EXTRA_CLIENT_IP)
  }

  // ============================================================
  // start() method tests
  // ============================================================

  @Test
  fun `start creates intent with correct ip extra`() {
    AdbService.start(context, "192.168.1.100", 5555)

    val shadowApp = Shadows.shadowOf(context as android.app.Application)
    val startedService = shadowApp.nextStartedService

    assertNotNull(startedService)
    assertEquals("192.168.1.100", startedService.getStringExtra("ip"))
  }

  @Test
  fun `start creates intent with correct port extra`() {
    AdbService.start(context, "192.168.1.100", 5556)

    val shadowApp = Shadows.shadowOf(context as android.app.Application)
    val startedService = shadowApp.nextStartedService

    assertNotNull(startedService)
    assertEquals(5556, startedService.getIntExtra("port", -1))
  }

  @Test
  fun `start creates intent with relay_enabled extra`() {
    AdbService.start(context, "192.168.1.100", 5555, relayEnabled = true)

    val shadowApp = Shadows.shadowOf(context as android.app.Application)
    val startedService = shadowApp.nextStartedService

    assertNotNull(startedService)
    assertTrue(startedService.getBooleanExtra("relay_enabled", false))
  }

  @Test
  fun `start defaults relay_enabled to false`() {
    AdbService.start(context, "192.168.1.100", 5555)

    val shadowApp = Shadows.shadowOf(context as android.app.Application)
    val startedService = shadowApp.nextStartedService

    assertNotNull(startedService)
    assertFalse(startedService.getBooleanExtra("relay_enabled", true))
  }

  // ============================================================
  // stop() method tests
  // ============================================================

  @Test
  fun `stop creates correct stop service intent`() {
    // First start the service
    AdbService.start(context, "192.168.1.100", 5555)

    // Clear the started services
    val shadowApp = Shadows.shadowOf(context as android.app.Application)
    shadowApp.clearStartedServices()

    // Now stop it
    AdbService.stop(context)

    // Verify stop was called (service intent created)
    val stoppedService = shadowApp.nextStoppedService
    assertNotNull(stoppedService)
  }

  // ============================================================
  // approveDevice() method tests
  // ============================================================

  @Test
  fun `approveDevice creates intent with correct action`() {
    AdbService.approveDevice(context, "192.168.1.101")

    val shadowApp = Shadows.shadowOf(context as android.app.Application)
    val startedService = shadowApp.nextStartedService

    assertNotNull(startedService)
    assertEquals(AdbService.ACTION_APPROVE_DEVICE, startedService.action)
  }

  @Test
  fun `approveDevice includes client IP in intent`() {
    AdbService.approveDevice(context, "192.168.1.101")

    val shadowApp = Shadows.shadowOf(context as android.app.Application)
    val startedService = shadowApp.nextStartedService

    assertNotNull(startedService)
    assertEquals("192.168.1.101", startedService.getStringExtra(AdbService.EXTRA_CLIENT_IP))
  }

  // ============================================================
  // denyDevice() method tests
  // ============================================================

  @Test
  fun `denyDevice creates intent with correct action`() {
    AdbService.denyDevice(context, "192.168.1.102")

    val shadowApp = Shadows.shadowOf(context as android.app.Application)
    val startedService = shadowApp.nextStartedService

    assertNotNull(startedService)
    assertEquals(AdbService.ACTION_DENY_DEVICE, startedService.action)
  }

  @Test
  fun `denyDevice includes client IP in intent`() {
    AdbService.denyDevice(context, "192.168.1.102")

    val shadowApp = Shadows.shadowOf(context as android.app.Application)
    val startedService = shadowApp.nextStartedService

    assertNotNull(startedService)
    assertEquals("192.168.1.102", startedService.getStringExtra(AdbService.EXTRA_CLIENT_IP))
  }

  // ============================================================
  // Intent component tests
  // ============================================================

  @Test
  fun `start intent has correct component`() {
    AdbService.start(context, "192.168.1.100", 5555)

    val shadowApp = Shadows.shadowOf(context as android.app.Application)
    val startedService = shadowApp.nextStartedService

    assertNotNull(startedService)
    assertEquals(AdbService::class.java.name, startedService.component?.className)
  }

  @Test
  fun `approveDevice intent has correct component`() {
    AdbService.approveDevice(context, "192.168.1.100")

    val shadowApp = Shadows.shadowOf(context as android.app.Application)
    val startedService = shadowApp.nextStartedService

    assertNotNull(startedService)
    assertEquals(AdbService::class.java.name, startedService.component?.className)
  }
}
