package id.peritanix.dape.android.core.physics

import kotlin.math.max
import kotlin.math.min

/**
 * FR-3.1 Microclimate Early Warning System (EWS) & GDD Thermal Chronology.
 *
 * Tracks cumulative biological heat units (Growing Degree Days, T_base = 15°C)
 * and projects 72-hour fungal sporulation risk (Anthracnose / Phytophthora)
 * from ambient microclimate conditions (temperature & relative humidity).
 */
object MicroclimateEwsEvaluator {

    const val T_BASE_CHILI_C = 15.0

    data class GddDailyResult(
        val tMinC: Double,
        val tMaxC: Double,
        val dailyGdd: Double,
        val accumulatedGdd: Double,
        val physiologicalPhase: String,
        val isFortificationLocked: Boolean
    )

    data class EwsRiskForecast(
        val relativeHumidityPct: Double,
        val temperatureC: Double,
        val sporulationRiskScore: Double, // 0.0 to 1.0
        val riskLevel: String, // LOW | MODERATE | HIGH | CRITICAL
        val alertHeader: String,
        val preventiveSprayAdvisory: String
    )

    /**
     * Calculates daily GDD increment and physiological phase transition.
     */
    fun calculateDailyGdd(
        tMinC: Double,
        tMaxC: Double,
        previousAccumulatedGdd: Double = 0.0
    ): GddDailyResult {
        val meanTemp = (tMinC + tMaxC) / 2.0
        val daily = max(0.0, meanTemp - T_BASE_CHILI_C)
        val newAccum = Math.round((previousAccumulatedGdd + daily) * 10.0) / 10.0

        val (phase, locked) = when {
            newAccum < 450.0 -> Pair("VEGETATIVE", false)
            newAccum < 750.0 -> Pair("FLOWERING", true)
            newAccum < 1100.0 -> Pair("FRUITING", false)
            else -> Pair("HARVEST_READY", false)
        }

        return GddDailyResult(
            tMinC = tMinC,
            tMaxC = tMaxC,
            dailyGdd = Math.round(daily * 100.0) / 100.0,
            accumulatedGdd = newAccum,
            physiologicalPhase = phase,
            isFortificationLocked = locked
        )
    }

    /**
     * Evaluates 72-hour microclimate conditions for fungal blast / anthracnose spore release.
     * High humidity (> 80%) coupled with warm temperature (24°C - 32°C) is the primary trigger.
     */
    fun evaluate72hFungalRisk(
        relativeHumidityPct: Double,
        temperatureC: Double
    ): EwsRiskForecast {
        val rhFactor = max(0.0, (relativeHumidityPct - 70.0) / 25.0) // 0 at 70% RH, 1.0 at 95% RH
        val tempFactor = when {
            temperatureC in 24.0..32.0 -> 1.0
            temperatureC in 20.0..24.0 -> (temperatureC - 20.0) / 4.0
            temperatureC in 32.0..36.0 -> (36.0 - temperatureC) / 4.0
            else -> 0.1
        }

        val rawScore = min(1.0, rhFactor * tempFactor)
        val score = Math.round(rawScore * 100.0) / 100.0

        val (level, header, advisory) = when {
            score >= 0.75 -> Triple(
                "CRITICAL",
                "PERINGATAN DINI EWS: Risiko Ledakan Spora Patek Ekstrem (>75%) dalam 72 Jam!",
                "Kelembaban udara ${relativeHumidityPct.toInt()}% dan suhu ${temperatureC.toInt()}°C sangat kondusif untuk perkecambahan spora Colletotrichum. Lakukan penyemprotan protektif fungisida tembaga hidroksida / mankozeb sore ini."
            )
            score >= 0.50 -> Triple(
                "HIGH",
                "Waspada: Peningkatan Kelembaban Mikro Kebun.",
                "Periksa kebersihan parit drainase bedengan; hindari pemupukan daun berkadar nitrogen tinggi."
            )
            score >= 0.25 -> Triple(
                "MODERATE",
                "Kondisi Mikro Stabil.",
                "Pertahankan sanitasi gulma dan monitor gejala awal pada buah cabai tua."
            )
            else -> Triple(
                "LOW",
                "Risiko Spora Minimal (Cuaca Kering/Dingin).",
                "Tidak diperlukan tindakan fungisida mendesak."
            )
        }

        return EwsRiskForecast(
            relativeHumidityPct = relativeHumidityPct,
            temperatureC = temperatureC,
            sporulationRiskScore = score,
            riskLevel = level,
            alertHeader = header,
            preventiveSprayAdvisory = advisory
        )
    }
}
