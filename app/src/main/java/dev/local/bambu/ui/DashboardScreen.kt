/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: DashboardScreen.kt
 * 职责: 监视器主屏幕容器，集成主任务与无框背景融合机型舞台、仓内 RTSPS 实时视频监控、温控、AMS 及双列折叠屏适配
 * 层级: UI Presentation Layer (dev.local.bambu.ui)
 */

package dev.local.bambu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.local.bambu.model.*
import dev.local.bambu.ui.components.*
import dev.local.bambu.ui.theme.*

@Composable
fun DashboardScreen(
    state: BambuUiState,
    onOpenDeviceManager: () -> Unit,
    onStartScan: () -> Unit,
    onRefresh: () -> Unit,
    onSetCameraSurface: (android.view.Surface?) -> Unit = {},
    onToggleChamberLight: () -> Unit,
    onToggleCameraStream: () -> Unit,
    onSetSpeedLevel: (SpeedLevel) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    var isFullscreenVideo by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
            DashboardTopBar(
                selectedDevice = state.selectedDevice,
                connectionState = state.status.connectionState,
                onOpenDeviceManager = onOpenDeviceManager,
                onRefresh = onRefresh
            )
        }
    ) { innerPadding ->
        if (state.selectedDevice == null) {
            EmptyDevicePlaceholder(
                isScanning = state.isScanning,
                discoveredCount = state.discoveredDevices.size,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
                onOpenDeviceManager = onOpenDeviceManager,
                onStartScan = onStartScan
            )
        } else {
            val activeDevice = state.selectedDevice
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val isFoldableWide = maxWidth >= 600.dp

                if (isFoldableWide) {
                    // 折叠屏展开 / 平板 / 横屏：双列并排工作台布局
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 左列：主任务卡 + 仓内实时视频监控 + 硬件参数
                        LazyColumn(
                            modifier = Modifier.weight(1.05f),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (state.status.hmsErrors.isNotEmpty()) {
                                item { HmsAlertCard(errors = state.status.hmsErrors) }
                            }
                            item {
                                PrintTaskHeroCard(
                                    device = activeDevice,
                                    status = state.status,
                                    onToggleLight = onToggleChamberLight,
                                    onSetSpeedLevel = onSetSpeedLevel,
                                    onPause = onPause,
                                    onResume = onResume,
                                    onStop = onStop
                                )
                            }
                            item {
                                ChamberVideoCard(
                                    device = activeDevice,
                                    status = state.status,
                                    streamState = state.cameraStreamState,
                                    liveBitmap = state.cameraBitmap,
                                    fps = state.cameraFps,
                                    bitrateKbps = state.cameraBitrateKbps,
                                    streamError = state.cameraError,
                                    onSetCameraSurface = onSetCameraSurface,
                                    onToggleStream = onToggleCameraStream,
                                    onToggleLight = onToggleChamberLight,
                                    onEnterFullscreen = { isFullscreenVideo = true }
                                )
                            }
                            item {
                                DeviceInfoCard(device = activeDevice, status = state.status)
                            }
                        }

                        // 右列：热端与热床温控 + AMS 多色供料系统
                        LazyColumn(
                            modifier = Modifier.weight(0.95f),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item { ThermalControlCard(status = state.status) }
                            if (state.status.amsUnits.isNotEmpty()) {
                                item { AmsSystemCard(amsUnits = state.status.amsUnits) }
                            }
                        }
                    }
                } else {
                    // 标准手机屏 / 折叠屏外屏：单列瀑布流
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (state.status.hmsErrors.isNotEmpty()) {
                            item { HmsAlertCard(errors = state.status.hmsErrors) }
                        }
                        item {
                            PrintTaskHeroCard(
                                device = activeDevice,
                                status = state.status,
                                onToggleLight = onToggleChamberLight,
                                onSetSpeedLevel = onSetSpeedLevel,
                                onPause = onPause,
                                onResume = onResume,
                                onStop = onStop
                            )
                        }
                        item {
                            ChamberVideoCard(
                                device = activeDevice,
                                status = state.status,
                                streamState = state.cameraStreamState,
                                liveBitmap = state.cameraBitmap,
                                fps = state.cameraFps,
                                bitrateKbps = state.cameraBitrateKbps,
                                streamError = state.cameraError,
                                onSetCameraSurface = onSetCameraSurface,
                                onToggleStream = onToggleCameraStream,
                                onToggleLight = onToggleChamberLight,
                                onEnterFullscreen = { isFullscreenVideo = true }
                            )
                        }
                        item { ThermalControlCard(status = state.status) }
                        if (state.status.amsUnits.isNotEmpty()) {
                            item { AmsSystemCard(amsUnits = state.status.amsUnits) }
                        }
                        item {
                            DeviceInfoCard(device = activeDevice, status = state.status)
                        }
                    }
                }
            }
        }
    }

        // 顶层沉浸式无缝全屏覆盖层
        val activeDevice = state.selectedDevice
        if (isFullscreenVideo && activeDevice != null) {
            ChamberFullscreenOverlay(
                device = activeDevice,
                status = state.status,
                streamState = state.cameraStreamState,
                liveBitmap = state.cameraBitmap,
                fps = state.cameraFps,
                bitrateKbps = state.cameraBitrateKbps,
                onClose = { isFullscreenVideo = false },
                onToggleLight = onToggleChamberLight,
                onSetCameraSurface = onSetCameraSurface
            )
        }
    }
}

