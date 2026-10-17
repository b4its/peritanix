package id.peritanix.dape.android

import id.peritanix.dape.android.core.hardware.BarometricBedHeight
import id.peritanix.dape.android.core.hardware.BarometricStormPredictor
import id.peritanix.dape.android.core.hardware.CanopyCeptometer
import id.peritanix.dape.android.core.hardware.PedestrianDeadReckoning
import id.peritanix.dape.android.core.hardware.RidgeSunOrientationCalculator
import id.peritanix.dape.android.core.hardware.SpatialContextCollector
import id.peritanix.dape.android.core.sync.P2pFieldMeshSync
import id.peritanix.dape.android.core.sync.SmartCloudSync
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DAPE Built-in Smartphone Sensor Exploitation & Agro-Physics modules.
 */
class HardwareSensorModulesTest {

    @Test
    fun testRidgeSunOrientationCalculator_NorthSouthVsEastWest() {
        // Bedengan membujur Utara-Selatan (0° atau 180°)
        val nsAdvisory = RidgeSunOrientationCalculator.evaluateBedOrientation(0.0)
        assertTrue("Utara-Selatan harus ideal untuk penyinaran seimbang", nsAdvisory.bedOrientationType.contains("Utara-Selatan"))
        assertTrue("Pagi hari harus cepat kering dari embun", nsAdvisory.morningSunExposurePct > 70)

        // Bedengan membujur Timur-Barat (90° atau 270°)
        val ewAdvisory = RidgeSunOrientationCalculator.evaluateBedOrientation(90.0)
        assertTrue("Timur-Barat harus tergolong Sub-Optimal", ewAdvisory.bedOrientationType.contains("Timur-Barat"))
        assertTrue("Perlu saran jarak tanam atau pemangkasan wiwilan", ewAdvisory.recommendation.contains("wiwilan") || ewAdvisory.dewDryingSpeedRating.contains("Embun"))
    }

    @Test
    fun testBarometricBedHeight_ElevationAndBoilingPoint() {
        // Sea level boiling point (altitude 0 m) -> ~100.0 °C
        val seaLevelBp = BarometricBedHeight.boilingPointAtAltitude(0.0)
        assertEquals(100.0, seaLevelBp, 0.5)

        // Highland / Wonosobo elevation (~850m) -> ~97.2 °C
        val highlandBp = BarometricBedHeight.boilingPointAtAltitude(850.0)
        assertTrue("Titik didih di dataran tinggi harus lebih rendah dari 100°C", highlandBp < 98.0 && highlandBp > 96.0)

        // Pressure to altitude test
        val seaAlt = BarometricBedHeight.pressureToAltitude(1013.25)
        assertEquals(0.0, seaAlt, 1.0)

        val highlandAlt = BarometricBedHeight.pressureToAltitude(917.0)
        assertTrue("Elevasi 917 hPa harus di kisaran 800 - 900 mdpl", highlandAlt in 800.0..900.0)
    }

    @Test
    fun testCanopyCeptometer_BeerLambertLawAndWiwilan() {
        // Case 1: Dense canopy (only 2,000 Lux reaches ground from 80,000 Lux)
        val denseResult = CanopyCeptometer.calculateLai(
            aboveLux = 80_000f,
            belowLux = 2_000f
        )
        assertTrue("LAI harus > 3.5 pada tajuk lebat", denseResult.leafAreaIndex > 3.5)
        assertTrue("Wajib wiwilan/pemangkasan tunas saat tajuk terlalu rapat", denseResult.isWiwilanRequired)

        // Case 2: Post-pruned / well-spaced canopy (25,000 Lux reaches ground from 80,000 Lux)
        val sparseResult = CanopyCeptometer.calculateLai(
            aboveLux = 80_000f,
            belowLux = 25_000f
        )
        assertTrue("LAI harus < 2.5 pada tajuk terbuka", sparseResult.leafAreaIndex < 2.5)
        assertFalse("Tidak perlu wiwilan darurat jika tajuk cukup renggang", sparseResult.isWiwilanRequired)
    }

    @Test
    fun testBarometricStormPredictor_DropDetection() {
        val now = System.currentTimeMillis()
        val history = listOf(
            BarometricStormPredictor.PressureLogPoint(
                timestampMs = now - 7200_000,
                pressureHpa = 1012.0
            ),
            BarometricStormPredictor.PressureLogPoint(
                timestampMs = now - 3600_000,
                pressureHpa = 1011.8
            )
        )
        val stableStatus = BarometricStormPredictor.evaluatePressureTrend(
            history = history,
            currentPressureHpa = 1011.6,
            currentTimeMs = now
        )
        assertFalse("Tekanan stabil tidak memicu badai", stableStatus.isImminentStorm)
        assertTrue(stableStatus.sprayPermitted)

        // Convective storm: sudden pressure drop -2.8 hPa in 2 hours
        val stormHistory = listOf(
            BarometricStormPredictor.PressureLogPoint(
                timestampMs = now - 7200_000,
                pressureHpa = 1012.5
            )
        )
        val stormStatus = BarometricStormPredictor.evaluatePressureTrend(
            history = stormHistory,
            currentPressureHpa = 1009.5,
            currentTimeMs = now
        )
        assertTrue("Penurunan drastis harus memicu deteksi badai konvektif", stormStatus.isImminentStorm)
        assertFalse("Semprot pestisida harus diblokir untuk hindari pencucian hujan", stormStatus.sprayPermitted)
        assertTrue(stormStatus.warningMessage.contains("Badai") || stormStatus.warningMessage.contains("hujan"))
    }

