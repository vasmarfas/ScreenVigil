package com.vasmarfas.screenvigil.kiosk

class KioskPolicy {

    enum class Reason {
        START,
        NOT_RUNNING,
        RESTARTED,
        HOME,
        OTHER_APP,
        SCREEN_OFF,
        NOT_RESPONDING,
        CRASHED,
        SCHEDULED,
        COMMAND,
    }

    enum class Fault { NONE, CRASHED, NOT_RESPONDING }

    sealed interface Action {
        data object None : Action
        data class Launch(val reason: Reason, val cold: Boolean) : Action
        data class Kill(val reason: Reason) : Action
    }

    data class Snapshot(
        val now: Long,
        val screenOn: Boolean,
        val pid: Int?,
        val pidKnown: Boolean,
        val top: String?,
        val fault: Fault = Fault.NONE,
    )

    data class Params(
        val packageName: String,
        val ownPackage: String,
        val homePackages: Set<String>,
        val returnMs: Long,
        val restartMs: Long,
        val canKill: Boolean,
    )

    private var pending: Reason? = Reason.START
    private var lastPid: Int? = null
    private var startedAt = 0L
    private var awayTop: String? = null
    private var awaySince = 0L
    private var faultSince = 0L
    private var holdUntil = 0L
    private val coldLaunches = ArrayDeque<Long>()

    val backingOff: Boolean get() = coldLaunches.size >= BACKOFF_COUNT

    fun decide(s: Snapshot, p: Params): Action {
        if (startedAt == 0L) startedAt = s.now
        trackPid(s)
        if (s.now < holdUntil) return Action.None
        when (s.fault) {
            Fault.NONE -> faultSince = 0L
            Fault.CRASHED -> return if (p.canKill) Action.Kill(Reason.CRASHED) else Action.None
            Fault.NOT_RESPONDING -> {
                if (faultSince == 0L) faultSince = s.now
                val stuck = s.now - faultSince >= ANR_GRACE_MS
                return if (p.canKill && stuck) Action.Kill(Reason.NOT_RESPONDING) else Action.None
            }
        }
        val running = s.pid != null || !s.pidKnown
        val inFront = s.top == p.packageName && running && (s.screenOn || pending == null)
        if (inFront) {
            pending = null
            clearAway()
            val due = p.restartMs > 0 && s.now - startedAt >= p.restartMs
            return if (due && p.canKill && !s.screenOn) Action.Kill(Reason.SCHEDULED) else Action.None
        }
        val cold = pending
        if (!s.screenOn || s.top == null) {
            return if (cold != null) Action.Launch(cold, cold = true) else Action.None
        }
        if (s.top == p.ownPackage) {
            clearAway()
            return Action.None
        }
        val home = s.top in p.homePackages
        if (home && cold != null) return Action.Launch(cold, cold = true)
        if (s.top != awayTop) {
            awayTop = s.top
            awaySince = s.now
        }
        val limit = if (home) HOME_GRACE_MS else p.returnMs
        if (s.now - awaySince < limit) return Action.None
        return Action.Launch(if (home) Reason.HOME else Reason.OTHER_APP, cold = cold != null)
    }

    fun screenOff(now: Long, top: String?, p: Params): Action {
        if (now < holdUntil || top == null || top == p.packageName) return Action.None
        return Action.Launch(pending ?: Reason.SCREEN_OFF, cold = pending != null)
    }

    fun launched(now: Long, cold: Boolean) {
        pending = null
        clearAway()
        faultSince = 0L
        if (cold) coldLaunches.addLast(now)
        while (coldLaunches.isNotEmpty() && now - coldLaunches.first() > BACKOFF_WINDOW_MS) {
            coldLaunches.removeFirst()
        }
        holdUntil = now + if (backingOff) BACKOFF_MS else SETTLE_MS
    }

    fun launchFailed(now: Long) {
        holdUntil = now + SETTLE_MS
    }

    fun killed(reason: Reason, now: Long) {
        pending = reason
        lastPid = null
        startedAt = now
        faultSince = 0L
        holdUntil = now + KILL_SETTLE_MS
    }

    private fun clearAway() {
        awayTop = null
        awaySince = 0L
    }

    private fun trackPid(s: Snapshot) {
        if (!s.pidKnown) return
        val previous = lastPid
        when {
            s.pid == null -> if (pending == null) pending = Reason.NOT_RUNNING
            previous == null -> startedAt = s.now
            s.pid != previous -> {
                pending = Reason.RESTARTED
                startedAt = s.now
            }
        }
        lastPid = s.pid
    }

    companion object {
        const val SETTLE_MS = 15_000L
        const val HOME_GRACE_MS = 10_000L
        const val ANR_GRACE_MS = 20_000L
        const val KILL_SETTLE_MS = 3_000L
        const val BACKOFF_WINDOW_MS = 10 * 60_000L
        const val BACKOFF_COUNT = 5
        const val BACKOFF_MS = 5 * 60_000L
    }
}
