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
                }
                2 -> { // Vegetatif (1 s.d. 35 HST)
                    add(wiwilMission(epochDay))
                    add(nitrogenBalanceMission(epochDay))
                    add(silicaEpidermisMission(epochDay))
                    add(yellowTrapMission(epochDay))
                }
                3 -> { // Pembungaan (36 s.d. 55 HST) — Kritis kualitas dinding buah & bunga
                    add(calciumBoronMission(epochDay))
                    add(flowerRetentionMission(epochDay))
                    add(fruitFlyTrapMission(epochDay))
                    add(foliarPBMission(epochDay))
                }
                4 -> { // Pembuahan & Pematangan (56 s.d. 75 HST) — Kunci warna & kepedasan
                    add(potassiumMission(epochDay))
                    add(antracnoseScoutMission(epochDay))
                    add(capsaicinStressMission(epochDay))
                    add(calciumCrackGuardMission(epochDay))
                }
                else -> { // Panen & Pascapanen (76 s.d. 120 HST) — Standar Grade A+ Ekspor
                    add(harvestTimeMorningMission(epochDay))
                    add(harvestRipenessMission(epochDay))
                    add(sortationMission(epochDay))
                    add(archimedesFloatMission(epochDay))
                    add(crateStorageMission(epochDay))
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
}
