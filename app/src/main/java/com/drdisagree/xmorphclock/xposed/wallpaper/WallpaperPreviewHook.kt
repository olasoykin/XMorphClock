package com.drdisagree.xmorphclock.xposed.wallpaper

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.Rect
import android.os.SystemClock
import android.view.Gravity
import android.view.SurfaceControlViewHost
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextClock
import android.widget.TextView
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
import java.util.concurrent.ConcurrentHashMap

object WallpaperPreviewHook {

    private const val PREVIEWER_CLASS = "com.android.wallpaper.widget.LockScreenPreviewer"
    private const val CLOCK_CAROUSEL_CLASS = "com.android.customization.picker.clock.ui.view.ClockCarouselView"
    private const val CLOCK_HOST_CLASS = "com.android.customization.picker.clock.ui.view.ClockHostView"
    private const val THEME_PICKER_CLOCK_HOST_CLASS = "com.android.customization.picker.clock.ui.view.ThemePickerClockHostView"
    private const val CLOCK_PREVIEW_VIEW_CLASS = "com.android.customization.picker.clock.ui.view.ClockPreviewView"
    private const val CONSTRAINT_LAYOUT_SUFFIX = ".ConstraintLayout"
    private const val CONSTRAINT_LP_CLASS = "androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams"
    private const val LOCK_TIME_ID = "lock_time"
    private const val LOCK_DATE_ID = "lock_date"
    private const val ID_TYPE = "id"
    private const val TOP_FRACTION = 0.14f

    private val WHITESPACE_REGEX = Regex("\\s+")
    private val CLOCK_DIGITS_REGEX = Regex(
        "^\\d{1,2}(?:[:\\s]\\d{2})?(?:\\s?[AP]M)?$",
        RegexOption.IGNORE_CASE
    )
    private val DATE_TEXT_REGEX = Regex(
        "^(?:[a-zA-ZÀ-ÿ]{2,12}[.,]?\\s+(?:\\d{1,2}\\s+(?:de\\s+)?[a-zA-ZÀ-ÿ]{2,12}[.,]?|[a-zA-ZÀ-ÿ]{2,12}[.,]?\\s+\\d{1,2})|\\d{1,2}\\s+(?:de\\s+)?[a-zA-ZÀ-ÿ]{3,12}[.,]?)(?:[.,]?\\s+\\d{2,4})?$"
    )

    private val EXCLUDED_ENTRY_KEYWORDS = listOf(
        "shortcut",
        "affordance",
        "flashlight",
        "camera",
        "button",
        "chip",
        "wallpaper",
        "scrim",
        "toolbar",
        "action_bar",
        "swatch",
        "picker",
        "bottom_sheet"
    )

    private val MAIN_PREVIEW_SURFACE_HINTS = listOf(
        "workspace",
        "wallpaper",
        "lockscreen_preview",
        "lock_screen_preview",
        "preview_surface"
    )

    private val CLOCK_CLASS_PREFIXES = listOf(
        "com.android.keyguard.AnimatableClockView",
        "com.android.keyguard.KeyguardClockSwitch",
        "com.android.keyguard.KeyguardSliceView",
        "com.android.systemui.clocks.",
        "com.google.android.systemui.clocks.",
        "com.google.android.systemui.keyguard.clocks.",
        "com.android.systemui.shared.clocks.",
        "com.android.systemui.customization.clocks.",
        "com.android.systemui.plugins.clocks.",
        "com.android.systemui.plugins.keyguard.ui.clocks.",
        "com.google.android.systemui.smartspace.",
        "com.android.systemui.smartspace.",
        "com.android.systemui.statusbar.policy.DateView",
        CLOCK_HOST_CLASS,
        THEME_PICKER_CLOCK_HOST_CLASS,
        CLOCK_PREVIEW_VIEW_CLASS,
        CLOCK_CAROUSEL_CLASS
    )

    private val CLOCK_CLASS_KEYWORDS = listOf(
        "ClockHostView",
        "ClockPreviewView",
        "ClockCarousel",
        "ClockSection",
        "ClockSwitch",
        "AnimatableClock",
        "FlexClock",
        "SimpleDigitalClockTextView",
        "DigitalClockFaceView",
        "ComposedDigitalClockView",
        "ClockView",
        "ClockPreview",
        "ClockFace",
        "DigitalClock",
        "AnalogClock",
        "ClockContainer",
        "Smartspace",
        "KeyguardSlice",
        "DateView",
        "TextClock"
    )

