/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: PrinterArtwork.kt
 * 职责: 拓竹全系打印机、一体式热端总成、PEI 纹理打印热床与 AMS 的无边框背景融合渲染组件（自适应亮/暗模式）
 * 层级: UI Components Layer (dev.local.bambu.ui.components)
 */

package dev.local.bambu.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.local.bambu.R
import dev.local.bambu.model.PrinterModel
import dev.local.bambu.ui.theme.*

/**
 * 将识别到的拓竹机型映射到官方透明底无损 WebP 渲染图资源
 */
@DrawableRes
fun PrinterModel.drawableRes(): Int = when (this) {
    PrinterModel.X1_CARBON, PrinterModel.X1 -> R.drawable.printer_x1c
    PrinterModel.X1E -> R.drawable.printer_x1e
    PrinterModel.P1S -> R.drawable.printer_p1s
    PrinterModel.P1P -> R.drawable.printer_p1p
    PrinterModel.P2S -> R.drawable.printer_p2s
    PrinterModel.A1 -> R.drawable.printer_a1
    PrinterModel.A1_MINI -> R.drawable.printer_a1mini
    PrinterModel.H2D, PrinterModel.H2D_PRO -> R.drawable.printer_h2d
    PrinterModel.H2S -> R.drawable.printer_h2s
    PrinterModel.H2C -> R.drawable.printer_h2c
    PrinterModel.X2D -> R.drawable.printer_x2d
    PrinterModel.H3C -> R.drawable.printer_h3c
    PrinterModel.UNKNOWN -> R.drawable.printer_generic
}

/**
 * 无边框沉浸式打印机主视觉融合组件：
 * 摒弃传统“小图标方框”，通过多级径向环境光晕、暗部轮廓背光、底部椭圆阴影及边缘 Alpha 羽化，
 * 将超清透明底打印机直接融入卡片背景之中。
 */
@Composable
fun BlendedPrinterHeroShowcase(
    model: PrinterModel,
    isActive: Boolean = false,
    height: Dp = 152.dp,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // 纯深色规范：中性白色环境光，运行状态由卡片边框与流光表达，不再给机身铺色
    val primaryGlow = Color.White.copy(alpha = if (isActive) 0.07f else 0.05f)
    val rimLight = Color.White.copy(alpha = 0.12f)
    val floorShadow = Color.Black.copy(alpha = 0.55f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .drawBehind {
                val w = size.width
                val h = size.height

                // 1. 大范围环境氛围光晕，向四周卡片背景平滑消散
                val outerRadius = maxOf(w, h) * 0.72f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryGlow, Color.Transparent),
                        center = Offset(w * 0.5f, h * 0.48f),
                        radius = outerRadius
                    ),
                    radius = outerRadius,
                    center = Offset(w * 0.5f, h * 0.48f)
                )

                // 2. 机身轮廓摄影棚背光，让深色机身（P1S/P1P/X1E）在暗色背景中依然通透立体
                val rimRadius = minOf(w, h) * 0.46f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(rimLight, Color.Transparent),
                        center = Offset(w * 0.5f, h * 0.44f),
                        radius = rimRadius
                    ),
                    radius = rimRadius,
                    center = Offset(w * 0.5f, h * 0.44f)
                )

                // 3. 底部接触面柔和椭圆环境投影，使机器稳固“站立”在卡片空间内
                val ovalW = w * 0.68f
                val ovalH = h * 0.11f
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(floorShadow, Color.Transparent),
                        center = Offset(w * 0.5f, h * 0.90f),
                        radius = ovalW * 0.55f
                    ),
                    topLeft = Offset((w - ovalW) / 2f, h * 0.85f),
                    size = Size(ovalW, ovalH)
                )
            }
    ) {
        Image(
            painter = painterResource(model.drawableRes()),
            contentDescription = model.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 4.dp)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    // 底部极轻微渐隐过渡，消除生硬切边感，使底座与环境光晕自然相融
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Black,
                            0.88f to Color.Black,
                            1.0f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        )
    }
}

