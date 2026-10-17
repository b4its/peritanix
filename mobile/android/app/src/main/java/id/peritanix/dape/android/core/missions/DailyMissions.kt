package id.peritanix.dape.android.core.missions

import id.peritanix.dape.android.core.guide.ChiliGrowingGuide
import java.time.LocalDate

/**
 * Misi harian untuk menghasilkan kualitas cabai terbaik (Grade A+ Ekspor).
 *
 * Menghasilkan satu set misi harian yang **deterministik** (berbasis HST dan
 * fase tumbuh) dan diarahkan pada 5 pilar mutu cabai presisi:
 * 1. Ketebalan & Kepadatan Pericarp (Ca + B + Si, lamela tengah kokoh, tahan simpan >14 hari).
 * 2. Konsentrasi Kapsaisin & Kepedasan Maksimal (SHU > 80.000, defisit air terukur & rasio K:N tinggi).
 * 3. Warna Merah Mengkilap Seragam (Kapsantin & Kapsorubin > 85%, penetrasi kanopi & nutrisi Mg/K).
 * 4. Nol Cacat Fisik & Proteksi Patogen (0% Antraknosa/Patek, perangkap hama, dan imunisasi biologis).
 * 5. Presisi Panen & Pascapanen Grade A+ (Turgor puncak 07:00-10:00, termoterapi Asas Black 52°C, uji apung Archimedes).
 *
 * Logika murni & teruji: tidak bergantung pada Android. UI hanya menampilkan dan
 * mencatat penyelesaian; penyimpanan via [MissionStore] (Room).
 */
object DailyMissions {

    /** Kategori misi — memetakan ke ikon/warna di UI. */
    enum class Category(val label: String) {
        AIR("Irigasi & Air"),
        NUTRISI("Nutrisi & Dosis"),
        SANITASI("Sanitasi & Hama"),
        INSPEKSI("Inspeksi Kamera"),
        PANEN("Panen & Sortasi"),
        UMUM("Perawatan Umum")
    }

    /** Pilar sasaran mutu cabai terbaik untuk pasar ekspor / industri / premium. */
    enum class QualityTarget(
        val title: String,
        val goalMetric: String,
        val description: String
    ) {
        PERICARP_TEBAL(
            "Pericarp Tebal & Keras",
            "Tebal >1.8 mm, daya simpan >14 hari",
            "Membentuk kalsium pektat pada lamela tengah dinding sel via Kalsium (Ca), Boron (B), dan Silika (Si) agar buah padat, tahan benturan, dan bebas busuk ujung (BER)."
        ),
        KAPSAISIN_PEDAS(
            "Kepedasan & Kapsaisin Maksimal",
            "SHU >80.000, rasio K:N tinggi",
            "Memicu biosintesis alkaloid kapsaisin pada plasenta buah melalui defisit air terkontrol (mild stress) dan pasokan Kalium tinggi pada fase pematangan."
        ),
        WARNA_KILAP(
            "Merah Mengkilap Seragam",
            "Kapsantin >85%, merah mengkilap",
            "Stimulasi pigmen karotenoid kapsantin & kapsorubin dengan Kalium (KNO3/MKP), Magnesium (Mg), dan kanopi pencahayaan optimal."
        ),
        BEBAS_CACAT(
            "Nol Cacat & Bebas Antraknosa",
            "0% Patek, bebas lalat buah & thrips",
            "Sanitasi proaktif, pemasangan likat kuning & metil eugenol, serta inokulasi Trichoderma untuk menjamin buah mulus tanpa bercak."
        ),
        GRADE_A_PANEN(
            "Grade A+ & Pascapanen Presisi",
            "Petik 07:00-10:00, tangkai 2-3 cm, 52°C",
            "Pemetikan saat turgor sel maksimum, sortasi 4 kelas, termoterapi Asas Black 52°C, dan uji apung Archimedes untuk grade pasar premium."
        ),
        VIGOR_AKAR(
            "Vigor Tanaman & Serapan Hara",
            "Sistem perakaran dalam & kanopi prima",
            "Fondasi pertumbuhan vegetatif dan penyerapan hara yang kokoh untuk menopang produksi buah berkualitas tinggi."
        )
    }

    /** Satu misi harian berbasis kualitas. `id` stabil per (tanggal, template) agar idempoten. */
    data class Mission(
        val id: String,
        val category: Category,
        val title: String,
        val detail: String,
        val qualityImpact: String,          // mengapa ini meningkatkan kualitas cabai
        val targetAmount: Int = 1,          // mis. 5 pohon sampel, 2 bedengan, dst.
        val weight: Int = 1,                // bobot kontribusi ke skor kualitas harian
        val qualityTarget: QualityTarget = QualityTarget.PERICARP_TEBAL,
        val howToExecute: String = "",      // Ringkasan SOP eksekusi di kebun
        val scientificBasis: String = "",   // Landasan ilmiah agro-fisika
        val targetMetric: String = "",      // Metrik target spesifik (angka/kondisi)
        val toolsAndMaterials: List<String> = emptyList(),     // Alat & bahan yang diperlukan
        val executionSteps: List<String> = emptyList(),        // Langkah pengerjaan 1, 2, 3, 4 terstruktur
        val optimalTiming: String = "",                        // Waktu & kondisi cuaca terbaik
        val commonMistakesToAvoid: List<String> = emptyList(), // Pantangan / kesalahan yang harus dihindari
        val successVerification: String = "",                  // Kriteria verifikasi keberhasilan fisik
        val safetyGear: List<String> = emptyList(),            // Standar APD & Keselamatan Kerja
        val standardDosagePerLiter: Double = 0.0,              // Takaran baku per liter (untuk kalkulator tangki)
        val dosageUnit: String = "gram",                       // Satuan dosis ("gram", "ml", "L")
        val durationMinutes: Int = 0,                          // Durasi pengerjaan khusus (untuk stopwatch/timer)
        val troubleshootingTip: String = "",                   // Solusi kendala lapangan (troubleshooting bila terjadi masalah)
        val qualitySigns: String = ""                          // Tanda fisik kualitas cabai terbaik yang dihasilkan
    )

    /** Hasil pembangkitan untuk satu hari + fase. */
    data class DailyPlan(
        val epochDay: Long,
        val stageIndex: Int,
        val stageTitle: String,
        val phaseName: String,
        val missions: List<Mission>
    ) {
        val totalWeight: Int get() = missions.sumOf { it.weight }
    }

    /**
     * Bangkitkan rencana misi untuk [date] pada [hst] tertentu.
     *
     * @param hst Hari Setelah Tanam (boleh negatif untuk fase persemaian).
     * @param riskBoost 0..1 — bila cuaca/EWS menunjukkan risiko jamur tinggi,
     *        tambahkan misi sanitasi defensif ekstra.
     */
    fun planFor(date: LocalDate, hst: Int, riskBoost: Double = 0.0): DailyPlan {
        val stage = ChiliGrowingGuide.stageForHst(hst)
        val epochDay = date.toEpochDay()
        val base = buildList {
            // Misi universal berkualitas: hidrasi presisi + inspeksi visual harian.
            add(hydrationMission(stage.index, epochDay))
            add(inspectionMission(stage.index, epochDay))

            // Misi spesifik fase pertumbuhan untuk menjamin kualitas cabai terbaik.
            when (stage.index) {
                1 -> { // Persemaian & Pra-Tanam (-21 s.d. 0 HST)
                    add(seedlingVigorMission(epochDay))
                    add(pestNetMission(epochDay))
                    add(trichodermaSeedlingMission(epochDay))
                    add(hardeningOffMission(epochDay))
                    add(seedThermoSterilizationMission(epochDay))
                    add(cellTrayAerationMission(epochDay))
                }
                2 -> { // Vegetatif (1 s.d. 35 HST)
                    add(wiwilMission(epochDay))
                    add(nitrogenBalanceMission(epochDay))
                    add(silicaEpidermisMission(epochDay))
                    add(yellowTrapMission(epochDay))
                    add(bedSlopeClinometerMission(epochDay))
                    add(canopyCeptometerMission(epochDay))
                    add(humicAcidRootMission(epochDay))
                }
                3 -> { // Pembungaan (36 s.d. 55 HST) — Kritis kualitas dinding buah & bunga
                    add(calciumBoronMission(epochDay))
                    add(flowerRetentionMission(epochDay))
                    add(fruitFlyTrapMission(epochDay))
                    add(foliarPBMission(epochDay))
                    add(barometerSprayCheckMission(epochDay))
                    add(flowerVibrationPollinationMission(epochDay))
                    add(flowerPetalSanitationMission(epochDay))
                }
                4 -> { // Pembuahan & Pematangan (56 s.d. 75 HST) — Kunci warna & kepedasan
                    add(potassiumMission(epochDay))
                    add(antracnoseScoutMission(epochDay))
                    add(capsaicinStressMission(epochDay))
                    add(calciumCrackGuardMission(epochDay))
                    add(mulchSoilThermistorMission(epochDay))
                    add(magnesiumCuticleGlossMission(epochDay))
                    add(bottomLeafPruningMission(epochDay))
                }
                else -> { // Panen & Pascapanen (76 s.d. 120 HST) — Standar Grade A+ Ekspor
                    add(harvestTimeMorningMission(epochDay))
                    add(harvestRipenessMission(epochDay))
                    add(sortationMission(epochDay))
                    add(archimedesFloatMission(epochDay))
                    add(crateStorageMission(epochDay))
                    add(pedicelFreshnessMission(epochDay))
                    add(cielabColorCameraMission(epochDay))
                    add(fieldHeatPreCoolingMission(epochDay))
                }
            }
        }.toMutableList()

        // EWS-driven defensive sanitation when spore risk is elevated.
        if (riskBoost >= 0.5) {
            base.add(sporeDefenseMission(epochDay, riskBoost))
        }

        return DailyPlan(
            epochDay = epochDay,
            stageIndex = stage.index,
            stageTitle = stage.title,
            phaseName = stage.phaseName,
            missions = base
        )
    }

    /** Skor kualitas harian 0..100 dari misi yang selesai (berbobot). */
    fun dailyQualityScore(plan: DailyPlan, completedIds: Set<String>): Int {
        if (plan.totalWeight <= 0) return 100
        val done = plan.missions.filter { it.id in completedIds }.sumOf { it.weight }
        return Math.round(100.0 * done / plan.totalWeight).toInt().coerceIn(0, 100)
    }

    /** Tingkat predikat kepatuhan harian untuk memotivasi kualitas terbaik. */
    fun scoreGrade(score: Int): String = when {
        score >= 90 -> "Sangat Baik — Kualitas Prima"
        score >= 70 -> "Baik — Pertahankan"
        score >= 40 -> "Cukup — Kejar Target"
        else -> "Kurang — Segera Tuntaskan"
    }

    /** Predikat kelas pasar komersial berdasarkan skor kualitas. */
    fun exportGradeBadge(score: Int): String = when {
        score >= 90 -> "Grade A+ (Standar Ekspor)"
        score >= 75 -> "Grade A (Pasar Swalayan)"
        score >= 50 -> "Grade B (Pasar Induk)"
        else -> "Grade Standar (Perlu Optimasi)"
    }

    /** Misi berikutnya yang belum selesai (untuk kartu "fokus hari ini"). */
    fun nextUnfinished(plan: DailyPlan, completedIds: Set<String>): Mission? =
        plan.missions.firstOrNull { it.id !in completedIds }

    /** Progres pencapaian per pilar sasaran mutu cabai. */
    fun getPillarProgress(plan: DailyPlan, completedIds: Set<String>): Map<QualityTarget, Pair<Int, Int>> {
        val groups = plan.missions.groupBy { it.qualityTarget }
        return groups.mapValues { (_, missions) ->
            val done = missions.count { it.id in completedIds }
            Pair(done, missions.size)
        }
    }

    /** Filter misi berdasarkan target mutu. */
    fun filterByTarget(plan: DailyPlan, target: QualityTarget?): List<Mission> {
        if (target == null) return plan.missions
        return plan.missions.filter { it.qualityTarget == target }
    }

    // --- Step progress cache (in-memory persistence across dialog toggles & tabs) ---
    private val stepProgressCache = mutableMapOf<String, MutableSet<Int>>()

    fun getCheckedSteps(missionId: String): Set<Int> = stepProgressCache[missionId]?.toSet() ?: emptySet()

    fun setStepChecked(missionId: String, stepIndex: Int, isChecked: Boolean) {
        val set = stepProgressCache.getOrPut(missionId) { mutableSetOf() }
        if (isChecked) set.add(stepIndex) else set.remove(stepIndex)
    }

    fun setAllStepsChecked(missionId: String, totalSteps: Int, isChecked: Boolean) {
        val set = stepProgressCache.getOrPut(missionId) { mutableSetOf() }
        if (isChecked) {
            set.clear()
            for (i in 0 until totalSteps) set.add(i)
        } else {
            set.clear()
        }
    }

    fun clearStepProgress(missionId: String) {
        stepProgressCache.remove(missionId)
    }

    // --- Panduan Urutan Pencampuran Baku & Uji Kompatibilitas Toples ---
    data class MixingStep(
        val code: String,
        val stageName: String,
        val instruction: String
    )

    object MixingGuide {
        val walesSteps = listOf(
            MixingStep("W", "Water (Air Bersih)", "Isi tangki semprot 50% hingga 70% dengan air bersih jernih bersuhu normal (pH 6.0 - 7.0, hindari air berlumpur)."),
            MixingStep("A", "Agitation / WP & WDG (Tepung/Granul)", "Larutkan formulasi bubuk/tepung (WP/WDG) dalam ember kecil berisi sedikit air, aduk hingga larut sempurna tanpa gumpalan, lalu tuangkan ke tangki."),
            MixingStep("L", "Liquid / SC & SL (Larutan/Suspensi Cair)", "Tuangkan konsentrat pupuk/pestisida cair (Kalsium, Silika, Boron, atau SL/SC) ke tangki sambil terus diaduk perlahan."),
            MixingStep("E", "Emulsifiable Concentrates (EC)", "Tambahkan formula pekatan minyak (EC) bila ada, aduk hingga larutan membentuk emulsi susu yang homogen."),
            MixingStep("S", "Surfactant / Adjuvant (Perekat & Perata)", "Tambahkan zat perekat/perata/penembus di tahap paling akhir, lalu tambahkan air bersih hingga batas volume tangki penuh.")
        )

        const val jarTestGuide = "Uji Toples 5 Menit (Jar Compatibility Test): Jika ingin mencampur 2 atau lebih jenis bahan, larutkan masing-masing 1 sendok teh ke dalam botol kecil 500 ml air bersih. Kocok rata dan diamkan selama 5 menit. Bila terjadi gumpalan seperti susu basi, endapan kasar di dasar, kenaikan suhu panas, atau pemisahan minyak pekat, JANGAN dicampur dalam satu tangki karena berisiko menyumbat nozel dan membakar daun cabai."
    }

