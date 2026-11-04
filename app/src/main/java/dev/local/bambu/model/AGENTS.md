# 领域模型层规范 (AGENTS.md)

本目录承载系统的所有领域数据对象（Domain Data Models）、状态枚举与全系机型自动识别注册表。

---

## 1. 职能与约束

- **纯数据结构**：所有类必须是 Kotlin `data class` 或 `enum class`，不包含网络、数据库或 Android UI 相关逻辑。
- **不可变性（Immutability）**：所有字段必须使用 `val` 声明，确保并发更新与 Compose 状态重组（Recomposition）的确定性。
- **派生只读属性**：可提供纯函数式的计算属性（如温度加热判定 `isHeating`、时间格式化 `formattedRemainingTime`、机型自动解析 `printerModel`）。

---

## 2. 核心文件索引

- `PrinterModel.kt`：拓竹全系机型注册表（X1 Carbon / X1E / P1S / P1P / P2S / A1 / A1 mini / H2D / H2S / H2C / X2D / H3C），依据 Bambu Studio 官方定义实现按 SSDP 型号代号、序列号前三位及别名的三级自动识别。
- `BambuDevice.kt`：已配对打印机的实体信息（IP、序列号、局域网访问码、型号、别名及派生标准机型）。
- `BambuStatus.kt`：打印机实时遥测状态快照、温控、打印状态枚举、AMS 单元与槽位数据。
- `BambuDiscoveredDevice.kt`：通过局域网广播发现的原始打印机设备信息。
