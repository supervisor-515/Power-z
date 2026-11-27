package com.powerz.km003c.usb

import com.powerz.km003c.model.DecodedPdo
import com.powerz.km003c.model.DeviceInfo
import com.powerz.km003c.model.PdLogEntry
import com.powerz.km003c.model.TelemetrySample
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Realistic KM003C hardware simulator so users can explore the UI and test features
 * even without a physical KM003C plugged in.
 */
class Km003cSimulator {
    private var tick = 0L
    private var targetVoltageV = 9.0
    private var targetCurrentA = 2.45

    fun deviceInfo(): DeviceInfo = DeviceInfo(
        productName = "POWER-Z KM003C (시뮬레이터)",
        vendorId = 0x5FC9,
        productId = 0x0063,
        serialNumber = "SIM-KM003C-001",
        hardwareVersion = "HW 2.1",
        firmwareVersion = "V2.0.7 (Sim)",
        hardwareIdHex = "53494D554C41544F52303031",
        streamingAuthorized = true,
        transportMode = "Simulator",
        hasCdcSerial = true
    )

    fun setTarget(voltageV: Double, currentA: Double = targetCurrentA) {
        targetVoltageV = voltageV
        targetCurrentA = currentA
    }

    fun nextSample(): TelemetrySample {
        tick++
        val t = tick * 0.1
        val vRipple = sin(t * 0.7) * 0.015 + (Random.nextDouble() - 0.5) * 0.006
        val iRipple = sin(t * 1.3) * 0.08 + (Random.nextDouble() - 0.5) * 0.02
        val v = (targetVoltageV + vRipple).coerceAtLeast(0.0)
        val i = (targetCurrentA + iRipple).coerceAtLeast(0.0)
        return TelemetrySample(
            timestampMs = System.currentTimeMillis(),
            vbusV = v,
            ibusA = i,
            powerW = abs(v * i),
            vbusAvgV = targetVoltageV,
            ibusAvgA = targetCurrentA,
            tempC = 32.4 + sin(t * 0.1) * 0.6,
            cc1V = 1.62,
            cc2V = 0.01,
            dpV = 0.61,
            dmV = 0.02,
            vddV = 3.30,
            sequence = (tick and 0xFFFF).toInt()
        )
    }

    fun initialPdLogs(): List<PdLogEntry> {
        val pdos = listOf(
            DecodedPdo(1, "Fixed", "5.00V", 3.00, 15.0),
            DecodedPdo(2, "Fixed", "9.00V", 3.00, 27.0),
            DecodedPdo(3, "Fixed", "15.00V", 3.00, 45.0),
            DecodedPdo(4, "Fixed", "20.00V", 3.25, 65.0),
            DecodedPdo(5, "PPS", "3.3-11.0V", 5.00, 55.0),
            DecodedPdo(6, "PPS", "3.3-21.0V", 3.25, 65.0)
        )
        return listOf(
            PdLogEntry(1, 1020, "EVENT", "Connect", "USB-C 연결 감지 (CC1)", "45 FC 03 00 00 21", 5.02, 0.01, isEvent = true),
            PdLogEntry(2, 1145, "SOP", "Source_Capabilities", "PD3.0 #0 • 6 PDOs: 5.00V/3.00A, 9.00V/3.00A, 15.00V/3.00A, 20.00V/3.25A, 3.3-11.0V/5.00A, 3.3-21.0V/3.25A", "A1 61 2C 91 01 08 ...", 5.02, 0.02, pdos = pdos),
            PdLogEntry(3, 1147, "SOP", "GoodCRC", "PD3.0 #0", "41 00", 5.02, 0.02),
            PdLogEntry(4, 1152, "SOP", "Request", "PD3.0 #1 • Pos #2 (Op 2500mA / Max 3000mA)", "82 10 2C B1 04 20", 5.02, 0.02),
            PdLogEntry(5, 1155, "SOP", "Accept", "PD3.0 #1", "A3 02", 5.03, 0.05),
            PdLogEntry(6, 1280, "SOP", "PS_RDY", "PD3.0 #2", "A6 04", 9.01, 2.45)
        )
    }

    fun handleSimulatedCdcCommand(cmd: String): String {
        val c = cmd.trim().lowercase()
        return when {
            c == "pdm open" -> "pdm mode entry\nver1.0\nok"
            c == "pdm close" -> "ok"
            c == "reset" -> "ok"
            c == "entry list" -> "PD3.0 (65W)\nPPS (3.3-21V)\nQC2.0 (5/9/12V)\nQC3.0\nAFC (5/9/12V)\nFCP (5/9/12V)\nSCP\nApple 2.4A\nBC1.2"
            c == "entry pd" -> "CC1 Orient\nPDO 6\nready:5012mV,0mA"
            c == "pd pdo" -> """
                PDO 6
                max power 65W
                Fixed:   5.00V 3.00A
                Fixed:   9.00V 3.00A
                Fixed:  15.00V 3.00A
                Fixed:  20.00V 3.25A
                PPS: 3.30-11.00V 5.00A
                PPS: 3.30-21.00V 3.25A
            """.trimIndent()
            c.startsWith("pd req=") -> {
                val voltMatch = Regex("""volt=(\d+)""").find(c)
                if (voltMatch != null) {
                    val v = voltMatch.groupValues[1].toInt() / 1000.0
                    setTarget(v)
                    "ok\nready:${voltMatch.groupValues[1]}mV,0mA"
                } else {
                    val pos = Regex("""req=(\d+)""").find(c)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val v = when (pos) { 1 -> 5.0; 2 -> 9.0; 3 -> 15.0; 4 -> 20.0; else -> 9.0 }
                    setTarget(v)
                    "ok\nready:${(v * 1000).toInt()}mV,0mA"
                }
            }
            c.startsWith("qc ") -> {
                val v = Regex("""(\d+)v""").find(c)?.groupValues?.get(1)?.toDoubleOrNull() ?: 5.0
                setTarget(v)
                "ok (${v}V)"
            }
            c.startsWith("qc3 volt=") -> {
                val mv = c.substringAfter("volt=").toIntOrNull() ?: 5000
                setTarget(mv / 1000.0)
                "ok (${mv}mV)"
            }
            c.startsWith("afc ") || c.startsWith("fcp ") -> {
                val v = Regex("""(\d+)v""").find(c)?.groupValues?.get(1)?.toDoubleOrNull() ?: 9.0
                setTarget(v)
                "ok (${v}V)"
            }
            else -> "ok ($cmd)"
        }
    }
}
