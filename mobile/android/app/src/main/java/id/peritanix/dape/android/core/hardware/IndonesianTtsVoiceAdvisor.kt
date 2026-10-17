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
        actionAdvice: String,
        treatmentName: String = "Perawatan Rutin"
    ): String {
        val target = if (treatmentName == "Perawatan Rutin") "pada $stageTitle" else "untuk $treatmentName pada $stageTitle"
        return "Hasil inspeksi kamera $target: status $statusLabel, skor kesehatan ${score.toInt()} persen. Rekomendasi tindakan: $actionAdvice"
    }

    /**
     * Menyusun skrip narasi audio briefing harian misi kualitas cabai terbaik.
     */
    fun composeDailyMissionsBriefingScript(
        stageTitle: String,
        phaseName: String,
        hst: Int,
        score: Int,
        exportBadge: String,
        totalMissions: Int,
        completedMissions: Int,
        nextFocusTitle: String?
    ): String {
        val hstDesc = if (hst < 0) "kurang ${-hst} hari sebelum pindah tanam" else "$hst hari setelah tanam"
        val statusMisi = if (completedMissions >= totalMissions && totalMissions > 0) {
            "Hebat! Seluruh $totalMissions misi mutu telah tuntas seratus persen. Pertahankan standar ekspor ini."
        } else {
            val sisa = totalMissions - completedMissions
            val fokus = if (!nextFocusTitle.isNullOrBlank()) "Fokus prioritas berikutnya: $nextFocusTitle." else ""
            "Tersisa $sisa misi yang perlu diselesaikan. $fokus"
        }
        return "Briefing mutu harian DAPE. $stageTitle fase $phaseName pada $hstDesc. " +
            "Skor capaian mutu saat ini $score persen dengan predikat $exportBadge. $statusMisi"
    }

    /**
     * Menyusun narasi audio langkah operasional SOP untuk satu misi kualitas.
     */
    fun composeMissionSopScript(
        title: String,
        qualityTarget: String,
        howToExecute: String,
        targetMetric: String,
        optimalTiming: String = "",
        executionSteps: List<String> = emptyList(),
        commonMistakesToAvoid: List<String> = emptyList(),
        safetyGear: List<String> = emptyList(),
        durationMinutes: Int = 0,
        troubleshootingTip: String = "",
        qualitySigns: String = ""
    ): String {
        val timingDesc = if (optimalTiming.isNotBlank()) "Waktu pelaksanaan optimal: $optimalTiming. " else ""
        val safetyDesc = if (safetyGear.isNotEmpty()) "Gunakan alat pelindung: ${safetyGear.joinToString(", ")}. " else ""
        val durationDesc = if (durationMinutes > 0) "Durasi operasional: $durationMinutes menit. " else ""
        val stepsDesc = if (executionSteps.isNotEmpty()) {
            "Langkah pengerjaan: " + executionSteps.mapIndexed { idx, s -> "Langkah ${idx + 1}, $s" }.joinToString(". ") + ". "
        } else {
            "Langkah pelaksanaan di kebun: $howToExecute. "
        }
        val mistakesDesc = if (commonMistakesToAvoid.isNotEmpty()) {
            "Pantangan penting: ${commonMistakesToAvoid.first()}. "
        } else ""
        val troubleshootingDesc = if (troubleshootingTip.isNotBlank()) {
            "Solusi kendala lapangan: $troubleshootingTip. "
        } else ""
        val signsDesc = if (qualitySigns.isNotBlank()) {
            "Tanda kualitas fisik yang dihasilkan: $qualitySigns. "
        } else ""

        return "Panduan Standar Operasional: $title. Sasaran pilar mutu: $qualityTarget. " +
            timingDesc + safetyDesc + durationDesc + stepsDesc + mistakesDesc + troubleshootingDesc + signsDesc + "Target parameter terukur: $targetMetric."
    }

    /**
     * Menyusun narasi audio untuk satu langkah operasional pada mode panduan bertahap (wizard).
     */
    fun composeStepScript(
        stepNumber: Int,
        totalSteps: Int,
        stepText: String,
        safetyReminder: String = ""
    ): String {
        val safetyPart = if (safetyReminder.isNotBlank()) "Peringatan keselamatan: $safetyReminder. " else ""
        return "Langkah $stepNumber dari $totalSteps. $stepText. $safetyPart"
    }

    /**
     * Menyusun narasi audio untuk pemeriksaan kesiapan kerja (pre-flight checklist).
     */
    fun composePreFlightScript(
        safetyGear: List<String>,
        missionTitle: String
    ): String {
        val gear = if (safetyGear.isNotEmpty()) safetyGear.joinToString(", ") else "topi pelindung dan sepatu bot"
        return "Pemeriksaan kesiapan sebelum pengerjaan misi $missionTitle. Pastikan APD lengkap telah terpasang: $gear. Periksa cuaca aman dan siapkan peralatan dalam kondisi bersih."
    }

    /**
     * Menyusun narasi audio panduan pencampuran tangki semprot metode baku W-A-L-E-S.
     */
    fun composeMixingGuideScript(
        tankLiters: Int,
        dosePerTank: String,
        unit: String
    ): String {
        return "Panduan pencampuran tangki semprot $tankLiters Liter. Dosis: $dosePerTank $unit. " +
            "Urutan baku: Pertama, isi tangki setengahnya dengan air bersih. Kedua, larutkan tepung bila ada. " +
            "Ketiga, tuang cairan pekat atau pupuk. Keempat, tuang cairan emulsi. " +
            "Kelima, tambahkan perekat di akhir dan penuhi air hingga $tankLiters Liter. Aduk homogen."
    }
}
