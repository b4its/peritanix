package id.peritanix.dape.android.core.guide

/**
 * Panduan Budidaya Cabai Presisi DAPE — Dari Persiapan Tanam, Pemeliharaan,
 * hingga Panen Kualitas Terbaik dengan Pemantauan Real-Time & Verifikasi Kamera.
 *
 * Menggabungkan kaidah fisika tanah (TWI), keseimbangan nutrisi NPK/Ca/B/Si,
 * iklim mikro (GDD, suhu, kelembaban), dan protokol inspeksi kamera per fase.
 */
object ChiliGrowingGuide {

    /**
     * Protokol verifikasi kamera untuk memastikan setiap tahap perawatan
     * menghasilkan pertumbuhan tanaman yang optimal dan bebas patogen.
     */
    data class CameraProtocol(
        val targetSubject: String,
        val photoInstructions: String,
        val successCriteria: String,
        val inspectionKeyPoints: List<String>,
        val failAction: String
    )

    data class Stage(
        val index: Int,
        val title: String,
        val phaseName: String,
        val hstRange: String,
        val minHst: Int,
        val maxHst: Int,
        val summary: String,
        val bestYieldGoal: String,
        val plantingGuide: List<String>,
        val maintenanceGuide: List<String>,
        val harvestGuide: List<String>,
        val cameraProtocol: CameraProtocol,
        val risk: String,
        val checklist: List<String>,
        val optimalTempRange: String,
        val optimalRhRange: String,
        val targetGdd: Double
    ) {
        val activities: List<String> get() = maintenanceGuide
    }

    data class RealtimeAdvisory(
        val hst: Int,
        val currentStage: Stage,
        val daysToHarvest: Int,
        val progressPct: Float,
        val gddEstimated: Double,
        val weatherAlert: String,
        val recommendedActionToday: String,
        val cameraInspectionTip: String
    )

