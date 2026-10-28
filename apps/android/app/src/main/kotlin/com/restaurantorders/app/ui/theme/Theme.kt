package com.restaurantorders.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.restaurantorders.domain.PreModifier

// The same colours as the web app (apps/frontend/src/styles.css).
val Red = Color(0xFFB91C1C)
val RedSoft = Color(0xFFFEE2E2)
val Ink = Color(0xFF111827)
val Muted = Color(0xFF6B7280)
val Line = Color(0xFFE5E7EB)
val Background = Color(0xFFF9FAFB)
val Ok = Color(0xFF166534)
val OkSoft = Color(0xFFDCFCE7)
val Warn = Color(0xFF92400E)
val WarnSoft = Color(0xFFFEF3C7)
val Info = Color(0xFF1E40AF)
val InfoSoft = Color(0xFFDBEAFE)

/** NO in red, ADD/SUB in green, EXTRA in blue, LIGHT/ON SIDE in amber, as in the web app. */
fun premodColor(action: PreModifier): Color = when (action) {
    PreModifier.NO -> Red
    PreModifier.ADD, PreModifier.SUB -> Ok
    PreModifier.EXTRA -> Info
    PreModifier.LIGHT, PreModifier.SIDE -> Warn
}

private val colors = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    primaryContainer = RedSoft,
    onPrimaryContainer = Red,
    secondary = Ink,
    onSecondary = Color.White,
    secondaryContainer = Line,
    onSecondaryContainer = Ink,
    background = Background,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    // Cards and sheets stay white, as in the web app, instead of Material's tinted greys.
    surfaceVariant = Background,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    onSurfaceVariant = Muted,
    outline = Line,
    outlineVariant = Line,
    error = Red,
)

@Composable
fun OrdersTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, content = content)
