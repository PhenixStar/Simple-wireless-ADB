package com.phenix.wirelessadb.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.phenix.wirelessadb.AdbManager
import com.phenix.wirelessadb.PrefsManager
import com.phenix.wirelessadb.model.ConnectionMode
import com.phenix.wirelessadb.model.IndicatorState
import com.phenix.wirelessadb.model.StatusIndicators
import com.phenix.wirelessadb.relay.DeviceAuthManager
import com.phenix.wirelessadb.relay.TailscaleHelper
import com.phenix.wirelessadb.shell.ShellExecutor
import com.phenix.wirelessadb.util.NotificationHider
import com.phenix.wirelessadb.warpgate.WarpgateConfig
import com.phenix.wirelessadb.warpgate.WarpgateManager
import kotlinx.coroutines.launch

class AdbViewModel(application: Application) : AndroidViewModel(application) {

  private val context = application.applicationContext
  private val authManager = DeviceAuthManager(context)

  // ADB Status
  private val _adbStatus = MutableLiveData<AdbManager.AdbStatus>()
  val adbStatus: LiveData<AdbManager.AdbStatus> = _adbStatus

  // Root availability
  private val _hasRoot = MutableLiveData<Boolean>()
  val hasRoot: LiveData<Boolean> = _hasRoot

  // Loading state
  private val _isLoading = MutableLiveData(false)
  val isLoading: LiveData<Boolean> = _isLoading

  // Error messages
  private val _error = MutableLiveData<String?>()
  val error: LiveData<String?> = _error

  // Pending approval IP
  private val _pendingApprovalIp = MutableLiveData<String?>()
  val pendingApprovalIp: LiveData<String?> = _pendingApprovalIp

  // Trusted device count
  private val _trustedDeviceCount = MutableLiveData(0)
  val trustedDeviceCount: LiveData<Int> = _trustedDeviceCount

  // Status indicators for toolbar
  private val _statusIndicators = MutableLiveData(StatusIndicators.DEFAULT)
  val statusIndicators: LiveData<StatusIndicators> = _statusIndicators

  // Current connection mode
  private val _connectionMode = MutableLiveData(ConnectionMode.LOCAL_WIFI)
  val connectionMode: LiveData<ConnectionMode> = _connectionMode

  // Developer notification hidden state
  private val _devNotificationHidden = MutableLiveData(false)
  val devNotificationHidden: LiveData<Boolean> = _devNotificationHidden

  // Warpgate connection status
  private val _warpgateConnected = MutableLiveData(false)
  val warpgateConnected: LiveData<Boolean> = _warpgateConnected

  // P2P Manager and state (v1.2.0)
  private val p2pManager by lazy { com.phenix.wirelessadb.p2p.P2PManager.getInstance(context) }
  val p2pState: LiveData<com.phenix.wirelessadb.p2p.P2PState> get() = p2pManager.connectionState
  val p2pToken: LiveData<com.phenix.wirelessadb.model.P2PToken?> get() = p2pManager.currentToken
  val p2pError: LiveData<String?> get() = p2pManager.error
  val p2pTunnelEndpoint: LiveData<String?> get() = p2pManager.tunnelEndpoint

  // Connection Health Manager and state (v1.3.0)
  val healthState: LiveData<com.phenix.wirelessadb.health.ConnectionHealthManager.HealthState>
    get() = com.phenix.wirelessadb.health.ConnectionHealthManager.healthState

  init {
    initializeShellExecutor()
    loadSavedPreferences()
    checkRootAndRefresh()
  }

  private fun initializeShellExecutor() {
    viewModelScope.launch {
      ShellExecutor.initialize()
    }
  }

  private fun loadSavedPreferences() {
    _connectionMode.value = PrefsManager.getConnectionMode(context)
    _devNotificationHidden.value = PrefsManager.isHideDevNotification(context)
  }

  fun checkRootAndRefresh() {
    viewModelScope.launch {
      val rootAvailable = AdbManager.isRootAvailable()
      _hasRoot.value = rootAvailable
      if (rootAvailable) {
        refreshStatus()
      }
      refreshStatusIndicators()
    }
  }

  fun refreshStatus() {
    viewModelScope.launch {
      val status = AdbManager.getStatus(context)
      _adbStatus.value = status
      _trustedDeviceCount.value = authManager.getTrustedDeviceCount()
      refreshStatusIndicators()
    }
  }

