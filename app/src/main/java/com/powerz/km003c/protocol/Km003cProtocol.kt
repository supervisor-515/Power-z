package com.powerz.km003c.protocol

import com.powerz.km003c.model.TelemetrySample
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Wire-level protocol implementation for ChargerLAB POWER-Z KM003C / KM002C.
 * Verified against POWER-Z PC Software V2.5.1 (Mtools.exe) and captured USB traffic.
 */
object Km003cProtocol {

    // USB identifiers
    const val VID_CHARGERLAB = 0x5FC9
    const val PID_KM003C = 0x0063
    const val PID_KM002C = 0x0061

    // Command types (bits 0..6 of byte 0)
    const val CMD_CONNECT = 0x02
    const val CMD_DISCONNECT = 0x03
    const val CMD_ACCEPT = 0x05
    const val CMD_REJECT = 0x06
    const val CMD_GET_DATA = 0x0C
    const val CMD_START_GRAPH = 0x0E
    const val CMD_STOP_GRAPH = 0x0F
    const val CMD_ENABLE_PD_MONITOR = 0x10
    const val CMD_DISABLE_PD_MONITOR = 0x11
    const val CMD_NOT_READABLE = 0x27
    const val CMD_PUT_DATA = 0x41
    const val CMD_MEMORY_READ = 0x44
    const val CMD_STREAMING_AUTH = 0x4C

    // Attribute bitmasks (15-bit logical values placed at bits 17..31 of control header)
    const val ATT_ADC = 0x0001
    const val ATT_ADC_QUEUE = 0x0002
    const val ATT_SETTINGS = 0x0008
    const val ATT_PD_PACKET = 0x0010
    const val ATT_PD_TRACE = 0x0020
    const val ATT_LOG_METADATA = 0x0200

    // Memory addresses for MemoryRead (0x44)
    const val ADDR_DEVICE_INFO = 0x00000420L
    const val ADDR_FIRMWARE_INFO = 0x00004420L
    const val ADDR_SERIAL_UUID = 0x03000C00L
    const val ADDR_HARDWARE_ID = 0x40010450L

    // AES-128-ECB keys verified in POWER-Z V2.5.1 Mtools.exe
    private val KEY_MEMORY_READ = "Lh2yfB7n6X7d9a5Z".toByteArray(Charsets.US_ASCII)
    private val KEY_STREAMING_AUTH_ENC = "Fa0b4tA25f4R038a".toByteArray(Charsets.US_ASCII)

    data class ControlHeader(
        val type: Int,
        val reserved: Boolean,
        val transactionId: Int,
        val attribute: Int,
        val rawWordHi: Int
    )

    data class LogicalPacket(
        val attribute: Int,
        val hasNext: Boolean,
        val chunk: Int,
        val size: Int,
        val payload: ByteArray
    )

    data class PdPreamble(
        val timestampMs: Long,
        val vbusMv: Int,
        val ibusMa: Int,
        val cc1Mv: Int,
        val cc2Mv: Int
    )

    sealed class PdRawEvent {
        data class ConnectionEvent(val timestampMs: Long, val connected: Boolean) : PdRawEvent()
        data class WireMessage(val timestampMs: Long, val sopType: Int, val wireData: ByteArray) : PdRawEvent()
    }

    data class PdPacketData(
        val preamble: PdPreamble,
        val events: List<PdRawEvent>
    )

    /**
     * Build a standard 4-byte control header:
     * Bits 0..6:   type (7 bits)
     * Bit 7:       reserved (0)
     * Bits 8..15:  transactionId (8 bits)
     * Bit 16:      unused (0)
     * Bits 17..31: attribute (15 bits) -> wire bytes 2..3 = (attribute << 1) LE
     */
    fun buildControlHeader(type: Int, tid: Int, attribute: Int = 0): ByteArray {
        val word = (type and 0x7F) or
            ((tid and 0xFF) shl 8) or
            ((attribute and 0x7FFF) shl 17)
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(word).array()
    }

