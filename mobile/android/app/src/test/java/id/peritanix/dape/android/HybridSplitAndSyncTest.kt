package id.peritanix.dape.android

import id.peritanix.dape.android.core.ai_edge.HybridSplitComputing
import id.peritanix.dape.android.core.ai_edge.HybridSplitComputing.Strategy
import id.peritanix.dape.android.core.sync.SmartCloudSync
import id.peritanix.dape.android.core.sync.SmartCloudSync.NetworkQuality
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the Online-Maximized sync layer:
 *  - SmartCloudSync network-quality selection (incl. the weak-Edge fix)
 *  - HybridSplitComputing offload decision + ROI geometry (Tahap 0.2)
 */
class HybridSplitAndSyncTest {

    // ---- SmartCloudSync network classification ------------------------------

    @Test
    fun testOfflineSelectsOffline() {
        val s = SmartCloudSync.evaluateSyncStrategy(5, 2, isOnline = false, isWifi = false, isCharging = false)
        assertEquals(NetworkQuality.OFFLINE, s.networkQuality)
    }

    @Test
    fun testWifiSelectsHighSpeed() {
        val s = SmartCloudSync.evaluateSyncStrategy(5, 2, isOnline = true, isWifi = true, isCharging = false)
        assertEquals(NetworkQuality.WIFI_HIGH_SPEED, s.networkQuality)
    }

    @Test
    fun testStableCellularSelectsFullLane() {
        val s = SmartCloudSync.evaluateSyncStrategy(
            5, 2, isOnline = true, isWifi = false, isCharging = false,
            cellularSignalBars = 4, pingLatencyMs = 40
        )
        assertEquals(NetworkQuality.CELLULAR_4G_STABLE, s.networkQuality)
    }

    @Test
    fun testWeakSignalSelectsEdge() {
        // Regression: previously CELLULAR_WEAK_EDGE was never selected. Few bars → Edge.
        val weakBars = SmartCloudSync.evaluateSyncStrategy(
            5, 2, isOnline = true, isWifi = false, isCharging = false,
            cellularSignalBars = 1, pingLatencyMs = 40
        )
        assertEquals(NetworkQuality.CELLULAR_WEAK_EDGE, weakBars.networkQuality)
        assertTrue(weakBars.activeLaneAction.contains("Media ditunda", ignoreCase = true) ||
            weakBars.activeLaneAction.contains("sinyal tipis", ignoreCase = true))

        // High latency also → Edge fast-lane.
        val slow = SmartCloudSync.evaluateSyncStrategy(
            5, 2, isOnline = true, isWifi = false, isCharging = false,
            cellularSignalBars = 4, pingLatencyMs = 400
        )
        assertEquals(NetworkQuality.CELLULAR_WEAK_EDGE, slow.networkQuality)
    }

    @Test
    fun testOtaOnlyOnWifiCharging() {
        val charging = SmartCloudSync.evaluateSyncStrategy(0, 0, isOnline = true, isWifi = true, isCharging = true)
        assertTrue(charging.isOtaUpdateAvailable)
        val notCharging = SmartCloudSync.evaluateSyncStrategy(0, 0, isOnline = true, isWifi = true, isCharging = false)
        assertFalse(notCharging.isOtaUpdateAvailable)
        val cellular = SmartCloudSync.evaluateSyncStrategy(0, 0, isOnline = true, isWifi = false, isCharging = true)
        assertFalse(cellular.isOtaUpdateAvailable)
    }

    @Test
    fun testLaneSizes() {
        val s = SmartCloudSync.evaluateSyncStrategy(10, 3, isOnline = true, isWifi = false, isCharging = false)
        assertEquals(10, s.fastLanePendingCount)
        assertEquals(25.0, s.fastLaneSizeKb, 0.01)    // 10 * 2.5
        assertEquals(3, s.heavyLanePendingCount)
        assertEquals(14.4, s.heavyLaneSizeMb, 0.01)   // 3 * 4.8
    }

    // ---- HybridSplitComputing ----------------------------------------------

    @Test
    fun testOfflineAlwaysOnDevice() {
        val d = HybridSplitComputing.decide(latencyMs = 10, isOnline = false, deviceConstrained = true, modelAvailableOnDevice = false)
        assertEquals(Strategy.ON_DEVICE, d.strategy)
    }

    @Test
    fun testFastLatencyConstrainedOffloads() {
        val d = HybridSplitComputing.decide(latencyMs = 30, isOnline = true, deviceConstrained = true, modelAvailableOnDevice = true)
        assertEquals(Strategy.SPLIT_OFFLOAD, d.strategy)
    }

    @Test
    fun testFastLatencyMissingModelOffloads() {
        val d = HybridSplitComputing.decide(latencyMs = 20, isOnline = true, deviceConstrained = false, modelAvailableOnDevice = false)
        assertEquals(Strategy.SPLIT_OFFLOAD, d.strategy)
    }

    @Test
    fun testFastLatencyCapableDeviceStaysLocal() {
        val d = HybridSplitComputing.decide(latencyMs = 20, isOnline = true, deviceConstrained = false, modelAvailableOnDevice = true)
        assertEquals(Strategy.FAST_LANE_ONLY, d.strategy)
    }

    @Test
    fun testHighLatencyStaysLocal() {
        val d = HybridSplitComputing.decide(latencyMs = 300, isOnline = true, deviceConstrained = true, modelAvailableOnDevice = false)
        assertEquals(Strategy.ON_DEVICE, d.strategy)
    }

    @Test
    fun testCenterRoiGeometry() {
        val roi = HybridSplitComputing.centerRoi(1000, 800, padPct = 10)
        // Central 70% = 700x560, +10% pad on each side ≈ 840x672, clamped.
        assertTrue(roi.x >= 0 && roi.y >= 0)
        assertTrue(roi.x + roi.width <= 1000)
        assertTrue(roi.y + roi.height <= 800)
        assertTrue(roi.width in 700..1000)
        assertTrue(roi.height in 560..800)
    }

    @Test
    fun testRoiBytesSaved() {
        val roi = HybridSplitComputing.centerRoi(1000, 1000)
        val saved = HybridSplitComputing.bytesSavedPct(1000, 1000, roi)
        // Sending ~70-80% ROI area saves 20-30%.
        assertTrue("saved=$saved", saved in 15.0..40.0)
    }

    @Test
    fun testRoiClampedToOnePixelMinimum() {
        val roi = HybridSplitComputing.centerRoi(1, 1)
        assertEquals(1, roi.width)
        assertEquals(1, roi.height)
    }
}
