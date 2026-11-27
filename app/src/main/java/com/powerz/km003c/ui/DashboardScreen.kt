package com.powerz.km003c.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.powerz.km003c.model.GraphPoint
import com.powerz.km003c.model.RecordingConfig
import com.powerz.km003c.model.RecordingState
import com.powerz.km003c.model.RecordingStatus
import com.powerz.km003c.model.SampleRateMode
import com.powerz.km003c.model.SessionStats
import com.powerz.km003c.model.TelemetrySample
import com.powerz.km003c.ui.components.PzCard
import com.powerz.km003c.ui.components.PzInfoPill
import com.powerz.km003c.ui.components.PzLabel
import com.powerz.km003c.ui.components.PzPrimaryButton
import com.powerz.km003c.ui.components.PzProgressArc
import com.powerz.km003c.ui.components.PzRingGauge
import com.powerz.km003c.ui.components.PzSecondaryButton
import com.powerz.km003c.ui.components.PzSectionHeader
import com.powerz.km003c.ui.components.PzSparkline
import com.powerz.km003c.ui.components.PzStatCell
import com.powerz.km003c.ui.components.PzStatusPill
import com.powerz.km003c.ui.components.PzValue
import com.powerz.km003c.ui.theme.Aux
import com.powerz.km003c.ui.theme.BgBottom
import com.powerz.km003c.ui.theme.Brand
import com.powerz.km003c.ui.theme.Danger
import com.powerz.km003c.ui.theme.Energy
import com.powerz.km003c.ui.theme.Ibus
import com.powerz.km003c.ui.theme.Ink
import com.powerz.km003c.ui.theme.InkMuted
import com.powerz.km003c.ui.theme.Ok
import com.powerz.km003c.ui.theme.Power
import com.powerz.km003c.ui.theme.PzType
import com.powerz.km003c.ui.theme.Vbus
import com.powerz.km003c.ui.theme.Warn
import java.util.Locale
import kotlin.math.abs

private const val SPARK_POINTS = 120
private const val TEMP_WARN_C = 60.0

/** Standard USB PD power levels — ring scale snaps to the next one above peak. */
private val POWER_SCALES = doubleArrayOf(5.0, 10.0, 18.0, 27.0, 45.0, 65.0, 100.0, 140.0, 180.0, 240.0)

internal fun powerScaleFor(peakW: Double): Double =
    POWER_SCALES.firstOrNull { it >= peakW } ?: POWER_SCALES.last()