    /**
     * Menghasilkan checklist kesiapan kerja mandiri (Pre-Flight Agronomi) sebelum petani turun ke kebun.
     */
    fun getPreFlightChecklist(mission: Mission): List<String> {
        val base = mutableListOf<String>()
        if (mission.safetyGear.isNotEmpty()) {
            base.add("Kenakan APD lengkap: ${mission.safetyGear.joinToString(", ")}")
        } else {
            base.add("Kenakan topi kebun dan sepatu bot keselamatan kerja")
        }
        when (mission.category) {
            Category.AIR -> {
                base.add("Periksa kelembaban tanah bedengan dengan uji remas visual atau tensiometer")
                base.add("Pastikan air siraman jernih, bebas sedimen lumpur, dan bersuhu sejuk")
                base.add("Periksa kelancaran saluran parit pembuangan air agar tidak terjadi genangan")
            }
            Category.NUTRISI -> {
                base.add("Periksa cuaca: pastikan tidak hujan lebat dalam 2 jam ke depan")
                base.add("Pastikan tangki semprot bersih dan nozel tidak tersumbat atau aus")
                base.add("Takar dosis bahan dengan timbangan digital atau gelas ukur baku")
                base.add("Gunakan metode pencampuran W-A-L-E-S dalam melarutkan bahan")
            }
            Category.SANITASI -> {
                base.add("Siapkan kantong plastik khusus untuk mengisolasi bagian tanaman yang terinfeksi")
                base.add("Sterilkan gunting pangkas atau pisau dengan alkohol 70% sebelum bekerja")
                base.add("Pilih waktu pengerjaan saat embun daun sudah mengering untuk mencegah sebaran spora")
            }
            Category.INSPEKSI -> {
                base.add("Bersihkan lensa kamera ponsel pintar dengan kain mikrofiber")
                base.add("Pilih tanaman sampel representatif (minimal 5 titik acak pola diagonal/Z)")
                base.add("Pastikan pencahayaan cukup dan hindari backlight langsung ke lensa")
            }
            Category.PANEN -> {
                base.add("Lakukan pemetikan pada jendela turgor optimal (pukul 07:00 - 10:00 WIB)")
                base.add("Siapkan krat plastik berventilasi bersih yang diletakkan di bawah naungan teduh")
                base.add("Pastikan kuku pendek atau pakai sarung tangan agar kulit buah tidak terluka")
            }
            Category.UMUM -> {
                base.add("Siapkan seluruh peralatan kerja pendukung dalam kondisi siap pakai")
                base.add("Periksa kelembaban dan kerapihan mulsa di sepanjang bedengan")
            }
        }
        return base
    }

// --- Individual mission templates with Complete SOP Guidance ---

    private fun hydrationMission(stageIdx: Int, day: Long) = Mission(
        id = "m$day-air",
        category = Category.AIR,
        title = "Cek kelembaban tanah & siram presisi",
        detail = "Ukur kelembaban media/tanah; sirami hingga kapasitas lapang (jangan sampai tergenang). Lakukan pagi hari agar tajuk cepat kering.",
        qualityImpact = "Air yang seimbang mencegah busuk akar & bunga rontok, dasar buah mulus bebas pecah.",
        weight = 2,
        qualityTarget = QualityTarget.VIGOR_AKAR,
        howToExecute = "Gunakan sensor kapasitif atau periksa kepalan tanah di kedalaman 10 cm. Sirami perlahan pada zona perakaran antara pukul 06:00-08:00 WIB.",
        scientificBasis = "Fluktuasi kadar air ekstrem mengganggu translokasi ion Kalsium ke pucuk dan memicu keretakan buah (fruit cracking).",
        targetMetric = "Lengas tanah 60-70% kapasitas lapang (tegangan matriks -30 s.d. -50 kPa)",
        toolsAndMaterials = listOf(
            "Sensor kapasitif tanah / Tensiometer",
            "Gembor siram bermoncong halus",
            "Air irigasi bersih (pH 6,0 - 7,0, TDS < 300 ppm)"
        ),
        executionSteps = listOf(
            "Periksa kelembaban tanah pada 3 titik per bedengan di kedalaman 10-15 cm.",
            "Bila tanah tidak merekat saat dikepal atau tegangan < -50 kPa, siapkan penyiraman.",
            "Sirami perlahan melingkar pada zona rizosfer tajuk, hindari membasahi pangkal batang secara langsung.",
            "Hentikan penyiraman saat air meresap sempurna mencapai kapasitas lapang tanpa ada genangan di parit."
        ),
        optimalTiming = "Pukul 06:00 - 08:00 WIB (sebelum sinar matahari terik memicu penguapan kilat)",
        commonMistakesToAvoid = listOf(
            "Menyiram di tengah hari saat terik matahari (memicu syok termal dan perakaran busuk).",
            "Menyiram daun dan bunga mekar secara deras (menyebabkan gugur putik bunga).",
            "Membiarkan parit tergenang air lebih dari 2 jam pascapenyiraman."
        ),
        successVerification = "Tanah lembab remah saat digenggam, turgor daun tegak segar, dan parit bebas genangan.",
        safetyGear = listOf("Topi pelindung terik matahari", "Sepatu bot kebun anti-slip"),
        durationMinutes = 30,
        troubleshootingTip = "Bila air menggenang lebih dari 15 menit di parit atau lubang mulsa, segera bersihkan endapan lumpur saluran pembuang dan kurangi volume kocor.",
        qualitySigns = "Turgor sel daun tegak segar tanpa layu sementara, ovarium buah muda terhidrasi stabil bebas busuk ujung (BER)."
    )

    private fun inspectionMission(stageIdx: Int, day: Long) = Mission(
        id = "m$day-inspect",
        category = Category.INSPEKSI,
        title = "Inspeksi visual & foto kamera 5 sampel tanaman",
        detail = "Potret 5 tanaman acak per bedengan dengan kamera DAPE (deteksi kualitas) dan periksa gejala awal penyakit.",
        qualityImpact = "Deteksi dini patogen mencegah penyebaran yang menurunkan grade panen.",
        targetAmount = 5,
        weight = 2,
        qualityTarget = QualityTarget.BEBAS_CACAT,
        howToExecute = "Pilih 5 tanaman secara zigzag per petak. Buka kamera DAPE, arahkan ke pucuk daun, cabang Y, dan buah pentil untuk verifikasi spektral.",
        scientificBasis = "Pemantauan spektral RGB/HSV mendeteksi defisiensi klorofil dan lesi nekrotik sebelum gejala menyebar secara makroskopis.",
        targetMetric = "Defek klorosis < 5%, nekrosis 0%",
        toolsAndMaterials = listOf(
            "Smartphone dengan kamera DAPE",
            "Lembar kalibrasi spektral putih/abu-abu",
            "Catatan sinkronisasi agronomi DAPE"
        ),
        executionSteps = listOf(
            "Tentukan 5 titik tanaman sampel secara acak dengan pola zigzag per petak bedengan.",
            "Buka instrumen kamera DAPE, pastikan lensa bersih dari residu debu atau embun.",
            "Ambil foto spektral pada pucuk daun, cabang Y, dan buah pentil muda dari jarak 15-20 cm.",
            "Verifikasi hasil inferensi AI terhadap persentase lesi klorosis dan bintik nekrotik patogen."
        ),
        optimalTiming = "Pukul 07:30 - 09:30 WIB (saat pencahayaan matahari merata tanpa bayangan tajam)",
        commonMistakesToAvoid = listOf(
            "Memotret melawan arah sinar matahari langsung (backlight/silau).",
            "Mengambil foto buram akibat tangan bergetar saat pengambilan gambar.",
            "Hanya memeriksa tanaman pinggir bedengan yang tidak representatif."
        ),
        successVerification = "5 sampel tanaman tervalidasi di aplikasi dengan klorosis < 5% dan nekrosis 0%.",
        safetyGear = listOf("Topi lapangan bertali", "Sarung tangan katun periksa"),
        durationMinutes = 20,
        troubleshootingTip = "Bila kamera mendeteksi daun berkerut atau bintik halus di bawah daun, gunakan fitur zoom dan periksa keberadaan koloni thrips atau tungau merah.",
        qualitySigns = "Tajuk daun simetris bebas bercak nekrotik, pucuk tunas hijau muda segar tanpa gejala keriting atau menguning (vein clearing)."
    )

    // Stage 1: Persemaian (-21 s.d. 0 HST)

    private fun seedlingVigorMission(day: Long) = Mission(
        id = "m$day-vigor",
        category = Category.NUTRISI,
        title = "Beri pupuk daun mikro dosis 0,5 g/L",
        detail = "Semprot pupuk daun mikro (klorofil) tipis kabut pada pagi hari untuk bibit berdaun >=3.",
        qualityImpact = "Bibit vigor & batang kokoh menghasilkan tanaman produktif dengan buah seragam.",
        weight = 2,
        qualityTarget = QualityTarget.VIGOR_AKAR,
        howToExecute = "Larutkan 0,5 g pupuk mikro Zn/B/Mg per liter air bersih. Semprotkan secara kabut merata di bawah permukaan daun pukul 07:00-08:30 WIB.",
        scientificBasis = "Seng (Zn) memicu sintesis auksin alami pembelahan sel, Magnesium (Mg) mengoptimalkan klorofil untuk kapasitas fotosintesis bibit.",
        targetMetric = "Tinggi bibit 15-20 cm, jumlah daun 4-5 helai hijau tua",
        toolsAndMaterials = listOf(
            "Sprayer tangan kabut halus (mist nozzle)",
            "Pupuk mikro kelat (Zn 15%, B 2%, Mg 5%)",
            "Gelas ukur presisi & air bersih non-klorin"
        ),
        executionSteps = listOf(
            "Takar 0,5 gram pupuk mikro per 1 liter air bersih, aduk hingga larut sempurna.",
            "Masukkan ke sprayer kabut, atur tekanan nosel agar menghasilkan droplet mikro (< 100 mikron).",
            "Semprotkan merata mengarah ke bagian bawah permukaan daun semai.",
            "Pastikan daun basah berkabut tipis tanpa butiran air besar yang menetes jatuh."
        ),
        optimalTiming = "Pukul 06:30 - 08:00 WIB saat stomata bibit membuka maksimal",
        commonMistakesToAvoid = listOf(
            "Menggunakan dosis berlebih (> 1 g/L) yang membakar daun semai muda.",
            "Menyemprot di atas jam 09:00 saat suhu sungkup > 30°C."
        ),
        successVerification = "Bibit berdiri tegak, daun hijau segar mengkilap tanpa gejala klorosis.",
        safetyGear = listOf("Masker kain/partikel", "Sarung tangan karet"),
        standardDosagePerLiter = 0.5,
        dosageUnit = "gram",
        durationMinutes = 25,
        troubleshootingTip = "Bila bibit tumbuh kurus tinggi memanjang (etiolasi), pindahkan rak semai ke posisi yang terpapar sinar matahari pagi langsung minimal 4 jam.",
        qualitySigns = "Batang semai tebal kokoh (diameter >= 2 mm), ruas pendek internode rapat, daun hijau tua mengkilap tanpa gejala etiolasi."
    )

    private fun pestNetMission(day: Long) = Mission(
        id = "m$day-pestnet",
        category = Category.SANITASI,
        title = "Pastikan insect net tertutup rapat",
        detail = "Periksa kasa 50-mesh menutup pembibitan untuk mencegah kutu kebul (vektor virus kuning).",
        qualityImpact = "Bebas virus Gemini sejak dini menjaga kualitas & bentuk buah.",
        weight = 2,
        qualityTarget = QualityTarget.BEBAS_CACAT,
        howToExecute = "Periksa seluruh keliling sungkup persemaian. Pastikan tidak ada celah, sobekan, atau bukaan tanah yang terbuka.",
        scientificBasis = "Kutu kebul (Bemisia tabaci) dengan ukuran tubuh 0,8 mm tidak dapat menembus kasa mesh-50 (bukaan celah 0,28 mm).",
        targetMetric = "Kerapatan kasa 50-mesh 100% utuh tanpa celah",
        toolsAndMaterials = listOf(
            "Kasa insect net 50-mesh",
            "Pasak penjepit bambu/plastik",
            "Jarum & benang nilon penambal"
        ),
        executionSteps = listOf(
            "Kelilingi seluruh perimeter sungkup persemaian bibit cabai.",
            "Periksa kelim sambungan dan dasar kasa yang tertanam di tanah.",
            "Segera jahit rapat atau tambal bila ditemukan robekan sekecil apapun.",
            "Pastikan pintu sungkup selalu terkancing rapat setiap selesai akses."
        ),
        optimalTiming = "Pukul 07:00 WIB sebelum aktivitas serangga meningkat di siang hari",
        commonMistakesToAvoid = listOf(
            "Membiarkan pintu sungkup terbuka saat ditinggal mengambil alat.",
            "Menggunakan kasa jarang (< 40 mesh) yang masih dapat ditembus hama thrips."
        ),
        successVerification = "Sungkup tertutup 100% kedap serangga, nol kutu kebul di dalam ruang semai.",
        safetyGear = listOf("Sarung tangan kebun katun"),
        durationMinutes = 15,
        troubleshootingTip = "Bila terdapat celah robek pada sungkup kasa serangga, segera tambal menggunakan selotip bening atau jahitan nilon agar vektor tidak menyusup.",
        qualitySigns = "100% bibit bebas dari infeksi virus kuning (Gemini virus) dan daun bebas koloni kutu kebul (Bemisia tabaci)."
    )

    private fun trichodermaSeedlingMission(day: Long) = Mission(
        id = "m$day-tricho-seedling",
        category = Category.SANITASI,
        title = "Inokulasi Trichoderma harzianum pada media semai",
        detail = "Siramkan suspensi spora Trichoderma pada perakaran bibit untuk imunisasi biologis anti layu fusarium & rebah kecambah.",
        qualityImpact = "Perakaran steril dan terlindungi koloni mikroba mengamankan serapan hara buah.",
        weight = 2,
        qualityTarget = QualityTarget.BEBAS_CACAT,
        howToExecute = "Larutkan 10 gram bio-fungisida Trichoderma dalam 5 liter air. Kocor 20 ml per tray semai 7 hari sebelum pindah tanam.",
        scientificBasis = "Trichoderma melakukan mikoparasitisme dan kolonisasi perakaran yang memicu ketahanan terinduksi sistemik (ISR).",
        targetMetric = "100% perakaran bibit terinokulasi tanpa gejala rebah kecambah (damping-off)",
        toolsAndMaterials = listOf(
            "Isolat Trichoderma harzianum murni (10^8 CFU/g)",
            "Ember pencampur 10L",
            "Gayung takar 20 ml & air sumur steril"
        ),
        executionSteps = listOf(
            "Larutkan 10 gram serbuk Trichoderma ke dalam 5 liter air sumur bersih.",
            "Aduk perlahan selama 3 menit hingga suspensi spora homogen.",
            "Kocorkan 20 ml larutan ke setiap lubang baki semai tepat di pangkal akar bibit.",
            "Letakkan baki di tempat teduh agar koloni spora beradaptasi tanpa terpapar UV ekstrem."
        ),
        optimalTiming = "Pagi hari pukul 06:30 - 08:00 WIB atau sore hari pukul 16:00 WIB",
        commonMistakesToAvoid = listOf(
            "Melarutkan dengan air PDAM berkaporit yang membunuh mikroba aktif.",
            "Mencampur bio-fungisida bersama fungisida kimia sintetis."
        ),
        successVerification = "Seluruh baki terbasahi larutan hayati, perakaran bibit putih bersih tanpa layu.",
        safetyGear = listOf("Sarung tangan nitril", "Masker pelindung spora"),
        standardDosagePerLiter = 5.0,
        dosageUnit = "gram",
        durationMinutes = 15,
        troubleshootingTip = "Jangan mencampur Trichoderma dengan fungisida kimia sintetis; berikan jeda minimal 5 hari sebelum atau sesudah aplikasi fungisida.",
        qualitySigns = "Sistem perakaran serabut putih lebat menjalar memenuhi lubang tray, bebas layu Fusarium dan rebah semai (damping-off)."
    )

