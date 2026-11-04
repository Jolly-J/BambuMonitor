/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: Theme.kt
 * 职责: 北欧极简纯深色设计规范 (Dark Only)：中性灰阶色板 Token、唯一品牌主色、唯一警示色、G2 超椭圆形状集与主题 Provider
 * 层级: UI Design System Layer (dev.local.bambu.ui.theme)
 *
 * 色彩规范（全局仅允许以下 Token，禁止在组件内硬编码新颜色）：
 *   - 灰阶空间：SurfaceCanvas(底) → SurfacePanel(卡片) → SurfaceRaised(子卡片/按钮) → Hairline(微边框)
 *   - 文字墨色：InkPrimary(主数据) / InkSecondary(辅助说明) / InkTertiary(弱提示)
 *   - 品牌主色：BambuGreen —— 仅用于运行中状态点、进度圆环、在线指示
 *   - 警示色：ErrorRed —— 仅用于故障、停止、连接异常
 */

package dev.local.bambu.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ── 灰阶空间 ─────────────────────────────────────────────
val SurfaceCanvas = Color(0xFF0F1115)
val SurfacePanel = Color(0xFF181B20)
val SurfaceRaised = Color(0xFF20242B)
val Hairline = Color(0xFF262A32)

// ── 文字墨色 ─────────────────────────────────────────────
val InkPrimary = Color(0xFFF2F3F5)
val InkSecondary = Color(0xFF8C93A0)
val InkTertiary = Color(0xFF5A6170)

// ── 唯一品牌主色 & 唯一警示色 ─────────────────────────────
val BambuGreen = Color(0xFF00AE42)
val ErrorRed = Color(0xFFE5484D)

val DarkColorScheme = darkColorScheme(
    primary = BambuGreen,
    onPrimary = Color.White,
    primaryContainer = BambuGreen.copy(alpha = 0.16f),
    onPrimaryContainer = BambuGreen,
    secondary = InkSecondary,
    onSecondary = SurfaceCanvas,
    secondaryContainer = SurfaceRaised,
    onSecondaryContainer = InkPrimary,
    tertiary = InkSecondary,
    onTertiary = SurfaceCanvas,
    tertiaryContainer = SurfaceRaised,
    onTertiaryContainer = InkPrimary,
    error = ErrorRed,
    onError = Color.White,
    background = SurfaceCanvas,
    onBackground = InkPrimary,
    surface = SurfacePanel,
    onSurface = InkPrimary,
    surfaceContainerLowest = SurfaceCanvas,
    surfaceContainerLow = SurfacePanel,
    surfaceContainer = SurfacePanel,
    surfaceContainerHigh = SurfaceRaised,
    surfaceContainerHighest = SurfaceRaised,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = InkSecondary,
    outline = InkTertiary,
    outlineVariant = Hairline
)

val AppShapes = Shapes(
    extraSmall = ContinuousCornerShape(6.dp),
    small = ContinuousCornerShape(10.dp),
    medium = ContinuousCornerShape(14.dp),
    large = ContinuousCornerShape(18.dp),
    extraLarge = ContinuousCornerShape(24.dp)
)

@Composable
fun BambuMonitorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        shapes = AppShapes,
        content = content
    )
}
