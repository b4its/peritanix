package id.peritanix.dape.android.core.vision

import id.peritanix.dape.android.core.physics.PodMorphologyGrader

/**
 * Real-mode chili quality detection from a camera-captured image.
 *
 * The camera gives us an ARGB pixel buffer. This analyzer computes deterministic
 * colour statistics that map to the two commercial quality drivers a farmer can
 * actually judge by eye — ripeness (red coverage) and visible defects (dark
 * necrotic spots / anthracnose) — then hands them to [PodMorphologyGrader].
 *
 * It is pure (no Android framework types) so it is fully unit-testable: pass an
 * [IntArray] of ARGB pixels + dimensions, get a [QualityReport].
 *
 * Pipeline:
 *   1. Classify every pixel into RED (ripe), TURNING (blush), GREEN (unripe),
 *      DARK (defect/shadow) or OTHER (background/leaf).
 *   2. redCoveragePct = red / (leaf-pixels on the pod)
 *   3. defectPct     = dark / (leaf-pixels on the pod)
 *   4. Derive a ripeness class and a grade verdict.
 */
object ChiliQualityAnalyzer {

    /** Per-class pixel fractions (0..100), computed over the pod region only. */
    data class ColorStats(
        val redPct: Double,
        val turningPct: Double,
        val greenPct: Double,
        val defectPct: Double,
        val backgroundPct: Double,
        val sampledPixels: Int
    )

    enum class Ripeness { UNRIPE, TURNING, RIPE, OVERTIPE }

    data class QualityReport(
        val grade: String,          // A+, A, B, BS
        val commercialTier: String,
        val ripeness: Ripeness,
        val ripenessLabel: String,
        val redCoveragePct: Double,
        val defectPct: Double,
        val morphologicalScore: Double,
        val priceMultiplier: Double,
        val advice: String,
        val stats: ColorStats
    )

    // HSV-ish thresholds (0..1 hue via RGB dominance heuristics).
    private const val DARK_LUMA = 0.22      // below this is a dark defect/shadow
    private const val GREEN_MARGIN = 0.06   // green channel dominance for unripe

    /**
     * Analyse an ARGB pixel buffer.
     *
     * @param pixels ARGB (0xAARRGGBB) colours, row-major.
     * @param archimedesSinkRatio optional density (0..1); off-camera, so the UI
     *        passes a neutral 0.75 default which does not gate a good grade.
     */
    fun analyze(
        pixels: IntArray,
        width: Int,
        height: Int,
        archimedesSinkRatio: Double = 0.75
    ): QualityReport {
        require(width > 0 && height > 0) { "width/height must be > 0" }
        require(pixels.size >= width * height) { "pixel buffer smaller than $width x $height" }

        var red = 0; var turning = 0; var green = 0; var dark = 0; var bg = 0
        val total = width * height

        // Sample at a stride to bound work on large camera frames.
        val stride = if (total > 90_000) 3 else 1
        var sampled = 0

        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val px = pixels[y * width + x]
                val r = ((px shr 16) and 0xFF) / 255.0
                val g = ((px shr 8) and 0xFF) / 255.0
                val b = (px and 0xFF) / 255.0
                sampled++

                val luma = 0.299 * r + 0.587 * g + 0.114 * b
                when {
                    // Very dark -> necrotic defect / anthracnose lesion. (A pure
                    // black patch on the pod is a defect, not background; real
                    // shoot-on-contrast-mat photos put the mat OUTSIDE this
                    // classifier's dark threshold, so dark here = lesion.)
                    luma < DARK_LUMA -> dark++
                    // Red/purple dominates and clearly redder than green+blue.
                    r > 0.35 && r > g + 0.10 && r > b + 0.05 -> red++
                    // Orange-ish turning (red elevated but green still high).
                    r > 0.30 && g > 0.18 && r > b + 0.05 && g < r -> turning++
                    // Green dominates -> unripe pod or leaf background; both are
                    // "not ready" so count as green for ripeness but treat strong
                    // green as background leaf via margin.
                    g > r + GREEN_MARGIN && g > b + GREEN_MARGIN ->
                        if (luma < 0.30) green++ else bg++ // dark-green pod vs bright leaf
                    else -> bg++
                }
                x += stride
            }
            y += stride
        }

        val safe = sampled.coerceAtLeast(1)
        val podPixels = (red + turning + green + dark).coerceAtLeast(1)
        val stats = ColorStats(
            redPct = pct(red, podPixels),
            turningPct = pct(turning, podPixels),
            greenPct = pct(green, podPixels),
            defectPct = pct(dark, podPixels),
            backgroundPct = pct(bg, safe),
            sampledPixels = sampled
        )

        val ripeness = when {
            stats.redPct >= 85.0 -> Ripeness.RIPE
            stats.redPct >= 60.0 -> Ripeness.OVERTIPE
            stats.turningPct + stats.redPct >= 40.0 -> Ripeness.TURNING
            else -> Ripeness.UNRIPE
        }

        // Hand colour-derived inputs to the deterministic morphology grader. The
        // camera cannot measure length/curvature, so use conservative defaults
        // that let colour + defects decide the grade band.
        val verdict = PodMorphologyGrader.evaluateGrade(
            PodMorphologyGrader.PodMeasurement(
                lengthCm = 11.0,
                curvatureDeg = 12.0,
                redCoveragePct = stats.redPct,
                defectPct = stats.defectPct,
                archimedesSinkRatio = archimedesSinkRatio
            )
        )

        val advice = buildAdvice(ripeness, stats, verdict.finalGrade)

        return QualityReport(
            grade = verdict.finalGrade,
            commercialTier = verdict.commercialTier,
            ripeness = ripeness,
            ripenessLabel = ripenessLabel(ripeness),
            redCoveragePct = round1(stats.redPct),
            defectPct = round1(stats.defectPct),
            morphologicalScore = verdict.morphologicalScore,
            priceMultiplier = verdict.priceMultiplier,
            advice = advice,
            stats = stats
        )
    }

    private fun pct(part: Int, whole: Int): Double = part.toDouble() / whole.coerceAtLeast(1) * 100.0

    private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0

    private fun ripenessLabel(r: Ripeness): String = when (r) {
        Ripeness.UNRIPE -> "Mentah (hijau)"
        Ripeness.TURNING -> "Semburat / setengah matang"
        Ripeness.RIPE -> "Matang penuh (siap panen)"
        Ripeness.OVERTIPE -> "Matang lanjut (segera petik)"
    }

    private fun buildAdvice(ripeness: Ripeness, s: ColorStats, grade: String): String {
        val defectNote = if (s.defectPct > 8.0) {
            " Terdeteksi ${"%.1f".format(s.defectPct)}% bercak gelap — pisahkan buah cacat untuk cegah patek menyebar."
        } else ""
        return when (ripeness) {
            Ripeness.UNRIPE ->
                "Buah masih hijau (${"%.1f".format(s.redPct)}% merah). Tunda panen; optimum panen saat > 85% permukaan merah."
            Ripeness.TURNING ->
                "Setengah matang (${"%.1f".format(s.redPct)}% merah). Panen dalam 2–4 hari untuk pasar segar jarak jauh."
            Ripeness.RIPE ->
                "Matang penuh (${"%.1f".format(s.redPct)}% merah) — siap panen Grade $grade hari ini.$defectNote"
            Ripeness.OVERTIPE ->
                "Sudah sangat matang (${"%.1f".format(s.redPct)}% merah). Segera petik & sortir untuk hindari susut bobot.$defectNote"
        }
    }
}
