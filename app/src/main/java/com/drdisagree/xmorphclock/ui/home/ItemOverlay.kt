package com.drdisagree.xmorphclock.ui.home

import android.graphics.RectF
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.domain.editor.FaceComposer
import com.drdisagree.xmorphclock.domain.editor.ItemSnapper
import com.drdisagree.xmorphclock.domain.editor.LockTarget
import com.drdisagree.xmorphclock.domain.editor.WidgetGrids
import com.drdisagree.xmorphclock.domain.model.Composition
import com.drdisagree.xmorphclock.domain.model.FaceGrid
import com.drdisagree.xmorphclock.domain.model.ItemBounds
import com.drdisagree.xmorphclock.domain.model.ItemState
import com.drdisagree.xmorphclock.domain.model.LockscreenAffordance
import com.drdisagree.xmorphclock.domain.model.ShortcutSlot
import com.drdisagree.xmorphclock.domain.model.SnapResult
import com.drdisagree.xmorphclock.ui.components.AppIcon
import com.drdisagree.xmorphclock.ui.components.LocalClockGeometry
import com.drdisagree.xmorphclock.ui.theme.Accent
import com.drdisagree.xmorphclock.ui.theme.OnInk
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun BoxScope.ItemOverlay(
    extents: Map<String, RectF>,
    composition: Composition,
    gridWidgetIds: Set<Int>,
    inlineWidgetIds: Set<Int>,
    activeTarget: LockTarget?,
    activeShortcutSlot: ShortcutSlot,
    shortcutLeft: LockscreenAffordance,
    shortcutRight: LockscreenAffordance,
    isAod: Boolean,
    unit: Float,
    frameLeft: Float,
    frameTop: Float,
    width: Float,
    height: Float,
    settled: Boolean = true,
    onSelectTarget: (LockTarget?, ShortcutSlot?) -> Unit,
    onDrag: (Map<LockTarget, Pair<Float, Float>>, Boolean, Boolean) -> Unit,
    onDragEnd: () -> Unit,
    onToggleWidgetsPosition: () -> Unit,
    onMeasured: (Map<Int, ItemBounds>, Float, Map<ItemState, Float>) -> Unit
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val pad = with(density) { BOX_PAD.toPx() }
    val slotGap = with(density) { SLOT_GAP.toPx() }
    val edgePadPx = with(density) { FRAME_EDGE_PAD.toPx() }
    val gridSlotH = with(density) { GRID_PLACEHOLDER_HEIGHT.toPx() }
    val inlineSlotH = with(density) { INLINE_PLACEHOLDER_HEIGHT.toPx() }
    val minTouch = with(density) { MIN_TOUCH_SIZE.toPx() }
    val minShortcutPx = with(density) { SHORTCUT_MIN_SIZE.toPx() }
    val maxShortcutPx = with(density) { SHORTCUT_MAX_SIZE.toPx() }

    val shortcutSize = (width * SHORTCUT_WIDTH_RATIO).coerceIn(minShortcutPx, maxShortcutPx)
    val shortcutBottom = (height * SHORTCUT_BOTTOM_RATIO).coerceAtLeast(edgePadPx)
    val shortcutSide = (width * SHORTCUT_SIDE_RATIO).coerceAtLeast(edgePadPx)
    val leftShortcutRect = remember(width, height, shortcutSize, shortcutBottom, shortcutSide) {
        if (width <= 0f || height <= 0f) null
        else RectF(
            shortcutSide,
            height - shortcutBottom - shortcutSize,
            shortcutSide + shortcutSize,
            height - shortcutBottom
        )
    }
    val rightShortcutRect = remember(width, height, shortcutSize, shortcutBottom, shortcutSide) {
        if (width <= 0f || height <= 0f) null
        else RectF(
            width - shortcutSide - shortcutSize,
            height - shortcutBottom - shortcutSize,
            width - shortcutSide,
            height - shortcutBottom
        )
    }

    val clockLayers = extents.filterKeys { FaceComposer.isClockLayer(it) }
    val gridLayers = extents.filterKeys { FaceComposer.itemOf(it) in gridWidgetIds }
    val inlineLayers = extents.filterKeys { FaceComposer.itemOf(it) in inlineWidgetIds }

    val itemBounds = remember(extents, composition, unit, frameLeft, frameTop) {
        if (unit <= 0f) emptyMap()
        else composition.items.mapNotNull { item ->
            val layers = extents.filterKeys { id ->
                if (item.clock) FaceComposer.isClockLayer(id) else FaceComposer.itemOf(id) == item.id
            }
            layers.values.union()?.toBounds(frameLeft, frameTop, unit)?.let { item.id to it }
        }.toMap()
    }
    val shortcutRowTop = minOf(
        leftShortcutRect?.top ?: height,
        rightShortcutRect?.top ?: height
    )
    val safeBottomPx = (shortcutRowTop - slotGap).coerceAtLeast(edgePadPx)
    val maxY = if (unit > 0f) (safeBottomPx - frameTop) / unit else 0f
    val geometry = LocalClockGeometry.current
    val maxYByState = remember(geometry, width, height, safeBottomPx) {
        if (width <= 0f || height <= 0f) {
            emptyMap()
        } else {
            val largeFrame = geometry.frame(1f)
            val smallFrame = geometry.frame(0f)
            val largeUnit = width * largeFrame.width
            val smallUnit = width * smallFrame.width
            val largeTop = height * largeFrame.top
            val smallTop = height * smallFrame.top
            buildMap {
                if (largeUnit > 0f) {
                    val largeMax = (safeBottomPx - largeTop) / largeUnit
                    put(ItemState.LARGE, largeMax)
                    put(ItemState.AOD_LARGE, largeMax)
                }
                if (smallUnit > 0f) {
                    val smallMax = (safeBottomPx - smallTop) / smallUnit
                    put(ItemState.SMALL, smallMax)
                    put(ItemState.AOD_SMALL, smallMax)
                }
            }
        }
    }
    LaunchedEffect(settled, itemBounds, maxY, maxYByState) {
        if (settled && itemBounds.isNotEmpty()) {
            onMeasured(itemBounds, maxY, maxYByState)
        }
    }

    val clockRaw = clockLayers.values.union()
    val gridRaw = gridLayers.values.union()
    val inlineRaw = inlineLayers.values.union()

    val clockRect = clockRaw?.padded(pad)
    val gridRect = gridRaw?.padded(pad)
    val inlineRect = inlineRaw?.padded(pad)

    val placeholders = remember(
        clockRect,
        gridRect,
        inlineRect,
        composition.widgetsAbove,
        width,
        height,
        unit,
        frameLeft,
        edgePadPx,
        slotGap,
        safeBottomPx,
        gridSlotH,
        inlineSlotH,
        minTouch,
        isAod
    ) {
        WidgetGrids.calculatePlaceholders(
            clockRect = clockRect,
            gridRect = gridRect,
            inlineRect = inlineRect,
            widgetsAbove = composition.widgetsAbove,
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
    }
    val gridPlaceholder = placeholders.gridPlaceholder
    val inlinePlaceholder = placeholders.inlinePlaceholder

    val interactiveRects = buildMap {
        clockRect?.let { put(LockTarget.CLOCK, it) }
        (gridRect ?: gridPlaceholder)?.let { put(LockTarget.WIDGET_GRID, it) }
        (inlineRect ?: inlinePlaceholder)?.let { put(LockTarget.INLINE_GRID, it) }
    }
    val draggableRawRects = buildMap {
        clockRaw?.let { put(LockTarget.CLOCK, it) }
        gridRaw?.let { put(LockTarget.WIDGET_GRID, it) }
        inlineRaw?.let { put(LockTarget.INLINE_GRID, it) }
    }
    val shortcutObstacleBounds = remember(leftShortcutRect, rightShortcutRect, pad, frameLeft, frameTop, unit) {
        if (unit <= 0f) emptyList()
        else listOfNotNull(leftShortcutRect, rightShortcutRect).map { rect ->
            rect.padded(pad).toBounds(frameLeft, frameTop, unit)
        }
    }

    var guides by remember { mutableStateOf(SnapResult()) }
    var activeDragging by remember { mutableStateOf(false) }
    var liveGridDocked by remember(composition.gridDocked) { mutableStateOf(composition.gridDocked) }
    var liveInlineDocked by remember(composition.inlineDocked) { mutableStateOf(composition.inlineDocked) }

    val currentComposition by rememberUpdatedState(composition)
    val currentTargets by rememberUpdatedState(interactiveRects)
    val currentDraggables by rememberUpdatedState(draggableRawRects)
    val currentLeftShortcut by rememberUpdatedState(leftShortcutRect)
    val currentRightShortcut by rememberUpdatedState(rightShortcutRect)
    val currentShortcutObstacles by rememberUpdatedState(shortcutObstacleBounds)
    val currentIsAod by rememberUpdatedState(isAod)
    val currentActive by rememberUpdatedState(activeTarget)
    val currentUnit by rememberUpdatedState(unit)
    val currentFrameLeft by rememberUpdatedState(frameLeft)
    val currentFrameTop by rememberUpdatedState(frameTop)
    val currentHeight by rememberUpdatedState(height)
    val currentSelect by rememberUpdatedState(onSelectTarget)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    val currentToggleWidgetsPosition by rememberUpdatedState(onToggleWidgetsPosition)

    fun hitAt(position: Offset): Pair<LockTarget, ShortcutSlot?>? {
        if (!currentIsAod) {
            if (currentLeftShortcut?.containsWithSlop(position, pad * 1.5f) == true) {
                return LockTarget.SHORTCUTS to ShortcutSlot.LEFT
            }
            if (currentRightShortcut?.containsWithSlop(position, pad * 1.5f) == true) {
                return LockTarget.SHORTCUTS to ShortcutSlot.RIGHT
            }
        }
        return currentTargets.entries
            .filter { (_, rect) -> rect.containsWithSlop(position, pad) }
            .minByOrNull { (_, rect) -> rect.width() * rect.height() }
            ?.key
            ?.let { it to null }
    }

    Box(
        Modifier
            .matchParentSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val hitPair = hitAt(down.position)
                    val hit = hitPair?.first
                    val hitSlot = hitPair?.second
                    val unitAtDown = currentUnit
                    val frameLeftAtDown = currentFrameLeft
                    val frameTopAtDown = currentFrameTop
                    val rawStart = hit?.let { currentDraggables[it] }?.let(::RectF)
                    val startBoundsByGroup = if (hit != null && hit != LockTarget.SHORTCUTS && unitAtDown > 0f) {
                        currentDraggables.mapValues { (_, rect) ->
                            rect.toBounds(frameLeftAtDown, frameTopAtDown, unitAtDown)
                        }
                    } else emptyMap()
                    val startBounds = startBoundsByGroup[hit]

                    liveGridDocked = currentComposition.gridDocked
                    liveInlineDocked = currentComposition.inlineDocked

                    var totalPx = Offset.Zero
                    var appliedDeltas = startBoundsByGroup.keys.associateWith { 0f to 0f }
                    var movedBeyondSlop = false
                    var dragging = false
                    val slop = viewConfiguration.touchSlop

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        val change = pressed.first()
                        totalPx += change.positionChange()
                        if (!movedBeyondSlop && totalPx.getDistance() > slop) {
                            movedBeyondSlop = true
                            if (hit != null && hit != LockTarget.SHORTCUTS && startBounds != null) {
                                dragging = true
                                activeDragging = true
                                if (currentActive != hit) currentSelect(hit, null)
                            }
                        }
                        if (!dragging || hit == null || rawStart == null || startBounds == null) continue
                        val u = currentUnit.takeIf { it > 0f } ?: continue
                        change.consume()

                        val fLeft = currentFrameLeft
                        val fTop = currentFrameTop
                        val movingBounds = rawStart.toBounds(fLeft - totalPx.x, fTop - totalPx.y, u)
                        val liveOtherBounds = startBoundsByGroup.entries
                            .filter { it.key != hit }
                            .map { (key, bounds) ->
                                val (dx, dy) = appliedDeltas[key] ?: (0f to 0f)
                                ItemBounds(bounds.left + dx, bounds.top + dy, bounds.right + dx, bounds.bottom + dy)
                            }
                        val screenCenterY = (currentHeight / 2f - fTop) / u
                        val snapResult = ItemSnapper.snap(movingBounds, liveOtherBounds, screenCenterY, snapYToOthers = false)

                        val newX = snapResult.guideX != null && snapResult.guideX != guides.guideX
                        val newY = snapResult.guideY != null && snapResult.guideY != guides.guideY
                        if (newX || newY) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        guides = snapResult

                        val rawDx = totalPx.x / u + snapResult.dx
                        val rawDy = totalPx.y / u + snapResult.dy
                        val minY = (edgePadPx - fTop) / u
                        val maxYBound = (currentHeight - edgePadPx - fTop) / u
                        val stickyResult = WidgetGrids.solveStickyDrag(
                            activeTarget = hit,
                            startBounds = startBoundsByGroup,
                            rawDx = rawDx,
                            rawDy = rawDy,
                            lastValidDeltas = appliedDeltas,
                            gridDocked = liveGridDocked,
                            inlineDocked = liveInlineDocked,
                            minX = HORIZONTAL_EDGE,
                            maxX = 1f - HORIZONTAL_EDGE,
                            minY = minY,
                            maxY = maxYBound,
                            fixedObstacles = currentShortcutObstacles
                        )
                        if (stickyResult.attachedEvent) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        val prevGridDocked = liveGridDocked
                        val prevInlineDocked = liveInlineDocked
                        liveGridDocked = stickyResult.gridDocked
                        liveInlineDocked = stickyResult.inlineDocked

                        val steps = buildMap {
                            stickyResult.deltas.forEach { (key, next) ->
                                val prev = appliedDeltas[key] ?: (0f to 0f)
                                val stepX = next.first - prev.first
                                val stepY = next.second - prev.second
                                if (abs(stepX) > 1e-6f || abs(stepY) > 1e-6f) {
                                    put(key, stepX to stepY)
                                }
                            }
                        }
                        val dockedChanged = liveGridDocked != prevGridDocked || liveInlineDocked != prevInlineDocked
                        appliedDeltas = stickyResult.deltas
                        if (steps.isNotEmpty() || dockedChanged) {
                            currentDrag(steps, liveGridDocked, liveInlineDocked)
                        }
                    }

                    guides = SnapResult()
                    if (dragging) {
                        activeDragging = false
                        currentDragEnd()
                    } else if (!movedBeyondSlop) {
                        currentSelect(hit, hitSlot)
                    }
                }
            }
    )

    gridPlaceholder?.let { rect ->
        EmptySlotPill(
            rect = rect,
            label = stringResource(R.string.slot_add_widgets),
            selected = activeTarget == LockTarget.WIDGET_GRID,
            tag = "lock_slot_widgets"
        )
    }

    inlinePlaceholder?.let { rect ->
        EmptySlotPill(
            rect = rect,
            label = stringResource(R.string.slot_add_inline),
            selected = activeTarget == LockTarget.INLINE_GRID,
            tag = "lock_slot_inline"
        )
    }

    if (!isAod) {
        leftShortcutRect?.let { rect ->
            LockscreenShortcutOrb(
                rect = rect,
                shortcut = shortcutLeft,
                slotLabel = stringResource(R.string.shortcut_slot_left),
                selected = activeTarget == LockTarget.SHORTCUTS && activeShortcutSlot == ShortcutSlot.LEFT,
                tag = "lock_shortcut_left"
            )
        }
        rightShortcutRect?.let { rect ->
            LockscreenShortcutOrb(
                rect = rect,
                shortcut = shortcutRight,
                slotLabel = stringResource(R.string.shortcut_slot_right),
                selected = activeTarget == LockTarget.SHORTCUTS && activeShortcutSlot == ShortcutSlot.RIGHT,
                tag = "lock_shortcut_right"
            )
        }
    }

    clockRect?.let { rect ->
        SelectionZoneFrame(
            rect = rect,
            selected = activeTarget == LockTarget.CLOCK,
            dragging = activeDragging && activeTarget == LockTarget.CLOCK,
            columns = 1,
            animate = !activeDragging
        )
    }

    gridRect?.let { rect ->
        SelectionZoneFrame(
            rect = rect,
            selected = activeTarget == LockTarget.WIDGET_GRID,
            dragging = activeDragging && activeTarget == LockTarget.WIDGET_GRID,
            columns = FaceGrid.ROW_CELLS,
            animate = !activeDragging
        )
    }

    inlineRect?.let { rect ->
        SelectionZoneFrame(
            rect = rect,
            selected = activeTarget == LockTarget.INLINE_GRID,
            dragging = activeDragging && activeTarget == LockTarget.INLINE_GRID,
            columns = WidgetGrids.MAX_INLINE_ITEMS,
            animate = !activeDragging
        )
    }

    val bottomMostWidgetRect = remember(gridRect, gridPlaceholder, inlineRect, inlinePlaceholder) {
        listOfNotNull(gridRect ?: gridPlaceholder, inlineRect ?: inlinePlaceholder)
            .maxByOrNull { it.bottom }
    }
    val topMostWidgetRect = remember(gridRect, gridPlaceholder, inlineRect, inlinePlaceholder) {
        listOfNotNull(gridRect ?: gridPlaceholder, inlineRect ?: inlinePlaceholder)
            .minByOrNull { it.top }
    }

    val widgetSelected = activeTarget == LockTarget.WIDGET_GRID || activeTarget == LockTarget.INLINE_GRID
    if (widgetSelected && bottomMostWidgetRect != null && topMostWidgetRect != null && !activeDragging && unit > 0f) {
        val buttonSize = 34.dp
        val buttonPx = with(density) { buttonSize.toPx() }
        val btnRadius = buttonPx / 2f
        val btnGap = with(density) { 8.dp.toPx() }
        val activeWidgetRect = when (activeTarget) {
            LockTarget.WIDGET_GRID -> gridRect ?: gridPlaceholder ?: bottomMostWidgetRect
            LockTarget.INLINE_GRID -> inlineRect ?: inlinePlaceholder ?: bottomMostWidgetRect
            else -> bottomMostWidgetRect
        }
        val cx = ((activeWidgetRect.left + activeWidgetRect.right) / 2f)
            .coerceIn(edgePadPx + btnRadius, (width - edgePadPx - btnRadius).coerceAtLeast(edgePadPx + btnRadius))
        val minBtnY = edgePadPx + btnRadius
        val maxBtnY = (safeBottomPx - btnRadius - with(density) { 4.dp.toPx() }).coerceAtLeast(minBtnY)

        val toggleObstacles = listOfNotNull(clockRect, gridRect, inlineRect, gridPlaceholder, inlinePlaceholder)
        val candidateYs = buildList {
            if (composition.widgetsAbove) {
                if (clockRect != null && clockRect.top > bottomMostWidgetRect.bottom) {
                    add((bottomMostWidgetRect.bottom + clockRect.top) / 2f)
                }
                add(topMostWidgetRect.top - btnGap - btnRadius)
                add(bottomMostWidgetRect.bottom + btnGap + btnRadius)
            } else {
                add(bottomMostWidgetRect.bottom + btnGap + btnRadius)
                add(activeWidgetRect.bottom + btnGap + btnRadius)
                add(topMostWidgetRect.top - btnGap - btnRadius)
                if (clockRect != null && topMostWidgetRect.top > clockRect.bottom) {
                    add((clockRect.bottom + topMostWidgetRect.top) / 2f)
                }
                add(activeWidgetRect.top - btnGap - btnRadius)
            }
        }
        val validCy = candidateYs.firstOrNull { cy ->
            cy in minBtnY..maxBtnY && toggleObstacles.none { obs ->
                cx + btnRadius > obs.left + 1f &&
                    cx - btnRadius < obs.right - 1f &&
                    cy + btnRadius > obs.top + 1f &&
                    cy - btnRadius < obs.bottom - 1f
            }
        }
        val fallbackCy = if (composition.widgetsAbove && clockRect != null && clockRect.top > bottomMostWidgetRect.bottom) {
            (bottomMostWidgetRect.bottom + clockRect.top) / 2f
        } else {
            bottomMostWidgetRect.bottom + btnGap + btnRadius
        }.coerceIn(minBtnY, maxBtnY)

        GridPositionToggleButton(
            widgetsAbove = composition.widgetsAbove,
            centerX = cx,
            centerY = validCy ?: fallbackCy,
            onToggle = currentToggleWidgetsPosition
        )
    }

    if (activeDragging && unit > 0f && (guides.guideX != null || guides.guideY != null)) {
        val screenCenterY = (height / 2f - frameTop) / unit
        SmartCenterGuides(
            guides = guides,
            frameLeft = frameLeft,
            frameTop = frameTop,
            unit = unit,
            screenCenterY = screenCenterY
        )
    }
}

