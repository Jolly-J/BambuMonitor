/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: DeviceRepository.kt
 * 职责: 拓竹打印机本地已配对配置与选中项的 SharedPreferences 持久化仓储
 * 层级: Data Persistence Layer (dev.local.bambu.data)
 */

package dev.local.bambu.data

import android.content.Context
import dev.local.bambu.model.BambuDevice
import org.json.JSONArray
import org.json.JSONObject

class DeviceRepository(context: Context) {
    private val prefs = context.getSharedPreferences("bambu_devices", Context.MODE_PRIVATE)

    fun getSavedDevices(): List<BambuDevice> {
        val json = prefs.getString("device_list", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<BambuDevice>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    BambuDevice(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        ip = obj.getString("ip"),
                        accessCode = obj.getString("accessCode"),
                        model = obj.optString("model", "Bambu Printer")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveDevice(device: BambuDevice) {
        val current = getSavedDevices().toMutableList()
        val index = current.indexOfFirst { it.id == device.id }
        if (index >= 0) {
            current[index] = device
        } else {
            current.add(device)
        }
        persistDevices(current)
        if (getSelectedDeviceId() == null) {
            setSelectedDeviceId(device.id)
        }
    }

    fun deleteDevice(deviceId: String) {
        val current = getSavedDevices().toMutableList()
        current.removeAll { it.id == deviceId }
        persistDevices(current)
        if (getSelectedDeviceId() == deviceId) {
            setSelectedDeviceId(current.firstOrNull()?.id ?: "")
        }
    }

    fun getSelectedDeviceId(): String? {
        val id = prefs.getString("selected_device_id", null)
        return if (id.isNullOrBlank()) null else id
    }

    fun setSelectedDeviceId(deviceId: String) {
        prefs.edit().putString("selected_device_id", deviceId).apply()
    }

    private fun persistDevices(list: List<BambuDevice>) {
        val array = JSONArray()
        for (dev in list) {
            val obj = JSONObject().apply {
                put("id", dev.id)
                put("name", dev.name)
                put("ip", dev.ip)
                put("accessCode", dev.accessCode)
                put("model", dev.model)
            }
            array.put(obj)
        }
        prefs.edit().putString("device_list", array.toString()).apply()
    }
}