    private fun hardeningOffMission(day: Long) = Mission(
        id = "m$day-hardening",
        category = Category.UMUM,
        title = "Pengerasan bibit (hardening-off) buka naungan bertahap",
        detail = "Buka plastik naungan persemaian secara bertahap selama 3-4 jam di pagi hari agar bibit tahan terik matahari lapangan.",
        qualityImpact = "Tanaman tidak stres saat pindah tanam, adaptasi cepat menghasilkan pertumbuhan buah optimal.",
        weight = 2,
        qualityTarget = QualityTarget.VIGOR_AKAR,
        howToExecute = "Buka naungan mulai jam 07:30 s.d. 10:30 WIB. Amati daun tidak layu parah, semprot air tipis jika suhu udara > 32°C.",
        scientificBasis = "Peningkatan intensitas cahaya merangsang penebalan kutikula lilin daun dan akumulasi karbohidrat pada batang.",
        targetMetric = "Batang kokoh berwarna hijau kecokelatan, turgor daun stabil",
        toolsAndMaterials = listOf(
            "Plastik naungan UV fleksibel",
            "Tali pengikat naungan",
            "Termometer lingkungan HP"
        ),
        executionSteps = listOf(
            "Gulung plastik naungan UV secara perlahan selebar 50% pada pukul 07:30 WIB.",
            "Biarkan bibit terpapar sinar matahari langsung selama 3 jam untuk memacu kutikula.",
            "Pantau respon daun; jika terjadi layu temporer ekstrem, kabuti air tipis di atas tajuk.",
            "Tutup kembali naungan pada pukul 10:30 WIB sebelum indeks radiasi UV memuncak."
        ),
        optimalTiming = "Pukul 07:30 - 10:30 WIB pada 5 hari menjelang pindah tanam",
        commonMistakesToAvoid = listOf(
            "Membuka naungan penuh sepanjang hari secara mendadak (memicu sun-scald).",
            "Membiarkan media semai kering kerontang saat pembukaan naungan berlangsung."
        ),
        successVerification = "Batang bibit kokoh berwarna hijau kecokelatan dan daun tidak terkulai lemas.",
        safetyGear = listOf("Topi pelindung matahari", "Termometer lingkungan"),
        durationMinutes = 15,
        troubleshootingTip = "Bila bibit menunjukkan layu permanen saat dijemur terik, segera tarik paranet peneduh 50% selama 1 jam dan percikkan sedikit air halus.",
        qualitySigns = "Kutikula daun tebal berlilin, batang kemerahan liat tidak mudah patah saat dipindah tanam ke lahan terbuka."
    )

    private fun seedThermoSterilizationMission(day: Long) = Mission(
        id = "m$day-seed-thermo",
        category = Category.SANITASI,
        title = "Termoterapi benih air hangat 50°C Asas Black",
        detail = "Rendam benih cabai dalam air hangat 50°C selama 15 menit untuk sterilisasi spora antraknosa bawaan benih dan memecah dormansi embrio.",
        qualityImpact = "Mencegah penularan penyakit tular-benih (seed-borne) sejak hari ke-0 demi integritas pericarp buah kelak.",
        weight = 2,
        qualityTarget = QualityTarget.BEBAS_CACAT,
        howToExecute = "Campur air mendidih dan air sumur sesuai kalkulator termal Asas Black hingga suhu tepat 50°C. Rendam benih selama 15 menit, tiriskan lalu angin-anginkan.",
        scientificBasis = "Spora laten Colletotrichum dan bakteri Xanthomonas terdenaturasi pada suhu 50°C tanpa merusak daya kecambah embrio biji.",
        targetMetric = "Daya kecambah benih > 90%, benih bebas patogen 100%",
        toolsAndMaterials = listOf(
            "Kalkulator termal Asas Black DAPE",
            "Air mendidih & air sumur dingin",
            "Kantong kain mori & saringan peniris"
        ),
        executionSteps = listOf(
            "Hitung rasio pencampuran air di kalkulator Asas Black DAPE sesuai elevasi lahan.",
            "Campurkan air mendidih dan air sumur hingga suhu stabil di 50°C (toleransi ±0,5°C).",
            "Masukkan benih dalam kantong kain mori lalu celupkan ke bejana air 50°C.",
            "Pertahankan suhu selama 15 menit dengan adukan perlahan, angkat lalu angin-anginkan."
        ),
        optimalTiming = "Sebelum perendaman hormon atau 1 hari sebelum semai baki",
        commonMistakesToAvoid = listOf(
            "Suhu air melampaui 53°C yang dapat mematikan daya hidup embrio benih.",
            "Suhu di bawah 48°C yang tidak efektif membunuh spora antraknosa laten."
        ),
        successVerification = "Suhu stabil 50°C selama 15 menit, benih mengering merata dengan daya kecambah > 90%.",
        safetyGear = listOf("Sarung tangan tahan panas (heat-resistant)", "Termometer digital presisi"),
        durationMinutes = 30,
        troubleshootingTip = "Jika suhu air perendaman naik melebihi 52°C, segera tambahkan air dingin tetes demi tetes sambil diaduk cepat agar embrio benih tidak mati.",
        qualitySigns = "Daya berkecambah > 90%, bibit bebas bawaan bakteri Xanthomonas campestris pv. vesicatoria dan virus TMV/PMMoV."
    )

    private fun cellTrayAerationMission(day: Long) = Mission(
        id = "m$day-tray-aeration",
        category = Category.UMUM,
        title = "Aerasi baki semai & jaga jarak antar tray",
        detail = "Beri jarak 10 cm antar tray semai dan letakkan baki di atas rak minimal 50 cm dari tanah.",
        qualityImpact = "Mencegah etiolasi (batang rapuh) sehingga bibit tumbuh kekar dan siap menopang buah berbobot berat.",
        weight = 2,
        qualityTarget = QualityTarget.VIGOR_AKAR,
        howToExecute = "Atur tray di rak bambu/besi berketinggian 50 cm. Beri rongga udara 10 cm antar tray semai agar sirkulasi udara bawah pot lancar.",
        scientificBasis = "Pruning udara (air root pruning) mencegah akar melingkar (root bound) dan merangsang perbanyakan tudung akar serabut.",
        targetMetric = "Rasio tinggi bibit terhadap diameter batang < 10 (bibit pendek kokoh)",
        toolsAndMaterials = listOf(
            "Rak semai berongga (tinggi 50 cm dari tanah)",
            "Penggaris ukur jarak antar baki",
            "Alas ram kawat penopang baki"
        ),
        executionSteps = listOf(
            "Pastikan rak semai bersih dan memiliki ketinggian minimal 50 cm dari tanah.",
            "Susun baki semai dengan memberi celah udara renggang 10 cm antar baki.",
            "Pastikan lubang drainase bawah tiap sel baki tidak tertutup permukaan datar rapat.",
            "Periksa sirkulasi udara di bawah kolong rak tidak terhalang rumput liar."
        ),
        optimalTiming = "Pagi hari setelah penyiraman baki semai",
        commonMistakesToAvoid = listOf(
            "Menaruh baki semai langsung di atas tanah yang memicu akar menembus tanah.",
            "Merapatkan baki tanpa jarak celah sehingga terjadi kelembaban stagnan."
        ),
        successVerification = "Ujung akar serabut tertahan rapi di dalam sel baki tanpa memanjang keluar melingkar.",
        safetyGear = listOf("Sarung tangan katun bibit"),
        durationMinutes = 15,
        troubleshootingTip = "Bila dasar tray semai lembab berlebih dan berlumut, gunakan ganjal kayu setinggi 15 cm agar sirkulasi udara bawah baki lancar.",
        qualitySigns = "Media semai tidak berlumut hijau, perakaran mendapat pasokan oksigen prima dan tidak terjadi pembusukan akar semai."
    )

    // Stage 2: Vegetatif (1 s.d. 35 HST)

    private fun wiwilMission(day: Long) = Mission(
        id = "m$day-wiwil",
        category = Category.UMUM,
        title = "Wiwil (pangkas) tunas air bawah cabang Y",
        detail = "Buang tunas liar di ketiak daun bawah percabangan utama agar nutrisi fokus ke tajuk produktif.",
        qualityImpact = "Sirkulasi udara lebih baik menekan Antraknosa & buah lebih besar seragam.",
        weight = 3,
        qualityTarget = QualityTarget.WARNA_KILAP,
        howToExecute = "Gunakan ibu jari dan telunjuk bersih untuk mematahkan tunas air sepanjang 2-5 cm di ketiak daun sebelum cabang Y.",
        scientificBasis = "Memotong tunas parasitik mengarahkan asimilat fotosintesis ke primordia bunga dan menjaga LAI < 3.5 untuk aerasi kanopi.",
        targetMetric = "Semua tunas di bawah cabang Y bersih 100%",
        toolsAndMaterials = listOf(
            "Alkohol 70% pensanitasi tangan",
            "Sarung tangan lateks bersih",
            "Ember penampung sisa pangkasan tunas"
        ),
        executionSteps = listOf(
            "Semprot sarung tangan atau jari dengan alkohol 70% sebelum wiwil dimulai.",
            "Identifikasi tunas air di ketiak daun di bawah cabang utama Y (panjang 2-5 cm).",
            "Patahkan tunas air ke arah samping dengan gerakan mantap dan rapi.",
            "Kumpulkan seluruh serpihan tunas ke dalam ember, jangan biarkan jatuh di atas mulsa."
        ),
        optimalTiming = "Pukul 08:30 - 10:30 WIB saat embun telah kering agar luka patahan cepat sembuh",
        commonMistakesToAvoid = listOf(
            "Memangkas saat daun basah kuyup oleh embun atau hujan (memicu infeksi bakteri).",
            "Menarik tunas ke arah bawah yang merobek kulit batang utama."
        ),
        successVerification = "Batang utama di bawah cabang Y bersih 100%, luka pangkas mengering kecokelatan rapi.",
        safetyGear = listOf("Sarung tangan steril lateks", "Gunting pangkas steril 70% alkohol"),
        durationMinutes = 45,
        troubleshootingTip = "Jika tunas ketiak sudah berkayu (> 5 mm), jangan ditarik paksa; gunakan pisau atau gunting steril agar kulit batang utama tidak terkoyak.",
        qualitySigns = "Batang utama berbentuk 'V' kokoh, distribusi asimilasi hara fokus ke 2 cabang primer untuk buah berukuran seragam."
    )

    private fun nitrogenBalanceMission(day: Long) = Mission(
        id = "m$day-n-balance",
        category = Category.NUTRISI,
        title = "Jaga dosis N seimbang, siapkan transisi generatif",
        detail = "Hindari urea berlebih; siapkan pupuk Ca/B untuk fase pembungaan berikutnya.",
        qualityImpact = "Nitrogen berlebih memperlemah dinding sel & memicu rontok bunga.",
        weight = 2,
        qualityTarget = QualityTarget.VIGOR_AKAR,
        howToExecute = "Kocorkan NPK 16-16-16 dosis 5 g/tanaman + 1 ml asam humat per liter. Hindari pupuk tunggal Urea berkadar tinggi.",
        scientificBasis = "Kelebihan N menurunkan rasio C/N, menghasilkan sel mesofil berdinding tipis yang rentan penetrasi spora patogen.",
        targetMetric = "EC larutan kocor 1,5 - 1,8 mS/cm, pH tanah 6,0 - 6,8",
        toolsAndMaterials = listOf(
            "Pupuk NPK 16-16-16 seimbang",
            "Asam humat 85%",
            "EC meter / TDS meter & ember takar kocor"
        ),
        executionSteps = listOf(
            "Timbang NPK 16-16-16 dosis 5 gram/tanaman dan campurkan 1 ml asam humat per liter air.",
            "Aduk rata dalam air bersih, ukur konduktivitas larutan kocor pada rentang 1,5 - 1,8 mS/cm.",
            "Buat lubang kocor pupuk pada mulsa berjarak 10 cm dari pangkal batang.",
            "Kocorkan 200 ml larutan pupuk secara presisi ke dalam setiap lubang kocor."
        ),
        optimalTiming = "Pukul 06:30 - 08:30 WIB pada tanah yang telah berkelembaban kapasitas lapang",
        commonMistakesToAvoid = listOf(
            "Menggunakan pupuk Urea tunggal berlebih (membuat dinding sel sukulen tipis dan mengundang patek).",
            "Mengocorkan pupuk pekat pada media tanah yang kering kerontang."
        ),
        successVerification = "EC larutan terkalibrasi 1,5 - 1,8 mS/cm, daun tetap tebal kaku berwarna hijau tua alami.",
        safetyGear = listOf("Sarung tangan karet", "Masker pelindung debu pupuk"),
        standardDosagePerLiter = 2.5,
        dosageUnit = "gram",
        durationMinutes = 35,
        troubleshootingTip = "Bila daun berwarna hijau terlalu gelap dan melengkung seperti mangkuk, segera hentikan pupuk Urea/ZA dan kocorkan Kalsium Nitrat + Kalium Sulfat.",
        qualitySigns = "Warna daun hijau segar proporsional (SPAD 38-44), dinding sel batang tebal, tidak memicu kerontokan bunga atau serangan antraknosa."
    )

