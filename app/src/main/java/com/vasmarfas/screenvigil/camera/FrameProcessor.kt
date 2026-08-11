package com.vasmarfas.screenvigil.camera

import kotlin.math.abs

data class FrameStats(
    val avgLuma: Int,
    val diffPixels: Int,
    val comparedPixels: Int,
    val hadPrevious: Boolean,
    val zoneDiffPixels: Int = 0,
    val zonePixels: Int = 0,
) {
    val diffRatio: Float get() = if (comparedPixels > 0) diffPixels.toFloat() / comparedPixels else 0f

    val zoneDiffRatio: Float
        get() = if (zonePixels > 0) zoneDiffPixels.toFloat() / zonePixels else diffRatio
}

class FrameProcessor {

    private var prev: ByteArray? = null
    private var prevSize = 0
    private var hasPrev = false

    var stride = 1
        private set

    fun reset() {
        hasPrev = false
    }

    fun process(
        data: ByteArray,
        width: Int,
        height: Int,
        pixelThreshold: Int,
        zoneFraction: Float = 0f,
        preview: IntArray? = null,
    ): FrameStats {
        val lumaSize = width * height
        if (lumaSize <= 0 || data.size < lumaSize) {
            return FrameStats(0, 0, 0, false)
        }
        if (prevSize != lumaSize) {
            prev = ByteArray(lumaSize)
            prevSize = lumaSize
            hasPrev = false
        }
        stride = when {
            lumaSize <= 1 shl 16 -> 1
            else -> lumaSize / (1 shl 16)
        }

        val useZone = zoneFraction > 0f && zoneFraction < 1f
        val zoneWidth = if (useZone) (width * zoneFraction).toInt().coerceAtLeast(2) else 0
        val zoneHeight = if (useZone) (height * zoneFraction).toInt().coerceAtLeast(2) else 0
        val zoneLeft = (width - zoneWidth) / 2
        val zoneTop = (height - zoneHeight) / 2
        val zoneRight = zoneLeft + zoneWidth
        val zoneBottom = zoneTop + zoneHeight

        val previous = prev!!
        val compare = hasPrev
        val step = stride
        val drawPreview = preview != null && preview.size >= lumaSize
        var sum = 0L
        var counted = 0
        var diff = 0
        var zoneCounted = 0
        var zoneDiff = 0

        var i = 0
        while (i < lumaSize) {
            val v = data[i].toInt() and 0xFF
            sum += v
            counted++
            var changed = false
            if (compare) {
                if (abs(v - (previous[i].toInt() and 0xFF)) > pixelThreshold) {
                    diff++
                    changed = true
                }
            }
            previous[i] = data[i]

            var inZone = false
            if (useZone || drawPreview) {
                val y = i / width
                val x = i - y * width
                if (useZone && x >= zoneLeft && x < zoneRight && y >= zoneTop && y < zoneBottom) {
                    inZone = true
                    zoneCounted++
                    if (changed) zoneDiff++
                }
                if (drawPreview) {
                    val onZoneBorder = useZone && inZone &&
                        (x == zoneLeft || x == zoneRight - 1 || y == zoneTop || y == zoneBottom - 1)
                    preview[i] = when {
                        onZoneBorder -> 0xFF00C853.toInt()
                        changed -> 0xFF000000.toInt() or (0xFF shl 16) or (v shr 2 shl 8) or (v shr 2)
                        else -> 0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
                    }
                }
            }
            i += step
        }

        if (drawPreview && step > 1) {
            var j = 0
            var last = 0xFF000000.toInt()
            while (j < lumaSize) {
                if (j % step == 0) last = preview[j] else preview[j] = last
                j++
            }
        }

        hasPrev = true
        val avg = if (counted > 0) (sum / counted).toInt() else 0
        return FrameStats(
            avgLuma = avg,
            diffPixels = diff,
            comparedPixels = if (compare) counted else 0,
            hadPrevious = compare,
            zoneDiffPixels = if (compare) zoneDiff else 0,
            zonePixels = if (compare) zoneCounted else 0,
        )
    }
}
