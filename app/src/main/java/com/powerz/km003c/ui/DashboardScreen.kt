package com.powerz.km003c.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerz.km003c.model.RecordingConfig
import com.powerz.km003c.model.RecordingState
import com.powerz.km003c.model.RecordingStatus
import com.powerz.km003c.model.SampleRateMode
import com.powerz.km003c.model.SessionStats
import com.powerz.km003c.model.TelemetrySample
import com.powerz.km003c.ui.theme.AmpCyan
import com.powerz.km003c.ui.theme.BorderHighlight
import com.powerz.km003c.ui.theme.BorderSubtle
import com.powerz.km003c.ui.theme.EnergyGreen
import com.powerz.km003c.ui.theme.PdPurple
import com.powerz.km003c.ui.theme.PowerOrange
import com.powerz.km003c.ui.theme.RecordRed
import com.powerz.km003c.ui.theme.SurfaceCard
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
    recordingConfig: RecordingConfig,
    recordingStatus: RecordingStatus,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onOpenRecordingSettings: () -> Unit,
    onTogglePause: () -> Unit,
    onResetStats: () -> Unit,
    onSavePng: () -> Unit,
    onSaveCsv: () -> Unit,
    onShareCsv: () -> Unit,
    onOpenChartTab: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Hero Precision Digital Meter Display
        HeroMeterCard(telemetry = telemetry, stats = stats, sampleRate = sampleRate)

        // 2. Upgraded Recording Control Deck (Start/Stop + Interval + Conditional Auto-Stop)
        RecordingControlDeck(
            config = recordingConfig,
            status = recordingStatus,
            onStart = onStartRecording,
            onStop = onStopRecording,
            onOpenSettings = onOpenRecordingSettings,
            onSavePng = onSavePng,
            onSaveCsv = onSaveCsv,
            onShareCsv = onShareCsv,
            onOpenChartTab = onOpenChartTab
        )

        // 3. Session Accumulation Cards (mAh / Wh / Time)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricPillCard(
                modifier = Modifier.weight(1f),
                label = "용량",
                value = String.format(Locale.US, "%.1f", stats.capacityMah),
                unit = "mAh",
                accent = EnergyGreen
            )
            MetricPillCard(
                modifier = Modifier.weight(1f),
                label = "에너지",
                value = String.format(Locale.US, "%.3f", stats.energyMwh / 1000.0),
                unit = "Wh",
                accent = PdPurple
            )
            MetricPillCard(
                modifier = Modifier.weight(1f),
                label = "경과 시간",
                value = formatElapsed(stats.elapsedSec),
                unit = "",
                accent = TextPrimary
            )
        }

        // 4. Auxiliary Lines & Sensor Grid (D+, D-, CC1, CC2, TEMP, VDD)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderSubtle),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "USB-C 신호선 & 센서",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = sampleRate.label,
                        color = AmpCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AmpCyan.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PinTile(Modifier.weight(1f), "D+", String.format(Locale.US, "%.3fV", telemetry.dpV), VoltYellow)
                    PinTile(Modifier.weight(1f), "D-", String.format(Locale.US, "%.3fV", telemetry.dmV), AmpCyan)
                    PinTile(Modifier.weight(1f), "TEMP", String.format(Locale.US, "%.1f°C", telemetry.tempC), PowerOrange)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PinTile(Modifier.weight(1f), "CC1", String.format(Locale.US, "%.3fV", telemetry.cc1V), EnergyGreen)
                    PinTile(Modifier.weight(1f), "CC2", String.format(Locale.US, "%.3fV", telemetry.cc2V), PdPurple)
                    PinTile(Modifier.weight(1f), "VDD", String.format(Locale.US, "%.2fV", telemetry.vddV), TextSecondary)
                }
            }
        }

        // 5. Quick Live Controls (Pause / Reset / Quick PNG & CSV)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onTogglePause,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderHighlight)
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isPaused) "재개" else "일시정지", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onResetStats,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderHighlight)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("통계 리셋", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onSavePng,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, EnergyGreen.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = EnergyGreen)
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("PNG 저장", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun HeroMeterCard(
    telemetry: TelemetrySample,
    stats: SessionStats,
    sampleRate: SampleRateMode
) {
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            VoltYellow.copy(alpha = 0.45f),
            AmpCyan.copy(alpha = 0.35f),
            PowerOrange.copy(alpha = 0.45f)
        )
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.2.dp, borderBrush),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF182234), SurfaceCard)
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeroChannelRow(
                label = "VBUS",
                badge = "VOLTAGE",
                value = String.format(Locale.US, "%.4f", telemetry.vbusV),
                unit = "V",
                accent = VoltYellow,
                progress = (telemetry.vbusV / 28.0).toFloat().coerceIn(0f, 1f),
                sub = String.format(Locale.US, "AVG %.3fV  •  PEAK %.3fV", telemetry.vbusAvgV, stats.maxVbusV)
            )
            HeroChannelRow(
                label = "IBUS",
                badge = if (telemetry.ibusA >= 0) "FLOW →" else "FLOW ←",
                value = String.format(Locale.US, "%.4f", abs(telemetry.ibusA)),
                unit = "A",
                accent = AmpCyan,
                progress = (abs(telemetry.ibusA) / 5.0).toFloat().coerceIn(0f, 1f),
                sub = String.format(Locale.US, "AVG %.3fA  •  PEAK %.3fA", abs(telemetry.ibusAvgA), stats.maxIbusA)
            )
            HeroChannelRow(
                label = "POWER",
                badge = sampleRate.label.uppercase(),
                value = String.format(Locale.US, "%.3f", telemetry.powerW),
                unit = "W",
                accent = PowerOrange,
                progress = (telemetry.powerW / 140.0).toFloat().coerceIn(0f, 1f),
                sub = String.format(Locale.US, "PEAK %.2fW  •  TEMP %.1f°C", stats.maxPowerW, telemetry.tempC)
            )
        }
    }
}

