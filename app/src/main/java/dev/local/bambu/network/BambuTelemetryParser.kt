/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: BambuTelemetryParser.kt
 * 职责: 拓竹 JSON 遥测报文解析器与增量合并引擎 (Delta State Merging)
 * 层级: Network Communication Layer (dev.local.bambu.network)
 */

package dev.local.bambu.network

import dev.local.bambu.model.*
import org.json.JSONArray
import org.json.JSONObject

object BambuTelemetryParser {

    /**
     * 将接收到的最新增量 JSON 报文合并到当前状态中，保留未变动的字段
     */
    fun merge(current: BambuPrinterStatus, jsonString: String): BambuPrinterStatus {
        return try {
            val root = JSONObject(jsonString)
            val printObj = root.optJSONObject("print") ?: root

            var updated = current

            if (printObj.has("gcode_state")) {
                val rawState = printObj.getString("gcode_state").uppercase()
                val printState = when (rawState) {
                    "IDLE" -> PrintState.IDLE
                    "PREPARE" -> PrintState.PREPARING
                    "RUNNING" -> PrintState.RUNNING
                    "PAUSE" -> PrintState.PAUSED
                    "FINISH" -> PrintState.FINISHED
                    "FAILED" -> PrintState.FAILED
                    else -> current.printState
                }
                updated = updated.copy(gcodeStateRaw = rawState, printState = printState)
            }

            if (printObj.has("mc_percent")) {
                updated = updated.copy(progressPercent = printObj.getInt("mc_percent").coerceIn(0, 100))
            }

            if (printObj.has("mc_remaining_time")) {
                updated = updated.copy(remainingMinutes = printObj.getInt("mc_remaining_time").coerceAtLeast(0))
            }

            if (printObj.has("layer_num")) {
                updated = updated.copy(currentLayer = printObj.getInt("layer_num"))
            }

            if (printObj.has("total_layer_num")) {
                updated = updated.copy(totalLayers = printObj.getInt("total_layer_num"))
            }

            if (printObj.has("subtask_name")) {
                val name = printObj.getString("subtask_name")
                if (name.isNotBlank()) {
                    updated = updated.copy(taskName = name)
                }
            }

            // 双喷头与单喷头兼容解析 (X2D 旗舰双喷头与标准系列)
            val deviceObj = printObj.optJSONObject("device")
            val extruderArray = deviceObj?.optJSONObject("extruder")?.optJSONArray("info")
            if (extruderArray != null && extruderArray.length() >= 2) {
                val left = extruderArray.optJSONObject(0)
                val right = extruderArray.optJSONObject(1)
                val rawLeft = left?.optDouble("temp", updated.nozzleTemp.toDouble()) ?: updated.nozzleTemp.toDouble()
                val rawRight = right?.optDouble("temp", updated.nozzleTempRight.toDouble()) ?: updated.nozzleTempRight.toDouble()
                updated = updated.copy(
                    nozzleTemp = parseSingleOrPackedTemp(rawLeft, isLeft = true),
                    nozzleTempRight = parseSingleOrPackedTemp(rawRight, isLeft = false),
                    hasDualNozzle = true
                )
            } else {
                if (printObj.has("nozzle_temper")) {
                    val rawVal = printObj.getDouble("nozzle_temper")
                    if (rawVal > 500.0) {
                        updated = updated.copy(
                            nozzleTemp = parseSingleOrPackedTemp(rawVal, isLeft = true),
                            nozzleTempRight = parseSingleOrPackedTemp(rawVal, isLeft = false),
                            hasDualNozzle = true
                        )
                    } else {
                        updated = updated.copy(nozzleTemp = rawVal.toFloat())
                    }
                }
            }

            if (printObj.has("nozzle_target_temper")) {
                val rawTarget = printObj.getDouble("nozzle_target_temper")
                updated = updated.copy(nozzleTargetTemp = parseSingleOrPackedTemp(rawTarget, isLeft = true))
            }

            if (printObj.has("bed_temper")) {
                updated = updated.copy(bedTemp = printObj.getDouble("bed_temper").toFloat())
            }
            if (printObj.has("bed_target_temper")) {
                updated = updated.copy(bedTargetTemp = printObj.getDouble("bed_target_temper").toFloat())
            }

            // 机箱腔体温度解析 (优先支持 X2D 的 device.ctc.info.temp 与 info.temp，兼顾标准 chamber_temper)
            val ctcTemp = deviceObj?.optJSONObject("ctc")?.optJSONObject("info")?.optDouble("temp", -1.0)
                ?: printObj.optJSONObject("info")?.optDouble("temp", -1.0)
            if (ctcTemp != null && ctcTemp > 0) {
                updated = updated.copy(chamberTemp = ctcTemp.toFloat())
            } else if (printObj.has("chamber_temper")) {
                val temp = printObj.getDouble("chamber_temper").toFloat()
                if (temp > 0) {
                    updated = updated.copy(chamberTemp = temp)
                }
            }

            if (printObj.has("cooling_fan_speed")) {
                val rawFan = printObj.optString("cooling_fan_speed")
                val fanInt = rawFan.toIntOrNull() ?: printObj.optInt("cooling_fan_speed", 0)
                val fanPercent = if (fanInt in 1..15) (fanInt * 100) / 15 else fanInt.coerceIn(0, 100)
                updated = updated.copy(coolingFanSpeed = fanPercent)
            }

            if (printObj.has("spd_lvl")) {
                val lvlCode = printObj.getInt("spd_lvl")
                updated = updated.copy(speedLevel = SpeedLevel.fromCode(lvlCode))
            }

            if (printObj.has("lights_report")) {
                val lightsArray = printObj.optJSONArray("lights_report")
                if (lightsArray != null) {
                    var chamberLight = updated.chamberLightOn
                    for (i in 0 until lightsArray.length()) {
                        val light = lightsArray.optJSONObject(i) ?: continue
                        if (light.optString("node") == "chamber_light") {
                            chamberLight = light.optString("mode").equals("on", ignoreCase = true)
                        }
                    }
                    updated = updated.copy(chamberLightOn = chamberLight)
                }
            }

            if (printObj.has("ams")) {
                val amsObj = printObj.optJSONObject("ams")
                if (amsObj != null) {
                    val trayNow = amsObj.optString("tray_now", "-1").toIntOrNull() ?: -1
                    val amsArray = amsObj.optJSONArray("ams")
                    if (amsArray != null) {
                        val units = parseAmsUnits(amsArray, trayNow)
                        if (units.isNotEmpty()) {
                            updated = updated.copy(amsUnits = units)
                        }
                    }
                }
            }

            if (printObj.has("wifi_signal")) {
                updated = updated.copy(wifiSignal = printObj.optString("wifi_signal"))
            }

            if (printObj.has("ipcam")) {
                val ipcamObj = printObj.optJSONObject("ipcam")
                if (ipcamObj != null) {
                    val rtsp = ipcamObj.optString("rtsp_url", updated.rtspUrl)
                    val res = ipcamObj.optString("resolution", updated.ipcamResolution)
                    val rec = ipcamObj.optString("ipcam_record", if (updated.ipcamRecord) "enable" else "disable")
                    val tl = ipcamObj.optString("timelapse", if (updated.timelapseEnabled) "enable" else "disable")
                    updated = updated.copy(
                        rtspUrl = rtsp,
                        ipcamResolution = res.ifBlank { "1080p" },
                        ipcamRecord = rec.equals("enable", ignoreCase = true),
                        timelapseEnabled = tl.equals("enable", ignoreCase = true)
                    )
                }
            }

            if (printObj.has("hms")) {
                val hmsArray = printObj.optJSONArray("hms")
                if (hmsArray != null) {
                    val errorList = mutableListOf<String>()
                    for (i in 0 until hmsArray.length()) {
                        val item = hmsArray.optJSONObject(i) ?: continue
                        val code = item.optString("code")
                        if (code.isNotBlank()) errorList.add(code)
                    }
                    updated = updated.copy(hmsErrors = errorList)
                }
            }

            updated
        } catch (_: Exception) {
            current
        }
    }