/**
 * 紧凑型无边框背景融合机型展示（用于弹窗列表与设备卡片角落）
 */
@Composable
fun PrinterRenderStage(
    model: PrinterModel,
    isActive: Boolean = false,
    size: Dp = 64.dp,
    cornerRadius: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glowColor = Color.White.copy(alpha = if (isActive) 0.10f else 0.07f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .drawBehind {
                val r = this.size.minDimension * 0.62f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                        center = Offset(this.size.width * 0.5f, this.size.height * 0.52f),
                        radius = r
                    ),
                    radius = r
                )
            }
    ) {
        Image(
            painter = painterResource(model.drawableRes()),
            contentDescription = model.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 喷头热端总成无边框背景融合组件：
 * 去除外框，直接将官方透明底一体式热端悬浮于温控卡片右侧，喷嘴尖端散发动态橙红热辐射光晕。
 */
@Composable
fun HotendIllustration(
    isHeating: Boolean,
    currentTemp: Float,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val isHot = isHeating || currentTemp >= 50f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 64.dp, height = 84.dp)
            .drawBehind {
                // 1. 环境热辐射底光直接向卡片背景扩散
                val ambientRadius = size.maxDimension * 0.78f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            InkSecondary.copy(alpha = if (isHot) (if (isDark) 0.28f else 0.18f) else 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.54f, size.height * 0.55f),
                        radius = ambientRadius
                    ),
                    radius = ambientRadius,
                    center = Offset(size.width * 0.54f, size.height * 0.55f)
                )

                // 2. 暗色模式下为黑色散热鳍片提供柔和冷白轮廓背光
                if (isDark) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.38f),
                            radius = size.width * 0.62f
                        ),
                        radius = size.width * 0.62f,
                        center = Offset(size.width * 0.5f, size.height * 0.38f)
                    )
                }

                // 3. 喷嘴尖端高温聚焦辉光
                if (isHot) {
                    val tipCenter = Offset(size.width * 0.5f, size.height * 0.85f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                InkSecondary.copy(alpha = if (isHeating) 0.55f else 0.35f),
                                Color.Transparent
                            ),
                            center = tipCenter,
                            radius = size.width * 0.48f
                        ),
                        radius = size.width * 0.48f,
                        center = tipCenter
                    )
                }
            }
    ) {
        Image(
            painter = painterResource(R.drawable.part_hotend),
            contentDescription = "拓竹一体式热端总成",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.08f to Color.Black,
                            0.94f to Color.Black,
                            1.0f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        )
    }
}

/**
 * 拓竹官方金色 PEI 纹理弹簧钢热床板无边框背景融合组件：
 * 采用官方超清透明底 PEI 纹理板实拍图，微倾透视悬浮于热床温控卡右侧，配合琥珀色热辐射柔光与背景融合。
 */
@Composable
fun HeatbedIllustration(
    isHeating: Boolean,
    currentTemp: Float,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val isHot = isHeating || currentTemp >= 40f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 74.dp, height = 84.dp)
            .drawBehind {
                val glowRadius = size.maxDimension * 0.76f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            InkSecondary.copy(alpha = if (isHot) (if (isDark) 0.30f else 0.20f) else 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.52f, size.height * 0.52f),
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = Offset(size.width * 0.52f, size.height * 0.52f)
                )
            }
    ) {
        Image(
            painter = painterResource(R.drawable.part_heatbed),
            contentDescription = "拓竹金色纹理 PEI 打印热床板",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .graphicsLayer(
                    rotationZ = -6f
                )
        )
    }
}

/**
 * 拓竹官方 AMS 供料器无边框背景融合视觉组件
 */
@Composable
fun BlendedAmsIllustration(
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 88.dp, height = 42.dp)
            .drawBehind {
                val r = size.width * 0.58f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDark) Color.White.copy(alpha = 0.14f) else InkSecondary.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                        radius = r
                    ),
                    radius = r
                )
            }
    ) {
        Image(
            painter = painterResource(R.drawable.part_ams),
            contentDescription = "Bambu Lab AMS",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
