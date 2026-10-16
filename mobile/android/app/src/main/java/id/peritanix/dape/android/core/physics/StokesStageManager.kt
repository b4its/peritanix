package id.peritanix.dape.android.core.physics

import id.peritanix.dape.physics.Physics

/**
 * FR-1.1 Stokes Sedimentation Stage Manager & Protocol Guide.
 *
 * Implements the field soil texture sedimentation workflow using Stokes' Law:
 *   v = (2/9) * ((rho_p - rho_f) * g / mu) * r^2
 *
 * Manages timed sedimentation stages:
 *   1. Stage 1 (0 - 60s): Active jar agitation with detergent dispersant.
 *   2. Stage 2 (t = 40s): Sand fraction settles out of suspension.
 *   3. Stage 3 (t = 7200s / 2 hours): Silt settles above sand; fine clay remains suspended.
 *
 * Computes layer thickness proportions and maps to USDA texture triangle and CEC.
 */
object StokesStageManager {

    const val GRAVITY = 9.80665 // m/s^2
    const val PARTICLE_DENSITY_DEFAULT = 2650.0 // kg/m^3 (quartz/mineral soil)
    const val WATER_DENSITY = 1000.0 // kg/m^3
    const val WATER_VISCOSITY = 0.001 // Pa.s at 20°C

    enum class Stage(val durationSeconds: Int, val title: String, val instructions: String) {
        SHAKING(
            durationSeconds = 60,
            title = "Pengocokan Sampel",
            instructions = "Kocok botol berisi 1/3 tanah + air bersih + 1 sdt detergen cair selama 60 detik agar agregat terdispersi sempurna."
        ),
        SAND_SETTLING(
            durationSeconds = 40,
            title = "Pengendapan Fraksi Pasir (40 Detik)",
            instructions = "Letakkan botol di permukaan datar. Pada detik ke-40, ambil foto pertama dengan posisi kamera tegak 90° untuk mengukur lapisan pasir."
        ),
        SILT_CLAY_SETTLING(
            durationSeconds = 7200,
            title = "Pengendapan Fraksi Debu & Liat (2 Jam)",
            instructions = "Diamkan botol selama 2 jam. Partikel debu akan mengendap membentuk garis batas jelas di atas pasir, sementara koloid liat tersisa di atas."
        )
    }

    data class LayerThickness(
        val sandMm: Double,
        val siltMm: Double,
        val clayMm: Double
    ) {
        val totalMm: Double get() = sandMm + siltMm + clayMm
    }

    /**
     * Calculates Stokes settling terminal velocity for a spherical soil grain.
     * @param radiusM Grain radius in meters (e.g. 0.000025m for coarse silt).
     */
    fun calculateSettlingVelocity(
        radiusM: Double,
        particleDensity: Double = PARTICLE_DENSITY_DEFAULT,
        fluidDensity: Double = WATER_DENSITY,
        viscosity: Double = WATER_VISCOSITY
    ): Double {
        val deltaRho = particleDensity - fluidDensity
        val velocity = (2.0 / 9.0) * (deltaRho * GRAVITY / viscosity) * (radiusM * radiusM)
        return velocity
    }

    /**
     * Converts layer thicknesses to USDA fractions, CEC, and agronomic recommendations.
     */
    fun evaluateTexture(thickness: LayerThickness): StokesCalculator.SedimentResult {
        return StokesCalculator.computeFromThickness(
            sandThickness = thickness.sandMm,
            siltThickness = thickness.siltMm,
            clayThickness = thickness.clayMm
        )
    }

    /**
     * Shutter unlock logic based on camera perpendicularity and active stage.
     */
    fun getShutterStatus(
        isPerpendicular: Boolean,
        stage: Stage,
        secondsElapsed: Int
    ): Pair<Boolean, String> {
        if (!isPerpendicular) {
            return Pair(false, "KAMERA MIRING: Posisikan smartphone tegak lurus (90° ± 5°) terhadap botol untuk membuka shutter.")
        }

        return when (stage) {
            Stage.SHAKING -> {
                if (secondsElapsed < stage.durationSeconds) {
                    Pair(false, "Masih dalam proses pengocokan (${stage.durationSeconds - secondsElapsed}s tersisa).")
                } else {
                    Pair(true, "Pengocokan selesai. Segera letakkan botol pada permukaan rata.")
                }
            }
            Stage.SAND_SETTLING -> {
                if (secondsElapsed < stage.durationSeconds) {
                    Pair(false, "Menunggu pengendapan pasir (${stage.durationSeconds - secondsElapsed}s tersisa).")
                } else {
                    Pair(true, "Waktu t=40s tercapai! Shutter TERBUKA: Ambil foto lapisan pasir sekarang.")
                }
            }
            Stage.SILT_CLAY_SETTLING -> {
                if (secondsElapsed < stage.durationSeconds) {
                    val remMins = (stage.durationSeconds - secondsElapsed) / 60
                    Pair(false, "Proses sedimentasi lambat ($remMins menit tersisa hingga t=2 jam).")
                } else {
                    Pair(true, "Waktu t=2 jam tercapai! Shutter TERBUKA: Ambil foto batas lapisan debu dan liat.")
                }
            }
        }
    }
}
