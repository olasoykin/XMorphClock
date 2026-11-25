package com.drdisagree.xmorphclock.domain.editor

import android.graphics.RectF
import com.drdisagree.xmorphclock.domain.model.ClockFace
import com.drdisagree.xmorphclock.domain.model.ColumnNode
import com.drdisagree.xmorphclock.domain.model.Composition
import com.drdisagree.xmorphclock.domain.model.CompositionItem
import com.drdisagree.xmorphclock.domain.model.FaceAlign
import com.drdisagree.xmorphclock.domain.model.FaceGrid
import com.drdisagree.xmorphclock.domain.model.FaceLayer
import com.drdisagree.xmorphclock.domain.model.FaceLayerType
import com.drdisagree.xmorphclock.domain.model.FaceNode
import com.drdisagree.xmorphclock.domain.model.FacePlacement
import com.drdisagree.xmorphclock.domain.model.ItemBounds
import com.drdisagree.xmorphclock.domain.model.ItemState
import com.drdisagree.xmorphclock.domain.model.LayerNode
import com.drdisagree.xmorphclock.domain.model.RowNode
import com.drdisagree.xmorphclock.domain.model.StackNode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class LockTarget {
    CLOCK,
    WIDGET_GRID,
    INLINE_GRID,
    SHORTCUTS
}

enum class WidgetZone {
    GRID_1X4,
    INLINE_1X2
}

object WidgetGrids {

    const val MAX_GRID_CELLS = FaceGrid.ROW_CELLS
    const val MAX_INLINE_ITEMS = 2

    private const val INLINE_CATEGORY = "inline"
    private const val EDGE = 0.0225f
    private const val MIN_TOP = 0.02f
    private const val SMALL_SCALE = 0.62f
    private const val INLINE_HEIGHT = 0.08f
    private const val INLINE_HALF_GAP = 0.024f
    private const val GRID_GAP = 0.04f
    private const val ZONE_GAP = 0.018f
    const val INLINE_PLACEHOLDER_RATIO = 0.72f
    private const val DEFAULT_BOTTOM = 1.9f
    private const val EPS = 0.0005f
    private const val AREA_EPS = 1e-6f
    private const val PUSH_SUBSTEP = 0.04f

    fun zoneOf(face: ClockFace): WidgetZone =
        if (face.category == INLINE_CATEGORY) WidgetZone.INLINE_1X2 else WidgetZone.GRID_1X4

    fun spanOf(face: ClockFace): Int =
        if (zoneOf(face) == WidgetZone.INLINE_1X2) MAX_GRID_CELLS else face.span.coerceIn(1, MAX_GRID_CELLS)

    fun gridItems(composition: Composition, faceOf: (Int) -> ClockFace?): List<CompositionItem> =
        composition.items.filter { !it.clock && faceOf(it.style)?.let(::zoneOf) != WidgetZone.INLINE_1X2 }

    fun inlineItems(composition: Composition, faceOf: (Int) -> ClockFace?): List<CompositionItem> =
        composition.items.filter { !it.clock && faceOf(it.style)?.let(::zoneOf) == WidgetZone.INLINE_1X2 }

    fun usedGridCells(composition: Composition, faceOf: (Int) -> ClockFace?): Int =
        gridItems(composition, faceOf).sumOf { item -> faceOf(item.style)?.let(::spanOf) ?: 1 }

    fun canAdd(composition: Composition, face: ClockFace, faceOf: (Int) -> ClockFace?): Boolean = when (zoneOf(face)) {
        WidgetZone.GRID_1X4 -> usedGridCells(composition, faceOf) + spanOf(face) <= MAX_GRID_CELLS
        WidgetZone.INLINE_1X2 -> inlineItems(composition, faceOf).size < MAX_INLINE_ITEMS
    }

    fun layoutZone(
        composition: Composition,
        zone: WidgetZone,
        itemState: ItemState,
        faceOf: (Int) -> ClockFace?,
        clockBottomY: Float,
        maxY: Float = DEFAULT_BOTTOM,
        overrideCenter: FacePlacement? = null,
        centerHorizontally: Boolean = false,
        clockBounds: ItemBounds? = null,
        widgetsAbove: Boolean = false
    ): Composition {
        val large = itemState == ItemState.LARGE || itemState == ItemState.AOD_LARGE
        val scale = 1f
        val cellW = FaceGrid.CELL * scale
        val gap = FaceGrid.GAP * scale
        val zoneGap = GRID_GAP * scale
        val maxBottom = (maxY - EDGE).coerceAtLeast(MIN_TOP + 0.1f)

        return when (zone) {
            WidgetZone.GRID_1X4 -> {
                val items = gridItems(composition, faceOf)
                if (items.isEmpty()) return composition
                val spans = items.map { faceOf(it.style)?.let(::spanOf) ?: 1 }
                val widths = spans.map { s -> cellW * s + gap * (s - 1) }
                val totalW = widths.sum() + gap * (items.size - 1)
                val rowH = items.maxOf { item -> heightOf(faceOf(item.style)) }.coerceAtLeast(cellW)

                val existingOrigin = overrideCenter ?: currentGridCenter(items, widths, itemState)
                val desiredCenterX = if (overrideCenter != null) overrideCenter.x else if (centerHorizontally) 0.5f else (existingOrigin?.x ?: 0.5f)
                val centerX = clampCenterX(desiredCenterX, totalW)

                val clockSpan = clockBounds?.let { (it.top - zoneGap) to (it.bottom + zoneGap) }
                    ?: (0f to (clockBottomY + zoneGap))
                val inlineSpan = inlineVerticalSpan(composition, itemState, faceOf, large, zoneGap)
                val forbidden = listOfNotNull(clockSpan, inlineSpan)

                val defaultY = if (widgetsAbove) {
                    ((clockBounds?.top ?: (clockBottomY - 0.4f)) - zoneGap - rowH).coerceAtLeast(MIN_TOP)
                } else {
                    (clockBounds?.bottom ?: clockBottomY) + zoneGap
                }
                val desiredY = if (overrideCenter != null) {
                    overrideCenter.y
                } else if (existingOrigin != null) {
                    val clockRef = clockBounds?.bottom ?: clockBottomY
                    val clockTopRef = clockBounds?.top ?: (clockBottomY - 0.4f)
                    val matchesDirection = if (widgetsAbove) existingOrigin.y < clockTopRef else existingOrigin.y > clockRef
                    if (matchesDirection) existingOrigin.y else defaultY
                } else {
                    defaultY
                }
                val topY = findNonOverlappingY(
                    desiredTop = desiredY,
                    rowHeight = rowH,
                    minTop = MIN_TOP,
                    maxBottom = maxBottom,
                    forbidden = forbidden,
                    preferBelow = if (overrideCenter == null) !widgetsAbove else null,
                    clockSpan = if (overrideCenter == null) clockSpan else null
                )

                var cursor = centerX - totalW / 2f
                val placements = HashMap<Int, FacePlacement>(items.size)
                items.forEachIndexed { index, item ->
                    val w = widths[index]
                    placements[item.id] = FacePlacement(x = cursor + w / 2f, y = topY, anchor = FaceAlign.CENTER)
                    cursor += w + gap
                }
                applyZonePlacements(composition, itemState, placements)
            }

            WidgetZone.INLINE_1X2 -> {
                val items = inlineItems(composition, faceOf)
                if (items.isEmpty()) return composition
                val rowH = items.maxOf { item -> heightOf(faceOf(item.style)) }.coerceAtLeast(INLINE_HEIGHT * scale)
                val existingOrigin = overrideCenter ?: currentInlineCenter(items, itemState)
                val gridSpan = gridVerticalSpan(composition, itemState, faceOf, large, zoneGap)
                val clockSpan = clockBounds?.let { (it.top - zoneGap) to (it.bottom + zoneGap) }
                    ?: (0f to (clockBottomY + zoneGap))
                val defaultInlineY = if (widgetsAbove) {
                    gridSpan?.first?.minus(zoneGap + rowH) ?: ((clockBounds?.top ?: (clockBottomY - 0.4f)) - zoneGap - rowH)
                } else {
                    gridSpan?.second ?: (clockBounds?.bottom?.plus(zoneGap) ?: (clockBottomY + zoneGap + cellW + zoneGap))
                }
                val forbidden = listOfNotNull(clockSpan, gridSpan)

                val desiredInlineY = if (overrideCenter != null) {
                    overrideCenter.y
                } else if (existingOrigin != null) {
                    val clockRef = clockBounds?.bottom ?: clockBottomY
                    val clockTopRef = clockBounds?.top ?: (clockBottomY - 0.4f)
                    val matchesDirection = if (widgetsAbove) existingOrigin.y < clockTopRef else existingOrigin.y > clockRef
                    if (matchesDirection) existingOrigin.y else defaultInlineY
                } else {
                    defaultInlineY
                }
                val topY = findNonOverlappingY(
                    desiredTop = desiredInlineY,
                    rowHeight = rowH,
                    minTop = MIN_TOP,
                    maxBottom = maxBottom,
                    forbidden = forbidden,
                    preferBelow = if (overrideCenter == null) !widgetsAbove else null,
                    clockSpan = if (overrideCenter == null) clockSpan else null
                )

                val desiredCenterX = if (overrideCenter != null) overrideCenter.x else if (centerHorizontally) 0.5f else (existingOrigin?.x ?: 0.5f)
                val halfGap = INLINE_HALF_GAP * scale
                val placements = HashMap<Int, FacePlacement>(items.size)
                if (items.size == 1) {
                    val centerX = clampCenterX(desiredCenterX, 0.36f * scale)
                    placements[items[0].id] = FacePlacement(x = centerX, y = topY, anchor = FaceAlign.CENTER)
                } else {
                    val centerX = desiredCenterX.coerceIn(0.28f, 0.72f)
                    placements[items[0].id] = FacePlacement(x = (centerX - halfGap).coerceAtLeast(EDGE + 0.14f), y = topY, anchor = FaceAlign.END)
                    placements[items[1].id] = FacePlacement(x = (centerX + halfGap).coerceAtMost(1f - EDGE - 0.14f), y = topY, anchor = FaceAlign.START)
                }
                applyZonePlacements(composition, itemState, placements)
            }
        }
    }

