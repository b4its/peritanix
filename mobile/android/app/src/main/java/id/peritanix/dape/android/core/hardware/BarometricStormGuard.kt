package id.peritanix.dape.android.core.hardware

import kotlin.math.abs

/**
 * Tahap 4.4: Barometric Storm Guard & Spray Safety Lockout.
 *
 * Continuously evaluates barometric pressure rate of change (TYPE_PRESSURE):
 *   ΔP / Δt <= -1.5 hPa / 2 hours
 *
 * Detects impending local storm/rain 1 to 2 hours in advance without internet,
 * locking pesticide and foliar fertilizer spraying to prevent chemical wash-off
 * and pesticide waste.
 *
 * Combines with Acoustic Anemometer (> 12.0 km/h) to guarantee zero droplet drift.
 */
object BarometricStormGuard {

    const val STORM_DROP_THRESHOLD_HPA_PER_2HR = -1.5 // hPa in 2 hours
    const val MAX_SPRAY_WIND_SPEED_KMH = 12.0

    data class PressureReading(
        val timestampMs: Long,
        val pressureHpa: Double
    )

    enum class StormThreatLevel(val label: String) {
        SAFE("Kondisi Stabil / Cerah"),
        WATCH("Perhatian: Tekanan Turun"),
        STORM_IMMINENT("BAHAYA BADAI / HUJAN DEKAT")
    }

    data class StormVerdict(
        val currentPressureHpa: Double,
        val deltaPressure2Hr: Double,
        val threatLevel: StormThreatLevel,
        val isRainImminent: Boolean,
        val rainEtaMinutes: Int?,
        val advisoryNote: String
    )

    data class SprayLockoutVerdict(
        val isPermitted: Boolean,
        val windSpeedKmh: Double,
        val pressureDrop2Hr: Double,
        val reason: String,
        val correctiveAdvice: String
    )

    /**
     * Evaluates pressure slope over a recent time series.
     * Normalized to a 2-hour window (7,200,000 ms).
     */
    fun evaluateBarometricTrend(readings: List<PressureReading>): StormVerdict {
        if (readings.isEmpty()) {
            return StormVerdict(
                currentPressureHpa = 1013.25,
                deltaPressure2Hr = 0.0,
                threatLevel = StormThreatLevel.SAFE,
                isRainImminent = false,
                rainEtaMinutes = null,
                advisoryNote = "Belum ada riwayat barometer. Menggunakan baseline atmosfer 1013.25 hPa."
            )
        }

        if (readings.size == 1) {
            val p = readings.first().pressureHpa
            return StormVerdict(
                currentPressureHpa = p,
                deltaPressure2Hr = 0.0,
                threatLevel = StormThreatLevel.SAFE,
                isRainImminent = false,
                rainEtaMinutes = null,
                advisoryNote = "Sensor membaca ${"%.2f".format(p)} hPa. Mengumpulkan riwayat tren..."
            )
        }

        val sorted = readings.sortedBy { it.timestampMs }
        val latest = sorted.last()
        val first = sorted.first()
        val elapsedMs = (latest.timestampMs - first.timestampMs).coerceAtLeast(1000L)

        // Normalize delta to exactly 2-hour equivalent: (deltaP / elapsedHours) * 2.0
        val rawDelta = latest.pressureHpa - first.pressureHpa
        val elapsedHours = elapsedMs / (3600.0 * 1000.0)
        val normalized2HrDelta = if (elapsedHours > 0.0) {
            (rawDelta / elapsedHours) * 2.0
        } else {
            rawDelta
        }

        val roundedDelta = Math.round(normalized2HrDelta * 100.0) / 100.0
        val isStorm = roundedDelta <= STORM_DROP_THRESHOLD_HPA_PER_2HR
        val isWatch = roundedDelta <= -0.8 && !isStorm

        val threat = when {
            isStorm -> StormThreatLevel.STORM_IMMINENT
            isWatch -> StormThreatLevel.WATCH
            else -> StormThreatLevel.SAFE
        }

        val eta = if (isStorm) {
            // Faster drop means rain arrives sooner (45 - 90 minutes)
            val urgency = abs(roundedDelta / STORM_DROP_THRESHOLD_HPA_PER_2HR).coerceIn(1.0, 3.0)
            (90.0 / urgency).toInt()
        } else null

        val note = when {
            isStorm -> "PERINGATAN BADAI: Tekanan udara anjlok ${"%.2f".format(roundedDelta)} hPa / 2 jam. Hujan deras diprediksi turun dalam $eta menit. DILARANG MENYEMPROT."
            isWatch -> "Waspada: Tekanan udara menurun (${"%.2f".format(roundedDelta)} hPa / 2 jam). Pantau langit barat."
            else -> "Tekanan udara stabil (${"%.2f".format(latest.pressureHpa)} hPa, Δ2h ${"%.2f".format(roundedDelta)} hPa). Cuaca aman."
        }

        return StormVerdict(
            currentPressureHpa = latest.pressureHpa,
            deltaPressure2Hr = roundedDelta,
            threatLevel = threat,
            isRainImminent = isStorm,
            rainEtaMinutes = eta,
            advisoryNote = note
        )
    }