    private val CLOCK_ENTRY_NAMES = setOf(
        LOCK_TIME_ID,
        LOCK_DATE_ID,
        "lockscreen_clock_view",
        "lockscreen_clock_view_large",
        "lockscreen_clock_host_view",
        "lockscreen_clock",
        "clock_host_view",
        "clock_view",
        "clock_view_large",
        "clock_container",
        "clock_preview",
        "clock_carousel",
        "preview_clock",
        "large_clock_container",
        "small_clock_container",
        "large_clock_host_view",
        "small_clock_host_view",
        "keyguard_clock_container",
        "keyguard_status_view",
        "keyguard_status_area",
        "keyguard_slice_view",
        "date_smartspace_view",
        "date_smartspace_view_large",
        "weather_smartspace_view",
        "weather_smartspace_view_large",
        "smartspace_view",
        "smartspace_container",
        "date_view",
        "keyguard_date_view",
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
    private val legacyIdsCache = ConcurrentHashMap<String, IntArray>()
    private val dedicatedClockSurfaceCache = ConcurrentHashMap<Class<*>, Boolean>()
    private val explicitClockClassCache = ConcurrentHashMap<Class<*>, Boolean>()
    private val scratchSurfaces = ArrayList<Rect>()
    private val scratchProtected = HashSet<View>()
    private val scratchOverlayClocks = LinkedHashSet<View>()
    private val scratchBounds = Rect()
    private val scratchLoc = IntArray(2)
    private var lastErrorLogMs = 0L

    private const val CANCEL_GAP_MS = 500L

    fun install() {
        installLegacyPreviewerHook()
        installClockOverlayHooks()
        installActivityDecorHook()
        installDialogDecorHook()
        installSurfaceControlHostHook()
    }

    internal fun isPreviewClockOrDateText(rawText: String?): Boolean {
        val text = rawText?.trim()?.replace(WHITESPACE_REGEX, " ") ?: return false
        if (text.isEmpty() || text.length > 32) return false
        if (CLOCK_DIGITS_REGEX.matches(text)) return true
        if (text.contains('/') || text.contains(':')) return false
        return DATE_TEXT_REGEX.matches(text)
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
        listOf(
            CLOCK_CAROUSEL_CLASS,
            CLOCK_HOST_CLASS,
            THEME_PICKER_CLOCK_HOST_CLASS,
            CLOCK_PREVIEW_VIEW_CLASS
        ).forEach { className ->
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
        Activity::class.java.findMethod("onWindowFocusChanged") {
            it.parameterCount == 1 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType
        }?.hookAfter { chain, _ ->
            val hasFocus = chain.args.getOrNull(0) as? Boolean ?: false
            if (!hasFocus) return@hookAfter
            val activity = chain.thisObject as? Activity ?: return@hookAfter
            val decor = activity.window?.decorView as? ViewGroup ?: return@hookAfter
            watchDecorView(decor)
        }
    }

    private fun installDialogDecorHook() {
        Dialog::class.java.findMethod("onStart") { it.parameterCount == 0 }?.hookAfter { chain, _ ->
            val dialog = chain.thisObject as? Dialog ?: return@hookAfter
            val decor = dialog.window?.decorView as? ViewGroup ?: return@hookAfter
            watchDecorView(decor)
        }
    }

    private fun installSurfaceControlHostHook() {
        SurfaceControlViewHost::class.java.declaredMethods
            .filter { it.name == "setView" && it.parameterCount >= 1 && View::class.java.isAssignableFrom(it.parameterTypes[0]) }
            .forEach { method ->
                method.isAccessible = true
                method.hookAfter { chain, _ ->
                    val root = chain.args.getOrNull(0) as? ViewGroup ?: return@hookAfter
                    watchDecorView(root)
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
        val previewSurfaces = scratchSurfaces
        val protectedAncestors = scratchProtected
        val overlayClocks = scratchOverlayClocks
        previewSurfaces.clear()
        protectedAncestors.clear()
        overlayClocks.clear()

        collectPreviewSurfaceRects(decor, decor, previewSurfaces)
        if (previewSurfaces.isNotEmpty()) {
            collectSurfaceAndSlotAncestors(decor, decor, protectedAncestors)
            collectOverlayClocks(decor, previewSurfaces, protectedAncestors, overlayClocks)
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
            if (hiddenViews.isNotEmpty()) {
                hiddenViews.keys.toList().forEach { view ->
                    if (view.rootView === decor && restoreStockView(view)) changed = true
                }
            }
        }
        return changed
    }

    private fun collectSurfaceAndSlotAncestors(
        current: ViewGroup,
        stopAt: ViewGroup,
        out: MutableSet<View>
    ): Boolean {
        var containsProtected = false
        for (i in 0 until current.childCount) {
            val child = current.getChildAt(i)
            val isProtectedLeaf = child is PreviewClockSlot ||
                ((child is SurfaceView || child is TextureView) && !isDedicatedClockSurface(child))
            if (isProtectedLeaf) {
                out += child
                containsProtected = true
            } else if (child is ViewGroup && collectSurfaceAndSlotAncestors(child, stopAt, out)) {
                containsProtected = true
            }
        }
        if (containsProtected) {
            var cursor: View? = current
            while (cursor != null) {
                out += cursor
                if (cursor === stopAt) break
                cursor = cursor.parent as? View
            }
        }
        return containsProtected
    }

    private fun isDedicatedClockSurface(view: View): Boolean {
        val entry = resolveEntryName(view)
        if (entry != null) {
            if (MAIN_PREVIEW_SURFACE_HINTS.any { entry.contains(it, ignoreCase = true) }) {
                return false
            }
            if (entry in CLOCK_ENTRY_NAMES || entry.contains("clock", ignoreCase = true)) {
                return true
            }
        }
        val viewClass = view.javaClass
        dedicatedClockSurfaceCache[viewClass]?.let { return it }
        var matched = false
        var cls: Class<*>? = viewClass
        while (cls != null && cls != SurfaceView::class.java && cls != TextureView::class.java && cls != View::class.java) {
            val name = cls.name
            if (CLOCK_CLASS_PREFIXES.any { name.startsWith(it) } ||
                CLOCK_CLASS_KEYWORDS.any { name.contains(it, ignoreCase = true) }
            ) {
                matched = true
                break
            }
            cls = cls.superclass
        }
        dedicatedClockSurfaceCache.putIfAbsent(viewClass, matched)
        return matched
    }

    private fun collectPreviewSurfaceRects(group: ViewGroup, decor: ViewGroup, out: MutableList<Rect>) {
        val minW = (decor.width * 0.15f).toInt().coerceAtLeast(80)
        val minH = (decor.height * 0.15f).toInt().coerceAtLeast(120)
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            val isPreviewSurface = (child is SurfaceView || child is TextureView) && !isDedicatedClockSurface(child)
            if (isPreviewSurface && child.isVisible) {
                val rect = globalVisibleBounds(child)
                if (!rect.isEmpty && rect.width() >= minW && rect.height() >= minH) {
                    out += rect
                    continue
                }
            }
            if (child is ViewGroup) {
                collectPreviewSurfaceRects(child, decor, out)
            }
        }
    }

    private fun collectOverlayClocks(
        group: ViewGroup,
        previewSurfaces: List<Rect>,
        protectedAncestors: Set<View>,
        out: MutableSet<View>
    ) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is PreviewClockSlot) continue
            if ((child is SurfaceView || child is TextureView) && !isDedicatedClockSurface(child)) continue

            if (child !in protectedAncestors) {
                if (fillVisibleBounds(child, scratchBounds) &&
                    isOverlayClockCandidate(child, scratchBounds, previewSurfaces)
                ) {
                    out += child
                    continue
                }
            }

            if (child is ViewGroup) {
                collectOverlayClocks(child, previewSurfaces, protectedAncestors, out)
            }
        }
    }

