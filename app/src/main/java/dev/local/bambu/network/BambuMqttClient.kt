/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: BambuMqttClient.kt
 * 职责: 拓竹打印机 MQTTS (TLS Port 8883) 局域网直连客户端，自签名证书适配与控制指令分发
 * 层级: Network Communication Layer (dev.local.bambu.network)
 */

package dev.local.bambu.network

import android.util.Log
import dev.local.bambu.model.BambuDevice
import dev.local.bambu.model.ConnectionState
import dev.local.bambu.model.SpeedLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class BambuMqttClient(
    private val device: BambuDevice,
    private val onMessageReceived: (String) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var client: MqttAsyncClient? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val reportTopic = "device/${device.id}/report"
    private val requestTopic = "device/${device.id}/request"

    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED
        ) return

        _connectionState.value = ConnectionState.CONNECTING
        _lastError.value = null

        scope.launch {
            try {
                val serverUri = "ssl://${device.ip}:8883"
                val clientId = "bambu_monitor_" + UUID.randomUUID().toString().take(8)

                val sslContext = createTrustAllSslContext()
                val connectOptions = MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 10
                    keepAliveInterval = 30
                    isAutomaticReconnect = true
                    userName = "bblp"
                    password = device.accessCode.toCharArray()
                    socketFactory = sslContext.socketFactory
                    setSSLHostnameVerifier { _, _ -> true }
                }

                val mqttClient = MqttAsyncClient(serverUri, clientId, MemoryPersistence())
                client = mqttClient

                mqttClient.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        Log.i(TAG, "MQTT Connected to $serverURI (reconnect=$reconnect)")
                        _connectionState.value = ConnectionState.CONNECTED
                        _lastError.value = null
                        subscribeAndRequestStatus()
                    }

                    override fun connectionLost(cause: Throwable?) {
                        Log.w(TAG, "MQTT Connection lost: ${cause?.message}")
                        _connectionState.value = ConnectionState.CONNECTING
                        _lastError.value = cause?.message
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        if (message != null) {
                            val payload = String(message.payload, Charsets.UTF_8)
                            onMessageReceived(payload)
                        }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                val token = mqttClient.connect(connectOptions)
                token.waitForCompletion(10000)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to ${device.ip}: ${e.message}", e)
                _connectionState.value = ConnectionState.ERROR
                _lastError.value = e.message ?: "连接失败"
            }
        }
    }

    private fun subscribeAndRequestStatus() {
        client?.let { c ->
            try {
                if (c.isConnected) {
                    c.subscribe(reportTopic, 1)
                    requestPushAll()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error subscribing: ${e.message}")
            }
        }
    }

    private var sequenceId = 2000

    fun requestPushAll() {
        val seq = sequenceId++
        val payload = """{"pushing": {"command": "pushall", "version": 1, "sequence_id": "$seq"}}"""
        publish(payload)
    }

    fun setChamberLight(on: Boolean) {
        val mode = if (on) "on" else "off"
        val seq = sequenceId++
        val payload = """{"system": {"sequence_id": "$seq", "command": "ledctrl", "led_node": "chamber_light", "led_mode": "$mode", "led_on_time": 500, "led_off_time": 500, "loop_times": 0, "interval_time": 0}}"""
        publish(payload)
    }

    fun setSpeedLevel(level: SpeedLevel) {
        val seq = sequenceId++
        val payload = """{"print": {"sequence_id": "$seq", "command": "print_speed", "param": "${level.code}"}}"""
        publish(payload)
    }

    fun pausePrint() {
        val seq = sequenceId++
        val payload = """{"print": {"sequence_id": "$seq", "command": "pause"}}"""
        publish(payload)
    }

    fun resumePrint() {
        val seq = sequenceId++
        val payload = """{"print": {"sequence_id": "$seq", "command": "resume"}}"""
        publish(payload)
    }

    fun stopPrint() {
        val seq = sequenceId++
        val payload = """{"print": {"sequence_id": "$seq", "command": "stop"}}"""
        publish(payload)
    }

    private fun publish(jsonPayload: String) {
        scope.launch {
            try {
                val c = client
                if (c != null && c.isConnected) {
                    val message = MqttMessage(jsonPayload.toByteArray(Charsets.UTF_8)).apply {
                        qos = 1
                    }
                    c.publish(requestTopic, message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error publishing payload: ${e.message}")
            }
        }
    }

    fun disconnect() {
        scope.launch {
            try {
                client?.let {
                    if (it.isConnected) it.disconnect()
                    it.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error disconnecting: ${e.message}")
            } finally {
                client = null
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    private fun createTrustAllSslContext(): SSLContext {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        return sslContext
    }

    companion object {
        private const val TAG = "BambuMqttClient"
    }
}
