package id.peritanix.dape.android

import id.peritanix.dape.android.core.hardware.MotionBlurGuard
import id.peritanix.dape.android.core.hardware.MotionBlurGuard.LockState
import id.peritanix.dape.android.core.physics.MicroclimateEwsEvaluator
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for two Tahap 4 / Tahap 2 deliverables:
 *  - MotionBlurGuard (Stokes zero-jitter shutter + pitch/roll angle lock)
 *  - MicroclimateEwsEvaluator.recommendIrrigation (menit/bedengan schedule)
 */
class MotionBlurAndIrrigationTest {

    // ---- MotionBlurGuard ----------------------------------------------------

    @Test
    fun testAlignedAndSteadyAllowsShutter() {
        // Perfectly perpendicular (0° off-axis) + very steady gyro (tiny spread).
        val d = MotionBlurGuard.evaluate(offAxisTiltDeg = 0.0, recentGyroMagnitudes = listOf(0.01f, 0.011f, 0.009f, 0.01f))
        assertTrue(d.canFire)
        assertEquals(LockState.ALIGNED_AND_STEADY, d.state)
    }

    @Test
    fun testMisalignedBlocksShutter() {
        val d = MotionBlurGuard.evaluate(offAxisTiltDeg = 3.0, recentGyroMagnitudes = listOf(0.01f, 0.01f, 0.01f))
        assertFalse(d.canFire)
        assertEquals(LockState.MISALIGNED, d.state)
    }

    @Test
    fun testShakyBlocksShutter() {
        // Aligned but high gyro variance → shaky.
        val d = MotionBlurGuard.evaluate(offAxisTiltDeg = 0.0, recentGyroMagnitudes = listOf(0.0f, 0.5f, 0.0f, 0.6f, 0.1f))
        assertFalse(d.canFire)
        assertEquals(LockState.SHAKY, d.state)
    }

    @Test
    fun testToleranceBoundary() {
        // Exactly at ±0.5° tolerance → allowed (aligned).
        val atEdge = MotionBlurGuard.evaluate(0.5, listOf(0.01f, 0.01f, 0.01f))
        assertTrue(atEdge.canFire)
        // Just beyond → blocked.
        val beyond = MotionBlurGuard.evaluate(0.6, listOf(0.01f, 0.01f, 0.01f))
        assertFalse(beyond.canFire)
    }

    @Test
    fun testGyroSigmaNeedsTwoSamples() {
        assertEquals(Double.MAX_VALUE, MotionBlurGuard.gyroSigma(listOf(0.1f)), 0.0)
        // Constant series → sigma 0.
        assertEquals(0.0, MotionBlurGuard.gyroSigma(listOf(0.1f, 0.1f, 0.1f)), 1e-9)
    }

    @Test
    fun testAngleErrorDeg() {
        assertEquals(0.0, MotionBlurGuard.angleErrorDeg(0.0, 0.0), 0.001)
        // 3-4-5 style: pitch 3°, roll 4° → effective 5°.
        assertEquals(5.0, MotionBlurGuard.angleErrorDeg(3.0, 4.0), 0.2)
    }

    // ---- Irrigation recommendation -----------------------------------------

    @Test
    fun testIrrigationHighRiskShortensWatering() {
        val lowRisk = MicroclimateEwsEvaluator.recommendIrrigation(riskScore = 0.1, soilMoisturePct = 50.0, baseMinutes = 20.0)
        val highRisk = MicroclimateEwsEvaluator.recommendIrrigation(riskScore = 0.9, soilMoisturePct = 50.0, baseMinutes = 20.0)
        assertTrue("high risk should water less", highRisk.minutesPerBed < lowRisk.minutesPerBed)
        assertEquals(24, highRisk.intervalHours) // high risk → daily
        assertTrue(highRisk.rationale.contains("jamur", ignoreCase = true))
    }

    @Test
    fun testIrrigationDrySoilWatersMore() {
        val dry = MicroclimateEwsEvaluator.recommendIrrigation(0.2, soilMoisturePct = 25.0, baseMinutes = 20.0)
        val wet = MicroclimateEwsEvaluator.recommendIrrigation(0.2, soilMoisturePct = 70.0, baseMinutes = 20.0)
        assertTrue("dry soil > wet soil minutes", dry.minutesPerBed > wet.minutesPerBed)
    }

    @Test
    fun testIrrigationMinutesClamped() {
        val extremeDry = MicroclimateEwsEvaluator.recommendIrrigation(0.0, soilMoisturePct = 0.0, baseMinutes = 100.0)
        assertTrue(extremeDry.minutesPerBed <= 45.0)
        val extremeWet = MicroclimateEwsEvaluator.recommendIrrigation(1.0, soilMoisturePct = 90.0, baseMinutes = 1.0)
        assertTrue(extremeWet.minutesPerBed >= 5.0)
    }

    @Test
    fun testIrrigationNormalInterval() {
        val normal = MicroclimateEwsEvaluator.recommendIrrigation(0.2, soilMoisturePct = 55.0, baseMinutes = 20.0)
        assertEquals(48, normal.intervalHours)
    }
}
