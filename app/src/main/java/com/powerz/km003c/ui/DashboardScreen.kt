package com.powerz.km003c.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerz.km003c.model.SampleRateMode
import com.powerz.km003c.model.SessionStats
import com.powerz.km003c.model.TelemetrySample
import com.powerz.km003c.ui.theme.AmpCyan
import com.powerz.km003c.ui.theme.BorderSubtle
import com.powerz.km003c.ui.theme.EnergyGreen
import com.powerz.km003c.ui.theme.PowerOrange
import com.powerz.km003c.ui.theme.SurfaceDark
import com.powerz.km003c.ui.theme.SurfaceElevated
import com.powerz.km003c.ui.theme.TextPrimary
import com.powerz.km003c.ui.theme.TextSecondary
import com.powerz.km003c.ui.theme.VoltYellow
import java.util.Locale
import kotlin.math.abs

@Composable
fun DashboardScreen(
    telemetry: TelemetrySample,
    stats: SessionStats,
    sampleRate: SampleRateMode,
    isPaused: Boolean,
    onSelectRate: (SampleRateMode) -> Unit,
    onTogglePause: () -> Unit,
    onResetStats: () -> Unit,
    onExportCsv: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Primary Digital Meter Display (KM003C Style)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, BorderSubtle),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BigMeterRow(
                    label = "VBUS",
                    value = String.format(Locale.US, "%7.4f", telemetry.vbusV),
                    unit = "V",
                    accent = VoltYellow,
                    sub = String.format(Locale.US, "AVG %.4fV  MAX %.3fV", telemetry.vbusAvgV, stats.maxVbusV)
                )
                BigMeterRow(
                    label = if (telemetry.ibusA >= 0) "IBUS →" else "IBUS ←",
                    value = String.format(Locale.US, "%7.4f", abs(telemetry.ibusA)),
                    unit = "A",
                    accent = AmpCyan,
                    sub = String.format(Locale.US, "AVG %.4fA  MAX %.3fA", abs(telemetry.ibusAvgA), stats.maxIbusA)
                )
                BigMeterRow(
                    label = "POWER",
                    value = String.format(Locale.US, "%7.4f", telemetry.powerW),
                    unit = "W",
                    accent = PowerOrange,
                    sub = String.format(Locale.US, "MAX %.3fW  •  %.1f°C", stats.maxPowerW, telemetry.tempC)
                )
            }
        }

        // Accumulation Card (Capacity, Energy, Time)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniStatCard(
                modifier = Modifier.weight(1f),
                label = "용량 (Capacity)",
                value = String.format(Locale.US, "%.2f", stats.capacityMah),
                unit = "mAh",
                color = EnergyGreen
            )
            MiniStatCard(
                modifier = Modifier.weight(1f),
                label = "에너지 (Energy)",
                value = String.format(Locale.US, "%.2f", stats.energyMwh),
                unit = "mWh",
                color = EnergyGreen
            )
            MiniStatCard(
                modifier = Modifier.weight(1f),
                label = "경과 시간",
                value = formatElapsed(stats.elapsedSec),
                unit = "",
                color = TextPrimary
            )
        }

        // Auxiliary Pins (D+, D-, CC1, CC2, Temp, VDD)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, BorderSubtle),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "USB 데이터 / CC 라인 & 온도",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PinChip(Modifier.weight(1f), "D+", String.format(Locale.US, "%.3f V", telemetry.dpV))
                    PinChip(Modifier.weight(1f), "D-", String.format(Locale.US, "%.3f V", telemetry.dmV))
                    PinChip(Modifier.weight(1f), "TEMP", String.format(Locale.US, "%.1f °C", telemetry.tempC))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PinChip(Modifier.weight(1f), "CC1", String.format(Locale.US, "%.3f V", telemetry.cc1V))
                    PinChip(Modifier.weight(1f), "CC2", String.format(Locale.US, "%.3f V", telemetry.cc2V))
                    PinChip(Modifier.weight(1f), "VDD", String.format(Locale.US, "%.2f V", telemetry.vddV))
                }
            }
        }

        // Sample Rate Selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, BorderSubtle),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "샘플링 속도 (AdcQueue 스트리밍)",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SampleRateMode.entries.forEach { mode ->
                        FilterChip(
                            selected = sampleRate == mode,
                            onClick = { onSelectRate(mode) },
                            label = { Text(mode.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VoltYellow.copy(alpha = 0.2f),
                                selectedLabelColor = VoltYellow
                            )
                        )
                    }
                }
            }
        }

        // Action Buttons (Pause, Reset, CSV Export)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onTogglePause,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isPaused) "재개" else "일시정지", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onResetStats,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("초기화", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onExportCsv,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, EnergyGreen.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = EnergyGreen)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("CSV", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun BigMeterRow(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    sub: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = accent.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value.trim(),
                    color = accent,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-1).sp
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = unit,
                    color = accent.copy(alpha = 0.8f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
        }
        Text(
            text = sub,
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Composable
private fun MiniStatCard(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, BorderSubtle),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, color = TextSecondary, fontSize = 10.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (unit.isNotEmpty()) "$value $unit" else value,
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun PinChip(modifier: Modifier, name: String, value: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(name, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun formatElapsed(sec: Double): String {
    val total = sec.toLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
