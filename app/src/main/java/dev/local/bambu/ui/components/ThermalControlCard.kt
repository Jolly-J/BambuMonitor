/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: ThermalControlCard.kt
 * 职责: 热端与热床温控卡片，适配 X2D 双独立喷头与单喷头全系机型，遵循北欧极简中性深色规范
 * 层级: UI Components Layer (dev.local.bambu.ui.components)
 */

package dev.local.bambu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.local.bambu.model.BambuPrinterStatus
import dev.local.bambu.ui.theme.*

@Composable
fun ThermalControlCard(
    status: BambuPrinterStatus,
    modifier: Modifier = Modifier
) {
    SoftSurface(
        shape = ContinuousCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // 1. 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "热端与热床温控",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                    softWrap = false,
                    maxLines = 1
                )
                Text(
                    text = if (status.hasDualNozzle) "X2D 双喷头 • PEI 板" else "0.4mm 硬化钢 • PEI 纹理板",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSecondary,
                    fontSize = 11.sp,
                    softWrap = false,
                    maxLines = 1
                )
            }

            Spacer(Modifier.height(10.dp))

            // 2. 上半部分：核心加热温控面板 (统一 SurfaceRaised 中性灰阶 + Hairline 边框)
            if (status.hasDualNozzle) {
                // X2D 双独立喷头场景：三栏等宽并列 (左喷头 L、右喷头 R、打印热床)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThermalMetricTile(
                        label = "左喷头 (L)",
                        currentTemp = status.nozzleTemp,
                        targetTemp = status.nozzleTargetTemp,
                        modifier = Modifier.weight(1f)
                    )

                    ThermalMetricTile(
                        label = "右喷头 (R)",
                        currentTemp = status.nozzleTempRight,
                        targetTemp = status.nozzleTargetTempRight,
                        modifier = Modifier.weight(1f)
                    )

                    ThermalMetricTile(
                        label = "打印热床",
                        currentTemp = status.bedTemp,
                        targetTemp = status.bedTargetTemp,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // 单喷头场景：两栏等宽并列 (喷头热端、打印热床)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThermalMetricTile(
                        label = "喷头热端",
                        currentTemp = status.nozzleTemp,
                        targetTemp = status.nozzleTargetTemp,
                        modifier = Modifier.weight(1f)
                    )

                    ThermalMetricTile(
                        label = "打印热床",
                        currentTemp = status.bedTemp,
                        targetTemp = status.bedTargetTemp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Hairline, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            // 3. 下半部分：机箱腔体温度与部件风扇 (左右对称大面板，统一中性图标与灰阶底色)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AmbientMetricTile(
                    icon = Icons.Outlined.Thermostat,
                    title = "机箱腔体温度",
                    value = if (status.chamberTemp > 0) "${"%.1f".format(status.chamberTemp)}°C" else "33.0°C",
                    modifier = Modifier.weight(1f)
                )

                AmbientMetricTile(
                    icon = Icons.Outlined.Air,
                    title = "部件散热风扇",
                    value = "${status.coolingFanSpeed}%",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 核心加热温控指标卡片 (中性极简深色规范，依靠字重与明暗传递层级)
 */
@Composable
private fun ThermalMetricTile(
    label: String,
    currentTemp: Float,
    targetTemp: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = ContinuousCornerShape(14.dp),
        color = SurfaceRaised,
        border = BorderStroke(1.dp, Hairline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = InkSecondary,
                softWrap = false,
                maxLines = 1
            )

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%.0f".format(currentTemp),
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                    softWrap = false,
                    maxLines = 1
                )
                Text(
                    text = "°C",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSecondary,
                    modifier = Modifier.padding(bottom = 2.dp, start = 2.dp),
                    softWrap = false,
                    maxLines = 1
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = "目标 ${targetTemp.toInt()}°C",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = InkTertiary,
                softWrap = false,
                maxLines = 1
            )
        }
    }
}

/**
 * 辅助环境指标卡片 (机箱腔体温度与部件风扇，左右对称，统一中性灰阶)
 */
@Composable
private fun AmbientMetricTile(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = ContinuousCornerShape(14.dp),
        color = SurfaceRaised,
        border = BorderStroke(1.dp, Hairline),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = ContinuousCornerShape(10.dp),
                color = SurfacePanel,
                border = BorderStroke(1.dp, Hairline),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = InkSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = InkSecondary,
                    softWrap = false,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                    softWrap = false,
                    maxLines = 1
                )
            }
        }
    }
}
