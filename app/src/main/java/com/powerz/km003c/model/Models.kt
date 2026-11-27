package com.powerz.km003c.model

enum class ConnectionState {
    DISCONNECTED,
    REQUESTING_PERMISSION,
    CONNECTING,
    CONNECTED_VENDOR,
    CONNECTED_HID,
    SIMULATOR,
    ERROR
}

enum class SampleRateMode(val label: String, val rateIndex: Int?, val sps: Int) {
    SIMPLE_10HZ("Simple (10Hz)", null, 10),
    STREAM_2SPS("2 SPS", 0, 2),
    STREAM_10SPS("10 SPS", 1, 10),
    STREAM_50SPS("50 SPS", 2, 50),
    STREAM_1000SPS("1000 SPS", 3, 1000)
}

data class TelemetrySample(
    val timestampMs: Long = System.currentTimeMillis(),
    val vbusV: Double = 0.0,
    val ibusA: Double = 0.0,
    val powerW: Double = 0.0,
    val vbusAvgV: Double = 0.0,
    val ibusAvgA: Double = 0.0,
    val tempC: Double = 0.0,
    val cc1V: Double = 0.0,
    val cc2V: Double = 0.0,
    val dpV: Double = 0.0,
    val dmV: Double = 0.0,
    val vddV: Double = 0.0,
    val sequence: Int = 0
)

data class GraphPoint(
    val timeSec: Double,
    val vbusV: Float,
    val ibusA: Float,
    val powerW: Float
)

data class SessionStats(
    val capacityMah: Double = 0.0,
    val energyMwh: Double = 0.0,
    val elapsedSec: Double = 0.0,
    val maxVbusV: Double = 0.0,
    val minVbusV: Double = Double.MAX_VALUE,
    val maxIbusA: Double = 0.0,
    val maxPowerW: Double = 0.0,
    val sampleCount: Long = 0
)

// ---- Recording & Conditional Auto-Stop Models ----

enum class RecordingState {
    IDLE,
    ARMED_WAITING_TRIGGER,
    RECORDING,
    COMPLETED
}

data class RecordingConfig(
    val sessionName: String = "",
    val intervalMs: Long = 200L, // Recording/saving interval in ms (10ms .. 60000ms)

    // Auto-start trigger
    val autoStartCurrentEnabled: Boolean = false,
    val autoStartCurrentA: Double = 0.10,

    // Stop rule 1: Time duration limit
    val stopByDurationEnabled: Boolean = false,
    val stopDurationSec: Int = 600,

    // Stop rule 2: Low current sustained (classic battery full-charge stop)
    val stopByLowCurrentEnabled: Boolean = false,
    val stopLowCurrentA: Double = 0.05,
    val stopLowCurrentHoldSec: Int = 10,

    // Stop rule 3: Low power sustained
    val stopByLowPowerEnabled: Boolean = false,
    val stopLowPowerW: Double = 0.50,
    val stopLowPowerHoldSec: Int = 10,

    // Stop rule 4: Voltage threshold sustained
    val stopByVoltageEnabled: Boolean = false,
    val stopVoltageBelow: Boolean = true, // true: <= threshold, false: >= threshold
    val stopVoltageV: Double = 4.50,
    val stopVoltageHoldSec: Int = 5,

    // Stop rule 5: Target accumulated capacity (mAh)
    val stopByCapacityEnabled: Boolean = false,
    val stopTargetMah: Double = 1000.0
) {
    fun summaryConditions(): String {
        val parts = mutableListOf<String>()
        if (autoStartCurrentEnabled) parts.add("시작 I≥${autoStartCurrentA}A")
        if (stopByDurationEnabled) {
            val dur = if (stopDurationSec >= 60 && stopDurationSec % 60 == 0) "${stopDurationSec / 60}분" else "${stopDurationSec}초"
            parts.add("시간 $dur")
        }
        if (stopByLowCurrentEnabled) parts.add("I≤${stopLowCurrentA}A (${stopLowCurrentHoldSec}초)")
        if (stopByLowPowerEnabled) parts.add("W≤${stopLowPowerW}W (${stopLowPowerHoldSec}초)")
        if (stopByVoltageEnabled) {
            val op = if (stopVoltageBelow) "≤" else "≥"
            parts.add("V$op${stopVoltageV}V (${stopVoltageHoldSec}초)")
        }
        if (stopByCapacityEnabled) parts.add("${stopTargetMah.toInt()}mAh")
        return if (parts.isEmpty()) "수동 종료" else parts.joinToString(" • ")
    }

    fun intervalLabel(): String = when {
        intervalMs < 1000L -> "${intervalMs}ms"
        intervalMs % 1000L == 0L -> "${intervalMs / 1000L}초"
        else -> "${intervalMs / 1000.0}초"
    }
}

