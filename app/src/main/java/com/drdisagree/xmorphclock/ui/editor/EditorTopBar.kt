package com.drdisagree.xmorphclock.ui.editor

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.ui.components.AppIcon
import com.drdisagree.xmorphclock.ui.components.ExpressiveIconButton
import com.drdisagree.xmorphclock.ui.components.expressivePillShape

@Composable
fun EditorTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    fullscreen: Boolean,
    guides: Boolean,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleGuides: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveIconButton(
                icon = R.drawable.ic_cross,
                onClick = onCancel,
                description = stringResource(R.string.action_cancel)
            )
            Crossfade(guides, animationSpec = tween(220), label = "guides") { shown ->
                ExpressiveIconButton(
                    icon = if (shown) R.drawable.ic_guides else R.drawable.ic_guides_off,
                    onClick = onToggleGuides,
                    description = stringResource(if (shown) R.string.action_hide_guides else R.string.action_show_guides)
                )
            }
        }
        HistoryPill(canUndo, canRedo, onUndo, onRedo, Modifier.align(Alignment.Center))
        Row(
            Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Crossfade(fullscreen, animationSpec = tween(220), label = "fullscreen") { full ->
                ExpressiveIconButton(
                    icon = if (full) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen,
                    onClick = onToggleFullscreen,
                    description = stringResource(if (full) R.string.action_exit_fullscreen else R.string.action_fullscreen)
                )
            }
            ExpressiveIconButton(
                icon = R.drawable.ic_check,
                onClick = onSave,
                description = stringResource(R.string.action_save),
                filled = true
            )
        }
    }
}

@Composable
private fun HistoryPill(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Row(Modifier.padding(horizontal = 4.dp)) {
            HistoryButton(R.drawable.ic_undo, stringResource(R.string.action_undo), canUndo, onUndo)
            HistoryButton(R.drawable.ic_redo, stringResource(R.string.action_redo), canRedo, onRedo)
        }
    }
}

@Composable
private fun HistoryButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = expressivePillShape(interaction, restCorner = 22.dp, pressedCorner = 10.dp)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        interactionSource = interaction,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
        ),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .semantics { contentDescription = description }
    ) {
        AppIcon(
            icon = icon,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA),
            size = 20.dp
        )
    }
}

private const val DISABLED_ALPHA = 0.38f
