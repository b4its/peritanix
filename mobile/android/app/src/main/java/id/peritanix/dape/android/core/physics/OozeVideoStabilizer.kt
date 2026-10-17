package id.peritanix.dape.android.core.physics

/**
 * TAHAP 3.1 Langkah D — Ooze Video Gyro Stabilization & Quality Gate.
 *
 * The destructive ooze test records ~10 s of a cut stem in clear water. Hand
 * shake corrupts the Lucas-Kanade optical-flow that detects white bacterial
 * streaming (Ralstonia). Before the flow is computed we:
 *   1. Integrate the gyroscope angular-velocity trace over the clip duration to
 *      estimate cumulative angular shake (°) and a per-clip stability score.
 *   2. Recommend a crop/scale + a confidence multiplier so downstream flow is
 *      weighted by how still the shot was, and reject clips that are unusably shaky.
 *
 * Pure math (given the gyro samples), fully unit-tested. The gyro listener that
 * produces the samples lives in the Android layer.
 */
object OozeVideoStabilizer {

    /** One gyroscope sample (rad/s per axis) plus its timestamp (ms). */
    data class GyroSample(val tMs: Long, val gx: Float, val gy: Float, val gz: Float)

    data class StabilizationResult(
        val durationMs: Long,
        val angularShakeDeg: Double,     // integrated |ω| over the clip
        val shakeScore: Double,          // 0 (rock-steady) .. 1 (very shaky)
        val stabilityPct: Double,        // 100 * (1 - shakeScore)
        val recommendedCropPct: Int,     // extra border to crop for EIS (e.g. 12 = crop 12%)
        val opticalFlowConfidence: Double, // multiplier 0.2..1.0 for the flow result
        val isUsable: Boolean,           // false → re-record advised
        val advisory: String
    )

    /** Beyond this cumulative angular shake (~14°) the clip is too shaky. */
    const val MAX_SHAKE_DEG = 14.0
    /** Reference shake for normalization (~4° ⇒ still quite steady). */
    private const val REF_SHAKE_DEG = 4.0

    /**
     * Evaluate a gyro trace over the recording window.
     *
     * Cumulative rotation angle over the clip = Σ |ω|·dt (rad → deg). Using the
     * magnitude of the angular-velocity vector captures shake on all axes.
     */
    fun analyze(samples: List<GyroSample>): StabilizationResult {
        if (samples.size < 2) {
            return StabilizationResult(
                durationMs = 0, angularShakeDeg = 0.0, shakeScore = 1.0, stabilityPct = 0.0,
                recommendedCropPct = 20, opticalFlowConfidence = 0.2, isUsable = false,
                advisory = "Data giroskop tidak cukup. Rekam ulang video uji ooze minimal 10 detik."
            )
        }
        var angleRad = 0.0
        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val cur = samples[i]
            val dt = (cur.tMs - prev.tMs).coerceAtLeast(0L) / 1000.0
            val omega = Math.sqrt(
                cur.gx.toDouble() * cur.gx + cur.gy.toDouble() * cur.gy + cur.gz.toDouble() * cur.gz
            )
            angleRad += omega * dt
        }
        val shakeDeg = Math.toDegrees(angleRad)
        val duration = samples.last().tMs - samples.first().tMs

        val score = (shakeDeg / MAX_SHAKE_DEG).coerceIn(0.0, 1.0)
        val stabilityPct = (100.0 * (1.0 - score)).coerceIn(0.0, 100.0)

        // Crop grows with shake (more margin needed to stabilise), 8%..22%.
        val crop = (8 + (shakeDeg / MAX_SHAKE_DEG) * 14).toInt().coerceIn(8, 22)
        // Flow confidence decays with shake but never below 0.2.
        val confidence = (1.0 - 0.8 * score).coerceIn(0.2, 1.0)
        val usable = shakeDeg <= MAX_SHAKE_DEG && duration >= 8_000L

        val advisory = when {
            !usable && duration < 8_000L ->
                "Durasi rekaman terlalu pendek (${duration} ms). Rekam ulang minimal 10 detik."
            !usable ->
                "Goyangan tangan terlalu tinggi (${Math.round(shakeDeg)}°). Letakkan HP di tripod/tumpuan, rekam ulang uji ooze."
            shakeDeg <= REF_SHAKE_DEG ->
                "Stabil (goyangan ${"%.1f".format(shakeDeg)}°). Optical flow benang bakteri dapat dihitung dengan keyakinan tinggi."
            else ->
                "Cukup stabil (goyangan ${"%.1f".format(shakeDeg)}°). Kompensasi EIS diterapkan (crop ${crop}%)."
        }
        return StabilizationResult(
            durationMs = duration,
            angularShakeDeg = Math.round(shakeDeg * 10.0) / 10.0,
            shakeScore = Math.round(score * 1000.0) / 1000.0,
            stabilityPct = Math.round(stabilityPct * 10.0) / 10.0,
            recommendedCropPct = crop,
            opticalFlowConfidence = Math.round(confidence * 1000.0) / 1000.0,
            isUsable = usable,
            advisory = advisory
        )
    }
}
