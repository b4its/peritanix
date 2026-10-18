package id.peritanix.dape.android

import id.peritanix.dape.android.core.hardware.BarometricStormGuard
import id.peritanix.dape.android.core.hardware.DarkChamberFlashManager
import id.peritanix.dape.android.core.physics.BayesianSpatialInfectionEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for:
 * 1. BarometricStormGuard (Tahap 4.4 - ΔP/2h & Spray Lockout)
 * 2. BayesianSpatialInfectionEngine (Tahap 4.3 - Ralstonia vs Fusarium & Spatial Quarantine)
 * 3. DarkChamberFlashManager with Dolomite per Hole Calculation (Tahap 1.4)
 */
class BayesianAndStormGuardTest {

    // --- 1. Barometric Storm Guard Tests ------------------------------------

    @Test
    fun testBarometricTrend_RapidDropTriggersStormImminent() {
        val now = System.currentTimeMillis()
        val readings = listOf(
            BarometricStormGuard.PressureReading(now - 7200000L, 1014.0),
            BarometricStormGuard.PressureReading(now, 1012.2) // Drop of 1.8 hPa in 2 hours
        )

        val verdict = BarometricStormGuard.evaluateBarometricTrend(readings)
        assertEquals(BarometricStormGuard.StormThreatLevel.STORM_IMMINENT, verdict.threatLevel)
        assertTrue(verdict.isRainImminent)
        assertTrue(verdict.deltaPressure2Hr <= -1.5)
        assertNotNull(verdict.rainEtaMinutes)
        assertTrue(verdict.rainEtaMinutes!! in 30..90)
        assertTrue(verdict.advisoryNote.contains("PERINGATAN BADAI"))
    }

    @Test
    fun testBarometricTrend_StablePressureIsSafe() {
        val now = System.currentTimeMillis()
        val readings = listOf(
            BarometricStormGuard.PressureReading(now - 7200000L, 1013.0),
            BarometricStormGuard.PressureReading(now, 1012.8) // Only 0.2 hPa change
        )

        val verdict = BarometricStormGuard.evaluateBarometricTrend(readings)
        assertEquals(BarometricStormGuard.StormThreatLevel.SAFE, verdict.threatLevel)
        assertFalse(verdict.isRainImminent)
        assertNull(verdict.rainEtaMinutes)
        assertTrue(verdict.advisoryNote.contains("stabil"))
    }

    @Test
    fun testSprayLockout_WindSpeedAndStormCombinations() {
        val safeVerdict = BarometricStormGuard.StormVerdict(
            currentPressureHpa = 1013.0,
            deltaPressure2Hr = -0.3,
            threatLevel = BarometricStormGuard.StormThreatLevel.SAFE,
            isRainImminent = false,
            rainEtaMinutes = null,
            advisoryNote = "Stabil"
        )

        val stormVerdict = BarometricStormGuard.StormVerdict(
            currentPressureHpa = 1010.5,
            deltaPressure2Hr = -1.9,
            threatLevel = BarometricStormGuard.StormThreatLevel.STORM_IMMINENT,
            isRainImminent = true,
            rainEtaMinutes = 45,
            advisoryNote = "Badai dekat"
        )

        // 1. Calm wind + safe barometer -> spray permitted
        val case1 = BarometricStormGuard.evaluateSprayLockout(windSpeedKmh = 6.0, barometerVerdict = safeVerdict)
        assertTrue(case1.isPermitted)
        assertTrue(case1.reason.contains("KONDISI AMAN"))

        // 2. High wind (> 12 km/h) + safe barometer -> wind lockout
        val case2 = BarometricStormGuard.evaluateSprayLockout(windSpeedKmh = 14.5, barometerVerdict = safeVerdict)
        assertFalse(case2.isPermitted)
        assertTrue(case2.reason.contains("KUNCI ANGIN AKTIF"))

        // 3. Calm wind + storm imminent -> rain lockout
        val case3 = BarometricStormGuard.evaluateSprayLockout(windSpeedKmh = 5.0, barometerVerdict = stormVerdict)
        assertFalse(case3.isPermitted)
        assertTrue(case3.reason.contains("KUNCI HUJAN BAROMETER AKTIF"))

        // 4. High wind + storm imminent -> double lockout
        val case4 = BarometricStormGuard.evaluateSprayLockout(windSpeedKmh = 16.0, barometerVerdict = stormVerdict)
        assertFalse(case4.isPermitted)
        assertTrue(case4.reason.contains("KUNCI GANDA DIAKTIFKAN"))
    }

