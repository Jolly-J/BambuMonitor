# App 模块规范与工程构建 (AGENTS.md)

本模块包含拓竹局域网监视器的 Android 应用源码、资源与构建配置。

---

## 1. 构建环境与依赖规范

- **构建系统**：Gradle 8.11.1 + AGP 8.9.2
- **语言标准**：Kotlin 2.1.20，目标平台 JVM 17
- **UI 框架**：Jetpack Compose BOM `2025.04.01`，Material 3
- **通信核心库**：`org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5`
- **混淆与优化**：`proguard-rules.pro` 必须保留 Paho MQTT 相关包名，避免反射被混淆导致 TLS 握手异常。

---

## 2. 权限声明与安全上下文

- `android.permission.INTERNET`：用于局域网 Socket 通信。
- `android.permission.ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE`：读取网络状态。
- `android.permission.CHANGE_WIFI_MULTICAST_STATE`：SSDP 发现所必需的 Wi-Fi 组播锁权限。
- `networkSecurityConfig`：支持局域网设备自签名 TLS 握手与本地明文诊断调试。