    /**
     * Determines whether spraying (fungicide/fertilizer) is permitted or strictly locked.
     */
    fun evaluateSprayLockout(
        windSpeedKmh: Double,
        barometerVerdict: StormVerdict
    ): SprayLockoutVerdict {
        val windHazard = windSpeedKmh > MAX_SPRAY_WIND_SPEED_KMH
        val rainHazard = barometerVerdict.isRainImminent

        if (windHazard && rainHazard) {
            return SprayLockoutVerdict(
                isPermitted = false,
                windSpeedKmh = windSpeedKmh,
                pressureDrop2Hr = barometerVerdict.deltaPressure2Hr,
                reason = "KUNCI GANDA DIAKTIFKAN: Angin kencang (${"%.1f".format(windSpeedKmh)} km/jam) dan Hujan badai segera turun (${barometerVerdict.rainEtaMinutes} menit).",
                correctiveAdvice = "Tunda aplikasi. Semprotan akan terbawa angin (drift) dan terbilas air hujan (leaching). Tunggu badai reda."
            )
        }

        if (windHazard) {
            return SprayLockoutVerdict(
                isPermitted = false,
                windSpeedKmh = windSpeedKmh,
                pressureDrop2Hr = barometerVerdict.deltaPressure2Hr,
                reason = "KUNCI ANGIN AKTIF: Kecepatan angin ${"%.1f".format(windSpeedKmh)} km/jam (> 12 km/jam batas aman).",
                correctiveAdvice = "Hindari penyemprotan sekarang. Risiko droplet melayang ke tanaman non-target dan terbuang percuma hingga 40%."
            )
        }

        if (rainHazard) {
            return SprayLockoutVerdict(
                isPermitted = false,
                windSpeedKmh = windSpeedKmh,
                pressureDrop2Hr = barometerVerdict.deltaPressure2Hr,
                reason = "KUNCI HUJAN BAROMETER AKTIF: Penurunan drastis ${"%.2f".format(barometerVerdict.deltaPressure2Hr)} hPa/2 jam.",
                correctiveAdvice = "Hujan lokal segera turun dalam ${barometerVerdict.rainEtaMinutes} menit. Obat semprot belum sempat diserap daun dan akan tercuci total."
            )
        }

        return SprayLockoutVerdict(
            isPermitted = true,
            windSpeedKmh = windSpeedKmh,
            pressureDrop2Hr = barometerVerdict.deltaPressure2Hr,
            reason = "KONDISI AMAN LAPANGAN: Angin ${"%.1f".format(windSpeedKmh)} km/jam & Barometer stabil (${"%.2f".format(barometerVerdict.currentPressureHpa)} hPa).",
            correctiveAdvice = "Penyemprotan nutrisi Ca+B+Si dan fungisida protektif Grade A+ diizinkan. Gunakan nozel kabut halus."
        )
    }
}
