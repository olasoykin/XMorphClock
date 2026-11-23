package com.drdisagree.xmorphclock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

val Positive = Color(0xFF2E7D32)
val Negative: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
val Warning = Color(0xFFF57C00)

val Ink: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface

val OnInk: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface

val OnInkMuted: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

val OnInkFaint: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline

val Surface: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerLow

val SurfaceHigh: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHigh

val Hairline: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant

val Accent: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

@Composable
@ReadOnlyComposable
fun onTint(tint: Color): Color =
    if (tint == MaterialTheme.colorScheme.primary) MaterialTheme.colorScheme.onPrimary
    else if (tint == MaterialTheme.colorScheme.error) MaterialTheme.colorScheme.onError
    else Color.White
