package id.peritanix.dape.android

import id.peritanix.dape.android.core.crypto.ForensicHasher
import id.peritanix.dape.android.core.physics.BatchGradingSummary
import id.peritanix.dape.android.core.physics.BatchGradingSummary.GradedPod
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Tahap 5.2/5.3 deliverables:
 *  - ForensicHasher (image SHA-256, perceptual aHash, near-dup, cert field hash)
 *  - BatchGradingSummary (sortation recap by weight %)
 */
class ForensicAndBatchGradingTest {

    // ---- ForensicHasher -----------------------------------------------------

    @Test
    fun testSha256KnownVector() {
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        val hex = ForensicHasher.sha256Hex("abc".toByteArray())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex)
        assertEquals(64, hex.length)
    }

    @Test
    fun testSha256Empty() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ForensicHasher.sha256Hex(ByteArray(0)))
    }

    @Test
    fun testSha256Deterministic() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        assertEquals(ForensicHasher.sha256Hex(bytes), ForensicHasher.sha256Hex(bytes))
        assertNotEquals(ForensicHasher.sha256Hex(bytes), ForensicHasher.sha256Hex(byteArrayOf(1, 2, 3, 4, 6)))
    }

    @Test
    fun testPerceptualAHash() {
        // Bright-vs-dark halves: first 4 bright, last 4 dark → above-mean bits set on the left.
        val grid = intArrayOf(200, 200, 200, 200, 10, 10, 10, 10)
        val hash = ForensicHasher.perceptualAHash(grid)
        assertEquals(16, hash.length)
        // Two identical grids → identical hash.
        assertEquals(hash, ForensicHasher.perceptualAHash(grid))
    }

    @Test
    fun testHammingDistance() {
        assertEquals(0, ForensicHasher.hammingDistance("ffffffffffffffff", "ffffffffffffffff"))
        assertEquals(16, ForensicHasher.hammingDistance("ffffffffffffffff", "0000000000000000"))
        assertEquals(Int.MAX_VALUE, ForensicHasher.hammingDistance("abc", "abcd"))
    }

    @Test
    fun testNearDuplicate() {
        assertTrue(ForensicHasher.isNearDuplicate("ffffffffffffffff", "ffffffffffffffff"))
        // 2 nibbles differ → within default tolerance 3.
        assertTrue(ForensicHasher.isNearDuplicate("ffffffffffffffff", "ff00ffffffffffff", maxDistance = 3))
        // Very different → not duplicate.
        assertFalse(ForensicHasher.isNearDuplicate("ffffffffffffffff", "0000000000000000"))
    }

    @Test
    fun testCertificateFieldHashOrderIndependent() {
        val a = mapOf("land_id" to "L1", "grade" to "A", "weight" to "12.5")
        val b = mapOf("grade" to "A", "weight" to "12.5", "land_id" to "L1") // different insertion order
        assertEquals(ForensicHasher.certificateFieldHash(a), ForensicHasher.certificateFieldHash(b))
        // Changing a value changes the hash.
        val c = a.toMutableMap().apply { put("grade", "B") }
        assertNotEquals(ForensicHasher.certificateFieldHash(a), ForensicHasher.certificateFieldHash(c))
    }

    // ---- BatchGradingSummary -----------------------------------------------

    @Test
    fun testEmptyBatch() {
        val s = BatchGradingSummary.summarize(emptyList())
        assertEquals(0, s.totalPods)
        assertEquals("Belum ada buah tersortir.", s.batchVerdict)
    }

    @Test
    fun testWeightPercentagesSumTo100() {
        val pods = listOf(
            GradedPod("A+", 15.0), GradedPod("A+", 14.0),   // 29 g premium
            GradedPod("A", 12.0),                            // 12 g
            GradedPod("B", 10.0),                            // 10 g
            GradedPod("BS", 9.0)                             // 9 g
        )
        val s = BatchGradingSummary.summarize(pods)
        assertEquals(5, s.totalPods)
        assertEquals(60.0, s.totalWeightG, 0.01)
        val sum = s.weightPctByGrade.values.sum()
        assertEquals(100.0, sum, 0.5)
        // A+ = 29/60 = 48.3%
        assertEquals(48.3, s.weightPctByGrade["A+"]!!, 0.2)
        // premium = A+ + A = (29+12)/60 = 68.3%
        assertEquals(68.3, s.premiumPct, 0.2)
    }

    @Test
    fun testPremiumBatchVerdict() {
        val pods = listOf(GradedPod("A+", 20.0), GradedPod("A", 18.0), GradedPod("BS", 2.0))
        val s = BatchGradingSummary.summarize(pods)
        assertTrue(s.premiumPct >= 85.0)
        assertTrue(s.batchVerdict.contains("premium", ignoreCase = true))
    }

    @Test
    fun testRejectHeavyVerdict() {
        val pods = listOf(GradedPod("B", 10.0), GradedPod("BS", 6.0), GradedPod("BS", 5.0))
        val s = BatchGradingSummary.summarize(pods)
        assertTrue(s.rejectPct >= 25.0)
        assertTrue(s.batchVerdict.contains("sortiran", ignoreCase = true))
    }

    @Test
    fun testUnknownGradeTreatedAsBS() {
        val s = BatchGradingSummary.summarize(listOf(GradedPod("X", 10.0), GradedPod("a+", 10.0)))
        assertEquals(1, s.countByGrade["BS"])
        assertEquals(1, s.countByGrade["A+"]) // case-insensitive normalisation
    }
}
