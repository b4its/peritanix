package id.peritanix.dape.android

import id.peritanix.dape.pathology.PathologyClassifier
import id.peritanix.dape.physics.Physics
import org.junit.Assert.*
import org.junit.Test

class AndroidPathologyIntegrationTest {

    @Test
    fun testThripsDiferentiation() {
        val result = PathologyClassifier.differentiateLeafCurl(
            curlDirection = "up",
            undersideColor = "silvery",
            mosaicVeins = false,
            sunkenLesions = false
        )
        assertEquals("Keriting_Thrips", result.condition)
        assertTrue(result.activeIngredient.contains("Abamektin", ignoreCase = true))
        assertTrue(result.dosagePerLiter.isNotEmpty())
        assertTrue(result.sprayingInstructions.isNotEmpty())
    }

    @Test
    fun testMitesDiferentiation() {
        val result = PathologyClassifier.differentiateLeafCurl(
            curlDirection = "down",
            undersideColor = "bronze",
            mosaicVeins = false,
            sunkenLesions = false
        )
        assertEquals("Keriting_Mites", result.condition)
        assertTrue(result.activeIngredient.contains("Bifenazat", ignoreCase = true) || result.activeIngredient.contains("Akarisida", ignoreCase = true))
        assertTrue(result.dosagePerLiter.isNotEmpty())
    }

    @Test
    fun testGeminiVirusDiferentiation() {
        val result = PathologyClassifier.differentiateLeafCurl(
            curlDirection = "none",
            undersideColor = "normal",
            mosaicVeins = true,
            sunkenLesions = false
        )
        assertEquals("Begomovirus", result.condition)
        assertTrue(result.title.contains("Gemini", ignoreCase = true))
    }

    @Test
    fun testAntraknosaDiferentiation() {
        val result = PathologyClassifier.differentiateLeafCurl(
            curlDirection = "none",
            undersideColor = "normal",
            mosaicVeins = false,
            sunkenLesions = true
        )
        assertEquals("Antraknosa", result.condition)
        assertTrue(result.activeIngredient.contains("Mankozeb", ignoreCase = true))
    }

    @Test
    fun testDestructiveOozeTriggerLogic() {
        // Wilting + High Moisture -> Mandatory
        val res1 = PathologyClassifier.evaluateDestructiveOozeTrigger(isWilting = true, soilMoisturePct = 70.0)
        assertTrue(res1.isMandatory)
        assertEquals("OOZE_TEST_MANDATORY", res1.protocolCode)

        // Non-wilting + High Moisture -> Not mandatory
        val res2 = PathologyClassifier.evaluateDestructiveOozeTrigger(isWilting = false, soilMoisturePct = 70.0)
        assertFalse(res2.isMandatory)

        // Wilting + Dry Moisture -> Stres air biasa
        val res3 = PathologyClassifier.evaluateDestructiveOozeTrigger(isWilting = true, soilMoisturePct = 30.0)
        assertFalse(res3.isMandatory)
    }

    @Test
    fun testImmersionTraceAntiFraud() {
        // Valid dynamic motion
        val dynamicSamples = (0..59).map { i ->
            Physics.TraceSample(
                t = (180.0 * i) / 59.0,
                gx = 0.05 * Math.sin(i.toDouble()),
                gy = 0.05 * Math.cos(i.toDouble() * 1.3),
                gz = 0.02 * Math.sin(i.toDouble() * 0.7),
            )
        }
        val resDynamic = Physics.validateImmersionTrace(dynamicSamples)
        assertTrue(resDynamic.valid)

        // Invalid constant trace (cheating by leaving device flat)
        val flatSamples = (0..180).map { t ->
            Physics.TraceSample(t = t.toDouble(), gx = 0.01, gy = 0.01, gz = 0.01)
        }
        val resFlat = Physics.validateImmersionTrace(flatSamples)
        assertFalse(resFlat.valid)
    }
}
