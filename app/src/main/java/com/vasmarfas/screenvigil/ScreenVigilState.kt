package com.vasmarfas.screenvigil

import com.vasmarfas.screenvigil.camera.CameraController
import com.vasmarfas.screenvigil.kiosk.KioskKeeper
import com.vasmarfas.screenvigil.motion.MotionDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update


object ScreenVigilState {

    data class Live(
        val serviceRunning: Boolean = false,
        val startedAt: Long = 0L,
        val startedElapsed: Long = 0L,
        val cameraState: CameraController.State = CameraController.State.IDLE,
        val cameraMessage: String? = null,
        val previewWidth: Int = 0,
        val previewHeight: Int = 0,
        val avgLuma: Int = 0,
        val targetBrightness: Int = 0,
        val smoothedBrightness: Float = 0f,
        val appliedBrightness: Int = 0,
        val brightnessWriteFailed: Boolean = false,
        val diffPixels: Int = 0,
        val diffPercent: Float = 0f,
        val motion: Boolean = false,
        val motionNearby: Boolean = false,
        val zoneDiffPercent: Float = 0f,
        val proximityAvailable: Boolean = false,
        val proximityNear: Boolean = false,
        val motionVerdict: MotionDetector.Verdict = MotionDetector.Verdict.NO_REFERENCE,
        val lightChanges: Int = 0,
        val frameCount: Long = 0L,
        val cameraRestarts: Int = 0,
        val wakeCount: Int = 0,
        val lastWakeAt: Long = 0L,
        val screenOn: Boolean = true,
        val quietActive: Boolean = false,
        val lastFrameAt: Long = 0L,
        val haStatus: String = "",
        val haStatusAt: Long = 0L,
        val rootAvailable: Boolean = false,
        val kioskState: KioskKeeper.State = KioskKeeper.State.OFF,
        val kioskLaunches: Int = 0,
        val kioskLastLaunchAt: Long = 0L,
        val powerOffReason: String = "",
    )

    class PreviewFrame(val width: Int, val height: Int, val pixels: IntArray, val stamp: Long)

    private val _live = MutableStateFlow(Live())
    val live: StateFlow<Live> = _live

    private val _preview = MutableStateFlow<PreviewFrame?>(null)
    val preview: StateFlow<PreviewFrame?> = _preview

    @Volatile
    var uiVisible: Boolean = false

    fun update(block: (Live) -> Live) {
        _live.update(block)
    }

    fun publishPreview(frame: PreviewFrame?) {
        _preview.value = frame
    }

    fun reset() {
        _live.value = Live()
        _preview.value = null
    }
}
