package id.peritanix.dape.android.core.hardware

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.roundToInt

/**
 * High-precision Compass and Sensor Calibration Manager.
 *
 * Coordinates:
 * 1. Rotation Vector sensor for tilt-compensated 360° heading.
 * 2. Magnetometer for magnetic field magnitude, accuracy, and hard-iron figure-8 calibration.
 * 3. Accelerometer & Clinometer for spirit-level bubble tilt measurement and zero-tilt calibration.
 * 4. Hardware sensor diagnostics listing all sensors detected on the device.
 */
class CompassSensorManager(private val context: Context) : SensorEventListener {

    data class DeviceSensorInfo(
        val name: String,
        val typeName: String,
        val vendor: String,
        val version: Int,
        val powerMa: Float,
        val maxRange: Float,
        val resolution: Float
    )

    data class CompassState(
        val rawAzimuth: Float = 0f,
        val smoothedAzimuth: Float = 0f,
        val rawPitch: Double = 0.0,
        val rawRoll: Double = 0.0,
        val calibratedPitch: Double = 0.0,
        val calibratedRoll: Double = 0.0,
        val isLevel: Boolean = false,
        val magneticMagnitude: Double = 45.0,
        val magneticStatus: SensorCalibrationMath.MagneticStatus = SensorCalibrationMath.MagneticStatus.NORMAL,
        val accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
        val isCalibrating: Boolean = false,
        val calibrationProgressPct: Int = 0,
        val hardIronOffsets: SensorCalibrationMath.HardIronOffsets = SensorCalibrationMath.HardIronOffsets(),
        val tiltOffset: SensorCalibrationMath.TiltOffset = SensorCalibrationMath.TiltOffset()
    )

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val prefs: SharedPreferences = context.getSharedPreferences("pentasix_sensor_calibration", Context.MODE_PRIVATE)

    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var hardIronOffsets: SensorCalibrationMath.HardIronOffsets
    private var tiltOffset: SensorCalibrationMath.TiltOffset

    // Calibration working state
    private var isFigure8Calibrating = false
    private var sampleCount = 0
    private val requiredSamples = 80 // ~4-5 seconds of continuous figure-8 sweeps
    private var minMagX = Float.MAX_VALUE
    private var maxMagX = -Float.MAX_VALUE
    private var minMagY = Float.MAX_VALUE
    private var maxMagY = -Float.MAX_VALUE
    private var minMagZ = Float.MAX_VALUE
    private var maxMagZ = -Float.MAX_VALUE

    // Live state
    private var currentSmoothedAzimuth = 0f
    private var latestState = CompassState()
    var onStateChanged: ((CompassState) -> Unit)? = null

    // Fallback matrices when TYPE_ROTATION_VECTOR is unavailable
    private val gravityValues = FloatArray(3)
    private val geomagneticValues = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    init {
        // Load saved offsets from preferences
        val ox = prefs.getFloat("hard_iron_x", 0f)
        val oy = prefs.getFloat("hard_iron_y", 0f)
        val oz = prefs.getFloat("hard_iron_z", 0f)
        hardIronOffsets = SensorCalibrationMath.HardIronOffsets(ox, oy, oz)

        val tp = prefs.getFloat("tilt_pitch", 0f).toDouble()
        val tr = prefs.getFloat("tilt_roll", 0f).toDouble()
        tiltOffset = SensorCalibrationMath.TiltOffset(tp, tr)

        latestState = latestState.copy(
            hardIronOffsets = hardIronOffsets,
            tiltOffset = tiltOffset
        )
    }

