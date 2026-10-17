package id.peritanix.dape.android

import id.peritanix.dape.android.core.crypto.CertificatePayloadBuilder
import id.peritanix.dape.android.core.crypto.CertificatePayloadBuilder.BuildResult
import id.peritanix.dape.android.core.crypto.CertificatePayloadBuilder.Inputs
import id.peritanix.dape.android.core.sync.PrefetchPlanner
import id.peritanix.dape.android.core.sync.PrefetchPlanner.ConnectionState
import id.peritanix.dape.android.core.sync.PrefetchPlanner.Job
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the two Tahap 5.3 / 7.2 deliverables:
 *  - CertificatePayloadBuilder (canonical harvest certificate aggregation + gates)
 *  - PrefetchPlanner (predictive pre-fetch job scheduling)
 */
class CertificateAndPrefetchTest {

    private val goodHash = "a".repeat(64)

    private fun validInputs() = Inputs(
        landId = "land-001",
        cecMeq = 18.5,
        gddFortificationCompliant = true,
        boilingAcousticVerified = true,
        gyroImmersionSeconds = 180,
        archimedesSinkRatio = 0.82,
        gradeResult = "A+",
        batchPremiumPct = 88.4,
        imageSha256Hex = goodHash,
        timestampEpochS = 1_700_000_000L
    )

    // ---- CertificatePayloadBuilder -----------------------------------------

    @Test
    fun testBuildValidPayloadHasAllKeys() {
        val r = CertificatePayloadBuilder.build(validInputs())
        assertTrue(r is BuildResult.Ok)
        val payload = (r as BuildResult.Ok).payload
        CertificatePayloadBuilder.REQUIRED_KEYS.forEach { k ->
            assertTrue("missing key $k", payload.containsKey(k))
        }
        assertEquals("land-001", payload["land_id"])
        assertEquals("A+", payload["grade_result"])
        assertEquals(0.82, payload["archimedes_sink_ratio"] as Double, 0.001)
        assertEquals(180, payload["gyro_immersion_seconds"])
        assertEquals(goodHash, payload["image_sha256_hash"])
    }

    @Test
    fun testBuildDeterministicAndOrdered() {
        val a = CertificatePayloadBuilder.build(validInputs()) as BuildResult.Ok
        val b = CertificatePayloadBuilder.build(validInputs()) as BuildResult.Ok
        assertEquals(a.payload, b.payload)
        // insertion order stable (linkedMapOf) for canonical signing
        assertEquals(CertificatePayloadBuilder.REQUIRED_KEYS, a.payload.keys.toList())
    }

    @Test
    fun testBuildRejectsBlankLandId() {
        val r = CertificatePayloadBuilder.build(validInputs().copy(landId = " "))
        assertTrue(r is BuildResult.Invalid)
    }

    @Test
    fun testBuildRejectsBadImageHash() {
        assertTrue(CertificatePayloadBuilder.build(validInputs().copy(imageSha256Hex = "xyz")) is BuildResult.Invalid)
        assertTrue(CertificatePayloadBuilder.build(validInputs().copy(imageSha256Hex = "a".repeat(63))) is BuildResult.Invalid)
    }

    @Test
    fun testBuildRejectsUnverifiedBoiling() {
        val r = CertificatePayloadBuilder.build(validInputs().copy(boilingAcousticVerified = false))
        assertTrue(r is BuildResult.Invalid)
        assertTrue((r as BuildResult.Invalid).reason.contains("akustik", ignoreCase = true))
    }

    @Test
    fun testBuildRejectsShortImmersion() {
        val r = CertificatePayloadBuilder.build(validInputs().copy(gyroImmersionSeconds = 120))
        assertTrue(r is BuildResult.Invalid)
        assertTrue((r as BuildResult.Invalid).reason.contains("180", ignoreCase = false) ||
            r.reason.contains("perendaman", ignoreCase = true))
    }

    @Test
    fun testBuildRejectsOutOfRangeArchimedes() {
        assertTrue(CertificatePayloadBuilder.build(validInputs().copy(archimedesSinkRatio = 1.5)) is BuildResult.Invalid)
        assertTrue(CertificatePayloadBuilder.build(validInputs().copy(archimedesSinkRatio = -0.1)) is BuildResult.Invalid)
    }

    @Test
    fun testBuildRejectsUnknownGrade() {
        assertTrue(CertificatePayloadBuilder.build(validInputs().copy(gradeResult = "Z")) is BuildResult.Invalid)
    }

    @Test
    fun testHashLowercased() {
        val upperHash = "A".repeat(64)
        val r = CertificatePayloadBuilder.build(validInputs().copy(imageSha256Hex = upperHash)) as BuildResult.Ok
        assertEquals(upperHash.lowercase(), r.payload["image_sha256_hash"])
    }

    // ---- PrefetchPlanner ----------------------------------------------------

    @Test
    fun testNoWifiDefersAll() {
        val plan = PrefetchPlanner.plan(ConnectionState(isWifi = false, isCharging = true, batteryPct = 90, freeStorageMb = 500))
        assertTrue(plan.jobs.isEmpty())
        assertTrue(plan.rationale.contains("seluler", ignoreCase = true))
    }

    @Test
    fun testWifiNotChargingSkipsOta() {
        val plan = PrefetchPlanner.plan(ConnectionState(isWifi = true, isCharging = false, batteryPct = 70, freeStorageMb = 500))
        assertTrue(plan.jobs.contains(Job.WEATHER_7D))
        assertTrue(plan.jobs.contains(Job.NEIGHBOR_RISK_5KM))
        assertFalse("OTA must be skipped when not charging", plan.jobs.contains(Job.OTA_MODELS))
    }

    @Test
    fun testWifiChargingIncludesOta() {
        val plan = PrefetchPlanner.plan(ConnectionState(isWifi = true, isCharging = true, batteryPct = 50, freeStorageMb = 500))
        assertTrue(plan.jobs.contains(Job.OTA_MODELS))
        assertEquals(3, plan.jobs.size)
    }

    @Test
    fun testLowStorageSkipsOta() {
        val plan = PrefetchPlanner.plan(ConnectionState(isWifi = true, isCharging = true, batteryPct = 80, freeStorageMb = 20))
        assertFalse(plan.jobs.contains(Job.OTA_MODELS))
    }

    @Test
    fun testTotalKbMatchesJobs() {
        val plan = PrefetchPlanner.plan(ConnectionState(isWifi = true, isCharging = true, batteryPct = 80, freeStorageMb = 500))
        assertEquals(plan.jobs.sumOf { it.approxKb }, plan.totalKb)
    }
}
