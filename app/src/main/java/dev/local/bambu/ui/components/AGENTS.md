# UI 业务卡片组件库规范 (AGENTS.md)

本目录承载经过细分、职责独立的业务卡片与高精度视觉组件。

---

## 1. 组件职能与独立性

每个卡片组件独立负责单一业务领域的展示与轻量交互，具备：
- **自适应弹性（Responsive & Adaptive）**：内部必须适配从窄屏（< 360dp，如折叠屏外屏）到宽屏（>= 600dp，如折叠屏展开）的尺寸变化。
- **亮/暗模式视觉增强**：渲染台与热端/热床示意图根据当前主题亮度动态调整背光光晕与描边对比度。
- **防止文字挤压换行（No Awkward Word-Wrapping）**：
  - 数值、单位、胶囊标签等短文案一律设置 `softWrap = false`, `maxLines = 1`；
  - 超长设备名或文件名必须明确 `overflow = TextOverflow.Ellipsis`；
  - 弹性权重与最小尺寸保护，严禁出现竖排文字现象。

---

## 2. 组件清单

- `PrinterArtwork.kt`：全系拓竹透明底打印机无框背景融合主舞台（`BlendedPrinterHeroShowcase`）、一体式热端总成无框渲染组件（`HotendIllustration`）、官方金色纹理 PEI 热床板无框渲染组件（`HeatbedIllustration`）及 AMS 无框渲染组件（`BlendedAmsIllustration`）。
- `PrintTaskHeroCard.kt`：打印进度大卡片（机型自动识别展台、弧形刻度、大字号百分比、剩余时间、紧凑防折行控制栏）。
- `ChamberVideoCard.kt`：仓内实时视频监控卡片（支持 RTSPS 322 与 TLS 6000 实时视频流渲染、OSD 帧率/码率/流地址叠加、腔体补光联动与机舱实时内窥仿真）。
- `ThermalControlCard.kt`：温控与风扇卡片（含透明底热端总成、PEI 热床示意图、机箱温度与部件风扇）。
- `AmsSystemCard.kt`：AMS 多色供料卡片（4 槽线盘、真实材质色、进料高亮、自适应网格/流式防挤压）。
- `DeviceInfoCard.kt`：打印机硬件识别结果与局域网参数卡片。
