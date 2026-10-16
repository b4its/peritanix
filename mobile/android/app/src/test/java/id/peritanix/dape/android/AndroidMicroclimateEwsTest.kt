package id.peritanix.dape.android

import id.peritanix.dape.android.core.physics.MicroclimateEwsEvaluator
import org.junit.Assert.*
import org.junit.Test

class AndroidMicroclimateEwsTest {

    @Test
    fun testGddDailyCalculationAndBiologicalChronology() {
        // Tmin = 20°C, Tmax = 30°C -> Mean = 25°C -> GDD = 25 - 15 = 10.0 GDD
        val day1 = MicroclimateEwsEvaluator.calculateDailyGdd(20.0, 30.0, 0.0)
        assertEquals(10.0, day1.dailyGdd, 0.01)
        assertEquals(10.0, day1.accumulatedGdd, 0.01)
        assertEquals("VEGETATIVE", day1.physiologicalPhase)
        assertFalse(day1.isFortificationLocked)

        // Accumulated GDD reaches 460 -> triggers FLOWERING & Fortification Lock
        val anthesis = MicroclimateEwsEvaluator.calculateDailyGdd(22.0, 32.0, 448.0)
        assertEquals(460.0, anthesis.accumulatedGdd, 0.01)
        assertEquals("FLOWERING", anthesis.physiologicalPhase)
        assertTrue(anthesis.isFortificationLocked)
    }

    @Test
    fun test72hAnthracnoseSporeRiskForecast() {
        // High humidity (90%) & warm temperature (28°C) -> Highly conducive for Colletotrichum (Score: 0.80)
        val forecastCritical = MicroclimateEwsEvaluator.evaluate72hFungalRisk(
            relativeHumidityPct = 90.0,
            temperatureC = 28.0
        )
        assertTrue("Skor risiko spora harus >= 0.75", forecastCritical.sporulationRiskScore >= 0.75)
        assertEquals("CRITICAL", forecastCritical.riskLevel)
        assertTrue(forecastCritical.alertHeader.contains("PERINGATAN DINI EWS"))

        // Low humidity (55%) & cool temperature (18°C)
        val forecastLow = MicroclimateEwsEvaluator.evaluate72hFungalRisk(
            relativeHumidityPct = 55.0,
            temperatureC = 18.0
        )
        assertEquals("LOW", forecastLow.riskLevel)
        assertEquals(0.0, forecastLow.sporulationRiskScore, 0.05)
    }
}
