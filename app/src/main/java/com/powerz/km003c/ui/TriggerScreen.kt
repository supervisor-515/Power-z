package com.powerz.km003c.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.powerz.km003c.model.PdmPdoItem
import com.powerz.km003c.protocol.PdmSerialProtocol
import com.powerz.km003c.ui.components.PzCard
import com.powerz.km003c.ui.components.PzChip
import com.powerz.km003c.ui.components.PzInfoPill
import com.powerz.km003c.ui.components.PzLabel
import com.powerz.km003c.ui.components.PzPrimaryButton
import com.powerz.km003c.ui.components.PzSecondaryButton
import com.powerz.km003c.ui.components.PzSectionHeader
import com.powerz.km003c.ui.components.PzStatusPill
import com.powerz.km003c.ui.components.PzTextField
import com.powerz.km003c.ui.components.PzValue
import com.powerz.km003c.ui.theme.Border
import com.powerz.km003c.ui.theme.Brand
import com.powerz.km003c.ui.theme.Danger
import com.powerz.km003c.ui.theme.IbusText
import com.powerz.km003c.ui.theme.Ink
import com.powerz.km003c.ui.theme.InkFaint
import com.powerz.km003c.ui.theme.InkMuted
import com.powerz.km003c.ui.theme.NumbersFont
import com.powerz.km003c.ui.theme.Ok
import com.powerz.km003c.ui.theme.Panel
import com.powerz.km003c.ui.theme.PanelStrong
import com.powerz.km003c.ui.theme.PzRadius
import com.powerz.km003c.ui.theme.PzType
import com.powerz.km003c.ui.theme.Vbus
import com.powerz.km003c.ui.theme.Warn
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TriggerScreen(
    hasCdcSerial: Boolean,
    pdmActive: Boolean,
    pdmPdos: List<PdmPdoItem>,
    consoleLines: List<String>,
    onOpenPdm: () -> Unit,
    onClosePdm: () -> Unit,
    onEnterPdAndFetch: () -> Unit,
    onSendCommand: (String) -> Unit,
    onClearConsole: () -> Unit
) {
    var customCmd by remember { mutableStateOf("") }
    var ppsMv by remember { mutableFloatStateOf(9000f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // Status + safety
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                pdmActive -> PzStatusPill("PDM 활성", Ok, dot = true)
                hasCdcSerial -> PzInfoPill("대기")
                else -> PzInfoPill("시리얼 없음", color = InkFaint)
            }
            Spacer(Modifier.weight(1f))
            PzLabel("CDC ACM")
        }
        PzCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Warn, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "트리거는 충전기 출력 전압을 강제로 바꿔요. 9~28V 고전압으로 기기가 손상되지 않도록 스마트폰·부하를 분리한 뒤 사용하세요.",
                    style = PzType.bodySm,
                    color = InkMuted
                )
            }
        }

        // Session controls — the one primary CTA on this screen
        PzPrimaryButton(
            text = if (pdmActive) "PDM 종료" else "PDM 시작",
            onClick = if (pdmActive) onClosePdm else onOpenPdm,
            container = if (pdmActive) Danger else Brand,
            content = if (pdmActive) Ink else com.powerz.km003c.ui.theme.InkOnBrand,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PzSecondaryButton("자동 감지", { onSendCommand(PdmSerialProtocol.CMD_ENTRY_LIST) }, Modifier.weight(1f))
            PzSecondaryButton("리셋", { onSendCommand(PdmSerialProtocol.CMD_RESET) }, Modifier.weight(1f))
        }

        // USB PD / PPS
        PzSectionHeader("USB PD · PPS") {
            Text(
                "PDO 읽기",
                style = PzType.body,
                color = Brand,
                modifier = Modifier
                    .clip(RoundedCornerShape(PzRadius.sm))
                    .clickable(onClick = onEnterPdAndFetch)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        PzCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
            if (pdmPdos.isEmpty()) {
                Text(
                    "'PDO 읽기'를 누르면 충전기의 Fixed / PPS / AVS 목록을 불러와요.",
                    style = PzType.bodySm,
                    color = InkMuted,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                pdmPdos.forEachIndexed { i, pdo ->
                    if (i > 0) HorizontalDivider(color = Border, modifier = Modifier.padding(start = 16.dp))
                    PdmPdoRow(
                        pdo = pdo,
                        onRequest = {
                            if (pdo.type == "Fixed") {
                                onSendCommand(PdmSerialProtocol.pdRequestFixed(pdo.index))
                            } else {
                                val clamped = ppsMv.toInt().coerceIn(pdo.minMv, pdo.maxMv)
                                onSendCommand(PdmSerialProtocol.pdRequestPps(pdo.index, clamped, pdo.maxMa))
                            }
                        }
                    )
                }
            }
        }

        val firstPps = pdmPdos.firstOrNull { it.type != "Fixed" }
        if (firstPps != null) {
            val safeVal = ppsMv.coerceIn(firstPps.minMv.toFloat(), firstPps.maxMv.toFloat())
            PzCard(modifier = Modifier.fillMaxWidth()) {
                PzLabel("${firstPps.type} 목표 전압 · #${firstPps.index}")
                Spacer(Modifier.height(10.dp))
                PzValue(String.format(Locale.US, "%.2f", safeVal / 1000f), "V", Vbus, PzType.metricLg)
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = safeVal,
                    onValueChange = { ppsMv = (it / 20f).toInt() * 20f },
                    valueRange = firstPps.minMv.toFloat()..firstPps.maxMv.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Vbus,
                        activeTrackColor = Vbus,
                        inactiveTrackColor = PanelStrong
                    )
                )
                Row {
                    Text(String.format(Locale.US, "%.1fV", firstPps.minMv / 1000f), style = PzType.caption.copy(fontFamily = NumbersFont), color = InkFaint)
                    Spacer(Modifier.weight(1f))
                    Text(String.format(Locale.US, "%.1fV", firstPps.maxMv / 1000f), style = PzType.caption.copy(fontFamily = NumbersFont), color = InkFaint)
                }
                Spacer(Modifier.height(12.dp))
                PzSecondaryButton(
                    "PPS 적용",
                    { onSendCommand(PdmSerialProtocol.pdRequestPps(firstPps.index, safeVal.toInt(), firstPps.maxMa)) },
                    Modifier.fillMaxWidth()
                )
            }
        }

        // QC / AFC / FCP / UFCS
        PzSectionHeader("QC · AFC · FCP · UFCS")
        PzCard(modifier = Modifier.fillMaxWidth()) {
            ProtocolGroup("Quick Charge") {
                PzChip("진입", false, { onSendCommand(PdmSerialProtocol.CMD_ENTRY_QC) })
                listOf(5, 9, 12, 20).forEach { v ->
                    PzChip("${v}V", false, { onSendCommand(PdmSerialProtocol.qc2Voltage(v)) })
                }
                PzChip("+200mV", false, { onSendCommand(PdmSerialProtocol.qc3Inc(1)) })
                PzChip("−200mV", false, { onSendCommand(PdmSerialProtocol.qc3Dec(1)) })
            }
            Spacer(Modifier.height(20.dp))
            ProtocolGroup("Samsung AFC") {
                PzChip("진입", false, { onSendCommand(PdmSerialProtocol.CMD_ENTRY_AFC) })
                PzChip("9V", false, { onSendCommand(PdmSerialProtocol.afcVoltage(9)) })
            }
            Spacer(Modifier.height(20.dp))
            ProtocolGroup("Huawei FCP") {
                PzChip("진입", false, { onSendCommand(PdmSerialProtocol.CMD_ENTRY_FCP) })
                PzChip("9V", false, { onSendCommand(PdmSerialProtocol.fcpVoltage(9)) })
            }
            Spacer(Modifier.height(20.dp))
            ProtocolGroup("UFCS") {
                PzChip("진입", false, { onSendCommand(PdmSerialProtocol.CMD_ENTRY_UFCS) })
                PzChip("PDO", false, { onSendCommand(PdmSerialProtocol.CMD_UFCS_PDO) })
            }
        }

        // Serial console
        PzSectionHeader("시리얼 콘솔") {
            IconButton(onClick = onClearConsole, enabled = consoleLines.isNotEmpty()) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "지우기", tint = if (consoleLines.isNotEmpty()) InkMuted else InkFaint)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(PzRadius.md))
                .background(Panel)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (consoleLines.isEmpty()) {
                Text("명령어 응답이 여기에 표시돼요.", style = PzType.mono, color = InkFaint)
            } else {
                consoleLines.forEach { line ->
                    Text(line, style = PzType.mono, color = if (line.startsWith(">")) Brand else InkMuted)
                }
            }
        }
        val send = {
            if (customCmd.isNotBlank()) {
                onSendCommand(customCmd.trim())
                customCmd = ""
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PzTextField(
                value = customCmd,
                onValueChange = { customCmd = it },
                placeholder = "pd req=2,cur=3000",
                textStyle = PzType.mono.copy(fontSize = PzType.body.fontSize),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = send, enabled = customCmd.isNotBlank()) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "전송",
                    tint = if (customCmd.isNotBlank()) Brand else InkFaint
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PdmPdoRow(pdo: PdmPdoItem, onRequest: () -> Unit) {
    val isFixed = pdo.type == "Fixed"
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "#${pdo.index}",
            style = PzType.caption.copy(fontFamily = NumbersFont),
            color = InkFaint,
            modifier = Modifier.width(28.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isFixed) String.format(Locale.US, "%.1f V", pdo.maxMv / 1000.0)
                else String.format(Locale.US, "%.1f–%.1f V", pdo.minMv / 1000.0, pdo.maxMv / 1000.0),
                style = PzType.metricSm,
                color = Vbus
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    String.format(Locale.US, "%.2f A", pdo.maxMa / 1000.0),
                    style = PzType.caption.copy(fontFamily = NumbersFont),
                    color = IbusText
                )
                Spacer(Modifier.width(8.dp))
                PzInfoPill(pdo.type)
            }
        }
        Text(
            "요청",
            style = PzType.body,
            color = Brand,
            modifier = Modifier
                .clip(RoundedCornerShape(PzRadius.sm))
                .clickable(onClick = onRequest)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProtocolGroup(title: String, content: @Composable () -> Unit) {
    Text(title, style = PzType.body, color = Ink)
    Spacer(Modifier.height(10.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}
