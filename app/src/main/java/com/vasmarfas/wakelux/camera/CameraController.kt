package com.vasmarfas.wakelux.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.vasmarfas.wakelux.Config
import com.vasmarfas.wakelux.Prefs
import com.vasmarfas.wakelux.R

@Suppress("DEPRECATION")
class CameraController(
    private val context: Context,
    private val callbacks: Callbacks,
    initialConfig: Config,
) {

    enum class State { IDLE, OPENING, RUNNING, ERROR, RESTARTING }

    interface Callbacks {
        fun onFrame(data: ByteArray, width: Int, height: Int)
        fun onCameraState(state: State, message: String?)
        fun onEvent(message: String)
    }

    private val thread = HandlerThread("wakelux-camera").apply { start() }
    private val handler = Handler(thread.looper)

    private var camera: Camera? = null
    private var surfaceTexture: SurfaceTexture? = null

    @Volatile
    private var config: Config = initialConfig

    @Volatile
    var state: State = State.IDLE
        private set

    @Volatile
    var previewWidth = 0
        private set

    @Volatile
    var previewHeight = 0
        private set

    @Volatile
    var lastFrameAt = 0L
        private set

    @Volatile
    var frameCount = 0L
        private set

    @Volatile
    var restartCount = 0
        private set

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    var stateChangedAt = SystemClock.elapsedRealtime()
        private set

    private var lastProcessedAt = 0L
    private var openAttempts = 0

    @Volatile
    private var running = false

    fun start() {
        handler.post {
            running = true
            openAttempts = 0
            openCamera()
            scheduleWatchdog()
        }
    }

    fun stop() {
        handler.post {
            running = false
            handler.removeCallbacks(watchdog)
            releaseCamera()
            setState(State.IDLE, null)
        }
    }

    fun updateConfig(newConfig: Config) {
        val old = config
        config = newConfig
        if (old.cameraFacing != newConfig.cameraFacing ||
            old.aeLock != newConfig.aeLock ||
            old.exposureCompensation != newConfig.exposureCompensation
        ) {
            restart(context.getString(R.string.ev_camera_settings_changed))
        }
    }

    fun restart(reason: String) {
        handler.post {
            if (!running) return@post
            restartCount++
            callbacks.onEvent(context.getString(R.string.ev_camera_restart, reason))
            setState(State.RESTARTING, reason)
            releaseCamera()
            openCamera()
        }
    }

    fun release() {
        handler.post {
            running = false
            handler.removeCallbacks(watchdog)
            releaseCamera()
        }
        handler.post { thread.quitSafely() }
    }

    fun abandon() {
        running = false
        handler.post {
            releaseCamera()
            thread.quitSafely()
        }
    }

    private fun setState(newState: State, message: String?) {
        state = newState
        stateChangedAt = SystemClock.elapsedRealtime()
        if (newState == State.ERROR) lastError = message
        callbacks.onCameraState(newState, message)
    }

    private fun openCamera() {
        if (!running) return
        setState(State.OPENING, null)
        val cfg = config
        val id = findCameraId(cfg.cameraFacing)
        if (id < 0) {
            setState(State.ERROR, context.getString(R.string.ev_camera_not_found))
            scheduleRetry()
            return
        }
        try {
            val cam = Camera.open(id)
            camera = cam
            applyParameters(cam, cfg)
            val texture = SurfaceTexture(TEXTURE_NAME)
            surfaceTexture = texture
            cam.setPreviewTexture(texture)
            cam.setErrorCallback { error, _ ->
                callbacks.onEvent(context.getString(R.string.ev_camera_hal_error, error))
                restart("HAL error $error")
            }
            val frameSize = previewWidth * previewHeight * 3 / 2
            repeat(BUFFER_COUNT) { cam.addCallbackBuffer(ByteArray(frameSize)) }
            cam.setPreviewCallbackWithBuffer(previewCallback)
            cam.startPreview()
            lastFrameAt = SystemClock.elapsedRealtime()
            openAttempts = 0
            setState(State.RUNNING, "${previewWidth}x$previewHeight")
            callbacks.onEvent(
                context.getString(R.string.ev_camera_opened, id, previewWidth, previewHeight),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "open failed", t)
            releaseCamera()
            setState(State.ERROR, t.message ?: t.javaClass.simpleName)
            scheduleRetry()
        }
    }

    private fun applyParameters(cam: Camera, cfg: Config) {
        val params = cam.parameters
        val sizes = params.supportedPreviewSizes
        val size = sizes?.minByOrNull { it.width * it.height }
        if (size != null) {
            params.setPreviewSize(size.width, size.height)
            previewWidth = size.width
            previewHeight = size.height
        } else {
            previewWidth = params.previewSize.width
            previewHeight = params.previewSize.height
        }
        if (params.supportedPreviewFormats?.contains(ImageFormat.NV21) != false) {
            params.previewFormat = ImageFormat.NV21
        }
        params.supportedPreviewFpsRange
            ?.minWithOrNull(compareBy({ it[1] }, { it[0] }))
            ?.let { runCatching { params.setPreviewFpsRange(it[0], it[1]) } }
        params.supportedFocusModes?.let { modes ->
            when {
                modes.contains(Camera.Parameters.FOCUS_MODE_FIXED) ->
                    params.focusMode = Camera.Parameters.FOCUS_MODE_FIXED

                modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY) ->
                    params.focusMode = Camera.Parameters.FOCUS_MODE_INFINITY
            }
        }
        params.supportedFlashModes?.let { modes ->
            if (modes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                params.flashMode = Camera.Parameters.FLASH_MODE_OFF
            }
        }
        if (params.maxExposureCompensation != params.minExposureCompensation) {
            params.exposureCompensation = cfg.exposureCompensation
                .coerceIn(params.minExposureCompensation, params.maxExposureCompensation)
        }
        params.setRecordingHint(false)
        cam.parameters = params
        val fpsRange = IntArray(2).also { runCatching { params.getPreviewFpsRange(it) } }
        callbacks.onEvent(
            context.getString(
                R.string.ev_camera_supports,
                sizes?.sortedBy { it.width * it.height }?.take(6)
                    ?.joinToString(",") { "${it.width}x${it.height}" }.orEmpty(),
                params.supportedPreviewFpsRange
                    ?.joinToString(",") { "${it[0] / 1000}-${it[1] / 1000}" }.orEmpty(),
            ),
        )
        callbacks.onEvent(
            context.getString(
                R.string.ev_camera_params,
                previewWidth,
                previewHeight,
                "${fpsRange[0] / 1000f}..${fpsRange[1] / 1000f}",
                params.previewFormat,
                params.focusMode.orEmpty(),
                params.exposureCompensation,
                params.isAutoExposureLockSupported,
            ),
        )
        if (cfg.aeLock && params.isAutoExposureLockSupported) {
            runCatching {
                val locked = cam.parameters
                locked.autoExposureLock = true
                cam.parameters = locked
            }
        }
    }

    private val previewCallback = Camera.PreviewCallback { data, cam ->
        if (data == null) return@PreviewCallback
        val now = SystemClock.elapsedRealtime()
        lastFrameAt = now
        val interval = config.frameIntervalMs.coerceIn(100, 60_000)
        if (now - lastProcessedAt < interval) {
            cam?.addCallbackBuffer(data)
            return@PreviewCallback
        }
        lastProcessedAt = now
        frameCount++
        try {
            callbacks.onFrame(data, previewWidth, previewHeight)
        } catch (t: Throwable) {
            Log.w(TAG, "frame processing failed", t)
        } finally {
            cam?.addCallbackBuffer(data)
        }
    }

    private val watchdog = object : Runnable {
        override fun run() {
            if (!running) return
            val timeout = config.watchdogTimeoutMs.coerceIn(5_000, 300_000)
            val silence = SystemClock.elapsedRealtime() - lastFrameAt
            if (state == State.RUNNING && silence > timeout) {
                callbacks.onEvent(context.getString(R.string.ev_camera_watchdog, silence / 1000))
                restartCount++
                setState(State.RESTARTING, "watchdog")
                releaseCamera()
                openCamera()
            }
            scheduleWatchdog()
        }
    }

    private fun scheduleWatchdog() {
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, WATCHDOG_PERIOD_MS)
    }

    private fun scheduleRetry() {
        if (!running) return
        openAttempts++
        val delay = when {
            openAttempts <= 2 -> 5_000L
            openAttempts <= 5 -> 15_000L
            else -> 30_000L
        }
        callbacks.onEvent(context.getString(R.string.ev_camera_retry, delay / 1000))
        handler.postDelayed({ if (running && camera == null) openCamera() }, delay)
    }

    private fun releaseCamera() {
        val cam = camera ?: run {
            surfaceTexture?.release()
            surfaceTexture = null
            return
        }
        camera = null
        runCatching { cam.setPreviewCallbackWithBuffer(null) }
        runCatching { cam.setErrorCallback(null) }
        runCatching { cam.stopPreview() }
        runCatching { cam.release() }
        runCatching { surfaceTexture?.release() }
        surfaceTexture = null
    }

    private fun findCameraId(facing: Int): Int {
        val target = if (facing == Prefs.FACING_BACK) {
            Camera.CameraInfo.CAMERA_FACING_BACK
        } else {
            Camera.CameraInfo.CAMERA_FACING_FRONT
        }
        val count = runCatching { Camera.getNumberOfCameras() }.getOrDefault(0)
        val info = Camera.CameraInfo()
        for (id in 0 until count) {
            runCatching {
                Camera.getCameraInfo(id, info)
                if (info.facing == target) return id
            }
        }
        return if (count > 0) 0 else -1
    }

    private companion object {
        const val TAG = "WakeLux/Camera"
        const val TEXTURE_NAME = 10
        const val BUFFER_COUNT = 3
        const val WATCHDOG_PERIOD_MS = 5_000L
    }
}