    fun buildConnect(tid: Int): ByteArray = buildControlHeader(CMD_CONNECT, tid, 0)
    fun buildDisconnect(tid: Int): ByteArray = buildControlHeader(CMD_DISCONNECT, tid, 0)
    fun buildGetData(tid: Int, attributeMask: Int): ByteArray = buildControlHeader(CMD_GET_DATA, tid, attributeMask)
    fun buildStartGraph(tid: Int, rateIndex: Int): ByteArray = buildControlHeader(CMD_START_GRAPH, tid, rateIndex and 0x03)
    fun buildStopGraph(tid: Int): ByteArray = buildControlHeader(CMD_STOP_GRAPH, tid, 0)
    fun buildEnablePdMonitor(tid: Int): ByteArray = buildControlHeader(CMD_ENABLE_PD_MONITOR, tid, 1)
    fun buildDisablePdMonitor(tid: Int): ByteArray = buildControlHeader(CMD_DISABLE_PD_MONITOR, tid, 0)

    fun parseControlHeader(data: ByteArray, offset: Int = 0): ControlHeader? {
        if (data.size - offset < 4) return null
        val word = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val type = word and 0x7F
        val reserved = ((word ushr 7) and 1) != 0
        val tid = (word ushr 8) and 0xFF
        val rawWordHi = (word ushr 16) and 0xFFFF
        val attr = (word ushr 17) and 0x7FFF
        return ControlHeader(type, reserved, tid, attr, rawWordHi)
    }

    /**
     * Build a 36-byte MemoryRead (0x44) request:
     * Header: 44 TID 01 01 (raw word 0x0101, unshifted)
     * Payload (32 bytes plaintext, AES-128-ECB encrypted with Key 0):
     *   [0..3]   address (u32 LE)
     *   [4..7]   size (u32 LE)
     *   [8..11]  0xFFFFFFFF
     *   [12..15] CRC32(bytes 0..11)
     *   [16..31] 0xFF padding
     */
    fun buildMemoryRead(tid: Int, address: Long, size: Int): ByteArray {
        val plain = ByteArray(32) { 0xFF.toByte() }
        val bb = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(0, address.toInt())
        bb.putInt(4, size)
        bb.putInt(8, -1) // 0xFFFFFFFF

        val crc = CRC32()
        crc.update(plain, 0, 12)
        bb.putInt(12, crc.value.toInt())

        val encrypted = aes128EcbEncrypt(plain, KEY_MEMORY_READ)
        val packet = ByteArray(4 + 32)
        packet[0] = CMD_MEMORY_READ.toByte()
        packet[1] = (tid and 0xFF).toByte()
        packet[2] = 0x01
        packet[3] = 0x01
        System.arraycopy(encrypted, 0, packet, 4, 32)
        return packet
    }

    fun decryptMemoryRead(ciphertext: ByteArray): ByteArray {
        val alignedLen = (ciphertext.size / 16) * 16
        if (alignedLen == 0) return ByteArray(0)
        return aes128EcbDecrypt(ciphertext.copyOf(alignedLen), KEY_MEMORY_READ)
    }

    /**
     * Build a 36-byte StreamingAuth (0x4C) request:
     * Header: 4C TID 00 02 (raw word 0x0200, unshifted)
     * Plaintext (32 bytes, AES-128-ECB encrypted with Key 3):
     *   [0..7]   timestamp (any)
     *   [8..19]  12-byte HardwareID from 0x40010450
     *   [20..31] padding
     */
    fun buildStreamingAuth(tid: Int, hardwareId: ByteArray): ByteArray {
        require(hardwareId.size >= 12) { "HardwareID must be at least 12 bytes" }
        val plain = ByteArray(32)
        val bb = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN)
        bb.putLong(0, System.currentTimeMillis())
        System.arraycopy(hardwareId, 0, plain, 8, 12)

