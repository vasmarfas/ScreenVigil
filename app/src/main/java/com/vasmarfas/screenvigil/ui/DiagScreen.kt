package com.vasmarfas.screenvigil.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vasmarfas.screenvigil.R
import com.vasmarfas.screenvigil.diag.Diag

@Composable
fun DiagScreen(onCopyLog: (String) -> Unit) {
    val version by Diag.version.collectAsState()
    val events by Diag.events.collectAsState()
    var windowMinutes by remember { mutableIntStateOf(30) }
    val samples = remember(version, windowMinutes) {
        val cutoff = System.currentTimeMillis() - windowMinutes * 60_000L
        Diag.snapshot().filter { windowMinutes <= 0 || it.ts >= cutoff }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        SectionCard(stringResource(R.string.tab_diag)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    5 to R.string.diag_window_5m,
                    30 to R.string.diag_window_30m,
                    180 to R.string.diag_window_3h,
                    0 to R.string.diag_window_all,
                ).forEach { (minutes, label) ->
                    FilterChip(
                        selected = windowMinutes == minutes,
                        onClick = { windowMinutes = minutes },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (samples.isEmpty()) {
                Hint(stringResource(R.string.diag_no_data))
            } else {
                HistoryChart(samples)
                val lumas = samples.map { it.luma }
                StatRow(
                    stringResource(R.string.diag_room_light),
                    stringResource(
                        R.string.diag_stats,
                        lumas.min(),
                        lumas.average().toInt(),
                        lumas.max(),
                    ),
                )
                val brightnesses = samples.map { it.brightness }
                StatRow(
                    stringResource(R.string.ov_brightness),
                    stringResource(
                        R.string.diag_stats,
                        brightnesses.min(),
                        brightnesses.average().toInt(),
                        brightnesses.max(),
                    ),
                )
                StatRow(
                    stringResource(R.string.diag_motion_count),
                    samples.count { it.motion }.toString(),
                )
                Hint(stringResource(R.string.diag_legend))
            }
            Diag.currentCsvPath()?.let { Hint(stringResource(R.string.diag_file_path, it)) }
        }

        SectionCard(stringResource(R.string.diag_events)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onCopyLog(events.joinToString("\n")) }) {
                    Text(stringResource(R.string.diag_share))
                }
                TextButton(onClick = { Diag.clear() }) {
                    Text(stringResource(R.string.diag_clear))
                }
            }
            events.take(80).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HistoryChart(samples: List<Diag.Sample>) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val lumaColor = Color(0xFFE8B923)
    val brightnessColor = MaterialTheme.colorScheme.primary
    val motionColor = MaterialTheme.colorScheme.error

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(vertical = 4.dp),
    ) {
        val w = size.width
        val h = size.height
        for (i in 0..4) {
            val y = h * i / 4f
            drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        if (samples.size < 2) return@Canvas
        val first = samples.first().ts
        val span = (samples.last().ts - first).coerceAtLeast(1L).toFloat()

        fun path(selector: (Diag.Sample) -> Int): Path {
            val p = Path()
            samples.forEachIndexed { index, sample ->
                val x = (sample.ts - first) / span * w
                val y = h - selector(sample).coerceIn(0, 255) / 255f * h
                if (index == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            return p
        }

        drawPath(path { it.luma }, lumaColor, style = Stroke(width = 3f))
        drawPath(path { it.brightness }, brightnessColor, style = Stroke(width = 3f))

        samples.forEach { sample ->
            if (sample.motion) {
                val x = (sample.ts - first) / span * w
                drawLine(motionColor, Offset(x, h), Offset(x, h * 0.88f), strokeWidth = 2f)
            }
        }
    }
}
