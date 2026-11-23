package com.drdisagree.xmorphclock.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.domain.model.UserFont
import com.drdisagree.xmorphclock.engine.FaceFont
import com.drdisagree.xmorphclock.ui.components.AppIcon
import com.drdisagree.xmorphclock.ui.components.ExpressiveDialogFrame
import com.drdisagree.xmorphclock.ui.components.expressivePillShape
import com.drdisagree.xmorphclock.ui.components.fadingEdges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FontPickerDialog(
    visible: Boolean,
    selected: String?,
    userFonts: List<UserFont>,
    onPick: (String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val builtIn = FontOptions.map { FontEntry(it.family, stringResource(it.label)) }
    val own = userFonts.map { FontEntry(it.family, it.name) }
    val ownLabel = stringResource(R.string.fonts_yours)
    val builtInLabel = stringResource(R.string.fonts_builtin)
    val list = rememberLazyListState()
    ExpressiveDialogFrame(visible, onDismiss) {
        Text(
            stringResource(R.string.choose_font),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 8.dp)
        )
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .heightIn(max = LIST_MAX)
                .fadingEdges(list, Orientation.Vertical)
        ) {
            item(key = ADD_KEY) { AddFontRow(onAdd) }
            if (own.isNotEmpty()) {
                item(key = OWN_HEADER) { SectionLabel(ownLabel) }
                items(own, key = { OWN_PREFIX + it.family }) { entry -> FontRow(entry, entry.family == selected) { onPick(entry.family) } }
            }
            item(key = BUILT_IN_HEADER) { SectionLabel(builtInLabel) }
            items(builtIn, key = { BUILT_IN_PREFIX + it.family }) { entry -> FontRow(entry, entry.family == selected) { onPick(entry.family) } }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun AddFontRow(onAdd: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = expressivePillShape(interaction, restCorner = 16.dp, pressedCorner = 12.dp)
    Surface(
        onClick = onAdd,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interaction,
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AppIcon(R.drawable.ic_plus, tint = MaterialTheme.colorScheme.onPrimaryContainer, size = 20.dp)
                }
            }
            Column {
                Text(stringResource(R.string.action_add_font), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.font_add_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FontRow(entry: FontEntry, selected: Boolean, onClick: () -> Unit) {
    val family by produceState<FontFamily?>(null, entry.family) {
        value = withContext(Dispatchers.Default) {
            FontFamily(Typeface(FaceFont.typeface(entry.family, SAMPLE_WEIGHT, SAMPLE_WIDTH, SAMPLE_HEIGHT)))
        }
    }
    val shown by animateFloatAsState(if (family != null) 1f else 0f, tween(240), label = "fontSample")
    val fill by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "fontFill"
    )
    val interaction = remember { MutableInteractionSource() }
    val shape = expressivePillShape(interaction, restCorner = 16.dp, pressedCorner = 12.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = fill,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                stringResource(R.string.font_sample),
                fontFamily = family,
                fontSize = SAMPLE_SIZE,
                lineHeight = SAMPLE_LINE,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { alpha = shown }
            )
            Text(
                entry.name,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private val LIST_MAX = 460.dp
private val SAMPLE_SIZE = 26.sp
private val SAMPLE_LINE = 32.sp
private const val SAMPLE_WEIGHT = 400
private const val SAMPLE_WIDTH = 100
private const val SAMPLE_HEIGHT = 700
private const val ADD_KEY = "add"
private const val OWN_HEADER = "ownHeader"
private const val BUILT_IN_HEADER = "builtInHeader"
private const val OWN_PREFIX = "own:"
private const val BUILT_IN_PREFIX = "builtIn:"
