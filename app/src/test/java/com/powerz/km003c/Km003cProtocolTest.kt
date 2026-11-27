package com.powerz.km003c

import com.powerz.km003c.protocol.Km003cProtocol
import com.powerz.km003c.protocol.PdmSerialProtocol
import com.powerz.km003c.protocol.UsbPdDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Km003cProtocolTest {

    @Test
    fun testControlHeaderBitfieldEncoding() {
        // GetData(ATT_ADC = 0x0001) -> bytes 2..3 must be 0x02, 0x00 (shifted left by 1)
        val adcReq = Km003cProtocol.buildGetData(tid = 0x05, attributeMask = Km003cProtocol.ATT_ADC)
        assertEquals(4, adcReq.size)
        assertEquals(0x0C.toByte(), adcReq[0])
        assertEquals(0x05.toByte(), adcReq[1])
        assertEquals(0x02.toByte(), adcReq[2])
        assertEquals(0x00.toByte(), adcReq[3])

        // GetData(ADC | PD = 0x0011) -> bytes 2..3 = 0x22, 0x00
        val bothReq = Km003cProtocol.buildGetData(
            tid = 0x12,
            attributeMask = Km003cProtocol.ATT_ADC or Km003cProtocol.ATT_PD_PACKET
        )
        assertEquals(0x22.toByte(), bothReq[2])
        assertEquals(0x00.toByte(), bothReq[3])

        // StartGraph(rate=2 -> 50 SPS) -> byte 2 = 0x04
        val startReq = Km003cProtocol.buildStartGraph(tid = 0x07, rateIndex = 2)
        assertEquals(0x0E.toByte(), startReq[0])
        assertEquals(0x04.toByte(), startReq[2])

        val parsed = Km003cProtocol.parseControlHeader(bothReq)!!
        assertEquals(Km003cProtocol.CMD_GET_DATA, parsed.type)
        assertEquals(0x12, parsed.transactionId)
        assertEquals(0x0011, parsed.attribute)
    }

    @Test
    fun testPutDataAndAdcParsing() {
        // Build synthetic PutData (0x41) with 44-byte ADC logical packet
        val adcPayload = ByteArray(44)
        val bb = ByteBuffer.wrap(adcPayload).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(0, 9_012_000)   // 9.012 V
        bb.putInt(4, -2_500_000)  // -2.500 A (reverse flow)
        bb.putInt(8, 9_010_000)
        bb.putInt(12, -2_490_000)
        bb.putShort(24, (32 * 128 + 64).toShort()) // 32.5 C
        bb.putShort(26, 16500)    // CC1 = 1.650 V (0.1 mV units)
        bb.putShort(28, 100)      // CC2 = 0.010 V
        bb.putShort(30, 6000)     // D+ = 0.600 V
        bb.putShort(32, 200)      // D- = 0.020 V
        bb.putShort(34, 33000.toShort()) // VDD = 3.300 V

        val packet = ByteArray(4 + 4 + 44)
        packet[0] = Km003cProtocol.CMD_PUT_DATA.toByte()
        packet[1] = 0x01
        // Extended header: att=1, next=0, chunk=0, size=44
        val ext = (Km003cProtocol.ATT_ADC and 0x7FFF) or (44 shl 22)
        ByteBuffer.wrap(packet, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(ext)
        System.arraycopy(adcPayload, 0, packet, 8, 44)

        val logical = Km003cProtocol.parsePutData(packet)
        assertEquals(1, logical.size)
        assertEquals(Km003cProtocol.ATT_ADC, logical[0].attribute)
        assertEquals(44, logical[0].size)

        val sample = Km003cProtocol.parseAdcPayload(logical[0].payload)!!
        assertEquals(9.012, sample.vbusV, 0.0001)
        assertEquals(-2.500, sample.ibusA, 0.0001)
        assertEquals(22.53, sample.powerW, 0.01)
        assertEquals(32.5, sample.tempC, 0.01)
        assertEquals(1.65, sample.cc1V, 0.001)
        assertEquals(0.60, sample.dpV, 0.001)
    }

    @Test
    fun testMemoryReadAndStreamingAuth() {
        val memReq = Km003cProtocol.buildMemoryRead(0x03, Km003cProtocol.ADDR_HARDWARE_ID, 12)
        assertEquals(36, memReq.size)
        assertEquals(0x44.toByte(), memReq[0])
        assertEquals(0x01.toByte(), memReq[2])
        assertEquals(0x01.toByte(), memReq[3])

        // Decrypt our own encrypted request payload to confirm AES-128-ECB round-trip
        val decrypted = Km003cProtocol.decryptMemoryRead(memReq.copyOfRange(4, 36))
        val bb = ByteBuffer.wrap(decrypted).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(Km003cProtocol.ADDR_HARDWARE_ID.toInt(), bb.getInt(0))
        assertEquals(12, bb.getInt(4))
        assertEquals(-1, bb.getInt(8))

        val hwId = ByteArray(12) { (it + 1).toByte() }
        val authReq = Km003cProtocol.buildStreamingAuth(0x04, hwId)
        assertEquals(36, authReq.size)
        assertEquals(0x4C.toByte(), authReq[0])
        assertEquals(0x00.toByte(), authReq[2])
        assertEquals(0x02.toByte(), authReq[3])

        assertTrue(Km003cProtocol.isStreamingAuthSuccess(byteArrayOf(0x4C, 0x00, 0x03, 0x02)))
        assertFalse(Km003cProtocol.isStreamingAuthSuccess(byteArrayOf(0x4C, 0x00, 0x01, 0x02)))
    }

    @Test
    fun testUsbPdSourcePdoDecoding() {
        // Fixed 20V 3.25A: voltage=400 (20000/50) << 10 | current=325 (3250/10)
        val rawFixed = (400 shl 10) or 325
        val fixed = UsbPdDecoder.decodeSourcePdo(4, rawFixed)
        assertEquals("Fixed", fixed.type)
        assertEquals("20.00V", fixed.voltageDesc)
        assertEquals(3.25, fixed.maxCurrentA, 0.001)
        assertEquals(65.0, fixed.maxPowerW, 0.01)

        // PPS 3.3V-11.0V 5.0A: type=3 (bits 30..31), subtype=0, min=33 << 8, max=110 << 17, cur=100 (5.0/0.05)
        val rawPps = (3 shl 30) or (110 shl 17) or (33 shl 8) or 100
        val pps = UsbPdDecoder.decodeSourcePdo(5, rawPps)
        assertEquals("PPS", pps.type)
        assertEquals("3.3-11.0V", pps.voltageDesc)
        assertEquals(5.0, pps.maxCurrentA, 0.01)
    }

    @Test
    fun testPdmSerialPdoParsing() {
        val sampleOut = """
            PDO 4
            max power 45W
            Fixed:   5.00V 3.00A
            Fixed:   9.00V 3.00A
            PPS: 3.30-5.90V 5.10A
            PPS: 3.30-20.00V 8.00A
        """.trimIndent()
        val pdos = PdmSerialProtocol.parsePdPdoOutput(sampleOut)
        assertEquals(4, pdos.size)
        assertEquals("Fixed", pdos[0].type)
        assertEquals(5000, pdos[0].maxMv)
        assertEquals("PPS", pdos[2].type)
        assertEquals(3300, pdos[2].minMv)
        assertEquals(5900, pdos[2].maxMv)
        assertEquals(5100, pdos[2].maxMa)
        assertNotNull(pdos[3])
    }
}
