package id.peritanix.dape.android

import id.peritanix.dape.android.core.ai_edge.EdgeInferenceRunner
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AndroidModelInferencePipelineTest {

    private val runner = EdgeInferenceRunner(File("/tmp/models"))

    @Test
    fun testPredictFertilizerMixVegetativePhase() {
        // Vegetative: HST <= 35, pH 6.5, CEC 15 meq
        val mix = runner.predictFertilizerMix(
            ph = 6.5f,
            cecMeq = 15.0f,
            hst = 25,
            targetYieldTonPerHa = 15.0f
        )
        // base NPK = 1.5 + 0.5 = 2.0; KNO3 = 0.5; Calcium = 1.0 (pH >= 5.5, not 36..60)
        assertEquals(2.0f, mix.npk16_16_16GPerL, 0.05f)
        assertEquals(0.5f, mix.kno3GPerL, 0.05f)
        assertEquals(1.0f, mix.calciumGPerL, 0.05f)
        assertTrue(mix.executionTimeMs >= 1L)
    }

    @Test
    fun testPredictFertilizerMixFloweringAnthesisPhase() {
        // Anthesis / Flowering: HST = 45 (between 36 and 60), pH 6.0, CEC 18 meq
        val mix = runner.predictFertilizerMix(
            ph = 6.0f,
            cecMeq = 18.0f,
            hst = 45,
            targetYieldTonPerHa = 15.0f
        )
        // Calcium boosted to 2.5 g/L during anthesis
        assertEquals(2.5f, mix.calciumGPerL, 0.05f)
        // cecFactor = 18 / 15 = 1.2
        // base NPK = 2.0 + 0.7 = 2.7 * 1.2 = 3.24
        assertEquals(3.24f, mix.npk16_16_16GPerL, 0.05f)
        // KNO3 = 1.2 * 1.2 = 1.44
        assertEquals(1.44f, mix.kno3GPerL, 0.05f)
    }

    @Test
    fun testPredictFertilizerMixAcidicSoil() {
        // Acidic soil: pH 5.0 (< 5.5), HST 75 (Fruiting)
        val mix = runner.predictFertilizerMix(
            ph = 5.0f,
            cecMeq = 12.0f,
            hst = 75,
            targetYieldTonPerHa = 15.0f
        )
        // Calcium for acidic soil = 2.0 g/L
        assertEquals(2.0f, mix.calciumGPerL, 0.05f)
        assertTrue(mix.kno3GPerL >= 2.0f) // Fruiting phase needs high potassium
    }

    @Test
    fun testSegmentLesionClassesStandard() {
        // 70% healthy, 20% chlorotic, 10% necrotic, 0% background
        val seg = runner.segmentLesionClasses(
            healthy = 0.70f,
            chlorotic = 0.20f,
            necrotic = 0.10f,
            background = 0.0f
        )
        // Severity = (0.10 + 0.5 * 0.20) / 1.0 * 100 = 20.0%
        assertEquals(20.0f, seg.calculatedSeverityPct, 0.1f)
        assertEquals(0.70f, seg.healthyFrac, 0.01f)
        assertEquals(0.20f, seg.chloroticFrac, 0.01f)
        assertEquals(0.10f, seg.necroticFrac, 0.01f)
    }

    @Test
    fun testSegmentLesionClassesWithBackgroundMask() {
        // 50% healthy, 20% chlorotic, 10% necrotic, 20% background
        // leafArea = 0.8
        // weightedNecrosis = 0.10 + 0.5 * 0.20 = 0.20
        // Severity = 0.20 / 0.80 * 100 = 25.0%
        val seg = runner.segmentLesionClasses(
            healthy = 0.50f,
            chlorotic = 0.20f,
            necrotic = 0.10f,
            background = 0.20f
        )
        assertEquals(25.0f, seg.calculatedSeverityPct, 0.1f)
    }

    @Test
    fun testYieldEstimationFruitCount() {
        val yield = runner.estimateYieldAndFruitCount(
            greenCount = 30,
            turningCount = 15,
            ripeCount = 10,
            plantDensityPerHa = 18000
        )
        assertEquals(55, yield.totalFruitCount)
        assertEquals(30, yield.greenFruitCount)
        assertEquals(15, yield.turningFruitCount)
        assertEquals(10, yield.ripeRedCount)
        assertTrue(yield.projectedYieldKgPerPlant > 0.0)
        assertTrue(yield.projectedYieldTonPerHa > 0.0)
    }

    @Test
    fun testOptimalHarvestWindowAndPhysiologicalIndices() {
        // Optimal harvest ready: 45 ripe, 5 turning, 0 green -> 90% ripe
        val ready = runner.estimateYieldAndFruitCount(
            greenCount = 0,
            turningCount = 5,
            ripeCount = 45
        )
        assertEquals(90.0, ready.harvestReadinessPct, 0.1)
        assertEquals(0, ready.optimalHarvestWindowDays)
        assertEquals(92.5, ready.capsanthinGlossIndex, 0.1)
        assertEquals(21.4, ready.estimatedDryMatterPct, 0.1)
        assertTrue(ready.recommendedHarvestDayAdvisory.contains("KEMATANGAN FISIOLOGIS OPTIMAL"))

        // Turning dominant: 10 green, 25 turning, 15 ripe -> 50 fruits, 30% ripe
        val turning = runner.estimateYieldAndFruitCount(
            greenCount = 10,
            turningCount = 25,
            ripeCount = 15
        )
        assertEquals(30.0, turning.harvestReadinessPct, 0.1)
        assertEquals(7, turning.optimalHarvestWindowDays)
        assertTrue(turning.recommendedHarvestDayAdvisory.contains("FASE PEMATANGAN AWAL"))
    }

    @Test
    fun testActiveLeafTransIllumination() {
        // Latent fungal infection: R=130, G=110, B=90 (green < red -> high attenuation)
        val infected = runner.evaluateTransIllumination(130, 110, 90)
        assertTrue(infected.isLatentFungalDetected)
        assertTrue(infected.greenAttenuationRatio <= 0.85)

        // Healthy leaf: R=80, G=170, B=60 (green >> red)
        val healthy = runner.evaluateTransIllumination(80, 170, 60)
        assertFalse(healthy.isLatentFungalDetected)
        assertTrue(healthy.greenAttenuationRatio > 0.90)
    }
}
