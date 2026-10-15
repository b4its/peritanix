package id.peritanix.dape.crypto

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom
import java.util.Base64

/**
 * Offline Ed25519 certificate signer for the DAPE post-harvest quality QR.
 *
 * Produces a canonical JSON payload signature that the Go backend verifies
 * byte-for-byte (see internal/infra/crypto/ed25519.go and CanonicalJSON).
 * On a real device the private key lives in the Android Keystore; here we
 * accept an injected seed so the logic is unit-testable on the JVM.
 */
object Ed25519OfflineSigner {

    data class KeyPair(val privateKey: Ed25519PrivateKeyParameters, val publicKey: Ed25519PublicKeyParameters)

    /** Generate a fresh keypair (device onboarding). */
    fun generateKeyPair(secureRandom: SecureRandom = SecureRandom()): KeyPair {
        val gen = Ed25519KeyPairGenerator()
        gen.init(Ed25519KeyGenerationParameters(secureRandom))
        val kp = gen.generateKeyPair()
        return KeyPair(
            kp.private as Ed25519PrivateKeyParameters,
            kp.public as Ed25519PublicKeyParameters,
        )
    }

    /** Deterministically derive a keypair from a seed (testable / recoverable). */
    fun fromSeed(seed: ByteArray): KeyPair {
        require(seed.size == 32) { "Ed25519 seed must be 32 bytes" }
        val priv = Ed25519PrivateKeyParameters(seed, 0)
        return KeyPair(priv, priv.generatePublicKey())
    }

    /** Sign the canonical encoding of [payload]. */
    fun sign(payload: Map<String, Any?>, keyPair: KeyPair): ByteArray {
        val msg = CanonicalJson.encode(payload).toByteArray(Charsets.UTF_8)
        val signer = Ed25519Signer()
        signer.init(true, keyPair.privateKey)
        signer.update(msg, 0, msg.size)
        return signer.generateSignature()
    }

    /** Encode the certificate envelope embedded in the QR code. */
    fun certificate(payload: Map<String, Any?>, keyPair: KeyPair): String {
        val sig = sign(payload, keyPair)
        val enc = Base64.getEncoder()
        val json = buildString {
            append("{\"payload\":")
            append(CanonicalJson.encode(payload))
            append(",\"signature\":\"")
            append(enc.encodeToString(sig))
            append("\",\"public_key\":\"")
            append(enc.encodeToString(keyPair.publicKey.encoded))
            append("\"}")
        }
        return json
    }
}

/**
 * Deterministic JSON encoder with sorted keys — MUST match the Go
 * `CanonicalJSON` and the TypeScript implementation so signatures verify across
 * platforms. Supports: null, Boolean, Number, String, List, Map.
 */
object CanonicalJson {
    fun encode(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is Boolean -> sb.append(if (value) "true" else "false")
            is Number -> sb.append(formatNumber(value))
            is String -> sb.append(quote(value))
            is List<*> -> {
                sb.append('[')
                value.forEachIndexed { i, e ->
                    if (i > 0) sb.append(',')
                    write(sb, e)
                }
                sb.append(']')
            }
            is Map<*, *> -> {
                val keys = value.keys.map { it.toString() }.sorted()
                sb.append('{')
                keys.forEachIndexed { i, k ->
                    if (i > 0) sb.append(',')
                    sb.append(quote(k)).append(':')
                    write(sb, value[k])
                }
                sb.append('}')
            }
            else -> sb.append(quote(value.toString()))
        }
    }

    private fun formatNumber(n: Number): String {
        val d = n.toDouble()
        // Canonical rule (shared with Go formatter): integral values keep a
        // single decimal place ("2" -> "2.0"); non-integral values use the
        // shortest round-trip form ("0.82" -> "0.82").
        return if (d == Math.floor(d) && !d.isInfinite()) {
            "%d.0".format(d.toLong())
        } else {
            d.toString()
        }
    }

    private fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
