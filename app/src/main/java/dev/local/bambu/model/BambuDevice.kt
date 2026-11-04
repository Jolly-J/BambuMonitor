/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: BambuDevice.kt
 * 职责: 拓竹打印机配置实体类，定义局域网直连所需的核心参数与自动识别机型属性
 * 层级: Domain Model Layer (dev.local.bambu.model)
 */

package dev.local.bambu.model

/**
 * 拓竹打印机已配对设备数据结构
 *
 * @property id 打印机唯一序列号 (DevID / Serial Number，例如 01P00A123456789)
 * @property name 用户自定义的打印机别名 (例如 "客厅的 P1S")
 * @property ip 局域网内分配的静态或动态 IP 地址 (例如 "192.168.1.188")
 * @property accessCode 打印机屏幕显示的局域网访问码 (LAN Mode Access Code)
 * @property model 打印机硬件型号文本或代号 (例如 "X1 Carbon", "P1S", "A1 mini")
 */
data class BambuDevice(
    val id: String,
    val name: String,
    val ip: String,
    val accessCode: String,
    val model: String = "Bambu Printer"
) {
    /**
     * 自动识别出的标准拓竹机型枚举（综合匹配型号代号、序列号前缀与型号名）
     */
    val printerModel: PrinterModel
        get() = PrinterModel.resolve(modelCodeOrName = model, serial = id)

    /**
     * 标准化后的展示机型名称
     */
    val displayModelName: String
        get() {
            val resolved = printerModel
            return if (resolved != PrinterModel.UNKNOWN) resolved.displayName else model.ifBlank { "拓竹打印机" }
        }
}
