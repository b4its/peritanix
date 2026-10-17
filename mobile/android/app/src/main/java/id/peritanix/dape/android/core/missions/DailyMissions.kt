package id.peritanix.dape.android.core.missions

import id.peritanix.dape.android.core.guide.ChiliGrowingGuide
import java.time.LocalDate

/**
 * Misi harian untuk kualitas cabai terbaik.
 *
 * Menghasilkan satu set misi harian yang **deterministik** (berbasis HST dan
 * fase tumbuh) dan diarahkan pada tujuan kualitas panen terbaik: vigor tanaman,
 * keseimbangan nutrisi (Ca/B/Si untuk pericarp tebal), sanitasi patogen
 * (Antraknosa), manajemen air, dan panen tepat waktu (kematangan >85% merah).
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

    /** Satu misi harian. `id` stabil per (tanggal, template) agar idempoten. */
    data class Mission(
        val id: String,
        val category: Category,
        val title: String,
        val detail: String,
        val qualityImpact: String,   // mengapa ini meningkatkan kualitas
        val targetAmount: Int = 1,   // mis. 5 pohon sampel, 2 bedengan, dst.
        val weight: Int = 1          // bobot kontribusi ke skor kualitas harian
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

            // Misi spesifik fase.
            when (stage.index) {
                1 -> { // Persemaian
                    add(seedlingVigorMission(epochDay))
                    add(pestNetMission(epochDay))
                }
                2 -> { // Vegetatif
                    add(wiwilMission(epochDay))
                    add(nitrogenBalanceMission(epochDay))
                }
                3 -> { // Pembungaan (kritis kualitas)
                    add(calciumBoronMission(epochDay))
                    add(flowerRetentionMission(epochDay))
                }
                4 -> { // Pembuahan
                    add(potassiumMission(epochDay))
                    add(antracnoseScoutMission(epochDay))
                }
                else -> { // Panen
                    add(harvestRipenessMission(epochDay))
                    add(sortationMission(epochDay))
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

    /** Misi berikutnya yang belum selesai (untuk kartu "fokus hari ini"). */
    fun nextUnfinished(plan: DailyPlan, completedIds: Set<String>): Mission? =
        plan.missions.firstOrNull { it.id !in completedIds }

    // --- Individual mission templates ---------------------------------------

    private fun hydrationMission(stageIdx: Int, day: Long) = Mission(
        id = "m$day-air",
        category = Category.AIR,
        title = "Cek kelembaban tanah & siram presisi",
        detail = "Ukur kelembaban media/tanah; sirami hingga kapasitas lapang (jangan sampai tergenang). " +
            "Lakukan pagi hari agar tajuk cepat kering.",
        qualityImpact = "Air yang seimbang mencegah busuk akar & bunga rontok, dasar buah mulus bebas pecah.",
        weight = 2
    )

    private fun inspectionMission(stageIdx: Int, day: Long) = Mission(
        id = "m$day-inspect",
        category = Category.INSPEKSI,
        title = "Inspeksi visual & foto kamera 5 sampel tanaman",
        detail = "Potret 5 tanaman acak per bedengan dengan kamera DAPE (deteksi kualitas) dan periksa gejala awal penyakit.",
        qualityImpact = "Deteksi dini patogen mencegah penyebaran yang menurunkan grade panen.",
        targetAmount = 5,
        weight = 2
    )

    private fun seedlingVigorMission(day: Long) = Mission(
        id = "m$day-vigor",
        category = Category.NUTRISI,
        title = "Beri pupuk daun mikro dosis 0,5 g/L",
        detail = "Semprot pupuk daun mikro (klorofil) tipis kabut pada pagi hari untuk bibit berdaun >=3.",
        qualityImpact = "Bibit vigor & batang kokoh menghasilkan tanaman produktif dengan buah seragam.",
        weight = 1
    )

    private fun pestNetMission(day: Long) = Mission(
        id = "m$day-pestnet",
        category = Category.SANITASI,
        title = "Pastikan insect net tertutup rapat",
        detail = "Periksa kasa 50-mesh menutup pembibitan untuk mencegah kutu kebul (vektor virus kuning).",
        qualityImpact = "Bebas virus Gemini sejak dini menjaga kualitas & bentuk buah.",
        weight = 1
    )

    private fun wiwilMission(day: Long) = Mission(
        id = "m$day-wiwil",
        category = Category.UMUM,
        title = "Wiwil (pangkas) tunas air bawah cabang Y",
        detail = "Buang tunas liar di ketiak daun bawah percabangan utama agar nutrisi fokus ke tajuk produktif.",
        qualityImpact = "Sirkulasi udara lebih baik menekan Antraknosa & buah lebih besar seragam.",
        weight = 1
    )

    private fun nitrogenBalanceMission(day: Long) = Mission(
        id = "m$day-n-balance",
        category = Category.NUTRISI,
        title = "Jaga dosis N seimbang, siapkan transisi generatif",
        detail = "Hindari urea berlebih; siapkan pupuk Ca/B untuk fase pembungaan berikutnya.",
        qualityImpact = "Nitrogen berlebih memperlemah dinding sel & memicu rontok bunga.",
        weight = 1
    )

    private fun calciumBoronMission(day: Long) = Mission(
        id = "m$day-ca-b",
        category = Category.NUTRISI,
        title = "Aplikasi Kalsium Nitrat + Boron (kunci pericarp)",
        detail = "Semprot/kocor Ca(NO3)2 + Boron sore hari. Ini fase paling kritis untuk kualitas buah.",
        qualityImpact = "Dinding sel (pericarp) tebal → buah mulus, tahan simpan, bebas busuk ujung (BER).",
        weight = 3
    )

    private fun flowerRetentionMission(day: Long) = Mission(
        id = "m$day-flower",
        category = Category.AIR,
        title = "Stabilkan kelembaban untuk cegah rontok bunga",
        detail = "Hindari kekeringan/over-watering mendadak; jaga kelembaban stabil & suhu tidak ekstrem.",
        qualityImpact = "Bunga tidak rontok → populasi buah lebih banyak & seragam.",
        weight = 2
    )

    private fun potassiumMission(day: Long) = Mission(
        id = "m$day-k",
        category = Category.NUTRISI,
        title = "Tingkatkan Kalium (KNO3) untuk pembesaran & warna",
        detail = "Kocor KNO3 sesuai dosis preskriptif; K meningkatkan bobot & warna merah merata.",
        qualityImpact = "Kalium menaikkan grade (warna & bobot) dan daya simpan buah.",
        weight = 2
    )

    private fun antracnoseScoutMission(day: Long) = Mission(
        id = "m$day-antracnose",
        category = Category.SANITASI,
        title = "Pantau & petik buah berbercak Antraknosa",
        detail = "Cari bercak cekung gelap konsentris; petik & musnahkan buah terinfeksi agar tidak menular.",
        qualityImpact = "Satu buah ber-patek bisa membusukkan satu keranjang → sanitasi menjaga grade A.",
        weight = 2
    )

    private fun harvestRipenessMission(day: Long) = Mission(
        id = "m$day-ripeness",
        category = Category.PANEN,
        title = "Petik selektif buah merah >85% dengan gunting steril",
        detail = "Panen pagi hari; gunting tangkai, hindari luka. Cek kematangan via kamera kualitas.",
        qualityImpact = "Kematangan tepat waktu memaksimalkan grade & harga jual.",
        weight = 3
    )

    private fun sortationMission(day: Long) = Mission(
        id = "m$day-sort",
        category = Category.PANEN,
        title = "Sortir Grade A+/A/B/BS & steril 52°C pasca-panen",
        detail = "Hamparkan di alas kontras, sortir per grade, lakukan sterilisasi air 52°C untuk bunuh spora laten.",
        qualityImpact = "Sortir + sterilisasi mencegah satu buah busuk merusak seluruh keranjang Grade A.",
        weight = 2
    )

    private fun sporeDefenseMission(day: Long, risk: Double) = Mission(
        id = "m$day-spore",
        category = Category.SANITASI,
        title = "Semprot fungisida protektif (risiko jamur tinggi)",
        detail = "EWS mencatat risiko sporulasi ${(risk * 100).toInt()}%. Semprot protektif sore hari & turunkan kelembaban tajuk.",
        qualityImpact = "Menahan ledakan Antraknosa yang bisa menggagalkan panen berkualitas.",
        weight = 2
    )
}