    fun ensureClockClearanceForWidgets(
        composition: Composition,
        itemState: ItemState,
        faceOf: (Int) -> ClockFace?,
        clockBounds: ItemBounds?,
        maxY: Float = DEFAULT_BOTTOM
    ): Pair<Composition, ItemBounds?> {
        val clock = clockBounds ?: return composition to null
        val clockItem = composition.clockItem ?: return composition to clock
        val grid = gridItems(composition, faceOf)
        val inline = inlineItems(composition, faceOf)
        if (grid.isEmpty() && inline.isEmpty()) return composition to clock

        val zoneGap = GRID_GAP
        val maxBottom = (maxY - EDGE).coerceAtLeast(MIN_TOP + 0.1f)
        val rowHGrid = if (grid.isNotEmpty()) grid.maxOf { heightOf(faceOf(it.style)) }.coerceAtLeast(FaceGrid.CELL) else 0f
        val rowHInline = if (inline.isNotEmpty()) inline.maxOf { heightOf(faceOf(it.style)) }.coerceAtLeast(INLINE_HEIGHT) else 0f
        val totalH = rowHGrid + rowHInline + (if (grid.isNotEmpty() && inline.isNotEmpty()) zoneGap else 0f)

        val clockShiftY = if (composition.widgetsAbove) {
            val spaceAbove = clock.top - MIN_TOP - zoneGap
            if (spaceAbove < totalH) {
                val neededShift = totalH - spaceAbove
                val maxAllowedShift = (maxBottom - clock.bottom - 0.05f).coerceAtLeast(0f)
                neededShift.coerceIn(0f, maxAllowedShift)
            } else 0f
        } else {
            val spaceBelow = maxBottom - clock.bottom - zoneGap
            if (spaceBelow < totalH) {
                val neededShift = totalH - spaceBelow
                val maxAllowedShift = (clock.top - MIN_TOP).coerceAtLeast(0f)
                -neededShift.coerceIn(0f, maxAllowedShift)
            } else 0f
        }

        if (clockShiftY == 0f) return composition to clock
        val shifted = applyMovedItems(composition, setOf(clockItem.id), itemState, 0f, clockShiftY) { FacePlacement() }
        val shiftedBounds = ItemBounds(clock.left, clock.top + clockShiftY, clock.right, clock.bottom + clockShiftY)
        return shifted to shiftedBounds
    }

    fun reconcileWidgetsForClock(
        composition: Composition,
        clockFace: ClockFace,
        faceOf: (Int) -> ClockFace?,
        largeMaxY: Float = DEFAULT_BOTTOM,
        smallMaxY: Float = DEFAULT_BOTTOM,
        largeClockBounds: ItemBounds? = null,
        smallClockBounds: ItemBounds? = null
    ): Composition {
        val grid = gridItems(composition, faceOf)
        val inline = inlineItems(composition, faceOf)
        if (grid.isEmpty() && inline.isEmpty()) return composition

        var result = composition
        listOf(ItemState.LARGE to largeMaxY, ItemState.SMALL to smallMaxY).forEach { (state, maxY) ->
            val rawClock = when (state) {
                ItemState.LARGE -> largeClockBounds
                ItemState.SMALL -> smallClockBounds
                else -> null
            } ?: estimateClockBounds(clockFace, result.clockItem, state)

            val paddedClock = ItemBounds(
                rawClock.left - GRID_GAP,
                rawClock.top - GRID_GAP,
                rawClock.right + GRID_GAP,
                rawClock.bottom + GRID_GAP
            )
            val gridOverlaps = grid.any { item ->
                val b = widgetBoundsOf(item, state, faceOf) ?: return@any true
                overlaps(b, paddedClock)
            }
            val inlineOverlaps = inline.any { item ->
                val b = widgetBoundsOf(item, state, faceOf) ?: return@any true
                overlaps(b, paddedClock)
            }

            val needGridLayout = grid.isNotEmpty() && (result.gridDocked || gridOverlaps)
            val needInlineLayout = inline.isNotEmpty() && (result.inlineDocked || inlineOverlaps)
            if (!needGridLayout && !needInlineLayout) return@forEach

            val (cleared, clockBounds) = ensureClockClearanceForWidgets(result, state, faceOf, rawClock, maxY)
            result = cleared
            val activeClock = clockBounds ?: rawClock
            if (needGridLayout) {
                result = layoutZone(
                    composition = result,
                    zone = WidgetZone.GRID_1X4,
                    itemState = state,
                    faceOf = faceOf,
                    clockBottomY = activeClock.bottom,
                    maxY = maxY,
                    centerHorizontally = result.gridDocked,
                    clockBounds = activeClock,
                    widgetsAbove = result.widgetsAbove
                )
            }
            if (needInlineLayout) {
                result = layoutZone(
                    composition = result,
                    zone = WidgetZone.INLINE_1X2,
                    itemState = state,
                    faceOf = faceOf,
                    clockBottomY = activeClock.bottom,
                    maxY = maxY,
                    centerHorizontally = result.inlineDocked,
                    clockBounds = activeClock,
                    widgetsAbove = result.widgetsAbove
                )
            }
        }
        return result
    }

