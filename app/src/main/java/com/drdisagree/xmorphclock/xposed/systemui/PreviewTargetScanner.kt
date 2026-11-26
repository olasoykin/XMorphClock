package com.drdisagree.xmorphclock.xposed.systemui

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.widget.TextClock
import com.drdisagree.xmorphclock.xposed.hook.callMethod
import com.drdisagree.xmorphclock.xposed.hook.fieldValue
import com.drdisagree.xmorphclock.xposed.hook.findMethod
import com.drdisagree.xmorphclock.xposed.preview.PreviewClockSlot
import java.util.WeakHashMap

internal object PreviewTargetScanner {

    private const val LARGE_CLOCK_ID = "lockscreen_clock_view_large"
    private const val SMALL_CLOCK_ID = "lockscreen_clock_view"
    private const val SMARTSPACE_ID = "bc_smartspace_view"
    private const val ID_TYPE = "id"
    private const val TOP_FRACTION = 0.16f

    private val LARGE_ENTRY_NAMES = setOf(
        LARGE_CLOCK_ID,
        "large_clock_container",
        "large_clock_view",
        "clock_view_large",
        "large_clock_host_view"
    )

    private val SMALL_ENTRY_NAMES = setOf(
        SMALL_CLOCK_ID,
        "small_clock_container",
        "small_clock_view",
        "clock_view",
        "small_clock_host_view",
        "lock_time"
    )

    private val EXTRA_ENTRY_NAMES = setOf(
        "clock_host_view",
        "lockscreen_clock_host_view",
        "keyguard_clock_container",
        "keyguard_status_area",
        "keyguard_status_view",
        "date_smartspace_view",
        "date_smartspace_view_large",
        "weather_smartspace_view",
        "weather_smartspace_view_large",
        "smartspace_view",
        "smartspace_container",
        "date_view",
        "keyguard_date_view",
        "clock_container",
        "preview_clock",
        "lockscreen_clock",
        SMARTSPACE_ID,
        "keyguard_slice_view",
        "lock_date"
    )

    private val EXTRA_CLASS_PREFIXES = listOf(
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
        "com.android.systemui.statusbar.policy.DateView"
    )

    private val CLOCK_SIZE_PROPERTY_NAMES = listOf(
        "selectedClockSize",
        "previewClockSize",
        "clockSize"
    )

    private val CONTROLLER_FLOW_NAMES = listOf(
        "previewClock",
        "currentClock",
        "clock"
    )

    data class HiddenState(
        val visibility: Int,
        val alpha: Float
    )

    data class ScanResult(
        val largeViews: Set<View>,
        val smallViews: Set<View>,
        val extraViews: Set<View>
    ) {
        val allTargets: Set<View> = largeViews + smallViews + extraViews
    }

    private val hiddenViews = WeakHashMap<View, HiddenState>()
    private val entryNameCache = HashMap<Int, String>()

    fun clearHiddenState(view: View) {
        hiddenViews.remove(view)
    }

    fun readClockSize(vm: Any?): Boolean? {
        vm ?: return null
        for (prop in CLOCK_SIZE_PROPERTY_NAMES) {
            val selected = readFlowValue(vm, prop) ?: continue
            val label = selected.toString()
            when {
                label.contains("SMALL", ignoreCase = true) -> return false
                label.contains("LARGE", ignoreCase = true) -> return true
            }
        }
        val largeFlow = readFlowValue(vm, "isLargeClockVisible") as? Boolean
        val smallFlow = readFlowValue(vm, "isSmallClockVisible") as? Boolean
        return when {
            largeFlow == true && smallFlow != true -> true
            smallFlow == true && largeFlow != true -> false
            else -> null
        }
    }

