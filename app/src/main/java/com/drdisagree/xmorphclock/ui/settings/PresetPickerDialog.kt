package com.drdisagree.xmorphclock.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.ui.components.ClockFacePreview
import com.drdisagree.xmorphclock.ui.components.DialogButton
import com.drdisagree.xmorphclock.ui.components.ExpressiveDialogFrame
import com.drdisagree.xmorphclock.ui.components.ExpressiveTonalButton
import com.drdisagree.xmorphclock.ui.components.expressivePillShape
import com.drdisagree.xmorphclock.ui.components.fadingEdges
import com.drdisagree.xmorphclock.ui.home.ThumbInk
import com.drdisagree.xmorphclock.ui.theme.DarkPalette
import com.drdisagree.xmorphclock.ui.theme.LocalPalette

@Composable
fun PresetPickerDialog(
    visible: Boolean,
    title: String,
    message: String,
    items: List<PresetPickerItem>,
    initialSelection: Set<Int>,
    @StringRes confirmLabel: Int,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    ExpressiveDialogFrame(visible, onDismiss) {
        var selected by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(initialSelection) }
        val all = remember(items) { items.mapTo(HashSet()) { it.key } }
        val everything = all.isNotEmpty() && selected.containsAll(all)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 20.dp, top = 24.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (items.isNotEmpty()) {
                SelectionAction(everything) { selected = if (everything) emptySet() else all }
            }
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
        )
        if (items.isNotEmpty()) {
            val list = rememberLazyListState()
            LazyColumn(
                state = list,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .heightIn(max = LIST_MAX)
                    .fadingEdges(list, Orientation.Vertical)
            ) {
                items(items, key = { it.key }) { item ->
                    PresetRow(item, item.key in selected) {
                        selected = if (item.key in selected) selected - item.key else selected + item.key
                    }
                }
            }
        }
        Box(
            Modifier
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)
                .fillMaxWidth()
        ) {
            if (items.isEmpty()) {
                DialogButton(stringResource(R.string.action_close), primary = true, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            } else {
                val ready = selected.isNotEmpty()
                DialogButton(
                    label = stringResource(confirmLabel, selected.size),
                    primary = true,
                    onClick = { if (ready) onConfirm(items.map { it.key }.filter { it in selected }) },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SelectionAction(everything: Boolean, onClick: () -> Unit) {
    ExpressiveTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        AnimatedContent(
            targetState = everything,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = SWAP_SCALE)) togetherWith
                    (fadeOut(tween(140)) + scaleOut(tween(140), targetScale = SWAP_SCALE)) using SizeTransform(clip = false)
            },
            contentAlignment = Alignment.Center,
            label = "selectionAction"
        ) { cleared ->
            Text(
                stringResource(if (cleared) R.string.action_clear else R.string.action_select_all),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PresetRow(item: PresetPickerItem, checked: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val rowShape = expressivePillShape(interaction, restCorner = 16.dp, pressedCorner = 12.dp)
    val rowColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        tween(220),
        label = "presetRowColor"
    )
    Surface(
        onClick = onToggle,
        shape = rowShape,
        color = rowColor,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .semantics { role = Role.Checkbox }
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(THUMB_WIDTH, THUMB_HEIGHT)
                    .clip(ThumbShape)
                    .background(ThumbInk)
                    .padding(THUMB_PADDING),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(LocalPalette provides DarkPalette) {
                    ClockFacePreview(face = item.face, modifier = Modifier.fillMaxWidth())
                }
            }
            Text(
                item.face.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Checkbox(checked = checked, onCheckedChange = null)
        }
    }
}

private val SelectionSaver = listSaver<Set<Int>, Int>(save = { it.toList() }, restore = { it.toSet() })
private val ThumbShape = RoundedCornerShape(10.dp)
private val ROW_HEIGHT = 60.dp
private val THUMB_WIDTH = 72.dp
private val THUMB_HEIGHT = 44.dp
private val THUMB_PADDING = 6.dp
private val LIST_MAX = 360.dp
private const val SWAP_SCALE = 0.9f
