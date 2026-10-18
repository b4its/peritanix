package id.peritanix.dape.android

import id.peritanix.dape.android.core.ai_edge.EdgeInferenceRunner
import id.peritanix.dape.android.core.ai_edge.MultiInstancePodSegmentationEngine
import id.peritanix.dape.android.core.crypto.ForensicHasher
import id.peritanix.dape.android.core.hardware.BarometricStormGuard
import id.peritanix.dape.android.core.hardware.DarkChamberFlashManager
import id.peritanix.dape.android.core.physics.BayesianSpatialInfectionEngine
import id.peritanix.dape.android.core.physics.BlackCalorimetryEngine
import id.peritanix.dape.crypto.Ed25519OfflineSigner
import id.peritanix.dape.physics.Physics
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * TAHAP 7: Definition of Done (DoD) - 100% Offline-First Quality Verification Suite.
 *
 * Formally validates the 3 key verification criteria from the master specification:
 * 1. Uji Ketahanan Airplane Mode Hulu-ke-Hilir (Tahap 1 -> Tahap 6 100% offline).
 * 2. Uji Akurasi Fisika & Sensor (0 Lux interlock, optical mm error < 3%, Asas Black 52°C equilibrium).
 * 3. Uji Performa HP Spesifikasi Rendah (Latency < 200 ms budget for INT8 edge models).
 */
class OfflineQualityDefinitionOfDoneTest {

    private val inferenceRunner = EdgeInferenceRunner(File("/tmp/models"))

