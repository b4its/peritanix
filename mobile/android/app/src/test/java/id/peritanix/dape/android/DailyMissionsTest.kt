package id.peritanix.dape.android

import id.peritanix.dape.android.core.missions.DailyMissions
import id.peritanix.dape.android.core.missions.DailyMissions.Category
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for the daily-quality-mission engine:
 *  - deterministic, phase-aware mission generation
 *  - universal hydration + inspection always present
 *  - phase-specific missions (Ca/B at flowering, K at fruiting, ripeness at harvest…)
 *  - EWS risk boost adds a spore-defense mission
 *  - weighted daily quality score + grade + next-unfinished
 */
class DailyMissionsTest {

    private val today = LocalDate.of(2026, 10, 17)

    private fun ids(plan: DailyMissions.DailyPlan) = plan.missions.map { it.id }.toSet()

    @Test
    fun testDeterministicSameDay() {
        val a = DailyMissions.planFor(today, 45)
        val b = DailyMissions.planFor(today, 45)
        assertEquals(a.missions.map { it.id }, b.missions.map { it.id })
        assertEquals(a.stageIndex, b.stageIndex)
    }

    @Test
    fun testUniversalMissionsAlwaysPresent() {
        listOf(5, 20, 45, 75, 100).forEach { hst ->
            val plan = DailyMissions.planFor(today, hst)
            val cats = plan.missions.map { it.category }.toSet()
            assertTrue("hydration missing @$hst", Category.AIR in cats)
            assertTrue("inspection missing @$hst", Category.INSPEKSI in cats)
        }
    }

    @Test
    fun testPhaseMissionsVegetative() {
        val plan = DailyMissions.planFor(today, 20) // stage 2
        assertEquals(2, plan.stageIndex)
        val titles = plan.missions.joinToString(" ") { it.title.lowercase() }
        assertTrue(titles.contains("wiwil"))
    }

    @Test
    fun testPhaseMissionsFloweringHasCalcium() {
        val plan = DailyMissions.planFor(today, 45) // stage 3
        assertEquals(3, plan.stageIndex)
        val titles = plan.missions.joinToString(" ") { it.title.lowercase() }
        assertTrue("expected calcium/boron mission", titles.contains("kalsium") || titles.contains("calcium"))
        assertTrue(titles.contains("rontok") || titles.contains("kelembaban"))
    }

    @Test
    fun testPhaseMissionsFruitingHasPotassiumAndAntracnose() {
        val plan = DailyMissions.planFor(today, 75) // stage 4
        assertEquals(4, plan.stageIndex)
        val titles = plan.missions.joinToString(" ") { it.title.lowercase() }
        assertTrue(titles.contains("kalium"))
        assertTrue(titles.contains("antraknosa"))
    }

    @Test
    fun testPhaseMissionsHarvestHasRipenessAndSortation() {
        val plan = DailyMissions.planFor(today, 100) // stage 5
        assertEquals(5, plan.stageIndex)
        val titles = plan.missions.joinToString(" ") { it.title.lowercase() }
        assertTrue(titles.contains("petik") || titles.contains("merah"))
        assertTrue(titles.contains("sortir"))
    }

    @Test
    fun testSeedlingPhase() {
        val plan = DailyMissions.planFor(today, -5) // stage 1
        assertEquals(1, plan.stageIndex)
        val titles = plan.missions.joinToString(" ") { it.title.lowercase() }
        assertTrue(titles.contains("pupuk daun") || titles.contains("vigor"))
    }

    @Test
    fun testRiskBoostAddsSporeDefense() {
        val low = DailyMissions.planFor(today, 75, riskBoost = 0.2)
        val high = DailyMissions.planFor(today, 75, riskBoost = 0.8)
        assertTrue("high risk should add a mission", high.missions.size > low.missions.size)
        val titles = high.missions.joinToString(" ") { it.title.lowercase() }
        assertTrue(titles.contains("fungisida") || titles.contains("spora"))
    }

    @Test
    fun testMissionIdsUniqueWithinDay() {
        val plan = DailyMissions.planFor(today, 45, riskBoost = 0.9)
        val ids = plan.missions.map { it.id }
        assertEquals(ids.size, ids.toSet().size) // no duplicates
    }

    @Test
    fun testQualityScoreAllComplete() {
        val plan = DailyMissions.planFor(today, 45)
        val score = DailyMissions.dailyQualityScore(plan, ids(plan))
        assertEquals(100, score)
    }

    @Test
    fun testQualityScoreNoneComplete() {
        val plan = DailyMissions.planFor(today, 45)
        assertEquals(0, DailyMissions.dailyQualityScore(plan, emptySet()))
    }

    @Test
    fun testQualityScorePartialWeighted() {
        val plan = DailyMissions.planFor(today, 45)
        // Complete only the heavy Ca/B mission (weight 3).
        val calcium = plan.missions.first { it.title.lowercase().contains("kalsium") || it.title.lowercase().contains("calcium") }
        val score = DailyMissions.dailyQualityScore(plan, setOf(calcium.id))
        assertTrue("partial score should be between 0 and 100: $score", score in 1..99)
        // Completing a high-weight mission yields a higher share than a low-weight one.
        val lowWeight = plan.missions.first { it.weight < calcium.weight }
        val scoreLow = DailyMissions.dailyQualityScore(plan, setOf(lowWeight.id))
        assertTrue(score > scoreLow)
    }

    @Test
    fun testScoreGrade() {
        assertEquals("Sangat Baik — Kualitas Prima", DailyMissions.scoreGrade(95))
        assertEquals("Baik — Pertahankan", DailyMissions.scoreGrade(75))
        assertEquals("Cukup — Kejar Target", DailyMissions.scoreGrade(50))
        assertEquals("Kurang — Segera Tuntaskan", DailyMissions.scoreGrade(10))
    }

    @Test
    fun testNextUnfinished() {
        val plan = DailyMissions.planFor(today, 45)
        // Nothing done → first mission is next.
        assertEquals(plan.missions.first().id, DailyMissions.nextUnfinished(plan, emptySet())?.id)
        // All but the last done → last is next.
        val allButLast = ids(plan) - plan.missions.last().id
        assertEquals(plan.missions.last().id, DailyMissions.nextUnfinished(plan, allButLast)?.id)
        // All done → null.
        assertNull(DailyMissions.nextUnfinished(plan, ids(plan)))
    }

    @Test
    fun testEveryMissionHasQualityImpact() {
        listOf(-5, 20, 45, 75, 100).forEach { hst ->
            DailyMissions.planFor(today, hst).missions.forEach { m ->
                assertTrue("mission ${m.id} missing quality impact", m.qualityImpact.isNotBlank())
                assertTrue("mission ${m.id} weight > 0", m.weight > 0)
            }
        }
    }
}