    fun startListening() {
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        }
        if (magneticSensor != null) {
            sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI)
        }
        if (accelerometerSensor != null) {
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    fun startFigure8Calibration() {
        isFigure8Calibrating = true
        sampleCount = 0
        minMagX = Float.MAX_VALUE
        maxMagX = -Float.MAX_VALUE
        minMagY = Float.MAX_VALUE
        maxMagY = -Float.MAX_VALUE
        minMagZ = Float.MAX_VALUE
        maxMagZ = -Float.MAX_VALUE

        latestState = latestState.copy(
            isCalibrating = true,
            calibrationProgressPct = 0
        )
        onStateChanged?.invoke(latestState)
    }

    fun cancelFigure8Calibration() {
        isFigure8Calibrating = false
        latestState = latestState.copy(
            isCalibrating = false,
            calibrationProgressPct = 0
        )
        onStateChanged?.invoke(latestState)
    }

    fun calibrateZeroTilt(): SensorCalibrationMath.TiltOffset {
        val newOffset = SensorCalibrationMath.TiltOffset(
            pitchZeroOffset = latestState.rawPitch,
            rollZeroOffset = latestState.rawRoll
        )
        tiltOffset = newOffset

        prefs.edit()
            .putFloat("tilt_pitch", newOffset.pitchZeroOffset.toFloat())
            .putFloat("tilt_roll", newOffset.rollZeroOffset.toFloat())
            .apply()

        val (calPitch, calRoll) = SensorCalibrationMath.calibrateTilt(
            latestState.rawPitch,
            latestState.rawRoll,
            newOffset
        )

        latestState = latestState.copy(
            tiltOffset = newOffset,
            calibratedPitch = calPitch,
            calibratedRoll = calRoll,
            isLevel = SensorCalibrationMath.isDeviceLevel(calPitch, calRoll)
        )
        onStateChanged?.invoke(latestState)
        return newOffset
    }

    fun resetTiltCalibration() {
        tiltOffset = SensorCalibrationMath.TiltOffset(0.0, 0.0)
        prefs.edit()
            .putFloat("tilt_pitch", 0f)
            .putFloat("tilt_roll", 0f)
            .apply()

        val (calPitch, calRoll) = SensorCalibrationMath.calibrateTilt(
            latestState.rawPitch,
            latestState.rawRoll,
            tiltOffset
        )

        latestState = latestState.copy(
            tiltOffset = tiltOffset,
            calibratedPitch = calPitch,
            calibratedRoll = calRoll,
            isLevel = SensorCalibrationMath.isDeviceLevel(calPitch, calRoll)
        )
        onStateChanged?.invoke(latestState)
    }

    fun resetFigure8Calibration() {
        hardIronOffsets = SensorCalibrationMath.HardIronOffsets(0f, 0f, 0f)
        prefs.edit()
            .putFloat("hard_iron_x", 0f)
            .putFloat("hard_iron_y", 0f)
            .putFloat("hard_iron_z", 0f)
            .apply()

        latestState = latestState.copy(hardIronOffsets = hardIronOffsets)
        onStateChanged?.invoke(latestState)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                val orientationValues = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientationValues)

                // Azimuth (rad -> deg 0..360)
                val targetAzimuth = ((Math.toDegrees(orientationValues[0].toDouble()) + 360.0) % 360.0).toFloat()
                currentSmoothedAzimuth = SensorCalibrationMath.smoothAzimuth(currentSmoothedAzimuth, targetAzimuth, 0.25f)

                // Pitch & Roll in degrees
                val rawPitch = Math.toDegrees(orientationValues[1].toDouble())
                val rawRoll = Math.toDegrees(orientationValues[2].toDouble())

                val (calPitch, calRoll) = SensorCalibrationMath.calibrateTilt(rawPitch, rawRoll, tiltOffset)
                val isLevel = SensorCalibrationMath.isDeviceLevel(calPitch, calRoll)

                latestState = latestState.copy(
                    rawAzimuth = targetAzimuth,
                    smoothedAzimuth = currentSmoothedAzimuth,
                    rawPitch = rawPitch,
                    rawRoll = rawRoll,
                    calibratedPitch = calPitch,
                    calibratedRoll = calRoll,
                    isLevel = isLevel
                )
                onStateChanged?.invoke(latestState)
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                val rawX = event.values[0]
                val rawY = event.values[1]
                val rawZ = event.values[2]

                // Magnetometer figure-8 collection
                if (isFigure8Calibrating) {
                    sampleCount++
                    if (rawX < minMagX) minMagX = rawX
                    if (rawX > maxMagX) maxMagX = rawX
                    if (rawY < minMagY) minMagY = rawY
                    if (rawY > maxMagY) maxMagY = rawY
                    if (rawZ < minMagZ) minMagZ = rawZ
                    if (rawZ > maxMagZ) maxMagZ = rawZ

                    val progress = ((sampleCount.toFloat() / requiredSamples) * 100f).coerceIn(0f, 100f).roundToInt()

                    if (sampleCount >= requiredSamples) {
                        // Calibration sweep complete
                        isFigure8Calibrating = false
                        hardIronOffsets = SensorCalibrationMath.calculateHardIronOffsets(
                            minMagX, maxMagX, minMagY, maxMagY, minMagZ, maxMagZ
                        )
                        prefs.edit()
                            .putFloat("hard_iron_x", hardIronOffsets.offsetX)
                            .putFloat("hard_iron_y", hardIronOffsets.offsetY)
                            .putFloat("hard_iron_z", hardIronOffsets.offsetZ)
                            .apply()

                        latestState = latestState.copy(
                            isCalibrating = false,
                            calibrationProgressPct = 100,
                            hardIronOffsets = hardIronOffsets
                        )
                    } else {
                        latestState = latestState.copy(
                            isCalibrating = true,
                            calibrationProgressPct = progress
                        )
                    }
                }

                // Apply hard-iron offset to magnetic readings
                val corrX = rawX - hardIronOffsets.offsetX
                val corrY = rawY - hardIronOffsets.offsetY
                val corrZ = rawZ - hardIronOffsets.offsetZ

                val mag = SensorCalibrationMath.calculateMagneticMagnitude(corrX, corrY, corrZ)
                val status = SensorCalibrationMath.evaluateMagneticStatus(mag)

                geomagneticValues[0] = corrX
                geomagneticValues[1] = corrY
                geomagneticValues[2] = corrZ
                hasGeomagnetic = true

                // Fallback computation if TYPE_ROTATION_VECTOR is not present
                if (rotationVectorSensor == null && hasGravity && hasGeomagnetic) {
                    computeFallbackOrientation()
                }

                latestState = latestState.copy(
                    magneticMagnitude = Math.round(mag * 10.0) / 10.0,
                    magneticStatus = status,
                    accuracy = event.accuracy
                )
                onStateChanged?.invoke(latestState)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravityValues, 0, 3)
                hasGravity = true

                if (rotationVectorSensor == null && hasGravity && hasGeomagnetic) {
                    computeFallbackOrientation()
                }
            }
        }
    }

    private fun computeFallbackOrientation() {
        val r = FloatArray(9)
        val i = FloatArray(9)
        if (SensorManager.getRotationMatrix(r, i, gravityValues, geomagneticValues)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(r, orientation)

            val targetAzimuth = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
            currentSmoothedAzimuth = SensorCalibrationMath.smoothAzimuth(currentSmoothedAzimuth, targetAzimuth, 0.25f)

            val rawPitch = Math.toDegrees(orientation[1].toDouble())
            val rawRoll = Math.toDegrees(orientation[2].toDouble())

            val (calPitch, calRoll) = SensorCalibrationMath.calibrateTilt(rawPitch, rawRoll, tiltOffset)
            val isLevel = SensorCalibrationMath.isDeviceLevel(calPitch, calRoll)

            latestState = latestState.copy(
                rawAzimuth = targetAzimuth,
                smoothedAzimuth = currentSmoothedAzimuth,
                rawPitch = rawPitch,
                rawRoll = rawRoll,
                calibratedPitch = calPitch,
                calibratedRoll = calRoll,
                isLevel = isLevel
            )
            onStateChanged?.invoke(latestState)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            latestState = latestState.copy(accuracy = accuracy)
            onStateChanged?.invoke(latestState)
        }
    }

    /**
     * Diagnostic scanner: Retrieves all available hardware sensors on the user's smartphone.
     */
    fun getAvailableSensors(): List<DeviceSensorInfo> {
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        return allSensors.map { sensor ->
            val typeStr = when (sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> "Accelerometer (Percepatan)"
                Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer (Geomagnetik)"
                Sensor.TYPE_GYROSCOPE -> "Gyroscope (Rotasi Sudut)"
                Sensor.TYPE_ROTATION_VECTOR -> "Rotation Vector (Orientasi 9-Axis)"
                Sensor.TYPE_LIGHT -> "Ambient Light (Intensitas Cahaya)"
                Sensor.TYPE_PRESSURE -> "Barometer (Tekanan Udara)"
                Sensor.TYPE_PROXIMITY -> "Proximity (Jarak Dekat)"
                Sensor.TYPE_GRAVITY -> "Gravity (Vektor Gravitasi)"
                Sensor.TYPE_LINEAR_ACCELERATION -> "Linear Acceleration (G-Force)"
                else -> "Sensor Tipe ${sensor.type}"
            }
            DeviceSensorInfo(
                name = sensor.name,
                typeName = typeStr,
                vendor = sensor.vendor,
                version = sensor.version,
                powerMa = sensor.power,
                maxRange = sensor.maximumRange,
                resolution = sensor.resolution
            )
        }.sortedBy { it.typeName }
    }
}