    private fun silicaEpidermisMission(day: Long) = Mission(
        id = "m$day-silica",
        category = Category.NUTRISI,
        title = "Aplikasi Silika terlarut + Kalsium penguat daun",
        detail = "Semprot Silika cair (SiO2) dosis 1 ml/L untuk membentuk lapisan silikat keras di kutikula tanaman.",
        qualityImpact = "Dinding sel keras tahan terhadap gigitan hama trips dan tusukan spora antraknosa.",
        weight = 2,
        qualityTarget = QualityTarget.PERICARP_TEBAL,
        howToExecute = "Semprotkan silika terlarut pada pagi hari pukul 06:30-08:00 WIB, basahi bagian atas dan bawah daun merata.",
        scientificBasis = "Deposisi silika pada sel epidermis membentuk double layer silika-selulosa yang menjadi perisai fisik alami.",
        targetMetric = "Kekerasan jaringan epidermis daun meningkat, indeks serangan hama 0%",
        toolsAndMaterials = listOf(
            "Silika cair larut air (SiO2 teraktivasi kalium silikat)",
            "Gelas ukur mililiter presisi",
            "Tangki sprayer kabut halus bertekanan stabil"
        ),
        executionSteps = listOf(
            "Takar 1 ml silika cair per liter air bersih (konsentrasi 1 ml/L).",
            "Kocok kuat dalam tangki semprot hingga cairan silikat terdispersi homogen.",
            "Setel nosel semprot ke mode kabut halus bertekanan sedang.",
            "Semprotkan merata ke seluruh kanopi tajuk tanaman, basahi atas dan bawah daun."
        ),
        optimalTiming = "Pukul 06:30 - 08:00 WIB saat stomata dan kutikula daun aktif menyerap hara",
        commonMistakesToAvoid = listOf(
            "Mencampur silika pekat langsung bersama kalsium nitrat tanpa pengenceran air terlebih dahulu.",
            "Menyemprot dengan tetesan butir kasar yang mengalir terbuang ke tanah."
        ),
        successVerification = "Permukaan daun terasa kaku berlilin saat diraba, kutikula mengkilap tanpa kerak kapur.",
        safetyGear = listOf("Masker respirator partikel", "Kacamata pelindung (goggles)", "Sarung tangan nitril"),
        standardDosagePerLiter = 1.5,
        dosageUnit = "gram",
        durationMinutes = 30,
        troubleshootingTip = "Gunakan silika larut air dengan pH stabil; jangan campur dengan pupuk mikro ber-chelating asam kuat tanpa uji kelarutan dalam ember kecil.",
        qualitySigns = "Kutikula daun dan kulit buah terasa kesat dan keras, sel-sel epidermis terlapisi lapisan silika biogenik tahan gigitan hama pengisap."
    )

    private fun yellowTrapMission(day: Long) = Mission(
        id = "m$day-yellowtrap",
        category = Category.SANITASI,
        title = "Pasang & pantau perangkap likat kuning 40 titik/ha",
        detail = "Pasang lembar kuning berperekat setinggi 20 cm di atas tajuk tanaman cabai untuk menangkap Thrips & Kutu Kebul.",
        qualityImpact = "Menekan populasi hama vektor pembawa virus keriting dan penusuk buah muda.",
        weight = 2,
        qualityTarget = QualityTarget.BEBAS_CACAT,
        howToExecute = "Gantung pelat plastik kuning berlem serangga pada tiang ajir di sela bedengan. Ganti jika permukaan tertutup serangga > 60%.",
        scientificBasis = "Spektrum warna kuning cerah (panjang gelombang 570-590 nm) merupakan atraktan visual bagi hama Thrips dan kutu kebul.",
        targetMetric = "Populasi kutu kebul < 2 ekor/perangkap/hari",
        toolsAndMaterials = listOf(
            "Lembar perangkap plastik kuning 580 nm",
            "Lem perekat serangga transparan",
            "Tiang ajir bambu 1,5 meter & kawat pengikat"
        ),
        executionSteps = listOf(
            "Oleskan lem serangga secara merata pada kedua permukaan pelat kuning.",
            "Pasang lembar perangkap pada tiang ajir setinggi 20 cm di atas pucuk tajuk cabai.",
            "Atur jarak antar perangkap 10-15 meter secara merata di seluruh petak bedengan.",
            "Pantau dan catat jumlah tangkapan serangga vektor di aplikasi DAPE setiap pagi."
        ),
        optimalTiming = "Pukul 07:00 - 09:00 WIB saat hembusan angin masih tenang",
        commonMistakesToAvoid = listOf(
            "Memasang perangkap terlalu rendah di dalam tajuk rimbun yang menempel ke daun.",
            "Membiarkan perangkap yang kotor tertutup debu > 60% tanpa diganti."
        ),
        successVerification = "Perangkap terpasang tegak 20 cm di atas tajuk, tangkapan kutu kebul < 2 ekor/perangkap/hari.",
        safetyGear = listOf("Sarung tangan sekali pakai (anti-lengket lem)"),
        durationMinutes = 20,
        troubleshootingTip = "Jika lem perangkap tertutup debu jalan atau bangkai serangga lebih dari 70%, oleskan lem perangkap serangga baru agar daya rekat tetap maksimal.",
        qualitySigns = "Populasi kutu kebul, thrips, dan lalat penggorok daun terkendali di bawah ambang batas ekonomi tanpa residu kimia berlebih."
    )

    private fun bedSlopeClinometerMission(day: Long) = Mission(
        id = "m$day-bed-slope",
        category = Category.INSPEKSI,
        title = "Verifikasi klinometer giroskop kemiringan bedengan",
        detail = "Gunakan sensor giroskop ponsel untuk mengukur kemiringan bedengan 0,5° - 1,0° dan saluran drainase parit.",
        qualityImpact = "Drainase sempurna mencegah genangan air penyebab busuk akar Phytophthora yang menggugurkan buah.",
        weight = 2,
        qualityTarget = QualityTarget.VIGOR_AKAR,
        howToExecute = "Buka instrumen klinometer, letakkan punggung ponsel di atas permukaan bedengan sepanjang 2 meter. Pastikan sudut kemiringan tertera 0,5° - 1,0°.",
        scientificBasis = "Kemiringan 0,5° - 1,0° menghasilkan kecepatan aliran gravitasi air tanpa mengikis tanah bedengan mulsa (erosi nol).",
        targetMetric = "Sudut kemiringan bedengan β = 0,5° - 1,0°, bebas genangan pascahujan",
        toolsAndMaterials = listOf(
            "Smartphone dengan sensor giroskop DAPE (Tab Kompas)",
            "Mistar lurus kayu/aluminium 2 meter",
            "Cangkul pembersih endapan parit"
        ),
        executionSteps = listOf(
            "Buka instrumen Klinometer & Giroskop pada menu Kompas & Sensor aplikasi DAPE.",
            "Letakkan mistar datar memanjang di atas bedengan dan taruh ponsel di tengah mistar.",
            "Baca sudut kemiringan longitudinal bedengan; pastikan berada pada rentang 0,5° - 1,0°.",
            "Periksa kedalaman parit (minimal 40 cm dari puncak bedengan) dan bersihkan sumbatan parit."
        ),
        optimalTiming = "Pukul 08:00 - 11:00 WIB atau segera setelah hujan lebat reda",
        commonMistakesToAvoid = listOf(
            "Mengukur langsung di atas mulsa bergelombang tanpa menggunakan mistar datar.",
            "Membiarkan parit buntu di ujung petak yang memicu genangan air balik."
        ),
        successVerification = "Sudut kemiringan terukur 0,5° - 1,0°, air hujan mengalir tuntas keluar petak dalam 15 menit.",
        safetyGear = listOf("Sepatu bot kebun bergerigi anti-slip"),
        durationMinutes = 15,
        troubleshootingTip = "Bila kemiringan bedengan < 0,2%, buat sudetan silang pembuang air di tengah bedengan menuju parit utama agar air tidak terjebak.",
        qualitySigns = "Drainase parit bekerja optimal saat hujan lebat, tidak ada genangan air yang memicu asfiksia akar atau Phytophthora capsici."
    )

    private fun canopyCeptometerMission(day: Long) = Mission(
        id = "m$day-ceptometer",
        category = Category.INSPEKSI,
        title = "Ceptometer sensor Lux uji penetrasi cahaya kanopi",
        detail = "Ukur rasio intensitas cahaya Lux di atas tajuk vs di dasar percabangan Y menggunakan sensor cahaya ponsel.",
        qualityImpact = "Menjamin distribusi foton surya merata untuk biosintesis karotenoid kapsantin (merah mengkilap).",
        weight = 2,
        qualityTarget = QualityTarget.WARNA_KILAP,
        howToExecute = "Arahkan sensor cahaya ponsel ke langit terbuka di atas tajuk, catat Lux. Kemudian arahkan sensor di bawah tajuk cabang Y. Hitung rasio penetrasi.",
        scientificBasis = "Hukum Beer-Lambert: fraksi transmisi cahaya I/I0 > 0,40 diperlukan untuk fotosintesis daun bawah dan pembentukan kutikula lilin buah.",
        targetMetric = "Transmisi cahaya tajuk > 40%, Leaf Area Index (LAI) 2,5 - 3,2",
        toolsAndMaterials = listOf(
            "Smartphone dengan sensor cahaya ambient (Lux)",
            "Tongkat pemegang ponsel vertikal",
            "Aplikasi DAPE fitur Sensor Lab (Tab 10)"
        ),
        executionSteps = listOf(
            "Buka menu Sensor Lab pada aplikasi DAPE.",
            "Posisikan sensor ponsel menghadap langit di atas kanopi tanaman, tekan Ukur Lux Atas (I0).",
            "Turunkan sensor ponsel tepat di bawah tajuk cabang Y dekat mulsa, tekan Ukur Lux Bawah (I).",
            "Aplikasi menghitung fraksi transmisi cahaya (I/I0) dan estimasi Leaf Area Index (LAI)."
        ),
        optimalTiming = "Pukul 11:00 - 13:00 WIB saat matahari berada di puncak zenith",
        commonMistakesToAvoid = listOf(
            "Bayangan tubuh petani menutupi sensor cahaya saat pengukuran berlangsung.",
            "Mengukur saat kondisi langit berawan mendung tebal berganti-ganti secara cepat."
        ),
        successVerification = "Rasio transmisi cahaya tajuk > 40%, Leaf Area Index (LAI) berada pada rentang seimbang 2,5 - 3,2.",
        safetyGear = listOf("Topi kebun", "Tali lanyard pengaman smartphone"),
        durationMinutes = 20,
        troubleshootingTip = "Bila nilai LAI > 4.0 (terlalu rimbun), segera lakukan perompesan 20% daun tua di bawah cabang 'V' untuk memperbaiki sirkulasi udara.",
        qualitySigns = "Indeks Luas Daun (LAI) ideal 3.0-3.5, sinar matahari tembus merata ke bagian dalam tajuk, menstimulasi fotosintesis bunga."
    )

    private fun humicAcidRootMission(day: Long) = Mission(
        id = "m$day-humic-root",
        category = Category.NUTRISI,
        title = "Kocor asam humat 85% + mikroba pelarut fosfat",
        detail = "Kocorkan larutan asam humat 2 g/L di zona perakaran untuk meningkatkan kapasitas tukar kation (KTK) media.",
        qualityImpact = "Akar lateral meluas memaksimalkan serapan Kalsium dan Kalium untuk pericarp tebal berkualitas ekspor.",
        weight = 2,
        qualityTarget = QualityTarget.VIGOR_AKAR,
        howToExecute = "Larutkan 20 gram asam humat larut air dan konsorsium mikroba pelarut fosfat dalam 10 liter air. Kocor 250 ml pada setiap lubang tanam.",
        scientificBasis = "Gugus karboksil dan fenolat asam humat mengkhelat ion Ca2+ dan Mg2+, mencegah fiksasi hara oleh lempung tanah.",
        targetMetric = "KTK tanah zona rizosfer > 25 meq/100g, serapan Kalsium meningkat > 30%",
        toolsAndMaterials = listOf(
            "Asam humat soluble powder 85%",
            "Inokulan mikroba pelarut fosfat (Pseudomonas)",
            "Ember pencampur 10L & gayung takar 250 ml"
        ),
        executionSteps = listOf(
            "Larutkan 20 gram asam humat dan 10 ml mikroba ke dalam 10 liter air sumur bersih.",
            "Aduk rata hingga cairan berwarna hitam pekat homogen tanpa butiran gumpal.",
            "Ambil 250 ml larutan menggunakan gayung takar presisi.",
            "Kocorkan tepat pada lubang tanam di sekeliling perakaran cabai secara merata."
        ),
        optimalTiming = "Pukul 06:30 - 08:30 WIB saat tanah lembab sejuk",
        commonMistakesToAvoid = listOf(
            "Menggunakan asam humat berkadar rendah yang tidak larut air dan menyumbat lubang mulsa.",
            "Mencampurkan bakterisida kimia pada ember yang sama dengan inokulan hayati."
        ),
        successVerification = "Larutan meresap ke zona rizosfer, serapan Kalsium dan Kalium terakselerasi aktif.",
        safetyGear = listOf("Sarung tangan karet", "Masker debu organik"),
        standardDosagePerLiter = 3.0,
        dosageUnit = "gram",
        durationMinutes = 30,
        troubleshootingTip = "Pastikan asam humat terlarut sempurna tanpa gumpalan; saring larutan induk sebelum dimasukkan ke tangki kocor agar pipa tidak tersumbat.",
        qualitySigns = "Kapasitas Tukar Kation (KTK) rizosfer meningkat, bulu-bulu akar aktif memanjang dan serapan hara kation K+, Ca2+, Mg2+ melonjak drastis."
    )

    // Stage 3: Pembungaan (36 s.d. 55 HST)

    private fun calciumBoronMission(day: Long) = Mission(
        id = "m$day-ca-b",
        category = Category.NUTRISI,
        title = "Aplikasi Kalsium Nitrat + Boron (kunci pericarp)",
        detail = "Semprot/kocor Ca(NO3)2 + Boron sore hari. Ini fase paling kritis untuk kualitas buah.",
        qualityImpact = "Dinding sel (pericarp) tebal → buah mulus, tahan simpan, bebas busuk ujung (BER).",
        weight = 4,
        qualityTarget = QualityTarget.PERICARP_TEBAL,
        howToExecute = "Larutkan Kalsium Nitrat 2 g/L + Boron 0,5 g/L. Kocorkan 200 ml per lubang tanam atau semprotkan saat stomata terbuka.",
        scientificBasis = "Boron bertindak sebagai transporter hara yang membantu mobilisasi ion Ca2+ melintasi dinding sel membentuk ikatan kalsium pektat.",
        targetMetric = "Tebal dinding sel ovarium buah > 1.8 mm, kejadian BER = 0%",
        toolsAndMaterials = listOf(
            "Kalsium Nitrat (Ca(NO3)2) foliar grade",
            "Boron larut air murni",
            "Sprayer elektrik bertekanan kabut halus"
        ),
        executionSteps = listOf(
            "Timbang 20 gram Kalsium Nitrat dan 5 gram Boron untuk 10 liter air bersih (Ca 2 g/L + B 0,5 g/L).",
            "Larutkan terpisah dalam ember kecil sebelum dituangkan ke tangki semprot utama.",
            "Semprotkan secara merata kabut halus ke bunga mekar dan buah pentil kecil.",
            "Pastikan cairan membasahi tangkai bunga dan calyx penopang buah."
        ),
        optimalTiming = "Pukul 15:30 - 17:30 WIB (sore hari saat laju transpirasi melambat)",
        commonMistakesToAvoid = listOf(
            "Mencampur kalsium bersama pupuk berkandungan sulfat atau fosfat (terjadi endapan gipsum tak larut!).",
            "Menyemprot di siang hari saat suhu udara > 32°C."
        ),
        successVerification = "Dinding ovarium pentil tebal padat, kejadian busuk ujung buah (BER) nol persen.",
        safetyGear = listOf("Masker respirator semprot", "Sarung tangan nitril", "Kacamata pelindung"),
        standardDosagePerLiter = 2.0,
        dosageUnit = "gram",
        durationMinutes = 40,
        troubleshootingTip = "Jangan mencampur kalsium nitrat dengan pupuk fosfat pekat (seperti MKP atau SP-36) karena akan mengendap membentuk kalsium fosfat tak larut.",
        qualitySigns = "Bunga mekar serempak tidak rontok, terbentuk tabung polen yang kuat, dan dinding ovarium buah terbentuk kokoh bebas busuk pantat (BER)."
    )

