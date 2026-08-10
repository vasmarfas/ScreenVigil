package com.vasmarfas.wakelux

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vasmarfas.wakelux.brightness.BrightnessController
import com.vasmarfas.wakelux.camera.CameraController
import com.vasmarfas.wakelux.camera.FrameProcessor
import com.vasmarfas.wakelux.diag.Diag
import com.vasmarfas.wakelux.ha.HaPublisher
import com.vasmarfas.wakelux.motion.MotionDetector
import com.vasmarfas.wakelux.power.ProximityWatcher
import com.vasmarfas.wakelux.power.ScreenWaker
import com.vasmarfas.wakelux.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class KioskSensorService : Service(), CameraController.Callbacks {

    private lateinit var prefs: Prefs
    private lateinit var brightness: BrightnessController
    private lateinit var waker: ScreenWaker
    private lateinit var camera: CameraController
    private lateinit var proximity: ProximityWatcher
    private val processor = FrameProcessor()
    private val motion = MotionDetector()
    private val ha by lazy { HaPublisher(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var previewPixels: IntArray? = null
    private var lastMotionAt = 0L
    private var lastNotificationAt = 0L
    private var lastSampleAt = 0L
    private var lastLightChangeLogAt = 0L
    private var frameOffset = 0L
    private var restartOffset = 0
    private var startedAt = 0L
    private var startedElapsed = 0L
    private var explicitStop = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val on = intent.action == Intent.ACTION_SCREEN_ON
            WakeLuxState.update { it.copy(screenOn = on) }
            Diag.event(getString(if (on) R.string.ev_screen_on else R.string.ev_screen_off))
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        brightness = BrightnessController(this)
        waker = ScreenWaker(this)
        camera = CameraController(this, this, prefs.current)
        proximity = ProximityWatcher(this) { onProximityNear() }
        Diag.attach(this)
        startedAt = System.currentTimeMillis()
        startedElapsed = SystemClock.elapsedRealtime()

        createNotificationChannel()
        startForegroundSafely(buildNotification(getString(R.string.notif_idle)))

        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )

        WakeLuxState.reset()
        WakeLuxState.update {
            it.copy(
                serviceRunning = true,
                startedAt = startedAt,
                startedElapsed = startedElapsed,
                screenOn = waker.screenOn,
            )
        }

        scope.launch(Dispatchers.Default) {
            val cfg = prefs.current
            if (cfg.rootEnabled && cfg.rootAutoGrant) autoGrantWithRoot()
            WakeLuxState.update { it.copy(rootAvailable = RootShell.isAvailable()) }
        }

        applySystemSettings(prefs.current)
        applyProximity(prefs.current)
        observeConfig()
        camera.start()
        mainHandler.postDelayed(houseKeeping, HOUSEKEEPING_PERIOD_MS)
        Diag.event(getString(R.string.ev_service_started))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                explicitStop = true
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_WAKE -> {
                val woke = waker.wake(prefs.current)
                Diag.event(
                    getString(
                        if (woke) R.string.ev_wake_external else R.string.ev_wake_external_denied,
                    ),
                )
                publishWakeState()
            }

            ACTION_SCREEN_OFF -> scope.launch(Dispatchers.IO) {
                val result = waker.forceScreenOff(prefs.current, requireAutoSetting = false)
                Diag.event(getString(R.string.ev_screen_off_cmd, screenOffResultText(result)))
            }

            ACTION_RESTART_CAMERA -> camera.restart(getString(R.string.ev_camera_external))

            ACTION_HA_TEST -> {
                ha.publish(WakeLuxState.live.value, prefs.current)
                Diag.event(getString(R.string.ev_ha_test))
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Diag.event(getString(if (explicitStop) R.string.ev_service_stopped else R.string.ev_service_killed))
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        proximity.stop()
        camera.release()
        ha.release()
        waker.releaseWakeLock()
        if (explicitStop) waker.restoreScreenOffTimeout(prefs.current)
        WakeLuxState.update { it.copy(serviceRunning = false) }
        WakeLuxState.publishPreview(null)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onFrame(data: ByteArray, width: Int, height: Int) {
        val cfg = prefs.current
        val now = SystemClock.elapsedRealtime()
        val wallNow = System.currentTimeMillis()
        val preview = if (WakeLuxState.uiVisible) obtainPreviewBuffer(width * height) else null

        val zoneFraction = if (cfg.wakeZoneEnabled) cfg.wakeZoneFraction else 0f
        val stats = processor.process(
            data,
            width,
            height,
            cfg.pixelThreshold,
            zoneFraction,
            preview,
        )

        var brightnessUpdate: BrightnessController.Update? = null
        if (cfg.autoBrightness) {
            brightnessUpdate = brightness.onLuma(stats.avgLuma, cfg, now)
        }

        var motionResult: MotionDetector.Result? = null
        if (cfg.motionEnabled) {
            motionResult = motion.update(stats, cfg)
            if (motionResult.verdict == MotionDetector.Verdict.LIGHT_CHANGE) {
                if (now - lastLightChangeLogAt > LIGHT_CHANGE_LOG_PERIOD_MS) {
                    lastLightChangeLogAt = now
                    Diag.event(
                        getString(R.string.ev_light_change, (stats.diffRatio * 100).roundToInt()),
                    )
                }
            }
            handleMotion(motionResult, cfg, now)
        }

        val quiet = cfg.isQuietAt(wallNow)
        WakeLuxState.update {
            it.copy(
                avgLuma = stats.avgLuma,
                diffPixels = stats.diffPixels,
                diffPercent = stats.diffRatio * 100f,
                targetBrightness = brightnessUpdate?.target ?: it.targetBrightness,
                smoothedBrightness = brightnessUpdate?.smoothed ?: it.smoothedBrightness,
                appliedBrightness = brightnessUpdate?.applied ?: it.appliedBrightness,
                brightnessWriteFailed = brightnessUpdate?.writeFailed ?: it.brightnessWriteFailed,
                motion = motionResult?.motion ?: false,
                motionNearby = motionResult?.nearby ?: false,
                motionVerdict = motionResult?.verdict ?: MotionDetector.Verdict.NONE,
                zoneDiffPercent = (motionResult?.wakeRatio ?: 0f) * 100f,
                lightChanges = motion.lightChangeCount,
                frameCount = frameOffset + camera.frameCount,
                cameraRestarts = restartOffset + camera.restartCount,
                previewWidth = width,
                previewHeight = height,
                lastFrameAt = now,
                quietActive = quiet,
                screenOn = waker.screenOn,
                wakeCount = waker.wakeCount,
                lastWakeAt = waker.lastWakeAt,
                proximityAvailable = proximity.available,
                proximityNear = proximity.near,
            )
        }

        if (preview != null) {
            WakeLuxState.publishPreview(WakeLuxState.PreviewFrame(width, height, preview, now))
        }

        if (now - lastSampleAt >= SAMPLE_PERIOD_MS) {
            lastSampleAt = now
            Diag.addSample(
                Diag.Sample(
                    ts = wallNow,
                    luma = stats.avgLuma,
                    brightness = WakeLuxState.live.value.appliedBrightness,
                    diffPercent = stats.diffRatio * 100f,
                    motion = motionResult?.motion == true,
                ),
                cfg.diagHistoryMin,
                cfg.diagFileLog,
            )
        }

        ha.maybePublish(WakeLuxState.live.value, cfg, now)

        if (now - lastNotificationAt >= NOTIFICATION_PERIOD_MS) {
            lastNotificationAt = now
            updateNotification(cfg)
        }
    }

    override fun onCameraState(state: CameraController.State, message: String?) {
        WakeLuxState.update { it.copy(cameraState = state, cameraMessage = message) }
        if (state == CameraController.State.RESTARTING || state == CameraController.State.ERROR) {
            processor.reset()
            motion.reset()
        }
        updateNotification(prefs.current)
    }

    override fun onEvent(message: String) = Diag.event(message)

    private fun handleMotion(result: MotionDetector.Result, cfg: Config, now: Long) {
        if (result.motion) lastMotionAt = now
        val screenOn = waker.screenOn
        if (!screenOn) {
            if (!result.nearby) return
            if (waker.wake(cfg)) {
                Diag.event(getString(R.string.ev_wake_motion))
            } else {
                Diag.event(getString(R.string.ev_wake_quiet))
            }
        } else if (result.motion && cfg.keepAwakeOnMotion) {
            waker.wake(cfg, countAsWake = false)
        }
        publishWakeState()
    }

    private fun onProximityNear() {
        val cfg = prefs.current
        if (!cfg.proximityWake) return
        lastMotionAt = SystemClock.elapsedRealtime()
        if (waker.screenOn) return
        if (waker.wake(cfg)) Diag.event(getString(R.string.ev_wake_proximity))
        publishWakeState()
    }

    private fun applyProximity(cfg: Config) {
        if (cfg.proximityWake && proximity.available) {
            proximity.start()
        } else {
            proximity.stop()
            if (cfg.proximityWake && !proximity.available) {
                Diag.event(getString(R.string.ev_proximity_missing))
            }
        }
        WakeLuxState.update {
            it.copy(proximityAvailable = proximity.available, proximityNear = proximity.near)
        }
    }

    private fun recreateCamera(reason: String) {
        Diag.event(getString(R.string.ev_camera_recreate, reason))
        frameOffset += camera.frameCount
        restartOffset += camera.restartCount + 1
        camera.abandon()
        processor.reset()
        motion.reset()
        camera = CameraController(this, this, prefs.current)
        camera.start()
    }

    private fun screenOffResultText(result: ScreenWaker.ScreenOffResult): String = getString(
        when (result) {
            ScreenWaker.ScreenOffResult.DONE -> R.string.ev_screen_off_done
            ScreenWaker.ScreenOffResult.ALREADY_OFF -> R.string.ev_screen_off_already
            ScreenWaker.ScreenOffResult.NO_ROOT -> R.string.ev_screen_off_no_root
            ScreenWaker.ScreenOffResult.DISABLED -> R.string.ev_screen_off_disabled
            ScreenWaker.ScreenOffResult.FAILED -> R.string.ev_screen_off_failed
        },
    )

    private fun publishWakeState() {
        WakeLuxState.update {
            it.copy(wakeCount = waker.wakeCount, lastWakeAt = waker.lastWakeAt, screenOn = waker.screenOn)
        }
    }
    private val houseKeeping = object : Runnable {
        override fun run() {
            val cfg = prefs.current
            val now = SystemClock.elapsedRealtime()
            WakeLuxState.update { it.copy(screenOn = waker.screenOn) }

            val stuckFor = now - camera.stateChangedAt
            if (camera.state != CameraController.State.RUNNING && stuckFor > CAMERA_STUCK_TIMEOUT_MS) {
                recreateCamera(
                    getString(R.string.ev_camera_stuck, camera.state.name, stuckFor / 1000),
                )
            }

            if (cfg.autoBrightness) {
                scope.launch(Dispatchers.IO) {
                    SettingsWriter.ensureManualBrightnessMode(this@KioskSensorService, cfg)
                }
            }

            if (cfg.rootEnabled && cfg.rootForceScreenOff && lastMotionAt > 0) {
                val idle = now - lastMotionAt
                if (idle > cfg.rootForceScreenOffDelayMs && waker.screenOn) {
                    scope.launch(Dispatchers.IO) {
                        val result = waker.forceScreenOff(cfg)
                        if (result == ScreenWaker.ScreenOffResult.DONE) {
                            Diag.event(getString(R.string.ev_screen_off_root))
                        }
                    }
                }
            }
            mainHandler.postDelayed(this, HOUSEKEEPING_PERIOD_MS)
        }
    }

    private fun observeConfig() {
        scope.launch {
            var previous = prefs.current
            prefs.config.collect { cfg ->
                camera.updateConfig(cfg)
                if (cfg.screenOffTimeoutMs != previous.screenOffTimeoutMs ||
                    cfg.manageScreenOffTimeout != previous.manageScreenOffTimeout
                ) {
                    applySystemSettings(cfg)
                }
                if (cfg.autoBrightness && !previous.autoBrightness) brightness.reset()
                if (!cfg.motionEnabled) motion.reset()
                if (cfg.haEnabled && !previous.haEnabled) ha.publish(WakeLuxState.live.value, cfg)
                if (cfg.proximityWake != previous.proximityWake) applyProximity(cfg)
                previous = cfg
                updateNotification(cfg)
            }
        }
    }

    private fun applySystemSettings(cfg: Config) {
        scope.launch(Dispatchers.IO) {
            if (cfg.manageScreenOffTimeout) {
                val ok = waker.applyScreenOffTimeout(cfg)
                if (!ok) Diag.event(getString(R.string.ev_timeout_failed))
            }
            if (cfg.autoBrightness && !SettingsWriter.canWriteSystem(this@KioskSensorService)) {
                Diag.event(getString(R.string.ev_no_write_settings))
            }
        }
    }

    private fun autoGrantWithRoot() {
        val pkg = packageName
        if (!RootShell.isAvailable()) return
        var granted = false
        if (!SettingsWriter.canWriteSystem(this)) {
            granted = RootShell.grantWriteSettings(pkg) || granted
        }
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            granted = RootShell.grantPermission(pkg, android.Manifest.permission.CAMERA) || granted
        }
        if (granted) Diag.event(getString(R.string.ev_root_granted))
    }

    private fun obtainPreviewBuffer(size: Int): IntArray {
        val existing = previewPixels
        if (existing != null && existing.size == size) return existing
        return IntArray(size).also { previewPixels = it }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            flags,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, KioskSensorService::class.java).setAction(ACTION_STOP),
            flags,
        )
        val wake = PendingIntent.getService(
            this,
            2,
            Intent(this, KioskSensorService::class.java).setAction(ACTION_WAKE),
            flags,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_wakelux)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_action_wake), wake)
            .addAction(0, getString(R.string.notif_action_stop), stop)
            .build()
    }

    private fun updateNotification(cfg: Config) {
        val live = WakeLuxState.live.value
        val parts = mutableListOf<String>()
        parts += getString(R.string.notif_room, live.avgLuma)
        if (cfg.autoBrightness) parts += getString(R.string.notif_screen, live.appliedBrightness)
        if (cfg.motionEnabled) {
            parts += getString(R.string.notif_change, "%.1f".format(live.diffPercent))
            parts += getString(if (live.motion) R.string.notif_motion else R.string.notif_still)
        }
        if (live.cameraState != CameraController.State.RUNNING) {
            parts += getString(R.string.notif_camera, cameraStateLabel(live.cameraState))
        }
        if (live.quietActive) parts += getString(R.string.notif_quiet)
        val notification = buildNotification(parts.joinToString(" · "))
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun cameraStateLabel(state: CameraController.State): String = getString(
        when (state) {
            CameraController.State.IDLE -> R.string.cam_idle
            CameraController.State.OPENING -> R.string.cam_opening
            CameraController.State.RUNNING -> R.string.cam_running
            CameraController.State.ERROR -> R.string.cam_error
            CameraController.State.RESTARTING -> R.string.cam_restarting
        },
    )

    private fun startForegroundSafely(notification: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { Log.w(TAG, "startForeground failed", it) }
    }

    companion object {
        private const val TAG = "WakeLux/Service"
        private const val CHANNEL_ID = "wakelux"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_PERIOD_MS = 5_000L
        private const val HOUSEKEEPING_PERIOD_MS = 5_000L
        private const val SAMPLE_PERIOD_MS = 1_000L
        private const val LIGHT_CHANGE_LOG_PERIOD_MS = 10_000L
        private const val CAMERA_STUCK_TIMEOUT_MS = 90_000L

        const val ACTION_STOP = "com.vasmarfas.wakelux.action.STOP"
        const val ACTION_WAKE = "com.vasmarfas.wakelux.action.WAKE"
        const val ACTION_SCREEN_OFF = "com.vasmarfas.wakelux.action.SCREEN_OFF"
        const val ACTION_RESTART_CAMERA = "com.vasmarfas.wakelux.action.RESTART_CAMERA"
        const val ACTION_HA_TEST = "com.vasmarfas.wakelux.action.HA_TEST"

        fun start(context: Context) {
            val intent = Intent(context, KioskSensorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, KioskSensorService::class.java).setAction(ACTION_STOP),
            )
        }

        fun command(context: Context, action: String) {
            context.startService(
                Intent(context, KioskSensorService::class.java).setAction(action),
            )
        }
    }
}
