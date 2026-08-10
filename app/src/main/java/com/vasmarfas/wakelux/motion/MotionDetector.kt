package com.vasmarfas.wakelux.motion

import com.vasmarfas.wakelux.Config
import com.vasmarfas.wakelux.camera.FrameStats

class MotionDetector {

    enum class Verdict { NONE, PENDING, MOTION, LIGHT_CHANGE, TOO_DARK, NO_REFERENCE }

    data class Result(
        val verdict: Verdict,
        val nearby: Boolean,
        val confirmedFrames: Int,
        val nearFrames: Int,
        val diffRatio: Float,
        val wakeRatio: Float,
    ) {
        val motion: Boolean get() = verdict == Verdict.MOTION
    }

    private var consecutive = 0
    private var consecutiveNear = 0
    private var lightChanges = 0

    val lightChangeCount: Int get() = lightChanges

    fun reset() {
        consecutive = 0
        consecutiveNear = 0
    }

    fun update(stats: FrameStats, config: Config): Result {
        val ratio = stats.diffRatio
        val wakeRatio = if (config.wakeZoneEnabled) stats.zoneDiffRatio else ratio

        if (!stats.hadPrevious) {
            reset()
            return Result(Verdict.NO_REFERENCE, false, 0, 0, 0f, 0f)
        }
        if (stats.avgLuma < config.darkCutoffLuma) {
            reset()
            return Result(Verdict.TOO_DARK, false, 0, 0, ratio, wakeRatio)
        }
        // включили/выключили свет: меняется почти весь кадр, движением это не считаем
        if (ratio > config.lightChangeRatio) {
            reset()
            lightChanges++
            return Result(Verdict.LIGHT_CHANGE, false, 0, 0, ratio, wakeRatio)
        }

        consecutiveNear = if (wakeRatio > config.wakeMotionRatio) consecutiveNear + 1 else 0
        val nearby = consecutiveNear >= config.wakeConfirmFrames.coerceAtLeast(1)

        if (ratio > config.motionRatio) {
            consecutive++
            val needed = config.confirmFrames.coerceAtLeast(1)
            val verdict = if (consecutive >= needed) Verdict.MOTION else Verdict.PENDING
            return Result(verdict, nearby, consecutive, consecutiveNear, ratio, wakeRatio)
        }
        consecutive = 0
        return Result(Verdict.NONE, nearby, 0, consecutiveNear, ratio, wakeRatio)
    }
}