    private fun flowerRetentionMission(day: Long) = Mission(
        id = "m$day-flower",
        category = Category.AIR,
        title = "Stabilkan kelembaban untuk cegah rontok bunga",
        detail = "Hindari kekeringan/over-watering mendadak; jaga kelembaban stabil & suhu tidak ekstrem.",
        qualityImpact = "Bunga tidak rontok → populasi buah lebih banyak & seragam.",
        weight = 2,
        qualityTarget = QualityTarget.PERICARP_TEBAL,
        howToExecute = "Jaga kelembaban tanah 65% kapasitas lapang. Hindari penyiraman deras yang membasahi putik bunga mekar.",
        scientificBasis = "Kekeringan mendadak memicu sintesis asam absisat (ABA) di tangkai bunga yang mengaktifkan zona absisi dan menggugurkan putik.",
        targetMetric = "Retensi bunga mekar menjadi bakal buah (fruit set) > 80%",
        toolsAndMaterials = listOf(
            "Tensiometer tanah presisi",
            "Higrometer pengukur RH udara",
            "Sprayer kabut pendingin kanopi"
        ),
        executionSteps = listOf(
            "Periksa kelembaban tanah; pertahankan secara ketat pada 65% kapasitas lapang.",
            "Jika suhu lahan > 33°C dengan RH < 50%, semprotkan kabut air tipis di atas lorong bedengan.",
            "Hindari penggenangan parit berlebih yang memicu defisiensi oksigen perakaran.",
            "Catat persentase bunga mekar yang berhasil menjadi pentil buah (fruit set)."
        ),
        optimalTiming = "Pukul 07:00 WIB untuk cek lengas tanah, pukul 12:00 WIB untuk cek suhu mikro",
        commonMistakesToAvoid = listOf(
            "Membiarkan tanah mengering hingga daun layu saat fase antesis bunga.",
            "Genangan air berlebih di parit yang memicu pembentukan gas etilen perakaran."
        ),
        successVerification = "Retensi bunga mekar menjadi pentil buah (fruit set) mencapai > 80%.",
        safetyGear = listOf("Sepatu bot kebun sol datar"),
        durationMinutes = 25,
        troubleshootingTip = "Hindari penyemprotan insektisida kimia berbau tajam pada pagi hari saat lebah dan serangga penyerbuk alami sedang giat mencari nektar.",
        qualitySigns = "Persentase buah jadi (fruit set) > 85%, bunga mekar tahan terpaan angin kencang dan tidak rontok massal."
    )

    private fun fruitFlyTrapMission(day: Long) = Mission(
        id = "m$day-fruitfly",
        category = Category.SANITASI,
        title = "Pasang perangkap metil eugenol untuk lalat buah",
        detail = "Gantung botol perangkap berumpan atraktan metil eugenol di perimeter kebun untuk menangkap lalat buah jantan.",
        qualityImpact = "Mencegah sengatan lalat buah pada pentil cabai muda yang menyebabkan buah busuk dan rontok.",
        weight = 2,
        qualityTarget = QualityTarget.BEBAS_CACAT,
        howToExecute = "Teteskan 2-3 tetes metil eugenol pada kapas dalam botol plastik berlubang kecil. Gantung di luar tajuk setinggi 1,5 meter.",
        scientificBasis = "Metil eugenol adalah feromon sintetis kuat yang memerangkap lalat buah jantan Bactrocera dorsalis sebelum mengawini betina.",
        targetMetric = "Serangan lalat buah pada ovarium pentil = 0%",
        toolsAndMaterials = listOf(
            "Botol plastik air mineral 1,5L berlubang masuk 5 mm",
            "Cairan atraktan metil eugenol murni",
            "Kapas bola & kawat penggantung 1,5 meter"
        ),
        executionSteps = listOf(
            "Buat 4 lubang masuk berdiameter 5 mm di sekeliling dinding botol plastik.",
            "Teteskan 3-4 tetes metil eugenol pada kapas bola bersih yang diikat kawat di dalam botol.",
            "Isi dasar botol dengan air sabun setinggi 2 cm untuk mematikan lalat yang masuk.",
            "Gantungkan botol pada tiang ajir setinggi 1,5 meter di pinggiran luar perimeter kebun."
        ),
        optimalTiming = "Pukul 07:00 - 09:00 WIB saat lalat buah jantan aktif mencari feromon",
        commonMistakesToAvoid = listOf(
            "Menggantung perangkap di tengah tajuk cabai (memancing lalat berkumpul dekat buah pentil).",
            "Meneteskan metil eugenol berlebihan hingga mengotori dinding luar botol."
        ),
        successVerification = "Lalat jantan terperangkap di dasar botol, nol sengatan ovipositor pada pentil buah.",
        safetyGear = listOf("Sarung tangan sekali pakai lateks (minyak atraktan)"),
        durationMinutes = 20,
        troubleshootingTip = "Gantungkan perangkap di luar tajuk tanaman pada ketinggian 1-1,5 meter; jangan menaruh perangkap tepat di tengah tajuk karena justru memikat lalat ke tanaman.",
        qualitySigns = "Buah cabai muda bebas dari lubang sengatan ovipositor lalat buah (Bactrocera dorsalis) dan larva belatung di dalam rongga buah."
    )

    private fun foliarPBMission(day: Long) = Mission(
        id = "m$day-foliar-pb",
        category = Category.NUTRISI,
        title = "Aplikasi pupuk Phospat tinggi + Kalium pembungaan",
        detail = "Semprotkan pupuk MKP (Mono Kalium Phospat) dosis 2 g/L untuk memperkuat tangkai bunga dan menyeragamkan ukuran buah.",
        qualityImpact = "Tangkai buah kuat kokoh, buah tumbuh serempak dan berukuran besar seragam.",
        weight = 2,
        qualityTarget = QualityTarget.WARNA_KILAP,
        howToExecute = "Semprotkan kabut larutan MKP di pagi hari pukul 06:30-08:00 WIB ke seluruh kanopi tanaman.",
        scientificBasis = "Fosfat menyediakan energi ATP/ADP untuk pembelahan sel ovarium, Kalium mengatur turgor sel meristematik.",
        targetMetric = "Tangkai bunga berdiameter > 1,5 mm, tidak mudah layu",
        toolsAndMaterials = listOf(
            "Pupuk MKP murni (P2O5 52%, K2O 34%)",
            "Gelas takar gramasi presisi",
            "Tangki sprayer nosel kerucut kabut halus"
        ),
        executionSteps = listOf(
            "Larutkan pupuk MKP dosis 2 gram per liter air bersih (20 gram per tangki 10L).",
            "Aduk rata hingga seluruh butiran kristal larut bening sempurna.",
            "Semprotkan kabut halus ke seluruh tajuk kanopi dan tangkai bunga mekar.",
            "Pastikan semprotan menyentuh permukaan bawah daun untuk serapan fosfat cepat."
        ),
        optimalTiming = "Pukul 06:30 - 08:00 WIB saat cuaca cerah berembun tipis",
        commonMistakesToAvoid = listOf(
            "Menggunakan dosis > 3 g/L yang memicu stres garam pada pucuk daun muda.",
            "Menyemprot saat angin kencang (> 15 km/jam) yang menerbangkan droplet kabut."
        ),
        successVerification = "Tangkai bunga berdiameter > 1,5 mm, bunga mekar serempak dan tidak mudah rontok.",
        safetyGear = listOf("Masker respirator", "Sarung tangan karet semprot"),
        standardDosagePerLiter = 3.0,
        dosageUnit = "gram",
        durationMinutes = 35,
        troubleshootingTip = "Gunakan nozel semprot berkabut halus (hollow cone) dengan tekanan 3-4 bar agar larutan pupuk menempel merata di permukaan bawah daun tempat stomata berada.",
        qualitySigns = "Dinding pericarp buah berbobot padat, rongga buah terisi penuh biji bernas, dan kulit buah tebal elastis."
    )

    private fun barometerSprayCheckMission(day: Long) = Mission(
        id = "m$day-baro-spray",
        category = Category.INSPEKSI,
        title = "Verifikasi barometer tekanan udara pra-semprot Kalsium",
        detail = "Periksa tren barometer ponsel (ΔP/Δt); pastikan tidak ada penurunan tekanan curam (>1,5 hPa/jam) sebelum aplikasi pupuk daun.",
        qualityImpact = "Mencegah pupuk Kalsium dan Boron tercuci hujan lebat sebelum terserap membran kutikula buah.",
        weight = 2,
        qualityTarget = QualityTarget.PERICARP_TEBAL,
        howToExecute = "Buka instrumen barometer/cuaca di aplikasi. Pastikan grafik tekanan stabil atau naik. Hindari penyemprotan jika terdeteksi peringatan badai mikro.",
        scientificBasis = "Penurunan tekanan udara drastis mengindikasikan konveksi awan cumulonimbus lokal yang memicu hujan lebat dalam 60-120 menit.",
        targetMetric = "Laju perubahan tekanan |ΔP/Δt| < 1,0 hPa/jam (kondisi atmosfer stabil)",
        toolsAndMaterials = listOf(
            "Smartphone dengan sensor barometer (TYPE_PRESSURE)",
            "Aplikasi DAPE fitur Cuaca Lapangan (Tab 7)",
            "Tangki semprot pupuk foliar"
        ),
        executionSteps = listOf(
            "Buka fitur Cuaca & Hujan Lapangan pada aplikasi DAPE.",
            "Amati grafik pembacaan tekanan udara barometer selama 3 jam terakhir.",
            "Pastikan laju perubahan tekanan |ΔP/Δt| kurang dari 1,0 hPa/jam (grafik stabil).",
            "Jika tekanan anjlok > 1,5 hPa/jam, tunda penyemprotan karena hujan badai segera tiba."
        ),
        optimalTiming = "30 menit sebelum memulai pengadukan tangki semprot pupuk foliar",
        commonMistakesToAvoid = listOf(
            "Memaksakan semprot saat sinyal badai mikro aktif sehingga pupuk terbuang sia-sia.",
            "Mengabaikan sensor barometer fisik dan hanya mengandalkan perkiraan awan kasat mata."
        ),
        successVerification = "Indikator atmosfer stabil (|ΔP/Δt| < 1,0 hPa/jam), pupuk terserap sempurna sebelum hujan.",
        safetyGear = listOf("Topi pelindung lapangan"),
        durationMinutes = 10,
        troubleshootingTip = "Bila tekanan udara turun drastis (> 1,5 hPa/jam), tunda penyemprotan dan amankan peralatan; prioritaskan sanitasi gulma manual.",
        qualitySigns = "Aplikasi pestisida atau pupuk daun efektif 100%, tidak tercuci oleh air hujan dalam 2 jam setelah penyemprotan."
    )

    private fun flowerVibrationPollinationMission(day: Long) = Mission(
        id = "m$day-flower-vibrate",
        category = Category.UMUM,
        title = "Getarkan tali ajir pagi hari untuk penyerbukan sempurna",
        detail = "Goyang lembut kawat atau tali ajir per baris bedengan pukul 08:00 - 09:30 saat bunga cabai membuka penuh.",
        qualityImpact = "Penyerbukan mandiri optimal menghasilkan buah cabai lurus panjang simetris tanpa bengkok (Grade A+).",
        weight = 2,
        qualityTarget = QualityTarget.GRADE_A_PANEN,
        howToExecute = "Berjalan santai menyusuri bedengan sambil menyentuh/menggoyang kawat bentangan ajir secara ritmis. Lakukan saat bunga kering dari embun pagi.",
        scientificBasis = "Bunga cabai bersifat cleistogamous/chasmogamous; vibrasi mikro 20-50 Hz memicu pelepasan butir polen dari anther ke kepala putik (stigma).",
        targetMetric = "Fruit set bunga mekar > 85%, buah lurus simetris > 90%",
        toolsAndMaterials = listOf(
            "Tongkat bambu lentur berujung tumpul / tangan bersarung kain",
            "Tali bentangan ajir lanjaran bedengan"
        ),
        executionSteps = listOf(
            "Berjalan santai menyusuri lorong bedengan saat bunga cabai mekar membuka sempurna.",
            "Sentuh dan goyangkan tali bentangan ajir secara ritmis (20-30 getaran per menit).",
            "Pastikan getaran merontokkan butir polen ke kepala putik tanpa mematahkan ranting.",
            "Ulangi gerakan pada seluruh baris bedengan secara merata."
        ),
        optimalTiming = "Pukul 08:00 - 09:30 WIB saat bunga kering dari embun pagi",
        commonMistakesToAvoid = listOf(
            "Menggoyang terlalu kasar yang mematahkan tangkai bunga muda.",
            "Melakukan getaran saat bunga masih basah kuyup oleh embun (polen menggumpal)."
        ),
        successVerification = "Butir polen membasahi kepala putik, fruit set simetris lurus mencapai > 90%.",
        safetyGear = listOf("Sarung tangan katun bersih lembut"),
        durationMinutes = 25,
        troubleshootingTip = "Goyangkan tali gawar secara lembut; jangan menghentak kencang yang dapat mematahkan tangkai bunga muda atau merobek daun.",
        qualitySigns = "Seluruh putik terserbuki sempurna menghasilkan buah berbentuk silindris lurus tanpa lekukan bengkok akibat malformasi ovul."
    )

