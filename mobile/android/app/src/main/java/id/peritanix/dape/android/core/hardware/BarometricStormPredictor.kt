package id.peritanix.dape.android.core.hardware

/**
 * Tahap 4.2: Pendeteksi Badai Mikro Barometrik (BarometricStormPredictor).
 *
 * Tracks atmospheric pressure trends over 2-3 hours to detect rapid drops (Delta P / Delta t <= -1.5 hPa)
 * indicating imminent convective thunderstorms. Blocks contact pesticide spraying to prevent rain washoff.
 */
object BarometricStormPredictor {

    data class PressureLogPoint(
        val timestampMs: Long,
        val pressureHpa: Double
    )

    data class StormRiskEvaluation(
        val pressureDeltaHpa: Double,
        val timeIntervalHours: Double,
        val dropRateHpaPerHour: Double,
        val isImminentStorm: Boolean, // true if rapid drop <= -1.5 hPa in 2-3h
        val sprayPermitted: Boolean,
        val warningTitle: String,
        val warningMessage: String
    )

    /**
     * Evaluates pressure slope from historical pressure log points.
     */
    fun evaluatePressureTrend(
        history: List<PressureLogPoint>,
        currentPressureHpa: Double,
        currentTimeMs: Long = System.currentTimeMillis()
    ): StormRiskEvaluation {
        if (history.isEmpty()) {
            return StormRiskEvaluation(
                pressureDeltaHpa = 0.0,
                timeIntervalHours = 1.0,
                dropRateHpaPerHour = 0.0,
                isImminentStorm = false,
                sprayPermitted = true,
                warningTitle = "Tekanan Barometer Stabil",
                warningMessage = "Tren tekanan atmosfer ${"%.1f".format(currentPressureHpa)} hPa stabil. Tidak ada indikasi badai konvektif mendadak."
            )
        }

        // Find reference point 2 to 3 hours ago (or earliest available)
        val targetWindowMs = 2.5 * 3600 * 1000
        val refPoint = history.minByOrNull { kotlin.math.abs((currentTimeMs - it.timestampMs) - targetWindowMs) }
            ?: history.first()

        val deltaMs = (currentTimeMs - refPoint.timestampMs).coerceAtLeast(60 * 1000)
        val deltaHours = deltaMs / (3600.0 * 1000.0)

        val deltaP = currentPressureHpa - refPoint.pressureHpa
        val ratePerHour = deltaP / deltaHours

        // Rapid drop threshold: drop of 1.5 hPa over 2.5 hours -> rate <= -0.60 hPa/hour
        val isStorm = deltaP <= -1.4 || ratePerHour <= -0.60

        val (title, msg) = if (isStorm) {
            Pair(
                "PERINGATAN BADAI MIKRO KONVEKTIF!",
                "Terdeteksi penurunan tekanan udara tajam ${"%.2f".format(deltaP)} hPa dalam ${"%.1f".format(deltaHours)} jam (laju ${"%.2f".format(ratePerHour)} hPa/jam). " +
                        "Hujan lebat diprediksi turun dalam 1-2 jam ke depan! BLOKIR jadwal semprot pestisida kontak agar bahan aktif tidak terbuang sia-sia tercuci air hujan, " +
                        "atau gunakan bahan perekat (sticker/adjuvant) dosis tinggi."
            )
        } else {
            Pair(
                "Barometer Kondusif",
                "Perubahan tekanan udara wajar (${"%.2f".format(deltaP)} hPa / ${"%.1f".format(deltaHours)} jam). Aman untuk aplikasi penyemprotan pupuk daun dan fungisida protektif."
            )
        }

        return StormRiskEvaluation(
            pressureDeltaHpa = Math.round(deltaP * 100.0) / 100.0,
            timeIntervalHours = Math.round(deltaHours * 10.0) / 10.0,
            dropRateHpaPerHour = Math.round(ratePerHour * 100.0) / 100.0,
            isImminentStorm = isStorm,
            sprayPermitted = !isStorm,
            warningTitle = title,
            warningMessage = msg
        )
    }
}