@Composable
fun DashboardScreen(
    telemetry: TelemetrySample,
    stats: SessionStats,
    livePoints: List<GraphPoint>,
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
    val recent = remember(livePoints) { livePoints.takeLast(SPARK_POINTS) }
    val isActive = recordingStatus.state == RecordingState.RECORDING ||
        recordingStatus.state == RecordingState.ARMED_WAITING_TRIGGER

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            StatusStrip(recordingStatus, sampleRate, isPaused, telemetry.tempC)

            PowerHero(telemetry = telemetry, stats = stats, recording = recordingStatus.state == RecordingState.RECORDING)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "VBUS",
                    value = String.format(Locale.US, "%.3f", telemetry.vbusV),
                    unit = "V",
                    color = Vbus,
                    caption = String.format(Locale.US, "AVG %.2f · PEAK %.2f", telemetry.vbusAvgV, stats.maxVbusV),
                    spark = recent.map { it.vbusV }
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = if (telemetry.ibusA >= 0) "IBUS  →" else "IBUS  ←",
                    value = String.format(Locale.US, "%.3f", abs(telemetry.ibusA)),
                    unit = "A",
                    color = Ibus,
                    caption = String.format(Locale.US, "AVG %.2f · PEAK %.2f", abs(telemetry.ibusAvgA), stats.maxIbusA),
                    spark = recent.map { abs(it.ibusA) }
                )
            }

            PzCard(modifier = Modifier.fillMaxWidth()) {
                Row {
                    PzStatCell("용량", String.format(Locale.US, "%.1f", stats.capacityMah), "mAh", Energy)
                    PzStatCell("에너지", String.format(Locale.US, "%.3f", stats.energyMwh / 1000.0), "Wh", Energy)
                    PzStatCell("경과", formatElapsed(stats.elapsedSec))
                }
            }

            RecordingSection(
                config = recordingConfig,
                status = recordingStatus,
                isActive = isActive,
                onOpenSettings = onOpenRecordingSettings,
                onSavePng = onSavePng,
                onSaveCsv = onSaveCsv,
                onShareCsv = onShareCsv,
                onOpenChartTab = onOpenChartTab
            )

            AuxSection(telemetry)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PzSecondaryButton(
                    text = if (isPaused) "재개" else "일시정지",
                    icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    onClick = onTogglePause,
                    modifier = Modifier.weight(1f)
                )
                PzSecondaryButton(
                    text = "통계 리셋",
                    icon = Icons.Default.Refresh,
                    onClick = onResetStats,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // Fixed bottom CTA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgBottom)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            PzPrimaryButton(
                text = when (recordingStatus.state) {
                    RecordingState.RECORDING -> "측정 종료 및 저장"
                    RecordingState.ARMED_WAITING_TRIGGER -> "시작 대기 취소"
                    RecordingState.COMPLETED -> "새 측정 시작"
                    RecordingState.IDLE -> "측정 기록 시작"
                },
                icon = if (isActive) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                onClick = if (isActive) onStopRecording else onStartRecording,
                container = if (isActive) Danger else Brand,
                content = if (isActive) Ink else com.powerz.km003c.ui.theme.InkOnBrand,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatusStrip(status: RecordingStatus, sampleRate: SampleRateMode, isPaused: Boolean, tempC: Double) {
    val pulse = rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recAlpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (status.state) {
            RecordingState.RECORDING -> PzStatusPill("REC", Danger, dot = true, modifier = Modifier.alpha(pulse.value))
            RecordingState.ARMED_WAITING_TRIGGER -> PzStatusPill("대기", Warn, dot = true)
            RecordingState.COMPLETED -> PzStatusPill("저장됨", Ok)
            RecordingState.IDLE -> {}
        }
        if (isPaused) PzStatusPill("일시정지", Warn)
        if (tempC >= TEMP_WARN_C) PzStatusPill(String.format(Locale.US, "고온 %.0f°C", tempC), Warn)
        Spacer(Modifier.weight(1f))
        PzInfoPill(sampleRate.label)
    }
}

@Composable
private fun PowerHero(telemetry: TelemetrySample, stats: SessionStats, recording: Boolean) {
    val scale = powerScaleFor(maxOf(stats.maxPowerW, telemetry.powerW))
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PzRingGauge(
            progress = (telemetry.powerW / scale).toFloat(),
            color = Power,
            glow = recording,
            strokeWidth = 14.dp,
            modifier = Modifier.fillMaxWidth(0.74f)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PzLabel("Power", color = Power)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = formatPower(telemetry.powerW),
                    style = PzType.heroNumber,
                    color = Ink
                )
                Text("W", style = PzType.bodyLg, color = InkMuted)
            }
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            HeroFootnote("Peak", String.format(Locale.US, "%.1f W", stats.maxPowerW))
            HeroFootnote("Scale", String.format(Locale.US, "%.0f W", scale))
        }
    }
}

@Composable
private fun HeroFootnote(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PzLabel(label, color = com.powerz.km003c.ui.theme.InkFaint)
        Spacer(Modifier.width(6.dp))
        Text(value, style = PzType.metricSm.copy(fontSize = PzType.bodySm.fontSize), color = InkMuted)
    }
}

/** Keep ~4 significant digits so the hero number never changes width wildly. */
private fun formatPower(w: Double): String = when {
    w >= 100 -> String.format(Locale.US, "%.1f", w)
    w >= 10 -> String.format(Locale.US, "%.2f", w)
    else -> String.format(Locale.US, "%.3f", w)
}

