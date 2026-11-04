# 核心业务包根目录规范 (AGENTS.md)

本包为应用的代码根命名空间 `dev.local.bambu`。

---

## 1. 包结构与职能分区

```text
dev.local.bambu/
├── model/        # 实体模型与枚举，无状态、不可变（Immutable）
├── network/      # 底层网络直连，处理 SSDP 组播、TLS MQTT 与 JSON 增量解析
├── data/         # 数据持久化层，负责已配对设备持久化与读取
└── ui/           # 表现层，基于 MVVM 构建的 Compose 视图体系
```

---

## 2. 根级类职责

- `MainActivity.kt`：单一活动宿主（Single Activity Architecture），负责全屏沉浸 Edge-to-Edge 窗口初始化与全局 SnackBar/主题分发。
- `BambuApplication.kt`：全局 Application 上下文。
