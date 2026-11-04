/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: InterfaceSurfaces.kt
 * 职责: UI 微边框柔和面板、Apple-grade 质感图标容器与防折行胶囊组件
 * 层级: UI Design System Layer (dev.local.bambu.ui.theme)
 */

package dev.local.bambu.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 借鉴自《小蝠》的 SoftSurface 柔和微边框面板：
 * 舍弃沉重浑浊的全局阴影，采用 1.dp 极细微半透明轮廓线与受控微弱着色，保证通透高级感。
 */
@Composable
fun SoftSurface(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    shape: Shape = ContinuousCornerShape(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val clickableModifier = if (onClick != null) {
        modifier.clip(shape).clickable(onClick = onClick)
    } else {
        modifier.clip(shape)
    }

    Surface(
        modifier = clickableModifier,
        shape = shape,
        color = accent?.copy(alpha = 0.035f)?.compositeOver(MaterialTheme.colorScheme.surface)
            ?: MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            accent?.copy(alpha = 0.25f)?.compositeOver(MaterialTheme.colorScheme.surface)
                ?: MaterialTheme.colorScheme.outlineVariant
        ),
        content = content
    )
}

/**
 * 圆形操作图标按钮
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String?,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, description, tint = tint)
        }
    }
}

/**
 * 超椭圆强调色图标底托容器
 */
@Composable
fun AccentIcon(
    icon: ImageVector,
    color: Color,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp
) {
    Surface(
        shape = ContinuousCornerShape(size * 0.32f),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * 紧凑状态胶囊：严禁换行拉高行高，具备 softWrap = false 与 maxLines = 1 保护
 */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    dot: Boolean = true,
    neutral: Boolean = false
) {
    Surface(
        shape = ContinuousCornerShape(8.dp),
        color = if (neutral) SurfaceRaised else color.copy(alpha = 0.12f),
        border = if (neutral) BorderStroke(1.dp, Hairline) else null,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dot) {
                Surface(
                    shape = CircleShape,
                    color = color,
                    modifier = Modifier.size(6.dp)
                ) {}
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                softWrap = false,
                maxLines = 1
            )
        }
    }
}

/**
 * 统一中性控制胶囊：全站所有操作按钮（速度、照明、暂停、停止）共用同一质感。
 * 底色 SurfaceRaised + Hairline 微边框；仅通过 contentColor 区分语义（默认 InkPrimary，停止用 ErrorRed）。
 */
@Composable
fun ControlPill(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = InkPrimary,
    iconTint: Color = contentColor,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(
        shape = ContinuousCornerShape(12.dp),
        color = SurfaceRaised,
        border = BorderStroke(1.dp, Hairline),
        modifier = modifier
            .clip(ContinuousCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                softWrap = false,
                maxLines = 1
            )
            trailing?.invoke()
        }
    }
}
