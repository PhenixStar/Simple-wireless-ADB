package com.phenix.wirelessadb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

  companion object {
    private const val TAG = "BootReceiver"
    private const val MAX_RETRIES = 3

    // Overridable so tests don't have to wait out real boot/retry delays
    internal var bootDelayMs = 5000L
    internal var retryDelayMs = 3000L
  }

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
        intent.action != "android.intent.action.QUICKBOOT_POWERON") {
      return
    }

    if (!PrefsManager.isEnableOnBoot(context)) {
      Log.d(TAG, "Enable-on-boot disabled, skipping")
      return
    }

    val port = PrefsManager.getPort(context)
    Log.i(TAG, "Boot completed, enabling ADB on port $port after ${bootDelayMs}ms delay")

    val pendingResult = goAsync()

    CoroutineScope(Dispatchers.IO).launch {
      try {
        // Wait for system services to stabilize after boot
        delay(bootDelayMs)

        var lastError: Throwable? = null
        for (attempt in 1..MAX_RETRIES) {
          val result = AdbManager.enable(port)
          if (result.isSuccess) {
            Log.i(TAG, "ADB enabled successfully on attempt $attempt")
            // Start the foreground service to keep it alive
            try {
              val status = AdbManager.getStatus(context)
              if (status.enabled && status.ip != null) {
                AdbService.start(context, status.ip, status.port)
              }
            } catch (e: Exception) {
              Log.w(TAG, "Failed to start AdbService: ${e.message}")
            }
            return@launch
          }
          lastError = result.exceptionOrNull()
          Log.w(TAG, "ADB enable attempt $attempt/$MAX_RETRIES failed: ${lastError?.message}")
          if (attempt < MAX_RETRIES) delay(retryDelayMs)
        }
        Log.e(TAG, "All $MAX_RETRIES attempts to enable ADB failed", lastError)
      } catch (e: Exception) {
        Log.e(TAG, "Boot enable failed with exception", e)
      } finally {
        pendingResult.finish()
      }
    }
  }
}