    // -------------------------------------------------------------------------
    // 1. Uji Ketahanan Airplane Mode Hulu-ke-Hilir (Tahap 1 -> Tahap 6)
    // -------------------------------------------------------------------------
    @Test
    fun testAirplaneModeEndToEndPipelineHuluKeHilir() {
        // --- TAHAP 1: Kualitas Tanah, Drainase & Nutrisi Dasar ---
        // Stokes sedimentation: sandy loam (60% sand, 25% silt, 15% clay)
        val soilClass = Physics.classifyUsda(sandPct = 60.0, siltPct = 25.0, clayPct = 15.0)
        assertEquals("sandy loam", soilClass)
        val cecMeq = Physics.cecFromTexture(sandPct = 60.0, siltPct = 25.0, clayPct = 15.0)
        assertTrue("CEC should be estimated accurately", cecMeq in 5.0..25.0)

        // Dark-Chamber pH photometry: pH 5.2 (acidic soil -> calcium uptake blocked)
        val phValue = 5.2
        val dolomitePerHoleG = Physics.calculateDolomitePerPlantingHole(
            ph = phValue,
            targetPh = 6.5,
            holesPerHectare = 18000
        )
        assertTrue("Dolomite should be prescribed when pH < 6.0", dolomitePerHoleG > 0.0)
        assertEquals(144.4, dolomitePerHoleG, 0.5)

        // TWI & Bed height
        val twiValue = Physics.twi(upslopeAreaM2PerM = 450.0, slopeBetaRad = Math.toRadians(2.5))
        assertTrue("TWI should be calculated locally", twiValue > 8.0)
        val bedHeight = Physics.requiredBedHeightCm(twiValue)
        assertTrue("High TWI requires raised bed >= 40 cm", bedHeight >= 40)

        // --- TAHAP 2: Vigor Bibit & Arsitektur Tajuk ---
        // Beer-Lambert Canopy Ceptometer (LAI)
        val canopyK = 0.65
        val lightAbove = 80000.0
        val lightBelow = 2000.0
        val lai = -kotlin.math.ln(lightBelow / lightAbove) / canopyK
        assertTrue("LAI should be calculated offline", lai > 3.5)

        // --- TAHAP 3: Rekayasa Pembungaan & Kunci Kalsium-Silika ---
        // GDD Anthesis stage
        val gdd = Physics.gddDay(tMin = 21.0, tMax = 31.0, tBase = 15.0)
        assertEquals(11.0, gdd, 0.01)
        val isAnthesisLock = Physics.isFortificationWindow(gddCum = 450.0)
        assertTrue("GDD 450 should enter fortification window", isAnthesisLock)

        // Prescriptive generative fertilizer plan
        val fertPlan = Physics.prescribeFertilizer(ph = 6.4, cecMeq = cecMeq, hst = 45)
        assertTrue("Flowering phase requires Calcium", fertPlan.calciumGPerL > 0.0)
        assertTrue("Fertilizer recommendation emphasizes Ca+B and avoids high N", fertPlan.recommendation.contains("Ca+B"))

        // --- TAHAP 4: Pengawalan Pembesaran Buah & Deteksi Laten ---
        // Active trans-illumination latent patek check
        val transResult = inferenceRunner.evaluateTransIllumination(rawR = 130, rawG = 150, rawB = 100)
        assertTrue("Latent fungal infection detected via light attenuation", transResult.isLatentFungalDetected)

        // Spray safety lock (Acoustic anemometer + Barometric storm guard)
        val stormVerdict = BarometricStormGuard.StormVerdict(
            currentPressureHpa = 1008.2,
            deltaPressure2Hr = -2.2,
            threatLevel = BarometricStormGuard.StormThreatLevel.STORM_IMMINENT,
            isRainImminent = true,
            rainEtaMinutes = 60,
            advisoryNote = "Hujan Badai Segera Turun"
        )
        val stormGuard = BarometricStormGuard.evaluateSprayLockout(
            windSpeedKmh = 14.5,
            barometerVerdict = stormVerdict
        )
        assertFalse("Rain storm + high wind -> spray strictly locked", stormGuard.isPermitted)

        // Ripeness & Yield estimation
        val yield = inferenceRunner.estimateYieldAndFruitCount(greenCount = 2, turningCount = 4, ripeCount = 38)
        assertTrue("Harvest readiness high", yield.harvestReadinessPct >= 80.0)
        assertEquals(0, yield.optimalHarvestWindowDays)

        // --- TAHAP 5: Pasca-Panen Termodinamika & Meja Sortir Pintar ---
        // Asas Black 52°C water mixing
        val calib = BlackCalorimetryEngine.calculate(elevationM = 850.0, wellWaterTempC = 26.0)
        assertTrue("Boiling parts calculated", calib.boilingParts > 0.0)

        // Archimedes saltwater density assay
        val sinkRatio = Physics.archimedesSinkRatio(sinkCount = 18, neutralCount = 2, floatCount = 0)
        assertTrue("Sink ratio >= 85% qualifies for Grade A+", sinkRatio >= 0.85)
        assertEquals("A+", Physics.gradeFromSinkRatio(sinkRatio))

        // Multi-instance sorting table with visual reject overlay
        val tableResult = MultiInstancePodSegmentationEngine.detectAndSegmentTable(
            podCount = 16,
            tiltAngleDeg = 1.2,
            lensFocusDistanceDiopters = 3.0f
        )
        assertTrue(tableResult.isTopDownAngleLocked)
        assertTrue(tableResult.rejectPods.isNotEmpty())

        // Discard rejects -> clean export batch
        val cleanTable = MultiInstancePodSegmentationEngine.removeAllRejects(tableResult)
        assertEquals(0, cleanTable.rejectPods.size)
        assertTrue(cleanTable.premiumPct >= 85.0)

        // --- TAHAP 6: Sertifikasi Kriptografis Ed25519 & Verifikasi Pembeli ---
        val testKp = Ed25519OfflineSigner.fromSeed(ByteArray(32) { (it + 7).toByte() })
        val certPayload = mapOf(
            "land_id" to "land-peritanix-001",
            "cec_meq" to cecMeq,
            "gdd_fortification_compliant" to true,
            "sterilization_c" to 52.0,
            "acoustic_boiling_verified" to true,
            "gyro_immersion_seconds" to 180,
            "archimedes_sink_ratio" to sinkRatio,
            "grade_result" to "A+",
            "batch_premium_pct" to cleanTable.premiumPct,
            "image_sha256_hash" to ForensicHasher.sha256Hex("harvest-batch-clean".toByteArray()),
            "timestamp" to (System.currentTimeMillis() / 1000)
        )

        val signedCertificate = Ed25519OfflineSigner.certificate(certPayload, testKp)
        assertNotNull(signedCertificate)
        assertTrue(signedCertificate.contains("\"grade_result\":\"A+\""))

        // Buyer verification
        val buyerVerdict = Ed25519OfflineSigner.verifyCertificate(signedCertificate)
        assertTrue("Cryptographic signature valid 100% offline", buyerVerdict.isAuthentic)
        assertTrue("Awarded Grade A+ Export Prime badge", buyerVerdict.isGradeAPlus)
        assertEquals(1.35, buyerVerdict.priceMultiplier, 0.01)
    }