    fun estimateClockBounds(
        face: ClockFace,
        clockItem: CompositionItem?,
        itemState: ItemState
    ): ItemBounds {
        val baseNode = when (itemState) {
            ItemState.LARGE -> face.large
            ItemState.SMALL -> face.small
            ItemState.AOD_LARGE -> face.aodLarge ?: face.large
            ItemState.AOD_SMALL -> face.aodSmall ?: face.small
        }
        val scale = clockItem?.scale(itemState) ?: 1f
        val node = FaceEditor.scaleNode(baseNode, scale)
        val placement = clockItem?.placement(itemState) ?: node.placement
        val layersById = face.layers.associateBy { it.id }
        val box = estimateNode(node, layersById) ?: return ItemBounds.of(placement, 0.5f, 0.25f)
        val left = when (placement.anchor) {
            FaceAlign.START -> placement.x
            FaceAlign.CENTER -> placement.x - box.width / 2f
            FaceAlign.END -> placement.x - box.width
        }
        return ItemBounds(
            left = left + box.minLeft,
            top = placement.y + box.minTop,
            right = left + box.maxRight,
            bottom = placement.y + box.maxBottom
        )
    }

    private fun widgetBoundsOf(
        item: CompositionItem,
        state: ItemState,
        faceOf: (Int) -> ClockFace?
    ): ItemBounds? {
        val placement = item.placement(state) ?: return null
        val face = faceOf(item.style) ?: return null
        val root = face.large as? StackNode
        val w = root?.width ?: if (zoneOf(face) == WidgetZone.INLINE_1X2) 0.36f else FaceGrid.width(spanOf(face))
        val h = heightOf(face)
        return ItemBounds.of(placement, w, h)
    }

    fun flipWidgetsPosition(
        composition: Composition,
        widgetsAbove: Boolean,
        itemState: ItemState,
        faceOf: (Int) -> ClockFace?,
        clockBounds: ItemBounds?,
        maxY: Float = DEFAULT_BOTTOM
    ): Composition {
        val large = itemState == ItemState.LARGE || itemState == ItemState.AOD_LARGE
        val scale = 1f
        val zoneGap = GRID_GAP
        val maxBottom = (maxY - EDGE).coerceAtLeast(MIN_TOP + 0.1f)
        val clock = clockBounds ?: ItemBounds(0.05f, 0.2f, 0.95f, 0.6f)

        var result = composition
        val grid = gridItems(composition, faceOf)
        val inline = inlineItems(composition, faceOf)
        if (grid.isEmpty() && inline.isEmpty()) return result.copy(widgetsAbove = widgetsAbove)

        val cellW = FaceGrid.CELL
        val rowHGrid = if (grid.isNotEmpty()) grid.maxOf { heightOf(faceOf(it.style)) }.coerceAtLeast(cellW) else 0f
        val rowHInline = if (inline.isNotEmpty()) inline.maxOf { heightOf(faceOf(it.style)) }.coerceAtLeast(INLINE_HEIGHT) else 0f
        val totalH = rowHGrid + rowHInline + (if (grid.isNotEmpty() && inline.isNotEmpty()) zoneGap else 0f)

        var clockShiftY = 0f
        if (widgetsAbove) {
            val spaceAbove = clock.top - MIN_TOP - zoneGap
            if (spaceAbove < totalH) {
                val neededShift = totalH - spaceAbove
                val maxAllowedShift = maxBottom - clock.bottom - 0.05f
                clockShiftY = neededShift.coerceIn(0f, maxAllowedShift.coerceAtLeast(0f))
            }
        } else {
            val spaceBelow = maxBottom - clock.bottom - zoneGap
            if (spaceBelow < totalH) {
                val neededShift = totalH - spaceBelow
                val maxAllowedShift = clock.top - MIN_TOP - 0.05f
                clockShiftY = -neededShift.coerceIn(0f, maxAllowedShift.coerceAtLeast(0f))
            }
        }

        val clockItem = composition.clockItem
        if (clockShiftY != 0f && clockItem != null) {
            result = applyMovedItems(result, setOf(clockItem.id), itemState, 0f, clockShiftY) { FacePlacement() }
        }

        val shiftedClock = ItemBounds(clock.left, clock.top + clockShiftY, clock.right, clock.bottom + clockShiftY)
        val clockBottomY = shiftedClock.bottom

        result = layoutZone(
            composition = result,
            zone = WidgetZone.GRID_1X4,
            itemState = itemState,
            faceOf = faceOf,
            clockBottomY = clockBottomY,
            maxY = maxY,
            centerHorizontally = true,
            clockBounds = shiftedClock,
            widgetsAbove = widgetsAbove
        )
        result = layoutZone(
            composition = result,
            zone = WidgetZone.INLINE_1X2,
            itemState = itemState,
            faceOf = faceOf,
            clockBottomY = clockBottomY,
            maxY = maxY,
            centerHorizontally = true,
            clockBounds = shiftedClock,
            widgetsAbove = widgetsAbove
        )
        return result.copy(widgetsAbove = widgetsAbove, gridDocked = true, inlineDocked = true)
    }

    data class PlaceholdersResult(
        val gridPlaceholder: RectF?,
        val inlinePlaceholder: RectF?
    )

    fun calculatePlaceholders(
        clockRect: RectF?,
        gridRect: RectF?,
        inlineRect: RectF?,
        widgetsAbove: Boolean,
        width: Float,
        height: Float,
        unit: Float,
        frameLeft: Float,
        edgePadPx: Float,
        slotGap: Float,
        safeBottomPx: Float,
        gridSlotH: Float,
        inlineSlotH: Float,
        minTouch: Float,
        isAod: Boolean
    ): PlaceholdersResult {
        if (isAod || unit <= 0f || height <= 0f) {
            return PlaceholdersResult(null, null)
        }

        val wGrid = minOf(unit * FaceGrid.width(FaceGrid.ROW_CELLS), (width - edgePadPx * 2f).coerceAtLeast(minTouch))
        val hGrid = maxOf(unit * FaceGrid.CELL * 0.72f, gridSlotH)
        val leftGrid = (frameLeft + (unit - wGrid) / 2f).coerceIn(edgePadPx, (width - edgePadPx - wGrid).coerceAtLeast(edgePadPx))

        val wInline = minOf(unit * INLINE_PLACEHOLDER_RATIO, (width - edgePadPx * 2f).coerceAtLeast(minTouch))
        val hInline = inlineSlotH
        val leftInline = (frameLeft + (unit - wInline) / 2f).coerceIn(edgePadPx, (width - edgePadPx - wInline).coerceAtLeast(edgePadPx))

        val needGrid = gridRect == null
        val needInline = inlineRect == null
        if (!needGrid && !needInline) return PlaceholdersResult(null, null)

        val cTop = clockRect?.top ?: (height * 0.35f)
        val cBottom = clockRect?.bottom ?: (height * 0.45f)
        val spaceBelow = safeBottomPx - cBottom - slotGap

        val placeAbove = widgetsAbove || spaceBelow < (if (needGrid) hGrid + slotGap else 0f) + (if (needInline) hInline + slotGap else 0f)

        var gRes: RectF? = null
        var iRes: RectF? = null

        if (placeAbove) {
            if (needGrid && needInline) {
                val gTop = (cTop - slotGap - hGrid).coerceIn(edgePadPx + hInline + slotGap, (cTop - slotGap - hGrid).coerceAtLeast(edgePadPx))
                val iTop = (gTop - slotGap - hInline).coerceAtLeast(edgePadPx)
                gRes = RectF(leftGrid, gTop, leftGrid + wGrid, gTop + hGrid)
                iRes = RectF(leftInline, iTop, leftInline + wInline, iTop + hInline)
            } else if (needGrid) {
                val refTop = inlineRect?.top ?: cTop
                val gTop = (refTop - slotGap - hGrid).coerceAtLeast(edgePadPx)
                gRes = RectF(leftGrid, gTop, leftGrid + wGrid, gTop + hGrid)
            } else {
                val refTop = gridRect?.top ?: cTop
                val iTop = (refTop - slotGap - hInline).coerceAtLeast(edgePadPx)
                iRes = RectF(leftInline, iTop, leftInline + wInline, iTop + hInline)
            }
        } else {
            if (needGrid && needInline) {
                val gTop = (cBottom + slotGap).coerceIn(edgePadPx, safeBottomPx - hGrid - hInline - slotGap)
                val iTop = gTop + hGrid + slotGap
                gRes = RectF(leftGrid, gTop, leftGrid + wGrid, gTop + hGrid)
                iRes = RectF(leftInline, iTop, leftInline + wInline, iTop + hInline)
            } else if (needGrid) {
                val gTop = (cBottom + slotGap).coerceIn(edgePadPx, safeBottomPx - hGrid)
                gRes = RectF(leftGrid, gTop, leftGrid + wGrid, gTop + hGrid)
            } else {
                if (gridRect != null) {
                    val canFitBelowGrid = safeBottomPx - gridRect.bottom >= hInline + slotGap
                    val canFitBetweenClockAndGrid = gridRect.top - cBottom >= hInline + slotGap * 2f
                    val iTop = when {
                        canFitBelowGrid -> gridRect.bottom + slotGap
                        canFitBetweenClockAndGrid -> cBottom + slotGap
                        else -> (cTop - slotGap - hInline).coerceAtLeast(edgePadPx)
                    }
                    iRes = RectF(leftInline, iTop, leftInline + wInline, iTop + hInline)
                } else {
                    val iTop = (cBottom + slotGap).coerceIn(edgePadPx, safeBottomPx - hInline)
                    iRes = RectF(leftInline, iTop, leftInline + wInline, iTop + hInline)
                }
            }
        }

        return PlaceholdersResult(gRes, iRes)
    }

