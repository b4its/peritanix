package id.peritanix.dape.android.core.crypto

import java.security.MessageDigest

/**
 * TAHAP 5.3 — Offline forensic image hashing for the physical-quality
 * certificate.
 *
 * The Crypto-QR certificate payload embeds `image_sha256_hash` (the exact bytes
 * of the grading/archimedes photo) so a buyer's verifier can confirm the photo
 * was not swapped after signing. It also produces a coarse perceptual hash
 * (aHash) used to detect "same photo reused from a previous batch" — a cheap
 * anti-fraud check the server later corroborates with ELA.
 *
 * Pure (JDK MessageDigest only) → fully unit-testable offline.
 */
object ForensicHasher {

    /** SHA-256 of raw image bytes, hex-encoded (lowercase, 64 chars). */
    fun sha256Hex(bytes: ByteArray): String = toHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /**
     * 64-bit average hash (aHash) of a grayscale grid. Pass a downsampled
     * luminance matrix (e.g. 8×8); pixels above the mean become '1', below '0'.
     * Returns a 16-char hex string (64 bits).
     */
    fun perceptualAHash(luminanceGrid: IntArray): String {
        if (luminanceGrid.isEmpty()) return "0".repeat(16)
        val mean = luminanceGrid.average()
        var bits = 0L
        for (i in 0 until minOf(64, luminanceGrid.size)) {
            if (luminanceGrid[i] > mean) bits = bits or (1L shl (63 - i))
        }
        return bits.toULong().toString(16).padStart(16, '0')
    }

    /** Hamming distance between two aHash hex strings (0 = identical structure). */
    fun hammingDistance(hashA: String, hashB: String): Int {
        if (hashA.length != hashB.length) return Int.MAX_VALUE
        return hashA.zip(hashB).count { (a, b) -> a != b }
    }

    /** True when two aHashes are near-duplicates (≤ [maxDistance] differing nibbles). */
    fun isNearDuplicate(hashA: String, hashB: String, maxDistance: Int = 3): Boolean =
        hammingDistance(hashA, hashB) <= maxDistance

    /**
     * Deterministic hash of a certificate field map for tamper-evidence. The
     * fields are canonicalised as `key=value` joined by ';' in sorted key order
     * (stable across platforms, mirroring the server's canonical JSON rule).
     */
    fun certificateFieldHash(fields: Map<String, String>): String {
        val canonical = fields.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" }
        return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    private fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
