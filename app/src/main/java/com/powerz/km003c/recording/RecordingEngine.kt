package com.powerz.km003c.recording

import com.powerz.km003c.model.RecordedSample
import com.powerz.km003c.model.RecordingConfig
import com.powerz.km003c.model.RecordingState
import com.powerz.km003c.model.RecordingStatus
import com.powerz.km003c.model.SavedSession
import com.powerz.km003c.model.SavedSessionSummary
import com.powerz.km003c.model.TelemetrySample
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Deterministic state machine for user-triggered recording sessions with:
 *  - Custom recording interval (intervalMs)
 *  - Optional auto-start current trigger
 *  - Combinable conditional auto-stop rules (duration, sustained low current, sustained low power,
 *    sustained voltage threshold, target capacity mAh)
 */
class RecordingEngine {

    data class TickOutcome(
        val status: RecordingStatus,
        val appendedSample: RecordedSample? = null,
        val completedSession: SavedSession? = null
    )

    private var config: RecordingConfig = RecordingConfig()
    private var state: RecordingState = RecordingState.IDLE
    private var sessionId: String = ""
    private var sessionName: String = ""

    private var recordingStartMs: Long = 0L
    private var lastSampleMs: Long = 0L
    private var lastRecordedMs: Long = 0L

    private var capacityMah: Double = 0.0
    private var energyMwh: Double = 0.0
    private var maxVbusV: Double = 0.0
    private var maxIbusA: Double = 0.0
    private var maxPowerW: Double = 0.0
    private var powerSumW: Double = 0.0
    private var integratedSampleCount: Long = 0L

    // Continuous hold timers for conditional auto-stop (timestamp when condition first became true, or 0L)
    private var lowCurrentSinceMs: Long = 0L
    private var lowPowerSinceMs: Long = 0L
    private var voltageSinceMs: Long = 0L

    private var stopReason: String? = null
    private val samples = ArrayList<RecordedSample>(2048)

    fun currentStatus(): RecordingStatus = buildStatus(null, 0f)

    fun currentSamples(): List<RecordedSample> = samples.toList()

