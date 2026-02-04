package com.phenix.wirelessadb.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewAnimationUtils
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.annotation.RequiresApi
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import kotlin.math.hypot

/**
 * Material motion animation utilities (v1.2.0 Phase 5).
 *
 * Provides extension functions for:
 * - Fade in/out animations
 * - Slide animations
 * - Circular reveal
 * - Scale bounce effects
 * - Shimmer loading states
 * - Reduced motion detection
 */

// Standard Material durations (in ms)
const val DURATION_INSTANT = 50
const val DURATION_SHORT = 150
const val DURATION_MEDIUM = 250
const val DURATION_LONG = 350
const val DURATION_EXTRA_LONG = 450

// Standard Material easing
val EASING_STANDARD = AccelerateDecelerateInterpolator()
const val EASING_EMPHASIZED = 0.2f // Decelerate factor
const val EASING_LEGACY = 0.5f // Standard decelerate

/**
 * Fade in animation.
 *
 * @param duration Animation duration in ms
 * @param onStart Callback when animation starts
 * @param onEnd Callback when animation ends
 */
fun View.fadeIn(
    duration: Long = DURATION_MEDIUM.toLong(),
    onStart: (() -> Unit)? = null,
    onEnd: (() -> Unit)? = null
) {
    alpha = 0f
    isVisible = true

    animate()
        .alpha(1f)
        .setDuration(duration)
        .setInterpolator(EASING_STANDARD)
        .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                onStart?.invoke()
            }

            override fun onAnimationEnd(animation: Animator) {
                onEnd?.invoke()
            }
        })
        .start()
}

/**
 * Fade out animation.
 *
 * @param duration Animation duration in ms
 * @param onHide Callback when view is hidden
 */
fun View.fadeOut(
    duration: Long = DURATION_SHORT.toLong(),
    onHide: (() -> Unit)? = null
) {
    animate()
        .alpha(0f)
        .setDuration(duration)
        .setInterpolator(EASING_STANDARD)
        .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isInvisible = true
                onHide?.invoke()
            }
        })
        .start()
}

/**
 * Cross-fade (replace content with fade).
 *
 * @param newContent The new view to show
 * @param duration Animation duration in ms
 */
fun View.crossFade(
    newContent: View,
    duration: Long = DURATION_MEDIUM.toLong()
) {
    fadeOut(duration / 2) {
        isInvisible = true
    }
    newContent.alpha = 0f
    newContent.isVisible = true
    newContent.fadeIn(duration / 2)
}

/**
 * Slide in from bottom.
 *
 * @param duration Animation duration in ms
 */
fun View.slideInFromBottom(
    duration: Long = DURATION_MEDIUM.toLong()
) {
    translationY = height.toFloat()
    isVisible = true

    animate()
        .translationY(0f)
        .setDuration(duration)
        .setInterpolator(EASING_STANDARD)
        .start()
}

/**
 * Slide out to bottom.
 *
 * @param duration Animation duration in ms
 * @param onHide Callback when view is hidden
 */
fun View.slideOutToBottom(
    duration: Long = DURATION_SHORT.toLong(),
    onHide: (() -> Unit)? = null
) {
    animate()
        .translationY(height.toFloat())
        .setDuration(duration)
        .setInterpolator(EASING_STANDARD)
        .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isInvisible = true
                translationY = 0f
                onHide?.invoke()
            }
        })
        .start()
}

/**
 * Circular reveal animation (API 21+).
 *
 * @param centerX X coordinate for reveal center
 * @param centerY Y coordinate for reveal center
 * @param duration Animation duration in ms
 * @param onStart Callback when animation starts
 * @param onEnd Callback when animation ends
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun View.circularReveal(
    centerX: Int = width / 2,
    centerY: Int = height / 2,
    duration: Long = DURATION_MEDIUM.toLong(),
    onStart: (() -> Unit)? = null,
    onEnd: (() -> Unit)? = null
) {
    val finalRadius = hypot(width.toFloat(), height.toFloat())

    ViewAnimationUtils.createCircularReveal(this, centerX, centerY, 0f, finalRadius).apply {
        setDuration(duration)
        setInterpolator(EASING_STANDARD)
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                isVisible = true
                onStart?.invoke()
            }

            override fun onAnimationEnd(animation: Animator) {
                onEnd?.invoke()
            }
        })
        start()
    }
}

/**
 * Scale bounce animation.
 *
 * @param scaleFrom Starting scale
 * @param scaleTo Ending scale
 * @param duration Animation duration in ms
 */
fun View.scaleBounce(
    scaleFrom: Float = 0.8f,
    scaleTo: Float = 1f,
    duration: Long = DURATION_MEDIUM.toLong()
) {
    scaleX = scaleFrom
    scaleY = scaleFrom
    isVisible = true

    animate()
        .scaleX(scaleTo)
        .scaleY(scaleTo)
        .setDuration(duration)
        .setInterpolator(EASING_STANDARD)
        .start()
}

/**
 * Shimmer loading effect.
 * Note: Simple alpha-based shimmer. For production, consider using shimmer library.
 */
fun View.startShimmer() {
    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1500
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = EASING_STANDARD

        addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            alpha = 0.3f + (0.7f * value)
        }
    }
    animator.start()

    // Store animator reference for cleanup using tag
    setTag(com.phenix.wirelessadb.R.id.copyButton, animator)
}

/**
 * Stop shimmer effect.
 */
fun View.stopShimmer() {
    val animator = getTag(com.phenix.wirelessadb.R.id.copyButton) as? ValueAnimator
    animator?.cancel()
    alpha = 1f
}

/**
 * Check if reduced motion is enabled in system settings.
 */
fun Context.isReducedMotionEnabled(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        android.provider.Settings.System.getFloat(
            contentResolver,
            android.provider.Settings.System.TRANSITION_ANIMATION_SCALE,
            1.0f
        ) < 0.5f
    } else {
        false
    }
}

/**
 * Conditionally animate based on reduced motion setting.
 * If reduced motion is enabled, sets final state immediately.
 *
 * @param animation The animation block to run
 */
fun View.animateIfNotReduced(
    animation: View.() -> Unit
) {
    if (!context.isReducedMotionEnabled()) {
        animation()
    } else {
        // Skip animation, ensure final state
        alpha = 1f
        translationY = 0f
        scaleX = 1f
        scaleY = 1f
    }
}

/**
 * Add scale press animation to a view.
 * Scales down slightly on press, returns to normal on release/cancel.
 *
 * Usage: myButton.setupPressAnimation()
 */
fun View.setupPressAnimation(
    pressScale: Float = 0.95f,
    duration: Long = DURATION_INSTANT.toLong()
) {
    setOnTouchListener { view, event ->
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                if (!view.context.isReducedMotionEnabled()) {
                    view.animate()
                        .scaleX(pressScale)
                        .scaleY(pressScale)
                        .setDuration(duration)
                        .start()
                }
                true
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                if (!view.context.isReducedMotionEnabled()) {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(DURATION_SHORT.toLong())
                        .start()
                }
                view.performClick()
                true
            }
            else -> false
        }
    }
}
