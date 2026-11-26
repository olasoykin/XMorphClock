package com.drdisagree.xmorphclock.xposed.systemui

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.drdisagree.xmorphclock.xposed.hook.HookEnv
import com.drdisagree.xmorphclock.xposed.hook.HookSafety
import com.drdisagree.xmorphclock.xposed.hook.callMethod
import com.drdisagree.xmorphclock.xposed.hook.fieldValue
import com.drdisagree.xmorphclock.xposed.hook.findMethod
import com.drdisagree.xmorphclock.xposed.hook.hookAfter
import com.drdisagree.xmorphclock.xposed.hook.onEachPreDrawGate
import com.drdisagree.xmorphclock.xposed.hook.setFieldValue
import com.drdisagree.xmorphclock.xposed.prefs.ModulePrefs
import com.drdisagree.xmorphclock.xposed.preview.PreviewClockSlot
import com.drdisagree.xmorphclock.xposed.report.HookReporter
import java.lang.ref.WeakReference
import java.util.WeakHashMap

object PreviewClockHook {

    private const val BINDER_CLASS = "com.android.systemui.keyguard.ui.binder.KeyguardPreviewClockViewBinder"
    private const val RENDERER_CLASS = "com.android.systemui.keyguard.ui.preview.KeyguardPreviewRenderer"
    private const val VIEW_MODEL_FRAGMENT = "KeyguardPreviewClockViewModel"
    private const val CONSTRAINT_LAYOUT_FRAGMENT = "ConstraintLayout"
    private const val CONSTRAINT_LP_CLASS = "androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams"
    private const val LARGE_CLOCK_ID = "lockscreen_clock_view_large"
    private const val SMALL_CLOCK_ID = "lockscreen_clock_view"
    private const val SMARTSPACE_ID = "bc_smartspace_view"
    private const val ID_TYPE = "id"
    private const val HOOK_ID = "preview_clock"
    private const val TOP_FRACTION = 0.14f

    private val EXTRA_IDS = listOf(
        "date_smartspace_view",
        "date_smartspace_view_large",
        "weather_smartspace_view",
        "weather_smartspace_view_large",
        SMARTSPACE_ID,
        "keyguard_slice_view"
    )

    private val CLOCK_CLASS_PREFIXES = listOf(
        "com.android.keyguard.AnimatableClockView",
        "com.android.systemui.clocks.",
        "com.android.systemui.shared.clocks.",
        "com.android.systemui.customization.clocks.",
        "com.google.android.systemui.smartspace.DateSmartspaceView",
        "com.google.android.systemui.smartspace.WeatherSmartspaceView",
        "com.android.keyguard.KeyguardSliceView"
    )

    private class RootState(
        var viewModel: WeakReference<Any>? = null,
        var lastIsLarge: Boolean = true,
        var cachedTopOffset: Int = 0
    )

    private val hookedHosts = WeakHashMap<ViewGroup, Boolean>()
    private val hookedRoots = WeakHashMap<ViewGroup, RootState>()
    private val hiddenViews = WeakHashMap<View, Int>()

    fun install() {
        hookPreviewBinder()
        hookPreviewRenderer()
    }

    private fun hookPreviewBinder() {
        val binderClass = HookEnv.findClass(BINDER_CLASS) ?: return
        binderClass.declaredMethods
            .filter { it.name == "bind" }
            .forEach { method ->
                method.isAccessible = true
                method.hookAfter { chain, _ ->
                    val args = chain.args
                    if (args.isEmpty()) return@hookAfter

                    val viewModel = args.firstOrNull {
                        it != null && it.javaClass.name.contains(VIEW_MODEL_FRAGMENT)
                    }

                    // Case 1 (A14 early): bind(largeClockHostView, smallClockHostView, viewModel)
                    val arg0 = args.getOrNull(0) as? ViewGroup
                    val arg1 = args.getOrNull(1) as? ViewGroup
                    if (arg0 != null && arg1 != null && arg0 !== arg1) {
                        enforceHost(arg0, isLarge = true)
                        enforceHost(arg1, isLarge = false)
                        HookReporter.count("previewBinderHost")
                        return@hookAfter
                    }

                    // Case 2 (A14 QPR2 - A17): bind(context, rootView: ConstraintLayout, viewModel, ...)
                    args.filterIsInstance<ViewGroup>().forEach { root ->
                        inspectAndHookRoot(root, viewModel)
                    }
                }
            }
    }

