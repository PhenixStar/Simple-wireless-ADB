package com.phenix.wirelessadb.util

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import kotlin.math.max
import kotlin.math.min

/**
 * Validates touch target sizes meet accessibility guidelines (v1.2.0 Phase 4).
 *
 * Minimum: 48x48dp (Android) / 44x44pt (iOS)
 *
 * Usage:
 * ```kotlin
 * // Check single view
 * if (!myButton.isTouchTargetValid()) {
 *     // Fix the button size
 * }
 *
 * // Validate entire layout
 * val invalidViews = binding.root.validateTouchTargets()
 * ```
 */
object TouchTargetValidator {

  const val MIN_SIZE_DP = 48

  /**
   * Check if view meets minimum touch target size.
   *
   * @param minSizeDp Minimum size in density-independent pixels
   * @return true if view meets or exceeds minimum size
   */
  fun View.isTouchTargetValid(minSizeDp: Int = MIN_SIZE_DP): Boolean {
    val sizePx = min(width, height)
    val minSizePx = minSizeDp * resources.displayMetrics.density
    return sizePx >= minSizePx
  }

  /**
   * Get the effective touch target size (including padding and margins).
   *
   * @return Effective touch target size in pixels
   */
  fun View.getEffectiveTouchSize(): Int {
    val left = (left - (paddingLeft + translationX)).toInt()
    val top = (top - (paddingTop + translationY)).toInt()
    val right = (right + paddingRight).toInt()
    val bottom = (bottom + paddingBottom).toInt()

    return min(right - left, bottom - top)
  }

  /**
   * Get touch target info for debugging.
   *
   * @return String describing the touch target
   */
  fun View.getTouchTargetInfo(): String {
    val widthDp = width / resources.displayMetrics.density
    val heightDp = height / resources.displayMetrics.density
    val effectiveSize = getEffectiveTouchSize()
    val effectiveSizeDp = effectiveSize / resources.displayMetrics.density
    val isValid = isTouchTargetValid()

    return buildString {
      append("${javaClass.simpleName}: ")
      append("${widthDp.toInt()}x${heightDp.toInt()}dp")
      append(" (effective: ${effectiveSizeDp.toInt()}x${effectiveSizeDp.toInt()}dp)")
      append(if (isValid) " ✓" else " ✗ (min $MIN_SIZE_DP dp)")
    }
  }

  /**
   * Validate all interactive children of a ViewGroup.
   *
   * @return List of views that don't meet minimum touch target size
   */
  fun ViewGroup.validateTouchTargets(): List<View> {
    val invalidTargets = mutableListOf<View>()

    fun checkView(view: View) {
      if (view.isClickable || view.isFocusable) {
        if (!view.isTouchTargetValid()) {
          invalidTargets.add(view)
        }
      }
      if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
          checkView(view.getChildAt(i))
        }
      }
    }

    for (i in 0 until childCount) {
      checkView(getChildAt(i))
    }

    return invalidTargets
  }

  /**
   * Print validation report for debugging.
   *
   * @return Validation report as string
   */
  fun ViewGroup.printTouchTargetReport(): String {
    val invalid = validateTouchTargets()
    return buildString {
      appendLine("Touch Target Validation Report")
      appendLine("==============================")
      if (invalid.isEmpty()) {
        appendLine("✓ All touch targets meet $MIN_SIZE_DP dp minimum")
      } else {
        appendLine("✗ ${invalid.size} touch targets are too small:")
        invalid.forEach { view ->
          appendLine("  - ${view.getTouchTargetInfo()}")
        }
      }
    }
  }

  /**
   * Suggest minimum padding to reach touch target size.
   *
   * @param view The view to analyze
   * @return Suggested padding in dp to meet minimum
   */
  fun suggestPadding(view: View): Int {
    val currentSize = min(view.width, view.height)
    val currentSizeDp = currentSize / view.resources.displayMetrics.density
    val deficit = MIN_SIZE_DP - currentSizeDp
    return max(0, (deficit / 2).toInt())
  }

  /**
   * Check if two touch targets are too close (minimum 8dp spacing).
   *
   * @param view1 First view
   * @param view2 Second view
   * @return true if views are properly spaced
   */
  fun areTouchTargetsProperlySpaced(view1: View, view2: View): Boolean {
    val rect1 = Rect().apply { view1.getGlobalVisibleRect(this) }
    val rect2 = Rect().apply { view2.getGlobalVisibleRect(this) }

    val spacing = min(
      min(kotlin.math.abs(rect1.left - rect2.right), kotlin.math.abs(rect2.left - rect1.right)),
      min(kotlin.math.abs(rect1.top - rect2.bottom), kotlin.math.abs(rect2.top - view1.bottom))
    )

    val minSpacingDp = 8 * view1.resources.displayMetrics.density
    return spacing >= minSpacingDp
  }
}

/**
 * Extension property to check if a view has valid touch target size.
 */
val View.hasValidTouchTarget: Boolean
  get() = with(TouchTargetValidator) { this@hasValidTouchTarget.isTouchTargetValid() }

/**
 * Extension property to get suggested padding for touch target compliance.
 */
val View.suggestedTouchTargetPadding: Int
  get() = with(TouchTargetValidator) { suggestPadding(this@suggestedTouchTargetPadding) }
