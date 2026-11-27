package com.powerz.km003c.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerz.km003c.model.GraphPoint
import com.powerz.km003c.model.TelemetrySample
import com.powerz.km003c.ui.theme.AmpCyan
import com.powerz.km003c.ui.theme.BorderSubtle
import com.powerz.km003c.ui.theme.PowerOrange
import com.powerz.km003c.ui.theme.SurfaceDark
import com.powerz.km003c.ui.theme.TextSecondary
import com.powerz.km003c.ui.theme.VoltYellow
import java.util.Locale
import kotlin.math.abs

@Composable
fun ChartScreen(
    telemetry: TelemetrySample,
    points: List<GraphPoint>
) {
    var showV by remember { mutableStateOf(true) }
    var showI by remember { mutableStateOf(true) }
    var showW by remember { mutableStateOf(true) }
    var windowSec by remember { mutableDoubleStateOf(30.0) }

    val latestTime = points.lastOrNull()?.timeSec ?: 0.0
    val visible = remember(points, windowSec) {
        if (windowSec <= 0.0) points
        else points.filter { it.timeSec >= latestTime - windowSec }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Live mini readout bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = String.format(Locale.US, "%.3f V", telemetry.vbusV),
                color = VoltYellow,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = String.format(Locale.US, "%.3f A", abs(telemetry.ibusA)),
                color = AmpCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = String.format(Locale.US, "%.2f W", telemetry.powerW),
                color = PowerOrange,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        // Channel toggles & window selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = showV,
                onClick = { showV = !showV },
                label = { Text("VBUS", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VoltYellow.copy(alpha = 0.2f),
                    selectedLabelColor = VoltYellow
                )
            )
            FilterChip(
                selected = showI,
                onClick = { showI = !showI },
                label = { Text("IBUS", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AmpCyan.copy(alpha = 0.2f),
                    selectedLabelColor = AmpCyan
                )
            )
            FilterChip(
                selected = showW,
                onClick = { showW = !showW },
                label = { Text("POWER", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PowerOrange.copy(alpha = 0.2f),
                    selectedLabelColor = PowerOrange
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(10.0 to "10초", 30.0 to "30초", 60.0 to "1분", 0.0 to "전체").forEach { (sec, label) ->
                FilterChip(
                    selected = windowSec == sec,
                    onClick = { windowSec = sec },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        // Main Chart Canvas
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, BorderSubtle),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (visible.size < 2) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("측정 데이터 수신 대기 중...", color = TextSecondary, fontSize = 13.sp)
                }
            } else {
                val maxV = (visible.maxOf { it.vbusV } * 1.15f).coerceAtLeast(6f)
                val maxI = (visible.maxOf { it.ibusA } * 1.15f).coerceAtLeast(1f)
                val maxW = (visible.maxOf { it.powerW } * 1.15f).coerceAtLeast(5f)

                Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                    // Scale header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = String.format(Locale.US, "Y-Max: %.1fV | %.2fA | %.1fW", maxV, maxI, maxW),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${visible.size} pts",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val w = size.width
                        val h = size.height

                        // Grid lines
                        val gridColor = Color(0xFF262C36)
                        for (i in 0..4) {
                            val y = h * i / 4f
                            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                        }
                        for (i in 0..4) {
                            val x = w * i / 4f
                            drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                        }

                        val minT = visible.first().timeSec
                        val spanT = (visible.last().timeSec - minT).coerceAtLeast(0.1)

                        fun drawSeries(color: Color, maxVal: Float, selector: (GraphPoint) -> Float) {
                            val path = Path()
                            visible.forEachIndexed { idx, pt ->
                                val x = (((pt.timeSec - minT) / spanT) * w).toFloat().coerceIn(0f, w)
                                val y = (h - (selector(pt) / maxVal) * h).coerceIn(0f, h)
                                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }

                        if (showW) drawSeries(PowerOrange, maxW) { it.powerW }
                        if (showI) drawSeries(AmpCyan, maxI) { it.ibusA }
                        if (showV) drawSeries(VoltYellow, maxV) { it.vbusV }
                    }
                }
            }
        }
    }
}
