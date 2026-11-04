/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: AmsSystemCard.kt
 * 职责: AMS 多色供料系统卡片，融入官方透明底 AMS 渲染图，展示各槽位线盘真实色值、材质与温湿度
 * 层级: UI Components Layer (dev.local.bambu.ui.components)
 */

package dev.local.bambu.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.local.bambu.model.AmsTray
import dev.local.bambu.model.AmsUnit
import dev.local.bambu.ui.theme.*

@Composable
fun AmsSystemCard(
    amsUnits: List<AmsUnit>,
    modifier: Modifier = Modifier
) {
    SoftSurface(
        shape = ContinuousCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            val isNarrow = maxWidth < 380.dp

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "AMS 多色供料系统",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = InkPrimary,
                                softWrap = false,
                                maxLines = 1
                            )
                            val firstUnit = amsUnits.firstOrNull()
                            if (firstUnit != null) {
                                val humText = when {
                                    firstUnit.humidityRaw.isNotBlank() -> "${firstUnit.humidityRaw}%"
                                    firstUnit.humidity in 1..5 -> "${firstUnit.humidity * 20}%"
                                    firstUnit.humidity > 5 -> "${firstUnit.humidity}%"
                                    else -> ""
                                }
                                val tempText = if (firstUnit.temp > 0f) "${"%.0f".format(firstUnit.temp)}°C" else ""
                                val displayMetrics = listOfNotNull(
                                    if (humText.isNotBlank()) "湿度 $humText" else null,
                                    if (tempText.isNotBlank()) tempText else null
                                ).joinToString(" • ")
                                if (displayMetrics.isNotBlank()) {
                                    StatusPill(
                                        text = displayMetrics,
                                        color = InkSecondary,
                                        dot = false,
                                        neutral = true
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "4 槽位 RFID 自动识别 • 密封除湿供料",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSecondary,
                            fontSize = 11.sp,
                            softWrap = false,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    BlendedAmsIllustration()
                }

                Spacer(Modifier.height(12.dp))

                amsUnits.forEachIndexed { unitIndex, unit ->
                    if (unitIndex > 0) Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (isNarrow) 6.dp else 8.dp)
                    ) {
                        unit.trays.forEach { tray ->
                            AmsTrayItem(
                                tray = tray,
                                isNarrow = isNarrow,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmsTrayItem(
    tray: AmsTray,
    isNarrow: Boolean,
    modifier: Modifier = Modifier
) {
    val filamentColor = runCatching {
        Color(android.graphics.Color.parseColor(tray.colorHex))
    }.getOrDefault(BambuGreen)

    val labelText = if (isNarrow) tray.shortMaterial else tray.material.ifBlank { "槽位 ${tray.id + 1}" }

    Surface(
        shape = ContinuousCornerShape(14.dp),
        color = SurfaceRaised,
        border = BorderStroke(
            1.dp,
            if (tray.isActive) BambuGreen else Hairline
        ),
        modifier = modifier.heightIn(min = 72.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Visual Spool Indicator (保留耗材本身的物理颜色)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(if (isNarrow) 30.dp else 34.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = filamentColor,
                    border = BorderStroke(1.5.dp, Hairline),
                    modifier = Modifier.fillMaxSize()
                ) {}

                // Active dot if feeding
                if (tray.isActive) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(8.dp)
                    ) {}
                }
            }

            Spacer(Modifier.height(6.dp))

            // Material label: strictly maxLines = 1 to never stretch card vertically
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (tray.isActive) InkPrimary else InkSecondary,
                fontSize = if (isNarrow) 10.sp else 11.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
