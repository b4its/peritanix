package id.peritanix.dape.android

import id.peritanix.dape.android.core.physics.BlackCalorimetryEngine
import id.peritanix.dape.android.core.physics.StokesCalculator
import id.peritanix.dape.android.core.physics.TwiLocalCompute
import org.junit.Assert.*
import org.junit.Test

class AndroidPhysicsCoreTest {

    @Test
    fun testTwiLocalCompute() {
        val result = TwiLocalCompute.compute(upslopeAreaM2PerM = 150.0, slopeDeg = 4.5)
        assertTrue(result.twiValue > 6.0)
        assertEquals("MODERATE", result.drainageRisk)
        assertEquals(30, result.requiredBedHeightCm)
    }

    @Test
    fun testStokesCalculatorSoilProfile() {
        val profile = StokesCalculator.computeFromThickness(
            sandThickness = 40.0,
            siltThickness = 40.0,
            clayThickness = 20.0
        )
        assertEquals("loam", profile.usdaClass)
        assertTrue(profile.cecMeq > 10.0)
    }

    @Test
    fun testStokesPerpendicularVerification() {
        // Flat upright orientation (Y=9.8, X=0, Z=0)
        assertTrue(StokesCalculator.isUprightPerpendicular(0.0f, 9.8f, 0.0f))
        // Tilted orientation (X=3.0, Y=9.0)
        assertFalse(StokesCalculator.isUprightPerpendicular(3.0f, 9.0f, 0.0f))
    }

    @Test
    fun testBlackCalorimetryEngine() {
        val r = BlackCalorimetryEngine.calculate(elevationM = 850.0, wellWaterTempC = 26.0)
        assertTrue(r.localBoilingPointC in 96.0..98.0)
        assertTrue(r.ratioText.contains("gayung didih"))
        assertTrue(r.voiceInstructionId.contains("52 derajat"))
        assertEquals(180, r.countdownSeconds)
    }
}