@Composable
private fun DashboardTopBar(
    selectedDevice: BambuDevice?,
    connectionState: ConnectionState,
    onOpenDeviceManager: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable(onClick = onOpenDeviceManager)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selectedDevice?.name ?: "拓竹局域网监视器",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Connection indicator pill
            val (statusText, statusColor) = when (connectionState) {
                ConnectionState.CONNECTED -> "局域网已连接" to BambuGreen
                ConnectionState.CONNECTING -> "正在连接..." to InkSecondary
                ConnectionState.DISCONNECTED -> "未连接设备" to MaterialTheme.colorScheme.onSurfaceVariant
                ConnectionState.ERROR -> "连接失败" to ErrorRed
            }

            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = statusColor,
                    modifier = Modifier.size(6.dp)
                ) {}
                Spacer(Modifier.width(5.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (connectionState == ConnectionState.ERROR) ErrorRed else InkSecondary,
                    fontWeight = FontWeight.Medium,
                    softWrap = false,
                    maxLines = 1
                )
                if (selectedDevice != null) {
                    Text(
                        text = " • ${selectedDevice.ip}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundIconButton(
                icon = Icons.Outlined.Refresh,
                description = "刷新状态",
                onClick = onRefresh
            )
            RoundIconButton(
                icon = Icons.Outlined.Devices,
                description = "管理设备",
                onClick = onOpenDeviceManager
            )
        }
    }
}

@Composable
private fun HmsAlertCard(errors: List<String>) {
    val isMqttAuthError = errors.any { it.contains("65543") || it.contains("0001_0007") }
    SoftSurface(
        accent = ErrorRed,
        shape = ContinuousCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isMqttAuthError) Icons.Outlined.Lock else Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = if (isMqttAuthError) "局域网控制受限 (需开启开发者模式)" else "拓竹 HMS 诊断提醒",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = ErrorRed,
                    softWrap = false,
                    maxLines = 1
                )
                Text(
                    text = if (isMqttAuthError) "请在打印机屏幕启用: 局域网模式 > 开发者模式" else "故障代码: ${errors.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = false,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EmptyDevicePlaceholder(
    isScanning: Boolean,
    discoveredCount: Int,
    modifier: Modifier = Modifier,
    onOpenDeviceManager: () -> Unit,
    onStartScan: () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        SoftSurface(
            shape = ContinuousCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrinterRenderStage(model = PrinterModel.X2D, isActive = true, size = 62.dp, cornerRadius = 14.dp)
                    PrinterRenderStage(model = PrinterModel.X1_CARBON, isActive = false, size = 62.dp, cornerRadius = 14.dp)
                    PrinterRenderStage(model = PrinterModel.H2D, isActive = false, size = 62.dp, cornerRadius = 14.dp)
                    PrinterRenderStage(model = PrinterModel.P1S, isActive = false, size = 62.dp, cornerRadius = 14.dp)
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "请连接局域网拓竹打印机",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    softWrap = false,
                    maxLines = 1
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = if (discoveredCount > 0) {
                        "已在当前 Wi-Fi 发现 $discoveredCount 台拓竹打印机，点击下方按钮输入访问码即可直连。"
                    } else if (isScanning) {
                        "正在自动扫描当前局域网内的拓竹打印机 (SSDP / 8883 端口)..."
                    } else {
                        "支持 X2D / X1 / H2 / P1 / P2 / A1 全系 14 款机型自动识别，基于局域网 TLS MQTT 与 RTSPS 直连监控。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (discoveredCount > 0) BambuGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(22.dp))

                Button(
                    onClick = onOpenDeviceManager,
                    shape = ContinuousCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BambuGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Devices, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (discoveredCount > 0) "查看已发现设备 ($discoveredCount) 并配对" else "扫描或手动添加打印机",
                        softWrap = false,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onStartScan,
                    enabled = !isScanning,
                    shape = ContinuousCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在扫描局域网...", softWrap = false, maxLines = 1)
                    } else {
                        Icon(Icons.Outlined.Search, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("重新扫描局域网", softWrap = false, maxLines = 1)
                    }
                }
            }
        }
    }
}
