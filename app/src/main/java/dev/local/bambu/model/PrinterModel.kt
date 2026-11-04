/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: PrinterModel.kt
 * 职责: 拓竹全系 14 款打印机机型注册表，负责根据 SSDP 型号代码、序列号前缀或机型名称自动识别机型及机内摄像头流协议
 * 层级: Domain Model Layer (dev.local.bambu.model)
 *
 * 数据来源: Bambu Studio 官方开源仓库 resources/printers/{code}.json
 *          (型号代码 = 文件名，序列号前缀 = 文件内 serial 字段，机型名 = display_name)
 */

package dev.local.bambu.model

/**
 * 机型结构形态，用于 UI 决定是否展示机箱温度等差异化信息
 */
enum class PrinterForm {
    ENCLOSED,   // 封闭式恒温腔体 (X2D / X1 / P1S / P2S / H2 / H3 系列)
    OPEN_FRAME, // 开放 CoreXY 框架 (P1P)
    BED_SLINGER // 龙门/悬臂床式 (A1 / A1 mini)
}

/**
 * 拓竹局域网仓内实时视频监控传输协议
 */
enum class CameraProtocol(
    val defaultPort: Int,
    val protocolBadge: String,
    val resolutionSpec: String
) {
    /**
     * X2D / X1 / X1C / X1E / H2 / H3 / P2S 旗舰机型采用局域网 TLS 加密 RTSPS 协议 (Port 322, H.264)
     * 标准地址: rtsps://bblp:<AccessCode>@<IP>:322/streaming/live/1
     */
    RTSPS_322(322, "RTSPS 322", "1080P 30fps H.264"),

    /**
     * P1S / P1P / A1 / A1 mini 采用局域网 TLS 6000 端口二进制鉴权帧流协议
     */
    TLS_6000(6000, "TLS 6000", "720P JPEG Stream")
}

/**
 * 拓竹全系 14 款机型枚举
 *
 * @property displayName 对外展示名称
 * @property modelCodes SSDP DevModel 广播中的型号代码
 * @property serialPrefixes 序列号前三位特征前缀
 * @property form 机械结构形态
 * @property cameraProtocol 局域网仓内摄像头传输协议
 * @property aliases 用户手填或历史数据中可能出现的别名 (归一化后匹配)
 */
