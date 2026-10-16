package id.peritanix.dape.android.core.hardware

import kotlin.math.abs
import kotlin.math.atan2

/**
 * FR-1.1 Water-Level Camera Tilt Overlay Controller.
 *
 * Evaluates real-time accelerometer readings to ensure the camera optical axis
 * is perpendicular (90° ± 5.0°) to the sedimentation bottle.
 * Shutter remains hard-locked unless the phone orientation is verified upright.
 */
object WaterLevelOverlayController {

    const val MAX_TOLERANCE_DEG = 5.0

    data class TiltStatus(
        val pitchDeg: Double,
        val rollDeg: Double,
        val isUpright: Boolean,
        val feedbackText: String
    )

    /**
     * Evaluates smartphone tilt from raw accelerometer gravity vectors (m/s²).
     * In a standard portrait upright hold:
     *   gravityY ≈ 9.8 m/s², gravityZ ≈ 0, gravityX ≈ 0.
     */
    fun evaluateTilt(gravityX: Float, gravityY: Float, gravityZ: Float): TiltStatus {
        val safeY = if (abs(gravityY) < 0.001f) 0.001 else gravityY.toDouble()

        val pitchRad = atan2(gravityZ.toDouble(), safeY)
        val rollRad = atan2(gravityX.toDouble(), safeY)

        val pitchDeg = Math.toDegrees(pitchRad)
        val rollDeg = Math.toDegrees(rollRad)

        val pitchValid = abs(pitchDeg) <= MAX_TOLERANCE_DEG
        val rollValid = abs(rollDeg) <= MAX_TOLERANCE_DEG
        val isUpright = pitchValid && rollValid

        val feedback = when {
            isUpright -> "POSISI TEGAK 90° IDEAL: Shutter kamera terbuka untuk capture sedimentasi."
            !pitchValid && pitchDeg > MAX_TOLERANCE_DEG -> "HP Terlalu Menunduk (+${"%.1f".format(pitchDeg)}°): Tegakkan HP sejajar botol."
            !pitchValid && pitchDeg < -MAX_TOLERANCE_DEG -> "HP Terlalu Menengadah (${"%.1f".format(pitchDeg)}°): Condongkan sedikit ke depan."
            !rollValid && rollDeg > MAX_TOLERANCE_DEG -> "HP Miring ke Kanan (+${"%.1f".format(rollDeg)}°): Luruskan posisi smartphone."
            else -> "HP Miring ke Kiri (${"%.1f".format(rollDeg)}°): Luruskan posisi smartphone."
        }

        return TiltStatus(
            pitchDeg = Math.round(pitchDeg * 10.0) / 10.0,
            rollDeg = Math.round(rollDeg * 10.0) / 10.0,
            isUpright = isUpright,
            feedbackText = feedback
        )
    }
}
