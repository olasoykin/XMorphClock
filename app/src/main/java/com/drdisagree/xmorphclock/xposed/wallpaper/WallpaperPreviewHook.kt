package com.drdisagree.xmorphclock.xposed.wallpaper

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Rect
import android.os.SystemClock
import android.view.Gravity
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.drdisagree.xmorphclock.xposed.hook.HookEnv
import com.drdisagree.xmorphclock.xposed.hook.callMethod
import com.drdisagree.xmorphclock.xposed.hook.fieldValue
import com.drdisagree.xmorphclock.xposed.hook.findMethod
import com.drdisagree.xmorphclock.xposed.hook.hookAfter
import com.drdisagree.xmorphclock.xposed.hook.onEachPreDrawGate
import com.drdisagree.xmorphclock.xposed.hook.setFieldValue
import com.drdisagree.xmorphclock.xposed.prefs.ModulePrefs
import com.drdisagree.xmorphclock.xposed.preview.PreviewClockSlot
import java.util.WeakHashMap

object WallpaperPreviewHook {

    private const val PREVIEWER_CLASS = "com.android.wallpaper.widget.LockScreenPreviewer"
    private const val CLOCK_CAROUSEL_CLASS = "com.android.customization.picker.clock.ui.view.ClockCarouselView"
    private const val CLOCK_HOST_CLASS = "com.android.customization.picker.clock.ui.view.ClockHostView"
    private const val THEME_PICKER_CLOCK_HOST_CLASS = "com.android.customization.picker.clock.ui.view.ThemePickerClockHostView"
    private const val CONSTRAINT_LAYOUT_SUFFIX = ".ConstraintLayout"
    private const val CONSTRAINT_LP_CLASS = "androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams"
    private const val LOCK_TIME_ID = "lock_time"
    private const val LOCK_DATE_ID = "lock_date"
    private const val ID_TYPE = "id"
    private const val TOP_FRACTION = 0.14f

    private val CLOCK_CLASS_PREFIXES = listOf(
        "com.android.keyguard.AnimatableClockView",
        "com.android.keyguard.KeyguardClockSwitch",
        "com.android.keyguard.KeyguardSliceView",
        "com.android.systemui.clocks.",
        "com.google.android.systemui.clocks.",
        "com.android.systemui.shared.clocks.",
        "com.android.systemui.customization.clocks.",
        "com.android.systemui.plugins.clocks.",
        "com.android.customization.picker.clock.ui.view.ClockHostView",
        "com.android.customization.picker.clock.ui.view.ThemePickerClockHostView"
    )

    private val CLOCK_CLASS_KEYWORDS = listOf(
        "ClockHostView",
        "AnimatableClockView",
        "FlexClockView",
        "SimpleDigitalClockTextView",
        "DigitalClockFaceView",
        "ComposedDigitalClockView",
        "ClockView",
        "ClockPreview",
        "ClockFace",
        "DigitalClock",
        "AnalogClock",
        "ClockContainer"
    )

    private val CLOCK_ENTRY_NAMES = setOf(
        LOCK_TIME_ID,
        LOCK_DATE_ID,
        "lockscreen_clock_view",
        "lockscreen_clock_view_large",
        "clock_host_view",
        "clock_view",
        "clock_view_large",
        "large_clock_container",
        "small_clock_container",
        "date_smartspace_view",
        "date_smartspace_view_large",
        "bc_smartspace_view"
    )

    private data class HiddenState(
        val visibility: Int,
        val alpha: Float
    )

    private val hookedContainers = WeakHashMap<ViewGroup, Boolean>()
    private val hookedDecorViews = WeakHashMap<ViewGroup, LongArray>()
    private val hiddenViews = WeakHashMap<View, HiddenState>()
    private val entryNameCache = HashMap<Int, String>()
    private var lastErrorLogMs = 0L

    private const val CANCEL_GAP_MS = 500L

    fun install() {
        installLegacyPreviewerHook()
        installClockOverlayHooks()
        installActivityDecorHook()
    }

