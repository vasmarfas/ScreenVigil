package com.vasmarfas.screenvigil

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.Calendar

data class CurvePoint(val luma: Int, val brightness: Int)

data class Config(
    val autoBrightness: Boolean = true,
    val motionEnabled: Boolean = true,
    val lumaMin: Int = 10,
    val lumaMax: Int = 180,
    val brightnessMin: Int = 10,
    val brightnessMax: Int = 255,
    val alpha: Float = 0.15f,
    val hysteresis: Int = 5,
    val curveEnabled: Boolean = false,
    val curvePoints: List<CurvePoint> = emptyList(),
    val pixelThreshold: Int = 20,
    val motionRatio: Float = 0.02f,
    val confirmFrames: Int = 2,
    val wakeMotionRatio: Float = 0.12f,
    val wakeConfirmFrames: Int = 2,
    val wakeZoneEnabled: Boolean = false,
    val wakeZoneFraction: Float = 0.5f,
    val proximityWake: Boolean = false,
    val lightChangeRatio: Float = 0.5f,
    val darkCutoffLuma: Int = 2,
    val screenOffTimeoutMs: Int = 30_000,
    val manageScreenOffTimeout: Boolean = true,
    val keepAwakeOnMotion: Boolean = true,
    val wakeLockMs: Int = 1000,
    val frameIntervalMs: Int = 1000,
    val watchdogTimeoutMs: Int = 15_000,
    val cameraFacing: Int = Prefs.FACING_FRONT,
    val aeLock: Boolean = false,
    val exposureCompensation: Int = 0,
    val quietEnabled: Boolean = false,
    val quietStartMin: Int = 23 * 60,
    val quietEndMin: Int = 7 * 60,
    val quietBrightnessMax: Int = 40,
    val quietNoWake: Boolean = false,
    val rootEnabled: Boolean = false,
    val rootSettingsFallback: Boolean = true,
    val rootForceScreenOff: Boolean = false,
    val rootForceScreenOffDelayMs: Int = 30_000,
    val rootAutoGrant: Boolean = false,
    val haEnabled: Boolean = false,
    val haUrl: String = "",
    val haToken: String = "",
    val haPrefix: String = "screenvigil",
    val haIntervalSec: Int = 30,
    val startOnBoot: Boolean = true,
    val commandToken: String = "",
    val diagFileLog: Boolean = false,
    val diagHistoryMin: Int = 60,
) {
    fun isQuietAt(timeMs: Long): Boolean {
        if (!quietEnabled) return false
        if (quietStartMin == quietEndMin) return false
        val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return if (quietStartMin < quietEndMin) {
            minutes >= quietStartMin && minutes < quietEndMin
        } else {
            minutes >= quietStartMin || minutes < quietEndMin
        }
    }

    fun effectiveBrightnessMax(timeMs: Long): Int =
        if (isQuietAt(timeMs)) minOf(brightnessMax, quietBrightnessMax) else brightnessMax
}

