/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: BambuMonitorViewModel.kt
 * 职责: MVVM 表现层核心 ViewModel，管理状态 StateFlow、局域网 MQTTS 直连、RTSPS/TLS 仓内实时视频流与设备发现生命周期
 * 层级: UI Presentation Layer (dev.local.bambu.ui)
 */

package dev.local.bambu.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.local.bambu.data.DeviceRepository
import dev.local.bambu.model.*
import dev.local.bambu.network.BambuCameraStreamClient
import dev.local.bambu.network.BambuDiscoveryEngine
import dev.local.bambu.network.BambuMqttClient
import dev.local.bambu.network.BambuTelemetryParser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BambuUiState(
    val savedDevices: List<BambuDevice> = emptyList(),
    val selectedDevice: BambuDevice? = null,
    val status: BambuPrinterStatus = BambuPrinterStatus(),
    val cameraStreamState: CameraStreamState = CameraStreamState.IDLE,
    val cameraBitmap: Bitmap? = null,
    val cameraFps: Int = 0,
    val cameraBitrateKbps: Int = 0,
    val cameraError: String? = null,
    val isScanning: Boolean = false,
    val discoveredDevices: List<BambuDiscoveredDevice> = emptyList(),
    val message: String? = null
)

class BambuMonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DeviceRepository(application)
    private val discoveryEngine = BambuDiscoveryEngine(application)

    private val _uiState = MutableStateFlow(BambuUiState())
    val uiState: StateFlow<BambuUiState> = _uiState.asStateFlow()

    private var mqttClient: BambuMqttClient? = null
    private var cameraClient: BambuCameraStreamClient? = null
    private var pendingCameraSurface: android.view.Surface? = null

    fun setCameraSurface(surface: android.view.Surface?) {
        pendingCameraSurface = surface
        cameraClient?.setSurface(surface)
    }

    init {
        observeDiscovery()
        loadSavedDevices()
    }

    private fun loadSavedDevices() {
        // 清理遗留的演示 mock 设备数据，确保实机环境纯净
        val rawDevices = repository.getSavedDevices()
        rawDevices.filter {
            it.accessCode == "demo1234" || it.id.startsWith("DEMO") || it.name.contains("演示机")
        }.forEach {
            repository.deleteDevice(it.id)
        }

        val devices = repository.getSavedDevices()
        val selectedId = repository.getSelectedDeviceId()
        val selected = devices.firstOrNull { it.id == selectedId } ?: devices.firstOrNull()

        _uiState.update { it.copy(savedDevices = devices, selectedDevice = selected) }

        if (selected != null) {
            connectToDevice(selected)
        } else {
            // 若尚未配置真机设备，启动时自动扫描局域网内的拓竹打印机
            startDiscovery(silent = true)
        }
    }

    private fun observeDiscovery() {
        viewModelScope.launch {
            discoveryEngine.discoveredDevices.collect { discovered ->
                _uiState.update { it.copy(discoveredDevices = discovered) }
            }
        }
        viewModelScope.launch {
            discoveryEngine.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            }
        }
    }

    fun startDiscovery(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(message = "正在扫描局域网拓竹打印机...") }
            }
            discoveryEngine.scan()
        }
    }

    fun selectDevice(device: BambuDevice) {
        repository.setSelectedDeviceId(device.id)
        _uiState.update { it.copy(selectedDevice = device) }
        connectToDevice(device)
    }

    fun saveDevice(device: BambuDevice) {
        repository.saveDevice(device)
        val devices = repository.getSavedDevices()
        _uiState.update { it.copy(savedDevices = devices) }
        selectDevice(device)
        _uiState.update { it.copy(message = "已连接至：${device.name}") }
    }

    fun deleteDevice(deviceId: String) {
        if (_uiState.value.selectedDevice?.id == deviceId) {
            disconnectCurrent()
        }
        repository.deleteDevice(deviceId)
        val devices = repository.getSavedDevices()
        val nextSelected = devices.firstOrNull()
        _uiState.update {
            it.copy(
                savedDevices = devices,
                selectedDevice = nextSelected,
                status = BambuPrinterStatus(),
                cameraStreamState = CameraStreamState.IDLE,
                cameraBitmap = null,
                cameraError = null
            )
        }
        nextSelected?.let { connectToDevice(it) }
        _uiState.update { it.copy(message = "已移除打印机") }
    }

    fun connectToDevice(device: BambuDevice) {
        disconnectCurrent()

        _uiState.update {
            it.copy(
                status = BambuPrinterStatus(
                    connectionState = ConnectionState.CONNECTING,
                    printState = PrintState.IDLE
                ),
                cameraStreamState = CameraStreamState.IDLE,
                cameraBitmap = null,
                cameraFps = 0,
                cameraBitrateKbps = 0,
                cameraError = null
            )
        }

        val client = BambuMqttClient(
            device = device,
            onMessageReceived = { payload ->
                viewModelScope.launch {
                    val updated = BambuTelemetryParser.merge(_uiState.value.status, payload)
                    _uiState.update { it.copy(status = updated) }
                }
            }
        )
        mqttClient = client

        viewModelScope.launch {
            client.connectionState.collect { connState ->
                _uiState.update {
                    it.copy(status = it.status.copy(connectionState = connState))
                }
            }
        }

        viewModelScope.launch {
            client.lastError.collect { err ->
                if (err != null) {
                    _uiState.update { it.copy(message = "连接异常: $err") }
                }
            }
        }

        client.connect()
    }

    private fun startRealCameraStream(device: BambuDevice) {
        val cam = BambuCameraStreamClient(device)
        cameraClient = cam
        pendingCameraSurface?.let { cam.setSurface(it) }

        viewModelScope.launch {
            cam.streamState.collect { st ->
                _uiState.update { it.copy(cameraStreamState = st) }
            }
        }
        viewModelScope.launch {
            cam.currentFrame.collect { bmp ->
                _uiState.update { it.copy(cameraBitmap = bmp) }
            }
        }
        viewModelScope.launch {
            cam.fps.collect { f ->
                _uiState.update { it.copy(cameraFps = f) }
            }
        }
        viewModelScope.launch {
            cam.bitrateKbps.collect { br ->
                _uiState.update { it.copy(cameraBitrateKbps = br) }
            }
        }
        viewModelScope.launch {
            cam.lastError.collect { err ->
                _uiState.update { it.copy(cameraError = err) }
            }
        }

        cam.start(_uiState.value.status.rtspUrl)
    }

    fun toggleCameraStream() {
        val dev = _uiState.value.selectedDevice ?: return
        if (_uiState.value.cameraStreamState == CameraStreamState.STREAMING ||
            _uiState.value.cameraStreamState == CameraStreamState.CONNECTING
        ) {
            cameraClient?.stop()
        } else {
            if (cameraClient == null) {
                startRealCameraStream(dev)
            } else {
                cameraClient?.start(_uiState.value.status.rtspUrl)
            }
        }
    }

    fun refreshStatus() {
        val dev = _uiState.value.selectedDevice ?: return
        if (_uiState.value.status.connectionState != ConnectionState.CONNECTED) {
            connectToDevice(dev)
            _uiState.update { it.copy(message = "正在重新连接 ${dev.name}...") }
        } else {
            mqttClient?.requestPushAll()
            _uiState.update { it.copy(message = "已请求同步最新遥测状态") }
        }
    }

    fun toggleChamberLight() {
        val currentLight = _uiState.value.status.chamberLightOn
        val newLight = !currentLight
        mqttClient?.setChamberLight(newLight)
        _uiState.update { it.copy(status = it.status.copy(chamberLightOn = newLight)) }
    }

    fun setSpeedLevel(level: SpeedLevel) {
        mqttClient?.setSpeedLevel(level)
        _uiState.update { it.copy(status = it.status.copy(speedLevel = level)) }
    }

    fun pausePrint() {
        mqttClient?.pausePrint()
    }

    fun resumePrint() {
        mqttClient?.resumePrint()
    }

    fun stopPrint() {
        mqttClient?.stopPrint()
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun disconnectCurrent() {
        cameraClient?.stop()
        cameraClient = null
        mqttClient?.disconnect()
        mqttClient = null
    }

    override fun onCleared() {
        super.onCleared()
        disconnectCurrent()
    }
}
