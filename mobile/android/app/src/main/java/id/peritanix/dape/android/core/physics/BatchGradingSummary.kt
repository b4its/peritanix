package id.peritanix.dape.android.core.physics

/**
 * TAHAP 5.2 — Batch sortation recap by weight percentage.
 *
 * When a worker photographs a spread of pods on the sorting table, the on-device
 * detector segments each pod. This module aggregates the per-pod grades
 * (A+/A/B/BS) into batch-level **weight percentages** — the number off-takers and
 * the Crypto-QR certificate actually care about ("x% Grade A+, y% BS").
 *
 * Pure and unit-tested; the segmentation/detection that supplies per-pod grades
 * and weights lives on-device.
 */
object BatchGradingSummary {

    /** One graded pod: its commercial grade and measured fresh weight (grams). */
    data class GradedPod(val grade: String, val weightG: Double)

    data class Summary(
        val totalPods: Int,
        val totalWeightG: Double,
        val weightPctByGrade: Map<String, Double>, // A+, A, B, BS → % of total weight
        val countByGrade: Map<String, Int>,
        val premiumPct: Double,   // A+ + A share of weight
        val rejectPct: Double,    // BS share of weight
        val batchVerdict: String  // overall market verdict
    )

    val GRADES = listOf("A+", "A", "B", "BS")

    /**
     * Aggregate a list of graded pods into a batch recap.
     * Grades outside [GRADES] are treated as "BS".
     */
    fun summarize(pods: List<GradedPod>): Summary {
        if (pods.isEmpty()) {
            return Summary(
                0, 0.0, GRADES.associateWith { 0.0 }, GRADES.associateWith { 0 },
                0.0, 0.0, "Belum ada buah tersortir."
            )
        }
        val totalWeight = pods.sumOf { it.weightG.coerceAtLeast(0.0) }.coerceAtLeast(0.001)

        val weightByGrade = GRADES.associateWith { g ->
            pods.filter { normalise(it.grade) == g }.sumOf { it.weightG.coerceAtLeast(0.0) }
        }
        val countByGrade = GRADES.associateWith { g -> pods.count { normalise(it.grade) == g } }
        val pctByGrade = weightByGrade.mapValues { (_, w) ->
            Math.round((w / totalWeight * 100.0) * 10.0) / 10.0
        }

        val premium = (pctByGrade["A+"] ?: 0.0) + (pctByGrade["A"] ?: 0.0)
        val reject = pctByGrade["BS"] ?: 0.0
        val verdict = when {
            premium >= 85.0 -> "Mutu premium: mayoritas layak ekspor/pasar modern (Grade A+/A ${"%.1f".format(premium)}%)."
            premium >= 60.0 -> "Mutu baik: cocok supermarket & grosir (Grade A+/A ${"%.1f".format(premium)}%)."
            reject >= 25.0 -> "Banyak sortiran (BS ${"%.1f".format(reject)}%): periksa patek pasca-panen & perbaiki penanganan."
            else -> "Mutu campuran: sortir ulang untuk memaksimalkan harga Grade A."
        }

        return Summary(
            totalPods = pods.size,
            totalWeightG = Math.round(totalWeight * 10.0) / 10.0,
            weightPctByGrade = pctByGrade,
            countByGrade = countByGrade,
            premiumPct = Math.round(premium * 10.0) / 10.0,
            rejectPct = Math.round(reject * 10.0) / 10.0,
            batchVerdict = verdict
        )
    }

    private fun normalise(grade: String): String {
        val g = grade.trim().uppercase()
        return when (g) {
            "A+" -> "A+"
            "A" -> "A"
            "B" -> "B"
            "BS", "SORTIRAN" -> "BS"
            else -> "BS"
        }
    }
}
