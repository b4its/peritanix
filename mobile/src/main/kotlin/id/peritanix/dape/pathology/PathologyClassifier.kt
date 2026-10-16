package id.peritanix.dape.pathology

/**
 * On-device differential diagnosis for chili leaf curling & lesions (Opsi 1 / TAHAP 3).
 *
 * Deterministic morphology rules that distinguish:
 * 1. Thrips (upward curling, canoe shape, silvery sheen underside) -> Abamektin
 * 2. Mites/Tungau (downward curling, inverted spoon shape, bronze coloration) -> Bifenazat/Piridaben
 * 3. Begomovirus/Gemini (yellow mosaic, thickened vein clearing, stunting) -> Sanitasi + Imidakloprid
 * 4. Antraknosa (sunken concentric dark necrotic lesions with spore mass) -> Mankozeb + Azoxystrobin
 */
object PathologyClassifier {

    data class CurlDiagnosis(
        val condition: String,
        val title: String,
        val description: String,
        val activeIngredient: String,
        val dosagePerLiter: String,
        val sprayingInstructions: String,
        val organicAlternative: String,
        val confidence: Double,
        val symptoms: List<String>,
    )

    fun differentiateLeafCurl(
        curlDirection: String = "none",     // "up", "down", "none"
        undersideColor: String = "normal",   // "silvery", "bronze", "normal"
        mosaicVeins: Boolean = false,
        sunkenLesions: Boolean = false,
        severityPct: Double = 0.0,
    ): CurlDiagnosis {
        // 1. Thrips: upward curling, silvery underside
        if (curlDirection == "up" || undersideColor == "silvery") {
            return CurlDiagnosis(
                condition = "Keriting_Thrips",
                title = "Keriting Daun akibat Thrips (Thrips parvispinus)",
                description = "Daun mengeriting ke atas menyerupai perahu/mangkuk, permukaan bawah keperakan mengkilap akibat sel epidermis dihisap cairan selnya.",
                activeIngredient = "Abamektin 18 g/L atau Spinetoram 120 g/L",
                dosagePerLiter = "0.25 – 0.5 mL per liter air",
                sprayingInstructions = "Semprot pagi hari (06:00–08:30) saat thrips aktif di pucuk muda dan ketiak daun. Wajib membasahi permukaan bawah daun.",
                organicAlternative = "Ekstrak daun mimba (Azadirachtin 0.3%) + tembakau, atau pelepasan predator alami Orius sp.",
                confidence = if (curlDirection == "up" && undersideColor == "silvery") 0.95 else 0.82,
                symptoms = listOf(
                    "Helaian daun melengkung ke atas (boat/canoe shape)",
                    "Permukaan bawah daun tampak keperakan atau bercak garis perak",
                    "Pucuk daun mengecil, kaku, dan pertumbuhan tunas terhambat",
                ),
            )
        }

        // 2. Mites / Tungau: downward curling, spoon-shaped, bronze coloration
        if (curlDirection == "down" || undersideColor == "bronze") {
            return CurlDiagnosis(
                condition = "Keriting_Mites",
                title = "Keriting Daun akibat Tungau / Mites (Polyphagotarsonemus latus)",
                description = "Daun mengeriting ke bawah melengkung cembung seperti sendok terbalik, tekstur daun kaku tebal dan rapuh, bagian bawah berwarna kecokelatan/tembaga.",
                activeIngredient = "Akarisida (Bifenazat 240 g/L atau Piridaben 135 g/L / Abamektin)",
                dosagePerLiter = "0.5 – 1.0 mL per liter air",
                sprayingInstructions = "Semprot sore hari (16:00–18:00) dengan tekanan nozzle halus berkabut ke balik daun muda. Wajib rotasi golongan kimia tiap 2 aplikasi.",
                organicAlternative = "Belerang mikronize (Sulfur 80%) dosis 2 g/L air atau semprot jamur Beauveria bassiana.",
                confidence = if (curlDirection == "down" || undersideColor == "bronze") 0.94 else 0.80,
                symptoms = listOf(
                    "Tepi daun melengkung ke bawah (sendok terbalik)",
                    "Warna permukaan bawah daun berubah kecokelatan tembaga (bronze/russet)",
                    "Daun menjadi kaku, tebal, dan mudah patah saat ditekuk",
                ),
            )
        }

        // 3. Begomovirus / Gemini: yellow mosaic, vein clearing
        if (mosaicVeins) {
            return CurlDiagnosis(
                condition = "Begomovirus",
                title = "Virus Kuning / Gemini Virus (Begomovirus / Pepper Yellow Leaf Curl)",
                description = "Klorosis kuning cerah menyolok berpola mosaik di antara tulang daun, tulang daun menebal kehijauan, daun mengecil dan berkerut rimbun.",
                activeIngredient = "Pengendali Vektor Kutu Kebul (Imidakloprid 200 g/L atau Tiametoksam 25 WG)",
                dosagePerLiter = "0.25 – 0.5 g/L air untuk semprot vektor",
                sprayingInstructions = "Virus sistemik tidak bisa disembuhkan kimiawi. Segera cabut dan bakar tanaman bergejala berat. Semprot insektisida vektor pada tanaman sekitarnya.",
                organicAlternative = "Pasang perangkap kuning berperekat (yellow sticky trap) 40 lembar/ha + gunakan mulsa plastik perak.",
                confidence = 0.96,
                symptoms = listOf(
                    "Bercak belang kuning menyolok berpola mosaik",
                    "Tulang daun menebal dan jernih (vein clearing/thickening)",
                    "Pertumbuhan ruas pucuk memendek membentuk tajuk seperti sapu/kerdil",
                ),
            )
        }

        // 4. Antraknosa: sunken dark concentric lesions
        if (sunkenLesions) {
            return CurlDiagnosis(
                condition = "Antraknosa",
                title = "Patek / Antraknosa (Colletotrichum capsici / gloeosporioides)",
                description = "Bercak nekrotik gelap cekung melingkar dengan lingkaran konsentris titik spora hitam/oranye pada buah atau daun.",
                activeIngredient = "Fungisida Sistemik + Kontak (Mankozeb 80% + Azoksistrobin 250 g/L atau Difenokonazol)",
                dosagePerLiter = "2.0 – 2.5 g per liter air",
                sprayingInstructions = "Semprot sore hari (16:00–18:00) saat cuaca teduh. Petik dan musnahkan semua buah yang memiliki bercak patek sebelum penyemprotan.",
                organicAlternative = "Kocor Trichoderma harzianum ke perakaran tanah + semprot ekstrak rimpang lengkuas dan kunyit.",
                confidence = 0.95,
                symptoms = listOf(
                    "Bercak cekung cokelat kehitaman berbentuk bulat konsentris",
                    "Permukaan lesi memproduksi massa spora seperti lendir oranye saat basah",
                    "Buah layu, keriput, dan gugur prematur",
                ),
            )
        }

        // 5. Default: Sehat atau defisiensi ringan
        return if (severityPct > 10.0) {
            CurlDiagnosis(
                condition = "Defisiensi_Nutrisi",
                title = "Defisiensi Hara Mikro / Stres Fisiologis",
                description = "Klorosis ringan non-spesifik. Periksa pH tanah dan kecukupan Nitrogen/Magnesium sebelum aplikasi pestisida.",
                activeIngredient = "Pupuk Daun Mikro Komplit (Fe, Mg, Zn, B)",
                dosagePerLiter = "1.0 – 2.0 g/L air semprot kabut",
                sprayingInstructions = "Semprot pagi hari saat stomata daun terbuka penuh.",
                organicAlternative = "Aplikasi asam amino hayati atau pupuk organik cair (POC) rebung/kelor.",
                confidence = 0.70,
                symptoms = listOf("Klorosis antar tulang daun", "Tidak ditemukan tanda serangan hama"),
            )
        } else {
            CurlDiagnosis(
                condition = "Sehat",
                title = "Tajuk Daun Normal / Sehat",
                description = "Morfologi helaian daun hijau segar, turgor baik, tidak ada gejala keriting maupun nekrosis patologis.",
                activeIngredient = "Pemeliharaan preventif / Plant Growth Promoting Rhizobacteria (PGPR)",
                dosagePerLiter = "5 – 10 mL/L air kocor",
                sprayingInstructions = "Lakukan pemantauan berkala setiap 3 hari.",
                organicAlternative = "Aplikasi trichoderma preventif pada perakaran 2 minggu sekali.",
                confidence = 0.92,
                symptoms = listOf("Warna hijau merata", "Turgor elastis normal"),
            )
        }
    }

