package com.phenix.wirelessadb.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.phenix.wirelessadb.R
import com.phenix.wirelessadb.databinding.DialogAdbPairingBinding
import com.phenix.wirelessadb.pairing.AdbPairingManager
import kotlinx.coroutines.launch

/**
 * Dialog for ADB pairing on Android 11+.
 *
 * Allows users to pair their device with the wireless debugging service
 * without needing a PC for initial setup.
 */
class AdbPairingDialog : DialogFragment() {

  private var _binding: DialogAdbPairingBinding? = null
  private val binding get() = _binding!!

  private var onPairingSuccess: (() -> Unit)? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NORMAL, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = DialogAdbPairingBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    setupUI()
    observePairingState()
  }

  private fun setupUI() {
    // Check if pairing is supported
    if (!AdbPairingManager.isSupported()) {
      showUnsupportedMessage()
      return
    }

    // Cancel button
    binding.btnCancel.setOnClickListener {
      dismiss()
    }

    // Scan QR button
    binding.btnScanQr.setOnClickListener {
      launchQrScanner()
    }

    // Pair button
    binding.btnPair.setOnClickListener {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        startPairing()
      }
    }
  }

  /**
   * Launch QR scanner dialog to scan pairing code.
   */
  private fun launchQrScanner() {
    try {
      QrScannerDialog.newInstance { parsedData ->
        // Auto-fill port and code from scanned QR
        binding.portInput.setText(parsedData.port.toString())
        binding.codeInput.setText(parsedData.code)

        // Clear any previous errors
        binding.portLayout.error = null
        binding.codeLayout.error = null
        binding.errorText.isVisible = false
      }.show(childFragmentManager, QrScannerDialog.TAG)
    } catch (e: Exception) {
      // Handle scanner launch error gracefully
      showError(getString(R.string.scanner_error))
    }
  }

  private fun showUnsupportedMessage() {
    binding.portLayout.isVisible = false
    binding.codeLayout.isVisible = false
    binding.btnPair.isEnabled = false
    binding.errorText.apply {
      text = getString(R.string.pairing_not_supported)
      isVisible = true
    }
  }

  @RequiresApi(Build.VERSION_CODES.R)
  private fun startPairing() {
    val port = binding.portInput.text?.toString()?.toIntOrNull()
    val code = binding.codeInput.text?.toString()

    // Validate port
    if (port == null || port < 1 || port > 65535) {
      binding.portLayout.error = getString(R.string.error_invalid_port)
      return
    }
    binding.portLayout.error = null

    // Validate code
    if (code.isNullOrEmpty() || code.length != 6 || !code.all { it.isDigit() }) {
      binding.codeLayout.error = "Enter 6-digit code"
      return
    }
    binding.codeLayout.error = null

    // Disable inputs during pairing
    setInputsEnabled(false)
    showProgress(true)

    // Start pairing
    lifecycleScope.launch {
      val result = AdbPairingManager.pair(port, code)

      if (result.success) {
        showSuccess()
      } else {
        showError(result.message)
      }
    }
  }

  private fun observePairingState() {
    AdbPairingManager.state.observe(viewLifecycleOwner) { state ->
      when (state) {
        AdbPairingManager.PairingState.PAIRING -> {
          binding.statusText.text = getString(R.string.pairing_connecting)
        }
        AdbPairingManager.PairingState.PAIRED -> {
          binding.statusText.text = getString(R.string.pairing_success)
        }
        AdbPairingManager.PairingState.FAILED -> {
          binding.statusText.text = getString(R.string.pairing_failed)
        }
        else -> { }
      }
    }
  }

  private fun setInputsEnabled(enabled: Boolean) {
    binding.portInput.isEnabled = enabled
    binding.codeInput.isEnabled = enabled
    binding.btnPair.isEnabled = enabled
  }

  private fun showProgress(show: Boolean) {
    binding.statusContainer.isVisible = show
    binding.progressIndicator.isVisible = show
    binding.errorText.isVisible = false
  }

  private fun showSuccess() {
    binding.progressIndicator.isVisible = false
    binding.statusText.text = getString(R.string.pairing_success)

    // Notify success and close after delay
    onPairingSuccess?.invoke()

    view?.postDelayed({
      if (isAdded) {
        dismiss()
      }
    }, 1500)
  }

  private fun showError(message: String) {
    showProgress(false)
    setInputsEnabled(true)
    binding.errorText.apply {
      text = message
      isVisible = true
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  companion object {
    const val TAG = "AdbPairingDialog"

    /**
     * Create a new instance of the dialog.
     * @param onSuccess Callback when pairing succeeds
     */
    fun newInstance(onSuccess: (() -> Unit)? = null): AdbPairingDialog {
      return AdbPairingDialog().apply {
        onPairingSuccess = onSuccess
      }
    }

    /**
     * Show the dialog or an alert if not supported.
     */
    fun showOrAlert(
      fragment: androidx.fragment.app.Fragment,
      onSuccess: (() -> Unit)? = null
    ) {
      if (AdbPairingManager.isSupported()) {
        newInstance(onSuccess).show(fragment.childFragmentManager, TAG)
      } else {
        MaterialAlertDialogBuilder(fragment.requireContext())
          .setTitle(R.string.pairing_title)
          .setMessage(AdbPairingManager.getUnsupportedReason())
          .setPositiveButton(android.R.string.ok, null)
          .show()
      }
    }
  }
}
