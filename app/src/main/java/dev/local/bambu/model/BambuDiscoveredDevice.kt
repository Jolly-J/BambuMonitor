/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: BambuDiscoveredDevice.kt
 * 职责: 局域网 SSDP 广播扫描阶段发现的原始拓竹设备数据实体
 * 层级: Domain Model Layer (dev.local.bambu.model)
 */

package dev.local.bambu.model

/**
 * 局域网广播发现的设备数据模型
 *
 * @property devId 设备序列号
 * @property devName 广播附带的设备名称
 * @property devModel 原始型号代码 (如 "BL-P001", "C11", "C12", "N1", "N2S", "O1D")
 * @property ip 探测到的局域网 IPv4 地址
 */
data class BambuDiscoveredDevice(
    val devId: String,
    val devName: String,
    val devModel: String,
    val ip: String
) {
    val printerModel: PrinterModel
        get() = PrinterModel.resolve(modelCodeOrName = devModel, serial = devId)

    val readableModel: String
        get() {
            val resolved = printerModel
            return if (resolved != PrinterModel.UNKNOWN) resolved.displayName else devModel.ifBlank { "拓竹打印机" }
        }
}