class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(read())
    val config: StateFlow<Config> = _config

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _config.value = read()
    }

    init {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    val current: Config get() = _config.value

    fun putBoolean(key: String, value: Boolean) = sp.edit().putBoolean(key, value).apply()
    fun putInt(key: String, value: Int) = sp.edit().putInt(key, value).apply()
    fun putFloat(key: String, value: Float) = sp.edit().putFloat(key, value).apply()
    fun putString(key: String, value: String) = sp.edit().putString(key, value).apply()

    fun putCurve(points: List<CurvePoint>) = putString(KEY_CURVE, encodeCurve(points))

    fun resetAll() {
        sp.edit().clear().apply()
        _config.value = read()
    }

    fun exportJson(): String {
        val c = current
        val o = JSONObject()
        o.put(KEY_AUTO_BRIGHTNESS, c.autoBrightness)
        o.put(KEY_MOTION, c.motionEnabled)
        o.put(KEY_LUMA_MIN, c.lumaMin)
        o.put(KEY_LUMA_MAX, c.lumaMax)
        o.put(KEY_BRIGHTNESS_MIN, c.brightnessMin)
        o.put(KEY_BRIGHTNESS_MAX, c.brightnessMax)
        o.put(KEY_ALPHA, c.alpha.toDouble())
        o.put(KEY_HYSTERESIS, c.hysteresis)
        o.put(KEY_CURVE_ENABLED, c.curveEnabled)
        o.put(KEY_CURVE, encodeCurve(c.curvePoints))
        o.put(KEY_PIXEL_THRESHOLD, c.pixelThreshold)
        o.put(KEY_MOTION_RATIO, c.motionRatio.toDouble())
        o.put(KEY_CONFIRM_FRAMES, c.confirmFrames)
        o.put(KEY_WAKE_RATIO, c.wakeMotionRatio.toDouble())
        o.put(KEY_WAKE_CONFIRM, c.wakeConfirmFrames)
        o.put(KEY_WAKE_ZONE, c.wakeZoneEnabled)
        o.put(KEY_WAKE_ZONE_SIZE, c.wakeZoneFraction.toDouble())
        o.put(KEY_PROXIMITY, c.proximityWake)
        o.put(KEY_LIGHT_CHANGE_RATIO, c.lightChangeRatio.toDouble())
        o.put(KEY_DARK_CUTOFF, c.darkCutoffLuma)
        o.put(KEY_SCREEN_TIMEOUT, c.screenOffTimeoutMs)
        o.put(KEY_MANAGE_TIMEOUT, c.manageScreenOffTimeout)
        o.put(KEY_KEEP_AWAKE, c.keepAwakeOnMotion)
        o.put(KEY_WAKELOCK_MS, c.wakeLockMs)
        o.put(KEY_FRAME_INTERVAL, c.frameIntervalMs)
        o.put(KEY_WATCHDOG, c.watchdogTimeoutMs)
        o.put(KEY_FACING, c.cameraFacing)
        o.put(KEY_AE_LOCK, c.aeLock)
        o.put(KEY_EXPOSURE_COMP, c.exposureCompensation)
        o.put(KEY_QUIET_ENABLED, c.quietEnabled)
        o.put(KEY_QUIET_START, c.quietStartMin)
        o.put(KEY_QUIET_END, c.quietEndMin)
        o.put(KEY_QUIET_MAX, c.quietBrightnessMax)
        o.put(KEY_QUIET_NO_WAKE, c.quietNoWake)
        o.put(KEY_ROOT_ENABLED, c.rootEnabled)
        o.put(KEY_ROOT_FALLBACK, c.rootSettingsFallback)
        o.put(KEY_ROOT_FORCE_OFF, c.rootForceScreenOff)
        o.put(KEY_ROOT_FORCE_OFF_DELAY, c.rootForceScreenOffDelayMs)
        o.put(KEY_ROOT_AUTO_GRANT, c.rootAutoGrant)
        o.put(KEY_HA_ENABLED, c.haEnabled)
        o.put(KEY_HA_URL, c.haUrl)
        o.put(KEY_HA_PREFIX, c.haPrefix)
        o.put(KEY_HA_INTERVAL, c.haIntervalSec)
        o.put(KEY_BOOT, c.startOnBoot)
        o.put(KEY_CMD_TOKEN, c.commandToken)
        o.put(KEY_DIAG_FILE, c.diagFileLog)
        o.put(KEY_DIAG_HISTORY, c.diagHistoryMin)
        return o.toString(2)
    }

    fun importJson(json: String): Int {
        val parsed = runCatching { JSONObject(json) }.getOrNull() ?: return 0
        var applied = 0
        val editor = sp.edit()
        for (key in parsed.keys()) {
            val raw = parsed.optString(key)
            val ok = when (key) {
                KEY_AUTO_BRIGHTNESS, KEY_MOTION, KEY_CURVE_ENABLED, KEY_MANAGE_TIMEOUT,
                KEY_KEEP_AWAKE, KEY_QUIET_ENABLED, KEY_QUIET_NO_WAKE, KEY_ROOT_ENABLED,
                KEY_ROOT_FALLBACK, KEY_ROOT_FORCE_OFF, KEY_ROOT_AUTO_GRANT, KEY_HA_ENABLED,
                KEY_BOOT, KEY_DIAG_FILE, KEY_AE_LOCK, KEY_WAKE_ZONE, KEY_PROXIMITY ->
                    raw.toBooleanStrictOrNull()?.let { editor.putBoolean(key, it); true } ?: false

                KEY_ALPHA, KEY_MOTION_RATIO, KEY_LIGHT_CHANGE_RATIO, KEY_WAKE_RATIO,
                KEY_WAKE_ZONE_SIZE ->
                    raw.toFloatOrNull()?.let { editor.putFloat(key, it); true } ?: false

                KEY_CURVE, KEY_HA_URL, KEY_HA_TOKEN, KEY_HA_PREFIX, KEY_CMD_TOKEN -> {
                    editor.putString(key, raw); true
                }

                else -> raw.toIntOrNull()?.let { editor.putInt(key, it); true } ?: false
            }
            if (ok) applied++
        }
        editor.apply()
        _config.value = read()
        return applied
    }

    fun setByKey(key: String, raw: String): Boolean = importJson("{\"$key\": \"$raw\"}") == 1

    private fun read(): Config = Config(
        autoBrightness = sp.getBoolean(KEY_AUTO_BRIGHTNESS, true),
        motionEnabled = sp.getBoolean(KEY_MOTION, true),
        lumaMin = sp.getInt(KEY_LUMA_MIN, 10),
        lumaMax = sp.getInt(KEY_LUMA_MAX, 180),
        brightnessMin = sp.getInt(KEY_BRIGHTNESS_MIN, 10),
        brightnessMax = sp.getInt(KEY_BRIGHTNESS_MAX, 255),
        alpha = sp.getFloat(KEY_ALPHA, 0.15f),
        hysteresis = sp.getInt(KEY_HYSTERESIS, 5),
        curveEnabled = sp.getBoolean(KEY_CURVE_ENABLED, false),
        curvePoints = decodeCurve(sp.getString(KEY_CURVE, null)),
        pixelThreshold = sp.getInt(KEY_PIXEL_THRESHOLD, 20),
        motionRatio = sp.getFloat(KEY_MOTION_RATIO, 0.02f),
        confirmFrames = sp.getInt(KEY_CONFIRM_FRAMES, 2),
        wakeMotionRatio = sp.getFloat(KEY_WAKE_RATIO, 0.12f),
        wakeConfirmFrames = sp.getInt(KEY_WAKE_CONFIRM, 2),
        wakeZoneEnabled = sp.getBoolean(KEY_WAKE_ZONE, false),
        wakeZoneFraction = sp.getFloat(KEY_WAKE_ZONE_SIZE, 0.5f),
        proximityWake = sp.getBoolean(KEY_PROXIMITY, false),
        lightChangeRatio = sp.getFloat(KEY_LIGHT_CHANGE_RATIO, 0.5f),
        darkCutoffLuma = sp.getInt(KEY_DARK_CUTOFF, 2),
        screenOffTimeoutMs = sp.getInt(KEY_SCREEN_TIMEOUT, 30_000),
        manageScreenOffTimeout = sp.getBoolean(KEY_MANAGE_TIMEOUT, true),
        keepAwakeOnMotion = sp.getBoolean(KEY_KEEP_AWAKE, true),
        wakeLockMs = sp.getInt(KEY_WAKELOCK_MS, 1000),
        frameIntervalMs = sp.getInt(KEY_FRAME_INTERVAL, 1000),
        watchdogTimeoutMs = sp.getInt(KEY_WATCHDOG, 15_000),
        cameraFacing = sp.getInt(KEY_FACING, FACING_FRONT),
        aeLock = sp.getBoolean(KEY_AE_LOCK, false),
        exposureCompensation = sp.getInt(KEY_EXPOSURE_COMP, 0),
        quietEnabled = sp.getBoolean(KEY_QUIET_ENABLED, false),
        quietStartMin = sp.getInt(KEY_QUIET_START, 23 * 60),
        quietEndMin = sp.getInt(KEY_QUIET_END, 7 * 60),
        quietBrightnessMax = sp.getInt(KEY_QUIET_MAX, 40),
        quietNoWake = sp.getBoolean(KEY_QUIET_NO_WAKE, false),
        rootEnabled = sp.getBoolean(KEY_ROOT_ENABLED, false),
        rootSettingsFallback = sp.getBoolean(KEY_ROOT_FALLBACK, true),
        rootForceScreenOff = sp.getBoolean(KEY_ROOT_FORCE_OFF, false),
        rootForceScreenOffDelayMs = sp.getInt(KEY_ROOT_FORCE_OFF_DELAY, 30_000),
        rootAutoGrant = sp.getBoolean(KEY_ROOT_AUTO_GRANT, false),
        haEnabled = sp.getBoolean(KEY_HA_ENABLED, false),
        haUrl = sp.getString(KEY_HA_URL, "").orEmpty(),
        haToken = sp.getString(KEY_HA_TOKEN, "").orEmpty(),
        haPrefix = sp.getString(KEY_HA_PREFIX, "screenvigil").orEmpty().ifBlank { "screenvigil" },
        haIntervalSec = sp.getInt(KEY_HA_INTERVAL, 30),
        startOnBoot = sp.getBoolean(KEY_BOOT, true),
        commandToken = sp.getString(KEY_CMD_TOKEN, "").orEmpty(),
        diagFileLog = sp.getBoolean(KEY_DIAG_FILE, false),
        diagHistoryMin = sp.getInt(KEY_DIAG_HISTORY, 60),
    )

    companion object {
        private const val FILE = "screenvigil"

        const val KEY_AUTO_BRIGHTNESS = "auto_brightness"
        const val KEY_MOTION = "motion_enabled"
        const val KEY_LUMA_MIN = "luma_min"
        const val KEY_LUMA_MAX = "luma_max"
        const val KEY_BRIGHTNESS_MIN = "brightness_min"
        const val KEY_BRIGHTNESS_MAX = "brightness_max"
        const val KEY_ALPHA = "alpha"
        const val KEY_HYSTERESIS = "hysteresis"
        const val KEY_CURVE_ENABLED = "curve_enabled"
        const val KEY_CURVE = "curve_points"
        const val KEY_PIXEL_THRESHOLD = "pixel_threshold"
        const val KEY_MOTION_RATIO = "motion_ratio"
        const val KEY_CONFIRM_FRAMES = "confirm_frames"
        const val KEY_WAKE_RATIO = "wake_motion_ratio"
        const val KEY_WAKE_CONFIRM = "wake_confirm_frames"
        const val KEY_WAKE_ZONE = "wake_zone_enabled"
        const val KEY_WAKE_ZONE_SIZE = "wake_zone_size"
        const val KEY_PROXIMITY = "proximity_wake"
        const val KEY_LIGHT_CHANGE_RATIO = "light_change_ratio"
        const val KEY_DARK_CUTOFF = "dark_cutoff_luma"
        const val KEY_SCREEN_TIMEOUT = "screen_off_timeout_ms"
        const val KEY_MANAGE_TIMEOUT = "manage_screen_off_timeout"
        const val KEY_KEEP_AWAKE = "keep_awake_on_motion"
        const val KEY_WAKELOCK_MS = "wakelock_ms"
        const val KEY_FRAME_INTERVAL = "frame_interval_ms"
        const val KEY_WATCHDOG = "watchdog_timeout_ms"
        const val KEY_FACING = "camera_facing"
        const val KEY_AE_LOCK = "ae_lock"
        const val KEY_EXPOSURE_COMP = "exposure_compensation"
        const val KEY_QUIET_ENABLED = "quiet_enabled"
        const val KEY_QUIET_START = "quiet_start_min"
        const val KEY_QUIET_END = "quiet_end_min"
        const val KEY_QUIET_MAX = "quiet_brightness_max"
        const val KEY_QUIET_NO_WAKE = "quiet_no_wake"
        const val KEY_ROOT_ENABLED = "root_enabled"
        const val KEY_ROOT_FALLBACK = "root_settings_fallback"
        const val KEY_ROOT_FORCE_OFF = "root_force_screen_off"
        const val KEY_ROOT_FORCE_OFF_DELAY = "root_force_screen_off_delay_ms"
        const val KEY_ROOT_AUTO_GRANT = "root_auto_grant"
        const val KEY_HA_ENABLED = "ha_enabled"
        const val KEY_HA_URL = "ha_url"
        const val KEY_HA_TOKEN = "ha_token"
        const val KEY_HA_PREFIX = "ha_prefix"
        const val KEY_HA_INTERVAL = "ha_interval_sec"
        const val KEY_BOOT = "start_on_boot"
        const val KEY_CMD_TOKEN = "command_token"
        const val KEY_DIAG_FILE = "diag_file_log"
        const val KEY_DIAG_HISTORY = "diag_history_min"

        const val FACING_FRONT = 1
        const val FACING_BACK = 0

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }

        fun encodeCurve(points: List<CurvePoint>): String =
            points.joinToString(",") { "${it.luma}:${it.brightness}" }

        fun decodeCurve(raw: String?): List<CurvePoint> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(',').mapNotNull { part ->
                val bits = part.split(':')
                if (bits.size != 2) return@mapNotNull null
                val luma = bits[0].trim().toIntOrNull() ?: return@mapNotNull null
                val brightness = bits[1].trim().toIntOrNull() ?: return@mapNotNull null
                CurvePoint(luma.coerceIn(0, 255), brightness.coerceIn(0, 255))
            }.sortedBy { it.luma }
        }
    }
}