    /** 5 Tahap Budidaya Cabai Terpadu dari Persemaian hingga Pasca-Panen (0–120 HST). */
    val stages: List<Stage> = listOf(
        Stage(
            index = 1,
            title = "Persemaian Bibit",
            phaseName = "FASE PERSEMAIAN & SELEKSI BENIH",
            hstRange = "-21 – 0 HST (Pra-Tanam)",
            minHst = -21,
            maxHst = 0,
            summary = "Mempersiapkan benih unggul tahan virus menjadi bibit berdaya tumbuh tinggi sebelum dipindahkan ke lahan.",
            bestYieldGoal = "Target: Daya berkecambah > 90%, batang kokoh tebal (tidak etiolasi), bebas rebah semai (damping-off).",
            plantingGuide = listOf(
                "Seleksi benih: Rendam benih dalam air hangat bersuhu 45-50°C selama 30 menit. Buang benih terapung.",
                "Inokulasi mikoriza / PGPR (Plant Growth-Promoting Rhizobacteria) untuk memicu perakaran awal.",
                "Gunakan tray semai dengan media arang sekam + kompos fermentasi matang (rasio 1:1, pH 6,2-6,8).",
                "Kedalaman sebar benih 0,5 cm, tutup tipis tanah halus, dan pasang naungan paranet 50%."
            ),
            maintenanceGuide = listOf(
                "Penyiraman pagi hari dengan sprayer halus kabut, hindari media terlalu basah menggenang.",
                "Berikan pupuk daun mikro dosis 0,5 g/L saat bibit memasuki hari ke-12 setelah semai.",
                "Pencegahan thrips dan kutu kebul: Pasang insect net kasa 50 mesh di sekeliling bedeng pembibitan.",
                "Hardening-off (aklimatisasi): Buka paranet penuh 4 hari sebelum pindah tanam agar bibit tahan terik matahari."
            ),
            harvestGuide = listOf(
                "Bibit siap dipindahkan ke lahan saat memiliki 4-6 helai daun sejati (umur 18-21 hari setelah semai).",
                "Pastikan tinggi bibit mencapai 12-15 cm dengan batang bawah berwarna hijau kecokelatan kokoh."
            ),
            cameraProtocol = CameraProtocol(
                targetSubject = "Foto Daun Sejati & Batang Bibit",
                photoInstructions = "Arahkan kamera 20 cm tegak lurus ke atas tray bibit di bawah pencahayaan terang alami.",
                successCriteria = "Klorofil hijau segar merata > 60%, daun mekar simetris, tidak ada bercak rebah kecambah.",
                inspectionKeyPoints = listOf(
                    "Verifikasi jumlah daun sejati (minimal 4 helai)",
                    "Batang berdiri tegak tanpa pembengkokan etiolasi",
                    "Tidak ada bercak basah busuk pada pangkal batang"
                ),
                failAction = "Jika daun menguning pucat, kurangi air dan semprotkan pupuk N-mikro klorofil ringan."
            ),
            risk = "Rebah kecambah (damping-off) akibat Pythium / Rhizoctonia dan infeksi dini virus Gemini oleh vektor kutu kebul.",
            checklist = listOf(
                "Benih direndam air hangat & disortir apung",
                "Media tray arang sekam + kompos steril siap",
                "Insect net pelindung kutu kebul terpasang",
                "Aklimatisasi sinar matahari penuh 4 hari"
            ),
            optimalTempRange = "24 – 28°C",
            optimalRhRange = "65 – 80%",
            targetGdd = 150.0
        ),
        Stage(
            index = 2,
            title = "Pindah Tanam & Vegetatif",
            phaseName = "FASE VEGETATIF & PEMBENTUKAN AKAR",
            hstRange = "0 – 35 HST",
            minHst = 0,
            maxHst = 35,
            summary = "Pindah tanam ke bedengan mulsa, pembentukan perakaran dalam, penguatan tajuk, dan sanitasi lorong tanam.",
            bestYieldGoal = "Target: Pertumbuhan serempak 100%, cabang tajuk simetris, TWI aman tanpa genangan akar.",
            plantingGuide = listOf(
                "Buat bedengan tinggi 40-50 cm dengan lebar 100-110 cm dan parit pembuangan sedalam 50 cm.",
                "Pastikan indeks kebasahan topografi (TWI) < 7,5 untuk mencegah busuk akar Ralstonia.",
                "Pasang mulsa perak-hitam untuk memantulkan sinar matahari (mengusir thrips) dan menjaga kelembaban.",
                "Tanam bibit dengan jarak 50 cm antar-lubang (searah mulsa) dan 60 cm antar-baris di sore hari."
            ),
            maintenanceGuide = listOf(
                "Kocor pupuk awal: NPK 16-16-16 dosis 3-4 gram/tanaman dilarutkan per 200 ml air setiap 7 hari.",
                "Pemasangan ajir bambu (tiang 1,5 meter) pada jarak 10 cm dari pangkal batang saat 7-10 HST.",
                "Wiwilan tunas air: Buang semua tunas samping yang tumbuh di ketiak daun di bawah cabang utama (cabang Y).",
                "Pengamatan hama: Semprotkan insektisida nabati / abamektin jika ditemukan daun melengkung ke atas (thrips)."
            ),
            harvestGuide = listOf(
                "Fondasi vegetatif yang kokoh akan menentukan kapasitas tampung buah hingga 1,5 - 2,5 kg per pohon.",
                "Catat pertumbuhan tinggi tanaman dan diameter batang setiap minggu."
            ),
            cameraProtocol = CameraProtocol(
                targetSubject = "Foto Bedengan Mulsa & Wiwilan Cabang Y",
                photoInstructions = "Potret tanaman dari sudut 45 derajat mencakup pangkal batang mulsa hingga percabangan Y.",
                successCriteria = "Tunas air bersih terwiwil di bawah cabang Y, mulsa rapat rapi, tajuk hijau pekat tanpa keriting.",
                inspectionKeyPoints = listOf(
                    "Pangkal batang bersih dari tunas air liar",
                    "Tidak ada gejala daun keriting cekung/cembung",
                    "Mulsa terbentang rapi tanpa robekan besar"
                ),
                failAction = "Jika tunas air masih rimbun di bawah Y, segera lakukan pemangkasan manual untuk menurunkan LAI."
            ),
            risk = "Stres pindah tanam, serangan thrips/tungau penyebab daun keriting, dan infeksi layu bakteri pada bedengan rendah.",
            checklist = listOf(
                "Tinggi bedengan terverifikasi 40-50 cm",
                "pH tanah terukur 6,0 – 6,8",
                "Ajir penopang terpasang kokoh",
                "Wiwilan tunas air bawah cabang Y tuntas",
                "Kocor NPK rutin tiap minggu"
            ),
            optimalTempRange = "25 – 30°C",
            optimalRhRange = "60 – 75%",
            targetGdd = 450.0
        ),
        Stage(
            index = 3,
            title = "Pembungaan & Transisi Generatif",
            phaseName = "FASE PEMBUNGAAN (ANTESIS KRITIS)",
            hstRange = "36 – 60 HST",
            minHst = 36,
            maxHst = 60,
            summary = "Fase transisi penentu: bunga mekar serentak, inisiasi buah cabai, dan penguncian fortifikasi dinding sel.",
            bestYieldGoal = "Target: Rontok bunga < 5%, dinding sel buah kokoh, persentase jadi buah (fruit-set) > 85%.",
            plantingGuide = listOf(
                "Periksa kebersihan parit antar-bedengan untuk memastikan drainase air hujan mengalir instan.",
                "Pastikan arah bedengan menerima paparan matahari pagi penuh (orientasi timur-barat optimal)."
            ),
            maintenanceGuide = listOf(
                "Fortifikasi Pericarp: Berikan Kalsium Nitrat (2 g/L) + Boron (1 g/L) + Asam Silikat via semprot daun.",
                "Hentikan total pupuk Nitrogen tinggi / Urea: Kelebihan N memicu rontok bunga massal dan dinding buah tipis.",
                "Tingkatkan fosfat dan kalium kocor (MKP: Mono Kalium Fosfat) untuk memperkuat tangkai bunga.",
                "Jaga kelembaban tanah konstan di angka 65-70%; fluktuasi air ekstrem menyebabkan stres bunga gugur."
            ),
            harvestGuide = listOf(
                "Kualitas pembungaan di fase ini berbanding lurus dengan kelenturan dan ketahanan cabai pasca-panen.",
                "Pastikan penyerbukan lebah atau angin berjalan optimal tanpa gangguan residu insektisida keras."
            ),
            cameraProtocol = CameraProtocol(
                targetSubject = "Foto Bunga Mekar & Kelopak Bawah",
                photoInstructions = "Fokuskan kamera makro pada jarak 10-15 cm ke arah gugus bunga cabai yang mekar.",
                successCriteria = "Kelopak bunga putih bersih, putik kuning cerah, tidak ada bercak cokelat kebasahan.",
                inspectionKeyPoints = listOf(
                    "Tangkai bunga hijau segar melekat kuat pada cabang",
                    "Tidak ada lesi antraknosa bunga (blossom blight)",
                    "Kerapatan bunga aktif seragam di tiap cabang Y"
                ),
                failAction = "Jika kelopak gugur sebelum mekar, semprotkan kalsium nitrat + boron dan kurangi kadar air siram."
            ),
            risk = "Gugur bunga akibat suhu ekstrem > 32°C, kekurangan kalsium-boron, dan serangan thrips pada kelopak bunga.",
            checklist = listOf(
                "Fortifikasi Kalsium Nitrat + Boron disemprotkan",
                "Pupuk Urea dilarang keras & dihentikan",
                "Pengikatan tajuk ke ajir diperkuat",
                "Tingkat rontok bunga terverifikasi < 5%"
            ),
            optimalTempRange = "22 – 28°C",
            optimalRhRange = "65 – 75%",
            targetGdd = 800.0
        ),
        Stage(
            index = 4,
            title = "Pembuahan & Pematangan",
            phaseName = "FASE PEMBESARAN & PENGISIAN BUAH",
            hstRange = "61 – 90+ HST",
            minHst = 61,
            maxHst = 90,
            summary = "Pengisian bobot buah cabai, penebalan kulit (pericarp), serta pencegahan busuk pantat dan patek.",
            bestYieldGoal = "Target: Panjang buah seragam 12-15 cm, pericarp tebal > 1,8 mm, bebas Antraknosa Colletotrichum.",
            plantingGuide = listOf(
                "Pasang perangkap likat kuning dan perangkap metil eugenol (lalat buah Bactrocera) di batas luar kebun."
            ),
            maintenanceGuide = listOf(
                "Berikan Kalium Nitrat (KNO3 Putih) dosis 5-7 gram/tanaman untuk memadatkan daging buah dan mengilapkan warna.",
                "Aplikasi kalsium larut air secara berkala untuk mencegah Busuk Ujung Buah (Blossom End Rot / BER).",
                "Sanitasi intensif: Petik segera dan musnahkan buah yang menunjukkan gejala patek (bercak cincin konsentris).",
                "Gunakan anemometer akustik mikrofon sebelum semprot fungisida untuk memastikan angin < 12 km/jam."
            ),
            harvestGuide = listOf(
                "Amati perubahan semburat warna dari hijau pekat ke semburat jingga kehitaman menuju merah menyala.",
                "Proyeksikan tonase panen menggunakan regresi visual kamera DAPE untuk koordinasi dengan pembeli/offtaker."
            ),
            cameraProtocol = CameraProtocol(
                targetSubject = "Foto Buah Cabai & Kulit Pericarp",
                photoInstructions = "Arahkan kamera 25 cm ke arah gantungan buah cabai di dahan bawah dan tengah.",
                successCriteria = "Kulit cabai mulus mengkilap, lurus tanpa bercak cekung, ujung buah utuh tanpa nekrosis.",
                inspectionKeyPoints = listOf(
                    "Permukaan buah bebas cincin spora patek",
                    "Ujung buah tidak gosong busuk (bebas BER)",
                    "Kelenturan buah padat berisi saat ditekan lembut"
                ),
                failAction = "Jika terdeteksi bercak busuk cincin, segera petik buah sakit dan semprot fungisida tembaga Cu(OH)2."
            ),
            risk = "Wabah patek (Antraknosa), serangan lalat buah, busuk ujung buah (Blossom End Rot), dan dahan patah keberatan buah.",
            checklist = listOf(
                "KNO3 putih diaplikasikan untuk bobot & kilap",
                "Perangkap lalat buah aktif terpasang",
                "Sanitasi buah rontok / afkir setiap pagi",
                "Pencegahan antraknosa terjadwal"
            ),
            optimalTempRange = "24 – 29°C",
            optimalRhRange = "60 – 70%",
            targetGdd = 1200.0
        ),
        Stage(
            index = 5,
            title = "Panen & Pasca-Panen",
            phaseName = "FASE PANEN & PASCA-PANEN MUTU TINGGI",
            hstRange = "90 – 120 HST",
            minHst = 90,
            maxHst = 120,
            summary = "Pemetikan pada puncak kematangan, penyortiran objektif Grade A+, dan sterilisasi Asas Black 52°C.",
            bestYieldGoal = "Target: 85-95% merah merata, Grade A+ > 75%, daya simpan > 14 hari tanpa jamur susulan.",
            plantingGuide = listOf(
                "Persiapkan keranjang panen berventilasi (krat plastik bersih), jangan gunakan karung goni tertutup rapat."
            ),
            maintenanceGuide = listOf(
                "Lanjutkan penyiraman moderat dan kocor ringan NPK+KNO3 untuk mempertahankan putaran panen ke-2 dan ke-3.",
                "Buang daun tua yang menguning di bagian bawah untuk menjaga sirkulasi udara lorong bedengan."
            ),
            harvestGuide = listOf(
                "Waktu panen: Petik pagi hari (pukul 07.00 - 10.00) setelah embun kering sepenuhnya pada kulit buah.",
                "Teknik petik: Gunakan gunting panen steril atau petik bersama tangkainya (jangan ditarik paksa).",
                "Sortir Meja DAPE: Pisahkan Grade A+ (panjang > 12 cm, merah > 85%), Grade A, Grade B, dan BS.",
                "Sterilisasi Asas Black 52°C: Rendam buah selama 180 detik dalam campuran air panas terkalibrasi untuk mematikan spora patek.",
                "Uji Apung Archimedes: Pastikan rasio tenggelam > 80% sebagai bukti kepadatan daging buah premium."
            ),
            cameraProtocol = CameraProtocol(
                targetSubject = "Foto Hamparan Hasil Panen & Tangkai",
                photoInstructions = "Letakkan sampel panen di atas meja grading datar, potret tegak lurus horizontal 0 derajat.",
                successCriteria = "Cakupan warna merah > 85%, tangkai buah hijau segar utuh, bobot padat Grade A+ dominan.",
                inspectionKeyPoints = listOf(
                    "Persentase kematangan merah merata > 85%",
                    "Tangkai buah berwarna hijau segar tidak layu",
                    "Bebas cacat fisik gigitan hama atau goresan"
                ),
                failAction = "Pisahkan buah yang memiliki semburat hijau < 70% merah untuk disimpan terpisah hingga matang penuh."
            ),
            risk = "Kerusakan mekanis saat petik, infeksi jamur pasca-panen saat pengangkutan, dan susut bobot akibat panas berlebih.",
            checklist = listOf(
                "Kematangan > 85% merah kilap terverifikasi kamera",
                "Panen dilakukan pagi hari dengan tangkai utuh",
                "Sortasi meja grading Grade A+/A/B/BS tuntas",
                "Termoterapi Asas Black 52°C terverifikasi gyro",
                "Sertifikat mutu kripto QR Ed25519 dicetak"
            ),
            optimalTempRange = "20 – 26°C",
            optimalRhRange = "55 – 65%",
            targetGdd = 1500.0
        )
    )

