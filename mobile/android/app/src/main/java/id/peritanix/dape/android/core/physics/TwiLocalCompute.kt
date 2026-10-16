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

    data class TwiCell(
        val row: Int,
        val col: Int,
        val upslopeM2: Double,
        val slopeDeg: Double,
        val twi: Double,
        val riskClass: String
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

    /**
     * Computes a 2D topographical grid of TWI values across a micro-plot bed segment
     * to render an offline color-coded risk contour map directly on the device.
     */
    fun computeLocalGrid(
        baseUpslope: Double,
        baseSlope: Double,
        rows: Int = 4,
        cols: Int = 4
    ): List<List<TwiCell>> {
        val grid = mutableListOf<List<TwiCell>>()
        for (r in 0 until rows) {
            val rowList = mutableListOf<TwiCell>()
            val rowSlope = (baseSlope * (1.0 + (r - rows / 2.0) * 0.15)).coerceAtLeast(0.1)
            for (c in 0 until cols) {
                val cellUpslope = (baseUpslope * (0.7 + (r * cols + c) * (0.6 / (rows * cols)))).coerceAtLeast(10.0)
                val cellTwi = Physics.twiFromDegrees(cellUpslope, rowSlope)
                val risk = Physics.drainageRiskClass(cellTwi)
                rowList.add(
                    TwiCell(
                        row = r,
                        col = c,
                        upslopeM2 = Math.round(cellUpslope * 10.0) / 10.0,
                        slopeDeg = Math.round(rowSlope * 10.0) / 10.0,
                        twi = Math.round(cellTwi * 100.0) / 100.0,
                        riskClass = risk
                    )
                )
            }
            grid.add(rowList)
        }
        return grid
    }
}
