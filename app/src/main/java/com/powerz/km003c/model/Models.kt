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
