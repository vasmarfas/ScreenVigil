package com.vasmarfas.wakelux.ha

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.vasmarfas.wakelux.Config
import com.vasmarfas.wakelux.R
import com.vasmarfas.wakelux.WakeLuxState
import com.vasmarfas.wakelux.diag.Diag
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Публикация чисел в Home Assistant через REST `/api/states`. Кадры никуда не уходят —
 * только avgLuma, яркость, флаг движения и состояние экрана.
 */
class HaPublisher(private val context: Context) {

    companion object {
        const val OK = "ok"
    }


    private val thread = HandlerThread("wakelux-ha").apply { start() }
    private val handler = Handler(thread.looper)

    private var lastPublishAt = 0L
    private var lastMotion: Boolean? = null
    private var lastScreenOn: Boolean? = null

    fun maybePublish(live: WakeLuxState.Live, config: Config, now: Long) {
        if (!config.haEnabled || config.haUrl.isBlank() || config.haToken.isBlank()) return
        val intervalMs = config.haIntervalSec.coerceIn(5, 3600) * 1000L
        val changed = lastMotion != live.motion || lastScreenOn != live.screenOn
        if (!changed && now - lastPublishAt < intervalMs) return
        publish(live, config)
    }

    fun publish(live: WakeLuxState.Live, config: Config) {
        // интервал считается по монотонным часам: сразу после загрузки стенное время может прыгнуть
        lastPublishAt = SystemClock.elapsedRealtime()
        lastMotion = live.motion
        lastScreenOn = live.screenOn
        val base = config.haUrl.trim().trimEnd('/')
        val token = config.haToken.trim()
        val prefix = config.haPrefix.ifBlank { "wakelux" }
        handler.post {
            val results = listOf(
                post(base, token, "sensor.${prefix}_luma", live.avgLuma.toString()) {
                    put("unit_of_measurement", "luma")
                    put("friendly_name", "WakeLux luma")
                    put("icon", "mdi:brightness-6")
                },
                post(base, token, "sensor.${prefix}_brightness", live.appliedBrightness.toString()) {
                    put("unit_of_measurement", "/255")
                    put("friendly_name", "WakeLux screen brightness")
                    put("icon", "mdi:brightness-5")
                },
                post(base, token, "binary_sensor.${prefix}_motion", if (live.motion) "on" else "off") {
                    put("device_class", "motion")
                    put("friendly_name", "WakeLux motion")
                },
                post(base, token, "binary_sensor.${prefix}_screen", if (live.screenOn) "on" else "off") {
                    put("friendly_name", "WakeLux screen on")
                    put("icon", "mdi:tablet-dashboard")
                },
                post(base, token, "sensor.${prefix}_status", live.cameraState.name.lowercase()) {
                    put("friendly_name", "WakeLux status")
                    put("frames", live.frameCount)
                    put("camera_restarts", live.cameraRestarts)
                    put("wake_count", live.wakeCount)
                    put("diff_percent", live.diffPercent)
                    put("quiet_hours", live.quietActive)
                    put(
                        "uptime_sec",
                        if (live.startedElapsed > 0) {
                            (SystemClock.elapsedRealtime() - live.startedElapsed) / 1000
                        } else {
                            0
                        },
                    )
                },
            )
            val failure = results.firstOrNull { it != null }
            report(failure ?: OK)
            if (failure != null) Diag.event(context.getString(R.string.ev_ha_error, failure))
        }
    }

    /** @return null при успехе, текст ошибки при неудаче. */
    private fun post(
        base: String,
        token: String,
        entityId: String,
        state: String,
        attributes: JSONObject.() -> Unit,
    ): String? {
        val body = JSONObject().apply {
            put("state", state)
            put("attributes", JSONObject().apply(attributes))
        }.toString()
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("$base/api/states/$entityId").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            if (code in 200..299) {
                connection.inputStream.use { it.readBytes() }
                null
            } else {
                val text = runCatching { connection.errorStream?.use { String(it.readBytes()) } }
                    .getOrNull()
                    ?.take(120)
                "HTTP $code ${text.orEmpty()}".trim()
            }
        } catch (t: Throwable) {
            t.message ?: t.javaClass.simpleName
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun report(status: String) {
        WakeLuxState.update { it.copy(haStatus = status, haStatusAt = System.currentTimeMillis()) }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
    }
}
