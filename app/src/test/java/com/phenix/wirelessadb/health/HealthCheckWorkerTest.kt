package com.phenix.wirelessadb.health

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.phenix.wirelessadb.AdbManager
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HealthCheckWorkerTest {

  private lateinit var mockContext: Context
  private lateinit var mockParams: WorkerParameters
  private lateinit var worker: HealthCheckWorker

  @Before
  fun setup() {
    mockContext = mockk(relaxed = true)
    mockParams = mockk(relaxed = true)

    // Mock AdbManager singleton
    mockkObject(AdbManager)

    worker = HealthCheckWorker(mockContext, mockParams)
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  // ============================================================
  // Success scenario tests
  // ============================================================

  @Test
  fun `testDoWork_Success - returns success when ADB is healthy`() = runBlocking {
    // Arrange
    val inputData = workDataOf(
      HealthCheckWorker.KEY_LAST_KNOWN_ENABLED to true,
      HealthCheckWorker.KEY_LAST_KNOWN_PORT to 5555,
      HealthCheckWorker.KEY_LAST_KNOWN_IP to "192.168.1.100"
    )
    every { mockParams.inputData } returns inputData

    val healthyStatus = AdbManager.AdbStatus(
      enabled = true,
      port = 5555,
      ip = "192.168.1.100"
    )
    coEvery { AdbManager.getStatus(any()) } returns healthyStatus

    // Act
    val result = worker.doWork()

    // Assert
    assertTrue(result is ListenableWorker.Result.Success)
    val outputData = (result as ListenableWorker.Result.Success).outputData
    assertEquals(true, outputData.getBoolean(HealthCheckWorker.KEY_CURRENT_ENABLED, false))
    assertEquals(5555, outputData.getInt(HealthCheckWorker.KEY_CURRENT_PORT, 0))
    assertEquals("192.168.1.100", outputData.getString(HealthCheckWorker.KEY_CURRENT_IP))
    assertEquals(HealthCheckWorker.HEALTH_HEALTHY, outputData.getString(HealthCheckWorker.KEY_HEALTH_STATUS))
    assertEquals(false, outputData.getBoolean(HealthCheckWorker.KEY_DEGRADATION_DETECTED, true))
  }

  // ============================================================
  // ADB disabled scenario
  // ============================================================

  @Test
  fun `testDoWork_AdbDisabled_ReturnsFailed - returns FAILED when ADB is disabled`() = runBlocking {
    // Arrange
    val inputData = workDataOf(
      HealthCheckWorker.KEY_LAST_KNOWN_ENABLED to true,
      HealthCheckWorker.KEY_LAST_KNOWN_PORT to 5555,
      HealthCheckWorker.KEY_LAST_KNOWN_IP to "192.168.1.100"
    )
    every { mockParams.inputData } returns inputData

    val disabledStatus = AdbManager.AdbStatus(
      enabled = false,
      port = 0,
      ip = null
    )
    coEvery { AdbManager.getStatus(any()) } returns disabledStatus

    // Act
    val result = worker.doWork()

    // Assert
    assertTrue(result is ListenableWorker.Result.Success)
    val outputData = (result as ListenableWorker.Result.Success).outputData
    assertEquals(HealthCheckWorker.HEALTH_FAILED, outputData.getString(HealthCheckWorker.KEY_HEALTH_STATUS))
    assertEquals(false, outputData.getBoolean(HealthCheckWorker.KEY_CURRENT_ENABLED, true))
  }

  // ============================================================
  // Degradation detection tests
  // ============================================================

  @Test
  fun `testDoWork_DetectsDegradation_WhenPortChanges - detects port change`() = runBlocking {
    // Arrange
    val inputData = workDataOf(
      HealthCheckWorker.KEY_LAST_KNOWN_ENABLED to true,
      HealthCheckWorker.KEY_LAST_KNOWN_PORT to 5555,
      HealthCheckWorker.KEY_LAST_KNOWN_IP to "192.168.1.100"
    )
    every { mockParams.inputData } returns inputData

    val changedStatus = AdbManager.AdbStatus(
      enabled = true,
      port = 5556, // Port changed!
      ip = "192.168.1.100"
    )
    coEvery { AdbManager.getStatus(any()) } returns changedStatus

    // Act
    val result = worker.doWork()

    // Assert
    assertTrue(result is ListenableWorker.Result.Success)
    val outputData = (result as ListenableWorker.Result.Success).outputData
    assertEquals(HealthCheckWorker.HEALTH_DEGRADED, outputData.getString(HealthCheckWorker.KEY_HEALTH_STATUS))
    assertEquals(true, outputData.getBoolean(HealthCheckWorker.KEY_DEGRADATION_DETECTED, false))
  }

  @Test
  fun `testDoWork_DetectsDegradation_WhenIpChanges - detects IP address change`() = runBlocking {
    // Arrange
    val inputData = workDataOf(
      HealthCheckWorker.KEY_LAST_KNOWN_ENABLED to true,
      HealthCheckWorker.KEY_LAST_KNOWN_PORT to 5555,
      HealthCheckWorker.KEY_LAST_KNOWN_IP to "192.168.1.100"
    )
    every { mockParams.inputData } returns inputData

    val changedStatus = AdbManager.AdbStatus(
      enabled = true,
      port = 5555,
      ip = "192.168.1.200" // IP changed!
    )
    coEvery { AdbManager.getStatus(any()) } returns changedStatus

    // Act
    val result = worker.doWork()

    // Assert
    assertTrue(result is ListenableWorker.Result.Success)
    val outputData = (result as ListenableWorker.Result.Success).outputData
    assertEquals(HealthCheckWorker.HEALTH_DEGRADED, outputData.getString(HealthCheckWorker.KEY_HEALTH_STATUS))
    assertEquals(true, outputData.getBoolean(HealthCheckWorker.KEY_DEGRADATION_DETECTED, false))
  }

  @Test
  fun `testDoWork_DetectsDegradation_WhenAdbDisabled - detects ADB disabled state`() = runBlocking {
    // Arrange
    val inputData = workDataOf(
      HealthCheckWorker.KEY_LAST_KNOWN_ENABLED to true,
      HealthCheckWorker.KEY_LAST_KNOWN_PORT to 5555,
      HealthCheckWorker.KEY_LAST_KNOWN_IP to "192.168.1.100"
    )
    every { mockParams.inputData } returns inputData

    val disabledStatus = AdbManager.AdbStatus(
      enabled = false, // ADB disabled!
      port = 0,
      ip = null
    )
    coEvery { AdbManager.getStatus(any()) } returns disabledStatus

    // Act
    val result = worker.doWork()

    // Assert
    assertTrue(result is ListenableWorker.Result.Success)
    val outputData = (result as ListenableWorker.Result.Success).outputData
    assertEquals(HealthCheckWorker.HEALTH_FAILED, outputData.getString(HealthCheckWorker.KEY_HEALTH_STATUS))
    // Note: degradation is detected when ADB was enabled but is now disabled
    assertEquals(true, outputData.getBoolean(HealthCheckWorker.KEY_DEGRADATION_DETECTED, false))
  }

  // ============================================================
  // False positive prevention
  // ============================================================

  @Test
  fun `testDoWork_NoFalsePositives_WhenNeverEnabled - no degradation when never enabled`() = runBlocking {
    // Arrange
    val inputData = workDataOf(
      HealthCheckWorker.KEY_LAST_KNOWN_ENABLED to false,
      HealthCheckWorker.KEY_LAST_KNOWN_PORT to 0,
      HealthCheckWorker.KEY_LAST_KNOWN_IP to ""
    )
    every { mockParams.inputData } returns inputData

    val disabledStatus = AdbManager.AdbStatus(
      enabled = false,
      port = 0,
      ip = null
    )
    coEvery { AdbManager.getStatus(any()) } returns disabledStatus

    // Act
    val result = worker.doWork()

    // Assert
    assertTrue(result is ListenableWorker.Result.Success)
    val outputData = (result as ListenableWorker.Result.Success).outputData
    assertEquals(HealthCheckWorker.HEALTH_FAILED, outputData.getString(HealthCheckWorker.KEY_HEALTH_STATUS))
    // No degradation because it was never enabled
    assertEquals(false, outputData.getBoolean(HealthCheckWorker.KEY_DEGRADATION_DETECTED, true))
  }

  // ============================================================
  // Exception handling
  // ============================================================

  @Test
  fun `testDoWork_HandlesException_ReturnsFailure - returns failure when exception occurs`() = runBlocking {
    // Arrange
    val inputData = workDataOf(
      HealthCheckWorker.KEY_LAST_KNOWN_ENABLED to true,
      HealthCheckWorker.KEY_LAST_KNOWN_PORT to 5555,
      HealthCheckWorker.KEY_LAST_KNOWN_IP to "192.168.1.100"
    )
    every { mockParams.inputData } returns inputData

    coEvery { AdbManager.getStatus(any()) } throws RuntimeException("Network error")

    // Act
    val result = worker.doWork()

    // Assert
    assertTrue(result is ListenableWorker.Result.Failure)
  }

  // ============================================================
  // Degradation detection logic tests
  // ============================================================

  @Test
  fun `testDetectDegradation_AllScenarios - comprehensive degradation scenarios`() = runBlocking {
    // Test scenario 1: No degradation when everything matches
    val inputHealthy = workDataOf(
      HealthCheckWorker.KEY_LAST_KNOWN_ENABLED to true,
      HealthCheckWorker.KEY_LAST_KNOWN_PORT to 5555,
      HealthCheckWorker.KEY_LAST_KNOWN_IP to "192.168.1.100"
    )
    every { mockParams.inputData } returns inputHealthy
    val healthyStatus = AdbManager.AdbStatus(enabled = true, port = 5555, ip = "192.168.1.100")
    coEvery { AdbManager.getStatus(any()) } returns healthyStatus

    var result = worker.doWork()
    var outputData = (result as ListenableWorker.Result.Success).outputData
    assertEquals(false, outputData.getBoolean(HealthCheckWorker.KEY_DEGRADATION_DETECTED, true))

    // Test scenario 2: Degradation when IP is null (network lost)
    val inputIpNull = workDataOf(
      HealthCheckWorker.KEY_LAST_KNOWN_ENABLED to true,
      HealthCheckWorker.KEY_LAST_KNOWN_PORT to 5555,
      HealthCheckWorker.KEY_LAST_KNOWN_IP to "192.168.1.100"
    )
    every { mockParams.inputData } returns inputIpNull
    val statusIpNull = AdbManager.AdbStatus(enabled = true, port = 5555, ip = null)
    coEvery { AdbManager.getStatus(any()) } returns statusIpNull

    result = worker.doWork()
    outputData = (result as ListenableWorker.Result.Success).outputData
    assertEquals(true, outputData.getBoolean(HealthCheckWorker.KEY_DEGRADATION_DETECTED, false))

    // Test scenario 3: No degradation when initializing from never-enabled state
    val inputNeverEnabled = workDataOf(
      HealthCheckWorker.KEY_LAST_KNOWN_ENABLED to false,
      HealthCheckWorker.KEY_LAST_KNOWN_PORT to 0,
      HealthCheckWorker.KEY_LAST_KNOWN_IP to ""
    )
    every { mockParams.inputData } returns inputNeverEnabled
    val statusNewlyEnabled = AdbManager.AdbStatus(enabled = true, port = 5555, ip = "192.168.1.100")
    coEvery { AdbManager.getStatus(any()) } returns statusNewlyEnabled

    result = worker.doWork()
    outputData = (result as ListenableWorker.Result.Success).outputData
    assertEquals(false, outputData.getBoolean(HealthCheckWorker.KEY_DEGRADATION_DETECTED, true))
  }
}