    data class StickyDragResult(
        val deltas: Map<LockTarget, Pair<Float, Float>>,
        val gridDocked: Boolean,
        val inlineDocked: Boolean,
        val attachedEvent: Boolean
    )

    fun solveStickyDrag(
        activeTarget: LockTarget,
        startBounds: Map<LockTarget, ItemBounds>,
        rawDx: Float,
        rawDy: Float,
        lastValidDeltas: Map<LockTarget, Pair<Float, Float>>,
        gridDocked: Boolean,
        inlineDocked: Boolean,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        fixedObstacles: List<ItemBounds>,
        gap: Float = ZONE_GAP
    ): StickyDragResult {
        if (activeTarget !in startBounds) {
            return StickyDragResult(lastValidDeltas, gridDocked, inlineDocked, false)
        }

        var nextGridDocked = gridDocked
        var nextInlineDocked = inlineDocked
        var attachedEvent = false

        val activeStart = startBounds.getValue(activeTarget)
        val activeLast = lastValidDeltas[activeTarget] ?: (0f to 0f)

        if (activeTarget == LockTarget.CLOCK) {
            val candidateClock = shifted(activeStart, rawDx, rawDy)
            val paddedClock = ItemBounds(
                candidateClock.left - gap - 0.02f,
                candidateClock.top - gap - 0.02f,
                candidateClock.right + gap + 0.02f,
                candidateClock.bottom + gap + 0.02f
            )

            if (!nextGridDocked && LockTarget.WIDGET_GRID in startBounds) {
                val gridCurrent = shifted(
                    startBounds.getValue(LockTarget.WIDGET_GRID),
                    lastValidDeltas[LockTarget.WIDGET_GRID]?.first ?: 0f,
                    lastValidDeltas[LockTarget.WIDGET_GRID]?.second ?: 0f
                )
                if (overlaps(paddedClock, gridCurrent)) {
                    nextGridDocked = true
                    attachedEvent = true
                }
            }

            if (!nextInlineDocked && LockTarget.INLINE_GRID in startBounds) {
                val inlineCurrent = shifted(
                    startBounds.getValue(LockTarget.INLINE_GRID),
                    lastValidDeltas[LockTarget.INLINE_GRID]?.first ?: 0f,
                    lastValidDeltas[LockTarget.INLINE_GRID]?.second ?: 0f
                )
                if (overlaps(paddedClock, inlineCurrent)) {
                    nextInlineDocked = true
                    attachedEvent = true
                }
            }

            val dockedGroup = mutableSetOf(LockTarget.CLOCK)
            if (nextGridDocked && LockTarget.WIDGET_GRID in startBounds) dockedGroup += LockTarget.WIDGET_GRID
            if (nextInlineDocked && LockTarget.INLINE_GRID in startBounds) dockedGroup += LockTarget.INLINE_GRID

            val clusterBoxes = dockedGroup.map { target ->
                val targetLast = lastValidDeltas[target] ?: (0f to 0f)
                shifted(
                    startBounds.getValue(target),
                    targetLast.first - activeLast.first,
                    targetLast.second - activeLast.second
                )
            }
            val clusterLeft = clusterBoxes.minOf { it.left }
            val clusterTop = clusterBoxes.minOf { it.top }
            val clusterRight = clusterBoxes.maxOf { it.right }
            val clusterBottom = clusterBoxes.maxOf { it.bottom }
            val clusterBounds = ItemBounds(clusterLeft, clusterTop, clusterRight, clusterBottom)

            val (clampedDx, clampedDy) = clampNonOverlappingDelta(
                startBounds = clusterBounds,
                rawDx = rawDx,
                rawDy = rawDy,
                lastValidDelta = activeLast,
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
                obstacles = fixedObstacles,
                gap = gap
            )

            val stepDx = clampedDx - activeLast.first
            val stepDy = clampedDy - activeLast.second
            val newDeltas = HashMap(lastValidDeltas)
            dockedGroup.forEach { target ->
                val targetLast = lastValidDeltas[target] ?: (0f to 0f)
                newDeltas[target] = (targetLast.first + stepDx) to (targetLast.second + stepDy)
            }

            val undockedTargets = startBounds.keys - dockedGroup
            if (undockedTargets.isNotEmpty()) {
                val pushResult = solvePushLayout(
                    activeTarget = LockTarget.CLOCK,
                    startBounds = startBounds,
                    rawDx = clampedDx,
                    rawDy = clampedDy,
                    lastValidDeltas = newDeltas,
                    minX = minX,
                    maxX = maxX,
                    minY = minY,
                    maxY = maxY,
                    fixedObstacles = fixedObstacles,
                    gap = gap
                )
                return StickyDragResult(pushResult, nextGridDocked, nextInlineDocked, attachedEvent)
            }

            return StickyDragResult(newDeltas, nextGridDocked, nextInlineDocked, attachedEvent)
        } else {
            val wasDocked = when (activeTarget) {
                LockTarget.WIDGET_GRID -> gridDocked
                LockTarget.INLINE_GRID -> inlineDocked
                else -> false
            }
            if (activeTarget == LockTarget.WIDGET_GRID) nextGridDocked = false
            if (activeTarget == LockTarget.INLINE_GRID) nextInlineDocked = false

            if (LockTarget.CLOCK in startBounds) {
                val candidateWidget = shifted(activeStart, rawDx, rawDy)
                val paddedWidget = ItemBounds(
                    candidateWidget.left - gap - 0.02f,
                    candidateWidget.top - gap - 0.02f,
                    candidateWidget.right + gap + 0.02f,
                    candidateWidget.bottom + gap + 0.02f
                )
                val clockCurrent = shifted(
                    startBounds.getValue(LockTarget.CLOCK),
                    lastValidDeltas[LockTarget.CLOCK]?.first ?: 0f,
                    lastValidDeltas[LockTarget.CLOCK]?.second ?: 0f
                )
                if (overlaps(paddedWidget, clockCurrent)) {
                    if (activeTarget == LockTarget.WIDGET_GRID) nextGridDocked = true
                    if (activeTarget == LockTarget.INLINE_GRID) nextInlineDocked = true
                    attachedEvent = !wasDocked
                }
            }

            val solved = solvePushLayout(
                activeTarget = activeTarget,
                startBounds = startBounds,
                rawDx = rawDx,
                rawDy = rawDy,
                lastValidDeltas = lastValidDeltas,
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
                fixedObstacles = fixedObstacles,
                gap = gap
            )
            return StickyDragResult(solved, nextGridDocked, nextInlineDocked, attachedEvent)
        }
    }

