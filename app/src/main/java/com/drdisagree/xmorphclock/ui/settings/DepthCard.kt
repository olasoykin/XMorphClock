package com.drdisagree.xmorphclock.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.domain.model.DepthSettings
import com.drdisagree.xmorphclock.domain.model.DepthStatus
import com.drdisagree.xmorphclock.ui.components.ExpressiveSlider
import com.drdisagree.xmorphclock.ui.components.ExpressiveSwitch
import com.drdisagree.xmorphclock.ui.components.ExpressiveTonalButton
import com.drdisagree.xmorphclock.ui.components.StatusGlyph
import com.drdisagree.xmorphclock.ui.components.StatusTone
import com.drdisagree.xmorphclock.ui.components.toneTextColor
import kotlin.math.roundToInt

@Composable
fun DepthCard(
    settings: DepthSettings,
    status: DepthStatus,
    opacity: Float,
    onEnabledChange: (Boolean) -> Unit,
    onOpacityDrag: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onWidgetsFrontChange: (Boolean) -> Unit,
    onRetry: () -> Unit
) {
    SectionCard {
        Surface(
            onClick = { onEnabledChange(!settings.enabled) },
            shape = TileShape,
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { role = Role.Switch }
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.depth_switch), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    AnimatedContent(
                        targetState = status,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                        label = "depthStatus"
                    ) { current ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (current != DepthStatus.OFF && toneOf(current) == StatusTone.BUSY) StatusGlyph(StatusTone.BUSY, size = 12.dp)
                            Text(
                                stringResource(depthStatusLabel(current)),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (current == DepthStatus.OFF) MaterialTheme.colorScheme.onSurfaceVariant else toneTextColor(toneOf(current))
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                ExpressiveSwitch(settings.enabled, onEnabledChange)
            }
        }
        AnimatedVisibility(
            visible = status == DepthStatus.NO_SUBJECT || status == DepthStatus.UNAVAILABLE,
            enter = fadeIn(tween(240)) + expandVertically(tween(320)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(280))
        ) {
            Box(Modifier.padding(start = 20.dp, bottom = 12.dp)) {
                ExpressiveTonalButton(onClick = onRetry) {
                    Text(stringResource(R.string.action_retry), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    AnimatedVisibility(
        visible = settings.enabled,
        enter = fadeIn(tween(240)) + expandVertically(tween(320)),
        exit = fadeOut(tween(160)) + shrinkVertically(tween(280))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard {
                Surface(
                    onClick = { onWidgetsFrontChange(!settings.widgetsFront) },
                    shape = TileShape,
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { role = Role.Switch }
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.depth_widgets_front), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.depth_widgets_front_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        ExpressiveSwitch(settings.widgetsFront, onWidgetsFrontChange)
                    }
                }
            }
            SectionCard {
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.depth_opacity),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        stringResource(R.string.percent_value, (opacity * PERCENT).roundToInt()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveSlider(
                        value = opacity,
                        onValueChange = onOpacityDrag,
                        valueRange = MIN_OPACITY..1f,
                        onValueChangeFinished = { onOpacityChange(opacity) }
                    )
                }
            }
        }
    }
}

private const val PERCENT = 100f
private const val MIN_OPACITY = 0.1f

internal fun depthStatusLabel(status: DepthStatus): Int = when (status) {
    DepthStatus.OFF -> R.string.depth_summary
    DepthStatus.WAITING -> R.string.depth_status_waiting
    DepthStatus.PREPARING -> R.string.depth_status_preparing
    DepthStatus.EXTRACTING -> R.string.depth_status_extracting
    DepthStatus.READY -> R.string.depth_status_ready
    DepthStatus.NO_SUBJECT -> R.string.depth_status_no_subject
    DepthStatus.UNAVAILABLE -> R.string.depth_status_unavailable
}

private fun toneOf(status: DepthStatus): StatusTone = when (status) {
    DepthStatus.READY -> StatusTone.OK
    DepthStatus.NO_SUBJECT, DepthStatus.UNAVAILABLE -> StatusTone.WARNING
    else -> StatusTone.BUSY
}
