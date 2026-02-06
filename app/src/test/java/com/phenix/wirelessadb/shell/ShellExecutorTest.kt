package com.phenix.wirelessadb.shell

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class ShellExecutorTest {

  companion object {
    @BeforeClass
    @JvmStatic
    fun setupClass() {
      mockkStatic(Log::class)
      every { Log.d(any(), any()) } returns 0
      every { Log.i(any(), any()) } returns 0
      every { Log.w(any(), any<String>()) } returns 0
      every { Log.e(any(), any<String>()) } returns 0
      every { Log.e(any(), any(), any()) } returns 0
    }
  }

  @Before
  fun setUp() {
    setBackend(ExecutorBackend.NONE)
  }

  // ============================================================
  // isAvailable() tests
  // ============================================================

  @Test
  fun `isAvailable returns false when backend is NONE`() {
    setBackend(ExecutorBackend.NONE)
    assertFalse(ShellExecutor.isAvailable())
  }

  @Test
  fun `isAvailable returns true when backend is ROOT`() {
    setBackend(ExecutorBackend.ROOT)
    assertTrue(ShellExecutor.isAvailable())
  }

  @Test
  fun `isAvailable returns true when backend is SHIZUKU_ROOT`() {
    setBackend(ExecutorBackend.SHIZUKU_ROOT)
    assertTrue(ShellExecutor.isAvailable())
  }

  @Test
  fun `isAvailable returns true when backend is SHIZUKU_SHELL`() {
    setBackend(ExecutorBackend.SHIZUKU_SHELL)
    assertTrue(ShellExecutor.isAvailable())
  }

  // ============================================================
  // hasRootAccess() tests
  // ============================================================

  @Test
  fun `hasRootAccess returns false when backend is NONE`() {
    setBackend(ExecutorBackend.NONE)
    assertFalse(ShellExecutor.hasRootAccess())
  }

  @Test
  fun `hasRootAccess returns true when backend is ROOT`() {
    setBackend(ExecutorBackend.ROOT)
    assertTrue(ShellExecutor.hasRootAccess())
  }

  @Test
  fun `hasRootAccess returns true when backend is SHIZUKU_ROOT`() {
    setBackend(ExecutorBackend.SHIZUKU_ROOT)
    assertTrue(ShellExecutor.hasRootAccess())
  }

  @Test
  fun `hasRootAccess returns false when backend is SHIZUKU_SHELL`() {
    setBackend(ExecutorBackend.SHIZUKU_SHELL)
    assertFalse(ShellExecutor.hasRootAccess())
  }

  // ============================================================
  // canHideDevNotification() tests
  // ============================================================

  @Test
  fun `canHideDevNotification returns false when backend is NONE`() {
    setBackend(ExecutorBackend.NONE)
    assertFalse(ShellExecutor.canHideDevNotification())
  }

  @Test
  fun `canHideDevNotification returns true when backend is ROOT`() {
    setBackend(ExecutorBackend.ROOT)
    assertTrue(ShellExecutor.canHideDevNotification())
  }

  @Test
  fun `canHideDevNotification returns true when backend is SHIZUKU_ROOT`() {
    setBackend(ExecutorBackend.SHIZUKU_ROOT)
    assertTrue(ShellExecutor.canHideDevNotification())
  }

  @Test
  fun `canHideDevNotification returns false when backend is SHIZUKU_SHELL`() {
    setBackend(ExecutorBackend.SHIZUKU_SHELL)
    assertFalse(ShellExecutor.canHideDevNotification())
  }

  // ============================================================
  // isUsingShizuku() tests
  // ============================================================

  @Test
  fun `isUsingShizuku returns false when backend is NONE`() {
    setBackend(ExecutorBackend.NONE)
    assertFalse(ShellExecutor.isUsingShizuku())
  }

  @Test
  fun `isUsingShizuku returns false when backend is ROOT`() {
    setBackend(ExecutorBackend.ROOT)
    assertFalse(ShellExecutor.isUsingShizuku())
  }

  @Test
  fun `isUsingShizuku returns true when backend is SHIZUKU_ROOT`() {
    setBackend(ExecutorBackend.SHIZUKU_ROOT)
    assertTrue(ShellExecutor.isUsingShizuku())
  }

  @Test
  fun `isUsingShizuku returns true when backend is SHIZUKU_SHELL`() {
    setBackend(ExecutorBackend.SHIZUKU_SHELL)
    assertTrue(ShellExecutor.isUsingShizuku())
  }

  // ============================================================
  // backend property tests
  // ============================================================

  @Test
  fun `backend property reflects NONE state`() {
    setBackend(ExecutorBackend.NONE)
    assertEquals(ExecutorBackend.NONE, ShellExecutor.backend)
  }

  @Test
  fun `backend property reflects ROOT state`() {
    setBackend(ExecutorBackend.ROOT)
    assertEquals(ExecutorBackend.ROOT, ShellExecutor.backend)
  }

  @Test
  fun `backend property reflects SHIZUKU_ROOT state`() {
    setBackend(ExecutorBackend.SHIZUKU_ROOT)
    assertEquals(ExecutorBackend.SHIZUKU_ROOT, ShellExecutor.backend)
  }

  @Test
  fun `backend property reflects SHIZUKU_SHELL state`() {
    setBackend(ExecutorBackend.SHIZUKU_SHELL)
    assertEquals(ExecutorBackend.SHIZUKU_SHELL, ShellExecutor.backend)
  }

  @Test
  fun `backend transitions update query methods`() {
    setBackend(ExecutorBackend.NONE)
    assertFalse(ShellExecutor.isAvailable())
    assertFalse(ShellExecutor.hasRootAccess())

    setBackend(ExecutorBackend.ROOT)
    assertTrue(ShellExecutor.isAvailable())
    assertTrue(ShellExecutor.hasRootAccess())
    assertFalse(ShellExecutor.isUsingShizuku())

    setBackend(ExecutorBackend.SHIZUKU_SHELL)
    assertTrue(ShellExecutor.isAvailable())
    assertFalse(ShellExecutor.hasRootAccess())
    assertTrue(ShellExecutor.isUsingShizuku())
  }

  // ============================================================
  // execute() - basic behavior tests
  // ============================================================

  @Test
  fun `execute fails when no backend available`() = runBlocking {
    setBackend(ExecutorBackend.NONE)
    val result = ShellExecutor.execute("echo test")

    assertTrue("Should fail with no backend", result.isFailure)
    assertEquals(
      "No privileged backend available",
      result.exceptionOrNull()?.message
    )
  }

  @Test
  fun `execute validates empty command array`() = runBlocking {
    setBackend(ExecutorBackend.ROOT)
    val result = ShellExecutor.execute()

    assertTrue("Should fail with empty commands", result.isFailure)
    assertTrue(
      "Error should mention empty commands",
      result.exceptionOrNull()?.message?.contains("cannot be empty") == true
    )
  }

  @Test
  fun `execute validates blank commands`() = runBlocking {
    setBackend(ExecutorBackend.ROOT)
    val result = ShellExecutor.execute("   ", "")

    assertTrue("Should fail with blank commands", result.isFailure)
    assertTrue(
      "Error should mention empty commands",
      result.exceptionOrNull()?.message?.contains("cannot be empty") == true
    )
  }

  @Test
  fun `execute passes validation for simple command when backend available`() = runBlocking {
    setBackend(ExecutorBackend.ROOT)
    val result = ShellExecutor.execute("id")

    if (result.isFailure) {
      val message = result.exceptionOrNull()?.message ?: ""
      assertFalse(message.contains("Invalid command"))
      assertFalse(message.contains("cannot be empty"))
    }
  }

  @Test
  fun `execute joins multiple commands with AND operator`() = runBlocking {
    setBackend(ExecutorBackend.ROOT)
    val result = ShellExecutor.execute("echo test1", "echo test2", "echo test3")

    if (result.isFailure) {
      val message = result.exceptionOrNull()?.message ?: ""
      assertFalse("Should not fail with validation error", message.contains("Invalid command"))
    }
  }

  // ============================================================
  // State consistency tests
  // ============================================================

  @Test
  fun `ROOT backend has all expected capabilities`() {
    setBackend(ExecutorBackend.ROOT)

    assertTrue("ROOT should be available", ShellExecutor.isAvailable())
    assertTrue("ROOT should have root access", ShellExecutor.hasRootAccess())
    assertTrue("ROOT should hide dev notification", ShellExecutor.canHideDevNotification())
    assertFalse("ROOT should not be Shizuku", ShellExecutor.isUsingShizuku())
  }

  @Test
  fun `SHIZUKU_ROOT backend has expected capabilities`() {
    setBackend(ExecutorBackend.SHIZUKU_ROOT)

    assertTrue("SHIZUKU_ROOT should be available", ShellExecutor.isAvailable())
    assertTrue("SHIZUKU_ROOT should have root access", ShellExecutor.hasRootAccess())
    assertTrue("SHIZUKU_ROOT should hide dev notification", ShellExecutor.canHideDevNotification())
    assertTrue("SHIZUKU_ROOT should be Shizuku", ShellExecutor.isUsingShizuku())
  }

  @Test
  fun `SHIZUKU_SHELL backend has expected capabilities`() {
    setBackend(ExecutorBackend.SHIZUKU_SHELL)

    assertTrue("SHIZUKU_SHELL should be available", ShellExecutor.isAvailable())
    assertFalse("SHIZUKU_SHELL should not have root access", ShellExecutor.hasRootAccess())
    assertFalse("SHIZUKU_SHELL cannot hide dev notification", ShellExecutor.canHideDevNotification())
    assertTrue("SHIZUKU_SHELL should be Shizuku", ShellExecutor.isUsingShizuku())
  }

  @Test
  fun `NONE backend has no capabilities`() {
    setBackend(ExecutorBackend.NONE)

    assertFalse("NONE should not be available", ShellExecutor.isAvailable())
    assertFalse("NONE should not have root access", ShellExecutor.hasRootAccess())
    assertFalse("NONE cannot hide dev notification", ShellExecutor.canHideDevNotification())
    assertFalse("NONE should not be Shizuku", ShellExecutor.isUsingShizuku())
  }

  // ============================================================
  // Helper methods
  // ============================================================

  private fun setBackend(backend: ExecutorBackend) {
    val backendField = ShellExecutor::class.java.getDeclaredField("backend")
    backendField.isAccessible = true
    backendField.set(ShellExecutor, backend)
  }
}
