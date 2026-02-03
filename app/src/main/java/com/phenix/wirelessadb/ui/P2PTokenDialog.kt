package com.phenix.wirelessadb.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.phenix.wirelessadb.R
import com.phenix.wirelessadb.databinding.DialogQrCodeBinding
import com.phenix.wirelessadb.model.P2PToken
import com.phenix.wirelessadb.util.QrCodeGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Bottom sheet dialog showing P2P token with QR code (v1.2.0).
 *
 * Features:
 * - QR code for easy scanning
 * - Token masking/unmasking toggle
 * - Copy to clipboard
 * - Share intent
 * - Live expiry countdown
 */
class P2PTokenDialog : BottomSheetDialogFragment() {

  private var _binding: DialogQrCodeBinding? = null
  private val binding get() = _binding!!

  private var token: P2PToken? = null
  private var expiryUpdateJob: Job? = null
  private val scope = CoroutineScope(Dispatchers.Main)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NORMAL, R.style.Theme_WirelessAdb_Dialog)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = DialogQrCodeBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setupViews()
    loadQrCode()
    startExpiryUpdater()
  }

  private fun setupViews() {
    binding.apply {
      // Close button
      closeButton.setOnClickListener {
        dismiss()
      }

      // Copy token button
      copyButton.setOnClickListener {
        copyToken()
      }

      // Share button
      shareButton.setOnClickListener {
        shareToken()
      }

      // Show/hide toggle
      showHideButton.setOnClickListener {
        toggleTokenVisibility()
      }
    }
  }

  private fun loadQrCode() {
    val token = token ?: return

    binding.apply {
      // Generate QR code
      scope.launch(Dispatchers.IO) {
        val primaryColor = try {
          ContextCompat.getColor(
            requireContext(),
            com.google.android.material.R.color.material_dynamic_primary40
          )
        } catch (e: Exception) {
          Color.parseColor("#6750A4") // Default purple
        }

        QrCodeGenerator.generateWithColors(
          content = token.getShareableString(),
          foreground = primaryColor,
          background = Color.WHITE,
          size = 512
        ).onSuccess { qrBitmap ->
          launch(Dispatchers.Main) {
            qrCodeImage.setImageBitmap(qrBitmap)
            loadingProgress.visibility = View.GONE
            qrCodeImage.visibility = View.VISIBLE
          }
        }.onFailure { _ ->
          launch(Dispatchers.Main) {
            loadingProgress.visibility = View.GONE
            errorText.visibility = View.VISIBLE
            errorText.text = getString(R.string.p2p_qr_error)
          }
        }
      }

      // Set token text (masked by default)
      tokenCodeText.text = token.masked()

      // Update expiry
      updateExpiryText()
    }
  }

  private fun startExpiryUpdater() {
    expiryUpdateJob = scope.launch {
      while (isActive && token?.isExpired() == false) {
        updateExpiryText()
        delay(1000) // Update every second
      }
    }
  }

  private fun updateExpiryText() {
    val token = token ?: return
    binding.apply {
      if (token.isExpired()) {
        expiryText.text = getString(R.string.p2p_token_expired)
        expiryText.setTextColor(
          ContextCompat.getColor(requireContext(), R.color.warning_orange)
        )
        expiryProgress.max = 100
        expiryProgress.progress = 0
      } else {
        expiryText.text = getString(R.string.p2p_token_expires_in, token.getRemainingTime())
        val totalDuration = P2PToken.DEFAULT_EXPIRY_MS
        val remaining = token.expiresAt - System.currentTimeMillis()
        val progress = ((remaining.toFloat() / totalDuration) * 100).toInt()
        expiryProgress.max = 100
        expiryProgress.progress = progress
      }
    }
  }

  private fun copyToken() {
    val token = token ?: return
    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("P2P Token", token.code))
    Toast.makeText(requireContext(), R.string.p2p_token_copied, Toast.LENGTH_SHORT).show()
  }

  private fun shareToken() {
    val token = token ?: return

    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(android.content.Intent.EXTRA_TEXT, token.getShareableString())
      putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.p2p_share_subject))
    }

    startActivity(android.content.Intent.createChooser(intent, getString(R.string.share)))
  }

  private fun toggleTokenVisibility() {
    val token = token ?: return
    binding.apply {
      val isCurrentlyVisible = tokenCodeText.text.toString() == token.code

      tokenCodeText.text = if (isCurrentlyVisible) {
        token.masked()
      } else {
        token.code
      }

      showHideButton.setIconResource(
        if (isCurrentlyVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
      )
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    expiryUpdateJob?.cancel()
    _binding = null
  }

  companion object {
    private const val ARG_TOKEN_CODE = "token_code"
    private const val ARG_DEVICE_HASH = "device_hash"
    private const val ARG_SESSION_ID = "session_id"
    private const val ARG_EXPIRES_AT = "expires_at"

    fun create(token: P2PToken): P2PTokenDialog {
      return P2PTokenDialog().apply {
        arguments = Bundle().apply {
          putString(ARG_TOKEN_CODE, token.code)
          putString(ARG_DEVICE_HASH, token.deviceHash)
          putString(ARG_SESSION_ID, token.sessionId)
          putLong(ARG_EXPIRES_AT, token.expiresAt)
        }
      }
    }
  }

  /**
   * Reconstruct P2PToken from arguments.
   * Called after view is created.
   */
  private fun getTokenFromArguments(): P2PToken? {
    val args = arguments ?: return null
    val code = args.getString(ARG_TOKEN_CODE) ?: return null
    val deviceHash = args.getString(ARG_DEVICE_HASH) ?: return null
    val sessionId = args.getString(ARG_SESSION_ID) ?: return null
    val expiresAt = args.getLong(ARG_EXPIRES_AT)

    return P2PToken(
      code = code,
      deviceHash = deviceHash,
      sessionId = sessionId,
      expiresAt = expiresAt
    )
  }
}
