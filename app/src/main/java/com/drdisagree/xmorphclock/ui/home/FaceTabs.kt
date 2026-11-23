package com.drdisagree.xmorphclock.ui.home

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.ui.components.AppIcon

@Composable
fun FaceTabs(
    selected: FaceTab,
    onSelect: (FaceTab) -> Unit,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val entries = FaceTab.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.heightIn(min = height)) {
        entries.forEachIndexed { index, tab ->
            val label = stringResource(tabLabel(tab))
            val isSelected = tab == selected
            SegmentedButton(
                selected = isSelected,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size),
                icon = {},
                modifier = Modifier.semantics { contentDescription = label }
            ) {
                AppIcon(
                    icon = tabIcon(tab),
                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    size = ICON_SIZE
                )
            }
        }
    }
}

private fun tabIcon(tab: FaceTab): Int = when (tab) {
    FaceTab.CLOCK -> R.drawable.ic_kind_clock
    FaceTab.WIDGETS -> R.drawable.ic_kind_widgets
    FaceTab.MINE -> R.drawable.ic_kind_mine
}

private val ICON_SIZE = 18.dp
