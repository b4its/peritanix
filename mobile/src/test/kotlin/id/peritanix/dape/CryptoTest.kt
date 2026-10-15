package id.peritanix.dape

import id.peritanix.dape.crypto.CanonicalJson
import id.peritanix.dape.crypto.Ed25519OfflineSigner
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CryptoTest {

    @Test fun canonicalJsonSortsKeys() {
        val a = mapOf("b" to 2, "a" to 1, "c" to mapOf("z" to true, "y" to false))
        assertEquals("""{"a":1.0,"b":2.0,"c":{"y":false,"z":true}}""", CanonicalJson.encode(a))
    }

    @Test fun canonicalJsonIsOrderIndependent() {
        val a = mapOf("x" to 1, "y" to 2)
        val b = mapOf("y" to 2, "x" to 1)
        assertEquals(CanonicalJson.encode(a), CanonicalJson.encode(b))
    }

    /** Signature made by the Kotlin signer must verify with a raw Ed25519 verifier. */
    @Test fun signatureVerifiesWithRawVerifier() {
        val kp = Ed25519OfflineSigner.fromSeed(ByteArray(32) { (it + 1).toByte() })
        val payload = mapOf<String, Any?>(
            "land_id" to "aaa-111",
            "grade_result" to "A",
            "archimedes_sink_ratio" to 0.82,
        )
        val sig = Ed25519OfflineSigner.sign(payload, kp)
        val msg = CanonicalJson.encode(payload).toByteArray(Charsets.UTF_8)

        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(kp.publicKey.encoded, 0))
        verifier.update(msg, 0, msg.size)
        assertTrue(verifier.verifySignature(sig), "signature should verify")
    }

    @Test fun tamperedPayloadFailsVerification() {
        val kp = Ed25519OfflineSigner.fromSeed(ByteArray(32) { (it + 2).toByte() })
        val payload = mapOf<String, Any?>("land_id" to "aaa-111", "grade_result" to "A")
        val sig = Ed25519OfflineSigner.sign(payload, kp)

        val tampered = CanonicalJson.encode(
            mapOf<String, Any?>("land_id" to "aaa-111", "grade_result" to "BS"),
        ).toByteArray(Charsets.UTF_8)

        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(kp.publicKey.encoded, 0))
        verifier.update(tampered, 0, tampered.size)
        assertTrue(!verifier.verifySignature(sig), "tampered payload must fail")
    }

    @Test fun certificateContainsAllFields() {
        val kp = Ed25519OfflineSigner.fromSeed(ByteArray(32) { (it + 3).toByte() })
        val cert = Ed25519OfflineSigner.certificate(mapOf("land_id" to "x"), kp)
        assertTrue(cert.contains("\"payload\""))
        assertTrue(cert.contains("\"signature\""))
        assertTrue(cert.contains("\"public_key\""))
    }
}
