package id.peritanix.dape.android

import id.peritanix.dape.android.core.ai_edge.EdgeInferenceRunner
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AndroidEdgeInferenceTest {

    @Test
    fun testBegomovirusYellowProxyClassification() {
        val runner = EdgeInferenceRunner(File("/tmp/models"))
        val width = 10
        val height = 10
        val numPixels = width * height
        // Yellow dominant: High R, High G, Low B
        val buffer = FloatArray(numPixels * 3)
        for (i in 0 until numPixels) {
            buffer[i] = 0.85f                  // R
            buffer[numPixels + i] = 0.80f      // G
            buffer[2 * numPixels + i] = 0.10f  // B
        }
        val result = runner.classifyLeaf(buffer, width, height)
        assertEquals("Begomovirus", result.topCondition)
        assertTrue(result.confidence > 0.85f)
        assertTrue(result.isLocalInference)
    }

    @Test
    fun testAnthracnoseDarkProxyClassification() {
        val runner = EdgeInferenceRunner(File("/tmp/models"))
        val width = 10
        val height = 10
        val numPixels = width * height
        // Brown/Dark lesions: R > 0.5, G < 0.4
        val buffer = FloatArray(numPixels * 3)
        for (i in 0 until numPixels) {
            buffer[i] = 0.55f
            buffer[numPixels + i] = 0.30f
            buffer[2 * numPixels + i] = 0.15f
        }
        val result = runner.classifyLeaf(buffer, width, height)
        assertEquals("Antraknosa", result.topCondition)
    }

    @Test
    fun testHealthyLeafProxyClassification() {
        val runner = EdgeInferenceRunner(File("/tmp/models"))
        val width = 10
        val height = 10
        val numPixels = width * height
        // Normal green leaf
        val buffer = FloatArray(numPixels * 3)
        for (i in 0 until numPixels) {
            buffer[i] = 0.20f
            buffer[numPixels + i] = 0.65f
            buffer[2 * numPixels + i] = 0.25f
        }
        val result = runner.classifyLeaf(buffer, width, height)
        assertEquals("Daun_Sehat", result.topCondition)
    }
}