@Composable
private fun LockscreenShortcutOrb(
    rect: RectF,
    shortcut: LockscreenAffordance,
    slotLabel: String,
    selected: Boolean,
    tag: String
) {
    val density = LocalDensity.current
    val isEmpty = shortcut.isNone
    val shortcutName = affordanceLabel(shortcut)
    val fillColor by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            isEmpty -> Color.Black.copy(alpha = 0.28f)
            else -> Color(0xFF1F2227).copy(alpha = 0.78f)
        },
        tween(200),
        label = "shortcutOrbFill"
    )
    val borderColor by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.primary
            isEmpty -> OnInk.copy(alpha = 0.36f)
            else -> OnInk.copy(alpha = 0.18f)
        },
        tween(200),
        label = "shortcutOrbBorder"
    )
    val iconTint by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            isEmpty -> OnInk.copy(alpha = 0.65f)
            else -> OnInk
        },
        tween(200),
        label = "shortcutOrbTint"
    )
    val iconRes = if (isEmpty) R.drawable.ic_plus else shortcut.iconRes
    val iconSizeDp = with(density) { (rect.width() * 0.48f).toDp() }

    Box(
        modifier = Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width().toDp() }, with(density) { rect.height().toDp() })
            .alpha(if (isEmpty && !selected) 0.72f else 1f)
            .clip(CircleShape)
            .background(fillColor)
            .border(if (selected) 2.5.dp else 1.2.dp, borderColor, CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = "$slotLabel: $shortcutName"
            }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(icon = iconRes, tint = iconTint, size = iconSizeDp)
    }
}

