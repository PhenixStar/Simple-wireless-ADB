package com.phenix.wirelessadb.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.phenix.wirelessadb.AdbManager
import com.phenix.wirelessadb.PrefsManager
import com.phenix.wirelessadb.model.ConnectionMode
import com.phenix.wirelessadb.relay.TailscaleHelper
import com.phenix.wirelessadb.shell.ExecutorBackend
import com.phenix.wirelessadb.shell.ShellExecutor
import com.phenix.wirelessadb.warpgate.WarpgateConfig
import com.phenix.wirelessadb.warpgate.WarpgateManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for AdbViewModel using Robolectric.
 *
 * Uses InstantTaskExecutorRule to allow LiveData testing on main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdbViewModelTest {

  @get:Rule
  val instantTaskExecutorRule = InstantTaskExecutorRule()

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var application: Application
  private lateinit var viewModel: AdbViewModel

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)

    application = RuntimeEnvironment.getApplication()

    // Mock PrefsManager
    mockkObject(PrefsManager)
    every { PrefsManager.getConnectionMode(any()) } returns ConnectionMode.LOCAL_WIFI
    every { PrefsManager.isHideDevNotification(any()) } returns false
    every { PrefsManager.isEnableOnBoot(any()) } returns false
    every { PrefsManager.setEnableOnBoot(any(), any()) } just Runs
    every { PrefsManager.isRelayEnabled(any()) } returns false
    every { PrefsManager.setRelayEnabled(any(), any()) } just Runs
    every { PrefsManager.getPort(any()) } returns 5555
    every { PrefsManager.setPort(any(), any()) } just Runs
    every { PrefsManager.getRelayPort(any()) } returns 5556
    every { PrefsManager.setConnectionMode(any(), any()) } just Runs
    every { PrefsManager.getWarpgateConfig(any()) } returns WarpgateConfig()
    every { PrefsManager.setWarpgateConfig(any(), any()) } just Runs
    every { PrefsManager.setHideDevNotification(any(), any()) } just Runs

    // Mock AdbManager
    mockkObject(AdbManager)
    coEvery { AdbManager.isRootAvailable() } returns true
    coEvery { AdbManager.getStatus(any()) } returns AdbManager.AdbStatus(
      enabled = false,
      port = 5555,
      ip = null
    )
    coEvery { AdbManager.enable(any()) } returns Result.success(Unit)
    coEvery { AdbManager.disable() } returns Result.success(Unit)

    // Mock TailscaleHelper
    mockkObject(TailscaleHelper)
    every { TailscaleHelper.getTailscaleIp(any()) } returns null

    // Mock WarpgateManager
    mockkObject(WarpgateManager)
    every { WarpgateManager.isConnected } returns false
    every { WarpgateManager.disconnect() } just Runs
    every { WarpgateManager.getAdbCommand() } returns null
    coEvery { WarpgateManager.connect(any(), any()) } returns Result.success(Unit)

    // Mock ShellExecutor
    mockkObject(ShellExecutor)
    coEvery { ShellExecutor.initialize() } returns ExecutorBackend.ROOT

    viewModel = AdbViewModel(application)

    // Advance time to complete initialization
    testDispatcher.scheduler.advanceUntilIdle()
  }

  @After
  fun teardown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  // ============================================================
  // Initial state tests
  // ============================================================

  @Test
  fun `initial loading state is false`() {
    assertEquals(false, viewModel.isLoading.value)
  }

  @Test
  fun `initial error state is null`() {
    assertNull(viewModel.error.value)
  }

  @Test
  fun `initial connection mode is loaded from preferences`() {
    assertEquals(ConnectionMode.LOCAL_WIFI, viewModel.connectionMode.value)
  }

  @Test
  fun `initial trusted device count is zero`() {
    assertEquals(0, viewModel.trustedDeviceCount.value)
  }

  // ============================================================
  // Preference methods tests
  // ============================================================

  @Test
  fun `isEnableOnBoot returns value from PrefsManager`() {
    every { PrefsManager.isEnableOnBoot(any()) } returns true
    assertTrue(viewModel.isEnableOnBoot())

    every { PrefsManager.isEnableOnBoot(any()) } returns false
    assertFalse(viewModel.isEnableOnBoot())
  }

  @Test
  fun `setEnableOnBoot delegates to PrefsManager`() {
    viewModel.setEnableOnBoot(true)
    verify { PrefsManager.setEnableOnBoot(any(), true) }

    viewModel.setEnableOnBoot(false)
    verify { PrefsManager.setEnableOnBoot(any(), false) }
  }

  @Test
  fun `isRelayEnabled returns value from PrefsManager`() {
    every { PrefsManager.isRelayEnabled(any()) } returns true
    assertTrue(viewModel.isRelayEnabled())
  }

  @Test
  fun `setRelayEnabled delegates to PrefsManager`() {
    viewModel.setRelayEnabled(true)
    verify { PrefsManager.setRelayEnabled(any(), true) }
  }

  @Test
  fun `getPort returns value from PrefsManager`() {
    every { PrefsManager.getPort(any()) } returns 5557
    assertEquals(5557, viewModel.getPort())
  }

  @Test
  fun `getRelayPort returns value from PrefsManager`() {
    every { PrefsManager.getRelayPort(any()) } returns 5558
    assertEquals(5558, viewModel.getRelayPort())
  }

  // ============================================================
  // Connection mode tests
  // ============================================================

  @Test
  fun `setConnectionMode updates LiveData and saves to prefs`() {
    viewModel.setConnectionMode(ConnectionMode.TAILSCALE_DIRECT)

    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(ConnectionMode.TAILSCALE_DIRECT, viewModel.connectionMode.value)
    verify { PrefsManager.setConnectionMode(any(), ConnectionMode.TAILSCALE_DIRECT) }
  }

  @Test
  fun `getConnectionMode returns current mode`() {
    assertEquals(ConnectionMode.LOCAL_WIFI, viewModel.getConnectionMode())

    viewModel.setConnectionMode(ConnectionMode.WARPGATE)
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(ConnectionMode.WARPGATE, viewModel.getConnectionMode())
  }

  // ============================================================
  // Error handling tests
  // ============================================================

  @Test
  fun `clearError sets error to null`() {
    // Simulate an error state would require toggling ADB with failure
    viewModel.clearError()
    assertNull(viewModel.error.value)
  }

  // ============================================================
  // Pending approval tests
  // ============================================================

  @Test
  fun `setPendingApproval updates LiveData`() {
    viewModel.setPendingApproval("192.168.1.100")
    assertEquals("192.168.1.100", viewModel.pendingApprovalIp.value)

    viewModel.setPendingApproval(null)
    assertNull(viewModel.pendingApprovalIp.value)
  }

  // ============================================================
  // Warpgate tests
  // ============================================================

  @Test
  fun `getWarpgateConfig returns value from PrefsManager`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "test.example.com",
      port = 8443
    )
    every { PrefsManager.getWarpgateConfig(any()) } returns config

    val result = viewModel.getWarpgateConfig()

    assertEquals(config.host, result.host)
    assertEquals(config.port, result.port)
  }

  @Test
  fun `setWarpgateConfig saves to PrefsManager`() {
    val config = WarpgateConfig(
      enabled = true,
      host = "new.example.com"
    )

    viewModel.setWarpgateConfig(config)
    testDispatcher.scheduler.advanceUntilIdle()

    verify { PrefsManager.setWarpgateConfig(any(), config) }
  }

  @Test
  fun `disconnectWarpgate updates state`() {
    viewModel.disconnectWarpgate()
    testDispatcher.scheduler.advanceUntilIdle()

    assertEquals(false, viewModel.warpgateConnected.value)
    verify { WarpgateManager.disconnect() }
  }

  // ============================================================
  // Connection command tests
  // ============================================================

  @Test
  fun `getConnectionCommand returns empty when disabled`() {
    coEvery { AdbManager.getStatus(any()) } returns AdbManager.AdbStatus(
      enabled = false,
      port = 5555,
      ip = null
    )

    viewModel.refreshStatus()
    testDispatcher.scheduler.advanceUntilIdle()

    val command = viewModel.getConnectionCommand()
    assertTrue(command.isEmpty())
  }

  @Test
  fun `getConnectionCommand returns local wifi command`() {
    coEvery { AdbManager.getStatus(any()) } returns AdbManager.AdbStatus(
      enabled = true,
      port = 5555,
      ip = "192.168.1.100"
    )

    viewModel.refreshStatus()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.setConnectionMode(ConnectionMode.LOCAL_WIFI)
    testDispatcher.scheduler.advanceUntilIdle()

    val command = viewModel.getConnectionCommand()
    assertEquals("adb connect 192.168.1.100:5555", command)
  }

  @Test
  fun `getConnectionCommand falls back to local when tailscale unavailable`() {
    coEvery { AdbManager.getStatus(any()) } returns AdbManager.AdbStatus(
      enabled = true,
      port = 5555,
      ip = "192.168.1.100"
    )
    every { TailscaleHelper.getTailscaleIp(any()) } returns null

    viewModel.refreshStatus()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.setConnectionMode(ConnectionMode.TAILSCALE_DIRECT)
    testDispatcher.scheduler.advanceUntilIdle()

    val command = viewModel.getConnectionCommand()
    assertEquals("adb connect 192.168.1.100:5555", command)
  }

  @Test
  fun `getConnectionCommand uses tailscale when available`() {
    coEvery { AdbManager.getStatus(any()) } returns AdbManager.AdbStatus(
      enabled = true,
      port = 5555,
      ip = "192.168.1.100"
    )
    every { TailscaleHelper.getTailscaleIp(any()) } returns "100.64.0.1"

    viewModel.refreshStatus()
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.setConnectionMode(ConnectionMode.TAILSCALE_DIRECT)
    testDispatcher.scheduler.advanceUntilIdle()

    val command = viewModel.getConnectionCommand()
    assertEquals("adb connect 100.64.0.1:5555", command)
  }
}
