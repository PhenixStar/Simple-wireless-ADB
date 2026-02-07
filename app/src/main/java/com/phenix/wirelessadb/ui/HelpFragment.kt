package com.phenix.wirelessadb.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.phenix.wirelessadb.BuildConfig
import com.phenix.wirelessadb.R
import com.phenix.wirelessadb.databinding.FragmentHelpBinding
import com.phenix.wirelessadb.theme.AccentColor
import com.phenix.wirelessadb.theme.ThemeManager
import com.phenix.wirelessadb.theme.ThemeMode

class HelpFragment : Fragment() {

  private var _binding: FragmentHelpBinding? = null
  private val binding get() = _binding!!

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentHelpBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setupViews()
  }

  private fun setupViews() {
    binding.apply {
      versionText.text = getString(R.string.version_format, BuildConfig.VERSION_NAME)

      // Theme Mode Toggle
      setupThemeModeToggle()

      // Accent Color Buttons
      setupAccentColorButtons()

      githubButton.setOnClickListener {
        openUrl(GITHUB_URL)
      }

      emailButton.setOnClickListener {
        sendEmail(DEV_EMAIL, getString(R.string.email_subject_contact))
      }

      bugReportButton.setOnClickListener {
        sendBugReport()
      }

      downloadScriptsButton.setOnClickListener {
        openUrl(SCRIPTS_URL)
      }

      // Download buttons (v1.2.0)
      downloadAdbButton.setOnClickListener {
        openUrl(ADB_DOWNLOAD_URL)
      }

      downloadTailscaleButton.setOnClickListener {
        openUrl(TAILSCALE_URL)
      }

      downloadNgrokButton.setOnClickListener {
        openUrl(NGROK_URL)
      }
    }
  }

  private fun setupThemeModeToggle() {
    binding.apply {
      // Set initial selection based on current theme
      val currentMode = ThemeManager.getThemeMode(requireContext())
      val checkedId = when (currentMode) {
        ThemeMode.SYSTEM -> R.id.themeModeSystem
        ThemeMode.LIGHT -> R.id.themeModeLight
        ThemeMode.DARK -> R.id.themeModeDark
      }
      themeModeToggle.check(checkedId)

      // Handle toggle changes
      themeModeToggle.addOnButtonCheckedListener { _, buttonId, isChecked ->
        if (isChecked) {
          val newMode = when (buttonId) {
            R.id.themeModeSystem -> ThemeMode.SYSTEM
            R.id.themeModeLight -> ThemeMode.LIGHT
            R.id.themeModeDark -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
          }
          ThemeManager.setThemeMode(requireContext(), newMode)
        }
      }
    }
  }

  private fun setupAccentColorButtons() {
    binding.apply {
      val accentButtons = listOf(
        accentBlue to AccentColor.BLUE,
        accentTeal to AccentColor.TEAL,
        accentPurple to AccentColor.PURPLE,
        accentOrange to AccentColor.ORANGE,
        accentPink to AccentColor.PINK,
        accentGreen to AccentColor.GREEN
      )

      // Set up click handlers and initial selection indicator
      val currentAccent = ThemeManager.getAccentColor(requireContext())

      accentButtons.forEach { (button, color) ->
        // Add check icon to currently selected color
        updateAccentButtonSelection(button, color == currentAccent)

        button.setOnClickListener {
          ThemeManager.setAccentColor(requireContext(), color)

          // Update selection indicators
          accentButtons.forEach { (btn, clr) ->
            updateAccentButtonSelection(btn, clr == color)
          }

          // Recreate activity to apply new accent color immediately
          activity?.recreate()
        }
      }
    }
  }

  private fun updateAccentButtonSelection(button: MaterialButton, isSelected: Boolean) {
    if (isSelected) {
      button.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check)
      button.iconSize = resources.getDimensionPixelSize(R.dimen.accent_check_size)
      button.iconTint = ContextCompat.getColorStateList(requireContext(), R.color.accent_on_primary)
    } else {
      button.icon = null
      button.iconSize = 0
    }
  }

  private fun openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    startActivity(intent)
  }

  private fun sendEmail(email: String, subject: String, body: String = "") {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
      data = Uri.parse("mailto:")
      putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
      putExtra(Intent.EXTRA_SUBJECT, subject)
      if (body.isNotEmpty()) {
        putExtra(Intent.EXTRA_TEXT, body)
      }
    }
    try {
      startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
      Toast.makeText(requireContext(), "No email app found", Toast.LENGTH_SHORT).show()
    }
  }

  private fun sendBugReport() {
    val deviceInfo = buildString {
      appendLine("--- Device Info ---")
      appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
      appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
      appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
      appendLine("-------------------")
      appendLine()
      appendLine("Describe your issue:")
      appendLine()
    }

    sendEmail(
      DEV_EMAIL,
      getString(R.string.email_subject_bug),
      deviceInfo
    )
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  companion object {
    private const val GITHUB_URL = "https://github.com/Danz17/Simple-wireless-ADB"
    private const val SCRIPTS_URL = "https://github.com/Danz17/Simple-wireless-ADB/releases"
    private const val DEV_EMAIL = "alaa@nulled.ai"

    // Download URLs (v1.2.0)
    private const val ADB_DOWNLOAD_URL = "https://developer.android.com/tools/releases/platform-tools"
    private const val TAILSCALE_URL = "https://play.google.com/store/apps/details?id=com.tailscale.ipn"
    private const val NGROK_URL = "https://ngrok.com/download"
  }
}
