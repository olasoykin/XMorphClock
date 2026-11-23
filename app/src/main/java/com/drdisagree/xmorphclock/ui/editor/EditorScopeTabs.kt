package com.drdisagree.xmorphclock.ui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.domain.model.EditScope
import com.drdisagree.xmorphclock.ui.components.ExpressiveSegmentedTabs
import com.drdisagree.xmorphclock.ui.components.FitText

@Composable
fun EditorScopeTabs(
    scope: EditScope,
    onScopeChange: (EditScope) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") height: Dp = 44.dp
) {
    val entries = EditScope.entries
    ExpressiveSegmentedTabs(
        count = entries.size,
        selectedIndex = scope.ordinal,
        onSelect = { onScopeChange(entries[it]) },
        modifier = modifier.fillMaxWidth()
    ) { index, selected ->
        val entry = entries[index]
        FitText(
            stringResource(scopeLabel(entry)),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

private fun scopeLabel(scope: EditScope): Int = when (scope) {
    EditScope.LARGE -> R.string.scope_large
    EditScope.SMALL -> R.string.scope_small
    EditScope.AOD -> R.string.scope_aod
}