    /** Mencari stage yang cocok berdasarkan nilai Hari Setelah Tanam (HST). */
    fun stageForHst(hst: Int): Stage = when {
        hst < 0 -> stages[0]
        hst <= 35 -> stages[1]
        hst <= 60 -> stages[2]
        hst <= 90 -> stages[3]
        else -> stages[4]
    }

    /** Menghitung estimasi hari menuju panen (berbasis 90 HST). */
    fun daysUntilHarvest(hst: Int): Int = (90 - hst).coerceAtLeast(0)

    /** Menghitung progress persentase siklus budidaya 0-120 HST. */
    fun progressPercentage(hst: Int): Float {
        val clamped = (hst + 21).coerceIn(0, 141)
        return (clamped / 141f).coerceIn(0f, 1f)
    }

    /** Estimasi akumulasi GDD teoritis berdasarkan HST. */
    fun estimatedGddForHst(hst: Int): Double {
        if (hst <= -21) return 0.0
        val effectiveHst = (hst + 21).coerceAtLeast(0)
        // Rata-rata GDD per hari di iklim tropis cabai (Tmean ≈ 25°C, Base 15°C -> 10 GDD/hari)
        return effectiveHst * 10.8
    }

    /** Rekomendasi tindakan real-time terpadu berdasarkan HST dan status iklim. */
    fun generateRealtimeAdvisory(
        hst: Int,
        isRaining: Boolean,
        tempC: Double = 26.0,
        rhPct: Double = 70.0
    ): RealtimeAdvisory {
        val stage = stageForHst(hst)
        val daysToHarvest = daysUntilHarvest(hst)
        val progress = progressPercentage(hst)
        val gdd = estimatedGddForHst(hst)

        val weatherAlert = when {
            isRaining -> "Hujan Lapangan Terdeteksi: Tunda pemupukan daun dan semprotan insektisida. Pastikan parit bedengan bebas genangan!"
            rhPct > 85.0 -> "Kelembaban Tinggi (${rhPct.toInt()}%): Waspada spora Antraknosa aktif. Periksa kerapatan wiwilan tajuk tanaman."
            tempC > 32.0 -> "Suhu Sangat Terik (${tempC.toInt()}°C): Evapotranspirasi tinggi. Jadwalkan fertigasi air kocor pada sore hari."
            else -> "Kondisi Mikroklimat Optimal (${tempC.toInt()}°C, ${rhPct.toInt()}% RH): Ideal untuk fotosintesis dan pemeliharaan rutin."
        }

        val recommendedAction = when (stage.index) {
            1 -> "Periksa kelembaban media semai di pagi hari, pastikan insect net tertutup rapat dari kutu kebul."
            2 -> "Pantau tunas air liar di ketiak daun bawah cabang Y. Wiwil tunas yang tumbuh agar nutrisi fokus ke batang utama."
            3 -> "Kunci fase pembungaan: Semprot Kalsium Nitrat + Boron, dan pastikan tidak ada pupuk Urea berlebih."
            4 -> "Tingkatkan Kalium kocor (KNO3) untuk pengisian daging buah dan pasang perangkap lalat buah di batas petak."
            5 -> "Lakukan petik selektif buah merah > 85% di pagi hari menggunakan gunting steril, lanjutkan ke meja sortasi."
            else -> "Lakukan inspeksi visual tanaman secara menyeluruh."
        }

        val cameraTip = "Gunakan kamera HP untuk memotret ${stage.cameraProtocol.targetSubject.lowercase()} dan verifikasi keberhasilan perawatan."

        return RealtimeAdvisory(
            hst = hst,
            currentStage = stage,
            daysToHarvest = daysToHarvest,
            progressPct = progress,
            gddEstimated = gdd,
            weatherAlert = weatherAlert,
            recommendedActionToday = recommendedAction,
            cameraInspectionTip = cameraTip
        )
    }
}
