package com.powerz.km003c.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerz.km003c.model.GraphPoint
import com.powerz.km003c.model.SavedSession
import com.powerz.km003c.model.SavedSessionSummary
import com.powerz.km003c.model.TelemetrySample
import com.powerz.km003c.ui.theme.AmpCyan
import com.powerz.km003c.ui.theme.BorderHighlight
import com.powerz.km003c.ui.theme.BorderSubtle
import com.powerz.km003c.ui.theme.EnergyGreen
import com.powerz.km003c.ui.theme.PdPurple
import com.powerz.km003c.ui.theme.PowerOrange
import com.powerz.km003c.ui.theme.SurfaceCard
import com.powerz.km003c.ui.theme.SurfaceElevated
import com.powerz.km003c.ui.theme.TextPrimary
import com.powerz.km003c.ui.theme.TextSecondary
import com.powerz.km003c.ui.theme.VoltYellow
import com.powerz.km003c.ui.theme.WarnRed
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

    // If a saved session is loaded for viewing, show it directly on the chart
    val isViewingArchive = viewingSavedSession != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Source Switcher Bar
        if (isViewingArchive) {
            val s = viewingSavedSession!!.summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = PdPurple.copy(alpha = 0.14f)),
                border = BorderStroke(1.dp, PdPurple.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "저장된 기록 보기: ${s.name}",
                            color = PdPurple,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${s.sampleCount} pts • ${formatElapsed(s.durationSec)} • ${String.format(Locale.US, "%.1fmAh", s.capacityMah)} • ${s.stopReason}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = {
                        cursorFraction = null
                        onCloseSavedSession()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "닫기", tint = TextPrimary)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    0 to "실시간",
                    1 to "현재 기록 (${recordingPoints.size})",
                    2 to "저장 목록 (${savedSessions.size})"
                ).forEach { (idx, label) ->
                    FilterChip(
                        selected = modeTab == idx,
                        onClick = {
                            modeTab = idx
                            cursorFraction = null
                        },
                        label = { Text(label, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VoltYellow.copy(alpha = 0.2f),
                            selectedLabelColor = VoltYellow
                        )
                    )
                }
            }
        }

        // Saved Sessions Archive List view
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

        // Determine active chart points
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

        // Inspected point (if user is touching/dragging cursor) or live readout
        val inspectedPoint = remember(visible, cursorFraction) {
            val frac = cursorFraction
            if (frac == null || visible.isEmpty()) null
            else {
                val idx = (frac * (visible.size - 1)).toInt().coerceIn(0, visible.lastIndex)
                visible[idx]
            }
        }

        // Live / Cursor Readout Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, if (inspectedPoint != null) VoltYellow.copy(alpha = 0.6f) else BorderSubtle),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dispV = inspectedPoint?.vbusV?.toDouble() ?: telemetry.vbusV
                val dispI = inspectedPoint?.ibusA?.toDouble() ?: abs(telemetry.ibusA)
                val dispW = inspectedPoint?.powerW?.toDouble() ?: telemetry.powerW

                Text(
                    text = String.format(Locale.US, "%.3fV", dispV),
                    color = VoltYellow,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = String.format(Locale.US, "%.3fA", dispI),
                    color = AmpCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = String.format(Locale.US, "%.2fW", dispW),
                    color = PowerOrange,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (inspectedPoint != null) {
                    Text(
                        text = String.format(Locale.US, "@%.1fs", inspectedPoint.timeSec),
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceElevated)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Channel toggles & Time window selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = showV,
                    onClick = { showV = !showV },
                    label = { Text("V", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VoltYellow.copy(alpha = 0.22f),
                        selectedLabelColor = VoltYellow
                    )
                )
                FilterChip(
                    selected = showI,
                    onClick = { showI = !showI },
                    label = { Text("A", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmpCyan.copy(alpha = 0.22f),
                        selectedLabelColor = AmpCyan
                    )
                )
                FilterChip(
                    selected = showW,
                    onClick = { showW = !showW },
                    label = { Text("W", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PowerOrange.copy(alpha = 0.22f),
                        selectedLabelColor = PowerOrange
                    )
                )
            }
            if (!isViewingArchive && modeTab == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(10.0 to "10s", 30.0 to "30s", 60.0 to "1m", 0.0 to "전체").forEach { (sec, label) ->
                        FilterChip(
                            selected = windowSec == sec,
                            onClick = { windowSec = sec },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // Interactive Oscilloscope Canvas with Gradient Fills + Touch Crosshair
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, BorderSubtle),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (visible.size < 2) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (modeTab == 1) "기록된 샘플이 없습니다.\n측정 탭에서 기록을 시작해 주세요." else "측정 데이터 수신 대기 중...",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                val maxV = (visible.maxOf { it.vbusV } * 1.15f).coerceAtLeast(6f)
                val maxI = (visible.maxOf { it.ibusA } * 1.15f).coerceAtLeast(1f)
                val maxW = (visible.maxOf { it.powerW } * 1.15f).coerceAtLeast(5f)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = String.format(Locale.US, "MAX %.1fV | %.2fA | %.1fW", maxV, maxI, maxW),
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${visible.size} pts  •  터치/드래그로 값 확인",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .pointerInput(visible) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        cursorFraction = frac
                                    },
                                    onDoubleTap = { cursorFraction = null }
                                )
                            }
                            .pointerInput(visible) {
                                detectHorizontalDragGestures(
                                    onDragEnd = { },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        val frac = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        cursorFraction = frac
                                    }
                                )
                            }
                    ) {
                        val w = size.width
                        val h = size.height

                        // Dashed Grid
                        val gridColor = Color(0xFF222D40)
                        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        for (i in 0..4) {
                            val y = h * i / 4f
                            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.2f, pathEffect = dash)
                        }
                        for (i in 0..4) {
                            val x = w * i / 4f
                            drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1.2f, pathEffect = dash)
                        }

                        val minT = visible.first().timeSec
                        val spanT = (visible.last().timeSec - minT).coerceAtLeast(0.1)

                        fun drawChannel(color: Color, maxVal: Float, selector: (GraphPoint) -> Float) {
                            val linePath = Path()
                            val fillPath = Path()
                            visible.forEachIndexed { idx, pt ->
                                val x = (((pt.timeSec - minT) / spanT) * w).toFloat().coerceIn(0f, w)
                                val y = (h - (selector(pt) / maxVal) * h).coerceIn(0f, h)
                                if (idx == 0) {
                                    linePath.moveTo(x, y)
                                    fillPath.moveTo(x, h)
                                    fillPath.lineTo(x, y)
                                } else {
                                    linePath.lineTo(x, y)
                                    fillPath.lineTo(x, y)
                                }
                            }
                            val lastX = (((visible.last().timeSec - minT) / spanT) * w).toFloat().coerceIn(0f, w)
                            fillPath.lineTo(lastX, h)
                            fillPath.close()

                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(color.copy(alpha = 0.22f), Color.Transparent)
                                )
                            )
                            drawPath(
                                path = linePath,
                                color = color,
                                style = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }

                        if (showW) drawChannel(PowerOrange, maxW) { it.powerW }
                        if (showI) drawChannel(AmpCyan, maxI) { it.ibusA }
                        if (showV) drawChannel(VoltYellow, maxV) { it.vbusV }

                        // Crosshair line + dots when inspecting
                        val cFrac = cursorFraction
                        if (cFrac != null) {
                            val cx = (cFrac * w).coerceIn(0f, w)
                            drawLine(
                                color = Color.White.copy(alpha = 0.7f),
                                start = Offset(cx, 0f),
                                end = Offset(cx, h),
                                strokeWidth = 2f
                            )
                            inspectedPoint?.let { pt ->
                                if (showV) {
                                    val y = (h - (pt.vbusV / maxV) * h).coerceIn(0f, h)
                                    drawCircle(VoltYellow, radius = 6f, center = Offset(cx, y))
                                }
                                if (showI) {
                                    val y = (h - (pt.ibusA / maxI) * h).coerceIn(0f, h)
                                    drawCircle(AmpCyan, radius = 6f, center = Offset(cx, y))
                                }
                                if (showW) {
                                    val y = (h - (pt.powerW / maxW) * h).coerceIn(0f, h)
                                    drawCircle(PowerOrange, radius = 6f, center = Offset(cx, y))
                                }
                            }
                        }
                    }

                    // Bottom time axis labels
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val minT = visible.first().timeSec
                        val maxT = visible.last().timeSec
                        Text(String.format(Locale.US, "%.1fs", minT), color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(String.format(Locale.US, "%.1fs", (minT + maxT) / 2.0), color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(String.format(Locale.US, "%.1fs", maxT), color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Export Action Bar: Save PNG / CSV / JSON / Share
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { onSavePng(viewingSavedSession, showV, showI, showW) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, VoltYellow.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp), tint = VoltYellow)
                Spacer(Modifier.width(4.dp))
                Text("PNG 저장", fontSize = 11.sp, color = VoltYellow)
            }
            OutlinedButton(
                onClick = { onSaveCsv(viewingSavedSession) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AmpCyan.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(15.dp), tint = AmpCyan)
                Spacer(Modifier.width(4.dp))
                Text("CSV", fontSize = 11.sp, color = AmpCyan)
            }
            OutlinedButton(
                onClick = { onSaveJson(viewingSavedSession) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, PdPurple.copy(alpha = 0.6f))
            ) {
                Text("JSON", fontSize = 11.sp, color = PdPurple)
            }
            OutlinedButton(
                onClick = { onShare("PNG", viewingSavedSession, showV, showI, showW) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, EnergyGreen.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = EnergyGreen)
                Spacer(Modifier.width(4.dp))
                Text("공유", fontSize = 11.sp, color = EnergyGreen)
            }
        }
    }
}

