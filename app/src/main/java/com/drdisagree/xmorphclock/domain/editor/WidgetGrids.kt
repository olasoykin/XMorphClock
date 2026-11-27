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
    const val MAX_GRID_COLS = FaceGrid.ROW_CELLS
    const val MAX_GRID_ROWS = 6
    const val MAX_INLINE_ROWS = 6
    const val INLINE_ITEM_GAP = 0.036f
    const val DEFAULT_INLINE_ITEM_WIDTH = 0.36f

    private const val INLINE_CATEGORY = "inline"
    private const val EDGE = 0.0225f
    private const val MIN_TOP = 0.02f
    private const val INLINE_HEIGHT = 0.08f
    private const val GRID_GAP = 0.04f
    private const val ZONE_GAP = 0.018f
    const val INLINE_PLACEHOLDER_RATIO = 0.72f
    private const val DEFAULT_BOTTOM = 1.9f
    private const val EPS = 0.0005f
    private const val AREA_EPS = 1e-6f
    private const val PUSH_SUBSTEP = 0.04f
    private const val MIN_AUTO_CLOCK_SCALE = 0.45f
    private const val MIN_AUTO_CLOCK_STRETCH = 0.55f
    private const val MIN_AUTO_CLOCK_HEIGHT = 0.16f

    data class GridRow(
        val items: List<CompositionItem>,
        val widths: List<Float>,
        val width: Float,
        val height: Float
    )

    data class InlineRow(
        val items: List<CompositionItem>,
        val widths: List<Float>,
        val totalWidth: Float,
        val height: Float
    )

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

    fun packGridRows(
        items: List<CompositionItem>,
        cols: Int,
        faceOf: (Int) -> ClockFace?
    ): List<GridRow> {
        if (items.isEmpty()) return emptyList()
        val effectiveCols = cols.coerceIn(1, MAX_GRID_COLS)
        val cellW = FaceGrid.CELL
        val gap = FaceGrid.GAP
        val rows = ArrayList<GridRow>()
        var rowItems = ArrayList<CompositionItem>()
        var rowWidths = ArrayList<Float>()
        var rowCells = 0

        fun flushRow() {
            if (rowItems.isEmpty()) return
            val w = rowWidths.sum() + gap * (rowItems.size - 1)
            val h = rowItems.maxOf { heightOf(faceOf(it.style)) }.coerceAtLeast(cellW)
            rows += GridRow(rowItems, rowWidths, w, h)
            rowItems = ArrayList()
            rowWidths = ArrayList()
            rowCells = 0
        }

        for (item in items) {
            val span = (faceOf(item.style)?.let(::spanOf) ?: 1).coerceIn(1, effectiveCols)
            if (rowItems.isNotEmpty() && rowCells + span > effectiveCols) {
                flushRow()
            }
            rowItems += item
            rowWidths += FaceGrid.width(span)
            rowCells += span
        }
        flushRow()
        return rows
    }

    fun minColsForGrid(composition: Composition, faceOf: (Int) -> ClockFace?): Int {
        val items = gridItems(composition, faceOf)
        if (items.isEmpty()) return 1
        val maxSpan = items.maxOf { faceOf(it.style)?.let(::spanOf) ?: 1 }.coerceIn(1, MAX_GRID_COLS)
        val rowsAllowed = composition.gridRows.coerceIn(1, MAX_GRID_ROWS)
        for (cols in maxSpan..MAX_GRID_COLS) {
            if (packGridRows(items, cols, faceOf).size <= rowsAllowed) return cols
        }
        return MAX_GRID_COLS
    }

    fun minRowsForGrid(composition: Composition, cols: Int, faceOf: (Int) -> ClockFace?): Int {
        val items = gridItems(composition, faceOf)
        if (items.isEmpty()) return 1
        return packGridRows(items, cols.coerceIn(1, MAX_GRID_COLS), faceOf).size.coerceIn(1, MAX_GRID_ROWS)
    }

    fun maxFittingGridRows(
        composition: Composition,
        faceOf: (Int) -> ClockFace?,
        maxY: Float = DEFAULT_BOTTOM
    ): Int {
        val maxBottom = (maxY - EDGE).coerceAtLeast(MIN_TOP + 0.1f)
        val avail = (maxBottom - MIN_TOP - 0.24f).coerceAtLeast(FaceGrid.CELL)
        val items = gridItems(composition, faceOf)
        val rowH = items.maxOfOrNull { heightOf(faceOf(it.style)) }?.coerceAtLeast(FaceGrid.CELL) ?: FaceGrid.CELL
        val step = rowH + FaceGrid.GAP
        val fit = ((avail + FaceGrid.GAP) / step).toInt().coerceIn(1, MAX_GRID_ROWS)
        return maxOf(fit, minRowsForGrid(composition, composition.gridCols, faceOf))
    }

    fun estimateInlineWidth(
        item: CompositionItem,
        faceOf: (Int) -> ClockFace?,
        measuredWidths: Map<Int, Float> = emptyMap()
    ): Float {
        measuredWidths[item.id]?.takeIf { it.isFinite() && it > 0.02f }?.let {
            return it.coerceIn(0.08f, 1f - EDGE * 2f)
        }
        val face = faceOf(item.style) ?: return DEFAULT_INLINE_ITEM_WIDTH
        val root = face.large
        (root as? StackNode)?.width?.takeIf { it > 0.02f }?.let {
            return it.coerceIn(0.08f, 1f - EDGE * 2f)
        }
        val layersById = face.layers.associateBy { it.id }
        val estimated = estimateNode(root, layersById)?.width
        return (estimated ?: DEFAULT_INLINE_ITEM_WIDTH).coerceIn(0.10f, 1f - EDGE * 2f)
    }

    fun packInlineRows(
        items: List<CompositionItem>,
        faceOf: (Int) -> ClockFace?,
        measuredWidths: Map<Int, Float> = emptyMap(),
        maxRowWidth: Float = 1f - EDGE * 2f
    ): List<InlineRow> {
        if (items.isEmpty()) return emptyList()
        val rows = ArrayList<InlineRow>()
        var rowItems = ArrayList<CompositionItem>()
        var rowWidths = ArrayList<Float>()
        var runningW = 0f

        fun flushRow() {
            if (rowItems.isEmpty()) return
            val totalW = rowWidths.sum() + INLINE_ITEM_GAP * (rowItems.size - 1)
            val rowH = rowItems.maxOf { heightOf(faceOf(it.style)) }.coerceAtLeast(INLINE_HEIGHT)
            rows += InlineRow(rowItems, rowWidths, totalW, rowH)
            rowItems = ArrayList()
            rowWidths = ArrayList()
            runningW = 0f
        }

        for (item in items) {
            val w = estimateInlineWidth(item, faceOf, measuredWidths).coerceAtMost(maxRowWidth)
            val needed = if (rowItems.isEmpty()) w else runningW + INLINE_ITEM_GAP + w
            if (rowItems.isNotEmpty() && needed > maxRowWidth + EPS) {
                flushRow()
            }
            runningW = if (rowItems.isEmpty()) w else runningW + INLINE_ITEM_GAP + w
            rowItems += item
            rowWidths += w
        }
        flushRow()
        return rows
    }

    fun minRowsForInline(
        composition: Composition,
        faceOf: (Int) -> ClockFace?,
        measuredWidths: Map<Int, Float> = emptyMap()
    ): Int {
        val items = inlineItems(composition, faceOf)
        if (items.isEmpty()) return 1
        return packInlineRows(items, faceOf, measuredWidths).size.coerceIn(1, MAX_INLINE_ROWS)
    }

    fun maxFittingInlineRows(
        composition: Composition,
        faceOf: (Int) -> ClockFace?,
        maxY: Float = DEFAULT_BOTTOM,
        measuredWidths: Map<Int, Float> = emptyMap()
    ): Int {
        val maxBottom = (maxY - EDGE).coerceAtLeast(MIN_TOP + 0.1f)
        val avail = (maxBottom - MIN_TOP - 0.24f).coerceAtLeast(INLINE_HEIGHT)
        val items = inlineItems(composition, faceOf)
        val rowH = items.maxOfOrNull { heightOf(faceOf(it.style)) }?.coerceAtLeast(INLINE_HEIGHT) ?: INLINE_HEIGHT
        val step = rowH + FaceGrid.GAP
        val fit = ((avail + FaceGrid.GAP) / step).toInt().coerceIn(1, MAX_INLINE_ROWS)
        return maxOf(fit, minRowsForInline(composition, faceOf, measuredWidths))
    }

    fun canAdd(
        composition: Composition,
        face: ClockFace,
        faceOf: (Int) -> ClockFace?,
        maxY: Float = DEFAULT_BOTTOM,
        measuredInlineWidths: Map<Int, Float> = emptyMap()
    ): Boolean = when (zoneOf(face)) {
        WidgetZone.GRID_1X4 -> {
            val cols = composition.gridCols.coerceIn(1, MAX_GRID_COLS)
            val rows = composition.gridRows.coerceIn(1, MAX_GRID_ROWS)
            val itemSpan = spanOf(face)
            if (itemSpan > cols) {
                false
            } else {
                val existing = gridItems(composition, faceOf)
                val dummyStyle = Int.MIN_VALUE
                val simulated = existing + CompositionItem(id = Int.MIN_VALUE, style = dummyStyle)
                val packed = packGridRows(simulated, cols) { style ->
                    if (style == dummyStyle) face else faceOf(style)
                }
                packed.size <= rows && usedGridCells(composition, faceOf) + itemSpan <= cols * rows
            }
        }

        WidgetZone.INLINE_1X2 -> {
            val existing = inlineItems(composition, faceOf)
            val dummyStyle = Int.MIN_VALUE
            val simulated = existing + CompositionItem(id = Int.MIN_VALUE, style = dummyStyle)
            val packed = packInlineRows(
                items = simulated,
                faceOf = { style -> if (style == dummyStyle) face else faceOf(style) },
                measuredWidths = measuredInlineWidths
            )
            if (packed.size > MAX_INLINE_ROWS) {
                false
            } else {
                val gap = FaceGrid.GAP
                val inlineH = packed.sumOf { it.height.toDouble() }.toFloat() + gap * (packed.size - 1).coerceAtLeast(0)
                val gridRows = packGridRows(gridItems(composition, faceOf), composition.gridCols, faceOf)
                val gridH = if (gridRows.isEmpty()) 0f else {
                    gridRows.sumOf { it.height.toDouble() }.toFloat() + gap * (gridRows.size - 1).coerceAtLeast(0) + GRID_GAP
                }
                val maxBottom = (maxY - EDGE).coerceAtLeast(MIN_TOP + 0.1f)
                val minClockReserve = 0.22f
                inlineH + gridH <= (maxBottom - MIN_TOP - minClockReserve).coerceAtLeast(INLINE_HEIGHT)
            }
        }
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
        widgetsAbove: Boolean = false,
        measuredInlineWidths: Map<Int, Float> = emptyMap(),
        preserveExistingSide: Boolean = false
    ): Composition {
        val gap = FaceGrid.GAP
        val defaultZoneGap = GRID_GAP
        val maxBottom = (maxY - EDGE).coerceAtLeast(MIN_TOP + 0.1f)
        val clockCenterY = clockBounds?.let { (it.top + it.bottom) / 2f } ?: (clockBottomY - 0.15f)

        return when (zone) {
            WidgetZone.GRID_1X4 -> {
                val items = gridItems(composition, faceOf)
                if (items.isEmpty()) return composition
                val rows = packGridRows(items, composition.gridCols, faceOf)
                val totalH = rows.sumOf { it.height.toDouble() }.toFloat() + gap * (rows.size - 1).coerceAtLeast(0)
                val maxRowW = rows.maxOf { it.width }

                val existingBoxes = items.mapNotNull { widgetBoundsOf(it, itemState, faceOf) }
                val existingCenterY = if (existingBoxes.isNotEmpty()) {
                    (existingBoxes.minOf { it.top } + existingBoxes.maxOf { it.bottom }) / 2f
                } else null
                val effectiveAbove = if (preserveExistingSide && overrideCenter == null && existingCenterY != null) {
                    existingCenterY < clockCenterY
                } else {
                    widgetsAbove
                }

                val existingOrigin = overrideCenter ?: currentGridCenter(items, itemState, faceOf)
                val desiredCenterX = if (overrideCenter != null) overrideCenter.x else if (centerHorizontally) 0.5f else (existingOrigin?.x ?: 0.5f)
                val centerX = clampCenterX(desiredCenterX, maxRowW)

                val defaultY = if (effectiveAbove) {
                    ((clockBounds?.top ?: (clockBottomY - 0.4f)) - defaultZoneGap - totalH).coerceAtLeast(MIN_TOP)
                } else {
                    (clockBounds?.bottom ?: clockBottomY) + defaultZoneGap
                }
                val desiredY = if (overrideCenter != null) {
                    overrideCenter.y
                } else if (existingOrigin != null) {
                    val existingAbove = existingCenterY?.let { it < clockCenterY } ?: if (effectiveAbove) {
                        existingOrigin.y < (clockBounds?.top ?: (clockBottomY - 0.4f))
                    } else {
                        existingOrigin.y > (clockBounds?.bottom ?: clockBottomY)
                    }
                    if (existingAbove == effectiveAbove) existingOrigin.y else defaultY
                } else {
                    defaultY
                }
                val topY = findZoneY(
                    desiredTop = desiredY,
                    rowHeight = totalH,
                    minTop = MIN_TOP,
                    maxBottom = maxBottom,
                    clockBounds = clockBounds,
                    clockBottomY = clockBottomY,
                    otherZoneSpan = { spanGap -> inlineVerticalSpan(composition, itemState, faceOf, spanGap, measuredInlineWidths) },
                    preferBelow = if (overrideCenter == null) !effectiveAbove else null
                )

                val placements = HashMap<Int, FacePlacement>(items.size)
                var rowY = topY
                rows.forEach { row ->
                    var cursor = centerX - row.width / 2f
                    row.items.forEachIndexed { idx, item ->
                        val w = row.widths[idx]
                        placements[item.id] = FacePlacement(x = cursor + w / 2f, y = rowY, anchor = FaceAlign.CENTER)
                        cursor += w + gap
                    }
                    rowY += row.height + gap
                }
                applyZonePlacements(composition, itemState, placements)
            }

            WidgetZone.INLINE_1X2 -> {
                val items = inlineItems(composition, faceOf)
                if (items.isEmpty()) return composition
                val rows = packInlineRows(items, faceOf, measuredInlineWidths)
                val totalH = rows.sumOf { it.height.toDouble() }.toFloat() + gap * (rows.size - 1).coerceAtLeast(0)
                val maxRowW = rows.maxOf { it.totalWidth }
                val existingBoxes = items.mapNotNull { widgetBoundsOf(it, itemState, faceOf, measuredInlineWidths) }
                val existingCenterY = if (existingBoxes.isNotEmpty()) {
                    (existingBoxes.minOf { it.top } + existingBoxes.maxOf { it.bottom }) / 2f
                } else null
                val effectiveAbove = if (preserveExistingSide && overrideCenter == null && existingCenterY != null) {
                    existingCenterY < clockCenterY
                } else {
                    widgetsAbove
                }

                val existingOrigin = overrideCenter ?: currentInlineCenter(items, itemState, faceOf, measuredInlineWidths)
                val gridSpan = gridVerticalSpan(composition, itemState, faceOf, defaultZoneGap)
                val defaultInlineY = if (effectiveAbove) {
                    gridSpan?.first?.minus(totalH) ?: ((clockBounds?.top ?: (clockBottomY - 0.4f)) - defaultZoneGap - totalH)
                } else {
                    gridSpan?.second ?: (clockBounds?.bottom?.plus(defaultZoneGap) ?: (clockBottomY + defaultZoneGap + FaceGrid.CELL + defaultZoneGap))
                }

                val desiredInlineY = if (overrideCenter != null) {
                    overrideCenter.y
                } else if (existingOrigin != null) {
                    val existingAbove = existingCenterY?.let { it < clockCenterY } ?: if (effectiveAbove) {
                        existingOrigin.y < (clockBounds?.top ?: (clockBottomY - 0.4f))
                    } else {
                        existingOrigin.y > (clockBounds?.bottom ?: clockBottomY)
                    }
                    if (existingAbove == effectiveAbove) existingOrigin.y else defaultInlineY
                } else {
                    defaultInlineY
                }
                val topY = findZoneY(
                    desiredTop = desiredInlineY,
                    rowHeight = totalH,
                    minTop = MIN_TOP,
                    maxBottom = maxBottom,
                    clockBounds = clockBounds,
                    clockBottomY = clockBottomY,
                    otherZoneSpan = { spanGap -> gridVerticalSpan(composition, itemState, faceOf, spanGap) },
                    preferBelow = if (overrideCenter == null) !effectiveAbove else null
                )

                val desiredCenterX = if (overrideCenter != null) overrideCenter.x else if (centerHorizontally) 0.5f else (existingOrigin?.x ?: 0.5f)
                val centerX = clampCenterX(desiredCenterX, maxRowW)
                val placements = HashMap<Int, FacePlacement>(items.size)
                var rowY = topY
                rows.forEach { row ->
                    var cursor = centerX - row.totalWidth / 2f
                    row.items.forEachIndexed { idx, item ->
                        val w = row.widths[idx]
                        placements[item.id] = FacePlacement(x = cursor + w / 2f, y = rowY, anchor = FaceAlign.CENTER)
                        cursor += w + INLINE_ITEM_GAP
                    }
                    rowY += row.height + gap
                }
                applyZonePlacements(composition, itemState, placements)
            }
        }
    }

    fun repackInlineHorizontally(
        composition: Composition,
        itemState: ItemState,
        faceOf: (Int) -> ClockFace?,
        measuredInlineWidths: Map<Int, Float> = emptyMap(),
        centerHorizontally: Boolean = false
    ): Composition {
        val items = inlineItems(composition, faceOf)
        if (items.isEmpty()) return composition
        val rows = packInlineRows(items, faceOf, measuredInlineWidths)
        val existingBoxes = items.mapNotNull { widgetBoundsOf(it, itemState, faceOf, measuredInlineWidths) }
        val baseTopY = existingBoxes.minOfOrNull { it.top } ?: return composition
        val existingCenterX = if (existingBoxes.isNotEmpty()) {
            (existingBoxes.minOf { it.left } + existingBoxes.maxOf { it.right }) / 2f
        } else 0.5f
        val maxRowW = rows.maxOf { it.totalWidth }
        val centerX = clampCenterX(if (centerHorizontally) 0.5f else existingCenterX, maxRowW)
        val gap = FaceGrid.GAP
        val placements = HashMap<Int, FacePlacement>(items.size)
        var rowY = baseTopY
        rows.forEach { row ->
            var cursor = centerX - row.totalWidth / 2f
            row.items.forEachIndexed { idx, item ->
                val w = row.widths[idx]
                placements[item.id] = FacePlacement(x = cursor + w / 2f, y = rowY, anchor = FaceAlign.CENTER)
                cursor += w + INLINE_ITEM_GAP
            }
            rowY += row.height + gap
        }
        return applyZonePlacements(composition, itemState, placements)
    }

    fun isZoneAboveClock(
        composition: Composition,
        zone: WidgetZone,
        itemState: ItemState,
        faceOf: (Int) -> ClockFace?,
        clockBounds: ItemBounds?,
        measuredInlineWidths: Map<Int, Float> = emptyMap()
    ): Boolean? {
        val clock = clockBounds ?: return null
        val items = when (zone) {
            WidgetZone.GRID_1X4 -> gridItems(composition, faceOf)
            WidgetZone.INLINE_1X2 -> inlineItems(composition, faceOf)
        }
        val boxes = items.mapNotNull { widgetBoundsOf(it, itemState, faceOf, measuredInlineWidths) }
        if (boxes.isEmpty()) return null
        val zoneCenterY = (boxes.minOf { it.top } + boxes.maxOf { it.bottom }) / 2f
        val clockCenterY = (clock.top + clock.bottom) / 2f
        return zoneCenterY < clockCenterY
    }

    fun areWidgetsAboveClock(
        composition: Composition,
        itemState: ItemState,
        faceOf: (Int) -> ClockFace?,
        clockBounds: ItemBounds?,
        preferredTarget: LockTarget? = null,
        measuredInlineWidths: Map<Int, Float> = emptyMap()
    ): Boolean? {
        val clock = clockBounds ?: return null
        val clockCenterY = (clock.top + clock.bottom) / 2f
        val gridBoxes = gridItems(composition, faceOf)
            .mapNotNull { widgetBoundsOf(it, itemState, faceOf, measuredInlineWidths) }
        val inlineBoxes = inlineItems(composition, faceOf)
            .mapNotNull { widgetBoundsOf(it, itemState, faceOf, measuredInlineWidths) }
        val preferredBoxes = when (preferredTarget) {
            LockTarget.WIDGET_GRID -> gridBoxes
            LockTarget.INLINE_GRID -> inlineBoxes
            else -> emptyList()
        }
        val targetBoxes = if (preferredBoxes.isNotEmpty()) preferredBoxes else (gridBoxes + inlineBoxes)
        if (targetBoxes.isEmpty()) return null
        val zoneCenterY = (targetBoxes.minOf { it.top } + targetBoxes.maxOf { it.bottom }) / 2f
        return zoneCenterY < clockCenterY
    }

    fun syncWidgetsAboveFlag(
        composition: Composition,
        itemState: ItemState,
        faceOf: (Int) -> ClockFace?,
        clockBounds: ItemBounds?,
        preferredTarget: LockTarget? = null,
        measuredInlineWidths: Map<Int, Float> = emptyMap()
    ): Composition {
        val above = areWidgetsAboveClock(
            composition = composition,
            itemState = itemState,
            faceOf = faceOf,
            clockBounds = clockBounds,
            preferredTarget = preferredTarget,
            measuredInlineWidths = measuredInlineWidths
        ) ?: return composition
        return if (composition.widgetsAbove == above) composition else composition.copy(widgetsAbove = above)
    }

    fun ensureClockClearanceForWidgets(
        composition: Composition,
        itemState: ItemState,
        faceOf: (Int) -> ClockFace?,
        clockBounds: ItemBounds?,
        maxY: Float = DEFAULT_BOTTOM,
        measuredInlineWidths: Map<Int, Float> = emptyMap(),
        clockFace: ClockFace? = null
    ): Pair<Composition, ItemBounds?> {
        val clock = clockBounds ?: return composition to null
        val clockItem = composition.clockItem ?: return composition to clock
        val grid = gridItems(composition, faceOf)
        val inline = inlineItems(composition, faceOf)
        if (grid.isEmpty() && inline.isEmpty()) return composition to clock

        val gap = FaceGrid.GAP
        val zoneGap = GRID_GAP
        val maxBottom = (maxY - EDGE).coerceAtLeast(MIN_TOP + 0.1f)
        val gridRows = packGridRows(grid, composition.gridCols, faceOf)
        val inlineRows = packInlineRows(inline, faceOf, measuredInlineWidths)
        val rowHGrid = if (gridRows.isEmpty()) 0f else {
            gridRows.sumOf { it.height.toDouble() }.toFloat() + gap * (gridRows.size - 1).coerceAtLeast(0)
        }
        val rowHInline = if (inlineRows.isEmpty()) 0f else {
            inlineRows.sumOf { it.height.toDouble() }.toFloat() + gap * (inlineRows.size - 1).coerceAtLeast(0)
        }
        val totalH = rowHGrid + rowHInline + (if (grid.isNotEmpty() && inline.isNotEmpty()) zoneGap else 0f)
        val effectiveAbove = if (composition.gridDocked || composition.inlineDocked) {
            composition.widgetsAbove
        } else {
            areWidgetsAboveClock(
                composition = composition,
                itemState = itemState,
                faceOf = faceOf,
                clockBounds = clock,
                measuredInlineWidths = measuredInlineWidths
            ) ?: composition.widgetsAbove
        }

        val clockShiftY = if (effectiveAbove) {
            val spaceAbove = clock.top - MIN_TOP - zoneGap
            if (spaceAbove < totalH) {
                val neededShift = totalH - spaceAbove
                val maxAllowedShift = (maxBottom - clock.bottom - 0.02f).coerceAtLeast(0f)
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
        val shifted = applyMovedItems(composition, setOf(clockItem.id), itemState, 0f, clockShiftY) {
            clockFace?.let { defaultClockPlacement(it, itemState) } ?: FacePlacement()
        }
        val shiftedBounds = ItemBounds(clock.left, clock.top + clockShiftY, clock.right, clock.bottom + clockShiftY)
        return shifted to shiftedBounds
    }

    fun fitClockScaleForComposition(
        composition: Composition,
        clockFace: ClockFace,
        faceOf: (Int) -> ClockFace?,
        largeMaxY: Float = DEFAULT_BOTTOM,
        smallMaxY: Float = DEFAULT_BOTTOM,
        largeClockBounds: ItemBounds? = null,
        smallClockBounds: ItemBounds? = null,
        measuredInlineWidths: Map<Int, Float> = emptyMap()
    ): Composition {
        var result = composition
        val clockItemInitial = result.clockItem ?: return result
        val grid = gridItems(result, faceOf)
        val inline = inlineItems(result, faceOf)
        val gap = FaceGrid.GAP
        val zoneGap = GRID_GAP
        val gridRows = packGridRows(grid, result.gridCols, faceOf)
        val inlineRows = packInlineRows(inline, faceOf, measuredInlineWidths)
        val rowHGrid = if (gridRows.isEmpty()) 0f else {
            gridRows.sumOf { it.height.toDouble() }.toFloat() + gap * (gridRows.size - 1).coerceAtLeast(0)
        }
        val rowHInline = if (inlineRows.isEmpty()) 0f else {
            inlineRows.sumOf { it.height.toDouble() }.toFloat() + gap * (inlineRows.size - 1).coerceAtLeast(0)
        }
        val hasWidgets = grid.isNotEmpty() || inline.isNotEmpty()
        val widgetReserveH = if (!hasWidgets) {
            0f
        } else {
            rowHGrid + rowHInline + (if (grid.isNotEmpty() && inline.isNotEmpty()) zoneGap else 0f) + zoneGap
        }

        val maxAvailW = (1f - EDGE * 2f).coerceAtLeast(0.2f)
        val states = buildList {
            add(Triple(ItemState.LARGE, largeMaxY, largeClockBounds))
            add(Triple(ItemState.SMALL, smallMaxY, smallClockBounds))
            if (clockFace.aodLarge != null || clockItemInitial.aodLarge != null) {
                add(Triple(ItemState.AOD_LARGE, largeMaxY, null))
            }
            if (clockFace.aodSmall != null || clockItemInitial.aodSmall != null) {
                add(Triple(ItemState.AOD_SMALL, smallMaxY, null))
            }
        }

        states.forEach { (state, maxY, measuredBounds) ->
            val clockItem = result.clockItem ?: return@forEach
            val maxBottom = (maxY - EDGE).coerceAtLeast(MIN_TOP + 0.1f)
            val maxAvailH = if (hasWidgets) {
                (maxBottom - MIN_TOP - widgetReserveH).coerceAtLeast(MIN_AUTO_CLOCK_HEIGHT)
            } else {
                (maxBottom - MIN_TOP).coerceAtLeast(MIN_AUTO_CLOCK_HEIGHT)
            }

            val currentScale = clockItem.scale(state)
            var currentWStretch = clockItem.widthStretch(state)
            var currentHStretch = clockItem.heightStretch(state)
            if (clockFace.isAnalog && (currentWStretch != 1f || currentHStretch != 1f)) {
                currentWStretch = 1f
                currentHStretch = 1f
                val normalized = clockItem.withTransform(state, currentScale, 1f, 1f)
                result = result.copy(items = result.items.map { if (it.id == normalized.id) normalized else it })
            }

            val estimatedBefore = estimateClockBounds(clockFace, result.clockItem, state)
            val activeBoundsBefore = measuredBounds ?: estimatedBefore
            val wRatio = if (activeBoundsBefore.width > maxAvailW && activeBoundsBefore.width > EPS) {
                maxAvailW / activeBoundsBefore.width
            } else 1f
            val hRatio = if (activeBoundsBefore.height > maxAvailH && activeBoundsBefore.height > EPS) {
                maxAvailH / activeBoundsBefore.height
            } else 1f
            val shrinkRatio = minOf(wRatio, hRatio)

            var effectiveScale = currentScale
            var effectiveWStretch = currentWStretch
            var effectiveHStretch = currentHStretch

            if (shrinkRatio < 0.995f) {
                val targetScale = (currentScale * shrinkRatio).coerceIn(MIN_AUTO_CLOCK_SCALE, currentScale)
                val appliedScaleRatio = if (currentScale > EPS) targetScale / currentScale else 1f
                effectiveScale = targetScale

                if (!clockFace.isAnalog) {
                    val remainingWRatio = if (appliedScaleRatio > EPS) (wRatio / appliedScaleRatio).coerceAtMost(1f) else 1f
                    val remainingHRatio = if (appliedScaleRatio > EPS) (hRatio / appliedScaleRatio).coerceAtMost(1f) else 1f
                    if (remainingWRatio < 0.995f) {
                        effectiveWStretch = (effectiveWStretch * remainingWRatio).coerceIn(MIN_AUTO_CLOCK_STRETCH, effectiveWStretch)
                    }
                    if (remainingHRatio < 0.995f) {
                        effectiveHStretch = (effectiveHStretch * remainingHRatio).coerceIn(MIN_AUTO_CLOCK_STRETCH, effectiveHStretch)
                    }
                }

                val latestClock = result.clockItem ?: return@forEach
                val resizedClock = latestClock.withTransform(state, effectiveScale, effectiveWStretch, effectiveHStretch)
                result = result.copy(items = result.items.map { if (it.id == resizedClock.id) resizedClock else it })
            }

            val updatedItem = result.clockItem ?: return@forEach
            val basePlacement = updatedItem.placement(state) ?: defaultClockPlacement(clockFace, state)
            val boundsAfterScale = if (measuredBounds != null) {
                scaleBoundsForTransform(
                    measured = measuredBounds,
                    anchor = basePlacement.anchor,
                    scaleRatioX = (effectiveScale / currentScale.coerceAtLeast(EPS)) * (effectiveWStretch / currentWStretch.coerceAtLeast(EPS)),
                    scaleRatioY = (effectiveScale / currentScale.coerceAtLeast(EPS)) * (effectiveHStretch / currentHStretch.coerceAtLeast(EPS))
                )
            } else {
                estimateClockBounds(clockFace, updatedItem, state)
            }

            val clamped = clampClockBoundsToScreen(
                composition = result,
                clockFace = clockFace,
                itemState = state,
                maxY = maxY,
                clockBoundsOverride = boundsAfterScale
            )
            result = clamped.first
        }

        return result
    }

    fun clampClockBoundsToScreen(
        composition: Composition,
        clockFace: ClockFace,
        itemState: ItemState,
        maxY: Float = DEFAULT_BOTTOM,
        clockBoundsOverride: ItemBounds? = null
    ): Pair<Composition, ItemBounds?> {
        val clockItem = composition.clockItem ?: return composition to null
        val bounds = clockBoundsOverride ?: estimateClockBounds(clockFace, clockItem, itemState)
        val maxBottom = (maxY - EDGE).coerceAtLeast(MIN_TOP + 0.1f)
        val availW = (1f - EDGE * 2f).coerceAtLeast(0.1f)
        val availH = (maxBottom - MIN_TOP).coerceAtLeast(0.1f)

        val dx = if (bounds.width <= availW) {
            when {
                bounds.left < EDGE -> EDGE - bounds.left
                bounds.right > 1f - EDGE -> (1f - EDGE) - bounds.right
                else -> 0f
            }
        } else {
            0.5f - (bounds.left + bounds.right) * 0.5f
        }

        val dy = if (bounds.height <= availH) {
            when {
                bounds.top < MIN_TOP -> MIN_TOP - bounds.top
                bounds.bottom > maxBottom -> maxBottom - bounds.bottom
                else -> 0f
            }
        } else {
            MIN_TOP - bounds.top
        }

        if (abs(dx) <= EPS && abs(dy) <= EPS) {
            return composition to bounds
        }

        val shiftedComp = applyMovedItems(composition, setOf(clockItem.id), itemState, dx, dy) {
            defaultClockPlacement(clockFace, itemState)
        }
        return shiftedComp to shifted(bounds, dx, dy)
    }

    fun layoutPenalty(
        composition: Composition,
        clockFace: ClockFace,
        faceOf: (Int) -> ClockFace?,
        itemState: ItemState,
        maxY: Float = DEFAULT_BOTTOM,
        clockBoundsOverride: ItemBounds? = null,
        measuredInlineWidths: Map<Int, Float> = emptyMap(),
        minGap: Float = ZONE_GAP
    ): Float {
        val clockItem = composition.clockItem ?: return 0f
        val clock = clockBoundsOverride ?: estimateClockBounds(clockFace, clockItem, itemState)
        val grid = gridItems(composition, faceOf).mapNotNull { widgetBoundsOf(it, itemState, faceOf, measuredInlineWidths) }
        val inline = inlineItems(composition, faceOf).mapNotNull { widgetBoundsOf(it, itemState, faceOf, measuredInlineWidths) }
        val maxBottom = (maxY - EDGE).coerceAtLeast(MIN_TOP + 0.1f)

        var penalty = 0f
        fun edgeOverflow(box: ItemBounds) {
            if (box.left < EDGE - EPS) penalty += (EDGE - box.left)
            if (box.right > 1f - EDGE + EPS) penalty += (box.right - (1f - EDGE))
            if (box.top < MIN_TOP - EPS) penalty += (MIN_TOP - box.top)
            if (box.bottom > maxBottom + EPS) penalty += (box.bottom - maxBottom)
        }

        edgeOverflow(clock)
        grid.forEach(::edgeOverflow)
        inline.forEach(::edgeOverflow)

        val paddedClock = padded(clock, minGap)
        grid.forEach { g ->
            if (overlaps(paddedClock, g)) {
                val ox = (minOf(paddedClock.right, g.right) - maxOf(paddedClock.left, g.left)).coerceAtLeast(0f)
                val oy = (minOf(paddedClock.bottom, g.bottom) - maxOf(paddedClock.top, g.top)).coerceAtLeast(0f)
                penalty += ox * oy + maxOf(ox, oy) * 0.05f
            }
        }
        inline.forEach { i ->
            if (overlaps(paddedClock, i)) {
                val ox = (minOf(paddedClock.right, i.right) - maxOf(paddedClock.left, i.left)).coerceAtLeast(0f)
                val oy = (minOf(paddedClock.bottom, i.bottom) - maxOf(paddedClock.top, i.top)).coerceAtLeast(0f)
                penalty += ox * oy + maxOf(ox, oy) * 0.05f
            }
        }
        val widgetGap = minGap * 0.5f
        grid.forEach { g ->
            val pg = padded(g, widgetGap)
            inline.forEach { i ->
                if (overlaps(pg, i)) {
                    val ox = (minOf(pg.right, i.right) - maxOf(pg.left, i.left)).coerceAtLeast(0f)
                    val oy = (minOf(pg.bottom, i.bottom) - maxOf(pg.top, i.top)).coerceAtLeast(0f)
                    penalty += ox * oy + maxOf(ox, oy) * 0.05f
                }
            }
        }
        return penalty
    }

    fun hasAnyCollision(
        composition: Composition,
        clockFace: ClockFace,
        faceOf: (Int) -> ClockFace?,
        itemState: ItemState,
        maxY: Float = DEFAULT_BOTTOM,
        clockBoundsOverride: ItemBounds? = null,
        measuredInlineWidths: Map<Int, Float> = emptyMap(),
        minGap: Float = ZONE_GAP
    ): Boolean = layoutPenalty(
        composition = composition,
        clockFace = clockFace,
        faceOf = faceOf,
        itemState = itemState,
        maxY = maxY,
        clockBoundsOverride = clockBoundsOverride,
        measuredInlineWidths = measuredInlineWidths,
        minGap = minGap
    ) > 1e-4f

    private fun scaleBoundsForTransform(
        measured: ItemBounds,
        anchor: FaceAlign,
        scaleRatioX: Float,
        scaleRatioY: Float
    ): ItemBounds {
        val newW = measured.width * scaleRatioX
        val newH = measured.height * scaleRatioY
        val left = when (anchor) {
            FaceAlign.START -> measured.left
            FaceAlign.CENTER -> (measured.left + measured.right) * 0.5f - newW * 0.5f
            FaceAlign.END -> measured.right - newW
        }
        return ItemBounds(left, measured.top, left + newW, measured.top + newH)
    }

    private fun defaultClockPlacement(face: ClockFace, state: ItemState): FacePlacement = when (state) {
        ItemState.LARGE -> face.large.placement
        ItemState.SMALL -> face.small.placement
        ItemState.AOD_LARGE -> (face.aodLarge ?: face.large).placement
        ItemState.AOD_SMALL -> (face.aodSmall ?: face.small).placement
    }

    fun reconcileWidgetsForClock(
        composition: Composition,
        clockFace: ClockFace,
        faceOf: (Int) -> ClockFace?,
        largeMaxY: Float = DEFAULT_BOTTOM,
        smallMaxY: Float = DEFAULT_BOTTOM,
        largeClockBounds: ItemBounds? = null,
        smallClockBounds: ItemBounds? = null,
        measuredInlineWidths: Map<Int, Float> = emptyMap()
    ): Composition {
        val grid = gridItems(composition, faceOf)
        val inline = inlineItems(composition, faceOf)
        if (grid.isEmpty() && inline.isEmpty()) return composition

        var result = composition
        listOf(ItemState.LARGE to largeMaxY, ItemState.SMALL to smallMaxY).forEach { (state, maxY) ->
            val maxBottom = (maxY - EDGE).coerceAtLeast(MIN_TOP + 0.1f)
            val rawClock = when (state) {
                ItemState.LARGE -> largeClockBounds
                ItemState.SMALL -> smallClockBounds
                else -> null
            } ?: estimateClockBounds(clockFace, result.clockItem, state)

            val (cleared, clockBounds) = ensureClockClearanceForWidgets(
                composition = result,
                itemState = state,
                faceOf = faceOf,
                clockBounds = rawClock,
                maxY = maxY,
                measuredInlineWidths = measuredInlineWidths,
                clockFace = clockFace
            )
            result = cleared
            val activeClock = clockBounds ?: rawClock
            val activePaddedClock = ItemBounds(
                activeClock.left - GRID_GAP,
                activeClock.top - GRID_GAP,
                activeClock.right + GRID_GAP,
                activeClock.bottom + GRID_GAP
            )
            fun isOutOfBounds(b: ItemBounds): Boolean =
                b.top < MIN_TOP - EPS || b.bottom > maxBottom + EPS || b.left < EDGE - EPS || b.right > 1f - EDGE + EPS

            val gridOverlaps = grid.any { item ->
                val b = widgetBoundsOf(item, state, faceOf, measuredInlineWidths) ?: return@any true
                overlaps(b, activePaddedClock) || isOutOfBounds(b)
            }
            val inlineOverlapsClock = inline.any { item ->
                val b = widgetBoundsOf(item, state, faceOf, measuredInlineWidths) ?: return@any true
                overlaps(b, activePaddedClock) || isOutOfBounds(b)
            }
            val gridInlineOverlap = grid.any { g ->
                val gb = widgetBoundsOf(g, state, faceOf, measuredInlineWidths) ?: return@any true
                inline.any { i ->
                    val ib = widgetBoundsOf(i, state, faceOf, measuredInlineWidths) ?: return@any true
                    val paddedIb = ItemBounds(
                        ib.left - GRID_GAP * 0.5f,
                        ib.top - GRID_GAP * 0.5f,
                        ib.right + GRID_GAP * 0.5f,
                        ib.bottom + GRID_GAP * 0.5f
                    )
                    overlaps(gb, paddedIb)
                }
            }

            val needGridLayout = grid.isNotEmpty() && (result.gridDocked || gridOverlaps || gridInlineOverlap)
            val needInlineLayout = inline.isNotEmpty() && (result.inlineDocked || inlineOverlapsClock || gridInlineOverlap)
            if (!needGridLayout && !needInlineLayout) return@forEach

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
                    widgetsAbove = result.widgetsAbove,
                    measuredInlineWidths = measuredInlineWidths,
                    preserveExistingSide = !result.gridDocked
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
                    widgetsAbove = result.widgetsAbove,
                    measuredInlineWidths = measuredInlineWidths,
                    preserveExistingSide = !result.inlineDocked
                )
            }
            if (needGridLayout && needInlineLayout) {
                val stillOverlapGridInline = gridItems(result, faceOf).any { g ->
                    val gb = widgetBoundsOf(g, state, faceOf, measuredInlineWidths) ?: return@any false
                    inlineItems(result, faceOf).any { i ->
                        val ib = widgetBoundsOf(i, state, faceOf, measuredInlineWidths) ?: return@any false
                        overlaps(gb, padded(ib, GRID_GAP * 0.5f))
                    }
                }
                if (stillOverlapGridInline) {
                    result = layoutZone(
                        composition = result,
                        zone = WidgetZone.GRID_1X4,
                        itemState = state,
                        faceOf = faceOf,
                        clockBottomY = activeClock.bottom,
                        maxY = maxY,
                        centerHorizontally = result.gridDocked,
                        clockBounds = activeClock,
                        widgetsAbove = result.widgetsAbove,
                        measuredInlineWidths = measuredInlineWidths,
                        preserveExistingSide = !result.gridDocked
                    )
                }
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
        val widthStretch = clockItem?.widthStretch(itemState) ?: 1f
        val heightStretch = clockItem?.heightStretch(itemState) ?: 1f
        val node = FaceEditor.stretchNode(FaceEditor.scaleNode(baseNode, scale), face.layers, widthStretch, heightStretch)
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
        faceOf: (Int) -> ClockFace?,
        measuredInlineWidths: Map<Int, Float> = emptyMap()
    ): ItemBounds? {
        val placement = item.placement(state) ?: return null
        val face = faceOf(item.style) ?: return null
        val root = face.large as? StackNode
        val w = if (zoneOf(face) == WidgetZone.INLINE_1X2) {
            estimateInlineWidth(item, faceOf, measuredInlineWidths)
        } else {
            root?.width ?: FaceGrid.width(spanOf(face))
        }
        val h = heightOf(face)
        return ItemBounds.of(placement, w, h)
    }

    fun flipWidgetsPosition(
        composition: Composition,
        widgetsAbove: Boolean,
        itemState: ItemState,
        faceOf: (Int) -> ClockFace?,
        clockBounds: ItemBounds?,
        maxY: Float = DEFAULT_BOTTOM,
        measuredInlineWidths: Map<Int, Float> = emptyMap(),
        clockFace: ClockFace? = null
    ): Composition {
        val zoneGap = GRID_GAP
        val gap = FaceGrid.GAP
        val maxBottom = (maxY - EDGE).coerceAtLeast(MIN_TOP + 0.1f)
        val clock = clockBounds ?: ItemBounds(0.05f, 0.2f, 0.95f, 0.6f)

        var result = composition
        val grid = gridItems(composition, faceOf)
        val inline = inlineItems(composition, faceOf)
        if (grid.isEmpty() && inline.isEmpty()) return result.copy(widgetsAbove = widgetsAbove)

        val gridRows = packGridRows(grid, composition.gridCols, faceOf)
        val inlineRows = packInlineRows(inline, faceOf, measuredInlineWidths)
        val rowHGrid = if (gridRows.isEmpty()) 0f else {
            gridRows.sumOf { it.height.toDouble() }.toFloat() + gap * (gridRows.size - 1).coerceAtLeast(0)
        }
        val rowHInline = if (inlineRows.isEmpty()) 0f else {
            inlineRows.sumOf { it.height.toDouble() }.toFloat() + gap * (inlineRows.size - 1).coerceAtLeast(0)
        }
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
            result = applyMovedItems(result, setOf(clockItem.id), itemState, 0f, clockShiftY) {
                clockFace?.let { defaultClockPlacement(it, itemState) } ?: FacePlacement()
            }
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
            widgetsAbove = widgetsAbove,
            measuredInlineWidths = measuredInlineWidths
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
            widgetsAbove = widgetsAbove,
            measuredInlineWidths = measuredInlineWidths
        )
        return result.copy(widgetsAbove = widgetsAbove, gridDocked = true, inlineDocked = true)
    }

    data class PlaceholdersResult(
        val gridPlaceholder: RectF?,
        val inlinePlaceholder: RectF?
    )

    data class PlaceholderBoundsResult(
        val gridPlaceholder: ItemBounds?,
        val inlinePlaceholder: ItemBounds?
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
        val result = calculatePlaceholderBounds(
            clockBounds = clockRect?.let { ItemBounds(it.left, it.top, it.right, it.bottom) },
            gridBounds = gridRect?.let { ItemBounds(it.left, it.top, it.right, it.bottom) },
            inlineBounds = inlineRect?.let { ItemBounds(it.left, it.top, it.right, it.bottom) },
            widgetsAbove = widgetsAbove,
            width = width,
            height = height,
            unit = unit,
            frameLeft = frameLeft,
            edgePadPx = edgePadPx,
            slotGap = slotGap,
            safeBottomPx = safeBottomPx,
            gridSlotH = gridSlotH,
            inlineSlotH = inlineSlotH,
            minTouch = minTouch,
            isAod = isAod
        )
        return PlaceholdersResult(
            gridPlaceholder = result.gridPlaceholder?.let { RectF(it.left, it.top, it.right, it.bottom) },
            inlinePlaceholder = result.inlinePlaceholder?.let { RectF(it.left, it.top, it.right, it.bottom) }
        )
    }

    fun calculatePlaceholderBounds(
        clockBounds: ItemBounds?,
        gridBounds: ItemBounds?,
        inlineBounds: ItemBounds?,
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
    ): PlaceholderBoundsResult {
        if (isAod || unit <= 0f || height <= 0f) {
            return PlaceholderBoundsResult(null, null)
        }

        val needGrid = gridBounds == null
        val needInline = inlineBounds == null
        if (!needGrid && !needInline) return PlaceholderBoundsResult(null, null)

        val minY = edgePadPx
        val maxY = safeBottomPx.coerceAtLeast(minY)
        val maxWidth = (width - edgePadPx * 2f).coerceAtLeast(minTouch)

        val wGrid = minOf(unit * FaceGrid.width(FaceGrid.ROW_CELLS), maxWidth)
        val hGrid = maxOf(unit * FaceGrid.CELL * 0.72f, gridSlotH)
        val gridCenterX = inlineBounds?.let { (it.left + it.right) / 2f } ?: (frameLeft + unit / 2f)
        val leftGrid = (gridCenterX - wGrid / 2f).coerceIn(edgePadPx, (width - edgePadPx - wGrid).coerceAtLeast(edgePadPx))

        val wInline = minOf(unit * INLINE_PLACEHOLDER_RATIO, maxWidth)
        val hInline = inlineSlotH
        val inlineCenterX = gridBounds?.let { (it.left + it.right) / 2f } ?: (frameLeft + unit / 2f)
        val leftInline = (inlineCenterX - wInline / 2f).coerceIn(edgePadPx, (width - edgePadPx - wInline).coerceAtLeast(edgePadPx))

        val cTop = clockBounds?.top ?: (height * 0.35f)
        val cBottom = clockBounds?.bottom ?: (height * 0.45f)
        val baseObstacles = listOfNotNull(clockBounds, gridBounds, inlineBounds)

        var gRes: ItemBounds? = null
        var iRes: ItemBounds? = null

        if (needGrid && needInline) {
            val gridAboveY = cTop - slotGap - hGrid
            val inlineAboveGridY = gridAboveY - slotGap - hInline
            val gridBelowY = cBottom + slotGap
            val inlineBelowGridY = gridBelowY + hGrid + slotGap

            val preferredPairs = if (widgetsAbove) {
                listOf(
                    gridAboveY to inlineAboveGridY,
                    gridAboveY to (cBottom + slotGap),
                    gridBelowY to inlineBelowGridY
                )
            } else {
                listOf(
                    gridBelowY to inlineBelowGridY,
                    gridBelowY to (cTop - slotGap - hInline),
                    gridAboveY to inlineAboveGridY
                )
            }

            var placedPair: Pair<ItemBounds, ItemBounds>? = null
            for ((gY, iY) in preferredPairs) {
                val gCand = ItemBounds(leftGrid, gY, leftGrid + wGrid, gY + hGrid)
                val iCand = ItemBounds(leftInline, iY, leftInline + wInline, iY + hInline)
                if (isValidPlaceholder(gCand, baseObstacles, minY, maxY, slotGap) &&
                    isValidPlaceholder(iCand, baseObstacles + gCand, minY, maxY, slotGap)
                ) {
                    placedPair = gCand to iCand
                    break
                }
            }

            if (placedPair != null) {
                gRes = placedPair.first
                iRes = placedPair.second
            } else {
                val gridDesiredY = if (widgetsAbove) gridAboveY else gridBelowY
                val gridCandidates = if (widgetsAbove) listOf(gridAboveY, gridBelowY) else listOf(gridBelowY, gridAboveY)
                gRes = findPlaceholderBounds(
                    left = leftGrid,
                    width = wGrid,
                    height = hGrid,
                    preferredCandidates = gridCandidates,
                    preferredY = gridDesiredY,
                    obstacles = baseObstacles,
                    minY = minY,
                    maxY = maxY,
                    slotGap = slotGap
                )
                val obstaclesWithGrid = if (gRes != null) baseObstacles + gRes else baseObstacles
                val gTopEff = gRes?.top ?: cTop
                val gBottomEff = gRes?.bottom ?: cBottom
                val inlineDesiredY = if (widgetsAbove) gTopEff - slotGap - hInline else gBottomEff + slotGap
                val inlineCandidates = if (widgetsAbove) {
                    listOf(gTopEff - slotGap - hInline, gBottomEff + slotGap, cBottom + slotGap, cTop - slotGap - hInline)
                } else {
                    listOf(gBottomEff + slotGap, gTopEff - slotGap - hInline, cTop - slotGap - hInline, cBottom + slotGap)
                }
                iRes = findPlaceholderBounds(
                    left = leftInline,
                    width = wInline,
                    height = hInline,
                    preferredCandidates = inlineCandidates,
                    preferredY = inlineDesiredY,
                    obstacles = obstaclesWithGrid,
                    minY = minY,
                    maxY = maxY,
                    slotGap = slotGap
                )
            }
        } else if (needGrid) {
            val refInline = requireNotNull(inlineBounds)
            val gridIsAboveClock = if (clockBounds != null) {
                (refInline.top + refInline.bottom) / 2f < (clockBounds.top + clockBounds.bottom) / 2f
            } else {
                widgetsAbove
            }
            val preferredCandidates = if (gridIsAboveClock) {
                listOf(
                    refInline.bottom + slotGap,
                    cTop - slotGap - hGrid,
                    refInline.top - slotGap - hGrid,
                    cBottom + slotGap
                )
            } else {
                listOf(
                    refInline.top - slotGap - hGrid,
                    cBottom + slotGap,
                    refInline.bottom + slotGap,
                    cTop - slotGap - hGrid
                )
            }
            val preferredY = preferredCandidates.first()
            gRes = findPlaceholderBounds(
                left = leftGrid,
                width = wGrid,
                height = hGrid,
                preferredCandidates = preferredCandidates,
                preferredY = preferredY,
                obstacles = baseObstacles,
                minY = minY,
                maxY = maxY,
                slotGap = slotGap
            )
        } else {
            val refGrid = requireNotNull(gridBounds)
            val gridIsAboveClock = if (clockBounds != null) {
                (refGrid.top + refGrid.bottom) / 2f < (clockBounds.top + clockBounds.bottom) / 2f
            } else {
                widgetsAbove
            }
            val preferredCandidates = if (gridIsAboveClock) {
                listOf(
                    refGrid.top - slotGap - hInline,
                    refGrid.bottom + slotGap,
                    cBottom + slotGap,
                    cTop - slotGap - hInline
                )
            } else {
                listOf(
                    refGrid.bottom + slotGap,
                    refGrid.top - slotGap - hInline,
                    cTop - slotGap - hInline,
                    cBottom + slotGap
                )
            }
            val preferredY = preferredCandidates.first()
            iRes = findPlaceholderBounds(
                left = leftInline,
                width = wInline,
                height = hInline,
                preferredCandidates = preferredCandidates,
                preferredY = preferredY,
                obstacles = baseObstacles,
                minY = minY,
                maxY = maxY,
                slotGap = slotGap
            )
        }

        return PlaceholderBoundsResult(gRes, iRes)
    }

    private fun findPlaceholderBounds(
        left: Float,
        width: Float,
        height: Float,
        preferredCandidates: List<Float>,
        preferredY: Float,
        obstacles: List<ItemBounds>,
        minY: Float,
        maxY: Float,
        slotGap: Float
    ): ItemBounds? {
        if (maxY - minY < height - 0.5f) return null

        for (y in preferredCandidates) {
            val cand = ItemBounds(left, y, left + width, y + height)
            if (isValidPlaceholder(cand, obstacles, minY, maxY, slotGap)) {
                return cand
            }
        }

        val gaps = listOf(slotGap, (slotGap * 0.4f).coerceAtLeast(2f), 0f).distinct()
        for (g in gaps) {
            val fitting = freeVerticalIntervalsForBounds(
                left = left,
                right = left + width,
                minY = minY,
                maxY = maxY,
                obstacles = obstacles,
                gap = g
            ).filter { (s, e) -> e - s >= height - 0.5f }

            if (fitting.isNotEmpty()) {
                val bestInterval = fitting.minBy { (s, e) ->
                    val maxAllowed = (e - height).coerceAtLeast(s)
                    val clamped = preferredY.coerceIn(s, maxAllowed)
                    abs(clamped - preferredY)
                }
                val maxAllowed = (bestInterval.second - height).coerceAtLeast(bestInterval.first)
                val y = preferredY.coerceIn(bestInterval.first, maxAllowed)
                return ItemBounds(left, y, left + width, y + height)
            }
        }
        return null
    }

    private fun isValidPlaceholder(
        cand: ItemBounds,
        obstacles: List<ItemBounds>,
        minY: Float,
        maxY: Float,
        gap: Float
    ): Boolean {
        if (cand.top < minY - 0.5f || cand.bottom > maxY + 0.5f) return false
        return obstacles.none { obs ->
            cand.left < obs.right + gap - 0.5f &&
                cand.right > obs.left - gap + 0.5f &&
                cand.top < obs.bottom + gap - 0.5f &&
                cand.bottom > obs.top - gap + 0.5f
        }
    }

    private fun freeVerticalIntervalsForBounds(
        left: Float,
        right: Float,
        minY: Float,
        maxY: Float,
        obstacles: List<ItemBounds>,
        gap: Float
    ): List<Pair<Float, Float>> {
        val merged = obstacles
            .filter { obs -> left < obs.right + gap - 0.5f && right > obs.left - gap + 0.5f }
            .map { obs -> (obs.top - gap) to (obs.bottom + gap) }
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
        var cursor = minY
        for ((start, end) in merged) {
            if (start > cursor) {
                free += cursor to minOf(start, maxY)
            }
            cursor = maxOf(cursor, end)
            if (cursor >= maxY) break
        }
        if (cursor < maxY) {
            free += cursor to maxY
        }
        return free
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

        val activeLast = lastValidDeltas[activeTarget] ?: (0f to 0f)
        val dist = hypot(rawDx - activeLast.first, rawDy - activeLast.second)
        val substeps = ceil(dist / PUSH_SUBSTEP).toInt().coerceIn(1, 32)
        if (substeps == 1) {
            return solveSingleStepStickyDrag(
                activeTarget = activeTarget,
                startBounds = startBounds,
                rawDx = rawDx,
                rawDy = rawDy,
                lastValidDeltas = lastValidDeltas,
                gridDocked = gridDocked,
                inlineDocked = inlineDocked,
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
                fixedObstacles = fixedObstacles,
                gap = gap
            )
        }

        var currentDeltas = lastValidDeltas
        var currentGridDocked = gridDocked
        var currentInlineDocked = inlineDocked
        var anyAttached = false

        for (step in 1..substeps) {
            val fraction = step.toFloat() / substeps.toFloat()
            val stepDx = activeLast.first + (rawDx - activeLast.first) * fraction
            val stepDy = activeLast.second + (rawDy - activeLast.second) * fraction
            val res = solveSingleStepStickyDrag(
                activeTarget = activeTarget,
                startBounds = startBounds,
                rawDx = stepDx,
                rawDy = stepDy,
                lastValidDeltas = currentDeltas,
                gridDocked = currentGridDocked,
                inlineDocked = currentInlineDocked,
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
                fixedObstacles = fixedObstacles,
                gap = gap
            )
            currentDeltas = res.deltas
            currentGridDocked = res.gridDocked
            currentInlineDocked = res.inlineDocked
            if (res.attachedEvent) anyAttached = true
        }

        return StickyDragResult(
            deltas = currentDeltas,
            gridDocked = currentGridDocked,
            inlineDocked = currentInlineDocked,
            attachedEvent = anyAttached
        )
    }

    private fun solveSingleStepStickyDrag(
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
        gap: Float
    ): StickyDragResult {
        var nextGridDocked = gridDocked
        var nextInlineDocked = inlineDocked
        var attachedEvent = false

        val activeStart = startBounds.getValue(activeTarget)
        val dockMargin = gap + 0.02f
        val existingDockMargin = maxOf(dockMargin, GRID_GAP + 0.04f)

        fun liveBox(target: LockTarget): ItemBounds? {
            val base = startBounds[target] ?: return null
            val delta = lastValidDeltas[target] ?: (0f to 0f)
            return shifted(base, delta.first, delta.second)
        }

        fun areAdjacent(a: ItemBounds, b: ItemBounds, margin: Float): Boolean =
            overlaps(padded(a, margin), b)

        fun gapDistSq(a: ItemBounds, b: ItemBounds): Float {
            val dx = maxOf(0f, maxOf(a.left - b.right, b.left - a.right))
            val dy = maxOf(0f, maxOf(a.top - b.bottom, b.top - a.bottom))
            return dx * dx + dy * dy
        }

        fun shouldAttach(prevA: ItemBounds?, candA: ItemBounds, targetB: ItemBounds?): Boolean {
            if (targetB == null) return false
            if (!overlaps(padded(candA, dockMargin), targetB)) return false
            if (prevA == null || !areAdjacent(prevA, targetB, dockMargin)) return true
            return gapDistSq(candA, targetB) < gapDistSq(prevA, targetB) - 1e-6f
        }

        val clockCur = liveBox(LockTarget.CLOCK)
        val gridCur = liveBox(LockTarget.WIDGET_GRID)
        val inlineCur = liveBox(LockTarget.INLINE_GRID)

        val rigidGroup = HashSet<LockTarget>(3).apply { add(activeTarget) }

        when (activeTarget) {
            LockTarget.CLOCK -> {
                val candidateClock = shifted(activeStart, rawDx, rawDy)
                val paddedCandidateClock = padded(candidateClock, dockMargin)

                if (!nextGridDocked && shouldAttach(clockCur, candidateClock, gridCur)) {
                    nextGridDocked = true
                    attachedEvent = true
                }
                if (nextGridDocked && gridCur != null &&
                    (clockCur == null || areAdjacent(clockCur, gridCur, existingDockMargin) || overlaps(paddedCandidateClock, gridCur))
                ) {
                    rigidGroup += LockTarget.WIDGET_GRID
                }

                val clockTouchesInline = shouldAttach(clockCur, candidateClock, inlineCur)
                val gridTouchesInline = gridCur != null && inlineCur != null && areAdjacent(gridCur, inlineCur, dockMargin)
                if (!nextInlineDocked && inlineCur != null && (clockTouchesInline || (LockTarget.WIDGET_GRID in rigidGroup && gridTouchesInline))) {
                    nextInlineDocked = true
                    attachedEvent = true
                }

                if (nextInlineDocked && inlineCur != null) {
                    val adjacentToClock = clockCur != null && areAdjacent(clockCur, inlineCur, existingDockMargin)
                    val adjacentToGrid = gridCur != null && areAdjacent(gridCur, inlineCur, existingDockMargin)
                    val inlineFollowsClock = when {
                        gridCur == null -> adjacentToClock || overlaps(paddedCandidateClock, inlineCur)
                        LockTarget.WIDGET_GRID in rigidGroup -> adjacentToClock || adjacentToGrid
                        else -> adjacentToClock && !adjacentToGrid
                    }
                    if (inlineFollowsClock) {
                        rigidGroup += LockTarget.INLINE_GRID
                    }
                }
            }

            LockTarget.WIDGET_GRID -> {
                val candidateGrid = shifted(activeStart, rawDx, rawDy)
                val paddedCandidateGrid = padded(candidateGrid, dockMargin)

                val inlineAttachedToGrid = nextInlineDocked && inlineCur != null && gridCur != null &&
                    areAdjacent(gridCur, inlineCur, existingDockMargin)
                if (inlineAttachedToGrid) {
                    rigidGroup += LockTarget.INLINE_GRID
                }

                if (!nextInlineDocked && shouldAttach(gridCur, candidateGrid, inlineCur)) {
                    nextInlineDocked = true
                    attachedEvent = true
                }

                nextGridDocked = if (clockCur != null && overlaps(paddedCandidateGrid, clockCur)) {
                    if (nextGridDocked) {
                        true
                    } else if (shouldAttach(gridCur, candidateGrid, clockCur)) {
                        attachedEvent = true
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }

            LockTarget.INLINE_GRID -> {
                val candidateInline = shifted(activeStart, rawDx, rawDy)
                val paddedCandidateInline = padded(candidateInline, dockMargin)

                val touchesGrid = gridCur != null && overlaps(paddedCandidateInline, gridCur)
                val touchesClock = clockCur != null && overlaps(paddedCandidateInline, clockCur)
                nextInlineDocked = if (touchesGrid || touchesClock) {
                    if (nextInlineDocked) {
                        true
                    } else if ((touchesGrid && shouldAttach(inlineCur, candidateInline, gridCur)) ||
                        (touchesClock && shouldAttach(inlineCur, candidateInline, clockCur))
                    ) {
                        attachedEvent = true
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }

            LockTarget.SHORTCUTS -> Unit
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
            gap = gap,
            rigidGroup = rigidGroup
        )

        // Post-step contact check so pushing/touching an undocked neighbor also docks cleanly once touching
        fun solvedBox(target: LockTarget): ItemBounds? {
            val base = startBounds[target] ?: return null
            val delta = solved[target] ?: (0f to 0f)
            return shifted(base, delta.first, delta.second)
        }

        val finalClock = solvedBox(LockTarget.CLOCK)
        val finalGrid = solvedBox(LockTarget.WIDGET_GRID)
        val finalInline = solvedBox(LockTarget.INLINE_GRID)

        when (activeTarget) {
            LockTarget.CLOCK -> {
                if (!nextGridDocked && finalClock != null && finalGrid != null && shouldAttach(clockCur, finalClock, finalGrid)) {
                    nextGridDocked = true
                    attachedEvent = true
                }
                if (!nextInlineDocked && finalInline != null && (
                        (finalClock != null && shouldAttach(clockCur, finalClock, finalInline)) ||
                            (nextGridDocked && finalGrid != null && areAdjacent(finalGrid, finalInline, dockMargin))
                        )
                ) {
                    nextInlineDocked = true
                    attachedEvent = true
                }
            }

            LockTarget.WIDGET_GRID -> {
                if (!nextGridDocked && finalClock != null && finalGrid != null && shouldAttach(gridCur, finalGrid, finalClock)) {
                    nextGridDocked = true
                    attachedEvent = true
                }
                if (!nextInlineDocked && finalGrid != null && finalInline != null && shouldAttach(gridCur, finalGrid, finalInline)) {
                    nextInlineDocked = true
                    attachedEvent = true
                }
            }

            LockTarget.INLINE_GRID -> {
                if (!nextInlineDocked && finalInline != null && (
                        (finalGrid != null && shouldAttach(inlineCur, finalInline, finalGrid)) ||
                            (finalClock != null && shouldAttach(inlineCur, finalInline, finalClock))
                        )
                ) {
                    nextInlineDocked = true
                    attachedEvent = true
                }
            }

            LockTarget.SHORTCUTS -> Unit
        }

        return StickyDragResult(solved, nextGridDocked, nextInlineDocked, attachedEvent)
    }

    private fun padded(bounds: ItemBounds, amount: Float): ItemBounds = ItemBounds(
        bounds.left - amount,
        bounds.top - amount,
        bounds.right + amount,
        bounds.bottom + amount
    )

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
        gap: Float = ZONE_GAP,
        rigidGroup: Set<K> = setOf(activeTarget)
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
                gap = gap,
                rigidGroup = rigidGroup
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
                gap = gap,
                rigidGroup = rigidGroup
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
        gap: Float,
        rigidGroup: Set<K>
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
            gap = gap,
            rigidGroup = rigidGroup
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
            gap = gap,
            rigidGroup = rigidGroup
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
                gap = gap,
                rigidGroup = rigidGroup
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
            val fullX = tryPushLayout(
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
                gap = gap,
                rigidGroup = rigidGroup
            )
            if (fullX != null) {
                best = fullX
            } else {
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
                        gap = gap,
                        rigidGroup = rigidGroup
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
        }
        val settledAfterX = best[activeTarget] ?: settled
        if (abs(rawDy - settledAfterX.second) > EPS) {
            val fullY = tryPushLayout(
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
                gap = gap,
                rigidGroup = rigidGroup
            )
            if (fullY != null) {
                best = fullY
            } else {
                var yLo = settledAfterX.second
                var yHi = rawDy
                var yBest = best
                repeat(10) {
                    val midY = (yLo + yHi) * 0.5f
                    val res = tryPushLayout(
                        activeTarget = activeTarget,
                        startBounds = startBounds,
                        candidateActiveDx = settledAfterX.first,
                        candidateActiveDy = midY,
                        lastValidDeltas = best,
                        minX = minX,
                        maxX = maxX,
                        minY = minY,
                        maxY = maxY,
                        fixedObstacles = fixedObstacles,
                        gap = gap,
                        rigidGroup = rigidGroup
                    )
                    if (res != null) {
                        yBest = res
                        yLo = midY
                    } else {
                        yHi = midY
                    }
                }
                best = yBest
            }
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
        gap: Float,
        rigidGroup: Set<K>
    ): Map<K, Pair<Float, Float>>? {
        val activeStart = startBounds[activeTarget] ?: return null
        val activeLast = lastValidDeltas[activeTarget] ?: (0f to 0f)
        val effectiveRigid = rigidGroup.filter { it in startBounds }.toSet()

        val activeDelta = if (effectiveRigid.size <= 1) {
            clampNonOverlappingDelta(
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
        } else {
            candidateActiveDx to candidateActiveDy
        }

        val stepDx = activeDelta.first - activeLast.first
        val stepDy = activeDelta.second - activeLast.second

        val deltas = HashMap<K, Pair<Float, Float>>(startBounds.size)
        val prevBoxes = HashMap<K, ItemBounds>(startBounds.size)
        startBounds.forEach { (k, bounds) ->
            val prevDelta = lastValidDeltas[k] ?: (0f to 0f)
            prevBoxes[k] = shifted(bounds, prevDelta.first, prevDelta.second)
            deltas[k] = if (k in effectiveRigid) {
                (prevDelta.first + stepDx) to (prevDelta.second + stepDy)
            } else {
                prevDelta
            }
        }

        fun currentBox(k: K): ItemBounds {
            val d = deltas.getValue(k)
            return shifted(startBounds.getValue(k), d.first, d.second)
        }

        val paddedFixed = fixedObstacles.map { obs ->
            ItemBounds(obs.left - gap, obs.top - gap, obs.right + gap, obs.bottom + gap)
        }

        // Enforce bounds and fixed-obstacle clearance for all members of rigidGroup
        if (effectiveRigid.size > 1) {
            for (member in effectiveRigid) {
                val mBox = currentBox(member)
                val (validDx, validDy) = clampDelta(mBox, 0f, 0f, minX, maxX, minY, maxY)
                if (abs(validDx) > EPS || abs(validDy) > EPS) return null
                if (paddedFixed.any { overlaps(mBox, it) }) return null
            }
        }

        val prevBoxList = prevBoxes.values.toList()
        val initialPhysicalOverlap = layoutOverlapArea(prevBoxList, fixedObstacles, 0f)
        val initialGappedOverlap = layoutOverlapArea(prevBoxList, paddedFixed, gap)

        val queue = ArrayDeque<K>()
        effectiveRigid.forEach { queue.addLast(it) }
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
                if (victim == pusher || victim in effectiveRigid) continue
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
                    moveBiasDx = stepDx,
                    moveBiasDy = stepDy,
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
        val finalPhysicalOverlap = layoutOverlapArea(finalBoxes, fixedObstacles, 0f)
        val finalGappedOverlap = layoutOverlapArea(finalBoxes, paddedFixed, gap)

        if (initialPhysicalOverlap <= AREA_EPS) {
            if (finalPhysicalOverlap > AREA_EPS) return null
        } else {
            if (finalPhysicalOverlap > initialPhysicalOverlap + AREA_EPS) return null
        }

        if (initialGappedOverlap <= AREA_EPS) {
            if (finalGappedOverlap > AREA_EPS) return null
        } else {
            if (finalGappedOverlap > initialGappedOverlap + AREA_EPS) return null
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
        val aboveGap = victimPrev.top - pusherPrev.bottom
        val belowGap = pusherPrev.top - victimPrev.bottom
        val leftGap = victimPrev.left - pusherPrev.right
        val rightGap = pusherPrev.left - victimPrev.right

        val candidates = listOf(
            0 to aboveGap,
            1 to belowGap,
            2 to leftGap,
            3 to rightGap
        )
        val bestAxis = candidates
            .filter { it.second >= -gap - EPS }
            .maxWithOrNull(compareBy<Pair<Int, Float>> { (_, sep) ->
                if (sep >= -EPS) sep else sep - 1f
            }.thenBy { (axis, _) ->
                when (axis) {
                    0 -> if (moveBiasDy > EPS) 1f else 0f
                    1 -> if (moveBiasDy < -EPS) 1f else 0f
                    2 -> if (moveBiasDx > EPS) 1f else 0f
                    else -> if (moveBiasDx < -EPS) 1f else 0f
                }
            })

        return when (bestAxis?.first) {
            0 -> victimDelta.first to (victimDelta.second + (paddedPusher.bottom - victimBox.top).coerceAtLeast(0f))
            1 -> victimDelta.first to (victimDelta.second + (paddedPusher.top - victimBox.bottom).coerceAtMost(0f))
            2 -> (victimDelta.first + (paddedPusher.right - victimBox.left).coerceAtLeast(0f)) to victimDelta.second
            3 -> (victimDelta.first + (paddedPusher.left - victimBox.right).coerceAtMost(0f)) to victimDelta.second
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

    private fun findZoneY(
        desiredTop: Float,
        rowHeight: Float,
        minTop: Float,
        maxBottom: Float,
        clockBounds: ItemBounds?,
        clockBottomY: Float,
        otherZoneSpan: (Float) -> Pair<Float, Float>?,
        preferBelow: Boolean?
    ): Float {
        if (preferBelow == null) {
            val zoneGap = GRID_GAP
            val clockSpan = clockBounds?.let { (it.top - zoneGap) to (it.bottom + zoneGap) }
                ?: (0f to (clockBottomY + zoneGap))
            val forbidden = listOfNotNull(clockSpan, otherZoneSpan(zoneGap))
            return findNonOverlappingY(desiredTop, rowHeight, minTop, maxBottom, forbidden)
        }
        val rawClockTop = clockBounds?.top ?: (clockBottomY - 0.4f)
        val rawClockBottom = clockBounds?.bottom ?: clockBottomY
        val clockMinGap = 0.004f
        val attempts = floatArrayOf(GRID_GAP, 0.016f, clockMinGap)
        for (gapAttempt in attempts) {
            val clockSpan = (rawClockTop - gapAttempt) to (rawClockBottom + gapAttempt)
            val forbidden = listOfNotNull(clockSpan, otherZoneSpan(gapAttempt))
            findNonOverlappingYOnSide(
                desiredTop = desiredTop,
                rowHeight = rowHeight,
                minTop = minTop,
                maxBottom = maxBottom,
                forbidden = forbidden,
                preferBelow = preferBelow,
                clockSpan = clockSpan
            )?.let { return it }
        }
        val minClockOnlySpan = (rawClockTop - clockMinGap) to (rawClockBottom + clockMinGap)
        // First stay on the requested side clear of the clock
        findNonOverlappingYOnSide(
            desiredTop = desiredTop,
            rowHeight = rowHeight,
            minTop = minTop,
            maxBottom = maxBottom,
            forbidden = listOf(minClockOnlySpan),
            preferBelow = preferBelow,
            clockSpan = minClockOnlySpan
        )?.let { return it }

        // Only flip sides if the requested side cannot fit the zone clear of the clock
        val bothFreeIntervals = freeIntervals(
            minTop,
            maxBottom,
            listOfNotNull(minClockOnlySpan, otherZoneSpan(clockMinGap))
        ).filter { (s, e) -> e - s >= rowHeight - EPS }
        if (bothFreeIntervals.isNotEmpty()) {
            return findNonOverlappingY(
                desiredTop = desiredTop,
                rowHeight = rowHeight,
                minTop = minTop,
                maxBottom = maxBottom,
                forbidden = listOfNotNull(minClockOnlySpan, otherZoneSpan(clockMinGap)),
                preferBelow = preferBelow,
                clockSpan = minClockOnlySpan
            )
        }

        val fallbackClockSpan = (rawClockTop - GRID_GAP) to (rawClockBottom + GRID_GAP)
        return findNonOverlappingY(
            desiredTop = desiredTop,
            rowHeight = rowHeight,
            minTop = minTop,
            maxBottom = maxBottom,
            forbidden = listOfNotNull(fallbackClockSpan, otherZoneSpan(GRID_GAP)),
            preferBelow = preferBelow,
            clockSpan = fallbackClockSpan
        )
    }

    private fun freeIntervals(
        minTop: Float,
        maxBottom: Float,
        forbidden: List<Pair<Float, Float>>
    ): List<Pair<Float, Float>> {
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
        return free
    }

    private fun findNonOverlappingYOnSide(
        desiredTop: Float,
        rowHeight: Float,
        minTop: Float,
        maxBottom: Float,
        forbidden: List<Pair<Float, Float>>,
        preferBelow: Boolean,
        clockSpan: Pair<Float, Float>
    ): Float? {
        val fitting = freeIntervals(minTop, maxBottom, forbidden)
            .filter { (s, e) -> e - s >= rowHeight - EPS }
        if (fitting.isEmpty()) return null
        val sideFiltered = fitting.filter { (s, e) ->
            if (preferBelow) s >= clockSpan.second - EPS else e <= clockSpan.first + EPS
        }
        if (sideFiltered.isEmpty()) return null
        val bestInterval = sideFiltered.minBy { (s, e) ->
            val maxAllowedInInterval = (e - rowHeight).coerceAtLeast(s)
            val clamped = desiredTop.coerceIn(s, maxAllowedInInterval)
            abs(clamped - desiredTop)
        }
        val maxAllowedInInterval = (bestInterval.second - rowHeight).coerceAtLeast(bestInterval.first)
        return desiredTop.coerceIn(bestInterval.first, maxAllowedInInterval)
    }

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
        val fitting = freeIntervals(minTop, maxBottom, forbidden)
            .filter { (s, e) -> e - s >= rowHeight - EPS }
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
        zoneGap: Float
    ): Pair<Float, Float>? {
        val grid = gridItems(composition, faceOf)
        if (grid.isEmpty()) return null
        val boxes = grid.mapNotNull { widgetBoundsOf(it, itemState, faceOf) }
        if (boxes.isEmpty()) return null
        return (boxes.minOf { it.top } - zoneGap) to (boxes.maxOf { it.bottom } + zoneGap)
    }

    private fun inlineVerticalSpan(
        composition: Composition,
        itemState: ItemState,
        faceOf: (Int) -> ClockFace?,
        zoneGap: Float,
        measuredInlineWidths: Map<Int, Float> = emptyMap()
    ): Pair<Float, Float>? {
        val inline = inlineItems(composition, faceOf)
        if (inline.isEmpty()) return null
        val boxes = inline.mapNotNull { widgetBoundsOf(it, itemState, faceOf, measuredInlineWidths) }
        if (boxes.isEmpty()) return null
        return (boxes.minOf { it.top } - zoneGap) to (boxes.maxOf { it.bottom } + zoneGap)
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
        state: ItemState,
        faceOf: (Int) -> ClockFace?
    ): FacePlacement? {
        val boxes = items.mapNotNull { widgetBoundsOf(it, state, faceOf) }
        if (boxes.isEmpty()) return null
        return FacePlacement(
            x = (boxes.minOf { it.left } + boxes.maxOf { it.right }) / 2f,
            y = boxes.minOf { it.top },
            anchor = FaceAlign.CENTER
        )
    }

    private fun currentInlineCenter(
        items: List<CompositionItem>,
        state: ItemState,
        faceOf: (Int) -> ClockFace?,
        measuredInlineWidths: Map<Int, Float> = emptyMap()
    ): FacePlacement? {
        val boxes = items.mapNotNull { widgetBoundsOf(it, state, faceOf, measuredInlineWidths) }
        if (boxes.isEmpty()) return null
        return FacePlacement(
            x = (boxes.minOf { it.left } + boxes.maxOf { it.right }) / 2f,
            y = boxes.minOf { it.top },
            anchor = FaceAlign.CENTER
        )
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
                    val slack = (layer?.let { if (it.stroke != null) it.strokeWidth else 0f } ?: 0f) + ESTIMATE_EDGE_SLACK
                    w = estimateWidthRatio(layer, tracking) * stretchX * node.size + node.size * slack * 2f
                    h = capRatio * stretch * node.size * lineHeight + descent + node.size * slack * 2f
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

    private const val ESTIMATE_CAP_RATIO = 0.84f
    private const val ESTIMATE_EDGE_SLACK = 0.04f
    private const val ESTIMATE_TEXT_DESCENT = 0.24f
}
