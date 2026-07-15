package com.phenix.wirelessadb

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.MarginPageTransformer
import com.google.android.material.tabs.TabLayoutMediator
import com.phenix.wirelessadb.databinding.ActivityMainBinding
import com.phenix.wirelessadb.theme.ThemeManager
import com.phenix.wirelessadb.ui.MainPagerAdapter
import com.phenix.wirelessadb.util.AccessibilityHelper
import com.phenix.wirelessadb.util.applyTopSystemBarInsets
import com.phenix.wirelessadb.util.isReducedMotionEnabled
import com.phenix.wirelessadb.viewmodel.AdbViewModel

class MainActivity : AppCompatActivity() {

  private lateinit var binding: ActivityMainBinding
  private val viewModel: AdbViewModel by viewModels()

  private val pendingAuthReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val clientIp = intent.getStringExtra(AdbService.EXTRA_CLIENT_IP)
      if (clientIp != null) {
        viewModel.setPendingApproval(clientIp)
      }
    }
  }

  // Network change listener - refreshes status when VPN connects/disconnects
  private val networkChangeReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      viewModel.refreshStatus()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    // Apply saved theme before super.onCreate()
    ThemeManager.applyTheme(this)

    super.onCreate(savedInstanceState)

    // Apply accent color overlay (or Material You dynamic color) before inflating views
    ThemeManager.applyAccent(this)

    WindowCompat.setDecorFitsSystemWindows(window, false)

    // API 26 can't render dark nav-bar icons (windowLightNavigationBar is API 27+),
    // so a fully transparent nav bar would leave white buttons invisible on light
    // content. Use a translucent scrim there instead.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
      @Suppress("DEPRECATION")
      window.navigationBarColor = Color.argb(0x66, 0, 0, 0)
    }

    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    // Edge-to-edge: app bar absorbs the status bar / cutout insets
    binding.appBarLayout.applyTopSystemBarInsets()

    setSupportActionBar(binding.toolbar)

    requestNotificationPermission()
    setupViewPager()

    LocalBroadcastManager.getInstance(this).registerReceiver(
      pendingAuthReceiver,
      IntentFilter(AdbService.ACTION_PENDING_AUTH)
    )

    // Register network change listener for VPN connect/disconnect
    @Suppress("DEPRECATION")
    registerReceiver(
      networkChangeReceiver,
      IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
    )
  }

  override fun onDestroy() {
    LocalBroadcastManager.getInstance(this).unregisterReceiver(pendingAuthReceiver)
    unregisterReceiver(networkChangeReceiver)
    super.onDestroy()
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshStatus()
    viewModel.refreshTrustedCount()
  }

  private fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
          this,
          arrayOf(Manifest.permission.POST_NOTIFICATIONS),
          REQUEST_NOTIFICATION_PERMISSION
        )
      }
    }
  }

  private fun setupViewPager() {
    val adapter = MainPagerAdapter(this)
    binding.viewPager.adapter = adapter

    // Add smooth page transformer for fragment transitions (respecting reduced motion)
    if (!isReducedMotionEnabled()) {
      binding.viewPager.setPageTransformer(
        MarginPageTransformer(16) // 16dp margin between pages
      )
    }

    TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
      val tabName = when (position) {
        MainPagerAdapter.TAB_CONTROL -> getString(R.string.tab_control)
        MainPagerAdapter.TAB_DEVICES -> getString(R.string.tab_devices)
        MainPagerAdapter.TAB_STATISTICS -> getString(R.string.tab_statistics)
        MainPagerAdapter.TAB_HELP -> getString(R.string.tab_help)
        else -> null
      }
      tab.text = tabName
      // Set content description for accessibility
      tab.contentDescription = when (position) {
        MainPagerAdapter.TAB_CONTROL -> getString(R.string.cd_tab_control)
        MainPagerAdapter.TAB_DEVICES -> getString(R.string.cd_tab_devices)
        MainPagerAdapter.TAB_STATISTICS -> getString(R.string.cd_tab_statistics)
        MainPagerAdapter.TAB_HELP -> getString(R.string.cd_tab_help)
        else -> tabName
      }
    }.attach()
  }

  companion object {
    private const val REQUEST_NOTIFICATION_PERMISSION = 100
  }
}
