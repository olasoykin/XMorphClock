package com.drdisagree.xmorphclock.ui.about

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drdisagree.xmorphclock.BuildConfig
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.ui.components.AppIcon
import com.drdisagree.xmorphclock.ui.components.PageScaffold
import com.drdisagree.xmorphclock.ui.components.expressivePillShape
import com.drdisagree.xmorphclock.ui.components.openUrl

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit) {
    val context = LocalContext.current
    val developerUrl = stringResource(R.string.developer_url)
    val sourceUrl = stringResource(R.string.source_url)
    val supportUrl = stringResource(R.string.support_url)
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    PageScaffold(stringResource(R.string.action_about), onBack) {
        item(key = "identity") { Entrance(entered, 0) { Identity() } }
        item(key = "links") {
            Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                    Entrance(entered, 1, Modifier.weight(1f)) {
                        LinkTile(R.drawable.ic_code, stringResource(R.string.about_source), stringResource(R.string.about_source_hint)) { openUrl(context, sourceUrl) }
                    }
                    Entrance(entered, 2, Modifier.weight(1f)) {
                        LinkTile(R.drawable.ic_coffee, stringResource(R.string.about_support), stringResource(R.string.about_support_hint)) { openUrl(context, supportUrl) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                    Entrance(entered, 3, Modifier.weight(1f)) {
                        LinkTile(R.drawable.ic_kind_mine, stringResource(R.string.about_developer), stringResource(R.string.about_developer_name)) { openUrl(context, developerUrl) }
                    }
                    Entrance(entered, 4, Modifier.weight(1f)) {
                        LinkTile(R.drawable.ic_document, stringResource(R.string.licenses), stringResource(R.string.about_licenses_hint), onOpenLicenses)
                    }
                }
            }
        }
        item(key = "footer") {
            Entrance(entered, 5) {
                Text(
                    stringResource(R.string.about_footer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun Entrance(entered: Boolean, index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val progress by animateFloatAsState(
        if (entered) 1f else 0f,
        tween(ENTER_MS, delayMillis = index * STAGGER_MS, easing = Emphasized),
        label = "entrance"
    )
    Box(
        modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * with(density) { ENTER_SHIFT.toPx() }
        }
    ) { content() }
}

@Composable
private fun Identity() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(64.dp).clip(IconShape), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(96.dp))
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(96.dp))
        }
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 14.dp)
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
        Text(
            stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp, start = 24.dp, end = 24.dp)
        )
    }
}

@Composable
private fun LinkTile(@DrawableRes icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, tween(PRESS_MS), label = "press")
    val shape = expressivePillShape(interaction, restCorner = 28.dp, pressedCorner = 20.dp)
    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Column(Modifier.padding(18.dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AppIcon(icon, tint = MaterialTheme.colorScheme.onPrimaryContainer, size = 20.dp)
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(top = 18.dp)
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val IconShape = RoundedCornerShape(18.dp)
private val TILE_GAP = 12.dp
private val ENTER_SHIFT = 24.dp
private const val ENTER_MS = 520
private const val STAGGER_MS = 70
private const val PRESS_MS = 160
private const val PRESSED_SCALE = 0.96f