        val encrypted = aes128EcbEncrypt(plain, KEY_STREAMING_AUTH_ENC)
        val packet = ByteArray(4 + 32)
        packet[0] = CMD_STREAMING_AUTH.toByte()
        packet[1] = (tid and 0xFF).toByte()
        packet[2] = 0x00
        packet[3] = 0x02
        System.arraycopy(encrypted, 0, packet, 4, 32)
        return packet
    }

    /**
     * Check if a StreamingAuth (0x4C) response succeeded (raw word hi == 0x0203).
     */
    fun isStreamingAuthSuccess(response: ByteArray): Boolean {
        val hdr = parseControlHeader(response) ?: return false
        return hdr.type == CMD_STREAMING_AUTH && hdr.rawWordHi == 0x0203
    }

    /**
     * Parse all chained logical packets inside a PutData (0x41) response.
     * Extended header (4 bytes LE):
     *   Bits 0..14:  att (15 bits)
     *   Bit 15:      next (1 bit)
     *   Bits 16..21: chunk (6 bits)
     *   Bits 22..31: size (10 bits)
     */
    fun parsePutData(packet: ByteArray, length: Int = packet.size): List<LogicalPacket> {
        if (length < 8) return emptyList()
        val mainType = packet[0].toInt() and 0x7F
        if (mainType != CMD_PUT_DATA) return emptyList()

        val result = mutableListOf<LogicalPacket>()
        var offset = 4
        while (offset + 4 <= length) {
            val ext = ByteBuffer.wrap(packet, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
            offset += 4
            val att = ext and 0x7FFF
            val hasNext = ((ext ushr 15) and 1) != 0
            val chunk = (ext ushr 16) and 0x3F
            val size = (ext ushr 22) and 0x3FF

            if (att == 0 && size == 0 && !hasNext) break
            if (offset + size > length) {
                // Clamp gracefully if truncated
                val avail = (length - offset).coerceAtLeast(0)
                val payload = packet.copyOfRange(offset, offset + avail)
                result.add(LogicalPacket(att, hasNext, chunk, avail, payload))
                break
            }
            val payload = packet.copyOfRange(offset, offset + size)
            offset += size
            result.add(LogicalPacket(att, hasNext, chunk, size, payload))
            if (!hasNext) break
        }
        return result
    }

    /**
     * Parse a 44-byte ADC payload (attribute 0x0001).
     */
    fun parseAdcPayload(payload: ByteArray, nowMs: Long = System.currentTimeMillis()): TelemetrySample? {
        if (payload.size < 36) return null
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val vbusUv = bb.getInt(0)
        val ibusUa = bb.getInt(4)
        val vbusAvgUv = bb.getInt(8)
        val ibusAvgUa = bb.getInt(12)
        val tempRaw = bb.getShort(24).toInt()
        val cc1TenthMv = bb.getShort(26).toInt() and 0xFFFF
        val cc2TenthMv = bb.getShort(28).toInt() and 0xFFFF
        val dpTenthMv = bb.getShort(30).toInt() and 0xFFFF
        val dmTenthMv = bb.getShort(32).toInt() and 0xFFFF
        val vddTenthMv = bb.getShort(34).toInt() and 0xFFFF

        val vbusV = vbusUv / 1_000_000.0
        val ibusA = ibusUa / 1_000_000.0
        val powerW = abs(vbusV * ibusA)

        return TelemetrySample(
            timestampMs = nowMs,
            vbusV = vbusV,
            ibusA = ibusA,
            powerW = powerW,
            vbusAvgV = vbusAvgUv / 1_000_000.0,
            ibusAvgA = ibusAvgUa / 1_000_000.0,
            tempC = tempRaw / 128.0,
            cc1V = cc1TenthMv / 10_000.0,
            cc2V = cc2TenthMv / 10_000.0,
            dpV = dpTenthMv / 10_000.0,
            dmV = dmTenthMv / 10_000.0,
            vddV = vddTenthMv / 10_000.0
        )
    }

    /**
     * Parse an AdcQueue payload (attribute 0x0002), 20 bytes per sample.
     * At 2 SPS (rateIndex == 0), CC/D+/D- are 0.1 mV/count; at faster rates, 1 mV/count.
     */
    fun parseAdcQueuePayload(
        payload: ByteArray,
        rateIndex: Int,
        lastTempC: Double = 0.0,
        lastVddV: Double = 0.0,
        nowMs: Long = System.currentTimeMillis()
    ): List<TelemetrySample> {
        val sampleSize = 20
        val count = payload.size / sampleSize
        if (count <= 0) return emptyList()

        val auxDivisor = if (rateIndex == 0) 10_000.0 else 1_000.0
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val out = ArrayList<TelemetrySample>(count)
        for (i in 0 until count) {
            val base = i * sampleSize
            val seq = bb.getShort(base).toInt() and 0xFFFF
            val vbusUv = bb.getInt(base + 4)
            val ibusUa = bb.getInt(base + 8)
            val cc1Raw = bb.getShort(base + 12).toInt() and 0xFFFF
            val cc2Raw = bb.getShort(base + 14).toInt() and 0xFFFF
            val dpRaw = bb.getShort(base + 16).toInt() and 0xFFFF
            val dmRaw = bb.getShort(base + 18).toInt() and 0xFFFF

            val vbusV = vbusUv / 1_000_000.0
            val ibusA = ibusUa / 1_000_000.0
            out.add(
                TelemetrySample(
                    timestampMs = nowMs,
                    vbusV = vbusV,
                    ibusA = ibusA,
                    powerW = abs(vbusV * ibusA),
                    vbusAvgV = vbusV,
                    ibusAvgA = ibusA,
                    tempC = lastTempC,
                    cc1V = cc1Raw / auxDivisor,
                    cc2V = cc2Raw / auxDivisor,
                    dpV = dpRaw / auxDivisor,
                    dmV = dmRaw / auxDivisor,
                    vddV = lastVddV,
                    sequence = seq
                )
            )
        }
        return out
    }

    /**
     * Parse a PdPacket payload (attribute 0x0010):
     * [Preamble 12B][Event 1][Event 2]...
     */
    fun parsePdPacketPayload(payload: ByteArray): PdPacketData? {
        if (payload.size < 12) return null
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val preamble = PdPreamble(
            timestampMs = bb.getInt(0).toLong() and 0xFFFFFFFFL,
            vbusMv = bb.getShort(4).toInt() and 0xFFFF,
            ibusMa = bb.getShort(6).toInt(),
            cc1Mv = bb.getShort(8).toInt() and 0xFFFF,
            cc2Mv = bb.getShort(10).toInt() and 0xFFFF
        )

        val events = mutableListOf<PdRawEvent>()
        var offset = 12
        while (offset + 6 <= payload.size) {
            val firstByte = payload[offset].toInt() and 0xFF
            if (firstByte == 0x45) {
                // Connection / disconnection event (6 bytes total)
                val ts24 = (payload[offset + 1].toInt() and 0xFF) or
                    ((payload[offset + 2].toInt() and 0xFF) shl 8) or
                    ((payload[offset + 3].toInt() and 0xFF) shl 16)
                val code = payload[offset + 5].toInt() and 0xFF
                if (code == 0x21 || code == 0x22) {
                    events.add(PdRawEvent.ConnectionEvent(ts24.toLong(), connected = (code == 0x21)))
                }
                offset += 6
            } else {
                // Wrapped wire PD message: [size_flag:1][timestamp_ms:4 LE][sop:1][wire:N]
                val wireLen = (firstByte and 0x3F) - 5
                val ts = bb.getInt(offset + 1).toLong() and 0xFFFFFFFFL
                val sop = payload[offset + 5].toInt() and 0xFF
                offset += 6
                if (wireLen <= 0 || offset + wireLen > payload.size) {
                    break
                }
                val wire = payload.copyOfRange(offset, offset + wireLen)
                offset += wireLen
                events.add(PdRawEvent.WireMessage(ts, sop, wire))
            }
        }
        return PdPacketData(preamble, events)
    }

    private fun aes128EcbEncrypt(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    private fun aes128EcbDecrypt(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }
}