    fun applyMovedItems(
        composition: Composition,
        ids: Set<Int>,
        itemState: ItemState,
        dx: Float,
        dy: Float,
        defaultPlacementOf: (CompositionItem) -> FacePlacement
    ): Composition {
        if (dx == 0f && dy == 0f) return composition
        val nextItems = composition.items.map { item ->
            if (item.id !in ids) return@map item
            val base = item.placement(itemState) ?: defaultPlacementOf(item)
            val updated = base.copy(x = base.x + dx, y = base.y + dy)
            when (itemState) {
                ItemState.LARGE -> item.copy(large = updated)
                ItemState.SMALL -> item.copy(small = updated)
                ItemState.AOD_LARGE -> item.copy(aodLarge = updated)
                ItemState.AOD_SMALL -> item.copy(aodSmall = updated)
            }
        }
        return composition.copy(items = nextItems)
    }

    fun clampDelta(
        bounds: ItemBounds,
        rawDx: Float,
        rawDy: Float,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float
    ): Pair<Float, Float> {
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        val dx = if (width <= maxX - minX) {
            rawDx.coerceIn(minX - bounds.left, maxX - bounds.right)
        } else {
            val centerTarget = ((minX + maxX) / 2f) - (bounds.left + bounds.right) / 2f
            rawDx.coerceIn(minOf(minX - bounds.left, centerTarget), maxOf(maxX - bounds.right, centerTarget))
        }
        val dy = if (height <= maxY - minY) {
            rawDy.coerceIn(minY - bounds.top, maxY - bounds.bottom)
        } else {
            rawDy.coerceAtLeast(minY - bounds.top)
        }
        return dx to dy
    }

    fun clampNonOverlappingDelta(
        startBounds: ItemBounds,
        rawDx: Float,
        rawDy: Float,
        lastValidDelta: Pair<Float, Float>,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        obstacles: List<ItemBounds>,
        gap: Float = ZONE_GAP
    ): Pair<Float, Float> {
        val (clampedDx, clampedDy) = clampDelta(startBounds, rawDx, rawDy, minX, maxX, minY, maxY)
        if (obstacles.isEmpty()) return clampedDx to clampedDy

        val paddedObstacles = obstacles.map { obs ->
            ItemBounds(obs.left - gap, obs.top - gap, obs.right + gap, obs.bottom + gap)
        }
        val targetBounds = shifted(startBounds, clampedDx, clampedDy)
        val blocking = paddedObstacles.filter { overlaps(targetBounds, it) }
        if (blocking.isEmpty()) return clampedDx to clampedDy

        val prevBounds = shifted(startBounds, lastValidDelta.first, lastValidDelta.second)
        val candidates = LinkedHashSet<Pair<Float, Float>>(16)
        candidates += clampedDx to lastValidDelta.second
        candidates += lastValidDelta.first to clampedDy

        blocking.forEach { obs ->
            val topSnap = obs.top - startBounds.bottom
            val bottomSnap = obs.bottom - startBounds.top
            val leftSnap = obs.left - startBounds.right
            val rightSnap = obs.right - startBounds.left

            // Prevent fast vertical or horizontal drags from tunneling or sliding sideways unexpectedly
            val xAlignedWithPrev = prevBounds.left < obs.right - EPS && prevBounds.right > obs.left + EPS
            val yAlignedWithPrev = prevBounds.top < obs.bottom - EPS && prevBounds.bottom > obs.top + EPS
            val preferTopOnly = xAlignedWithPrev && prevBounds.bottom <= obs.top + EPS
            val preferBottomOnly = xAlignedWithPrev && prevBounds.top >= obs.bottom - EPS
            val preferLeftOnly = yAlignedWithPrev && prevBounds.right <= obs.left + EPS
            val preferRightOnly = yAlignedWithPrev && prevBounds.left >= obs.right - EPS

            if (preferTopOnly) {
                candidates += clampedDx to topSnap
                candidates += lastValidDelta.first to topSnap
            } else if (preferBottomOnly) {
                candidates += clampedDx to bottomSnap
                candidates += lastValidDelta.first to bottomSnap
            } else if (preferLeftOnly) {
                candidates += leftSnap to clampedDy
                candidates += leftSnap to lastValidDelta.second
            } else if (preferRightOnly) {
                candidates += rightSnap to clampedDy
                candidates += rightSnap to lastValidDelta.second
            } else {
                candidates += clampedDx to topSnap
                candidates += lastValidDelta.first to topSnap
                candidates += clampedDx to bottomSnap
                candidates += lastValidDelta.first to bottomSnap
                candidates += leftSnap to clampedDy
                candidates += leftSnap to lastValidDelta.second
                candidates += rightSnap to clampedDy
                candidates += rightSnap to lastValidDelta.second
            }
        }

        val validCandidates = candidates.map { (cx, cy) ->
            clampDelta(startBounds, cx, cy, minX, maxX, minY, maxY)
        }.filter { (cx, cy) ->
            val moved = shifted(startBounds, cx, cy)
            paddedObstacles.none { overlaps(moved, it) }
        }

        if (validCandidates.isNotEmpty()) {
            return validCandidates.minBy { (cx, cy) ->
                val ddx = cx - clampedDx
                val ddy = cy - clampedDy
                ddx * ddx + ddy * ddy
            }
        }

        // Fallback if already overlapping from an earlier layout: allow moves that strictly reduce overlap area
        val prevOverlap = totalOverlapArea(prevBounds, paddedObstacles)
        if (prevOverlap > 0f) {
            val targetOverlap = totalOverlapArea(targetBounds, paddedObstacles)
            if (targetOverlap < prevOverlap) return clampedDx to clampedDy
        }

        return clampDelta(startBounds, lastValidDelta.first, lastValidDelta.second, minX, maxX, minY, maxY)
    }

    fun <K> solvePushLayout(
        activeTarget: K,
        startBounds: Map<K, ItemBounds>,
        rawDx: Float,
        rawDy: Float,
        lastValidDeltas: Map<K, Pair<Float, Float>>,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        fixedObstacles: List<ItemBounds>,
        gap: Float = ZONE_GAP
    ): Map<K, Pair<Float, Float>> {
        if (activeTarget !in startBounds) return lastValidDeltas
        val activeLast = lastValidDeltas[activeTarget] ?: (0f to 0f)
        val dist = hypot(rawDx - activeLast.first, rawDy - activeLast.second)
        val substeps = ceil(dist / PUSH_SUBSTEP).toInt().coerceIn(1, 32)
        if (substeps == 1) {
            return solveSingleStepPushLayout(
                activeTarget = activeTarget,
                startBounds = startBounds,
                rawDx = rawDx,
                rawDy = rawDy,
                lastValidDeltas = lastValidDeltas,
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
                fixedObstacles = fixedObstacles,
                gap = gap
            )
        }
        var current = lastValidDeltas
        for (step in 1..substeps) {
            val fraction = step.toFloat() / substeps.toFloat()
            val stepDx = activeLast.first + (rawDx - activeLast.first) * fraction
            val stepDy = activeLast.second + (rawDy - activeLast.second) * fraction
            current = solveSingleStepPushLayout(
                activeTarget = activeTarget,
                startBounds = startBounds,
                rawDx = stepDx,
                rawDy = stepDy,
                lastValidDeltas = current,
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
                fixedObstacles = fixedObstacles,
                gap = gap
            )
        }
        return current
    }

