package com.powerz.km003c.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Design tokens — see design/DESIGN.md (WHOOP-benchmarked, data-first dark UI).

// Background (official WHOOP in-app gradient)
val BgTop = Color(0xFF283339)
val BgBottom = Color(0xFF101518)
val Panel = Color(0xFF1A2227)
val PanelStrong = Color(0xFF232D33)
val Border = Color(0xFF2A353C)

// Text
val Ink = Color(0xFFFFFFFF)
val InkMuted = Color(0xFF9AA7AF)
val InkFaint = Color(0xFF5E6B73)
val InkOnBrand = Color(0xFF000000)

// Brand / action
val Brand = Color(0xFF00F19F)
val BrandPressed = Color(0xFF00C482)
val BrandSoft = Color(0xFF0A2A22)

// Measurement data — one metric, one colour, everywhere
val Vbus = Color(0xFFFFDE00)
val Ibus = Color(0xFF0093E7)
val IbusText = Color(0xFF3AB0FF)   // small-size IBUS text (contrast)
val Power = Color(0xFFFF7A2F)
val Energy = Color(0xFF00F19F)
val Aux = Color(0xFF7BA1BB)
val Pd = Color(0xFF67AEE6)

// Status only
val Ok = Color(0xFF16EC06)
val Warn = Color(0xFFFFDE00)
val Danger = Color(0xFFFF0026)

// ── Legacy aliases (screens not yet migrated pick up the new palette) ──
val BgDark = BgBottom
val SurfaceDark = BgBottom
val SurfaceCard = Panel
val SurfaceElevated = PanelStrong
val BorderSubtle = Border
val BorderHighlight = Color(0xFF3A4750)
val VoltYellow = Vbus
val AmpCyan = Ibus
val PowerOrange = Power
val EnergyGreen = Energy
val PdPurple = Pd
val WarnRed = Danger
val RecordRed = Danger
val TextPrimary = Ink
val TextSecondary = InkMuted

val BackgroundGradient = Brush.verticalGradient(listOf(BgTop, BgBottom))

fun Modifier.appBackground(): Modifier = this.background(BackgroundGradient)

private val PowerZDarkColors = darkColorScheme(
    primary = Brand,
    onPrimary = InkOnBrand,
    primaryContainer = BrandSoft,
    onPrimaryContainer = Brand,
    secondary = Ibus,
    onSecondary = InkOnBrand,
    tertiary = Power,
    background = BgBottom,
    onBackground = Ink,
    surface = BgBottom,
    onSurface = Ink,
    surfaceVariant = PanelStrong,
    onSurfaceVariant = InkMuted,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelStrong,
    surfaceContainerHighest = PanelStrong,
    outline = Border,
    outlineVariant = Border,
    error = Danger,
    onError = Ink
)

@Composable
fun PowerZTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PowerZDarkColors,
        typography = PowerZTypography,
        shapes = PowerZShapes,
        content = content
    )
}
