package com.phenix.wirelessadb.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.phenix.wirelessadb.AdbService
import com.phenix.wirelessadb.R
import com.phenix.wirelessadb.databinding.FragmentControlBinding
import com.phenix.wirelessadb.model.ConnectionMode
import com.phenix.wirelessadb.p2p.P2PState
import com.phenix.wirelessadb.pairing.AdbPairingManager
import com.phenix.wirelessadb.util.AccessibilityHelper.announceForAccessibility
import com.phenix.wirelessadb.util.AccessibilityHelper.setStateDescription
import com.phenix.wirelessadb.relay.TailscaleHelper
import com.phenix.wirelessadb.viewmodel.AdbViewModel
import com.phenix.wirelessadb.warpgate.WarpgateConfig
import com.phenix.wirelessadb.util.applyBottomSystemBarInsets

/**
 * TAB 1: CONTROL (v1.3.0 - Mode-Based UI)
 * =======================================
 * Unified control center with mode selector:
 * - Status Card: IP:Port + Copy
 * - Mode Selector: Local/Tailscale/Warpgate/P2P
 * - Config Card: ViewFlipper with mode-specific configs
 * - Settings Card: Collapsible boot/notification
 */
class ControlFragment : Fragment() {

  private var _binding: FragmentControlBinding? = null
  private val binding get() = _binding!!
  private val viewModel: AdbViewModel by activityViewModels()

  // Mode indices for ViewFlipper
  private enum class Mode(val index: Int) {
    LOCAL(0),
    TAILSCALE(1),
    WARPGATE(2),
    P2P(3)
  }

  // P2P token visibility state
  private var isTokenVisible = false

  // Handler for token expiry updates
  private val handler = Handler(Looper.getMainLooper())
  private val expiryUpdateRunnable = object : Runnable {
    override fun run() {
      updateTokenExpiry()
      handler.postDelayed(this, 1000)
    }
  }

