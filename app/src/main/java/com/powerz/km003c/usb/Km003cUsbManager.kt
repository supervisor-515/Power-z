package com.powerz.km003c.usb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import com.powerz.km003c.model.DeviceInfo
import com.powerz.km003c.protocol.Km003cProtocol
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android USB Host (OTG) transport for ChargerLAB POWER-Z KM003C / KM002C.
 * Supports:
 *  - Interface 0 (Vendor Specific Bulk: EP 0x01 OUT, 0x81 IN) — Primary full-speed binary protocol
 *  - Interface 3 (HID Interrupt: EP 0x05 OUT, 0x85 IN) — Fallback basic polling
 *  - Interface 1/2 (CDC ACM Virtual Serial: EP 0x02 OUT, 0x82 IN) — PDM protocol trigger console
 */
class Km003cUsbManager(context: Context) {

    companion object {
        private const val TAG = "Km003cUsb"
        private const val USB_TIMEOUT_MS = 1000
        private const val BULK_BUFFER_SIZE = 4096

        fun isSupportedDevice(device: UsbDevice): Boolean {
            return device.vendorId == Km003cProtocol.VID_CHARGERLAB &&
                (device.productId == Km003cProtocol.PID_KM003C ||
                    device.productId == Km003cProtocol.PID_KM002C)
        }
    }

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var device: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null

    // Primary binary protocol interface (Vendor Bulk IF0 or HID IF3)
    private var primaryInterface: UsbInterface? = null
    private var epOut: UsbEndpoint? = null
    private var epIn: UsbEndpoint? = null
    var isVendorBulk: Boolean = false
        private set

    // CDC ACM Virtual Serial interfaces (IF1 control, IF2 data)
    private var cdcControlInterface: UsbInterface? = null
    private var cdcDataInterface: UsbInterface? = null
    private var cdcOut: UsbEndpoint? = null
    private var cdcIn: UsbEndpoint? = null

    private val tidCounter = AtomicInteger(1)
    private val ioLock = Any()
    private val cdcLock = Any()

    var streamingAuthorized: Boolean = false
        private set