  /**
   * Refresh toolbar status indicators based on current state.
   */
  fun refreshStatusIndicators() {
    viewModelScope.launch {
      val status = _adbStatus.value
      val tailscaleIp = TailscaleHelper.getTailscaleIp(context)
      val warpgateConfig = PrefsManager.getWarpgateConfig(context)
      val p2pState = p2pManager.connectionState.value

      _statusIndicators.value = StatusIndicators(
        localAdb = when {
          status?.enabled == true && status.ip != null -> IndicatorState.ACTIVE
          status?.enabled == true -> IndicatorState.WARNING
          else -> IndicatorState.INACTIVE
        },
        tailscale = if (tailscaleIp != null) IndicatorState.ACTIVE else IndicatorState.INACTIVE,
        warpgate = when {
          WarpgateManager.isConnected -> IndicatorState.ACTIVE
          warpgateConfig.enabled -> IndicatorState.WARNING
          else -> IndicatorState.INACTIVE
        },
        p2p = when (p2pState) {
          com.phenix.wirelessadb.p2p.P2PState.CONNECTED -> IndicatorState.ACTIVE
          com.phenix.wirelessadb.p2p.P2PState.TOKEN_READY,
          com.phenix.wirelessadb.p2p.P2PState.CONNECTING -> IndicatorState.WARNING
          else -> IndicatorState.INACTIVE
        }
      )
    }
  }

  fun toggleAdb(port: Int) {
    viewModelScope.launch {
      _isLoading.value = true
      _error.value = null

      val currentStatus = _adbStatus.value
      val isEnabled = currentStatus?.enabled == true

      val result = if (isEnabled) {
        AdbManager.disable()
      } else {
        PrefsManager.setPort(context, port)
        AdbManager.enable(port)
      }

      result.onSuccess {
        refreshStatus()
      }.onFailure { e ->
        _error.value = e.message
      }

      _isLoading.value = false
    }
  }

  fun setPendingApproval(ip: String?) {
    _pendingApprovalIp.value = ip
  }

  fun refreshTrustedCount() {
    _trustedDeviceCount.value = authManager.getTrustedDeviceCount()
  }

  fun clearError() {
    _error.value = null
  }

  // Connection Mode
  fun setConnectionMode(mode: ConnectionMode) {
    _connectionMode.value = mode
    PrefsManager.setConnectionMode(context, mode)
    refreshStatusIndicators()
  }

  fun getConnectionMode(): ConnectionMode = _connectionMode.value ?: ConnectionMode.LOCAL_WIFI

  /**
   * Get the appropriate ADB connect command based on current mode.
   * Falls back to local command if preferred mode isn't available.
   */
  fun getConnectionCommand(): String {
    val status = _adbStatus.value ?: return ""
    if (!status.enabled) return ""

    val mode = _connectionMode.value ?: ConnectionMode.LOCAL_WIFI
    val tailscaleIp = TailscaleHelper.getTailscaleIp(context)
    val localIp = status.ip

    // Try to get command for preferred mode, fallback to local if unavailable
    return when (mode) {
      ConnectionMode.LOCAL_WIFI -> {
        localIp?.let { "adb connect $it:${status.port}" } ?: ""
      }
      ConnectionMode.TAILSCALE_DIRECT -> {
        tailscaleIp?.let { "adb connect $it:${status.port}" }
          ?: localIp?.let { "adb connect $it:${status.port}" }
          ?: ""
      }
      ConnectionMode.TAILSCALE_RELAY -> {
        tailscaleIp?.let { "adb connect $it:${PrefsManager.getRelayPort(context)}" }
          ?: localIp?.let { "adb connect $it:${status.port}" }
          ?: ""
      }
      ConnectionMode.WARPGATE -> {
        WarpgateManager.getAdbCommand()
          ?: localIp?.let { "adb connect $it:${status.port}" }
          ?: ""
      }
      ConnectionMode.P2P_TOKEN -> {
        // Use P2P tunnel endpoint when connected
        p2pManager.getAdbCommand()
          ?: localIp?.let { "adb connect $it:${status.port}" }
          ?: ""
      }
    }
  }

