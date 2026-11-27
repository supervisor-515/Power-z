package com.powerz.km003c.viewmodel

import android.app.Application
import android.content.Intent
import android.hardware.usb.UsbDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.powerz.km003c.export.SessionExporter
import com.powerz.km003c.model.ConnectionState
import com.powerz.km003c.model.DeviceInfo
import com.powerz.km003c.model.GraphPoint
import com.powerz.km003c.model.PdLogEntry
import com.powerz.km003c.model.PdmPdoItem
import com.powerz.km003c.model.RecordedSample
import com.powerz.km003c.model.RecordingConfig
import com.powerz.km003c.model.RecordingState
import com.powerz.km003c.model.RecordingStatus
import com.powerz.km003c.model.SampleRateMode
import com.powerz.km003c.model.SavedSession
import com.powerz.km003c.model.SavedSessionSummary
import com.powerz.km003c.model.SessionStats
import com.powerz.km003c.model.TelemetrySample
import com.powerz.km003c.protocol.Km003cProtocol
import com.powerz.km003c.protocol.PdmSerialProtocol
import com.powerz.km003c.protocol.UsbPdDecoder
import com.powerz.km003c.recording.RecordingEngine
import com.powerz.km003c.usb.Km003cSimulator
import com.powerz.km003c.usb.Km003cUsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

class PowerZViewModel(application: Application) : AndroidViewModel(application) {

    val usbManager = Km003cUsbManager(application)
    val exporter = SessionExporter(application)
    private val simulator = Km003cSimulator()
    private val recordingEngine = RecordingEngine()
    private val recLock = Any()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _statusMessage = MutableStateFlow("USB-C OTG로 KM003C를 연결하거나 시뮬레이터를 실행하세요")
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

    // ---- Recording & Saved Sessions State ----

    private val _recordingConfig = MutableStateFlow(RecordingConfig())
    val recordingConfig: StateFlow<RecordingConfig> = _recordingConfig.asStateFlow()

    private val _recordingStatus = MutableStateFlow(RecordingStatus())
    val recordingStatus: StateFlow<RecordingStatus> = _recordingStatus.asStateFlow()

    private val _recordingGraphPoints = MutableStateFlow<List<GraphPoint>>(emptyList())
    val recordingGraphPoints: StateFlow<List<GraphPoint>> = _recordingGraphPoints.asStateFlow()

    private val _savedSessions = MutableStateFlow<List<SavedSessionSummary>>(emptyList())
    val savedSessions: StateFlow<List<SavedSessionSummary>> = _savedSessions.asStateFlow()

    private val _viewingSavedSession = MutableStateFlow<SavedSession?>(null)
    val viewingSavedSession: StateFlow<SavedSession?> = _viewingSavedSession.asStateFlow()

    private val _lastCompletedSession = MutableStateFlow<SavedSession?>(null)
    val lastCompletedSession: StateFlow<SavedSession?> = _lastCompletedSession.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    // ---- PD & PDM State ----

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

    // Live buffer
    private var sessionStartMs = System.currentTimeMillis()
    private var lastSampleMs = 0L
    private val historyBuffer = ArrayDeque<GraphPoint>(3200)
    private val liveRawBuffer = ArrayList<RecordedSample>(4096)

    // Callback to ask MainActivity to request USB permission
    var onRequestUsbPermission: ((UsbDevice) -> Unit)? = null

    init {
        refreshSavedSessions()
    }

    // ---- USB Connection ----

