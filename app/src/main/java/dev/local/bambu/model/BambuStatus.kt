/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: BambuStatus.kt
 * 职责: 拓竹打印机遥测状态快照、仓内视频监控状态、枚举定义与 AMS 多色数据模型
 * 层级: Domain Model Layer (dev.local.bambu.model)
 */

package dev.local.bambu.model

/**
 * 局域网连接生命周期状态
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * 打印机 GCode 执行状态
 */
enum class PrintState(val displayName: String) {
    OFFLINE("离线"),
    IDLE("空闲"),
    PREPARING("准备中"),
    RUNNING("打印中"),
    PAUSED("已暂停"),
    FINISHED("已完成"),
    FAILED("异常中止")
}

/**
 * 仓内视频流连接状态
 */
enum class CameraStreamState(val displayName: String) {
    IDLE("待机"),
    CONNECTING("RTSPS 握手中..."),
    STREAMING("实时推流中"),
    ERROR("流连接中断")
}

/**
 * 打印速度等级
 */
enum class SpeedLevel(val code: Int, val displayName: String, val speedPercent: String) {
    QUIET(1, "静音", "50%"),
    NORMAL(2, "标准", "100%"),
    SPORT(3, "运动", "124%"),
    LUDICROUS(4, "狂暴", "166%");

    companion object {
        fun fromCode(code: Int): SpeedLevel = when (code) {
            1 -> QUIET
            2 -> NORMAL
            3 -> SPORT
            4 -> LUDICROUS
            else -> NORMAL
        }
    }
}

/**
 * AMS 供料系统单个线材托盘数据
 *
 * @property id 槽位编号 (0..3)
 * @property colorHex 真实线材颜色 16进制代码 (如 "#00AE42")
 * @property material 线材品牌与材质 (如 "PLA Basic 拓竹绿")
 * @property remainPercent 剩余线材百分比 (0..100, -1为未知)
 * @property isActive 当前是否正在向工具头供料
 */
data class AmsTray(
    val id: Int,
    val colorHex: String,
    val material: String,
    val remainPercent: Int,
    val isActive: Boolean
) {
    /**
     * 窄屏适配模式下的极简材质代号 (如 "PLA", "PETG", "ABS")，防止窄卡片文字溢出换行
     */
    val shortMaterial: String
        get() {
            val upper = material.uppercase()
            return when {
                upper.contains("PLA") -> "PLA"
                upper.contains("PETG") -> "PETG"
                upper.contains("ABS") -> "ABS"
                upper.contains("TPU") -> "TPU"
                upper.contains("PC") -> "PC"
                upper.contains("PA") -> "PA"
                else -> material.take(5).ifBlank { "料槽${id + 1}" }
            }
        }
}

/**
 * AMS 单元设备
 */
data class AmsUnit(
    val id: Int,
    val temp: Float = 0f,
    val humidity: Int = 0,
    val humidityRaw: String = "",
    val trays: List<AmsTray> = emptyList()
)

/**
 * 拓竹打印机全局遥测状态聚合快照
 */
data class BambuPrinterStatus(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val errorMessage: String? = null,
    val printState: PrintState = PrintState.OFFLINE,
    val gcodeStateRaw: String = "UNKNOWN",
    val progressPercent: Int = 0,
    val remainingMinutes: Int = 0,
    val currentLayer: Int = 0,
    val totalLayers: Int = 0,
    val taskName: String = "",
    val nozzleTemp: Float = 0f,
    val nozzleTargetTemp: Float = 0f,
    val nozzleTempRight: Float = 0f,
    val nozzleTargetTempRight: Float = 0f,
    val hasDualNozzle: Boolean = false,
    val bedTemp: Float = 0f,
    val bedTargetTemp: Float = 0f,
    val chamberTemp: Float = 0f,
    val chamberTargetTemp: Float = 0f,
    val coolingFanSpeed: Int = 0, // 0..100 %
    val speedLevel: SpeedLevel = SpeedLevel.NORMAL,
    val chamberLightOn: Boolean = false,
    val amsUnits: List<AmsUnit> = emptyList(),
    val hmsErrors: List<String> = emptyList(),
    val wifiSignal: String = "",
    val rtspUrl: String = "",
    val ipcamResolution: String = "1080p",
    val ipcamRecord: Boolean = true,
    val timelapseEnabled: Boolean = false
) {
    val formattedRemainingTime: String
        get() {
            if (remainingMinutes <= 0) return "--"
            val hours = remainingMinutes / 60
            val mins = remainingMinutes % 60
            return if (hours > 0) "${hours}小时${mins}分" else "${mins}分钟"
        }

    val isHeatingNozzle: Boolean
        get() = nozzleTargetTemp > 0 && (nozzleTargetTemp - nozzleTemp) > 2f

    val isHeatingNozzleRight: Boolean
        get() = nozzleTargetTempRight > 0 && (nozzleTargetTempRight - nozzleTempRight) > 2f

    val isHeatingBed: Boolean
        get() = bedTargetTemp > 0 && (bedTargetTemp - bedTemp) > 2f
}
