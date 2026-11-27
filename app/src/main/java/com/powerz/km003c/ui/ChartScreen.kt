package com.powerz.km003c.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.powerz.km003c.model.GraphPoint
import com.powerz.km003c.model.SavedSession
import com.powerz.km003c.model.SavedSessionSummary
import com.powerz.km003c.model.TelemetrySample
import com.powerz.km003c.ui.components.PzCard
import com.powerz.km003c.ui.components.PzChip
import com.powerz.km003c.ui.components.PzEmptyState
import com.powerz.km003c.ui.components.PzLabel
import com.powerz.km003c.ui.components.PzSecondaryButton
import com.powerz.km003c.ui.components.PzStatCell
import com.powerz.km003c.ui.theme.Border
import com.powerz.km003c.ui.theme.Brand
import com.powerz.km003c.ui.theme.Danger
import com.powerz.km003c.ui.theme.Energy
import com.powerz.km003c.ui.theme.Ibus
import com.powerz.km003c.ui.theme.IbusText
import com.powerz.km003c.ui.theme.Ink
import com.powerz.km003c.ui.theme.InkFaint
import com.powerz.km003c.ui.theme.InkMuted
import com.powerz.km003c.ui.theme.NumbersFont
import com.powerz.km003c.ui.theme.Panel
import com.powerz.km003c.ui.theme.PanelStrong
import com.powerz.km003c.ui.theme.Power
import com.powerz.km003c.ui.theme.PzRadius
import com.powerz.km003c.ui.theme.PzType
import com.powerz.km003c.ui.theme.Vbus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun ChartScreen(
    telemetry: TelemetrySample,
    livePoints: List<GraphPoint>,
    recordingPoints: List<GraphPoint>,
    savedSessions: List<SavedSessionSummary>,
    viewingSavedSession: SavedSession?,
    onOpenSavedSession: (String) -> Unit,
    onCloseSavedSession: () -> Unit,
    onDeleteSavedSession: (String) -> Unit,
    onSavePng: (SavedSession?, Boolean, Boolean, Boolean) -> Unit,
    onSaveCsv: (SavedSession?) -> Unit,
    onSaveJson: (SavedSession?) -> Unit,
    onShare: (String, SavedSession?, Boolean, Boolean, Boolean) -> Unit
) {
    // 0 = Live, 1 = Current Recording, 2 = Saved Sessions List
    var modeTab by remember { mutableIntStateOf(0) }
    var showV by remember { mutableStateOf(true) }
    var showI by remember { mutableStateOf(true) }
    var showW by remember { mutableStateOf(true) }
    var windowSec by remember { mutableDoubleStateOf(30.0) }
    var cursorFraction by remember { mutableStateOf<Float?>(null) }

    val isViewingArchive = viewingSavedSession != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(0.dp))

        if (isViewingArchive) {
            ArchiveHeader(
                summary = viewingSavedSession!!.summary,
                onClose = {
                    cursorFraction = null
                    onCloseSavedSession()
                }
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    0 to "실시간",
                    1 to "현재 기록 ${recordingPoints.size}",
                    2 to "저장 목록 ${savedSessions.size}"
                ).forEach { (idx, label) ->
                    PzChip(
                        text = label,
                        selected = modeTab == idx,
                        onClick = {
                            modeTab = idx
                            cursorFraction = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (!isViewingArchive && modeTab == 2) {
            SavedSessionsList(
                sessions = savedSessions,
                onOpen = { id ->
                    cursorFraction = null
                    onOpenSavedSession(id)
                },
                onDelete = onDeleteSavedSession
            )
            return
        }

        val sourcePoints = remember(isViewingArchive, viewingSavedSession, modeTab, livePoints, recordingPoints) {
            when {
                isViewingArchive -> viewingSavedSession!!.samples.map { it.toGraphPoint() }
                modeTab == 1 -> recordingPoints
                else -> livePoints
            }
        }
        val latestTime = sourcePoints.lastOrNull()?.timeSec ?: 0.0
        val effectiveWindow = if (isViewingArchive || modeTab == 1) 0.0 else windowSec
        val visible = remember(sourcePoints, effectiveWindow) {
            if (effectiveWindow <= 0.0) sourcePoints
            else sourcePoints.filter { it.timeSec >= latestTime - effectiveWindow }
        }
        val inspectedPoint = remember(visible, cursorFraction) {
            val frac = cursorFraction
            if (frac == null || visible.isEmpty()) null
            else visible[(frac * (visible.size - 1)).toInt().coerceIn(0, visible.lastIndex)]
        }

        // Readout: live values, or the point under the crosshair
        Readout(
            v = inspectedPoint?.vbusV?.toDouble() ?: telemetry.vbusV,
            a = inspectedPoint?.ibusA?.toDouble() ?: abs(telemetry.ibusA),
            w = inspectedPoint?.powerW?.toDouble() ?: telemetry.powerW,
            timeSec = inspectedPoint?.timeSec,
            onClearCursor = { cursorFraction = null }
        )

        // Legend toggles + time window
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PzChip("V", showV, { showV = !showV }, accent = Vbus)
            PzChip("A", showI, { showI = !showI }, accent = Ibus)
            PzChip("W", showW, { showW = !showW }, accent = Power)
            Spacer(Modifier.weight(1f))
            if (!isViewingArchive && modeTab == 0) {
                listOf(10.0 to "10s", 30.0 to "30s", 60.0 to "1m", 0.0 to "ALL").forEach { (sec, label) ->
                    WindowTab(label, windowSec == sec) { windowSec = sec }
                }
            }
        }

        // Chart — full width, no card
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (visible.size < 2) {
                PzEmptyState(
                    title = if (modeTab == 1) "기록된 샘플이 없어요" else "데이터 수신 대기 중",
                    body = if (modeTab == 1) "측정 탭에서 기록을 시작하세요." else "장치를 연결하거나 시뮬레이터를 켜세요.",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LineChart(
                    visible = visible,
                    showV = showV,
                    showI = showI,
                    showW = showW,
                    cursorFraction = cursorFraction,
                    inspectedPoint = inspectedPoint,
                    onCursor = { cursorFraction = it }
                )
            }
        }

        // Export
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PzSecondaryButton("PNG", { onSavePng(viewingSavedSession, showV, showI, showW) }, Modifier.weight(1f), Icons.Default.Image)
            PzSecondaryButton("CSV", { onSaveCsv(viewingSavedSession) }, Modifier.weight(1f), Icons.Default.SaveAlt)
            PzSecondaryButton("JSON", { onSaveJson(viewingSavedSession) }, Modifier.weight(1f), Icons.Outlined.DataObject)
            PzSecondaryButton("공유", { onShare("PNG", viewingSavedSession, showV, showI, showW) }, Modifier.weight(1f), Icons.Default.Share)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ArchiveHeader(summary: SavedSessionSummary, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            PzLabel("저장된 기록", color = Brand)
            Spacer(Modifier.height(4.dp))
            Text(summary.name, style = PzType.bodyLg, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatElapsed(summary.durationSec)} · ${summary.sampleCount} pts · ${summary.stopReason}",
                style = PzType.caption,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "닫기", tint = Ink)
        }
    }
}

