/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: BambuCameraStreamClient.kt
 * 职责: 拓竹局域网机内实时视频监控引擎，支持旗舰机型 RTSPS (Port 322, TLS + RTP/AVP/TCP H.264) 与 P1/A1 系列 TLS 6000 帧流直连
 * 层级: Network Communication Layer (dev.local.bambu.network)
 */

package dev.local.bambu.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.util.Base64
import android.view.Surface
import dev.local.bambu.model.BambuDevice
import dev.local.bambu.model.CameraProtocol
import dev.local.bambu.model.CameraStreamState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class BambuCameraStreamClient(
    private val device: BambuDevice
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var streamJob: Job? = null

    private var activeSurface: Surface? = null
    private var activeCodec: MediaCodec? = null

    @Volatile
    private var needsCodecReset = false

    fun setSurface(surface: Surface?) {
        activeSurface = surface
        val codec = activeCodec
        if (codec != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (surface != null && surface.isValid) {
                val ok = runCatching {
                    codec.setOutputSurface(surface)
                    true
                }.getOrDefault(false)

                if (ok) {
                    android.util.Log.i("BambuCamera", "成功动态切换解码输出 Surface: $surface")
                } else {
                    android.util.Log.w("BambuCamera", "动态切换 Surface 失败，标记解码器热重构")
                    needsCodecReset = true
                }
            }
        }
    }

    private val _streamState = MutableStateFlow(CameraStreamState.IDLE)
    val streamState: StateFlow<CameraStreamState> = _streamState.asStateFlow()

    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame.asStateFlow()

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _bitrateKbps = MutableStateFlow(0)
    val bitrateKbps: StateFlow<Int> = _bitrateKbps.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun start(reportedRtspUrl: String = "") {
        stop()
        streamJob = scope.launch {
            _streamState.value = CameraStreamState.CONNECTING
            _lastError.value = null
            try {
                when (device.printerModel.cameraProtocol) {
                    CameraProtocol.RTSPS_322 -> runRtsps322Stream(reportedRtspUrl)
                    CameraProtocol.TLS_6000 -> runTls6000JpegStream()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("BambuCamera", "视频流发生异常中断: ${e.javaClass.simpleName} - ${e.message}", e)
                _streamState.value = CameraStreamState.ERROR
                _lastError.value = e.message ?: "局域网视频流连接异常"
            }
        }
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        _streamState.value = CameraStreamState.IDLE
        _fps.value = 0
        _bitrateKbps.value = 0
    }

    /**
     * 协议一：X2D / X1 / H2 / P2S 系列局域网 RTSPS 322 端口 (TLS 1.2 + RTSP Interleaved H.264)
     * 端点格式: rtsps://bblp:<AccessCode>@<IP>:322/streaming/live/1
     */
    private suspend fun CoroutineScope.runRtsps322Stream(reportedRtspUrl: String) {
        val sslSocket = createTrustAllSslSocket(device.ip, 322)
        var codec: MediaCodec? = null
        var keepAliveJob: Job? = null
        try {
            val input = DataInputStream(sslSocket.inputStream)
            val output = sslSocket.outputStream
            val baseUri = if (reportedRtspUrl.startsWith("rtsps://", ignoreCase = true)) {
                reportedRtspUrl
            } else {
                "rtsps://${device.ip}:322/streaming/live/1"
            }

            var cseq = 1
            var savedRealm: String? = null
            var savedNonce: String? = null

            fun extractAuth(wwwAuth: String?) {
                if (wwwAuth == null) return
                Regex("""realm="([^"]+)"""").find(wwwAuth)?.groupValues?.get(1)?.let { savedRealm = it }
                Regex("""nonce="([^"]+)"""").find(wwwAuth)?.groupValues?.get(1)?.let { savedNonce = it }
            }

            fun makeAuth(method: String, uri: String): String {
                val r = savedRealm ?: return ""
                val n = savedNonce ?: return ""
                val ha1 = md5Hex("bblp:$r:${device.accessCode}")
                val ha2 = md5Hex("$method:$uri")
                val resp = md5Hex("$ha1:$n:$ha2")
                return """Authorization: Digest username="bblp", realm="$r", nonce="$n", uri="$uri", response="$resp"""" + "\r\n"
            }

            // 1. DESCRIBE (初次请求探测 401 质询以提取 Digest realm 与 nonce)
            android.util.Log.i("BambuCamera", "RTSPS 1. 发送 DESCRIBE: $baseUri")
            sendRtspRequest(
                output,
                "DESCRIBE $baseUri RTSP/1.0\r\nCSeq: ${cseq++}\r\nAccept: application/sdp\r\n\r\n"
            )
            var descResp = readRtspResponse(input)
            if (descResp.statusCode == 401) {
                extractAuth(descResp.wwwAuthenticate)
                android.util.Log.i("BambuCamera", "RTSPS 收到 401 质询, realm=$savedRealm, nonce=$savedNonce")
                val descAuth = makeAuth("DESCRIBE", baseUri)
                sendRtspRequest(
                    output,
                    "DESCRIBE $baseUri RTSP/1.0\r\nCSeq: ${cseq++}\r\nAccept: application/sdp\r\n$descAuth\r\n"
                )
                descResp = readRtspResponse(input)
            }
            if (descResp.statusCode != 200) {
                throw IllegalStateException("RTSPS DESCRIBE 失败, 状态码: ${descResp.statusCode}")
            }
            android.util.Log.i("BambuCamera", "RTSPS 2. DESCRIBE 成功 (200 OK)")

            val sdp = descResp.body
            val trackUrl = parseTrackControlUrl(baseUri, sdp)
            val spsPps = parseSpsPpsFromSdp(sdp)

            // 2. SETUP (使用独立的 SETUP Digest 认证头，启用 TCP Interleaved 通道 0-1)
            val setupAuth = makeAuth("SETUP", trackUrl)
            android.util.Log.i("BambuCamera", "RTSPS 3. 发送 SETUP: $trackUrl")
            sendRtspRequest(
                output,
                "SETUP $trackUrl RTSP/1.0\r\nCSeq: ${cseq++}\r\nTransport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n$setupAuth\r\n"
            )
            val setupResp = readRtspResponse(input)
            if (setupResp.statusCode != 200 && setupResp.statusCode != 0) {
                throw IllegalStateException("RTSPS SETUP 失败, 状态码: ${setupResp.statusCode}")
            }
            val session = setupResp.sessionId ?: "0"
            android.util.Log.i("BambuCamera", "RTSPS 4. SETUP 成功, session=$session")

            // 3. PLAY (使用独立的 PLAY Digest 认证头)
            val playAuth = makeAuth("PLAY", baseUri)
            android.util.Log.i("BambuCamera", "RTSPS 5. 发送 PLAY: $baseUri")
            sendRtspRequest(
                output,
                "PLAY $baseUri RTSP/1.0\r\nCSeq: ${cseq++}\r\nSession: $session\r\nRange: npt=0.000-\r\n$playAuth\r\n"
            )

            // 5. 初始化 Android MediaCodec 硬件 H.264 解码器 (支持 Surface 直通硬件渲染与软解回退)
            val currentSurface = activeSurface
            android.util.Log.i("BambuCamera", "初始化 MediaCodec: currentSurface=$currentSurface, activeSurface=$activeSurface")
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080).apply {
                if (currentSurface == null) {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                }
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024) // 扩展到 1MB 以稳妥容纳 1080P 大关键帧
                if (spsPps.isNotEmpty()) {
                    setByteBuffer("csd-0", ByteBuffer.wrap(spsPps[0]))
                    if (spsPps.size > 1) {
                        setByteBuffer("csd-1", ByteBuffer.wrap(spsPps[1]))
                    }
                }
            }
            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, currentSurface, null, 0)
                start()
            }
            activeCodec = codec

            _streamState.value = CameraStreamState.STREAMING
            val fuBuffer = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var frameCount = 0
            var bytesCount = 0L
            var statsTimestamp = System.currentTimeMillis()
            var lastRenderTime = 0L
            val minRenderInterval = 65L // 限制最高渲染 15 FPS

            var cachedSps: ByteArray? = null
            var cachedPps: ByteArray? = null

            // 启动原生纯硬件 SurfaceView 流水线与 RTSP 周期保活心跳 (每隔 3 秒发送 OPTIONS，确保持久推流不中断)
            keepAliveJob = launch {
                while (isActive) {
                    delay(3000)
                    runCatching {
                        val ping = "OPTIONS $baseUri RTSP/1.0\r\nCSeq: ${cseq++}\r\nSession: $session\r\n\r\n"
                        output.write(ping.toByteArray(Charsets.UTF_8))
                        output.flush()
                        android.util.Log.d("BambuCamera", "发送 OPTIONS 保活心跳")
                    }.onFailure {
                        android.util.Log.w("BambuCamera", "发送保活心跳失败", it)
                    }
                }
            }

            fun drainOutput(isThrottle: Boolean, waitUs: Long = 5_000) {
                val c = codec ?: return
                runCatching {
                    val currentSurface = activeSurface
                    val isSurfaceValid = currentSurface != null && currentSurface.isValid
                    var outIdx = c.dequeueOutputBuffer(bufferInfo, waitUs)
                    while (outIdx >= 0) {
                        val now = System.currentTimeMillis()
                        if (isSurfaceValid) {
                            // 硬件 Surface 直通模式：GPU 硬件实时渲染
                            runCatching { c.releaseOutputBuffer(outIdx, true) }
                            frameCount++
                            lastRenderTime = now
                        } else {
                            runCatching { c.releaseOutputBuffer(outIdx, false) }
                        }
                        outIdx = c.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }.onFailure {
                    android.util.Log.w("BambuCamera", "drainOutput 捕获到非致命异常: ${it.message}")
                    if (it is IllegalStateException) {
                        needsCodecReset = true
                    }
                }
            }

            while (isActive) {
                // 如果 Surface 重建或者切换导致解码器损坏，触发即时热重构
                if (needsCodecReset) {
                    val targetSurface = activeSurface
                    if (targetSurface != null && targetSurface.isValid) {
                        needsCodecReset = false
                        runCatching { codec?.stop(); codec?.release() }
                        val newCodec = runCatching {
                            MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                                configure(format, targetSurface, null, 0)
                                start()
                            }
                        }.getOrNull()

                        if (newCodec != null) {
                            codec = newCodec
                            activeCodec = newCodec
                            cachedSps?.let { sps ->
                                val idx = runCatching { newCodec.dequeueInputBuffer(10_000) }.getOrDefault(-1)
                                if (idx >= 0) {
                                    newCodec.getInputBuffer(idx)?.apply { clear(); put(sps) }
                                    newCodec.queueInputBuffer(idx, 0, sps.size, 0L, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                                }
                            }
                            cachedPps?.let { pps ->
                                val idx = runCatching { newCodec.dequeueInputBuffer(10_000) }.getOrDefault(-1)
                                if (idx >= 0) {
                                    newCodec.getInputBuffer(idx)?.apply { clear(); put(pps) }
                                    newCodec.queueInputBuffer(idx, 0, pps.size, 0L, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                                }
                            }
                            android.util.Log.i("BambuCamera", "解码器热重构完成并重放 SPS/PPS")
                        }
                    }
                }

                val readStart = System.currentTimeMillis()
                val magic = input.read()
                val readElapsed = System.currentTimeMillis() - readStart
                if (readElapsed > 1500) {
                    android.util.Log.w("BambuCamera", "Socket input.read() 阻塞耗时 ${readElapsed}ms, 读到: $magic")
                }
                if (magic == -1) {
                    android.util.Log.w("BambuCamera", "Socket EOF (-1)")
                    break
                }
                if (magic != 0x24) {
                    if (magic == 'R'.code) {
                        consumeRtspResponse(magic, input)
                        continue
                    } else {
                        var nextByte = magic
                        while (nextByte != -1 && nextByte != 0x24 && isActive) {
                            nextByte = input.read()
                        }
                        if (nextByte == -1) break
                    }
                }

                val channel = input.readUnsignedByte()
                val packetLen = input.readUnsignedShort()
                val rtpPacket = ByteArray(packetLen)
                input.readFully(rtpPacket)
                bytesCount += packetLen

                if (channel == 0 && packetLen > 12) {
                    val nalUnits = depacketizeH264Rtp(rtpPacket, fuBuffer)
                    val activeC = codec
                    if (activeC != null) {
                        for (nal in nalUnits) {
                            var inIdx = runCatching { activeC.dequeueInputBuffer(8_000) }.getOrDefault(-1)
                            var waitCount = 0
                            while (inIdx < 0 && isActive) {
                                drainOutput(isThrottle = false, waitUs = 10_000)
                                inIdx = runCatching { activeC.dequeueInputBuffer(8_000) }.getOrDefault(-1)
                                waitCount++
                                if (waitCount > 100) break
                            }
                            if (inIdx >= 0) {
                                val inBuf = activeC.getInputBuffer(inIdx)
                                inBuf?.clear()
                                inBuf?.put(nal)
                                val nalType = (nal.getOrNull(4)?.toInt() ?: 0) and 0x1F
                                if (nalType == 7) cachedSps = nal
                                if (nalType == 8) cachedPps = nal
                                val flags = when (nalType) {
                                    7, 8 -> MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                                    5 -> MediaCodec.BUFFER_FLAG_KEY_FRAME
                                    else -> 0
                                }
                                val pts = if (flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) 0L else System.nanoTime() / 1000
                                runCatching {
                                    activeC.queueInputBuffer(inIdx, 0, nal.size, pts, flags)
                                }.onFailure {
                                    android.util.Log.e("BambuCamera", "queueInputBuffer failed (type=$nalType, flags=$flags)", it)
                                }
                            }
                            drainOutput(isThrottle = true, waitUs = 0)
                        }
                    }
                } else if (channel != 0) {
                    android.util.Log.d("BambuCamera", "收到非视频通道数据包: channel=$channel, len=$packetLen")
                }

                val now = System.currentTimeMillis()
                if (now - statsTimestamp >= 1000L) {
                    _fps.value = frameCount
                    _bitrateKbps.value = ((bytesCount * 8) / 1000).toInt()
                    android.util.Log.i("BambuCamera", "Stream stats: fps=$frameCount, kbps=${_bitrateKbps.value}, bytes=$bytesCount")
                    frameCount = 0
                    bytesCount = 0L
                    statsTimestamp = now
                }
            }
        } finally {
            keepAliveJob?.cancel()
            activeCodec = null
            runCatching { codec?.stop(); codec?.release() }
            runCatching { sslSocket.close() }
        }
    }

    /**
     * 协议二：P1S / P1P / A1 / A1 mini 局域网 TLS 6000 端口二进制鉴权 JPEG 实时流
     */
    private suspend fun CoroutineScope.runTls6000JpegStream() {
        val sslSocket = createTrustAllSslSocket(device.ip, 6000)
        try {
            val output = sslSocket.outputStream
            val input = DataInputStream(sslSocket.inputStream)

            // 构造拓竹 80 字节二进制鉴权头
            val authPacket = ByteBuffer.allocate(80).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(0x40)
                putInt(0x3000)
                putInt(0)
                putInt(0)
                val userBytes = "bblp".toByteArray(Charsets.US_ASCII)
                put(userBytes)
                position(16 + 32)
                val codeBytes = device.accessCode.take(32).toByteArray(Charsets.US_ASCII)
                put(codeBytes)
            }.array()

            output.write(authPacket)
            output.flush()

            _streamState.value = CameraStreamState.STREAMING
            val headerBuf = ByteArray(16)
            var frameCount = 0
            var bytesCount = 0L
            var statsTimestamp = System.currentTimeMillis()

            while (isActive) {
                input.readFully(headerBuf)
                val payloadSize = ByteBuffer.wrap(headerBuf, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (payloadSize <= 0 || payloadSize > 5_000_000) continue

                val jpegBytes = ByteArray(payloadSize)
                input.readFully(jpegBytes)
                bytesCount += payloadSize

                val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                if (bmp != null) {
                    _currentFrame.value = bmp
                    frameCount++
                }

                val now = System.currentTimeMillis()
                if (now - statsTimestamp >= 1000L) {
                    _fps.value = frameCount
                    _bitrateKbps.value = ((bytesCount * 8) / 1000).toInt()
                    android.util.Log.i("BambuCamera", "Stream stats: fps=$frameCount, kbps=${_bitrateKbps.value}, bytes=$bytesCount")
                    frameCount = 0
                    bytesCount = 0L
                    statsTimestamp = now
                }
            }
        } finally {
            runCatching { sslSocket.close() }
        }
    }

    private fun createTrustAllSslSocket(host: String, port: Int): SSLSocket {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        val socket = sslContext.socketFactory.createSocket() as SSLSocket
        socket.soTimeout = 10_000
        socket.connect(InetSocketAddress(host, port), 6_000)
        socket.startHandshake()
        return socket
    }

    private fun sendRtspRequest(output: OutputStream, request: String) {
        output.write(request.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    private data class RtspResponse(
        val statusCode: Int,
        val sessionId: String?,
        val wwwAuthenticate: String?,
        val body: String
    )

    private fun consumeRtspResponse(firstByte: Int, input: DataInputStream) {
        runCatching {
            var contentLen = 0
            val statusLine = readAsciiLineWithFirstByte(firstByte, input).orEmpty()
            while (true) {
                val line = readAsciiLine(input) ?: break
                if (line.isEmpty()) break
                if (line.lowercase().startsWith("content-length:")) {
                    contentLen = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }
            if (contentLen > 0) {
                val buf = ByteArray(contentLen)
                input.readFully(buf)
            }
            android.util.Log.d("BambuCamera", "消费并对齐 RTSP 异步响应: $statusLine (bodyLen=$contentLen)")
        }.onFailure {
            android.util.Log.w("BambuCamera", "consumeRtspResponse 解析异常", it)
        }
    }

    private fun readAsciiLineWithFirstByte(firstByte: Int, input: DataInputStream): String? {
        val sb = StringBuilder()
        sb.append(firstByte.toChar())
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\r'.code) {
                val next = input.read()
                if (next == '\n'.code) break
            } else if (b == '\n'.code) {
                break
            } else {
                sb.append(b.toChar())
            }
        }
        return sb.toString()
    }

    private fun readRtspResponse(input: DataInputStream): RtspResponse {
        val lines = mutableListOf<String>()
        while (true) {
            val line = readAsciiLine(input) ?: break
            if (line.isEmpty()) break
            lines.add(line)
        }
        val statusLine = lines.firstOrNull().orEmpty()
        val statusCode = statusLine.split(" ").getOrNull(1)?.toIntOrNull() ?: 500
        var contentLen = 0
        var sessionId: String? = null
        var wwwAuth: String? = null

        for (l in lines.drop(1)) {
            val lower = l.lowercase()
            when {
                lower.startsWith("content-length:") -> {
                    contentLen = l.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                lower.startsWith("session:") -> {
                    sessionId = l.substringAfter(":").substringBefore(";").trim()
                }
                lower.startsWith("www-authenticate:") -> {
                    wwwAuth = l.substringAfter(":").trim()
                }
            }
        }

        val body = if (contentLen > 0) {
            val buf = ByteArray(contentLen)
            input.readFully(buf)
            String(buf, Charsets.UTF_8)
        } else ""

        return RtspResponse(statusCode, sessionId, wwwAuth, body)
    }

    private fun readAsciiLine(input: DataInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\r'.code) {
                val next = input.read()
                if (next == '\n'.code) break
            } else if (b == '\n'.code) {
                break
            } else {
                sb.append(b.toChar())
            }
        }
        return sb.toString()
    }

    private fun buildDigestAuthHeader(method: String, uri: String, wwwAuth: String): String {
        val realm = Regex("""realm="([^"]+)"""").find(wwwAuth)?.groupValues?.get(1).orEmpty()
        val nonce = Regex("""nonce="([^"]+)"""").find(wwwAuth)?.groupValues?.get(1).orEmpty()
        val ha1 = md5Hex("bblp:$realm:${device.accessCode}")
        val ha2 = md5Hex("$method:$uri")
        val response = md5Hex("$ha1:$nonce:$ha2")
        return """Authorization: Digest username="bblp", realm="$realm", nonce="$nonce", uri="$uri", response="$response"""" + "\r\n"
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun parseTrackControlUrl(baseUri: String, sdp: String): String {
        var inVideo = false
        for (line in sdp.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("m=video")) inVideo = true
            else if (trimmed.startsWith("m=")) inVideo = false
            if (inVideo && trimmed.startsWith("a=control:")) {
                val ctrl = trimmed.removePrefix("a=control:").trim()
                return if (ctrl.startsWith("rtsp", ignoreCase = true)) ctrl
                else "${baseUri.trimEnd('/')}/${ctrl.trimStart('/')}"
            }
        }
        return "$baseUri/trackID=0"
    }

    private fun parseSpsPpsFromSdp(sdp: String): List<ByteArray> {
        val match = Regex("""sprop-parameter-sets=([A-Za-z0-9+/=,]+)""").find(sdp) ?: return emptyList()
        val parts = match.groupValues[1].split(",")
        return parts.mapNotNull { part ->
            runCatching {
                val raw = Base64.decode(part, Base64.DEFAULT)
                byteArrayOf(0, 0, 0, 1) + raw
            }.getOrNull()
        }
    }

    /**
     * RFC 6184 H.264 RTP 解包 (支持 Single NAL Unit、STAP-A 与 FU-A 分片重组)
     */
    private fun depacketizeH264Rtp(rtp: ByteArray, fuBuffer: ByteArrayOutputStream): List<ByteArray> {
        val csrcCount = rtp[0].toInt() and 0x0F
        val hasExtension = (rtp[0].toInt() and 0x10) != 0
        var offset = 12 + csrcCount * 4
        if (hasExtension && rtp.size >= offset + 4) {
            val extLen = ((rtp[offset + 2].toInt() and 0xFF) shl 8) or (rtp[offset + 3].toInt() and 0xFF)
            offset += 4 + extLen * 4
        }
        if (offset >= rtp.size) return emptyList()

        val nalHeader = rtp[offset].toInt() and 0xFF
        val nalType = nalHeader and 0x1F
        val startCode = byteArrayOf(0, 0, 0, 1)

        return when (nalType) {
            in 1..23 -> {
                listOf(startCode + rtp.copyOfRange(offset, rtp.size))
            }
            24 -> { // STAP-A
                val out = mutableListOf<ByteArray>()
                var ptr = offset + 1
                while (ptr + 2 <= rtp.size) {
                    val size = ((rtp[ptr].toInt() and 0xFF) shl 8) or (rtp[ptr + 1].toInt() and 0xFF)
                    ptr += 2
                    if (ptr + size <= rtp.size) {
                        out.add(startCode + rtp.copyOfRange(ptr, ptr + size))
                    }
                    ptr += size
                }
                out
            }
            28 -> { // FU-A
                if (offset + 2 > rtp.size) return emptyList()
                val fuHeader = rtp[offset + 1].toInt() and 0xFF
                val startBit = (fuHeader and 0x80) != 0
                val endBit = (fuHeader and 0x40) != 0
                val reconstructedHeader = (nalHeader and 0xE0) or (fuHeader and 0x1F)

                if (startBit) {
                    fuBuffer.reset()
                    fuBuffer.write(startCode)
                    fuBuffer.write(reconstructedHeader)
                    fuBuffer.write(rtp, offset + 2, rtp.size - (offset + 2))
                } else if (fuBuffer.size() > 0) {
                    fuBuffer.write(rtp, offset + 2, rtp.size - (offset + 2))
                    if (endBit) {
                        val complete = fuBuffer.toByteArray()
                        fuBuffer.reset()
                        return listOf(complete)
                    }
                }
                emptyList()
            }
            else -> emptyList()
        }
    }

    private fun yuv420ToBitmap(width: Int, height: Int, image: android.media.Image): Bitmap? {
        return runCatching {
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val yRowStride = yPlane.rowStride
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            val nv21 = ByteArray(width * height * 3 / 2)
            var pos = 0

            // 1. 抽取 Y 亮度平面 (去除 rowStride 填充字节)
            val yLine = ByteArray(width)
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(yLine, 0, width)
                System.arraycopy(yLine, 0, nv21, pos, width)
                pos += width
            }

            // 2. 抽取 VU 交错平面 (严格保证 V 在前、U 在后，彻底消除 UV 错位导致的绿紫偏色与画面黑白)
            val uvHeight = height / 2
            val uvWidth = width / 2
            for (row in 0 until uvHeight) {
                val rowOffsetV = row * uvRowStride
                val rowOffsetU = row * uvRowStride
                for (col in 0 until uvWidth) {
                    nv21[pos++] = vBuffer.get(rowOffsetV + col * uvPixelStride)
                    nv21[pos++] = uBuffer.get(rowOffsetU + col * uvPixelStride)
                }
            }

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream(nv21.size / 4)
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 70, out)
            val bytes = out.toByteArray()
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 2
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }.getOrNull()
    }
}