    // --- Destructive Ooze Trigger (FR-2.3 / TAHAP 3.1) ------------------------

    data class OozeTriggerResult(
        val isMandatory: Boolean,
        val reason: String,
        val protocolCode: String,
    )

    fun evaluateDestructiveOozeTrigger(
        isWilting: Boolean,
        soilMoisturePct: Double,
    ): OozeTriggerResult {
        return if (isWilting && soilMoisturePct > 60.0) {
            OozeTriggerResult(
                isMandatory = true,
                reason = "Tanaman layu saat tanah basah (>60%): indikasi kuat Ralstonia solanacearum (bakteri), uji suspensi air bening wajib dilakukan sebelum tindakan kimiawi",
                protocolCode = "OOZE_TEST_MANDATORY",
            )
        } else if (isWilting) {
            OozeTriggerResult(
                isMandatory = false,
                reason = "Tanaman layu pada tanah kering: stres air normal atau Fusarium, lakukan irigasi sebelum diagnostik invasif",
                protocolCode = "FUSARIUM_OR_DROUGHT",
            )
        } else {
            OozeTriggerResult(
                isMandatory = false,
                reason = "Tidak ada gejala kelayuan vaskular",
                protocolCode = "NORMAL",
            )
        }
    }

    // --- Early Warning System (EWS) Blast / Antraknosa 72h -------------------

