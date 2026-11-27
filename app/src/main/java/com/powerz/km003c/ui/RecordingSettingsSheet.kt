package com.powerz.km003c.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.powerz.km003c.model.RecordingConfig
import com.powerz.km003c.model.SampleRateMode
import com.powerz.km003c.ui.components.PzCard
import com.powerz.km003c.ui.components.PzChip
import com.powerz.km003c.ui.components.PzLabel
import com.powerz.km003c.ui.components.PzPrimaryButton
import com.powerz.km003c.ui.components.PzSectionHeader
import com.powerz.km003c.ui.components.PzSwitch
import com.powerz.km003c.ui.components.PzTextField
import com.powerz.km003c.ui.theme.Ink
import com.powerz.km003c.ui.theme.InkFaint
import com.powerz.km003c.ui.theme.InkMuted
import com.powerz.km003c.ui.theme.PanelStrong
import com.powerz.km003c.ui.theme.PzRadius
import com.powerz.km003c.ui.theme.PzType

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
        containerColor = PanelStrong,
        shape = RoundedCornerShape(topStart = PzRadius.lg, topEnd = PzRadius.lg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("기록 설정", style = PzType.bodyLg.copy(fontSize = PzType.metricSm.fontSize), color = Ink)

            PzTextField(
                value = sessionName,
                onValueChange = { sessionName = it },
                placeholder = "세션 이름 (비우면 날짜·시간)",
                modifier = Modifier.fillMaxWidth()
            )

            // Interval
            PzSectionHeader("기록 간격")
            SheetCard {
                val presets = listOf(
                    10L to "10ms", 50L to "50ms", 100L to "100ms", 200L to "200ms", 500L to "500ms",
                    1000L to "1초", 2000L to "2초", 5000L to "5초", 10000L to "10초"
                )
                val currentInterval = intervalMsText.toLongOrNull() ?: config.intervalMs
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { (ms, label) ->
                        PzChip(label, currentInterval == ms, { intervalMsText = ms.toString() })
                    }
                }
                Spacer(Modifier.height(12.dp))
                NumberField("직접 입력 (10 ~ 60000)", intervalMsText, { intervalMsText = it.filter { c -> c.isDigit() } }, "ms", decimal = false)
                Spacer(Modifier.height(20.dp))
                PzLabel("하드웨어 수집 속도")
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SampleRateMode.entries.forEach { mode ->
                        PzChip(mode.label, sampleRate == mode, { onSelectSampleRate(mode) })
                    }
                }
            }

            // Auto start
            PzSectionHeader("자동 시작")
            RuleCard(
                title = "전류 감지 시 시작",
                subtitle = "전류가 기준 이상 흐르면 기록을 시작해요",
                checked = autoStartEnabled,
                onCheckedChange = { autoStartEnabled = it }
            ) {
                NumberField("시작 기준 전류", autoStartAText, { autoStartAText = it }, "A")
            }

            // Auto stop
            PzSectionHeader("자동 종료")
            Text("여러 개를 켜면 먼저 충족된 조건으로 종료돼요.", style = PzType.caption, color = InkFaint)

            RuleCard(
                title = "저전류 유지 (완충 감지)",
                subtitle = "전류가 기준 이하로 일정 시간 유지되면 종료",
                checked = stopLowCurEnabled,
                onCheckedChange = { stopLowCurEnabled = it }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("기준 이하", stopLowCurAText, { stopLowCurAText = it }, "A", Modifier.weight(1f))
                    NumberField("유지 시간", stopLowCurHoldText, { stopLowCurHoldText = it.filter { c -> c.isDigit() } }, "초", Modifier.weight(1f), decimal = false)
                }
            }

            RuleCard(
                title = "시간 경과",
                subtitle = "설정한 시간이 지나면 종료",
                checked = stopDurationEnabled,
                onCheckedChange = { stopDurationEnabled = it }
            ) {
                val durPresets = listOf(30 to "30초", 60 to "1분", 300 to "5분", 600 to "10분", 1800 to "30분", 3600 to "1시간")
                val curDur = stopDurationSecText.toIntOrNull() ?: config.stopDurationSec
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    durPresets.forEach { (sec, lbl) ->
                        PzChip(lbl, curDur == sec, { stopDurationSecText = sec.toString() })
                    }
                }
                Spacer(Modifier.height(12.dp))
                NumberField("측정 시간", stopDurationSecText, { stopDurationSecText = it.filter { c -> c.isDigit() } }, "초", decimal = false)
            }

            RuleCard(
                title = "저전력 유지",
                subtitle = "전력이 기준 이하로 일정 시간 유지되면 종료",
                checked = stopLowPwrEnabled,
                onCheckedChange = { stopLowPwrEnabled = it }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("기준 이하", stopLowPwrWText, { stopLowPwrWText = it }, "W", Modifier.weight(1f))
                    NumberField("유지 시간", stopLowPwrHoldText, { stopLowPwrHoldText = it.filter { c -> c.isDigit() } }, "초", Modifier.weight(1f), decimal = false)
                }
            }

            RuleCard(
                title = "전압 기준",
                subtitle = "VBUS가 기준 이하(또는 이상)로 유지되면 종료",
                checked = stopVoltEnabled,
                onCheckedChange = { stopVoltEnabled = it }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PzChip("이하 ≤", stopVoltBelow, { stopVoltBelow = true }, Modifier.weight(1f))
                    PzChip("이상 ≥", !stopVoltBelow, { stopVoltBelow = false }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("기준 전압", stopVoltVText, { stopVoltVText = it }, "V", Modifier.weight(1f))
                    NumberField("유지 시간", stopVoltHoldText, { stopVoltHoldText = it.filter { c -> c.isDigit() } }, "초", Modifier.weight(1f), decimal = false)
                }
            }

            RuleCard(
                title = "목표 용량 도달",
                subtitle = "누적 용량이 목표치에 도달하면 종료",
                checked = stopCapEnabled,
                onCheckedChange = { stopCapEnabled = it }
            ) {
                NumberField("목표 용량", stopCapMahText, { stopCapMahText = it }, "mAh")
            }

            Spacer(Modifier.height(8.dp))
            PzPrimaryButton(
                text = "설정 적용",
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
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SheetCard(content: @Composable ColumnScope.() -> Unit) {
    PzCard(modifier = Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun RuleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    PzCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = PzType.body, color = Ink)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = PzType.caption, color = InkMuted)
            }
            Spacer(Modifier.width(12.dp))
            PzSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (checked) {
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    decimal: Boolean = true
) {
    Column(modifier = modifier) {
        Text(label, style = PzType.caption, color = InkMuted)
        Spacer(Modifier.height(6.dp))
        PzTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = PzType.metricSm,
            suffix = unit,
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
