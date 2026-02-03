package com.phenix.wirelessadb.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * ViewPager2 adapter for the main tab navigation.
 *
 * TAB STRUCTURE (v1.3.0 - Simplified):
 * ====================================
 * Tab 0: ControlFragment - Mode-based connection control (Local/Tailscale/Warpgate/P2P)
 * Tab 1: DevicesFragment - Trusted device management + pending approvals
 * Tab 2: HelpFragment    - Help docs, downloads, theme settings
 *
 * v1.3.0 Changes:
 * - Replaced DashboardFragment + RemoteRelayFragment with ControlFragment
 * - Added DevicesFragment for trusted device management
 * - Reduced complexity: 3 focused tabs instead of redundant UI
 */
class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

  override fun getItemCount(): Int = TAB_COUNT

  override fun createFragment(position: Int): Fragment {
    return when (position) {
      TAB_CONTROL -> ControlFragment()   // Tab 0: Mode-based connection control
      TAB_DEVICES -> DevicesFragment()   // Tab 1: Trusted device management
      TAB_HELP -> HelpFragment()         // Tab 2: Help & downloads
      else -> throw IllegalArgumentException("Invalid tab position: $position")
    }
  }

  companion object {
    const val TAB_COUNT = 3

    // Tab indices (v1.3.0)
    const val TAB_CONTROL = 0  // ControlFragment: Mode-based connection control
    const val TAB_DEVICES = 1  // DevicesFragment: Trusted device management
    const val TAB_HELP = 2     // HelpFragment: Help & downloads
  }
}
