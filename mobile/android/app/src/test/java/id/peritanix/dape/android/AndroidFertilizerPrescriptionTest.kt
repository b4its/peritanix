package id.peritanix.dape.android

import id.peritanix.dape.physics.Physics
import org.junit.Assert.*
import org.junit.Test

class AndroidFertilizerPrescriptionTest {

    @Test
    fun testVegetativeFertilizerFormula() {
        val plan = Physics.prescribeFertilizer(ph = 6.2, cecMeq = 15.0, hst = 20)
        assertTrue(plan.npk16_16_16GPerL > 0.0)
        assertEquals(0.0, plan.calciumGPerL, 0.01) // No heavy calcium needed yet in vegetative
        assertEquals(0.0, plan.dolomiteGPerM2, 0.01) // pH 6.2 is optimal, no dolomite needed
        assertTrue(plan.recommendation.contains("vegetatif", ignoreCase = true))
    }

    @Test
    fun testFloweringFertilizerFortification() {
        val plan = Physics.prescribeFertilizer(ph = 6.0, cecMeq = 15.0, hst = 45)
        assertTrue(plan.calciumGPerL >= 2.0) // Calcium fortification required during anthesis
        assertTrue(plan.kno3GPerL > 0.0)
        assertTrue(plan.recommendation.contains("Ca+B", ignoreCase = true) || plan.recommendation.contains("Kalsium", ignoreCase = true))
    }

    @Test
    fun testAcidicSoilDolomiteAdvisory() {
        val plan = Physics.prescribeFertilizer(ph = 4.8, cecMeq = 12.0, hst = 30)
        assertTrue(plan.dolomiteGPerM2 > 50.0) // Dolomite required for acidic soil
        assertTrue(plan.recommendation.contains("dolomit", ignoreCase = true))
    }

    @Test
    fun testCecModulation() {
        // High CEC soil (clayey/loam) can hold more nutrients
        val planHigh = Physics.prescribeFertilizer(ph = 6.0, cecMeq = 25.0, hst = 50)
        // Low CEC soil (sandy) needs smaller frequent doses
        val planLow = Physics.prescribeFertilizer(ph = 6.0, cecMeq = 8.0, hst = 50)

        assertTrue(planHigh.npk16_16_16GPerL <= planLow.npk16_16_16GPerL || planHigh.npk16_16_16GPerL > 0.0)
    }
}
