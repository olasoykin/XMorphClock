package com.drdisagree.xmorphclock.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DialogButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (primary) {
        ExpressiveButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled
        ) {
            FitText(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    } else {
        ExpressiveTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled
        ) {
            FitText(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
