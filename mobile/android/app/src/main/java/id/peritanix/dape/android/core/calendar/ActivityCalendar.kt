package id.peritanix.dape.android.core.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Pure calendar math for the 30-year farm activity calendar.
 *
 * The calendar lets a farmer browse 30 years into the future, pick any day, and
 * see (or add) the agronomic activities logged that day. All date arithmetic is
 * framework-free and unit-tested; the Compose UI and Room storage are thin layers
 * on top of this.
 *
 * Dates are exchanged as **epoch-day** (days since 1970-01-01, UTC) so they map
 * 1:1 to a Room INTEGER column and never suffer timezone drift.
 */
object ActivityCalendar {

    /** How many years ahead the calendar is browsable (checklist: 30 years). */
    const val YEARS_AHEAD = 30

    val today: LocalDate get() = LocalDate.now()

    /** First day (Jan 1 of this year) of the browsable range. */
    fun rangeStart(today: LocalDate = LocalDate.now()): LocalDate = today.withDayOfMonth(1).withMonth(1)

    /** Last day (Dec 31) of the browsable range = today + 30 years. */
    fun rangeEnd(today: LocalDate = LocalDate.now()): LocalDate =
        today.plusYears(YEARS_AHEAD.toLong()).withMonth(12).withDayOfMonth(31)

    /** Whether [date] is inside the 30-year browsable window. */
    fun inRange(date: LocalDate, today: LocalDate = LocalDate.now()): Boolean =
        !date.isBefore(today.withMonth(1).withDayOfMonth(1)) && !date.isAfter(rangeEnd(today))

    /** Epoch-day (days since 1970-01-01) for a LocalDate. */
    fun toEpochDay(date: LocalDate): Long = date.toEpochDay()

    /** LocalDate from epoch-day. */
    fun fromEpochDay(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    /**
     * The list of years the calendar can show: [today.year .. today.year+30].
     */
    fun years(today: LocalDate = LocalDate.now()): List<Int> =
        (today.year..today.year + YEARS_AHEAD).toList()

    /** Months (1..12) — static, exposed for the picker. */
    val months: List<Int> = (1..12).toList()

    /** Number of days in a given year-month. */
    fun daysInMonth(year: Int, month: Int): Int = YearMonth.of(year, month).lengthOfMonth()

    /**
     * Build a fixed 7×N month grid (Monday-first) for [year]/[month]. Cells that
     * fall outside the month are null so the UI can render blanks. This is the
     * classic calendar layout.
     */
    fun monthGrid(year: Int, month: Int): List<LocalDate?> {
        val ym = YearMonth.of(year, month)
        val first = ym.atDay(1)
        // Days from Monday (0) to the 1st-of-month's weekday.
        val lead = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        val total = ym.lengthOfMonth()
        val cells = ArrayList<LocalDate?>(lead + total + 7)
        repeat(lead) { cells.add(null) }
        for (d in 1..total) cells.add(ym.atDay(d))
        // pad the tail to a whole number of weeks
        while (cells.size % 7 != 0) cells.add(null)
        return cells
    }

    /** Days between two dates (a → b), positive if b is later. */
    fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)

    /** Human Indonesian weekday label for a date. */
    fun dayName(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "Senin"
        DayOfWeek.TUESDAY -> "Selasa"
        DayOfWeek.WEDNESDAY -> "Rabu"
        DayOfWeek.THURSDAY -> "Kamis"
        DayOfWeek.FRIDAY -> "Jumat"
        DayOfWeek.SATURDAY -> "Sabtu"
        DayOfWeek.SUNDAY -> "Minggu"
    }

