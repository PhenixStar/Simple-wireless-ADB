package com.phenix.wirelessadb.shell

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Unified shell executor that abstracts root and Shizuku backends.
 * Automatically selects the best available backend.
 */
object ShellExecutor {

  private const val TAG = "ShellExecutor"
  private const val TIMEOUT_SECONDS = 10L

  /**
   * Currently active backend.
   */
  var backend: ExecutorBackend = ExecutorBackend.NONE
    private set

  /**
   * Initialize and detect the best available backend.
   * Should be called early in app lifecycle.
   */
  suspend fun initialize(): ExecutorBackend = withContext(Dispatchers.IO) {
    backend = detectBestBackend()
    Log.i(TAG, "Initialized with backend: $backend")
    backend
  }

  /**
   * Detect the best available execution backend.
   * Priority: ROOT > SHIZUKU_ROOT > SHIZUKU_SHELL > NONE
   */
  suspend fun detectBestBackend(): ExecutorBackend = withContext(Dispatchers.IO) {
    // Try native root first (most capable)
    if (isRootAvailable()) {
      return@withContext ExecutorBackend.ROOT
    }

    // Fallback to Shizuku (checks both root and shell mode)
    if (ShizukuExecutor.isAvailable()) {
      val shizukuBackend = ShizukuExecutor.detectBackend()
      if (shizukuBackend != ExecutorBackend.NONE) {
        return@withContext shizukuBackend
      }
    }

    ExecutorBackend.NONE
  }

  /**
   * Execute shell commands using the current backend.
   * @param commands Commands to execute (joined with &&)
   * @return Result with stdout on success, error on failure
   */
  suspend fun execute(vararg commands: String): Result<String> = withContext(Dispatchers.IO) {
    val cmd = commands.joinToString(" && ")
    Log.d(TAG, "Executing via $backend: $cmd")

    when (backend) {
      ExecutorBackend.ROOT -> executeAsRoot(cmd)
      ExecutorBackend.SHIZUKU_ROOT,
      ExecutorBackend.SHIZUKU_SHELL -> ShizukuExecutor.execute(cmd)
      ExecutorBackend.NONE -> Result.failure(Exception("No privileged backend available"))
    }
  }

  /**
   * Check if any privileged backend is available.
   */
  fun isAvailable(): Boolean = backend != ExecutorBackend.NONE

  /**
   * Check if the current backend has root-level access.
   */
  fun hasRootAccess(): Boolean = backend.isRoot()

  /**
   * Check if the current backend can hide developer notifications.
   * Only ROOT and SHIZUKU_ROOT can do this.
   */
  fun canHideDevNotification(): Boolean = backend.canHideDevNotification()

  /**
   * Check if the current backend uses Shizuku.
   */
  fun isUsingShizuku(): Boolean = backend.isShizuku()

  /**
   * Refresh the backend detection.
   * Useful when Shizuku state changes (e.g., permission granted).
   */
  suspend fun refresh(): ExecutorBackend {
    backend = detectBestBackend()
    Log.i(TAG, "Refreshed backend: $backend")
    return backend
  }

  /**
   * Check if root (su) is available.
   */
  private suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
    try {
      val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
      val completed = process.waitFor(5, TimeUnit.SECONDS)
      if (!completed) {
        process.destroyForcibly()
        return@withContext false
      }
      val output = process.inputStream.bufferedReader().readText()
      output.contains("uid=0")
    } catch (e: Exception) {
      Log.w(TAG, "Root check failed: ${e.message}")
      false
    }
  }

  /**
   * Execute command as root via su.
   */
  private suspend fun executeAsRoot(command: String): Result<String> = withContext(Dispatchers.IO) {
    try {
      val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
      val completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)

      if (!completed) {
        process.destroyForcibly()
        return@withContext Result.failure(Exception("Command timed out"))
      }

      val exitCode = process.exitValue()
      val stdout = process.inputStream.bufferedReader().readText()
      val stderr = process.errorStream.bufferedReader().readText()

      if (exitCode == 0) {
        Result.success(stdout)
      } else {
        Result.failure(Exception("Exit code $exitCode: $stderr"))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Root execution failed: ${e.message}")
      Result.failure(e)
    }
  }
}
