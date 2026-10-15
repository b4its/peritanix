package id.peritanix.dape

import id.peritanix.dape.physics.Physics
import kotlin.math.PI
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PhysicsTest {

    private val eps = 1e-9

    // --- Calorimetry --------------------------------------------------------

    @Test fun boilingPointSeaLevel() {
        assertEquals(100.0, Physics.boilingPoint(0.0), eps)
    }

    @Test fun boilingPoint1000m() {
        assertEquals(96.7, Physics.boilingPoint(1000.0), eps)
    }

    @Test fun ratioHitsTargetLowland() {
        val r = Physics.hotWaterRatio(100.0, 27.0, 52.0, 9.0)
        assertEquals(52.0, Physics.mixTemperature(100.0, r.vBoilParts, 27.0, r.vWellParts), 1e-6)
    }

    @Test fun ratioHitsTargetHighland() {
        val r = Physics.hotWaterRatio(1000.0, 22.0, 52.0, 9.0)
        assertEquals(52.0, Physics.mixTemperature(1000.0, r.vBoilParts, 22.0, r.vWellParts), 1e-6)
    }

    @Test fun unreachableTargetThrows() {
        assertFailsWith<IllegalArgumentException> { Physics.hotWaterRatio(100.0, 60.0, 52.0) }
    }

    // --- TWI ----------------------------------------------------------------

    @Test fun twi45Degrees() {
        assertEquals(ln(10.0), Physics.twi(10.0, PI / 4), eps)
    }

    @Test fun twiFlatDoesNotExplode() {
        assertTrue(Physics.twi(50.0, 0.0).isFinite())
    }

    @Test fun riskBands() {
        assertEquals("CRITICAL", Physics.drainageRiskClass(11.0))
        assertEquals("HIGH", Physics.drainageRiskClass(9.0))
        assertEquals("MODERATE", Physics.drainageRiskClass(7.0))
        assertEquals("LOW", Physics.drainageRiskClass(4.0))
    }

    @Test fun bedHeightPrescription() {
        assertEquals(50, Physics.requiredBedHeightCm(11.0))
        assertEquals(40, Physics.requiredBedHeightCm(9.0))
        assertEquals(30, Physics.requiredBedHeightCm(7.0))
        assertEquals(20, Physics.requiredBedHeightCm(4.0))
    }

    // --- GDD ----------------------------------------------------------------

    @Test fun gddStandardDay() {
        assertEquals(10.0, Physics.gddDay(20.0, 30.0), eps)
    }

    @Test fun gddClampedBelowBase() {
        assertEquals(0.0, Physics.gddDay(10.0, 12.0), eps)
    }

    @Test fun gddPhases() {
        assertEquals("VEGETATIVE", Physics.phaseForGdd(100.0))
        assertEquals("FLOWERING", Physics.phaseForGdd(350.0))
        assertEquals("FRUITING", Physics.phaseForGdd(700.0))
        assertEquals("HARVEST", Physics.phaseForGdd(1050.0))
    }

    @Test fun fortificationWindow() {
        assertTrue(Physics.isFortificationWindow(400.0))
        assertTrue(!Physics.isFortificationWindow(300.0))
        assertTrue(!Physics.isFortificationWindow(700.0))
    }

    // --- Archimedes ---------------------------------------------------------

    @Test fun archimedesAllSink() {
        assertEquals(1.0, Physics.archimedesSinkRatio(10, 0, 0), eps)
    }

    @Test fun archimedesNeutralHalfWeight() {
        assertEquals(0.5, Physics.archimedesSinkRatio(5, 10, 5), eps)
    }

    @Test fun archimedesEmptyThrows() {
        assertFailsWith<IllegalArgumentException> { Physics.archimedesSinkRatio(0, 0, 0) }
    }

    @Test fun gradeBoundaries() {
        assertEquals("A+", Physics.gradeFromSinkRatio(0.80))
        assertEquals("A", Physics.gradeFromSinkRatio(0.70))
        assertEquals("B", Physics.gradeFromSinkRatio(0.50))
        assertEquals("BS", Physics.gradeFromSinkRatio(0.30))
    }

    // --- Stokes / USDA ------------------------------------------------------

    @Test fun stokesVelocityScalesWithRadiusSquared() {
        val v1 = Physics.stokesVelocity(1e-5)
        val v2 = Physics.stokesVelocity(2e-5)
        assertEquals(4.0 * v1, v2, v1 * 1e-6)
    }

    @Test fun usdaLoam() {
        assertEquals("loam", Physics.classifyUsda(40.0, 40.0, 20.0))
    }

    @Test fun usdaSand() {
        assertEquals("sand", Physics.classifyUsda(95.0, 3.0, 2.0))
    }

    @Test fun cecClayHigherThanSand() {
        assertTrue(Physics.cecFromTexture(5.0, 10.0, 85.0) > Physics.cecFromTexture(95.0, 3.0, 2.0))
    }

    @Test fun brineDensityIncreasesWithSalt() {
        assertTrue(Physics.brineRelativeDensity(200.0, 1000.0) > Physics.brineRelativeDensity(10.0, 1000.0))
    }
}