@Composable
private fun Readout(v: Double, a: Double, w: Double, timeSec: Double?, onClearCursor: () -> Unit) {
    PzCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PzStatCell("VBUS", String.format(Locale.US, "%.3f", v), "V", Vbus, PzType.metricSm)
            PzStatCell("IBUS", String.format(Locale.US, "%.3f", a), "A", IbusText, PzType.metricSm)
            PzStatCell("POWER", String.format(Locale.US, "%.2f", w), "W", Power, PzType.metricSm)
        }
        if (timeSec != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    String.format(Locale.US, "커서 %.1fs", timeSec),
                    style = PzType.caption,
                    color = InkMuted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "실시간으로",
                    style = PzType.caption,
                    color = Brand,
                    modifier = Modifier.clickable(onClick = onClearCursor)
                )
            }
        }
    }
}

@Composable
private fun WindowTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = PzType.label,
        color = if (selected) Ink else InkFaint,
        modifier = Modifier
            .clip(RoundedCornerShape(PzRadius.sm))
            .background(if (selected) PanelStrong else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
private fun LineChart(
    visible: List<GraphPoint>,
    showV: Boolean,
    showI: Boolean,
    showW: Boolean,
    cursorFraction: Float?,
    inspectedPoint: GraphPoint?,
    onCursor: (Float?) -> Unit
) {
    val rV = autoRange(visible.map { it.vbusV }, 0.5f)
    val rI = autoRange(visible.map { it.ibusA }, 0.2f)
    val rW = autoRange(visible.map { it.powerW }, 2f)
    // Only one channel gets an area fill — the "primary" visible one (W > A > V)
    val fillChannel = when { showW -> 'W'; showI -> 'I'; else -> 'V' }
    val minT = visible.first().timeSec
    val maxT = visible.last().timeSec

    Column(modifier = Modifier.fillMaxSize()) {
        // Per-channel scale (top of chart)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showV) AxisMax(String.format(Locale.US, "%.1f–%.1fV", rV.start, rV.endInclusive), Vbus)
            if (showI) AxisMax(String.format(Locale.US, "%.2f–%.2fA", rI.start, rI.endInclusive), IbusText)
            if (showW) AxisMax(String.format(Locale.US, "%.1f–%.1fW", rW.start, rW.endInclusive), Power)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(visible) {
                    detectTapGestures(
                        onTap = { o -> onCursor((o.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)) },
                        onDoubleTap = { onCursor(null) }
                    )
                }
                .pointerInput(visible) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onCursor((change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f))
                    }
                }
        ) {
            val w = size.width
            val h = size.height

            // Horizontal grid only (4 lines)
            for (i in 0..3) {
                val y = h * i / 3f
                drawLine(Border, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }

            val spanT = (maxT - minT).coerceAtLeast(0.1)
            fun xOf(t: Double) = (((t - minT) / spanT) * w).toFloat().coerceIn(0f, w)

            fun yOf(v: Float, r: ClosedFloatingPointRange<Float>) =
                (h - ((v - r.start) / (r.endInclusive - r.start)) * h).coerceIn(0f, h)

            fun drawChannel(color: Color, r: ClosedFloatingPointRange<Float>, fillArea: Boolean, sel: (GraphPoint) -> Float) {
                val line = Path()
                val fill = Path()
                visible.forEachIndexed { i, p ->
                    val x = xOf(p.timeSec)
                    val y = yOf(sel(p), r)
                    if (i == 0) {
                        line.moveTo(x, y); fill.moveTo(x, h); fill.lineTo(x, y)
                    } else {
                        line.lineTo(x, y); fill.lineTo(x, y)
                    }
                }
                fill.lineTo(xOf(maxT), h)
                fill.close()
                if (fillArea) drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.14f), Color.Transparent)))
                drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            if (showW) drawChannel(Power, rW, fillChannel == 'W') { it.powerW }
            if (showI) drawChannel(Ibus, rI, fillChannel == 'I') { it.ibusA }
            if (showV) drawChannel(Vbus, rV, fillChannel == 'V') { it.vbusV }

            if (cursorFraction != null) {
                val cx = (cursorFraction * w).coerceIn(0f, w)
                drawLine(Ink.copy(alpha = 0.4f), Offset(cx, 0f), Offset(cx, h), strokeWidth = 1.dp.toPx())
                inspectedPoint?.let { p ->
                    val r = 4.dp.toPx()
                    fun dot(c: Color, v: Float, range: ClosedFloatingPointRange<Float>) {
                        val y = yOf(v, range)
                        drawCircle(Panel, r + 2.dp.toPx(), Offset(cx, y))
                        drawCircle(c, r, Offset(cx, y))
                    }
                    if (showW) dot(Power, p.powerW, rW)
                    if (showI) dot(Ibus, p.ibusA, rI)
                    if (showV) dot(Vbus, p.vbusV, rV)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AxisTime(minT)
            AxisTime((minT + maxT) / 2.0)
            AxisTime(maxT)
        }
    }
}

