package id.peritanix.dape.android.core.onboarding

/**
 * First-run onboarding / device calibration state machine (pure logic).
 *
 * A brand-new install walks the farmer through two offline obligations before
 * they can trust photometry or issue certificates:
 *   1. Spectral white-reference calibration (dark-chamber capture) so readings
 *      are corrected for this specific handset's flash/LED colour.
 *   2. Ed25519 key-pair generation stored in the Android Keystore.
 *
 * The state machine is deterministic and framework-free so it can be unit-tested
 * and reused by the Compose screen. [OnboardingSync] adds the online push.
 */
enum class OnboardingStep(val index: Int, val title: String, val instruction: String) {
    WELCOME(
        0, "Selamat Datang di Pentasix",
        "Sebelum mulai, lakukan 2 langkah singkat: kalibrasi warna kamera & buat kunci kriptografi perangkat Anda. Semua 100% offline."
    ),
    SPECTRAL(
        1, "Kalibrasi Spektral Flash",
        "Letakkan kertas HVS putih / bungkus garam standar di dalam mangkuk tertutup rapat (0 Lux), lalu ambil foto dengan flash menyala."
    ),
    KEYPAIR(
        2, "Kunci Kriptografi Perangkat",
        "Buat pasangan kunci Ed25519 di Android Keystore. Kunci privat tidak dapat diekstrak — dipakai menandatangani sertifikat panen offline."
    ),
    DONE(
        3, "Siap Digunakan",
        "Kalibrasi & kunci tersimpan. Saat online, data ini otomatis tersinkron ke server untuk verifikasi QR pembeli & profil warna CIELAB."
    );

    companion object {
        val ordered: List<OnboardingStep> = listOf(WELCOME, SPECTRAL, KEYPAIR, DONE)
        fun next(current: OnboardingStep): OnboardingStep =
            ordered.getOrElse(current.index + 1) { DONE }
        fun previous(current: OnboardingStep): OnboardingStep =
            ordered.getOrElse(current.index - 1) { WELCOME }
    }
}

/** Snapshot of what the onboarding flow has completed so far. */
data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val spectralDone: Boolean = false,
    val keyPairDone: Boolean = false,
    val whiteReference: WhiteReferenceReading? = null,
    val publicKeyBase64: String? = null
) {
    /** Both offline obligations satisfied → app may proceed to the main shell. */
    val isComplete: Boolean get() = spectralDone && keyPairDone

    /** Human-readable blocking reason when not complete, else null. */
    fun blockingReason(): String? = when {
        !spectralDone -> "Kalibrasi spektral belum selesai."
        !keyPairDone -> "Kunci kriptografi belum dibuat."
        else -> null
    }

    /** Mark spectral calibration complete with its computed white-reference. */
    fun withSpectral(wr: WhiteReferenceReading): OnboardingState =
        copy(spectralDone = wr.luxVerified, whiteReference = wr, step = advanceIfReady(spectralDone = wr.luxVerified))

    /** Mark key generation complete with the base64 public key. */
    fun withKeyPair(publicKey: String): OnboardingState =
        copy(keyPairDone = publicKey.isNotBlank(), publicKeyBase64 = publicKey, step = advanceIfReady(keyPairDone = publicKey.isNotBlank()))

    private fun advanceIfReady(spectralDone: Boolean = this.spectralDone, keyPairDone: Boolean = this.keyPairDone): OnboardingStep = when {
        spectralDone && keyPairDone -> OnboardingStep.DONE
        spectralDone -> OnboardingStep.KEYPAIR
        else -> OnboardingStep.SPECTRAL
    }
}

/** RGB white-reference reading from the dark-chamber capture. */
data class WhiteReferenceReading(
    val rawR: Int,
    val rawG: Int,
    val rawB: Int,
    val rScale: Double,
    val gScale: Double,
    val bScale: Double,
    val ambientLux: Float,
    val luxVerified: Boolean
) {
    /** Compact JSON array [rScale, gScale, bScale] for server CIELAB profiling. */
    fun toWhiteReferenceArray(): List<Double> = listOf(rScale, gScale, bScale)
}
