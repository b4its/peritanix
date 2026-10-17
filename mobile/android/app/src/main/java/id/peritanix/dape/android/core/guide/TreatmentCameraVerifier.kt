package id.peritanix.dape.android.core.guide

import android.graphics.Bitmap

/**
 * Verifikator Hasil Perawatan Budidaya Cabai Berbasis Analisis Kamera (100% On-Device).
 *
 * Menginspeksi citra tanaman/buah cabai pada setiap tahapan budidaya untuk
 * mengevaluasi:
 *   1. Indeks Klorofil Hijau (Kesehatan vegetatif & tajuk)
 *   2. Indeks Kematangan Merah (Kematangan panen buah)
 *   3. Indeks Klorosis Kuning (Gejala defisiensi N/Mg atau vektor kutu kebul)
 *   4. Indeks Nekrosis Cacat Gelap (Bercak patek/antraknosa atau busuk buah)
 *   5. Skor Kesehatan Keseluruhan (0.0 – 100.0%)
 *
 * Memberikan verifikasi kepatuhan SOP DAPE serta rekomendasi tindakan preskriptif.
 */
object TreatmentCameraVerifier {

    enum class VerificationStatus(val displayName: String, val badgeColorHex: Long) {
        VERIFIED_OPTIMAL("SOP Optimal & Sangat Sehat", 0xFF2E7D32),
        VERIFIED_GOOD("Perawatan Baik & Stabil", 0xFF388E3C),
        ATTENTION_NEEDED("Perlu Perhatian / Pemangkasan", 0xFFF57C00),
        CRITICAL_RISK("Peringatan / Gejala Penyakit", 0xFFD32F2F)
    }

    enum class SampleCondition {
        OPTIMAL,
        GOOD,
        MILD_DEFECT,
        SEVERE_DISEASE
    }

    data class InspectionResult(
        val stageIndex: Int,
        val stageTitle: String,
        val treatmentName: String = "Perawatan Rutin",
        val hst: Int,
        val timestampMs: Long,
        val healthScore: Double,
        val greenPct: Double,
        val redPct: Double,
        val yellowPct: Double,
        val defectPct: Double,
        val status: VerificationStatus,
        val statusTitle: String,
        val diagnosisText: String,
        val prescriptiveAction: String,
        val sampledPixelsCount: Int
    )

    /**
     * Menganalisis Bitmap dari kamera perangkat untuk hasil perawatan tertentu.
     */
    fun analyzeBitmap(
        stage: ChiliGrowingGuide.Stage,
        bitmap: Bitmap,
        hst: Int,
        treatmentName: String = "Perawatan Rutin"
    ): InspectionResult {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return analyzePixels(stage, pixels, width, height, hst, treatmentName)
    }

