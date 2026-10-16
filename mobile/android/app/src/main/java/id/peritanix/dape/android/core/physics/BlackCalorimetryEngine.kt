package id.peritanix.dape.android.core.physics

import id.peritanix.dape.physics.Physics

/**
 * FR-4.1 Volumetric Calorimetry Engine & Indonesian Voice Guidance.
 *
 * Implements Asas Black thermodynamics to achieve an exact 52.0 °C mixture without
 * needing thermometers on remote farms:
 *   V_didih * (T_didih - 52) = V_sumur * (52 - T_sumur)
 * where T_didih = 100 - 0.0033 * h (elevation in meters).
 *
 * Provides:
 *   1. Precise gayung volume ratio.
 *   2. Bahasa Indonesia Text-to-Speech (TTS) script generation.
 *   3. 180-second (3-minute) immersion sterilization timer state.
 */
object BlackCalorimetryEngine {

    const val TARGET_STERILIZATION_C = 52.0
    const val IMMERSION_DURATION_SECONDS = 180 // 3 minutes

    data class CalorimetryGuide(
        val elevationM: Double,
        val localBoilingPointC: Double,
        val wellWaterTempC: Double,
        val boilingParts: Double,
        val wellParts: Double,
        val ratioText: String,
        val voiceInstructionId: String,
        val countdownSeconds: Int = IMMERSION_DURATION_SECONDS
    )

    fun calculate(
        elevationM: Double,
        wellWaterTempC: Double = 26.0,
        gayungWellScale: Double = 9.0
    ): CalorimetryGuide {
        val r = Physics.hotWaterRatio(
            elevationM = elevationM,
            wellTempC = wellWaterTempC,
            targetC = TARGET_STERILIZATION_C,
            scale = gayungWellScale
        )

        val roundedBoil = Math.round(r.vBoilParts * 10.0) / 10.0
        val roundedWell = Math.round(r.vWellParts)

        val voiceText = "Perhatian. Pada ketinggian ${elevationM.toInt()} meter, titik didih air adalah ${"%.1f".format(r.boilingPointC)} derajat Celsius. " +
            "Untuk sterilisasi spora patek tepat 52 derajat, campurkan $roundedBoil gayung air mendidih dengan $roundedWell gayung air sumur. " +
            "Rendam buah cabai selama 3 menit penuh dan aduk perlahan."

        return CalorimetryGuide(
            elevationM = elevationM,
            localBoilingPointC = Math.round(r.boilingPointC * 100.0) / 100.0,
            wellWaterTempC = wellWaterTempC,
            boilingParts = roundedBoil,
            wellParts = roundedWell.toDouble(),
            ratioText = "$roundedBoil gayung didih : $roundedWell gayung sumur",
            voiceInstructionId = voiceText,
            countdownSeconds = IMMERSION_DURATION_SECONDS
        )
    }
}
