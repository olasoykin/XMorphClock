package com.drdisagree.xmorphclock.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.ui.components.ExpressiveButton
import com.drdisagree.xmorphclock.ui.components.ExpressiveIconButton

@Composable
fun GroupingBar(count: Int, onCancel: () -> Unit, onGroup: () -> Unit, modifier: Modifier = Modifier) {
    val ready = count >= MIN_ITEMS
    Box(
        modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        ExpressiveIconButton(
            icon = R.drawable.ic_cross,
            onClick = onCancel,
            description = stringResource(R.string.action_cancel),
            modifier = Modifier.align(Alignment.CenterStart)
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                stringResource(R.string.grouping_hint),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        ExpressiveButton(
            onClick = onGroup,
            enabled = ready,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Text(stringResource(R.string.action_group_count, count), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private const val MIN_ITEMS = 2