    data class BlastRiskResult(
        val blastRisk72h: Double,
        val peakRisk: Double,
        val action: String,
        val irrigationMinutesPerBed: Int,
    )

    fun calculateBlastRisk72h(
        temperatures: List<Double>,
        humidities: List<Double>,
    ): BlastRiskResult {
        require(temperatures.size == humidities.size && temperatures.isNotEmpty()) {
            "Series suhu dan kelembaban harus seimbang dan tidak kosong"
        }
        val risks = temperatures.indices.map { i ->
            val t = temperatures[i]
            val rh = humidities[i]
            val tFactor = if (t in 22.0..32.0) 1.0 - Math.abs(t - 27.0) / 10.0 else 0.2
            val rhFactor = if (rh >= 80.0) ((rh - 80.0) / 20.0).coerceIn(0.0, 1.0) else 0.0
            (tFactor * 0.4 + rhFactor * 0.6).coerceIn(0.0, 1.0)
        }
        val peak = risks.maxOrNull() ?: 0.0
        val peak0 = risks.first()

        val action: String
        val minutes: Int
        when {
            peak >= 0.7 -> {
                action = "RISIKO TINGGI ledakan Antraknosa 72 jam. Fungisida protektif (Mankozeb) sore ini, kurangi penyiraman."
                minutes = 0
            }
            peak >= 0.4 -> {
                action = "Risiko sedang. Pantau kelembaban tajuk, hindari penyiraman berlebih."
                minutes = 10
            }
            else -> {
                action = "Risiko rendah. Penyiraman normal."
                minutes = 20
            }
        }
        return BlastRiskResult(
            blastRisk72h = Math.round(peak0 * 10000.0) / 10000.0,
            peakRisk = Math.round(peak * 10000.0) / 10000.0,
            action = action,
            irrigationMinutesPerBed = minutes,
        )
    }
}