    private fun isOverlayClockCandidate(
        view: View,
        bounds: Rect,
        previewSurfaces: List<Rect>
    ): Boolean {
        val entry = resolveEntryName(view)
        if (entry != null && EXCLUDED_ENTRY_KEYWORDS.any { entry.contains(it, ignoreCase = true) }) {
            return false
        }

        val matchingSurface = previewSurfaces.firstOrNull { surfaceRect ->
            overlapsPreviewSurface(bounds, surfaceRect)
        } ?: return false

        if (isExplicitClockClassOrEntry(view, entry)) {
            return true
        }

        if (view is TextView &&
            view !is EditText &&
            view !is Button &&
            !view.isClickable &&
            !view.isLongClickable
        ) {
            val upperLimitY = matchingSurface.top + (matchingSurface.height() * 0.72f)
            if (bounds.centerY() <= upperLimitY && isPreviewClockOrDateText(view.text?.toString())) {
                return true
            }
        }

        return false
    }

    private fun isExplicitClockClassOrEntry(view: View, entry: String?): Boolean {
        if (entry != null && entry in CLOCK_ENTRY_NAMES) return true
        if (view is TextClock) return true

        val viewClass = view.javaClass
        explicitClockClassCache[viewClass]?.let { return it }
        var matched = false
        var cls: Class<*>? = viewClass
        while (cls != null && cls != ViewGroup::class.java && cls != View::class.java) {
            val name = cls.name
            if (name.contains("PreviewClockSlot") || name.contains("ClockFaceView")) {
                matched = false
                break
            }
            if (CLOCK_CLASS_PREFIXES.any { name.startsWith(it) } ||
                CLOCK_CLASS_KEYWORDS.any { name.contains(it, ignoreCase = true) }
            ) {
                matched = true
                break
            }
            cls = cls.superclass
        }
        explicitClockClassCache.putIfAbsent(viewClass, matched)
        return matched
    }

