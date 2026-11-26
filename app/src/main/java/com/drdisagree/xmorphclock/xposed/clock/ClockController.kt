package com.drdisagree.xmorphclock.xposed.clock

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.RectF
import android.view.animation.PathInterpolator
import com.drdisagree.xmorphclock.engine.ClockFaceView
import com.drdisagree.xmorphclock.xposed.hook.onEachPreDraw
import com.drdisagree.xmorphclock.xposed.prefs.ModulePrefs
import com.drdisagree.xmorphclock.xposed.signal.ClockSignals
import java.lang.ref.WeakReference

object ClockController {

    private const val SIZE_DURATION = 500L
    private const val BURN_IN_DP = 6f
    private const val BURN_IN_MAX_DP = 24f
    private const val EDGE_MARGIN_DP = 12f
    private const val BURN_IN_STEP_DP = 2f
    private const val BURN_IN_STROKE_FACTOR = 0.8f
    private val EMPHASIZED = PathInterpolator(0.2f, 0f, 0f, 1f)

    private var viewRef: WeakReference<ClockFaceView>? = null
    private val content = RectF()
    private val location = IntArray(2)
    private var largeProgress = 1f
    private var largeTarget = 1f
    private var largeAnimator: ValueAnimator? = null
    private var initialized = false
    private var cachedRevision = Int.MIN_VALUE
    private var cachedWidth = -1
    private var cachedStrokePx = 0f
    private var cachedHasBounds = false

    fun init() {
        if (initialized) return
        initialized = true
        ClockSignals.addListener { view()?.let { applyProgress(it) } }
    }

    fun attach(view: ClockFaceView) {
        viewRef = WeakReference(view)
        cachedRevision = Int.MIN_VALUE
        cachedWidth = -1
        ClockSignals.isLargeClock()?.let { snapLarge(if (it) 1f else 0f) }
        view.setLargeProgress(largeProgress)
        applyProgress(view)
        view.onEachPreDraw { onPreDraw(view) }
    }

    private fun view(): ClockFaceView? = viewRef?.get()

    private fun onPreDraw(view: ClockFaceView) {
        ClockSignals.isLargeClock()?.let { large ->
            val target = if (large) 1f else 0f
            if (ClockSignals.isAsleep) snapLarge(target) else animateLarge(target)
        }
        val stockBurnIn = (view.parent as? ClockSlot)?.stockBurnIn == true
        val aod = if (ModulePrefs.isBurnInProtectionEnabled && !stockBurnIn) ClockSignals.aodProgress else 0f
        if (aod <= 0f) {
            if (view.translationX != 0f) view.translationX = 0f
            if (view.translationY != 0f) view.translationY = 0f
            return
        }
        val density = view.resources.displayMetrics.density
        val rev = view.layoutRevision
        val viewWidth = view.width
        if (rev != cachedRevision || viewWidth != cachedWidth || view.isLayoutAnimating) {
            cachedRevision = rev
            cachedWidth = viewWidth
            cachedStrokePx = view.aodStrokePx()
            cachedHasBounds = view.contentBounds(content)
        }
        val range = (cachedStrokePx * BURN_IN_STROKE_FACTOR).coerceIn(BURN_IN_DP * density, BURN_IN_MAX_DP * density)
        val stepPx = BURN_IN_STEP_DP * density
        var x = BurnInOffset.x(range, stepPx) * aod
        var y = BurnInOffset.y(range, stepPx) * aod
        if (cachedHasBounds) {
            val root = view.rootView
            view.getLocationOnScreen(location)
            val margin = EDGE_MARGIN_DP * density
            val left = location[0] - view.translationX + content.left
            val top = location[1] - view.translationY + content.top
            val right = location[0] - view.translationX + content.right
            val bottom = location[1] - view.translationY + content.bottom
            x = x.coerceIn(-(left - margin).coerceAtLeast(0f), (root.width - margin - right).coerceAtLeast(0f))
            y = y.coerceIn(-(top - margin).coerceAtLeast(0f), (root.height - margin - bottom).coerceAtLeast(0f))
        }
        if (view.translationX != x) view.translationX = x
        if (view.translationY != y) view.translationY = y
    }

    private fun applyProgress(view: ClockFaceView) {
        view.setAmbient(ClockSignals.isAmbient)
        view.setAodProgress(ClockSignals.aodProgress)
        view.setSleepProgress(ClockSignals.sleepProgress)
    }

    private fun snapLarge(target: Float) {
        if (largeTarget == target && largeAnimator == null && largeProgress == target) return
        largeTarget = target
        largeAnimator?.cancel()
        largeAnimator = null
        largeProgress = target
        view()?.setLargeProgress(target)
    }

    private fun animateLarge(target: Float) {
        if (largeTarget == target && (largeAnimator != null || largeProgress == target)) return
        largeTarget = target
        largeAnimator?.cancel()
        largeAnimator = ValueAnimator.ofFloat(largeProgress, target).apply {
            duration = SIZE_DURATION
            interpolator = EMPHASIZED
            addUpdateListener { animator ->
                largeProgress = animator.animatedValue as Float
                view()?.setLargeProgress(largeProgress)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (largeAnimator === animation) largeAnimator = null
                }
            })
            start()
        }
    }
}