    fun scan(
        topRoot: ViewGroup,
        viewModel: Any?,
        renderer: Any?,
        protectedAncestors: Set<View>
    ): ScanResult {
        val largeViews = LinkedHashSet<View>()
        val smallViews = LinkedHashSet<View>()
        val extraViews = LinkedHashSet<View>()

        collectFromControllers(viewModel, renderer, topRoot, largeViews, smallViews)

        val largeId = resolveResourceId(topRoot, LARGE_CLOCK_ID)
        val smallId = resolveResourceId(topRoot, SMALL_CLOCK_ID)
        val extraIds = EXTRA_ENTRY_NAMES.mapNotNull { name ->
            resolveResourceId(topRoot, name).takeIf { it != 0 }
        }.toSet()

        collectDescendants(
            group = topRoot,
            largeId = largeId,
            smallId = smallId,
            extraIds = extraIds,
            protectedAncestors = protectedAncestors,
            largeOut = largeViews,
            smallOut = smallViews,
            extraOut = extraViews
        )

        return ScanResult(
            largeViews = largeViews.filterNotTo(LinkedHashSet()) { it in protectedAncestors },
            smallViews = smallViews.filterNotTo(LinkedHashSet()) { it in protectedAncestors },
            extraViews = extraViews.filterNotTo(LinkedHashSet()) { it in protectedAncestors }
        )
    }

    fun inferIsLarge(
        topRoot: ViewGroup,
        scan: ScanResult,
        currentIsLarge: Boolean
    ): Boolean {
        val largeFreshVisible = scan.largeViews.any { it.visibility == View.VISIBLE && it.alpha > 0.1f }
        val smallFreshVisible = scan.smallViews.any { it.visibility == View.VISIBLE && it.alpha > 0.1f }
        if (largeFreshVisible && !smallFreshVisible) return true
        if (smallFreshVisible && !largeFreshVisible) return false

        val largeEffective = scan.largeViews.any { effectiveShownUpTo(it, topRoot) }
        val smallEffective = scan.smallViews.any { effectiveShownUpTo(it, topRoot) }
        return when {
            largeEffective && !smallEffective -> true
            smallEffective && !largeEffective -> false
            else -> currentIsLarge
        }
    }

    fun resolveTopOffset(
        slotRoot: ViewGroup,
        isLarge: Boolean,
        scan: ScanResult,
        slotHeight: Int = 0
    ): Int {
        val rootHeight = slotRoot.height
        val rootWidth = slotRoot.width

        if (rootHeight > 0) {
            if (isLarge) {
                val minLarge = (rootHeight * 0.08f).toInt().coerceAtLeast((rootWidth * 0.08f).toInt())
                if (slotHeight > 0) {
                    val maxTop = ((rootHeight * 0.80f) - slotHeight).toInt().coerceAtLeast(minLarge)
                    val centeredTop = ((rootHeight * 0.45f) - (slotHeight * 0.5f)).toInt()
                    return centeredTop.coerceIn(minLarge, maxTop)
                }
                return (rootHeight * 0.22f).toInt().coerceAtLeast(minLarge)
            } else {
                val maxReasonableChildHeight = (rootHeight * 0.65f).toInt()
                val primaryTop = scan.smallViews
                    .asSequence()
                    .filter { it.height in 1 until maxReasonableChildHeight }
                    .mapNotNull { view -> viewTopInRoot(view, slotRoot).takeIf { it > 0 } }
                    .minOrNull()
                val minSmall = (rootHeight * 0.08f).toInt().coerceAtLeast((rootWidth * 0.10f).toInt())
                val maxSmall = (rootHeight * 0.28f).toInt().coerceAtLeast(minSmall)
                val fallbackSmall = (rootHeight * 0.10f).toInt().coerceAtLeast((rootWidth * 0.12f).toInt())
                val smallOrExtraTop = primaryTop ?: scan.extraViews
                    .asSequence()
                    .filter { it.height in 1 until maxReasonableChildHeight }
                    .mapNotNull { view -> viewTopInRoot(view, slotRoot).takeIf { it > 0 } }
                    .minOrNull()
                return smallOrExtraTop?.coerceIn(minSmall, maxSmall) ?: fallbackSmall
            }
        }

        val maxReasonableChildHeight = Int.MAX_VALUE
        val primaryViews = if (isLarge) scan.largeViews else scan.smallViews
        val primaryTop = primaryViews
            .asSequence()
            .filter { it.height in 1 until maxReasonableChildHeight }
            .mapNotNull { view -> viewTopInRoot(view, slotRoot).takeIf { it > 0 } }
            .minOrNull()
        if (primaryTop != null) return primaryTop
        return if (rootWidth > 0) (rootWidth * TOP_FRACTION).toInt() else 0
    }

