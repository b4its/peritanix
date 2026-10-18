package id.peritanix.dape.android.core.hardware

import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Pure deterministic mathematics for compass orientation, figure-8 magnetometer calibration,
 * spirit bubble level zeroing, and agronomic bed orientation intelligence.
 */
object SensorCalibrationMath {

    enum class BedOrientationType {
        NORTH_SOUTH,
        EAST_WEST,
        DIAGONAL
    }

    data class AgronomicHeading(
        val azimuthDeg: Double,
        val cardinalCode: String,
        val cardinalName: String,
        val bedOrientationType: BedOrientationType,
        val orientationTitle: String,
        val orientationAdvice: String,
        val sporeWindRisk: String,
        val isSporeWindAligned: Boolean,
        val isIdealBedOrientation: Boolean
    )

    data class HardIronOffsets(
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val offsetZ: Float = 0f
    ) {
        val isCalibrated: Boolean
            get() = abs(offsetX) > 0.001f || abs(offsetY) > 0.001f || abs(offsetZ) > 0.001f
    }

    enum class MagneticStatus(val label: String, val isReliable: Boolean) {
        NORMAL("Normal Geomagnetik (25-65 µT)", true),
        INTERFERENCE_HIGH("Anomali Medan Magnet Tinggi (>70 µT, Logam/Speaker)", false),
        WEAK_SHIELDED("Sinyal Magnet Terlalu Lemah (<20 µT)", false)
    }

    data class TiltOffset(
        val pitchZeroOffset: Double = 0.0,
        val rollZeroOffset: Double = 0.0
    ) {
        val isCalibrated: Boolean
            get() = abs(pitchZeroOffset) > 0.001 || abs(rollZeroOffset) > 0.001
    }

