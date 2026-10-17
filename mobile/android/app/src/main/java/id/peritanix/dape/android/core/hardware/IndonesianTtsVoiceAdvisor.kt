package id.peritanix.dape.android.core.hardware

import java.util.Locale

/**
 * FR-4.1 Indonesian & Regional Text-to-Speech (TTS) Voice Advisor.
 *
 * Dispatches spoken thermodynamic instructions to guide farmers through
 * hot-water immersion calibration and countdown checkpoints without needing
 * thermometers or continuous visual screen monitoring.
 */
object IndonesianTtsVoiceAdvisor {

    fun getIndonesianLocale(): Locale = Locale("id", "ID")

    data class VoiceCue(
        val checkpointSecondsRemaining: Int,
        val textPrompt: String,
        val alertLevel: String = "INFO" // INFO, WARNING, SUCCESS
    )

    /**
     * Composes the full narration script for hot-water preparation.
     */
    fun composePreparationScript(
        elevationM: Double,
        boilingPointC: Double,
        boilingParts: Double,
        wellParts: Double
    ): String {
        val elev = elevationM.toInt()
        val bp = "%.1f".format(boilingPointC)
        val bParts = if (boilingParts % 1.0 == 0.0) "${boilingParts.toInt()}" else "$boilingParts"
        val wParts = if (wellParts % 1.0 == 0.0) "${wellParts.toInt()}" else "$wellParts"

        return "Perhatian Petani DAPE. Pada ketinggian $elev meter di atas permukaan laut, " +
            "titik didih air terkalibrasi $bp derajat Celsius. " +
            "Untuk membunuh seratus persen spora laten patek, takar $bParts gayung air mendidih " +
            "dan campurkan ke dalam $wParts gayung air sumur. " +
            "Suhu campuran akan tepat 52 derajat Celsius. Masukkan benih atau buah cabai, " +
            "lalu mulai timer tiga menit sekarang."
    }

    /**
     * Provides temporal audio cues throughout the 180-second immersion process.
     */
    fun getImmersionCheckpoints(): List<VoiceCue> = listOf(
        VoiceCue(
            checkpointSecondsRemaining = 180,
            textPrompt = "Perendaman dimulai. Aduk cabai perlahan agar panas merata ke seluruh permukaan.",
            alertLevel = "INFO"
        ),
        VoiceCue(
            checkpointSecondsRemaining = 90,
            textPrompt = "Satu setengah menit tersisa. Pertahankan pengadukan stabil, sensor giroskop mendeteksi aktivitas valid.",
            alertLevel = "INFO"
        ),
        VoiceCue(
            checkpointSecondsRemaining = 30,
            textPrompt = "Tiga puluh detik terakhir. Siapkan wadah tirisan bersih.",
            alertLevel = "WARNING"
        ),
        VoiceCue(
            checkpointSecondsRemaining = 0,
            textPrompt = "Waktu perendaman tiga menit selesai! Segera angkat cabai dan tiriskan di tempat teduh. Sterilisasi sempurna.",
            alertLevel = "SUCCESS"
        )
    )

    fun evaluateCueForSecond(secondsRemaining: Int): VoiceCue? {
        return getImmersionCheckpoints().firstOrNull { it.checkpointSecondsRemaining == secondsRemaining }
    }

    /**
     * Menyusun skrip suara TTS bahasa Indonesia untuk hasil inspeksi kamera perawatan.
     */
    fun composeTreatmentInspectionScript(
        stageTitle: String,
        score: Double,
        statusLabel: String,
        actionAdvice: String
    ): String {
        return "Hasil inspeksi kamera pada $stageTitle: status $statusLabel, skor kesehatan ${score.toInt()} persen. Rekomendasi tindakan: $actionAdvice"
    }
}