@Composable
private fun SavedSessionsList(
    sessions: List<SavedSessionSummary>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    if (sessions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "저장된 측정 기록이 없습니다.\n'측정' 탭에서 측정 기록을 시작하고 종료하면 자동 저장됩니다.",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        return
    }

    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(sessions, key = { it.id }) { s ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, BorderHighlight),
                shape = RoundedCornerShape(14.dp)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s.name,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${dateFmt.format(Date(s.createdAtMs))}  •  간격 ${if (s.intervalMs < 1000) "${s.intervalMs}ms" else "${s.intervalMs / 1000}s"}  •  ${s.sampleCount} pts",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = { onDelete(s.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "삭제", tint = WarnRed)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceElevated)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(String.format(Locale.US, "MAX %.2fV", s.maxVbusV), color = VoltYellow, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(String.format(Locale.US, "%.2fA", s.maxIbusA), color = AmpCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(String.format(Locale.US, "%.1fW", s.maxPowerW), color = PowerOrange, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(String.format(Locale.US, "%.1fmAh", s.capacityMah), color = EnergyGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "종료: ${s.stopReason} (${formatElapsed(s.durationSec)})",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { onOpen(s.id) },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, VoltYellow.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp), tint = VoltYellow)
                            Spacer(Modifier.width(4.dp))
                            Text("그래프 열기", fontSize = 12.sp, color = VoltYellow)
                        }
                    }
                }
            }
        }
    }
}
