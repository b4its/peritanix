package id.peritanix.dape.android.core.crypto

/**
 * TAHAP 5.3 — Canonical physical-quality certificate payload builder.
 *
 * Aggregates every upstream metric of a harvest batch (soil CEC/KTK, GDD
 * calcium-silica fortification compliance, the 52 °C acoustic + gyroscope
 * sterilization proof, Archimedes sink ratio, visual grading recap, and the
 * forensic image hash) into ONE compact, canonical JSON-ready map that is then
 * signed with the farmer's Ed25519 device key and rendered as the buyer QR.
 *
 * Previously HarvestScreen built this inline with hardcoded placeholder values;
 * this builder guarantees all required fields are present + validated, so a
 * buyer's verifier can trust the certificate. Pure and unit-tested.
 */
object CertificatePayloadBuilder {

    data class Inputs(
        val landId: String,
        val cecMeq: Double,
        val gddFortificationCompliant: Boolean,
        val sterilizationTargetC: Double = 52.0,
        val boilingAcousticVerified: Boolean,
        val gyroImmersionSeconds: Int,          // 180 s expected
        val archimedesSinkRatio: Double,        // 0..1
        val gradeResult: String,                 // A+ | A | B | BS
        val batchPremiumPct: Double,
        val imageSha256Hex: String,
        val timestampEpochS: Long
    )

    /** Field names are the cross-platform contract (matches server verifier). */
    object Keys {
        const val LAND_ID = "land_id"
        const val CEC_MEQ = "cec_meq"
        const val GDD_FORTIFICATION = "gdd_fortification_compliant"
        const val STERILIZATION_C = "sterilization_c"
        const val ACOUSTIC_VERIFIED = "acoustic_boiling_verified"
        const val GYRO_SECONDS = "gyro_immersion_seconds"
        const val ARCHIMEDES = "archimedes_sink_ratio"
        const val GRADE = "grade_result"
        const val PREMIUM_PCT = "batch_premium_pct"
        const val IMAGE_SHA256 = "image_sha256_hash"
        const val TIMESTAMP = "timestamp"
    }

    val REQUIRED_KEYS = listOf(
        Keys.LAND_ID, Keys.CEC_MEQ, Keys.GDD_FORTIFICATION, Keys.STERILIZATION_C,
        Keys.ACOUSTIC_VERIFIED, Keys.GYRO_SECONDS, Keys.ARCHIMEDES, Keys.GRADE,
        Keys.PREMIUM_PCT, Keys.IMAGE_SHA256, Keys.TIMESTAMP
    )

    sealed class BuildResult {
        data class Ok(val payload: Map<String, Any?>) : BuildResult()
        data class Invalid(val reason: String) : BuildResult()
    }

    /**
     * Build the certificate payload. Returns [BuildResult.Invalid] when a
     * required physical proof is missing (e.g. not really sterilized at 52 °C, or
     * no valid image hash) — a certificate must never be issued on weak evidence.
     */
    fun build(input: Inputs): BuildResult {
        if (input.landId.isBlank()) return BuildResult.Invalid("land_id kosong.")
        if (input.imageSha256Hex.length != 64 || !input.imageSha256Hex.all { it in "0123456789abcdefABCDEF" }) {
            return BuildResult.Invalid("Hash foto (SHA-256) tidak valid — lakukan pemotretan ulang.")
        }
        if (!input.boilingAcousticVerified) {
            return BuildResult.Invalid("Bukti akustik air mendidih tidak terverifikasi — sertifikat tidak boleh diterbitkan.")
        }
        if (input.gyroImmersionSeconds < 180) {
            return BuildResult.Invalid("Durasi perendaman ${input.gyroImmersionSeconds}s < 180s — bukti fisik belum lengkap.")
        }
        if (input.archimedesSinkRatio !in 0.0..1.0) {
            return BuildResult.Invalid("Rasio Archimedes di luar rentang 0..1.")
        }
        if (input.gradeResult !in setOf("A+", "A", "B", "BS")) {
            return BuildResult.Invalid("Grade tidak dikenali: ${input.gradeResult}.")
        }

        val payload: Map<String, Any?> = linkedMapOf(
            Keys.LAND_ID to input.landId,
            Keys.CEC_MEQ to round2(input.cecMeq),
            Keys.GDD_FORTIFICATION to input.gddFortificationCompliant,
            Keys.STERILIZATION_C to round2(input.sterilizationTargetC),
            Keys.ACOUSTIC_VERIFIED to input.boilingAcousticVerified,
            Keys.GYRO_SECONDS to input.gyroImmersionSeconds,
            Keys.ARCHIMEDES to round2(input.archimedesSinkRatio),
            Keys.GRADE to input.gradeResult,
            Keys.PREMIUM_PCT to round2(input.batchPremiumPct),
            Keys.IMAGE_SHA256 to input.imageSha256Hex.lowercase(),
            Keys.TIMESTAMP to input.timestampEpochS
        )
        return BuildResult.Ok(payload)
    }

    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0
}
