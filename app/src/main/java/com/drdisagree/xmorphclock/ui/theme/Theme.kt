package com.drdisagree.xmorphclock.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun XMorphClockTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val rawScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val scheme = animatedColorScheme(rawScheme)
    val palette = Palette(
        isDark = dark,
        ink = scheme.surface,
        onInk = scheme.onSurface,
        onInkMuted = scheme.onSurfaceVariant,
        onInkFaint = scheme.outline,
        container = scheme.surfaceContainerLow,
        containerHigh = scheme.surfaceContainerHigh,
        hairline = scheme.outlineVariant,
        accent = scheme.primary,
        flat = scheme.surfaceContainer,
        tabContainer = scheme.surfaceContainerHighest,
        toggleTrack = scheme.surfaceContainerHighest,
        toggleOn = scheme.primary,
        mesh = listOf(scheme.surface, scheme.surfaceContainerLow, scheme.surfaceContainer, scheme.surfaceContainerHigh)
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = ExpressiveShapes,
            typography = Typography,
            content = content
        )
    }
}

@Composable
private fun animatedColorScheme(target: ColorScheme): ColorScheme = ColorScheme(
    primary = animated(target.primary),
    onPrimary = animated(target.onPrimary),
    primaryContainer = animated(target.primaryContainer),
    onPrimaryContainer = animated(target.onPrimaryContainer),
    inversePrimary = animated(target.inversePrimary),
    secondary = animated(target.secondary),
    onSecondary = animated(target.onSecondary),
    secondaryContainer = animated(target.secondaryContainer),
    onSecondaryContainer = animated(target.onSecondaryContainer),
    tertiary = animated(target.tertiary),
    onTertiary = animated(target.onTertiary),
    tertiaryContainer = animated(target.tertiaryContainer),
    onTertiaryContainer = animated(target.onTertiaryContainer),
    background = animated(target.background),
    onBackground = animated(target.onBackground),
    surface = animated(target.surface),
    onSurface = animated(target.onSurface),
    surfaceVariant = animated(target.surfaceVariant),
    onSurfaceVariant = animated(target.onSurfaceVariant),
    surfaceTint = animated(target.surfaceTint),
    inverseSurface = animated(target.inverseSurface),
    inverseOnSurface = animated(target.inverseOnSurface),
    error = animated(target.error),
    onError = animated(target.onError),
    errorContainer = animated(target.errorContainer),
    onErrorContainer = animated(target.onErrorContainer),
    outline = animated(target.outline),
    outlineVariant = animated(target.outlineVariant),
    scrim = animated(target.scrim),
    surfaceBright = animated(target.surfaceBright),
    surfaceDim = animated(target.surfaceDim),
    surfaceContainer = animated(target.surfaceContainer),
    surfaceContainerHigh = animated(target.surfaceContainerHigh),
    surfaceContainerHighest = animated(target.surfaceContainerHighest),
    surfaceContainerLow = animated(target.surfaceContainerLow),
    surfaceContainerLowest = animated(target.surfaceContainerLowest),
    primaryFixed = animated(target.primaryFixed),
    primaryFixedDim = animated(target.primaryFixedDim),
    onPrimaryFixed = animated(target.onPrimaryFixed),
    onPrimaryFixedVariant = animated(target.onPrimaryFixedVariant),
    secondaryFixed = animated(target.secondaryFixed),
    secondaryFixedDim = animated(target.secondaryFixedDim),
    onSecondaryFixed = animated(target.onSecondaryFixed),
    onSecondaryFixedVariant = animated(target.onSecondaryFixedVariant),
    tertiaryFixed = animated(target.tertiaryFixed),
    tertiaryFixedDim = animated(target.tertiaryFixedDim),
    onTertiaryFixed = animated(target.onTertiaryFixed),
    onTertiaryFixedVariant = animated(target.onTertiaryFixedVariant)
)

@Composable
private fun animated(color: Color): Color = animateColorAsState(color, tween(THEME_FADE), label = "schemeColor").value

private const val THEME_FADE = 350
