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
        val howToExecute: String = "",      // Panduan SOP eksekusi di kebun
        val scientificBasis: String = "",   // Landasan ilmiah agro-fisika
        val targetMetric: String = ""       // Metrik target spesifik (angka/kondisi)
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

    // --- Individual mission templates ---------------------------------------

    private fun hydrationMission(stageIdx: Int, day: Long) = Mission(
        id = "m$day-air",
        category = Category.AIR,
        title = "Cek kelembaban tanah & siram presisi",
        detail = "Ukur kelembaban media/tanah; sirami hingga kapasitas lapang (jangan sampai tergenang). " +
            "Lakukan pagi hari agar tajuk cepat kering.",
        qualityImpact = "Air yang seimbang mencegah busuk akar & bunga rontok, dasar buah mulus bebas pecah.",
        weight = 2,
        qualityTarget = QualityTarget.VIGOR_AKAR,
        howToExecute = "Gunakan sensor kapasitif atau periksa kepalan tanah di kedalaman 10 cm. Sirami perlahan pada zona perakaran antara pukul 06:00-08:00 WIB.",
        scientificBasis = "Fluktuasi kadar air ekstrem mengganggu translokasi ion Kalsium ke pucuk dan memicu keretakan buah (fruit cracking).",
        targetMetric = "Lengas tanah 60-70% kapasitas lapang (tegangan matriks -30 s.d. -50 kPa)"
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
        targetMetric = "Defek klorosis < 5%, nekrosis 0%"
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
        targetMetric = "Tinggi bibit 15-20 cm, jumlah daun 4-5 helai hijau tua"
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
        targetMetric = "Kerapatan kasa 50-mesh 100% utuh tanpa celah"
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
        targetMetric = "100% perakaran bibit terinokulasi tanpa gejala rebah kecambah (damping-off)"
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
        targetMetric = "Batang kokoh berwarna hijau kecokelatan, turgor daun stabil"
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
        targetMetric = "Semua tunas di bawah cabang Y bersih 100%"
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
        targetMetric = "EC larutan kocor 1,5 - 1,8 mS/cm, pH tanah 6,0 - 6,8"
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
        targetMetric = "Kekerasan jaringan epidermis daun meningkat, indeks serangan hama 0%"
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
        targetMetric = "Populasi kutu kebul < 2 ekor/perangkap/hari"
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
        targetMetric = "Tebal dinding sel ovarium buah > 1.8 mm, kejadian BER = 0%"
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
        targetMetric = "Retensi bunga mekar menjadi bakal buah (fruit set) > 80%"
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
        targetMetric = "Serangan lalat buah pada ovarium pentil = 0%"
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
        targetMetric = "Tangkai bunga berdiameter > 1,5 mm, tidak mudah layu"
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
        targetMetric = "Bobot rata-rata buah > 15 g/buah, warna merah merata > 85%"
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
        targetMetric = "Insidensi antraknosa pada petak budidaya = 0%"
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
        targetMetric = "Kadar kapsaisin buah > 80.000 SHU (Scoville Heat Units)"
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
        targetMetric = "Buah retak (cracking) = 0%"
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
        targetMetric = "Turgor sel > 95%, susut bobot pascapanen < 2%"
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
        targetMetric = "Kematangan merah > 85%, panjang tangkai 2-3 cm utuh"
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
        targetMetric = "Suhu air 52°C ± 1°C selama 180 detik, Grade A+ > 75%"
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
        targetMetric = "Persentase buah tenggelam > 80%"
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
        targetMetric = "Sirkulasi udara lancar, tumpukan maks 15 kg"
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
        targetMetric = "Cakupan semprotan merata, sporulasi ditekan 0%"
    )

    // Stage 1 Extra Quality Missions

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
        targetMetric = "Daya kecambah benih > 90%, benih bebas patogen 100%"
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
        targetMetric = "Rasio tinggi bibit terhadap diameter batang < 10 (bibit pendek kokoh)"
    )

    // Stage 2 Extra Quality Missions

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
        targetMetric = "Sudut kemiringan bedengan β = 0,5° - 1,0°, bebas genangan pascahujan"
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
        targetMetric = "Transmisi cahaya tajuk > 40%, Leaf Area Index (LAI) 2,5 - 3,2"
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
        targetMetric = "KTK tanah zona rizosfer > 25 meq/100g, serapan Kalsium meningkat > 30%"
    )

    // Stage 3 Extra Quality Missions

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
        targetMetric = "Laju perubahan tekanan |ΔP/Δt| < 1,0 hPa/jam (kondisi atmosfer stabil)"
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
        targetMetric = "Fruit set bunga mekar > 85%, buah lurus simetris > 90%"
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
        targetMetric = "Pentil buah bersih dari mahkota kering 100%, busuk pentil 0%"
    )

    // Stage 4 Extra Quality Missions

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
        targetMetric = "Suhu media tanah mulsa < 30°C pada tengah hari"
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
        targetMetric = "Tingkat kilap spekular buah > 80%, susut bobot simpan < 0,5%/hari"
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
        targetMetric = "Bawah percabangan Y bersih dari daun kuning, aerasi dasar tajuk lancar"
    )

    // Stage 5 Extra Quality Missions

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
        targetMetric = "Panjang tangkai buah 2,5 - 3,5 cm, tangkai hijau segar 100%"
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
        targetMetric = "Nilai CIELAB a* >= 42,0, indeks keseragaman warna > 90%"
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
        targetMetric = "Suhu internal buah turun ke 18°C - 20°C sebelum dimasukkan ke krat penyimpanan"
    )
}
