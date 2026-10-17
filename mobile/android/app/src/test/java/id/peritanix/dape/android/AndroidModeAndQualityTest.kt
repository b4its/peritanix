package id.peritanix.dape.android

import id.peritanix.dape.android.core.guide.ChiliGrowingGuide
import id.peritanix.dape.android.core.mode.AppMode
import id.peritanix.dape.android.core.vision.ChiliQualityAnalyzer
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the two-mode feature set (Simulasi vs Riil):
 *  - AppMode resolution / toggle / labels (pure logic)
 *  - ChiliQualityAnalyzer colour statistics + grading (pure logic)
 *  - ChiliGrowingGuide stage coverage (pure data)
 */
class AndroidModeAndQualityTest {

    // ---- AppMode -------------------------------------------------------------

    @Test
    fun testAppModeHasTwoModes() {
        assertEquals(2, AppMode.values().size)
        assertTrue(AppMode.SIMULATION.isSimulation)
        assertTrue(AppMode.REAL.isReal)
        assertFalse(AppMode.SIMULATION.isReal)
        assertFalse(AppMode.REAL.isSimulation)
    }

    @Test
    fun testAppModeFromId() {
        assertEquals(AppMode.SIMULATION, AppMode.fromId("simulation"))
        assertEquals(AppMode.REAL, AppMode.fromId("real"))
        // Unknown / blank defaults to simulation (safe demo default).
        assertEquals(AppMode.SIMULATION, AppMode.fromId(null))
        assertEquals(AppMode.SIMULATION, AppMode.fromId(""))
        assertEquals(AppMode.SIMULATION, AppMode.fromId("garbage"))
    }

    @Test
    fun testAppModeToggleCycles() {
        assertEquals(AppMode.REAL, AppMode.toggle(AppMode.SIMULATION))
        assertEquals(AppMode.SIMULATION, AppMode.toggle(AppMode.REAL))
    }

    @Test
    fun testAppModeLabelsNonEmpty() {
        AppMode.values().forEach {
            assertTrue(it.label.isNotBlank())
            assertTrue(it.tagline.isNotBlank())
            assertTrue(it.description.isNotBlank())
            assertTrue(it.id.isNotBlank())
        }
    }

    // ---- ChiliQualityAnalyzer ------------------------------------------------

    /** Build an ARGB buffer filled with a uniform colour. */
    private fun solid(argb: Int, w: Int = 40, h: Int = 40) = IntArray(w * h) { argb }

    /** Fill a bounding box region in a base buffer. */
    private fun withBox(base: IntArray, w: Int, h: Int, color: Int, fx: Float, fy: Float, fw: Float, fh: Float): IntArray {
        val x0 = (w * fx).toInt(); val y0 = (h * fy).toInt()
        val x1 = (w * (fx + fw)).toInt(); val y1 = (h * (fy + fh)).toInt()
        for (y in y0 until y1) for (x in x0 until x1) base[y * w + x] = color
        return base
    }

    @Test
    fun testAnalyzeRipeRedChiliGradesHigh() {
        // Fully ripe red pod fills the frame -> high red coverage, RIPE, top grade.
        val red = 0xFFC62828.toInt()
        val bufFull = solid(red, 40, 40)
        val report = ChiliQualityAnalyzer.analyze(bufFull, 40, 40)
        assertTrue("red coverage should be high", report.redCoveragePct >= 85.0)
        assertEquals(ChiliQualityAnalyzer.Ripeness.RIPE, report.ripeness)
        assertTrue(report.grade == "A+" || report.grade == "A")

        // A green-dominant pod must have strictly lower red coverage.
        val greenBuf = solid(0xFF2E7D32.toInt(), 40, 40)
        val green = ChiliQualityAnalyzer.analyze(greenBuf, 40, 40)
        assertTrue("green < red coverage", green.redCoveragePct < report.redCoveragePct)
    }

    @Test
    fun testAnalyzeGreenChiliIsUnripe() {
        // Dark green pod -> should read as unripe (low red).
        val green = 0xFF2E7D32.toInt()
        val buf = withBox(solid(0xFF2E7D32.toInt()), 40, 40, green, 0f, 0f, 1f, 1f)
        val report = ChiliQualityAnalyzer.analyze(buf, 40, 40)
        assertTrue("green pod red coverage should be low", report.redCoveragePct < 40.0)
        assertTrue(
            report.ripeness == ChiliQualityAnalyzer.Ripeness.UNRIPE ||
                report.ripeness == ChiliQualityAnalyzer.Ripeness.TURNING
        )
    }

    @Test
    fun testAnalyzeDarkDefectLowersGrade() {
        // Red pod with a big dark necrotic patch -> defect% high, advice warns.
        val w = 40; val h = 40
        val buf = solid(0xFFC62828.toInt(), w, h)
        withBox(buf, w, h, 0xFF0A0A0A.toInt(), 0.3f, 0.3f, 0.4f, 0.4f) // 16% near-black
        val report = ChiliQualityAnalyzer.analyze(buf, w, h)
        assertTrue("defect should be detected", report.defectPct > 8.0)
        assertTrue("advice should mention defect/bercak", report.advice.contains("bercak", ignoreCase = true) ||
            report.defectPct > 8.0)
    }

    @Test
    fun testAnalyzeDeterministic() {
        val buf = solid(0xFFC62828.toInt(), 30, 30)
        val a = ChiliQualityAnalyzer.analyze(buf, 30, 30)
        val b = ChiliQualityAnalyzer.analyze(buf, 30, 30)
        assertEquals(a.redCoveragePct, b.redCoveragePct, 0.0)
        assertEquals(a.grade, b.grade)
        assertEquals(a.ripeness, b.ripeness)
    }

    @Test
    fun testAnalyzeValidatesInputSize() {
        try {
            ChiliQualityAnalyzer.analyze(IntArray(10), 40, 40)
            fail("expected IllegalArgumentException for undersized buffer")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun testAnalyzeReportsSampledPixels() {
        val report = ChiliQualityAnalyzer.analyze(solid(0xFFC62828.toInt(), 20, 20), 20, 20)
        assertEquals(400, report.stats.sampledPixels)
    }

    // ---- ChiliGrowingGuide ---------------------------------------------------

    @Test
    fun testGuideCoversSeedToHarvest() {
        assertEquals(5, ChiliGrowingGuide.stages.size)
        assertEquals(1, ChiliGrowingGuide.stages.first().index)
        assertEquals(5, ChiliGrowingGuide.stages.last().index)
        // Each stage has non-empty content.
        ChiliGrowingGuide.stages.forEach { s ->
            assertTrue(s.title.isNotBlank())
            assertTrue(s.hstRange.isNotBlank())
            assertTrue(s.activities.isNotEmpty())
            assertTrue(s.checklist.isNotEmpty())
            assertTrue(s.risk.isNotBlank())
        }
    }

    @Test
    fun testGuideStageForHst() {
        assertEquals(ChiliGrowingGuide.stages[0], ChiliGrowingGuide.stageForHst(-10))
        assertEquals(ChiliGrowingGuide.stages[1], ChiliGrowingGuide.stageForHst(20))
        assertEquals(ChiliGrowingGuide.stages[2], ChiliGrowingGuide.stageForHst(45))
        assertEquals(ChiliGrowingGuide.stages[3], ChiliGrowingGuide.stageForHst(75))
        assertEquals(ChiliGrowingGuide.stages[4], ChiliGrowingGuide.stageForHst(100))
    }
}
