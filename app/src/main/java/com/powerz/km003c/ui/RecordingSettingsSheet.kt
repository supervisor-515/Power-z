package com.powerz.km003c.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerz.km003c.model.RecordingConfig
import com.powerz.km003c.model.SampleRateMode
import com.powerz.km003c.ui.theme.AmpCyan
import com.powerz.km003c.ui.theme.BorderSubtle
import com.powerz.km003c.ui.theme.EnergyGreen
import com.powerz.km003c.ui.theme.PowerOrange
import com.powerz.km003c.ui.theme.SurfaceCard
import com.powerz.km003c.ui.theme.SurfaceDark
import com.powerz.km003c.ui.theme.TextPrimary
import com.powerz.km003c.ui.theme.TextSecondary
import com.powerz.km003c.ui.theme.VoltYellow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordingSettingsSheet(
    config: RecordingConfig,
    sampleRate: SampleRateMode,
    onSelectSampleRate: (SampleRateMode) -> Unit,
    onApply: (RecordingConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var sessionName by remember { mutableStateOf(config.sessionName) }
    var intervalMsText by remember { mutableStateOf(config.intervalMs.toString()) }

    var autoStartEnabled by remember { mutableStateOf(config.autoStartCurrentEnabled) }
    var autoStartAText by remember { mutableStateOf(config.autoStartCurrentA.toString()) }

    var stopDurationEnabled by remember { mutableStateOf(config.stopByDurationEnabled) }
    var stopDurationSecText by remember { mutableStateOf(config.stopDurationSec.toString()) }

    var stopLowCurEnabled by remember { mutableStateOf(config.stopByLowCurrentEnabled) }
    var stopLowCurAText by remember { mutableStateOf(config.stopLowCurrentA.toString()) }
    var stopLowCurHoldText by remember { mutableStateOf(config.stopLowCurrentHoldSec.toString()) }

    var stopLowPwrEnabled by remember { mutableStateOf(config.stopByLowPowerEnabled) }
    var stopLowPwrWText by remember { mutableStateOf(config.stopLowPowerW.toString()) }
    var stopLowPwrHoldText by remember { mutableStateOf(config.stopLowPowerHoldSec.toString()) }

    var stopVoltEnabled by remember { mutableStateOf(config.stopByVoltageEnabled) }
    var stopVoltBelow by remember { mutableStateOf(config.stopVoltageBelow) }
    var stopVoltVText by remember { mutableStateOf(config.stopVoltageV.toString()) }
    var stopVoltHoldText by remember { mutableStateOf(config.stopVoltageHoldSec.toString()) }

    var stopCapEnabled by remember { mutableStateOf(config.stopByCapacityEnabled) }
    var stopCapMahText by remember { mutableStateOf(config.stopTargetMah.toInt().toString()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "측정 간격 & 자동 종료 조건",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Session Name
            OutlinedTextField(
                value = sessionName,
                onValueChange = { sessionName = it },
                label = { Text("측정 세션 이름 (비워두면 날짜/시간 자동 지정)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Recording Interval Section
            SectionCard(title = "기록 측정 간격 (Recording Interval)") {
                val presets = listOf(
                    10L to "10ms",
                    50L to "50ms",
                    100L to "100ms",
                    200L to "200ms",
                    500L to "500ms",
                    1000L to "1초",
                    2000L to "2초",
                    5000L to "5초",
                    10000L to "10초"
                )
                val currentInterval = intervalMsText.toLongOrNull() ?: config.intervalMs
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presets.forEach { (ms, label) ->
                        FilterChip(
                            selected = currentInterval == ms,
                            onClick = { intervalMsText = ms.toString() },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VoltYellow.copy(alpha = 0.22f),
                                selectedLabelColor = VoltYellow
                            )
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = intervalMsText,
                    onValueChange = { intervalMsText = it.filter { c -> c.isDigit() } },
                    label = { Text("직접 입력 (ms, 10 ~ 60000)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text("하드웨어 수집 속도 (KM003C SPS)", color = TextSecondary, fontSize = 11.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SampleRateMode.entries.forEach { mode ->
                        FilterChip(
                            selected = sampleRate == mode,
                            onClick = { onSelectSampleRate(mode) },
                            label = { Text(mode.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmpCyan.copy(alpha = 0.22f),
                                selectedLabelColor = AmpCyan
                            )
                        )
                    }
                }
            }

            // Auto-Start Trigger
            RuleToggleCard(
                title = "전류 감지 자동 시작",
                subtitle = "부하 연결로 전류가 기준 이상 흐를 때 기록 자동 시작",
                checked = autoStartEnabled,
                onCheckedChange = { autoStartEnabled = it }
            ) {
                OutlinedTextField(
                    value = autoStartAText,
                    onValueChange = { autoStartAText = it },
                    label = { Text("시작 기준 전류 (A)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = BorderSubtle)
            Text(
                text = "자동 종료 조건 (복수 선택 시 먼저 충족된 조건으로 종료)",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Stop Rule 1: Low Current (Full-Charge Detection)
            RuleToggleCard(
                title = "저전류 유지 종료 (완충 감지)",
                subtitle = "전류가 설정값 이하로 지정 시간 동안 지속되면 자동 저장 후 종료",
                checked = stopLowCurEnabled,
                onCheckedChange = { stopLowCurEnabled = it }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stopLowCurAText,
                        onValueChange = { stopLowCurAText = it },
                        label = { Text("기준 전류 (A 이하)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = stopLowCurHoldText,
                        onValueChange = { stopLowCurHoldText = it.filter { c -> c.isDigit() } },
                        label = { Text("지속 시간 (초)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Stop Rule 2: Duration Limit
            RuleToggleCard(
                title = "시간 경과 종료",
                subtitle = "설정한 시간이 지나면 자동으로 측정 종료",
                checked = stopDurationEnabled,
                onCheckedChange = { stopDurationEnabled = it }
            ) {
                val durPresets = listOf(30 to "30초", 60 to "1분", 300 to "5분", 600 to "10분", 1800 to "30분", 3600 to "1시간")
                val curDur = stopDurationSecText.toIntOrNull() ?: config.stopDurationSec
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    durPresets.forEach { (sec, lbl) ->
                        FilterChip(
                            selected = curDur == sec,
                            onClick = { stopDurationSecText = sec.toString() },
                            label = { Text(lbl, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = stopDurationSecText,
                    onValueChange = { stopDurationSecText = it.filter { c -> c.isDigit() } },
                    label = { Text("측정 시간 (초)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Stop Rule 3: Low Power
            RuleToggleCard(
                title = "저전력 유지 종료",
                subtitle = "전력(W)이 기준 이하로 지속되면 자동 종료",
                checked = stopLowPwrEnabled,
                onCheckedChange = { stopLowPwrEnabled = it }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stopLowPwrWText,
                        onValueChange = { stopLowPwrWText = it },
                        label = { Text("기준 전력 (W 이하)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = stopLowPwrHoldText,
                        onValueChange = { stopLowPwrHoldText = it.filter { c -> c.isDigit() } },
                        label = { Text("지속 시간 (초)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Stop Rule 4: Voltage Threshold
            RuleToggleCard(
                title = "전압 기준 종료",
                subtitle = "VBUS가 기준 전압 이하(또는 이상)로 지속되면 종료",
                checked = stopVoltEnabled,
                onCheckedChange = { stopVoltEnabled = it }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = stopVoltBelow,
                        onClick = { stopVoltBelow = true },
                        label = { Text("기준 이하 (≤)") }
                    )
                    FilterChip(
                        selected = !stopVoltBelow,
                        onClick = { stopVoltBelow = false },
                        label = { Text("기준 이상 (≥)") }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stopVoltVText,
                        onValueChange = { stopVoltVText = it },
                        label = { Text("기준 전압 (V)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = stopVoltHoldText,
                        onValueChange = { stopVoltHoldText = it.filter { c -> c.isDigit() } },
                        label = { Text("지속 시간 (초)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Stop Rule 5: Capacity Target
            RuleToggleCard(
                title = "목표 용량 도달 종료",
                subtitle = "누적 용량(mAh)이 목표치에 도달하면 종료",
                checked = stopCapEnabled,
                onCheckedChange = { stopCapEnabled = it }
            ) {
                OutlinedTextField(
                    value = stopCapMahText,
                    onValueChange = { stopCapMahText = it },
                    label = { Text("목표 용량 (mAh)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    val updated = RecordingConfig(
                        sessionName = sessionName.trim(),
                        intervalMs = (intervalMsText.toLongOrNull() ?: config.intervalMs).coerceIn(10L, 60_000L),
                        autoStartCurrentEnabled = autoStartEnabled,
                        autoStartCurrentA = (autoStartAText.toDoubleOrNull() ?: config.autoStartCurrentA).coerceAtLeast(0.01),
                        stopByDurationEnabled = stopDurationEnabled,
                        stopDurationSec = (stopDurationSecText.toIntOrNull() ?: config.stopDurationSec).coerceAtLeast(1),
                        stopByLowCurrentEnabled = stopLowCurEnabled,
                        stopLowCurrentA = (stopLowCurAText.toDoubleOrNull() ?: config.stopLowCurrentA).coerceAtLeast(0.0),
                        stopLowCurrentHoldSec = (stopLowCurHoldText.toIntOrNull() ?: config.stopLowCurrentHoldSec).coerceAtLeast(1),
                        stopByLowPowerEnabled = stopLowPwrEnabled,
                        stopLowPowerW = (stopLowPwrWText.toDoubleOrNull() ?: config.stopLowPowerW).coerceAtLeast(0.0),
                        stopLowPowerHoldSec = (stopLowPwrHoldText.toIntOrNull() ?: config.stopLowPowerHoldSec).coerceAtLeast(1),
                        stopByVoltageEnabled = stopVoltEnabled,
                        stopVoltageBelow = stopVoltBelow,
                        stopVoltageV = (stopVoltVText.toDoubleOrNull() ?: config.stopVoltageV).coerceAtLeast(0.0),
                        stopVoltageHoldSec = (stopVoltHoldText.toIntOrNull() ?: config.stopVoltageHoldSec).coerceAtLeast(1),
                        stopByCapacityEnabled = stopCapEnabled,
                        stopTargetMah = (stopCapMahText.toDoubleOrNull() ?: config.stopTargetMah).coerceAtLeast(1.0)
                    )
                    onApply(updated)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VoltYellow)
            ) {
                Text("설정 적용", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, BorderSubtle),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = VoltYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun RuleToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, if (checked) EnergyGreen.copy(alpha = 0.55f) else BorderSubtle),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = TextSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            if (checked) {
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}
