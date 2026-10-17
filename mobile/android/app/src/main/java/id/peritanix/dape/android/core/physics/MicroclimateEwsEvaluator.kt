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

    /** Irrigation plan derived from the 72 h risk + a soil-moisture reading. */
    data class IrrigationPlan(
        val minutesPerBed: Double,
        val intervalHours: Int,
        val rationale: String
    )

    /**
     * TAHAP 4.3 — Irrigation duration (menit/bedengan) that also hedges fungal
     * risk: when sporulation risk is high we shorten and retreat watering
     * (drier canopy), when the soil is already moist we cut it; when the canopy is
     * dry-but-risky we water at dawn only to avoid a humid evening.
     *
     * @param riskScore 0..1 from [evaluate72hFungalRisk].
     * @param soilMoisturePct 0..100 (volumetric), 60 is field capacity for loam.
     * @param baseMinutes the nominal full-watering duration for one bed.
     */
    fun recommendIrrigation(
        riskScore: Double,
        soilMoisturePct: Double,
        baseMinutes: Double = 20.0
    ): IrrigationPlan {
        // Moisture deficit scaling: drier soil → longer, wetter → shorter.
        val moistureFactor = when {
            soilMoisturePct >= 65.0 -> 0.4   // already near field capacity → light
            soilMoisturePct >= 50.0 -> 1.0
            soilMoisturePct >= 35.0 -> 1.3
            else -> 1.6                       // quite dry
        }
        // Fungal-risk damping: high risk ⇒ shorter watering + more frequent checks.
        val riskFactor = 1.0 - 0.35 * riskScore.coerceIn(0.0, 1.0)
        val minutes = (baseMinutes * moistureFactor * riskFactor).coerceIn(5.0, 45.0)
        val rounded = Math.round(minutes * 10.0) / 10.0

        val interval = when {
            riskScore >= 0.75 -> 24
            riskScore >= 0.5 -> 36
            soilMoisturePct < 35.0 -> 24
            else -> 48
        }
        val rationale = when {
            riskScore >= 0.75 ->
                "Risiko jamur tinggi: siram singkat $rounded menit/bedengan sekali per hari (pagi) agar tajuk cepat kering — hindari kelembaban malam."
            soilMoisturePct >= 65.0 ->
                "Tanah sudah lembap (${"%.0f".format(soilMoisturePct)}%): siram ringan $rounded menit/bedengan untuk mencegah busuk akar/Phytophthora."
            soilMoisturePct < 35.0 ->
                "Tanah kering (${"%.0f".format(soilMoisturePct)}%): siram $rounded menit/bedengan tiap $interval jam untuk cegah rontok bunga."
            else ->
                "Kondisi normal: siram $rounded menit/bedengan tiap $interval jam sesuai jadwal pagi."
        }
        return IrrigationPlan(minutesPerBed = rounded, intervalHours = interval, rationale = rationale)
    }
}
