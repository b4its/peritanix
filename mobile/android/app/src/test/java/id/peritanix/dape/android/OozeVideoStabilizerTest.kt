package id.peritanix.dape.android

import id.peritanix.dape.android.core.physics.OozeVideoStabilizer
import id.peritanix.dape.android.core.physics.OozeVideoStabilizer.GyroSample
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the ooze-video gyro stabilization & quality gate
 * (Tahap 3.1 Langkah D).
 */
class OozeVideoStabilizerTest {

    private fun trace(omegaRad: Float, count: Int, dtMs: Long = 100L): List<GyroSample> =
        (0 until count).map { GyroSample(it * dtMs, omegaRad, omegaRad * 0.5f, omegaRad * 0.2f) }

    @Test
    fun testTooFewSamplesNotUsable() {
        val r = OozeVideoStabilizer.analyze(emptyList())
        assertFalse(r.isUsable)
        assertEquals(0.0, r.angularShakeDeg, 0.0)
    }

    @Test
    fun testSteadyClipIsUsable() {
        // Tiny angular velocity (0.005 rad/s) over 10 s → very steady.
        val r = OozeVideoStabilizer.analyze(trace(0.005f, count = 101, dtMs = 100L))
        assertEquals(10_000L, r.durationMs)
        assertTrue("shake should be small: ${r.angularShakeDeg}", r.angularShakeDeg < 4.0)
        assertTrue(r.isUsable)
        assertTrue(r.stabilityPct > 70.0)
        assertTrue(r.opticalFlowConfidence > 0.7)
    }

    @Test
    fun testVeryShakyClipNotUsable() {
        // Large angular velocity (1.0 rad/s) over 10 s → huge cumulative shake.
        val r = OozeVideoStabilizer.analyze(trace(1.0f, count = 101, dtMs = 100L))
        assertFalse(r.isUsable)
        assertTrue(r.angularShakeDeg > OozeVideoStabilizer.MAX_SHAKE_DEG)
        assertEquals(1.0, r.shakeScore, 0.01)
        assertEquals(22, r.recommendedCropPct) // max crop (clamped)
        assertEquals(0.2, r.opticalFlowConfidence, 0.01)
    }

    @Test
    fun testShortClipNotUsable() {
        // Even if steady, a <8 s clip is rejected.
        val r = OozeVideoStabilizer.analyze(trace(0.005f, count = 51, dtMs = 100L)) // 5 s
        assertFalse(r.isUsable)
        assertTrue(r.advisory.contains("pendek", ignoreCase = true))
    }

    @Test
    fun testConfidenceDecreasesWithShake() {
        val steady = OozeVideoStabilizer.analyze(trace(0.02f, count = 101))
        val shaky = OozeVideoStabilizer.analyze(trace(0.2f, count = 101))
        assertTrue("steady confidence > shaky", steady.opticalFlowConfidence > shaky.opticalFlowConfidence)
        assertTrue("steady stability > shaky", steady.stabilityPct > shaky.stabilityPct)
    }

    @Test
    fun testCropIncreasesWithShake() {
        val steady = OozeVideoStabilizer.analyze(trace(0.01f, count = 101))
        val shaky = OozeVideoStabilizer.analyze(trace(0.15f, count = 101))
        assertTrue(shaky.recommendedCropPct >= steady.recommendedCropPct)
        assertTrue(steady.recommendedCropPct in 8..22)
    }

    @Test
    fun testBoundsClamped() {
        val r = OozeVideoStabilizer.analyze(trace(0.5f, count = 101))
        assertTrue(r.stabilityPct in 0.0..100.0)
        assertTrue(r.opticalFlowConfidence in 0.2..1.0)
        assertTrue(r.recommendedCropPct in 8..22)
    }
}
