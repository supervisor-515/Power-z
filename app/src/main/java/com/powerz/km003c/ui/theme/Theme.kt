package com.powerz.km003c.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF080B10)
val SurfaceDark = Color(0xFF111622)
val SurfaceCard = Color(0xFF141B29)
val SurfaceElevated = Color(0xFF1B2436)
val BorderSubtle = Color(0xFF253147)
val BorderHighlight = Color(0xFF334463)

val VoltYellow = Color(0xFFFFD54F)
val AmpCyan = Color(0xFF26E6FF)
val PowerOrange = Color(0xFFFF7A2F)
val EnergyGreen = Color(0xFF2EE68A)
val PdPurple = Color(0xFFC49BFF)
val WarnRed = Color(0xFFFF4D61)
val RecordRed = Color(0xFFFF3B57)
val TextPrimary = Color(0xFFF3F7FF)
val TextSecondary = Color(0xFF8B99B0)

private val PowerZDarkColors = darkColorScheme(
    primary = VoltYellow,
    onPrimary = Color(0xFF0A0D14),
    secondary = AmpCyan,
    onSecondary = Color(0xFF0A0D14),
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