    private fun flowerPetalSanitationMission(day: Long) = Mission(
        id = "m$day-petal-sanitation",
        category = Category.SANITASI,
        title = "Sanitasi kelopak bunga gugur di atas mulsa",
        detail = "Bersihkan mahkota bunga kering yang menempel di ujung buah pentil atau jatuh di atas plastik mulsa.",
        qualityImpact = "Memutus inokulum awal jamur busuk basah Choanephora yang membuat pentil buah busuk hitam.",
        weight = 2,
        qualityTarget = QualityTarget.BEBAS_CACAT,
        howToExecute = "Gunakan sapu lidi lembut atau tepis perlahan ujung pentil buah muda agar mahkota bunga kering terlepas dan tidak membusuk saat terkena air.",
        scientificBasis = "Sisa kelopak bunga layu yang basah merupakan media tanam ideal bagi koloni spora Choanephora cucurbitarum.",
        targetMetric = "Pentil buah bersih dari mahkota kering 100%, busuk pentil 0%",
        toolsAndMaterials = listOf(
            "Kuas halus pembersih / sapu lidi mini bersih",
            "Kantong plastik penampung sisa organik"
        ),
        executionSteps = listOf(
            "Amati ujung pentil buah muda yang telah mekar 2-3 hari.",
            "Tepis perlahan sisa mahkota bunga kering yang menempel di ujung buah pentil.",
            "Pungut mahkota bunga layu yang gugur di atas permukaan plastik mulsa.",
            "Kumpulkan ke kantong penampung untuk dimusnahkan di luar lahan."
        ),
        optimalTiming = "Pukul 09:00 - 11:00 WIB saat mahkota bunga telah kering rapuh",
        commonMistakesToAvoid = listOf(
            "Menarik paksa kelopak bunga yang masih melekat kuat dan melukai ujung pentil.",
            "Membiarkan kelopak basah membusuk di atas mulsa tepat di bawah tetesan daun."
        ),
        successVerification = "Pentil buah bersih dari mahkota kering 100%, nol gejala busuk basah Choanephora.",
        safetyGear = listOf("Sarung tangan steril", "Wadah penampung kelopak"),
        durationMinutes = 30,
        troubleshootingTip = "Hanya sentuh mahkota bunga yang sudah berwarna cokelat kering; mahkota yang masih melekat kuat jangan ditarik paksa karena dapat melukai bakal buah.",
        qualitySigns = "Pangkal buah cabai muda mulus bebas spora jamur Botrytis cinerea atau Colletotrichum yang bersembunyi di balik mahkota bunga layu."
    )

    // Stage 4: Pembuahan & Pematangan (56 s.d. 75 HST)

    private fun potassiumMission(day: Long) = Mission(
        id = "m$day-k",
        category = Category.NUTRISI,
        title = "Tingkatkan Kalium (KNO3) untuk pembesaran & warna",
        detail = "Kocor KNO3 sesuai dosis preskriptif; K meningkatkan bobot & warna merah merata.",
        qualityImpact = "Kalium menaikkan grade (warna & bobot) dan daya simpan buah.",
        weight = 3,
        qualityTarget = QualityTarget.WARNA_KILAP,
        howToExecute = "Kocorkan KNO3 putih (13-0-45) dosis 4 g/tanaman bersama Magnesium Sulfat 1 g/tanaman dengan interval 5 hari.",
        scientificBasis = "Ion K+ mengaktifkan enzim sintesis gula dan pati, serta menstimulasi akumulasi pigmen karotenoid kapsantin (merah terang).",
        targetMetric = "Bobot rata-rata buah > 15 g/buah, warna merah merata > 85%",
        toolsAndMaterials = listOf(
            "Pupuk KNO3 putih kristal (13-0-45)",
            "Magnesium Sulfat (MgSO4)",
            "Timbangan gram digital & ember takar kocor"
        ),
        executionSteps = listOf(
            "Larutkan 4 gram KNO3 putih dan 1 gram MgSO4 per tanaman ke dalam 250 ml air bersih.",
            "Aduk rata hingga seluruh butiran kristal larut transparan.",
            "Kocorkan 250 ml larutan pupuk ke dalam lubang pupuk di sebelah lubang tanam.",
            "Ulangi aplikasi kocor kalium dengan interval 5 hari pada fase pembesaran buah."
        ),
        optimalTiming = "Pukul 06:30 - 08:30 WIB saat tanah berkelembaban sejuk",
        commonMistakesToAvoid = listOf(
            "Menggunakan pupuk KCl berkadar klorida tinggi yang merusak kilap merah kapsantin.",
            "Mengocorkan pada tanah yang sedang mengalami kekeringan ekstrem."
        ),
        successVerification = "Buah membesar padat terisi, bobot rata-rata buah > 15 gram, warna merah menyala serempak.",
        safetyGear = listOf("Sarung tangan karet", "Masker debu pupuk"),
        standardDosagePerLiter = 2.5,
        dosageUnit = "gram",
        durationMinutes = 40,
        troubleshootingTip = "Kocorkan pada media lembab; bila tanah kering, siram air biasa terlebih dahulu 15 menit sebelumnya agar akar tidak mengalami syok osmotik.",
        qualitySigns = "Kulit buah cabai mengkilap mengilap, tingkat kepedasan kapsaisin tinggi, dan bobot per buah meningkat 15-20%."
    )

    private fun antracnoseScoutMission(day: Long) = Mission(
        id = "m$day-antracnose",
        category = Category.SANITASI,
        title = "Pantau & petik buah berbercak Antraknosa",
        detail = "Cari bercak cekung gelap konsentris; petik & musnahkan buah terinfeksi agar tidak menular.",
        qualityImpact = "Satu buah ber-patek bisa membusukkan satu keranjang → sanitasi menjaga grade A.",
        weight = 2,
        qualityTarget = QualityTarget.BEBAS_CACAT,
        howToExecute = "Sisir setiap baris bedengan. Jika ada buah dengan bercak lesi patek sekecil apapun, petik dengan kantong plastik tertutup dan kubur di luar kebun.",
        scientificBasis = "Konidia Colletotrichum gloeosporioides menyebar dengan percikan air hujan (rain-splash) dan angin kencang.",
        targetMetric = "Insidensi antraknosa pada petak budidaya = 0%",
        toolsAndMaterials = listOf(
            "Sarung tangan plastik sekali pakai",
            "Kantong kresek hitam tertutup rapat",
            "Desinfektan klorin 5% pencuci tangan"
        ),
        executionSteps = listOf(
            "Sisir setiap baris bedengan, periksa buah yang menggantung di bawah tajuk rimbun.",
            "Identifikasi lesi awal: bercak cekung melingkar kebasahan berwarna oranye/hitam.",
            "Petik buah bergejala dengan sangat hati-hati, langsung masukkan ke kresek hitam tertutup.",
            "Ikat kantong rapat lalu kubur di kedalaman 50 cm di luar areal lahan tanam."
        ),
        optimalTiming = "Pukul 07:00 - 09:00 WIB sebelum spora mengering dan terbawa angin siang",
        commonMistakesToAvoid = listOf(
            "Membuang buah busuk patek ke parit bedengan (menjadi sumber spora jutaan konidia).",
            "Menyentuh buah sehat dengan tangan yang baru memetik buah sakit tanpa cuci tangan."
        ),
        successVerification = "Petak bersih dari buah berlesi patek, insidensi antraknosa terkendali nol persen.",
        safetyGear = listOf("Sarung tangan isolasi sekali pakai", "Kantong plastik zip-lock sampel"),
        durationMinutes = 25,
        troubleshootingTip = "Buah yang bergejala bintik antraknosa wajib dimasukkan ke dalam kantong plastik rapat dan dibakar atau dikubur di luar area kebun; jangan dibuang di parit.",
        qualitySigns = "0% buah bergejala patek di seluruh lahan, rantai penyebaran spora konidia terputus sejak fase buah hijau awal."
    )

    private fun capsaicinStressMission(day: Long) = Mission(
        id = "m$day-capsaicin",
        category = Category.AIR,
        title = "Defisit air terukur untuk memicu kepedasan maksimal",
        detail = "Terapkan controlled mild deficit irrigation (kurangi air 20% selama 2-3 hari) pada fase pembesaran buah.",
        qualityImpact = "Memicu sintesis alkaloid kapsaisin di plasenta buah → tingkat kepedasan cabai melonjak tajam.",
        weight = 3,
        qualityTarget = QualityTarget.KAPSAISIN_PEDAS,
        howToExecute = "Turunkan volume penyiraman hingga kelembaban tanah 45-50% kapasitas lapang selama 48 jam, amati daun tidak sampai layu permanen.",
        scientificBasis = "Cekaman air ringan mengaktifkan jalur biosintesis fenilpropanoid dan ekspresi gen capsaicin synthase (CS) pada vakuola plasenta buah.",
        targetMetric = "Kadar kapsaisin buah > 80.000 SHU (Scoville Heat Units)",
        toolsAndMaterials = listOf(
            "Sensor kelembaban tanah kapasitif DAPE",
            "Pencatat jadwal katup irigasi tetes"
        ),
        executionSteps = listOf(
            "Catat kelembaban tanah awal pada kondisi kapasitas lapang (lengas 70%).",
            "Hentikan penyiraman selama 48 jam hingga kelembaban tanah turun ke 45 - 50%.",
            "Pantau tajuk tanaman pukul 12:00 WIB; pastikan hanya layu ringan dan tegak kembali saat sore.",
            "Lanjutkan pengairan normal setelah 48 jam periode defisit air tercapai."
        ),
        optimalTiming = "Saat 30% populasi buah telah mencapai ukuran maksimal dan mulai transisi warna merah",
        commonMistakesToAvoid = listOf(
            "Membiarkan tanaman mengalami kelayuan permanen (daun rontok dan buah terbakar matahari).",
            "Menerapkan cekaman air pada fase pembungaan yang menyebabkan rontok bunga massal."
        ),
        successVerification = "Kadar kapsaisin buah cabai terstimulasi optimal dengan kepedasan > 80.000 SHU.",
        safetyGear = listOf("Sepatu bot kebun"),
        durationMinutes = 20,
        troubleshootingTip = "Bila daun cabai mulai layu permanen hingga pucuk merunduk di sore hari, segera berikan sedikit air di pangkal batang untuk mencegah rontok buah prematur.",
        qualitySigns = "Tingkat kepedasan (Scoville Heat Units / SHU) meningkat drastis > 80.000 SHU pada plasenta buah cabai."
    )

    private fun calciumCrackGuardMission(day: Long) = Mission(
        id = "m$day-crackguard",
        category = Category.NUTRISI,
        title = "Semprot Bio-Kalsium + Silika cegah buah pecah",
        detail = "Aplikasi kalsium chelate dan silika cair menjelang perubahan cuaca untuk elastisitas kulit buah.",
        qualityImpact = "Mencegah buah cabai retak/pecah membujur saat terjadi hujan lebat mendadak.",
        weight = 2,
        qualityTarget = QualityTarget.PERICARP_TEBAL,
        howToExecute = "Semprotkan 2 ml/L kalsium cair formulasi chelate ke tajuk buah pada sore hari saat kelembaban udara meningkat.",
        scientificBasis = "Kalsium ekstraseluler meningkatkan integritas membran plasma dan elastisitas dinding pericarp saat turgor mendadak naik.",
        targetMetric = "Buah retak (cracking) = 0%",
        toolsAndMaterials = listOf(
            "Kalsium kelat organik (Ca-EDTA cair)",
            "Silika kalium cair",
            "Sprayer elektrik bertekanan stabil"
        ),
        executionSteps = listOf(
            "Campurkan 2 ml Kalsium kelat dan 1 ml silika cair ke dalam 1 liter air bersih.",
            "Aduk rata hingga larutan homogen jernih.",
            "Semprotkan merata ke seluruh permukaan kulit buah cabai yang sedang membesar.",
            "Pastikan droplet menempel tipis merata di kutikula buah."
        ),
        optimalTiming = "Sore hari pukul 15:30 - 17:00 WIB, 1-2 hari sebelum diprakirakan hujan lebat",
        commonMistakesToAvoid = listOf(
            "Menyemprot kalsium kelat bersama insektisida pekat berbahan dasar minyak (EC).",
            "Terlambat aplikasi setelah buah sudah mengalami retak fisik membujur."
        ),
        successVerification = "Kulit buah elastis kencang, nol buah retak membujur (cracking = 0%) pascahujan deras.",
        safetyGear = listOf("Masker pelindung semprot", "Sarung tangan nitril"),
        standardDosagePerLiter = 2.0,
        dosageUnit = "gram",
        durationMinutes = 35,
        troubleshootingTip = "Jika diperkirakan hujan badai turun sore hari, lakukan penyemprotan kalsium klorida (CaCl2) pagi hari agar sel kutikula menyerap ion Ca sebelum air hujan membanjiri perakaran.",
        qualitySigns = "Dinding pericarp lentur dan kuat menahan lonjakan tekanan turgor internal buah, bebas retak mikro (micro-cracking) saat panen."
    )

    private fun mulchSoilThermistorMission(day: Long) = Mission(
        id = "m$day-mulch-temp",
        category = Category.INSPEKSI,
        title = "Kalibrasi termal suhu mulsa siang hari via sensor HP",
        detail = "Ukur suhu permukaan tanah di bawah mulsa plastik perak-hitam pukul 12:00 - 13:00; kendalikan agar tetap di bawah 32°C.",
        qualityImpact = "Suhu rizosfer sejuk mencegah respirasi hara berlebih dan mempertahankan turgor buah cabai.",
        weight = 2,
        qualityTarget = QualityTarget.VIGOR_AKAR,
        howToExecute = "Selipkan sensor ponsel di balik lipatan mulsa pada kedalaman 5 cm. Jika suhu > 32°C, alirkan air irigasi dingin ke parit selama 15 menit.",
        scientificBasis = "Suhu tanah > 34°C menghentikan aktivitas sitokinin akar dan memicu penutupan stomata total yang menggagalkan pengisian buah.",
        targetMetric = "Suhu media tanah mulsa < 30°C pada tengah hari",
        toolsAndMaterials = listOf(
            "Smartphone dengan sensor termal baterai / termistor tanah",
            "Termometer probe tanah digital",
            "Pompa sirkulasi air irigasi parit"
        ),
        executionSteps = listOf(
            "Selipkan probe termistor atau ponsel di balik lipatan mulsa pada kedalaman 5 cm.",
            "Baca suhu media perakaran tepat pada tengah hari pukul 12:00 - 13:00 WIB.",
            "Jika suhu terukur > 32°C, segera alirkan air irigasi dingin ke dalam parit selama 15-20 menit.",
            "Ukur kembali suhu pascapengaliran parit; pastikan suhu turun ke rentang aman 26°C - 30°C."
        ),
        optimalTiming = "Pukul 12:00 - 13:00 WIB saat radiasi termal matahari mencapai puncak",
        commonMistakesToAvoid = listOf(
            "Membiarkan air parit tergenang diam hingga air ikut memanas oleh terik matahari.",
            "Mengukur suhu di atas permukaan mulsa bukan pada zona perakaran dalam tanah."
        ),
        successVerification = "Suhu zona perakaran bawah mulsa stabil < 30°C, tanaman bebas aborsi buah.",
        safetyGear = listOf("Topi pelindung terik", "Lanyard HP anti-jatuh"),
        durationMinutes = 15,
        troubleshootingTip = "Bila suhu mulsa perak-hitam tembus > 35°C di siang terik, tutupi sebagian lorong antar bedengan dengan serasah jerami padi tipis untuk mereduksi radiasi balik termal.",
        qualitySigns = "Suhu rizosfer stabil di kisaran 24-28°C, respirasi akar efisien dan penyerapan hara mikro (Fe, Mn, Zn) berjalan lancar."
    )

