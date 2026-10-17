package id.peritanix.dape.android

import id.peritanix.dape.android.core.hardware.AcousticAnemometer
import id.peritanix.dape.android.core.hardware.BoilingSoundVerifier
import id.peritanix.dape.android.core.hardware.HardwarePerformanceManager
import id.peritanix.dape.android.core.hardware.SunlightUiAdapter
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the pure (hardware-free) formulas of the sensor-fusion modules
 * introduced by the built-in sensor exploitation checklist:
 *  - AcousticAnemometer.windSpeedFromRms / isDriftHazard (spray-drift safety)
 *  - BoilingSoundVerifier.localBoilingPointC (barometric boiling point)
 *  - HardwarePerformanceManager (tiering, thermal throttle, cloud offload policy)
 *  - SunlightUiAdapter.evaluateSunlightTheme (high-contrast sunlight UI)
 *
 * The Android-framework code paths (AudioRecord, Camera2, sensors) are exercised
 * on-device; here we lock in the deterministic math and decision thresholds.
 */
class SensorFusionFormulaTest {

    // ---- Acoustic anemometer ------------------------------------------------

    @Test
    fun testWindSpeedMonotonicInRms() {
        // Louder low-frequency buffeting => faster wind.
        val calm = AcousticAnemometer.windSpeedFromRms(50.0)
        val breezy = AcousticAnemometer.windSpeedFromRms(200.0)
        val gusty = AcousticAnemometer.windSpeedFromRms(1000.0)
        assertTrue("breezier > calm", breezy > calm)
        assertTrue("gustier > breezy", gusty > breezy)
    }

    @Test
    fun testWindSpeedClamped() {
        // Extremely low RMS → clamped to floor 2.0 km/h.
        assertEquals(2.0, AcousticAnemometer.windSpeedFromRms(1.0), 0.001)
        // Extremely high RMS → clamped to ceiling 35.0 km/h.
        assertEquals(35.0, AcousticAnemometer.windSpeedFromRms(1_000_000.0), 0.001)
    }

    @Test
    fun testDriftHazardThreshold() {
        assertFalse(AcousticAnemometer.isDriftHazard(12.0))
        assertFalse(AcousticAnemometer.isDriftHazard(11.9))
        assertTrue(AcousticAnemometer.isDriftHazard(12.1))
        assertTrue(AcousticAnemometer.isDriftHazard(20.0))
        // Threshold constant is the documented 12 km/h.
        assertEquals(12.0, AcousticAnemometer.DRIFT_HAZARD_THRESHOLD_KMH, 0.0)
    }

    // ---- Boiling sound verifier --------------------------------------------

    @Test
    fun testLocalBoilingPointSeaLevel() {
        assertEquals(100.0, BoilingSoundVerifier.localBoilingPointC(0.0), 0.001)
    }

    @Test
    fun testLocalBoilingPointAtAltitude() {
        // 100 - 0.0033*850 = 97.195 → 97.2
        assertEquals(97.2, BoilingSoundVerifier.localBoilingPointC(850.0), 0.001)
        // 100 - 0.0033*1000 = 96.7
        assertEquals(96.7, BoilingSoundVerifier.localBoilingPointC(1000.0), 0.001)
        // Higher altitude => lower boiling point (monotone decreasing).
        assertTrue(BoilingSoundVerifier.localBoilingPointC(2000.0) < BoilingSoundVerifier.localBoilingPointC(0.0))
    }

    // ---- Hardware performance manager --------------------------------------

    @Test
    fun testDeviceTiering() {
        assertEquals(HardwarePerformanceManager.DeviceTier.HIGH_END, HardwarePerformanceManager.tierForRam(6144))
        assertEquals(HardwarePerformanceManager.DeviceTier.HIGH_END, HardwarePerformanceManager.tierForRam(5500))
        assertEquals(HardwarePerformanceManager.DeviceTier.MID_TIER, HardwarePerformanceManager.tierForRam(4000))
        assertEquals(HardwarePerformanceManager.DeviceTier.MID_TIER, HardwarePerformanceManager.tierForRam(3500))
        assertEquals(HardwarePerformanceManager.DeviceTier.LOW_END_KENTANG, HardwarePerformanceManager.tierForRam(2048))
    }

