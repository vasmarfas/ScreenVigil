package com.vasmarfas.wakelux

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.vasmarfas.wakelux.root.RootShell

object SettingsWriter {

    private const val ROOT_WRITE_MIN_INTERVAL_MS = 2_000L
    private val lastRootWrite = HashMap<String, Long>()

    fun canWriteSystem(context: Context): Boolean =
        runCatching { Settings.System.canWrite(context) }.getOrDefault(false)

    fun readInt(context: Context, key: String, fallback: Int): Int =
        runCatching { Settings.System.getInt(context.contentResolver, key, fallback) }
            .getOrDefault(fallback)

    fun putInt(context: Context, config: Config, key: String, value: Int): Boolean {
        if (canWriteSystem(context)) {
            val ok = runCatching {
                Settings.System.putInt(context.contentResolver, key, value)
            }.getOrDefault(false)
            if (ok) return true
        }
        if (config.rootEnabled && config.rootSettingsFallback && RootShell.isAvailable()) {
            val now = SystemClock.elapsedRealtime()
            synchronized(lastRootWrite) {
                val previous = lastRootWrite[key] ?: 0L
                if (now - previous < ROOT_WRITE_MIN_INTERVAL_MS) return false
                lastRootWrite[key] = now
            }
            return RootShell.putSystemSetting(key, value.toString())
        }
        return false
    }

    fun ensureManualBrightnessMode(context: Context, config: Config): Boolean {
        val mode = readInt(context, Settings.System.SCREEN_BRIGHTNESS_MODE, -1)
        if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) return true
        return putInt(
            context,
            config,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
    }
}