    private fun <K> solveSingleStepPushLayout(
        activeTarget: K,
        startBounds: Map<K, ItemBounds>,
        rawDx: Float,
        rawDy: Float,
        lastValidDeltas: Map<K, Pair<Float, Float>>,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        fixedObstacles: List<ItemBounds>,
        gap: Float
    ): Map<K, Pair<Float, Float>> {
        val activeLast = lastValidDeltas[activeTarget] ?: (0f to 0f)

        tryPushLayout(
            activeTarget = activeTarget,
            startBounds = startBounds,
            candidateActiveDx = rawDx,
            candidateActiveDy = rawDy,
            lastValidDeltas = lastValidDeltas,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
            fixedObstacles = fixedObstacles,
            gap = gap
        )?.let { return it }

        var lo = 0f
        var hi = 1f
        var best = tryPushLayout(
            activeTarget = activeTarget,
            startBounds = startBounds,
            candidateActiveDx = activeLast.first,
            candidateActiveDy = activeLast.second,
            lastValidDeltas = lastValidDeltas,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
            fixedObstacles = fixedObstacles,
            gap = gap
        ) ?: startBounds.keys.associateWith { lastValidDeltas[it] ?: (0f to 0f) }

        repeat(14) {
            val mid = (lo + hi) * 0.5f
            val testDx = activeLast.first + (rawDx - activeLast.first) * mid
            val testDy = activeLast.second + (rawDy - activeLast.second) * mid
            val res = tryPushLayout(
                activeTarget = activeTarget,
                startBounds = startBounds,
                candidateActiveDx = testDx,
                candidateActiveDy = testDy,
                lastValidDeltas = lastValidDeltas,
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
                fixedObstacles = fixedObstacles,
                gap = gap
            )
            if (res != null) {
                best = res
                lo = mid
            } else {
                hi = mid
            }
        }

        // Allow sliding along X or Y independently if the perpendicular axis hit a wall
        val settled = best[activeTarget] ?: activeLast
        if (abs(rawDx - settled.first) > EPS) {
            var xLo = settled.first
            var xHi = rawDx
            var xBest = best
            tryPushLayout(
                activeTarget = activeTarget,
                startBounds = startBounds,
                candidateActiveDx = rawDx,
                candidateActiveDy = settled.second,
                lastValidDeltas = best,
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
                fixedObstacles = fixedObstacles,
                gap = gap
            )?.let { return it }
            repeat(10) {
                val midX = (xLo + xHi) * 0.5f
                val res = tryPushLayout(
                    activeTarget = activeTarget,
                    startBounds = startBounds,
                    candidateActiveDx = midX,
                    candidateActiveDy = settled.second,
                    lastValidDeltas = best,
                    minX = minX,
                    maxX = maxX,
                    minY = minY,
                    maxY = maxY,
                    fixedObstacles = fixedObstacles,
                    gap = gap
                )
                if (res != null) {
                    xBest = res
                    xLo = midX
                } else {
                    xHi = midX
                }
            }
            best = xBest
        }
        val settledAfterX = best[activeTarget] ?: settled
        if (abs(rawDy - settledAfterX.second) > EPS) {
            tryPushLayout(
                activeTarget = activeTarget,
                startBounds = startBounds,
                candidateActiveDx = settledAfterX.first,
                candidateActiveDy = rawDy,
                lastValidDeltas = best,
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
                fixedObstacles = fixedObstacles,
                gap = gap
            )?.let { return it }
        }
        return best
    }

    private fun <K> tryPushLayout(
        activeTarget: K,
        startBounds: Map<K, ItemBounds>,
        candidateActiveDx: Float,
        candidateActiveDy: Float,
        lastValidDeltas: Map<K, Pair<Float, Float>>,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        fixedObstacles: List<ItemBounds>,
        gap: Float
    ): Map<K, Pair<Float, Float>>? {
        val activeStart = startBounds[activeTarget] ?: return null
        val activeLast = lastValidDeltas[activeTarget] ?: (0f to 0f)
        val activeDelta = clampNonOverlappingDelta(
            startBounds = activeStart,
            rawDx = candidateActiveDx,
            rawDy = candidateActiveDy,
            lastValidDelta = activeLast,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
            obstacles = fixedObstacles,
            gap = gap
        )

        val deltas = HashMap<K, Pair<Float, Float>>(startBounds.size)
        val prevBoxes = HashMap<K, ItemBounds>(startBounds.size)
        startBounds.forEach { (k, bounds) ->
            val prevDelta = lastValidDeltas[k] ?: (0f to 0f)
            prevBoxes[k] = shifted(bounds, prevDelta.first, prevDelta.second)
            deltas[k] = if (k == activeTarget) activeDelta else prevDelta
        }

        fun currentBox(k: K): ItemBounds {
            val d = deltas.getValue(k)
            return shifted(startBounds.getValue(k), d.first, d.second)
        }

        val paddedFixed = fixedObstacles.map { obs ->
            ItemBounds(obs.left - gap, obs.top - gap, obs.right + gap, obs.bottom + gap)
        }
        val initialOverlap = layoutOverlapArea(prevBoxes.values.toList(), paddedFixed, gap)

        val queue = ArrayDeque<K>()
        queue.addLast(activeTarget)
        val maxSteps = (startBounds.size * startBounds.size * 4).coerceAtLeast(16)
        var steps = 0

        while (queue.isNotEmpty() && steps < maxSteps) {
            steps++
            val pusher = queue.removeFirst()
            val pusherBox = currentBox(pusher)
            val pusherPrev = prevBoxes.getValue(pusher)
            val paddedPusher = ItemBounds(
                pusherBox.left - gap,
                pusherBox.top - gap,
                pusherBox.right + gap,
                pusherBox.bottom + gap
            )
            val sweptPusher = swept(pusherPrev, pusherBox)
            val paddedSweptPusher = ItemBounds(
                sweptPusher.left - gap,
                sweptPusher.top - gap,
                sweptPusher.right + gap,
                sweptPusher.bottom + gap
            )

            for (victim in startBounds.keys) {
                if (victim == pusher || victim == activeTarget) continue
                val victimBox = currentBox(victim)
                if (!overlaps(paddedSweptPusher, victimBox)) continue

                val victimPrev = prevBoxes.getValue(victim)
                val victimDelta = deltas.getValue(victim)

                val (rawVictimDx, rawVictimDy) = resolvePushVector(
                    paddedPusher = paddedPusher,
                    victimBox = victimBox,
                    pusherPrev = pusherPrev,
                    victimPrev = victimPrev,
                    victimDelta = victimDelta,
                    moveBiasDx = activeDelta.first - activeLast.first,
                    moveBiasDy = activeDelta.second - activeLast.second,
                    gap = gap
                )

                val clampedVictim = clampNonOverlappingDelta(
                    startBounds = startBounds.getValue(victim),
                    rawDx = rawVictimDx,
                    rawDy = rawVictimDy,
                    lastValidDelta = victimDelta,
                    minX = minX,
                    maxX = maxX,
                    minY = minY,
                    maxY = maxY,
                    obstacles = fixedObstacles,
                    gap = gap
                )

                if (clampedVictim != victimDelta) {
                    deltas[victim] = clampedVictim
                    if (victim !in queue) queue.addLast(victim)
                }
            }
        }

        val finalBoxes = startBounds.keys.map { currentBox(it) }
        val finalOverlap = layoutOverlapArea(finalBoxes, paddedFixed, gap)
        if (initialOverlap <= AREA_EPS) {
            if (finalOverlap > AREA_EPS) return null
        } else {
            if (finalOverlap > initialOverlap + AREA_EPS) return null
        }
        return deltas
    }

