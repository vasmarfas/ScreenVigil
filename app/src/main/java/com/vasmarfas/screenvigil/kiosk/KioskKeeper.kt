package com.vasmarfas.screenvigil.kiosk

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.vasmarfas.screenvigil.Config
import com.vasmarfas.screenvigil.Prefs
import com.vasmarfas.screenvigil.R
import com.vasmarfas.screenvigil.ScreenVigilState
import com.vasmarfas.screenvigil.diag.Diag
import com.vasmarfas.screenvigil.root.RootShell
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

class KioskKeeper(private val context: Context, private val prefs: Prefs) {

    enum class State { OFF, NO_APP, NO_ACCESS, IN_FRONT, AWAY, WAITING, NOT_RESPONDING }

    private val thread = HandlerThread("screenvigil-kiosk").apply { start() }
    private val handler = Handler(thread.looper)
    private val watcher = ForegroundWatcher(context)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val procVisible = File("/proc/1").exists()
    private val cmdline = ByteArray(256)

    private var policy = KioskPolicy()
    private var policyPackage = ""
    private var processName = ""
    private var homePackages = emptySet<String>()
    private var cachedPid = 0
    private var waitingSince = 0L
    private var launches = 0
    private var lastLaunchAt = 0L
    private var accessWarned = false
    private var rootWarned = false
    private var backoffWarned = false
    private var launchWarned = false

    @Volatile
    private var released = false

