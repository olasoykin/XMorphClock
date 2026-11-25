package com.drdisagree.xmorphclock.domain.editor

import com.drdisagree.xmorphclock.domain.model.ClockFace
import com.drdisagree.xmorphclock.domain.model.ColumnNode
import com.drdisagree.xmorphclock.domain.model.Composition
import com.drdisagree.xmorphclock.domain.model.FaceAlign
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
        assertTrue(WidgetGrids.canAdd(comp, w3) { map[it] })
        assertFalse(WidgetGrids.canAdd(comp, w2) { map[it] })

        comp = comp.add(102, null, null)
        assertEquals(4, WidgetGrids.usedGridCells(comp) { map[it] })
        assertFalse(WidgetGrids.canAdd(comp, wExtra) { map[it] })
    }

    @Test
    fun inline1x2OnlyAcceptsInlineAndMaxTwoItems() {
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
        assertTrue(WidgetGrids.canAdd(comp, inlineB) { map[it] })

        comp = comp.add(201, null, null)
        assertEquals(2, WidgetGrids.inlineItems(comp) { map[it] }.size)
        assertFalse(WidgetGrids.canAdd(comp, inlineC) { map[it] })
        // 1x4 grid still has 2 free cells
        assertTrue(WidgetGrids.canAdd(comp, face("Small", span = 2)) { map[it] })
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
        val inlinePlacements = WidgetGrids.inlineItems(comp) { map[it] }.map { it.large!! }
        assertTrue(gridPlacement.y > clockBottom)
        assertTrue(inlinePlacements[0].y > gridPlacement.y)
        assertEquals(inlinePlacements[0].y, inlinePlacements[1].y, 1e-4f)
        assertEquals(FaceAlign.END, inlinePlacements[0].anchor)
        assertEquals(FaceAlign.START, inlinePlacements[1].anchor)
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

    private fun boundsOverlap(a: ItemBounds, b: ItemBounds): Boolean =
        a.left < b.right - 1e-4f && a.right > b.left + 1e-4f && a.top < b.bottom - 1e-4f && a.bottom > b.top + 1e-4f

    private fun shift(b: ItemBounds, delta: Pair<Float, Float>): ItemBounds =
        ItemBounds(b.left + delta.first, b.top + delta.second, b.right + delta.first, b.bottom + delta.second)
}
