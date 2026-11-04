/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: MainActivity.kt
 * 职责: Android 主入口 Activity，沉浸式 Edge-to-Edge 窗口初始化、纯深色主题挂载与 Compose 根容器挂载
 * 层级: UI Presentation Layer (dev.local.bambu)
 */

package dev.local.bambu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.local.bambu.ui.BambuMonitorViewModel
import dev.local.bambu.ui.DashboardScreen
import dev.local.bambu.ui.DeviceManagementDialog
import dev.local.bambu.ui.theme.BambuMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BambuApp()
        }
    }
}

@Composable
fun BambuApp(viewModel: BambuMonitorViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeviceManager by remember { mutableStateOf(false) }

    SideEffect {
        (context as? android.app.Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                // 纯深色模式：状态栏与导航栏统一使用浅色图标
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    BambuMonitorTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                DashboardScreen(
                    state = state,
                    onOpenDeviceManager = { showDeviceManager = true },
                    onStartScan = { viewModel.startDiscovery() },
                    onRefresh = { viewModel.refreshStatus() },
                    onSetCameraSurface = { viewModel.setCameraSurface(it) },
                    onToggleChamberLight = { viewModel.toggleChamberLight() },
                    onToggleCameraStream = { viewModel.toggleCameraStream() },
                    onSetSpeedLevel = { level -> viewModel.setSpeedLevel(level) },
                    onPause = { viewModel.pausePrint() },
                    onResume = { viewModel.resumePrint() },
                    onStop = { viewModel.stopPrint() }
                )

                if (showDeviceManager) {
                    DeviceManagementDialog(
                        savedDevices = state.savedDevices,
                        selectedDevice = state.selectedDevice,
                        discoveredDevices = state.discoveredDevices,
                        isScanning = state.isScanning,
                        onDismiss = { showDeviceManager = false },
                        onSelectDevice = { dev -> viewModel.selectDevice(dev) },
                        onSaveDevice = { dev -> viewModel.saveDevice(dev) },
                        onDeleteDevice = { id -> viewModel.deleteDevice(id) },
                        onStartScan = { viewModel.startDiscovery() }
                    )
                }
            }
        }
    }
}
