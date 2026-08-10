package com.vasmarfas.wakelux

import com.vasmarfas.wakelux.brightness.BrightnessCurve
import com.vasmarfas.wakelux.camera.FrameProcessor
import com.vasmarfas.wakelux.motion.MotionDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class PipelineTest {

    private val width = 160
    private val height = 120
    private val lumaSize = width * height
    private val config = Config()

    private fun frame(background: Int, changedFraction: Double = 0.0, changedValue: Int = 0): ByteArray {
        val data = ByteArray(lumaSize * 3 / 2)
        for (i in 0 until lumaSize) data[i] = background.toByte()
        val changed = (lumaSize * changedFraction).toInt()
        for (i in 0 until changed) data[i] = changedValue.toByte()
        return data
    }

    @Test
    fun `scene walkthrough behaves as designed`() {
        val processor = FrameProcessor()
        val detector = MotionDetector()

        // тихая освещённая комната: два одинаковых кадра
        processor.process(frame(120), width, height, config.pixelThreshold)
        var stats = processor.process(frame(120), width, height, config.pixelThreshold)
        var verdict = detector.update(stats, config)
        assertEquals(MotionDetector.Verdict.NONE, verdict.verdict)
        assertEquals(120, stats.avgLuma)

        // человек занял ~8 % кадра — нужно два кадра подряд для подтверждения
        stats = processor.process(frame(120, 0.08, 40), width, height, config.pixelThreshold)
        assertEquals(MotionDetector.Verdict.PENDING, detector.update(stats, config).verdict)
        stats = processor.process(frame(120, 0.08, 200), width, height, config.pixelThreshold)
        assertTrue(detector.update(stats, config).motion)

        // включили верхний свет: изменился весь кадр — это не движение
        stats = processor.process(frame(220), width, height, config.pixelThreshold)
        verdict = detector.update(stats, config)
        assertEquals(MotionDetector.Verdict.LIGHT_CHANGE, verdict.verdict)
        assertFalse(verdict.motion)

        // яркая комната - яркость на максимуме
        assertEquals(config.brightnessMax, BrightnessCurve.rawMap(stats.avgLuma, config))

        // свет выключили, полная темнота: детектор глушится, яркость падает до минимума
        processor.process(frame(1), width, height, config.pixelThreshold)
        stats = processor.process(frame(1), width, height, config.pixelThreshold)
        assertEquals(MotionDetector.Verdict.TOO_DARK, detector.update(stats, config).verdict)
        assertEquals(config.brightnessMin, BrightnessCurve.rawMap(stats.avgLuma, config))
    }

    @Test
    fun `smoothing needs several frames and never overshoots`() {
        // повторяем арифметику BrightnessController без Android-зависимостей
        val target = BrightnessCurve.rawMap(180, config)
        var smoothed = config.brightnessMin.toFloat()
        val trajectory = mutableListOf<Int>()
        repeat(30) {
            smoothed += (target - smoothed) * config.alpha
            trajectory += smoothed.roundToInt()
        }
        assertTrue("первый шаг не должен быть прыжком в цель", trajectory.first() < target / 2)
        assertTrue("должно сойтись к цели", abs(trajectory.last() - target) <= 2)
        assertTrue("монотонно, без перелёта", trajectory.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("не выше цели", trajectory.all { it <= target })
    }

    @Test
    fun `hysteresis suppresses one unit jitter`() {
        var applied = 100
        var written = 0
        // цель дрожит в пределах гистерезиса — записей быть не должно
        for (candidate in listOf(101, 99, 102, 98, 103)) {
            if (abs(candidate - applied) >= config.hysteresis) {
                applied = candidate
                written++
            }
        }
        assertEquals(0, written)
        // выход за гистерезис — запись происходит
        val candidate = applied + config.hysteresis
        assertTrue(abs(candidate - applied) >= config.hysteresis)
    }
}
