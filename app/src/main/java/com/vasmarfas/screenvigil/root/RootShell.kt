package com.vasmarfas.screenvigil.root

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object RootShell {

    private const val TAG = "ScreenVigil/Root"
    private val COMPONENT = Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+")

    @Volatile
    private var available: Boolean? = null

    fun isAvailable(): Boolean {
        available?.let { return it }
        val result = runCatching { exec("id").let { it.ok && it.output.contains("uid=0") } }
            .getOrDefault(false)
        available = result
        return result
    }

    fun invalidate() {
        available = null
    }

    data class Result(val ok: Boolean, val output: String)

    fun exec(command: String): Result {
        var process: Process? = null
        return try {
            process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val code = process.waitFor()
            Result(code == 0, output.trim())
        } catch (t: Throwable) {
            Log.w(TAG, "su failed: $command", t)
            Result(false, t.message.orEmpty())
        } finally {
            runCatching { process?.destroy() }
        }
    }

    fun putSystemSetting(key: String, value: String): Boolean =
        exec("settings put system $key $value").ok

    fun grantPermission(packageName: String, permission: String): Boolean =
        exec("pm grant $packageName $permission").ok

    fun grantWriteSettings(packageName: String): Boolean =
        exec("appops set $packageName WRITE_SETTINGS allow").ok

    fun grantUsageStats(packageName: String): Boolean =
        exec("appops set $packageName GET_USAGE_STATS allow").ok

    fun exemptFromDoze(packageName: String): Boolean =
        exec("dumpsys deviceidle whitelist +$packageName").ok

    fun forceStop(packageName: String): Boolean = exec("am force-stop $packageName").ok

    fun startLauncherActivity(component: String): Boolean {
        if (!COMPONENT.matches(component)) return false
        return exec(
            "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
                "-f 0x10200000 -n '$component'",
        ).let { it.ok && !it.output.contains("Error") }
    }

    fun kernelLog(): String = exec("dmesg").output

    fun screenOff(): Boolean = exec("input keyevent 26").ok

    fun screenOn(): Boolean = exec("input keyevent 224").ok
}
