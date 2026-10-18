package id.peritanix.dape.android.core.physics

import kotlin.math.cos
import kotlin.math.sin

/**
 * Tahap 4.3: Offline Ooze Test & Local Bayesian Spatial Infection Isolation Engine.
 *
 * Fuses multi-modal field evidence to differentiate between:
 *   1. Ralstonia solanacearum (Bacterial Wilt - Layu Bakteri)
 *   2. Fusarium oxysporum (Fungal Wilt - Layu Jamur)
 *
 * Implements Bayes' Rule with local spatial factors:
 *   P(Bacterial | V, TWI, S, Wind) = [P(V, TWI, S, Wind | Bacterial) * P(Bacterial)] / P(Evidence)
 *
 * Calculates a precise spatial quarantine perimeter (Bed #, Hole # range)
 * to cordon off and stop disease spreading across the chili block without internet.
 */
object BayesianSpatialInfectionEngine {

    data class MultiModalEvidence(
        val isWilting: Boolean,
        val leavesRemainGreen: Boolean, // Ralstonia plants wilt while still green; Fusarium turns yellow
        val oozeStreamingDetected: Boolean, // True if white bacterial streams seen in 10s water test
        val oozeFlowVelocityPxS: Double = 0.0,
        val twiScore: Double, // Topographic Wetness Index
        val soilMoisturePct: Double,
        val prevailingWindBearingDeg: Double,
        val infectedBedIndex: Int,
        val infectedHoleIndex: Int,
        val totalBedsInBlock: Int = 12
    )

    data class QuarantinePerimeter(
        val bacterialWiltProbability: Double, // 0.0 .. 1.0 (Bayes posterior)
        val fungalWiltProbability: Double,
        val diagnosedPathogen: String,
        val urgencyLevel: String, // "KRITIS - SEGERA ISOLASI", "WASPADA TINGGI", "PEMANTAUAN"
        val quarantinedBedStart: Int,
        val quarantinedBedEnd: Int,
        val quarantinedHoleStart: Int,
        val quarantinedHoleEnd: Int,
        val downwindThreatBed: Int?,
        val isolationTrenchAction: String,
        val biologicalDrenchingRecipe: String,
        val agronomicSummary: String
    )

