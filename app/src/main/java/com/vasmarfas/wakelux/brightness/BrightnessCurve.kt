package com.vasmarfas.wakelux.brightness

import com.vasmarfas.wakelux.Config
import com.vasmarfas.wakelux.CurvePoint

/** Отображение средней яркости кадра (0..255) в системную яркость экрана (0..255). */
object BrightnessCurve {

    fun defaultPoints(config: Config): List<CurvePoint> = listOf(
        CurvePoint(config.lumaMin, config.brightnessMin),
        CurvePoint(config.lumaMax, config.brightnessMax),
    )

    /** Значение кривой без ограничений тихих часов. */
    fun rawMap(luma: Int, config: Config): Int {
        val points = if (config.curveEnabled && config.curvePoints.size >= 2) {
            config.curvePoints
        } else {
            defaultPoints(config)
        }
        return interpolate(luma, points)
    }

    /** Значение кривой с учётом ограничения яркости в тихие часы. */
    fun map(luma: Int, config: Config, timeMs: Long): Int {
        val cap = config.effectiveBrightnessMax(timeMs)
        return rawMap(luma, config).coerceAtMost(cap).coerceIn(1, 255)
    }

    fun interpolate(luma: Int, rawPoints: List<CurvePoint>): Int {
        if (rawPoints.isEmpty()) return 0
        val points = rawPoints.sortedBy { it.luma }
        if (points.size == 1) return points[0].brightness.coerceIn(0, 255)
        if (luma <= points.first().luma) return points.first().brightness.coerceIn(0, 255)
        if (luma >= points.last().luma) return points.last().brightness.coerceIn(0, 255)
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            if (luma in a.luma..b.luma) {
                val span = (b.luma - a.luma).toFloat()
                if (span <= 0f) return b.brightness.coerceIn(0, 255)
                val t = (luma - a.luma) / span
                val value = a.brightness + (b.brightness - a.brightness) * t
                return Math.round(value).coerceIn(0, 255)
            }
        }
        return points.last().brightness.coerceIn(0, 255)
    }
}
