package com.phenix.wirelessadb.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.phenix.wirelessadb.R
import com.phenix.wirelessadb.databinding.DialogQrScannerBinding

/**
 * Dialog to scan QR codes for ADB pairing.
 *
 * Allows users to scan QR codes from Developer Options
 * to automatically fill in pairing port and code.
 */
class QrScannerDialog : DialogFragment() {

  private var _binding: DialogQrScannerBinding? = null
  private val binding get() = _binding!!

  private var onScanSuccess: ((ParsedPairingData) -> Unit)? = null
  private var isScanningActive = false

  private val cameraPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      startScanning()
    } else {
      showError(getString(R.string.scanner_permission_denied))
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NORMAL, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = DialogQrScannerBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setupUI()
    requestCameraPermissionAndStart()
  }

  private fun setupUI() {
    // Cancel button
    binding.btnCancel.setOnClickListener {
      dismiss()
    }
  }

  private fun requestCameraPermissionAndStart() {
    when {
      ContextCompat.checkSelfPermission(
        requireContext(),
        Manifest.permission.CAMERA
      ) == PackageManager.PERMISSION_GRANTED -> {
        startScanning()
      }
      else -> {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
      }
    }
  }

  private fun startScanning() {
    if (isScanningActive) return

    isScanningActive = true
    binding.errorText.isVisible = false

    // Initialize barcode scanner
    binding.barcodeScanner.decodeContinuous(object : BarcodeCallback {
      override fun barcodeResult(result: BarcodeResult?) {
        if (result == null) return

        // Stop scanning to prevent multiple scans
        pauseScanning()

        // Parse the QR code content
        val parsed = parseQrCode(result.text)
        if (parsed != null) {
          onScanSuccess?.invoke(parsed)
          dismiss()
        } else {
          showError(getString(R.string.scanner_error_invalid_qr))
          // Resume scanning after showing error
          view?.postDelayed({
            if (isAdded && isScanningActive) {
              resumeScanning()
            }
          }, 2000)
        }
      }

      override fun possibleResultPoints(resultPoints: List<ResultPoint>?) {
        // Optional: Can be used to draw scan overlay
      }
    })

    binding.barcodeScanner.resume()
  }

  /**
   * Parse QR code content to extract pairing port and code.
   *
   * Supports multiple formats:
   * - Plain text: "PORT:CODE" (e.g., "37045:123456")
   * - JSON: {"port": 37045, "code": "123456"}
   * - URI: adb://pair?port=37045&code=123456
   *
   * @param content The QR code content
   * @return ParsedPairingData if valid, null otherwise
   */
  private fun parseQrCode(content: String): ParsedPairingData? {
    if (content.isEmpty()) return null

    return try {
      // Try plain text format first: PORT:CODE
      if (content.contains(":") && !content.contains("{")) {
        val parts = content.split(":")
        if (parts.size == 2) {
          val port = parts[0].trim().toIntOrNull()
          val code = parts[1].trim()
          if (isValidPort(port) && isValidCode(code)) {
            return ParsedPairingData(port!!, code)
          }
        }
      }

      // Try JSON format: {"port": 37045, "code": "123456"}
      if (content.trim().startsWith("{")) {
        val portMatch = Regex("\"port\"\\s*:\\s*(\\d+)").find(content)
        val codeMatch = Regex("\"code\"\\s*:\\s*\"(\\d{6})\"").find(content)

        if (portMatch != null && codeMatch != null) {
          val port = portMatch.groupValues[1].toIntOrNull()
          val code = codeMatch.groupValues[1]
          if (isValidPort(port) && isValidCode(code)) {
            return ParsedPairingData(port!!, code)
          }
        }
      }

      // Try URI format: adb://pair?port=37045&code=123456
      if (content.startsWith("adb://")) {
        val portMatch = Regex("port=(\\d+)").find(content)
        val codeMatch = Regex("code=(\\d{6})").find(content)

        if (portMatch != null && codeMatch != null) {
          val port = portMatch.groupValues[1].toIntOrNull()
          val code = codeMatch.groupValues[1]
          if (isValidPort(port) && isValidCode(code)) {
            return ParsedPairingData(port!!, code)
          }
        }
      }

      null
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Validate port number is within valid range.
   */
  private fun isValidPort(port: Int?): Boolean {
    return port != null && port in 1..65535
  }

  /**
   * Validate pairing code is exactly 6 digits.
   */
  private fun isValidCode(code: String): Boolean {
    return code.length == 6 && code.all { it.isDigit() }
  }

  private fun showError(message: String) {
    binding.errorText.apply {
      text = message
      isVisible = true
    }
  }

  private fun pauseScanning() {
    binding.barcodeScanner.pause()
  }

  private fun resumeScanning() {
    binding.barcodeScanner.resume()
  }

  override fun onResume() {
    super.onResume()
    if (isScanningActive) {
      resumeScanning()
    }
  }

  override fun onPause() {
    super.onPause()
    pauseScanning()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  /**
   * Data class to hold parsed pairing information.
   */
  data class ParsedPairingData(
    val port: Int,
    val code: String
  )

  companion object {
    const val TAG = "QrScannerDialog"

    /**
     * Create a new instance of the QR scanner dialog.
     * @param onSuccess Callback when QR code is successfully scanned and parsed
     */
    fun newInstance(onSuccess: (ParsedPairingData) -> Unit): QrScannerDialog {
      return QrScannerDialog().apply {
        onScanSuccess = onSuccess
      }
    }
  }
}
