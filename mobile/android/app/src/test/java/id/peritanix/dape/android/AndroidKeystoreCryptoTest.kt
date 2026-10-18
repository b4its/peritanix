package id.peritanix.dape.android

import id.peritanix.dape.crypto.Ed25519OfflineSigner
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class AndroidKeystoreCryptoTest {

    @Test
    fun testKeypairGenerationAndSigningDeterminism() {
        // Derive keypair from fixed 32-byte seed
        val seed = ByteArray(32) { (it + 1).toByte() }
        val kp = Ed25519OfflineSigner.fromSeed(seed)

        val pubB64 = Base64.getEncoder().encodeToString(kp.publicKey.encoded)
        assertNotNull("Public key Base64 tidak boleh null", pubB64)
        assertEquals(44, pubB64.length) // Standard 32-byte Ed25519 pubkey in base64 is 44 chars

        val payload = mapOf(
            "land_id" to "land-peritanix-001",
            "grade_result" to "A",
            "archimedes_sink_ratio" to 0.85,
            "sterilization_c" to 52.0
        )

        val certJson = Ed25519OfflineSigner.certificate(payload, kp)
        assertTrue("Sertifikat harus memuat payload", certJson.contains("\"payload\":"))
        assertTrue("Sertifikat harus memuat signature", certJson.contains("\"signature\":\""))
        assertTrue("Sertifikat harus memuat public_key", certJson.contains("\"public_key\":\""))
    }

    @Test
    fun testVerifySignatureDirectly() {
        val seed = ByteArray(32) { (it + 5).toByte() }
        val kp = Ed25519OfflineSigner.fromSeed(seed)

        val payload = mapOf(
            "land_id" to "land-peritanix-002",
            "grade_result" to "A+",
            "archimedes_sink_ratio" to 0.88,
            "sterilization_c" to 52.0
        )

        val sig = Ed25519OfflineSigner.sign(payload, kp)
        val valid = Ed25519OfflineSigner.verify(payload, sig, kp.publicKey.encoded)
        assertTrue("Tanda tangan Ed25519 harus valid secara matematis", valid)

        // Test with corrupted signature
        val corruptedSig = sig.copyOf()
        corruptedSig[0] = (corruptedSig[0] + 1).toByte()
        val invalid = Ed25519OfflineSigner.verify(payload, corruptedSig, kp.publicKey.encoded)
        assertFalse("Tanda tangan yang diubah 1 byte harus ditolak", invalid)
    }

    @Test
    fun testVerifyCertificateGradeAPlus() {
        val seed = ByteArray(32) { (it * 3).toByte() }
        val kp = Ed25519OfflineSigner.fromSeed(seed)

        val payload = mapOf(
            "land_id" to "land-peritanix-001",
            "cec_meq" to 18.5,
            "gdd_fortification_compliant" to true,
            "sterilization_c" to 52.0,
            "acoustic_boiling_verified" to true,
            "gyro_immersion_seconds" to 180,
            "archimedes_sink_ratio" to 0.89,
            "grade_result" to "A+",
            "batch_premium_pct" to 94.0,
            "image_sha256_hash" to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            "timestamp" to 1700000000
        )

        val certJson = Ed25519OfflineSigner.certificate(payload, kp)
        val verdict = Ed25519OfflineSigner.verifyCertificate(certJson)

        assertTrue("Sertifikat harus otentik", verdict.isAuthentic)
        assertEquals("A+", verdict.grade)
        assertTrue("Harus memenuhi kualifikasi Grade A+ Export Prime", verdict.isGradeAPlus)
        assertEquals(1.35, verdict.priceMultiplier, 0.001)
        assertTrue("Badge harus memuat TERVERIFIKASI ASLI - MUTU GRADE A+", verdict.badgeTitle.contains("GRADE A+"))
        assertTrue("Pericarp harus terfortifikasi", verdict.pericarpFortified)
        assertTrue("Sterilisasi 52C harus terverifikasi", verdict.sterilized52C)
        assertEquals("land-peritanix-001", verdict.landId)
    }

    @Test
    fun testVerifyCertificateTamperDetected() {
        val seed = ByteArray(32) { (it + 7).toByte() }
        val kp = Ed25519OfflineSigner.fromSeed(seed)

        val payload = mapOf(
            "land_id" to "land-peritanix-001",
            "grade_result" to "B",
            "archimedes_sink_ratio" to 0.45,
            "sterilization_c" to 52.0
        )

        val certJson = Ed25519OfflineSigner.certificate(payload, kp)

        // Attacker tampers with the payload in the JSON string: alters "B" to "A+"
        val tamperedJson = certJson.replace("\"grade_result\":\"B\"", "\"grade_result\":\"A+\"")

        val verdict = Ed25519OfflineSigner.verifyCertificate(tamperedJson)
        assertFalse("Sertifikat yang dimanipulasi harus ditolak (fraud detected)", verdict.isAuthentic)
        assertEquals("FRAUD", verdict.grade)
        assertTrue("Badge harus memperingatkan fraud", verdict.badgeTitle.contains("FRAUD DETECTED"))
    }

    @Test
    fun testVerifyCertificateMalformedJson() {
        val verdict = Ed25519OfflineSigner.verifyCertificate("bukan json yang valid")
        assertFalse("JSON rusak harus tidak valid", verdict.isAuthentic)
        assertEquals("INVALID", verdict.grade)
    }
}
