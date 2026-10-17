package id.peritanix.dape.android.core.guide

/**
 * Panduan budidaya cabai dari bibit hingga siap panen.
 *
 * Static, offline agronomic content shown in both modes (it is reference
 * material, not device data). Each stage carries an HST window, the key
 * activities, the main risk for that window, and a checklist a farmer can tick
 * off. Kept as pure data so it can be asserted in unit tests.
 */
object ChiliGrowingGuide {

    data class Stage(
        val index: Int,
        val title: String,
        val hstRange: String,
        val summary: String,
        val activities: List<String>,
        val risk: String,
        val checklist: List<String>
    )

    /** Ordered stages from seeding to harvest for Capsicum annuum/frutescens. */
    val stages: List<Stage> = listOf(
        Stage(
            index = 1,
            title = "Persemaian Bibit",
            hstRange = "-21 – 0 HST (sebelum tanam)",
            summary = "Menyiapkan benih sehat menjadi bibit kuat sebelum pindah tanam.",
            activities = listOf(
                "Rendam benih 4–6 jam, buang benih yang mengapung.",
                "Semai di tray/ polibag kecil dengan media tanah + kompos steril.",
                "Siram halus pagi hari, letakkan di tempat terang tanpa hujan langsung.",
                "Pilih benih dari varietas tahan (mis. Lado F1, Bhaskara) untuk kebutuhan pasar."
            ),
            risk = "Rebah kecambah (damping-off) & kutu kebul pembawa virus.",
            checklist = listOf(
                "Benih direndam & disortir",
                "Media semai steril disiapkan",
                "Naungan/plastik pelindung dipasang",
                "Bibit berdaun 4–6 lembar (≈21 hari)"
            )
        ),
        Stage(
            index = 2,
            title = "Pindah Tanam & Vegetatif",
            hstRange = "0 – 35 HST",
            summary = "Tanam bibit di lahan, bangun perakaran dan tajuk yang sehat.",
            activities = listOf(
                "Buat bedengan tinggi 30–50 cm (wajib bila TWI tinggi).",
                "Tanam 1 bibit per lubang, jarak 50–60 cm x 60–70 cm.",
                "Pupuk dasar: kompos + NPK seimbang; cek pH tanah (optimal 6,0–6,8).",
                "Pasang mulsa & ajir (tiang) untuk penopang awal."
            ),
            risk = "Defisiensi N, serangan thrips/tungau awal, stres pindah tanam.",
            checklist = listOf(
                "Bedengan & lubang tanam siap",
                "pH tanah terukur ≥ 5,5",
                "Ajir/mulsa terpasang",
                "Penyiraman terjadwal"
            )
        ),
        Stage(
            index = 3,
            title = "Pembungaan (Transisi Generatif)",
            hstRange = "36 – 60 HST",
            summary = "Fase kritis: bunga muncul, kebutuhan kalsium & boron meningkat.",
            activities = listOf(
                "Berikan Kalsium Nitrat + Boron untuk penguatan dinding sel (pericarp).",
                "Hindari pupuk Nitrogen tinggi/urea — memicu kerontokan bunga.",
                "Jaga kelembaban stabil; hindari over-watering & kekeringan ekstrem.",
                "Pantau thrips & tungau pada bunga muda."
            ),
            risk = "Rontok bunga akibat stres air/suhu, defisiensi Ca/P, serangan thrips.",
            checklist = listOf(
                "Kalsium + Boron diaplikasikan",
                "Urea/N tinggi dihentikan",
                "Kelembaban tanah stabil",
                "Bunga rontok < 10%"
            )
        ),
        Stage(
            index = 4,
            title = "Pembuahan & Pematangan",
            hstRange = "61 – 90+ HST",
            summary = "Buah terbentuk & membesar; fokus kualitas dan proteksi penyakit.",
            activities = listOf(
                "Tingkatkan Kalium (KNO3) untuk bobot & warna buah.",
                "Jaga ketebalan pericarp dengan kalsium agar hindari busuk ujung buah (BER).",
                "Waspada Antraknosa (patek): petik & musnahkan buah berbercak gelap.",
                "Pasang perangkap lalat buah (Bactrocera)."
            ),
            risk = "Antraknosa, lalat buah, busuk ujung buah (Blossom End Rot).",
            checklist = listOf(
                "Pupuk K (KNO3) diberikan",
                "Buah berbercak dipetik & dibuang",
                "Perangkap lalat buah terpasang",
                "Penyemprotan fungisida protektif sesuai jadwal"
            )
        ),
        Stage(
            index = 5,
            title = "Panen & Pasca-Panen",
            hstRange = "90 – 120 HST",
            summary = "Panen pada kematangan tepat, sortir mutu, jaga harga jual.",
            activities = listOf(
                "Panen saat > 85% permukaan buah merah (atau sesuai permintaan pasar).",
                "Gunakan gunting, jangan tarik — hindari luka tangkai.",
                "Sortir Grade A+/A/B/BS; pisahkan buah cacat agar tidak menular.",
                "Simpan di tempat sejuk, jual cepat atau proses lanjut."
            ),
            risk = "Ketidakseragaman kematangan, penyortiran lambat, fluktuasi harga.",
            checklist = listOf(
                "Kematangan > 85% merah (cek via kamera kualitas)",
                "Sortir mutu selesai",
                "Buah cacat dipisahkan",
                "Jadwal jual / kontrak harga dicek"
            )
        )
    )

    fun stageForHst(hst: Int): Stage? = when {
        hst < 0 -> stages[0]
        hst <= 35 -> stages[1]
        hst <= 60 -> stages[2]
        hst <= 90 -> stages[3]
        else -> stages[4]
    }
}
