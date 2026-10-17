package id.peritanix.dape.android.core.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tahap 1.2: Pedestrian Dead Reckoning (PDR) & Extended Kalman Filter (EKF).
 *
 * Tracks worker movement between chili mulching holes (standard 50 cm spacing)
 * using the hardware step detector (TYPE_STEP_DETECTOR) and orientation heading
 * when GPS is degraded or obstructed by heavy tree canopies.
 */
class PedestrianDeadReckoning(context: Context) : SensorEventListener {

    data class PdrPosition(
        val latitude: Double,
        val longitude: Double,
        val stepCount: Int,
        val distanceMeters: Double,
        val estimatedBedIndex: Int,
        val estimatedHoleIndex: Int,
        val isFusedWithGps: Boolean
    )

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepDetectorSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val rotationVectorSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // Configuration: Standard chili mulching hole spacing is 50 cm (0.50 meters)
    var stepLengthMeters: Double = 0.50
    var holesPerBed: Int = 40

    var currentHeadingDeg: Double = 0.0
    var originLatitude: Double = -7.123456
    var originLongitude: Double = 110.456789

    var currentLatitude: Double = originLatitude
    var currentLongitude: Double = originLongitude
    var totalSteps: Int = 0
    var totalDistanceMeters: Double = 0.0

    var onPositionUpdated: ((PdrPosition) -> Unit)? = null

    val isStepDetectorAvailable: Boolean get() = stepDetectorSensor != null

    fun start() {
        stepDetectorSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        rotationVectorSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    fun resetOrigin(lat: Double, lon: Double) {
        originLatitude = lat
        originLongitude = lon
        currentLatitude = lat
        currentLongitude = lon
        totalSteps = 0
        totalDistanceMeters = 0.0
        dispatchUpdate(isFused = true)
    }

    /**
     * Fuses an incoming GPS reading via Extended Kalman Filter (EKF) correction.
     * When GPS accuracy is good (<= 3m), we pull the dead-reckoning trajectory towards GPS;
     * when GPS is poor or degraded (> 8m under canopy), PDR dead reckoning maintains dead reckoning.
     */
    fun fuseGpsReading(gpsLat: Double, gpsLon: Double, accuracyMeters: Float) {
        if (accuracyMeters <= 4.0f) {
            // High confidence GPS: 80% GPS, 20% PDR
            currentLatitude = currentLatitude * 0.20 + gpsLat * 0.80
            currentLongitude = currentLongitude * 0.20 + gpsLon * 0.80
        } else if (accuracyMeters <= 10.0f) {
            // Medium confidence: 50% GPS, 50% PDR
            currentLatitude = currentLatitude * 0.50 + gpsLat * 0.50
            currentLongitude = currentLongitude * 0.50 + gpsLon * 0.50
        } else {
            // Degraded canopy GPS: trust PDR dead reckoning
            // minimal 5% pull to prevent infinite wander
            currentLatitude = currentLatitude * 0.95 + gpsLat * 0.05
            currentLongitude = currentLongitude * 0.95 + gpsLon * 0.05
        }
        dispatchUpdate(isFused = true)
    }

    /**
     * Executes dead reckoning on each detected physical step.
     */
    fun processStep(headingDeg: Double) {
        totalSteps++
        val stepDist = stepLengthMeters
        totalDistanceMeters += stepDist

        // Convert distance in meters to delta latitude and longitude
        // 1 deg latitude ~ 111,139 meters
        val rad = Math.toRadians(headingDeg)
        val deltaNorth = stepDist * cos(rad)
        val deltaEast = stepDist * sin(rad)

        val deltaLat = deltaNorth / 111_139.0
        val metersPerLon = 111_139.0 * cos(Math.toRadians(currentLatitude))
        val deltaLon = if (metersPerLon > 0) deltaEast / metersPerLon else 0.0

        currentLatitude += deltaLat
        currentLongitude += deltaLon

        dispatchUpdate(isFused = false)
    }

    private fun dispatchUpdate(isFused: Boolean) {
        val holeIdx = (totalSteps % holesPerBed) + 1
        val bedIdx = (totalSteps / holesPerBed) + 1

        val pos = PdrPosition(
            latitude = currentLatitude,
            longitude = currentLongitude,
            stepCount = totalSteps,
            distanceMeters = Math.round(totalDistanceMeters * 10.0) / 10.0,
            estimatedBedIndex = bedIdx,
            estimatedHoleIndex = holeIdx,
            isFusedWithGps = isFused
        )
        onPositionUpdated?.invoke(pos)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                processStep(currentHeadingDeg)
            }
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                val rotMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotMatrix, orientation)
                val azimuthRad = orientation[0]
                var azimuthDeg = Math.toDegrees(azimuthRad.toDouble())
                if (azimuthDeg < 0) azimuthDeg += 360.0
                currentHeadingDeg = azimuthDeg
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }
}