    @Test
    fun testSpatialContextCollector_DiopterFocusToDistance() {
        // Diopter = 1 / focal_distance_in_meters
        // 2.0 diopter -> 0.50 meters = 500 mm
        val dist500mm = SpatialContextCollector.diopterToDistanceMm(2.0f)
        assertEquals(500.0, dist500mm, 1.0)

        // 10.0 diopter -> 0.10 meters = 100 mm (10 cm macro)
        val dist100mm = SpatialContextCollector.diopterToDistanceMm(10.0f)
        assertEquals(100.0, dist100mm, 1.0)

        // 0.0 diopter (infinity focus) -> safe fallback 500 mm
        val distInfinity = SpatialContextCollector.diopterToDistanceMm(0.0f)
        assertTrue(distInfinity >= 500.0)

        // Physical lesion area calculation from pixel area at known distance
        val lesionArea = SpatialContextCollector.calculatePhysicalLesionAreaMm2(
            pixelCount = 1000,
            distanceMm = 100.0,
            focalLengthMm = 4.2,
            sensorPixelPitchUm = 1.4
        )
        assertTrue("Luas lesi fisik harus positif dan terukur secara matematis", lesionArea > 0.0)

        // Spatial context assembly
        val ctx = SpatialContextCollector.assembleContext(
            latitude = -7.123456,
            longitude = 110.456789,
            bedIndex = 3,
            holeIndex = 14,
            twiScore = 7.5,
            windBearingDeg = 90.0
        )
        assertEquals("Timur", ctx.windCardinal)
        assertEquals(270.0, ctx.downwindThreatAzimuthDeg, 0.1)
        assertTrue(ctx.sporeRiskMultiplier > 1.0)
    }

    @Test
    fun testPedestrianDeadReckoning_StepMath() {
        val pdr = PedestrianDeadReckoning.PdrPosition(
            latitude = -7.123456,
            longitude = 110.456789,
            stepCount = 42,
            distanceMeters = 21.0,
            estimatedBedIndex = 2,
            estimatedHoleIndex = 3,
            isFusedWithGps = false
        )

        assertEquals(42, pdr.stepCount)
        assertEquals(21.0, pdr.distanceMeters, 0.01)
        assertEquals(2, pdr.estimatedBedIndex)
        assertEquals(3, pdr.estimatedHoleIndex)
        assertFalse(pdr.isFusedWithGps)
    }

    @Test
    fun testSmartCloudSync_MultiLaneBandwidthManager() {
        val offlineStrategy = SmartCloudSync.evaluateSyncStrategy(
            pendingJsonRecords = 12,
            pendingMediaItems = 5,
            isOnline = false,
            isWifi = false,
            isCharging = false
        )
        assertEquals(SmartCloudSync.NetworkQuality.OFFLINE, offlineStrategy.networkQuality)
        assertFalse("Offline tidak boleh upload media berat", offlineStrategy.networkQuality.allowsHeavyUpload)

        val wifiStrategy = SmartCloudSync.evaluateSyncStrategy(
            pendingJsonRecords = 12,
            pendingMediaItems = 5,
            isOnline = true,
            isWifi = true,
            isCharging = true
        )
        assertEquals(SmartCloudSync.NetworkQuality.WIFI_HIGH_SPEED, wifiStrategy.networkQuality)
        assertTrue("Wi-Fi diizinkan upload media berat", wifiStrategy.networkQuality.allowsHeavyUpload)
        assertTrue("Wi-Fi + charging mengaktifkan update OTA model AI", wifiStrategy.isOtaUpdateAvailable)
    }

    @Test
    fun testP2pFieldMeshSync_VectorTimestampAndPayload() {
        val meshSync = P2pFieldMeshSync("WORKER-ALPHA")
        val testMutation = JSONObject().apply {
            put("type", "HARVEST_BATCH")
            put("batch_id", "hb-101")
            put("weight_kg", 24.5)
        }
        val packet = meshSync.packLocalMutations(listOf(testMutation))
        assertEquals("WORKER-ALPHA", packet.originWorkerId)
        assertEquals(1, packet.mutationCount)
        assertTrue(packet.timestampMs > 0)
        assertEquals(64, packet.payloadHashSha256.length)

        val localQueue = mutableListOf<JSONObject>()
        val mergeResult = meshSync.mergePeerPacket(packet, localQueue)
        assertEquals(1, mergeResult.mergedCount)
        assertTrue(mergeResult.isMuleReadyForCloudUpload)
    }
}
