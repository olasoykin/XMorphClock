package com.drdisagree.xmorphclock.ui.licenses

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.domain.model.License
import com.drdisagree.xmorphclock.ui.components.AppIcon
import com.drdisagree.xmorphclock.ui.components.PageScaffold
import com.drdisagree.xmorphclock.ui.components.fadingEdges

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val CardShape = RoundedCornerShape(24.dp)
private val TEXT_HEIGHT = 360.dp

@Composable
fun LicensesScreen(viewModel: LicensesViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    PageScaffold(stringResource(R.string.licenses), onBack) {
        items(state.licenses, key = { it.name }) { license ->
            val open = expanded == license.name
            LicenseCard(license, open, onToggle = { expanded = if (open) null else license.name })
        }
    }
}

@Composable
private fun LicenseCard(license: License, open: Boolean, onToggle: () -> Unit) {
    val paragraphs = remember(license.text) { paragraphsOf(license.text) }
    val rotation by animateFloatAsState(if (open) 90f else 0f, tween(320, easing = Emphasized), label = "chevron")
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            onClick = onToggle,
            shape = CardShape,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(license.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(paragraphs.firstOrNull().orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                AppIcon(
                    R.drawable.ic_chevron,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 16.dp,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                )
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(260, delayMillis = 80)) + expandVertically(tween(420, easing = Emphasized)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(360, easing = Emphasized))
        ) {
            val list = rememberLazyListState()
            LazyColumn(
                state = list,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TEXT_HEIGHT)
                    .fadingEdges(list, Orientation.Vertical),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(paragraphs.drop(1)) { paragraph ->
                    Text(paragraph, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun paragraphsOf(text: String): List<String> =
    text.trim().split(PARAGRAPH).map { it.trim().replace(LINE_BREAK, " ") }.filter { it.isNotEmpty() }

private val PARAGRAPH = Regex("\\n\\s*\\n")
private val LINE_BREAK = Regex("\\s*\\n\\s*")
