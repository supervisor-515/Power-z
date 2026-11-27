package com.powerz.km003c.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerz.km003c.model.ConnectionState
import com.powerz.km003c.model.DeviceInfo
import com.powerz.km003c.model.RecordingState
import com.powerz.km003c.model.SavedSession
import com.powerz.km003c.ui.theme.BgBottom
import com.powerz.km003c.ui.theme.Brand
import com.powerz.km003c.ui.theme.Danger
import com.powerz.km003c.ui.theme.Ink
import com.powerz.km003c.ui.theme.InkFaint
import com.powerz.km003c.ui.theme.InkMuted
import com.powerz.km003c.ui.theme.Ok
import com.powerz.km003c.ui.theme.Panel
import com.powerz.km003c.ui.theme.PanelStrong
import com.powerz.km003c.ui.theme.Pd
import com.powerz.km003c.ui.theme.PzRadius
import com.powerz.km003c.ui.theme.PzType
import com.powerz.km003c.ui.theme.Warn
import com.powerz.km003c.ui.theme.appBackground
import com.powerz.km003c.viewmodel.PowerZViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerZApp(
    viewModel: PowerZViewModel,
    onShareExport: (format: String, session: SavedSession?, showV: Boolean, showI: Boolean, showW: Boolean) -> Unit
) {
    val connState by viewModel.connectionState.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val graphPoints by viewModel.graphPoints.collectAsState()
    val sampleRate by viewModel.sampleRate.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()

    val recordingConfig by viewModel.recordingConfig.collectAsState()
    val recordingStatus by viewModel.recordingStatus.collectAsState()
    val recordingGraphPoints by viewModel.recordingGraphPoints.collectAsState()
    val savedSessions by viewModel.savedSessions.collectAsState()
    val viewingSavedSession by viewModel.viewingSavedSession.collectAsState()

    val pdCaptureEnabled by viewModel.pdCaptureEnabled.collectAsState()
    val pdLogs by viewModel.pdLogs.collectAsState()
    val pdmActive by viewModel.pdmActive.collectAsState()
    val pdmConsole by viewModel.pdmConsoleLines.collectAsState()
    val pdmPdos by viewModel.pdmPdos.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showRecordingSettings by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val isConnected = connState == ConnectionState.CONNECTED_VENDOR ||
        connState == ConnectionState.CONNECTED_HID ||
        connState == ConnectionState.SIMULATOR

    val connLabel = when (connState) {
        ConnectionState.CONNECTED_VENDOR, ConnectionState.CONNECTED_HID -> "연결됨"
        ConnectionState.SIMULATOR -> "시뮬레이터"
        ConnectionState.CONNECTING, ConnectionState.REQUESTING_PERMISSION -> "연결 중"
        ConnectionState.ERROR -> "오류"
        ConnectionState.DISCONNECTED -> "연결 안 됨"
    }
    val connColor = when (connState) {
        ConnectionState.CONNECTED_VENDOR, ConnectionState.CONNECTED_HID -> Ok
        ConnectionState.SIMULATOR -> Pd
        ConnectionState.CONNECTING, ConnectionState.REQUESTING_PERMISSION -> Warn
        ConnectionState.ERROR -> Danger
        ConnectionState.DISCONNECTED -> InkFaint
    }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = Color.Transparent,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = PanelStrong,
                    contentColor = Ink,
                    shape = RoundedCornerShape(PzRadius.md)
                )
            }
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Column {
                        Text("POWER-Z", style = PzType.headline, color = Ink)
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(connColor))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isConnected) connLabel else statusMsg.ifBlank { connLabel },
                                style = PzType.caption,
                                color = InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "장치 정보", tint = InkMuted)
                    }
                    if (!isConnected) {
                        IconButton(onClick = { viewModel.startSimulator() }) {
                            Icon(Icons.Outlined.Science, contentDescription = "시뮬레이터", tint = InkMuted)
                        }
                        IconButton(onClick = { viewModel.scanAndConnect() }) {
                            Icon(Icons.Default.Usb, contentDescription = "USB 연결", tint = Brand)
                        }
                    } else {
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(Icons.Default.UsbOff, contentDescription = "연결 해제", tint = InkMuted)
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = BgBottom, tonalElevation = 0.dp) {
                val navColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Ink,
                    selectedTextColor = Ink,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = InkFaint,
                    unselectedTextColor = InkFaint
                )
                val tabs = listOf(
                    "측정" to Icons.Default.Speed,
                    "그래프" to Icons.AutoMirrored.Filled.ShowChart,
                    "PD 분석" to Icons.AutoMirrored.Filled.ViewList,
                    "트리거" to Icons.Default.Bolt
                )
                tabs.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = navColors,
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label, style = PzType.caption) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    telemetry = telemetry,
                    stats = stats,
                    livePoints = graphPoints,
                    sampleRate = sampleRate,
                    isPaused = isPaused,
                    recordingConfig = recordingConfig,
                    recordingStatus = recordingStatus,
                    onStartRecording = viewModel::startRecording,
                    onStopRecording = { viewModel.stopRecording() },
                    onOpenRecordingSettings = { showRecordingSettings = true },
                    onTogglePause = viewModel::togglePause,
                    onResetStats = viewModel::resetSession,
                    onSavePng = { viewModel.saveSessionGraphPng() },
                    onSaveCsv = { viewModel.saveSessionCsv() },
                    onShareCsv = { onShareExport("CSV", null, true, true, true) },
                    onOpenChartTab = {
                        recordingStatus.lastSavedSessionId?.let { viewModel.openSavedSession(it) }
                        selectedTab = 1
                    }
                )
                1 -> ChartScreen(
                    telemetry = telemetry,
                    livePoints = graphPoints,
                    recordingPoints = recordingGraphPoints,
                    savedSessions = savedSessions,
                    viewingSavedSession = viewingSavedSession,
                    onOpenSavedSession = viewModel::openSavedSession,
                    onCloseSavedSession = viewModel::closeSavedSessionView,
                    onDeleteSavedSession = viewModel::deleteSavedSession,
                    onSavePng = { session, v, i, w -> viewModel.saveSessionGraphPng(session, v, i, w) },
                    onSaveCsv = { session -> viewModel.saveSessionCsv(session) },
                    onSaveJson = { session -> viewModel.saveSessionJson(session) },
                    onShare = onShareExport
                )
                2 -> PdAnalyzerScreen(
                    pdCaptureEnabled = pdCaptureEnabled,
                    logs = pdLogs,
                    onToggleCapture = viewModel::togglePdCapture,
                    onClear = viewModel::clearPdLogs
                )
                3 -> TriggerScreen(
                    hasCdcSerial = deviceInfo.hasCdcSerial || connState == ConnectionState.SIMULATOR,
                    pdmActive = pdmActive,
                    pdmPdos = pdmPdos,
                    consoleLines = pdmConsole,
                    onOpenPdm = viewModel::openPdmTrigger,
                    onClosePdm = viewModel::closePdmTrigger,
                    onEnterPdAndFetch = viewModel::enterPdAndFetchPdos,
                    onSendCommand = { cmd -> viewModel.sendPdmCommand(cmd) },
                    onClearConsole = viewModel::clearPdmConsole
                )
            }
        }
    }

    if (showRecordingSettings) {
        RecordingSettingsSheet(
            config = recordingConfig,
            sampleRate = sampleRate,
            onSelectSampleRate = viewModel::setSampleRate,
            onApply = viewModel::updateRecordingConfig,
            onDismiss = { showRecordingSettings = false }
        )
    }

    if (showInfoDialog) {
        DeviceInfoDialog(info = deviceInfo, onDismiss = { showInfoDialog = false })
    }
}

@Composable
private fun DeviceInfoDialog(info: DeviceInfo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel, shape = RoundedCornerShape(PzRadius.md),
        title = { Text("장치 정보", style = PzType.headline, color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoLine("모델명", info.productName)
                InfoLine("VID / PID", String.format("0x%04X / 0x%04X", info.vendorId, info.productId))
                InfoLine("전송 방식", info.transportMode)
                InfoLine("시리얼 번호", info.serialNumber)
                InfoLine("하드웨어 정보", info.hardwareVersion)
                InfoLine("펌웨어 정보", info.firmwareVersion)
                InfoLine("Hardware ID", info.hardwareIdHex)
                InfoLine("고속 스트리밍 인증", if (info.streamingAuthorized) "성공 (0x0203)" else "미인증")
                InfoLine("CDC 가상 시리얼", if (info.hasCdcSerial) "사용 가능 (PDM 지원)" else "없음")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기", style = PzType.body, color = Brand)
            }
        }
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = PzType.caption, color = InkMuted)
        Spacer(Modifier.width(12.dp))
        Text(value, style = PzType.mono, color = Ink)
    }
}
