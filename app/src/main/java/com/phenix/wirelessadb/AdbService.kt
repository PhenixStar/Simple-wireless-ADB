package com.phenix.wirelessadb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.phenix.wirelessadb.relay.AdbRelayServer
import com.phenix.wirelessadb.relay.TailscaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AdbService : Service() {

  private val TAG = "AdbService"
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var relayServer: AdbRelayServer? = null
  private var currentIp: String = "Unknown"
  private var currentPort: Int = 5555
  private var currentRelayEnabled: Boolean = false

  companion object {
    private const val CHANNEL_ID = "adb_service_channel"
    private const val NOTIFICATION_ID = 1001
    const val ACTION_PENDING_AUTH = "com.phenix.wirelessadb.PENDING_AUTH"
    const val ACTION_APPROVE_DEVICE = "com.phenix.wirelessadb.APPROVE_DEVICE"
    const val ACTION_DENY_DEVICE = "com.phenix.wirelessadb.DENY_DEVICE"
    const val ACTION_NETWORK_CHANGED = "com.phenix.wirelessadb.NETWORK_CHANGED"
    const val EXTRA_CLIENT_IP = "client_ip"

    fun start(context: Context, ip: String, port: Int, relayEnabled: Boolean = false) {
      val intent = Intent(context, AdbService::class.java).apply {
        putExtra("ip", ip)
        putExtra("port", port)
        putExtra("relay_enabled", relayEnabled)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, AdbService::class.java))
    }

    fun approveDevice(context: Context, clientIp: String) {
      val intent = Intent(context, AdbService::class.java).apply {
        action = ACTION_APPROVE_DEVICE
        putExtra(EXTRA_CLIENT_IP, clientIp)
      }
      context.startService(intent)
    }

    fun denyDevice(context: Context, clientIp: String) {
      val intent = Intent(context, AdbService::class.java).apply {
        action = ACTION_DENY_DEVICE
        putExtra(EXTRA_CLIENT_IP, clientIp)
      }
      context.startService(intent)
    }

    fun onNetworkChanged(context: Context) {
      val intent = Intent(context, AdbService::class.java).apply {
        action = ACTION_NETWORK_CHANGED
      }
      context.startService(intent)
    }
  }

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_APPROVE_DEVICE -> {
        val clientIp = intent.getStringExtra(EXTRA_CLIENT_IP)
        if (clientIp != null) {
          relayServer?.approveDevice(clientIp)
        }
        return START_STICKY
      }
      ACTION_DENY_DEVICE -> {
        val clientIp = intent.getStringExtra(EXTRA_CLIENT_IP)
        if (clientIp != null) {
          relayServer?.denyDevice(clientIp)
        }
        return START_STICKY
      }
      ACTION_NETWORK_CHANGED -> {
        handleNetworkChange()
        return START_STICKY
      }
    }

    val ip = intent?.getStringExtra("ip") ?: "Unknown"
    val port = intent?.getIntExtra("port", 5555) ?: 5555
    val relayEnabled = intent?.getBooleanExtra("relay_enabled", false) ?: false

    // Update current state
    currentIp = ip
    currentPort = port
    currentRelayEnabled = relayEnabled

    val tailscaleIp = TailscaleHelper.getTailscaleIp()
    startForeground(NOTIFICATION_ID, createNotification(ip, port, tailscaleIp, relayEnabled))

    if (relayEnabled) {
      startRelayServer(port)
    }

    return START_STICKY
  }

  private fun startRelayServer(adbPort: Int) {
    val relayPort = PrefsManager.getRelayPort(this)

    relayServer = AdbRelayServer(
      context = this,
      relayPort = relayPort,
      adbPort = adbPort,
      onPendingAuth = { clientIp ->
        broadcastPendingAuth(clientIp)
      },
      onConnectionEstablished = { _ ->
        updateNotificationWithActiveConnection()
      },
      onConnectionClosed = { _ ->
        updateNotificationWithActiveConnection()
      }
    )

    serviceScope.launch {
      try {
        relayServer?.start()
      } catch (e: Exception) {
        Log.e(TAG, "Failed to start relay server", e)
      }
    }
  }

  private fun broadcastPendingAuth(clientIp: String) {
    val intent = Intent(ACTION_PENDING_AUTH).apply {
      putExtra(EXTRA_CLIENT_IP, clientIp)
    }
    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
  }

  private fun handleNetworkChange() {
    serviceScope.launch {
      val status = AdbManager.getStatus(this@AdbService)
      if (status.enabled && status.ip != null) {
        currentIp = status.ip
        updateNotification()
      }
    }
  }

  private fun updateNotification() {
    val tailscaleIp = TailscaleHelper.getTailscaleIp()
    val notification = createNotification(currentIp, currentPort, tailscaleIp, currentRelayEnabled)
    val notificationManager = getSystemService(NotificationManager::class.java)
    notificationManager.notify(NOTIFICATION_ID, notification)
  }

  private fun updateNotificationWithActiveConnection() {
    // Could update notification to show active connections
  }

  override fun onDestroy() {
    relayServer?.stop()
    relayServer = null
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "ADB Service",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Shows when Wireless ADB is active"
        setShowBadge(false)
      }
      val manager = getSystemService(NotificationManager::class.java)
      manager.createNotificationChannel(channel)
    }
  }

  private fun createNotification(
    ip: String,
    port: Int,
    tailscaleIp: String?,
    relayEnabled: Boolean
  ): Notification {
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE
    )

    val contentText = buildString {
      append("adb connect $ip:$port")
      if (relayEnabled && tailscaleIp != null) {
        val relayPort = PrefsManager.getRelayPort(this@AdbService)
        append("\nRemote: $tailscaleIp:$relayPort")
      }
    }

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("Wireless ADB Active")
      .setContentText(contentText)
      .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
      .setSmallIcon(R.drawable.ic_notification)
      .setOngoing(true)
      .setContentIntent(pendingIntent)
      .build()
  }
}
