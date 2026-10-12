package com.vasmarfas.screenvigil.kiosk

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

class ForegroundWatcher(private val context: Context) {

    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val appOps = context.getSystemService(AppOpsManager::class.java)
    private val event = UsageEvents.Event()

    private var queriedUntil = 0L
    private var top: String? = null

    fun hasAccess(): Boolean {
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @Suppress("DEPRECATION")
    fun top(): String? {
        val now = System.currentTimeMillis()
        val from = if (queriedUntil == 0L || queriedUntil > now) {
            now - LOOKBACK_MS
        } else {
            queriedUntil - OVERLAP_MS
        }
        val events = runCatching { usage.queryEvents(from, now) }.getOrNull() ?: return top
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) top = event.packageName
        }
        queriedUntil = now
        return top
    }

    private companion object {
        const val LOOKBACK_MS = 24 * 60 * 60_000L
        const val OVERLAP_MS = 2_000L
    }
}
