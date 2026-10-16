package id.peritanix.dape.android

import id.peritanix.dape.physics.Physics
import org.junit.Assert.*
import org.junit.Test

class AndroidCalorimetryIntegrationTest {

    @Test
    fun testBlackCalorimetryAcrossElevations() {
        val testAltitudes = listOf(0.0, 500.0, 1000.0, 1500.0, 2000.0)
        val wellTemp = 26.0

        for (alt in testAltitudes) {
            val ratio = Physics.hotWaterRatio(alt, wellTemp)
            val expectedBoil = 100.0 - 0.0033 * alt
            assertEquals(expectedBoil, ratio.boilingPointC, 0.01)

            val mixedTemp = Physics.mixTemperature(
                elevationM = alt,
                vBoil = ratio.vBoilParts,
                wellTempC = wellTemp,
                vWell = ratio.vWellParts
            )

            // Mixed temperature must hit target 52.0°C within ± 0.5°C
            assertEquals(52.0, mixedTemp, 0.5)
        }
    }

    @Test
    fun testArchimedesSinkRatioAndGrading() {
        // High quality seed (majority sinks)
        val ratioA1 = Physics.archimedesSinkRatio(sinkCount = 18, neutralCount = 2, floatCount = 0)
        assertTrue(ratioA1 >= 0.85)
        assertEquals("A+", Physics.gradeFromSinkRatio(ratioA1))

        // Good quality seed
        val ratioA = Physics.archimedesSinkRatio(sinkCount = 12, neutralCount = 6, floatCount = 2)
        assertTrue(ratioA in 0.70..0.85)
        assertEquals("A", Physics.gradeFromSinkRatio(ratioA))

        // Standard seed
        val ratioB = Physics.archimedesSinkRatio(sinkCount = 8, neutralCount = 4, floatCount = 8)
        assertTrue(ratioB in 0.45..0.65)
        assertEquals("B", Physics.gradeFromSinkRatio(ratioB))

        // Poor quality seed (majority floats)
        val ratioBS = Physics.archimedesSinkRatio(sinkCount = 1, neutralCount = 2, floatCount = 17)
        assertTrue(ratioBS < 0.50)
        assertEquals("BS", Physics.gradeFromSinkRatio(ratioBS))
    }
}
