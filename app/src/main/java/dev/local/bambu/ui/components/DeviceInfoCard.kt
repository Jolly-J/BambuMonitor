/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: DeviceInfoCard.kt
 * 职责: 打印机局域网网络参数、视频流协议与硬件序列号自动识别展示卡片
 * 层级: UI Components Layer (dev.local.bambu.ui.components)
 */

package dev.local.bambu.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.local.bambu.model.BambuDevice
import dev.local.bambu.model.BambuPrinterStatus
import dev.local.bambu.model.PrinterForm
import dev.local.bambu.ui.theme.*

@Composable
fun DeviceInfoCard(
    device: BambuDevice,
    status: BambuPrinterStatus,
    modifier: Modifier = Modifier
) {
    val recognized = device.printerModel
    val structureDesc = when (recognized.form) {
        PrinterForm.ENCLOSED -> "CoreXY 封闭式恒温腔体"
        PrinterForm.OPEN_FRAME -> "CoreXY 开放式框架"
        PrinterForm.BED_SLINGER -> "龙门线性导轨直驱"
    }

    SoftSurface(
        shape = ContinuousCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "打印机硬件与局域网协议",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                    softWrap = false,
                    maxLines = 1
                )
                StatusPill(
                    text = "已识别: ${device.displayModelName}",
                    color = InkSecondary,
                    dot = false,
                    neutral = true
                )
            }
            Spacer(Modifier.height(12.dp))

            InfoRow(label = "识别机型", value = "Bambu Lab ${device.displayModelName}")
            InfoRow(label = "机械架构", value = structureDesc)
            InfoRow(label = "遥测协议", value = "TLS MQTT (Port 8883)")
            InfoRow(label = "视频流协议", value = "${recognized.cameraProtocol.protocolBadge} (${recognized.cameraProtocol.resolutionSpec})")
            InfoRow(label = "局域网 IP", value = device.ip)
            InfoRow(label = "序列号 (DevID)", value = device.id)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = InkSecondary,
            softWrap = false,
            maxLines = 1
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = InkPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
