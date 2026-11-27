package com.powerz.km003c.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerz.km003c.model.PdmPdoItem
import com.powerz.km003c.protocol.PdmSerialProtocol
import com.powerz.km003c.ui.theme.AmpCyan
import com.powerz.km003c.ui.theme.BorderSubtle
import com.powerz.km003c.ui.theme.EnergyGreen
import com.powerz.km003c.ui.theme.PdPurple
import com.powerz.km003c.ui.theme.SurfaceDark
import com.powerz.km003c.ui.theme.SurfaceElevated
import com.powerz.km003c.ui.theme.TextPrimary
import com.powerz.km003c.ui.theme.TextSecondary
import com.powerz.km003c.ui.theme.VoltYellow
import com.powerz.km003c.ui.theme.WarnRed
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
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Safety Warning Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = WarnRed.copy(alpha = 0.14f)),
            border = BorderStroke(1.dp, WarnRed.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "⚠ 주의: 프로토콜 트리거(PDM)는 충전기 출력 전압을 강제로 변경합니다. 고전압(9V~28V)으로 기기가 손상되지 않도록 부하/스마트폰을 분리한 뒤 사용하세요.",
                color = WarnRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(10.dp)
            )
        }

        // Session Controls (PDM Open / Close / Auto Detect / Reset)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "가상 시리얼 트리거 (CDC ACM)",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (pdmActive) "PDM 활성" else if (hasCdcSerial) "대기" else "시리얼 미연결",
                        color = if (pdmActive) EnergyGreen else TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = if (pdmActive) onClosePdm else onOpenPdm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pdmActive) WarnRed else VoltYellow
                        )
                    ) {
                        Text(if (pdmActive) "PDM 종료" else "PDM 시작", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = { onSendCommand(PdmSerialProtocol.CMD_ENTRY_LIST) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("프로토콜 자동감지", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onSendCommand(PdmSerialProtocol.CMD_RESET) }
                    ) {
                        Text("리셋", fontSize = 12.sp)
                    }
                }
            }
        }

        // USB PD Trigger Section
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("USB PD / PPS 트리거", color = VoltYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Button(onClick = onEnterPdAndFetch) {
                        Text("PD PDO 읽기", fontSize = 12.sp)
                    }
                }

                if (pdmPdos.isEmpty()) {
                    Text(
                        text = "'PD PDO 읽기'를 누르면 충전기의 Fixed / PPS / AVS 목록을 불러옵니다.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pdmPdos.forEach { pdo ->
                            val isVariable = pdo.type != "Fixed"
                            OutlinedButton(
                                onClick = {
                                    if (!isVariable) {
                                        onSendCommand(PdmSerialProtocol.pdRequestFixed(pdo.index))
                                    } else {
                                        val clamped = ppsMv.toInt().coerceIn(pdo.minMv, pdo.maxMv)
                                        onSendCommand(PdmSerialProtocol.pdRequestPps(pdo.index, clamped, pdo.maxMa))
                                    }
                                },
                                border = BorderStroke(1.dp, if (isVariable) PdPurple else VoltYellow)
                            ) {
                                val label = if (!isVariable) {
                                    "#${pdo.index} ${String.format(Locale.US, "%.1fV", pdo.maxMv / 1000.0)}"
                                } else {
                                    "#${pdo.index} ${pdo.type} ${String.format(Locale.US, "%.1f-%.1fV", pdo.minMv / 1000.0, pdo.maxMv / 1000.0)}"
                                }
                                Text(label, fontSize = 12.sp, color = if (isVariable) PdPurple else VoltYellow)
                            }
                        }
                    }

                    // PPS Slider if any PPS/AVS PDO exists
                    val firstPps = pdmPdos.firstOrNull { it.type != "Fixed" }
                    if (firstPps != null) {
                        Spacer(Modifier.height(4.dp))
                        val safeVal = ppsMv.coerceIn(firstPps.minMv.toFloat(), firstPps.maxMv.toFloat())
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PPS 목표 전압: ${String.format(Locale.US, "%.2f V", safeVal / 1000f)}",
                                color = PdPurple,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            OutlinedButton(
                                onClick = {
                                    onSendCommand(PdmSerialProtocol.pdRequestPps(firstPps.index, safeVal.toInt(), firstPps.maxMa))
                                }
                            ) {
                                Text("PPS 적용", fontSize = 12.sp)
                            }
                        }
                        Slider(
                            value = safeVal,
                            onValueChange = { ppsMv = (it / 20f).toInt() * 20f },
                            valueRange = firstPps.minMv.toFloat()..firstPps.maxMv.toFloat()
                        )
                    }
                }
            }
        }

        // Quick Charge / AFC / FCP / UFCS Presets
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
                Text("QC / AFC / FCP / UFCS 빠른 트리거", color = AmpCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(onClick = { onSendCommand(PdmSerialProtocol.CMD_ENTRY_QC) }) { Text("QC 진입", fontSize = 11.sp) }
                    listOf(5, 9, 12, 20).forEach { v ->
                        OutlinedButton(onClick = { onSendCommand(PdmSerialProtocol.qc2Voltage(v)) }) {
                            Text("QC ${v}V", fontSize = 11.sp)
                        }
                    }
                    OutlinedButton(onClick = { onSendCommand(PdmSerialProtocol.qc3Inc(1)) }) { Text("+200mV", fontSize = 11.sp) }
                    OutlinedButton(onClick = { onSendCommand(PdmSerialProtocol.qc3Dec(1)) }) { Text("-200mV", fontSize = 11.sp) }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(onClick = { onSendCommand(PdmSerialProtocol.CMD_ENTRY_AFC) }) { Text("AFC 진입", fontSize = 11.sp) }
                    OutlinedButton(onClick = { onSendCommand(PdmSerialProtocol.afcVoltage(9)) }) { Text("AFC 9V", fontSize = 11.sp) }
                    OutlinedButton(onClick = { onSendCommand(PdmSerialProtocol.CMD_ENTRY_FCP) }) { Text("FCP 진입", fontSize = 11.sp) }
                    OutlinedButton(onClick = { onSendCommand(PdmSerialProtocol.fcpVoltage(9)) }) { Text("FCP 9V", fontSize = 11.sp) }
                    OutlinedButton(onClick = { onSendCommand(PdmSerialProtocol.CMD_ENTRY_UFCS) }) { Text("UFCS 진입", fontSize = 11.sp) }
                    OutlinedButton(onClick = { onSendCommand(PdmSerialProtocol.CMD_UFCS_PDO) }) { Text("UFCS PDO", fontSize = 11.sp) }
                }
            }
        }

        // Raw Serial Console (cmd.pdf)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("시리얼 콘솔 (cmd.pdf 명령어)", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = onClearConsole) { Text("지우기", fontSize = 11.sp) }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceElevated)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    if (consoleLines.isEmpty()) {
                        Text("명령어 응답이 여기에 표시됩니다.", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    } else {
                        consoleLines.forEach { line ->
                            Text(
                                text = line,
                                color = if (line.startsWith(">")) VoltYellow else TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customCmd,
                        onValueChange = { customCmd = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("예: pd req=2,cur=3000", fontSize = 12.sp) },
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (customCmd.isNotBlank()) {
                                onSendCommand(customCmd.trim())
                                customCmd = ""
                            }
                        }
                    ) {
                        Text("전송", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
