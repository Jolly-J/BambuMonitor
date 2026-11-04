# 资源目录规范 (AGENTS.md)

本目录承载 Android 系统的静态资源、主题、图标声明与拓竹官方透明底高清渲染图。

---

## 1. 结构与维护规则

- `drawable-nodpi/`：
  - 存放提取自拓竹 Bambu Studio 官方开源仓库并经边缘緊密裁剪的透明底无损 WebP 渲染图：
    - 机型渲染图：`printer_x1c.webp`, `printer_x1e.webp`, `printer_p1s.webp`, `printer_p1p.webp`, `printer_p2s.webp`, `printer_a1.webp`, `printer_a1mini.webp`, `printer_h2d.webp`, `printer_h2s.webp`, `printer_h2c.webp`, `printer_x2d.webp`, `printer_h3c.webp`, `printer_generic.webp`
    - 部件渲染图：`part_hotend.webp`（0.4mm 一体式硬化钢全金属热端总成）、`part_heatbed.webp`（拓竹官方金色纹理 PEI 弹簧钢热床板）、`part_ams.webp`（拓竹官方 4 槽位 AMS 供料器）
    - 仓内监控图像资源：`chamber_placeholder.webp`（拓竹 Bambu Studio 官方未播放模糊占位底图 `live_stream_default`）、`chamber_live_sample.webp`（拓竹官方仓内摄像头实拍打印画面）
- `values/`：
  - `styles.xml`：定义系统的基础透明沉浸式 NoActionBar 主题。
  - `strings.xml`：应用名称与系统级文本资源。
- `xml/`：
  - `network_security_config.xml`：局域网环境网络安全策略。
- `mipmap-*/` & `drawable-*/`：
  - 应用自适应启动图标（Adaptive Icons），保持全分辨率资源完整。

---

## 2. 约束原则

- 所有应用内动态组件的颜色、字号与形状均统一由 `ui/theme/` 内的 Kotlin Compose 主题系统驱动，禁止在 XML 中硬编码业务颜色。