    /**
     * Executes deterministic local Bayesian update.
     */
    fun evaluatePathogenAndPerimeter(evidence: MultiModalEvidence): QuarantinePerimeter {
        if (!evidence.isWilting) {
            return QuarantinePerimeter(
                bacterialWiltProbability = 0.05,
                fungalWiltProbability = 0.05,
                diagnosedPathogen = "SEHAT / TIDAK ADA GEJALA LAYU",
                urgencyLevel = "NORMAL",
                quarantinedBedStart = evidence.infectedBedIndex,
                quarantinedBedEnd = evidence.infectedBedIndex,
                quarantinedHoleStart = evidence.infectedHoleIndex,
                quarantinedHoleEnd = evidence.infectedHoleIndex,
                downwindThreatBed = null,
                isolationTrenchAction = "Tidak diperlukan karantina.",
                biologicalDrenchingRecipe = "Pertahankan aplikasi rutin Trichoderma spp. 2 minggu sekali.",
                agronomicSummary = "Tanaman dalam kondisi turgor normal. Tidak terdeteksi infeksi vaskular."
            )
        }

        // Base priors in tropical chili monoculture
        val priorBacterial = 0.35
        val priorFungal = 0.65

        // Likelihood 1: Ooze streaming optical test (10s cup test)
        val (lOozeBact, lOozeFung) = if (evidence.oozeStreamingDetected) {
            Pair(0.96, 0.04)
        } else {
            Pair(0.08, 0.92)
        }

        // Likelihood 2: Foliar phenotype (green wilting vs yellow chlorosis)
        val (lPhenoBact, lPhenoFung) = if (evidence.leavesRemainGreen) {
            Pair(0.85, 0.25)
        } else {
            Pair(0.20, 0.80)
        }

        // Likelihood 3: Soil moisture & TWI (high moisture stagnation favors bacterial multiplication)
        val (lTwiBact, lTwiFung) = if (evidence.twiScore >= 8.0 || evidence.soilMoisturePct > 70.0) {
            Pair(0.88, 0.45)
        } else {
            Pair(0.35, 0.70)
        }

        // Total likelihood product P(E | H)
        val jointBacterial = priorBacterial * lOozeBact * lPhenoBact * lTwiBact
        val jointFungal = priorFungal * lOozeFung * lPhenoFung * lTwiFung
        val evidenceTotal = (jointBacterial + jointFungal).coerceAtLeast(1e-6)

        val postBacterial = Math.round((jointBacterial / evidenceTotal) * 1000.0) / 1000.0
        val postFungal = Math.round((1.0 - postBacterial) * 1000.0) / 1000.0

        val isBacterial = postBacterial >= 0.50
        val diagnosedName = if (isBacterial) {
            "Ralstonia solanacearum (Layu Bakteri Vaskular)"
        } else {
            "Fusarium oxysporum (Layu Jamur Pembuluh)"
        }

        val urgency = when {
            postBacterial >= 0.80 -> "KRITIS - SEGERA ISOLASI 24 JAM"
            postBacterial >= 0.50 -> "WASPADA TINGGI - ISOLASI BEDENG"
            else -> "PENANGANAN FUSARIUM (FUNGISIDA HAYATI)"
        }

        // Spatial Quarantine Buffer: Bed index ± 1, Holes ± 4 along bed
        val bedStart = (evidence.infectedBedIndex - 1).coerceAtLeast(1)
        val bedEnd = (evidence.infectedBedIndex + 1).coerceAtMost(evidence.totalBedsInBlock)
        val holeStart = (evidence.infectedHoleIndex - 4).coerceAtLeast(1)
        val holeEnd = evidence.infectedHoleIndex + 4

        // Wind vector direction for secondary downwind bed threat
        val windNorm = (evidence.prevailingWindBearingDeg % 360.0 + 360.0) % 360.0
        val downwindBed = if (windNorm in 45.0..135.0 && evidence.infectedBedIndex < evidence.totalBedsInBlock) {
            evidence.infectedBedIndex + 1
        } else if (windNorm in 225.0..315.0 && evidence.infectedBedIndex > 1) {
            evidence.infectedBedIndex - 1
        } else {
            null
        }

        val isolationAction = if (isBacterial) {
            "Gali parit pembatas sedalam 30 cm di antara Bedengan #${evidence.infectedBedIndex} dan bedeng tetangga. Cabut pohon terinfeksi bersama tanah perakarannya dan bakar di luar area kebun. TABURKAN KAPUR DOLOMIT 150g/LUBANG untuk menaikkan pH tanah lokal."
        } else {
            "Turunkan debit air irigasi pada zona TWI tinggi (${"%.1f".format(evidence.twiScore)}). Buka guludan bedeng agar drainase lancar dan spora jamur Fusarium tidak terbawa air genangan."
        }

        val drenchRecipe = if (isBacterial) {
            "Kocor bakterisida hayati Pseudomonas fluorescens + Bacillus subtilis (dosis 5 ml/liter air, 250 ml/lubang) pada 10 tanaman di sekeliling lubang infeksi."
        } else {
            "Kocor agen antagonis Trichoderma harzianum (dosis 10 gram/liter air, 250 ml/lubang) pada Bedengan #$bedStart s/d #$bedEnd untuk melindungi akar tanaman sehat."
        }

        val summary = if (isBacterial) {
            "Bayesian Posterior Layu Bakteri: ${"%.1f".format(postBacterial * 100)}%. " +
                    "Infeksi dipicu TWI ${"%.1f".format(evidence.twiScore)} & aliran suspensi bakteri positif. Karantina perimeter Bedengan #$bedStart - #$bedEnd (Lubang #$holeStart - #$holeEnd)."
        } else {
            "Bayesian Posterior Layu Jamur: ${"%.1f".format(postFungal * 100)}%. " +
                    "Tidak ada aliran ooze bakteri. Isolasi area perakaran Bedengan #$bedStart - #$bedEnd dengan Trichoderma."
        }

        return QuarantinePerimeter(
            bacterialWiltProbability = postBacterial,
            fungalWiltProbability = postFungal,
            diagnosedPathogen = diagnosedName,
            urgencyLevel = urgency,
            quarantinedBedStart = bedStart,
            quarantinedBedEnd = bedEnd,
            quarantinedHoleStart = holeStart,
            quarantinedHoleEnd = holeEnd,
            downwindThreatBed = downwindBed,
            isolationTrenchAction = isolationAction,
            biologicalDrenchingRecipe = drenchRecipe,
            agronomicSummary = summary
        )
    }
}
