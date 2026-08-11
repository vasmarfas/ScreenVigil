package com.vasmarfas.screenvigil

import com.vasmarfas.screenvigil.camera.FrameStats
import com.vasmarfas.screenvigil.motion.MotionDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionDetectorTest {

    private val config = Config()
    private val total = 176 * 144

    private fun stats(ratio: Float, luma: Int = 100, zoneRatio: Float = -1f): FrameStats {
        val zonePixels = total / 4
        val effectiveZone = if (zoneRatio < 0f) ratio else zoneRatio
        return FrameStats(
            avgLuma = luma,
            diffPixels = (total * ratio).toInt(),
            comparedPixels = total,
            hadPrevious = true,
            zoneDiffPixels = (zonePixels * effectiveZone).toInt(),
            zonePixels = zonePixels,
        )
    }

    @Test
    fun `single frame above threshold is not enough with two confirming frames`() {
        val detector = MotionDetector()
        val first = detector.update(stats(0.05f), config)
        assertEquals(MotionDetector.Verdict.PENDING, first.verdict)
        assertFalse(first.motion)
    }

    @Test
    fun `two frames in a row confirm motion`() {
        val detector = MotionDetector()
        detector.update(stats(0.05f), config)
        val second = detector.update(stats(0.05f), config)
        assertTrue(second.motion)
        assertEquals(MotionDetector.Verdict.MOTION, second.verdict)
    }

    @Test
    fun `quiet frame between two active frames resets the counter`() {
        val detector = MotionDetector()
        detector.update(stats(0.05f), config)
        detector.update(stats(0.001f), config)
        val third = detector.update(stats(0.05f), config)
        assertEquals(MotionDetector.Verdict.PENDING, third.verdict)
    }

    @Test
    fun `room light switch is filtered instead of waking the screen`() {
        val detector = MotionDetector()
        val result = detector.update(stats(0.95f), config)
        assertEquals(MotionDetector.Verdict.LIGHT_CHANGE, result.verdict)
        assertFalse(result.motion)
        assertEquals(1, detector.lightChangeCount)
    }

    @Test
    fun `light change does not accumulate towards motion confirmation`() {
        val detector = MotionDetector()
        detector.update(stats(0.6f), config)
        detector.update(stats(0.6f), config)
        val third = detector.update(stats(0.05f), config)
        assertEquals(MotionDetector.Verdict.PENDING, third.verdict)
    }

    @Test
    fun `sensor noise in the dark is suppressed`() {
        val detector = MotionDetector()
        val result = detector.update(stats(0.2f, luma = 1), config)
        assertEquals(MotionDetector.Verdict.TOO_DARK, result.verdict)
        assertFalse(result.motion)
    }

    @Test
    fun `first frame without reference never reports motion`() {
        val detector = MotionDetector()
        val result = detector.update(
            FrameStats(avgLuma = 100, diffPixels = total, comparedPixels = 0, hadPrevious = false),
            config,
        )
        assertEquals(MotionDetector.Verdict.NO_REFERENCE, result.verdict)
    }

    @Test
    fun `single confirming frame setting reacts immediately`() {
        val detector = MotionDetector()
        val instant = config.copy(confirmFrames = 1)
        assertTrue(detector.update(stats(0.05f), instant).motion)
    }

    @Test
    fun `continuous motion keeps reporting motion`() {
        val detector = MotionDetector()
        detector.update(stats(0.05f), config)
        repeat(5) { assertTrue(detector.update(stats(0.05f), config).motion) }
    }

    @Test
    fun `ratio exactly at the threshold does not trigger`() {
        val detector = MotionDetector()
        val exact = FrameStats(
            avgLuma = 100,
            diffPixels = (total * config.motionRatio).toInt(),
            comparedPixels = total,
            hadPrevious = true,
        )
        detector.update(exact, config)
        val second = detector.update(exact, config)
        assertFalse(second.motion)
    }

    @Test
    fun `small movement across the room does not wake the screen`() {
        val detector = MotionDetector()
        // 5 % кадра: присутствие есть, порог пробуждения (12 %) не достигнут
        detector.update(stats(0.05f), config)
        val second = detector.update(stats(0.05f), config)
        assertTrue("присутствие засчитано", second.motion)
        assertFalse("экран будить нельзя", second.nearby)
    }

    @Test
    fun `someone close by wakes the screen`() {
        val detector = MotionDetector()
        detector.update(stats(0.3f), config)
        val second = detector.update(stats(0.3f), config)
        assertTrue(second.nearby)
    }

    @Test
    fun `wake needs its own confirmation frames`() {
        val detector = MotionDetector()
        assertFalse(detector.update(stats(0.3f), config).nearby)
        assertTrue(detector.update(stats(0.3f), config).nearby)
    }

    @Test
    fun `wake zone ignores movement outside the centre`() {
        val zoned = config.copy(wakeZoneEnabled = true)
        val detector = MotionDetector()
        // в кадре целиком много изменений, в центре — ничего
        detector.update(stats(0.4f, zoneRatio = 0f), zoned)
        val second = detector.update(stats(0.4f, zoneRatio = 0f), zoned)
        assertTrue("присутствие всё равно видно", second.motion)
        assertFalse("но это не рядом с планшетом", second.nearby)
    }

    @Test
    fun `wake zone reacts to movement in the centre`() {
        val zoned = config.copy(wakeZoneEnabled = true)
        val detector = MotionDetector()
        detector.update(stats(0.05f, zoneRatio = 0.4f), zoned)
        assertTrue(detector.update(stats(0.05f, zoneRatio = 0.4f), zoned).nearby)
    }

    @Test
    fun `light change never counts as someone close by`() {
        val detector = MotionDetector()
        detector.update(stats(0.9f), config)
        val second = detector.update(stats(0.9f), config)
        assertEquals(MotionDetector.Verdict.LIGHT_CHANGE, second.verdict)
        assertFalse(second.nearby)
    }

    @Test
    fun `darkness never counts as someone close by`() {
        val detector = MotionDetector()
        detector.update(stats(0.3f, luma = 1), config)
        assertFalse(detector.update(stats(0.3f, luma = 1), config).nearby)
    }

    @Test
    fun `lowering the wake threshold makes any motion wake the screen`() {
        val eager = config.copy(wakeMotionRatio = 0.01f)
        val detector = MotionDetector()
        detector.update(stats(0.05f), eager)
        assertTrue(detector.update(stats(0.05f), eager).nearby)
    }

    @Test
    fun `reset clears pending confirmation`() {
        val detector = MotionDetector()
        detector.update(stats(0.05f), config)
        detector.reset()
        assertEquals(MotionDetector.Verdict.PENDING, detector.update(stats(0.05f), config).verdict)
    }
}
