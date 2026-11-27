package com.powerz.km003c.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CodeOff
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.powerz.km003c.model.PdLogEntry
import com.powerz.km003c.ui.components.PzEmptyState
import com.powerz.km003c.ui.components.PzPdoRow
import com.powerz.km003c.ui.components.PzStatusPill
import com.powerz.km003c.ui.components.PzSwitch
import com.powerz.km003c.ui.theme.Aux
import com.powerz.km003c.ui.theme.Border
import com.powerz.km003c.ui.theme.Energy
import com.powerz.km003c.ui.theme.Ink
import com.powerz.km003c.ui.theme.InkFaint
import com.powerz.km003c.ui.theme.InkMuted
import com.powerz.km003c.ui.theme.NumbersFont
import com.powerz.km003c.ui.theme.Ok
import com.powerz.km003c.ui.theme.Pd
import com.powerz.km003c.ui.theme.PzType
import com.powerz.km003c.ui.theme.Vbus
import java.util.Locale

@Composable
fun PdAnalyzerScreen(
    pdCaptureEnabled: Boolean,
    logs: List<PdLogEntry>,
    onToggleCapture: (Boolean) -> Unit,
    onClear: () -> Unit
) {
    var showRawHex by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Control bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PzSwitch(checked = pdCaptureEnabled, onCheckedChange = onToggleCapture)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("PD 캡처", style = PzType.body, color = Ink)
                Text("${logs.size}개 메시지", style = PzType.caption, color = InkMuted)
            }
            if (pdCaptureEnabled) PzStatusPill("LIVE", Ok, dot = true)
            IconButton(onClick = { showRawHex = !showRawHex }) {
                Icon(
                    if (showRawHex) Icons.Outlined.CodeOff else Icons.Outlined.Code,
                    contentDescription = if (showRawHex) "HEX 숨기기" else "HEX 보기",
                    tint = if (showRawHex) Ink else InkMuted
                )
            }
            IconButton(onClick = onClear, enabled = logs.isNotEmpty()) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "지우기", tint = if (logs.isNotEmpty()) InkMuted else InkFaint)
            }
        }
        HorizontalDivider(color = Border)

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PzEmptyState(
                    title = "캡처된 PD 메시지가 없어요",
                    body = "충전기 ↔ KM003C ↔ 기기를 연결하면\nPD 협상 과정이 여기에 표시돼요."
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), reverseLayout = true) {
                items(logs.reversed(), key = { it.id }) { entry ->
                    PdLogRow(entry = entry, showRawHex = showRawHex)
                    HorizontalDivider(color = Border, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

/** Direction colour: source-originated (Source_Cap, Accept, PS_RDY…) vs sink (Request…). */
private fun directionColor(entry: PdLogEntry): Color = when {
    entry.isEvent -> Energy
    entry.messageType.contains("Request") || entry.messageType.contains("Sink") -> Aux
    else -> Pd
}

@Composable
private fun PdLogRow(entry: PdLogEntry, showRawHex: Boolean) {
    val dirColor = directionColor(entry)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dirColor))
            Spacer(Modifier.width(8.dp))
            Text(entry.messageType, style = PzType.body, color = Ink, modifier = Modifier.weight(1f), maxLines = 1)
            Text(
                String.format(Locale.US, "%.3fs", entry.deviceTimestampMs / 1000.0),
                style = PzType.caption.copy(fontFamily = NumbersFont),
                color = InkFaint
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(entry.sop, style = PzType.label, color = dirColor)
            Spacer(Modifier.width(8.dp))
            Text(
                String.format(Locale.US, "%.2fV", entry.vbusV),
                style = PzType.caption.copy(fontFamily = NumbersFont),
                color = Vbus
            )
        }
        if (entry.summary.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(entry.summary, style = PzType.bodySm, color = InkMuted, modifier = Modifier.padding(start = 14.dp))
        }
        if (entry.pdos.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                entry.pdos.forEach { PzPdoRow(it) }
            }
        }
        if (showRawHex) {
            Spacer(Modifier.height(8.dp))
            Text(entry.rawHex, style = PzType.mono, color = InkFaint, modifier = Modifier.padding(start = 14.dp))
        }
    }
}
