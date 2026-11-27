package com.powerz.km003c.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.powerz.km003c.ui.theme.Brand
import com.powerz.km003c.ui.theme.Ink
import com.powerz.km003c.ui.theme.InkFaint
import com.powerz.km003c.ui.theme.InkMuted
import com.powerz.km003c.ui.theme.InkOnBrand
import com.powerz.km003c.ui.theme.Panel
import com.powerz.km003c.ui.theme.PanelStrong
import com.powerz.km003c.ui.theme.PzRadius
import com.powerz.km003c.ui.theme.PzType

/** Wide uppercase tracking suits Latin labels only; Hangul gets near-zero tracking. */
internal fun TextStyle.forText(text: String): TextStyle =
    if (text.any { it in '\uAC00'..'\uD7A3' || it in '\u3131'..'\u318E' }) copy(letterSpacing = 0.02.em) else this

private val GaugeEasing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/** Flat card: Panel background, 8dp radius, no border, no shadow. */
@Composable
fun PzCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(PzRadius.md))
            .background(Panel)
            .padding(padding),
        content = content
    )
}

/** Small uppercase label (VBUS, IBUS, POWER…). */
@Composable
fun PzLabel(text: String, color: Color = InkMuted, modifier: Modifier = Modifier) {
    Text(text = text.uppercase(), style = PzType.label.forText(text), color = color, modifier = modifier)
}

/** Number + smaller muted unit, baseline aligned. */
@Composable
fun PzValue(
    value: String,
    unit: String,
    color: Color,
    style: TextStyle,
    unitStyle: TextStyle = PzType.unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = style,
            color = color,
            modifier = Modifier.alignByBaseline()
        )
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(text = unit, style = unitStyle, color = InkMuted, modifier = Modifier.alignByBaseline())
        }
    }
}

/**
 * Circular gauge (WHOOP-style ring). 270° sweep, round caps.
 * Fills from 0 on first composition (1200ms), then follows the value without animation.
 */
@Composable
fun PzRingGauge(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    glow: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(1200, easing = GaugeEasing)) }
    val shown = progress.coerceIn(0f, 1f) * intro.value

    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val sw = strokeWidth.toPx()
            val inset = sw / 2f + if (glow) sw else 0f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val start = 135f
            val sweep = 270f
            drawArc(Ink.copy(alpha = 0.08f), start, sweep, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            if (shown > 0f) {
                if (glow) {
                    drawArc(
                        color.copy(alpha = 0.10f), start, sweep * shown, false, topLeft, arcSize,
                        style = Stroke(sw * 2f, cap = StrokeCap.Round)
                    )
                }
                drawArc(color, start, sweep * shown, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Semi-circular progress arc (auto-stop countdown, target mAh). */
@Composable
fun PzProgressArc(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(modifier = modifier.aspectRatio(2f), contentAlignment = Alignment.BottomCenter) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val sw = strokeWidth.toPx()
            val d = size.width - sw
            val topLeft = Offset(sw / 2f, sw / 2f)
            val arcSize = Size(d, d)
            drawArc(Ink.copy(alpha = 0.08f), 180f, 180f, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            val p = progress.coerceIn(0f, 1f)
            if (p > 0f) drawArc(color, 180f, 180f * p, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Minimal line sparkline with soft area fill. */
@Composable
fun PzSparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.dp
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 1e-6f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val pad = strokeWidth.toPx()
        val h = size.height - pad * 2
        fun y(v: Float) = pad + h - ((v - min) / range) * h

        val line = Path().apply {
            moveTo(0f, y(values[0]))
            for (i in 1 until values.size) lineTo(i * stepX, y(values[i]))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Status pill: coloured background, black uppercase text. */
@Composable
fun PzStatusPill(text: String, color: Color, modifier: Modifier = Modifier, dot: Boolean = false) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(InkOnBrand))
            Spacer(Modifier.width(6.dp))
        }
        Text(text.uppercase(), style = PzType.label.forText(text), color = InkOnBrand)
    }
}

/** Quiet pill: outline-free, muted surface — for neutral info (sample rate, interval). */
@Composable
fun PzInfoPill(text: String, modifier: Modifier = Modifier, color: Color = InkMuted) {
    Text(
        text = text.uppercase(),
        style = PzType.label.forText(text),
        color = color,
        modifier = modifier
            .clip(CircleShape)
            .background(PanelStrong)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** Primary CTA — one per screen. */
@Composable
fun PzPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color = Brand,
    content: Color = InkOnBrand,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(PzRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = PanelStrong,
            disabledContentColor = InkFaint
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), style = PzType.headline.forText(text))
    }
}

/** Secondary — white outline. */
@Composable
fun PzSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(PzRadius.md),
        border = BorderStroke(1.5.dp, if (enabled) Ink.copy(alpha = 0.85f) else PanelStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink, disabledContentColor = InkFaint),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text.uppercase(), style = PzType.section.forText(text), maxLines = 1)
    }
}

/** Label-over-value cell used in stat rows. */
@Composable
fun RowScope.PzStatCell(
    label: String,
    value: String,
    unit: String = "",
    color: Color = Ink,
    style: TextStyle = PzType.metric
) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PzLabel(label)
        PzValue(value = value, unit = unit, color = color, style = style, unitStyle = PzType.caption)
    }
}

/** Section header — small uppercase with optional trailing slot. */
@Composable
fun PzSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title.uppercase(), style = PzType.section.forText(title), color = InkMuted, modifier = Modifier.weight(1f))
        trailing()
    }
}
