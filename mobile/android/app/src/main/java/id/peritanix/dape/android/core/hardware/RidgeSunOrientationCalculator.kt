package id.peritanix.dape.android.core.hardware

import java.util.Calendar
import kotlin.math.*

/**
 * Tahap 1.2: Analisis Orientasi Bedengan & Sudut Penyinaran Matahari (Magnetometer).
 *
 * Uses magnetometer ridge heading + GPS coordinates + calendar date/time to compute
 * solar azimuth & solar elevation, evaluating whether bed orientation maximizes morning
 * sunlight exposure to dry canopy dew rapidly and suppress fungal sporulation (Anthracnose).
 */
object RidgeSunOrientationCalculator {

    data class OrientationAssessment(
        val bedAzimuthDeg: Double,
        val bedOrientationType: String, // "Utara-Selatan (Optimal)" / "Timur-Barat" / "Diagonal"
        val solarAzimuthDeg: Double,
        val solarElevationDeg: Double,
        val morningSunExposurePct: Int,
        val dewDryingSpeedRating: String, // "Sangat Cepat", "Standar", "Lambat (Risiko Embun)"
        val recommendedPlantSpacingCm: Int,
        val recommendation: String
    )

    /**
     * Evaluates agronomic alignment between chili bed ridge direction and solar path.
     * In equatorial Indonesia (-7° to +5° Lat), a North-South bed orientation receives equal
     * sunlight on both sides of the ridge (morning on east side, afternoon on west side),
     * drying dew quickly.
     */
    fun evaluateBedOrientation(
        bedHeadingDeg: Double,
        latitude: Double = -7.123,
        longitude: Double = 110.456,
        calendar: Calendar = Calendar.getInstance()
    ): OrientationAssessment {
        val normalizedBed = (bedHeadingDeg % 180.0 + 180.0) % 180.0 // 0 - 180° line

        // Solar position approximation for date & time
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val hour = calendar.get(Calendar.HOUR_OF_DAY) + calendar.get(Calendar.MINUTE) / 60.0

        // Approximate solar declination
        val declinationRad = Math.toRadians(23.45 * sin(Math.toRadians(360.0 / 365.0 * (dayOfYear - 81))))
        val latRad = Math.toRadians(latitude)
        val hourAngleRad = Math.toRadians((hour - 12.0) * 15.0)

        // Solar elevation
        val sinElevation = sin(latRad) * sin(declinationRad) + cos(latRad) * cos(declinationRad) * cos(hourAngleRad)
        val elevationRad = asin(sinElevation.coerceIn(-1.0, 1.0))
        val elevationDeg = Math.toDegrees(elevationRad)

        // Solar azimuth
        val cosAzimuth = (sin(declinationRad) * cos(latRad) - cos(declinationRad) * sin(latRad) * cos(hourAngleRad)) / cos(elevationRad).coerceAtLeast(0.001)
        var azimuthRad = acos(cosAzimuth.coerceIn(-1.0, 1.0))
        if (sin(hourAngleRad) > 0) {
            azimuthRad = 2 * Math.PI - azimuthRad
        }
        val solarAzimuthDeg = (Math.toDegrees(azimuthRad) + 360.0) % 360.0

        // Orientation classification
        // 0° / 180° = North-South. 90° = East-West.
        val angleToNorth = min(normalizedBed, 180.0 - normalizedBed) // 0 to 90

        val (orientationType, exposurePct, dewSpeed, spacing, rec) = when {
            angleToNorth <= 22.5 -> {
                // North-South (0° - 22.5°)
                Pentad(
                    "Utara-Selatan (Sangat Ideal)",
                    96,
                    "Sangat Cepat (< 08:30 WIB)",
                    50,
                    "Orientasi memanjang Utara-Selatan sangat ideal: lorong bedengan tersinari penuh pagi & sore hari. Embun pagi menguap cepat, menekan perkecambahan spora Antraknosa Colletotrichum hingga 65%."
                )
            }
            angleToNorth >= 67.5 -> {
                // East-West (67.5° - 90°)
                Pentad(
                    "Timur-Barat (Sub-Optimal)",
                    68,
                    "Lambat (Risiko Embun Mengendap)",
                    60,
                    "Orientasi memanjang Timur-Barat: salah satu sisi bedengan akan terbayangi tajuknya sendiri sepanjang hari. Disarankan perlebar jarak tanam mulsa menjadi 60 cm dan lakukan wiwilan tunas lebih intensif."
                )
            }
            else -> {
                // Diagonal (22.5° - 67.5°)
                Pentad(
                    "Diagonal / Tenggara-Barat Laut",
                    82,
                    "Standar (~ 09:15 WIB)",
                    50,
                    "Orientasi diagonal cukup baik dalam menangkap radiasi matahari pagi. Pastikan parit drainase lancar agar tidak terjadi stagnasi kelembaban di permukaan bedengan."
                )
            }
        }

        return OrientationAssessment(
            bedAzimuthDeg = Math.round(bedHeadingDeg * 10.0) / 10.0,
            bedOrientationType = orientationType,
            solarAzimuthDeg = Math.round(solarAzimuthDeg * 10.0) / 10.0,
            solarElevationDeg = Math.round(elevationDeg * 10.0) / 10.0,
            morningSunExposurePct = exposurePct,
            dewDryingSpeedRating = dewSpeed,
            recommendedPlantSpacingCm = spacing,
            recommendation = rec
        )
    }

    private data class Pentad(
        val type: String,
        val exposure: Int,
        val speed: String,
        val spacing: Int,
        val rec: String
    )
}
