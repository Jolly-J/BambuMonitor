/**
 * 项目: 拓竹局域网监视器 (Bambu LAN Monitor)
 * 文件: BambuTelemetryParserTest.kt
 * 职责: 拓竹遥测报文解析、增量合并、字段防冲刷保护及全系机型自动识别单元测试套件
 * 层级: Unit Test Layer (dev.local.bambu)
 */

package dev.local.bambu

import dev.local.bambu.model.BambuPrinterStatus
import dev.local.bambu.model.PrintState
import dev.local.bambu.model.PrinterModel
import dev.local.bambu.model.SpeedLevel
import dev.local.bambu.network.BambuTelemetryParser
import org.junit.Assert.*
import org.junit.Test

class BambuTelemetryParserTest {

    @Test
    fun testMergeFullTelemetry() {
        val initial = BambuPrinterStatus()
        val jsonPayload = """
        {
          "print": {
            "gcode_state": "RUNNING",
            "mc_percent": 75,
            "mc_remaining_time": 25,
            "layer_num": 180,
            "total_layer_num": 240,
            "subtask_name": "tool_mount.gcode",
            "nozzle_temper": 219.8,
            "nozzle_target_temper": 220.0,
            "bed_temper": 55.2,
            "bed_target_temper": 55.0,
            "chamber_temper": 36.4,
            "cooling_fan_speed": "12",
            "spd_lvl": 3,
            "wifi_signal": "-45dBm",
            "lights_report": [
              {"node": "chamber_light", "mode": "on"}
            ],
            "ams": {
              "ams": [
                {
                  "id": "0",
                  "temp": "24.5",
                  "humidity": "2",
                  "tray": [
                    {
                      "id": "0",
                      "tray_color": "00AE42FF",
                      "tray_sub_brands": "PLA Basic",
                      "remain": 85
                    },
                    {
                      "id": "1",
                      "tray_color": "FFFFFF00",
                      "tray_sub_brands": "PLA Matte",
                      "remain": 40
                    }
                  ]
                }
              ],
              "tray_now": "0"
            }
          }
        }
        """.trimIndent()

        val result = BambuTelemetryParser.merge(initial, jsonPayload)

        assertEquals(PrintState.RUNNING, result.printState)
        assertEquals(75, result.progressPercent)
        assertEquals(25, result.remainingMinutes)
        assertEquals("25分钟", result.formattedRemainingTime)
        assertEquals(180, result.currentLayer)
        assertEquals(240, result.totalLayers)
        assertEquals("tool_mount.gcode", result.taskName)
        assertEquals(219.8f, result.nozzleTemp, 0.01f)
        assertEquals(220.0f, result.nozzleTargetTemp, 0.01f)
        assertEquals(55.2f, result.bedTemp, 0.01f)
        assertEquals(55.0f, result.bedTargetTemp, 0.01f)
        assertEquals(36.4f, result.chamberTemp, 0.01f)
        assertEquals(80, result.coolingFanSpeed)
        assertEquals(SpeedLevel.SPORT, result.speedLevel)
        assertTrue(result.chamberLightOn)

        assertEquals(1, result.amsUnits.size)
        val unit = result.amsUnits[0]
        assertEquals(2, unit.trays.size)
        assertEquals("#00AE42", unit.trays[0].colorHex)
        assertEquals("PLA Basic", unit.trays[0].material)
        assertTrue(unit.trays[0].isActive)
        assertEquals("#FFFFFF", unit.trays[1].colorHex)
        assertFalse(unit.trays[1].isActive)
    }

    @Test
    fun testIncrementalDeltaMerging() {
        val initial = BambuPrinterStatus(
            printState = PrintState.RUNNING,
            taskName = "dragon.gcode",
            nozzleTemp = 215.0f,
            nozzleTargetTemp = 220.0f,
            progressPercent = 50
        )

        val deltaPayload = """
        {
          "print": {
            "nozzle_temper": 220.1
          }
        }
        """.trimIndent()

        val updated = BambuTelemetryParser.merge(initial, deltaPayload)

        assertEquals("dragon.gcode", updated.taskName)
        assertEquals(50, updated.progressPercent)
        assertEquals(PrintState.RUNNING, updated.printState)
        assertEquals(220.1f, updated.nozzleTemp, 0.01f)
    }

    @Test
    fun testPrinterModelAutoRecognition() {
        // 1. 按官方 SSDP DevModel 代码识别 (含 X2D = N6)
        assertEquals(PrinterModel.X2D, PrinterModel.fromModelCode("N6"))
        assertEquals(PrinterModel.X1_CARBON, PrinterModel.fromModelCode("BL-P001"))
        assertEquals(PrinterModel.P1P, PrinterModel.fromModelCode("C11"))
        assertEquals(PrinterModel.P1S, PrinterModel.fromModelCode("C12"))
        assertEquals(PrinterModel.X1E, PrinterModel.fromModelCode("C13"))
        assertEquals(PrinterModel.A1_MINI, PrinterModel.fromModelCode("N1"))
        assertEquals(PrinterModel.A1, PrinterModel.fromModelCode("N2S"))
        assertEquals(PrinterModel.P2S, PrinterModel.fromModelCode("N7"))
        assertEquals(PrinterModel.H2D, PrinterModel.fromModelCode("O1D"))
        assertEquals(PrinterModel.H2S, PrinterModel.fromModelCode("O1S"))

        // 2. 按序列号前三位识别 (含 X2D = 20P)
        assertEquals(PrinterModel.X2D, PrinterModel.fromSerial("20P00A888800123"))
        assertEquals(PrinterModel.X1_CARBON, PrinterModel.fromSerial("00M09A350100123"))
        assertEquals(PrinterModel.P1S, PrinterModel.fromSerial("01P00A381200456"))
        assertEquals(PrinterModel.P1P, PrinterModel.fromSerial("01S00A290100789"))
        assertEquals(PrinterModel.A1, PrinterModel.fromSerial("03919A410100321"))
        assertEquals(PrinterModel.A1_MINI, PrinterModel.fromSerial("03012A390100654"))
        assertEquals(PrinterModel.H2D, PrinterModel.fromSerial("09400A510100999"))

        // 3. 综合回退识别与 14 款全量支持验证
        assertEquals(14, PrinterModel.supportedModels.size)
        assertEquals(PrinterModel.X2D, PrinterModel.resolve("X2D", "20P00A888800"))
        assertEquals(PrinterModel.X1_CARBON, PrinterModel.resolve("X1-Carbon", "DEMO-X1C-8888"))
        assertEquals(PrinterModel.P1S, PrinterModel.resolve("", "01P00A123456789"))
    }

    @Test
    fun testIpcamRtspTelemetryParsing() {
        val initial = BambuPrinterStatus()
        val payload = """
        {
          "print": {
            "ipcam": {
              "rtsp_url": "rtsps://192.168.1.180:322/streaming/live/1",
              "resolution": "1080p",
              "ipcam_record": "enable",
              "timelapse": "enable"
            }
          }
        }
        """.trimIndent()
        val updated = BambuTelemetryParser.merge(initial, payload)
        assertEquals("rtsps://192.168.1.180:322/streaming/live/1", updated.rtspUrl)
        assertEquals("1080p", updated.ipcamResolution)
        assertTrue(updated.ipcamRecord)
        assertTrue(updated.timelapseEnabled)
    }
}
