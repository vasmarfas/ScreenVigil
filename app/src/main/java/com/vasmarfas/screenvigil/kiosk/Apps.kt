package com.vasmarfas.screenvigil.kiosk

import android.content.Context
import android.content.Intent

data class LaunchableApp(val label: String, val packageName: String)

fun appLabel(context: Context, packageName: String): String? = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
}.getOrNull()

fun launchableApps(context: Context): List<LaunchableApp> {
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return context.packageManager.queryIntentActivities(launcher, 0)
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it != context.packageName }
        .map { LaunchableApp(appLabel(context, it) ?: it, it) }
        .sortedBy { it.label.lowercase() }
}