    /**
     * Menganalisis buffer piksel ARGB (Pure Kotlin, unit-testable).
     */
    fun analyzePixels(
        stage: ChiliGrowingGuide.Stage,
        pixels: IntArray,
        width: Int,
        height: Int,
        hst: Int,
        treatmentName: String = "Perawatan Rutin"
    ): InspectionResult {
        require(width > 0 && height > 0) { "Lebar dan tinggi gambar harus > 0" }
        val total = width * height
        require(pixels.size >= total) { "Buffer piksel tidak mencukupi ukuran $width x $height" }

        // Gunakan stride untuk menjaga latensi < 100ms pada foto kamera beresolusi tinggi
        val stride = if (total > 200_000) 4 else if (total > 50_000) 2 else 1

        var greenCount = 0
        var redCount = 0
        var yellowCount = 0
        var darkDefectCount = 0
        var backgroundCount = 0
        var sampled = 0

        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val px = pixels[y * width + x]
                val r = ((px shr 16) and 0xFF) / 255.0
                val g = ((px shr 8) and 0xFF) / 255.0
                val b = (px and 0xFF) / 255.0
                sampled++

                val luma = 0.299 * r + 0.587 * g + 0.114 * b

                when {
                    // Lesi nekrotik gelap / antraknosa / busuk basah
                    luma < 0.20 || (r < 0.22 && g < 0.22 && b < 0.22 && luma < 0.25) -> {
                        darkDefectCount++
                    }
                    // Kematangan merah buah cabai (merah dominan)
                    r > 0.38 && r > g + 0.12 && r > b + 0.10 -> {
                        redCount++
                    }
                    // Klorosis daun / kuning pucat (R & G tinggi, B rendah)
                    r > 0.42 && g > 0.38 && b < 0.28 && (r - g < 0.15 && g - r < 0.15) -> {
                        yellowCount++
                    }
                    // Klorofil hijau tanaman sehat (G dominan atas R dan B)
                    g > r + 0.05 && g > b + 0.05 && luma in 0.15..0.78 -> {
                        greenCount++
                    }
                    else -> {
                        backgroundCount++
                    }
                }
                x += stride
            }
            y += stride
        }

        val effectiveCropPixels = (greenCount + redCount + yellowCount + darkDefectCount).coerceAtLeast(1)
        val greenPct = round1((greenCount.toDouble() / effectiveCropPixels) * 100.0)
        val redPct = round1((redCount.toDouble() / effectiveCropPixels) * 100.0)
        val yellowPct = round1((yellowCount.toDouble() / effectiveCropPixels) * 100.0)
        val defectPct = round1((darkDefectCount.toDouble() / effectiveCropPixels) * 100.0)

        // Hitung skor kesehatan (0..100) bergantung pada fase budidaya
        val healthScore = calculateHealthScore(stage.index, greenPct, redPct, yellowPct, defectPct)

        val status = evaluateStatus(healthScore, defectPct, yellowPct)
        val (diagnosis, prescriptive) = generateDiagnosisAndPrescription(
            stage = stage,
            status = status,
            score = healthScore,
            greenPct = greenPct,
            redPct = redPct,
            yellowPct = yellowPct,
            defectPct = defectPct,
            hst = hst,
            treatmentName = treatmentName
        )

        return InspectionResult(
            stageIndex = stage.index,
            stageTitle = stage.title,
            treatmentName = treatmentName,
            hst = hst,
            timestampMs = System.currentTimeMillis(),
            healthScore = healthScore,
            greenPct = greenPct,
            redPct = redPct,
            yellowPct = yellowPct,
            defectPct = defectPct,
            status = status,
            statusTitle = status.displayName,
            diagnosisText = diagnosis,
            prescriptiveAction = prescriptive,
            sampledPixelsCount = sampled
        )
    }

    /**
     * Menghitung skor kesehatan tanaman (0.0 – 100.0) disesuaikan dengan target fase tanaman.
     */
    private fun calculateHealthScore(
        stageIndex: Int,
        greenPct: Double,
        redPct: Double,
        yellowPct: Double,
        defectPct: Double
    ): Double {
        var baseScore = when (stageIndex) {
            1 -> { // Persemaian: Fokus kehijauan daun semai
                (greenPct * 1.2).coerceIn(0.0, 100.0)
            }
            2 -> { // Vegetatif: Tajuk hijau subur
                (greenPct * 1.15).coerceIn(0.0, 100.0)
            }
            3 -> { // Pembungaan: Keseimbangan tajuk
                (greenPct * 1.1).coerceIn(0.0, 100.0)
            }
            4 -> { // Pembuahan: Kombinasi hijau tajuk + buah membesar
                ((greenPct * 0.7) + (redPct * 0.5) + 30.0).coerceIn(0.0, 100.0)
            }
            5 -> { // Panen: Fokus dominasi warna merah
                ((redPct * 1.1) + (greenPct * 0.2)).coerceIn(0.0, 100.0)
            }
            else -> 75.0
        }

        // Penalti berat untuk lesi nekrotik / busuk patek
        baseScore -= (defectPct * 4.5)
        // Penalti untuk klorosis / kuning pucat
        baseScore -= (yellowPct * 2.0)

        return round1(baseScore.coerceIn(10.0, 100.0))
    }

    /**
     * Menentukan status verifikasi kepatuhan SOP DAPE.
     */
    private fun evaluateStatus(
        score: Double,
        defectPct: Double,
        yellowPct: Double
    ): VerificationStatus {
        return when {
            defectPct > 10.0 || score < 45.0 -> VerificationStatus.CRITICAL_RISK
            defectPct > 5.0 || yellowPct > 18.0 || score < 65.0 -> VerificationStatus.ATTENTION_NEEDED
            score >= 82.0 && defectPct <= 3.5 -> VerificationStatus.VERIFIED_OPTIMAL
            else -> VerificationStatus.VERIFIED_GOOD
        }
    }

    /**
     * Merumuskan diagnosis visual dan tindakan preskriptif agronomi.
     */
    private fun generateDiagnosisAndPrescription(
        stage: ChiliGrowingGuide.Stage,
        status: VerificationStatus,
        score: Double,
        greenPct: Double,
        redPct: Double,
        yellowPct: Double,
        defectPct: Double,
        hst: Int,
        treatmentName: String = "Perawatan Rutin"
    ): Pair<String, String> {
        val lower = treatmentName.lowercase()

        // Jika inspeksi ditujukan pada perawatan spesifik, berikan diagnosis agronomi presisi
        if (treatmentName != "Perawatan Rutin") {
            when {
                lower.contains("wiwil") || lower.contains("pangkas") -> {
                    return if (status == VerificationStatus.VERIFIED_OPTIMAL || status == VerificationStatus.VERIFIED_GOOD) {
                        Pair(
                            "Hasil wiwilan terverifikasi optimal: pangkal dahan bersih dari tunas air, sirkulasi aerasi kanopi terbuka (klorofil $greenPct%).",
                            "Pertahankan kebersihan ketiak daun bawah cabang Y untuk menjaga LAI < 3.5 dan mencegah jamur mikro."
                        )
                    } else {
                        Pair(
                            "Ditemukan sisa tunas liar atau daun tua menyentuh mulsa ($defectPct% defek), pemangkasan belum tuntas.",
                            "Segera selesaikan pewiwilan tunas samping bawah cabang Y dan angkat daun yang menyentuh tanah."
                        )
                    }
                }
                lower.contains("pupuk") || lower.contains("kalsium") || lower.contains("kno3") || lower.contains("nutrisi") || lower.contains("mkp") -> {
                    return if (status == VerificationStatus.VERIFIED_OPTIMAL || status == VerificationStatus.VERIFIED_GOOD) {
                        Pair(
                            "Hasil pemupukan terverifikasi berhasil: warna hijau daun pekat ($greenPct% klorofil), dinding sel kokoh tanpa klorosis.",
                            "Lanjutkan jadwal kocor fertigasi berimbang dan jaga kelembaban tanah 65-70%."
                        )
                    } else if (defectPct > 4.0) {
                        Pair(
                            "Terdeteksi defisiensi kalsium/nekrosis ($defectPct% defek) pada ujung buah atau tepi daun.",
                            "Kocor Kalsium Nitrat + Boron larut air segera dan periksa kestabilan pH perakaran tanah."
                        )
                    } else {
                        Pair(
                            "Daun menunjukkan gejala klorosis kekuningan ($yellowPct%), serapan nitrogen atau magnesium terhambat.",
                            "Tambahkan pupuk mikro klorofil dan kocor NPK seimbang untuk mempercepat fotosintesis."
                        )
                    }
                }
                lower.contains("panen") || lower.contains("petik") || lower.contains("sortasi") || lower.contains("grading") || lower.contains("asas black") -> {
                    return if (redPct >= 75.0 && defectPct <= 3.5) {
                        Pair(
                            "Hasil panen memenuhi kualifikasi mutu Grade A+: kematangan merah $redPct%, tangkai utuh segar, bebas patek.",
                            "Lanjutkan ke penimbangan, sterilisasi Asas Black 52°C, dan kemas dalam krat berventilasi."
                        )
                    } else if (defectPct > 4.0) {
                        Pair(
                            "Ditemukan cacat fisik / lesi antraknosa laten ($defectPct%) pada buah hasil petik.",
                            "Pisahkan ke keranjang BS afkir dan lakukan sterilisasi perendaman termoterapi 52°C segera."
                        )
                    } else {
                        Pair(
                            "Kematangan buah merah baru $redPct%, masih dominan semburat hijau ($greenPct%).",
                            "Tunda petik 2-3 hari untuk mencapai kematangan seragam > 85% merah menyala."
                        )
                    }
                }
                lower.contains("hama") || lower.contains("jamur") || lower.contains("proteksi") || lower.contains("patek") || lower.contains("thrips") || lower.contains("perangkap") -> {
                    return if (defectPct <= 3.5 && yellowPct <= 12.0) {
                        Pair(
                            "Proteksi tanaman berhasil: tajuk bersih tanpa lesi jamur aktif atau serangan hama (defek hanya $defectPct%).",
                            "Pertahankan monitoring perangkap likat kuning dan semprot preventif silika pelindung kutikula."
                        )
                    } else {
                        Pair(
                            "Terdeteksi lesi aktif nekrosis ($defectPct%) atau daun melengkung/kuning ($yellowPct%).",
                            "Segera aplikasikan fungisida tembaga/azoksistrobin atau insektisida abamektin sesuai SOP."
                        )
                    }
                }
            }
        }

        val diagnosis: String
        val prescriptive: String

        when (stage.index) {
            1 -> { // Persemaian
                if (status == VerificationStatus.VERIFIED_OPTIMAL || status == VerificationStatus.VERIFIED_GOOD) {
                    diagnosis = "Bibit berdaun sejati hijau segar ($greenPct%), batang tegak simetris tanpa etiolasi."
                    prescriptive = "Pertahankan penyiraman kabut pagi hari dan jaga kerapatan insect net pelindung bibit."
                } else if (defectPct > 5.0) {
                    diagnosis = "Terdeteksi bercak basah nekrosis ($defectPct%) pada bibit — indikasi rebah kecambah (Pythium)."
                    prescriptive = "Kurangi kelembaban media semai, buang bibit yang rebah, dan semprot fungisida tembaga konsentrasi rendah."
                } else {
                    diagnosis = "Daun bibit pucat/klorosis ($yellowPct%), pertumbuhan klorofil terhambat."
                    prescriptive = "Berikan paparan sinar matahari pagi bertahap dan kocor pupuk daun N-mikro konsentrasi 0,5 g/L."
                }
            }
            2 -> { // Vegetatif
                if (status == VerificationStatus.VERIFIED_OPTIMAL || status == VerificationStatus.VERIFIED_GOOD) {
                    diagnosis = "Tajuk tanaman berkembang optimal ($greenPct% hijau klorofil), mulsa rapi, tunas air di bawah Y bersih."
                    prescriptive = "Lanjutkan pemupukan kocor NPK 16-16-16 mingguan dan pastikan ajir mengikat batang dengan aman."
                } else if (defectPct > 5.0) {
                    diagnosis = "Terdeteksi bercak lesi nekrotik ($defectPct%) pada daun bawah."
                    prescriptive = "Pangkas daun tua yang menyentuh mulsa dan semprotkan fungisida mankozeb untuk sanitasi tajuk."
                } else {
                    diagnosis = "Daun muda mengalami klorosis/menguning ($yellowPct%), kemungkinan defisiensi Nitrogen atau serangan kutu kebul."
                    prescriptive = "Tambahkan pupuk NPK kocor seimbang dan periksa permukaan bawah daun dari keberadaan kutu kebul."
                }
            }
            3 -> { // Pembungaan
                if (status == VerificationStatus.VERIFIED_OPTIMAL || status == VerificationStatus.VERIFIED_GOOD) {
                    diagnosis = "Bunga mekar serentak, kelopak kokoh, tidak ditemukan rontok bunga atau lesi basah."
                    prescriptive = "Kunci fortifikasi pericarp: Semprot Kalsium Nitrat (2 g/L) + Boron (1 g/L) dan hentikan pupuk Urea."
                } else if (defectPct > 5.0) {
                    diagnosis = "Terdeteksi bercak cokelat busuk ($defectPct%) pada kelopak bunga — risiko blossom blight / antraknosa bunga."
                    prescriptive = "Semprot fungisida tembaga hidrokasida segera, kurangi intensitas penyiraman, dan buka ventilasi tajuk."
                } else {
                    diagnosis = "Kelopak bunga pucat atau rontok ($yellowPct% klorosis pada tangkai bunga)."
                    prescriptive = "Tingkatkan unsur Kalsium dan Fosfat (MKP), hindari stres kekeringan atau genangan pada parit bedengan."
                }
            }
            4 -> { // Pembuahan
                if (status == VerificationStatus.VERIFIED_OPTIMAL || status == VerificationStatus.VERIFIED_GOOD) {
                    diagnosis = "Buah cabai berkembang padat mulus, pericarp tebal mengkilap, bebas lesi patek (defek $defectPct%)."
                    prescriptive = "Aplikasi Kalium Nitrat (KNO3 Putih) 5 g/tanaman untuk bobot buah maksimal dan pasang perangkap lalat buah."
                } else if (defectPct > 4.0) {
                    diagnosis = "Terdeteksi bercak cincin konsentris nekrosis ($defectPct%) — gejala awal Antraknosa Colletotrichum!"
                    prescriptive = "Petik segera dan musnahkan buah sakit, semprot fungisida azoksistrobin + difenokonazol, dan jangan menyiram tajuk."
                } else {
                    diagnosis = "Pertumbuhan buah kurang seragam dengan semburat kusam ($yellowPct% klorosis tajuk)."
                    prescriptive = "Kocor Kalsium larut air untuk mencegah busuk pantat (Blossom End Rot) dan cukupi suplai air harian."
                }
            }
            5 -> { // Panen
                if (redPct >= 80.0 && defectPct <= 3.5) {
                    diagnosis = "Kematangan buah sangat tinggi ($redPct% merah pekat), kilap sempurna memenuhi kualifikasi Grade A+!"
                    prescriptive = "Petik pagi hari bersama tangkai utuh menggunakan gunting panen steril, lanjutkan ke perendaman Asas Black 52°C."
                } else if (defectPct > 4.0) {
                    diagnosis = "Ditemukan cacat fisik / bintik antraknosa laten ($defectPct%) pada permukaan buah panen."
                    prescriptive = "Pisahkan ke keranjang BS (afkir) agar tidak menulari buah sehat, lakukan sterilisasi air panas 52°C segera."
                } else {
                    diagnosis = "Kematangan merah baru mencapai $redPct%, masih terdapat semburat hijau ($greenPct%)."
                    prescriptive = "Tunda petik 2–3 hari untuk buah yang masih bersemburat hijau agar mencapai kematangan merah seragam > 85%."
                }
            }
            else -> {
                diagnosis = "Inspeksi visual tanaman tercatat dengan skor kesehatan $score%."
                prescriptive = "Lanjutkan pemantauan rutin dan sesuaikan perawatan dengan kondisi cuaca terkini."
            }
        }

        return Pair(diagnosis, prescriptive)
    }

    /**
     * Menghasilkan sampel simulasi piksel sintetis untuk demonstrasi & pengujian unit.
     */
    fun createSimulatedSample(
        stage: ChiliGrowingGuide.Stage,
        hst: Int,
        condition: SampleCondition = SampleCondition.OPTIMAL,
        treatmentName: String = "Perawatan Rutin"
    ): InspectionResult {
        val width = 60
        val height = 60
        val pixels = IntArray(width * height)

        val (greenRatio, redRatio, yellowRatio, defectRatio) = when (condition) {
            SampleCondition.OPTIMAL -> when (stage.index) {
                5 -> listOf(0.10, 0.85, 0.03, 0.02)
                4 -> listOf(0.40, 0.50, 0.06, 0.04)
                else -> listOf(0.85, 0.05, 0.08, 0.02)
            }
            SampleCondition.GOOD -> when (stage.index) {
                5 -> listOf(0.20, 0.72, 0.05, 0.03)
                4 -> listOf(0.50, 0.35, 0.10, 0.05)
                else -> listOf(0.72, 0.05, 0.18, 0.05)
            }
            SampleCondition.MILD_DEFECT -> when (stage.index) {
                5 -> listOf(0.35, 0.50, 0.07, 0.08)
                else -> listOf(0.55, 0.05, 0.30, 0.10)
            }
            SampleCondition.SEVERE_DISEASE -> listOf(0.30, 0.10, 0.35, 0.25)
        }

        // Sintesis warna piksel ARGB
        val cGreen = 0xFF2E7D32.toInt()
        val cRed = 0xFFD32F2F.toInt()
        val cYellow = 0xFFFBC02D.toInt()
        val cDark = 0xFF1B1B1B.toInt()

        val total = width * height
        val nGreen = (total * greenRatio).toInt()
        val nRed = (total * redRatio).toInt()
        val nYellow = (total * yellowRatio).toInt()

        for (i in 0 until total) {
            pixels[i] = when {
                i < nGreen -> cGreen
                i < nGreen + nRed -> cRed
                i < nGreen + nRed + nYellow -> cYellow
                else -> cDark
            }
        }

        return analyzePixels(stage, pixels, width, height, hst, treatmentName)
    }

    private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0
}