    private fun magnesiumCuticleGlossMission(day: Long) = Mission(
        id = "m$day-mg-cuticle",
        category = Category.NUTRISI,
        title = "Semprot Magnesium Sulfat + K-Silikat kilap lilin kutikula",
        detail = "Semprotkan larutan MgSO4 1 g/L dan Kalium Silikat 1 ml/L secara kabut halus ke permukaan buah menjelang merah.",
        qualityImpact = "Membentuk lapisan lilin kristal tebal pada kutikula buah cabai sehingga merah mengkilap dan tahan simpan.",
        weight = 3,
        qualityTarget = QualityTarget.WARNA_KILAP,
        howToExecute = "Larutkan 10 gram garam Inggris (MgSO4) + 10 ml K-Silikat dalam 10 liter air. Semprotkan saat matahari bersinar lembut pukul 07:00 WIB.",
        scientificBasis = "Magnesium mengaktifkan sintesis asam lemak ester pembentuk lilin kutikula (cuticular wax) yang memantulkan cahaya dan mencegah susut air.",
        targetMetric = "Tingkat kilap spekular buah > 80%, susut bobot simpan < 0,5%/hari",
        toolsAndMaterials = listOf(
            "Magnesium Sulfat murni (MgSO4)",
            "Kalium Silikat cair soluble",
            "Sprayer kabut halus bertekanan stabil"
        ),
        executionSteps = listOf(
            "Timbang 10 gram MgSO4 dan 10 ml Kalium Silikat untuk 10 liter air bersih.",
            "Aduk rata hingga seluruh kristal magnesium larut transparan.",
            "Semprotkan kabut halus ke buah cabai yang mulai mengalami semburat merah.",
            "Pastikan seluruh permukaan kulit buah terlapisi cairan kabut tipis."
        ),
        optimalTiming = "Pukul 06:30 - 08:00 WIB saat sinar matahari pagi membantu asimilasi karotenoid",
        commonMistakesToAvoid = listOf(
            "Menyemprotkan larutan terlalu pekat yang meninggalkan kerak putih pada buah.",
            "Menyemprot di siang hari yang menyebabkan efek lensa air pembakar kulit buah."
        ),
        successVerification = "Kutikula buah mengkilap kristal (pantulan spekular > 80%), susut bobot simpan < 0,5%/hari.",
        safetyGear = listOf("Masker respirator", "Sarung tangan karet"),
        standardDosagePerLiter = 1.5,
        dosageUnit = "gram",
        durationMinutes = 30,
        troubleshootingTip = "Pastikan garam Epsom (MgSO4) larut 100% tanpa residu; hindari penyemprotan saat terik matahari siang untuk menghindari bintik garam (salt scorch) di kulit buah.",
        qualitySigns = "Kulit buah cabai memiliki pantulan cahaya mengkilap (glossy) dengan lapisan lilin alami tebal yang menekan susut bobot pascapanen."
    )

    private fun bottomLeafPruningMission(day: Long) = Mission(
        id = "m$day-leaf-pruning",
        category = Category.UMUM,
        title = "Perompesan daun tua bawah kanopi (defoliasi aerasi)",
        detail = "Pangkas 3 helai daun tertua yang menguning di bawah percabangan Y untuk melancarkan sirkulasi angin parit.",
        qualityImpact = "Menurunkan kelembaban mikro di dasar tajuk, mencegah kolonisasi spora Antraknosa ke buah.",
        weight = 2,
        qualityTarget = QualityTarget.BEBAS_CACAT,
        howToExecute = "Gunakan gunting pangkas bersih. Potong tangkai daun tua menyisakan 0,5 cm dari batang utama. Kumpulkan daun pangkasan dan buang ke bak kompos.",
        scientificBasis = "Daun tua menguning telah menjadi organ parasitik (sink bukan source) yang mengonsumsi karbohidrat namun tidak menyumbang fotosintat.",
        targetMetric = "Bawah percabangan Y bersih dari daun kuning, aerasi dasar tajuk lancar",
        toolsAndMaterials = listOf(
            "Gunting pangkas tajam steril",
            "Botol semprot alkohol 70%",
            "Keranjang wadah daun pangkasan"
        ),
        executionSteps = listOf(
            "Sterilkan mata gunting dengan alkohol 70%.",
            "Identifikasi 3-4 helai daun tertua yang menguning kusam di bawah cabang Y.",
            "Gunting tangkai daun dengan menyisakan 0,5 cm dari batang utama tanpa merobek kulit ari.",
            "Kumpulkan seluruh daun pangkasan ke keranjang, jangan tinggalkan di parit."
        ),
        optimalTiming = "Pukul 08:30 - 10:30 WIB saat cuaca cerah dan kering agar luka segera menutup kalus",
        commonMistakesToAvoid = listOf(
            "Merontokkan daun dengan menarik ke bawah yang merusak jaringan kulit batang.",
            "Memangkas daun di atas percabangan Y yang masih aktif mengisi buah."
        ),
        successVerification = "Bagian bawah percabangan Y bersih dari daun kuning, sirkulasi udara parit lancar.",
        safetyGear = listOf("Gunting pangkas tajam steril", "Sarung tangan tebal", "Sepatu bot"),
        durationMinutes = 45,
        troubleshootingTip = "Gunakan gunting pangkas tajam yang sudah dibasahi alkohol 70%; potong tangkai daun menyisakan 0,5 cm dari batang utama agar tidak mengelupas jaringan korteks batang.",
        qualitySigns = "Sirkulasi udara di bawah kanopi lancar (kelembaban mikroklimat < 75%), sinar matahari pagi menembus pangkal batang utama."
    )

    // Stage 5: Panen & Pascapanen (76 s.d. 120 HST)

    private fun harvestTimeMorningMission(day: Long) = Mission(
        id = "m$day-harvest-morning",
        category = Category.PANEN,
        title = "Jadwalkan panen pagi pukul 07:00–10:00 WIB",
        detail = "Pemetikan cabai wajib dilakukan pagi hari sebelum sinar matahari terik menguapkan kadar air sel buah.",
        qualityImpact = "Turgor sel buah maksimal membuat cabai segar, berbobot berat, renyah, dan tidak layu menyusut.",
        weight = 3,
        qualityTarget = QualityTarget.GRADE_A_PANEN,
        howToExecute = "Mulai pemetikan pukul 07:00 WIB setelah embun mengering, sudahi paling lambat pukul 10:00 WIB.",
        scientificBasis = "Laju transpirasi buah pascapanen melonjak drastis bila dipetik saat suhu udara > 30°C, menyebabkan susut bobot hingga 12%.",
        targetMetric = "Turgor sel > 95%, susut bobot pascapanen < 2%",
        toolsAndMaterials = listOf(
            "Keranjang panen plastik berpori ventilasi",
            "Lapisan kain bersih penahan benturan",
            "Jam / alarm smartphone pengatur waktu"
        ),
        executionSteps = listOf(
            "Mulai pemetikan tepat pukul 07:00 WIB setelah tetesan embun pada buah mengering.",
            "Petik buah dan letakkan perlahan ke keranjang panen berkapasitas maksimal 10 kg.",
            "Segera angkut keranjang ke tempat penampungan sementara yang teduh dan dingin.",
            "Hentikan pemetikan paling lambat pukul 10:00 WIB saat suhu udara mulai melampaui 30°C."
        ),
        optimalTiming = "Tepat pukul 07:00 - 10:00 WIB",
        commonMistakesToAvoid = listOf(
            "Memanen saat buah masih basah kuyup oleh embun (memicu pembusukan bakteri di keranjang).",
            "Memanen di siang hari terik (buah layu, bobot susut drastis, dan tekstur lembek)."
        ),
        successVerification = "Buah bertekstur kencang renyah dengan turgor sel > 95%, susut bobot simpan < 2%.",
        safetyGear = listOf("Topi pelindung embun pagi", "Sarung tangan katun petik halus"),
        durationMinutes = 60,
        troubleshootingTip = "Hentikan panen setelah pukul 10:00 WIB saat suhu udara mulai panas; buah yang dipetik saat suhu tinggi mengalami laju respirasi 3x lebih cepat dan cepat lembek.",
        qualitySigns = "Kandungan air dan turgor pericarp maksimal (krispi renyah), tangkai buah hijau segar tidak keriput, dan masa simpan mencapai > 14 hari."
    )

    private fun harvestRipenessMission(day: Long) = Mission(
        id = "m$day-ripeness",
        category = Category.PANEN,
        title = "Petik selektif buah merah >85% dengan gunting steril",
        detail = "Panen pagi hari; gunting tangkai, hindari luka. Cek kematangan via kamera kualitas.",
        qualityImpact = "Kematangan tepat waktu memaksimalkan grade & harga jual.",
        weight = 3,
        qualityTarget = QualityTarget.GRADE_A_PANEN,
        howToExecute = "Gunakan gunting pangkas tajam yang telah disterilkan alkohol 70%. Sisakan tangkai buah utuh 2–3 cm.",
        scientificBasis = "Menyisakan tangkai utuh menutup jalan masuk mikroba pembusuk dan mempertahankan kadar asam askorbat buah.",
        targetMetric = "Kematangan merah > 85%, panjang tangkai 2-3 cm utuh",
        toolsAndMaterials = listOf(
            "Gunting panen bergagang pendek steril",
            "Alkohol 70% pensanitasi bilah",
            "Kamera DAPE pemeriksa spektrum kematangan"
        ),
        executionSteps = listOf(
            "Pilih buah cabai yang telah matang merah fisiologis minimal 85%.",
            "Gunting tangkai buah tepat 2-3 cm di atas kelopak buah (calyx); sisakan tangkai utuh.",
            "Masukkan buah ke wadah panen secara hati-hati tanpa dilempar.",
            "Tinggalkan buah semburat hijau untuk putaran panen berikutnya (3-4 hari ke depan)."
        ),
        optimalTiming = "Pukul 07:00 - 10:00 WIB",
        commonMistakesToAvoid = listOf(
            "Memetik dengan mencabut tangkai menggunakan tangan (merobek kulit ari dan memicu luka).",
            "Mencampur buah matang sempurna dengan buah hijau mentah dalam satu wadah."
        ),
        successVerification = "Kematangan merah > 85%, tangkai buah hijau segar utuh 2-3 cm, nol memar fisik.",
        safetyGear = listOf("Sarung tangan petik halus", "Krat berventilasi bersih"),
        durationMinutes = 45,
        troubleshootingTip = "Untuk pasar jarak jauh (> 3 hari pengiriman), petik buah dengan tingkat kematangan semburat merah 80%; untuk pasar lokal atau segar petik merah penuh 95%.",
        qualitySigns = "Buah cabai memenuhi standar Grade A+ Ekspor dengan kematangan merah seragam 85-90%, rasa pedas optimal dan aroma khas cabai pekat."
    )

    private fun sortationMission(day: Long) = Mission(
        id = "m$day-sort",
        category = Category.PANEN,
        title = "Sortir Grade A+/A/B/BS & steril 52°C pasca-panen",
        detail = "Hamparkan di alas kontras, sortir per grade, lakukan sterilisasi air 52°C untuk bunuh spora laten.",
        qualityImpact = "Sortir + sterilisasi mencegah satu buah busuk merusak seluruh keranjang Grade A.",
        weight = 3,
        qualityTarget = QualityTarget.GRADE_A_PANEN,
        howToExecute = "Hamparkan buah di meja sortir, pisahkan Grade A+ (panjang >12 cm, mulus, mengkilap), celupkan ke air hangat 52°C selama 3 menit, lalu tiriskan.",
        scientificBasis = "Termoterapi 52°C (Asas Black) mendenaturasi protein spora antraknosa laten tanpa merusak integritas kutikula buah.",
        targetMetric = "Suhu air 52°C ± 1°C selama 180 detik, Grade A+ > 75%",
        toolsAndMaterials = listOf(
            "Meja sortir berlapis terpal putih kontras",
            "Wadah termoterapi air hangat 52°C Asas Black",
            "Termometer digital & rak penirisan kawat kasa"
        ),
        executionSteps = listOf(
            "Hamparkan buah cabai di atas meja sortir kontras dengan pencahayaan terang merata.",
            "Pisahkan ke 4 kelas: Grade A+ (>12 cm, mulus mengkilap), Grade A, Grade B, dan BS (afkir).",
            "Masukkan cabai Grade A+ ke keranjang kawat lalu celupkan ke bejana air 52°C selama 3 menit.",
            "Angkat dan hamparkan di rak kawat kasa berventilasi untuk penirisan cepat di ruang teduh."
        ),
        optimalTiming = "Maksimal 2 jam setelah buah dipetik dari lahan",
        commonMistakesToAvoid = listOf(
            "Suhu perendaman > 55°C yang mematangkan daging buah dan merusak lapisan lilin.",
            "Menjemur cabai di bawah terik matahari pascaperendaman air hangat."
        ),
        successVerification = "Spora laten antraknosa terdenaturasi 100%, Grade A+ terpisah murni dengan permukaan kering.",
        safetyGear = listOf("Sarung tangan katun bersih steril", "Meja sortir berlapis busa"),
        durationMinutes = 40,
        troubleshootingTip = "Gunakan sarung tangan katun saat memegang buah cabai untuk mencegah goresan kuku tangan yang memicu infeksi sekunder bakteri Erwinia.",
        qualitySigns = "Keseragaman ukuran panjang 12-15 cm, diameter 1,2-1,5 cm, permukaan mulus tanpa cacat fisik, dan nilai jual di pedagang atau eksportir melonjak 30-50%."
    )

    private fun archimedesFloatMission(day: Long) = Mission(
        id = "m$day-archimedes",
        category = Category.INSPEKSI,
        title = "Uji apung Archimedes uji kepadatan & rongga buah",
        detail = "Rendam sampel 20 buah cabai dalam bejana air bersih; uji persentase buah yang tenggelam sempurna.",
        qualityImpact = "Memastikan buah padat terisi plasenta berbiji sempurna tanpa rongga kopong atau ulat dalam.",
        weight = 2,
        qualityTarget = QualityTarget.PERICARP_TEBAL,
        howToExecute = "Masukkan 20 buah sampel acak ke wadah air 5 liter. Hitung buah yang tenggelam ke dasar bejana.",
        scientificBasis = "Massa jenis buah cabai padat berkualitas (ρ > 1,02 g/cm3) melebihi massa jenis air, membuktikan pericarp tebal dan bebas larva hama.",
        targetMetric = "Persentase buah tenggelam > 80%",
        toolsAndMaterials = listOf(
            "Bejana air transparan volume 5-10 liter",
            "Air bersih (ρ = 1,00 g/cm3)",
            "Pengaduk kayu & 20 buah sampel cabai panen"
        ),
        executionSteps = listOf(
            "Ambil 20 buah cabai secara acak dari keranjang sortasi Grade A+.",
            "Isi bejana dengan air bersih hingga ketinggian 30 cm.",
            "Masukkan 20 buah cabai secara bersamaan, aduk perlahan 5 detik untuk membuang gelembung luar.",
            "Hitung jumlah buah yang tenggelam di dasar bejana vs buah yang mengapung di permukaan."
        ),
        optimalTiming = "Saat proses kendali mutu sortasi pascapanen",
        commonMistakesToAvoid = listOf(
            "Menggunakan air berlumpur kotor yang mengubah berat jenis cairan.",
            "Menekan buah mengapung ke dasar bejana dengan jari secara manual."
        ),
        successVerification = "Persentase buah tenggelam > 80% (membuktikan pericarp tebal tanpa rongga kopong/ulat).",
        safetyGear = listOf("Sarung tangan karet panjang", "Wadah perendaman bersih non-reaktif"),
        durationMinutes = 25,
        troubleshootingTip = "Setelah uji sampel apung Archimedes, keringkan buah sampel dengan lap mikrofiber bersih; jangan campur kembali buah basah ke dalam krat panen kering.",
        qualitySigns = "Kepadatan jaringan pericarp teruji 100% (buah tenggelam di larutan garam Archimedes), tidak ada rongga udara kosong atau kopong di dalam buah."
    )

