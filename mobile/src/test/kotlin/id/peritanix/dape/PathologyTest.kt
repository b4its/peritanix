package id.peritanix.dape

import id.peritanix.dape.pathology.PathologyClassifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathologyTest {

    @Test
    fun testThripsDiagnosis() {
        val diag = PathologyClassifier.differentiateLeafCurl(
            curlDirection = "up",
            undersideColor = "silvery"
        )
        assertEquals("Keriting_Thrips", diag.condition)
        assertTrue(diag.activeIngredient.contains("Abamektin"))
        assertEquals(0.95, diag.confidence)
    }

    @Test
    fun testMitesDiagnosis() {
        val diag = PathologyClassifier.differentiateLeafCurl(
            curlDirection = "down",
            undersideColor = "bronze"
        )
        assertEquals("Keriting_Mites", diag.condition)
        assertTrue(diag.activeIngredient.contains("Bifenazat"))
        assertEquals(0.94, diag.confidence)
    }

    @Test
    fun testBegomovirusDiagnosis() {
        val diag = PathologyClassifier.differentiateLeafCurl(
            mosaicVeins = true
        )
        assertEquals("Begomovirus", diag.condition)
        assertTrue(diag.activeIngredient.contains("Imidakloprid"))
    }

    @Test
    fun testAnthracnoseDiagnosis() {
        val diag = PathologyClassifier.differentiateLeafCurl(
            sunkenLesions = true
        )
        assertEquals("Antraknosa", diag.condition)
        assertTrue(diag.activeIngredient.contains("Mankozeb"))
    }

    @Test
    fun testDestructiveOozeTriggerWhenWiltingAndWet() {
        val res = PathologyClassifier.evaluateDestructiveOozeTrigger(
            isWilting = true,
            soilMoisturePct = 75.0
        )
        assertTrue(res.isMandatory)
        assertEquals("OOZE_TEST_MANDATORY", res.protocolCode)
    }

    @Test
    fun testDestructiveOozeTriggerWhenDryWilting() {
        val res = PathologyClassifier.evaluateDestructiveOozeTrigger(
            isWilting = true,
            soilMoisturePct = 30.0
        )
        assertFalse(res.isMandatory)
        assertEquals("FUSARIUM_OR_DROUGHT", res.protocolCode)
    }

    @Test
    fun testBlastRisk72hCalculation() {
        val temps = listOf(28.0, 27.5, 29.0)
        val rhs = listOf(90.0, 95.0, 92.0)
        val res = PathologyClassifier.calculateBlastRisk72h(temps, rhs)
        assertTrue(res.peakRisk >= 0.7)
        assertEquals(0, res.irrigationMinutesPerBed)
        assertTrue(res.action.contains("RISIKO TINGGI"))
    }
}
