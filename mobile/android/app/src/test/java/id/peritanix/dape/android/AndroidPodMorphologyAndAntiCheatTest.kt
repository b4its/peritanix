package id.peritanix.dape.android

import id.peritanix.dape.android.core.physics.ImmersionTraceAuditor
import id.peritanix.dape.android.core.physics.PodMorphologyGrader
import org.junit.Assert.*
import org.junit.Test

class AndroidPodMorphologyAndAntiCheatTest {

    @Test
    fun testGradeAPlusExportQualityPod() {
        val measurement = PodMorphologyGrader.PodMeasurement(
            lengthCm = 13.5,
            curvatureDeg = 10.0,
            redCoveragePct = 95.0,
            defectPct = 1.0,
            archimedesSinkRatio = 0.88
        )
        val verdict = PodMorphologyGrader.evaluateGrade(measurement)
        assertEquals("A+", verdict.finalGrade)
        assertEquals("Export Prime Quality", verdict.commercialTier)
        assertTrue(verdict.isExportQuality)
        assertEquals(1.35, verdict.priceMultiplier, 0.01)
    }

    @Test
    fun testGradeBSDefectiveOrLowDensityPod() {
        val defective = PodMorphologyGrader.PodMeasurement(
            lengthCm = 7.2,
            curvatureDeg = 50.0,
            redCoveragePct = 40.0,
            defectPct = 18.0,
            archimedesSinkRatio = 0.35
        )
        val verdict = PodMorphologyGrader.evaluateGrade(defective)
        assertEquals("BS", verdict.finalGrade)
        assertEquals("Industri Olahan / Saus Sambal", verdict.commercialTier)
        assertFalse(verdict.isExportQuality)
        assertEquals(0.60, verdict.priceMultiplier, 0.01)
    }

    @Test
    fun testImmersionTraceAuditorDetectsRealMotionVsStaticCheat() {
        val auditor = ImmersionTraceAuditor()

        // Static phone cheating (phone left on desk: values near 9.8 with zero variation)
        for (i in 0 until 20) {
            auditor.recordVector(0.01f, 9.80f, 0.02f)
        }
        val staticResult = auditor.evaluateTrace()
        assertFalse("Smartphone diam statis harus terdeteksi sebagai FRAUD", staticResult.isValidRealActivity)
        assertTrue(staticResult.statusMessage.contains("FRAUD DETECTED"))

        // Authentic dynamic stirring (operator gently stirring hot water, varied acceleration)
        auditor.clear()
        val magnitudes = doubleArrayOf(
            9.2, 10.5, 9.6, 11.2, 8.8, 10.1, 9.4, 11.8, 9.0, 10.4,
            9.1, 10.8, 8.9, 11.0, 9.5, 10.2, 9.3, 11.5, 9.0, 10.6
        )
        for (m in magnitudes) {
            auditor.recordMagnitude(m)
        }
        val motionResult = auditor.evaluateTrace()
        assertTrue("Pengadukan nyata harus lolos audit jejak giroskop", motionResult.isValidRealActivity)
        assertTrue(motionResult.statusMessage.contains("JEJAK GERAK OTENTIK"))
        assertTrue("Variansi harus >= 0.05", motionResult.variance >= 0.05)
    }

    @Test
    fun testImmersionPhotoSha256Digest() {
        val auditor = ImmersionTraceAuditor()
        val testBytes = "dape-chili-harvest-verification".toByteArray(Charsets.UTF_8)
        val hash = auditor.computeSha256(testBytes)
        assertEquals(64, hash.length) // 64 hex characters
    }
}
