package com.drdisagree.xmorphclock.xposed.systemui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.drdisagree.xmorphclock.xposed.clock.SlotWidth
import com.drdisagree.xmorphclock.xposed.hook.HookEnv
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
import java.util.concurrent.atomic.AtomicInteger

object PreviewClockHook {

    private const val BINDER_CLASS = "com.android.systemui.keyguard.ui.binder.KeyguardPreviewClockViewBinder"
    private const val VIEW_BINDER_CLASS = "com.android.systemui.keyguard.ui.binder.KeyguardPreviewViewBinder"
    private const val RENDERER_CLASS = "com.android.systemui.keyguard.ui.preview.KeyguardPreviewRenderer"
    private const val CLOCK_VM_CLASS = "com.android.systemui.keyguard.ui.viewmodel.KeyguardPreviewClockViewModel"
    private const val CLOCK_VM_ALT_CLASS = "com.android.systemui.keyguard.ui.preview.KeyguardPreviewClockViewModel"
    private const val VIEW_MODEL_FRAGMENT = "KeyguardPreviewClockViewModel"
    private const val CONSTRAINT_LAYOUT_SUFFIX = ".ConstraintLayout"
    private const val CONSTRAINT_LP_CLASS = "androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams"
    private const val LARGE_CLOCK_ID = "lockscreen_clock_view_large"
    private const val SMALL_CLOCK_ID = "lockscreen_clock_view"
    private const val SMARTSPACE_ID = "bc_smartspace_view"
    private const val ID_TYPE = "id"
    private const val TOP_FRACTION = 0.14f
    private const val CANCEL_GAP_MS = 500L

    private val RENDERER_METHOD_KEYWORDS = listOf(
        "render",
        "setup",
        "clock",
        "preview",
        "root",
        "bind"
    )

    private val PREVIEW_STACK_HINTS = listOf(
        "KeyguardPreview",
        "LockscreenPreview",
        "KeyguardRemotePreview",
        "CustomizationProvider"
    )

    private val PREVIEW_VIEW_CLASS_HINTS = listOf(
        "KeyguardRootView",
        "KeyguardPreview",
        "AnimatableClockView",
        "FlexClockView",
        "SimpleDigitalClockTextView",
        "KeyguardClockSwitch"
    )

    private val PREVIEW_CLOCK_IDS = listOf(
        LARGE_CLOCK_ID,
        SMALL_CLOCK_ID,
        SMARTSPACE_ID
    )

    private val EXTRA_IDS = listOf(
        "clock_view",
        "clock_host_view",
        "small_clock_container",
        "large_clock_container",
        "date_smartspace_view",
        "date_smartspace_view_large",
        "weather_smartspace_view",
        "weather_smartspace_view_large",
        SMARTSPACE_ID,
        "keyguard_slice_view"
    )

    private val EXTRA_CLASS_PREFIXES = listOf(
        "com.android.keyguard.AnimatableClockView",
        "com.android.systemui.clocks.",
        "com.google.android.systemui.clocks.",
        "com.android.systemui.shared.clocks.",
        "com.android.systemui.customization.clocks.",
        "com.android.systemui.plugins.clocks.",
        "com.google.android.systemui.smartspace.DateSmartspaceView",
        "com.google.android.systemui.smartspace.WeatherSmartspaceView",
        "com.android.keyguard.KeyguardSliceView"
    )

    private data class HiddenState(
        val visibility: Int,
        val alpha: Float
    )

