package com.vasmarfas.screenvigil

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.vasmarfas.screenvigil.diag.Diag
import org.json.JSONObject

class CommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs.get(context)
        Diag.attach(context)
        val expected = prefs.current.commandToken
        if (expected.isNotBlank() && intent.getStringExtra("token") != expected) {
            reply("denied")
            return
        }
        when (val cmd = intent.getStringExtra("cmd")?.lowercase()) {
            "start" -> {
                KioskSensorService.start(context)
                reply("ok")
            }

            "stop" -> {
                KioskSensorService.stop(context)
                reply("ok")
            }

            "wake" -> {
                KioskSensorService.command(context, KioskSensorService.ACTION_WAKE)
                reply("ok")
            }

            "screen_off" -> {
                KioskSensorService.command(context, KioskSensorService.ACTION_SCREEN_OFF)
                reply("ok")
            }

            "restart_camera" -> {
                KioskSensorService.command(context, KioskSensorService.ACTION_RESTART_CAMERA)
                reply("ok")
            }

            "kiosk_show" -> {
                KioskSensorService.command(context, KioskSensorService.ACTION_KIOSK_SHOW)
                reply("ok")
            }

            "kiosk_restart" -> {
                KioskSensorService.command(context, KioskSensorService.ACTION_KIOSK_RESTART)
                reply("ok")
            }

            "status" -> reply(statusJson())

            "export" -> reply(prefs.exportJson())

            "import" -> {
                val json = intent.getStringExtra("json").orEmpty()
                val applied = prefs.importJson(json)
                reply("applied=$applied")
            }

            "set" -> {
                val key = intent.getStringExtra("key").orEmpty()
                val value = intent.getStringExtra("value").orEmpty()
                val ok = key.isNotBlank() && prefs.setByKey(key, value)
                Diag.event(
                    context.getString(
                        if (ok) R.string.ev_cmd_set else R.string.ev_cmd_rejected,
                        key,
                        value,
                    ),
                )
                reply(if (ok) "ok" else "bad_key_or_value")
            }

            "reset" -> {
                prefs.resetAll()
                reply("ok")
            }

            "csv" -> reply(Diag.currentCsvPath() ?: "disabled")

            else -> reply("unknown_cmd:$cmd")
        }
    }

    private fun BroadcastReceiver.reply(data: String) {
        if (isOrderedBroadcast) {
            resultCode = 0
            resultData = data
        }
    }

    private fun statusJson(): String {
        val live = ScreenVigilState.live.value
        return JSONObject().apply {
            put("service_running", live.serviceRunning)
            put("camera_state", live.cameraState.name.lowercase())
            put("camera_message", live.cameraMessage ?: "")
            put("preview", "${live.previewWidth}x${live.previewHeight}")
            put("avg_luma", live.avgLuma)
            put("target_brightness", live.targetBrightness)
            put("applied_brightness", live.appliedBrightness)
            put("brightness_write_failed", live.brightnessWriteFailed)
            put("diff_percent", live.diffPercent)
            put("motion", live.motion)
            put("motion_nearby", live.motionNearby)
            put("zone_diff_percent", live.zoneDiffPercent)
            put("motion_verdict", live.motionVerdict.name.lowercase())
            put("proximity_available", live.proximityAvailable)
            put("proximity_near", live.proximityNear)
            put("light_changes", live.lightChanges)
            put("frames", live.frameCount)
            put("camera_restarts", live.cameraRestarts)
            put("wake_count", live.wakeCount)
            put("last_wake_at", live.lastWakeAt)
            put("screen_on", live.screenOn)
            put("quiet_active", live.quietActive)
            put("root_available", live.rootAvailable)
            put("ha_status", live.haStatus)
            put("kiosk_state", live.kioskState.name.lowercase())
            put("kiosk_launches", live.kioskLaunches)
            put("last_power_off", live.powerOffReason)
            put(
                "uptime_sec",
                if (live.startedElapsed > 0) {
                    (SystemClock.elapsedRealtime() - live.startedElapsed) / 1000
                } else {
                    0
                },
            )
        }.toString()
    }
}