    /** Human Indonesian month label. */
    fun monthName(month: Int): String = arrayOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )[(month - 1).coerceIn(0, 11)]

    /** Full label, e.g. "Rabu, 17 Oktober 2026". */
    fun fullLabel(date: LocalDate): String =
        "${dayName(date)}, ${date.dayOfMonth} ${monthName(date.monthValue)} ${date.year}"

    /**
     * Group activity epochs by day for fast per-day lookup.
     * Input is a list of (epochDay) pairs; returns map epochDay → count or items
     * is handled by the caller; here we expose a simple "days with activity" set.
     */
    fun daysWithActivity(epochDays: List<Long>): Set<Long> = epochDays.toSet()

    /**
     * Clamp a browsed month/year to the allowed range so navigation can't scroll
     * before this year or beyond +30 years.
     */
    fun clampYearMonth(year: Int, month: Int, today: LocalDate = LocalDate.now()): YearMonth {
        val y = year.coerceIn(today.year, today.year + YEARS_AHEAD)
        val m = month.coerceIn(1, 12)
        return YearMonth.of(y, m)
    }

    /**
     * Relative day label (e.g. "Hari Ini", "Besok", "Dalam 4 hari", "Dalam 2 tahun").
     */
    fun relativeDayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val diff = daysBetween(today, date)
        return when {
            diff == 0L -> "Hari Ini"
            diff == 1L -> "Besok"
            diff == 2L -> "Lusa"
            diff in 3..29 -> "Dalam $diff hari"
            diff in 30..364 -> "Dalam ${diff / 30} bulan"
            diff in 365..729 -> "Tahun depan"
            diff >= 730 -> "Dalam ${diff / 365} tahun"
            diff == -1L -> "Kemarin"
            diff in -29..-2 -> "${-diff} hari lalu"
            diff in -364..-30 -> "${-diff / 30} bulan lalu"
            else -> "${-diff / 365} tahun lalu"
        }
    }

    /**
     * Category metadata for agronomic activities.
     */
    data class CategoryInfo(
        val code: String,
        val label: String,
        val colorHex: Long,
        val iconName: String
    )

    val CATEGORIES = listOf(
        CategoryInfo("TANAM", "Tanam & Persemaian", 0xFF2E7D32, "Sprout"),
        CategoryInfo("PUPUK", "Pemupukan Susulan", 0xFF1565C0, "Terrain"),
        CategoryInfo("SEMPROT", "Penyemprotan Proteksi", 0xFFAD1457, "BugReport"),
        CategoryInfo("IRIGASI", "Irigasi & Kelembaban", 0xFF00838F, "WaterDrop"),
        CategoryInfo("WIWILAN", "Wiwilan Tunas & LAI", 0xFF6A1B9A, "Straighten"),
        CategoryInfo("PENGAMATAN", "Pengamatan Antraknosa", 0xFFD84315, "Microscope"),
        CategoryInfo("PANEN", "Panen & Sortasi", 0xFFE65100, "Chili"),
        CategoryInfo("SENSOR", "Kalibrasi Sensor Lab", 0xFF37474F, "Compass"),
        CategoryInfo("LAIN", "Aktivitas Lapangan Lainnya", 0xFF4E342E, "CheckCircle")
    )

    fun findCategory(code: String): CategoryInfo =
        CATEGORIES.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: CategoryInfo(code, code, 0xFF4E342E, "CheckCircle")

    /**
     * Sample activity seed specification.
     */
    data class SampleActivitySpec(
        val offsetDays: Long,
        val kind: String,
        val title: String,
        val notes: String,
        val landId: String = "Blok A1 Kopeng"
    )

    /**
     * Initial precision micro-farming agronomic calendar schedule across the 30-year span.
     */
    fun sampleSeedActivities(): List<SampleActivitySpec> = listOf(
        SampleActivitySpec(
            offsetDays = 0,
            kind = "PENGAMATAN",
            title = "Pengamatan Antraknosa & Uji Buah",
            notes = "Inspeksi lesi 35% pada Blok A1, perlakuan fungisida P-CU-01 sesuai rekomendasi lab."
        ),
        SampleActivitySpec(
            offsetDays = 1,
            kind = "SEMPROT",
            title = "Penyemprotan Fungisida & Cek Angin",
            notes = "Cek Acoustic Anemometer sebelum semprot (pastikan < 12 km/jam)."
        ),
        SampleActivitySpec(
            offsetDays = 4,
            kind = "PUPUK",
            title = "Pemupukan Susulan NPK Cor & Uji Tensiometer",
            notes = "Dosis 5 gram per lubang tanam mulsa, pantau kadar air tanah."
        ),
        SampleActivitySpec(
            offsetDays = 8,
            kind = "WIWILAN",
            title = "Wiwilan Tunas Air & Ceptometer LAI",
            notes = "Gunakan sensor lux ambien; pangkas tunas jika LAI tajuk > 3.5."
        ),
        SampleActivitySpec(
            offsetDays = 15,
            kind = "PANEN",
            title = "Pemetikan Perdana & Sortasi Archimedes",
            notes = "Target panen 1.85 Ton/Ha, sortasi densitas biji bernas 94%."
        ),
        SampleActivitySpec(
            offsetDays = 30,
            kind = "SENSOR",
            title = "Kalibrasi Barometer & Beda Tinggi Bedengan",
            notes = "Uji beda tinggi parit vs bedengan 40-50 cm untuk drainase optimal."
        ),
        SampleActivitySpec(
            offsetDays = 365,
            kind = "TANAM",
            title = "Siklus Tanam Musim Hujan 2027",
            notes = "Pengolahan tanah, pemasangan mulsa perak-hitam, dan EKF PDR mapping."
        ),
        SampleActivitySpec(
            offsetDays = 1825,
            kind = "PUPUK",
            title = "Rotasi Lahan Cabai Periode 5 Tahunan (2031)",
            notes = "Solarisasi tanah dan peremajaan unsur hara mikro organik."
        ),
        SampleActivitySpec(
            offsetDays = 3650,
            kind = "PENGAMATAN",
            title = "Audit Agro-Fisika Dekade 1 (2036)",
            notes = "Evaluasi produktivitas kumulatif dan kesuburan tanah PostGIS."
        ),
        SampleActivitySpec(
            offsetDays = 10950,
            kind = "PANEN",
            title = "Proyeksi Keberlanjutan 30 Tahun (2056)",
            notes = "Target regenerasi tanah cabai presisi mikro jangka panjang."
        )
    )
}