@Composable
private fun MetricTile(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String,
    color: androidx.compose.ui.graphics.Color,
    caption: String,
    spark: List<Float>
) {
    PzCard(modifier = modifier, padding = PaddingValues(16.dp)) {
        PzLabel(label, color = color)
        Spacer(Modifier.height(10.dp))
        PzValue(value = value, unit = unit, color = color, style = PzType.metric.copy(fontSize = PzType.metric.fontSize * 1.25f))
        Spacer(Modifier.height(10.dp))
        PzSparkline(values = spark, color = color, modifier = Modifier.fillMaxWidth().height(32.dp))
        Spacer(Modifier.height(8.dp))
        Text(caption, style = PzType.caption, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RecordingSection(
    config: RecordingConfig,
    status: RecordingStatus,
    isActive: Boolean,
    onOpenSettings: () -> Unit,
    onSavePng: () -> Unit,
    onSaveCsv: () -> Unit,
    onShareCsv: () -> Unit,
    onOpenChartTab: () -> Unit
) {
    PzSectionHeader("기록") {
        IconButton(onClick = onOpenSettings, enabled = !isActive) {
            Icon(Icons.Default.Tune, contentDescription = "조건 설정", tint = if (isActive) com.powerz.km003c.ui.theme.InkFaint else Ink)
        }
    }
    PzCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = when (status.state) {
                RecordingState.RECORDING -> status.sessionName
                RecordingState.ARMED_WAITING_TRIGGER -> "전류 감지 대기 중"
                RecordingState.COMPLETED -> status.sessionName
                RecordingState.IDLE -> "새 측정 세션"
            },
            style = PzType.bodyLg,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "간격 ${config.intervalLabel()} · ${config.summaryConditions()}",
            style = PzType.caption,
            color = InkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (status.state != RecordingState.IDLE) {
            Spacer(Modifier.height(20.dp))
            Row {
                PzStatCell("시간", formatElapsed(status.elapsedSec), style = PzType.metricSm)
                PzStatCell("샘플", "${status.sampleCount}", style = PzType.metricSm)
                PzStatCell("용량", String.format(Locale.US, "%.1f", status.capacityMah), "mAh", Energy, PzType.metricSm)
                PzStatCell("에너지", String.format(Locale.US, "%.2f", status.energyMwh / 1000.0), "Wh", Energy, PzType.metricSm)
            }
        }

        val holdLabel = status.holdConditionLabel
        if (holdLabel != null) {
            Spacer(Modifier.height(20.dp))
            PzProgressArc(
                progress = status.holdProgress,
                color = Energy,
                modifier = Modifier.fillMaxWidth(0.6f).align(Alignment.CenterHorizontally)
            ) {
                Text(
                    String.format(Locale.US, "%.0f%%", status.holdProgress * 100),
                    style = PzType.metric,
                    color = Ink
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                holdLabel,
                style = PzType.caption,
                color = InkMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        if (status.state == RecordingState.COMPLETED) {
            status.stopReason?.let {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Ok))
                    Spacer(Modifier.width(8.dp))
                    Text("$it · 자동 저장됨", style = PzType.bodySm, color = Ink)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PzSecondaryButton("PNG", onSavePng, Modifier.weight(1f), Icons.Default.Image)
                PzSecondaryButton("CSV", onSaveCsv, Modifier.weight(1f), Icons.Default.SaveAlt)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PzSecondaryButton("공유", onShareCsv, Modifier.weight(1f), Icons.Default.Share)
                PzSecondaryButton("그래프", onOpenChartTab, Modifier.weight(1f), Icons.AutoMirrored.Filled.ShowChart)
            }
        }
    }
}

@Composable
private fun AuxSection(t: TelemetrySample) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    PzSectionHeader(
        "신호선 · 센서",
        modifier = Modifier.clickable { expanded = !expanded }
    ) {
        Text(
            String.format(Locale.US, "%.1f°C", t.tempC),
            style = PzType.bodySm,
            color = if (t.tempC >= TEMP_WARN_C) Warn else InkMuted
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "접기" else "펼치기",
            tint = InkMuted
        )
    }
    AnimatedVisibility(visible = expanded) {
        PzCard(modifier = Modifier.fillMaxWidth()) {
            Row {
                PzStatCell("D+", String.format(Locale.US, "%.3f", t.dpV), "V", Aux, PzType.metricSm)
                PzStatCell("D−", String.format(Locale.US, "%.3f", t.dmV), "V", Aux, PzType.metricSm)
                PzStatCell("VDD", String.format(Locale.US, "%.2f", t.vddV), "V", Aux, PzType.metricSm)
            }
            Spacer(Modifier.height(20.dp))
            Row {
                PzStatCell("CC1", String.format(Locale.US, "%.3f", t.cc1V), "V", Aux, PzType.metricSm)
                PzStatCell("CC2", String.format(Locale.US, "%.3f", t.cc2V), "V", Aux, PzType.metricSm)
                PzStatCell("TEMP", String.format(Locale.US, "%.1f", t.tempC), "°C", if (t.tempC >= TEMP_WARN_C) Warn else Aux, PzType.metricSm)
            }
        }
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
