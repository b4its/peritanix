package id.peritanix.dape.android.core.physics

import id.peritanix.dape.physics.Physics

/**
 * FR-1.3 On-Device TWI Local Compute Engine.
 *
 * Combines in-situ slope angle (beta) from [ClinometerSensor] with the cached
 * upslope contributing area (a) from Room DB:
 *   TWI = ln(a / tan(beta))
 *
 * Enforces safety bound floors (minimum 0.1 degree slope) and renders bed height
 * instructions (e.g. 50 cm high beds for critical pooling zones to prevent
 * Phytophthora capsici and Ralstonia solanacearum).
 */
object TwiLocalCompute {

    data class TwiAssessment(
        val upslopeAreaM2PerM: Double,
        val slopeDeg: Double,
        val twiValue: Double,
        val drainageRisk: String, // LOW | MODERATE | HIGH | CRITICAL
        val requiredBedHeightCm: Int,
        val warningPhytophthora: Boolean,
        val recommendation: String
    )

    fun compute(upslopeAreaM2PerM: Double, slopeDeg: Double): TwiAssessment {
        val twi = Physics.twiFromDegrees(upslopeAreaM2PerM, slopeDeg)
        val risk = Physics.drainageRiskClass(twi)
        val bedHeight = Physics.requiredBedHeightCm(twi)
        val warn = twi >= 8.0

        val rec = when (risk) {
            "CRITICAL" -> "Zona genangan air ekstrem! Wajib bedengan tinggi 50 cm, parit pembuangan primer kedalaman 60 cm, dan hindari varietas rentan layu."
            "HIGH" -> "Risiko genangan tinggi. Wajib bedengan tinggi 40 cm dan perbaikan drainase lateral untuk mencegah Phytophthora capsici."
            "MODERATE" -> "Kondisi kelembaban ideal. Pertahankan bedengan standar 30 cm dengan mulsa plastik perak."
            else -> "Lahan drainase cepat / lereng kering. Bedengan 20 cm cukup; antisipasi kekurangan air saat fase pembungaan."
        }

        return TwiAssessment(
            upslopeAreaM2PerM = upslopeAreaM2PerM,
            slopeDeg = slopeDeg,
            twiValue = Math.round(twi * 100.0) / 100.0,
            drainageRisk = risk,
            requiredBedHeightCm = bedHeight,
            warningPhytophthora = warn,
            recommendation = rec
        )
    }
}
