package org.openroadster.roadsterremote

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.asin
import kotlin.math.sqrt

/**
 * Reports the phone's pitch in radians: the angle of its long edge from
 * horizontal, the same value as iOS's CMAttitude.pitch. Held in landscape,
 * turning the phone like a steering wheel changes it.
 */
class TiltSensor(context: Context, private val onPitch: (Double) -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    // Gravity is the accelerometer with motion filtered out; fall back to the raw one
    private val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    fun beginUpdates() {
        sensor ?: return
        sensorManager?.registerListener(this, sensor, 100_000)
    }

    fun endUpdates() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        val g = sqrt(x * x + y * y + z * z)
        if (g == 0f) return
        // y points out the top of the phone; it reads +g when the phone is upright
        onPitch(asin((y / g).coerceIn(-1f, 1f)).toDouble())
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}