@Composable
private fun HeroChannelRow(
    label: String,
    badge: String,
    value: String,
    unit: String,
    accent: Color,
    progress: Float,
    sub: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = label,
                    color = accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = badge,
                    color = accent.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = accent,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-1).sp
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = unit,
                    color = accent.copy(alpha = 0.8f),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = accent,
            trackColor = SurfaceElevated
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = sub,
            color = TextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

@Composable
private fun RecordingControlDeck(
    config: RecordingConfig,
    status: RecordingStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onSavePng: () -> Unit,
    onSaveCsv: () -> Unit,
    onShareCsv: () -> Unit,
    onOpenChartTab: () -> Unit
) {
    val isActive = status.state == RecordingState.RECORDING || status.state == RecordingState.ARMED_WAITING_TRIGGER
    val pulse = rememberInfiniteTransition(label = "rec_pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "rec_alpha"
    )

    val cardBorderColor = when (status.state) {
        RecordingState.RECORDING -> RecordRed.copy(alpha = 0.75f)
        RecordingState.ARMED_WAITING_TRIGGER -> VoltYellow.copy(alpha = 0.75f)
        RecordingState.COMPLETED -> EnergyGreen.copy(alpha = 0.65f)
        RecordingState.IDLE -> BorderSubtle
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.2.dp, cardBorderColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top header: Title + interval/condition summary + Tune button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    val dotColor = when (status.state) {
                        RecordingState.RECORDING -> RecordRed
                        RecordingState.ARMED_WAITING_TRIGGER -> VoltYellow
                        RecordingState.COMPLETED -> EnergyGreen
                        RecordingState.IDLE -> TextSecondary
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .alpha(if (isActive) pulse.value else 1f)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = when (status.state) {
                                RecordingState.RECORDING -> "측정 기록 중 • ${status.sessionName}"
                                RecordingState.ARMED_WAITING_TRIGGER -> "전류 감지 대기 중..."
                                RecordingState.COMPLETED -> "측정 완료 • ${status.sessionName}"
                                RecordingState.IDLE -> "측정 기록 세션"
                            },
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "간격 ${config.intervalLabel()}  |  ${config.summaryConditions()}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onOpenSettings,
                    enabled = !isActive,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isActive) BorderSubtle else VoltYellow.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isActive) TextSecondary else VoltYellow)
                    Spacer(Modifier.width(4.dp))
                    Text("조건 설정", fontSize = 12.sp, color = if (isActive) TextSecondary else VoltYellow)
                }
            }

            // Live REC Stats strip when active or completed
            if (status.state != RecordingState.IDLE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MiniRecStat("시간", formatElapsed(status.elapsedSec), TextPrimary)
                    MiniRecStat("샘플", "${status.sampleCount} pts", AmpCyan)
                    MiniRecStat("용량", String.format(Locale.US, "%.1fmAh", status.capacityMah), EnergyGreen)
                    MiniRecStat("에너지", String.format(Locale.US, "%.2fWh", status.energyMwh / 1000.0), PowerOrange)
                }
            }

            // Auto-stop / Auto-start hold progress bar
            if (status.holdConditionLabel != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = status.holdConditionLabel,
                        color = VoltYellow,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    LinearProgressIndicator(
                        progress = { status.holdProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = VoltYellow,
                        trackColor = SurfaceElevated
                    )
                }
            }

            // Completed stop reason & instant export actions
            if (status.state == RecordingState.COMPLETED) {
                if (status.stopReason != null) {
                    Text(
                        text = "✓ ${status.stopReason} (자동 저장 완료)",
                        color = EnergyGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onSavePng,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, VoltYellow.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp), tint = VoltYellow)
                        Spacer(Modifier.width(4.dp))
                        Text("PNG", fontSize = 11.sp, color = VoltYellow)
                    }
                    OutlinedButton(
                        onClick = onSaveCsv,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AmpCyan.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(15.dp), tint = AmpCyan)
                        Spacer(Modifier.width(4.dp))
                        Text("CSV", fontSize = 11.sp, color = AmpCyan)
                    }
                    OutlinedButton(
                        onClick = onShareCsv,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, EnergyGreen.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = EnergyGreen)
                        Spacer(Modifier.width(4.dp))
                        Text("공유", fontSize = 11.sp, color = EnergyGreen)
                    }
                    OutlinedButton(
                        onClick = onOpenChartTab,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PdPurple.copy(alpha = 0.6f))
                    ) {
                        Text("그래프", fontSize = 11.sp, color = PdPurple)
                    }
                }
            }

            // Primary Start / Stop CTA button
            Button(
                onClick = if (isActive) onStop else onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) RecordRed else EnergyGreen,
                    contentColor = if (isActive) Color.White else Color(0xFF06120B)
                )
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (status.state) {
                        RecordingState.RECORDING -> "측정 종료 및 저장"
                        RecordingState.ARMED_WAITING_TRIGGER -> "시작 대기 취소"
                        RecordingState.COMPLETED -> "새 측정 시작"
                        RecordingState.IDLE -> "측정 기록 시작"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MiniRecStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextSecondary, fontSize = 10.sp)
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun MetricPillCard(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String,
    accent: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, BorderSubtle),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            Text(label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(3.dp))
                    Text(unit, color = accent.copy(alpha = 0.75f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 1.dp))
                }
            }
        }
    }
}

@Composable
private fun PinTile(modifier: Modifier, name: String, value: String, accent: Color) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceElevated)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(name, color = accent.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
    }
}

internal fun formatElapsed(sec: Double): String {
    val total = sec.toLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
