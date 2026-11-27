package com.powerz.km003c.protocol

import com.powerz.km003c.model.DecodedPdo
import com.powerz.km003c.model.PdLogEntry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Decodes raw USB Power Delivery (PD 2.0 / 3.0 / 3.1 / 3.2) wire messages captured by KM003C.
 */
object UsbPdDecoder {

    private val CONTROL_MSG_NAMES = mapOf(
        1 to "GoodCRC",
        2 to "GotoMin",
        3 to "Accept",
        4 to "Reject",
        5 to "Ping",
        6 to "PS_RDY",
        7 to "Get_Source_Cap",
        8 to "Get_Sink_Cap",
        9 to "DR_Swap",
        10 to "PR_Swap",
        11 to "VCONN_Swap",
        12 to "Wait",
        13 to "Soft_Reset",
        14 to "Data_Reset",
        15 to "Data_Reset_Complete",
        16 to "Not_Supported",
        17 to "Get_Source_Cap_Ext",
        18 to "Get_Status",
        19 to "FR_Swap",
        20 to "Get_PPS_Status",
        21 to "Get_Country_Codes",
        22 to "Get_Sink_Cap_Ext",
        23 to "Get_Source_Info",
        24 to "Get_Revision"
    )

    private val DATA_MSG_NAMES = mapOf(
        1 to "Source_Capabilities",
        2 to "Request",
        3 to "BIST",
        4 to "Sink_Capabilities",
        5 to "Battery_Status",
        6 to "Alert",
        7 to "Get_Country_Info",
        8 to "Enter_USB",
        9 to "EPR_Request",
        10 to "EPR_Mode",
        11 to "Source_Info",
        12 to "Revision",
        15 to "Vendor_Defined (VDM)"
    )

    private val EXT_MSG_NAMES = mapOf(
        1 to "Source_Capabilities_Ext",
        2 to "Status",
        3 to "Get_Battery_Cap",
        4 to "Get_Battery_Status",
        5 to "Battery_Capabilities",
        6 to "Get_Manufacturer_Info",
        7 to "Manufacturer_Info",
        8 to "Security_Request",
        9 to "Security_Response",
        10 to "Firmware_Update_Req",
        11 to "Firmware_Update_Resp",
        12 to "PPS_Status",
        13 to "Country_Info",
        14 to "Country_Codes",
        15 to "Sink_Capabilities_Ext",
        16 to "Extended_Control",
        17 to "EPR_Source_Capabilities",
        18 to "EPR_Sink_Capabilities",
        30 to "Vendor_Defined_Ext"
    )

    fun sopName(sopType: Int): String = when (sopType) {
        0 -> "SOP"
        1 -> "SOP'"
        2 -> "SOP''"
        3 -> "SOP'_Dbg"
        4 -> "SOP''_Dbg"
        5 -> "HardReset"
        6 -> "CableReset"
        else -> "SOP($sopType)"
    }

    fun decodeWireMessage(
        id: Long,
        timestampMs: Long,
        sopType: Int,
        wire: ByteArray,
        vbusV: Double,
        ibusA: Double
    ): PdLogEntry {
        val hex = wire.joinToString(" ") { String.format(Locale.US, "%02X", it) }
        val sop = sopName(sopType)

        if (wire.size < 2) {
            return PdLogEntry(
                id = id,
                deviceTimestampMs = timestampMs,
                sop = sop,
                messageType = "Raw (${wire.size}B)",
                summary = hex,
                rawHex = hex,
                vbusV = vbusV,
                ibusA = ibusA
            )
        }

        val header = (wire[0].toInt() and 0xFF) or ((wire[1].toInt() and 0xFF) shl 8)
        val msgType = header and 0x1F
        val specRev = (header ushr 6) and 0x03
        val msgId = (header ushr 9) and 0x07
        val numDataObjects = (header ushr 12) and 0x07
        val isExtended = ((header ushr 15) and 0x01) != 0

        val revStr = when (specRev) {
            0 -> "PD2.0"
            1 -> "PD3.0"
            2 -> "PD3.1"
            else -> "PD3.x"
        }

        val typeName = when {
            isExtended -> EXT_MSG_NAMES[msgType] ?: "ExtMsg($msgType)"
            numDataObjects == 0 -> CONTROL_MSG_NAMES[msgType] ?: "CtrlMsg($msgType)"
            else -> DATA_MSG_NAMES[msgType] ?: "DataMsg($msgType)"
        }

        val pdos = mutableListOf<DecodedPdo>()
        val summary = buildString {
            append("$revStr #$msgId")
            if (!isExtended && numDataObjects > 0 && wire.size >= 2 + numDataObjects * 4) {
                val bb = ByteBuffer.wrap(wire, 2, numDataObjects * 4).order(ByteOrder.LITTLE_ENDIAN)
                when (msgType) {
                    1 -> { // Source_Capabilities
                        append(" • ${numDataObjects} PDOs: ")
                        val parts = mutableListOf<String>()
                        for (i in 0 until numDataObjects) {
                            val rawPdo = bb.getInt(i * 4)
                            val decoded = decodeSourcePdo(i + 1, rawPdo)
                            pdos.add(decoded)
                            parts.add("${decoded.voltageDesc}/${String.format(Locale.US, "%.2fA", decoded.maxCurrentA)}")
                        }
                        append(parts.joinToString(", "))
                    }
                    2, 9 -> { // Request / EPR_Request
                        val rdo = bb.getInt(0)
                        val objPos = (rdo ushr 28) and 0x0F
                        val opCurrentMa = ((rdo ushr 10) and 0x3FF) * 10
                        val maxCurrentMa = (rdo and 0x3FF) * 10
                        append(" • Pos #$objPos (Op ${opCurrentMa}mA / Max ${maxCurrentMa}mA)")
                    }
                    15 -> { // VDM
                        val vdmHdr = bb.getInt(0)
                        val svid = (vdmHdr ushr 16) and 0xFFFF
                        val structured = ((vdmHdr ushr 15) and 1) != 0
                        append(String.format(Locale.US, " • SVID=0x%04X (%s)", svid, if (structured) "Structured" else "Unstructured"))
                    }
                    else -> {
                        append(" • $numDataObjects objects")
                    }
                }
            } else if (isExtended) {
                append(" • Extended ($numDataObjects chunks)")
            }
        }

        return PdLogEntry(
            id = id,
            deviceTimestampMs = timestampMs,
            sop = sop,
            messageType = typeName,
            summary = summary,
            rawHex = hex,
            vbusV = vbusV,
            ibusA = ibusA,
            pdos = pdos
        )
    }

