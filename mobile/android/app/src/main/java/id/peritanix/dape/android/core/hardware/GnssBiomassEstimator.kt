package id.peritanix.dape.android.core.hardware

/**
 * Matriks Sensor — GNSS Mentah (Raw GPS) C/N0 Biomass Estimator.
 *
 * Under a dense chili canopy the GNSS carrier-to-noise density (C/N0, dB-Hz)
 * drops compared to open sky because foliage attenuates the L-band signal. By
 * comparing the mean C/N0 of visible satellites at a point with a calibrated
 * open-sky baseline, we get a cheap, fully-offline proxy for canopy density and
 * therefore leaf biomass / micro-humidity (which drives Antraknosa risk).
 *
 * All math is pure and unit-tested; the GnssMeasurementsEvent/GnssStatus
 * listener that supplies the C/N0 list lives in the Android layer.
 */
object GnssBiomassEstimator {

    /** Typical open-sky C/N0 for a strong satellite around 35–45 dB-Hz. */
    const val DEFAULT_OPEN_SKY_CN0_DBHZ = 40.0

    data class BiomassEstimate(
        val meanCn0DbHz: Double,
        val satelliteCount: Int,
        val openSkyCn0DbHz: Double,
        val attenuationDb: Double,       // openSky - observed (>= 0 means canopy loss)
        val canopyDensityPct: Double,    // 0..100 derived density proxy
        val densityClass: String,        // "Rimbun", "Sedang", "Terbuka"
        val biomassNote: String
    )

    /**
     * Estimate canopy density from a set of satellite C/N0 readings.
     *
     * @param cn0Values C/N0 (dB-Hz) per visible satellite from GnssStatus.getCn0DbHz().
     * @param openSkyBaseline calibrated open-sky mean C/N0 (defaults to 40 dB-Hz).
     */
    fun estimate(cn0Values: List<Float>, openSkyBaseline: Double = DEFAULT_OPEN_SKY_CN0_DBHZ): BiomassEstimate {
        // Ignore implausible readings (0 = satellite without signal).
        val valid = cn0Values.filter { it > 5.0f && it < 60.0f }.map { it.toDouble() }
        if (valid.isEmpty()) {
            return BiomassEstimate(
                meanCn0DbHz = 0.0, satelliteCount = 0,
                openSkyCn0DbHz = openSkyBaseline,
                attenuationDb = 0.0, canopyDensityPct = 0.0,
                densityClass = "Tidak diketahui",
                biomassNote = "Tidak ada sinyal GNSS valid. Pindah ke area lebih terbuka untuk mengukur kerapatan tajuk."
            )
        }
        val mean = valid.average()
        val atten = (openSkyBaseline - mean).coerceAtLeast(0.0)

        // Map attenuation (0..~15 dB practical range) to a 0..100 density proxy.
        val density = (atten / 15.0 * 100.0).coerceIn(0.0, 100.0)
        val (cls, note) = when {
            density >= 55.0 -> "Rimbun" to
                "Atenuasi GNSS ${"%.1f".format(atten)} dB: tajuk sangat rimbun. Kelembaban mikro tinggi → risiko Antraknosa meningkat. Pertimbangkan wiwilan & perbaiki sirkulasi udara."
            density >= 25.0 -> "Sedang" to
                "Atenuasi GNSS ${"%.1f".format(atten)} dB: kerapatan tajuk sedang, kanopi sehat. Lanjutkan pemantauan rutin."
            else -> "Terbuka" to
                "Atenuasi GNSS ${"%.1f".format(atten)} dB: tajuk relatif terbuka. Kanopi mungkin masih muda atau kurang rimbun."
        }
        return BiomassEstimate(
            meanCn0DbHz = Math.round(mean * 10.0) / 10.0,
            satelliteCount = valid.size,
            openSkyCn0DbHz = openSkyBaseline,
            attenuationDb = Math.round(atten * 10.0) / 10.0,
            canopyDensityPct = Math.round(density * 10.0) / 10.0,
            densityClass = cls,
            biomassNote = note
        )
    }
}
