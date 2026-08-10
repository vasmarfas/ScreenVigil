package com.vasmarfas.wakelux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vasmarfas.wakelux.diag.Diag

// Автостарт после перезагрузки, пропадания питания и обновления приложения.
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return
        val prefs = Prefs.get(context)
        if (!prefs.current.startOnBoot) return
        Diag.attach(context)
        Diag.event(context.getString(R.string.ev_boot, action))
        KioskSensorService.start(context)
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