    private fun parseAmsUnits(amsArray: JSONArray, trayNow: Int): List<AmsUnit> {
        val result = mutableListOf<AmsUnit>()
        for (i in 0 until amsArray.length()) {
            val unitObj = amsArray.optJSONObject(i) ?: continue
            val unitId = unitObj.optInt("id", i)
            val temp = unitObj.optDouble("temp", 0.0).toFloat()
            val humidity = unitObj.optInt("humidity", 0)
            val humidityRaw = unitObj.optString("humidity_raw", "").ifBlank {
                if (humidity > 5) "$humidity" else ""
            }

            val traysList = mutableListOf<AmsTray>()
            val trayArray = unitObj.optJSONArray("tray")
            if (trayArray != null) {
                for (j in 0 until trayArray.length()) {
                    val trayObj = trayArray.optJSONObject(j) ?: continue
                    val trayId = trayObj.optInt("id", j)
                    val rawColor = trayObj.optString("tray_color", "FFFFFF")
                    val colorHex = formatColorHex(rawColor)
                    val material = trayObj.optString("tray_sub_brands", "").ifBlank {
                        trayObj.optString("tray_type", "线材")
                    }
                    val remain = trayObj.optInt("remain", -1)
                    val isActive = (unitId * 4 + trayId) == trayNow

                    traysList.add(
                        AmsTray(
                            id = trayId,
                            colorHex = colorHex,
                            material = material,
                            remainPercent = remain,
                            isActive = isActive
                        )
                    )
                }
            }
            result.add(AmsUnit(id = unitId, temp = temp, humidity = humidity, humidityRaw = humidityRaw, trays = traysList))
        }
        return result
    }

    private fun formatColorHex(raw: String): String {
        val clean = raw.trim().replace("#", "")
        return when (clean.length) {
            8 -> "#" + clean.substring(0, 6)
            6 -> "#$clean"
            else -> "#00AE42"
        }
    }

    private fun parseSingleOrPackedTemp(raw: Double, isLeft: Boolean): Float {
        if (raw > 500.0) {
            val longVal = raw.toLong()
            val high = ((longVal shr 16) and 0xFFFF).toFloat()
            val low = (longVal and 0xFFFF).toFloat()
            return if (isLeft) high else low
        }
        return raw.toFloat()
    }
}