    private fun hookPreviewRenderer() {
        val rendererClass = HookEnv.findClass(RENDERER_CLASS) ?: return

        rendererClass.declaredMethods
            .filter { it.name == "setUpClock" }
            .forEach { method ->
                method.isAccessible = true
                method.hookAfter { chain, _ ->
                    chain.args.filterIsInstance<ViewGroup>().forEach { root ->
                        inspectAndHookRoot(root, viewModel = null)
                    }
                }
            }

        rendererClass.declaredMethods
            .filter { it.name == "render" }
            .forEach { method ->
                method.isAccessible = true
                method.hookAfter { chain, _ ->
                    chain.thisObject?.let(::findHostFromFields)
                }
            }

        rendererClass.declaredConstructors.forEach { constructor ->
            constructor.isAccessible = true
            constructor.hookAfter { chain, _ ->
                chain.thisObject?.let(::findHostFromFields)
            }
        }
    }

    private fun findHostFromFields(renderer: Any) {
        val largeHost = renderer.fieldValue("largeClockHostView") as? ViewGroup
        val smallHost = renderer.fieldValue("smallClockHostView") as? ViewGroup
        if (largeHost != null) enforceHost(largeHost, isLarge = true)
        if (smallHost != null) enforceHost(smallHost, isLarge = false)

        listOf("parentView", "mParentView", "rootView").forEach { name ->
            val view = renderer.fieldValue(name) as? ViewGroup ?: return@forEach
            inspectAndHookRoot(view, viewModel = null)
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun inspectAndHookRoot(root: ViewGroup, viewModel: Any?) {
        if (root.javaClass.name.contains(CONSTRAINT_LAYOUT_FRAGMENT) || viewModel != null) {
            enforceConstraintRoot(root, viewModel)
            return
        }

        val pkg = root.context.packageName
        val largeId = root.resources.getIdentifier(LARGE_CLOCK_ID, ID_TYPE, pkg)
        val smallId = root.resources.getIdentifier(SMALL_CLOCK_ID, ID_TYPE, pkg)

        val largeView = if (largeId != 0) root.findViewById<View>(largeId) else null
        val smallView = if (smallId != 0) root.findViewById<View>(smallId) else null

        if (largeView is ViewGroup && smallView is ViewGroup) {
            enforceHost(largeView, isLarge = true)
            enforceHost(smallView, isLarge = false)
        } else {
            enforceConstraintRoot(root, viewModel)
        }
    }

    private fun enforceHost(host: ViewGroup, isLarge: Boolean) {
        if (hookedHosts.containsKey(host)) return
        hookedHosts[host] = true

        host.onEachPreDrawGate {
            HookSafety.runGuarded(HOOK_ID, maxCallsPerSecond = 120) {
                val enabled = ModulePrefs.isClockEnabled && ModulePrefs.isWallpaperPreviewEnabled
                val existingSlot = findSlot(host)
                if (enabled) {
                    for (i in 0 until host.childCount) {
                        val child = host.getChildAt(i)
                        if (child is PreviewClockSlot) continue
                        hideStockView(child)
                    }
                    if (existingSlot == null) {
                        val created = PreviewClockSlot(host.context, initialLarge = isLarge)
                        val params = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            if (isLarge) Gravity.CENTER else Gravity.START or Gravity.TOP
                        )
                        host.addView(created, params)
                    } else if (!existingSlot.isVisible) {
                        existingSlot.isVisible = true
                    }
                } else {
                    for (i in 0 until host.childCount) {
                        val child = host.getChildAt(i)
                        if (child is PreviewClockSlot) {
                            if (child.isVisible) child.isVisible = false
                        } else {
                            restoreStockView(child)
                        }
                    }
                }
            }
            true
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun enforceConstraintRoot(root: ViewGroup, viewModel: Any?) {
        val existing = hookedRoots[root]
        if (existing != null) {
            if (viewModel != null) existing.viewModel = WeakReference(viewModel)
            return
        }
        val state = RootState(viewModel = viewModel?.let(::WeakReference))
        hookedRoots[root] = state
        HookReporter.count("previewRootHooked")

        root.onEachPreDrawGate {
            HookSafety.runGuarded(HOOK_ID, maxCallsPerSecond = 120) {
                syncConstraintRoot(root, state)
            }
            true
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun syncConstraintRoot(root: ViewGroup, state: RootState) {
        val pkg = root.context.packageName
        val largeId = root.resources.getIdentifier(LARGE_CLOCK_ID, ID_TYPE, pkg)
        val smallId = root.resources.getIdentifier(SMALL_CLOCK_ID, ID_TYPE, pkg)
        val extraIds = EXTRA_IDS.mapNotNull { name ->
            root.resources.getIdentifier(name, ID_TYPE, pkg).takeIf { it != 0 }
        }.toSet()

        val vm = state.viewModel?.get()
        val largeClockViews = HashSet<View>()
        val smallClockViews = HashSet<View>()
        val extraViews = HashSet<View>()

        if (vm != null) {
            val controller = readFlowValue(vm, "selectedClockSize")
                ?.let { size ->
                    val label = size.toString()
                    when {
                        label.contains("SMALL", ignoreCase = true) -> state.lastIsLarge = false
                        label.contains("LARGE", ignoreCase = true) -> state.lastIsLarge = true
                    }
                    readFlowValue(vm, "previewClock")
                }
                ?: readFlowValue(vm, "previewClock")

            if (controller != null) {
                collectClockViews(controller.callMethod("getLargeClock"), largeClockViews)
                collectClockViews(controller.callMethod("getSmallClock"), smallClockViews)
            }
        }

        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is PreviewClockSlot) continue
            when {
                largeId != 0 && child.id == largeId -> largeClockViews += child
                smallId != 0 && child.id == smallId -> smallClockViews += child
                child.id in extraIds -> extraViews += child
                isStockClockOrExtraClass(child) -> {
                    if (child !in largeClockViews && child !in smallClockViews) {
                        extraViews += child
                    }
                }
            }
        }

        // Fallback large/small detection from stock clock view visibility when ViewModel flow isn't read
        if (vm == null || readFlowValue(vm, "selectedClockSize") == null) {
            val largeVisible = largeClockViews.any { it.visibility == View.VISIBLE && it.alpha > 0f }
            val smallVisible = smallClockViews.any { it.visibility == View.VISIBLE && it.alpha > 0f }
            if (largeVisible && !smallVisible) state.lastIsLarge = true
            else if (smallVisible && !largeVisible) state.lastIsLarge = false
        }

        val enabled = ModulePrefs.isClockEnabled && ModulePrefs.isWallpaperPreviewEnabled
        val existingSlot = findSlot(root)
        val allHiddenTargets = largeClockViews + smallClockViews + extraViews

        if (enabled) {
            val measuredTop = (smallClockViews + extraViews + largeClockViews)
                .mapNotNull { view -> view.top.takeIf { it > 0 } }
                .minOrNull()
            if (measuredTop != null) {
                state.cachedTopOffset = measuredTop
            } else if (state.cachedTopOffset == 0 && root.width > 0) {
                state.cachedTopOffset = (root.width * TOP_FRACTION).toInt()
            }

            allHiddenTargets.forEach(::hideStockView)

            if (existingSlot == null) {
                val created = PreviewClockSlot(root.context, initialLarge = state.lastIsLarge).apply {
                    id = View.generateViewId()
                }
                val params = createOverlayLayoutParams(root, state.cachedTopOffset)
                root.addView(created, params)
            } else {
                existingSlot.setLarge(state.lastIsLarge)
                if (!existingSlot.isVisible) existingSlot.isVisible = true
                val lp = existingSlot.layoutParams as? ViewGroup.MarginLayoutParams
                if (lp != null && state.cachedTopOffset > 0 && lp.topMargin != state.cachedTopOffset) {
                    lp.topMargin = state.cachedTopOffset
                    existingSlot.layoutParams = lp
                }
            }
        } else {
            allHiddenTargets.forEach(::restoreStockView)
            if (existingSlot != null && existingSlot.isVisible) {
                existingSlot.isVisible = false
            }
        }
    }

    private fun collectClockViews(faceController: Any?, out: MutableSet<View>) {
        faceController ?: return
        (faceController.callMethod("getView") as? View)?.let { out += it }
        val views = faceController.callMethod("getLayout")?.callMethod("getViews") as? List<*>
        views?.filterIsInstance<View>()?.forEach { out += it }
    }

    private fun readFlowValue(target: Any, name: String): Any? {
        val getterName = "get" + name.replaceFirstChar { it.uppercase() }
        val flow = target.callMethod(getterName) ?: target.fieldValue(name) ?: return null
        val valueMethod = flow.javaClass.findMethod("getValue") { it.parameterCount == 0 } ?: return null
        return runCatching { valueMethod.invoke(flow) }.getOrNull()
    }

    private fun isStockClockOrExtraClass(view: View): Boolean {
        val className = view.javaClass.name
        return CLOCK_CLASS_PREFIXES.any { className.startsWith(it) }
    }

    private fun createOverlayLayoutParams(root: ViewGroup, topMargin: Int): ViewGroup.LayoutParams {
        if (root.javaClass.name.contains(CONSTRAINT_LAYOUT_FRAGMENT)) {
            val lp = runCatching {
                val lpClass = root.javaClass.classLoader?.loadClass(CONSTRAINT_LP_CLASS)
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

    private fun hideStockView(view: View) {
        if (!hiddenViews.containsKey(view)) {
            hiddenViews[view] = view.visibility
        }
        if (view.visibility != View.INVISIBLE) {
            view.visibility = View.INVISIBLE
        }
    }

    private fun restoreStockView(view: View) {
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
