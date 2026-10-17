package id.peritanix.dape.android

import id.peritanix.dape.android.core.hardware.CompassCalibration
import id.peritanix.dape.android.core.hardware.GnssBiomassEstimator
import id.peritanix.dape.android.core.hardware.ProximityAutoTrigger
import id.peritanix.dape.android.core.hardware.ProximityAutoTrigger.Action
import id.peritanix.dape.android.core.hardware.ProximityAutoTrigger.ProximityState
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the three new sensor-matrix hardware modules:
 *  - CompassCalibration (figure-eight coverage + tilt zero-point)
 *  - GnssBiomassEstimator (raw GNSS C/N0 → canopy density)
 *  - ProximityAutoTrigger (0 cm leaf contact → auto trans-illumination)
 *
 * Pure deterministic logic; the sensor listeners live in the Android layer.
 */
class SensorMatrixModulesTest {

    // ---- CompassCalibration -------------------------------------------------

    @Test
    fun testOctantIndex() {
        assertEquals(0, CompassCalibration.octantIndex(1f, 1f, 1f))     // +++
        assertEquals(7, CompassCalibration.octantIndex(-1f, -1f, -1f))  // ---
        assertEquals(1, CompassCalibration.octantIndex(-1f, 1f, 1f))    // -++
        assertEquals(2, CompassCalibration.octantIndex(1f, -1f, 1f))    // +-+
        assertEquals(4, CompassCalibration.octantIndex(1f, 1f, -1f))    // ++-
        assertEquals(-1, CompassCalibration.octantIndex(0f, 0f, 0f))    // zero
    }

    @Test
    fun testFigureEightNotConvergedInitially() {
        val acc = CompassCalibration.Accumulator()
        acc.addSample(CompassCalibration.MagSample(30f, 5f, 10f))
        val st = acc.evaluate()
        assertEquals(1, st.octantsCovered)
        assertFalse(st.isConverged)
    }

    @Test
    fun testFigureEightConvergesAfterFullSweep() {
        val acc = CompassCalibration.Accumulator()
        // Visit all 8 octants with varying radius to give spread.
        val signs = listOf(1f, -1f)
        var i = 0
        for (sx in signs) for (sy in signs) for (sz in signs) {
            repeat(8) {
                val r = 20f + (it * 2) // radius varies 20..34
                acc.addSample(CompassCalibration.MagSample(sx * r, sy * r, sz * r))
                i++
            }
        }
        val st = acc.evaluate()
        assertEquals(8, st.octantsCovered)
        assertTrue("samples=$i spread=${st.spreadRatio}", st.isConverged)
    }

    @Test
    fun testFigureEightReset() {
        val acc = CompassCalibration.Accumulator()
        acc.addSample(CompassCalibration.MagSample(30f, 5f, 10f))
        acc.reset()
        assertEquals(0, acc.evaluate().octantsCovered)
    }

    @Test
    fun testTiltZeroPoint() {
        val zp = CompassCalibration.computeTiltZeroPoint(1.234, -0.876)
        assertEquals(1.23, zp.pitchOffsetDeg, 0.001)
        assertEquals(-0.88, zp.rollOffsetDeg, 0.001)
        // After correction, the rest reading is flat.
        assertTrue(CompassCalibration.isFlatAfterCorrection(1.3, -0.9, zp))
        assertFalse(CompassCalibration.isFlatAfterCorrection(5.0, 0.0, zp))
    }

    // ---- GnssBiomassEstimator ----------------------------------------------

    @Test
    fun testGnssNoValidSignal() {
        val r = GnssBiomassEstimator.estimate(listOf(0f, 2f))
        assertEquals(0, r.satelliteCount)
        assertEquals("Tidak diketahui", r.densityClass)
    }

    @Test
    fun testGnssOpenSkyLowDensity() {
        // C/N0 close to open-sky baseline → low attenuation → open canopy.
        val r = GnssBiomassEstimator.estimate(listOf(40f, 41f, 39f, 40f))
        assertTrue(r.attenuationDb < 3.0)
        assertEquals("Terbuka", r.densityClass)
    }

    @Test
    fun testGnssDenseCanopyHighDensity() {
        // Heavy attenuation under dense foliage → high density → Rimbun.
        val r = GnssBiomassEstimator.estimate(listOf(22f, 20f, 24f, 21f))
        assertTrue(r.attenuationDb > 15.0)
        assertTrue(r.canopyDensityPct > 55.0)
        assertEquals("Rimbun", r.densityClass)
        assertTrue(r.biomassNote.contains("Antraknosa", ignoreCase = true))
    }

    @Test
    fun testGnssMediumDensity() {
        // ~30 dB-Hz → attenuation ~10 → density ~66% -> Rimbun; choose ~33 to hit medium.
        val r = GnssBiomassEstimator.estimate(listOf(34f, 35f, 33f, 34f))
        assertTrue(r.densityClass in setOf("Sedang", "Rimbun"))
        assertTrue(r.canopyDensityPct > 25.0)
    }

    @Test
    fun testGnssFiltersInvalidReadings() {
        val r = GnssBiomassEstimator.estimate(listOf(0f, 70f, 40f, 40f))
        // Only the two ~40 readings are valid.
        assertEquals(2, r.satelliteCount)
    }

    // ---- ProximityAutoTrigger ----------------------------------------------

    @Test
    fun testProximityFarNoAction() {
        val d = ProximityAutoTrigger.Debouncer()
        assertEquals(Action.NONE, d.update(ProximityState.FAR, 1000L).action)
    }

    @Test
    fun testProximityNearArmsThenTriggers() {
        val d = ProximityAutoTrigger.Debouncer()
        // First contact arms.
        assertEquals(Action.ARM, d.update(ProximityState.NEAR, 1000L).action)
        // Too soon → no trigger yet.
        assertEquals(Action.NONE, d.update(ProximityState.NEAR, 1100L).action)
        // After stable contact (>=300 ms) → trigger capture.
        assertEquals(Action.TRIGGER_CAPTURE, d.update(ProximityState.NEAR, 1400L).action)
    }

    @Test
    fun testProximityCooldownPreventsRetrigger() {
        val d = ProximityAutoTrigger.Debouncer()
        d.update(ProximityState.NEAR, 1000L) // ARM
        assertEquals(Action.TRIGGER_CAPTURE, d.update(ProximityState.NEAR, 1400L).action)
        // Immediately after, still NEAR within cooldown → no re-trigger.
        assertEquals(Action.NONE, d.update(ProximityState.NEAR, 1500L).action)
        // After cooldown elapses (>=1500 ms since capture at 1400) → allowed again.
        assertEquals(Action.TRIGGER_CAPTURE, d.update(ProximityState.NEAR, 3000L).action)
    }

    @Test
    fun testProximityRelease() {
        val d = ProximityAutoTrigger.Debouncer()
        d.update(ProximityState.NEAR, 1000L) // ARM
        assertEquals(Action.RELEASE, d.update(ProximityState.FAR, 1200L).action)
    }

    @Test
    fun testProximityUnavailable() {
        val d = ProximityAutoTrigger.Debouncer()
        assertEquals(Action.NONE, d.update(ProximityState.UNAVAILABLE, 1000L).action)
    }
}
