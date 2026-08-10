package com.vasmarfas.wakelux

import com.vasmarfas.wakelux.brightness.BrightnessCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BrightnessCurveTest {

    private val config = Config()

    @Test
    fun `luma below the minimum gives minimum brightness`() {
        assertEquals(config.brightnessMin, BrightnessCurve.rawMap(0, config))
        assertEquals(config.brightnessMin, BrightnessCurve.rawMap(config.lumaMin, config))
    }

    @Test
    fun `luma above the maximum gives maximum brightness`() {
        assertEquals(config.brightnessMax, BrightnessCurve.rawMap(255, config))
        assertEquals(config.brightnessMax, BrightnessCurve.rawMap(config.lumaMax, config))
    }

    @Test
    fun `midpoint is interpolated linearly`() {
        val middleLuma = (config.lumaMin + config.lumaMax) / 2
        // 10 + (255 - 10) * 0.5 = 132.5, округляется вверх
        assertEquals(133, BrightnessCurve.rawMap(middleLuma, config))
    }

    @Test
    fun `mapping is monotonic`() {
        var previous = -1
        for (luma in 0..255) {
            val value = BrightnessCurve.rawMap(luma, config)
            assertTrue("luma=$luma", value >= previous)
            previous = value
        }
    }

    @Test
    fun `custom curve overrides the simple mapping`() {
        val custom = config.copy(
            curveEnabled = true,
            curvePoints = listOf(
                CurvePoint(0, 5),
                CurvePoint(50, 100),
                CurvePoint(255, 255),
            ),
        )
        assertEquals(5, BrightnessCurve.rawMap(0, custom))
        assertEquals(100, BrightnessCurve.rawMap(50, custom))
        assertEquals(53, BrightnessCurve.rawMap(25, custom))
    }

    @Test
    fun `custom curve is ignored when it has less than two points`() {
        val custom = config.copy(curveEnabled = true, curvePoints = listOf(CurvePoint(10, 200)))
        assertEquals(config.brightnessMin, BrightnessCurve.rawMap(config.lumaMin, custom))
    }

    @Test
    fun `unsorted curve points still interpolate correctly`() {
        val points = listOf(CurvePoint(200, 250), CurvePoint(0, 0), CurvePoint(100, 100))
        assertEquals(100, BrightnessCurve.interpolate(100, points))
        assertEquals(50, BrightnessCurve.interpolate(50, points))
    }

    @Test
    fun `quiet hours cap the brightness`() {
        val quiet = config.copy(
            quietEnabled = true,
            quietStartMin = 0,
            quietEndMin = 24 * 60 - 1,
            quietBrightnessMax = 30,
        )
        val noon = calendarAt(12, 0)
        assertEquals(30, BrightnessCurve.map(255, quiet, noon))
    }

    @Test
    fun `quiet hours over midnight are detected`() {
        val quiet = config.copy(quietEnabled = true, quietStartMin = 23 * 60, quietEndMin = 7 * 60)
        assertTrue(quiet.isQuietAt(calendarAt(23, 30)))
        assertTrue(quiet.isQuietAt(calendarAt(3, 0)))
        assertTrue(quiet.isQuietAt(calendarAt(6, 59)))
        assertTrue(!quiet.isQuietAt(calendarAt(7, 0)))
        assertTrue(!quiet.isQuietAt(calendarAt(12, 0)))
    }

    @Test
    fun `quiet hours are inactive when disabled`() {
        val quiet = config.copy(quietEnabled = false, quietStartMin = 0, quietEndMin = 1439)
        assertTrue(!quiet.isQuietAt(calendarAt(2, 0)))
        assertEquals(config.brightnessMax, quiet.effectiveBrightnessMax(calendarAt(2, 0)))
    }

    @Test
    fun `brightness never drops to zero`() {
        val dark = config.copy(brightnessMin = 0)
        assertTrue(BrightnessCurve.map(0, dark, calendarAt(12, 0)) >= 1)
    }

    private fun calendarAt(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
    }.timeInMillis
}