    fun findDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { isSupportedDevice(it) }
    }

    fun hasPermission(usbDevice: UsbDevice): Boolean = usbManager.hasPermission(usbDevice)

    fun getUsbManager(): UsbManager = usbManager

    val isConnected: Boolean
        get() = connection != null && epOut != null && epIn != null

    val hasCdcSerial: Boolean
        get() = connection != null && cdcOut != null && cdcIn != null

    private fun nextTid(): Int = tidCounter.getAndIncrement() and 0xFF

    /**
     * Open the USB device, claim interfaces, run Connect (0x02), read DeviceInfo & HardwareID,
     * and attempt StreamingAuth (0x4C) for high-speed AdcQueue streaming.
     */
    fun open(usbDevice: UsbDevice): Result<DeviceInfo> {
        close()
        val conn = usbManager.openDevice(usbDevice)
            ?: return Result.failure(IllegalStateException("USB 장치를 열 수 없습니다 (권한 확인 필요)."))

        device = usbDevice
        connection = conn

        // 1. Locate Vendor Bulk (Class 0xFF, IF0) or HID (Class 0x03, IF3)
        var vendorIf: UsbInterface? = null
        var hidIf: UsbInterface? = null
        var cdcCtrlIf: UsbInterface? = null
        var cdcDatIf: UsbInterface? = null

        for (i in 0 until usbDevice.interfaceCount) {
            val intf = usbDevice.getInterface(i)
            when (intf.interfaceClass) {
                UsbConstants.USB_CLASS_VENDOR_SPEC -> if (vendorIf == null) vendorIf = intf
                UsbConstants.USB_CLASS_HID -> if (hidIf == null) hidIf = intf
                UsbConstants.USB_CLASS_COMM -> if (cdcCtrlIf == null) cdcCtrlIf = intf
                UsbConstants.USB_CLASS_CDC_DATA -> if (cdcDatIf == null) cdcDatIf = intf
            }
        }

        // Claim primary interface (prefer Vendor Bulk)
        var claimed = false
        if (vendorIf != null && conn.claimInterface(vendorIf, true)) {
            val (outEp, inEp) = findEndpoints(vendorIf)
            if (outEp != null && inEp != null) {
                primaryInterface = vendorIf
                epOut = outEp
                epIn = inEp
                isVendorBulk = true
                claimed = true
                Log.i(TAG, "Claimed Vendor Bulk interface ${vendorIf.id}")
            } else {
                conn.releaseInterface(vendorIf)
            }
        }

        if (!claimed && hidIf != null && conn.claimInterface(hidIf, true)) {
            val (outEp, inEp) = findEndpoints(hidIf)
            if (outEp != null && inEp != null) {
                primaryInterface = hidIf
                epOut = outEp
                epIn = inEp
                isVendorBulk = false
                claimed = true
                Log.i(TAG, "Claimed HID interface ${hidIf.id}")
            } else {
                conn.releaseInterface(hidIf)
            }
        }

        if (!claimed) {
            close()
            return Result.failure(IllegalStateException("KM003C USB 인터페이스를 점유할 수 없습니다."))
        }

        // Claim optional CDC ACM serial interfaces for PDM trigger
        if (cdcDatIf != null && conn.claimInterface(cdcDatIf, true)) {
            val (outEp, inEp) = findEndpoints(cdcDatIf)
            if (outEp != null && inEp != null) {
                cdcDataInterface = cdcDatIf
                cdcOut = outEp
                cdcIn = inEp
                if (cdcCtrlIf != null && conn.claimInterface(cdcCtrlIf, true)) {
                    cdcControlInterface = cdcCtrlIf
                    configureCdcAcm(conn, cdcCtrlIf.id)
                }
                Log.i(TAG, "Claimed CDC ACM serial interface ${cdcDatIf.id}")
            } else {
                conn.releaseInterface(cdcDatIf)
            }
        }

        // 2. Send Connect (0x02)
        val connectResp = transact(Km003cProtocol.buildConnect(nextTid()))
        if (connectResp == null) {
            close()
            return Result.failure(IllegalStateException("KM003C Connect 응답이 없습니다."))
        }
        val hdr = Km003cProtocol.parseControlHeader(connectResp)
        Log.i(TAG, "Connect response: type=0x${hdr?.type?.toString(16)}")

        // 3. Read HardwareID & Device Info over MemoryRead (Vendor Bulk only)
        var hwVer = "-"
        var fwVer = "-"
        var hwIdHex = "-"
        streamingAuthorized = false

        if (isVendorBulk) {
            val hwId = readMemoryBlock(Km003cProtocol.ADDR_HARDWARE_ID, 12)
            if (hwId != null && hwId.size >= 12) {
                hwIdHex = hwId.take(12).joinToString("") { "%02X".format(it) }
                val authReq = Km003cProtocol.buildStreamingAuth(nextTid(), hwId)
                val authResp = transact(authReq)
                if (authResp != null && Km003cProtocol.isStreamingAuthSuccess(authResp)) {
                    streamingAuthorized = true
                    Log.i(TAG, "StreamingAuth succeeded!")
                } else {
                    Log.w(TAG, "StreamingAuth returned non-0x0203; falling back to Simple ADC polling")
                }
            }

            val devInfoBytes = readMemoryBlock(Km003cProtocol.ADDR_DEVICE_INFO, 64)
            if (devInfoBytes != null) {
                hwVer = extractPrintableSummary(devInfoBytes)
            }
            val fwInfoBytes = readMemoryBlock(Km003cProtocol.ADDR_FIRMWARE_INFO, 64)
            if (fwInfoBytes != null) {
                fwVer = extractPrintableSummary(fwInfoBytes)
            }
        }

        val info = DeviceInfo(
            productName = usbDevice.productName ?: if (usbDevice.productId == Km003cProtocol.PID_KM002C) "POWER-Z KM002C" else "POWER-Z KM003C",
            vendorId = usbDevice.vendorId,
            productId = usbDevice.productId,
            serialNumber = runCatching { usbDevice.serialNumber }.getOrNull() ?: "-",
            hardwareVersion = hwVer,
            firmwareVersion = fwVer,
            hardwareIdHex = hwIdHex,
            streamingAuthorized = streamingAuthorized,
            transportMode = if (isVendorBulk) "Vendor Bulk (IF0)" else "HID Interrupt (IF3)",
            hasCdcSerial = hasCdcSerial
        )
        return Result.success(info)
    }

    private fun findEndpoints(intf: UsbInterface): Pair<UsbEndpoint?, UsbEndpoint?> {
        var outEp: UsbEndpoint? = null
        var inEp: UsbEndpoint? = null
        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            if (ep.direction == UsbConstants.USB_DIR_OUT && outEp == null) {
                outEp = ep
            } else if (ep.direction == UsbConstants.USB_DIR_IN && inEp == null) {
                inEp = ep
            }
        }
        return outEp to inEp
    }

    private fun configureCdcAcm(conn: UsbDeviceConnection, ctrlInterfaceId: Int) {
        // SET_LINE_CODING: 115200 baud, 1 stop bit, no parity, 8 data bits
        val lineCoding = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(115200)
            .put(0.toByte())
            .put(0.toByte())
            .put(8.toByte())
            .array()
        conn.controlTransfer(0x21, 0x20, 0, ctrlInterfaceId, lineCoding, lineCoding.size, 500)
        // SET_CONTROL_LINE_STATE: DTR=1 (bit 0) | RTS=1 (bit 1) = 0x03
        conn.controlTransfer(0x21, 0x22, 0x03, ctrlInterfaceId, null, 0, 500)
    }

    /**
     * Send a command packet and read the response over the primary interface.
     */
    fun transact(request: ByteArray, timeoutMs: Int = USB_TIMEOUT_MS): ByteArray? = synchronized(ioLock) {
        val conn = connection ?: return null
        val outEp = epOut ?: return null
        val inEp = epIn ?: return null

        val txData = if (!isVendorBulk && request.size < 64) {
            // HID reports are fixed 64 bytes
            request.copyOf(64)
        } else {
            request
        }

        val sent = conn.bulkTransfer(outEp, txData, txData.size, timeoutMs)
        if (sent < 0) {
            Log.w(TAG, "USB OUT failed: $sent")
            return null
        }

        val rxBuf = ByteArray(BULK_BUFFER_SIZE)
        val received = conn.bulkTransfer(inEp, rxBuf, rxBuf.size, timeoutMs)
        if (received <= 0) {
            return null
        }
        return rxBuf.copyOf(received)
    }

    /**
     * Read encrypted memory block via MemoryRead (0x44) and decrypt with AES-128-ECB Key 0.
     */
    fun readMemoryBlock(address: Long, size: Int): ByteArray? = synchronized(ioLock) {
        val conn = connection ?: return null
        val outEp = epOut ?: return null
        val inEp = epIn ?: return null

        val req = Km003cProtocol.buildMemoryRead(nextTid(), address, size)
        if (conn.bulkTransfer(outEp, req, req.size, USB_TIMEOUT_MS) < 0) return null

        // First packet: confirmation (0xC4 TID 01 01 + 16-byte confirm)
        val confirmBuf = ByteArray(256)
        val confirmLen = conn.bulkTransfer(inEp, confirmBuf, confirmBuf.size, USB_TIMEOUT_MS)
        if (confirmLen < 4) return null
        val respType = confirmBuf[0].toInt() and 0x7F
        if (respType != Km003cProtocol.CMD_MEMORY_READ) {
            Log.w(TAG, "MemoryRead unexpected confirm byte0=0x${(confirmBuf[0].toInt() and 0xFF).toString(16)}")
            return null
        }

        // Subsequent transfer(s): raw ciphertext, multiple of 16 bytes
        val expectedCipherLen = ((size + 15) / 16) * 16
        val cipherData = ByteArray(expectedCipherLen)
        var collected = 0
        val tmp = ByteArray(BULK_BUFFER_SIZE)
        var attempts = 0
        while (collected < expectedCipherLen && attempts < 10) {
            attempts++
            val n = conn.bulkTransfer(inEp, tmp, tmp.size, USB_TIMEOUT_MS)
            if (n <= 0) break
            val copyLen = minOf(n, expectedCipherLen - collected)
            System.arraycopy(tmp, 0, cipherData, collected, copyLen)
            collected += copyLen
        }
        if (collected < expectedCipherLen) return null

        val plain = Km003cProtocol.decryptMemoryRead(cipherData)
        return plain.copyOf(minOf(size, plain.size))
    }

    fun getData(attributeMask: Int): List<Km003cProtocol.LogicalPacket>? {
        val resp = transact(Km003cProtocol.buildGetData(nextTid(), attributeMask), timeoutMs = 400)
            ?: return null
        return Km003cProtocol.parsePutData(resp)
    }

    fun startGraph(rateIndex: Int): Boolean {
        val resp = transact(Km003cProtocol.buildStartGraph(nextTid(), rateIndex)) ?: return false
        val hdr = Km003cProtocol.parseControlHeader(resp)
        return hdr?.type == Km003cProtocol.CMD_ACCEPT
    }

    fun stopGraph(): Boolean {
        val resp = transact(Km003cProtocol.buildStopGraph(nextTid())) ?: return false
        val hdr = Km003cProtocol.parseControlHeader(resp)
        return hdr?.type == Km003cProtocol.CMD_ACCEPT
    }

    fun enablePdMonitor(): Boolean {
        val resp = transact(Km003cProtocol.buildEnablePdMonitor(nextTid())) ?: return false
        val hdr = Km003cProtocol.parseControlHeader(resp)
        return hdr?.type == Km003cProtocol.CMD_ACCEPT
    }

    /**
     * Send an ASCII command to the CDC ACM virtual serial port (PDM protocol trigger)
     * and collect the text response.
     */
    fun sendCdcCommand(command: String, readWaitMs: Long = 350): Result<String> = synchronized(cdcLock) {
        val conn = connection ?: return Result.failure(IllegalStateException("USB 미연결"))
        val outEp = cdcOut ?: return Result.failure(IllegalStateException("CDC 가상 시리얼 인터페이스 없음"))
        val inEp = cdcIn ?: return Result.failure(IllegalStateException("CDC 가상 시리얼 인터페이스 없음"))

        // Drain any stale bytes first
        val drainBuf = ByteArray(512)
        conn.bulkTransfer(inEp, drainBuf, drainBuf.size, 30)

        val payload = (command.trim() + "\r\n").toByteArray(Charsets.US_ASCII)
        val sent = conn.bulkTransfer(outEp, payload, payload.size, 500)
        if (sent < 0) {
            return Result.failure(IllegalStateException("CDC 명령 전송 실패 ($sent)"))
        }

        val sb = StringBuilder()
        val rxBuf = ByteArray(1024)
        val deadline = System.currentTimeMillis() + readWaitMs
        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(50)
            val n = conn.bulkTransfer(inEp, rxBuf, rxBuf.size, minOf(remaining, 150))
            if (n > 0) {
                sb.append(String(rxBuf, 0, n, Charsets.UTF_8))
            } else if (sb.isNotEmpty()) {
                break
            }
        }
        return Result.success(sb.toString().trim())
    }

    fun close() {
        synchronized(ioLock) {
            val conn = connection
            if (conn != null) {
                runCatching {
                    if (epOut != null && epIn != null) {
                        transact(Km003cProtocol.buildDisconnect(nextTid()), timeoutMs = 200)
                    }
                }
                primaryInterface?.let { runCatching { conn.releaseInterface(it) } }
                cdcControlInterface?.let { runCatching { conn.releaseInterface(it) } }
                cdcDataInterface?.let { runCatching { conn.releaseInterface(it) } }
                runCatching { conn.close() }
            }
            connection = null
            device = null
            primaryInterface = null
            epOut = null
            epIn = null
            cdcControlInterface = null
            cdcDataInterface = null
            cdcOut = null
            cdcIn = null
            streamingAuthorized = false
        }
    }

    private fun extractPrintableSummary(bytes: ByteArray): String {
        val strings = mutableListOf<String>()
        val cur = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E) {
                cur.append(c.toChar())
            } else {
                if (cur.length >= 3) strings.add(cur.toString().trim())
                cur.clear()
            }
        }
        if (cur.length >= 3) strings.add(cur.toString().trim())
        return strings.take(3).joinToString(" / ").ifEmpty { "-" }
    }
}
