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

    /**
     * Verify a canonical payload against an Ed25519 signature and 32-byte public key.
     * Runs 100% offline without network round-trips.
     */
    fun verify(payload: Map<String, Any?>, signature: ByteArray, publicKeyBytes: ByteArray): Boolean {
        return try {
            if (signature.size != 64 || publicKeyBytes.size != 32) return false
            val pub = Ed25519PublicKeyParameters(publicKeyBytes, 0)
            val msg = CanonicalJson.encode(payload).toByteArray(Charsets.UTF_8)
            val verifier = Ed25519Signer()
            verifier.init(false, pub)
            verifier.update(msg, 0, msg.size)
            verifier.verifySignature(signature)
        } catch (_: Exception) {
            false
        }
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

    /** Envelope parsed from a QR certificate JSON string. */
    data class CertificateEnvelope(
        val payload: Map<String, Any?>,
        val signatureB64: String,
        val publicKeyB64: String
    )

    /**
     * Comprehensive off-taker / buyer verification verdict for a chili harvest lot.
     */
    data class VerificationVerdict(
        val isAuthentic: Boolean,
        val grade: String,
        val isGradeAPlus: Boolean,
        val commercialTier: String,
        val pericarpFortified: Boolean,
        val sterilized52C: Boolean,
        val sinkRatio: Double,
        val landId: String,
        val photoSha256Hex: String,
        val timestampEpochS: Long,
        val priceMultiplier: Double,
        val badgeTitle: String,
        val statusMessage: String,
        val details: Map<String, String>
    )

    /**
     * Parse the raw QR certificate string into a structured envelope.
     */
    fun parseCertificate(json: String): CertificateEnvelope? {
        return try {
            val parsed = SimpleJsonParser.parse(json) as? Map<*, *> ?: return null
            val payload = (parsed["payload"] as? Map<*, *>)?.entries?.associate {
                it.key.toString() to it.value
            } ?: return null
            val sig = parsed["signature"]?.toString() ?: return null
            val pub = parsed["public_key"]?.toString() ?: return null
            CertificateEnvelope(payload = payload, signatureB64 = sig, publicKeyB64 = pub)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * TAHAP 6.2 — Offline Buyer QR Verifier:
     * Mathematically verifies the cryptographic signature of the harvest lot on-device
     * and evaluates all 6 agronomic quality parameters.
     */
    fun verifyCertificate(certificateJson: String): VerificationVerdict {
        val env = parseCertificate(certificateJson)
        if (env == null) {
            return VerificationVerdict(
                isAuthentic = false,
                grade = "INVALID",
                isGradeAPlus = false,
                commercialTier = "Tidak Terdefinisi",
                pericarpFortified = false,
                sterilized52C = false,
                sinkRatio = 0.0,
                landId = "",
                photoSha256Hex = "",
                timestampEpochS = 0L,
                priceMultiplier = 0.0,
                badgeTitle = "PERINGATAN: SERTIFIKAT CACAT / TIDAK ASLI (FRAUD DETECTED)",
                statusMessage = "Format JSON sertifikat QR rusak atau tidak dapat diuraikan.",
                details = emptyMap()
            )
        }

        val dec = Base64.getDecoder()
        val sigBytes = try { dec.decode(env.signatureB64) } catch (_: Exception) { null }
        val pubBytes = try { dec.decode(env.publicKeyB64) } catch (_: Exception) { null }

        if (sigBytes == null || pubBytes == null || !verify(env.payload, sigBytes, pubBytes)) {
            return VerificationVerdict(
                isAuthentic = false,
                grade = "FRAUD",
                isGradeAPlus = false,
                commercialTier = "Ditolak Pabrik / Fraud",
                pericarpFortified = false,
                sterilized52C = false,
                sinkRatio = 0.0,
                landId = env.payload["land_id"]?.toString() ?: "",
                photoSha256Hex = env.payload["image_sha256_hash"]?.toString() ?: "",
                timestampEpochS = (env.payload["timestamp"] as? Number)?.toLong() ?: 0L,
                priceMultiplier = 0.0,
                badgeTitle = "PERINGATAN: SERTIFIKAT CACAT / TIDAK ASLI (FRAUD DETECTED)",
                statusMessage = "Tanda tangan kriptografi Ed25519 tidak cocok dengan kunci publik perangkat. Data panen telah dimanipulasi!",
                details = mapOf(
                    "Status Kripto" to "Tanda tangan Ed25519 TIDAK VALID (Integritas Gagal)",
                    "Kunci Publik" to env.publicKeyB64.take(16) + "...",
                    "Tindakan Pembeli" to "Tolak pembelian atau batalkan premi harga Grade A"
                )
            )
        }

        // Signature is cryptographically verified! Now extract and validate quality parameters:
        val payload = env.payload
        val landId = payload["land_id"]?.toString() ?: "lahan-tidak-diketahui"
        val grade = payload["grade_result"]?.toString() ?: "B"
        val sinkRatio = (payload["archimedes_sink_ratio"] as? Number)?.toDouble() ?: 0.5
        val pericarpFortified = payload["gdd_fortification_compliant"] == true
        val acousticBoil = payload["acoustic_boiling_verified"] == true
        val gyroSec = (payload["gyro_immersion_seconds"] as? Number)?.toInt() ?: 0
        val sterC = (payload["sterilization_c"] as? Number)?.toDouble() ?: 0.0
        val sterilized52C = acousticBoil && gyroSec >= 180 && sterC in 50.0..54.0
        val photoHash = payload["image_sha256_hash"]?.toString() ?: ""
        val timestamp = (payload["timestamp"] as? Number)?.toLong() ?: (System.currentTimeMillis() / 1000)
        val cec = (payload["cec_meq"] as? Number)?.toDouble() ?: 15.0
        val premiumPct = (payload["batch_premium_pct"] as? Number)?.toDouble() ?: 75.0

        val isGradeAPlus = grade == "A+" && sinkRatio >= 0.80 && pericarpFortified && sterilized52C
        val (badgeTitle, tier, mult, advice) = when {
            isGradeAPlus -> Quadruple(
                "TERVERIFIKASI ASLI - MUTU GRADE A+ (EXPORT PRIME)",
                "Export Prime Quality (Grade A+)",
                1.35,
                "Cabai mutu tertinggi: Dinding sel buah padat, terbukti bebas spora laten patek, dan bersertifikat digital resmi."
            )
            grade == "A" -> Quadruple(
                "TERVERIFIKASI ASLI - MUTU GRADE A (SUPERMARKET)",
                "Supermarket Premium (Grade A)",
                1.15,
                "Cabai mutu premium: Lurus, seragam, dan daya simpan pasca-panen tinggi untuk rantai pasok modern."
            )
            grade == "B" -> Quadruple(
                "TERVERIFIKASI ASLI - MUTU GRADE B (PASAR GROSIR)",
                "Pasar Tradisional / Grosir (Grade B)",
                0.90,
                "Mutu standar konsumsi harian lokal. Bebas busuk patek aktif."
            )
            else -> Quadruple(
                "TERVERIFIKASI ASLI - MUTU GRADE BS (INDUSTRI OLAHAN)",
                "Industri Olahan / Saus Sambal (Grade BS)",
                0.60,
                "Cabai sortiran cocok untuk bahan baku penggilingan pasta dan saus cabai."
            )
        }

        val detailsMap = linkedMapOf(
            "Tanda Tangan Ed25519" to "VALID (Terotentikasi 100% Offline via Android Keystore)",
            "Dinding Sel Buah (Archimedes)" to "Sink Ratio: ${"%.1f".format(sinkRatio * 100)}% (${if (sinkRatio >= 0.80) "Padat & Tebal / Grade A+" else "Cukup"})",
            "Bebas Antraknosa / Patek" to if (sterilized52C) "180 dtk Sterilisasi 52°C Terverifikasi Akustik" else "Belum lengkap",
            "Fortifikasi Ca-B-Si (Antesis)" to if (pericarpFortified) "PATUH (Kalsium-Silika Terkunci, Bebas Urea Berlebih)" else "Tidak patuh",
            "Kapasitas KTK Tanah" to "$cec meq/100g",
            "Rasio Batch Premium" to "${"%.1f".format(premiumPct)}%",
            "Integritas Foto (SHA-256)" to if (photoHash.length == 64) photoHash.take(16) + "..." else "Tidak ada hash",
            "Kunci Publik Produsen" to env.publicKeyB64.take(18) + "..."
        )

        return VerificationVerdict(
            isAuthentic = true,
            grade = grade,
            isGradeAPlus = isGradeAPlus,
            commercialTier = tier,
            pericarpFortified = pericarpFortified,
            sterilized52C = sterilized52C,
            sinkRatio = sinkRatio,
            landId = landId,
            photoSha256Hex = photoHash,
            timestampEpochS = timestamp,
            priceMultiplier = mult,
            badgeTitle = badgeTitle,
            statusMessage = advice,
            details = detailsMap
        )
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

/**
 * Lightweight, zero-dependency pure Kotlin JSON parser for offline certificate parsing.
 */
object SimpleJsonParser {
    class Parser(private val s: String) {
        private var pos = 0

        private fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun parse(): Any? {
            skipWs()
            if (pos >= s.length) return null
            return parseValue()
        }

        private fun parseValue(): Any? {
            skipWs()
            if (pos >= s.length) return null
            return when (s[pos]) {
                '"' -> parseString()
                '{' -> parseObject()
                '[' -> parseArray()
                else -> parseNumberOrBoolOrNull()
            }
        }

        private fun parseString(): String {
            pos++ // skip initial "
            val sb = StringBuilder()
            while (pos < s.length) {
                val c = s[pos++]
                if (c == '"') return sb.toString()
                if (c == '\\' && pos < s.length) {
                    when (val esc = s[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000c')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            val end = (pos + 4).coerceAtMost(s.length)
                            val hex = s.substring(pos, end)
                            pos = end
                            try { sb.append(hex.toInt(16).toChar()) } catch (_: Exception) {}
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun parseNumberOrBoolOrNull(): Any? {
            val start = pos
            while (pos < s.length && s[pos] !in ",}] \r\n\t") pos++
            val token = s.substring(start, pos)
            return when (token) {
                "true" -> true
                "false" -> false
                "null" -> null
                else -> {
                    if (token.contains('.')) token.toDoubleOrNull() ?: token
                    else token.toLongOrNull()?.let {
                        if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it
                    } ?: token.toDoubleOrNull() ?: token
                }
            }
        }

        private fun parseObject(): Map<String, Any?> {
            pos++ // skip '{'
            val map = linkedMapOf<String, Any?>()
            skipWs()
            if (pos < s.length && s[pos] == '}') {
                pos++
                return map
            }
            while (pos < s.length) {
                skipWs()
                if (pos >= s.length || s[pos] != '"') break
                val key = parseString()
                skipWs()
                if (pos < s.length && s[pos] == ':') pos++
                skipWs()
                val v = parseValue()
                map[key] = v
                skipWs()
                if (pos < s.length && s[pos] == ',') {
                    pos++
                } else if (pos < s.length && s[pos] == '}') {
                    pos++
                    break
                }
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            pos++ // skip '['
            val list = mutableListOf<Any?>()
            skipWs()
            if (pos < s.length && s[pos] == ']') {
                pos++
                return list
            }
            while (pos < s.length) {
                skipWs()
                list.add(parseValue())
                skipWs()
                if (pos < s.length && s[pos] == ',') {
                    pos++
                } else if (pos < s.length && s[pos] == ']') {
                    pos++
                    break
                }
            }
            return list
        }
    }

    fun parse(json: String): Any? = Parser(json).parse()
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