    fun hideStockView(view: View): Boolean {
        var changed = false
        if (view.animation != null) view.clearAnimation()
        view.animate().cancel()
        when (view.visibility) {
            View.VISIBLE -> {
                hiddenViews[view] = HiddenState(View.VISIBLE, view.alpha.takeIf { it > 0f } ?: 1f)
            }
            View.GONE -> {
                val previousAlpha = hiddenViews[view]?.alpha ?: view.alpha.takeIf { it > 0f } ?: 1f
                hiddenViews[view] = HiddenState(View.GONE, previousAlpha)
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

    fun restoreStockView(view: View): Boolean {
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

    fun collectProtectedAncestors(root: ViewGroup): Set<View> {
        val protected = HashSet<View>()
        collectSlotsAndAncestors(root, root, protected)
        return protected
    }

    private fun collectSlotsAndAncestors(current: ViewGroup, stopAt: ViewGroup, out: MutableSet<View>): Boolean {
        var containsSlot = false
        for (i in 0 until current.childCount) {
            val child = current.getChildAt(i)
            if (child is PreviewClockSlot) {
                out += child
                containsSlot = true
            } else if (child is ViewGroup && collectSlotsAndAncestors(child, stopAt, out)) {
                containsSlot = true
            }
        }
        if (containsSlot) {
            var cursor: View? = current
            while (cursor != null) {
                out += cursor
                if (cursor === stopAt) break
                cursor = cursor.parent as? View
            }
        }
        return containsSlot
    }

    private fun collectFromControllers(
        viewModel: Any?,
        renderer: Any?,
        topRoot: ViewGroup,
        largeOut: MutableSet<View>,
        smallOut: MutableSet<View>
    ) {
        val controllers = LinkedHashSet<Any>()
        if (viewModel != null) {
            for (name in CONTROLLER_FLOW_NAMES) {
                readFlowValue(viewModel, name)?.let { controllers += it }
            }
        }
        if (renderer != null) {
            findClockControllersOnObject(renderer, controllers)
        }
        controllers.forEach { controller ->
            val largeTmp = LinkedHashSet<View>()
            val smallTmp = LinkedHashSet<View>()
            collectClockFaceViews(controller.callMethod("getLargeClock"), largeTmp)
            collectClockFaceViews(controller.callMethod("getSmallClock"), smallTmp)
            largeTmp.filterTo(largeOut) { isDescendantOf(it, topRoot) }
            smallTmp.filterTo(smallOut) { isDescendantOf(it, topRoot) }
        }
    }

    private fun findClockControllersOnObject(target: Any, out: MutableSet<Any>) {
        val directEventController = target.fieldValue("clockController") ?: target.fieldValue("mClockController")
        if (directEventController != null) {
            (directEventController.callMethod("getClock") ?: directEventController.fieldValue("clock"))?.let { out += it }
        }
        var cls: Class<*>? = target.javaClass
        while (cls != null && cls != Any::class.java) {
            for (field in cls.declaredFields) {
                val typeName = field.type.name
                if (!typeName.contains("ClockEventController") && !typeName.contains("ClockController")) continue
                field.isAccessible = true
                val value = runCatching { field.get(target) }.getOrNull() ?: continue
                if (typeName.contains("ClockEventController")) {
                    (value.callMethod("getClock") ?: value.fieldValue("clock"))?.let { out += it }
                } else {
                    out += value
                }
            }
            cls = cls.superclass
        }
    }

    private fun collectClockFaceViews(faceController: Any?, out: MutableSet<View>) {
        faceController ?: return
        (faceController.callMethod("getView") as? View)?.let { out += it }
        val views = faceController.callMethod("getLayout")?.callMethod("getViews") as? List<*>
        views?.filterIsInstance<View>()?.forEach { out += it }
    }

    private fun collectDescendants(
        group: ViewGroup,
        largeId: Int,
        smallId: Int,
        extraIds: Set<Int>,
        protectedAncestors: Set<View>,
        largeOut: MutableSet<View>,
        smallOut: MutableSet<View>,
        extraOut: MutableSet<View>
    ) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is PreviewClockSlot) continue
            val isProtectedAncestor = child in protectedAncestors
            val entryName = if (!isProtectedAncestor) resolveEntryName(child) else null

            val matched = if (!isProtectedAncestor) {
                when {
                    (largeId != 0 && child.id == largeId) || (entryName in LARGE_ENTRY_NAMES) -> {
                        largeOut += child
                        true
                    }
                    (smallId != 0 && child.id == smallId) || (entryName in SMALL_ENTRY_NAMES) -> {
                        smallOut += child
                        true
                    }
                    child.id in extraIds || (entryName in EXTRA_ENTRY_NAMES) -> {
                        extraOut += child
                        true
                    }
                    isStockClockOrExtraClass(child) -> {
                        if (child !in largeOut && child !in smallOut) {
                            extraOut += child
                        }
                        true
                    }
                    else -> false
                }
            } else {
                false
            }

            if ((!matched || isProtectedAncestor) && child is ViewGroup) {
                collectDescendants(child, largeId, smallId, extraIds, protectedAncestors, largeOut, smallOut, extraOut)
            }
        }
    }

    private fun resolveEntryName(view: View): String? {
        val id = view.id
        // Real compiled resource IDs have package namespace >= 0x02; dynamically generated IDs use 0x00.
        if (id == View.NO_ID || (id ushr 24) < 0x02) return null
        entryNameCache[id]?.let { return it.ifEmpty { null } }
        val name = runCatching { view.resources.getResourceEntryName(id) }.getOrNull().orEmpty()
        if (entryNameCache.size < 256) entryNameCache[id] = name
        return name.ifEmpty { null }
    }

    fun isPreviewClockView(view: View): Boolean {
        val entry = resolveEntryName(view)
        if (entry != null && (entry in LARGE_ENTRY_NAMES || entry in SMALL_ENTRY_NAMES || entry in EXTRA_ENTRY_NAMES)) {
            return true
        }
        return isStockClockOrExtraClass(view)
    }

    private fun isStockClockOrExtraClass(view: View): Boolean {
        if (view is TextClock) return true
        var cls: Class<*>? = view.javaClass
        while (cls != null && cls != ViewGroup::class.java && cls != View::class.java) {
            val className = cls.name
            if (className.contains("PreviewClockSlot") || className.contains("ClockFaceView")) return false
            if (EXTRA_CLASS_PREFIXES.any { className.startsWith(it) }) return true
            if (className.contains("Clock", ignoreCase = true) ||
                className.contains("Smartspace", ignoreCase = true) ||
                className.contains("KeyguardSlice", ignoreCase = true) ||
                className.contains("DateView", ignoreCase = true)
            ) {
                return true
            }
            cls = cls.superclass
        }
        return false
    }

    private fun readFlowValue(target: Any, name: String): Any? {
        val getterName = "get" + name.replaceFirstChar { it.uppercase() }
        val flow = target.callMethod(getterName) ?: target.fieldValue(name) ?: return null
        val valueMethod = flow.javaClass.findMethod("getValue") { it.parameterCount == 0 }
        if (valueMethod != null) {
            return runCatching { valueMethod.invoke(flow) }.getOrNull()
        }
        val delegate = flow.fieldValue("\$\$delegate_0")
        val delegateGetter = delegate?.javaClass?.findMethod("getValue") { it.parameterCount == 0 }
        if (delegateGetter != null) {
            return runCatching { delegateGetter.invoke(delegate) }.getOrNull()
        }
        return null
    }

    private fun viewTopInRoot(view: View, root: ViewGroup): Int {
        if (view === root) return 0
        var top = view.top
        var parent = view.parent
        while (parent is ViewGroup && parent !== root) {
            top += parent.top
            parent = parent.parent
        }
        return if (parent === root) top else 0
    }

    private fun isDescendantOf(view: View, ancestor: ViewGroup): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    private fun effectiveShownUpTo(view: View, stopAt: ViewGroup): Boolean {
        var cursor: View? = view
        while (cursor != null) {
            val hidden = hiddenViews[cursor]
            val vis = hidden?.visibility ?: cursor.visibility
            val alpha = hidden?.alpha ?: cursor.alpha
            if (vis != View.VISIBLE || alpha <= 0.1f) return false
            if (cursor === stopAt) return true
            cursor = cursor.parent as? View
        }
        return false
    }

    @SuppressLint("DiscouragedApi")
    private fun resolveResourceId(root: ViewGroup, name: String): Int {
        val pkg = root.context.packageName
        val primary = root.resources.getIdentifier(name, ID_TYPE, pkg)
        if (primary != 0) return primary
        return if (pkg != SystemUiModule.PACKAGE) {
            root.resources.getIdentifier(name, ID_TYPE, SystemUiModule.PACKAGE)
        } else {
            0
        }
    }
}