    private fun crateStorageMission(day: Long) = Mission(
        id = "m$day-crate",
        category = Category.UMUM,
        title = "Susun dalam krat berventilasi maks 15 kg di ruang teduh",
        detail = "Hindari karung plastik kedap; gunakan krat berventilasi dengan kapasitas maksimal 15 kg.",
        qualityImpact = "Mencegah panas respirasi terperangkap dan pembusukan anaerobik pada lapisan bawah.",
        weight = 2,
        qualityTarget = QualityTarget.GRADE_A_PANEN,
        howToExecute = "Letakkan alas kertas koran tipis di dasar krat plastik berlubang, isi cabai maksimal setinggi 30 cm (berat maks 15 kg), simpan di suhu 18-22°C.",
        scientificBasis = "Ventilasi mengalirkan gas etilen dan panas respirasi (heat of respiration) sehingga menekan laju senesens buah.",
        targetMetric = "Sirkulasi udara lancar, tumpukan maks 15 kg",
        toolsAndMaterials = listOf(
            "Krat plastik berventilasi (kapasitas 15 kg)",
            "Kertas koran/kraft bersih untuk alas",
            "Timbangan gantung digital presisi"
        ),
        executionSteps = listOf(
            "Letakkan 1 lembar kertas koran bersih di dasar krat plastik berlubang.",
            "Masukkan buah cabai yang telah ditiriskan dingin ke dalam krat hingga ketinggian maks 30 cm.",
            "Timbang berat total krat; pastikan tidak melebihi kapasitas maksimal 15 kg per krat.",
            "Susun krat secara bersilang di ruang penyimpanan teduh bersuhu 18°C - 22°C."
        ),
        optimalTiming = "Sebelum pengangkutan ke pasar atau gudang pendingin",
        commonMistakesToAvoid = listOf(
            "Menggunakan karung plastik kedap yang memicu akumulasi panas respirasi dan pembusukan kilat.",
            "Menumpuk cabai lebih dari 15 kg dalam satu krat yang meremukkan lapisan bawah."
        ),
        successVerification = "Sirkulasi udara antar-krat lancar, suhu tumpukan stabil tanpa uap embun kondensasi.",
        safetyGear = listOf("Sarung tangan pelindung beban", "Sepatu bot kerja"),
        durationMinutes = 20,
        troubleshootingTip = "Jangan pernah menggunakan karung plastik tertutup untuk menyimpan cabai segar; gunakan krat plastik berlubang dengan kapasitas maksimal 15-20 kg per krat.",
        qualitySigns = "Tidak terjadi pembusukan anaerobik, suhu tumpukan stabil tanpa kenaikan panas akibat gas etilen terperangkap di dalam kemasan."
    )

    private fun pedicelFreshnessMission(day: Long) = Mission(
        id = "m$day-pedicel",
        category = Category.PANEN,
        title = "Pemotongan nodus tangkai buah utuh 2,5–3,5 cm",
        detail = "Potong tepat pada buku tangkai (nodus) menggunakan gunting pangkas; sisakan tangkai utuh 2,5 - 3,5 cm berwarna hijau cerah.",
        qualityImpact = "Tangkai utuh segar merupakan syarat mutlak cabai Grade A+ Ekspor dan menjaga kesegaran buah >14 hari.",
        weight = 3,
        qualityTarget = QualityTarget.GRADE_A_PANEN,
        howToExecute = "Arahkan mata gunting tepat di nodus sambungan tangkai ke ranting. Jangan mematahkan tangkai dengan tangan untuk menghindari robekan kulit buah.",
        scientificBasis = "Tangkai buah berfungsi sebagai reservoir air mikroskopis; nodus alami menutup luka petik dan mencegah infeksi busuk pangkal buah.",
        targetMetric = "Panjang tangkai buah 2,5 - 3,5 cm, tangkai hijau segar 100%",
        toolsAndMaterials = listOf(
            "Gunting pangkas bergagang ergonomis steril",
            "Larutan ozonasi / air bersih pensanitasi",
            "Mistar ukur milimeter"
        ),
        executionSteps = listOf(
            "Posisikan mata gunting tepat pada nodus sambungan tangkai buah ke ranting.",
            "Potong dengan satu gerakan bersih tanpa mengoyak serat; pastikan panjang tangkai 2,5 - 3,5 cm.",
            "Bilas tangkai buah dengan semprotan air ozonasi halus untuk membersihkan debu.",
            "Angin-anginkan di rak teduh agar nodus tangkai mengering dan menutup rapat."
        ),
        optimalTiming = "Saat pemetikan panen di pagi hari",
        commonMistakesToAvoid = listOf(
            "Memotong tangkai terlalu pendek (< 1 cm) yang merusak kelopak calyx pelindung.",
            "Membiarkan tangkai kotor berlumpur yang memicu jamur pembusuk tangkai."
        ),
        successVerification = "Tangkai buah utuh 2,5 - 3,5 cm hijau segar, nodus tertutup rapat tanpa luka robek.",
        safetyGear = listOf("Gunting petik buah steril berujung lengkung", "Sarung tangan katun"),
        durationMinutes = 30,
        troubleshootingTip = "Petik dengan mematahkan persendian nodus tangkai ke arah atas secara perlahan; jangan menarik atau memuntir tangkai karena dapat merusak kelopak buah.",
        qualitySigns = "Tangkai buah berwarna hijau segar berukuran seragam 2-3 cm, calyx (kelopak) utuh merekat erat di pangkal buah cabai."
    )

    private fun cielabColorCameraMission(day: Long) = Mission(
        id = "m$day-cielab-camera",
        category = Category.INSPEKSI,
        title = "Audit spektral warna Kapsantin CIELAB via kamera kualitas",
        detail = "Potret sampel 10 buah cabai di atas alas kontras putih dengan kamera DAPE untuk verifikasi nilai kromatisitas merah a* > 42.",
        qualityImpact = "Verifikasi objektif standar warna ekspor premium tanpa keraguan visual mata telanjang.",
        weight = 2,
        qualityTarget = QualityTarget.WARNA_KILAP,
        howToExecute = "Letakkan 10 cabai panen di atas kertas HVS putih. Buka fitur Kamera Kualitas Cabai DAPE, tangkap gambar dengan pencahayaan merata.",
        scientificBasis = "Parameter ruang warna CIELAB a* mengukur saturasi spektrum merah karotenoid; a* > 42,0 mengindikasikan kadar kapsantin > 85%.",
        targetMetric = "Nilai CIELAB a* >= 42,0, indeks keseragaman warna > 90%",
        toolsAndMaterials = listOf(
            "Smartphone dengan kamera DAPE terkalibrasi",
            "Kertas HVS putih standar 80 gsm sebagai alas kontras",
            "10 buah sampel cabai hasil sortasi"
        ),
        executionSteps = listOf(
            "Hamparkan 10 buah cabai sampel di atas kertas putih bersih dengan pencahayaan alami difus.",
            "Buka fitur Kamera Kualitas Cabai (Tab 8) di aplikasi DAPE.",
            "Posisikan kamera sejajar tegak lurus pada jarak 20 cm, lalu tangkap foto spektral.",
            "Evaluasi nilai rata-rata CIELAB pada layar; verifikasi nilai a* >= 42,0 dan L* >= 35,0."
        ),
        optimalTiming = "Pukul 09:00 - 12:00 WIB di ruang sortasi beratap transparan",
        commonMistakesToAvoid = listOf(
            "Mengambil foto di bawah lampu pijar kuning yang membiaskan nilai spektrum warna asli.",
            "Memotret cabai yang masih kotor tertutup debu atau residu semprotan."
        ),
        successVerification = "Nilai CIELAB a* >= 42,0 terkonfirmasi di aplikasi, menandakan kapsantin > 85% standar ekspor.",
        safetyGear = listOf("Kain mikrofiber optik lensa", "Kartu kalibrasi warna abu-abu 18%"),
        durationMinutes = 20,
        troubleshootingTip = "Saat memotret kalibrasi warna CIELAB, posisikan HP tegak lurus sejajar dengan kertas putih atau kartu kalibrasi di tempat teduh agar tidak ada bayangan kamera.",
        qualitySigns = "Nilai kromatisitas CIELAB a* > 35, b* > 18, menghasilkan tampilan warna merah menyala yang sangat diminati pasar ekspor dan industri pasta cabai."
    )

    private fun fieldHeatPreCoolingMission(day: Long) = Mission(
        id = "m$day-precooling",
        category = Category.PANEN,
        title = "Pendinginan cepat penurun panas lapang (Pre-Cooling 15°C)",
        detail = "Angin-anginkan cabai yang baru dipetik di ruang teduh berventilasi kipas sejuk (15°C - 20°C) selama 30 - 45 menit sebelum ditimbang.",
        qualityImpact = "Menghilangkan panas lapang (field heat) menghentikan respirasi cepat dan mencegah pembusukan di dalam kemasan.",
        weight = 2,
        qualityTarget = QualityTarget.GRADE_A_PANEN,
        howToExecute = "Hamparkan cabai panen tipis 1 lapis di atas rak kawat kasa di ruang tertutup teduh. Nyalakan kipas angin sepoi-sepoi selama 45 menit.",
        scientificBasis = "Penurunan suhu buah dari 30°C ke 18°C menurunkan laju respirasi dan produksi etilen pascapanen hingga 65%.",
        targetMetric = "Suhu internal buah turun ke 18°C - 20°C sebelum dimasukkan ke krat penyimpanan",
        toolsAndMaterials = listOf(
            "Ruang peneduh berventilasi sejuk (suhu 15°C - 20°C)",
            "Kipas angin hembusan halus berpori",
            "Rak kawat kasa penirisan & termometer inframerah"
        ),
        executionSteps = listOf(
            "Bawa keranjang panen segera ke ruang pendinginan awal (pre-cooling room).",
            "Hamparkan cabai tipis satu lapis di atas rak kawat kasa berpori udara.",
            "Nyalakan kipas angin sepoi-sepoi pada suhu ruangan 15°C - 20°C selama 30 - 45 menit.",
            "Tembakkan termometer inframerah untuk memastikan suhu buah turun di bawah 20°C."
        ),
        optimalTiming = "Segera setelah tiba dari petak panen (maksimal 30 menit pascapetik)",
        commonMistakesToAvoid = listOf(
            "Menumpuk cabai tebal saat pendinginan yang memerangkap panas di bagian tengah.",
            "Langsung memasukkan cabai panas dari lapangan ke dalam mobil boks tertutup rapat."
        ),
        successVerification = "Suhu internal buah turun ke 18°C - 20°C, laju respirasi etilen melambat drastis.",
        safetyGear = listOf("Sarung tangan pelindung", "Termometer sensor infrared / tusuk"),
        durationMinutes = 30,
        troubleshootingTip = "Gunakan kipas angin angin-anginkan di ruangan teduh berventilasi; jangan langsung memasukkan cabai hangat ke dalam cold storage bersuhu < 10°C karena memicu chilling injury.",
        qualitySigns = "Suhu internal buah turun dari 32°C ke 18-20°C dalam waktu < 2 jam, memblokir degradasi vitamin C dan kapsantin pascapanen."
    )

    // EWS defensive addition

    private fun sporeDefenseMission(day: Long, risk: Double) = Mission(
        id = "m$day-spore",
        category = Category.SANITASI,
        title = "Semprot fungisida protektif (risiko jamur tinggi)",
        detail = "EWS mencatat risiko sporulasi ${(risk * 100).toInt()}%. Semprot protektif sore hari & turunkan kelembaban tajuk.",
        qualityImpact = "Menahan ledakan Antraknosa yang bisa menggagalkan panen berkualitas.",
        weight = 3,
        qualityTarget = QualityTarget.BEBAS_CACAT,
        howToExecute = "Semprotkan fungisida kontak berbahan aktif mankozeb atau klorotalonil dosis 2 g/L secara merata pada petak yang terindikasi risiko spora tinggi.",
        scientificBasis = "Kelembaban relatif > 85% dan suhu 25-28°C memicu pelepasan konidia jamur, lapisan fungisida protektif menghambat perkecambahan tabung spora.",
        targetMetric = "Cakupan semprotan merata, sporulasi ditekan 0%",
        toolsAndMaterials = listOf(
            "Fungisida kontak protektif (Mankozeb 80% / Klorotalonil 75%)",
            "Perekat-perata non-ionik khusus pertanian",
            "Tangki sprayer bertekanan kabut merata"
        ),
        executionSteps = listOf(
            "Timbang fungisida kontak dosis 2 gram/L dan tambahkan perekat-perata 0,5 ml/L.",
            "Aduk dalam ember hingga suspensi larut merata tanpa gumpalan butir.",
            "Semprotkan kabut merata ke seluruh tajuk, terutama permukaan bawah daun dan sela buah.",
            "Fokuskan semprotan pada petak yang memiliki kelembaban mikro tinggi."
        ),
        optimalTiming = "Sore hari pukul 15:30 - 17:30 WIB saat risiko sporulasi EWS >= 50%",
        commonMistakesToAvoid = listOf(
            "Menggunakan fungisida sistemik terus-menerus yang memicu resistensi jamur.",
            "Menyemprot tanpa perekat saat musim hujan (lapisan fungisida luntur tercuci air)."
        ),
        successVerification = "Lapisan fungisida protektif melapisi seluruh organ buah dan daun, sporulasi ditekan 0%.",
        safetyGear = listOf("Masker respirator gas & partikel", "Kacamata pelindung kedap (goggles)", "Sarung tangan nitril tebal kimia", "Baju kerja lengan panjang", "Sepatu bot kebun"),
        standardDosagePerLiter = 2.0,
        dosageUnit = "gram",
        durationMinutes = 45,
        troubleshootingTip = "Pastikan penyemprotan mengenai kedua sisi daun dan celah ketiak cabang; tambahkan bahan perata dan perekat non-ionik berstandar pertanian pangan.",
        qualitySigns = "Spora jamur patogen Colletotrichum dan Phytophthora inaktif 100% pada mikroklimat kebun saat cuaca mendung atau lembab ekstrem."
    )
}
