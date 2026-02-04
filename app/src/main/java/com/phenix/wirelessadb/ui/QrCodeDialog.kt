package com.phenix.wirelessadb.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.phenix.wirelessadb.databinding.DialogQrBinding

/**
 * Dialog to display ADB connection command as a QR code.
 *
 * Users can scan this QR code with their PC/laptop to quickly
 * copy the adb connect command.
 */
class QrCodeDialog : DialogFragment() {

  private var _binding: DialogQrBinding? = null
  private val binding get() = _binding!!

  private var command: String = ""

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NORMAL, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
    command = arguments?.getString(ARG_COMMAND) ?: ""
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = DialogQrBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setupUI()
  }

  private fun setupUI() {
    // Display the command text
    binding.commandText.text = command

    // Generate and display QR code
    val qrBitmap = generateQrCode(command, QR_SIZE)
    if (qrBitmap != null) {
      binding.qrImage.setImageBitmap(qrBitmap)
    }

    // Close button
    binding.closeButton.setOnClickListener {
      dismiss()
    }
  }

  /**
   * Generate a QR code bitmap from text.
   *
   * @param content The text to encode in the QR code
   * @param size The width and height of the resulting bitmap
   * @return A Bitmap containing the QR code, or null if generation fails
   */
  private fun generateQrCode(content: String, size: Int): Bitmap? {
    if (content.isEmpty()) return null

    return try {
      val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8"
      )

      val writer = QRCodeWriter()
      val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

      val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
      for (x in 0 until size) {
        for (y in 0 until size) {
          bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
        }
      }
      bitmap
    } catch (e: Exception) {
      null
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  companion object {
    const val TAG = "QrCodeDialog"
    private const val ARG_COMMAND = "command"
    private const val QR_SIZE = 512

    /**
     * Create a new instance of the QR code dialog.
     * @param command The ADB connection command to display as QR
     */
    fun newInstance(command: String): QrCodeDialog {
      return QrCodeDialog().apply {
        arguments = Bundle().apply {
          putString(ARG_COMMAND, command)
        }
      }
    }
  }
}
