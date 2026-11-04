/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: DeviceManagementDialog.kt
 * 职责: 设备管理与局域网发现弹窗，支持 SSDP/8883 机型自动识别、透明底渲染图预览、序列号前缀自动识别与手动配对
 * 层级: UI Presentation Layer (dev.local.bambu.ui)
 */

package dev.local.bambu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.local.bambu.model.BambuDevice
import dev.local.bambu.model.BambuDiscoveredDevice
import dev.local.bambu.model.PrinterModel
import dev.local.bambu.ui.components.PrinterRenderStage
import dev.local.bambu.ui.theme.*

@Composable
fun DeviceManagementDialog(
    savedDevices: List<BambuDevice>,
    selectedDevice: BambuDevice?,
    discoveredDevices: List<BambuDiscoveredDevice>,
    isScanning: Boolean,
    onDismiss: () -> Unit,
    onSelectDevice: (BambuDevice) -> Unit,
    onSaveDevice: (BambuDevice) -> Unit,
    onDeleteDevice: (String) -> Unit,
    onStartScan: () -> Unit
) {
    var showManualAdd by remember { mutableStateOf(false) }
    var selectedDiscovered by remember { mutableStateOf<BambuDiscoveredDevice?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        SoftSurface(
            shape = ContinuousCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "设备管理",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            softWrap = false,
                            maxLines = 1
                        )
                        Text(
                            text = "局域网直连 • 机型自动识别",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                    RoundIconButton(
                        icon = Icons.Outlined.Close,
                        description = "关闭",
                        onClick = onDismiss
                    )
                }

                Spacer(Modifier.height(14.dp))

                if (showManualAdd || selectedDiscovered != null) {
                    DeviceForm(
                        initialDiscovered = selectedDiscovered,
                        onCancel = {
                            showManualAdd = false
                            selectedDiscovered = null
                        },
                        onSave = { dev ->
                            onSaveDevice(dev)
                            showManualAdd = false
                            selectedDiscovered = null
                            onDismiss()
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Section: Discovered devices
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "局域网自动发现",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    softWrap = false,
                                    maxLines = 1
                                )
                                TextButton(
                                    onClick = onStartScan,
                                    enabled = !isScanning,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    if (isScanning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(13.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text("扫描中...", fontSize = 12.sp, softWrap = false, maxLines = 1)
                                    } else {
                                        Icon(Icons.Outlined.Search, null, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("重新扫描", fontSize = 12.sp, softWrap = false, maxLines = 1)
                                    }
                                }
                            }
                        }

                        if (discoveredDevices.isEmpty()) {
                            item {
                                Surface(
                                    shape = ContinuousCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (isScanning) "正在扫描局域网内的拓竹设备 (SSDP / 8883)..." else "暂未自动发现新设备，可点击重新扫描或下方手动添加",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        } else {
                            items(discoveredDevices) { item ->
                                DiscoveredDeviceRow(
                                    device = item,
                                    isAlreadySaved = savedDevices.any { it.id == item.devId || it.ip == item.ip },
                                    onClick = { selectedDiscovered = item }
                                )
                            }
                        }

                        // Section: Saved devices
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "已配对设备",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = false,
                                maxLines = 1
                            )
                        }

                        if (savedDevices.isEmpty()) {
                            item {
                                Surface(
                                    shape = ContinuousCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "暂无已保存的打印机，请选择上方发现的设备或手动添加",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        } else {
                            items(savedDevices) { dev ->
                                SavedDeviceRow(
                                    device = dev,
                                    isSelected = dev.id == selectedDevice?.id,
                                    onSelect = {
                                        onSelectDevice(dev)
                                        onDismiss()
                                    },
                                    onDelete = { onDeleteDevice(dev.id) }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { showManualAdd = true },
                        shape = ContinuousCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("手动输入 IP / 序列号 / 访问码", fontSize = 13.sp, softWrap = false, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveredDeviceRow(
    device: BambuDiscoveredDevice,
    isAlreadySaved: Boolean,
    onClick: () -> Unit
) {
    SoftSurface(
        accent = BambuGreen,
        shape = ContinuousCornerShape(14.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrinterRenderStage(
                model = device.printerModel,
                isActive = true,
                size = 46.dp,
                cornerRadius = 12.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.readableModel,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    StatusPill(text = "局域网在线", color = BambuGreen)
                }
                val snDisplay = if (device.devId.isNotBlank()) " • SN: ${device.devId}" else ""
                Text(
                    text = "${device.ip}$snDisplay",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isAlreadySaved) "已配置" else "配对",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                softWrap = false,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SavedDeviceRow(
    device: BambuDevice,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    SoftSurface(
        accent = if (isSelected) BambuGreen else null,
        shape = ContinuousCornerShape(14.dp),
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrinterRenderStage(
                model = device.printerModel,
                isActive = isSelected,
                size = 46.dp,
                cornerRadius = 12.dp
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = device.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isSelected) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill(text = "当前使用", color = BambuGreen)
                    }
                }
                Text(
                    text = "${device.displayModelName} • ${device.ip}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DeviceForm(
    initialDiscovered: BambuDiscoveredDevice?,
    onCancel: () -> Unit,
    onSave: (BambuDevice) -> Unit
) {
    var name by remember { mutableStateOf(initialDiscovered?.readableModel ?: "X2D") }
    var ip by remember { mutableStateOf(initialDiscovered?.ip ?: "") }
    var devId by remember { mutableStateOf(initialDiscovered?.devId ?: "") }
    var accessCode by remember { mutableStateOf("") }
    var selectedModel by remember {
        mutableStateOf(
            initialDiscovered?.printerModel?.takeIf { it != PrinterModel.UNKNOWN }
                ?: PrinterModel.X2D
        )
    }
    var showPassword by remember { mutableStateOf(false) }

    // 当用户输入序列号时，根据拓竹官方序列号前缀自动识别机型
    LaunchedEffect(devId) {
        val bySn = PrinterModel.fromSerial(devId)
        if (bySn != PrinterModel.UNKNOWN) {
            selectedModel = bySn
            if (name == "我的拓竹打印机" || PrinterModel.entries.any { it.displayName == name }) {
                name = bySn.displayName
            }
        }
    }

    val selectableModels = remember { PrinterModel.supportedModels }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 顶部实时机型预览栏
        SoftSurface(
            accent = BambuGreen,
            shape = ContinuousCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrinterRenderStage(
                    model = selectedModel,
                    isActive = true,
                    size = 52.dp,
                    cornerRadius = 12.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Bambu Lab ${selectedModel.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            softWrap = false,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(6.dp))
                        val snMatched = PrinterModel.fromSerial(devId) != PrinterModel.UNKNOWN
                        StatusPill(
                            text = if (snMatched || initialDiscovered != null) "已自动识别" else "支持SN识别",
                            color = BambuGreen,
                            dot = false
                        )
                    }
                    Text(
                        text = "输入序列号自动匹配机型，也可滑动下方标签手动指定",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 机型快速选择横向滑动条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            selectableModels.forEach { m ->
                val chosen = m == selectedModel
                Surface(
                    shape = ContinuousCornerShape(10.dp),
                    color = if (chosen) BambuGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, if (chosen) BambuGreen else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.clickable {
                        selectedModel = m
                        if (name == "我的拓竹打印机" || PrinterModel.entries.any { it.displayName == name }) {
                            name = m.displayName
                        }
                    }
                ) {
                    Text(
                        text = m.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                        color = if (chosen) BambuGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("打印机名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = ContinuousCornerShape(12.dp)
        )

        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text("局域网 IP 地址") },
            placeholder = { Text("例如 192.168.5.xx") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = ContinuousCornerShape(12.dp)
        )

        OutlinedTextField(
            value = devId,
            onValueChange = { devId = it },
            label = { Text("序列号 (DevID，用于订阅 MQTT 遥测主题)") },
            placeholder = { Text("打印机屏幕 → 设置 → 设备序列号") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = ContinuousCornerShape(12.dp)
        )

        OutlinedTextField(
            value = accessCode,
            onValueChange = { accessCode = it },
            label = { Text("局域网访问码 (Access Code)") },
            placeholder = { Text("8位访问码") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = ContinuousCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = ContinuousCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("返回", softWrap = false, maxLines = 1)
            }

            Button(
                onClick = {
                    if (ip.isNotBlank() && devId.isNotBlank() && accessCode.isNotBlank()) {
                        onSave(
                            BambuDevice(
                                id = devId.trim(),
                                name = name.trim().ifBlank { selectedModel.displayName },
                                ip = ip.trim(),
                                accessCode = accessCode.trim(),
                                model = selectedModel.displayName
                            )
                        )
                    }
                },
                enabled = ip.isNotBlank() && devId.isNotBlank() && accessCode.isNotBlank(),
                shape = ContinuousCornerShape(12.dp),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = BambuGreen)
            ) {
                Text("保存并直连", softWrap = false, maxLines = 1)
            }
        }
    }
}