/** Min–max of the window with 20% padding; never narrower than [minSpan], never below 0. */
private fun autoRange(values: List<Float>, minSpan: Float): ClosedFloatingPointRange<Float> {
    val lo = values.min()
    val hi = values.max()
    val span = maxOf(hi - lo, minSpan)
    val mid = (hi + lo) / 2f
    val start = (mid - span * 0.6f).coerceAtLeast(0f)
    return start..(start + span * 1.2f)
}

@Composable
private fun AxisMax(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(2.dp).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, style = PzType.caption.copy(fontFamily = NumbersFont), color = InkMuted)
    }
}

@Composable
private fun AxisTime(sec: Double) {
    Text(
        String.format(Locale.US, "%.1fs", sec),
        style = PzType.caption.copy(fontFamily = NumbersFont),
        color = InkFaint
    )
}

@Composable
private fun SavedSessionsList(
    sessions: List<SavedSessionSummary>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    if (sessions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PzEmptyState(
                title = "저장된 기록이 없어요",
                body = "측정 탭에서 기록을 시작하고 종료하면\n여기에 자동으로 저장돼요."
            )
        }
        return
    }

    val dateFmt = remember { SimpleDateFormat("MM.dd HH:mm", Locale.US) }
    var pendingDelete by remember { mutableStateOf<SavedSessionSummary?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sessions, key = { it.id }) { s ->
            PzCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PzRadius.md))
                    .clickable { onOpen(s.id) },
                padding = PaddingValues(start = 16.dp, top = 16.dp, end = 4.dp, bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        PzLabel(dateFmt.format(Date(s.createdAtMs)))
                        Spacer(Modifier.height(4.dp))
                        Text(s.name, style = PzType.bodyLg, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { pendingDelete = s }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "삭제", tint = InkMuted)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.padding(end = 12.dp)) {
                    PzStatCell("용량", String.format(Locale.US, if (s.capacityMah < 100) "%.1f" else "%.0f", s.capacityMah), "mAh", Energy, PzType.metricSm)
                    PzStatCell("최대", String.format(Locale.US, "%.1f", s.maxPowerW), "W", Power, PzType.metricSm)
                    PzStatCell("시간", formatElapsed(s.durationSec), style = PzType.metricSm)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "${s.stopReason} · ${s.sampleCount} pts · 간격 ${if (s.intervalMs < 1000) "${s.intervalMs}ms" else "${s.intervalMs / 1000}s"}",
                    style = PzType.caption,
                    color = InkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    pendingDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = PanelStrong,
            shape = RoundedCornerShape(PzRadius.md),
            title = { Text("기록 삭제", style = PzType.bodyLg, color = Ink) },
            text = { Text("'${s.name}' 기록을 삭제할까요? 되돌릴 수 없어요.", style = PzType.body, color = InkMuted) },
            confirmButton = {
                TextButton(onClick = { onDelete(s.id); pendingDelete = null }) {
                    Text("삭제", style = PzType.body, color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("취소", style = PzType.body, color = Ink)
                }
            }
        )
    }
}
