package id.peritanix.dape.android.core.hardware

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Tahap 1.1: Kalibrasi Figure-Eight Kompas & Zero-Point Sensor Inersia.
 *
 * Magnetic hard-iron offset estimation is only reliable once the user has swept
 * the phone through a full 3-D "figure-eight" so the magnetometer samples cover
 * every sphere octant. This pure-math module tracks that coverage and decides
 * when the calibration is good enough, plus derives tilt zero-points for
 * ROTATION_VECTOR / ACCELEROMETER from a flat-surface rest reading.
 *
 * All logic here is framework-free and unit-tested; the sensor plumbing that
 * feeds it (compass/game-rotation listeners) lives elsewhere.
 */
object CompassCalibration {

    /** One magnetometer sample (microtesla). */
    data class MagSample(val x: Float, val y: Float, val z: Float)

    data class FigureEightState(
        val samples: Int,
        val octantsCovered: Int,        // 0..8 signed-space octants visited
        val minRadius: Double,
        val maxRadius: Double,
        val spreadRatio: Double,        // min/max radius; near 1.0 = well distributed
        val isConverged: Boolean,
        val message: String
    )

    /**
     * The eight octants of 3-D space by sign of (x, y, z). A complete
     * figure-eight sweep visits all eight; we require all eight + a spread
     * ratio >= 0.55 to declare convergence.
     */
    private const val REQUIRED_OCTANTS = 8
    private const val MIN_SPREAD_RATIO = 0.55

    /**
     * Accumulates magnetometer samples during a figure-eight sweep and can be
     * queried for convergence. Use [addSample] per onSensorChanged then
     * [evaluate] to get the state.
     */
    class Accumulator {
        private val octants = BooleanArray(8)
        private var count = 0
        private var minMag = Double.MAX_VALUE
        private var maxMag = 0.0

        fun reset() {
            octants.fill(false); count = 0
            minMag = Double.MAX_VALUE; maxMag = 0.0
        }

        fun addSample(s: MagSample) {
            count++
            val idx = octantIndex(s.x, s.y, s.z)
            if (idx >= 0) octants[idx] = true
            val mag = sqrt(s.x.toDouble() * s.x + s.y.toDouble() * s.y + s.z.toDouble() * s.z)
            if (mag > 0.0) {
                if (mag < minMag) minMag = mag
                if (mag > maxMag) maxMag = mag
            }
        }

        fun evaluate(): FigureEightState = evaluateOctants(octants, count, minMag, maxMag)
    }

    /** Map a sample to one of 8 signed octants (0..7); -1 for a zero vector. */
    fun octantIndex(x: Float, y: Float, z: Float): Int {
        if (x == 0f && y == 0f && z == 0f) return -1
        var idx = 0
        if (x < 0f) idx = idx or 1
        if (y < 0f) idx = idx or 2
        if (z < 0f) idx = idx or 4
        return idx
    }

    /** Pure convergence evaluation from coverage bookkeeping. */
    fun evaluateOctants(
        octants: BooleanArray,
        sampleCount: Int,
        minRadius: Double,
        maxRadius: Double
    ): FigureEightState {
        val covered = octants.count { it }
        val spread = if (maxRadius > 0.0 && minRadius != Double.MAX_VALUE) minRadius / maxRadius else 0.0
        val converged = sampleCount >= 40 && covered >= REQUIRED_OCTANTS && spread >= MIN_SPREAD_RATIO
        val msg = when {
            converged -> "Kalibrasi kompas SELESAI: semua 8 oktan tercover, sebaran radius ${"%.2f".format(spread)}. Offset besi keras tersimpan."
            covered < REQUIRED_OCTANTS ->
                "Putar HP perlahan membentuk angka 8. Oktan tercover $covered/8 — terus gerakkan ke semua arah."
            else ->
                "Sebaran masih sempit ($covered/8 oktan). Perbesar gerakan angka 8 ke atas-bawah dan menyamping."
        }
        return FigureEightState(
            samples = sampleCount,
            octantsCovered = covered,
            minRadius = if (minRadius == Double.MAX_VALUE) 0.0 else Math.round(minRadius * 10.0) / 10.0,
            maxRadius = Math.round(maxRadius * 10.0) / 10.0,
            spreadRatio = Math.round(spread * 1000.0) / 1000.0,
            isConverged = converged,
            message = msg
        )
    }

    /**
     * Tilt zero-point from a flat-surface rest reading: the roll/pitch offsets to
     * subtract so a phone lying flat reports exactly 0° pitch and roll.
     */
    data class TiltZeroPoint(val pitchOffsetDeg: Double, val rollOffsetDeg: Double)

    fun computeTiltZeroPoint(pitchRestDeg: Double, rollRestDeg: Double): TiltZeroPoint =
        TiltZeroPoint(
            pitchOffsetDeg = Math.round(pitchRestDeg * 100.0) / 100.0,
            rollOffsetDeg = Math.round(rollRestDeg * 100.0) / 100.0
        )

    /** Whether the current tilt (after subtracting zero-point) is "flat enough". */
    fun isFlatAfterCorrection(
        pitchDeg: Double, rollDeg: Double, zp: TiltZeroPoint, toleranceDeg: Double = 1.5
    ): Boolean {
        val dp = abs(pitchDeg - zp.pitchOffsetDeg)
        val dr = abs(rollDeg - zp.rollOffsetDeg)
        return dp <= toleranceDeg && dr <= toleranceDeg
    }
}
