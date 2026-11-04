/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: PrintTaskHeroCard.kt
 * 职责: 打印任务主卡片，将自动识别机型的透明底超清渲染图与卡片背景光影无感融合，并列呈现圆弧进度与快速控制
 * 层级: UI Components Layer (dev.local.bambu.ui.components)
 */

package dev.local.bambu.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.local.bambu.model.*
import dev.local.bambu.ui.theme.*

@Composable
fun PrintTaskHeroCard(
    device: BambuDevice,
    status: BambuPrinterStatus,
    onToggleLight: () -> Unit,
    onSetSpeedLevel: (SpeedLevel) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressFloat by animateFloatAsState(
        targetValue = status.progressPercent / 100f,
        animationSpec = tween(durationMillis = 600),
        label = "progress"
    )
    val recognizedModel = device.printerModel
    val isRunning = status.printState == PrintState.RUNNING

    SoftSurface(
        shape = ContinuousCornerShape(22.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val isNarrow = maxWidth < 360.dp

            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. 顶部状态栏与机型徽标
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        val stateColor = when (status.printState) {
                            PrintState.RUNNING -> BambuGreen
                            PrintState.PAUSED -> InkSecondary
                            PrintState.PREPARING -> InkSecondary
                            PrintState.FINISHED -> BambuGreen
                            PrintState.FAILED -> ErrorRed
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        StatusPill(text = status.printState.displayName, color = stateColor)

                        val formText = when (recognizedModel.form) {
                            PrinterForm.ENCLOSED -> "封闭恒温腔"
                            PrinterForm.OPEN_FRAME -> "开放 CoreXY"
                            PrinterForm.BED_SLINGER -> "高速龙门直驱"
                        }
                        Surface(
                            shape = ContinuousCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "${device.displayModelName} • $formText",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                softWrap = false,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (status.wifiSignal.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Wifi,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = status.wifiSignal,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 2. 当前 G-code 打印任务标题
                Text(
                    text = status.taskName.ifBlank { "当前未执行打印任务" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(10.dp))

                // 3. 沉浸式主视觉舞台：左侧大图无框打印机与背景光影融合，右侧圆弧进度仪表
                val stageHeight = if (isNarrow) 142.dp else 162.dp
                val gaugeSize = if (isNarrow) 122.dp else 138.dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stageHeight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：大图无边框透明底打印机，直接融入卡片光效背景
                    BlendedPrinterHeroShowcase(
                        model = recognizedModel,
                        isActive = isRunning,
                        height = stageHeight,
                        modifier = Modifier.weight(1.06f)
                    )

                    Spacer(Modifier.width(8.dp))

                    // 右侧：圆弧进度仪表盘
                    Box(
                        modifier = Modifier
                            .weight(0.94f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                        val activeColor = BambuGreen

                        Canvas(modifier = Modifier.size(gaugeSize)) {
                            val stroke = Stroke(
                                width = if (isNarrow) 9.5.dp.toPx() else 11.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawArc(
                                color = trackColor,
                                startAngle = 150f,
                                sweepAngle = 240f,
                                useCenter = false,
                                style = stroke
                            )
                            drawArc(
                                color = activeColor,
                                startAngle = 150f,
                                sweepAngle = 240f * progressFloat.coerceIn(0f, 1f),
                                useCenter = false,
                                style = stroke
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${status.progressPercent}%",
                                style = if (isNarrow) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                softWrap = false,
                                maxLines = 1
                            )
                            Text(
                                text = if (isRunning) "剩余 ${status.formattedRemainingTime}" else "就绪",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 4. 核心指标行（层数、预估剩余、打印速度）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetricColumn(
                        title = "当前层数",
                        value = if (status.totalLayers > 0) "${status.currentLayer}/${status.totalLayers}" else "--",
                        isNarrow = isNarrow
                    )
                    MetricColumn(
                        title = "预估剩余",
                        value = status.formattedRemainingTime,
                        isNarrow = isNarrow
                    )
                    MetricColumn(
                        title = "打印速度",
                        value = status.speedLevel.displayName,
                        isNarrow = isNarrow
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                Spacer(Modifier.height(12.dp))

                // 5. 设备控制第一层：打印速度与腔体照明两个辅助胶囊
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpeedSelectorMenu(
                        currentSpeed = status.speedLevel,
                        onSelect = onSetSpeedLevel,
                        modifier = Modifier.weight(1f)
                    )
                    ChamberLightPill(
                        isOn = status.chamberLightOn,
                        onToggle = onToggleLight,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 6. 设备控制第二层：独立的“暂停”与“停止”两个主行动卡片按钮 (与上方参数胶囊明确区分)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isPaused = status.printState == PrintState.PAUSED
                    ControlPill(
                        icon = if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        text = if (isPaused) "继续" else "暂停",
                        onClick = { if (isPaused) onResume() else onPause() },
                        modifier = Modifier.weight(1f)
                    )
                    ControlPill(
                        icon = Icons.Outlined.Stop,
                        text = "停止",
                        onClick = onStop,
                        contentColor = ErrorRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChamberLightPill(
    isOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    ControlPill(
        icon = if (isOn) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
        text = if (isOn) "照明 开" else "照明 关",
        onClick = onToggle,
        contentColor = if (isOn) InkPrimary else InkSecondary,
        modifier = modifier
    )
}

@Composable
private fun SpeedSelectorMenu(
    currentSpeed: SpeedLevel,
    onSelect: (SpeedLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        ControlPill(
            icon = Icons.Outlined.Speed,
            text = "${currentSpeed.displayName} ${currentSpeed.speedPercent}",
            onClick = { expanded = true },
            iconTint = InkSecondary,
            modifier = Modifier.fillMaxWidth(),
            trailing = {
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = InkSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = ContinuousCornerShape(14.dp)
        ) {
            SpeedLevel.entries.forEach { level ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${level.displayName} (${level.speedPercent})",
                            fontWeight = if (level == currentSpeed) FontWeight.Bold else FontWeight.Normal,
                            color = if (level == currentSpeed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onSelect(level)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(title: String, value: String, isNarrow: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = if (isNarrow) 10.sp else 11.sp,
            softWrap = false,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = if (isNarrow) 13.sp else 14.sp,
            softWrap = false,
            maxLines = 1
        )
    }
}
