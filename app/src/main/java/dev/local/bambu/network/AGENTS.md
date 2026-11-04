# 网络与协议通信层规范 (AGENTS.md)

本目录承载与拓竹 3D 打印机局域网通信的核心底层实现。

---

## 1. 职能与约束

- **网络边界原则**：所有网络 I/O 必须在协程 `Dispatchers.IO` 下执行，禁止占用主线程。
- **协议契约**：
  - **SSDP 发现**：监听 `239.255.255.250:1900`，获取设备 IP 与 DevID。
  - **TLS MQTTS**：端口 8883，认证用户名固定为 `bblp`，密码为 Access Code，必须注入 TrustAll X509 信任管理器以兼容自签名证书。
  - **增量合并策略**：拓竹设备高频推送局部 JSON 切片，`BambuTelemetryParser` 必须采用增量合并（Delta Merging），防止局部温度数据冲刷掉文件名或 AMS 状态。

---

## 2. 核心文件索引

- `BambuDiscoveryEngine.kt`：管理 Wi-Fi 组播锁并监听 SSDP UDP 数据包。
- `BambuMqttClient.kt`：基于 Paho MQTT 实现 TLS 长连接、自动重连与指令下发。
- `BambuCameraStreamClient.kt`：局域网仓内实时视频监控流客户端，支持旗舰机型 RTSPS (Port 322, TLS + RTP/AVP/TCP Interleaved H.264 硬件解码) 与 P1/A1 系列 TLS 6000 端口二进制鉴权帧流。
- `BambuTelemetryParser.kt`：防崩溃的高容错 JSON 遥测解析与增量状态合并器（含 `ipcam.rtsp_url` 实时流配置解析）。
