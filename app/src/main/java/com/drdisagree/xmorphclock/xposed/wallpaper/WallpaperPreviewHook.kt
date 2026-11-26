package com.drdisagree.xmorphclock.xposed.wallpaper

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.drdisagree.xmorphclock.xposed.hook.HookEnv
import com.drdisagree.xmorphclock.xposed.hook.HookSafety
import com.drdisagree.xmorphclock.xposed.hook.fieldValue
import com.drdisagree.xmorphclock.xposed.hook.hookAfter
import com.drdisagree.xmorphclock.xposed.hook.onEachPreDrawGate
import com.drdisagree.xmorphclock.xposed.hook.setFieldValue
import com.drdisagree.xmorphclock.xposed.prefs.ModulePrefs
import com.drdisagree.xmorphclock.xposed.preview.PreviewClockSlot
import java.util.WeakHashMap

object WallpaperPreviewHook {

    private const val PREVIEWER_CLASS = "com.android.wallpaper.widget.LockScreenPreviewer"
    private const val CONSTRAINT_LP_CLASS = "androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams"
    private const val LOCK_TIME_ID = "lock_time"
    private const val LOCK_DATE_ID = "lock_date"
    private const val ID_TYPE = "id"
    private const val TOP_FRACTION = 0.14f
    private const val HOOK_ID = "wallpaper_preview"

    private val hookedContainers = WeakHashMap<ViewGroup, Boolean>()
    private val hiddenViews = WeakHashMap<View, Int>()

    fun install() {
        val previewerClass = HookEnv.findClass(PREVIEWER_CLASS) ?: return

        previewerClass.declaredConstructors.forEach { constructor ->
            constructor.isAccessible = true
            constructor.hookAfter { chain, _ ->
                val container = chain.args.filterIsInstance<ViewGroup>().firstOrNull()
                    ?: (chain.thisObject?.fieldValue("mPreviewContainer") as? ViewGroup)
                    ?: (chain.thisObject?.fieldValue("previewContainer") as? ViewGroup)
                    ?: return@hookAfter
                enforceContainer(container)
            }
        }
    }

    private fun enforceContainer(container: ViewGroup) {
        if (hookedContainers.containsKey(container)) return
        hookedContainers[container] = true

        container.onEachPreDrawGate {
            HookSafety.runGuarded(HOOK_ID, maxCallsPerSecond = 120) {
                enforce(container)
            }
            true
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun enforce(container: ViewGroup) {
        val pkg = container.context.packageName
        val timeId = container.resources.getIdentifier(LOCK_TIME_ID, ID_TYPE, pkg)
        val dateId = container.resources.getIdentifier(LOCK_DATE_ID, ID_TYPE, pkg)

        val timeView = if (timeId != 0) container.findViewById<View>(timeId) else null
        val dateView = if (dateId != 0) container.findViewById<View>(dateId) else null

        val enabled = ModulePrefs.isClockEnabled && ModulePrefs.isWallpaperPreviewEnabled
        val existingSlot = findSlot(container)

        if (enabled) {
            hideStockView(timeView)
            hideStockView(dateView)

            val topOffset = resolveTopOffset(container, dateView, timeView)
            if (existingSlot == null) {
                val created = PreviewClockSlot(container.context, initialLarge = true).apply {
                    id = View.generateViewId()
                }
                val params = createOverlayLayoutParams(container, topOffset)
                container.addView(created, params)
            } else {
                if (!existingSlot.isVisible) existingSlot.isVisible = true
                val lp = existingSlot.layoutParams as? ViewGroup.MarginLayoutParams
                if (lp != null && topOffset > 0 && lp.topMargin != topOffset) {
                    lp.topMargin = topOffset
                    existingSlot.layoutParams = lp
                }
            }
        } else {
            restoreStockView(timeView)
            restoreStockView(dateView)
            if (existingSlot != null && existingSlot.isVisible) {
                existingSlot.isVisible = false
            }
        }
    }

    private fun resolveTopOffset(container: ViewGroup, dateView: View?, timeView: View?): Int {
        val anchor = dateView ?: timeView
        val anchorTop = anchor?.top ?: 0
        if (anchorTop > 0) return anchorTop
        val containerWidth = container.width
        return if (containerWidth > 0) (containerWidth * TOP_FRACTION).toInt() else 0
    }

    private fun createOverlayLayoutParams(container: ViewGroup, topMargin: Int): ViewGroup.LayoutParams {
        if (container.javaClass.name.contains("ConstraintLayout")) {
            val lp = runCatching {
                val lpClass = container.javaClass.classLoader?.loadClass(CONSTRAINT_LP_CLASS)
                val ctor = lpClass?.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                (ctor?.newInstance(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) as? ViewGroup.MarginLayoutParams)?.apply {
                    setFieldValue("startToStart", 0)
                    setFieldValue("endToEnd", 0)
                    setFieldValue("topToTop", 0)
                    this.topMargin = topMargin
                }
            }.getOrNull()
            if (lp != null) return lp
        }
        return FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            this.topMargin = topMargin
        }
    }

    private fun hideStockView(view: View?) {
        view ?: return
        if (!hiddenViews.containsKey(view)) {
            hiddenViews[view] = view.visibility
        }
        if (view.visibility != View.INVISIBLE) {
            view.visibility = View.INVISIBLE
        }
    }

    private fun restoreStockView(view: View?) {
        view ?: return
        val previous = hiddenViews.remove(view) ?: return
        if (view.visibility != previous) {
            view.visibility = previous
        }
    }

    private fun findSlot(group: ViewGroup): PreviewClockSlot? {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is PreviewClockSlot) return child
        }
        return null
    }
}
