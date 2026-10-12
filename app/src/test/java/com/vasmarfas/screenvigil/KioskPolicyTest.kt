package com.vasmarfas.screenvigil

import com.vasmarfas.screenvigil.kiosk.KioskPolicy
import com.vasmarfas.screenvigil.kiosk.KioskPolicy.Action
import com.vasmarfas.screenvigil.kiosk.KioskPolicy.Fault
import com.vasmarfas.screenvigil.kiosk.KioskPolicy.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskPolicyTest {

    private val app = "io.homeassistant.companion.android.minimal"
    private val launcher = "com.android.launcher3"
    private val other = "com.android.settings"
    private val own = "com.vasmarfas.screenvigil"

    private val params = KioskPolicy.Params(
        packageName = app,
        ownPackage = own,
        homePackages = setOf(launcher),
        returnMs = 120_000L,
        restartMs = 0L,
        canKill = true,
    )

    private fun snap(
        now: Long,
        top: String?,
        pid: Int? = 100,
        screenOn: Boolean = true,
        fault: Fault = Fault.NONE,
        pidKnown: Boolean = true,
    ) = KioskPolicy.Snapshot(now, screenOn, pid, pidKnown, top, fault)

    private fun settled(): KioskPolicy = KioskPolicy().also {
        assertEquals(Action.None, it.decide(snap(1_000, app), params))
    }

    @Test
    fun `boot with the launcher on top opens the app cold`() {
        val policy = KioskPolicy()
        assertEquals(Action.Launch(Reason.START, cold = true), policy.decide(snap(1_000, launcher), params))
    }

    @Test
    fun `app already on the screen at start is left alone`() {
        val policy = KioskPolicy()
        assertEquals(Action.None, policy.decide(snap(1_000, app), params))
        assertEquals(Action.None, policy.decide(snap(6_000, app), params))
    }

    @Test
    fun `crash that leaves the launcher on top reopens the app at once`() {
        val policy = settled()
        val action = policy.decide(snap(6_000, launcher, pid = null), params)
        assertEquals(Action.Launch(Reason.NOT_RUNNING, cold = true), action)
    }

    @Test
    fun `process restarted by its own services counts as a crash`() {
        val policy = settled()
        val action = policy.decide(snap(6_000, launcher, pid = 200), params)
        assertEquals(Action.Launch(Reason.RESTARTED, cold = true), action)
    }

    @Test
    fun `process respawned while the screen is off still gets its interface back`() {
        val policy = settled()
        val action = policy.decide(snap(6_000, app, pid = 200, screenOn = false), params)
        assertEquals(Action.Launch(Reason.RESTARTED, cold = true), action)
    }

    @Test
    fun `home screen after another app gets its own ten seconds`() {
        val policy = settled()
        assertEquals(Action.None, policy.decide(snap(6_000, other), params))
        assertEquals(Action.None, policy.decide(snap(20_000, other), params))
        assertEquals(Action.None, policy.decide(snap(25_000, launcher), params))
        assertEquals(
            Action.Launch(Reason.HOME, cold = false),
            policy.decide(snap(35_000, launcher), params),
        )
    }

    @Test
    fun `home button leaves ten seconds to pick another app`() {
        val policy = settled()
        assertEquals(Action.None, policy.decide(snap(6_000, launcher), params))
        assertEquals(Action.None, policy.decide(snap(11_000, launcher), params))
        assertEquals(
            Action.Launch(Reason.HOME, cold = false),
            policy.decide(snap(16_000, launcher), params),
        )
    }

    @Test
    fun `another app stays until the return delay runs out`() {
        val policy = settled()
        assertEquals(Action.None, policy.decide(snap(6_000, other), params))
        assertEquals(Action.None, policy.decide(snap(120_000, other), params))
        assertEquals(
            Action.Launch(Reason.OTHER_APP, cold = false),
            policy.decide(snap(126_000, other), params),
        )
    }

    @Test
    fun `own settings screen never triggers a return`() {
        val policy = settled()
        assertEquals(Action.None, policy.decide(snap(6_000, own), params))
        assertEquals(Action.None, policy.decide(snap(3_600_000, own), params))
    }

    @Test
    fun `screen going dark brings the app back right away`() {
        val policy = settled()
        policy.decide(snap(6_000, other), params)
        assertEquals(Action.Launch(Reason.SCREEN_OFF, cold = false), policy.screenOff(7_000, other, params))
        assertEquals(Action.None, policy.screenOff(7_000, app, params))
    }

    @Test
    fun `dead process is relaunched while the screen is off`() {
        val policy = settled()
        val action = policy.decide(snap(6_000, app, pid = null, screenOn = false), params)
        assertEquals(Action.Launch(Reason.NOT_RUNNING, cold = true), action)
    }

    @Test
    fun `hung app is killed only after the grace period and only with root`() {
        val policy = settled()
        val hung = Fault.NOT_RESPONDING
        assertEquals(Action.None, policy.decide(snap(6_000, app, fault = hung), params))
        assertEquals(Action.None, policy.decide(snap(20_000, app, fault = hung), params))
        assertEquals(
            Action.Kill(Reason.NOT_RESPONDING),
            policy.decide(snap(26_000, app, fault = hung), params),
        )

        val noRoot = settled()
        val withoutRoot = params.copy(canKill = false)
        assertEquals(Action.None, noRoot.decide(snap(6_000, app, fault = hung), withoutRoot))
        assertEquals(Action.None, noRoot.decide(snap(60_000, app, fault = hung), withoutRoot))
    }

    @Test
    fun `crash dialog is cleared at once with root`() {
        val policy = settled()
        assertEquals(
            Action.Kill(Reason.CRASHED),
            policy.decide(snap(6_000, app, fault = Fault.CRASHED), params),
        )
    }

    @Test
    fun `scheduled restart waits for the screen to go off`() {
        val hourly = params.copy(restartMs = 3_600_000L)
        val policy = KioskPolicy()
        assertEquals(Action.None, policy.decide(snap(1_000, app), hourly))
        assertEquals(Action.None, policy.decide(snap(3_700_000, app), hourly))
        assertEquals(
            Action.Kill(Reason.SCHEDULED),
            policy.decide(snap(3_705_000, app, screenOn = false), hourly),
        )
    }

    @Test
    fun `killed app comes back cold after it has stopped`() {
        val policy = settled()
        policy.killed(Reason.SCHEDULED, 10_000)
        assertEquals(Action.None, policy.decide(snap(11_000, app, screenOn = false), params))
        assertEquals(
            Action.Launch(Reason.SCHEDULED, cold = true),
            policy.decide(snap(15_000, app, pid = null, screenOn = false), params),
        )
    }

    @Test
    fun `nothing is decided while a fresh launch settles`() {
        val policy = KioskPolicy()
        policy.decide(snap(1_000, launcher), params)
        policy.launched(1_000, cold = true)
        assertEquals(Action.None, policy.decide(snap(6_000, launcher, pid = null), params))
        assertEquals(
            Action.Launch(Reason.NOT_RUNNING, cold = true),
            policy.decide(snap(16_000, launcher, pid = null), params),
        )
    }

    @Test
    fun `app that keeps dying is retried less often`() {
        val policy = KioskPolicy()
        repeat(KioskPolicy.BACKOFF_COUNT) { i -> policy.launched(i * 20_000L, cold = true) }
        assertTrue(policy.backingOff)
        val last = (KioskPolicy.BACKOFF_COUNT - 1) * 20_000L
        assertEquals(Action.None, policy.decide(snap(last + 60_000, launcher, pid = null), params))
        assertEquals(
            Action.Launch(Reason.NOT_RUNNING, cold = true),
            policy.decide(snap(last + KioskPolicy.BACKOFF_MS, launcher, pid = null), params),
        )
    }

    @Test
    fun `warm returns do not count towards the back-off`() {
        val policy = KioskPolicy()
        repeat(10) { i -> policy.launched(i * 20_000L, cold = false) }
        assertFalse(policy.backingOff)
    }

    @Test
    fun `without usage access only boot and crashes are handled`() {
        val policy = KioskPolicy()
        val blind = { now: Long, pid: Int? -> snap(now, top = null, pid = pid) }
        assertEquals(Action.Launch(Reason.START, cold = true), policy.decide(blind(1_000, 100), params))
        policy.launched(1_000, cold = true)
        assertEquals(Action.None, policy.decide(blind(20_000, 100), params))
        assertEquals(
            Action.Launch(Reason.NOT_RUNNING, cold = true),
            policy.decide(blind(25_000, null), params),
        )
    }

    @Test
    fun `hidden proc falls back to the screen state alone`() {
        val policy = settled()
        val unknown = snap(6_000, launcher, pid = null, pidKnown = false)
        assertEquals(Action.None, policy.decide(unknown, params))
        assertEquals(
            Action.Launch(Reason.HOME, cold = false),
            policy.decide(unknown.copy(now = 16_000), params),
        )
    }
}
