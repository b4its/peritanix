package id.peritanix.dape.android

import id.peritanix.dape.android.core.hardware.DarkChamberFlashManager
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class AndroidHardwareTest {

    @Test
    fun testDarkChamberSealVerification() {
        assertTrue(DarkChamberFlashManager.isDarkChamberSealed(0.0f))
        assertTrue(DarkChamberFlashManager.isDarkChamberSealed(0.4f))
        assertFalse(DarkChamberFlashManager.isDarkChamberSealed(1.5f))
        assertFalse(DarkChamberFlashManager.isDarkChamberSealed(100.0f))
    }

    @Test
    fun testPhotometryEvaluationOptimalSoil() {
        // Optimal soil neutral/slight pink Griess reagent color patch
        val res = DarkChamberFlashManager.evaluatePhotometry(rawR = 175, rawG = 120, rawB = 110)
        assertTrue(res.isValid)
        assertTrue(res.phDeterministic in 5.5..7.5)
        assertTrue(res.nitratePpm in 20.0..100.0)
    }

    @Test
    fun testClinometerSlopeAngleMath() {
        val pitchRad = Math.toRadians(3.0)
        val rollRad = Math.toRadians(4.0)
        val totalSlopeRad = sqrt(pitchRad * pitchRad + rollRad * rollRad)
        val totalSlopeDeg = Math.toDegrees(totalSlopeRad)
        val rounded = Math.round(totalSlopeDeg * 10.0) / 10.0
        assertEquals(5.0, rounded, 0.1)
    }
}
