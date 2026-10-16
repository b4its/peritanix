package id.peritanix.dape.android.core.hardware

import android.hardware.camera2.CaptureRequest
import kotlin.math.pow

/**
 * FR-1.2 Controlled Dark-Chamber Photometry & Camera2 Parameter Lock.
 *
 * Implements strict optical photometry under a dark cover bowl:
 *   1. TYPE_LIGHT sensor verification: Lux reading must be 0 Lux before shutter enables.
 *   2. Camera2 CaptureRequest parameter lock:
 *      - CONTROL_AE_MODE_OFF (disable auto-exposure)
 *      - FLASH_MODE_TORCH (constant LED torch)
 *      - SENSOR_SENSITIVITY = ISO 100
 *      - SENSOR_EXPOSURE_TIME = 8,000,000 ns (1/125s)
 *   3. CIELAB conversion with White Reference Matrix spectral calibration.
 */
object DarkChamberFlashManager {

    const val REQUIRED_LUX_THRESHOLD = 0.5f // Must be < 1.0 Lux (0 Lux ideal)
    const val LOCKED_ISO = 100
    const val LOCKED_EXPOSURE_NS = 8_000_000L // 1/125 s

    data class WhiteReference(
        val rScale: Double,
        val gScale: Double,
        val bScale: Double,
        val luxVerified: Boolean,
        val isCalibrated: Boolean
    )

    data class PhotometryResult(
        val phDeterministic: Double,
        val nitratePpm: Double,
        val labL: Double,
        val labA: Double,
        val labB: Double,
        val isValid: Boolean,
        val statusMessage: String
    )

    /**
     * Calibrates device flash against standard white HVS paper in sealed dark chamber (0 Lux).
     * Calculates gain multipliers so that pure white maps to standard reference.
     */
    fun calibrateSpectralWhiteReference(
        rawR: Int, rawG: Int, rawB: Int, ambientLux: Float
    ): WhiteReference {
        val sealed = isDarkChamberSealed(ambientLux)
        if (!sealed) {
            return WhiteReference(1.0, 1.0, 1.0, luxVerified = false, isCalibrated = false)
        }
        val safeR = rawR.coerceAtLeast(1).toDouble()
        val safeG = rawG.coerceAtLeast(1).toDouble()
        val safeB = rawB.coerceAtLeast(1).toDouble()

        val rScale = Math.round((255.0 / safeR) * 1000.0) / 1000.0
        val gScale = Math.round((255.0 / safeG) * 1000.0) / 1000.0
        val bScale = Math.round((255.0 / safeB) * 1000.0) / 1000.0

        return WhiteReference(
            rScale = rScale,
            gScale = gScale,
            bScale = bScale,
            luxVerified = true,
            isCalibrated = true
        )
    }

    /**
     * Applies fixed Camera2 parameters to ensure repeatable, reproducible spectral capture.
     */
    fun configureLockedCaptureRequest(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        builder.set(CaptureRequest.SENSOR_SENSITIVITY, LOCKED_ISO)
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, LOCKED_EXPOSURE_NS)
    }

    /**
     * Verifies that the dark chamber cover is completely light-tight.
     */
    fun isDarkChamberSealed(ambientLux: Float): Boolean {
        return ambientLux <= REQUIRED_LUX_THRESHOLD
    }

    /**
     * Converts RGB with device White Reference calibration to CIELAB and matches pH / Nitrate curve.
     */
    fun evaluatePhotometry(
        rawR: Int, rawG: Int, rawB: Int,
        whiteRefR: Double = 1.0, whiteRefG: Double = 1.0, whiteRefB: Double = 1.0
    ): PhotometryResult {
        // 1. Normalize and apply white-reference correction
        val rNorm = (rawR.toDouble() / 255.0) * whiteRefR
        val gNorm = (rawG.toDouble() / 255.0) * whiteRefG
        val bNorm = (rawB.toDouble() / 255.0) * whiteRefB

        // 2. Simple linear sRGB to XYZ (D65 standard)
        val x = rNorm * 0.4124 + gNorm * 0.3576 + bNorm * 0.1805
        val y = rNorm * 0.2126 + gNorm * 0.7152 + bNorm * 0.0722
        val z = rNorm * 0.0193 + gNorm * 0.1192 + bNorm * 0.9505

        // 3. XYZ to CIELAB
        fun f(t: Double): Double = if (t > 0.008856) t.pow(1.0 / 3.0) else (7.787 * t) + (16.0 / 116.0)

        val xn = 0.95047; val yn = 1.00000; val zn = 1.08883
        val l = 116.0 * f(y / yn) - 16.0
        val a = 500.0 * (f(x / xn) - f(y / yn))
        val b = 200.0 * (f(y / yn) - f(z / zn))

        // 4. Deterministic reagent calibration polynomial for soil litmus strip
        // Typical universal indicator: red (pH 4) -> orange -> yellow (pH 6) -> green (pH 7) -> blue (pH 8)
        val phEstimate = when {
            a > 20.0 -> 4.5 + (a - 20.0) * 0.03
            a > 5.0 -> 5.5 + (20.0 - a) * 0.05
            b > 15.0 -> 6.5 + (b - 15.0) * 0.02
            else -> 7.0 + (l - 50.0) * 0.02
        }.coerceIn(4.0, 8.5)

        // Nitrate Griess reagent: pink intensity relates to `a*`
        val nitrateEstimate = if (a > 0) Math.round(a * 4.5 * 10.0) / 10.0 else 5.0

        val roundedPh = Math.round(phEstimate * 10.0) / 10.0

        val msg = when {
            roundedPh < 5.5 -> "Tanah masam (pH $roundedPh): Berikan Dolomit (Kapur Pertanian) 1.5 - 2 ton/ha untuk membuka fiksasi Al/Fe."
            roundedPh > 7.2 -> "Tanah alkalis (pH $roundedPh): Gunakan pupuk ZA / belerang untuk menurunkan pH ke rentang ideal 6.0 - 6.8."
            else -> "pH tanah ideal ($roundedPh): Ketersediaan hara makro (N, P, K, Ca, Mg) dalam kondisi optimum."
        }

        return PhotometryResult(
            phDeterministic = roundedPh,
            nitratePpm = nitrateEstimate,
            labL = Math.round(l * 10.0) / 10.0,
            labA = Math.round(a * 10.0) / 10.0,
            labB = Math.round(b * 10.0) / 10.0,
            isValid = true,
            statusMessage = msg
        )
    }
}
