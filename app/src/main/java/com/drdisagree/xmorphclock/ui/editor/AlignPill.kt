package com.drdisagree.xmorphclock.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.domain.model.FaceAlign
import com.drdisagree.xmorphclock.ui.components.AppIcon
import com.drdisagree.xmorphclock.ui.components.expressivePillShape
import kotlin.math.roundToInt

@Composable
internal fun AlignPill(centerX: Float, top: Float, width: Float, height: Float, current: FaceAlign?, visible: Boolean, onAlign: (FaceAlign) -> Unit) {
    val density = LocalDensity.current
    val pillWidth = with(density) { (ALIGN_BUTTON * FaceAlign.entries.size + ALIGN_PAD * 2).toPx() }
    val pillHeight = with(density) { (ALIGN_BUTTON + ALIGN_PAD * 2).toPx() }
    val gap = with(density) { ALIGN_GAP.toPx() }
    val margin = with(density) { ALIGN_MARGIN.toPx() }
    val x = (centerX - pillWidth / 2f).coerceAtMost(width - pillWidth - margin).coerceAtLeast(margin)
    val y = (top + gap).coerceAtMost(height - pillHeight - margin).coerceAtLeast(margin)
    val spec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val shown by animateFloatAsState(if (visible) 1f else 0f, spec, label = "alignShown")
    if (shown <= 0f) return
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .graphicsLayer {
                alpha = shown
                scaleX = ALIGN_START + (1f - ALIGN_START) * shown
                scaleY = ALIGN_START + (1f - ALIGN_START) * shown
            }
    ) {
        Row(Modifier.padding(ALIGN_PAD)) {
            FaceAlign.entries.forEach { align ->
                val isSelected = current == align
                val tint by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "alignTint"
                )
                val fill by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    label = "alignFill"
                )
                val label = stringResource(alignLabel(align))
                val interaction = remember { MutableInteractionSource() }
                val itemShape = expressivePillShape(interaction, restCorner = ALIGN_BUTTON / 2, pressedCorner = 8.dp)
                Surface(
                    onClick = { onAlign(align) },
                    enabled = visible,
                    shape = itemShape,
                    color = fill,
                    contentColor = tint,
                    interactionSource = interaction,
                    modifier = Modifier
                        .size(ALIGN_BUTTON)
                        .semantics {
                            role = Role.RadioButton
                            selected = isSelected
                            contentDescription = label
                        }
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AppIcon(alignIcon(align), tint = tint, size = ALIGN_ICON)
                    }
                }
            }
        }
    }
}

private fun alignIcon(align: FaceAlign): Int = when (align) {
    FaceAlign.START -> R.drawable.ic_align_start
    FaceAlign.CENTER -> R.drawable.ic_align_center
    FaceAlign.END -> R.drawable.ic_align_end
}

private fun alignLabel(align: FaceAlign): Int = when (align) {
    FaceAlign.START -> R.string.align_start
    FaceAlign.CENTER -> R.string.align_center
    FaceAlign.END -> R.string.align_end
}

private val ALIGN_BUTTON = 28.dp
private val ALIGN_ICON = 14.dp
private val ALIGN_PAD = 3.dp
private val ALIGN_GAP = 10.dp
private val ALIGN_MARGIN = 6.dp
private const val ALIGN_START = 0.6f
