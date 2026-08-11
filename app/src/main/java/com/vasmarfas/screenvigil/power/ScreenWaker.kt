package com.vasmarfas.screenvigil.power

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.vasmarfas.screenvigil.Config
import com.vasmarfas.screenvigil.SettingsWriter
import com.vasmarfas.screenvigil.root.RootShell

class ScreenWaker(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    var wakeCount = 0
        private set
    var lastWakeAt = 0L
        private set
    private var savedTimeout: Int? = null

    val screenOn: Boolean
        get() =powerManager.isInteractive

    @Suppress("DEPRECATION")
    private val wakeLock: PowerManager.WakeLock by lazy {
        powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            WAKE_TAG,
        ).apply { setReferenceCounted(false) }
    }

    fun wake(
        config: Config,
        now: Long = System.currentTimeMillis(),
        countAsWake: Boolean = true,
    ): Boolean {
        if (config.quietNoWake && config.isQuietAt(now)) return false
        val timeout = config.wakeLockMs.coerceIn(200, 30_000).toLong()
        return try {
            wakeLock.acquire(timeout)
            if (countAsWake) wakeCount++
            lastWakeAt = now
            true
        } catch (t: Throwable) {
            Log.w(TAG, "wake failed", t)
            false
        }
    }

    fun releaseWakeLock() {
        runCatching { if (wakeLock.isHeld) wakeLock.release() }
    }

    fun applyScreenOffTimeout(config: Config): Boolean {
        if (!config.manageScreenOffTimeout) return false
        val current = SettingsWriter.readInt(context, Settings.System.SCREEN_OFF_TIMEOUT, -1)
        if (savedTimeout == null && current > 0) savedTimeout = current
        if (current == config.screenOffTimeoutMs) return true
        return SettingsWriter.putInt(
            context,
            config,
            Settings.System.SCREEN_OFF_TIMEOUT,
            config.screenOffTimeoutMs,
        )
    }

    fun restoreScreenOffTimeout(config: Config) {
        val saved = savedTimeout ?: return
        SettingsWriter.putInt(context, config, Settings.System.SCREEN_OFF_TIMEOUT, saved)
        savedTimeout = null
    }

    enum class ScreenOffResult { DONE, ALREADY_OFF, NO_ROOT, DISABLED, FAILED }

    fun forceScreenOff(config: Config, requireAutoSetting: Boolean = true): ScreenOffResult {
        if (!config.rootEnabled) return ScreenOffResult.DISABLED
        if (requireAutoSetting && !config.rootForceScreenOff) return ScreenOffResult.DISABLED
        if (!screenOn) return ScreenOffResult.ALREADY_OFF
        if (!RootShell.isAvailable()) return ScreenOffResult.NO_ROOT
        return if (RootShell.screenOff()) ScreenOffResult.DONE else ScreenOffResult.FAILED
    }

    private companion object {
        const val TAG = "ScreenVigil/Screen"
        const val WAKE_TAG = "screenvigil:motion"
    }
}