    fun decodeSourcePdo(index: Int, raw: Int): DecodedPdo {
        val pdoType = (raw ushr 30) and 0x03
        return when (pdoType) {
            0 -> { // Fixed Supply
                val vMv = ((raw ushr 10) and 0x3FF) * 50
                val iMa = (raw and 0x3FF) * 10
                val v = vMv / 1000.0
                val a = iMa / 1000.0
                DecodedPdo(
                    index = index,
                    type = "Fixed",
                    voltageDesc = String.format(Locale.US, "%.2fV", v),
                    maxCurrentA = a,
                    maxPowerW = v * a
                )
            }
            1 -> { // Battery
                val minV = ((raw ushr 10) and 0x3FF) * 0.05
                val maxV = ((raw ushr 20) and 0x3FF) * 0.05
                val maxW = (raw and 0x3FF) * 0.25
                DecodedPdo(
                    index = index,
                    type = "Battery",
                    voltageDesc = String.format(Locale.US, "%.1f-%.1fV", minV, maxV),
                    maxCurrentA = if (maxV > 0) maxW / maxV else 0.0,
                    maxPowerW = maxW
                )
            }
            2 -> { // Variable
                val minV = ((raw ushr 10) and 0x3FF) * 0.05
                val maxV = ((raw ushr 20) and 0x3FF) * 0.05
                val a = (raw and 0x3FF) * 0.01
                DecodedPdo(
                    index = index,
                    type = "Variable",
                    voltageDesc = String.format(Locale.US, "%.1f-%.1fV", minV, maxV),
                    maxCurrentA = a,
                    maxPowerW = maxV * a
                )
            }
            else -> { // Augmented PDO (APDO: SPR PPS or EPR AVS)
                val apdoSubtype = (raw ushr 28) and 0x03
                if (apdoSubtype == 0) {
                    // SPR PPS
                    val minV = ((raw ushr 8) and 0xFF) * 0.1
                    val maxV = ((raw ushr 17) and 0xFF) * 0.1
                    val maxA = (raw and 0x7F) * 0.05
                    DecodedPdo(
                        index = index,
                        type = "PPS",
                        voltageDesc = String.format(Locale.US, "%.1f-%.1fV", minV, maxV),
                        maxCurrentA = maxA,
                        maxPowerW = maxV * maxA
                    )
                } else {
                    // EPR AVS
                    val minV = ((raw ushr 8) and 0xFF) * 0.1
                    val maxV = ((raw ushr 17) and 0x1FF) * 0.1
                    val pdpW = (raw and 0xFF).toDouble()
                    DecodedPdo(
                        index = index,
                        type = "EPR AVS",
                        voltageDesc = String.format(Locale.US, "%.1f-%.1fV", minV, maxV),
                        maxCurrentA = if (maxV > 0) pdpW / maxV else 0.0,
                        maxPowerW = pdpW
                    )
                }
            }
        }
    }
}
