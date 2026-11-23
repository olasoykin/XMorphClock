package com.drdisagree.xmorphclock.ui.home

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drdisagree.xmorphclock.ui.components.fadingEdges

@Composable
fun CategoryChips(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val list = rememberLazyListState()
    LaunchedEffect(selected) {
        if (selected in labels.indices) list.animateScrollToItem((selected - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = list,
        modifier = modifier.fadingEdges(list, Orientation.Horizontal),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(labels) { index, label ->
            val isSelected = index == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(index) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
            )
        }
    }
}
