package com.vasmarfas.screenvigil.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vasmarfas.screenvigil.Config
import com.vasmarfas.screenvigil.CurvePoint
import com.vasmarfas.screenvigil.Prefs
import com.vasmarfas.screenvigil.R
import com.vasmarfas.screenvigil.ScreenVigilState
import com.vasmarfas.screenvigil.brightness.BrightnessCurve
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CurveScreen(config: Config, prefs: Prefs, live: ScreenVigilState.Live) {
    var points by remember {
        mutableStateOf(config.curvePoints.ifEmpty { BrightnessCurve.defaultPoints(config) })
    }
    var dragIndex by remember { mutableStateOf(-1) }

    LaunchedEffect(config.curvePoints, config.lumaMin, config.lumaMax) {
        if (config.curvePoints.isNotEmpty()) {
            points = config.curvePoints
        } else if (dragIndex < 0) {
            points = BrightnessCurve.defaultPoints(config)
        }
    }

    fun commit(newPoints: List<CurvePoint>) {
        val cleaned = newPoints.sortedBy { it.luma }
        points = cleaned
        prefs.putCurve(cleaned)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        SectionCard(stringResource(R.string.tab_curve)) {
            SwitchRow(
                label = stringResource(R.string.curve_enabled),
                checked = config.curveEnabled,
            ) { enabled ->
                if (enabled && config.curvePoints.isEmpty()) {
                    prefs.putCurve(BrightnessCurve.defaultPoints(config))
                }
                prefs.putBoolean(Prefs.KEY_CURVE_ENABLED, enabled)
            }
            Hint(
                if (config.curveEnabled) {
                    stringResource(R.string.curve_hint)
                } else {
                    stringResource(R.string.curve_simple_hint)
                },
            )

            val grid = MaterialTheme.colorScheme.outlineVariant
            val curveColor = MaterialTheme.colorScheme.primary
            val pointColor = MaterialTheme.colorScheme.tertiary
            val markerColor = MaterialTheme.colorScheme.error

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(vertical = 8.dp)
                    .pointerInput(config.curveEnabled) {
                        if (!config.curveEnabled) return@pointerInput
                        detectTapGestures(
                            onTap = { offset ->
                                val p = toCurvePoint(offset.x / size.width, offset.y / size.height)
                                commit(points + p)
                            },
                            onLongPress = { offset ->
                                val idx = nearestIndex(points, offset, size.width.toFloat(), size.height.toFloat())
                                if (idx >= 0 && points.size > 2) {
                                    commit(points.filterIndexed { i, _ -> i != idx })
                                }
                            },
                        )
                    }
                    .pointerInput(config.curveEnabled) {
                        if (!config.curveEnabled) return@pointerInput
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragIndex = nearestIndex(
                                    points,
                                    offset,
                                    size.width.toFloat(),
                                    size.height.toFloat(),
                                )
                            },
                            onDragEnd = {
                                dragIndex = -1
                                commit(points)
                            },
                            onDragCancel = { dragIndex = -1 },
                        ) { change, _ ->
                            val idx = dragIndex
                            if (idx in points.indices) {
                                val moved = toCurvePoint(
                                    change.position.x / size.width,
                                    change.position.y / size.height,
                                )
                                points = points.toMutableList().also { it[idx] = moved }
                            }
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                for (i in 0..4) {
                    val x = w * i / 4f
                    val y = h * i / 4f
                    drawLine(grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                    drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }

                val path = Path()
                var started = false
                for (luma in 0..255 step 2) {
                    val brightness = if (config.curveEnabled) {
                        BrightnessCurve.interpolate(luma, points)
                    } else {
                        BrightnessCurve.rawMap(luma, config)
                    }
                    val x = luma / 255f * w
                    val y = h - brightness / 255f * h
                    if (!started) {
                        path.moveTo(x, y)
                        started = true
                    } else {
                        path.lineTo(x, y)
                    }
                }
                drawPath(path, curveColor, style = Stroke(width = 4f))

                if (config.curveEnabled) {
                    points.forEach { p ->
                        drawCircle(
                            color = pointColor,
                            radius = 12f,
                            center = Offset(p.luma / 255f * w, h - p.brightness / 255f * h),
                        )
                    }
                }

                if (live.serviceRunning) {
                    val x = live.avgLuma / 255f * w
                    drawLine(markerColor, Offset(x, 0f), Offset(x, h), strokeWidth = 2f)
                    val y = h - live.appliedBrightness / 255f * h
                    drawLine(markerColor, Offset(0f, y), Offset(w, y), strokeWidth = 2f)
                }
            }

            Text(
                text = stringResource(
                    R.string.curve_current,
                    live.avgLuma,
                    live.appliedBrightness,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Unspecified,
            )

            if (config.curveEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { commit(points + CurvePoint(128, 128)) }) {
                        Text(stringResource(R.string.curve_add))
                    }
                    TextButton(onClick = { commit(BrightnessCurve.defaultPoints(config)) }) {
                        Text(stringResource(R.string.curve_reset))
                    }
                }
                points.forEach { p ->
                    StatRow(
                        stringResource(R.string.curve_point, p.luma),
                        stringResource(R.string.curve_point_value, p.brightness),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun toCurvePoint(xFraction: Float, yFraction: Float): CurvePoint {
    val luma = (xFraction.coerceIn(0f, 1f) * 255f).roundToInt()
    val brightness = ((1f - yFraction.coerceIn(0f, 1f)) * 255f).roundToInt().coerceIn(1, 255)
    return CurvePoint(luma, brightness)
}

private fun nearestIndex(
    points: List<CurvePoint>,
    offset: Offset,
    width: Float,
    height: Float,
): Int {
    var best = -1
    var bestDistance = Float.MAX_VALUE
    points.forEachIndexed { index, p ->
        val x = p.luma / 255f * width
        val y = height - p.brightness / 255f * height
        val distance = abs(x - offset.x) + abs(y - offset.y)
        if (distance < bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return if (bestDistance < width * 0.12f) best else -1
}
