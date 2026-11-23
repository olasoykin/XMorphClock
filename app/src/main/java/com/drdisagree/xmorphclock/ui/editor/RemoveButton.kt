package com.drdisagree.xmorphclock.ui.editor

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.ui.components.AppIcon
import com.drdisagree.xmorphclock.ui.components.expressivePillShape
import kotlin.math.roundToInt

@Composable
internal fun RemoveButton(x: Float, y: Float, width: Float, visible: Boolean, onClick: () -> Unit) {
    val density = LocalDensity.current
    val size = with(density) { REMOVE_SIZE.toPx() }
    val margin = with(density) { REMOVE_MARGIN.toPx() }
    val spec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val left = (x - size * REMOVE_INSET).coerceAtMost(width - size - margin).coerceAtLeast(margin)
    val top = (y - size * REMOVE_OUTSET).coerceAtLeast(margin)
    val shown by animateFloatAsState(if (visible) 1f else 0f, spec, label = "removeShown")
    if (shown <= 0f) return
    val interaction = remember { MutableInteractionSource() }
    val shape = expressivePillShape(interaction, restCorner = 14.dp, pressedCorner = 6.dp)
    val label = stringResource(R.string.action_remove)
    Surface(
        onClick = onClick,
        enabled = visible,
        shape = shape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shadowElevation = 2.dp,
        interactionSource = interaction,
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(REMOVE_SIZE)
            .graphicsLayer {
                alpha = shown
                scaleX = REMOVE_START + (1f - REMOVE_START) * shown
                scaleY = REMOVE_START + (1f - REMOVE_START) * shown
            }
            .semantics {
                role = Role.Button
                contentDescription = label
            }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AppIcon(R.drawable.ic_minus, tint = MaterialTheme.colorScheme.onErrorContainer, size = REMOVE_ICON)
        }
    }
}

private val REMOVE_SIZE = 26.dp
private val REMOVE_ICON = 14.dp
private val REMOVE_MARGIN = 6.dp
private const val REMOVE_INSET = 0.8f
private const val REMOVE_OUTSET = 0.2f
private const val REMOVE_START = 0.6f
