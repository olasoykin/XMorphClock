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
import com.drdisagree.xmorphclock.xposed.hook.hookAfter
import com.drdisagree.xmorphclock.xposed.hook.onEachPreDrawGate
import com.drdisagree.xmorphclock.xposed.hook.setFieldValue
import com.drdisagree.xmorphclock.xposed.prefs.ModulePrefs
import com.drdisagree.xmorphclock.xposed.preview.PreviewClockSlot
import com.drdisagree.xmorphclock.xposed.report.HookReporter
import java.lang.ref.WeakReference
import java.util.Collections
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
    private const val CANCEL_GAP_MS = 500L

    private val RENDERER_METHOD_KEYWORDS = listOf(
        "render",
        "setup",
        "clock",
        "preview",
        "root",
        "bind",
        "update",
        "init"
    )

    private val PREVIEW_STACK_HINTS = listOf(
        "KeyguardPreview",
        "LockscreenPreview",
        "KeyguardRemotePreview",
        "CustomizationProvider",
        "ClockPreview",
        "ClockCarousel",
        "KeyguardClock"
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

    private class TopRootState(
        var viewModel: WeakReference<Any>? = null,
        var renderer: WeakReference<Any>? = null,
        val dualHosts: MutableSet<ViewGroup> = Collections.newSetFromMap(WeakHashMap()),
        var lastIsLarge: Boolean = true,
        var cachedTopOffset: Int = 0,
        var lastCancelMs: Long = 0L,
        var promoted: Boolean = false
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hookedHosts = WeakHashMap<ViewGroup, LongArray>()
    private val watchedRoots = WeakHashMap<ViewGroup, TopRootState>()
    private var latestViewModel: WeakReference<Any>? = null
    private var latestRenderer: WeakReference<Any>? = null
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
                        enforceDualHosts(arg0, arg1, viewModel, latestRenderer?.get())
                        HookReporter.count("previewBinderHost")
                        return@hookAfter
                    }

                    // Case 2 (A14 QPR2 - A17): bind(context, rootView, viewModel, ...)
                    args.filterIsInstance<ViewGroup>().forEach { root ->
                        HookReporter.count("previewBinderRoot")
                        watchTree(root, viewModel = viewModel, renderer = latestRenderer?.get())
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
                    }?.also { latestViewModel = WeakReference(it) }
                        ?: findNestedClockVm(args)?.also { latestViewModel = WeakReference(it) }
                        ?: latestViewModel?.get()
                    args.filterIsInstance<ViewGroup>().forEach { root ->
                        HookReporter.count("previewViewBinderRoot")
                        watchTree(root, viewModel = vm, renderer = latestRenderer?.get())
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
                        val renderer = chain.thisObject?.also { latestRenderer = WeakReference(it) }
                        chain.args.filterIsInstance<ViewGroup>().forEach { root ->
                            watchTree(root, viewModel = vm, renderer = renderer)
                        }
                        if (renderer != null) {
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
                        latestRenderer = WeakReference(renderer)
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
                    watchTree(root, viewModel = latestViewModel?.get(), renderer = latestRenderer?.get())
                    mainHandler.post {
                        runCatching {
                            watchTree(root, viewModel = latestViewModel?.get(), renderer = latestRenderer?.get())
                        }
                    }
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
        if (PREVIEW_VIEW_CLASS_HINTS.any { name.contains(it) } || PreviewTargetScanner.isPreviewClockView(view)) {
            return true
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (hasPreviewClassHint(view.getChildAt(i))) return true
            }
        }
        return false
    }

    private fun scanRendererFields(renderer: Any) {
        latestRenderer = WeakReference(renderer)
        val viewModel = findViewModelOnRenderer(renderer)?.also {
            latestViewModel = WeakReference(it)
        } ?: latestViewModel?.get()

        val largeHost = renderer.fieldValue("largeClockHostView") as? ViewGroup
        val smallHost = renderer.fieldValue("smallClockHostView") as? ViewGroup
        if (largeHost != null && smallHost != null && largeHost !== smallHost) {
            enforceDualHosts(largeHost, smallHost, viewModel, renderer)
        }

        var cls: Class<*>? = renderer.javaClass
        while (cls != null && cls != Any::class.java) {
            for (field in cls.declaredFields) {
                field.isAccessible = true
                val value = runCatching { field.get(renderer) }.getOrNull() ?: continue
                when (value) {
                    is ViewGroup -> {
                        if (value !== largeHost && value !== smallHost) {
                            watchTree(value, viewModel = viewModel, renderer = renderer)
                        }
                    }
                    is SurfaceControlViewHost -> {
                        val hostView = value.view as? ViewGroup
                        if (hostView != null) {
                            watchTree(hostView, viewModel = viewModel, renderer = renderer)
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

    private fun enforceDualHosts(
        largeHost: ViewGroup,
        smallHost: ViewGroup,
        viewModel: Any?,
        renderer: Any?
    ) {
        val topRoot = resolveTopRoot(largeHost)
        val state = ensureRootWatched(topRoot, viewModel, renderer)
        state.dualHosts += largeHost
        state.dualHosts += smallHost
        enforceHost(largeHost, isLarge = true)
        enforceHost(smallHost, isLarge = false)
    }

    private fun watchTree(candidate: ViewGroup, viewModel: Any?, renderer: Any?) {
        val topRoot = resolveTopRoot(candidate)
        ensureRootWatched(topRoot, viewModel, renderer)
    }

    private fun resolveTopRoot(view: ViewGroup): ViewGroup = (view.rootView as? ViewGroup) ?: view

    private fun ensureRootWatched(topRoot: ViewGroup, viewModel: Any?, renderer: Any?): TopRootState {
        val existing = watchedRoots[topRoot]
        if (existing != null) {
            if (viewModel != null) existing.viewModel = WeakReference(viewModel)
            if (renderer != null) existing.renderer = WeakReference(renderer)
            return existing
        }
        val state = TopRootState(
            viewModel = (viewModel ?: latestViewModel?.get())?.let(::WeakReference),
            renderer = (renderer ?: latestRenderer?.get())?.let(::WeakReference)
        )
        watchedRoots[topRoot] = state
        totalRoots.incrementAndGet()
        HookReporter.count("previewRootHooked")

        topRoot.onEachPreDrawGate {
            if (state.promoted) return@onEachPreDrawGate true
            val actualTop = resolveTopRoot(topRoot)
            if (actualTop !== topRoot) {
                promoteToParentTopRoot(topRoot, actualTop, state)
                return@onEachPreDrawGate true
            }
            val changed = try {
                syncTopRoot(topRoot, state)
            } catch (throwable: Throwable) {
                logThrottled("PreviewClock syncTopRoot failed", throwable)
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
        return state
    }

    private fun promoteToParentTopRoot(oldRoot: ViewGroup, actualTop: ViewGroup, oldState: TopRootState) {
        oldState.promoted = true
        findSlot(oldRoot)?.let { slot -> oldRoot.removeView(slot) }
        val targetState = ensureRootWatched(actualTop, oldState.viewModel?.get(), oldState.renderer?.get())
        targetState.dualHosts.addAll(oldState.dualHosts)
        if (targetState.cachedTopOffset == 0 && oldState.cachedTopOffset > 0) {
            targetState.cachedTopOffset = oldState.cachedTopOffset
        }
        targetState.lastIsLarge = oldState.lastIsLarge
    }

    private fun syncTopRoot(topRoot: ViewGroup, state: TopRootState): Boolean {
        val vm = state.viewModel?.get() ?: latestViewModel?.get()
        val renderer = state.renderer?.get() ?: latestRenderer?.get()
        val protectedAncestors = PreviewTargetScanner.collectProtectedAncestors(topRoot)
        val scan = PreviewTargetScanner.scan(topRoot, vm, renderer, protectedAncestors)

        val vmSize = PreviewTargetScanner.readClockSize(vm)
        state.lastIsLarge = vmSize ?: PreviewTargetScanner.inferIsLarge(topRoot, scan, state.lastIsLarge)

        val enabled = ModulePrefs.isClockEnabled && ModulePrefs.isWallpaperPreviewEnabled
        val activeDualHosts = state.dualHosts.count { it.isAttachedToWindow } >= 2
        val existingSlot = findSlot(topRoot)
        var changed = false

        if (enabled) {
            scan.allTargets.forEach { target ->
                if (PreviewTargetScanner.hideStockView(target)) changed = true
            }

            if (activeDualHosts) {
                if (existingSlot != null) {
                    topRoot.removeView(existingSlot)
                    changed = true
                }
                return changed
            }

            val resolvedTop = PreviewTargetScanner.resolveTopOffset(
                slotRoot = topRoot,
                isLarge = state.lastIsLarge,
                scan = scan,
                slotHeight = existingSlot?.height ?: 0
            )
            if (resolvedTop > 0) {
                state.cachedTopOffset = resolvedTop
            }

            if (existingSlot == null) {
                val created = PreviewClockSlot(topRoot.context, initialLarge = state.lastIsLarge).apply {
                    id = View.generateViewId()
                }
                val params = createOverlayLayoutParams(topRoot, state.cachedTopOffset)
                topRoot.addView(created, params)
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
                    val targetWidth = if (topRoot.width > 0) topRoot.width else lp.width
                    if (targetWidth > 0 && lp.width != targetWidth) {
                        lp.width = targetWidth
                        lpUpdated = true
                    }
                    if (lpUpdated) {
                        existingSlot.layoutParams = lp
                        changed = true
                    }
                }
                if (topRoot.getChildAt(topRoot.childCount - 1) !== existingSlot) {
                    existingSlot.bringToFront()
                    changed = true
                }
            }
        } else {
            scan.allTargets.forEach { target ->
                if (PreviewTargetScanner.restoreStockView(target)) changed = true
            }
            if (existingSlot != null && existingSlot.isVisible) {
                existingSlot.isVisible = false
                changed = true
            }
        }
        return changed
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
                if (PreviewTargetScanner.hideStockView(child)) changed = true
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
                } else if (PreviewTargetScanner.restoreStockView(child)) {
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
            val constraintWidth = if (root.width > 0) root.width else 0
            val lp = runCatching {
                val lpClass = root.javaClass.classLoader?.loadClass(CONSTRAINT_LP_CLASS)
                val ctor = lpClass?.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                (ctor?.newInstance(constraintWidth, ViewGroup.LayoutParams.WRAP_CONTENT) as? ViewGroup.MarginLayoutParams)?.apply {
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
