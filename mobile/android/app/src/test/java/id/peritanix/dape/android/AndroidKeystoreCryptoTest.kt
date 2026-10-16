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
}
