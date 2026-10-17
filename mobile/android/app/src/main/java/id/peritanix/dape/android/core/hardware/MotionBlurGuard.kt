package id.peritanix.dape.android.core.hardware

import kotlin.math.abs

/**
 * TAHAP 2.2 — Zero-Jitter Shutter & Pitch/Roll Angle Lock for the Stokes bottle
 * photograph.
 *
 * Two independent locks guarantee a razor-sharp, perfectly perpendicular photo of
 * the soil-sediment layers (which the OpenCV sediment-edge detector needs):
 *   1. Pitch/Roll Lock — the shutter is disabled unless the phone faces the
 *      bottle within 90° ± 0.5°.
 *   2. Zero-Jitter Shutter — even when aligned, the camera only fires once the
 *      recent gyro angular-velocity variance (σ_gyro) drops below a micro
 *      threshold, eliminating hand-shake motion blur.
 *
 * Pure math + small state buffer; fully unit-tested. The accelerometer/gyro
 * listeners that feed it live in the Android layer.
 */
object MotionBlurGuard {

    /** Allowed deviation from the target 90° angle (checklist: ±0.5°). */
    const val ANGLE_TOLERANCE_DEG = 0.5
    /** Target photo angle relative to the bottle. */
    const val TARGET_ANGLE_DEG = 90.0
    /** σ_gyro (rad/s) below which the hand is "still enough" to fire. */
    const val MICRO_JITTER_SIGMA = 0.05
    /** Samples of recent gyro magnitude used for the variance estimate. */
    const val JITTER_WINDOW = 8

    enum class LockState { ALIGNED_AND_STEADY, MISALIGNED, SHAKY }

    data class ShutterDecision(
        val canFire: Boolean,
        val state: LockState,
        val angleErrorDeg: Double,
        val gyroSigma: Double,
        val message: String
    )

    /** Off-axis tilt magnitude (°) from pitch/roll — 0 means perfectly perpendicular. */
    fun angleErrorDeg(pitchDeg: Double, rollDeg: Double): Double {
        val effective = Math.toDegrees(
            Math.sqrt(Math.toRadians(pitchDeg).let { it * it } + Math.toRadians(rollDeg).let { it * it })
        )
        return Math.round(abs(effective) * 10.0) / 10.0
    }

    /**
     * Given pitch/roll (degrees) and the recent gyro-magnitude buffer (rad/s),
     * decide whether the shutter may fire. `angleErrorDeg` here is the deviation
     * of the phone from the intended 90° face-on position, which the caller
     * computes as the magnitude of off-axis tilt (0 = perfectly perpendicular).
     */
    fun evaluate(
        offAxisTiltDeg: Double,
        recentGyroMagnitudes: List<Float>
    ): ShutterDecision {
        val aligned = abs(offAxisTiltDeg) <= ANGLE_TOLERANCE_DEG
        val sigma = gyroSigma(recentGyroMagnitudes)
        val steady = sigma <= MICRO_JITTER_SIGMA

        val state = when {
            !aligned -> LockState.MISALIGNED
            !steady -> LockState.SHAKY
            else -> LockState.ALIGNED_AND_STEADY
        }
        val msg = when (state) {
            LockState.MISALIGNED ->
                "Sejajarkan HP tegak lurus botol (${"%.1f".format(offAxisTiltDeg)}° meleset, toleransi ±${ANGLE_TOLERANCE_DEG}°)."
            LockState.SHAKY ->
                "Tahan HP diam sejenak (getaran tangan σ=${"%.3f".format(sigma)} rad/s, target < ${MICRO_JITTER_SIGMA})."
            LockState.ALIGNED_AND_STEADY ->
                "Terkunci sempurna & tajam. Rana siap ditembakkan otomatis."
        }
        return ShutterDecision(
            canFire = aligned && steady,
            state = state,
            angleErrorDeg = Math.round(abs(offAxisTiltDeg) * 10.0) / 10.0,
            gyroSigma = Math.round(sigma * 1000.0) / 1000.0,
            message = msg
        )
    }

    /** Population standard deviation of the recent gyro magnitudes. */
    fun gyroSigma(magnitudes: List<Float>): Double {
        if (magnitudes.size < 2) return Double.MAX_VALUE // not enough data → treat as shaky
        val window = magnitudes.takeLast(JITTER_WINDOW)
        val mean = window.average()
        val varSum = window.sumOf { v -> (v - mean) * (v - mean) }
        return Math.sqrt(varSum / window.size)
    }
}
