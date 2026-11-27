package com.powerz.km003c.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerz.km003c.model.ConnectionState
import com.powerz.km003c.model.DeviceInfo
import com.powerz.km003c.model.RecordingState
import com.powerz.km003c.model.SavedSession
import com.powerz.km003c.ui.theme.AmpCyan
import com.powerz.km003c.ui.theme.BgDark
import com.powerz.km003c.ui.theme.EnergyGreen
import com.powerz.km003c.ui.theme.RecordRed
import com.powerz.km003c.ui.theme.SurfaceCard
import com.powerz.km003c.ui.theme.SurfaceDark
import com.powerz.km003c.ui.theme.SurfaceElevated
import com.powerz.km003c.ui.theme.TextPrimary
import com.powerz.km003c.ui.theme.TextSecondary
import com.powerz.km003c.ui.theme.VoltYellow
import com.powerz.km003c.ui.theme.WarnRed
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

    val dotColor = when (connState) {
        ConnectionState.CONNECTED_VENDOR, ConnectionState.CONNECTED_HID -> EnergyGreen
        ConnectionState.SIMULATOR -> AmpCyan
        ConnectionState.CONNECTING, ConnectionState.REQUESTING_PERMISSION -> VoltYellow
        ConnectionState.ERROR -> WarnRed
        ConnectionState.DISCONNECTED -> TextSecondary
    }

    Scaffold(
        containerColor = BgDark,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = SurfaceElevated,
                    contentColor = TextPrimary,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "POWER-Z KM003C",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.4.sp
                                )
                                if (recordingStatus.state == RecordingState.RECORDING) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "REC",
                                        color = TextPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(RecordRed)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = statusMsg,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "장치 정보", tint = TextSecondary)
                    }
                    if (!isConnected) {
                        IconButton(onClick = { viewModel.startSimulator() }) {
                            Icon(Icons.Default.Science, contentDescription = "시뮬레이터", tint = AmpCyan)
                        }
                        IconButton(onClick = { viewModel.scanAndConnect() }) {
                            Icon(Icons.Default.Usb, contentDescription = "USB 연결", tint = VoltYellow)
                        }
                    } else {
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(Icons.Default.UsbOff, contentDescription = "연결 해제", tint = WarnRed)
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceDark) {
                val navColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = VoltYellow,
                    selectedTextColor = VoltYellow,
                    indicatorColor = VoltYellow.copy(alpha = 0.14f),
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                )
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = navColors,
                    icon = { Icon(Icons.Default.Speed, contentDescription = null) },
                    label = { Text("측정", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = navColors,
                    icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null) },
                    label = { Text("그래프", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = navColors,
                    icon = { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null) },
                    label = { Text("PD 분석", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    colors = navColors,
                    icon = { Icon(Icons.Default.Bolt, contentDescription = null) },
                    label = { Text("트리거", fontSize = 11.sp) }
                )
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
        containerColor = SurfaceCard,
        title = { Text("KM003C 장치 정보", color = VoltYellow, fontWeight = FontWeight.Bold) },
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
                Text("닫기", color = VoltYellow)
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
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.width(12.dp))
        Text(value, color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