enum class PrinterModel(
    val displayName: String,
    val modelCodes: List<String>,
    val serialPrefixes: List<String>,
    val form: PrinterForm,
    val cameraProtocol: CameraProtocol = CameraProtocol.RTSPS_322,
    val aliases: List<String> = emptyList()
) {
    X2D("X2D", listOf("N6"), listOf("20P"), PrinterForm.ENCLOSED, CameraProtocol.RTSPS_322, listOf("x2d")),
    X1_CARBON("X1 Carbon", listOf("BL-P001", "3DPrinter-X1-Carbon"), listOf("00M"), PrinterForm.ENCLOSED, CameraProtocol.RTSPS_322, listOf("x1c", "x1carbon")),
    X1("X1", listOf("BL-P002", "3DPrinter-X1"), listOf("00W"), PrinterForm.ENCLOSED, CameraProtocol.RTSPS_322, listOf("x1")),
    X1E("X1E", listOf("C13"), listOf("03W"), PrinterForm.ENCLOSED, CameraProtocol.RTSPS_322, listOf("x1e")),
    H2D("H2D", listOf("O1D"), listOf("094"), PrinterForm.ENCLOSED, CameraProtocol.RTSPS_322, listOf("h2d")),
    H2D_PRO("H2D Pro", listOf("O1E"), listOf("239"), PrinterForm.ENCLOSED, CameraProtocol.RTSPS_322, listOf("h2dpro")),
    H2S("H2S", listOf("O1S"), listOf("093"), PrinterForm.ENCLOSED, CameraProtocol.RTSPS_322, listOf("h2s")),
    H2C("H2C", listOf("O1C", "O1C2"), listOf("31B"), PrinterForm.ENCLOSED, CameraProtocol.RTSPS_322, listOf("h2c")),
    H3C("H3C", listOf("N10"), listOf("32X"), PrinterForm.ENCLOSED, CameraProtocol.RTSPS_322, listOf("h3c")),
    P2S("P2S", listOf("N7"), listOf("22E"), PrinterForm.ENCLOSED, CameraProtocol.RTSPS_322, listOf("p2s")),
    P1S("P1S", listOf("C12"), listOf("01P"), PrinterForm.ENCLOSED, CameraProtocol.TLS_6000, listOf("p1s")),
    P1P("P1P", listOf("C11"), listOf("01S"), PrinterForm.OPEN_FRAME, CameraProtocol.TLS_6000, listOf("p1p")),
    A1("A1", listOf("N2S"), listOf("039"), PrinterForm.BED_SLINGER, CameraProtocol.TLS_6000, listOf("a1")),
    A1_MINI("A1 mini", listOf("N1"), listOf("030"), PrinterForm.BED_SLINGER, CameraProtocol.TLS_6000, listOf("a1mini")),
    UNKNOWN("拓竹打印机", emptyList(), emptyList(), PrinterForm.ENCLOSED, CameraProtocol.RTSPS_322);

    /**
     * 生成展示用的局域网实时流地址（打码或显式展示）
     */
    fun buildMaskedStreamEndpoint(ip: String, reportedRtspUrl: String = ""): String {
        if (reportedRtspUrl.startsWith("rtsps://", ignoreCase = true)) {
            return reportedRtspUrl.replaceFirst("rtsps://", "rtsps://bblp:••••••@")
        }
        return when (cameraProtocol) {
            CameraProtocol.RTSPS_322 -> "rtsps://bblp:••••••@$ip:322/streaming/live/1"
            CameraProtocol.TLS_6000 -> "tls://bblp:••••••@$ip:6000/chamber_mjpeg"
        }
    }

    companion object {

        /** 获取除 UNKNOWN 外的全部 14 款官方支持机型 */
        val supportedModels: List<PrinterModel>
            get() = entries.filter { it != UNKNOWN }

        /** 按 SSDP 型号代码精确识别 (大小写不敏感) */
        fun fromModelCode(code: String?): PrinterModel {
            val c = code?.trim().orEmpty()
            if (c.isEmpty()) return UNKNOWN
            return entries.firstOrNull { m -> m.modelCodes.any { it.equals(c, ignoreCase = true) } } ?: UNKNOWN
        }

        /** 按序列号前三位识别 */
        fun fromSerial(serial: String?): PrinterModel {
            val s = serial?.trim()?.uppercase().orEmpty()
            if (s.length < 3) return UNKNOWN
            val prefix = s.take(3)
            return entries.firstOrNull { m -> m.serialPrefixes.any { it == prefix } } ?: UNKNOWN
        }

        /** 按机型名称/别名识别，兼容 "Bambu Lab X2D"、"X1-Carbon"、"x1c" 等写法 */
        fun fromName(name: String?): PrinterModel {
            val n = normalize(name)
            if (n.isEmpty()) return UNKNOWN
            return entries.firstOrNull { m ->
                m != UNKNOWN && (normalize(m.displayName) == n || m.aliases.contains(n) || n.contains(normalize(m.displayName)))
            } ?: UNKNOWN
        }

        /**
         * 综合识别：型号代码 > 序列号前缀 > 名称别名
         */
        fun resolve(modelCodeOrName: String?, serial: String?): PrinterModel {
            fromModelCode(modelCodeOrName).takeIf { it != UNKNOWN }?.let { return it }
            fromSerial(serial).takeIf { it != UNKNOWN }?.let { return it }
            return fromName(modelCodeOrName)
        }

        private fun normalize(raw: String?): String =
            raw.orEmpty()
                .lowercase()
                .replace("bambu lab", "")
                .replace("bambulab", "")
                .filter { it.isLetterOrDigit() }
    }
}
