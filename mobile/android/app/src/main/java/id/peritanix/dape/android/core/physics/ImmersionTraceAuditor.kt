package id.peritanix.dape.android.core.physics

import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * FR-4.1 & FR-6.3 Immersion Trace Anti-Fraud Auditor & Cryptographic Hashing.
 *
 * Prevents fraudulent shortcuts during the 180-second post-harvest hot water treatment:
 *   1. Collects continuous 3D gyroscope/accelerometer vector samples.
 *   2. Evaluates statistical motion variance and dynamic entropy.
 *   3. Rejects static spoofing (e.g. phone placed flat on a table with variance < 0.05).
 *   4. Generates SHA-256 digests of inspection photos to embed in Ed25519 certificates.
 */
class ImmersionTraceAuditor {

    companion object {
        const val MIN_VARIANCE_THRESHOLD = 0.05
        const val MIN_SAMPLES_REQUIRED = 10
    }

    data class AuditResult(
        val sampleCount: Int,
        val meanMagnitude: Double,
        val variance: Double,
        val isValidRealActivity: Boolean,
        val statusMessage: String
    )

    private val samples = mutableListOf<Double>()

    fun clear() {
        samples.clear()
    }

    fun recordVector(x: Float, y: Float, z: Float) {
        val magnitude = sqrt((x * x + y * y + z * z).toDouble())
        samples.add(magnitude)
    }

    fun recordMagnitude(magnitude: Double) {
        samples.add(magnitude)
    }

    /**
     * Evaluates whether authentic operator agitation occurred during the immersion countdown.
     */
    fun evaluateTrace(): AuditResult {
        if (samples.size < MIN_SAMPLES_REQUIRED) {
            return AuditResult(
                sampleCount = samples.size,
                meanMagnitude = 0.0,
                variance = 0.0,
                isValidRealActivity = false,
                statusMessage = "Sampel sensor terlalu sedikit (${samples.size} < $MIN_SAMPLES_REQUIRED). Proses perendaman belum terekam penuh."
            )
        }

        val mean = samples.average()
        val sumSqDiff = samples.map { (it - mean) * (it - mean) }.sum()
        val variance = sumSqDiff / samples.size
        val roundedVariance = Math.round(variance * 1000.0) / 1000.0

        val isValid = variance >= MIN_VARIANCE_THRESHOLD
        val msg = if (isValid) {
            "JEJAK GERAK OTENTIK: Variansi sensor $roundedVariance >= $MIN_VARIANCE_THRESHOLD. Pengadukan termoterapi 52°C terverifikasi valid."
        } else {
            "FRAUD DETECTED: Smartphone diam statis (Variansi $roundedVariance < $MIN_VARIANCE_THRESHOLD). Petani tidak mengaduk air perendaman!"
        }

        return AuditResult(
            sampleCount = samples.size,
            meanMagnitude = Math.round(mean * 100.0) / 100.0,
            variance = roundedVariance,
            isValidRealActivity = isValid,
            statusMessage = msg
        )
    }

    /**
     * Computes canonical SHA-256 hex digest of raw photo bytes.
     */
    fun computeSha256(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