    /**
     * Normalizes an angle in degrees to the [0, 360) range.
     */
    fun normalize360(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0.0) d += 360.0
        return d
    }

    fun normalize360(deg: Float): Float {
        var d = deg % 360f
        if (d < 0f) d += 360f
        return d
    }

    /**
     * Converts an azimuth angle (0..360°) to Indonesian cardinal code and label.
     */
    fun azimuthToCardinal(azimuthDeg: Double): Pair<String, String> {
        val norm = normalize360(azimuthDeg)
        return when {
            norm >= 337.5 || norm < 22.5 -> Pair("U", "Utara")
            norm < 67.5 -> Pair("TL", "Timur Laut")
            norm < 112.5 -> Pair("T", "Timur")
            norm < 157.5 -> Pair("TG", "Tenggara")
            norm < 202.5 -> Pair("S", "Selatan")
            norm < 247.5 -> Pair("BD", "Barat Daya")
            norm < 292.5 -> Pair("B", "Barat")
            norm < 337.5 -> Pair("BL", "Barat Laut")
            else -> Pair("U", "Utara")
        }
    }

    /**
     * Circular low-pass filter (modular smoothing) to prevent 359° -> 1° erratic rotations.
     */
    fun smoothAzimuth(current: Float, target: Float, alpha: Float = 0.2f): Float {
        val normCurrent = normalize360(current)
        val normTarget = normalize360(target)
        var diff = normTarget - normCurrent
        while (diff < -180f) diff += 360f
        while (diff > 180f) diff -= 360f
        return normalize360(normCurrent + alpha * diff)
    }

    /**
     * Computes hard-iron offsets from min and max envelope values recorded during figure-8 rotation.
     */
    fun calculateHardIronOffsets(
        minX: Float, maxX: Float,
        minY: Float, maxY: Float,
        minZ: Float, maxZ: Float
    ): HardIronOffsets {
        return HardIronOffsets(
            offsetX = (minX + maxX) / 2f,
            offsetY = (minY + maxY) / 2f,
            offsetZ = (minZ + maxZ) / 2f
        )
    }

    /**
     * Computes total magnetic field magnitude: sqrt(Bx² + By² + Bz²) in µT.
     */
    fun calculateMagneticMagnitude(x: Float, y: Float, z: Float): Double {
        return sqrt((x * x + y * y + z * z).toDouble())
    }

    /**
     * Evaluates magnetic interference based on standard Earth magnetic field (25 - 65 µT).
     */
    fun evaluateMagneticStatus(magnitudeMicroTesla: Double): MagneticStatus {
        return when {
            magnitudeMicroTesla > 70.0 -> MagneticStatus.INTERFERENCE_HIGH
            magnitudeMicroTesla < 20.0 -> MagneticStatus.WEAK_SHIELDED
            else -> MagneticStatus.NORMAL
        }
    }

    /**
     * Applies calibrated zero-tilt offsets to raw pitch and roll readings.
     */
    fun calibrateTilt(
        rawPitch: Double,
        rawRoll: Double,
        offset: TiltOffset
    ): Pair<Double, Double> {
        val calPitch = rawPitch - offset.pitchZeroOffset
        val calRoll = rawRoll - offset.rollZeroOffset
        return Pair(
            round(calPitch * 10.0) / 10.0,
            round(calRoll * 10.0) / 10.0
        )
    }

    /**
     * Checks if smartphone is held horizontally flat within specified tolerance.
     */
    fun isDeviceLevel(pitchDeg: Double, rollDeg: Double, toleranceDeg: Double = 2.5): Boolean {
        return abs(pitchDeg) <= toleranceDeg && abs(rollDeg) <= toleranceDeg
    }

    /**
     * Analyzes agronomic implications of current field heading:
     * - Bed / Ridge (guludan) solar exposure (North-South vs East-West).
     * - Kopeng / Merbabu wind spore dispersion vector (ENE ~65° ± 20°).
     */
    fun evaluateAgronomicHeading(azimuthDeg: Double): AgronomicHeading {
        val norm = normalize360(azimuthDeg)
        val (cardinalCode, cardinalName) = azimuthToCardinal(norm)

        // Bed orientation classification
        // North-South: 335°-25° (around North) or 155°-205° (around South)
        val isNorthSouth = (norm >= 335.0 || norm <= 25.0) || (norm in 155.0..205.0)
        // East-West: 65°-115° (around East) or 245°-295° (around West)
        val isEastWest = (norm in 65.0..115.0) || (norm in 245.0..295.0)

        val (bedType, title, advice) = when {
            isNorthSouth -> Triple(
                BedOrientationType.NORTH_SOUTH,
                "Orientasi Ideal: Utara - Selatan",
                "Kedua sisi tajuk cabai menerima sinar matahari penuh dari timur ke barat. Embun pagi cepat kering, menekan sporulasi Colletotrichum & Phytophthora."
            )
            isEastWest -> Triple(
                BedOrientationType.EAST_WEST,
                "Orientasi Peringatan: Timur - Barat",
                "Sisi utara/selatan bedengan rentan bayangan terus-menerus. Perlebar jarak parit minimal 50 cm dan lakukan pemangkasan tunas air untuk sirkulasi kanopi."
            )
            else -> Triple(
                BedOrientationType.DIAGONAL,
                "Orientasi Diagonal ($cardinalName)",
                "Angin bertiup menyerong melintasi bedengan. Pastikan kemiringan parit 2-3% agar air hujan mengalir lancar dan tidak mengikis guludan."
            )
        }

        // Kopeng highland wind / spore vector check (ENE ~65° ± 20°)
        val isSporeWindAligned = norm in 45.0..85.0
        val sporeWindRisk = if (isSporeWindAligned) {
            "Arah Searah Vektor Angin Spora Lereng Merbabu (ENE ~65°): Tingkat risiko penyebaran spora hawar daun & vektor kutu kebul tinggi dari lereng atas."
        } else {
            "Aman dari sumbu utama angin spora lereng atas."
        }

        return AgronomicHeading(
            azimuthDeg = round(norm * 10.0) / 10.0,
            cardinalCode = cardinalCode,
            cardinalName = cardinalName,
            bedOrientationType = bedType,
            orientationTitle = title,
            orientationAdvice = advice,
            sporeWindRisk = sporeWindRisk,
            isSporeWindAligned = isSporeWindAligned,
            isIdealBedOrientation = isNorthSouth
        )
    }

    /**
     * Converts pixel count to physical length (mm) using Camera2 diopter lens focus distance.
     * Focus distance D in diopters = 1 / d (meters). So d (mm) = 1000 / D.
     * [kOpticalScale] is the calibrated optical scale factor (mm * diopter / pixel).
     */
    fun pixelsToMm(pixels: Double, focusDiopter: Double, kOpticalScale: Double = 1.0): Double {
        val safeDiopter = focusDiopter.coerceIn(0.1, 25.0)
        val mm = (pixels * kOpticalScale) / safeDiopter
        return round(mm * 10.0) / 10.0
    }

    /**
     * Converts pixel area to physical square millimeters (mm²) using Camera2 diopter lens focus distance.
     */
    fun pixelsToMm2(pixelArea: Double, focusDiopter: Double, kOpticalScale: Double = 1.0): Double {
        val safeDiopter = focusDiopter.coerceIn(0.1, 25.0)
        val linearScale = kOpticalScale / safeDiopter
        val area = pixelArea * linearScale * linearScale
        return round(area * 10.0) / 10.0
    }

    /**
     * Calibrates optical constant k from a known reference physical object (e.g. standard card 85.6 mm or 100 mm card).
     */
    fun calculateOpticalScale(
        pixelLength: Double,
        referenceMm: Double,
        focusDiopter: Double
    ): Double {
        if (pixelLength <= 0.0) return 1.0
        val safeDiopter = focusDiopter.coerceIn(0.1, 25.0)
        val scale = (referenceMm * safeDiopter) / pixelLength
        return round(scale * 100.0) / 100.0
    }

    /**
     * Calculates bed height difference from differential atmospheric pressure (ΔP).
     * Near sea-level to 2000m, 1 hPa ~ 8.3 meters = 830 cm.
     */
    fun barometricPressureToBedHeightCm(baselinePressureHpa: Double, crestPressureHpa: Double): Double {
        val deltaP = baselinePressureHpa - crestPressureHpa
        val heightCm = deltaP * 830.0
        return round(heightCm.coerceIn(0.0, 150.0) * 10.0) / 10.0
    }
}
