package com.phenix.wirelessadb.util

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Edge-to-edge window inset helpers.
 *
 * The app disables decor inset fitting (WindowCompat.setDecorFitsSystemWindows(window, false))
 * and consumes insets explicitly via these helpers instead of android:fitsSystemWindows.
 * This keeps layouts correct on every version, including Android 15's enforced
 * edge-to-edge, and on devices with display cutouts.
 */

private val systemBarTypes =
  WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

/**
 * Pads this view by the status bar (top) and cutout/system-bar side insets,
 * preserving the padding declared in the layout. Use on top app bars.
 */
fun View.applyTopSystemBarInsets() {
  applySystemBarInsets(top = true)
}

/**
 * Pads this view by the navigation bar (bottom) and cutout/system-bar side insets,
 * preserving the padding declared in the layout. Use on scrolling content roots
 * together with clipToPadding=false so content scrolls under the gesture bar
 * but never rests hidden behind it.
 */
fun View.applyBottomSystemBarInsets() {
  applySystemBarInsets(bottom = true)
}

private fun View.applySystemBarInsets(top: Boolean = false, bottom: Boolean = false) {
  val initialLeft = paddingLeft
  val initialTop = paddingTop
  val initialRight = paddingRight
  val initialBottom = paddingBottom

  ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
    val insets: Insets = windowInsets.getInsets(systemBarTypes)
    view.setPadding(
      initialLeft + insets.left,
      initialTop + if (top) insets.top else 0,
      initialRight + insets.right,
      initialBottom + if (bottom) insets.bottom else 0
    )
    windowInsets
  }
}