    private fun resolvePushVector(
        paddedPusher: ItemBounds,
        victimBox: ItemBounds,
        pusherPrev: ItemBounds,
        victimPrev: ItemBounds,
        victimDelta: Pair<Float, Float>,
        moveBiasDx: Float,
        moveBiasDy: Float,
        gap: Float
    ): Pair<Float, Float> {
        val wasAbove = pusherPrev.bottom <= victimPrev.top + gap + EPS
        val wasBelow = pusherPrev.top >= victimPrev.bottom - gap - EPS
        val wasLeft = pusherPrev.right <= victimPrev.left + gap + EPS
        val wasRight = pusherPrev.left >= victimPrev.right - gap - EPS

        return when {
            wasAbove -> victimDelta.first to (victimDelta.second + (paddedPusher.bottom - victimBox.top).coerceAtLeast(0f))
            wasBelow -> victimDelta.first to (victimDelta.second + (paddedPusher.top - victimBox.bottom).coerceAtMost(0f))
            wasLeft -> (victimDelta.first + (paddedPusher.right - victimBox.left).coerceAtLeast(0f)) to victimDelta.second
            wasRight -> (victimDelta.first + (paddedPusher.left - victimBox.right).coerceAtMost(0f)) to victimDelta.second
            else -> {
                val downPen = paddedPusher.bottom - victimBox.top
                val upPen = victimBox.bottom - paddedPusher.top
                val rightPen = paddedPusher.right - victimBox.left
                val leftPen = victimBox.right - paddedPusher.left
                val minVertical = minOf(downPen, upPen)
                val minHorizontal = minOf(rightPen, leftPen)
                if (minVertical <= minHorizontal || abs(moveBiasDy) >= abs(moveBiasDx)) {
                    val pushDown = if (abs(moveBiasDy) > EPS) moveBiasDy > 0f else downPen <= upPen
                    val dy = if (pushDown) downPen.coerceAtLeast(0f) else -upPen.coerceAtLeast(0f)
                    victimDelta.first to (victimDelta.second + dy)
                } else {
                    val pushRight = if (abs(moveBiasDx) > EPS) moveBiasDx > 0f else rightPen <= leftPen
                    val dx = if (pushRight) rightPen.coerceAtLeast(0f) else -leftPen.coerceAtLeast(0f)
                    (victimDelta.first + dx) to victimDelta.second
                }
            }
        }
    }

    private fun layoutOverlapArea(
        boxes: List<ItemBounds>,
        paddedFixed: List<ItemBounds>,
        gap: Float
    ): Float {
        var area = 0f
        for (i in boxes.indices) {
            val a = boxes[i]
            val paddedA = ItemBounds(a.left - gap, a.top - gap, a.right + gap, a.bottom + gap)
            for (j in i + 1 until boxes.size) {
                val b = boxes[j]
                val w = (minOf(paddedA.right, b.right) - maxOf(paddedA.left, b.left)).coerceAtLeast(0f)
                val h = (minOf(paddedA.bottom, b.bottom) - maxOf(paddedA.top, b.top)).coerceAtLeast(0f)
                area += w * h
            }
            area += totalOverlapArea(a, paddedFixed)
        }
        return area
    }

