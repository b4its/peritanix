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
                assertTrue("mission ${m.id} missing howToExecute", m.howToExecute.isNotBlank())
                assertTrue("mission ${m.id} missing scientificBasis", m.scientificBasis.isNotBlank())
                assertTrue("mission ${m.id} missing targetMetric", m.targetMetric.isNotBlank())
            }
        }
    }

    @Test
    fun testEveryMissionHasCompleteSopGuidance() {
        listOf(-10, -5, 10, 20, 35, 45, 55, 65, 75, 95, 105, 115).forEach { hst ->
            DailyMissions.planFor(today, hst, riskBoost = 0.8).missions.forEach { m ->
                assertTrue("mission ${m.id} missing toolsAndMaterials", m.toolsAndMaterials.isNotEmpty())
                assertTrue("mission ${m.id} missing executionSteps", m.executionSteps.isNotEmpty())
                assertTrue("mission ${m.id} missing optimalTiming", m.optimalTiming.isNotBlank())
                assertTrue("mission ${m.id} missing commonMistakesToAvoid", m.commonMistakesToAvoid.isNotEmpty())
                assertTrue("mission ${m.id} missing successVerification", m.successVerification.isNotBlank())
            }
        }
    }

    @Test
    fun testQualityTargetsAndPillarsCovered() {
        assertEquals(6, DailyMissions.QualityTarget.values().size)
        DailyMissions.QualityTarget.values().forEach { target ->
            assertTrue(target.title.isNotBlank())
            assertTrue(target.goalMetric.isNotBlank())
            assertTrue(target.description.isNotBlank())
        }
    }

    @Test
    fun testPillarProgressCalculation() {
        val plan = DailyMissions.planFor(today, 45) // stage 3
        val emptyProgress = DailyMissions.getPillarProgress(plan, emptySet())
        emptyProgress.values.forEach { (done, total) ->
            assertEquals(0, done)
            assertTrue(total > 0)
        }

        val allIds = ids(plan)
        val fullProgress = DailyMissions.getPillarProgress(plan, allIds)
        fullProgress.values.forEach { (done, total) ->
            assertEquals(total, done)
        }
    }

    @Test
    fun testFilterByTarget() {
        val plan = DailyMissions.planFor(today, 45)
        val pericarpMissions = DailyMissions.filterByTarget(plan, DailyMissions.QualityTarget.PERICARP_TEBAL)
        assertTrue(pericarpMissions.isNotEmpty())
        pericarpMissions.forEach {
            assertEquals(DailyMissions.QualityTarget.PERICARP_TEBAL, it.qualityTarget)
        }

        val allMissions = DailyMissions.filterByTarget(plan, null)
        assertEquals(plan.missions.size, allMissions.size)
    }

    @Test
    fun testExportGradeBadge() {
        assertEquals("Grade A+ (Standar Ekspor)", DailyMissions.exportGradeBadge(95))
        assertEquals("Grade A (Pasar Swalayan)", DailyMissions.exportGradeBadge(80))
        assertEquals("Grade B (Pasar Induk)", DailyMissions.exportGradeBadge(60))
        assertEquals("Grade Standar (Perlu Optimasi)", DailyMissions.exportGradeBadge(20))
    }

    @Test
    fun testSpecificQualityMissionsPresentAcrossStages() {
        // Stage 1: Persemaian -21..0 HST
        val s1 = DailyMissions.planFor(today, -5)
        val s1Titles = s1.missions.joinToString(" ") { it.title.lowercase() }
        assertTrue(s1Titles.contains("trichoderma"))
        assertTrue(s1Titles.contains("hardening") || s1Titles.contains("pengerasan"))

        // Stage 2: Vegetatif 1..35 HST
        val s2 = DailyMissions.planFor(today, 20)
        val s2Titles = s2.missions.joinToString(" ") { it.title.lowercase() }
        assertTrue(s2Titles.contains("silika"))
        assertTrue(s2Titles.contains("likat kuning"))

        // Stage 3: Pembungaan 36..55 HST
        val s3 = DailyMissions.planFor(today, 45)
        val s3Titles = s3.missions.joinToString(" ") { it.title.lowercase() }
        assertTrue(s3Titles.contains("metil eugenol") || s3Titles.contains("lalat buah"))
        assertTrue(s3Titles.contains("phospat") || s3Titles.contains("mkp"))

        // Stage 4: Pembuahan 56..75 HST
        val s4 = DailyMissions.planFor(today, 65)
        val s4Titles = s4.missions.joinToString(" ") { it.title.lowercase() }
        assertTrue(s4Titles.contains("defisit air") || s4Titles.contains("kapsaisin"))
        assertTrue(s4Titles.contains("pecah") || s4Titles.contains("crack"))

        // Stage 5: Panen 76..120 HST (Stage 5 is active when hst > 90, e.g. 100)
        val s5 = DailyMissions.planFor(today, 100)
        val s5Titles = s5.missions.joinToString(" ") { it.title.lowercase() }
        assertTrue(s5Titles.contains("07:00") || s5Titles.contains("pagi"))
        assertTrue(s5Titles.contains("archimedes") || s5Titles.contains("apung"))
        assertTrue(s5Titles.contains("krat") || s5Titles.contains("ventilasi"))
    }

    @Test
    fun testNewSensorFusedQualityMissions() {
        // Stage 1: Termoterapi 50C & Aerasi Baki
        val s1 = DailyMissions.planFor(today, -10)
        val s1Titles = s1.missions.joinToString(" ") { it.title.lowercase() }
        assertTrue(s1Titles.contains("50°c") || s1Titles.contains("termoterapi"))
        assertTrue(s1Titles.contains("aerasi") || s1Titles.contains("jarak"))

        // Stage 2: Klinometer, Ceptometer, Asam Humat
        val s2 = DailyMissions.planFor(today, 20)
        val s2Titles = s2.missions.joinToString(" ") { it.title.lowercase() }
        assertTrue(s2Titles.contains("klinometer") || s2Titles.contains("kemiringan"))
        assertTrue(s2Titles.contains("ceptometer") || s2Titles.contains("lux"))
        assertTrue(s2Titles.contains("asam humat"))

        // Stage 3: Barometer, Penyerbukan getar, Kelopak
        val s3 = DailyMissions.planFor(today, 45)
        val s3Titles = s3.missions.joinToString(" ") { it.title.lowercase() }
        assertTrue(s3Titles.contains("barometer") || s3Titles.contains("tekanan"))
        assertTrue(s3Titles.contains("penyerbukan") || s3Titles.contains("ajir"))
        assertTrue(s3Titles.contains("kelopak") || s3Titles.contains("gugur"))

        // Stage 4: Termistor mulsa, MgSO4 lilin kutikula, Perompesan daun
        val s4 = DailyMissions.planFor(today, 65)
        val s4Titles = s4.missions.joinToString(" ") { it.title.lowercase() }
        assertTrue(s4Titles.contains("mulsa") || s4Titles.contains("termal"))
        assertTrue(s4Titles.contains("kutikula") || s4Titles.contains("magnesium"))
        assertTrue(s4Titles.contains("perompesan") || s4Titles.contains("defoliasi"))

        // Stage 5: Nodus tangkai, CIELAB kamera, Pre-cooling
        val s5 = DailyMissions.planFor(today, 100)
        val s5Titles = s5.missions.joinToString(" ") { it.title.lowercase() }
        assertTrue(s5Titles.contains("nodus") || s5Titles.contains("tangkai"))
        assertTrue(s5Titles.contains("cielab") || s5Titles.contains("kapsantin"))
        assertTrue(s5Titles.contains("pre-cooling") || s5Titles.contains("pendinginan"))
    }

    @Test
    fun testIndonesianTtsBriefingAndSopScript() {
        val briefing = id.peritanix.dape.android.core.hardware.IndonesianTtsVoiceAdvisor.composeDailyMissionsBriefingScript(
            stageTitle = "Tahap 3",
            phaseName = "Pembungaan",
            hst = 45,
            score = 80,
            exportBadge = "Grade A (Pasar Swalayan)",
            totalMissions = 9,
            completedMissions = 4,
            nextFocusTitle = "Aplikasi Kalsium Nitrat"
        )
        assertTrue(briefing.contains("Pembungaan"))
        assertTrue(briefing.contains("45 hari setelah tanam"))
        assertTrue(briefing.contains("80 persen"))
        assertTrue(briefing.contains("Aplikasi Kalsium Nitrat"))

        val sopScript = id.peritanix.dape.android.core.hardware.IndonesianTtsVoiceAdvisor.composeMissionSopScript(
            title = "Aplikasi Kalsium Nitrat",
            qualityTarget = "Pericarp Tebal & Keras",
            howToExecute = "Kocorkan 200 ml per lubang tanam",
            targetMetric = "Tebal dinding sel > 1.8 mm",
            optimalTiming = "Pagi hari pukul 06:30 - 08:30 WIB",
            executionSteps = listOf("Takar 3 gram pupuk", "Kocorkan 200 ml ke lubang tanam"),
            commonMistakesToAvoid = listOf("Mencampur kalsium nitrat dengan pupuk fosfat")
        )
        assertTrue(sopScript.contains("Aplikasi Kalsium Nitrat"))
        assertTrue(sopScript.contains("Pericarp Tebal & Keras"))
        assertTrue(sopScript.contains("06:30 - 08:30 WIB"))
        assertTrue(sopScript.contains("Langkah 1, Takar 3 gram pupuk"))
        assertTrue(sopScript.contains("Pantangan penting: Mencampur kalsium nitrat"))
        assertTrue(sopScript.contains("1.8 mm"))
    }
}