  // Developer Notification Hiding
  fun hideDevNotification() {
    viewModelScope.launch {
      val result = NotificationHider.hideUsbDebuggingNotification()
      result.onSuccess {
        _devNotificationHidden.value = true
        PrefsManager.setHideDevNotification(context, true)
      }.onFailure { e ->
        _error.value = "Failed to hide notification: ${e.message}"
      }
    }
  }

  fun showDevNotification() {
    viewModelScope.launch {
      val result = NotificationHider.showUsbDebuggingNotification()
      result.onSuccess {
        _devNotificationHidden.value = false
        PrefsManager.setHideDevNotification(context, false)
      }.onFailure { e ->
        _error.value = "Failed to show notification: ${e.message}"
      }
    }
  }

  fun isNotificationHidingSupported(): Boolean = NotificationHider.isSupported()

  // Warpgate
  fun connectWarpgate() {
    viewModelScope.launch {
      _isLoading.value = true
      _error.value = null

      val config = PrefsManager.getWarpgateConfig(context)
      val result = WarpgateManager.connect(config)

      result.onSuccess {
        _warpgateConnected.value = true
        refreshStatusIndicators()
      }.onFailure { e ->
        _error.value = "Warpgate connection failed: ${e.message}"
        _warpgateConnected.value = false
      }

      _isLoading.value = false
    }
  }

  fun disconnectWarpgate() {
    WarpgateManager.disconnect()
    _warpgateConnected.value = false
    refreshStatusIndicators()
  }

  fun getWarpgateConfig(): WarpgateConfig = PrefsManager.getWarpgateConfig(context)

  fun setWarpgateConfig(config: WarpgateConfig) {
    PrefsManager.setWarpgateConfig(context, config)
    refreshStatusIndicators()
  }

  // Preferences
  fun isEnableOnBoot(): Boolean = PrefsManager.isEnableOnBoot(context)
  fun setEnableOnBoot(enabled: Boolean) = PrefsManager.setEnableOnBoot(context, enabled)

  fun isRelayEnabled(): Boolean = PrefsManager.isRelayEnabled(context)
  fun setRelayEnabled(enabled: Boolean) = PrefsManager.setRelayEnabled(context, enabled)

  fun getPort(): Int = PrefsManager.getPort(context)
  fun getRelayPort(): Int = PrefsManager.getRelayPort(context)

  // Health Monitoring (v1.3.0)
  fun getHealthCheckInterval(): Long = PrefsManager.getHealthCheckInterval(context)
  fun setHealthCheckInterval(interval: Long) = PrefsManager.setHealthCheckInterval(context, interval)

  fun isAutoReconnectEnabled(): Boolean = PrefsManager.isAutoReconnectEnabled(context)
  fun setAutoReconnectEnabled(enabled: Boolean) {
    PrefsManager.setAutoReconnectEnabled(context, enabled)
    if (enabled && _adbStatus.value?.enabled == true) {
      startHealthMonitoring()
    } else {
      stopHealthMonitoring()
    }
  }

  fun startHealthMonitoring() {
    viewModelScope.launch {
      val interval = getHealthCheckInterval()
      com.phenix.wirelessadb.health.ConnectionHealthManager.startMonitoring(context, interval / 1000)
    }
  }

  fun stopHealthMonitoring() {
    viewModelScope.launch {
      com.phenix.wirelessadb.health.ConnectionHealthManager.stopMonitoring(context)
    }
  }

  // P2P Token methods (v1.2.0)
  fun generateP2PToken() {
    viewModelScope.launch {
      p2pManager.generateToken()
      refreshStatusIndicators()
    }
  }

  fun generatePersistentP2PToken() {
    viewModelScope.launch {
      p2pManager.generatePersistentToken()
      refreshStatusIndicators()
    }
  }

  fun revokeP2PToken() {
    p2pManager.revokeToken()
    refreshStatusIndicators()
  }

  fun connectWithP2PToken(tokenCode: String) {
    viewModelScope.launch {
      p2pManager.connectWithToken(tokenCode)
      refreshStatusIndicators()
    }
  }

  fun disconnectP2P() {
    p2pManager.disconnect()
    refreshStatusIndicators()
  }

  fun clearP2PError() {
    p2pManager.clearError()
  }

  override fun onCleared() {
    super.onCleared()
    // Disconnect Warpgate when ViewModel is cleared
    WarpgateManager.disconnect()
    // Cleanup P2P resources
    p2pManager.destroy()
  }
}
