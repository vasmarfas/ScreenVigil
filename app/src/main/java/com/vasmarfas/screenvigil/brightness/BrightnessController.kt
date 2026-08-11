package com.vasmarfas.screenvigil.brightness

import android.content.Context
import android.provider.Settings
import com.vasmarfas.screenvigil.Config
import com.vasmarfas.screenvigil.SettingsWriter
import kotlin.math.abs
import kotlin.math.roundToInt

class BrightnessController(private val context: Context) {

    data class Update(
        val target: Int,
        val smoothed: Float,
        val applied: Int,
        val written: Boolean,
        val writeFailed: Boolean,
    )

    private var smoothed = Float.NaN
    private var applied = -1
    private var manualModeChecked = false

    val lastApplied: Int get() = applied
    val lastSmoothed: Float get() = smoothed

    fun reset() {
        smoothed = Float.NaN
        applied = -1
        manualModeChecked = false
    }

    fun onLuma(avgLuma: Int, config: Config, now: Long): Update {
        val target = BrightnessCurve.map(avgLuma, config, now)
        val systemValue = SettingsWriter.readInt(
            context,
            Settings.System.SCREEN_BRIGHTNESS,
            target,
        )

        if (smoothed.isNaN()) {
            smoothed = systemValue.toFloat()
            applied = systemValue
        } else if (applied >= 0 && abs(systemValue - applied) > MAX_EXTERNAL_DRIFT) {
            // яркость поменяли извне - продолжаем от фактического значения
            smoothed = systemValue.toFloat()
            applied = systemValue
        }

        val alpha = config.alpha.coerceIn(0.01f, 1f)
        smoothed += (target - smoothed) * alpha
        val candidate = smoothed.roundToInt().coerceIn(1, 255)

        val hysteresis = config.hysteresis.coerceAtLeast(1)
        val needWrite = applied < 0 ||
            abs(candidate - applied) >= hysteresis ||
            (candidate != applied && candidate == target)

        if (!needWrite) {
            return Update(target, smoothed, applied, written = false, writeFailed = false)
        }

        if (!manualModeChecked) {
            SettingsWriter.ensureManualBrightnessMode(context, config)
            manualModeChecked = true
        }

        val ok = SettingsWriter.putInt(
            context,
            config,
            Settings.System.SCREEN_BRIGHTNESS,
            candidate,
        )
        if (ok) applied = candidate
        return Update(target, smoothed, if (ok) candidate else applied, written = ok, writeFailed = !ok)
    }

    private companion object {
        const val MAX_EXTERNAL_DRIFT = 12
    }
}
