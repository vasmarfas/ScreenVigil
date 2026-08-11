package com.vasmarfas.screenvigil.power

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.min

class ProximityWatcher(
    context: Context,
    private val onBecameNear: () -> Unit,
) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = runCatching {
        manager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    }.getOrNull()

    val available: Boolean get() = sensor != null

    @Volatile
    var near: Boolean = false
        private set

    private var listening = false

    fun start() {
        val s = sensor ?: return
        if (listening) return
        listening = manager?.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL) == true
    }

    fun stop() {
        if (!listening) return
        manager?.unregisterListener(this)
        listening = false
        near = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val s = sensor ?: return
        val value = event.values.firstOrNull() ?: return
        val threshold = min(5f, s.maximumRange.takeIf { it > 0f } ?: 5f)
        val isNear = value < threshold
        if (isNear && !near) onBecameNear()
        near = isNear
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