@Composable
private fun EmptySlotPill(
    rect: RectF,
    label: String,
    selected: Boolean,
    tag: String
) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(18.dp)
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else OnInk.copy(alpha = 0.42f),
        tween(200),
        label = "slotBorder"
    )
    val fillColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.28f),
        tween(200),
        label = "slotFill"
    )
    val contentColor = if (selected) OnInk else OnInk.copy(alpha = 0.88f)
    Box(
        modifier = Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width().toDp() }, with(density) { rect.height().toDp() })
            .defaultMinSize(minHeight = MIN_TOUCH_SIZE)
            .clip(shape)
            .background(fillColor)
            .border(if (selected) 2.dp else 1.2.dp, borderColor, shape)
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppIcon(R.drawable.ic_plus, tint = contentColor, size = 16.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SelectionZoneFrame(
    rect: RectF,
    selected: Boolean,
    dragging: Boolean,
    columns: Int,
    animate: Boolean
) {
    val density = LocalDensity.current
    val onInk = OnInk
    val spec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val animX by animateFloatAsState(rect.left, spec, label = "zoneLeft")
    val animY by animateFloatAsState(rect.top, spec, label = "zoneTop")
    val animW by animateFloatAsState(rect.width(), spec, label = "zoneWidth")
    val animH by animateFloatAsState(rect.height(), spec, label = "zoneHeight")
    val x = if (animate) animX else rect.left
    val y = if (animate) animY else rect.top
    val boxW = if (animate) animW else rect.width()
    val boxH = if (animate) animH else rect.height()

    val frameColor by animateColorAsState(
        when {
            dragging -> MaterialTheme.colorScheme.primary
            selected -> onInk.copy(alpha = 0.96f)
            else -> onInk.copy(alpha = 0.2f)
        },
        tween(180),
        label = "zoneColor"
    )
    val fillAlpha by animateFloatAsState(if (selected) 0.06f else 0f, tween(180), label = "zoneFill")

    Canvas(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(with(density) { boxW.toDp() }, with(density) { boxH.toDp() })
    ) {
        val corner = minOf(18.dp.toPx(), minOf(size.width, size.height) * 0.28f)
        if (fillAlpha > 0f) {
            drawRoundRect(
                color = onInk.copy(alpha = fillAlpha),
                cornerRadius = CornerRadius(corner)
            )
        }
        if (!selected) {
            drawRoundRect(
                color = frameColor,
                cornerRadius = CornerRadius(corner),
                style = Stroke(1.dp.toPx())
            )
            return@Canvas
        }

        drawRoundRect(
            color = frameColor.copy(alpha = 0.28f),
            cornerRadius = CornerRadius(corner),
            style = Stroke(1.dp.toPx())
        )

        if (columns > 1) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            for (c in 1 until columns) {
                val gx = size.width * c / columns
                drawLine(
                    color = frameColor.copy(alpha = 0.32f),
                    start = Offset(gx, 4.dp.toPx()),
                    end = Offset(gx, size.height - 4.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash
                )
            }
        }

        val stroke = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val arm = minOf(14.dp.toPx(), (minOf(size.width, size.height) - corner * 2f).coerceAtLeast(4.dp.toPx()) * 0.45f)
        val c2 = corner * 2f

        val tl = Path().apply {
            moveTo(0f, corner + arm)
            lineTo(0f, corner)
            arcTo(Rect(0f, 0f, c2, c2), 180f, 90f, false)
            lineTo(corner + arm, 0f)
        }
        val tr = Path().apply {
            moveTo(size.width - corner - arm, 0f)
            lineTo(size.width - corner, 0f)
            arcTo(Rect(size.width - c2, 0f, size.width, c2), -90f, 90f, false)
            lineTo(size.width, corner + arm)
        }
        val br = Path().apply {
            moveTo(size.width, size.height - corner - arm)
            lineTo(size.width, size.height - corner)
            arcTo(Rect(size.width - c2, size.height - c2, size.width, size.height), 0f, 90f, false)
            lineTo(size.width - corner - arm, size.height)
        }
        val bl = Path().apply {
            moveTo(corner + arm, size.height)
            lineTo(corner, size.height)
            arcTo(Rect(0f, size.height - c2, c2, size.height), 90f, 90f, false)
            lineTo(0f, size.height - corner - arm)
        }
        drawPath(tl, frameColor, style = stroke)
        drawPath(tr, frameColor, style = stroke)
        drawPath(br, frameColor, style = stroke)
        drawPath(bl, frameColor, style = stroke)
    }
}

@Composable
private fun SmartCenterGuides(
    guides: SnapResult,
    frameLeft: Float,
    frameTop: Float,
    unit: Float,
    screenCenterY: Float
) {
    val accent = Accent
    Canvas(Modifier.fillMaxSize()) {
        guides.guideX?.let { x ->
            val px = frameLeft + x * unit
            val isCenter = abs(x - 0.5f) < 0.005f
            drawLine(
                color = if (isCenter) accent else accent.copy(alpha = 0.78f),
                start = Offset(px, 0f),
                end = Offset(px, size.height),
                strokeWidth = if (isCenter) 1.75.dp.toPx() else 1.dp.toPx(),
                pathEffect = if (isCenter) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            )
            if (isCenter) {
                drawCircle(color = accent, radius = 3.5.dp.toPx(), center = Offset(px, size.height / 2f))
            }
        }
        guides.guideY?.let { y ->
            val py = frameTop + y * unit
            val isCenter = abs(y - screenCenterY) < 0.005f
            drawLine(
                color = if (isCenter) accent else accent.copy(alpha = 0.78f),
                start = Offset(0f, py),
                end = Offset(size.width, py),
                strokeWidth = if (isCenter) 1.75.dp.toPx() else 1.dp.toPx(),
                pathEffect = if (isCenter) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            )
            if (isCenter) {
                drawCircle(color = accent, radius = 3.5.dp.toPx(), center = Offset(size.width / 2f, py))
            }
        }
    }
}

private fun Collection<RectF>.union(): RectF? {
    if (isEmpty()) return null
    return RectF(minOf { it.left }, minOf { it.top }, maxOf { it.right }, maxOf { it.bottom })
}

private fun RectF.padded(pad: Float): RectF =
    RectF(left - pad, top - pad, right + pad, bottom + pad)

private fun RectF.containsWithSlop(position: Offset, slop: Float): Boolean =
    position.x >= left - slop && position.x <= right + slop && position.y >= top - slop && position.y <= bottom + slop

private fun RectF.toBounds(originX: Float, originY: Float, unit: Float): ItemBounds =
    ItemBounds((left - originX) / unit, (top - originY) / unit, (right - originX) / unit, (bottom - originY) / unit)

private val BOX_PAD = 6.dp
private val SLOT_GAP = 12.dp
private val FRAME_EDGE_PAD = 10.dp
private val GRID_PLACEHOLDER_HEIGHT = 48.dp
private val INLINE_PLACEHOLDER_HEIGHT = 48.dp
private val MIN_TOUCH_SIZE = 48.dp
private val SHORTCUT_MIN_SIZE = 30.dp
private val SHORTCUT_MAX_SIZE = 52.dp
private const val SHORTCUT_WIDTH_RATIO = 0.135f
private const val SHORTCUT_BOTTOM_RATIO = 0.036f
private const val SHORTCUT_SIDE_RATIO = 0.068f
private const val INLINE_PLACEHOLDER_RATIO = 0.72f
private const val HORIZONTAL_EDGE = 0.0225f

@Composable
private fun GridPositionToggleButton(
    widgetsAbove: Boolean,
    centerX: Float,
    centerY: Float,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val buttonSize = 34.dp
    val buttonPx = with(density) { buttonSize.toPx() }
    val leftPx = (centerX - buttonPx / 2f).roundToInt()
    val topPx = (centerY - buttonPx / 2f).roundToInt()
    val iconRes = if (widgetsAbove) R.drawable.ic_arrow_down else R.drawable.ic_arrow_up
    val desc = stringResource(
        if (widgetsAbove) R.string.action_move_widgets_below else R.string.action_move_widgets_above
    )

    Box(
        modifier = modifier
            .offset { IntOffset(leftPx, topPx) }
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
            shadowElevation = 3.dp,
            tonalElevation = 2.dp,
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    role = Role.Button,
                    onClick = onToggle
                )
                .semantics {
                    contentDescription = desc
                    role = Role.Button
                }
                .testTag("grid_position_toggle_button")
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
