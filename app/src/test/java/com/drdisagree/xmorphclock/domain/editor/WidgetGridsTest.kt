package com.drdisagree.xmorphclock.domain.editor

import com.drdisagree.xmorphclock.domain.model.ClockFace
import com.drdisagree.xmorphclock.domain.model.ColumnNode
import com.drdisagree.xmorphclock.domain.model.Composition
import com.drdisagree.xmorphclock.domain.model.FaceAlign
import com.drdisagree.xmorphclock.domain.model.FaceGrid
import com.drdisagree.xmorphclock.domain.model.FaceLayer
import com.drdisagree.xmorphclock.domain.model.FaceLayerType
import com.drdisagree.xmorphclock.domain.model.FacePlacement
import com.drdisagree.xmorphclock.domain.model.ItemBounds
import com.drdisagree.xmorphclock.domain.model.ItemState
import com.drdisagree.xmorphclock.domain.model.LayerNode
import com.drdisagree.xmorphclock.domain.model.RowNode
import com.drdisagree.xmorphclock.domain.model.StackNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetGridsTest {

    private fun face(
        name: String,
        category: String = "small",
        span: Int = 1,
        height: Float? = null
    ): ClockFace {
        val root = StackNode(
            height = height,
            children = listOf(LayerNode(id = "main", size = 0.1f, placement = FacePlacement(anchor = FaceAlign.CENTER)))
        )
        return ClockFace(name = name, layers = emptyList(), category = category, span = span, large = root, small = root)
    }

    @Test
    fun grid1x4EnforcesMaxFourCells() {
        val w1 = face("W1", category = "small", span = 1)
        val w2 = face("W2", category = "medium", span = 2)
        val w3 = face("W3", category = "small", span = 1)
        val wExtra = face("W4", category = "small", span = 1)
        val map = mapOf(100 to w1, 101 to w2, 102 to w3, 103 to wExtra)

        var comp = Composition().add(100, null, null).add(101, null, null)
        assertEquals(3, WidgetGrids.usedGridCells(comp) { map[it] })
        assertTrue(WidgetGrids.canAdd(comp, w3, faceOf = { map[it] }))
        assertFalse(WidgetGrids.canAdd(comp, w2, faceOf = { map[it] }))

        comp = comp.add(102, null, null)
        assertEquals(4, WidgetGrids.usedGridCells(comp) { map[it] })
        assertFalse(WidgetGrids.canAdd(comp, wExtra, faceOf = { map[it] }))
    }

    @Test
    fun inlineAcceptsMoreThanTwoItemsBoundedByScreenSpace() {
        val inlineA = face("InA", category = "inline", height = 0.08f)
        val inlineB = face("InB", category = "inline", height = 0.08f)
        val inlineC = face("InC", category = "inline", height = 0.08f)
        val gridW = face("GridW", category = "medium", span = 2)
        val map = mapOf(200 to inlineA, 201 to inlineB, 202 to inlineC, 203 to gridW)

        assertEquals(WidgetZone.INLINE_1X2, WidgetGrids.zoneOf(inlineA))
        assertEquals(WidgetZone.GRID_1X4, WidgetGrids.zoneOf(gridW))

        var comp = Composition().add(203, null, null).add(200, null, null)
        assertEquals(1, WidgetGrids.inlineItems(comp) { map[it] }.size)
        assertEquals(1, WidgetGrids.gridItems(comp) { map[it] }.size)
        assertTrue(WidgetGrids.canAdd(comp, inlineB, faceOf = { map[it] }))

        comp = comp.add(201, null, null)
        assertEquals(2, WidgetGrids.inlineItems(comp) { map[it] }.size)
        assertTrue(WidgetGrids.canAdd(comp, inlineC, faceOf = { map[it] }))
        // 1x4 grid still has 2 free cells
        assertTrue(WidgetGrids.canAdd(comp, face("Small", span = 2), faceOf = { map[it] }))
    }

    @Test
    fun layoutZonePlacesGridBelowClockAndInlineBelowGrid() {
        val g1 = face("G1", category = "small", span = 1)
        val in1 = face("I1", category = "inline", height = 0.08f)
        val in2 = face("I2", category = "inline", height = 0.08f)
        val map = mapOf(100 to g1, 200 to in1, 201 to in2)

        var comp = Composition()
            .add(100, null, null)
            .add(200, null, null)
            .add(201, null, null)

        val clockBottom = 0.45f
        comp = WidgetGrids.layoutZone(comp, WidgetZone.GRID_1X4, ItemState.LARGE, { map[it] }, clockBottom)
        comp = WidgetGrids.layoutZone(comp, WidgetZone.INLINE_1X2, ItemState.LARGE, { map[it] }, clockBottom)

        val gridPlacement = WidgetGrids.gridItems(comp) { map[it] }.first().large!!
        val inlineItems = WidgetGrids.inlineItems(comp) { map[it] }
        val inlinePlacements = inlineItems.map { it.large!! }
        assertTrue(gridPlacement.y > clockBottom)
        assertTrue(inlinePlacements[0].y > gridPlacement.y)
        assertEquals(inlinePlacements[0].y, inlinePlacements[1].y, 1e-4f)
        assertEquals(FaceAlign.CENTER, inlinePlacements[0].anchor)
        assertEquals(FaceAlign.CENTER, inlinePlacements[1].anchor)
        assertEquals(0.5f, (inlinePlacements[0].x + inlinePlacements[1].x) / 2f, 1e-4f)
        val w1 = WidgetGrids.estimateInlineWidth(inlineItems[0], { map[it] })
        val w2 = WidgetGrids.estimateInlineWidth(inlineItems[1], { map[it] })
        val expectedSpacing = (w1 + w2) / 2f + WidgetGrids.INLINE_ITEM_GAP
        assertEquals(expectedSpacing, inlinePlacements[1].x - inlinePlacements[0].x, 1e-4f)
    }

    @Test
    fun clampDeltaStopsElementsFromLeavingScreenBounds() {
        val bounds = ItemBounds(left = 0.1f, top = 0.2f, right = 0.5f, bottom = 0.6f)
        val (dxLeft, dyTop) = WidgetGrids.clampDelta(
            bounds = bounds,
            rawDx = -0.5f,
            rawDy = -0.5f,
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.8f
        )
        assertEquals(0.02f, bounds.left + dxLeft, 1e-4f)
        assertEquals(0.02f, bounds.top + dyTop, 1e-4f)

        val (dxRight, dyBottom) = WidgetGrids.clampDelta(
            bounds = bounds,
            rawDx = 1.2f,
            rawDy = 2.0f,
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.8f
        )
        assertEquals(0.98f, bounds.right + dxRight, 1e-4f)
        assertEquals(1.8f, bounds.bottom + dyBottom, 1e-4f)
    }

    @Test
    fun applyMovedItemsShiftsGroupTogether() {
        val comp = Composition().add(100, FacePlacement(0.4f, 0.5f), null).add(101, FacePlacement(0.6f, 0.5f), null)
        val moved = WidgetGrids.applyMovedItems(
            composition = comp,
            ids = setOf(comp.items[1].id, comp.items[2].id),
            itemState = ItemState.LARGE,
            dx = 0.05f,
            dy = -0.1f
        ) { FacePlacement() }

        assertEquals(0.45f, moved.items[1].large!!.x, 1e-4f)
        assertEquals(0.4f, moved.items[1].large!!.y, 1e-4f)
        assertEquals(0.65f, moved.items[2].large!!.x, 1e-4f)
        assertEquals(0.4f, moved.items[2].large!!.y, 1e-4f)
    }

    @Test
    fun addingMultipleGridWidgetsAlwaysAutoCentersSymmetrically() {
        val w1 = face("W1", category = "small", span = 1)
        val w2 = face("W2", category = "small", span = 1)
        val w3 = face("W3", category = "small", span = 1)
        val map = mapOf(100 to w1, 101 to w2, 102 to w3)

        var comp = Composition().add(100, FacePlacement(0.25f, 0.6f), null)
        comp = WidgetGrids.layoutZone(comp, WidgetZone.GRID_1X4, ItemState.LARGE, { map[it] }, 0.45f, centerHorizontally = true)
        assertEquals(0.5f, WidgetGrids.gridItems(comp) { map[it] }.first().large!!.x, 1e-4f)

        comp = comp.add(101, null, null)
        comp = WidgetGrids.layoutZone(comp, WidgetZone.GRID_1X4, ItemState.LARGE, { map[it] }, 0.45f, centerHorizontally = true)
        val two = WidgetGrids.gridItems(comp) { map[it] }.map { it.large!!.x }
        assertEquals(0.5f, (two[0] + two[1]) / 2f, 1e-4f)

        comp = comp.add(102, null, null)
        comp = WidgetGrids.layoutZone(comp, WidgetZone.GRID_1X4, ItemState.LARGE, { map[it] }, 0.45f, centerHorizontally = true)
        val three = WidgetGrids.gridItems(comp) { map[it] }.map { it.large!!.x }
        assertEquals(0.5f, three[1], 1e-4f)
        assertEquals(0.5f, (three[0] + three[2]) / 2f, 1e-4f)
    }

    @Test
    fun clampNonOverlappingDeltaStopsAtObstacleAndShortcutZones() {
        val movingClock = ItemBounds(left = 0.2f, top = 0.1f, right = 0.8f, bottom = 0.4f)
        val widgetObstacle = ItemBounds(left = 0.1f, top = 0.5f, right = 0.9f, bottom = 0.68f)
        val leftShortcut = ItemBounds(left = 0.04f, top = 1.45f, right = 0.20f, bottom = 1.62f)

        val (dx, dy) = WidgetGrids.clampNonOverlappingDelta(
            startBounds = movingClock,
            rawDx = 0.05f,
            rawDy = 0.25f,
            lastValidDelta = 0f to 0f,
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.7f,
            obstacles = listOf(widgetObstacle, leftShortcut),
            gap = 0.02f
        )

        // Horizontal motion still works while vertical stops cleanly 0.02 above widgetObstacle
        assertEquals(0.05f, dx, 1e-4f)
        assertEquals(widgetObstacle.top - 0.02f, movingClock.bottom + dy, 1e-4f)
    }

    @Test
    fun clampNonOverlappingDeltaBlocksMovingOverCornerShortcut() {
        val movingWidget = ItemBounds(left = 0.06f, top = 1.25f, right = 0.25f, bottom = 1.40f)
        val leftShortcut = ItemBounds(left = 0.04f, top = 1.45f, right = 0.20f, bottom = 1.62f)

        val (_, dy) = WidgetGrids.clampNonOverlappingDelta(
            startBounds = movingWidget,
            rawDx = 0f,
            rawDy = 0.20f,
            lastValidDelta = 0f to 0f,
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.7f,
            obstacles = listOf(leftShortcut),
            gap = 0.015f
        )

        assertTrue(movingWidget.bottom + dy <= leftShortcut.top - 0.015f + 1e-4f)
    }

    @Test
    fun solvePushLayoutClockPushesWidgetGridDownWithoutKnockingCenter() {
        val startBounds = mapOf(
            LockTarget.CLOCK to ItemBounds(left = 0.2f, top = 0.1f, right = 0.8f, bottom = 0.4f),
            LockTarget.WIDGET_GRID to ItemBounds(left = 0.15f, top = 0.45f, right = 0.85f, bottom = 0.60f)
        )
        val solved = WidgetGrids.solvePushLayout(
            activeTarget = LockTarget.CLOCK,
            startBounds = startBounds,
            rawDx = 0.03f,
            rawDy = 0.20f,
            lastValidDeltas = mapOf(
                LockTarget.CLOCK to (0f to 0f),
                LockTarget.WIDGET_GRID to (0f to 0f)
            ),
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.65f,
            fixedObstacles = emptyList(),
            gap = 0.02f
        )

        val clockDelta = solved.getValue(LockTarget.CLOCK)
        val gridDelta = solved.getValue(LockTarget.WIDGET_GRID)
        assertEquals(0.03f, clockDelta.first, 1e-4f)
        assertEquals(0.20f, clockDelta.second, 1e-4f)
        // Widget grid keeps its horizontal position while being pushed down to maintain the gap
        assertEquals(0f, gridDelta.first, 1e-4f)
        assertEquals(0.60f + 0.02f - 0.45f, gridDelta.second, 1e-4f)
    }

    @Test
    fun solvePushLayoutChainPushesGridAndInlineAndStopsAtShortcutObstacles() {
        val startBounds = mapOf(
            LockTarget.CLOCK to ItemBounds(left = 0.2f, top = 0.1f, right = 0.8f, bottom = 0.4f),
            LockTarget.WIDGET_GRID to ItemBounds(left = 0.12f, top = 0.45f, right = 0.88f, bottom = 0.65f),
            LockTarget.INLINE_GRID to ItemBounds(left = 0.12f, top = 0.70f, right = 0.88f, bottom = 0.80f)
        )
        val leftShortcut = ItemBounds(left = 0.04f, top = 1.15f, right = 0.20f, bottom = 1.32f)

        val solved = WidgetGrids.solvePushLayout(
            activeTarget = LockTarget.CLOCK,
            startBounds = startBounds,
            rawDx = 0.04f,
            rawDy = 0.80f,
            lastValidDeltas = startBounds.keys.associateWith { 0f to 0f },
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.50f,
            fixedObstacles = listOf(leftShortcut),
            gap = 0.02f
        )

        val clockFinal = shift(startBounds.getValue(LockTarget.CLOCK), solved.getValue(LockTarget.CLOCK))
        val gridFinal = shift(startBounds.getValue(LockTarget.WIDGET_GRID), solved.getValue(LockTarget.WIDGET_GRID))
        val inlineFinal = shift(startBounds.getValue(LockTarget.INLINE_GRID), solved.getValue(LockTarget.INLINE_GRID))

        // Inline grid stops cleanly above shortcut obstacle; grid and clock stack above with gap; X slide still succeeds
        assertTrue(inlineFinal.bottom <= leftShortcut.top - 0.02f + 1e-3f)
        assertTrue(gridFinal.bottom <= inlineFinal.top - 0.02f + 1e-3f)
        assertTrue(clockFinal.bottom <= gridFinal.top - 0.02f + 1e-3f)
        assertEquals(0.04f, solved.getValue(LockTarget.CLOCK).first, 1e-4f)
    }

    @Test
    fun solvePushLayoutWidgetsPushingUpMovesClockUntilTopWall() {
        val startBounds = mapOf(
            LockTarget.CLOCK to ItemBounds(left = 0.2f, top = 0.2f, right = 0.8f, bottom = 0.5f),
            LockTarget.WIDGET_GRID to ItemBounds(left = 0.15f, top = 0.55f, right = 0.85f, bottom = 0.70f)
        )
        val solved = WidgetGrids.solvePushLayout(
            activeTarget = LockTarget.WIDGET_GRID,
            startBounds = startBounds,
            rawDx = 0f,
            rawDy = -0.50f,
            lastValidDeltas = startBounds.keys.associateWith { 0f to 0f },
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.60f,
            fixedObstacles = emptyList(),
            gap = 0.02f
        )

        val clockFinal = shift(startBounds.getValue(LockTarget.CLOCK), solved.getValue(LockTarget.CLOCK))
        val gridFinal = shift(startBounds.getValue(LockTarget.WIDGET_GRID), solved.getValue(LockTarget.WIDGET_GRID))

        assertEquals(0.02f, clockFinal.top, 1e-3f)
        assertEquals(clockFinal.bottom + 0.02f, gridFinal.top, 1e-3f)
    }

    @Test
    fun solveStickyDragClockMovesDockedWidgetsTogether() {
        val startBounds = mapOf(
            LockTarget.CLOCK to ItemBounds(left = 0.2f, top = 0.2f, right = 0.8f, bottom = 0.5f),
            LockTarget.WIDGET_GRID to ItemBounds(left = 0.15f, top = 0.55f, right = 0.85f, bottom = 0.70f)
        )
        val result = WidgetGrids.solveStickyDrag(
            activeTarget = LockTarget.CLOCK,
            startBounds = startBounds,
            rawDx = 0.05f,
            rawDy = 0.10f,
            lastValidDeltas = startBounds.keys.associateWith { 0f to 0f },
            gridDocked = true,
            inlineDocked = true,
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.60f,
            fixedObstacles = emptyList(),
            gap = 0.02f
        )

        assertTrue(result.gridDocked)
        assertEquals(0.05f, result.deltas.getValue(LockTarget.CLOCK).first, 1e-4f)
        assertEquals(0.10f, result.deltas.getValue(LockTarget.CLOCK).second, 1e-4f)
        // Docked widget moves rigidly with clock
        assertEquals(0.05f, result.deltas.getValue(LockTarget.WIDGET_GRID).first, 1e-4f)
        assertEquals(0.10f, result.deltas.getValue(LockTarget.WIDGET_GRID).second, 1e-4f)
    }

    @Test
    fun solveStickyDragDirectWidgetDragDetachesFromClock() {
        val startBounds = mapOf(
            LockTarget.CLOCK to ItemBounds(left = 0.2f, top = 0.2f, right = 0.8f, bottom = 0.5f),
            LockTarget.WIDGET_GRID to ItemBounds(left = 0.15f, top = 0.55f, right = 0.85f, bottom = 0.70f)
        )
        // Drag widget down away from clock
        val result = WidgetGrids.solveStickyDrag(
            activeTarget = LockTarget.WIDGET_GRID,
            startBounds = startBounds,
            rawDx = 0f,
            rawDy = 0.20f,
            lastValidDeltas = startBounds.keys.associateWith { 0f to 0f },
            gridDocked = true,
            inlineDocked = true,
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.60f,
            fixedObstacles = emptyList(),
            gap = 0.02f
        )

        assertFalse(result.gridDocked)
        // Clock did not move
        assertEquals(0f, result.deltas[LockTarget.CLOCK]?.second ?: 0f, 1e-4f)
        // Widget moved independently
        assertEquals(0.20f, result.deltas.getValue(LockTarget.WIDGET_GRID).second, 1e-4f)
    }

    @Test
    fun solveStickyDragTouchingClockReattachesWidget() {
        val startBounds = mapOf(
            LockTarget.CLOCK to ItemBounds(left = 0.2f, top = 0.2f, right = 0.8f, bottom = 0.5f),
            // Start detached further down
            LockTarget.WIDGET_GRID to ItemBounds(left = 0.15f, top = 0.70f, right = 0.85f, bottom = 0.85f)
        )
        // Move widget up into contact with clock
        val result = WidgetGrids.solveStickyDrag(
            activeTarget = LockTarget.WIDGET_GRID,
            startBounds = startBounds,
            rawDx = 0f,
            rawDy = -0.17f,
            lastValidDeltas = startBounds.keys.associateWith { 0f to 0f },
            gridDocked = false,
            inlineDocked = true,
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.60f,
            fixedObstacles = emptyList(),
            gap = 0.02f
        )

        assertTrue(result.gridDocked)
        assertTrue(result.attachedEvent)
    }

    @Test
    fun flipWidgetsPositionTogglesPlacementAboveAndBelowClock() {
        val g1 = face("G1", category = "small", span = 1)
        val in1 = face("I1", category = "inline", height = 0.08f)
        val map = mapOf(100 to g1, 200 to in1)
        val clockBounds = ItemBounds(left = 0.1f, top = 0.35f, right = 0.9f, bottom = 0.65f)

        var comp = Composition().add(100, null, null).add(200, null, null)

        // Flip widgets ABOVE clock
        val above = WidgetGrids.flipWidgetsPosition(
            composition = comp,
            widgetsAbove = true,
            itemState = ItemState.LARGE,
            faceOf = { map[it] },
            clockBounds = clockBounds,
            maxY = 1.6f
        )
        assertTrue(above.widgetsAbove)
        assertTrue(above.gridDocked)
        assertTrue(above.inlineDocked)
        val gridYAbove = above.items.first { it.style == 100 }.large!!.y
        val inlineYAbove = above.items.first { it.style == 200 }.large!!.y
        assertTrue("Grid should be above clock", gridYAbove < clockBounds.top)
        assertTrue("Inline should be above grid", inlineYAbove < gridYAbove)

        // Flip widgets BELOW clock
        val below = WidgetGrids.flipWidgetsPosition(
            composition = above,
            widgetsAbove = false,
            itemState = ItemState.LARGE,
            faceOf = { map[it] },
            clockBounds = clockBounds,
            maxY = 1.6f
        )
        assertFalse(below.widgetsAbove)
        val gridYBelow = below.items.first { it.style == 100 }.large!!.y
        val inlineYBelow = below.items.first { it.style == 200 }.large!!.y
        assertTrue("Grid should be below clock", gridYBelow > clockBounds.bottom)
        assertTrue("Inline should be below grid", inlineYBelow > gridYBelow)
    }

    @Test
    fun solveStickyDragClockLeavesUndockedWidgetsStill() {
        val startBounds = mapOf(
            LockTarget.CLOCK to ItemBounds(left = 0.2f, top = 0.2f, right = 0.8f, bottom = 0.4f),
            LockTarget.WIDGET_GRID to ItemBounds(left = 0.15f, top = 0.70f, right = 0.85f, bottom = 0.85f)
        )
        val result = WidgetGrids.solveStickyDrag(
            activeTarget = LockTarget.CLOCK,
            startBounds = startBounds,
            rawDx = 0.05f,
            rawDy = -0.10f,
            lastValidDeltas = startBounds.keys.associateWith { 0f to 0f },
            gridDocked = false,
            inlineDocked = false,
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.60f,
            fixedObstacles = emptyList(),
            gap = 0.02f
        )

        assertFalse(result.gridDocked)
        assertEquals(0.05f, result.deltas.getValue(LockTarget.CLOCK).first, 1e-4f)
        assertEquals(-0.10f, result.deltas.getValue(LockTarget.CLOCK).second, 1e-4f)
        assertEquals(0f, result.deltas.getValue(LockTarget.WIDGET_GRID).first, 1e-4f)
        assertEquals(0f, result.deltas.getValue(LockTarget.WIDGET_GRID).second, 1e-4f)
    }

    @Test
    fun solveStickyDragClockMidDragReattachDoesNotTeleport() {
        val startBounds = mapOf(
            LockTarget.CLOCK to ItemBounds(left = 0.2f, top = 0.2f, right = 0.8f, bottom = 0.4f),
            LockTarget.WIDGET_GRID to ItemBounds(left = 0.15f, top = 0.70f, right = 0.85f, bottom = 0.85f)
        )
        // Clock already moved down 0.25f without touching widget (0.4+0.25=0.65 < 0.70 - 0.04)
        val lastDeltas = mapOf(
            LockTarget.CLOCK to (0f to 0.25f),
            LockTarget.WIDGET_GRID to (0f to 0f)
        )
        // Clock moves to 0.27f, entering contact range of widget
        val result = WidgetGrids.solveStickyDrag(
            activeTarget = LockTarget.CLOCK,
            startBounds = startBounds,
            rawDx = 0f,
            rawDy = 0.27f,
            lastValidDeltas = lastDeltas,
            gridDocked = false,
            inlineDocked = false,
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.60f,
            fixedObstacles = emptyList(),
            gap = 0.02f
        )

        assertTrue(result.gridDocked)
        assertTrue(result.attachedEvent)
        // Widget should only move by the incremental step (0.02f), not teleport by 0.27f
        assertEquals(0.02f, result.deltas.getValue(LockTarget.WIDGET_GRID).second, 1e-4f)
    }

    @Test
    fun estimateClockBoundsReflectsTallStackedClockAndCompactSmallClock() {
        val stackedClock = tallStackedClockFace()
        val largeBounds = WidgetGrids.estimateClockBounds(stackedClock, null, ItemState.LARGE)
        val smallBounds = WidgetGrids.estimateClockBounds(stackedClock, null, ItemState.SMALL)

        assertTrue("Tall stacked large clock should exceed 0.8f height", (largeBounds.bottom - largeBounds.top) > 0.8f)
        assertTrue("Small clock should remain compact (< 0.4f)", (smallBounds.bottom - smallBounds.top) < 0.4f)
        assertTrue("Large bottom should exceed small bottom", largeBounds.bottom > smallBounds.bottom + 0.4f)
    }

    @Test
    fun layoutZoneStaysOnRequestedSideInsteadOfFlippingOneStateAboveClock() {
        val w1 = face("W1", category = "small", span = 2)
        val map = mapOf(100 to w1)
        // Tall clock leaves plenty of room above, tighter room below (but enough for widget height)
        val tallClockBounds = ItemBounds(left = 0.1f, top = 0.45f, right = 0.9f, bottom = 1.40f)
        val comp = Composition(widgetsAbove = false).add(100, null, null)

        val laidOut = WidgetGrids.layoutZone(
            composition = comp,
            zone = WidgetZone.GRID_1X4,
            itemState = ItemState.LARGE,
            faceOf = { map[it] },
            clockBottomY = tallClockBounds.bottom,
            maxY = 1.75f,
            centerHorizontally = true,
            clockBounds = tallClockBounds,
            widgetsAbove = false
        )

        val placement = WidgetGrids.gridItems(laidOut) { map[it] }.first().large!!
        assertTrue("Widget should stay below clock when widgetsAbove=false", placement.y >= tallClockBounds.bottom)
    }

    @Test
    fun ensureClockClearanceShiftsTallClockUpWhenSpaceBelowIsTight() {
        val w1 = face("W1", category = "small", span = 2)
        val map = mapOf(100 to w1)
        val comp = Composition(widgetsAbove = false).add(100, null, null)
        val tightClock = ItemBounds(left = 0.1f, top = 0.45f, right = 0.9f, bottom = 1.45f)

        val (shiftedComp, shiftedBounds) = WidgetGrids.ensureClockClearanceForWidgets(
            composition = comp,
            itemState = ItemState.LARGE,
            faceOf = { map[it] },
            clockBounds = tightClock,
            maxY = 1.60f
        )

        val movedClock = shiftedComp.clockItem!!.large!!
        assertTrue("Clock should shift upward to make room below", movedClock.y < 0f)
        assertTrue("Shifted bounds bottom should drop below original bottom", shiftedBounds!!.bottom < tightClock.bottom)
    }

    @Test
    fun reconcileWidgetsForClockMovesWidgetsClearAcrossBothSmallAndLarge() {
        val tallClock = tallStackedClockFace()
        val w1 = face("W1", category = "small", span = 2)
        val in1 = face("I1", category = "inline", height = 0.08f)
        val map = mapOf(100 to w1, 200 to in1)

        // Start with widgets placed below a short clock (overlapping tallClock.large)
        val comp = Composition(gridDocked = true, inlineDocked = true, widgetsAbove = false)
            .add(100, large = FacePlacement(0.5f, 0.50f), small = FacePlacement(0.5f, 0.35f))
            .add(200, large = FacePlacement(0.5f, 0.72f), small = FacePlacement(0.5f, 0.55f))

        val reconciled = WidgetGrids.reconcileWidgetsForClock(
            composition = comp,
            clockFace = tallClock,
            faceOf = { map[it] },
            largeMaxY = 1.75f,
            smallMaxY = 1.75f
        )

        val largeClock = WidgetGrids.estimateClockBounds(tallClock, reconciled.clockItem, ItemState.LARGE)
        val smallClock = WidgetGrids.estimateClockBounds(tallClock, reconciled.clockItem, ItemState.SMALL)
        val grid = WidgetGrids.gridItems(reconciled) { map[it] }.first()
        val inline = WidgetGrids.inlineItems(reconciled) { map[it] }.first()

        assertTrue("LARGE grid must stay below tall LARGE clock", grid.large!!.y > largeClock.bottom)
        assertTrue("LARGE inline must stay below LARGE grid", inline.large!!.y > grid.large.y)
        assertTrue("SMALL grid must stay below SMALL clock", grid.small!!.y > smallClock.bottom)
        assertTrue("SMALL inline must stay below SMALL grid", inline.small!!.y > grid.small.y)
    }

    @Test
    fun reconcileWidgetsForClockPreservesNonOverlappingUndockedSmallWhileFixingLargeOverlap() {
        val tallClock = tallStackedClockFace()
        val w1 = face("W1", category = "small", span = 2)
        val map = mapOf(100 to w1)

        // User dragged widget in SMALL to y=0.90 (far below small clock), while LARGE overlaps tallClock
        val comp = Composition(gridDocked = false, inlineDocked = false, widgetsAbove = false)
            .add(100, large = FacePlacement(0.5f, 0.60f), small = FacePlacement(0.32f, 0.90f))

        val reconciled = WidgetGrids.reconcileWidgetsForClock(
            composition = comp,
            clockFace = tallClock,
            faceOf = { map[it] },
            largeMaxY = 1.75f,
            smallMaxY = 1.75f
        )

        val largeClock = WidgetGrids.estimateClockBounds(tallClock, reconciled.clockItem, ItemState.LARGE)
        val grid = WidgetGrids.gridItems(reconciled) { map[it] }.first()

        assertTrue("Overlapping LARGE widget must be moved clear of tall clock", grid.large!!.y > largeClock.bottom)
        assertEquals(0.32f, grid.small!!.x, 1e-4f)
        assertEquals(0.90f, grid.small.y, 1e-4f)
    }

    private fun tallStackedClockFace(): ClockFace {
        val layers = listOf(
            FaceLayer("hours", FaceLayerType.HOURS),
            FaceLayer("minutes", FaceLayerType.MINUTES)
        )
        val large = ColumnNode(
            gap = 0.04f,
            placement = FacePlacement(0.5f, 0.12f, FaceAlign.CENTER),
            children = listOf(
                LayerNode("hours", size = 0.56f),
                LayerNode("minutes", size = 0.56f)
            )
        )
        val small = RowNode(
            gap = 0.02f,
            placement = FacePlacement(0.5f, 0.05f, FaceAlign.CENTER),
            children = listOf(
                LayerNode("hours", size = 0.24f),
                LayerNode("minutes", size = 0.24f)
            )
        )
        return ClockFace(name = "Tall", layers = layers, large = large, small = small)
    }

    @Test
    fun calculatePlaceholdersMovesInlineAboveClockWhenClockPushedToBottomWithActiveGrid() {
        val clock = ItemBounds(left = 80f, top = 740f, right = 420f, bottom = 840f)
        val grid = ItemBounds(left = 40f, top = 860f, right = 460f, bottom = 960f)

        val res = WidgetGrids.calculatePlaceholderBounds(
            clockBounds = clock,
            gridBounds = grid,
            inlineBounds = null,
            widgetsAbove = false,
            width = 500f,
            height = 1100f,
            unit = 420f,
            frameLeft = 40f,
            edgePadPx = 20f,
            slotGap = 20f,
            safeBottomPx = 980f,
            gridSlotH = 72f,
            inlineSlotH = 72f,
            minTouch = 72f,
            isAod = false
        )

        val inlinePh = requireNotNull(res.inlinePlaceholder)
        assertTrue("Inline placeholder must not overlap clock", inlinePh.bottom <= clock.top - 19f)
        assertTrue("Inline placeholder must not overlap grid", !boundsOverlap(inlinePh, grid))
        assertTrue("Inline placeholder must stay inside top edge", inlinePh.top >= 19.5f)
        assertTrue("Inline placeholder must stay inside safe bottom", inlinePh.bottom <= 980.5f)
    }

    @Test
    fun calculatePlaceholdersDocksInlineBelowGridWhenRoomExists() {
        val clock = ItemBounds(left = 80f, top = 160f, right = 420f, bottom = 280f)
        val grid = ItemBounds(left = 40f, top = 300f, right = 460f, bottom = 400f)

        val res = WidgetGrids.calculatePlaceholderBounds(
            clockBounds = clock,
            gridBounds = grid,
            inlineBounds = null,
            widgetsAbove = false,
            width = 500f,
            height = 1100f,
            unit = 420f,
            frameLeft = 40f,
            edgePadPx = 20f,
            slotGap = 20f,
            safeBottomPx = 980f,
            gridSlotH = 72f,
            inlineSlotH = 72f,
            minTouch = 72f,
            isAod = false
        )

        val inlinePh = requireNotNull(res.inlinePlaceholder)
        assertTrue("Inline placeholder should dock below grid", inlinePh.top >= grid.bottom + 19.5f)
        assertTrue("Inline placeholder stays above safeBottom", inlinePh.bottom <= 980.5f)
    }

    @Test
    fun calculatePlaceholdersBothEmptyAdaptWhenClockNearBottom() {
        val clock = ItemBounds(left = 80f, top = 800f, right = 420f, bottom = 920f)

        val res = WidgetGrids.calculatePlaceholderBounds(
            clockBounds = clock,
            gridBounds = null,
            inlineBounds = null,
            widgetsAbove = false,
            width = 500f,
            height = 1100f,
            unit = 420f,
            frameLeft = 40f,
            edgePadPx = 20f,
            slotGap = 20f,
            safeBottomPx = 960f,
            gridSlotH = 72f,
            inlineSlotH = 72f,
            minTouch = 72f,
            isAod = false
        )

        val gridPh = requireNotNull(res.gridPlaceholder)
        val inlinePh = requireNotNull(res.inlinePlaceholder)
        assertTrue("Grid placeholder must avoid clock", !boundsOverlap(gridPh, clock))
        assertTrue("Inline placeholder must avoid clock", !boundsOverlap(inlinePh, clock))
        assertTrue("Placeholders must not overlap each other", !boundsOverlap(gridPh, inlinePh))
        assertTrue("Grid stays within safe bounds", gridPh.top >= 19.5f && gridPh.bottom <= 960.5f)
        assertTrue("Inline stays within safe bounds", inlinePh.top >= 19.5f && inlinePh.bottom <= 960.5f)
    }

    @Test
    fun solveStickyDragInlineDocksToGridAndMovesWithGrid() {
        val startBounds = mapOf(
            LockTarget.CLOCK to ItemBounds(left = 0.2f, top = 0.1f, right = 0.8f, bottom = 0.35f),
            LockTarget.WIDGET_GRID to ItemBounds(left = 0.12f, top = 0.45f, right = 0.88f, bottom = 0.62f),
            LockTarget.INLINE_GRID to ItemBounds(left = 0.25f, top = 0.80f, right = 0.75f, bottom = 0.88f)
        )

        val dragUp = WidgetGrids.solveStickyDrag(
            activeTarget = LockTarget.INLINE_GRID,
            startBounds = startBounds,
            rawDx = 0f,
            rawDy = -0.16f,
            lastValidDeltas = startBounds.keys.associateWith { 0f to 0f },
            gridDocked = false,
            inlineDocked = false,
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.60f,
            fixedObstacles = emptyList(),
            gap = 0.018f
        )

        assertTrue("Inline dragged to widget grid should dock", dragUp.inlineDocked)
        assertTrue("Attachment event should fire", dragUp.attachedEvent)
        val gridAfter = shift(startBounds.getValue(LockTarget.WIDGET_GRID), dragUp.deltas.getValue(LockTarget.WIDGET_GRID))
        val inlineAfter = shift(startBounds.getValue(LockTarget.INLINE_GRID), dragUp.deltas.getValue(LockTarget.INLINE_GRID))
        assertTrue("Inline must never cross widget grid", inlineAfter.top >= gridAfter.bottom - 1e-4f)

        // Now dragging WIDGET_GRID should carry the docked INLINE_GRID along with it
        val carryBounds = mapOf(
            LockTarget.CLOCK to startBounds.getValue(LockTarget.CLOCK),
            LockTarget.WIDGET_GRID to gridAfter,
            LockTarget.INLINE_GRID to inlineAfter
        )
        val dragGrid = WidgetGrids.solveStickyDrag(
            activeTarget = LockTarget.WIDGET_GRID,
            startBounds = carryBounds,
            rawDx = 0.04f,
            rawDy = 0.12f,
            lastValidDeltas = carryBounds.keys.associateWith { 0f to 0f },
            gridDocked = dragUp.gridDocked,
            inlineDocked = dragUp.inlineDocked,
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.60f,
            fixedObstacles = emptyList(),
            gap = 0.018f
        )

        assertEquals(0.04f, dragGrid.deltas.getValue(LockTarget.INLINE_GRID).first, 1e-4f)
        assertEquals(0.12f, dragGrid.deltas.getValue(LockTarget.INLINE_GRID).second, 1e-4f)
    }

    @Test
    fun solveStickyDragClockApproachingInlineDockedBetweenClockAndGridDoesNotCross() {
        val startBounds = mapOf(
            LockTarget.CLOCK to ItemBounds(left = 0.2f, top = 0.10f, right = 0.8f, bottom = 0.35f),
            LockTarget.INLINE_GRID to ItemBounds(left = 0.25f, top = 0.56f, right = 0.75f, bottom = 0.64f),
            LockTarget.WIDGET_GRID to ItemBounds(left = 0.12f, top = 0.68f, right = 0.88f, bottom = 0.85f)
        )

        val result = WidgetGrids.solveStickyDrag(
            activeTarget = LockTarget.CLOCK,
            startBounds = startBounds,
            rawDx = 0f,
            rawDy = 0.35f,
            lastValidDeltas = startBounds.keys.associateWith { 0f to 0f },
            gridDocked = false,
            inlineDocked = true,
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.60f,
            fixedObstacles = emptyList(),
            gap = 0.018f
        )

        val clockFinal = shift(startBounds.getValue(LockTarget.CLOCK), result.deltas.getValue(LockTarget.CLOCK))
        val inlineFinal = shift(startBounds.getValue(LockTarget.INLINE_GRID), result.deltas.getValue(LockTarget.INLINE_GRID))
        val gridFinal = shift(startBounds.getValue(LockTarget.WIDGET_GRID), result.deltas.getValue(LockTarget.WIDGET_GRID))

        assertTrue("Clock must stay above inline", clockFinal.bottom <= inlineFinal.top - 0.017f)
        assertTrue("Inline must stay above grid without crossing", inlineFinal.bottom <= gridFinal.top - 0.017f)
    }

    @Test
    fun solveStickyDragClockPinnedAgainstTightInlineAndGridNeverPenetrates() {
        // Inline starts closer than gap (0.01f < 0.018f) above grid, and grid is pinned at maxY
        val startBounds = mapOf(
            LockTarget.CLOCK to ItemBounds(left = 0.2f, top = 0.50f, right = 0.8f, bottom = 0.80f),
            LockTarget.INLINE_GRID to ItemBounds(left = 0.25f, top = 0.90f, right = 0.75f, bottom = 0.98f),
            LockTarget.WIDGET_GRID to ItemBounds(left = 0.12f, top = 0.99f, right = 0.88f, bottom = 1.16f)
        )

        val result = WidgetGrids.solveStickyDrag(
            activeTarget = LockTarget.CLOCK,
            startBounds = startBounds,
            rawDx = 0f,
            rawDy = 0.40f,
            lastValidDeltas = startBounds.keys.associateWith { 0f to 0f },
            gridDocked = false,
            inlineDocked = false,
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.16f,
            fixedObstacles = emptyList(),
            gap = 0.018f
        )

        val clockFinal = shift(startBounds.getValue(LockTarget.CLOCK), result.deltas.getValue(LockTarget.CLOCK))
        val inlineFinal = shift(startBounds.getValue(LockTarget.INLINE_GRID), result.deltas.getValue(LockTarget.INLINE_GRID))
        val gridFinal = shift(startBounds.getValue(LockTarget.WIDGET_GRID), result.deltas.getValue(LockTarget.WIDGET_GRID))

        assertFalse("Clock must not physically overlap inline", boundsOverlap(clockFinal, inlineFinal))
        assertFalse("Inline must not physically overlap grid", boundsOverlap(inlineFinal, gridFinal))
    }

    @Test
    fun solvePushLayoutSideBySideNeighborPushesHorizontally() {
        val startBounds = mapOf(
            LockTarget.CLOCK to ItemBounds(left = 0.10f, top = 0.20f, right = 0.45f, bottom = 0.40f),
            LockTarget.INLINE_GRID to ItemBounds(left = 0.48f, top = 0.24f, right = 0.78f, bottom = 0.34f)
        )

        val solved = WidgetGrids.solvePushLayout(
            activeTarget = LockTarget.CLOCK,
            startBounds = startBounds,
            rawDx = 0.12f,
            rawDy = 0f,
            lastValidDeltas = startBounds.keys.associateWith { 0f to 0f },
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.60f,
            fixedObstacles = emptyList(),
            gap = 0.018f
        )

        val clockFinal = shift(startBounds.getValue(LockTarget.CLOCK), solved.getValue(LockTarget.CLOCK))
        val inlineFinal = shift(startBounds.getValue(LockTarget.INLINE_GRID), solved.getValue(LockTarget.INLINE_GRID))
        assertTrue("Inline should be pushed right along X", inlineFinal.left >= clockFinal.right + 0.017f)
        assertEquals(0.24f, inlineFinal.top, 1e-4f)
    }

    @Test
    fun flipWidgetsPositionAodStatesUpdateAodPlacements() {
        val g1 = face("G1", category = "small", span = 2)
        val in1 = face("I1", category = "inline", height = 0.08f)
        val map = mapOf(100 to g1, 200 to in1)
        val clockBounds = ItemBounds(left = 0.1f, top = 0.35f, right = 0.9f, bottom = 0.65f)

        val comp = Composition()
            .add(100, large = FacePlacement(0.5f, 0.70f), small = FacePlacement(0.5f, 0.70f))
            .add(200, large = FacePlacement(0.5f, 0.90f), small = FacePlacement(0.5f, 0.90f))

        val flippedLargeAod = WidgetGrids.flipWidgetsPosition(
            composition = comp,
            widgetsAbove = true,
            itemState = ItemState.AOD_LARGE,
            faceOf = { map[it] },
            clockBounds = clockBounds,
            maxY = 1.6f
        )
        val flippedAllAod = WidgetGrids.flipWidgetsPosition(
            composition = flippedLargeAod,
            widgetsAbove = true,
            itemState = ItemState.AOD_SMALL,
            faceOf = { map[it] },
            clockBounds = clockBounds,
            maxY = 1.6f
        )

        val gridAodLarge = flippedAllAod.items.first { it.style == 100 }.aodLarge!!
        val inlineAodLarge = flippedAllAod.items.first { it.style == 200 }.aodLarge!!
        val gridAodSmall = flippedAllAod.items.first { it.style == 100 }.aodSmall!!
        val inlineAodSmall = flippedAllAod.items.first { it.style == 200 }.aodSmall!!

        assertTrue(gridAodLarge.y < clockBounds.top)
        assertTrue(inlineAodLarge.y < gridAodLarge.y)
        assertTrue(gridAodSmall.y < clockBounds.top)
        assertTrue(inlineAodSmall.y < gridAodSmall.y)
    }

    @Test
    fun grid2x4PacksUpToEightCellsAcrossTwoRows() {
        val m1 = face("M1", category = "medium", span = 2)
        val m2 = face("M2", category = "medium", span = 2)
        val m3 = face("M3", category = "medium", span = 2)
        val m4 = face("M4", category = "medium", span = 2)
        val extra = face("Extra", category = "small", span = 1)
        val map = mapOf(100 to m1, 101 to m2, 102 to m3, 103 to m4, 104 to extra)

        var comp = Composition(gridCols = 4, gridRows = 2)
            .add(100, null, null)
            .add(101, null, null)
            .add(102, null, null)
        assertTrue(WidgetGrids.canAdd(comp, m4, faceOf = { map[it] }))

        comp = comp.add(103, null, null)
        assertEquals(8, WidgetGrids.usedGridCells(comp) { map[it] })
        assertFalse(WidgetGrids.canAdd(comp, extra, faceOf = { map[it] }))

        val laidOut = WidgetGrids.layoutZone(comp, WidgetZone.GRID_1X4, ItemState.LARGE, { map[it] }, 0.40f)
        val placements = WidgetGrids.gridItems(laidOut) { map[it] }.map { it.large!! }
        assertEquals(placements[0].y, placements[1].y, 1e-4f)
        assertEquals(placements[2].y, placements[3].y, 1e-4f)
        assertTrue("Second row must be strictly below first row", placements[2].y > placements[0].y + FaceGrid.CELL)
        assertEquals(0.5f, (placements[0].x + placements[1].x) / 2f, 1e-4f)
        assertEquals(0.5f, (placements[2].x + placements[3].x) / 2f, 1e-4f)
    }

    @Test
    fun inlineFlowPackingWrapsIntoSecondRowAndCentersEachRow() {
        val in1 = face("I1", category = "inline", height = 0.08f)
        val in2 = face("I2", category = "inline", height = 0.08f)
        val in3 = face("I3", category = "inline", height = 0.08f)
        val map = mapOf(200 to in1, 201 to in2, 202 to in3)

        val comp = Composition(inlineRows = 2)
            .add(200, null, null)
            .add(201, null, null)
            .add(202, null, null)
        val widths = mapOf(
            comp.items[1].id to 0.42f,
            comp.items[2].id to 0.42f,
            comp.items[3].id to 0.30f
        )

        val laidOut = WidgetGrids.layoutZone(
            composition = comp,
            zone = WidgetZone.INLINE_1X2,
            itemState = ItemState.LARGE,
            faceOf = { map[it] },
            clockBottomY = 0.40f,
            measuredInlineWidths = widths
        )
        val placements = WidgetGrids.inlineItems(laidOut) { map[it] }.map { it.large!! }
        // First two fit on row 1 (0.42 + 0.036 + 0.42 = 0.876 <= 0.955), third wraps to row 2 centered
        assertEquals(placements[0].y, placements[1].y, 1e-4f)
        assertTrue("Third inline widget should wrap to second row", placements[2].y > placements[0].y + 0.05f)
        assertEquals(0.5f, (placements[0].x + placements[1].x) / 2f, 1e-4f)
        assertEquals(0.5f, placements[2].x, 1e-4f)
        assertEquals(0.42f + WidgetGrids.INLINE_ITEM_GAP, placements[1].x - placements[0].x, 1e-4f)
    }

    @Test
    fun solveStickyDragDetachedWidgetDraggedOutwardStaysDetached() {
        val startBounds = mapOf(
            LockTarget.CLOCK to ItemBounds(left = 0.2f, top = 0.2f, right = 0.8f, bottom = 0.5f),
            LockTarget.WIDGET_GRID to ItemBounds(left = 0.15f, top = 0.54f, right = 0.85f, bottom = 0.69f)
        )
        val result = WidgetGrids.solveStickyDrag(
            activeTarget = LockTarget.WIDGET_GRID,
            startBounds = startBounds,
            rawDx = 0f,
            rawDy = 0.08f,
            lastValidDeltas = startBounds.keys.associateWith { 0f to 0f },
            gridDocked = false,
            inlineDocked = false,
            minX = 0.02f,
            maxX = 0.98f,
            minY = 0.02f,
            maxY = 1.60f,
            fixedObstacles = emptyList(),
            gap = 0.02f
        )

        assertFalse("Dragging detached widget outward away from clock must stay detached", result.gridDocked)
        assertFalse(result.attachedEvent)
    }

    @Test
    fun layoutZoneTightBelowClockShrinksGapInsteadOfJumpingAboveClock() {
        val g1 = face("G1", category = "small", span = 2)
        val in1 = face("I1", category = "inline", height = 0.08f)
        val map = mapOf(100 to g1, 200 to in1)
        val clockBounds = ItemBounds(left = 0.1f, top = 0.45f, right = 0.9f, bottom = 1.25f)
        // Below clock space = (1.58 - 0.02) - 1.25 = 0.31f.
        // Grid (0.14f) + Inline (0.08f) + standard gaps (0.04*2 = 0.08) = 0.30f; if maxY is slightly tighter (1.51f -> 0.24f avail):
        // With shrinking gaps (0.016 or 0.004) inline still fits below clock instead of jumping above!
        val comp = Composition(widgetsAbove = false)
            .add(100, large = FacePlacement(0.5f, 1.29f), small = null)
            .add(200, large = FacePlacement(0.5f, 1.46f), small = null)

        val laidOut = WidgetGrids.layoutZone(
            composition = comp,
            zone = WidgetZone.INLINE_1X2,
            itemState = ItemState.LARGE,
            faceOf = { map[it] },
            clockBottomY = clockBounds.bottom,
            maxY = 1.53f,
            centerHorizontally = true,
            clockBounds = clockBounds,
            widgetsAbove = false
        )

        val inlineY = WidgetGrids.inlineItems(laidOut) { map[it] }.first().large!!.y
        assertTrue("Inline should tighten gap and stay below clock instead of jumping above (inlineY=$inlineY)", inlineY >= clockBounds.bottom)
    }

    @Test
    fun layoutZoneWithPreserveExistingSideKeepsWidgetsAboveClockDespiteStaleFlag() {
        val g1 = face("G1", category = "small", span = 2)
        val map = mapOf(100 to g1)
        val clockBounds = ItemBounds(left = 0.1f, top = 0.45f, right = 0.9f, bottom = 0.85f)
        // Widget was manually dragged above clock while widgetsAbove is still false
        val comp = Composition(widgetsAbove = false)
            .add(100, large = FacePlacement(0.3f, 0.20f), small = null)

        val laidOut = WidgetGrids.layoutZone(
            composition = comp,
            zone = WidgetZone.GRID_1X4,
            itemState = ItemState.LARGE,
            faceOf = { map[it] },
            clockBottomY = clockBounds.bottom,
            maxY = 1.65f,
            centerHorizontally = true,
            clockBounds = clockBounds,
            widgetsAbove = false,
            preserveExistingSide = true
        )

        val gridY = WidgetGrids.gridItems(laidOut) { map[it] }.first().large!!.y
        assertTrue("preserveExistingSide=true must keep manually dragged widget above clock", gridY < clockBounds.top)
    }

    @Test
    fun repackInlineHorizontallyKeepsVerticalPositionIntact() {
        val in1 = face("I1", category = "inline", height = 0.08f)
        val in2 = face("I2", category = "inline", height = 0.08f)
        val map = mapOf(200 to in1, 201 to in2)
        val comp = Composition(widgetsAbove = true)
            .add(200, large = FacePlacement(0.4f, 0.14f), small = null)
            .add(201, large = FacePlacement(0.6f, 0.14f), small = null)

        val widths = mapOf(
            comp.items[1].id to 0.25f,
            comp.items[2].id to 0.35f
        )

        val repacked = WidgetGrids.repackInlineHorizontally(
            composition = comp,
            itemState = ItemState.LARGE,
            faceOf = { map[it] },
            measuredInlineWidths = widths,
            centerHorizontally = true
        )

        val placements = WidgetGrids.inlineItems(repacked) { map[it] }.map { it.large!! }
        assertEquals(0.14f, placements[0].y, 1e-4f)
        assertEquals(0.14f, placements[1].y, 1e-4f)
        assertEquals(0.25f / 2f + 0.35f / 2f + WidgetGrids.INLINE_ITEM_GAP, placements[1].x - placements[0].x, 1e-4f)
    }

    @Test
    fun areWidgetsAboveClockAndSyncWidgetsAboveFlagDetectSpatialSideAccurately() {
        val g1 = face("G1", category = "small", span = 2)
        val map = mapOf(100 to g1)
        val clockBounds = ItemBounds(left = 0.1f, top = 0.40f, right = 0.9f, bottom = 0.80f)

        // Stale flag says false, but widget was dragged above clock
        val compAbove = Composition(widgetsAbove = false)
            .add(100, large = FacePlacement(0.5f, 0.15f), small = null)
        assertEquals(
            true,
            WidgetGrids.areWidgetsAboveClock(compAbove, ItemState.LARGE, { map[it] }, clockBounds)
        )
        val syncedAbove = WidgetGrids.syncWidgetsAboveFlag(compAbove, ItemState.LARGE, { map[it] }, clockBounds)
        assertTrue(syncedAbove.widgetsAbove)

        // Stale flag says true, but widget was dragged below clock
        val compBelow = Composition(widgetsAbove = true)
            .add(100, large = FacePlacement(0.5f, 0.95f), small = null)
        assertEquals(
            false,
            WidgetGrids.areWidgetsAboveClock(compBelow, ItemState.LARGE, { map[it] }, clockBounds)
        )
        val syncedBelow = WidgetGrids.syncWidgetsAboveFlag(compBelow, ItemState.LARGE, { map[it] }, clockBounds)
        assertFalse(syncedBelow.widgetsAbove)
    }

    @Test
    fun areWidgetsAboveClockPrioritizesPreferredTarget() {
        val g1 = face("G1", category = "small", span = 2)
        val in1 = face("I1", category = "inline", height = 0.08f)
        val map = mapOf(100 to g1, 200 to in1)
        val clockBounds = ItemBounds(left = 0.1f, top = 0.45f, right = 0.9f, bottom = 0.75f)

        val comp = Composition()
            .add(100, large = FacePlacement(0.5f, 0.15f), small = null) // above clock
            .add(200, large = FacePlacement(0.5f, 0.95f), small = null) // below clock

        assertEquals(
            true,
            WidgetGrids.areWidgetsAboveClock(comp, ItemState.LARGE, { map[it] }, clockBounds, preferredTarget = LockTarget.WIDGET_GRID)
        )
        assertEquals(
            false,
            WidgetGrids.areWidgetsAboveClock(comp, ItemState.LARGE, { map[it] }, clockBounds, preferredTarget = LockTarget.INLINE_GRID)
        )
    }

    @Test
    fun fitClockScaleForCompositionShrinksOversizedClockToAvoidWidgetOverlap() {
        val oversizedClock = tallStackedClockFace()
        val g1 = face("G1", category = "small", span = 2)
        val in1 = face("I1", category = "inline", height = 0.08f)
        val map = mapOf(100 to g1, 200 to in1)

        // Screen maxY = 1.25f: tall clock (~1.07f) + grid (0.14f) + inline (0.08f) + gaps exceeds available height.
        var comp = Composition(gridDocked = true, inlineDocked = true, widgetsAbove = false)
            .add(100, large = FacePlacement(0.5f, 1.30f), small = FacePlacement(0.5f, 0.35f))
            .add(200, large = FacePlacement(0.5f, 1.48f), small = FacePlacement(0.5f, 0.55f))

        comp = WidgetGrids.fitClockScaleForComposition(
            composition = comp,
            clockFace = oversizedClock,
            faceOf = { map[it] },
            largeMaxY = 1.25f,
            smallMaxY = 1.45f
        )
        comp = WidgetGrids.reconcileWidgetsForClock(
            composition = comp,
            clockFace = oversizedClock,
            faceOf = { map[it] },
            largeMaxY = 1.25f,
            smallMaxY = 1.45f
        )

        val fittedClock = requireNotNull(comp.clockItem)
        assertTrue("Oversized LARGE clock scale should shrink below 1.0", fittedClock.scale(ItemState.LARGE) < 1f)
        assertFalse(
            "Auto-fitted LARGE layout should have zero collisions",
            WidgetGrids.hasAnyCollision(comp, oversizedClock, { map[it] }, ItemState.LARGE, maxY = 1.25f)
        )
        assertFalse(
            "Auto-fitted SMALL layout should have zero collisions",
            WidgetGrids.hasAnyCollision(comp, oversizedClock, { map[it] }, ItemState.SMALL, maxY = 1.45f)
        )
    }

    @Test
    fun fitClockScaleForCompositionAndIsAnalogEnforceUniformAnalogScaling() {
        val analogLayers = listOf(
            FaceLayer("hours", FaceLayerType.HAND_HOUR),
            FaceLayer("minutes", FaceLayerType.HAND_MINUTE)
        )
        val large = StackNode(
            placement = FacePlacement(0.5f, 0.25f, FaceAlign.CENTER),
            children = listOf(
                LayerNode("hours", size = 0.90f),
                LayerNode("minutes", size = 0.90f)
            )
        )
        val analogClock = ClockFace(name = "Analog", layers = analogLayers, large = large, small = large)
        assertTrue(analogClock.isAnalog)

        val g1 = face("G1", category = "small", span = 2)
        val map = mapOf(100 to g1)
        val baseComp = Composition().add(100, null, null)
        val stretchedClock = baseComp.clockItem!!.withTransform(ItemState.LARGE, 1.1f, 1.4f, 0.7f)
        val comp = baseComp.copy(items = baseComp.items.map { if (it.clock) stretchedClock else it })

        val fitted = WidgetGrids.fitClockScaleForComposition(
            composition = comp,
            clockFace = analogClock,
            faceOf = { map[it] },
            largeMaxY = 1.45f,
            smallMaxY = 1.45f
        )

        val fittedClock = requireNotNull(fitted.clockItem)
        assertEquals("Analog clock widthStretch must stay uniform (1.0)", 1f, fittedClock.widthStretch(ItemState.LARGE), 1e-4f)
        assertEquals("Analog clock heightStretch must stay uniform (1.0)", 1f, fittedClock.heightStretch(ItemState.LARGE), 1e-4f)
    }

    @Test
    fun clampClockBoundsToScreenKeepsOffCenterResizedClockInsideEdges() {
        val clockFace = tallStackedClockFace()
        val g1 = face("G1", category = "small", span = 2)
        val map = mapOf(100 to g1)
        var comp = Composition().add(100, large = FacePlacement(0.5f, 1.15f), small = null)
        // Simulate off-center drag + oversized scale pushing clock past left and bottom bounds
        val offCenterClock = comp.clockItem!!
            .with(ItemState.LARGE, FacePlacement(x = -0.10f, y = 0.95f, anchor = FaceAlign.CENTER))
            .withTransform(ItemState.LARGE, scale = 0.8f, widthStretch = 1f, heightStretch = 1f)
        comp = comp.copy(items = comp.items.map { if (it.clock) offCenterClock else it })

        val (clampedComp, clampedBounds) = WidgetGrids.clampClockBoundsToScreen(
            composition = comp,
            clockFace = clockFace,
            itemState = ItemState.LARGE,
            maxY = 1.60f
        )

        val bounds = requireNotNull(clampedBounds)
        assertTrue("Clamped clock left must stay inside horizontal safe edge", bounds.left >= 0.02f)
        assertTrue("Clamped clock right must stay inside horizontal safe edge", bounds.right <= 0.98f)
        assertTrue("Clamped clock bottom must stay inside bottom safe edge", bounds.bottom <= 1.60f - 0.02f)
        assertTrue("Clamped composition updated clock placement", clampedComp.clockItem!!.large != offCenterClock.large)
        assertTrue(
            "Overlapping clock + widget must report positive layoutPenalty and hasAnyCollision",
            WidgetGrids.hasAnyCollision(clampedComp, clockFace, { map[it] }, ItemState.LARGE, maxY = 1.60f)
        )
    }

    @Test
    fun faceEditorStretchNodeQuantizesFontAxesToAvoidCacheThrashing() {
        val layer = FaceLayer("hours", FaceLayerType.HOURS, font = "", width = 100, height = 700)
        val node = LayerNode("hours", size = 0.4f)
        val stretchedA = FaceEditor.stretchNode(node, listOf(layer), widthStretch = 1.131f, heightStretch = 1.121f) as LayerNode
        val stretchedB = FaceEditor.stretchNode(node, listOf(layer), widthStretch = 1.139f, heightStretch = 1.129f) as LayerNode

        assertTrue("Width axis must be quantized to multiples of 5", (stretchedA.width ?: 0) % 5 == 0)
        assertTrue("Height axis must be quantized to multiples of 8", (stretchedA.height ?: 0) % 8 == 0)
        assertEquals("Tiny stretch deltas within one quantization step should produce identical axes", stretchedA.width, stretchedB.width)
        assertEquals("Tiny stretch deltas within one quantization step should produce identical axes", stretchedA.height, stretchedB.height)
    }

    @Test
    fun fitClockScaleForCompositionDoesNotCrashWhenScaleOrStretchBelowAutoFitFloors() {
        val oversizedClock = tallStackedClockFace()
        val g1 = face("G1", category = "small", span = 2)
        val map = mapOf(100 to g1)
        val baseComp = Composition(gridDocked = true, widgetsAbove = false)
            .add(100, large = FacePlacement(0.5f, 1.30f), small = FacePlacement(0.5f, 0.35f))
        val minStretchedClock = baseComp.clockItem!!
            .withTransform(ItemState.LARGE, scale = 0.40f, widthStretch = 0.45f, heightStretch = 0.45f)
            .withTransform(ItemState.SMALL, scale = 0.35f, widthStretch = 0.45f, heightStretch = 0.45f)
        val comp = baseComp.copy(items = baseComp.items.map { if (it.clock) minStretchedClock else it })

        val fitted = WidgetGrids.fitClockScaleForComposition(
            composition = comp,
            clockFace = oversizedClock,
            faceOf = { map[it] },
            largeMaxY = 0.90f,
            smallMaxY = 0.90f,
            largeClockBounds = ItemBounds(left = 0.01f, top = 0.05f, right = 0.99f, bottom = 0.95f),
            smallClockBounds = ItemBounds(left = 0.01f, top = 0.05f, right = 0.99f, bottom = 0.85f),
            largeMinY = -0.18f,
            smallMinY = -0.14f
        )

        val fittedClock = requireNotNull(fitted.clockItem)
        assertEquals(0.40f, fittedClock.scale(ItemState.LARGE), 1e-4f)
        assertEquals(0.45f, fittedClock.widthStretch(ItemState.LARGE), 1e-4f)
        assertEquals(0.45f, fittedClock.heightStretch(ItemState.LARGE), 1e-4f)
    }

    @Test
    fun negativeMinYAllowsTopAlignedClockAtZeroYWithoutShiftingOrColliding() {
        val clockFace = tallStackedClockFace()
        val comp = Composition()
        val topAlignedBounds = ItemBounds(left = 0.15f, top = -0.02f, right = 0.85f, bottom = 0.65f)

        val (clampedComp, clampedBounds) = WidgetGrids.clampClockBoundsToScreen(
            composition = comp,
            clockFace = clockFace,
            itemState = ItemState.LARGE,
            maxY = 1.65f,
            clockBoundsOverride = topAlignedBounds,
            minY = -0.18f
        )

        assertEquals(topAlignedBounds, clampedBounds)
        assertEquals(comp, clampedComp)
        assertFalse(
            "Top-aligned clock within negative minY frame origin must not report collision",
            WidgetGrids.hasAnyCollision(
                composition = comp,
                clockFace = clockFace,
                faceOf = { null },
                itemState = ItemState.LARGE,
                maxY = 1.65f,
                clockBoundsOverride = topAlignedBounds,
                minY = -0.18f
            )
        )
        val fitted = WidgetGrids.fitClockScaleForComposition(
            composition = comp,
            clockFace = clockFace,
            faceOf = { null },
            largeMaxY = 1.65f,
            smallMaxY = 1.65f,
            largeClockBounds = topAlignedBounds,
            largeMinY = -0.18f,
            smallMinY = -0.14f
        )
        assertEquals(1f, fitted.clockItem!!.scale(ItemState.LARGE), 1e-4f)
    }

    @Test
    fun maxFittingGridRowsAndInlineRowsRespectRealClockAndSiblingHeight() {
        val g1 = face("G1", category = "small", span = 2)
        val in1 = face("I1", category = "inline", height = 0.08f)
        val map = mapOf(100 to g1, 200 to in1)
        val tallClockBounds = ItemBounds(left = 0.10f, top = 0.05f, right = 0.90f, bottom = 1.15f)
        // maxY = 1.50 -> maxBottom = 1.4775, minY = 0.02, clockH = 1.10, inline + gap = 0.12
        // remaining for grid = 1.4775 - 0.02 - 1.10 - 0.04 - 0.12 = 0.1975 -> fits 1 grid row (0.171), NOT 2 rows (0.3735)!
        val comp = Composition(gridCols = 4, gridRows = 1, inlineRows = 1)
            .add(100, large = FacePlacement(0.5f, 1.19f), small = null)
            .add(200, large = FacePlacement(0.5f, 1.39f), small = null)

        val maxGrid = WidgetGrids.maxFittingGridRows(
            composition = comp,
            faceOf = { map[it] },
            maxY = 1.50f,
            clockBounds = tallClockBounds,
            minY = 0.02f
        )
        val maxInline = WidgetGrids.maxFittingInlineRows(
            composition = comp,
            faceOf = { map[it] },
            maxY = 1.50f,
            clockBounds = tallClockBounds,
            minY = 0.02f
        )

        assertEquals(1, maxGrid)
        assertEquals(1, maxInline)
    }

    @Test
    fun layoutWithReservedGridSizeRejectsRowExpansionWhenNoVerticalSpaceRemains() {
        val g1 = face("G1", category = "small", span = 2)
        val in1 = face("I1", category = "inline", height = 0.08f)
        val map = mapOf(100 to g1, 200 to in1)
        val clockBounds = ItemBounds(left = 0.10f, top = 0.02f, right = 0.90f, bottom = 1.10f)
        val comp = Composition(gridCols = 4, gridRows = 1, inlineRows = 1)
            .add(100, large = FacePlacement(0.5f, 1.14f), small = null)
            .add(200, large = FacePlacement(0.5f, 1.35f), small = null)

        val expanded = WidgetGrids.layoutWithReservedGridSize(
            composition = comp,
            cols = 4,
            rows = 2,
            itemState = ItemState.LARGE,
            faceOf = { map[it] },
            clockBounds = clockBounds,
            clockFace = null,
            minY = 0.02f,
            maxY = 1.50f
        )

        assertEquals("Expanding to 2 rows when only 1 fits must be rejected", null, expanded)
    }

    @Test
    fun canAddBlocksExtraRowWidgetsWhenClockAndSiblingLeaveNoSpace() {
        val g1 = face("G1", category = "small", span = 2)
        val g2 = face("G2", category = "small", span = 2)
        val g3 = face("G3", category = "small", span = 1)
        val in1 = face("I1", category = "inline", height = 0.08f)
        val map = mapOf(100 to g1, 101 to g2, 102 to g3, 200 to in1)
        val clockBounds = ItemBounds(left = 0.10f, top = 0.02f, right = 0.90f, bottom = 1.12f)
        // Even if gridRows is set to 2, clock + inline leave room for only 1 grid row
        val comp = Composition(gridCols = 4, gridRows = 2)
            .add(100, large = FacePlacement(0.3f, 1.16f), small = null)
            .add(101, large = FacePlacement(0.7f, 1.16f), small = null)
            .add(200, large = FacePlacement(0.5f, 1.36f), small = null)

        assertFalse(
            WidgetGrids.canAdd(
                composition = comp,
                face = g3,
                faceOf = { map[it] },
                maxY = 1.50f,
                clockBounds = clockBounds,
                minY = 0.02f
            )
        )
    }

    @Test
    fun layoutZoneReserveEmptyRowsKeepsInlineOutsideEmptyGridRowsBelowAndAboveClock() {
        val g1 = face("G1", category = "small", span = 2)
        val in1 = face("I1", category = "inline", height = 0.08f)
        val map = mapOf(100 to g1, 200 to in1)

        // Below clock: grid has 1 placed row but gridRows = 2
        val clockBelow = ItemBounds(left = 0.10f, top = 0.10f, right = 0.90f, bottom = 0.45f)
        val compBelow = Composition(gridCols = 4, gridRows = 2, widgetsAbove = false)
            .add(100, large = FacePlacement(0.5f, 0.49f), small = null)
            .add(200, large = FacePlacement(0.5f, 0.70f), small = null)
        val laidOutBelow = WidgetGrids.layoutWithReservedGridSize(
            composition = compBelow,
            cols = 4,
            rows = 2,
            itemState = ItemState.LARGE,
            faceOf = { map[it] },
            clockBounds = clockBelow,
            clockFace = null,
            minY = 0.02f,
            maxY = 1.65f
        )!!
        val gridBelowY = WidgetGrids.gridItems(laidOutBelow) { map[it] }.first().large!!.y
        val inlineBelowY = WidgetGrids.inlineItems(laidOutBelow) { map[it] }.first().large!!.y
        val twoRowGridHeight = FaceGrid.CELL * 2f + FaceGrid.GAP
        assertTrue(
            "Inline below clock must sit below BOTH row 1 and empty row 2 (gridY=$gridBelowY, inlineY=$inlineBelowY)",
            inlineBelowY >= gridBelowY + twoRowGridHeight
        )

        // Above clock: grid has 1 placed row but gridRows = 2 -> empty row sits above row 1, inline sits above empty row
        val clockAbove = ItemBounds(left = 0.10f, top = 0.75f, right = 0.90f, bottom = 1.15f)
        val compAbove = Composition(gridCols = 4, gridRows = 2, widgetsAbove = true)
            .add(100, large = FacePlacement(0.5f, 0.50f), small = null)
            .add(200, large = FacePlacement(0.5f, 0.35f), small = null)
        val laidOutAbove = WidgetGrids.layoutWithReservedGridSize(
            composition = compAbove,
            cols = 4,
            rows = 2,
            itemState = ItemState.LARGE,
            faceOf = { map[it] },
            clockBounds = clockAbove,
            clockFace = null,
            minY = 0.02f,
            maxY = 1.65f
        )!!
        val gridAboveY = WidgetGrids.gridItems(laidOutAbove) { map[it] }.first().large!!.y
        val inlineAboveY = WidgetGrids.inlineItems(laidOutAbove) { map[it] }.first().large!!.y
        val emptyTop = gridAboveY - (FaceGrid.CELL + FaceGrid.GAP)
        assertTrue(
            "Inline above clock must sit above the empty grid row above row 1 (inlineBottom=${inlineAboveY + 0.08f}, emptyTop=$emptyTop)",
            inlineAboveY + 0.08f <= emptyTop
        )
    }

    @Test
    fun trimEmptyWidgetRowsToFitReducesUnusedCapacityAfterClockEnlarges() {
        val g1 = face("G1", category = "small", span = 2)
        val map = mapOf(100 to g1)
        val bigClockBounds = ItemBounds(left = 0.10f, top = 0.05f, right = 0.90f, bottom = 1.25f)
        // Grid originally had 3 rows (only 1 used), then clock grew huge leaving room for only 1 row
        val comp = Composition(gridCols = 4, gridRows = 3)
            .add(100, large = FacePlacement(0.5f, 1.29f), small = null)

        val trimmed = WidgetGrids.trimEmptyWidgetRowsToFit(
            composition = comp,
            itemState = ItemState.LARGE,
            faceOf = { map[it] },
            clockBounds = bigClockBounds,
            minY = 0.02f,
            maxY = 1.55f
        )

        assertEquals(1, trimmed.gridRows)
        assertEquals(4, trimmed.gridCapacity)
    }

    private fun boundsOverlap(a: ItemBounds, b: ItemBounds): Boolean =
        a.left < b.right - 1e-4f && a.right > b.left + 1e-4f && a.top < b.bottom - 1e-4f && a.bottom > b.top + 1e-4f

    private fun shift(b: ItemBounds, delta: Pair<Float, Float>): ItemBounds =
        ItemBounds(b.left + delta.first, b.top + delta.second, b.right + delta.first, b.bottom + delta.second)
}
