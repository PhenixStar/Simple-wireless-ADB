package com.phenix.wirelessadb.util

import android.content.Context
import android.util.Log
import com.phenix.wirelessadb.PrefsManager
import com.phenix.wirelessadb.shell.ExecutorBackend
import com.phenix.wirelessadb.shell.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hides/shows the Android "USB/wireless debugging connected" notification.
 *
 * Requires ROOT: the levers below (global settings + SystemUI notification
 * channels) need WRITE_SECURE_SETTINGS / system access that a shell-mode
 * Shizuku backend (uid 2000) does not have.
 *
 * Two independent levers are used because ROMs differ:
 *  1. `global adb_notify 0` — suppresses the classic USB-debugging notification
 *     on AOSP-like builds. This is the only setting that is verifiable, so it
 *     is the source of truth for success.
 *  2. Blocking the SystemUI notification channel that actually posts the adb
 *     notification — needed on Android 11+ (wireless debugging) and on OEM
 *     ROMs where `adb_notify` is ignored. The channel id varies per ROM, so we
 *     discover it at runtime from `dumpsys notification` and fall back to a set
 *     of well-known ids.
 */
object NotificationHider {

  private const val TAG = "NotificationHider"
  private const val SYSTEMUI_PKG = "com.android.systemui"

  /** Well-known SystemUI channel ids that carry the adb notification. */
  private val KNOWN_ADB_CHANNELS = listOf("ADB", "adb", "USB", "DEVELOPER", "DEVELOPER_IMPORTANT")

  /** IMPORTANCE_NONE / IMPORTANCE_DEFAULT for `cmd notification set_channel_importance`. */
  private const val IMPORTANCE_NONE = 0
  private const val IMPORTANCE_DEFAULT = 3

  suspend fun hideUsbDebuggingNotification(): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isSupported()) {
      return@withContext Result.failure(
        Exception("Notification hiding requires ROOT access. Shizuku shell mode cannot perform this operation.")
      )
    }

    try {
      // Lever 1: the classic, verifiable global toggle.
      ShellExecutor.execute("settings put global adb_notify 0")

      // Lever 2: block whichever SystemUI channel actually posts the notification.
      val channels = (discoverAdbChannels() + KNOWN_ADB_CHANNELS).distinct()
      var channelBlocked = false
      for (id in channels) {
        val r = ShellExecutor.execute(
          "cmd notification set_channel_importance $SYSTEMUI_PKG $id $IMPORTANCE_NONE"
        )
        if (r.isSuccess) channelBlocked = true
      }

      // Success is defined by observable state, not by exit codes: `settings put`
      // returns 0 even for keys the ROM ignores. Verify the readback, and accept
      // a confirmed channel block as an alternative on ROMs without adb_notify.
      val notifyHidden = readAdbNotify() == "0"
      if (notifyHidden || channelBlocked) {
        Log.i(TAG, "Debugging notification hidden (adb_notify=$notifyHidden, channelBlocked=$channelBlocked)")
        Result.success(Unit)
      } else {
        Result.failure(Exception("Could not hide notification: adb_notify not applied and no adb channel found"))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to hide notification: ${e.message}")
      Result.failure(e)
    }
  }

  suspend fun showUsbDebuggingNotification(): Result<Unit> = withContext(Dispatchers.IO) {
    if (!isSupported()) {
      return@withContext Result.failure(Exception("Notification control requires ROOT access."))
    }

    try {
      ShellExecutor.execute("settings put global adb_notify 1")
      val channels = (discoverAdbChannels() + KNOWN_ADB_CHANNELS).distinct()
      for (id in channels) {
        ShellExecutor.execute(
          "cmd notification set_channel_importance $SYSTEMUI_PKG $id $IMPORTANCE_DEFAULT"
        )
      }
      Log.i(TAG, "Debugging notification restored")
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to show notification: ${e.message}")
      Result.failure(e)
    }
  }

  /**
   * Re-apply hiding if the user enabled it. The notification reappears whenever
   * adbd restarts (boot, reconnect, port change), so this must run after every
   * adb-enable, not only when the user taps the toggle.
   */
  suspend fun reapplyIfEnabled(context: Context) {
    if (isSupported() && PrefsManager.isHideDevNotification(context)) {
      hideUsbDebuggingNotification()
    }
  }

  /**
   * @return true if hidden, false if visible, null if it cannot be determined.
   */
  suspend fun isNotificationHidden(): Boolean? = withContext(Dispatchers.IO) {
    if (!isSupported()) return@withContext null
    when (readAdbNotify()) {
      "0" -> true
      null -> null
      else -> false
    }
  }

  /** Hiding requires a root-capable backend. */
  fun isSupported(): Boolean = ShellExecutor.backend.isRoot()

  /**
   * Read `global adb_notify` through the privileged backend. The `settings`
   * binary needs shell/system uid, so reading it from the app uid via a plain
   * process fails — it must go through ShellExecutor like the writes do.
   */
  private suspend fun readAdbNotify(): String? {
    val result = ShellExecutor.execute("settings get global adb_notify")
    return result.getOrNull()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
  }

  /**
   * Discover SystemUI notification channels whose id/name references adb or
   * debugging, so we block the right one even when the ROM uses a custom id.
   */
  private suspend fun discoverAdbChannels(): List<String> {
    val dump = ShellExecutor.execute(
      "dumpsys notification --noredact"
    ).getOrNull() ?: return emptyList()

    // Channel lines look like: NotificationChannel{mId='ADB', mName=... pkg=com.android.systemui ...}
    return Regex("""mId='([^']+)'[^}]*""")
      .findAll(dump)
      .map { it.groupValues[1] }
      .filter { it.contains("adb", true) || it.contains("debug", true) || it.contains("usb", true) }
      .distinct()
      .toList()
  }
}