    private class RootState(
        var viewModel: WeakReference<Any>? = null,
        var lastIsLarge: Boolean = true,
        var cachedTopOffset: Int = 0,
        var lastCancelMs: Long = 0L,
        var delegated: Boolean = false
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hookedHosts = WeakHashMap<ViewGroup, LongArray>()
    private val hookedRoots = WeakHashMap<ViewGroup, RootState>()
    private val watchedContainers = WeakHashMap<ViewGroup, Boolean>()
    private val hiddenViews = WeakHashMap<View, HiddenState>()
    private var latestViewModel: WeakReference<Any>? = null
    private var lastErrorLogMs = 0L

    private val totalRoots = AtomicInteger(0)
    private val totalSlots = AtomicInteger(0)
    private val totalSetViews = AtomicInteger(0)

    fun describe(): String =
        "roots=${totalRoots.get()}, slots=${totalSlots.get()}, setView=${totalSetViews.get()}, vm=${latestViewModel?.get() != null}"

    fun install() {
        val binderClass = HookEnv.findClass(BINDER_CLASS)
        val viewBinderClass = HookEnv.findClass(VIEW_BINDER_CLASS)
        val rendererClass = HookEnv.findClass(RENDERER_CLASS)
        val vmClass = HookEnv.findClass(CLOCK_VM_CLASS) ?: HookEnv.findClass(CLOCK_VM_ALT_CLASS)

        HookReporter.probe(
            "PreviewClock: binder=${ok(binderClass)}, viewBinder=${ok(viewBinderClass)}, " +
                "renderer=${ok(rendererClass)}, vm=${ok(vmClass)}"
        )

        vmClass?.let(::hookClockViewModel)
        binderClass?.let(::hookPreviewBinder)
        viewBinderClass?.let(::hookPreviewViewBinder)
        rendererClass?.let(::hookPreviewRenderer)
        hookSurfaceControlViewHost()
    }

    private fun ok(cls: Class<*>?): String = if (cls != null) "ok" else "missing"

    private fun hookClockViewModel(vmClass: Class<*>) {
        vmClass.declaredConstructors.forEach { constructor ->
            constructor.isAccessible = true
            constructor.hookAfter { chain, _ ->
                chain.thisObject?.let { vm ->
                    latestViewModel = WeakReference(vm)
                    HookReporter.count("previewVmCreated")
                }
            }
        }
    }

    private fun hookPreviewBinder(binderClass: Class<*>) {
        val bindMethods = binderClass.declaredMethods.filter { it.name == "bind" }
        HookReporter.probe("PreviewClock: bindMethods=${bindMethods.size}")
        bindMethods.forEach { method ->
            method.isAccessible = true
            method.hookAfter { chain, _ ->
                runCatching {
                    val args = chain.args
                    if (args.isEmpty()) return@hookAfter

                    val viewModel = args.firstOrNull {
                        it != null && it.javaClass.name.contains(VIEW_MODEL_FRAGMENT)
                    }?.also { latestViewModel = WeakReference(it) } ?: latestViewModel?.get()

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
                        HookReporter.count("previewBinderRoot")
                        inspectAndHookRoot(root, viewModel)
                    }
                }.onFailure { logThrottled("PreviewClock binder hook failed", it) }
            }
        }
    }

    private fun hookPreviewViewBinder(viewBinderClass: Class<*>) {
        val bindMethods = viewBinderClass.declaredMethods.filter { it.name == "bind" }
        HookReporter.probe("PreviewClock: viewBindMethods=${bindMethods.size}")
        bindMethods.forEach { method ->
            method.isAccessible = true
            method.hookAfter { chain, _ ->
                runCatching {
                    val args = chain.args
                    val vm = args.firstOrNull {
                        it != null && it.javaClass.name.contains(VIEW_MODEL_FRAGMENT)
                    } ?: findNestedClockVm(args) ?: latestViewModel?.get()
                    args.filterIsInstance<ViewGroup>().forEach { root ->
                        HookReporter.count("previewViewBinderRoot")
                        inspectAndHookRoot(root, vm)
                    }
                }.onFailure { logThrottled("PreviewClock viewBinder hook failed", it) }
            }
        }
    }

    private fun findNestedClockVm(args: List<Any?>): Any? {
        for (arg in args) {
            if (arg == null) continue
            val direct = arg.fieldValue("clockViewModel") ?: arg.callMethod("getClockViewModel")
            if (direct != null && direct.javaClass.name.contains(VIEW_MODEL_FRAGMENT)) {
                return direct
            }
        }
        return null
    }