    fun start() {
        handler.post(tick)
    }

    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
    }

    fun onScreenOff() {
        handler.post {
            val cfg = prefs.current
            val pkg = target(cfg) ?: return@post
            if (!watcher.hasAccess()) return@post
            val action = policy.screenOff(SystemClock.elapsedRealtime(), watcher.top(), params(cfg, pkg))
            perform(action, cfg, pkg)
        }
    }

    fun show() {
        handler.post {
            val cfg = prefs.current
            val pkg = target(cfg) ?: return@post
            perform(KioskPolicy.Action.Launch(KioskPolicy.Reason.COMMAND, cold = false), cfg, pkg)
        }
    }

    fun restart() {
        handler.post {
            val cfg = prefs.current
            val pkg = target(cfg) ?: return@post
            perform(KioskPolicy.Action.Kill(KioskPolicy.Reason.COMMAND), cfg, pkg)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            runCatching { check() }.onFailure { Log.w(TAG, "kiosk check failed", it) }
            handler.postDelayed(this, PERIOD_MS)
        }
    }

    private fun check() {
        val cfg = prefs.current
        if (!cfg.kioskEnabled) {
            policyPackage = ""
            waitingSince = 0L
            publish(State.OFF)
            return
        }
        val pkg = target(cfg)
        if (pkg == null) {
            publish(State.NO_APP)
            return
        }
        val access = watcher.hasAccess()
        if (!access && !accessWarned) Diag.event(context.getString(R.string.ev_kiosk_no_access))
        accessWarned = !access
        val snapshot = KioskPolicy.Snapshot(
            now = SystemClock.elapsedRealtime(),
            screenOn = powerManager.isInteractive,
            pid = if (procVisible) findPid(processName) else null,
            pidKnown = procVisible,
            top = if (access) watcher.top() else null,
            fault = faultOf(processName),
        )
        val p = params(cfg, pkg)
        val action = policy.decide(snapshot, p)
        if (action !is KioskPolicy.Action.Launch || !action.cold) waitingSince = 0L
        perform(action, cfg, pkg)
        val stuckWithoutRoot = snapshot.fault != KioskPolicy.Fault.NONE && !p.canKill
        if (stuckWithoutRoot && !rootWarned) {
            Diag.event(context.getString(R.string.ev_kiosk_needs_root, labelOf(pkg)))
        }
        rootWarned = stuckWithoutRoot
        publish(
            when {
                waitingSince > 0 -> State.WAITING
                snapshot.fault != KioskPolicy.Fault.NONE -> State.NOT_RESPONDING
                !access -> State.NO_ACCESS
                snapshot.top == pkg -> State.IN_FRONT
                else -> State.AWAY
            },
        )
    }

    private fun perform(action: KioskPolicy.Action, cfg: Config, pkg: String) {
        if (released) return
        val now = SystemClock.elapsedRealtime()
        when (action) {
            KioskPolicy.Action.None -> Unit

            is KioskPolicy.Action.Kill -> {
                if (!rootUsable(cfg)) {
                    Diag.event(context.getString(R.string.ev_kiosk_needs_root, labelOf(pkg)))
                    return
                }
                if (!RootShell.forceStop(pkg)) {
                    RootShell.invalidate()
                    policy.launchFailed(now)
                    return
                }
                Diag.event(
                    context.getString(R.string.ev_kiosk_restart, labelOf(pkg), reasonText(action.reason)),
                )
                cachedPid = 0
                policy.killed(action.reason, now)
            }

            is KioskPolicy.Action.Launch -> {
                if (action.cold && !gateOpen(cfg, pkg, now)) return
                if (!launch(pkg, cfg)) {
                    if (!launchWarned) {
                        Diag.event(context.getString(R.string.ev_kiosk_launch_failed, labelOf(pkg)))
                    }
                    launchWarned = true
                    policy.launchFailed(now)
                    return
                }
                launchWarned = false
                Diag.event(
                    context.getString(R.string.ev_kiosk_launch, labelOf(pkg), reasonText(action.reason)),
                )
                launches++
                lastLaunchAt = System.currentTimeMillis()
                policy.launched(now, action.cold)
                if (policy.backingOff && !backoffWarned) {
                    Diag.event(
                        context.getString(
                            R.string.ev_kiosk_backoff,
                            labelOf(pkg),
                            KioskPolicy.BACKOFF_MS / 60_000,
                        ),
                    )
                }
                backoffWarned = policy.backingOff
            }
        }
    }

    private fun gateOpen(cfg: Config, pkg: String, now: Long): Boolean {
        val url = cfg.haUrl.trim().trimEnd('/')
        val ready = connectivity.activeNetwork != null && (url.isEmpty() || serverAnswers(url))
        if (ready) {
            waitingSince = 0L
            return true
        }
        if (waitingSince == 0L) {
            waitingSince = now
            Diag.event(context.getString(R.string.ev_kiosk_wait, labelOf(pkg)))
            return false
        }
        if (now - waitingSince < MAX_WAIT_MS) return false
        Diag.event(context.getString(R.string.ev_kiosk_wait_over, MAX_WAIT_MS / 1000))
        waitingSince = 0L
        return true
    }

    private fun serverAnswers(base: String): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("$base/api/").openConnection() as HttpURLConnection).apply {
                connectTimeout = PROBE_TIMEOUT_MS
                readTimeout = PROBE_TIMEOUT_MS
                instanceFollowRedirects = false
            }
            connection.responseCode in 200..499
        } catch (t: Throwable) {
            false
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun launch(pkg: String, cfg: Config): Boolean {
        val component = launcherComponent(pkg) ?: return false
        val needsRoot = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !Settings.canDrawOverlays(context)
        if (needsRoot) {
            return rootUsable(cfg) && RootShell.startLauncherActivity(component.flattenToShortString())
        }
        val intent = Intent.makeMainActivity(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun target(cfg: Config): String? {
        val pkg = cfg.kioskPackage
        if (!cfg.kioskEnabled || !Prefs.isPackageName(pkg) || pkg == context.packageName) return null
        val component = launcherComponent(pkg) ?: return null
        if (pkg != policyPackage) {
            policy = KioskPolicy()
            policyPackage = pkg
            processName = runCatching {
                context.packageManager.getActivityInfo(component, 0).processName
            }.getOrNull() ?: pkg
            cachedPid = 0
            homePackages = queryHomePackages()
        }
        return pkg
    }

    private fun params(cfg: Config, pkg: String) = KioskPolicy.Params(
        packageName = pkg,
        ownPackage = context.packageName,
        homePackages = homePackages,
        returnMs = cfg.kioskReturnSec.coerceIn(10, 3600) * 1000L,
        restartMs = cfg.kioskRestartHours.coerceIn(0, 720) * 3_600_000L,
        canKill = rootUsable(cfg),
    )

    private fun rootUsable(cfg: Config): Boolean = cfg.rootEnabled && RootShell.isAvailable()

    private fun launcherComponent(pkg: String): ComponentName? =
        context.packageManager.getLaunchIntentForPackage(pkg)?.component

    private fun queryHomePackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(home, 0)
            .filter { it.priority >= 0 }
            .mapTo(HashSet()) { it.activityInfo.packageName }
    }

    private fun findPid(process: String): Int? {
        if (cachedPid > 0 && commandOf(cachedPid) == process) return cachedPid
        val found = File("/proc").list()?.firstNotNullOfOrNull { name ->
            name.toIntOrNull()?.takeIf { commandOf(it) == process }
        }
        cachedPid = found ?: 0
        return found
    }

    private fun commandOf(pid: Int): String? = runCatching {
        val read = FileInputStream("/proc/$pid/cmdline").use { it.read(cmdline) }
        if (read <= 0) return@runCatching null
        val end = (0 until read).firstOrNull { cmdline[it] == 0.toByte() } ?: read
        String(cmdline, 0, end)
    }.getOrNull()

    private fun faultOf(process: String): KioskPolicy.Fault {
        val errors = runCatching { activityManager.processesInErrorState }.getOrNull()
            ?: return KioskPolicy.Fault.NONE
        val info = errors.firstOrNull { it.processName == process } ?: return KioskPolicy.Fault.NONE
        return when (info.condition) {
            ActivityManager.ProcessErrorStateInfo.CRASHED -> KioskPolicy.Fault.CRASHED
            ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING -> KioskPolicy.Fault.NOT_RESPONDING
            else -> KioskPolicy.Fault.NONE
        }
    }

    private fun labelOf(pkg: String): String = appLabel(context, pkg) ?: pkg

    private fun reasonText(reason: KioskPolicy.Reason): String = context.getString(
        when (reason) {
            KioskPolicy.Reason.START -> R.string.kiosk_reason_start
            KioskPolicy.Reason.NOT_RUNNING -> R.string.kiosk_reason_not_running
            KioskPolicy.Reason.RESTARTED -> R.string.kiosk_reason_restarted
            KioskPolicy.Reason.HOME -> R.string.kiosk_reason_home
            KioskPolicy.Reason.OTHER_APP -> R.string.kiosk_reason_other_app
            KioskPolicy.Reason.SCREEN_OFF -> R.string.kiosk_reason_screen_off
            KioskPolicy.Reason.NOT_RESPONDING -> R.string.kiosk_reason_not_responding
            KioskPolicy.Reason.CRASHED -> R.string.kiosk_reason_crashed
            KioskPolicy.Reason.SCHEDULED -> R.string.kiosk_reason_scheduled
            KioskPolicy.Reason.COMMAND -> R.string.kiosk_reason_command
        },
    )

    private fun publish(state: State) {
        val live = ScreenVigilState.live.value
        if (live.kioskState == state && live.kioskLaunches == launches) return
        ScreenVigilState.update {
            it.copy(kioskState = state, kioskLaunches = launches, kioskLastLaunchAt = lastLaunchAt)
        }
    }

    private companion object {
        const val TAG = "ScreenVigil/Kiosk"
        const val PERIOD_MS = 5_000L
        const val MAX_WAIT_MS = 120_000L
        const val PROBE_TIMEOUT_MS = 3_000
    }
}