    private fun installLegacyPreviewerHook() {
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

    private fun installClockOverlayHooks() {
        listOf(CLOCK_CAROUSEL_CLASS, CLOCK_HOST_CLASS, THEME_PICKER_CLOCK_HOST_CLASS).forEach { className ->
            val cls = HookEnv.findClass(className) ?: return@forEach
            cls.declaredConstructors.forEach { constructor ->
                constructor.isAccessible = true
                constructor.hookAfter { chain, _ ->
                    val view = chain.thisObject as? View ?: return@hookAfter
                    view.post {
                        val decor = view.rootView as? ViewGroup ?: return@post
                        watchDecorView(decor)
                    }
                }
            }
        }
    }

    private fun installActivityDecorHook() {
        listOf("onStart", "onResume").forEach { methodName ->
            Activity::class.java.findMethod(methodName) { it.parameterCount == 0 }?.hookAfter { chain, _ ->
                val activity = chain.thisObject as? Activity ?: return@hookAfter
                val decor = activity.window?.decorView as? ViewGroup ?: return@hookAfter
                watchDecorView(decor)
            }
        }
    }

    private fun watchDecorView(decor: ViewGroup) {
        if (hookedDecorViews.containsKey(decor)) return
        val cancelStamp = LongArray(1)
        hookedDecorViews[decor] = cancelStamp

        decor.onEachPreDrawGate {
            val changed = try {
                enforceClockOverlaysInDecor(decor)
            } catch (throwable: Throwable) {
                logThrottled("WallpaperPreview decor enforce failed", throwable)
                false
            }
            val now = SystemClock.uptimeMillis()
            if (changed && now - cancelStamp[0] > CANCEL_GAP_MS) {
                cancelStamp[0] = now
                false
            } else {
                true
            }
        }
    }

    private fun enforceClockOverlaysInDecor(decor: ViewGroup): Boolean {
        val enabled = ModulePrefs.isClockEnabled && ModulePrefs.isWallpaperPreviewEnabled
        val previewSurfaces = ArrayList<Rect>()
        collectPreviewSurfaceRects(decor, decor, previewSurfaces)

        val overlayClocks = LinkedHashSet<View>()
        if (previewSurfaces.isNotEmpty()) {
            collectOverlayClocks(decor, previewSurfaces, overlayClocks)
        }

        var changed = false
        if (enabled) {
            overlayClocks.forEach {
                if (hideStockView(it)) changed = true
            }
        } else {
            overlayClocks.forEach {
                if (restoreStockView(it)) changed = true
            }
            hiddenViews.keys.toList().forEach { view ->
                if (view.rootView === decor && restoreStockView(view)) changed = true
            }
        }
        return changed
    }

    private fun collectPreviewSurfaceRects(group: ViewGroup, decor: ViewGroup, out: MutableList<Rect>) {
        val minW = (decor.width * 0.25f).toInt().coerceAtLeast(100)
        val minH = (decor.height * 0.25f).toInt().coerceAtLeast(150)
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            val isPreviewSurface = (child is SurfaceView || child is TextureView)
            if (isPreviewSurface && child.isVisible && child.width >= minW && child.height >= minH) {
                val rect = globalVisibleBounds(child)
                if (!rect.isEmpty) out += rect
            } else if (child is ViewGroup) {
                collectPreviewSurfaceRects(child, decor, out)
            }
        }
    }

