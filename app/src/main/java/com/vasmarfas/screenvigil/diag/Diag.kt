package com.vasmarfas.screenvigil.diag

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Diag {

    data class Sample(
        val ts: Long,
        val luma: Int,
        val brightness: Int,
        val diffPercent: Float,
        val motion: Boolean,
    )

    private const val TAG = "ScreenVigil"
    private const val MAX_EVENTS = 200
    private const val MAX_SAMPLES = 6 * 60 * 60
    private const val CSV_MAX_BYTES = 4L * 1024 * 1024
    private const val CSV_KEEP_FILES = 3

    private val samples = ArrayDeque<Sample>()
    private val lock = Any()

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)

    @Volatile
    var fileLoggingEnabled: Boolean = false

    private var csvDir: File? = null
    private var csvFile: File? = null
    private var csvBuffer = StringBuilder()
    private var csvDay: String? = null

    fun attach(context: Context) {
        if (csvDir != null) return
        csvDir = runCatching { context.getExternalFilesDir("logs") }.getOrNull()
            ?: context.filesDir.resolve("logs").also { it.mkdirs() }
    }

    fun addSample(sample: Sample, historyMinutes: Int, toFile: Boolean) {
        fileLoggingEnabled = toFile
        val limit = (historyMinutes.coerceIn(1, 360) * 60).coerceAtMost(MAX_SAMPLES)
        synchronized(lock) {
            samples.addLast(sample)
            while (samples.size > limit) samples.removeFirst()
        }
        _version.value += 1
        if (toFile) appendCsv(sample)
    }

    fun snapshot(): List<Sample> = synchronized(lock) { samples.toList() }

    fun clear() {
        synchronized(lock) { samples.clear() }
        _events.value = emptyList()
        _version.value += 1
    }

    fun event(message: String) {
        val line = "${timeFormat.format(Date())}  $message"
        Log.i(TAG, message)
        val list = _events.value.toMutableList()
        list.add(0, line)
        while (list.size > MAX_EVENTS) list.removeAt(list.size - 1)
        _events.value = list
        appendCsvEvent(line)
    }

    fun currentCsvPath(): String? = csvFile?.absolutePath

    private fun appendCsv(sample: Sample) = synchronized(lock) {
        val file = rollingFile() ?: return
        csvBuffer.append(sample.ts).append(',')
            .append(sample.luma).append(',')
            .append(sample.brightness).append(',')
            .append(String.format(Locale.US, "%.3f", sample.diffPercent)).append(',')
            .append(if (sample.motion) 1 else 0).append('\n')
        if (csvBuffer.length > 2048) flushCsv(file)
    }

    private fun appendCsvEvent(line: String) {
        if (!fileLoggingEnabled) return
        val file = rollingFile() ?: return
        synchronized(lock) {
            csvBuffer.append("# ").append(line).append('\n')
            flushCsv(file)
        }
    }

    private fun flushCsv(file: File) {
        if (csvBuffer.isEmpty()) return
        runCatching { file.appendText(csvBuffer.toString()) }
            .onFailure { Log.w(TAG, "csv write failed", it) }
        csvBuffer = StringBuilder()
    }

    private fun rollingFile(): File? {
        val dir = csvDir ?: return null
        if (!dir.exists()) dir.mkdirs()
        val today = dayFormat.format(Date())
        val existing = csvFile
        if (existing != null && csvDay == today && existing.length() < CSV_MAX_BYTES) return existing
        if (existing != null) flushCsv(existing)
        var index = 0
        var candidate: File
        do {
            candidate = File(dir, "screenvigil-$today${if (index == 0) "" else "-$index"}.csv")
            index++
        } while (candidate.exists() && candidate.length() >= CSV_MAX_BYTES)
        if (!candidate.exists()) {
            runCatching { candidate.writeText("ts,luma,brightness,diff_percent,motion\n") }
        }
        csvFile = candidate
        csvDay = today
        pruneOldFiles(dir)
        return candidate
    }

    private fun pruneOldFiles(dir: File) {
        val files = dir.listFiles { f -> f.name.startsWith("screenvigil-") && f.name.endsWith(".csv") }
            ?.sortedByDescending { it.lastModified() } ?: return
        files.drop(CSV_KEEP_FILES).forEach { runCatching { it.delete() } }
    }
}