    // --- 2. Bayesian Spatial Infection Engine Tests -------------------------

    @Test
    fun testBayesianEngine_BacterialWiltPosteriorAndPerimeter() {
        val evidence = BayesianSpatialInfectionEngine.MultiModalEvidence(
            isWilting = true,
            leavesRemainGreen = true,
            oozeStreamingDetected = true,
            oozeFlowVelocityPxS = 4.5,
            twiScore = 8.8,
            soilMoisturePct = 78.0,
            prevailingWindBearingDeg = 90.0, // Wind blowing towards East (azimuth 90)
            infectedBedIndex = 4,
            infectedHoleIndex = 10,
            totalBedsInBlock = 12
        )

        val result = BayesianSpatialInfectionEngine.evaluatePathogenAndPerimeter(evidence)
        assertTrue("Bacterial probability should be very high (> 0.90)", result.bacterialWiltProbability > 0.90)
        assertTrue(result.diagnosedPathogen.contains("Ralstonia"))
        assertTrue(result.urgencyLevel.contains("KRITIS"))

        // Perimeter bounds
        assertEquals(3, result.quarantinedBedStart)
        assertEquals(5, result.quarantinedBedEnd)
        assertEquals(6, result.quarantinedHoleStart)
        assertEquals(14, result.quarantinedHoleEnd)

        // Downwind threat bed
        assertEquals(5, result.downwindThreatBed)

        // Agronomic actions
        assertTrue(result.isolationTrenchAction.contains("Gali parit pembatas sedalam 30 cm"))
        assertTrue(result.isolationTrenchAction.contains("KAPUR DOLOMIT"))
        assertTrue(result.biologicalDrenchingRecipe.contains("Pseudomonas fluorescens"))
    }

    @Test
    fun testBayesianEngine_FungalWiltPosterior() {
        val evidence = BayesianSpatialInfectionEngine.MultiModalEvidence(
            isWilting = true,
            leavesRemainGreen = false, // Chlorosis yellowing
            oozeStreamingDetected = false, // No streaming in clear water
            oozeFlowVelocityPxS = 0.0,
            twiScore = 5.5,
            soilMoisturePct = 50.0,
            prevailingWindBearingDeg = 180.0,
            infectedBedIndex = 2,
            infectedHoleIndex = 8,
            totalBedsInBlock = 12
        )

        val result = BayesianSpatialInfectionEngine.evaluatePathogenAndPerimeter(evidence)
        assertTrue("Fungal probability should dominate (> 0.80)", result.fungalWiltProbability > 0.80)
        assertTrue(result.diagnosedPathogen.contains("Fusarium"))
        assertTrue(result.biologicalDrenchingRecipe.contains("Trichoderma"))
    }

    @Test
    fun testBayesianEngine_HealthyPlantReturnsNoQuarantine() {
        val evidence = BayesianSpatialInfectionEngine.MultiModalEvidence(
            isWilting = false,
            leavesRemainGreen = true,
            oozeStreamingDetected = false,
            twiScore = 4.0,
            soilMoisturePct = 60.0,
            prevailingWindBearingDeg = 0.0,
            infectedBedIndex = 1,
            infectedHoleIndex = 5,
            totalBedsInBlock = 12
        )

        val result = BayesianSpatialInfectionEngine.evaluatePathogenAndPerimeter(evidence)
        assertEquals(0.05, result.bacterialWiltProbability, 0.01)
        assertEquals("NORMAL", result.urgencyLevel)
        assertTrue(result.isolationTrenchAction.contains("Tidak diperlukan karantina"))
    }

    // --- 3. Dark-Chamber Dolomite per Hole Tests ----------------------------

    @Test
    fun testDarkChamberPhotometry_CalculatesDolomitePerPlantingHole() {
        // Red litmus indicating acidic soil (a > 20.0, pH < 6.0)
        val result = DarkChamberFlashManager.evaluatePhotometry(
            rawR = 210, rawG = 60, rawB = 55
        )

        assertTrue("pH should be acidic (< 6.0)", result.phDeterministic < 6.0)
        assertTrue("Dolomite requirement should be > 0", result.dolomiteGramsPerHole > 0.0)
        assertTrue(result.statusMessage.contains("Dolomit"))
        assertTrue(result.statusMessage.contains("gram per lubang tanam"))
    }
}
