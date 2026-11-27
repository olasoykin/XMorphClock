package com.drdisagree.xmorphclock.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.domain.editor.CustomFaces
import com.drdisagree.xmorphclock.domain.editor.ElementFactory
import com.drdisagree.xmorphclock.domain.editor.ItemGroups
import com.drdisagree.xmorphclock.domain.editor.LockTarget
import com.drdisagree.xmorphclock.domain.editor.WidgetGrids
import com.drdisagree.xmorphclock.domain.editor.WidgetZone
import com.drdisagree.xmorphclock.domain.model.ClockFace
import com.drdisagree.xmorphclock.domain.model.Composition
import com.drdisagree.xmorphclock.domain.model.CompositionItem
import com.drdisagree.xmorphclock.domain.model.ElementKind
import com.drdisagree.xmorphclock.domain.model.FaceAlign
import com.drdisagree.xmorphclock.domain.model.FaceGrid
import com.drdisagree.xmorphclock.domain.model.FaceKind
import com.drdisagree.xmorphclock.domain.model.FacePlacement
import com.drdisagree.xmorphclock.domain.model.FacePreset
import com.drdisagree.xmorphclock.domain.model.ItemBounds
import com.drdisagree.xmorphclock.domain.model.ItemState
import com.drdisagree.xmorphclock.domain.model.LockscreenAffordance
import com.drdisagree.xmorphclock.domain.model.ShortcutSlot
import com.drdisagree.xmorphclock.domain.model.StackNode
import com.drdisagree.xmorphclock.domain.model.StyleStatus
import com.drdisagree.xmorphclock.domain.usecase.ApplyClockPresetUseCase
import com.drdisagree.xmorphclock.domain.usecase.ComposePresetPreviewsUseCase
import com.drdisagree.xmorphclock.domain.usecase.CreateFaceUseCase
import com.drdisagree.xmorphclock.domain.usecase.DeletePresetUseCase
import com.drdisagree.xmorphclock.domain.usecase.DuplicatePresetUseCase
import com.drdisagree.xmorphclock.domain.usecase.ImportImageUseCase
import com.drdisagree.xmorphclock.domain.usecase.ObserveAppliedFaceUseCase
import com.drdisagree.xmorphclock.domain.usecase.ObserveAvailableAffordancesUseCase
import com.drdisagree.xmorphclock.domain.usecase.ObserveFacesUseCase
import com.drdisagree.xmorphclock.domain.usecase.ObserveLiveWallpaperUseCase
import com.drdisagree.xmorphclock.domain.usecase.ObserveModuleStatusUseCase
import com.drdisagree.xmorphclock.domain.usecase.ObserveSettingsUseCase
import com.drdisagree.xmorphclock.domain.usecase.ObserveStyleStatusUseCase
import com.drdisagree.xmorphclock.domain.usecase.SaveStylePresetUseCase
import com.drdisagree.xmorphclock.domain.usecase.SetLockscreenShortcutUseCase
import com.drdisagree.xmorphclock.domain.usecase.SyncLockscreenShortcutsUseCase
import com.drdisagree.xmorphclock.domain.usecase.UpdateCompositionUseCase
import com.drdisagree.xmorphclock.domain.usecase.UpdateStylePresetUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class HomeViewModel(
    observeFaces: ObserveFacesUseCase,
    private val observeSettings: ObserveSettingsUseCase,
    observeModuleStatus: ObserveModuleStatusUseCase,
    observeLiveWallpaper: ObserveLiveWallpaperUseCase,
    observeAppliedFace: ObserveAppliedFaceUseCase,
    composePresetPreviews: ComposePresetPreviewsUseCase,
    private val updateComposition: UpdateCompositionUseCase,
    private val createFace: CreateFaceUseCase,
    private val deletePreset: DeletePresetUseCase,
    private val duplicatePreset: DuplicatePresetUseCase,
    private val importImage: ImportImageUseCase,
    private val applyClockPreset: ApplyClockPresetUseCase,
    private val saveStylePreset: SaveStylePresetUseCase,
    private val updateStylePreset: UpdateStylePresetUseCase,
    observeStyleStatus: ObserveStyleStatusUseCase,
    private val setLockscreenShortcut: SetLockscreenShortcutUseCase,
    private val syncLockscreenShortcuts: SyncLockscreenShortcutsUseCase,
    observeAvailableAffordances: ObserveAvailableAffordancesUseCase
) : ViewModel() {

    private val previewMode = MutableStateFlow(PreviewMode.LARGE)
    private val editing = MutableStateFlow<Int?>(null)
    private val tab = MutableStateFlow(FaceTab.CLOCK)
    private val activeTarget = MutableStateFlow<LockTarget?>(null)
    private val sheetOpenState = MutableStateFlow(false)
    private val item = MutableStateFlow<Int?>(null)
    private val focus = MutableStateFlow<String?>(null)
    private val dragging = MutableStateFlow(false)
    private val shortcutSlot = MutableStateFlow(ShortcutSlot.LEFT)
    private val draftComposition = MutableStateFlow<Composition?>(null)
    private val editRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val notices = MutableSharedFlow<Int>(extraBufferCapacity = 2)

    val editEvents: SharedFlow<Unit> = editRequests.asSharedFlow()
    val noticeEvents: SharedFlow<Int> = notices.asSharedFlow()

    private val measuredSnapshots = HashMap<ItemState, MeasuredSnapshot>()
    private val measuredInlineWidths = HashMap<Int, Float>()
    private var screenTopByState: Map<ItemState, Float> = emptyMap()
    private var screenBottomByState: Map<ItemState, Float> = emptyMap()
    private var screenTop: Float = DEFAULT_TOP
    private var screenBottom: Float = DEFAULT_BOTTOM
    private var lastEditedItemState: ItemState = ItemState.LARGE
    private val autoFittedKeys = HashSet<String>()

    init {
        viewModelScope.launch { syncLockscreenShortcuts() }
    }

    val styleStatus: StateFlow<StyleStatus> = observeStyleStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), StyleStatus())

    val liveWallpaper: StateFlow<Boolean> = observeLiveWallpaper()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), false)

    private val catalog = combine(
        observeFaces(FaceKind.CLOCK),
        observeFaces(FaceKind.WEATHER),
        observeFaces(FaceKind.WIDGET)
    ) { clocks, weathers, widgets ->
        mapOf(FaceKind.CLOCK to clocks, FaceKind.WEATHER to weathers, FaceKind.WIDGET to widgets)
    }

    private val local = combine(
        combine(previewMode, editing, tab, sheetOpenState) { mode, style, selectedTab, isSheetOpen ->
            LocalMode(mode, style, selectedTab, isSheetOpen)
        },
        combine(activeTarget, item, focus, dragging, shortcutSlot) { target, id, layer, isDragging, slot ->
            LocalFocus(target, id, layer, isDragging, slot)
        }
    ) { localMode, localFocus ->
        HomeLocalState(
            mode = localMode.mode,
            editing = localMode.editing,
            tab = localMode.tab,
            target = localFocus.target,
            item = localFocus.item,
            focus = localFocus.focus,
            dragging = localFocus.dragging,
            shortcutSlot = localFocus.shortcutSlot,
            sheetOpen = localMode.sheetOpen
        )
    }

    private val settingsState = observeSettings().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val effective: StateFlow<Composition?> = combine(
        settingsState.map { it?.composition },
        draftComposition
    ) { persisted, live -> live ?: persisted }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val previews: StateFlow<Map<Int, ClockFace>> = catalog
        .map { composePresetPreviews(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val uiState: StateFlow<HomeUiState> = combine(
        combine(catalog, previews) { faces, composed -> faces to composed },
        combine(settingsState.filterNotNull(), observeAvailableAffordances()) { settings, affordances -> settings to affordances },
        observeModuleStatus(),
        observeAppliedFace(draft = effective),
        local
    ) { (faces, composed), (settings, affordances), status, preview, current ->
        val displayedComposition = draftComposition.value ?: settings.composition
        HomeUiState(
            tab = current.tab,
            faces = faces,
            presetPreviews = composed,
            clockStyle = settings.faceStyle,
            composition = displayedComposition,
            preview = preview,
            clockEnabled = settings.clockEnabled,
            moduleStatus = status,
            previewMode = current.mode,
            editingStyle = current.editing,
            activeTarget = current.target,
            selectedItem = current.item,
            focusedLayer = current.focus,
            dragging = current.dragging,
            availableAffordances = affordances,
            shortcutLeft = resolveAffordance(settings.shortcutLeft, affordances),
            shortcutRight = resolveAffordance(settings.shortcutRight, affordances),
            activeShortcutSlot = current.shortcutSlot,
            sheetOpen = current.sheetOpen
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), HomeUiState())

    private fun resolveAffordance(id: String, available: List<LockscreenAffordance>): LockscreenAffordance =
        available.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: LockscreenAffordance(id = id)

    fun onTabChange(selected: FaceTab) {
        tab.value = selected
    }

    fun onGestureStart(target: LockTarget) {
        dragging.value = true
        if (target == LockTarget.CLOCK) {
            val state = uiState.value
            markAutoFitted(draftComposition.value ?: state.composition, state.clockStyle, currentItemState(state))
        }
        onTargetSelected(target = target, slot = null, openSheet = false)
    }

    fun onTargetSelected(
        target: LockTarget?,
        slot: ShortcutSlot? = null,
        openSheet: Boolean = target != null
    ) {
        if (target != null && activeTarget.value == target && !openSheet) {
            sheetOpenState.value = false
            focus.value = null
            return
        }
        val openingShortcuts = target == LockTarget.SHORTCUTS && openSheet &&
            (!sheetOpenState.value || activeTarget.value != LockTarget.SHORTCUTS)
        activeTarget.value = target
        sheetOpenState.value = openSheet && target != null
        focus.value = null
        slot?.let { shortcutSlot.value = it }
        val state = uiState.value
        when (target) {
            LockTarget.CLOCK -> {
                tab.value = FaceTab.CLOCK
                item.value = state.composition.clockItem?.id
            }

            LockTarget.WIDGET_GRID -> {
                tab.value = FaceTab.WIDGETS
                item.value = state.gridWidgets.firstOrNull()?.id
            }

            LockTarget.INLINE_GRID -> {
                tab.value = FaceTab.WIDGETS
                item.value = state.inlineWidgets.firstOrNull()?.id
            }

            LockTarget.SHORTCUTS -> {
                item.value = null
                if (openingShortcuts) {
                    viewModelScope.launch { syncLockscreenShortcuts() }
                }
            }

            null -> item.value = null
        }
    }

    fun onShortcutSlotSelected(slot: ShortcutSlot) {
        shortcutSlot.value = slot
    }

    fun onShortcutSelected(slot: ShortcutSlot, affordanceId: String) {
        viewModelScope.launch { setLockscreenShortcut(slot, affordanceId) }
    }

    fun onItemSelected(id: Int?) {
        item.value = id
        focus.value = null
        val state = uiState.value
        val selected = id?.let { state.composition.item(it) }
        if (selected == null) {
            activeTarget.value = null
            return
        }
        activeTarget.value = when {
            selected.clock -> LockTarget.CLOCK
            state.faceOf(selected.style)?.let(WidgetGrids::zoneOf) == WidgetZone.INLINE_1X2 -> LockTarget.INLINE_GRID
            else -> LockTarget.WIDGET_GRID
        }
        tab.value = if (selected.clock) FaceTab.CLOCK else FaceTab.WIDGETS
    }

    fun onLayerFocused(id: Int, layer: String?) {
        item.value = id
        focus.value = layer
    }

    fun onItemsMeasured(
        state: ItemState,
        bounds: Map<Int, ItemBounds>,
        minY: Float,
        maxY: Float,
        minYByState: Map<ItemState, Float> = emptyMap(),
        maxYByState: Map<ItemState, Float> = emptyMap()
    ) {
        if (minY.isFinite()) screenTop = minY
        if (maxY > 0f) screenBottom = maxY
        if (minYByState.isNotEmpty()) screenTopByState = minYByState
        if (maxYByState.isNotEmpty()) screenBottomByState = maxYByState
        val current = uiState.value
        val clock = current.composition.clockItem
        measuredSnapshots[state] = MeasuredSnapshot(
            clockStyle = current.clockStyle,
            clockPlacement = clock?.placement(state),
            clockScale = clock?.scale(state) ?: 1f,
            clockWidthStretch = clock?.widthStretch(state) ?: 1f,
            clockHeightStretch = clock?.heightStretch(state) ?: 1f,
            bounds = bounds,
            minY = minY,
            maxY = maxY
        )
        var inlineWidthChanged = false
        current.inlineWidgets.forEach { widget ->
            val measured = bounds[widget.id] ?: return@forEach
            val w = (measured.right - measured.left).takeIf { it.isFinite() && it > 0.02f } ?: return@forEach
            val prev = measuredInlineWidths[widget.id]
            if (prev == null || kotlin.math.abs(prev - w) > 0.015f) {
                inlineWidthChanged = true
            }
            measuredInlineWidths[widget.id] = w
        }
        if (inlineWidthChanged && draftComposition.value == null && !dragging.value && current.inlineWidgets.size > 1) {
            rebalanceInlineForMeasuredWidths(state, current)
        }
        val clockFace = current.clockPreset?.face
        val measuredClock = clock?.id?.let { bounds[it] }
        if (clockFace != null &&
            measuredClock != null &&
            draftComposition.value == null &&
            !dragging.value &&
            (state == ItemState.LARGE || state == ItemState.SMALL)
        ) {
            val key = autoFitKey(current.composition, current.clockStyle, state)
            if (key !in autoFittedKeys) {
                val stateMinY = minYOf(state)
                val stateMaxY = maxYOf(state)
                val maxBottom = (stateMaxY - SCREEN_EDGE).coerceAtLeast(stateMinY + 0.12f)
                val overflowsScreen = measuredClock.left < SCREEN_EDGE - 0.008f ||
                    measuredClock.right > 1f - SCREEN_EDGE + 0.008f ||
                    measuredClock.top < stateMinY - 0.008f ||
                    measuredClock.bottom > maxBottom + 0.008f
                val collides = overflowsScreen || WidgetGrids.hasAnyCollision(
                    composition = current.composition,
                    clockFace = clockFace,
                    faceOf = current::faceOf,
                    itemState = state,
                    maxY = stateMaxY,
                    clockBoundsOverride = measuredClock,
                    measuredInlineWidths = measuredInlineWidths,
                    minY = stateMinY
                )
                if (collides) {
                    autoFittedKeys.add(key)
                    autoFitClockForMeasuredCollision(state, current, clockFace, measuredClock)
                }
            }
        }
    }

    private fun autoFitClockForMeasuredCollision(
        itemState: ItemState,
        snapshotState: HomeUiState,
        clockFace: ClockFace,
        measuredClock: ItemBounds
    ) {
        val widths = measuredInlineWidths.toMap()
        val largeMinY = minYOf(ItemState.LARGE)
        val smallMinY = minYOf(ItemState.SMALL)
        val largeMaxY = maxYOf(ItemState.LARGE)
        val smallMaxY = maxYOf(ItemState.SMALL)
        viewModelScope.launch {
            updateComposition { current ->
                val origItem = current.clockItem
                val fitted = WidgetGrids.fitClockScaleForComposition(
                    composition = current,
                    clockFace = clockFace,
                    faceOf = snapshotState::faceOf,
                    largeMaxY = largeMaxY,
                    smallMaxY = smallMaxY,
                    largeClockBounds = if (itemState == ItemState.LARGE) measuredClock else null,
                    smallClockBounds = if (itemState == ItemState.SMALL) measuredClock else null,
                    measuredInlineWidths = widths,
                    largeMinY = largeMinY,
                    smallMinY = smallMinY
                )
                val fittedItem = fitted.clockItem
                val projectedBounds = if (origItem != null && fittedItem != null) {
                    val sx = (fittedItem.scale(itemState) * fittedItem.widthStretch(itemState)) /
                        (origItem.scale(itemState) * origItem.widthStretch(itemState)).coerceAtLeast(0.01f)
                    val sy = (fittedItem.scale(itemState) * fittedItem.heightStretch(itemState)) /
                        (origItem.scale(itemState) * origItem.heightStretch(itemState)).coerceAtLeast(0.01f)
                    val defaultPlacement = when (itemState) {
                        ItemState.LARGE -> clockFace.large.placement
                        ItemState.SMALL -> clockFace.small.placement
                        ItemState.AOD_LARGE -> (clockFace.aodLarge ?: clockFace.large).placement
                        ItemState.AOD_SMALL -> (clockFace.aodSmall ?: clockFace.small).placement
                    }
                    val origP = origItem.placement(itemState) ?: defaultPlacement
                    val fittedP = fittedItem.placement(itemState) ?: origP
                    val anchor = fittedP.anchor
                    val newW = measuredClock.width * sx
                    val newH = measuredClock.height * sy
                    val shiftX = fittedP.x - origP.x
                    val shiftY = fittedP.y - origP.y
                    val newLeft = when (anchor) {
                        FaceAlign.START -> measuredClock.left
                        FaceAlign.CENTER -> (measuredClock.left + measuredClock.right) * 0.5f - newW * 0.5f
                        FaceAlign.END -> measuredClock.right - newW
                    } + shiftX
                    ItemBounds(
                        left = newLeft,
                        top = measuredClock.top + shiftY,
                        right = newLeft + newW,
                        bottom = measuredClock.top + shiftY + newH
                    )
                } else measuredClock
                WidgetGrids.reconcileWidgetsForClock(
                    composition = fitted,
                    clockFace = clockFace,
                    faceOf = snapshotState::faceOf,
                    largeMaxY = largeMaxY,
                    smallMaxY = smallMaxY,
                    largeClockBounds = if (itemState == ItemState.LARGE) projectedBounds else null,
                    smallClockBounds = if (itemState == ItemState.SMALL) projectedBounds else null,
                    measuredInlineWidths = widths,
                    largeMinY = largeMinY,
                    smallMinY = smallMinY
                )
            }
        }
    }

    private fun rebalanceInlineForMeasuredWidths(itemState: ItemState, snapshotState: HomeUiState) {
        val widthsSnapshot = measuredInlineWidths.toMap()
        val minY = minYOf(itemState)
        val maxY = maxYOf(itemState)
        viewModelScope.launch {
            updateComposition { current ->
                if (WidgetGrids.inlineItems(current, snapshotState::faceOf).size <= 1) {
                    current
                } else {
                    val clockBounds = clockBoundsFor(snapshotState, current, itemState)
                    val clockBottom = clockBounds?.bottom ?: clockBottomY(snapshotState, itemState)
                    val neededRows = WidgetGrids.minRowsForInline(current, snapshotState::faceOf, widthsSnapshot)
                    val updated = if (neededRows > current.inlineRows) current.copy(inlineRows = neededRows) else current
                    if (!updated.inlineDocked && neededRows <= current.inlineRows) {
                        WidgetGrids.repackInlineHorizontally(
                            composition = updated,
                            itemState = itemState,
                            faceOf = snapshotState::faceOf,
                            measuredInlineWidths = widthsSnapshot,
                            centerHorizontally = false
                        )
                    } else {
                        WidgetGrids.layoutZone(
                            composition = updated,
                            zone = WidgetZone.INLINE_1X2,
                            itemState = itemState,
                            faceOf = snapshotState::faceOf,
                            clockBottomY = clockBottom,
                            maxY = maxY,
                            centerHorizontally = updated.inlineDocked,
                            clockBounds = clockBounds,
                            widgetsAbove = updated.widgetsAbove,
                            measuredInlineWidths = widthsSnapshot,
                            preserveExistingSide = true,
                            minY = minY
                        )
                    }
                }
            }
        }
    }

    fun onTargetDrag(target: LockTarget, itemState: ItemState, dx: Float, dy: Float) {
        onTargetsDrag(itemState, mapOf(target to (dx to dy)))
    }

    fun onTargetsDrag(
        itemState: ItemState,
        deltas: Map<LockTarget, Pair<Float, Float>>,
        gridDocked: Boolean? = null,
        inlineDocked: Boolean? = null
    ) {
        lastEditedItemState = itemState
        val state = uiState.value
        val initial = draftComposition.value ?: state.composition
        var current = if (gridDocked != null || inlineDocked != null) {
            initial.copy(
                gridDocked = gridDocked ?: initial.gridDocked,
                inlineDocked = inlineDocked ?: initial.inlineDocked
            )
        } else {
            initial
        }
        var changed = (gridDocked != null && gridDocked != initial.gridDocked) ||
            (inlineDocked != null && inlineDocked != initial.inlineDocked)
        deltas.forEach { (target, step) ->
            val (dx, dy) = step
            if (dx == 0f && dy == 0f) return@forEach
            val ids = when (target) {
                LockTarget.CLOCK -> setOfNotNull(current.clockItem?.id)
                LockTarget.WIDGET_GRID -> WidgetGrids.gridItems(current, state::faceOf).mapTo(HashSet()) { it.id }
                LockTarget.INLINE_GRID -> WidgetGrids.inlineItems(current, state::faceOf).mapTo(HashSet()) { it.id }
                LockTarget.SHORTCUTS -> emptySet()
            }
            if (ids.isEmpty()) return@forEach
            current = WidgetGrids.applyMovedItems(current, ids, itemState, dx, dy) { targetItem ->
                val face = if (targetItem.clock) state.clockPreset?.face else state.faceOf(targetItem.style)
                val isLarge = itemState == ItemState.LARGE || itemState == ItemState.AOD_LARGE
                (if (isLarge) face?.large?.placement else face?.small?.placement) ?: FacePlacement()
            }
            changed = true
        }
        if (changed) {
            dragging.value = true
            sheetOpenState.value = false
            draftComposition.value = current
        }
    }

    fun onTargetDragEnd() {
        val pending = draftComposition.value
        val editedState = lastEditedItemState
        val target = activeTarget.value
        dragging.value = false
        if (pending == null) return
        val state = uiState.value
        val clockBounds = clockBoundsFor(state, pending, editedState)
        var synced = WidgetGrids.syncWidgetsAboveFlag(
            composition = pending,
            itemState = editedState,
            faceOf = state::faceOf,
            clockBounds = clockBounds,
            preferredTarget = target,
            measuredInlineWidths = measuredInlineWidths.toMap()
        )
        if (target == LockTarget.CLOCK || target == LockTarget.WIDGET_GRID || target == LockTarget.INLINE_GRID) {
            synced = WidgetGrids.trimEmptyWidgetRowsToFit(
                composition = synced,
                itemState = editedState,
                faceOf = state::faceOf,
                clockBounds = clockBounds,
                clockFace = state.clockPreset?.face,
                minY = minYOf(editedState),
                maxY = maxYOf(editedState),
                measuredInlineWidths = measuredInlineWidths.toMap()
            )
        }
        if (target == LockTarget.CLOCK) {
            markAutoFitted(synced, state.clockStyle, editedState)
        }
        viewModelScope.launch {
            updateComposition { synced }
            draftComposition.value = null
        }
    }

    fun onClockTransform(
        itemState: ItemState,
        scale: Float,
        widthStretch: Float,
        heightStretch: Float,
        placement: FacePlacement? = null
    ) {
        lastEditedItemState = itemState
        val state = uiState.value
        val initial = draftComposition.value ?: state.composition
        val clockItem = initial.clockItem ?: return
        val isAnalog = state.clockPreset?.face?.isAnalog == true
        val targetScale = scale.coerceIn(MIN_CLOCK_SCALE, MAX_CLOCK_SCALE)
        val targetWidth = if (isAnalog) 1f else widthStretch.coerceIn(MIN_CLOCK_STRETCH, MAX_CLOCK_STRETCH)
        val targetHeight = if (isAnalog) 1f else heightStretch.coerceIn(MIN_CLOCK_STRETCH, MAX_CLOCK_STRETCH)

        var updatedClock = clockItem.withTransform(itemState, targetScale, targetWidth, targetHeight)
        if (placement != null) {
            updatedClock = updatedClock.with(itemState, placement)
        }
        val next = initial.copy(
            items = initial.items.map { if (it.id == updatedClock.id) updatedClock else it }
        )
        markAutoFitted(next, state.clockStyle, itemState)
        dragging.value = true
        sheetOpenState.value = false
        draftComposition.value = next
    }

    fun onGridSizeChange(itemState: ItemState, cols: Int, rows: Int) {
        lastEditedItemState = itemState
        autoFittedKeys.clear()
        val state = uiState.value
        val initial = draftComposition.value ?: state.composition
        val widths = measuredInlineWidths.toMap()
        val minY = minYOf(itemState)
        val maxY = maxYOf(itemState)
        val clockFace = state.clockPreset?.face
        val rawClock = clockBoundsFor(state, initial, itemState)
        val clockFallback = clockBottomY(state, itemState)

        val minCols = WidgetGrids.minColsForGrid(initial, state::faceOf)
        val desiredCols = cols.coerceIn(minCols, WidgetGrids.MAX_GRID_COLS)
        var chosenCols = initial.gridCols.coerceIn(minCols, WidgetGrids.MAX_GRID_COLS)
        if (desiredCols != chosenCols) {
            val step = if (desiredCols > chosenCols) -1 else 1
            var candidateCol = desiredCols
            while (true) {
                val minRowsForCandidate = WidgetGrids.minRowsForGrid(initial, candidateCol, state::faceOf)
                val candidateRows = maxOf(initial.gridRows, minRowsForCandidate)
                val fitted = WidgetGrids.layoutWithReservedGridSize(
                    composition = initial,
                    cols = candidateCol,
                    rows = candidateRows,
                    itemState = itemState,
                    faceOf = state::faceOf,
                    clockBounds = rawClock,
                    clockFace = clockFace,
                    minY = minY,
                    maxY = maxY,
                    measuredInlineWidths = widths,
                    clockBottomFallback = clockFallback
                )
                if (fitted != null) {
                    chosenCols = candidateCol
                    break
                }
                if (candidateCol == chosenCols) break
                candidateCol += step
            }
        }

        val minRows = WidgetGrids.minRowsForGrid(initial, chosenCols, state::faceOf)
        val maxRows = WidgetGrids.maxFittingGridRows(
            composition = initial,
            faceOf = state::faceOf,
            maxY = maxY,
            clockBounds = rawClock,
            clockFace = clockFace,
            itemState = itemState,
            measuredInlineWidths = widths,
            minY = minY,
            cols = chosenCols
        )
        val desiredRows = rows.coerceIn(minRows, maxOf(minRows, maxRows))
        val baseRowsForCols = maxOf(initial.gridRows, minRows)

        var next: Composition? = null
        if (desiredRows > baseRowsForCols) {
            var candidateRows = desiredRows
            while (candidateRows > baseRowsForCols) {
                val fitted = WidgetGrids.layoutWithReservedGridSize(
                    composition = initial,
                    cols = chosenCols,
                    rows = candidateRows,
                    itemState = itemState,
                    faceOf = state::faceOf,
                    clockBounds = rawClock,
                    clockFace = clockFace,
                    minY = minY,
                    maxY = maxY,
                    measuredInlineWidths = widths,
                    clockBottomFallback = clockFallback
                )
                if (fitted != null) {
                    next = fitted
                    break
                }
                candidateRows--
            }
        }

        if (next == null) {
            val fallbackRows = minOf(desiredRows, baseRowsForCols).coerceAtLeast(minRows)
            if (chosenCols == initial.gridCols && fallbackRows == initial.gridRows) return
            next = WidgetGrids.layoutWithReservedGridSize(
                composition = initial,
                cols = chosenCols,
                rows = fallbackRows,
                itemState = itemState,
                faceOf = state::faceOf,
                clockBounds = rawClock,
                clockFace = clockFace,
                minY = minY,
                maxY = maxY,
                measuredInlineWidths = widths,
                clockBottomFallback = clockFallback
            )
            if (next == null && fallbackRows < initial.gridRows) {
                var shrunk = initial.copy(gridCols = chosenCols, gridRows = fallbackRows)
                val (cleared, clockBounds) = WidgetGrids.ensureClockClearanceForWidgets(
                    composition = shrunk,
                    itemState = itemState,
                    faceOf = state::faceOf,
                    clockBounds = rawClock,
                    maxY = maxY,
                    measuredInlineWidths = widths,
                    clockFace = clockFace,
                    minY = minY,
                    reserveEmptyRows = true
                )
                shrunk = cleared
                val cb = clockBounds?.bottom ?: clockFallback
                if (WidgetGrids.gridItems(shrunk, state::faceOf).isNotEmpty()) {
                    shrunk = WidgetGrids.layoutZone(
                        composition = shrunk,
                        zone = WidgetZone.GRID_1X4,
                        itemState = itemState,
                        faceOf = state::faceOf,
                        clockBottomY = cb,
                        maxY = maxY,
                        centerHorizontally = shrunk.gridDocked,
                        clockBounds = clockBounds,
                        widgetsAbove = shrunk.widgetsAbove,
                        measuredInlineWidths = widths,
                        preserveExistingSide = true,
                        minY = minY,
                        reserveEmptyRows = true
                    )
                    if (WidgetGrids.inlineItems(shrunk, state::faceOf).isNotEmpty() && shrunk.inlineDocked) {
                        shrunk = WidgetGrids.layoutZone(
                            composition = shrunk,
                            zone = WidgetZone.INLINE_1X2,
                            itemState = itemState,
                            faceOf = state::faceOf,
                            clockBottomY = cb,
                            maxY = maxY,
                            centerHorizontally = shrunk.inlineDocked,
                            clockBounds = clockBounds,
                            widgetsAbove = shrunk.widgetsAbove,
                            measuredInlineWidths = widths,
                            preserveExistingSide = true,
                            minY = minY,
                            reserveEmptyRows = true
                        )
                    }
                }
                next = shrunk
            }
            if (next == null) return
        }

        dragging.value = true
        sheetOpenState.value = false
        draftComposition.value = next
    }

    fun onInlineRowsChange(itemState: ItemState, rows: Int) {
        lastEditedItemState = itemState
        autoFittedKeys.clear()
        val state = uiState.value
        val initial = draftComposition.value ?: state.composition
        val widths = measuredInlineWidths.toMap()
        val minY = minYOf(itemState)
        val maxY = maxYOf(itemState)
        val clockFace = state.clockPreset?.face
        val rawClock = clockBoundsFor(state, initial, itemState)
        val clockFallback = clockBottomY(state, itemState)
        val minRows = WidgetGrids.minRowsForInline(initial, state::faceOf, widths)
        val maxRows = WidgetGrids.maxFittingInlineRows(
            composition = initial,
            faceOf = state::faceOf,
            maxY = maxY,
            measuredWidths = widths,
            clockBounds = rawClock,
            clockFace = clockFace,
            itemState = itemState,
            minY = minY
        )
        val desiredRows = rows.coerceIn(minRows, maxOf(minRows, maxRows))
        if (desiredRows == initial.inlineRows) return

        var next: Composition? = null
        if (desiredRows > initial.inlineRows) {
            var candidateRows = desiredRows
            while (candidateRows > initial.inlineRows) {
                val fitted = WidgetGrids.layoutWithReservedInlineRows(
                    composition = initial,
                    rows = candidateRows,
                    itemState = itemState,
                    faceOf = state::faceOf,
                    clockBounds = rawClock,
                    clockFace = clockFace,
                    minY = minY,
                    maxY = maxY,
                    measuredInlineWidths = widths,
                    clockBottomFallback = clockFallback
                )
                if (fitted != null) {
                    next = fitted
                    break
                }
                candidateRows--
            }
            if (next == null) return
        } else {
            next = WidgetGrids.layoutWithReservedInlineRows(
                composition = initial,
                rows = desiredRows,
                itemState = itemState,
                faceOf = state::faceOf,
                clockBounds = rawClock,
                clockFace = clockFace,
                minY = minY,
                maxY = maxY,
                measuredInlineWidths = widths,
                clockBottomFallback = clockFallback
            ) ?: run {
                var shrunk = initial.copy(inlineRows = desiredRows)
                val (cleared, clockBounds) = WidgetGrids.ensureClockClearanceForWidgets(
                    composition = shrunk,
                    itemState = itemState,
                    faceOf = state::faceOf,
                    clockBounds = rawClock,
                    maxY = maxY,
                    measuredInlineWidths = widths,
                    clockFace = clockFace,
                    minY = minY,
                    reserveEmptyRows = true
                )
                shrunk = cleared
                val cb = clockBounds?.bottom ?: clockFallback
                if (WidgetGrids.inlineItems(shrunk, state::faceOf).isNotEmpty()) {
                    shrunk = WidgetGrids.layoutZone(
                        composition = shrunk,
                        zone = WidgetZone.INLINE_1X2,
                        itemState = itemState,
                        faceOf = state::faceOf,
                        clockBottomY = cb,
                        maxY = maxY,
                        centerHorizontally = shrunk.inlineDocked,
                        clockBounds = clockBounds,
                        widgetsAbove = shrunk.widgetsAbove,
                        measuredInlineWidths = widths,
                        preserveExistingSide = true,
                        minY = minY,
                        reserveEmptyRows = true
                    )
                }
                shrunk
            }
        }
        dragging.value = true
        sheetOpenState.value = false
        draftComposition.value = next
    }

    fun onToggleWidgetsPosition() {
        autoFittedKeys.clear()
        val state = uiState.value
        val widths = measuredInlineWidths.toMap()
        val activeState = currentItemState(state)
        val activeClock = clockBoundsFor(state, state.composition, activeState)
        val currentlyAbove = WidgetGrids.areWidgetsAboveClock(
            composition = state.composition,
            itemState = activeState,
            faceOf = state::faceOf,
            clockBounds = activeClock,
            preferredTarget = state.activeTarget,
            measuredInlineWidths = widths
        ) ?: state.composition.widgetsAbove
        val nextAbove = !currentlyAbove
        val largeClock = clockBoundsOf(state, ItemState.LARGE)
        val smallClock = clockBoundsOf(state, ItemState.SMALL)
        val aodLargeClock = clockBoundsOf(state, ItemState.AOD_LARGE)
        val aodSmallClock = clockBoundsOf(state, ItemState.AOD_SMALL)
        val largeMinY = minYOf(ItemState.LARGE)
        val smallMinY = minYOf(ItemState.SMALL)
        val aodLargeMinY = minYOf(ItemState.AOD_LARGE)
        val aodSmallMinY = minYOf(ItemState.AOD_SMALL)
        val largeMaxY = maxYOf(ItemState.LARGE)
        val smallMaxY = maxYOf(ItemState.SMALL)
        val aodLargeMaxY = maxYOf(ItemState.AOD_LARGE)
        val aodSmallMaxY = maxYOf(ItemState.AOD_SMALL)
        val hasClockAodLarge = state.clockPreset?.face?.aodLarge != null
        val hasClockAodSmall = state.clockPreset?.face?.aodSmall != null
        viewModelScope.launch {
            updateComposition { current ->
                val hasCustomAodLarge = hasClockAodLarge || current.items.any { it.aodLarge != null }
                val hasCustomAodSmall = hasClockAodSmall || current.items.any { it.aodSmall != null }
                var next = current.copy(widgetsAbove = nextAbove, gridDocked = true, inlineDocked = true)
                val clockFaceForFlip = state.clockPreset?.face
                next = WidgetGrids.flipWidgetsPosition(
                    composition = next,
                    widgetsAbove = nextAbove,
                    itemState = ItemState.LARGE,
                    faceOf = state::faceOf,
                    clockBounds = largeClock,
                    maxY = largeMaxY,
                    measuredInlineWidths = widths,
                    clockFace = clockFaceForFlip,
                    minY = largeMinY
                )
                next = WidgetGrids.flipWidgetsPosition(
                    composition = next,
                    widgetsAbove = nextAbove,
                    itemState = ItemState.SMALL,
                    faceOf = state::faceOf,
                    clockBounds = smallClock,
                    maxY = smallMaxY,
                    measuredInlineWidths = widths,
                    clockFace = clockFaceForFlip,
                    minY = smallMinY
                )
                if (hasCustomAodLarge) {
                    next = WidgetGrids.flipWidgetsPosition(
                        composition = next,
                        widgetsAbove = nextAbove,
                        itemState = ItemState.AOD_LARGE,
                        faceOf = state::faceOf,
                        clockBounds = aodLargeClock,
                        maxY = aodLargeMaxY,
                        measuredInlineWidths = widths,
                        clockFace = clockFaceForFlip,
                        minY = aodLargeMinY
                    )
                }
                if (hasCustomAodSmall) {
                    next = WidgetGrids.flipWidgetsPosition(
                        composition = next,
                        widgetsAbove = nextAbove,
                        itemState = ItemState.AOD_SMALL,
                        faceOf = state::faceOf,
                        clockBounds = aodSmallClock,
                        maxY = aodSmallMaxY,
                        measuredInlineWidths = widths,
                        clockFace = clockFaceForFlip,
                        minY = aodSmallMinY
                    )
                }
                next
            }
        }
    }

    fun onFaceSelected(style: Int) {
        val state = uiState.value
        val selected = FaceKind.of(style) ?: return
        autoFittedKeys.clear()
        viewModelScope.launch {
            if (selected == FaceKind.CLOCK) {
                applyClockPreset(
                    style = style,
                    largeMaxY = maxYOf(ItemState.LARGE),
                    smallMaxY = maxYOf(ItemState.SMALL),
                    largeMinY = minYOf(ItemState.LARGE),
                    smallMinY = minYOf(ItemState.SMALL)
                )
                return@launch
            }
            val face = state.faceOf(style) ?: return@launch
            val existing = state.composition.items.firstOrNull { !it.clock && it.style == style }
            if (existing != null) {
                removeWidgetInternal(existing.id, state)
                return@launch
            }
            val widths = measuredInlineWidths.toMap()
            val zone = WidgetGrids.zoneOf(face)
            val largeMinY = minYOf(ItemState.LARGE)
            val smallMinY = minYOf(ItemState.SMALL)
            val largeMaxY = maxYOf(ItemState.LARGE)
            val smallMaxY = maxYOf(ItemState.SMALL)
            val largeClock = clockBoundsOf(state, ItemState.LARGE)
            val smallClock = clockBoundsOf(state, ItemState.SMALL)
            if (!WidgetGrids.canAdd(
                    composition = state.composition,
                    face = face,
                    faceOf = state::faceOf,
                    maxY = largeMaxY,
                    measuredInlineWidths = widths,
                    clockBounds = largeClock,
                    clockFace = state.clockPreset?.face,
                    itemState = ItemState.LARGE,
                    minY = largeMinY
                )
            ) {
                notices.tryEmit(if (zone == WidgetZone.INLINE_1X2) R.string.inline_grid_full else R.string.widget_grid_full)
                return@launch
            }
            val newId = state.composition.nextId
            val largeClockBottom = largeClock?.bottom ?: clockBottomY(state, ItemState.LARGE)
            val smallClockBottom = smallClock?.bottom ?: clockBottomY(state, ItemState.SMALL)
            updateComposition { current ->
                val gridWasEmpty = WidgetGrids.gridItems(current, state::faceOf).isEmpty()
                val inlineWasEmpty = WidgetGrids.inlineItems(current, state::faceOf).isEmpty()
                var next = current.add(style, null, null).let { added ->
                    when {
                        zone == WidgetZone.GRID_1X4 && gridWasEmpty -> added.copy(gridDocked = true)
                        zone == WidgetZone.INLINE_1X2 && inlineWasEmpty -> added.copy(inlineDocked = true)
                        else -> added
                    }
                }
                if (zone == WidgetZone.INLINE_1X2) {
                    val neededRows = WidgetGrids.minRowsForInline(next, state::faceOf, widths)
                    if (neededRows > next.inlineRows) {
                        next = next.copy(inlineRows = neededRows)
                    }
                }
                val (clearedLarge, effectiveLargeClock) = WidgetGrids.ensureClockClearanceForWidgets(
                    composition = next,
                    itemState = ItemState.LARGE,
                    faceOf = state::faceOf,
                    clockBounds = largeClock,
                    maxY = largeMaxY,
                    measuredInlineWidths = widths,
                    clockFace = state.clockPreset?.face,
                    minY = largeMinY,
                    reserveEmptyRows = true
                )
                next = clearedLarge
                val (clearedSmall, effectiveSmallClock) = WidgetGrids.ensureClockClearanceForWidgets(
                    composition = next,
                    itemState = ItemState.SMALL,
                    faceOf = state::faceOf,
                    clockBounds = smallClock,
                    maxY = smallMaxY,
                    measuredInlineWidths = widths,
                    clockFace = state.clockPreset?.face,
                    minY = smallMinY,
                    reserveEmptyRows = true
                )
                next = clearedSmall
                val effLargeBottom = effectiveLargeClock?.bottom ?: largeClockBottom
                val effSmallBottom = effectiveSmallClock?.bottom ?: smallClockBottom

                if (zone == WidgetZone.INLINE_1X2 && WidgetGrids.gridItems(next, state::faceOf).isNotEmpty()) {
                    val gridIds = WidgetGrids.gridItems(next, state::faceOf).mapTo(HashSet()) { it.id }
                    if (next.gridDocked && largeClock != null && effectiveLargeClock != null) {
                        val shiftY = effectiveLargeClock.top - largeClock.top
                        if (shiftY != 0f) {
                            next = WidgetGrids.applyMovedItems(next, gridIds, ItemState.LARGE, 0f, shiftY) { FacePlacement() }
                        }
                    }
                    if (next.gridDocked && smallClock != null && effectiveSmallClock != null) {
                        val shiftY = effectiveSmallClock.top - smallClock.top
                        if (shiftY != 0f) {
                            next = WidgetGrids.applyMovedItems(next, gridIds, ItemState.SMALL, 0f, shiftY) { FacePlacement() }
                        }
                    }
                    next = WidgetGrids.layoutZone(
                        composition = next,
                        zone = WidgetZone.GRID_1X4,
                        itemState = ItemState.LARGE,
                        faceOf = state::faceOf,
                        clockBottomY = effLargeBottom,
                        maxY = largeMaxY,
                        centerHorizontally = next.gridDocked,
                        clockBounds = effectiveLargeClock,
                        widgetsAbove = next.widgetsAbove,
                        measuredInlineWidths = widths,
                        preserveExistingSide = true,
                        minY = largeMinY,
                        reserveEmptyRows = true
                    )
                    next = WidgetGrids.layoutZone(
                        composition = next,
                        zone = WidgetZone.GRID_1X4,
                        itemState = ItemState.SMALL,
                        faceOf = state::faceOf,
                        clockBottomY = effSmallBottom,
                        maxY = smallMaxY,
                        centerHorizontally = next.gridDocked,
                        clockBounds = effectiveSmallClock,
                        widgetsAbove = next.widgetsAbove,
                        measuredInlineWidths = widths,
                        preserveExistingSide = true,
                        minY = smallMinY,
                        reserveEmptyRows = true
                    )
                }

                next = WidgetGrids.layoutZone(
                    composition = next,
                    zone = zone,
                    itemState = ItemState.LARGE,
                    faceOf = state::faceOf,
                    clockBottomY = effLargeBottom,
                    maxY = largeMaxY,
                    centerHorizontally = true,
                    clockBounds = effectiveLargeClock,
                    widgetsAbove = next.widgetsAbove,
                    measuredInlineWidths = widths,
                    preserveExistingSide = true,
                    minY = largeMinY,
                    reserveEmptyRows = true
                )
                next = WidgetGrids.layoutZone(
                    composition = next,
                    zone = zone,
                    itemState = ItemState.SMALL,
                    faceOf = state::faceOf,
                    clockBottomY = effSmallBottom,
                    maxY = smallMaxY,
                    centerHorizontally = true,
                    clockBounds = effectiveSmallClock,
                    widgetsAbove = next.widgetsAbove,
                    measuredInlineWidths = widths,
                    preserveExistingSide = true,
                    minY = smallMinY,
                    reserveEmptyRows = true
                )
                if (zone == WidgetZone.GRID_1X4 && WidgetGrids.inlineItems(next, state::faceOf).isNotEmpty()) {
                    next = WidgetGrids.layoutZone(
                        composition = next,
                        zone = WidgetZone.INLINE_1X2,
                        itemState = ItemState.LARGE,
                        faceOf = state::faceOf,
                        clockBottomY = effLargeBottom,
                        maxY = largeMaxY,
                        centerHorizontally = next.inlineDocked,
                        clockBounds = effectiveLargeClock,
                        widgetsAbove = next.widgetsAbove,
                        measuredInlineWidths = widths,
                        preserveExistingSide = true,
                        minY = largeMinY,
                        reserveEmptyRows = true
                    )
                    next = WidgetGrids.layoutZone(
                        composition = next,
                        zone = WidgetZone.INLINE_1X2,
                        itemState = ItemState.SMALL,
                        faceOf = state::faceOf,
                        clockBottomY = effSmallBottom,
                        maxY = smallMaxY,
                        centerHorizontally = next.inlineDocked,
                        clockBounds = effectiveSmallClock,
                        widgetsAbove = next.widgetsAbove,
                        measuredInlineWidths = widths,
                        preserveExistingSide = true,
                        minY = smallMinY,
                        reserveEmptyRows = true
                    )
                }
                val clockFace = state.clockPreset?.face
                if (clockFace != null && (
                        WidgetGrids.hasAnyCollision(
                            composition = next,
                            clockFace = clockFace,
                            faceOf = state::faceOf,
                            itemState = ItemState.LARGE,
                            maxY = largeMaxY,
                            measuredInlineWidths = widths,
                            minY = largeMinY
                        ) || WidgetGrids.hasAnyCollision(
                            composition = next,
                            clockFace = clockFace,
                            faceOf = state::faceOf,
                            itemState = ItemState.SMALL,
                            maxY = smallMaxY,
                            measuredInlineWidths = widths,
                            minY = smallMinY
                        )
                    )
                ) {
                    next = WidgetGrids.fitClockScaleForComposition(
                        composition = next,
                        clockFace = clockFace,
                        faceOf = state::faceOf,
                        largeMaxY = largeMaxY,
                        smallMaxY = smallMaxY,
                        largeClockBounds = largeClock,
                        smallClockBounds = smallClock,
                        measuredInlineWidths = widths,
                        largeMinY = largeMinY,
                        smallMinY = smallMinY
                    )
                    next = WidgetGrids.reconcileWidgetsForClock(
                        composition = next,
                        clockFace = clockFace,
                        faceOf = state::faceOf,
                        largeMaxY = largeMaxY,
                        smallMaxY = smallMaxY,
                        measuredInlineWidths = widths,
                        largeMinY = largeMinY,
                        smallMinY = smallMinY
                    )
                }
                next
            }
            item.value = newId
            focus.value = null
        }
    }

    fun onRemoveWidget(itemId: Int) {
        val state = uiState.value
        viewModelScope.launch {
            removeWidgetInternal(itemId, state)
        }
    }

    private suspend fun removeWidgetInternal(itemId: Int, state: HomeUiState) {
        val targetItem = state.composition.item(itemId) ?: return
        autoFittedKeys.clear()
        measuredInlineWidths.remove(itemId)
        val widths = measuredInlineWidths.toMap()
        val face = state.faceOf(targetItem.style)
        val zone = face?.let(WidgetGrids::zoneOf) ?: WidgetZone.GRID_1X4
        val largeClock = clockBoundsOf(state, ItemState.LARGE)
        val smallClock = clockBoundsOf(state, ItemState.SMALL)
        val largeMinY = minYOf(ItemState.LARGE)
        val smallMinY = minYOf(ItemState.SMALL)
        val largeMaxY = maxYOf(ItemState.LARGE)
        val smallMaxY = maxYOf(ItemState.SMALL)
        val largeClockBottom = largeClock?.bottom ?: clockBottomY(state, ItemState.LARGE)
        val smallClockBottom = smallClock?.bottom ?: clockBottomY(state, ItemState.SMALL)
        updateComposition { current ->
            var next = current.remove(itemId)
            if (zone == WidgetZone.GRID_1X4 && WidgetGrids.gridItems(next, state::faceOf).isEmpty()) {
                next = next.copy(gridDocked = true, gridCols = FaceGrid.ROW_CELLS, gridRows = 1)
            } else if (zone == WidgetZone.INLINE_1X2 && WidgetGrids.inlineItems(next, state::faceOf).isEmpty()) {
                next = next.copy(inlineDocked = true, inlineRows = 1)
            }
            next = WidgetGrids.layoutZone(
                composition = next,
                zone = zone,
                itemState = ItemState.LARGE,
                faceOf = state::faceOf,
                clockBottomY = largeClockBottom,
                maxY = largeMaxY,
                centerHorizontally = true,
                clockBounds = largeClock,
                widgetsAbove = next.widgetsAbove,
                measuredInlineWidths = widths,
                preserveExistingSide = true,
                minY = largeMinY
            )
            next = WidgetGrids.layoutZone(
                composition = next,
                zone = zone,
                itemState = ItemState.SMALL,
                faceOf = state::faceOf,
                clockBottomY = smallClockBottom,
                maxY = smallMaxY,
                centerHorizontally = true,
                clockBounds = smallClock,
                widgetsAbove = next.widgetsAbove,
                measuredInlineWidths = widths,
                preserveExistingSide = true,
                minY = smallMinY
            )
            next
        }
        if (item.value == itemId) {
            item.value = null
            focus.value = null
        }
    }

    fun onAddElement(kind: ElementKind, name: String, text: String) {
        val state = uiState.value
        val inInline = activeTarget.value == LockTarget.INLINE_GRID
        val rawFace = if (kind == ElementKind.TEXT) CustomFaces.text(name, text) else CustomFaces.element(name, ElementFactory.create(kind, text))
        rawFace ?: return
        val remaining = (state.composition.gridCapacity - state.gridCellsUsed).coerceAtLeast(1)
        val face = if (inInline) {
            rawFace.copy(category = "inline")
        } else {
            val targetSpan = rawFace.span.coerceIn(1, minOf(remaining, state.composition.gridCols, 2))
            rawFace.copy(span = targetSpan)
        }
        if (!WidgetGrids.canAdd(
                composition = state.composition,
                face = face,
                faceOf = state::faceOf,
                maxY = maxYOf(ItemState.LARGE),
                measuredInlineWidths = measuredInlineWidths.toMap(),
                clockBounds = clockBoundsOf(state, ItemState.LARGE),
                clockFace = state.clockPreset?.face,
                itemState = ItemState.LARGE,
                minY = minYOf(ItemState.LARGE)
            )
        ) {
            notices.tryEmit(if (inInline) R.string.inline_grid_full else R.string.widget_grid_full)
            return
        }
        viewModelScope.launch { addCreated(createFace(FaceKind.WIDGET, face)) }
    }

    fun onAddImage(uri: String, name: String) {
        val state = uiState.value
        viewModelScope.launch {
            val image = importImage(uri) ?: return@launch
            val rawFace = CustomFaces.image(name, image)
            val remaining = (state.composition.gridCapacity - state.gridCellsUsed).coerceAtLeast(1)
            val fitted = rawFace.copy(span = rawFace.span.coerceIn(1, minOf(remaining, state.composition.gridCols, 2)))
            if (!WidgetGrids.canAdd(
                    composition = state.composition,
                    face = fitted,
                    faceOf = state::faceOf,
                    maxY = maxYOf(ItemState.LARGE),
                    measuredInlineWidths = measuredInlineWidths.toMap(),
                    clockBounds = clockBoundsOf(state, ItemState.LARGE),
                    clockFace = state.clockPreset?.face,
                    itemState = ItemState.LARGE,
                    minY = minYOf(ItemState.LARGE)
                )
            ) {
                notices.tryEmit(R.string.widget_grid_full)
                return@launch
            }
            addCreated(createFace(FaceKind.WIDGET, fitted))
        }
    }

    private suspend fun addCreated(style: Int) {
        withTimeoutOrNull(CREATE_TIMEOUT) { uiState.first { it.faceOf(style) != null } } ?: return
        onFaceSelected(style)
    }

    private fun autoFitKey(composition: Composition, clockStyle: Int, state: ItemState): String =
        buildString {
            append(clockStyle).append(':')
            append(state.name).append(':')
            composition.items.joinTo(this, separator = ",") { "${it.id}:${it.style}" }
        }

    private fun markAutoFitted(composition: Composition, clockStyle: Int, state: ItemState) {
        autoFittedKeys.add(autoFitKey(composition, clockStyle, state))
    }

    private fun minYOf(itemState: ItemState): Float =
        screenTopByState[itemState] ?: measuredSnapshots[itemState]?.minY?.takeIf { it.isFinite() } ?: screenTop

    private fun maxYOf(itemState: ItemState): Float =
        screenBottomByState[itemState] ?: measuredSnapshots[itemState]?.maxY?.takeIf { it > 0f } ?: screenBottom

    private fun currentItemState(state: HomeUiState): ItemState = when (state.previewMode) {
        PreviewMode.LARGE -> ItemState.LARGE
        PreviewMode.SMALL -> ItemState.SMALL
        PreviewMode.AOD -> lastEditedItemState.takeIf { it == ItemState.AOD_LARGE || it == ItemState.AOD_SMALL } ?: ItemState.AOD_LARGE
    }

    private fun clockBoundsOf(state: HomeUiState, itemState: ItemState): ItemBounds? =
        clockBoundsFor(state, state.composition, itemState)

    private fun clockBoundsFor(
        state: HomeUiState,
        composition: Composition,
        itemState: ItemState
    ): ItemBounds? {
        val clock = composition.clockItem ?: return null
        val snapshot = measuredSnapshots[itemState]
        if (snapshot != null &&
            snapshot.clockStyle == state.clockStyle &&
            snapshot.clockPlacement == clock.placement(itemState) &&
            snapshot.clockScale == clock.scale(itemState) &&
            snapshot.clockWidthStretch == clock.widthStretch(itemState) &&
            snapshot.clockHeightStretch == clock.heightStretch(itemState)
        ) {
            snapshot.bounds[clock.id]?.let { return it }
        }
        val face = state.clockPreset?.face
        if (face != null) {
            return WidgetGrids.estimateClockBounds(face, clock, itemState)
        }
        return estimate(state, clock, itemState)
    }

    private fun clockBottomY(state: HomeUiState, itemState: ItemState): Float {
        return clockBoundsOf(state, itemState)?.bottom
            ?: if (itemState == ItemState.LARGE) CLOCK_LARGE.second else CLOCK_SMALL.second
    }

    private fun estimate(state: HomeUiState, target: CompositionItem, itemState: ItemState): ItemBounds? {
        val face = (if (target.clock) state.clockPreset?.face else state.faceOf(target.style)) ?: return null
        if (target.clock) return WidgetGrids.estimateClockBounds(face, target, itemState)
        val placement = target.placement(itemState) ?: face.large.placement
        val (width, height) = sizeOf(face)
        return ItemBounds.of(placement, width, height)
    }

    private fun sizeOf(face: ClockFace): Pair<Float, Float> {
        val root = face.large as? StackNode
        val width = root?.width ?: FaceGrid.width(face.span.coerceIn(1, FaceGrid.ROW_CELLS))
        val height = root?.height ?: INLINE_HEIGHT
        return width to height
    }

    fun editTarget(overrideItemId: Int? = null): String? {
        val state = uiState.value
        if (overrideItemId != null) {
            val chosen = state.composition.item(overrideItemId)
            if (chosen != null) return ItemGroups.keyOfItem(chosen.id, chosen.clock)
        }
        state.focusedLayer?.let { return it }
        val selected = state.selectedItem?.let { state.composition.item(it) }
            ?: when (state.activeTarget) {
                LockTarget.CLOCK, LockTarget.SHORTCUTS, null -> state.composition.clockItem
                LockTarget.WIDGET_GRID -> state.gridWidgets.firstOrNull()
                LockTarget.INLINE_GRID -> state.inlineWidgets.firstOrNull()
            }
            ?: return null
        return ItemGroups.keyOfItem(selected.id, selected.clock)
    }

    fun onDuplicate(preset: FacePreset, name: String) {
        viewModelScope.launch { duplicatePreset(preset, name) }
    }

    fun saveAsPreset(name: String) {
        viewModelScope.launch { saveStylePreset(name) }
    }

    fun updatePreset(style: Int) {
        viewModelScope.launch { updateStylePreset(style) }
    }

    fun onDelete(preset: FacePreset) {
        viewModelScope.launch { deletePreset(preset) }
    }

    fun onPreviewModeChange(mode: PreviewMode) {
        previewMode.value = mode
    }

    fun onEdit(style: Int) {
        editing.value = style
    }

    fun onEditDone() {
        autoFittedKeys.clear()
        editing.value = null
    }

    private data class MeasuredSnapshot(
        val clockStyle: Int,
        val clockPlacement: FacePlacement?,
        val clockScale: Float,
        val clockWidthStretch: Float,
        val clockHeightStretch: Float,
        val bounds: Map<Int, ItemBounds>,
        val minY: Float,
        val maxY: Float
    )

    private data class LocalMode(
        val mode: PreviewMode,
        val editing: Int?,
        val tab: FaceTab,
        val sheetOpen: Boolean
    )

    private data class LocalFocus(
        val target: LockTarget?,
        val item: Int?,
        val focus: String?,
        val dragging: Boolean,
        val shortcutSlot: ShortcutSlot
    )
}

private const val STOP_TIMEOUT = 5_000L
private const val DEFAULT_TOP = -0.15f
private const val DEFAULT_BOTTOM = 1.9f
private const val INLINE_HEIGHT = 0.08f
private val CLOCK_LARGE = 0.9f to 0.45f
private val CLOCK_SMALL = 0.45f to 0.2f
private const val MIN_CLOCK_SCALE = 0.45f
private const val MAX_CLOCK_SCALE = 1.85f
private const val MIN_CLOCK_STRETCH = 0.45f
private const val MAX_CLOCK_STRETCH = 1.75f
private const val SCREEN_EDGE = 0.0225f

private const val CREATE_TIMEOUT = 3_000L
