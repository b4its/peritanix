package id.peritanix.dape.android.core.hardware

import kotlin.math.cos
import kotlin.math.sin

/**
 * Tahap 3.1: Penguncian Konteks Geospasial, Kompas Angin & PDR (SpatialContextCollector).
 *
 * Gathers multi-modal spatial context for pathology diagnostics:
 *   1. GPS + PDR dead-reckoning location (Bed #, Hole #).
 *   2. TWI (Topographic Wetness Index) at plant position.
 *   3. Magnetometer prevailing wind direction (vector propagation for Anthracnose & Whitefly/Begomovirus).
 *   4. Spore dispersion vector calculation.
 */
object SpatialContextCollector {

    data class DiagnosticSpatialContext(
        val latitude: Double,
        val longitude: Double,
        val bedIndex: Int,
        val holeIndex: Int,
        val twiScore: Double,
        val prevailingWindDeg: Double,
        val windCardinal: String,
        val downwindThreatAzimuthDeg: Double,
        val sporeRiskMultiplier: Double,
        val summaryNote: String
    )

    fun assembleContext(
        latitude: Double,
        longitude: Double,
        bedIndex: Int,
        holeIndex: Int,
        twiScore: Double,
        windBearingDeg: Double
    ): DiagnosticSpatialContext {
        val normalizedWind = (windBearingDeg % 360.0 + 360.0) % 360.0
        // Downwind threat propagates in the direction the wind blows towards (+180°)
        val downwindAzimuth = (normalizedWind + 180.0) % 360.0

        val cardinal = when (normalizedWind) {
            in 337.5..360.0, in 0.0..22.5 -> "Utara"
            in 22.5..67.5 -> "Timur Laut"
            in 67.5..112.5 -> "Timur"
            in 112.5..157.5 -> "Tenggara"
            in 157.5..202.5 -> "Selatan"
            in 202.5..247.5 -> "Barat Daya"
            in 247.5..292.5 -> "Barat"
            else -> "Barat Laut"
        }

        // Higher TWI + strong downwind vector increases spore germination risk
        val twiFactor = (twiScore / 6.0).coerceIn(0.8, 2.0)
        val riskMultiplier = Math.round(twiFactor * 1.25 * 100.0) / 100.0

        val note = "Pohon di Bedengan #$bedIndex, Lubang #$holeIndex (TWI ${"%.2f".format(twiScore)}). " +
                "Arah angin datang dari $cardinal (${"%.0f".format(normalizedWind)}°). " +
                "Vektor penyebaran patogen mengarah ke azimut ${"%.0f".format(downwindAzimuth)}°."

        return DiagnosticSpatialContext(
            latitude = latitude,
            longitude = longitude,
            bedIndex = bedIndex,
            holeIndex = holeIndex,
            twiScore = twiScore,
            prevailingWindDeg = Math.round(normalizedWind * 10.0) / 10.0,
            windCardinal = cardinal,
            downwindThreatAzimuthDeg = Math.round(downwindAzimuth * 10.0) / 10.0,
            sporeRiskMultiplier = riskMultiplier,
            summaryNote = note
        )
    }

    /**
     * Converts camera lens focus distance (diopters m^-1) to physical distance in millimeters.
     * d = 1 / diopter (in meters) * 1000 = mm
     */
    fun diopterToDistanceMm(focusDistanceDiopters: Float): Double {
        if (focusDistanceDiopters <= 0.05f) return 500.0 // default 50 cm macro distance
        val meters = 1.0 / focusDistanceDiopters
        return Math.round(meters * 1000.0 * 10.0) / 10.0
    }

    /**
     * Calculates absolute physical area of a lesion in mm^2 without requiring a reference coin,
     * using the calibrated lens focus distance and sensor pixel pitch.
     */
    fun calculatePhysicalLesionAreaMm2(
        pixelCount: Int,
        distanceMm: Double,
        focalLengthMm: Double = 4.2,
        sensorPixelPitchUm: Double = 1.4
    ): Double {
        // Physical dimension of 1 pixel on the object plane:
        // pitch_obj = pitch_sensor * (distance / focalLength)
        val pixelPitchMm = (sensorPixelPitchUm / 1000.0) * (distanceMm / focalLengthMm)
        val singlePixelAreaMm2 = pixelPitchMm * pixelPitchMm
        val totalArea = pixelCount * singlePixelAreaMm2
        return Math.round(totalArea * 100.0) / 100.0
    }
}
