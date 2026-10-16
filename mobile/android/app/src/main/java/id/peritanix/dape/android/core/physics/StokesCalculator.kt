package id.peritanix.dape.android.core.physics

import id.peritanix.dape.physics.Physics

/**
 * FR-1.1 Stokes Sedimentation Analyzer & Water-Level Lock.
 *
 * Implements soil texture determination via Stokes' Law:
 *   v = (2/9) * ((rho_p - rho_f) * g / mu) * r^2
 *
 * Provides:
 *   1. Precise timer stages: t = 40s (sand settlement), t = 7200s / 2h (silt settlement).
 *   2. Water-level camera overlay validation (verifies smartphone is perpendicular at 90° ± 5°).
 *   3. Sub-pixel / thickness proportion parsing to USDA texture class & CEC (meq/100g).
 */
object StokesCalculator {

    const val TIMER_SAND_SECONDS = 40
    const val TIMER_SILT_SECONDS = 7200 // 2 hours

    data class SedimentResult(
        val sandPct: Double,
        val siltPct: Double,
        val clayPct: Double,
        val usdaClass: String,
        val cecMeq: Double,
        val baseFertilizerRecommendation: String
    )

    /**
     * Validates accelerometer readings to ensure camera is perpendicular (90° ± 5°)
     * against the transparent sedimentation jar before allowing shutter trigger.
     */
    fun isUprightPerpendicular(gravityX: Float, gravityY: Float, gravityZ: Float): Boolean {
        // In a portrait upright position: gravityY is near 9.8 m/s^2, gravityZ is near 0
        val pitchDeg = Math.toDegrees(Math.atan2(gravityZ.toDouble(), gravityY.toDouble()))
        val rollDeg = Math.toDegrees(Math.atan2(gravityX.toDouble(), gravityY.toDouble()))
        return Math.abs(pitchDeg) <= 5.0 && Math.abs(rollDeg) <= 5.0
    }

    /**
     * Calculates texture percentages from layer thickness (mm or pixels) and maps to USDA & CEC.
     */
    fun computeFromThickness(
        sandThickness: Double,
        siltThickness: Double,
        clayThickness: Double
    ): SedimentResult {
        require(sandThickness >= 0 && siltThickness >= 0 && clayThickness >= 0) { "Ketebalan tidak boleh negatif" }
        val total = sandThickness + siltThickness + clayThickness
        require(total > 0) { "Total ketebalan sedimentasi harus > 0" }

        val sand = Math.round((sandThickness / total * 100.0) * 10.0) / 10.0
        val silt = Math.round((siltThickness / total * 100.0) * 10.0) / 10.0
        val clay = Math.round((clayThickness / total * 100.0) * 10.0) / 10.0

        val usda = Physics.classifyUsda(sand, silt, clay)
        val cec = Math.round(Physics.cecFromTexture(sand, silt, clay) * 100.0) / 100.0

        val rec = when {
            sand > 60.0 -> "Tanah pasiran (KTK rendah): Pupuk kocor wajib diberikan sedikit demi sedikit dengan frekuensi tinggi (3 hari sekali) agar tidak tercuci (leaching)."
            clay > 35.0 -> "Tanah liat berat (KTK tinggi, aerasi rendah): Tambahkan bahan organik / pupuk kandang matang 20 ton/ha untuk memperbaiki drainase dan porositas akar."
            else -> "Tekstur lempung ideal: KTK sedang-tinggi. Respon pemupukan sangat efisien; pertahankan kelembaban tanah 60-70% kapasitas lapang."
        }

        return SedimentResult(
            sandPct = sand,
            siltPct = silt,
            clayPct = clay,
            usdaClass = usda,
            cecMeq = cec,
            baseFertilizerRecommendation = rec
        )
    }
}