    private fun overlapsPreviewSurface(bounds: Rect, surfaceRect: Rect): Boolean {
        if (bounds.width() < 6 || bounds.height() < 6) return false
        if (bounds.width() > surfaceRect.width() * 1.35f || bounds.height() > surfaceRect.height() * 1.35f) {
            return false
        }
        val interLeft = maxOf(bounds.left, surfaceRect.left)
        val interTop = maxOf(bounds.top, surfaceRect.top)
        val interRight = minOf(bounds.right, surfaceRect.right)
        val interBottom = minOf(bounds.bottom, surfaceRect.bottom)
        if (interLeft >= interRight || interTop >= interBottom) return false

        if (surfaceRect.contains(bounds.centerX(), bounds.centerY())) return true

        val boundsArea = bounds.width().toLong() * bounds.height().toLong()
        if (boundsArea <= 0L) return false
        val interArea = (interRight - interLeft).toLong() * (interBottom - interTop).toLong()
        return interArea * 100L >= boundsArea * 40L
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
        val rect = Rect()
        fillVisibleBounds(view, rect)
        return rect
    }

    private fun fillVisibleBounds(view: View, out: Rect): Boolean {
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) {
            out.setEmpty()
            return false
        }
        if (view.getGlobalVisibleRect(out) && !out.isEmpty) {
            return true
        }
        view.getLocationInWindow(scratchLoc)
        val scaledW = (view.width * kotlin.math.abs(view.scaleX)).toInt().coerceAtLeast(1)
        val scaledH = (view.height * kotlin.math.abs(view.scaleY)).toInt().coerceAtLeast(1)
        out.set(scratchLoc[0], scratchLoc[1], scratchLoc[0] + scaledW, scratchLoc[1] + scaledH)
        return !out.isEmpty
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
        val ids = legacyIdsCache.getOrPut(pkg) {
            intArrayOf(
                container.resources.getIdentifier(LOCK_TIME_ID, ID_TYPE, pkg),
                container.resources.getIdentifier(LOCK_DATE_ID, ID_TYPE, pkg)
            )
        }
        val timeId = ids[0]
        val dateId = ids[1]

        val timeView = if (timeId != 0) container.findViewById<View>(timeId) else null
        val dateView = if (dateId != 0) container.findViewById<View>(dateId) else null

        val enabled = ModulePrefs.isClockEnabled && ModulePrefs.isWallpaperPreviewEnabled
        val existingSlot = findSlot(container)

        if (enabled) {
            hideStockView(timeView)
            hideStockView(dateView)
            if (container.clipChildren) container.clipChildren = false
            if (container.clipToPadding) container.clipToPadding = false

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
        if (view.animation != null) view.clearAnimation()
        view.animate().cancel()
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