    fun start(newConfig: RecordingConfig, nowMs: Long = System.currentTimeMillis()): RecordingStatus {
        config = newConfig.copy(intervalMs = newConfig.intervalMs.coerceIn(10L, 60_000L))
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMs))
        sessionId = "rec_$stamp"
        sessionName = config.sessionName.trim().ifEmpty {
            "측정_${SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(nowMs))}"
        }

        samples.clear()
        capacityMah = 0.0
        energyMwh = 0.0
        maxVbusV = 0.0
        maxIbusA = 0.0
        maxPowerW = 0.0
        powerSumW = 0.0
        integratedSampleCount = 0L
        lowCurrentSinceMs = 0L
        lowPowerSinceMs = 0L
        voltageSinceMs = 0L
        stopReason = null
        lastSampleMs = 0L
        lastRecordedMs = 0L

        if (config.autoStartCurrentEnabled) {
            state = RecordingState.ARMED_WAITING_TRIGGER
            recordingStartMs = 0L
        } else {
            state = RecordingState.RECORDING
            recordingStartMs = nowMs
        }
        return currentStatus()
    }

    fun stopManually(reason: String = "사용자 수동 종료"): SavedSession? {
        if (state == RecordingState.ARMED_WAITING_TRIGGER) {
            state = RecordingState.IDLE
            stopReason = "대기 취소됨"
            return null
        }
        if (state != RecordingState.RECORDING) return null
        return finalizeSession(reason)
    }

    fun resetToIdle(): RecordingStatus {
        state = RecordingState.IDLE
        stopReason = null
        lowCurrentSinceMs = 0L
        lowPowerSinceMs = 0L
        voltageSinceMs = 0L
        return currentStatus()
    }

    fun onSample(sample: TelemetrySample): TickOutcome {
        if (state == RecordingState.IDLE || state == RecordingState.COMPLETED) {
            return TickOutcome(currentStatus())
        }

        val now = sample.timestampMs
        val absI = abs(sample.ibusA)

        // Check Auto-Start trigger when armed
        if (state == RecordingState.ARMED_WAITING_TRIGGER) {
            if (absI >= config.autoStartCurrentA) {
                state = RecordingState.RECORDING
                recordingStartMs = now
                lastSampleMs = now
                lastRecordedMs = 0L
            } else {
                val waitStatus = buildStatus(
                    holdLabel = String.format(
                        Locale.US,
                        "시작 대기 중: 전류 %.3fA / 기준 %.2fA",
                        absI,
                        config.autoStartCurrentA
                    ),
                    holdProgress = (absI / config.autoStartCurrentA.coerceAtLeast(0.001)).toFloat().coerceIn(0f, 1f)
                )
                return TickOutcome(waitStatus)
            }
        }

        // Accumulate accurate electrical integration using every incoming sample
        val dtSec = if (lastSampleMs == 0L) 0.0 else ((now - lastSampleMs).coerceIn(0L, 5_000L) / 1000.0)
        lastSampleMs = now
        val dtHours = dtSec / 3600.0
        capacityMah += absI * 1000.0 * dtHours
        energyMwh += sample.powerW * 1000.0 * dtHours
        maxVbusV = maxOf(maxVbusV, sample.vbusV)
        maxIbusA = maxOf(maxIbusA, absI)
        maxPowerW = maxOf(maxPowerW, sample.powerW)
        powerSumW += sample.powerW
        integratedSampleCount++

        val elapsedSec = ((now - recordingStartMs).coerceAtLeast(0L)) / 1000.0

        // Record a sample if intervalMs has elapsed (or first sample)
        var appended: RecordedSample? = null
        if (lastRecordedMs == 0L || (now - lastRecordedMs) >= config.intervalMs) {
            lastRecordedMs = now
            val rec = RecordedSample(
                elapsedSec = elapsedSec,
                timestampMs = now,
                vbusV = sample.vbusV,
                ibusA = sample.ibusA,
                powerW = sample.powerW,
                dpV = sample.dpV,
                dmV = sample.dmV,
                cc1V = sample.cc1V,
                cc2V = sample.cc2V,
                tempC = sample.tempC
            )
            samples.add(rec)
            appended = rec
        }

        // Evaluate conditional auto-stop rules
        var activeHoldLabel: String? = null
        var activeHoldProgress = 0f

        // Rule 1: Duration limit
        if (config.stopByDurationEnabled && config.stopDurationSec > 0) {
            if (elapsedSec >= config.stopDurationSec.toDouble()) {
                val session = finalizeSession("시간 도달 (${formatSec(config.stopDurationSec)})")
                return TickOutcome(currentStatus(), appended, session)
            }
        }

        // Rule 2: Target capacity (mAh)
        if (config.stopByCapacityEnabled && config.stopTargetMah > 0.0) {
            if (capacityMah >= config.stopTargetMah) {
                val session = finalizeSession(
                    String.format(Locale.US, "목표 용량 도달 (%.1f mAh)", config.stopTargetMah)
                )
                return TickOutcome(currentStatus(), appended, session)
            }
        }

        // Rule 3: Low current sustained
        if (config.stopByLowCurrentEnabled) {
            if (absI <= config.stopLowCurrentA) {
                if (lowCurrentSinceMs == 0L) lowCurrentSinceMs = now
                val heldSec = (now - lowCurrentSinceMs) / 1000.0
                val target = config.stopLowCurrentHoldSec.coerceAtLeast(1)
                if (heldSec >= target) {
                    val session = finalizeSession(
                        String.format(Locale.US, "저전류 종료 (I ≤ %.2fA, %d초 유지)", config.stopLowCurrentA, target)
                    )
                    return TickOutcome(currentStatus(), appended, session)
                }
                val prog = (heldSec / target).toFloat().coerceIn(0f, 1f)
                if (prog >= activeHoldProgress) {
                    activeHoldProgress = prog
                    activeHoldLabel = String.format(
                        Locale.US,
                        "저전류 자동종료 대기: %.1fs / %ds (I≤%.2fA)",
                        heldSec, target, config.stopLowCurrentA
                    )
                }
            } else {
                lowCurrentSinceMs = 0L
            }
        }

        // Rule 4: Low power sustained
        if (config.stopByLowPowerEnabled) {
            if (sample.powerW <= config.stopLowPowerW) {
                if (lowPowerSinceMs == 0L) lowPowerSinceMs = now
                val heldSec = (now - lowPowerSinceMs) / 1000.0
                val target = config.stopLowPowerHoldSec.coerceAtLeast(1)
                if (heldSec >= target) {
                    val session = finalizeSession(
                        String.format(Locale.US, "저전력 종료 (W ≤ %.2fW, %d초 유지)", config.stopLowPowerW, target)
                    )
                    return TickOutcome(currentStatus(), appended, session)
                }
                val prog = (heldSec / target).toFloat().coerceIn(0f, 1f)
                if (prog >= activeHoldProgress) {
                    activeHoldProgress = prog
                    activeHoldLabel = String.format(
                        Locale.US,
                        "저전력 자동종료 대기: %.1fs / %ds (W≤%.2fW)",
                        heldSec, target, config.stopLowPowerW
                    )
                }
            } else {
                lowPowerSinceMs = 0L
            }
        }

        // Rule 5: Voltage threshold sustained
        if (config.stopByVoltageEnabled) {
            val matched = if (config.stopVoltageBelow) {
                sample.vbusV <= config.stopVoltageV
            } else {
                sample.vbusV >= config.stopVoltageV
            }
            if (matched) {
                if (voltageSinceMs == 0L) voltageSinceMs = now
                val heldSec = (now - voltageSinceMs) / 1000.0
                val target = config.stopVoltageHoldSec.coerceAtLeast(1)
                val op = if (config.stopVoltageBelow) "≤" else "≥"
                if (heldSec >= target) {
                    val session = finalizeSession(
                        String.format(Locale.US, "전압 조건 종료 (V %s %.2fV, %d초 유지)", op, config.stopVoltageV, target)
                    )
                    return TickOutcome(currentStatus(), appended, session)
                }
                val prog = (heldSec / target).toFloat().coerceIn(0f, 1f)
                if (prog >= activeHoldProgress) {
                    activeHoldProgress = prog
                    activeHoldLabel = String.format(
                        Locale.US,
                        "전압 자동종료 대기: %.1fs / %ds (V%s%.2fV)",
                        heldSec, target, op, config.stopVoltageV
                    )
                }
            } else {
                voltageSinceMs = 0L
            }
        }

        return TickOutcome(buildStatus(activeHoldLabel, activeHoldProgress), appended, null)
    }

    private fun finalizeSession(reason: String): SavedSession {
        state = RecordingState.COMPLETED
        stopReason = reason
        val elapsedSec = if (recordingStartMs > 0 && lastSampleMs >= recordingStartMs) {
            (lastSampleMs - recordingStartMs) / 1000.0
        } else {
            samples.lastOrNull()?.elapsedSec ?: 0.0
        }
        val avgPower = if (integratedSampleCount > 0) powerSumW / integratedSampleCount else 0.0
        val summary = SavedSessionSummary(
            id = sessionId,
            name = sessionName,
            createdAtMs = if (recordingStartMs > 0) recordingStartMs else System.currentTimeMillis(),
            durationSec = elapsedSec,
            intervalMs = config.intervalMs,
            sampleCount = samples.size,
            capacityMah = capacityMah,
            energyMwh = energyMwh,
            maxVbusV = maxVbusV,
            maxIbusA = maxIbusA,
            maxPowerW = maxPowerW,
            avgPowerW = avgPower,
            stopReason = reason
        )
        return SavedSession(summary = summary, samples = samples.toList())
    }

    private fun buildStatus(holdLabel: String?, holdProgress: Float): RecordingStatus {
        val elapsed = if (recordingStartMs > 0 && lastSampleMs >= recordingStartMs) {
            (lastSampleMs - recordingStartMs) / 1000.0
        } else 0.0
        val avgP = if (integratedSampleCount > 0) powerSumW / integratedSampleCount else 0.0
        return RecordingStatus(
            state = state,
            sessionName = sessionName,
            elapsedSec = elapsed,
            sampleCount = samples.size,
            capacityMah = capacityMah,
            energyMwh = energyMwh,
            maxVbusV = maxVbusV,
            maxIbusA = maxIbusA,
            maxPowerW = maxPowerW,
            avgPowerW = avgP,
            holdConditionLabel = holdLabel,
            holdProgress = holdProgress,
            stopReason = stopReason,
            lastSavedSessionId = if (state == RecordingState.COMPLETED) sessionId else null
        )
    }

    private fun formatSec(sec: Int): String = when {
        sec >= 3600 && sec % 3600 == 0 -> "${sec / 3600}시간"
        sec >= 60 && sec % 60 == 0 -> "${sec / 60}분"
        else -> "${sec}초"
    }
}