    private fun swept(a: ItemBounds, b: ItemBounds): ItemBounds =
        ItemBounds(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))

    private fun shifted(bounds: ItemBounds, dx: Float, dy: Float): ItemBounds =
        ItemBounds(bounds.left + dx, bounds.top + dy, bounds.right + dx, bounds.bottom + dy)

    private fun overlaps(a: ItemBounds, b: ItemBounds): Boolean =
        a.left < b.right - EPS && a.right > b.left + EPS && a.top < b.bottom - EPS && a.bottom > b.top + EPS

    private fun totalOverlapArea(bounds: ItemBounds, obstacles: List<ItemBounds>): Float =
        obstacles.sumOf { obs ->
            val w = (minOf(bounds.right, obs.right) - maxOf(bounds.left, obs.left)).coerceAtLeast(0f)
            val h = (minOf(bounds.bottom, obs.bottom) - maxOf(bounds.top, obs.top)).coerceAtLeast(0f)
            (w * h).toDouble()
        }.toFloat()

    private fun findNonOverlappingY(
        desiredTop: Float,
        rowHeight: Float,
        minTop: Float,
        maxBottom: Float,
        forbidden: List<Pair<Float, Float>>,
        preferBelow: Boolean? = null,
        clockSpan: Pair<Float, Float>? = null
    ): Float {
        val absoluteMaxTop = (maxBottom - rowHeight).coerceAtLeast(minTop)
        val merged = forbidden
            .filter { it.second > it.first }
            .sortedBy { it.first }
            .fold(mutableListOf<Pair<Float, Float>>()) { acc, interval ->
                val last = acc.lastOrNull()
                if (last == null || interval.first > last.second) {
                    acc.add(interval)
                } else {
                    acc[acc.lastIndex] = last.first to maxOf(last.second, interval.second)
                }
                acc
            }

        val free = mutableListOf<Pair<Float, Float>>()
        var cursor = minTop
        for ((start, end) in merged) {
            if (start > cursor) {
                free += cursor to minOf(start, maxBottom)
            }
            cursor = maxOf(cursor, end)
            if (cursor >= maxBottom) break
        }
        if (cursor < maxBottom) {
            free += cursor to maxBottom
        }

        val fitting = free.filter { (s, e) -> e - s >= rowHeight - EPS }
        if (fitting.isNotEmpty()) {
            val sideFiltered = if (preferBelow != null && clockSpan != null) {
                fitting.filter { (s, e) ->
                    if (preferBelow) s >= clockSpan.second - EPS else e <= clockSpan.first + EPS
                }
            } else {
                emptyList()
            }
            val pool = sideFiltered.ifEmpty { fitting }
            val bestInterval = pool.minBy { (s, e) ->
                val maxAllowedInInterval = (e - rowHeight).coerceAtLeast(s)
                val clamped = desiredTop.coerceIn(s, maxAllowedInInterval)
                abs(clamped - desiredTop)
            }
            val maxAllowedInInterval = (bestInterval.second - rowHeight).coerceAtLeast(bestInterval.first)
            return desiredTop.coerceIn(bestInterval.first, maxAllowedInInterval)
        }

        return desiredTop.coerceIn(minTop, absoluteMaxTop)
    }

    private fun gridVerticalSpan(
        composition: Composition,
        itemState: ItemState,
        faceOf: (Int) -> ClockFace?,
        large: Boolean,
        zoneGap: Float
    ): Pair<Float, Float>? {
        val grid = gridItems(composition, faceOf)
        if (grid.isEmpty()) return null
        val y = grid.firstNotNullOfOrNull { it.placement(itemState)?.y } ?: return null
        val h = grid.maxOf { heightOf(faceOf(it.style)) }.coerceAtLeast(FaceGrid.CELL)
        return (y - zoneGap) to (y + h + zoneGap)
    }

    private fun inlineVerticalSpan(
        composition: Composition,
        itemState: ItemState,
        faceOf: (Int) -> ClockFace?,
        large: Boolean,
        zoneGap: Float
    ): Pair<Float, Float>? {
        val inline = inlineItems(composition, faceOf)
        if (inline.isEmpty()) return null
        val y = inline.firstNotNullOfOrNull { it.placement(itemState)?.y } ?: return null
        val h = inline.maxOf { heightOf(faceOf(it.style)) }.coerceAtLeast(INLINE_HEIGHT)
        return (y - zoneGap) to (y + h + zoneGap)
    }

    private fun applyZonePlacements(
        composition: Composition,
        itemState: ItemState,
        placements: Map<Int, FacePlacement>
    ): Composition = composition.copy(
        items = composition.items.map { item ->
            val p = placements[item.id] ?: return@map item
            when (itemState) {
                ItemState.LARGE -> item.copy(large = p)
                ItemState.SMALL -> item.copy(small = p)
                ItemState.AOD_LARGE -> item.copy(aodLarge = p)
                ItemState.AOD_SMALL -> item.copy(aodSmall = p)
            }
        }
    )

    private fun currentGridCenter(
        items: List<CompositionItem>,
        widths: List<Float>,
        state: ItemState
    ): FacePlacement? {
        val placed = items.zip(widths).mapNotNull { (item, w) ->
            item.placement(state)?.let { Triple(item, w, it) }
        }
        val (_, firstWidth, firstPlacement) = placed.firstOrNull() ?: return null
        if (placed.size == 1) {
            val centerX = when (firstPlacement.anchor) {
                FaceAlign.START -> firstPlacement.x + firstWidth / 2f
                FaceAlign.CENTER -> firstPlacement.x
                FaceAlign.END -> firstPlacement.x - firstWidth / 2f
            }
            return FacePlacement(centerX, firstPlacement.y, FaceAlign.CENTER)
        }
        val firstLeft = when (firstPlacement.anchor) {
            FaceAlign.START -> firstPlacement.x
            FaceAlign.CENTER -> firstPlacement.x - firstWidth / 2f
            FaceAlign.END -> firstPlacement.x - firstWidth
        }
        val (_, lastWidth, lastPlacement) = placed.last()
        val lastRight = when (lastPlacement.anchor) {
            FaceAlign.START -> lastPlacement.x + lastWidth
            FaceAlign.CENTER -> lastPlacement.x + lastWidth / 2f
            FaceAlign.END -> lastPlacement.x
        }
        return FacePlacement((firstLeft + lastRight) / 2f, firstPlacement.y, FaceAlign.CENTER)
    }

    private fun currentInlineCenter(
        items: List<CompositionItem>,
        state: ItemState
    ): FacePlacement? {
        val placed = items.mapNotNull { it.placement(state) }
        val first = placed.firstOrNull() ?: return null
        if (placed.size == 1) return first
        val second = placed[1]
        return FacePlacement((first.x + second.x) / 2f, first.y, FaceAlign.CENTER)
    }

    private fun clampCenterX(centerX: Float, rowWidth: Float): Float {
        val half = rowWidth / 2f
        val min = EDGE + half
        val max = 1f - EDGE - half
        return if (min <= max) centerX.coerceIn(min, max) else 0.5f
    }

    private fun heightOf(face: ClockFace?): Float {
        face ?: return FaceGrid.CELL
        val root = face.large as? StackNode
        val fallback = if (zoneOf(face) == WidgetZone.INLINE_1X2) INLINE_HEIGHT else FaceGrid.CELL
        return root?.height ?: fallback
    }

    private data class EstBox(
        val width: Float,
        val height: Float,
        val minLeft: Float = 0f,
        val minTop: Float = 0f,
        val maxRight: Float = width,
        val maxBottom: Float = height
    )

    private fun estimateNode(node: FaceNode, layersById: Map<String, FaceLayer>): EstBox? {
        if (node.placement.hidden) return null
        return when (node) {
            is LayerNode -> {
                if (node.alpha <= 0f) return null
                val layer = layersById[node.id]
                val w: Float
                val h: Float
                if (layer?.isHand == true) {
                    w = node.size
                    h = node.size
                } else if (layer?.isShape == true) {
                    w = node.size
                    h = node.size / layer.aspect.coerceAtLeast(0.05f)
                } else {
                    val isSymbol = layer?.isIcon == true || layer?.font?.startsWith("res:symbols") == true
                    val capRatio = if (isSymbol) 1f else ESTIMATE_CAP_RATIO
                    val lineHeight = node.lineHeight ?: layer?.lineHeight ?: 1f
                    val stretch = node.stretch ?: 1f
                    val stretchX = node.stretchX ?: 1f
                    val descent = if (layer == null || layer.isTime || layer.isShape) 0f else node.size * ESTIMATE_TEXT_DESCENT
                    val tracking = node.tracking ?: layer?.tracking ?: 0f
                    w = estimateWidthRatio(layer, tracking) * stretchX * node.size
                    h = capRatio * stretch * node.size * lineHeight + descent
                }
                EstBox(width = w, height = h)
            }

            is RowNode -> {
                val measured = node.children.map { child -> child to estimateNode(child, layersById) }
                val boxes = measured.mapNotNull { it.second }
                if (boxes.isEmpty()) return EstBox(0f, 0f)
                val base = boxes.maxOf { it.height }
                var x = 0f
                var minL = Float.MAX_VALUE
                var maxR = -Float.MAX_VALUE
                var minT = Float.MAX_VALUE
                var maxB = -Float.MAX_VALUE
                measured.forEach { (child, box) ->
                    val b = box ?: EstBox(0f, 0f)
                    val childAlign = (child as? LayerNode)?.align ?: node.align
                    val left = x + child.placement.x
                    val top = crossOffset(childAlign, base, b.height) + child.placement.y
                    minL = min(minL, left + b.minLeft)
                    maxR = max(maxR, left + b.maxRight)
                    minT = min(minT, top + b.minTop)
                    maxB = max(maxB, top + b.maxBottom)
                    x += b.width + node.gap
                }
                EstBox(maxR - minL, maxB - minT, 0f, 0f, maxR - minL, maxB - minT)
            }

            is ColumnNode -> {
                val measured = node.children.map { child -> child to estimateNode(child, layersById) }
                val boxes = measured.mapNotNull { it.second }
                if (boxes.isEmpty()) return EstBox(0f, 0f)
                val base = boxes.maxOf { it.width }
                var y = 0f
                var minL = Float.MAX_VALUE
                var maxR = -Float.MAX_VALUE
                var minT = Float.MAX_VALUE
                var maxB = -Float.MAX_VALUE
                measured.forEach { (child, box) ->
                    val b = box ?: EstBox(0f, 0f)
                    val childAlign = (child as? LayerNode)?.align ?: node.align
                    val left = crossOffset(childAlign, base, b.width) + child.placement.x
                    val top = y + child.placement.y
                    minL = min(minL, left + b.minLeft)
                    maxR = max(maxR, left + b.maxRight)
                    minT = min(minT, top + b.minTop)
                    maxB = max(maxB, top + b.maxBottom)
                    y += b.height + node.gap
                }
                EstBox(maxR - minL, maxB - minT, 0f, 0f, maxR - minL, maxB - minT)
            }

            is StackNode -> if (node.width != null) {
                val h = node.height ?: (node.children.mapNotNull { estimateNode(it, layersById)?.height }.maxOrNull() ?: 0f)
                EstBox(node.width, h)
            } else {
                var minL = Float.MAX_VALUE
                var minT = Float.MAX_VALUE
                var maxR = -Float.MAX_VALUE
                var maxB = -Float.MAX_VALUE
                var any = false
                node.children.forEach { child ->
                    val b = estimateNode(child, layersById) ?: return@forEach
                    val anchor = child.placement.x
                    val left = when (child.placement.anchor) {
                        FaceAlign.START -> anchor
                        FaceAlign.CENTER -> anchor - b.width / 2f
                        FaceAlign.END -> anchor - b.width
                    }
                    val top = child.placement.y
                    minL = min(minL, left + b.minLeft)
                    minT = min(minT, top + b.minTop)
                    maxR = max(maxR, left + b.maxRight)
                    maxB = max(maxB, top + b.maxBottom)
                    any = true
                }
                if (!any) EstBox(0f, 0f) else EstBox(
                    width = max(maxR, 0f),
                    height = max(maxB, 0f),
                    minLeft = minL,
                    minTop = minT,
                    maxRight = maxR,
                    maxBottom = maxB
                )
            }
        }
    }

    private fun estimateWidthRatio(layer: FaceLayer?, tracking: Float): Float {
        if (layer == null) return 1.2f
        val chars = when (layer.type) {
            FaceLayerType.HOURS, FaceLayerType.MINUTES -> 2
            FaceLayerType.TIME -> 5
            FaceLayerType.HOUR_TENS, FaceLayerType.HOUR_ONES,
            FaceLayerType.MINUTE_TENS, FaceLayerType.MINUTE_ONES,
            FaceLayerType.COLON -> 1
            FaceLayerType.DATE -> layer.format.length.coerceIn(4, 12)
            FaceLayerType.TEXT -> layer.format.length.coerceIn(1, 14)
            else -> 4
        }
        val perChar = if (layer.isTime) 0.62f else 0.56f
        return (chars * perChar + (chars - 1).coerceAtLeast(0) * tracking).coerceAtLeast(0.3f)
    }

    private fun crossOffset(align: FaceAlign, container: Float, child: Float): Float = when (align) {
        FaceAlign.START -> 0f
        FaceAlign.CENTER -> (container - child) / 2f
        FaceAlign.END -> container - child
    }

    private const val ESTIMATE_CAP_RATIO = 0.82f
    private const val ESTIMATE_TEXT_DESCENT = 0.24f
}
