package com.drdisagree.xmorphclock.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drdisagree.xmorphclock.R

@Composable
fun expressivePillShape(
    interactionSource: MutableInteractionSource,
    restCorner: Dp = 24.dp,
    pressedCorner: Dp = 10.dp
): Shape {
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) pressedCorner else restCorner,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 1400f),
        label = "expressiveShape"
    )
    return RoundedCornerShape(corner)
}

@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = expressivePillShape(interaction, restCorner = 24.dp, pressedCorner = 12.dp)
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interaction,
        content = content
    )
}

@Composable
fun ExpressiveTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = expressivePillShape(interaction, restCorner = 24.dp, pressedCorner = 12.dp)
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interaction,
        content = content
    )
}

@Composable
fun ExpressiveIconButton(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    description: String? = null,
    filled: Boolean = false,
    containerColor: Color = Color.Unspecified,
    iconTint: Color = Color.Unspecified,
    iconModifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = expressivePillShape(interaction, restCorner = size / 2, pressedCorner = 12.dp)
    val semModifier = if (description != null) modifier.semantics { contentDescription = description } else modifier
    val sizedModifier = semModifier.minimumInteractiveComponentSize().size(size)
    val iconSize = (size * 0.48f).coerceAtLeast(16.dp)

    if (filled) {
        val colors: IconButtonColors = if (containerColor != Color.Unspecified || iconTint != Color.Unspecified) {
            IconButtonDefaults.filledIconButtonColors(
                containerColor = if (containerColor != Color.Unspecified) containerColor else MaterialTheme.colorScheme.primary,
                contentColor = if (iconTint != Color.Unspecified) iconTint else MaterialTheme.colorScheme.onPrimary
            )
        } else {
            IconButtonDefaults.filledIconButtonColors()
        }
        FilledIconButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            interactionSource = interaction
        ) {
            AppIcon(
                icon,
                modifier = iconModifier,
                tint = if (iconTint != Color.Unspecified) iconTint else MaterialTheme.colorScheme.onPrimary,
                size = iconSize
            )
        }
    } else {
        val colors: IconButtonColors = if (containerColor != Color.Unspecified || iconTint != Color.Unspecified) {
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (containerColor != Color.Unspecified) containerColor else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (iconTint != Color.Unspecified) iconTint else MaterialTheme.colorScheme.onSecondaryContainer
            )
        } else {
            IconButtonDefaults.filledTonalIconButtonColors()
        }
        FilledTonalIconButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            interactionSource = interaction
        ) {
            AppIcon(
                icon,
                modifier = iconModifier,
                tint = if (iconTint != Color.Unspecified) iconTint else MaterialTheme.colorScheme.onSecondaryContainer,
                size = iconSize
            )
        }
    }
}

@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = {
            AppIcon(
                icon = if (checked) R.drawable.ic_check else R.drawable.ic_cross,
                tint = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                size = SwitchDefaults.IconSize
            )
        }
    )
}

@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null
) {
    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished
    )
}

@Composable
fun ExpressiveSegmentedTabs(
    count: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (Int, Boolean) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        for (index in 0 until count) {
            val selected = index == selectedIndex
            SegmentedButton(
                selected = selected,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = count),
                icon = {}
            ) {
                label(index, selected)
            }
        }
    }
}
