/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: BambuDiscoveryEngine.kt
 * 职责: 基于 SSDP (UDP 1900/2021/1990) 广播与局域网 /24 子网 8883 端口 TLS 证书探测的双引擎自动发现模块
 * 层级: Network Communication Layer (dev.local.bambu.network)
 */

package dev.local.bambu.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import dev.local.bambu.model.BambuDiscoveredDevice
import dev.local.bambu.model.PrinterModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class BambuDiscoveryEngine(private val context: Context) {

    private val _discoveredDevices = MutableStateFlow<List<BambuDiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BambuDiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    suspend fun scan(timeoutMillis: Long = 5500L) = withContext(Dispatchers.IO) {
        if (_isScanning.value) return@withContext
        _isScanning.value = true

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("bambu_ssdp_lock")?.apply {
            setReferenceCounted(true)
            acquire()
        }

        val foundMap = ConcurrentHashMap<String, BambuDiscoveredDevice>()

        try {
            coroutineScope {
                // 引擎 1：SSDP 多端口广播与监听 (UDP 1900 / 2021 / 1990)
                val ssdpJob = launch {
                    runSsdpDiscovery(timeoutMillis, foundMap)
                }

                // 引擎 2：局域网 /24 子网 8883 MQTTS 端口并发探测（防止路由器隔离组播包）
                val subnetJob = launch {
                    runSubnet8883Probe(foundMap)
                }

                ssdpJob.join()
                subnetJob.join()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in discovery: ${e.message}", e)
        } finally {
            runCatching { multicastLock?.release() }
            _isScanning.value = false
        }
    }

    private suspend fun CoroutineScope.runSsdpDiscovery(
        timeoutMillis: Long,
        foundMap: ConcurrentHashMap<String, BambuDiscoveredDevice>
    ) {
        val sockets = mutableListOf<DatagramSocket>()
        try {
            val activeSocket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = 400
                bind(InetSocketAddress(0))
            }
            sockets.add(activeSocket)

            // 同时尝试绑定拓竹打印机主动 NOTIFY 广播常用的 2021 与 1990 端口
            for (listenPort in listOf(2021, 1990)) {
                runCatching {
                    val s = DatagramSocket(null).apply {
                        reuseAddress = true
                        broadcast = true
                        soTimeout = 300
                        bind(InetSocketAddress(listenPort))
                    }
                    sockets.add(s)
                }
            }

            val bambuSearch = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: urn:bambulab-com:device:3dprinter:1\r\n\r\n"

            val genericSearch = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 2\r\n" +
                    "ST: ssdp:all\r\n\r\n"

            val targets = listOf(
                InetAddress.getByName("239.255.255.250") to 1900,
                InetAddress.getByName("239.255.255.250") to 2021,
                InetAddress.getByName("255.255.255.255") to 2021,
                InetAddress.getByName("255.255.255.255") to 1990
            )

            for ((addr, port) in targets) {
                runCatching {
                    activeSocket.send(DatagramPacket(bambuSearch.toByteArray(), bambuSearch.length, addr, port))
                    activeSocket.send(DatagramPacket(genericSearch.toByteArray(), genericSearch.length, addr, port))
                }
            }

            val startTime = System.currentTimeMillis()
            val buffer = ByteArray(4096)

            while (isActive && (System.currentTimeMillis() - startTime) < timeoutMillis) {
                for (sock in sockets) {
                    try {
                        val recvPacket = DatagramPacket(buffer, buffer.size)
                        sock.receive(recvPacket)
                        val rawResponse = String(recvPacket.data, 0, recvPacket.length, Charsets.UTF_8)
                        val senderIp = recvPacket.address.hostAddress ?: continue

                        val device = parseSsdpResponse(rawResponse, senderIp)
                        if (device != null) {
                            foundMap[device.ip] = device
                            _discoveredDevices.value = foundMap.values.toList()
                        }
                    } catch (_: SocketTimeoutException) {
                        // Normal non-blocking poll
                    } catch (_: Exception) {
                    }
                }
            }
        } finally {
            sockets.forEach { runCatching { it.close() } }
        }
    }

    private suspend fun CoroutineScope.runSubnet8883Probe(
        foundMap: ConcurrentHashMap<String, BambuDiscoveredDevice>
    ) {
        val localIp = getLocalIpv4Address() ?: return
        val prefix = localIp.substringBeforeLast('.', "")
        if (prefix.isEmpty()) return

        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }

        val semaphore = kotlinx.coroutines.sync.Semaphore(36)
        val jobs = (1..254).map { hostId ->
            val targetIp = "$prefix.$hostId"
            launch {
                if (targetIp == localIp || foundMap.containsKey(targetIp)) return@launch
                semaphore.acquire()
                try {
                    val discovered = probeHost8883(sslContext, targetIp)
                    if (discovered != null && !foundMap.containsKey(discovered.ip)) {
                        foundMap[discovered.ip] = discovered
                        _discoveredDevices.value = foundMap.values.toList()
                    }
                } finally {
                    semaphore.release()
                }
            }
        }
        jobs.joinAll()
    }

    private fun probeHost8883(sslContext: SSLContext, ip: String): BambuDiscoveredDevice? {
        var sslSocket: SSLSocket? = null
        return try {
            sslSocket = sslContext.socketFactory.createSocket() as SSLSocket
            sslSocket.soTimeout = 1200
            sslSocket.connect(InetSocketAddress(ip, 8883), 420)
            sslSocket.startHandshake()

            val certs = sslSocket.session.peerCertificates
            val certInfo = certs.firstOrNull() as? X509Certificate
            val subject = certInfo?.subjectX500Principal?.name.orEmpty()
            val issuer = certInfo?.issuerX500Principal?.name.orEmpty()

            // 提取证书 CN 中的序列号（拓竹打印机 TLS 证书 CN 通常为设备序列号或包含 Bambu 标识）
            val cn = Regex("""CN=([^,]+)""").find(subject)?.groupValues?.get(1)?.trim().orEmpty()
            val inferredModel = PrinterModel.fromSerial(cn)
            val isBambuCert = inferredModel != PrinterModel.UNKNOWN ||
                    subject.contains("bambu", ignoreCase = true) ||
                    issuer.contains("bambu", ignoreCase = true) ||
                    cn.length in 12..16

            if (!isBambuCert) return null

            val devId = if (cn.isNotBlank() && !cn.contains(" ")) cn else ""
            val modelName = if (inferredModel != PrinterModel.UNKNOWN) {
                inferredModel.displayName
            } else {
                "Bambu Lab 打印机"
            }

            BambuDiscoveredDevice(
                devId = devId,
                devName = "$modelName ($ip)",
                devModel = modelName,
                ip = ip
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { sslSocket?.close() }
        }
    }

    private fun getLocalIpv4Address(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
            null
        }.getOrNull()
    }

    private fun parseSsdpResponse(raw: String, ip: String): BambuDiscoveredDevice? {
        val lines = raw.lines()
        val headers = mutableMapOf<String, String>()

        for (line in lines) {
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim().lowercase()
                val value = line.substring(colonIndex + 1).trim()
                headers[key] = value
            }
        }

        val devId = headers["devid.bambu.com"] ?: headers["devid"] ?: headers["usn"]?.takeIf {
            PrinterModel.fromSerial(it) != PrinterModel.UNKNOWN
        }
        val devName = headers["devname.bambu.com"] ?: headers["devname"] ?: "Bambu 3D Printer"
        val devModel = headers["devmodel.bambu.com"] ?: headers["devmodel"] ?: "Bambu Printer"

        return if (!devId.isNullOrBlank()) {
            BambuDiscoveredDevice(
                devId = devId,
                devName = devName,
                devModel = devModel,
                ip = ip
            )
        } else if (raw.contains("bambulab", ignoreCase = true) || raw.contains("bambu", ignoreCase = true)) {
            val fallbackDevId = headers["usn"]?.substringAfter("uuid:")?.substringBefore("::") ?: ""
            BambuDiscoveredDevice(
                devId = fallbackDevId,
                devName = devName,
                devModel = devModel,
                ip = ip
            )
        } else {
            null
        }
    }

    companion object {
        private const val TAG = "BambuDiscoveryEngine"
    }
}
