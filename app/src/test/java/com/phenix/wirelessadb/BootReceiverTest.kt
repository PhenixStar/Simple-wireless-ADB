package com.phenix.wirelessadb

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for BootReceiver using Robolectric.
 *
 * Tests verify:
 * - Boot receiver only responds to BOOT_COMPLETED actions
 * - Respects enable_on_boot preference
 * - Uses saved port from preferences
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootReceiverTest {

  private lateinit var context: Context
  private lateinit var bootReceiver: BootReceiver
  private lateinit var mockPrefs: SharedPreferences

  @Before
  fun setup() {
    context = RuntimeEnvironment.getApplication()
    bootReceiver = BootReceiver()

    // Skip real boot/retry delays so tests observe the enable call promptly
    BootReceiver.bootDelayMs = 0L
    BootReceiver.retryDelayMs = 0L

    // Mock PrefsManager
    mockkObject(PrefsManager)

    // Mock AdbManager
    mockkObject(AdbManager)
    coEvery { AdbManager.enable(any()) } returns Result.success(Unit)
  }

  @After
  fun teardown() {
    BootReceiver.bootDelayMs = 5000L
    BootReceiver.retryDelayMs = 3000L
    unmockkAll()
  }

  // ============================================================
  // Action filtering tests
  // ============================================================

  @Test
  fun `onReceive ignores non-boot actions`() {
    every { PrefsManager.isEnableOnBoot(any()) } returns true
    every { PrefsManager.getPort(any()) } returns 5555

    val intent = Intent("some.random.action")
    bootReceiver.onReceive(context, intent)

    // AdbManager.enable should NOT be called
    coVerify(exactly = 0) { AdbManager.enable(any()) }
  }

  @Test
  fun `onReceive responds to ACTION_BOOT_COMPLETED`() {
    every { PrefsManager.isEnableOnBoot(any()) } returns true
    every { PrefsManager.getPort(any()) } returns 5555

    val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
    bootReceiver.onReceive(context, intent)

    // Wait a moment for coroutine to execute
    Thread.sleep(100)

    // AdbManager.enable should be called
    coVerify { AdbManager.enable(5555) }
  }

  @Test
  fun `onReceive responds to QUICKBOOT_POWERON`() {
    every { PrefsManager.isEnableOnBoot(any()) } returns true
    every { PrefsManager.getPort(any()) } returns 5556

    val intent = Intent("android.intent.action.QUICKBOOT_POWERON")
    bootReceiver.onReceive(context, intent)

    Thread.sleep(100)

    coVerify { AdbManager.enable(5556) }
  }

  // ============================================================
  // Preference tests
  // ============================================================

  @Test
  fun `onReceive does not enable ADB when enable_on_boot is false`() {
    every { PrefsManager.isEnableOnBoot(any()) } returns false
    every { PrefsManager.getPort(any()) } returns 5555

    val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
    bootReceiver.onReceive(context, intent)

    Thread.sleep(100)

    // AdbManager.enable should NOT be called when disabled
    coVerify(exactly = 0) { AdbManager.enable(any()) }
  }

  @Test
  fun `onReceive uses port from PrefsManager`() {
    every { PrefsManager.isEnableOnBoot(any()) } returns true
    every { PrefsManager.getPort(any()) } returns 5557

    val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
    bootReceiver.onReceive(context, intent)

    Thread.sleep(100)

    // Should use the configured port
    coVerify { AdbManager.enable(5557) }
  }

  @Test
  fun `onReceive queries preferences with correct context`() {
    every { PrefsManager.isEnableOnBoot(any()) } returns false

    val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
    bootReceiver.onReceive(context, intent)

    verify { PrefsManager.isEnableOnBoot(context) }
  }

  // ============================================================
  // Edge cases
  // ============================================================

  @Test
  fun `onReceive handles multiple boot events gracefully`() {
    every { PrefsManager.isEnableOnBoot(any()) } returns true
    every { PrefsManager.getPort(any()) } returns 5555

    // Send multiple boot events
    val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
    bootReceiver.onReceive(context, intent)
    bootReceiver.onReceive(context, intent)

    Thread.sleep(200)

    // Should have been called twice
    coVerify(exactly = 2) { AdbManager.enable(5555) }
  }

  @Test
  fun `onReceive handles intent with null action`() {
    every { PrefsManager.isEnableOnBoot(any()) } returns true

    val intent = Intent() // No action set
    bootReceiver.onReceive(context, intent)

    // Should not crash and should not enable ADB
    coVerify(exactly = 0) { AdbManager.enable(any()) }
  }

  @Test
  fun `onReceive continues even if AdbManager fails`() {
    every { PrefsManager.isEnableOnBoot(any()) } returns true
    every { PrefsManager.getPort(any()) } returns 5555
    coEvery { AdbManager.enable(any()) } returns Result.failure(RuntimeException("No root"))

    val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

    // Should not throw exception
    bootReceiver.onReceive(context, intent)

    Thread.sleep(100)

    coVerify { AdbManager.enable(5555) }
  }
}