    private fun hookPreviewRenderer(rendererClass: Class<*>) {
        rendererClass.declaredMethods
            .filter { method ->
                val lower = method.name.lowercase()
                RENDERER_METHOD_KEYWORDS.any { lower.contains(it) }
            }
            .forEach { method ->
                method.isAccessible = true
                method.hookAfter { chain, _ ->
                    runCatching {
                        HookReporter.count("previewRenderer_${method.name}")
                        val vm = latestViewModel?.get()
                        chain.args.filterIsInstance<ViewGroup>().forEach { root ->
                            inspectAndHookRoot(root, viewModel = vm)
                        }
                        chain.thisObject?.let { renderer ->
                            scanRendererFields(renderer)
                            mainHandler.post { runCatching { scanRendererFields(renderer) } }
                        }
                    }.onFailure { logThrottled("PreviewClock renderer hook failed", it) }
                }
            }

        rendererClass.declaredConstructors.forEach { constructor ->
            constructor.isAccessible = true
            constructor.hookAfter { chain, _ ->
                runCatching {
                    HookReporter.count("previewRendererInit")
                    chain.thisObject?.let { renderer ->
                        scanRendererFields(renderer)
                        mainHandler.post { runCatching { scanRendererFields(renderer) } }
                        mainHandler.postDelayed({ runCatching { scanRendererFields(renderer) } }, 150L)
                    }
                }.onFailure { logThrottled("PreviewClock constructor hook failed", it) }
            }
        }
    }

    private fun hookSurfaceControlViewHost() {
        val setViewMethods = SurfaceControlViewHost::class.java.declaredMethods.filter {
            it.name == "setView" && it.parameterCount >= 1 && View::class.java.isAssignableFrom(it.parameterTypes[0])
        }
        HookReporter.probe("PreviewClock: surfaceSetView=${setViewMethods.size}")
        setViewMethods.forEach { method ->
            method.isAccessible = true
            method.hookAfter { chain, _ ->
                runCatching {
                    val root = chain.args.getOrNull(0) as? ViewGroup ?: return@hookAfter
                    if (!isKeyguardPreviewContext(root)) return@hookAfter
                    totalSetViews.incrementAndGet()
                    HookReporter.count("previewSurfaceSetView")
                    val vm = latestViewModel?.get()
                    inspectAndHookRoot(root, vm)
                    mainHandler.post { runCatching { inspectAndHookRoot(root, latestViewModel?.get()) } }
                }.onFailure { logThrottled("PreviewClock SurfaceControlViewHost hook failed", it) }
            }
        }
    }

    private fun isKeyguardPreviewContext(root: ViewGroup): Boolean {
        val stack = Thread.currentThread().stackTrace
        if (stack.any { frame -> PREVIEW_STACK_HINTS.any { frame.className.contains(it) } }) {
            return true
        }
        return looksLikeKeyguardPreviewTree(root)
    }

    @SuppressLint("DiscouragedApi")
    private fun looksLikeKeyguardPreviewTree(group: ViewGroup): Boolean {
        val pkg = group.context.packageName
        val sysUiPkg = SystemUiModule.PACKAGE
        for (idName in PREVIEW_CLOCK_IDS) {
            val id = group.resources.getIdentifier(idName, ID_TYPE, pkg)
                .takeIf { it != 0 }
                ?: group.resources.getIdentifier(idName, ID_TYPE, sysUiPkg)
            if (id != 0 && group.findViewById<View>(id) != null) return true
        }
        return hasPreviewClassHint(group)
    }

