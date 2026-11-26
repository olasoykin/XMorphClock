package com.drdisagree.xmorphclock.xposed.wallpaper

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.drdisagree.xmorphclock.xposed.hook.HookEnv
import com.drdisagree.xmorphclock.xposed.hook.callMethod
import com.drdisagree.xmorphclock.xposed.hook.fieldValue
import com.drdisagree.xmorphclock.xposed.hook.hookAfter
import com.drdisagree.xmorphclock.xposed.hook.onEachPreDrawGate
import com.drdisagree.xmorphclock.xposed.hook.setFieldValue
import com.drdisagree.xmorphclock.xposed.prefs.ModulePrefs
import com.drdisagree.xmorphclock.xposed.preview.PreviewClockSlot
import java.util.WeakHashMap

object WallpaperPreviewHook {

    private const val PREVIEWER_CLASS = "com.android.wallpaper.widget.LockScreenPreviewer"
    private const val CONSTRAINT_LAYOUT_SUFFIX = ".ConstraintLayout"
    private const val CONSTRAINT_LP_CLASS = "androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams"
    private const val LOCK_TIME_ID = "lock_time"
    private const val LOCK_DATE_ID = "lock_date"
    private const val ID_TYPE = "id"
    private const val TOP_FRACTION = 0.14f

    private data class HiddenState(
        val visibility: Int,
        val alpha: Float
    )

    private val hookedContainers = WeakHashMap<ViewGroup, Boolean>()
    private val hiddenViews = WeakHashMap<View, HiddenState>()
    private var lastErrorLogMs = 0L

    fun install() {
        val previewerClass = HookEnv.findClass(PREVIEWER_CLASS) ?: return

        previewerClass.declaredConstructors.forEach { constructor ->
            constructor.isAccessible = true
            constructor.hookAfter { chain, _ ->
                runCatching {
                    val container = chain.args.filterIsInstance<ViewGroup>().firstOrNull()
                        ?: (chain.thisObject?.fieldValue("mPreviewContainer") as? ViewGroup)
                        ?: (chain.thisObject?.fieldValue("previewContainer") as? ViewGroup)
                        ?: (chain.thisObject?.fieldValue("mLockPreviewContainer") as? ViewGroup)
                        ?: (chain.thisObject?.fieldValue("lockPreviewContainer") as? ViewGroup)
                        ?: return@hookAfter
                    enforceContainer(container)
                }
            }
        }
    }

    private fun enforceContainer(container: ViewGroup) {
        if (hookedContainers.containsKey(container)) return
        hookedContainers[container] = true

        container.onEachPreDrawGate {
            try {
                enforce(container)
            } catch (throwable: Throwable) {
                val now = SystemClock.uptimeMillis()
                if (now - lastErrorLogMs > 5_000L) {
                    lastErrorLogMs = now
                    HookEnv.log("WallpaperPreview enforce failed", throwable)
                }
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
                if (existingSlot.alpha != 1f) existingSlot.alpha = 1f
                val lp = existingSlot.layoutParams as? ViewGroup.MarginLayoutParams
                var lpUpdated = false
                if (lp != null) {
                    if (topOffset > 0 && lp.topMargin != topOffset) {
                        lp.topMargin = topOffset
                        lpUpdated = true
                    }
                    val targetWidth = if (container.width > 0) container.width else lp.width
                    if (targetWidth > 0 && lp.width != targetWidth) {
                        lp.width = targetWidth
                        lpUpdated = true
                    }
                    if (lpUpdated) existingSlot.layoutParams = lp
                }
                if (container.getChildAt(container.childCount - 1) !== existingSlot) {
                    existingSlot.bringToFront()
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

    private fun isConstraintLayout(view: ViewGroup): Boolean {
        var cls: Class<*>? = view.javaClass
        while (cls != null && cls != ViewGroup::class.java) {
            if (cls.name.endsWith(CONSTRAINT_LAYOUT_SUFFIX)) return true
            cls = cls.superclass
        }
        return false
    }

    private fun createOverlayLayoutParams(container: ViewGroup, topMargin: Int): ViewGroup.LayoutParams {
        val targetWidth = if (container.width > 0) container.width else ViewGroup.LayoutParams.MATCH_PARENT
        if (isConstraintLayout(container)) {
            val lp = runCatching {
                val lpClass = container.javaClass.classLoader?.loadClass(CONSTRAINT_LP_CLASS)
                val ctor = lpClass?.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                (ctor?.newInstance(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT) as? ViewGroup.MarginLayoutParams)?.apply {
                    setFieldValue("leftToLeft", 0)
                    setFieldValue("rightToRight", 0)
                    setFieldValue("startToStart", 0)
                    setFieldValue("endToEnd", 0)
                    setFieldValue("topToTop", 0)
                    this.topMargin = topMargin
                    callMethod("validate")
                }
            }.getOrNull()
            if (lp != null) return lp
        }
        return FrameLayout.LayoutParams(
            targetWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            this.topMargin = topMargin
        }
    }

    private fun hideStockView(view: View?) {
        view ?: return
        if (!hiddenViews.containsKey(view)) {
            hiddenViews[view] = HiddenState(view.visibility, view.alpha)
        } else if (view.visibility == View.VISIBLE) {
            hiddenViews[view] = HiddenState(View.VISIBLE, view.alpha.takeIf { it > 0f } ?: 1f)
        }
        if (view.visibility != View.INVISIBLE) {
            view.visibility = View.INVISIBLE
        }
        if (view.alpha != 0f) {
            view.alpha = 0f
        }
    }

    private fun restoreStockView(view: View?) {
        view ?: return
        val previous = hiddenViews.remove(view) ?: return
        if (view.visibility != previous.visibility) {
            view.visibility = previous.visibility
        }
        if (view.alpha != previous.alpha) {
            view.alpha = previous.alpha
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
