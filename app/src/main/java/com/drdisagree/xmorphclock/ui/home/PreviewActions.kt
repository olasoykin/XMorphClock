package com.drdisagree.xmorphclock.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.ui.components.ExpressiveIconButton

@Composable
fun PreviewActions(
    height: Dp,
    canEdit: Boolean,
    onAdd: () -> Unit,
    onFullscreen: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonSize = (height - 4.dp).coerceAtLeast(36.dp)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveIconButton(
                icon = R.drawable.ic_fullscreen,
                onClick = onFullscreen,
                size = buttonSize,
                description = stringResource(R.string.action_fullscreen),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                iconTint = MaterialTheme.colorScheme.onSurface
            )
            ExpressiveIconButton(
                icon = R.drawable.ic_plus,
                onClick = onAdd,
                size = buttonSize,
                description = stringResource(R.string.action_add_element),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            ExpressiveIconButton(
                icon = R.drawable.ic_edit,
                onClick = onEdit,
                enabled = canEdit,
                size = buttonSize,
                description = stringResource(R.string.action_edit),
                filled = true
            )
        }
    }
}
