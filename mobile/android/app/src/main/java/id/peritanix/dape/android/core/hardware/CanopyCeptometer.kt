package id.peritanix.dape.android.core.hardware

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max

/**
 * Tahap 4.1: Ceptometer Tajuk Berbasis Sensor Cahaya (Hukum Beer-Lambert).
 *
 * Computes Leaf Area Index (LAI) using 2 ambient light sensor readings:
 *   1. I0 = incident solar radiation above canopy (Lux)
 *   2. Iz = transmitted radiation below canopy near mulching / main fork (Lux)
 *
 * Formula (Beer-Lambert Law inversion):
 *   LAI = -ln(Iz / I0) / k(theta_z)
 *   where k is the light extinction coefficient for Capsicum annuum canopy (~0.65).
 */
object CanopyCeptometer {

    data class CeptometerResult(
        val aboveCanopyLux: Float,
        val belowCanopyLux: Float,
        val transmissionRatio: Float,
        val extinctionCoefficientK: Double,
        val leafAreaIndex: Double,
        val isWiwilanRequired: Boolean,
        val canopyDensityRating: String, // "Terlalu Rimbun (Wajib Wiwil)", "Optimal", "Kurang Rimbun"
        val prescriptiveAdvisory: String
    )

    const val DEFAULT_CANOPY_K = 0.65 // Capsicum annuum foliar extinction coefficient

    fun calculateLai(
        aboveLux: Float,
        belowLux: Float,
        solarZenithAngleDeg: Double = 30.0,
        baseK: Double = DEFAULT_CANOPY_K
    ): CeptometerResult {
        val safeAbove = max(aboveLux, 100.0f)
        val safeBelow = max(belowLux, 1.0f).coerceAtMost(safeAbove * 0.99f)

        val transmissionRatio = safeBelow / safeAbove

        // Correct extinction coefficient for solar zenith angle: k(theta) = k0 / cos(theta)
        val zenithRad = Math.toRadians(solarZenithAngleDeg.coerceIn(0.0, 75.0))
        val cosZenith = cos(zenithRad).coerceAtLeast(0.2)
        val effectiveK = baseK / cosZenith

        // LAI = -ln(Iz / I0) / k
        val rawLai = -ln(transmissionRatio.toDouble()) / effectiveK
        val lai = Math.round(rawLai.coerceIn(0.2, 7.0) * 10.0) / 10.0

        val (isWiwilan, rating, advice) = when {
            lai > 3.5 -> {
                Triple(
                    true,
                    "Terlalu Rimbun (Wajib Wiwil)",
                    "LAI ${lai} > 3.5: Tajuk terlalu rimbun! Cahaya matahari terhalang menembus percabangan bawah. Segera lakukan Pemangkasan Tunas Air (Wiwilan) di bawah cabang Y untuk melancarkan sirkulasi udara dan mencegah sporulasi Antraknosa Colletotrichum."
                )
            }
            lai in 2.0..3.5 -> {
                Triple(
                    false,
                    "Optimal (Seimbang)",
                    "LAI ${lai}: Kerapatan tajuk cabai dalam kondisi ideal. Penangkapan radiasi fotosintesis (PAR) maksimal dengan aerasi kanopi yang sehat."
                )
            }
            else -> {
                Triple(
                    false,
                    "Kurang Rimbun / Kanopi Terbuka",
                    "LAI ${lai} < 2.0: Tajuk tanaman terlalu jarang. Periksa kecukupan hara Nitrogen & Magnesium, atau waspadai serangan kutu kebul/Thrips yang menghambat pertumbuhan vegetatif."
                )
            }
        }

        return CeptometerResult(
            aboveCanopyLux = safeAbove,
            belowCanopyLux = safeBelow,
            transmissionRatio = Math.round(transmissionRatio * 1000.0f) / 1000.0f,
            extinctionCoefficientK = Math.round(effectiveK * 100.0) / 100.0,
            leafAreaIndex = lai,
            isWiwilanRequired = isWiwilan,
            canopyDensityRating = rating,
            prescriptiveAdvisory = advice
        )
    }
}
