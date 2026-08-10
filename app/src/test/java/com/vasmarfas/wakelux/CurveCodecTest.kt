package com.vasmarfas.wakelux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurveCodecTest {

    @Test
    fun `curve survives encode and decode`() {
        val points = listOf(CurvePoint(0, 10), CurvePoint(90, 120), CurvePoint(200, 255))
        assertEquals(points, Prefs.decodeCurve(Prefs.encodeCurve(points)))
    }

    @Test
    fun `decoded points are sorted by luma`() {
        val decoded = Prefs.decodeCurve("200:255,0:10,90:120")
        assertEquals(listOf(0, 90, 200), decoded.map { it.luma })
    }

    @Test
    fun `broken entries are skipped`() {
        val decoded = Prefs.decodeCurve("10:20,garbage,,30:x,40:50")
        assertEquals(listOf(CurvePoint(10, 20), CurvePoint(40, 50)), decoded)
    }

    @Test
    fun `values are clamped into the valid range`() {
        val decoded = Prefs.decodeCurve("-20:900,300:-5")
        assertTrue(decoded.all { it.luma in 0..255 && it.brightness in 0..255 })
    }

    @Test
    fun `empty input yields an empty curve`() {
        assertTrue(Prefs.decodeCurve(null).isEmpty())
        assertTrue(Prefs.decodeCurve("").isEmpty())
    }
}
