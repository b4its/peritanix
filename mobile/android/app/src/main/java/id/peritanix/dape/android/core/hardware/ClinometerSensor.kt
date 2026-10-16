package id.peritanix.dape.android.core.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * FR-1.3 Clinometer Sensor Manager.
 *
 * Streams high-precision pitch and roll from [Sensor.TYPE_ROTATION_VECTOR]
 * when the smartphone is placed on a wooden plank along the bedengan.
 * Provides slope angle (beta) with 0.1 degree precision for TWI computation.
 */
class ClinometerSensor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    var onSlopeChanged: ((slopeDeg: Double, slopeRad: Double) -> Unit)? = null
    var latestSlopeDeg: Double = 0.0
        private set

    fun startListening() {
        rotationVectorSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        val orientationValues = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientationValues)

        // Pitch is rotation around X-axis (index 1), Roll is around Y-axis (index 2)
        val pitchRad = orientationValues[1].toDouble()
        val rollRad = orientationValues[2].toDouble()

        // Total bed slope magnitude relative to the horizontal plane
        val totalSlopeRad = sqrt(pitchRad * pitchRad + rollRad * rollRad)
        val totalSlopeDeg = Math.toDegrees(totalSlopeRad)

        // Precision round to 0.1 degrees
        latestSlopeDeg = (Math.round(totalSlopeDeg * 10.0) / 10.0)
        onSlopeChanged?.invoke(latestSlopeDeg, totalSlopeRad)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for rotation vector
    }
}
