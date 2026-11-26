package com.prabhu.wallpaperstudio.wallpaper

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.exp

/**
 * Turns gyroscope movement into a gentle tilt (-1..1 on each axis) that
 * drifts back to the centre, so the picture never gets stuck off to one side.
 */
class GyroTracker(context: Context, private val onMove: () -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private var registered = false
    private var rawX = 0f
    private var rawY = 0f
    private var lastTs = 0L

    /** Smoothed tilt, -1..1. */
    var x = 0f
        private set
    var y = 0f
        private set

    val running get() = registered

    fun start() {
        if (registered) return
        val sm = sensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return
        lastTs = 0L
        registered = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (registered) sensorManager?.unregisterListener(this)
        registered = false
        rawX = 0f
        rawY = 0f
        x = 0f
        y = 0f
    }

    fun recenter() {
        rawX = 0f
        rawY = 0f
    }

    /** Moves the smoothed value towards the sensor value. Returns true while still moving. */
    fun step(smoothness: Float): Boolean {
        val a = (0.35f - 0.3f * smoothness) * 1.6f
        val tx = if (registered) rawX / LIMIT else 0f
        val ty = if (registered) rawY / LIMIT else 0f
        x += (tx - x) * a
        y += (ty - y) * a
        return abs(tx - x) > 0.002f || abs(ty - y) > 0.002f
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (lastTs != 0L) {
            val dt = (event.timestamp - lastTs) / 1_000_000_000f
            if (dt > 0f && dt < 0.2f) {
                rawX += event.values[1] * dt
                rawY += event.values[0] * dt
                val decay = exp(-dt / 1.6f)
                rawX = (rawX * decay).coerceIn(-LIMIT, LIMIT)
                rawY = (rawY * decay).coerceIn(-LIMIT, LIMIT)
            }
        }
        lastTs = event.timestamp
        onMove()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val LIMIT = 0.45f
    }
}
