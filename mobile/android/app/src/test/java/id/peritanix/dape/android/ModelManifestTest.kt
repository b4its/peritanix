package id.peritanix.dape.android

import id.peritanix.dape.android.core.ai_edge.ModelManifest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the OTA continuous-learning model manifest client (Tahap 0.2 &
 * 7.2): JSON parsing, update-only download decisions, and the Wi-Fi+charging
 * fetch policy.
 */
class ModelManifestTest {

    private val sampleJson = """
        {
          "registry_version": "abc123def456",
          "update_count": 1,
          "models": [
            {"name":"classifier_mobilenetv3","version":"1.0.0","sha256":"aa","size_bytes":7484,"quantisation":"int8","update_available":true,"url":"http://x/models/classifier_mobilenetv3/1.0.0.onnx"},
            {"name":"fertilizer_lightgbm","version":"1.0.0","sha256":"bb","size_bytes":2001,"quantisation":"fp32","update_available":false,"url":"http://x/models/fertilizer_lightgbm/1.0.0.onnx"}
          ]
        }
    """.trimIndent()

    @Test fun testParseManifest() {
        val plan = ModelManifest.parse(sampleJson)
        assertNotNull(plan)
        plan!!
        assertEquals("abc123def456", plan.registryVersion)
        assertEquals(1, plan.updateCount)
        assertEquals(2, plan.models.size)
        val cls = plan.models.first { it.name == "classifier_mobilenetv3" }
        assertEquals("1.0.0", cls.version)
        assertTrue(cls.updateAvailable)
        assertEquals(7484L, cls.sizeBytes)
        assertEquals(0.01, cls.sizeMb, 0.01)
    }

    @Test fun testToDownloadOnlyUpdatable() {
        val plan = ModelManifest.parse(sampleJson)!!
        val dl = plan.toDownload
        assertEquals(1, dl.size)
        assertEquals("classifier_mobilenetv3", dl.first().name)
    }

    @Test fun testParseMalformedReturnsNull() {
        assertNull(ModelManifest.parse("{not valid json"))
    }

    @Test fun testParseMissingFieldsTolerant() {
        val plan = ModelManifest.parse("""{"models":[{"name":"x"}]}""")
        assertNotNull(plan)
        assertEquals(1, plan!!.models.size)
        assertEquals("x", plan.models[0].name)
        assertEquals(0L, plan.models[0].sizeBytes)
        assertFalse(plan.models[0].updateAvailable)
    }

    @Test fun testDecideDownloadsWhenVersionDiffers() {
        val registry = ModelManifest.parse(sampleJson)!!.models
        // Device has the classifier at an OLD version → must download.
        val installed = mapOf("classifier_mobilenetv3" to "0.9.0", "fertilizer_lightgbm" to "1.0.0")
        val dl = ModelManifest.decide(registry, installed)
        assertEquals(1, dl.size)
        assertEquals("classifier_mobilenetv3", dl.first().name)
    }

    @Test fun testDecideFirstRunDownloadsAll() {
        val registry = ModelManifest.parse(sampleJson)!!.models
        val dl = ModelManifest.decide(registry, emptyMap())
        assertEquals(2, dl.size) // nothing installed → download all
    }

    @Test fun testDecideUpToDateDownloadsNothing() {
        val registry = ModelManifest.parse(sampleJson)!!.models
        val installed = mapOf("classifier_mobilenetv3" to "1.0.0", "fertilizer_lightgbm" to "1.0.0")
        assertTrue(ModelManifest.decide(registry, installed).isEmpty())
    }

    @Test fun testOtaPolicyWifiCharging() {
        assertTrue(ModelManifest.OtaPolicy(isWifi = true, isCharging = true, batteryPct = 20).shouldFetch)
        // Wi-Fi, not charging, but plenty of battery → allowed.
        assertTrue(ModelManifest.OtaPolicy(isWifi = true, isCharging = false, batteryPct = 60).shouldFetch)
        // Wi-Fi, low battery, not charging → skip.
        assertFalse(ModelManifest.OtaPolicy(isWifi = true, isCharging = false, batteryPct = 20).shouldFetch)
        // Cellular → never auto-fetch weights.
        assertFalse(ModelManifest.OtaPolicy(isWifi = false, isCharging = true, batteryPct = 90).shouldFetch)
    }

    @Test fun testTotalDownloadMb() {
        val plan = ModelManifest.parse(sampleJson)!!
        // Only classifier (7484 bytes) qualifies → ~0.01 MB.
        assertTrue(plan.totalDownloadMb in 0.0..0.02)
    }
}