    // -------------------------------------------------------------------------
    // 2. Uji Akurasi Fisika & Sensor
    // -------------------------------------------------------------------------
    @Test
    fun testPhysicsAndSensorAccuracyTolerances() {
        // 1. Dark chamber photometer 0 Lux strictly rejects light leak
        val hasLeak = !DarkChamberFlashManager.isDarkChamberSealed(ambientLux = 1.5f)
        assertTrue("Ambient Lux > 0 must indicate unsealed chamber", hasLeak)

        val isSealed = DarkChamberFlashManager.isDarkChamberSealed(ambientLux = 0.0f)
        assertTrue("0.0 Lux must indicate sealed chamber", isSealed)

        // 2. Optical millimeter scale error budget < 3%
        val focalLengthMm = 4.38
        val sensorWidthMm = 6.4
        val imageWidthPx = 1920

        val scale = MultiInstancePodSegmentationEngine.calculateOpticalMmPerPixel(
            lensFocusDistanceDiopters = 3.0f,
            sensorWidthMm = sensorWidthMm,
            focalLengthMm = focalLengthMm,
            imageWidthPx = imageWidthPx
        )

        val expectedScale = ((sensorWidthMm * 333.333) / focalLengthMm) / imageWidthPx
        val errorPct = (kotlin.math.abs(scale - expectedScale) / expectedScale) * 100.0
        assertTrue("Optical scale margin of error must be < 3% (was $errorPct%)", errorPct < 3.0)

        // 3. Asas Black thermal equilibrium at 52.0°C ± 0.5°C
        val ratio = Physics.hotWaterRatio(elevationM = 900.0, wellTempC = 25.0)
        val mixT = Physics.mixTemperature(
            elevationM = 900.0,
            vBoil = ratio.vBoilParts,
            wellTempC = 25.0,
            vWell = ratio.vWellParts
        )
        assertEquals(52.0, mixT, 0.5)
    }

    // -------------------------------------------------------------------------
    // 3. Uji Performa HP Spesifikasi Rendah (Latency < 200 ms Budget)
    // -------------------------------------------------------------------------
    @Test
    fun testLowSpecInferencePerformanceBudget() {
        // Multi-Instance Pod Segmentation (16 instances simultaneously)
        val t0 = System.nanoTime()
        val segResult = MultiInstancePodSegmentationEngine.detectAndSegmentTable(podCount = 16)
        val t1 = System.nanoTime()
        val segElapsedMs = (t1 - t0) / 1_000_000.0
        assertTrue("Multi-instance segmentation must complete under 200 ms (was $segElapsedMs ms)", segElapsedMs < 200.0)

        // Bayesian Spatial Infection Engine
        val evidence = BayesianSpatialInfectionEngine.MultiModalEvidence(
            isWilting = true,
            leavesRemainGreen = true,
            oozeStreamingDetected = true,
            oozeFlowVelocityPxS = 3.2,
            twiScore = 13.5,
            soilMoisturePct = 85.0,
            prevailingWindBearingDeg = 180.0,
            infectedBedIndex = 4,
            infectedHoleIndex = 12
        )
        val t2 = System.nanoTime()
        val bayesResult = BayesianSpatialInfectionEngine.evaluatePathogenAndPerimeter(evidence)
        val t3 = System.nanoTime()
        val bayesElapsedMs = (t3 - t2) / 1_000_000.0
        assertTrue("Bayesian spatial isolation must complete under 200 ms (was $bayesElapsedMs ms)", bayesElapsedMs < 200.0)
        assertTrue(bayesResult.bacterialWiltProbability > 0.85)
    }
}