  // Settings expanded state
  private var isSettingsExpanded = false

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentControlBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    // Edge-to-edge: keep scrollable content clear of the gesture/navigation bar
    view.applyBottomSystemBarInsets()
    setupViews()
    observeViewModel()
  }

  private fun setupViews() {
    binding.apply {
      // Copy button
      copyButton.setOnClickListener {
        copyCommand()
        copyButton.announceForAccessibility(getString(R.string.announcement_copied))
      }

      // Mode selector
      setupModeSelector()

      // Settings toggle
      setupSettingsToggle()

      // Mode-specific configs
      setupLocalConfig()
      setupTailscaleConfig()
      setupWarpgateConfig()
      setupP2PConfig()
    }
  }

  private fun setupModeSelector() {
    binding.apply {
      // Set initial mode selection
      val currentMode = viewModel.getConnectionMode()
      val checkedId = when (currentMode) {
        ConnectionMode.LOCAL_WIFI -> R.id.modeLocal
        ConnectionMode.TAILSCALE_DIRECT, ConnectionMode.TAILSCALE_RELAY -> R.id.modeTailscale
        ConnectionMode.WARPGATE -> R.id.modeWarpgate
        ConnectionMode.P2P_TOKEN -> R.id.modeP2P
      }
      modeToggleGroup.check(checkedId)
      updateModeConfig(currentMode)

      // Handle mode changes
      modeToggleGroup.addOnButtonCheckedListener { _, buttonId, isChecked ->
        if (isChecked) {
          val newMode = when (buttonId) {
            R.id.modeLocal -> ConnectionMode.LOCAL_WIFI
            R.id.modeTailscale -> ConnectionMode.TAILSCALE_RELAY
            R.id.modeWarpgate -> ConnectionMode.WARPGATE
            R.id.modeP2P -> ConnectionMode.P2P_TOKEN
            else -> ConnectionMode.LOCAL_WIFI
          }
          viewModel.setConnectionMode(newMode)
          updateModeConfig(newMode)
        }
      }
    }
  }

  private fun updateModeConfig(mode: ConnectionMode) {
    val flipperIndex = when (mode) {
      ConnectionMode.LOCAL_WIFI -> Mode.LOCAL.index
      ConnectionMode.TAILSCALE_DIRECT, ConnectionMode.TAILSCALE_RELAY -> Mode.TAILSCALE.index
      ConnectionMode.WARPGATE -> Mode.WARPGATE.index
      ConnectionMode.P2P_TOKEN -> Mode.P2P.index
    }
    binding.configFlipper.displayedChild = flipperIndex

    // Start/stop P2P expiry timer
    if (mode == ConnectionMode.P2P_TOKEN) {
      handler.post(expiryUpdateRunnable)
    } else {
      handler.removeCallbacks(expiryUpdateRunnable)
    }

    updateCommandDisplay()
  }

  private fun setupSettingsToggle() {
    binding.apply {
      settingsHeader.setOnClickListener {
        isSettingsExpanded = !isSettingsExpanded
        settingsContent.visibility = if (isSettingsExpanded) View.VISIBLE else View.GONE
        settingsExpandIcon.rotation = if (isSettingsExpanded) 180f else 0f
      }

      // Boot switch
      bootSwitch.isChecked = viewModel.isEnableOnBoot()
      bootSwitch.setOnCheckedChangeListener { _, checked ->
        bootSwitch.setStateDescription(getString(R.string.switch_boot), checked)
        bootSwitch.announceForAccessibility(getString(R.string.announcement_settings_changed))
        viewModel.setEnableOnBoot(checked)
      }

      // Hide notification switch
      hideNotificationSwitch.isChecked = viewModel.devNotificationHidden.value ?: false
      hideNotificationSwitch.isEnabled = viewModel.isNotificationHidingSupported()
      hideNotificationSwitch.setOnCheckedChangeListener { _, checked ->
        hideNotificationSwitch.setStateDescription(getString(R.string.settings_hide_notification), checked)
        hideNotificationSwitch.announceForAccessibility(getString(R.string.announcement_settings_changed))
        if (checked) {
          viewModel.hideDevNotification()
        } else {
          viewModel.showDevNotification()
        }
      }

      // Health monitoring switch
      healthMonitoringSwitch.isChecked = viewModel.isAutoReconnectEnabled()
      healthMonitoringSwitch.setOnCheckedChangeListener { _, checked ->
        healthMonitoringSwitch.setStateDescription(getString(R.string.settings_health_monitoring), checked)
        healthMonitoringSwitch.announceForAccessibility(getString(R.string.announcement_settings_changed))
        viewModel.setAutoReconnectEnabled(checked)
      }
    }
  }

  // ==================== LOCAL CONFIG ====================
  private fun setupLocalConfig() {
    val localView = binding.configFlipper.getChildAt(Mode.LOCAL.index)
    val portInput = localView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.portInput)
    val enableButton = localView.findViewById<com.google.android.material.button.MaterialButton>(R.id.enableAdbButton)

    portInput.setText(viewModel.getPort().toString())

    enableButton.setOnClickListener {
      val port = portInput.text.toString().toIntOrNull() ?: 5555
      viewModel.toggleAdb(port)
    }
  }

  // ==================== TAILSCALE CONFIG ====================
  private fun setupTailscaleConfig() {
    val tailscaleView = binding.configFlipper.getChildAt(Mode.TAILSCALE.index)
    val relaySwitch = tailscaleView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.relaySwitch)

    relaySwitch.isChecked = viewModel.isRelayEnabled()
    relaySwitch.setOnCheckedChangeListener { _, checked ->
      relaySwitch.setStateDescription(getString(R.string.toggle_tailscale_relay), checked)
      viewModel.setRelayEnabled(checked)
      if (checked) {
        viewModel.setConnectionMode(ConnectionMode.TAILSCALE_RELAY)
        // Launch Tailscale if not active
        if (!TailscaleHelper.isTailscaleActive()) {
          TailscaleHelper.launchTailscale(requireContext())
        }
      } else {
        viewModel.setConnectionMode(ConnectionMode.TAILSCALE_DIRECT)
      }
      restartServiceIfNeeded()
      updateCommandDisplay()
    }
  }

  // ==================== WARPGATE CONFIG ====================
  private fun setupWarpgateConfig() {
    val warpgateView = binding.configFlipper.getChildAt(Mode.WARPGATE.index)
    val hostInput = warpgateView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.warpgateHostInput)
    val portInput = warpgateView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.warpgatePortInput)
    val usernameInput = warpgateView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.warpgateUsernameInput)
    val passwordInput = warpgateView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.warpgatePasswordInput)
    val targetInput = warpgateView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.warpgateTargetInput)
    val connectButton = warpgateView.findViewById<com.google.android.material.button.MaterialButton>(R.id.warpgateConnectButton)

    // Load saved config
    val config = viewModel.getWarpgateConfig()
    hostInput.setText(config.host)
    portInput.setText(config.port.toString())
    usernameInput.setText(config.username)
    passwordInput.setText(config.password)
    targetInput.setText(config.targetName)

    connectButton.setOnClickListener {
      val isConnected = viewModel.warpgateConnected.value ?: false
      if (isConnected) {
        viewModel.disconnectWarpgate()
      } else {
        // Save config before connecting
        val newConfig = WarpgateConfig(
          enabled = true,
          host = hostInput.text.toString().trim(),
          port = portInput.text.toString().toIntOrNull() ?: 8443,
          username = usernameInput.text.toString().trim(),
          password = passwordInput.text.toString(),
          targetName = targetInput.text.toString().trim().ifEmpty { "adb" }
        )
        viewModel.setWarpgateConfig(newConfig)
        viewModel.connectWarpgate()
      }
    }
  }

  // ==================== P2P CONFIG ====================
  private fun setupP2PConfig() {
    val p2pView = binding.configFlipper.getChildAt(Mode.P2P.index)
    val showHideButton = p2pView.findViewById<com.google.android.material.button.MaterialButton>(R.id.p2pShowHideButton)
    val copyButton = p2pView.findViewById<com.google.android.material.button.MaterialButton>(R.id.p2pCopyButton)
    val qrButton = p2pView.findViewById<com.google.android.material.button.MaterialButton>(R.id.p2pQrButton)
    val revokeButton = p2pView.findViewById<com.google.android.material.button.MaterialButton>(R.id.p2pRevokeButton)
    val generateButton = p2pView.findViewById<com.google.android.material.button.MaterialButton>(R.id.p2pGenerateButton)
    val tokenInput = p2pView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.p2pTokenInput)
    val connectButton = p2pView.findViewById<com.google.android.material.button.MaterialButton>(R.id.p2pConnectButton)

    generateButton.setOnClickListener {
      viewModel.generateP2PToken()
      generateButton.announceForAccessibility(getString(R.string.announcement_token_generated))
    }

    revokeButton.setOnClickListener {
      viewModel.revokeP2PToken()
      isTokenVisible = false
    }

    showHideButton.setOnClickListener {
      isTokenVisible = !isTokenVisible
      updateP2PTokenDisplay()
    }

    copyButton.setOnClickListener {
      viewModel.p2pToken.value?.let { token ->
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("P2P Token", token.code))
        Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show()
      }
    }

    qrButton.setOnClickListener {
      viewModel.p2pToken.value?.let { token ->
        P2PTokenDialog.create(token).show(childFragmentManager, "p2p_qr_dialog")
      }
    }

    connectButton.setOnClickListener {
      val tokenCode = tokenInput.text.toString().trim()
      if (tokenCode.isNotEmpty()) {
        viewModel.connectWithP2PToken(tokenCode)
      } else {
        Toast.makeText(requireContext(), "Enter a token code", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun updateP2PTokenDisplay() {
    val p2pView = binding.configFlipper.getChildAt(Mode.P2P.index)
    val tokenText = p2pView.findViewById<android.widget.TextView>(R.id.p2pTokenText)
    val showHideButton = p2pView.findViewById<com.google.android.material.button.MaterialButton>(R.id.p2pShowHideButton)

    val token = viewModel.p2pToken.value ?: return
    tokenText.text = if (isTokenVisible) token.code else token.masked()
    showHideButton.setIconResource(
      if (isTokenVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
    )
  }

  private fun updateTokenExpiry() {
    val p2pView = binding.configFlipper.getChildAt(Mode.P2P.index)
    val expiresText = p2pView.findViewById<android.widget.TextView>(R.id.p2pExpiresText)

    val token = viewModel.p2pToken.value ?: return
    if (token.isExpired()) {
      expiresText.text = getString(R.string.p2p_token_expired)
      expiresText.setTextColor(requireContext().getColor(R.color.warning_orange))
    } else {
      expiresText.text = getString(R.string.p2p_expires_in, token.getRemainingTime())
      expiresText.setTextColor(requireContext().getColor(R.color.text_secondary))
    }
  }

  // ==================== VIEW MODEL OBSERVERS ====================
  private fun observeViewModel() {
    // Root availability
    viewModel.hasRoot.observe(viewLifecycleOwner) { hasRoot ->
      binding.rootWarning.visibility = if (hasRoot) View.GONE else View.VISIBLE
      updateLocalConfigEnabled(hasRoot)
    }

    // ADB Status
    viewModel.adbStatus.observe(viewLifecycleOwner) { status ->
      binding.apply {
        val isEnabled = status.enabled && status.ip != null

        // Update status card
        if (isEnabled) {
          statusText.text = getString(R.string.status_connected)
          statusIcon.setImageResource(R.drawable.ic_indicator_wifi)
          statusIcon.imageTintList = context?.getColorStateList(R.color.status_active)
          ipPortText.text = "${status.ip}:${status.port}"
          copyButton.isEnabled = true
        } else {
          statusText.text = getString(R.string.status_disabled)
          statusIcon.setImageResource(R.drawable.ic_indicator_wifi)
          statusIcon.imageTintList = context?.getColorStateList(R.color.status_inactive)
          ipPortText.text = "---.---.---.---:${status.port}"
          copyButton.isEnabled = false
        }

        // Update local config button text
        updateLocalEnableButton(status.enabled)

        // Update Tailscale config
        updateTailscaleConfig(status)

        // Update command
        updateCommandDisplay()
      }
    }

    // Warpgate connection status
    viewModel.warpgateConnected.observe(viewLifecycleOwner) { connected ->
      updateWarpgateConnectionState(connected)
      updateCommandDisplay()
    }

    // Developer notification state
    viewModel.devNotificationHidden.observe(viewLifecycleOwner) { hidden ->
      binding.hideNotificationSwitch.isChecked = hidden
    }

    // P2P Token
    viewModel.p2pToken.observe(viewLifecycleOwner) { token ->
      val p2pView = binding.configFlipper.getChildAt(Mode.P2P.index)
      val tokenSection = p2pView.findViewById<View>(R.id.p2pTokenSection)
      val generateButton = p2pView.findViewById<com.google.android.material.button.MaterialButton>(R.id.p2pGenerateButton)

      if (token != null) {
        tokenSection.visibility = View.VISIBLE
        generateButton.visibility = View.GONE
        updateP2PTokenDisplay()
      } else {
        tokenSection.visibility = View.GONE
        generateButton.visibility = View.VISIBLE
        isTokenVisible = false
      }
    }

    // P2P State
    viewModel.p2pState.observe(viewLifecycleOwner) { state ->
      updateP2PConnectionState(state)
    }

    // Loading state
    viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
      updateLoadingState(loading)
    }

    // Error messages
    viewModel.error.observe(viewLifecycleOwner) { error ->
      error?.let {
        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
        viewModel.clearError()
      }
    }

    // P2P Error
    viewModel.p2pError.observe(viewLifecycleOwner) { error ->
      error?.let {
        Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
        viewModel.clearP2PError()
      }
    }

    // Connection Health
    viewModel.healthState.observe(viewLifecycleOwner) { healthState ->
      updateHealthIndicator(healthState)
    }
  }

  private fun updateLocalConfigEnabled(hasRoot: Boolean) {
    val localView = binding.configFlipper.getChildAt(Mode.LOCAL.index)
    val enableButton = localView.findViewById<com.google.android.material.button.MaterialButton>(R.id.enableAdbButton)
    enableButton.isEnabled = hasRoot
  }

  private fun updateLocalEnableButton(isEnabled: Boolean) {
    val localView = binding.configFlipper.getChildAt(Mode.LOCAL.index)
    val enableButton = localView.findViewById<com.google.android.material.button.MaterialButton>(R.id.enableAdbButton)
    enableButton.text = getString(if (isEnabled) R.string.btn_disable else R.string.btn_enable)
  }

  private fun updateTailscaleConfig(status: com.phenix.wirelessadb.AdbManager.AdbStatus) {
    val tailscaleView = binding.configFlipper.getChildAt(Mode.TAILSCALE.index)
    val indicator = tailscaleView.findViewById<android.widget.ImageView>(R.id.tailscaleIndicator)
    val statusText = tailscaleView.findViewById<android.widget.TextView>(R.id.tailscaleStatusText)
    val ipText = tailscaleView.findViewById<android.widget.TextView>(R.id.tailscaleIpText)
    val portInfo = tailscaleView.findViewById<android.widget.TextView>(R.id.portInfoText)

    if (status.tailscaleIp != null) {
      indicator.setBackgroundResource(R.drawable.ic_status_active)
      statusText.text = getString(R.string.tailscale_connected)
      ipText.text = status.tailscaleIp
    } else {
      indicator.setBackgroundResource(R.drawable.ic_status_inactive)
      statusText.text = getString(R.string.tailscale_not_connected)
      ipText.text = "---"
    }

    val isRelay = viewModel.isRelayEnabled()
    portInfo.text = if (isRelay) {
      "Port: ${viewModel.getRelayPort()} (relay)"
    } else {
      "Port: ${status.port} (direct)"
    }
  }

  private fun updateWarpgateConnectionState(connected: Boolean) {
    val warpgateView = binding.configFlipper.getChildAt(Mode.WARPGATE.index)
    val connectButton = warpgateView.findViewById<com.google.android.material.button.MaterialButton>(R.id.warpgateConnectButton)
    val statusText = warpgateView.findViewById<android.widget.TextView>(R.id.warpgateStatusText)
    val hostInput = warpgateView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.warpgateHostInput)
    val portInput = warpgateView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.warpgatePortInput)
    val usernameInput = warpgateView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.warpgateUsernameInput)
    val passwordInput = warpgateView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.warpgatePasswordInput)
    val targetInput = warpgateView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.warpgateTargetInput)

    if (connected) {
      connectButton.text = getString(R.string.btn_disconnect)
      statusText.text = getString(R.string.warpgate_connected)
      statusText.setTextColor(requireContext().getColor(R.color.status_green))
      // Disable inputs while connected
      hostInput.isEnabled = false
      portInput.isEnabled = false
      usernameInput.isEnabled = false
      passwordInput.isEnabled = false
      targetInput.isEnabled = false
    } else {
      connectButton.text = getString(R.string.btn_connect)
      statusText.text = getString(R.string.warpgate_disconnected)
      statusText.setTextColor(requireContext().getColor(R.color.text_secondary))
      // Enable inputs when disconnected
      hostInput.isEnabled = true
      portInput.isEnabled = true
      usernameInput.isEnabled = true
      passwordInput.isEnabled = true
      targetInput.isEnabled = true
    }
  }

  private fun updateP2PConnectionState(state: P2PState) {
    val p2pView = binding.configFlipper.getChildAt(Mode.P2P.index)
    val statusText = p2pView.findViewById<android.widget.TextView>(R.id.p2pStatusText)
    val connectButton = p2pView.findViewById<com.google.android.material.button.MaterialButton>(R.id.p2pConnectButton)

    val statusStr = when (state) {
      P2PState.IDLE -> getString(R.string.p2p_status_idle)
      P2PState.TOKEN_READY -> getString(R.string.p2p_status_ready)
      P2PState.CONNECTING -> getString(R.string.p2p_status_connecting)
      P2PState.CONNECTED -> {
        val endpoint = viewModel.p2pTunnelEndpoint.value
        if (endpoint != null) {
          "${getString(R.string.p2p_status_connected)}\nTunnel: $endpoint"
        } else {
          getString(R.string.p2p_status_connected)
        }
      }
      P2PState.FAILED -> getString(R.string.p2p_status_failed)
    }
    statusText.text = statusStr

    val color = when (state) {
      P2PState.CONNECTED -> R.color.status_green
      P2PState.FAILED -> R.color.warning_orange
      else -> R.color.text_secondary
    }
    statusText.setTextColor(requireContext().getColor(color))

    when (state) {
      P2PState.CONNECTING -> {
        connectButton.isEnabled = false
        connectButton.text = getString(R.string.p2p_status_connecting)
      }
      P2PState.CONNECTED -> {
        connectButton.isEnabled = true
        connectButton.text = getString(R.string.btn_disconnect)
        connectButton.setOnClickListener { viewModel.disconnectP2P() }
      }
      else -> {
        connectButton.isEnabled = true
        connectButton.text = getString(R.string.btn_connect)
        connectButton.setOnClickListener {
          val tokenInput = p2pView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.p2pTokenInput)
          val tokenCode = tokenInput.text.toString().trim()
          if (tokenCode.isNotEmpty()) {
            viewModel.connectWithP2PToken(tokenCode)
          }
        }
      }
    }
  }

  private fun updateLoadingState(loading: Boolean) {
    val localView = binding.configFlipper.getChildAt(Mode.LOCAL.index)
    val enableButton = localView.findViewById<com.google.android.material.button.MaterialButton>(R.id.enableAdbButton)
    enableButton.isEnabled = !loading && viewModel.hasRoot.value == true

    val warpgateView = binding.configFlipper.getChildAt(Mode.WARPGATE.index)
    val connectButton = warpgateView.findViewById<com.google.android.material.button.MaterialButton>(R.id.warpgateConnectButton)
    connectButton.isEnabled = !loading

    if (loading) {
      val statusText = warpgateView.findViewById<android.widget.TextView>(R.id.warpgateStatusText)
      statusText.text = getString(R.string.warpgate_connecting)
    }

    val p2pView = binding.configFlipper.getChildAt(Mode.P2P.index)
    val generateButton = p2pView.findViewById<com.google.android.material.button.MaterialButton>(R.id.p2pGenerateButton)
    val p2pConnectButton = p2pView.findViewById<com.google.android.material.button.MaterialButton>(R.id.p2pConnectButton)
    generateButton.isEnabled = !loading
    p2pConnectButton.isEnabled = !loading
  }

  private fun updateCommandDisplay() {
    binding.apply {
      val command = viewModel.getConnectionCommand()
      commandText.text = command.ifEmpty { getString(R.string.adb_not_active) }
      copyButton.isEnabled = command.isNotEmpty()
    }
  }

  private fun updateHealthIndicator(healthState: com.phenix.wirelessadb.health.ConnectionHealthManager.HealthState) {
    binding.apply {
      when (healthState) {
        com.phenix.wirelessadb.health.ConnectionHealthManager.HealthState.HEALTHY -> {
          healthIcon.visibility = View.VISIBLE
          healthIcon.setImageResource(R.drawable.ic_health)
          healthIcon.contentDescription = "Connection health: healthy"
        }
        com.phenix.wirelessadb.health.ConnectionHealthManager.HealthState.DEGRADED -> {
          healthIcon.visibility = View.VISIBLE
          healthIcon.setImageResource(R.drawable.ic_health_warning)
          healthIcon.contentDescription = "Connection health: degraded"
        }
        com.phenix.wirelessadb.health.ConnectionHealthManager.HealthState.FAILED -> {
          healthIcon.visibility = View.VISIBLE
          healthIcon.setImageResource(R.drawable.ic_health_warning)
          healthIcon.contentDescription = "Connection health: failed"
        }
        com.phenix.wirelessadb.health.ConnectionHealthManager.HealthState.UNKNOWN -> {
          healthIcon.visibility = View.GONE
        }
      }
    }
  }

  private fun restartServiceIfNeeded() {
    val status = viewModel.adbStatus.value ?: return
    if (!status.enabled) return

    status.ip?.let { ip ->
      AdbService.stop(requireContext())
      val relayEnabled = viewModel.isRelayEnabled()
      AdbService.start(requireContext(), ip, status.port, relayEnabled)
    }
  }

  private fun copyCommand() {
    val command = binding.commandText.text.toString()
    if (command.isEmpty() || command == getString(R.string.adb_not_active)) {
      return
    }
    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("ADB Command", command))
    Toast.makeText(requireContext(), R.string.copied, Toast.LENGTH_SHORT).show()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    handler.removeCallbacks(expiryUpdateRunnable)
    _binding = null
  }
}
