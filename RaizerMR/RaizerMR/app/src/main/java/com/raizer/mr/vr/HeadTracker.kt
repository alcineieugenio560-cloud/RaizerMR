package com.raizer.mr.vr

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface

/**
 * Orientação da cabeça via vetor de rotação do sistema (giroscópio fusionado).
 * Entrega uma matriz 3x3 (row-major) dispositivo->mundo já ajustada para landscape.
 */
class HeadTracker(private val activity: Activity) : SensorEventListener {
    private val sm = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val available: Boolean get() = sensor != null
    val sensorName: String get() = sensor?.name ?: ""

    private val raw = FloatArray(9)
    private val remapped = FloatArray(9)
    private val shared = FloatArray(9)
    private val lock = Any()
    private var has = false

    fun start() {
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
    }

    fun stop() {
        sm.unregisterListener(this)
    }

    @Suppress("DEPRECATION")
    override fun onSensorChanged(e: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(raw, e.values)
        val rot = activity.windowManager.defaultDisplay.rotation
        val ax: Int
        val ay: Int
        when (rot) {
            Surface.ROTATION_90 -> { ax = SensorManager.AXIS_Y; ay = SensorManager.AXIS_MINUS_X }
            Surface.ROTATION_270 -> { ax = SensorManager.AXIS_MINUS_Y; ay = SensorManager.AXIS_X }
            Surface.ROTATION_180 -> { ax = SensorManager.AXIS_MINUS_X; ay = SensorManager.AXIS_MINUS_Y }
            else -> { ax = SensorManager.AXIS_X; ay = SensorManager.AXIS_Y }
        }
        SensorManager.remapCoordinateSystem(raw, ax, ay, remapped)
        synchronized(lock) {
            System.arraycopy(remapped, 0, shared, 0, 9)
            has = true
        }
    }

    fun copyTo(out: FloatArray): Boolean {
        synchronized(lock) {
            if (!has) return false
            System.arraycopy(shared, 0, out, 0, 9)
            return true
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