    private fun hasPreviewClassHint(view: View): Boolean {
        val name = view.javaClass.name
        if (PREVIEW_VIEW_CLASS_HINTS.any { name.contains(it) }) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (hasPreviewClassHint(view.getChildAt(i))) return true
            }
        }
        return false
    }

    private fun scanRendererFields(renderer: Any) {
        val viewModel = findViewModelOnRenderer(renderer)?.also {
            latestViewModel = WeakReference(it)
        } ?: latestViewModel?.get()

        val largeHost = renderer.fieldValue("largeClockHostView") as? ViewGroup
        val smallHost = renderer.fieldValue("smallClockHostView") as? ViewGroup
        if (largeHost != null) enforceHost(largeHost, isLarge = true)
        if (smallHost != null) enforceHost(smallHost, isLarge = false)

        var cls: Class<*>? = renderer.javaClass
        while (cls != null && cls != Any::class.java) {
            for (field in cls.declaredFields) {
                field.isAccessible = true
                val value = runCatching { field.get(renderer) }.getOrNull() ?: continue
                when (value) {
                    is ViewGroup -> {
                        if (value !== largeHost && value !== smallHost) {
                            inspectAndHookRoot(value, viewModel)
                        }
                    }
                    is SurfaceControlViewHost -> {
                        val hostView = value.view as? ViewGroup
                        if (hostView != null) {
                            inspectAndHookRoot(hostView, viewModel)
                        }
                    }
                }
            }
            cls = cls.superclass
        }
    }

    private fun findViewModelOnRenderer(renderer: Any): Any? {
        var cls: Class<*>? = renderer.javaClass
        while (cls != null && cls != Any::class.java) {
            for (field in cls.declaredFields) {
                if (field.type.name.contains(VIEW_MODEL_FRAGMENT)) {
                    field.isAccessible = true
                    return runCatching { field.get(renderer) }.getOrNull()
                }
            }
            cls = cls.superclass
        }
        return null
    }

    @SuppressLint("DiscouragedApi")
    private fun inspectAndHookRoot(root: ViewGroup, viewModel: Any?) {
        if (isConstraintLayout(root)) {
            enforceConstraintRoot(root, viewModel)
            return
        }

        val nestedConstraint = findDescendantConstraintRoot(root)
        if (nestedConstraint != null) {
            enforceConstraintRoot(nestedConstraint, viewModel)
            return
        }

        val largeId = resolveResourceId(root, LARGE_CLOCK_ID)
        val smallId = resolveResourceId(root, SMALL_CLOCK_ID)
        val largeView = if (largeId != 0) root.findViewById<View>(largeId) else null
        val smallView = if (smallId != 0) root.findViewById<View>(smallId) else null

        if (largeView is ViewGroup && smallView is ViewGroup && largeView !== smallView) {
            enforceHost(largeView, isLarge = true)
            enforceHost(smallView, isLarge = false)
            return
        }

        // Wait if root is still an empty wrapper FrameLayout before KeyguardRootView is inflated/added
        if (root.childCount == 0) {
            watchEmptyWrapper(root, viewModel)
            return
        }

        enforceConstraintRoot(root, viewModel)
    }

    private fun watchEmptyWrapper(container: ViewGroup, viewModel: Any?) {
        if (watchedContainers.containsKey(container)) return
        watchedContainers[container] = true
        val containerRef = WeakReference(container)
        val vmRef = viewModel?.let(::WeakReference)
        container.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                val target = containerRef.get() ?: return
                if (child is PreviewClockSlot) return
                mainHandler.post {
                    runCatching {
                        val vm = vmRef?.get() ?: latestViewModel?.get()
                        inspectAndHookRoot(target, vm)
                    }
                }
            }

            override fun onChildViewRemoved(parent: View?, child: View?) = Unit
        })
    }

    private fun findDescendantConstraintRoot(group: ViewGroup): ViewGroup? {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is PreviewClockSlot) continue
            if (child is ViewGroup) {
                if (isConstraintLayout(child)) return child
                val found = findDescendantConstraintRoot(child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun enforceHost(host: ViewGroup, isLarge: Boolean) {
        if (hookedHosts.containsKey(host)) return
        val cancelStamp = LongArray(1)
        hookedHosts[host] = cancelStamp
        HookReporter.count(if (isLarge) "previewHostLarge" else "previewHostSmall")

        host.onEachPreDrawGate {
            val changed = try {
                syncHost(host, isLarge)
            } catch (throwable: Throwable) {
                logThrottled("PreviewClock syncHost failed", throwable)
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

    private fun syncHost(host: ViewGroup, isLarge: Boolean): Boolean {
        val enabled = ModulePrefs.isClockEnabled && ModulePrefs.isWallpaperPreviewEnabled
        val existingSlot = findSlot(host)
        var changed = false

        if (enabled) {
            for (i in 0 until host.childCount) {
                val child = host.getChildAt(i)
                if (child is PreviewClockSlot) continue
                if (hideStockView(child)) changed = true
            }
            val desiredWidth = resolveSlotWidth(host)
            if (existingSlot == null) {
                val created = PreviewClockSlot(host.context, initialLarge = isLarge)
                val params = FrameLayout.LayoutParams(
                    desiredWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    if (isLarge) Gravity.CENTER else Gravity.START or Gravity.TOP
                )
                host.addView(created, params)
                totalSlots.incrementAndGet()
                HookReporter.count("previewSlotAdded")
                changed = true
            } else {
                if (!existingSlot.isVisible) {
                    existingSlot.isVisible = true
                    changed = true
                }
                val lp = existingSlot.layoutParams
                if (lp != null && desiredWidth > 0 && lp.width != desiredWidth) {
                    lp.width = desiredWidth
                    existingSlot.layoutParams = lp
                    changed = true
                }
                if (host.getChildAt(host.childCount - 1) !== existingSlot) {
                    existingSlot.bringToFront()
                    changed = true
                }
            }
        } else {
            for (i in 0 until host.childCount) {
                val child = host.getChildAt(i)
                if (child is PreviewClockSlot) {
                    if (child.isVisible) {
                        child.isVisible = false
                        changed = true
                    }
                } else if (restoreStockView(child)) {
                    changed = true
                }
            }
        }
        return changed
    }

    private fun resolveSlotWidth(host: ViewGroup): Int {
        if (host.width > 0) return host.width
        val lpWidth = host.layoutParams?.width ?: 0
        if (lpWidth == ViewGroup.LayoutParams.WRAP_CONTENT) {
            val capped = SlotWidth.cap(host.context)
            if (capped > 0) return capped
        }
        return ViewGroup.LayoutParams.MATCH_PARENT
    }

    private fun enforceConstraintRoot(root: ViewGroup, viewModel: Any?) {
        val existing = hookedRoots[root]
        if (existing != null) {
            if (viewModel != null) existing.viewModel = WeakReference(viewModel)
            return
        }
        val state = RootState(viewModel = (viewModel ?: latestViewModel?.get())?.let(::WeakReference))
        hookedRoots[root] = state
        totalRoots.incrementAndGet()
        HookReporter.count("previewRootHooked")

        root.onEachPreDrawGate {
            if (state.delegated) return@onEachPreDrawGate true
            val changed = try {
                syncConstraintRoot(root, state)
            } catch (throwable: Throwable) {
                logThrottled("PreviewClock syncConstraintRoot failed", throwable)
                false
            }
            val now = SystemClock.uptimeMillis()
            if (changed && now - state.lastCancelMs > CANCEL_GAP_MS) {
                state.lastCancelMs = now
                false
            } else {
                true
            }
        }
    }

    private fun syncConstraintRoot(root: ViewGroup, state: RootState): Boolean {
        // If root is a wrapper FrameLayout and a ConstraintLayout (KeyguardRootView) appeared inside later, delegate to it
        if (!isConstraintLayout(root)) {
            val nested = findDescendantConstraintRoot(root)
            if (nested != null) {
                state.delegated = true
                findSlot(root)?.let { slot -> root.removeView(slot) }
                enforceConstraintRoot(nested, state.viewModel?.get())
                return true
            }
        }

        val largeId = resolveResourceId(root, LARGE_CLOCK_ID)
        val smallId = resolveResourceId(root, SMALL_CLOCK_ID)
        val extraIds = EXTRA_IDS.mapNotNull { name ->
            resolveResourceId(root, name).takeIf { it != 0 }
        }.toSet()

        val vm = state.viewModel?.get() ?: latestViewModel?.get()
        val largeClockViews = HashSet<View>()
        val smallClockViews = HashSet<View>()
        val extraViews = HashSet<View>()

        var sizeKnownFromVm = false
        if (vm != null) {
            val selectedSize = readFlowValue(vm, "selectedClockSize")
            if (selectedSize != null) {
                val label = selectedSize.toString()
                when {
                    label.contains("SMALL", ignoreCase = true) -> {
                        state.lastIsLarge = false
                        sizeKnownFromVm = true
                    }
                    label.contains("LARGE", ignoreCase = true) -> {
                        state.lastIsLarge = true
                        sizeKnownFromVm = true
                    }
                }
            } else {
                val largeFlow = readFlowValue(vm, "isLargeClockVisible") as? Boolean
                val smallFlow = readFlowValue(vm, "isSmallClockVisible") as? Boolean
                if (largeFlow == true && smallFlow != true) {
                    state.lastIsLarge = true
                    sizeKnownFromVm = true
                } else if (smallFlow == true && largeFlow != true) {
                    state.lastIsLarge = false
                    sizeKnownFromVm = true
                }
            }

            val controller = readFlowValue(vm, "previewClock") ?: readFlowValue(vm, "currentClock")
            if (controller != null) {
                collectClockViews(controller.callMethod("getLargeClock"), largeClockViews)
                collectClockViews(controller.callMethod("getSmallClock"), smallClockViews)
            }
        }

        collectDescendantTargets(
            group = root,
            largeId = largeId,
            smallId = smallId,
            extraIds = extraIds,
            largeOut = largeClockViews,
            smallOut = smallClockViews,
            extraOut = extraViews
        )

        if (!sizeKnownFromVm) {
            val largeVisible = largeClockViews.any { effectiveVisibility(it) == View.VISIBLE && effectiveAlpha(it) > 0.1f }
            val smallVisible = smallClockViews.any { effectiveVisibility(it) == View.VISIBLE && effectiveAlpha(it) > 0.1f }
            if (largeVisible && !smallVisible) state.lastIsLarge = true
            else if (smallVisible && !largeVisible) state.lastIsLarge = false
        }

        val enabled = ModulePrefs.isClockEnabled && ModulePrefs.isWallpaperPreviewEnabled
        val existingSlot = findSlot(root)
        val allHiddenTargets = largeClockViews + smallClockViews + extraViews
        var changed = false

        if (enabled) {
            val measuredTop = (smallClockViews + extraViews + largeClockViews)
                .mapNotNull { view -> viewTopInRoot(view, root).takeIf { it > 0 } }
                .minOrNull()
            if (measuredTop != null) {
                state.cachedTopOffset = measuredTop
            } else if (state.cachedTopOffset == 0 && root.width > 0) {
                state.cachedTopOffset = (root.width * TOP_FRACTION).toInt()
            }

            allHiddenTargets.forEach { target ->
                if (hideStockView(target)) changed = true
            }

            if (existingSlot == null) {
                val created = PreviewClockSlot(root.context, initialLarge = state.lastIsLarge).apply {
                    id = View.generateViewId()
                }
                val params = createOverlayLayoutParams(root, state.cachedTopOffset)
                root.addView(created, params)
                totalSlots.incrementAndGet()
                HookReporter.count("previewSlotAdded")
                changed = true
            } else {
                existingSlot.setLarge(state.lastIsLarge)
                if (!existingSlot.isVisible) {
                    existingSlot.isVisible = true
                    changed = true
                }
                if (existingSlot.alpha != 1f) {
                    existingSlot.alpha = 1f
                    changed = true
                }
                val lp = existingSlot.layoutParams as? ViewGroup.MarginLayoutParams
                var lpUpdated = false
                if (lp != null) {
                    if (state.cachedTopOffset > 0 && lp.topMargin != state.cachedTopOffset) {
                        lp.topMargin = state.cachedTopOffset
                        lpUpdated = true
                    }
                    val targetWidth = if (root.width > 0) root.width else lp.width
                    if (targetWidth > 0 && lp.width != targetWidth) {
                        lp.width = targetWidth
                        lpUpdated = true
                    }
                    if (lpUpdated) {
                        existingSlot.layoutParams = lp
                        changed = true
                    }
                }
                if (root.getChildAt(root.childCount - 1) !== existingSlot) {
                    existingSlot.bringToFront()
                    changed = true
                }
            }
        } else {
            allHiddenTargets.forEach { target ->
                if (restoreStockView(target)) changed = true
            }
            if (existingSlot != null && existingSlot.isVisible) {
                existingSlot.isVisible = false
                changed = true
            }
        }
        return changed
    }

    private fun collectDescendantTargets(
        group: ViewGroup,
        largeId: Int,
        smallId: Int,
        extraIds: Set<Int>,
        largeOut: MutableSet<View>,
        smallOut: MutableSet<View>,
        extraOut: MutableSet<View>
    ) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is PreviewClockSlot) continue
            val matched = when {
                largeId != 0 && child.id == largeId -> {
                    largeOut += child
                    true
                }
                smallId != 0 && child.id == smallId -> {
                    smallOut += child
                    true
                }
                child.id in extraIds -> {
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
            if (!matched && child is ViewGroup) {
                collectDescendantTargets(child, largeId, smallId, extraIds, largeOut, smallOut, extraOut)
            }
        }
    }

    private fun viewTopInRoot(view: View, root: ViewGroup): Int {
        var top = view.top
        var parent = view.parent
        while (parent is ViewGroup && parent !== root) {
            top += parent.top
            parent = parent.parent
        }
        return if (parent === root) top else 0
    }

    private fun effectiveVisibility(view: View): Int = hiddenViews[view]?.visibility ?: view.visibility

    private fun effectiveAlpha(view: View): Float = hiddenViews[view]?.alpha ?: view.alpha

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
        if (className.contains("PreviewClockSlot") || className.contains("ClockFaceView")) return false
        if (EXTRA_CLASS_PREFIXES.any { className.startsWith(it) }) return true
        return className.contains("Clock", ignoreCase = true)
    }

    private fun isConstraintLayout(view: ViewGroup): Boolean {
        var cls: Class<*>? = view.javaClass
        while (cls != null && cls != ViewGroup::class.java) {
            if (cls.name.endsWith(CONSTRAINT_LAYOUT_SUFFIX)) return true
            cls = cls.superclass
        }
        return false
    }

    private fun createOverlayLayoutParams(root: ViewGroup, topMargin: Int): ViewGroup.LayoutParams {
        val targetWidth = if (root.width > 0) root.width else ViewGroup.LayoutParams.MATCH_PARENT
        if (isConstraintLayout(root)) {
            val lp = runCatching {
                val lpClass = root.javaClass.classLoader?.loadClass(CONSTRAINT_LP_CLASS)
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

    private fun hideStockView(view: View): Boolean {
        var changed = false
        if (!hiddenViews.containsKey(view)) {
            hiddenViews[view] = HiddenState(view.visibility, view.alpha)
        } else if (view.visibility == View.VISIBLE) {
            hiddenViews[view] = HiddenState(View.VISIBLE, view.alpha.takeIf { it > 0f } ?: 1f)
        }
        if (view.visibility != View.INVISIBLE) {
            view.visibility = View.INVISIBLE
            changed = true
        }
        if (view.alpha != 0f) {
            view.alpha = 0f
            changed = true
        }
        return changed
    }

    private fun restoreStockView(view: View): Boolean {
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