data class RecordingStatus(
    val state: RecordingState = RecordingState.IDLE,
    val sessionName: String = "",
    val elapsedSec: Double = 0.0,
    val sampleCount: Int = 0,
    val capacityMah: Double = 0.0,
    val energyMwh: Double = 0.0,
    val maxVbusV: Double = 0.0,
    val maxIbusA: Double = 0.0,
    val maxPowerW: Double = 0.0,
    val avgPowerW: Double = 0.0,
    val holdConditionLabel: String? = null,
    val holdProgress: Float = 0f,
    val stopReason: String? = null,
    val lastSavedSessionId: String? = null
)

data class RecordedSample(
    val elapsedSec: Double,
    val timestampMs: Long,
    val vbusV: Double,
    val ibusA: Double,
    val powerW: Double,
    val dpV: Double,
    val dmV: Double,
    val cc1V: Double,
    val cc2V: Double,
    val tempC: Double
) {
    fun toGraphPoint(): GraphPoint = GraphPoint(
        timeSec = elapsedSec,
        vbusV = vbusV.toFloat(),
        ibusA = kotlin.math.abs(ibusA).toFloat(),
        powerW = powerW.toFloat()
    )
}

data class SavedSessionSummary(
    val id: String,
    val name: String,
    val createdAtMs: Long,
    val durationSec: Double,
    val intervalMs: Long,
    val sampleCount: Int,
    val capacityMah: Double,
    val energyMwh: Double,
    val maxVbusV: Double,
    val maxIbusA: Double,
    val maxPowerW: Double,
    val avgPowerW: Double,
    val stopReason: String
)

data class SavedSession(
    val summary: SavedSessionSummary,
    val samples: List<RecordedSample>
)

// ---- USB PD & Device Models ----

data class DecodedPdo(
    val index: Int,
    val type: String, // "Fixed", "PPS", "EPR AVS", "Variable", "Battery"
    val voltageDesc: String,
    val maxCurrentA: Double,
    val maxPowerW: Double
)

data class PdLogEntry(
    val id: Long,
    val deviceTimestampMs: Long,
    val sop: String,
    val messageType: String,
    val summary: String,
    val rawHex: String,
    val vbusV: Double = 0.0,
    val ibusA: Double = 0.0,
    val pdos: List<DecodedPdo> = emptyList(),
    val isEvent: Boolean = false
)

data class DeviceInfo(
    val productName: String = "POWER-Z KM003C",
    val vendorId: Int = 0x5FC9,
    val productId: Int = 0x0063,
    val serialNumber: String = "-",
    val hardwareVersion: String = "-",
    val firmwareVersion: String = "-",
    val hardwareIdHex: String = "-",
    val streamingAuthorized: Boolean = false,
    val transportMode: String = "None",
    val hasCdcSerial: Boolean = false
)

data class PdmPdoItem(
    val index: Int,
    val type: String, // "Fixed", "PPS", "AVS"
    val minMv: Int,
    val maxMv: Int,
    val maxMa: Int,
    val rawLine: String
)
