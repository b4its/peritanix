package id.peritanix.dape.android.core.physics

/**
 * FR-3.2 Pericarp Fortification Lock & High-Nitrogen Input Blocking.
 *
 * Intervenes during the critical chili transition & flowering phase (36 - 60 HST / GDD >= 450):
 *   1. Triggers FORTIFICATION_LOCKED state when anthesis / flowering threshold is reached.
 *   2. Enforces mandatory pericarp cell-wall strengthening stoichiometry:
 *      - Calcium Nitrate (Ca(NO3)2): 2.5 g/L
 *      - Boron (B): 0.5 g/L
 *      - Silicic Acid (H4SiO4): 1.0 g/L
 *   3. Enforces hard validation blocking: completely blocks logging and warns in red
 *      if a farmer attempts to apply high-Nitrogen fertilizer (Urea / ZA / N > 16%)
 *      which weakens fruit pericarp cell walls and triggers blossom-end rot & anthracnose.
 */
object PericarpFortificationManager {

    const val GDD_ANTHESIS_THRESHOLD = 450.0
    const val HST_FLOWERING_START = 36
    const val HST_FLOWERING_END = 60

    data class StoichiometryPrescription(
        val calciumNitrateGPerL: Double = 2.5,
        val boronGPerL: Double = 0.5,
        val silicicAcidGPerL: Double = 1.0,
        val frequencyDays: Int = 5,
        val targetPhysiology: String = "Penebalan lamela tengah dinding sel perikarp buah cabai"
    )

    data class FortificationStatus(
        val isLocked: Boolean,
        val phaseName: String,
        val prescription: StoichiometryPrescription,
        val advisory: String
    )

    data class FertilizerValidation(
        val isAllowed: Boolean,
        val isCriticalBlock: Boolean,
        val warningMessage: String,
        val correctiveAction: String
    )

    /**
     * Evaluates whether the plot is in the FORTIFICATION_LOCKED flowering stage.
     */
    fun evaluateLockStatus(
        currentPhase: String,
        accumulatedGdd: Double,
        hst: Int
    ): FortificationStatus {
        val locked = currentPhase == "FORTIFICATION_LOCKED" ||
                currentPhase.equals("FLOWERING", ignoreCase = true) ||
                accumulatedGdd >= GDD_ANTHESIS_THRESHOLD ||
                (hst in HST_FLOWERING_START..HST_FLOWERING_END)

        val advisory = if (locked) {
            "Fase Antesis Terkunci (FORTIFICATION_LOCKED): Tanaman memasuki pembungaan puncak & inisiasi buah. " +
            "Dilarang keras menambah Urea/N tinggi! Wajib berikan Kalsium Nitrat + Boron + Asam Silikat untuk mengunci pektin dinding buah."
        } else {
            "Fase Normal ($currentPhase): Lanjutkan panduan pemupukan sesuai jadwal fase vegetatif / pengisian."
        }

        return FortificationStatus(
            isLocked = locked,
            phaseName = if (locked) "FORTIFICATION_LOCKED" else currentPhase,
            prescription = StoichiometryPrescription(),
            advisory = advisory
        )
    }

    /**
     * Validates proposed fertilizer entry. If fortification is locked, blocks any high-N fertilizers.
     */
    fun validateFertilizerProposal(
        fertilizerName: String,
        nitrogenPct: Double,
        isLocked: Boolean
    ): FertilizerValidation {
        val nameLower = fertilizerName.lowercase().trim()
        val isHighN = nitrogenPct > 16.0 ||
                nameLower.contains("urea") ||
                nameLower.contains("za") ||
                nameLower.contains("ammonium")

        if (isLocked && isHighN) {
            return FertilizerValidation(
                isAllowed = false,
                isCriticalBlock = true,
                warningMessage = "BLOKIR KERAS: Pemupukan Nitrogen tinggi ($fertilizerName / ${nitrogenPct}%) dilarang pada fase antesis! Memperlemah kutikula & memicu rontok bunga / patek.",
                correctiveAction = "Ganti seketika dengan Kalsium Nitrat 2.5 g/L + Boron 0.5 g/L + Asam Silikat 1.0 g/L."
            )
        }

        return FertilizerValidation(
            isAllowed = true,
            isCriticalBlock = false,
            warningMessage = "Valid: Formula pemupukan sesuai dengan toleransi fase tanaman.",
            correctiveAction = "Lanjutkan aplikasi sesuai dosis tertera."
        )
    }
}
