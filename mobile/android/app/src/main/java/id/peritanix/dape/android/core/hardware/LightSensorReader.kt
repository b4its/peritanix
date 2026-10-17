package id.peritanix.dape.android.core.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Reads the ambient light sensor (TYPE_LIGHT) — used by the onboarding spectral
 * calibration to verify the dark chamber is sealed (0 Lux) before the flash is
 * fired, per item 2.2 of the master spec.
 *
 * If the device has no light sensor, [latestLux] stays null and [isAvailable] is
 * false so the calibration UI can fall back to a "cannot verify" state instead
 * of pretending the chamber is dark.
 */
class LightSensorReader(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    var latestLux: Float? = null
        private set

    val isAvailable: Boolean get() = lightSensor != null

    fun start() {
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /** True when sealed below the dark-chamber threshold (<= 0.5 Lux). */
    fun isDarkEnough(): Boolean {
        val lux = latestLux ?: return false
        return DarkChamberFlashManager.isDarkChamberSealed(lux)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_LIGHT) return
        latestLux = event.values.firstOrNull()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }
}
