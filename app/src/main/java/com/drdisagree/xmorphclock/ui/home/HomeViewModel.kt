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
    private var screenBottomByState: Map<ItemState, Float> = emptyMap()
    private var screenBottom: Float = DEFAULT_BOTTOM

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
        combine(previewMode, editing, tab) { mode, style, selectedTab -> Triple(mode, style, selectedTab) },
        combine(activeTarget, item, focus, dragging, shortcutSlot) { target, id, layer, isDragging, slot ->
            LocalFocus(target, id, layer, isDragging, slot)
        }
    ) { (mode, style, selectedTab), localFocus ->
        HomeLocalState(mode, style, selectedTab, localFocus.target, localFocus.item, localFocus.focus, localFocus.dragging, localFocus.shortcutSlot)
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
            activeShortcutSlot = current.shortcutSlot
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), HomeUiState())

    private fun resolveAffordance(id: String, available: List<LockscreenAffordance>): LockscreenAffordance =
        available.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: LockscreenAffordance(id = id)

    fun onTabChange(selected: FaceTab) {
        tab.value = selected
    }

    fun onTargetSelected(target: LockTarget?, slot: ShortcutSlot? = null) {
        val openingShortcuts = target == LockTarget.SHORTCUTS && activeTarget.value != LockTarget.SHORTCUTS
        activeTarget.value = target
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
        maxY: Float,
        maxYByState: Map<ItemState, Float> = emptyMap()
    ) {
        if (maxY > 0f) screenBottom = maxY
        if (maxYByState.isNotEmpty()) screenBottomByState = maxYByState
        val current = uiState.value
        val clock = current.composition.clockItem
        measuredSnapshots[state] = MeasuredSnapshot(
            clockStyle = current.clockStyle,
            clockPlacement = clock?.placement(state),
            clockScale = clock?.scale(state) ?: 1f,
            bounds = bounds,
            maxY = maxY
        )
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
        val state = uiState.value
        var current = draftComposition.value ?: state.composition
        if (gridDocked != null || inlineDocked != null) {
            current = current.copy(
                gridDocked = gridDocked ?: current.gridDocked,
                inlineDocked = inlineDocked ?: current.inlineDocked
            )
        }
        var changed = (gridDocked != null && gridDocked != state.composition.gridDocked) ||
            (inlineDocked != null && inlineDocked != state.composition.inlineDocked)
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
            draftComposition.value = current
        }
    }

    fun onTargetDragEnd() {
        val pending = draftComposition.value
        dragging.value = false
        if (pending == null) return
        viewModelScope.launch {
            updateComposition { pending }
            draftComposition.value = null
        }
    }

    fun onToggleWidgetsPosition() {
        val state = uiState.value
        val nextAbove = !state.composition.widgetsAbove
        val largeClock = clockBoundsOf(state, ItemState.LARGE)
        val smallClock = clockBoundsOf(state, ItemState.SMALL)
        val largeMaxY = maxYOf(ItemState.LARGE)
        val smallMaxY = maxYOf(ItemState.SMALL)
        viewModelScope.launch {
            updateComposition { current ->
                var next = current.copy(widgetsAbove = nextAbove, gridDocked = true, inlineDocked = true)
                next = WidgetGrids.flipWidgetsPosition(
                    composition = next,
                    widgetsAbove = nextAbove,
                    itemState = ItemState.LARGE,
                    faceOf = state::faceOf,
                    clockBounds = largeClock,
                    maxY = largeMaxY
                )
                next = WidgetGrids.flipWidgetsPosition(
                    composition = next,
                    widgetsAbove = nextAbove,
                    itemState = ItemState.SMALL,
                    faceOf = state::faceOf,
                    clockBounds = smallClock,
                    maxY = smallMaxY
                )
                next
            }
        }
    }

    fun onFaceSelected(style: Int) {
        val state = uiState.value
        val selected = FaceKind.of(style) ?: return
        viewModelScope.launch {
            if (selected == FaceKind.CLOCK) {
                applyClockPreset(
                    style = style,
                    largeMaxY = maxYOf(ItemState.LARGE),
                    smallMaxY = maxYOf(ItemState.SMALL)
                )
                return@launch
            }
            val face = state.faceOf(style) ?: return@launch
            val existing = state.composition.items.firstOrNull { !it.clock && it.style == style }
            if (existing != null) {
                removeWidgetInternal(existing.id, state)
                return@launch
            }
            val zone = WidgetGrids.zoneOf(face)
            if (!WidgetGrids.canAdd(state.composition, face, state::faceOf)) {
                notices.tryEmit(if (zone == WidgetZone.INLINE_1X2) R.string.inline_grid_full else R.string.widget_grid_full)
                return@launch
            }
            val newId = state.composition.nextId
            val largeClock = clockBoundsOf(state, ItemState.LARGE)
            val smallClock = clockBoundsOf(state, ItemState.SMALL)
            val largeMaxY = maxYOf(ItemState.LARGE)
            val smallMaxY = maxYOf(ItemState.SMALL)
            val largeClockBottom = largeClock?.bottom ?: clockBottomY(state, ItemState.LARGE)
            val smallClockBottom = smallClock?.bottom ?: clockBottomY(state, ItemState.SMALL)
            updateComposition { current ->
                var next = current.add(style, null, null)
                val (clearedLarge, effectiveLargeClock) = WidgetGrids.ensureClockClearanceForWidgets(
                    composition = next,
                    itemState = ItemState.LARGE,
                    faceOf = state::faceOf,
                    clockBounds = largeClock,
                    maxY = largeMaxY
                )
                next = clearedLarge
                val (clearedSmall, effectiveSmallClock) = WidgetGrids.ensureClockClearanceForWidgets(
                    composition = next,
                    itemState = ItemState.SMALL,
                    faceOf = state::faceOf,
                    clockBounds = smallClock,
                    maxY = smallMaxY
                )
                next = clearedSmall
                val effLargeBottom = effectiveLargeClock?.bottom ?: largeClockBottom
                val effSmallBottom = effectiveSmallClock?.bottom ?: smallClockBottom

                next = WidgetGrids.layoutZone(
                    composition = next,
                    zone = zone,
                    itemState = ItemState.LARGE,
                    faceOf = state::faceOf,
                    clockBottomY = effLargeBottom,
                    maxY = largeMaxY,
                    centerHorizontally = true,
                    clockBounds = effectiveLargeClock,
                    widgetsAbove = current.widgetsAbove
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
                    widgetsAbove = current.widgetsAbove
                )
                if (zone == WidgetZone.GRID_1X4 && WidgetGrids.inlineItems(next, state::faceOf).isNotEmpty()) {
                    next = WidgetGrids.layoutZone(
                        composition = next,
                        zone = WidgetZone.INLINE_1X2,
                        itemState = ItemState.LARGE,
                        faceOf = state::faceOf,
                        clockBottomY = effLargeBottom,
                        maxY = largeMaxY,
                        clockBounds = effectiveLargeClock,
                        widgetsAbove = current.widgetsAbove
                    )
                    next = WidgetGrids.layoutZone(
                        composition = next,
                        zone = WidgetZone.INLINE_1X2,
                        itemState = ItemState.SMALL,
                        faceOf = state::faceOf,
                        clockBottomY = effSmallBottom,
                        maxY = smallMaxY,
                        clockBounds = effectiveSmallClock,
                        widgetsAbove = current.widgetsAbove
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
        val face = state.faceOf(targetItem.style)
        val zone = face?.let(WidgetGrids::zoneOf) ?: WidgetZone.GRID_1X4
        val largeClock = clockBoundsOf(state, ItemState.LARGE)
        val smallClock = clockBoundsOf(state, ItemState.SMALL)
        val largeMaxY = maxYOf(ItemState.LARGE)
        val smallMaxY = maxYOf(ItemState.SMALL)
        val largeClockBottom = largeClock?.bottom ?: clockBottomY(state, ItemState.LARGE)
        val smallClockBottom = smallClock?.bottom ?: clockBottomY(state, ItemState.SMALL)
        updateComposition { current ->
            var next = current.remove(itemId)
            next = WidgetGrids.layoutZone(
                composition = next,
                zone = zone,
                itemState = ItemState.LARGE,
                faceOf = state::faceOf,
                clockBottomY = largeClockBottom,
                maxY = largeMaxY,
                centerHorizontally = true,
                clockBounds = largeClock,
                widgetsAbove = current.widgetsAbove
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
                widgetsAbove = current.widgetsAbove
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
        val remaining = (WidgetGrids.MAX_GRID_CELLS - state.gridCellsUsed).coerceAtLeast(1)
        val face = if (inInline) {
            rawFace.copy(category = "inline")
        } else {
            val targetSpan = rawFace.span.coerceIn(1, remaining.coerceAtMost(2))
            rawFace.copy(span = targetSpan)
        }
        if (!WidgetGrids.canAdd(state.composition, face, state::faceOf)) {
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
            val remaining = (WidgetGrids.MAX_GRID_CELLS - state.gridCellsUsed).coerceAtLeast(1)
            val fitted = rawFace.copy(span = rawFace.span.coerceIn(1, remaining.coerceAtMost(2)))
            if (!WidgetGrids.canAdd(state.composition, fitted, state::faceOf)) {
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

    private fun maxYOf(itemState: ItemState): Float =
        screenBottomByState[itemState] ?: measuredSnapshots[itemState]?.maxY?.takeIf { it > 0f } ?: screenBottom

    private fun clockBoundsOf(state: HomeUiState, itemState: ItemState): ItemBounds? {
        val clock = state.composition.clockItem ?: return null
        val snapshot = measuredSnapshots[itemState]
        if (snapshot != null &&
            snapshot.clockStyle == state.clockStyle &&
            snapshot.clockPlacement == clock.placement(itemState) &&
            snapshot.clockScale == clock.scale(itemState)
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
        val large = itemState == ItemState.LARGE || itemState == ItemState.AOD_LARGE
        val face = (if (target.clock) state.clockPreset?.face else state.faceOf(target.style)) ?: return null
        if (target.clock) return WidgetGrids.estimateClockBounds(face, target, itemState)
        val placement = target.placement(itemState) ?: face.large.placement
        val (width, height) = sizeOf(face, large = true)
        return ItemBounds.of(placement, width, height)
    }

    private fun sizeOf(face: ClockFace, large: Boolean): Pair<Float, Float> {
        val root = (if (large) face.large else face.small) as? StackNode
        val width = root?.width ?: FaceGrid.width(face.span.coerceIn(1, FaceGrid.ROW_CELLS)) * (if (large) 1f else SMALL_SCALE)
        val height = root?.height ?: INLINE_HEIGHT * (if (large) 1f else SMALL_SCALE)
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
        editing.value = null
    }

    private data class MeasuredSnapshot(
        val clockStyle: Int,
        val clockPlacement: FacePlacement?,
        val clockScale: Float,
        val bounds: Map<Int, ItemBounds>,
        val maxY: Float
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
private const val DEFAULT_BOTTOM = 1.9f
private const val SMALL_SCALE = 0.62f
private const val INLINE_HEIGHT = 0.08f
private val CLOCK_LARGE = 0.9f to 0.45f
private val CLOCK_SMALL = 0.45f to 0.2f

private const val CREATE_TIMEOUT = 3_000L
