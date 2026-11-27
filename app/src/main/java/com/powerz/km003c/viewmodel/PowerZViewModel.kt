package com.powerz.km003c.viewmodel

import android.app.Application
import android.content.Intent
import android.hardware.usb.UsbDevice
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.powerz.km003c.model.ConnectionState
import com.powerz.km003c.model.DeviceInfo
import com.powerz.km003c.model.GraphPoint
import com.powerz.km003c.model.PdLogEntry
import com.powerz.km003c.model.PdmPdoItem
import com.powerz.km003c.model.SampleRateMode
import com.powerz.km003c.model.SessionStats
import com.powerz.km003c.model.TelemetrySample
import com.powerz.km003c.protocol.Km003cProtocol
import com.powerz.km003c.protocol.PdmSerialProtocol
import com.powerz.km003c.protocol.UsbPdDecoder
import com.powerz.km003c.usb.Km003cSimulator
import com.powerz.km003c.usb.Km003cUsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

class PowerZViewModel(application: Application) : AndroidViewModel(application) {

    val usbManager = Km003cUsbManager(application)
    private val simulator = Km003cSimulator()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _statusMessage = MutableStateFlow("KM003C를 USB-C OTG로 연결하거나 시뮬레이터를 켜세요")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetrySample())
    val telemetry: StateFlow<TelemetrySample> = _telemetry.asStateFlow()

    private val _stats = MutableStateFlow(SessionStats())
    val stats: StateFlow<SessionStats> = _stats.asStateFlow()

    private val _graphPoints = MutableStateFlow<List<GraphPoint>>(emptyList())
    val graphPoints: StateFlow<List<GraphPoint>> = _graphPoints.asStateFlow()

    private val _sampleRate = MutableStateFlow(SampleRateMode.SIMPLE_10HZ)
    val sampleRate: StateFlow<SampleRateMode> = _sampleRate.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _pdCaptureEnabled = MutableStateFlow(true)
    val pdCaptureEnabled: StateFlow<Boolean> = _pdCaptureEnabled.asStateFlow()

    private val _pdLogs = MutableStateFlow<List<PdLogEntry>>(emptyList())
    val pdLogs: StateFlow<List<PdLogEntry>> = _pdLogs.asStateFlow()

    private val _pdmActive = MutableStateFlow(false)
    val pdmActive: StateFlow<Boolean> = _pdmActive.asStateFlow()

    private val _pdmConsoleLines = MutableStateFlow<List<String>>(emptyList())
    val pdmConsoleLines: StateFlow<List<String>> = _pdmConsoleLines.asStateFlow()

    private val _pdmPdos = MutableStateFlow<List<PdmPdoItem>>(emptyList())
    val pdmPdos: StateFlow<List<PdmPdoItem>> = _pdmPdos.asStateFlow()

    private var pollingJob: Job? = null
    private val pdLogIdGen = AtomicLong(1)

    // Internal session accumulation
    private var sessionStartMs = System.currentTimeMillis()
    private var lastSampleMs = 0L
    private val historyBuffer = ArrayDeque<GraphPoint>(3200)
    private val csvBuffer = ArrayList<TelemetrySample>(4096)

    // Callback to ask MainActivity to request USB permission
    var onRequestUsbPermission: ((UsbDevice) -> Unit)? = null

    fun scanAndConnect() {
        val dev = usbManager.findDevice()
        if (dev == null) {
            _connectionState.value = ConnectionState.DISCONNECTED
            _statusMessage.value = "KM003C/KM002C USB 장치가 감지되지 않았습니다 (VID:5FC9)"
            return
        }
        if (!usbManager.hasPermission(dev)) {
            _connectionState.value = ConnectionState.REQUESTING_PERMISSION
            _statusMessage.value = "USB 권한 요청 중..."
            onRequestUsbPermission?.invoke(dev)
        } else {
            connectToDevice(dev)
        }
    }

    fun onUsbPermissionResult(device: UsbDevice, granted: Boolean) {
        if (granted) {
            connectToDevice(device)
        } else {
            _connectionState.value = ConnectionState.DISCONNECTED
            _statusMessage.value = "USB 접근 권한이 거부되었습니다"
        }
    }

    fun onUsbDetached(device: UsbDevice) {
        if (Km003cUsbManager.isSupportedDevice(device) &&
            _connectionState.value != ConnectionState.SIMULATOR
        ) {
            disconnect("USB 연결이 해제되었습니다")
        }
    }

    fun connectToDevice(device: UsbDevice) {
        stopPolling()
        _connectionState.value = ConnectionState.CONNECTING
        _statusMessage.value = "KM003C 초기화 및 인증 중..."

        viewModelScope.launch(Dispatchers.IO) {
            val result = usbManager.open(device)
            result.onSuccess { info ->
                _deviceInfo.value = info
                _connectionState.value = if (usbManager.isVendorBulk) {
                    ConnectionState.CONNECTED_VENDOR
                } else {
                    ConnectionState.CONNECTED_HID
                }
                _statusMessage.value = buildString {
                    append("${info.productName} 연결됨")
                    if (info.streamingAuthorized) append(" • 고속 스트리밍 인증됨")
                }
                if (_pdCaptureEnabled.value && usbManager.isVendorBulk) {
                    usbManager.enablePdMonitor()
                }
                resetSession()
                startHardwareLoop()
            }.onFailure { err ->
                _connectionState.value = ConnectionState.ERROR
                _statusMessage.value = "연결 실패: ${err.message}"
            }
        }
    }

    fun startSimulator() {
        disconnect()
        _deviceInfo.value = simulator.deviceInfo()
        _connectionState.value = ConnectionState.SIMULATOR
        _statusMessage.value = "시뮬레이터 모드 실행 중 (실제 장비 없이 테스트)"
        _pdLogs.value = simulator.initialPdLogs()
        resetSession()

        pollingJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                if (!_isPaused.value) {
                    val sample = simulator.nextSample()
                    ingestSample(sample)
                }
                val intervalMs = when (_sampleRate.value) {
                    SampleRateMode.STREAM_2SPS -> 500L
                    SampleRateMode.SIMPLE_10HZ, SampleRateMode.STREAM_10SPS -> 100L
                    SampleRateMode.STREAM_50SPS -> 25L
                    SampleRateMode.STREAM_1000SPS -> 20L
                }
                delay(intervalMs)
            }
        }
    }

    fun disconnect(reason: String = "연결 해제됨") {
        stopPolling()
        viewModelScope.launch(Dispatchers.IO) {
            if (_pdmActive.value && usbManager.hasCdcSerial) {
                usbManager.sendCdcCommand(PdmSerialProtocol.CMD_PDM_CLOSE, 100)
            }
            if (usbManager.isConnected && _sampleRate.value.rateIndex != null) {
                usbManager.stopGraph()
            }
            usbManager.close()
        }
        _pdmActive.value = false
        _connectionState.value = ConnectionState.DISCONNECTED
        _statusMessage.value = reason
    }

    fun setSampleRate(mode: SampleRateMode) {
        val current = _sampleRate.value
        if (current == mode) return

        // If hardware doesn't have streaming auth, keep Simple mode
        if (_connectionState.value == ConnectionState.CONNECTED_HID && mode.rateIndex != null) {
            _statusMessage.value = "HID 모드에서는 Simple (10Hz)만 지원됩니다"
            return
        }
        if (_connectionState.value == ConnectionState.CONNECTED_VENDOR &&
            mode.rateIndex != null && !usbManager.streamingAuthorized
        ) {
            _statusMessage.value = "스트리밍 인증이 완료되지 않아 Simple 모드를 유지합니다"
            return
        }

        _sampleRate.value = mode
        if (_connectionState.value == ConnectionState.CONNECTED_VENDOR) {
            stopPolling()
            startHardwareLoop()
        }
    }

    fun togglePause() {
        _isPaused.value = !_isPaused.value
    }

    fun togglePdCapture(enabled: Boolean) {
        _pdCaptureEnabled.value = enabled
        if (_connectionState.value == ConnectionState.CONNECTED_VENDOR && enabled) {
            viewModelScope.launch(Dispatchers.IO) {
                usbManager.enablePdMonitor()
            }
        }
    }

    fun clearPdLogs() {
        _pdLogs.value = emptyList()
    }

    fun resetSession() {
        sessionStartMs = System.currentTimeMillis()
        lastSampleMs = 0L
        synchronized(historyBuffer) {
            historyBuffer.clear()
            csvBuffer.clear()
        }
        _graphPoints.value = emptyList()
        _stats.value = SessionStats()
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun startHardwareLoop() {
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            val mode = _sampleRate.value
            val useQueue = mode.rateIndex != null && usbManager.isVendorBulk && usbManager.streamingAuthorized
            if (useQueue) {
                usbManager.stopGraph()
                delay(20)
                val started = usbManager.startGraph(mode.rateIndex!!)
                if (!started) {
                    _sampleRate.value = SampleRateMode.SIMPLE_10HZ
                }
            }

            var lastTempC = 0.0
            var lastVddV = 0.0
            var loopCount = 0
            var consecutiveErrors = 0

            while (isActive && usbManager.isConnected) {
                if (_isPaused.value) {
                    delay(100)
                    continue
                }

                loopCount++
                val isQueueActive = _sampleRate.value.rateIndex != null &&
                    usbManager.isVendorBulk && usbManager.streamingAuthorized

                val attrMask = buildAttrMask(
                    isQueueActive = isQueueActive,
                    wantTempRefresh = (loopCount % 20 == 1),
                    wantPd = _pdCaptureEnabled.value
                )

                val packets = usbManager.getData(attrMask)
                if (packets == null) {
                    consecutiveErrors++
                    if (consecutiveErrors >= 15) {
                        withContext(Dispatchers.Main) {
                            disconnect("USB 통신 시간 초과로 연결이 종료되었습니다")
                        }
                        break
                    }
                    delay(50)
                    continue
                }
                consecutiveErrors = 0

                val nowMs = System.currentTimeMillis()
                for (lp in packets) {
                    when (lp.attribute) {
                        Km003cProtocol.ATT_ADC -> {
                            val sample = Km003cProtocol.parseAdcPayload(lp.payload, nowMs)
                            if (sample != null) {
                                lastTempC = sample.tempC
                                lastVddV = sample.vddV
                                if (!isQueueActive) {
                                    ingestSample(sample)
                                }
                            }
                        }
                        Km003cProtocol.ATT_ADC_QUEUE -> {
                            val rateIdx = _sampleRate.value.rateIndex ?: 1
                            val samples = Km003cProtocol.parseAdcQueuePayload(
                                payload = lp.payload,
                                rateIndex = rateIdx,
                                lastTempC = lastTempC,
                                lastVddV = lastVddV,
                                nowMs = nowMs
                            )
                            for (s in samples) {
                                ingestSample(s)
                            }
                        }
                        Km003cProtocol.ATT_PD_PACKET -> {
                            val pdData = Km003cProtocol.parsePdPacketPayload(lp.payload)
                            if (pdData != null && pdData.events.isNotEmpty()) {
                                val vbusV = pdData.preamble.vbusMv / 1000.0
                                val ibusA = pdData.preamble.ibusMa / 1000.0
                                val newEntries = pdData.events.map { ev ->
                                    when (ev) {
                                        is Km003cProtocol.PdRawEvent.ConnectionEvent -> PdLogEntry(
                                            id = pdLogIdGen.getAndIncrement(),
                                            deviceTimestampMs = ev.timestampMs,
                                            sop = "EVENT",
                                            messageType = if (ev.connected) "Connect" else "Disconnect",
                                            summary = if (ev.connected) "USB-C 연결 감지" else "USB-C 분리 감지",
                                            rawHex = if (ev.connected) "45 ... 21" else "45 ... 22",
                                            vbusV = vbusV,
                                            ibusA = ibusA,
                                            isEvent = true
                                        )
                                        is Km003cProtocol.PdRawEvent.WireMessage -> UsbPdDecoder.decodeWireMessage(
                                            id = pdLogIdGen.getAndIncrement(),
                                            timestampMs = ev.timestampMs,
                                            sopType = ev.sopType,
                                            wire = ev.wireData,
                                            vbusV = vbusV,
                                            ibusA = ibusA
                                        )
                                    }
                                }
                                if (newEntries.isNotEmpty()) {
                                    val updated = (_pdLogs.value + newEntries).takeLast(300)
                                    _pdLogs.value = updated
                                }
                            }
                        }
                    }
                }

                val pollDelayMs = when (_sampleRate.value) {
                    SampleRateMode.SIMPLE_10HZ -> 80L
                    SampleRateMode.STREAM_2SPS -> 200L
                    SampleRateMode.STREAM_10SPS -> 80L
                    SampleRateMode.STREAM_50SPS -> 40L
                    SampleRateMode.STREAM_1000SPS -> 20L
                }
                delay(pollDelayMs)
            }
        }
    }

    private fun buildAttrMask(
        isQueueActive: Boolean,
        wantTempRefresh: Boolean,
        wantPd: Boolean
    ): Int {
        var mask = if (isQueueActive) Km003cProtocol.ATT_ADC_QUEUE else Km003cProtocol.ATT_ADC
        if (isQueueActive && wantTempRefresh) {
            mask = mask or Km003cProtocol.ATT_ADC
        }
        if (wantPd) {
            mask = mask or Km003cProtocol.ATT_PD_PACKET
        }
        return mask
    }

    private fun ingestSample(sample: TelemetrySample) {
        val now = sample.timestampMs
        val dtSec = if (lastSampleMs == 0L) 0.0 else ((now - lastSampleMs).coerceIn(1L, 2000L) / 1000.0)
        lastSampleMs = now

        val curStats = _stats.value
        val dtHours = dtSec / 3600.0
        val addMah = abs(sample.ibusA) * 1000.0 * dtHours
        val addMwh = sample.powerW * 1000.0 * dtHours
        val elapsed = (now - sessionStartMs) / 1000.0

        _telemetry.value = sample
        _stats.value = curStats.copy(
            capacityMah = curStats.capacityMah + addMah,
            energyMwh = curStats.energyMwh + addMwh,
            elapsedSec = elapsed,
            maxVbusV = maxOf(curStats.maxVbusV, sample.vbusV),
            minVbusV = minOf(curStats.minVbusV, sample.vbusV),
            maxIbusA = maxOf(curStats.maxIbusA, abs(sample.ibusA)),
            maxPowerW = maxOf(curStats.maxPowerW, sample.powerW),
            sampleCount = curStats.sampleCount + 1
        )

        val point = GraphPoint(
            timeSec = elapsed,
            vbusV = sample.vbusV.toFloat(),
            ibusA = abs(sample.ibusA).toFloat(),
            powerW = sample.powerW.toFloat()
        )
        synchronized(historyBuffer) {
            historyBuffer.addLast(point)
            while (historyBuffer.size > 2000) {
                historyBuffer.removeFirst()
            }
            if (csvBuffer.size < 50_000) {
                csvBuffer.add(sample)
            }
            _graphPoints.value = historyBuffer.toList()
        }
    }

    // ---- PDM Virtual Serial Protocol Trigger ----

    fun openPdmTrigger() {
        sendPdmCommand(PdmSerialProtocol.CMD_PDM_OPEN) {
            _pdmActive.value = true
        }
    }

    fun closePdmTrigger() {
        sendPdmCommand(PdmSerialProtocol.CMD_PDM_CLOSE) {
            _pdmActive.value = false
            _pdmPdos.value = emptyList()
        }
    }

    fun enterPdAndFetchPdos() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!_pdmActive.value) {
                execCdcInternal(PdmSerialProtocol.CMD_PDM_OPEN, 300)
                _pdmActive.value = true
                delay(150)
            }
            execCdcInternal(PdmSerialProtocol.CMD_ENTRY_PD, 900)
            delay(250)
            val pdoResp = execCdcInternal(PdmSerialProtocol.CMD_PD_PDO, 700)
            if (pdoResp != null) {
                _pdmPdos.value = PdmSerialProtocol.parsePdPdoOutput(pdoResp)
            }
        }
    }

    fun sendPdmCommand(command: String, waitMs: Long = 450, onDone: ((String?) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val resp = execCdcInternal(command, waitMs)
            onDone?.invoke(resp)
        }
    }

    private fun execCdcInternal(command: String, waitMs: Long): String? {
        appendPdmConsole("> $command")
        if (_connectionState.value == ConnectionState.SIMULATOR) {
            val out = simulator.handleSimulatedCdcCommand(command)
            appendPdmConsole(out)
            return out
        }
        val res = usbManager.sendCdcCommand(command, waitMs)
        return res.fold(
            onSuccess = { text ->
                appendPdmConsole(text.ifEmpty { "(응답 없음)" })
                text
            },
            onFailure = { err ->
                appendPdmConsole("[오류] ${err.message}")
                null
            }
        )
    }

    private fun appendPdmConsole(text: String) {
        val lines = text.lines().filter { it.isNotBlank() }
        _pdmConsoleLines.value = (_pdmConsoleLines.value + lines).takeLast(120)
    }

    fun clearPdmConsole() {
        _pdmConsoleLines.value = emptyList()
    }

    // ---- CSV Export ----

    fun createCsvShareIntent(): Intent? {
        val samples = synchronized(historyBuffer) { csvBuffer.toList() }
        if (samples.isEmpty()) return null

        val ctx = getApplication<Application>()
        val exportDir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val csvFile = File(exportDir, "powerz_km003c_$stamp.csv")

        csvFile.bufferedWriter().use { w ->
            w.write("timestamp_ms,vbus_V,ibus_A,power_W,dp_V,dm_V,cc1_V,cc2_V,temp_C\n")
            for (s in samples) {
                w.write(
                    String.format(
                        Locale.US,
                        "%d,%.5f,%.5f,%.5f,%.4f,%.4f,%.4f,%.4f,%.2f\n",
                        s.timestampMs, s.vbusV, s.ibusA, s.powerW, s.dpV, s.dmV, s.cc1V, s.cc2V, s.tempC
                    )
                )
            }
        }

        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", csvFile)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "POWER-Z KM003C 측정 데이터 ($stamp)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
        usbManager.close()
    }
}