    @Test
    fun testDeviceTierQuantization() {
        assertEquals("FP16", HardwarePerformanceManager.DeviceTier.HIGH_END.recommendedQuantization)
        assertEquals("INT8", HardwarePerformanceManager.DeviceTier.MID_TIER.recommendedQuantization)
        assertEquals("INT8", HardwarePerformanceManager.DeviceTier.LOW_END_KENTANG.recommendedQuantization)
        assertTrue("low-end uses 2 threads", HardwarePerformanceManager.DeviceTier.LOW_END_KENTANG.threadCount == 2)
    }

    @Test
    fun testThermalThrottleByTemperature() {
        assertFalse(HardwarePerformanceManager.isThermallyThrottled(38.0f, "NORMAL"))
        assertFalse(HardwarePerformanceManager.isThermallyThrottled(42.9f, "LIGHT"))
        assertTrue(">=43C throttles", HardwarePerformanceManager.isThermallyThrottled(43.0f, "NORMAL"))
        assertTrue(HardwarePerformanceManager.isThermallyThrottled(45.0f, "NORMAL"))
    }

    @Test
    fun testThermalThrottleByStatus() {
        assertFalse(HardwarePerformanceManager.isThermallyThrottled(30.0f, "NONE"))
        assertFalse(HardwarePerformanceManager.isThermallyThrottled(30.0f, "LIGHT"))
        assertTrue(HardwarePerformanceManager.isThermallyThrottled(30.0f, "MODERATE"))
        assertTrue(HardwarePerformanceManager.isThermallyThrottled(30.0f, "SEVERE"))
        assertTrue(HardwarePerformanceManager.isThermallyThrottled(30.0f, "CRITICAL"))
        assertTrue(HardwarePerformanceManager.isThermallyThrottled(30.0f, "EMERGENCY"))
    }

    @Test
    fun testCloudOffloadPolicy() {
        // Offline → never offload.
        assertFalse(HardwarePerformanceManager.shouldOffload(20, isOnline = false, lowMemory = true, throttled = true))
        // Online + low latency + low-end → offload.
        assertTrue(HardwarePerformanceManager.shouldOffload(20, isOnline = true, lowMemory = true, throttled = false))
        // Online + low latency + throttled → offload even on a strong device.
        assertTrue(HardwarePerformanceManager.shouldOffload(20, isOnline = true, lowMemory = false, throttled = true))
        // High latency → do NOT offload (would be slower than on-device).
        assertFalse(HardwarePerformanceManager.shouldOffload(150, isOnline = true, lowMemory = true, throttled = true))
        // Strong + cool → keep on device.
        assertFalse(HardwarePerformanceManager.shouldOffload(20, isOnline = true, lowMemory = false, throttled = false))
    }

    // ---- Sunlight UI adapter -----------------------------------------------

    @Test
    fun testSunlightThemeBelowThreshold() {
        val cfg = SunlightUiAdapter.evaluateSunlightTheme(1000f)
        assertFalse(cfg.isExtremeSunlightActive)
        assertTrue(cfg.statusMessage.contains("normal", ignoreCase = true))
    }

    @Test
    fun testSunlightThemeAtThreshold() {
        // >= 25,000 Lux activates the extreme sunlight theme.
        val cfg = SunlightUiAdapter.evaluateSunlightTheme(25000f)
        assertTrue(cfg.isExtremeSunlightActive)
        assertTrue(cfg.statusMessage.contains("Terik", ignoreCase = true))
    }

    @Test
    fun testSunlightThresholdConstant() {
        assertEquals(25000.0f, SunlightUiAdapter.SUNLIGHT_LUX_THRESHOLD, 0.0f)
    }
}
