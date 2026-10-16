package id.peritanix.dape.android.core.physics

/**
 * FR-4.2 Automated Chili Pod Morphology & Comprehensive Post-Harvest Grading.
 *
 * Evaluates individual or batch chili pod visual morphology on a contrasting background:
 *   1. Pod length (cm)
 *   2. Curvature angle / straightness (degrees)
 *   3. Red skin color coverage percentage (%)
 *   4. Surface defect / anthracnose necrosis percentage (%)
 *
 * Combines morphological parameters with Archimedes dry-matter density (sink ratio)
 * to output holistic commercial quality tiers: Grade A+, Grade A, Grade B, or Grade BS.
 */
object PodMorphologyGrader {

    data class PodMeasurement(
        val lengthCm: Double,
        val curvatureDeg: Double,
        val redCoveragePct: Double,
        val defectPct: Double,
        val archimedesSinkRatio: Double
    )

    data class GradeVerdict(
        val finalGrade: String, // "A+", "A", "B", "BS"
        val commercialTier: String,
        val morphologicalScore: Double, // 0 to 100
        val isExportQuality: Boolean,
        val priceMultiplier: Double,
        val remarks: String
    )

    /**
     * Grades chili harvest batch based on morphology and Archimedes density.
     */
    fun evaluateGrade(measurement: PodMeasurement): GradeVerdict {
        val len = measurement.lengthCm
        val curv = measurement.curvatureDeg
        val red = measurement.redCoveragePct
        val defect = measurement.defectPct
        val sink = measurement.archimedesSinkRatio

        // Morphological score out of 100
        val lenScore = (len / 14.0 * 25.0).coerceIn(0.0, 25.0)
        val curvScore = ((45.0 - curv) / 45.0 * 25.0).coerceIn(0.0, 25.0)
        val redScore = (red / 100.0 * 25.0).coerceIn(0.0, 25.0)
        val densityScore = (sink * 25.0).coerceIn(0.0, 25.0)
        val defectPenalty = defect * 2.0

        val totalScore = Math.max(0.0, Math.min(100.0, lenScore + curvScore + redScore + densityScore - defectPenalty))

        return when {
            len >= 12.0 && curv <= 15.0 && red >= 90.0 && defect <= 2.0 && sink >= 0.80 -> {
                GradeVerdict(
                    finalGrade = "A+",
                    commercialTier = "Export Prime Quality",
                    morphologicalScore = Math.round(totalScore * 10.0) / 10.0,
                    isExportQuality = true,
                    priceMultiplier = 1.35,
                    remarks = "Kualitas prima: Dinding buah tebal, lurus sempurna, warna merah merata, bebas cacat fisik."
                )
            }
            len >= 10.0 && curv <= 25.0 && red >= 75.0 && defect <= 5.0 && sink >= 0.65 -> {
                GradeVerdict(
                    finalGrade = "A",
                    commercialTier = "Supermarket Premium",
                    morphologicalScore = Math.round(totalScore * 10.0) / 10.0,
                    isExportQuality = false,
                    priceMultiplier = 1.15,
                    remarks = "Mutu super: Ukuran panjang seragam, kelengkungan wajar, daya simpan pasca-panen tinggi."
                )
            }
            len >= 8.0 && curv <= 45.0 && red >= 50.0 && defect <= 12.0 && sink >= 0.45 -> {
                GradeVerdict(
                    finalGrade = "B",
                    commercialTier = "Pasar Tradisional / Grosir",
                    morphologicalScore = Math.round(totalScore * 10.0) / 10.0,
                    isExportQuality = false,
                    priceMultiplier = 0.90,
                    remarks = "Mutu standar: Terdapat kelengkungan atau variasi kematangan; cocok untuk konsumsi harian lokal."
                )
            }
            else -> {
                GradeVerdict(
                    finalGrade = "BS",
                    commercialTier = "Industri Olahan / Saus Sambal",
                    morphologicalScore = Math.round(totalScore * 10.0) / 10.0,
                    isExportQuality = false,
                    priceMultiplier = 0.60,
                    remarks = "Mutu sortiran: Ukuran pendek, cacat kulit, atau densitas rendah. Salurkan ke pabrik pasta/saus."
                )
            }
        }
    }
}
