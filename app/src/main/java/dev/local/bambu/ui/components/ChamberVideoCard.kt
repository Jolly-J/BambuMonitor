/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: ChamberVideoCard.kt
 * 职责: 仓内实时视频监控卡片与沉浸式硬件加速横屏全屏播放器组件，支持 16:9 无失真原生画幅硬件渲染与双向 Surface 切换
 * 层级: UI Components Layer (dev.local.bambu.ui.components)
 */

package dev.local.bambu.ui.components

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.local.bambu.R
import dev.local.bambu.model.*
import dev.local.bambu.ui.theme.*

@Composable
fun ChamberVideoCard(
    device: BambuDevice,
    status: BambuPrinterStatus,
    streamState: CameraStreamState,
    liveBitmap: Bitmap?,
    fps: Int,
    bitrateKbps: Int,
    streamError: String?,
    onSetCameraSurface: (Surface?) -> Unit = {},
    onToggleStream: () -> Unit,
    onToggleLight: () -> Unit,
    onEnterFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val model = device.printerModel
    val protocol = model.cameraProtocol
    val isStreaming = streamState == CameraStreamState.STREAMING
    val isConnecting = streamState == CameraStreamState.CONNECTING

    SoftSurface(
        shape = ContinuousCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // 1. 16:9 机舱广角实时监控视窗 (置顶，内嵌全屏按钮)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(ContinuousCornerShape(16.dp))
                    .background(Color(0xFF070B12))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleStream
                    )
            ) {
                // A. 视频底层渲染：RTSPS 322 走原生 SurfaceView 硬件直通流水线；TLS 6000 走 Bitmap
                if (protocol == CameraProtocol.RTSPS_322) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                clipToOutline = true
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        onSetCameraSurface(holder.surface)
                                    }
                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        onSetCameraSurface(null)
                                    }
                                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (isStreaming && liveBitmap != null) {
                    Image(
                        bitmap = liveBitmap.asImageBitmap(),
                        contentDescription = "仓内实时监控画面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // B. 未连接或断开时展示官方模糊占位底图
                if (!isStreaming) {
                    Image(
                        painter = painterResource(R.drawable.chamber_placeholder),
                        contentDescription = "未播放模糊占位背景",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // 在官方 Bambu Lab 标志下方放置半透明磨砂状态/播放胶囊
                    Surface(
                        shape = ContinuousCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.58f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 82.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    color = BambuGreen,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "正在连接 ${protocol.protocolBadge} 视频流...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    softWrap = false,
                                    maxLines = 1
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.PlayArrow,
                                    contentDescription = "点击播放",
                                    tint = if (streamError != null) ErrorRed else BambuGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = streamError ?: "点击播放仓内实时画面",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // 2. 顶部 OSD 叠加层 (保留 LIVE 指示、状态与独立全屏按钮)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isStreaming) BambuGreen else Color.White.copy(alpha = 0.45f))
                        )
                        Text(
                            text = when {
                                isStreaming -> "LIVE • ${status.ipcamResolution.uppercase()}"
                                isConnecting -> "正在握手..."
                                else -> "待机 • 点击画面播放"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            softWrap = false,
                            maxLines = 1
                        )
                        if (isStreaming) {
                            Text(
                                text = "$fps FPS",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 10.sp,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }

                    // 嵌入视频窗口内的独立全屏触发按钮 (大触控区域)
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onEnterFullscreen
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Fullscreen,
                                contentDescription = "进入全屏",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 3. 视窗下方补光灯胶囊开关按钮 (统一中性胶囊规范)
            ControlPill(
                icon = if (status.chamberLightOn) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                text = if (status.chamberLightOn) "照明：开启" else "照明：关闭",
                onClick = onToggleLight,
                contentColor = if (status.chamberLightOn) InkPrimary else InkSecondary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 沉浸式真全屏视频播放器覆盖层 (支持横屏旋转、16:9 原画幅杜绝拉伸畸变与系统物理返回键监听)
 */
@Composable
fun ChamberFullscreenOverlay(
    device: BambuDevice,
    status: BambuPrinterStatus,
    streamState: CameraStreamState,
    liveBitmap: Bitmap?,
    fps: Int,
    bitrateKbps: Int,
    onClose: () -> Unit,
    onToggleLight: () -> Unit,
    onSetCameraSurface: (Surface?) -> Unit
) {
    var showControls by remember { mutableStateOf(true) }
    val model = device.printerModel
    val isStreaming = streamState == CameraStreamState.STREAMING

    // 拦截返回键
    BackHandler {
        onClose()
    }

    // 获取所属 Activity 并控制横竖屏旋转
    val context = LocalContext.current
    val activity = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }

    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        // 自动强制进入横屏全屏模式 (支持传感器双向横屏旋转)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { showControls = !showControls }
            ),
        contentAlignment = Alignment.Center
    ) {
        // A. 底层 16:9 原生画幅渲染层 (居中，绝不拉伸畸变)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
                .align(Alignment.Center)
        ) {
            if (model.cameraProtocol == CameraProtocol.RTSPS_322) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    onSetCameraSurface(holder.surface)
                                }
                                override fun surfaceDestroyed(holder: SurfaceHolder) {}
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isStreaming && liveBitmap != null) {
                Image(
                    bitmap = liveBitmap.asImageBitmap(),
                    contentDescription = "全屏实时监控",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // B. 顶部悬浮控制栏
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(onClick = onClose)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.ArrowBack,
                                contentDescription = "退出全屏",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "${device.printerModel.displayName} 实时监控",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "局域网 ${device.ip}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 右侧状态指示与 FPS
                Surface(
                    shape = ContinuousCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isStreaming) BambuGreen else Color.Gray)
                        )
                        Text(
                            text = if (isStreaming) "${status.ipcamResolution.uppercase()} • $fps FPS" else "已暂停",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // C. 底部悬浮控制栏
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.90f))
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 补光灯快捷开关 (中性半透明灰白样式)
                Surface(
                    shape = ContinuousCornerShape(20.dp),
                    color = if (status.chamberLightOn) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(
                        1.dp,
                        if (status.chamberLightOn) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.clickable(onClick = onToggleLight)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (status.chamberLightOn) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                            contentDescription = "补光灯",
                            tint = if (status.chamberLightOn) Color.White else Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (status.chamberLightOn) "补光灯：开" else "补光灯：关",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (status.chamberLightOn) Color.White else Color.White.copy(alpha = 0.75f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 退出全屏按钮
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(onClick = onClose)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.FullscreenExit,
                            contentDescription = "退出全屏",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
