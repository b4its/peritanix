package id.peritanix.dape.android

import id.peritanix.dape.android.core.ai_edge.MultiInstancePodSegmentationEngine
import org.junit.Assert.*
import org.junit.Test

class MultiInstancePodSegmentationEngineTest {

    @Test
    fun testOpticalScaleMmPerPixel() {
        // At 3.0 diopters (33.3 cm distance), optical scale should be ~0.125 mm/pixel
        val scale = MultiInstancePodSegmentationEngine.calculateOpticalMmPerPixel(
            lensFocusDistanceDiopters = 3.0f,
            sensorWidthMm = 6.4,
            focalLengthMm = 4.38,
            imageWidthPx = 1920
        )
        assertTrue("Scale should be positive and realistic", scale in 0.05..0.50)
    }

    @Test
    fun testAngleLockingDetection() {
        val flat = MultiInstancePodSegmentationEngine.detectAndSegmentTable(
            podCount = 16,
            tiltAngleDeg = 1.5
        )
        assertTrue("Angle <= 3.0 deg should be locked", flat.isTopDownAngleLocked)

        val tilted = MultiInstancePodSegmentationEngine.detectAndSegmentTable(
            podCount = 16,
            tiltAngleDeg = 6.8
        )
        assertFalse("Angle > 3.0 deg should warn user", tilted.isTopDownAngleLocked)
        assertTrue(tilted.advisoryMessage.contains("Peringatan Kemiringan"))
    }

    @Test
    fun testDefectivePodDetectionAndRejectOverlay() {
        val result = MultiInstancePodSegmentationEngine.detectAndSegmentTable(
            podCount = 16,
            tiltAngleDeg = 0.5
        )

        assertEquals(16, result.totalPodsDetected)
        assertEquals(16, result.activePods.size)
        // Two pods are seeded with critical defects (Anthracnose & Blossom-end rot)
        assertTrue("Should detect reject pods", result.rejectPods.isNotEmpty())
        assertTrue("BS count should be >= 2", result.countByGrade["BS"] ?: 0 >= 2)

        val anthracnosePod = result.rejectPods.firstOrNull {
            it.defectType == MultiInstancePodSegmentationEngine.DefectType.ANTHRACNOSE_PATEK
        }
        assertNotNull("Should detect active Anthracnose lesion", anthracnosePod)
        assertEquals("BS", anthracnosePod?.grade)
        assertTrue(anthracnosePod?.isReject == true)
        assertTrue(anthracnosePod?.rejectionReason?.contains("Antraknosa") == true)
    }

    @Test
    fun testRemoveRejectPodAndBatchRecalculation() {
        val initial = MultiInstancePodSegmentationEngine.detectAndSegmentTable(podCount = 16)
        val rejectCountBefore = initial.rejectPods.size
        assertTrue(rejectCountBefore > 0)

        val rejectId = initial.rejectPods.first().id
        val updated = MultiInstancePodSegmentationEngine.removeRejectPod(initial, rejectId)

        assertEquals("Reject count should decrement by 1", rejectCountBefore - 1, updated.rejectPods.size)
        assertEquals(initial.activePods.size - 1, updated.activePods.size)

        // Now remove all rejects
        val cleaned = MultiInstancePodSegmentationEngine.removeAllRejects(initial)
        assertEquals(0, cleaned.rejectPods.size)
        assertEquals(0, cleaned.countByGrade["BS"])
        assertEquals(0.0, cleaned.rejectPct, 0.01)
        assertTrue("Premium % should be higher after cleaning", cleaned.premiumPct >= initial.premiumPct)
        assertTrue("Market suitability should indicate export or supermarket grade", cleaned.marketSuitability.contains("LULUS"))
    }
}
