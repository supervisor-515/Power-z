package com.powerz.km003c.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF0D1117)
val SurfaceDark = Color(0xFF161B22)
val SurfaceElevated = Color(0xFF21262D)
val BorderSubtle = Color(0xFF30363D)

val VoltYellow = Color(0xFFFFD600)
val AmpCyan = Color(0xFF00E5FF)
val PowerOrange = Color(0xFFFF6D00)
val EnergyGreen = Color(0xFF00E676)
val PdPurple = Color(0xFFBB86FC)
val WarnRed = Color(0xFFFF5252)
val TextPrimary = Color(0xFFF0F6FC)
val TextSecondary = Color(0xFF8B949E)

private val PowerZDarkColors = darkColorScheme(
    primary = VoltYellow,
    onPrimary = Color(0xFF0D1117),
    secondary = AmpCyan,
    onSecondary = Color(0xFF0D1117),
    tertiary = PowerOrange,
    background = BgDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = WarnRed
)

@Composable
fun PowerZTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PowerZDarkColors,
        content = content
    )
}