    fun scanAndConnect() {
        val dev = usbManager.findDevice()
        if (dev == null) {
            _connectionState.value = ConnectionState.DISCONNECTED
            _statusMessage.value = "KM003C/KM002C 미감지 (VID:5FC9) • 상단 버튼으로 연결"
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
            // Auto-save active recording if device disconnects unexpectedly
            stopRecording(reason = "USB 연결 해제로 자동 저장")
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
                    if (info.streamingAuthorized) append(" • 고속 스트리밍")
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
        _statusMessage.value = "시뮬레이터 실행 중"
        _pdLogs.value = simulator.initialPdLogs()
        resetSession()

        pollingJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                if (!_isPaused.value) {
                    val sample = simulator.nextSample()
                    ingestSample(sample)
                }
                val recInterval = _recordingConfig.value.intervalMs
                val baseInterval = when (_sampleRate.value) {
                    SampleRateMode.STREAM_2SPS -> 500L
                    SampleRateMode.SIMPLE_10HZ, SampleRateMode.STREAM_10SPS -> 100L
                    SampleRateMode.STREAM_50SPS -> 25L
                    SampleRateMode.STREAM_1000SPS -> 15L
                }
                val activeDelay = if (_recordingStatus.value.state == RecordingState.RECORDING) {
                    minOf(baseInterval, recInterval.coerceIn(15L, 200L))
                } else {
                    baseInterval
                }
                delay(activeDelay)
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

        if (_connectionState.value == ConnectionState.CONNECTED_HID && mode.rateIndex != null) {
            emitToast("HID 모드에서는 Simple (10Hz)만 지원됩니다")
            return
        }
        if (_connectionState.value == ConnectionState.CONNECTED_VENDOR &&
            mode.rateIndex != null && !usbManager.streamingAuthorized
        ) {
            emitToast("스트리밍 인증이 완료되지 않아 Simple 모드를 유지합니다")
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
            liveRawBuffer.clear()
        }
        _graphPoints.value = emptyList()
        _stats.value = SessionStats()
    }

    // ---- Recording Engine Controls ----

    fun updateRecordingConfig(newConfig: RecordingConfig) {
        _recordingConfig.value = newConfig.copy(intervalMs = newConfig.intervalMs.coerceIn(10L, 60_000L))
    }

    fun startRecording() {
        val isActiveConn = _connectionState.value == ConnectionState.CONNECTED_VENDOR ||
            _connectionState.value == ConnectionState.CONNECTED_HID ||
            _connectionState.value == ConnectionState.SIMULATOR
        if (!isActiveConn) {
            emitToast("먼저 KM003C를 연결하거나 시뮬레이터를 켜주세요")
            return
        }

        val cfg = _recordingConfig.value
        // Automatically step up hardware SPS if user requested faster recording interval and hardware supports it
        if (_connectionState.value == ConnectionState.CONNECTED_VENDOR && usbManager.streamingAuthorized) {
            val neededSps = (1000.0 / cfg.intervalMs.coerceAtLeast(10L))
            if (neededSps > 10.0 && _sampleRate.value.sps < neededSps) {
                val targetMode = if (neededSps > 50.0) SampleRateMode.STREAM_1000SPS else SampleRateMode.STREAM_50SPS
                setSampleRate(targetMode)
            }
        }

        val status = synchronized(recLock) {
            _recordingGraphPoints.value = emptyList()
            _lastCompletedSession.value = null
            recordingEngine.start(cfg)
        }
        _recordingStatus.value = status
        emitToast(
            if (status.state == RecordingState.ARMED_WAITING_TRIGGER) {
                "측정 대기 시작 (전류 ≥ ${cfg.autoStartCurrentA}A 감지 시 자동 기록)"
            } else {
                "측정 기록 시작 (간격 ${cfg.intervalLabel()})"
            }
        )
    }

    fun stopRecording(reason: String = "사용자 수동 종료") {
        val completed = synchronized(recLock) {
            val session = recordingEngine.stopManually(reason)
            _recordingStatus.value = recordingEngine.currentStatus()
            session
        }
        if (completed != null) {
            persistCompletedSession(completed)
        } else {
            emitToast("측정 대기가 취소되었습니다")
        }
    }

    fun clearRecordingCompletedBanner() {
        synchronized(recLock) {
            _recordingStatus.value = recordingEngine.resetToIdle()
        }
    }

    private fun persistCompletedSession(session: SavedSession) {
        _lastCompletedSession.value = session
        viewModelScope.launch(Dispatchers.IO) {
            exporter.saveSessionLocally(session)
            val list = exporter.listSavedSessions()
            _savedSessions.value = list
            emitToast("측정 완료 및 저장: ${session.summary.stopReason} (${session.summary.sampleCount} pts)")
        }
    }

    fun refreshSavedSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            _savedSessions.value = exporter.listSavedSessions()
        }
    }

    fun openSavedSession(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = exporter.loadSession(id)
            _viewingSavedSession.value = loaded
        }
    }

    fun closeSavedSessionView() {
        _viewingSavedSession.value = null
    }

    fun deleteSavedSession(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            exporter.deleteSession(id)
            if (_viewingSavedSession.value?.summary?.id == id) {
                _viewingSavedSession.value = null
            }
            if (_lastCompletedSession.value?.summary?.id == id) {
                _lastCompletedSession.value = null
            }
            _savedSessions.value = exporter.listSavedSessions()
            emitToast("측정 기록이 삭제되었습니다")
        }
    }

    // ---- Export Helpers (Works for SavedSession, Active/Last Recording, or Live Buffer) ----

    fun buildActiveExportSession(): SavedSession? {
        _viewingSavedSession.value?.let { return it }
        synchronized(recLock) {
            val recSamples = recordingEngine.currentSamples()
            if (recSamples.isNotEmpty()) {
                val st = _recordingStatus.value
                val summary = SavedSessionSummary(
                    id = st.lastSavedSessionId ?: "recording_live",
                    name = st.sessionName.ifBlank { "현재 측정 기록" },
                    createdAtMs = recSamples.first().timestampMs,
                    durationSec = st.elapsedSec,
                    intervalMs = _recordingConfig.value.intervalMs,
                    sampleCount = recSamples.size,
                    capacityMah = st.capacityMah,
                    energyMwh = st.energyMwh,
                    maxVbusV = st.maxVbusV,
                    maxIbusA = st.maxIbusA,
                    maxPowerW = st.maxPowerW,
                    avgPowerW = st.avgPowerW,
                    stopReason = st.stopReason ?: "진행 중"
                )
                return SavedSession(summary, recSamples)
            }
        }
        _lastCompletedSession.value?.let { return it }

        val liveSamples = synchronized(historyBuffer) { liveRawBuffer.toList() }
        if (liveSamples.isEmpty()) return null
        val st = _stats.value
        val avgP = if (liveSamples.isNotEmpty()) liveSamples.sumOf { it.powerW } / liveSamples.size else 0.0
        val summary = SavedSessionSummary(
            id = "live_${System.currentTimeMillis()}",
            name = "실시간 계측",
            createdAtMs = liveSamples.first().timestampMs,
            durationSec = st.elapsedSec,
            intervalMs = (1000L / _sampleRate.value.sps.coerceAtLeast(1)),
            sampleCount = liveSamples.size,
            capacityMah = st.capacityMah,
            energyMwh = st.energyMwh,
            maxVbusV = st.maxVbusV,
            maxIbusA = st.maxIbusA,
            maxPowerW = st.maxPowerW,
            avgPowerW = avgP,
            stopReason = "실시간 스냅샷"
        )
        return SavedSession(summary, liveSamples)
    }

    fun saveSessionGraphPng(
        session: SavedSession? = null,
        showV: Boolean = true,
        showI: Boolean = true,
        showW: Boolean = true
    ) {
        val target = session ?: buildActiveExportSession()
        if (target == null || target.samples.size < 2) {
            emitToast("그래프로 저장할 측정 데이터가 부족합니다")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            exporter.saveGraphPngToGallery(target, showV, showI, showW)
                .onSuccess { path -> emitToast("그래프 PNG 저장됨: $path") }
                .onFailure { err -> emitToast("PNG 저장 실패: ${err.message}") }
        }
    }

    fun saveSessionCsv(session: SavedSession? = null) {
        val target = session ?: buildActiveExportSession()
        if (target == null || target.samples.isEmpty()) {
            emitToast("저장할 CSV 데이터가 없습니다")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            exporter.saveCsvToDownloads(target)
                .onSuccess { path -> emitToast("CSV 파일 저장됨: $path") }
                .onFailure { err -> emitToast("CSV 저장 실패: ${err.message}") }
        }
    }

    fun saveSessionJson(session: SavedSession? = null) {
        val target = session ?: buildActiveExportSession()
        if (target == null || target.samples.isEmpty()) {
            emitToast("저장할 JSON 데이터가 없습니다")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            exporter.saveJsonToDownloads(target)
                .onSuccess { path -> emitToast("JSON 파일 저장됨: $path") }
                .onFailure { err -> emitToast("JSON 저장 실패: ${err.message}") }
        }
    }

    fun createShareIntent(
        format: String, // "PNG", "CSV", "JSON"
        session: SavedSession? = null,
        showV: Boolean = true,
        showI: Boolean = true,
        showW: Boolean = true
    ): Intent? {
        val target = session ?: buildActiveExportSession() ?: return null
        if (target.samples.isEmpty()) return null
        return when (format) {
            "PNG" -> exporter.createPngShareIntent(target, showV, showI, showW)
            "JSON" -> exporter.createJsonShareIntent(target)
            else -> exporter.createCsvShareIntent(target)
        }
    }

    private fun emitToast(msg: String) {
        _snackbarEvents.tryEmit(msg)
    }

    // ---- Hardware Polling Loop ----

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
                            stopRecording("USB 통신 타임아웃으로 자동 저장")
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
                            val dtStepMs = when (rateIdx) {
                                0 -> 500L
                                1 -> 100L
                                2 -> 20L
                                else -> 1L
                            }
                            val baseTs = nowMs - ((samples.size - 1).coerceAtLeast(0) * dtStepMs)
                            samples.forEachIndexed { idx, s ->
                                ingestSample(s.copy(timestampMs = baseTs + idx * dtStepMs))
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
                                    _pdLogs.value = (_pdLogs.value + newEntries).takeLast(300)
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
        val dtSec = if (lastSampleMs == 0L) 0.0 else ((now - lastSampleMs).coerceIn(0L, 2000L) / 1000.0)
        lastSampleMs = now

        val curStats = _stats.value
        val dtHours = dtSec / 3600.0
        val addMah = abs(sample.ibusA) * 1000.0 * dtHours
        val addMwh = sample.powerW * 1000.0 * dtHours
        val elapsed = ((now - sessionStartMs).coerceAtLeast(0L)) / 1000.0

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
            if (liveRawBuffer.size < 30_000) {
                liveRawBuffer.add(
                    RecordedSample(
                        elapsedSec = elapsed,
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
                )
            }
            _graphPoints.value = historyBuffer.toList()
        }

        // Feed RecordingEngine if recording or armed
        val outcome = synchronized(recLock) {
            recordingEngine.onSample(sample)
        }
        if (outcome.status != _recordingStatus.value) {
            _recordingStatus.value = outcome.status
        }
        if (outcome.appendedSample != null) {
            val pt = outcome.appendedSample.toGraphPoint()
            val cur = _recordingGraphPoints.value
            _recordingGraphPoints.value = if (cur.size >= 4000) cur.drop(1) + pt else cur + pt
        }
        if (outcome.completedSession != null) {
            persistCompletedSession(outcome.completedSession)
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

    override fun onCleared() {
        super.onCleared()
        stopPolling()
        usbManager.close()
    }
}
