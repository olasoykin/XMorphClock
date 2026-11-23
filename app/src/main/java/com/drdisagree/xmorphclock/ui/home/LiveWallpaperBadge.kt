package com.drdisagree.xmorphclock.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.ui.components.ExpressiveIconButton

@Composable
fun LiveWallpaperBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExpressiveIconButton(
        icon = R.drawable.ic_alert,
        onClick = onClick,
        modifier = modifier,
        size = LIVE_BADGE_SIZE,
        description = stringResource(R.string.live_wallpaper_badge),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        iconTint = MaterialTheme.colorScheme.onTertiaryContainer
    )
}

val LIVE_BADGE_SIZE = 32.dp