    private fun collectOverlayClocks(
        group: ViewGroup,
        previewSurfaces: List<Rect>,
        out: MutableSet<View>
    ) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is PreviewClockSlot || child is SurfaceView) continue

            if (isOverlayClockCandidate(child)) {
                val bounds = globalVisibleBounds(child)
                val overlapsPreview = previewSurfaces.any { surfaceRect ->
                    val minOverlayHeight = (surfaceRect.height() * 0.12f).toInt().coerceAtLeast(32)
                    bounds.height() >= minOverlayHeight && Rect.intersects(bounds, surfaceRect)
                }
                if (overlapsPreview) {
                    out += child
                    continue
                }
            }

            if (child is ViewGroup) {
                collectOverlayClocks(child, previewSurfaces, out)
            }
        }
    }

    private fun isOverlayClockCandidate(view: View): Boolean {
        val entry = resolveEntryName(view)
        if (entry != null && entry in CLOCK_ENTRY_NAMES) return true

        var cls: Class<*>? = view.javaClass
        while (cls != null && cls != ViewGroup::class.java && cls != View::class.java) {
            val name = cls.name
            if (name.contains("PreviewClockSlot") || name.contains("ClockFaceView")) return false
            if (CLOCK_CLASS_PREFIXES.any { name.startsWith(it) }) return true
            if (CLOCK_CLASS_KEYWORDS.any { name.contains(it) }) return true
            cls = cls.superclass
        }
        return false
    }

    private fun resolveEntryName(view: View): String? {
        val id = view.id
        if (id == View.NO_ID || (id ushr 24) < 0x02) return null
        entryNameCache[id]?.let { return it.ifEmpty { null } }
        val name = runCatching { view.resources.getResourceEntryName(id) }.getOrNull().orEmpty()
        if (entryNameCache.size < 256) entryNameCache[id] = name
        return name.ifEmpty { null }
    }

    private fun globalVisibleBounds(view: View): Rect {
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return Rect()
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        return Rect(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height)
    }

    private fun enforceContainer(container: ViewGroup) {
        if (hookedContainers.containsKey(container)) return
        hookedContainers[container] = true

        container.onEachPreDrawGate {
            try {
                enforce(container)
            } catch (throwable: Throwable) {
                logThrottled("WallpaperPreview enforce failed", throwable)
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

            val topOffset = resolveTopOffset(container, existingSlot?.height ?: 0, dateView, timeView)
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

    private fun resolveTopOffset(
        container: ViewGroup,
        slotHeight: Int,
        dateView: View?,
        timeView: View?
    ): Int {
        val containerHeight = container.height
        val containerWidth = container.width
        if (containerHeight > 0 && slotHeight > 0) {
            val minTop = (containerHeight * 0.08f).toInt().coerceAtLeast((containerWidth * 0.08f).toInt())
            val maxTop = ((containerHeight * 0.80f) - slotHeight).toInt().coerceAtLeast(minTop)
            val centeredTop = ((containerHeight * 0.45f) - (slotHeight * 0.5f)).toInt()
            return centeredTop.coerceIn(minTop, maxTop)
        }
        val anchor = dateView ?: timeView
        val anchorTop = anchor?.let { viewTopInContainer(it, container) } ?: 0
        val minOffset = if (containerHeight > 0) {
            (containerHeight * 0.14f).toInt().coerceAtLeast((containerWidth * TOP_FRACTION).toInt())
        } else {
            (containerWidth * TOP_FRACTION).toInt()
        }
        return if (anchorTop > 0) anchorTop.coerceAtLeast(minOffset) else minOffset
    }

    private fun viewTopInContainer(view: View, container: ViewGroup): Int {
        var top = view.top
        var parent = view.parent
        while (parent is ViewGroup && parent !== container) {
            top += parent.top
            parent = parent.parent
        }
        return if (parent === container) top else view.top
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
                    setFieldValue("bottomToTop", -1)
                    setFieldValue("bottomToBottom", -1)
                    this.topMargin = topMargin
                    callMethod("validate")
                }
            }.getOrNull()
            if (lp != null) return lp
        }
        return FrameLayout.LayoutParams(
            targetWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL
        ).apply {
            this.topMargin = topMargin
        }
    }

    private fun hideStockView(view: View?): Boolean {
        view ?: return false
        var changed = false
        when (view.visibility) {
            View.VISIBLE -> {
                hiddenViews[view] = HiddenState(View.VISIBLE, view.alpha.takeIf { it > 0f } ?: 1f)
            }
            View.GONE -> {
                val prevAlpha = hiddenViews[view]?.alpha ?: view.alpha.takeIf { it > 0f } ?: 1f
                hiddenViews[view] = HiddenState(View.GONE, prevAlpha)
            }
            else -> {
                if (!hiddenViews.containsKey(view)) {
                    hiddenViews[view] = HiddenState(view.visibility, view.alpha)
                }
            }
        }
        val desiredVisibility = if (view.visibility == View.GONE) View.GONE else View.INVISIBLE
        if (view.visibility != desiredVisibility) {
            view.visibility = desiredVisibility
            changed = true
        }
        if (view.alpha != 0f) {
            view.alpha = 0f
            changed = true
        }
        return changed
    }

    private fun restoreStockView(view: View?): Boolean {
        view ?: return false
        val previous = hiddenViews.remove(view) ?: return false
        var changed = false
        if (view.visibility != previous.visibility) {
            view.visibility = previous.visibility
            changed = true
        }
        if (view.alpha != previous.alpha) {
            view.alpha = previous.alpha
            changed = true
        }
        return changed
    }

    private fun findSlot(group: ViewGroup): PreviewClockSlot? {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is PreviewClockSlot) return child
        }
        return null
    }

    private fun logThrottled(message: String, throwable: Throwable) {
        val now = SystemClock.uptimeMillis()
        if (now - lastErrorLogMs > 5_000L) {
            lastErrorLogMs = now
            HookEnv.log(message, throwable)
        }
    }
}
