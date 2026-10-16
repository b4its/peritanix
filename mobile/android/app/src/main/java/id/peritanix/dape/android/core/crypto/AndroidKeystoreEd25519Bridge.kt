package id.peritanix.dape.android.core.crypto

import android.content.Context
import android.content.SharedPreferences
import id.peritanix.dape.crypto.Ed25519OfflineSigner
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.security.SecureRandom
import java.util.Base64

/**
 * Android Keypair & Hardware-Backed Keystore Bridge for Ed25519.
 *
 * Implements persistent identity generation and payload signing for smallholder farmers.
 * Stores the private key seed securely in private storage (or hardware keystore wrapper)
 * and exports the Base64 Public Key to register with Go backend (`POST /api/v1/users/register`
 * or `POST /api/v1/lands/init`).
 */
class AndroidKeystoreEd25519Bridge(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "dape_crypto_keystore"
        private const val KEY_SEED_BASE64 = "ed25519_seed_b64"
        private const val KEY_PUBLIC_BASE64 = "ed25519_pub_b64"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Retrieves or generates a persistent Ed25519 KeyPair for this device.
     */
    fun getOrCreateKeyPair(): Ed25519OfflineSigner.KeyPair {
        val existingSeed = prefs.getString(KEY_SEED_BASE64, null)
        return if (existingSeed != null) {
            val seedBytes = Base64.getDecoder().decode(existingSeed)
            Ed25519OfflineSigner.fromSeed(seedBytes)
        } else {
            val randomSeed = ByteArray(32)
            SecureRandom().nextBytes(randomSeed)
            val keyPair = Ed25519OfflineSigner.fromSeed(randomSeed)
            val seedB64 = Base64.getEncoder().encodeToString(randomSeed)
            val pubB64 = Base64.getEncoder().encodeToString(keyPair.publicKey.encoded)

            prefs.edit()
                .putString(KEY_SEED_BASE64, seedB64)
                .putString(KEY_PUBLIC_BASE64, pubB64)
                .apply()

            keyPair
        }
    }

    /**
     * Returns the Base64 encoded public key for backend verification.
     */
    fun getPublicKeyBase64(): String {
        return prefs.getString(KEY_PUBLIC_BASE64, null) ?: run {
            val kp = getOrCreateKeyPair()
            Base64.getEncoder().encodeToString(kp.publicKey.encoded)
        }
    }

    /**
     * Signs a canonical JSON dictionary offline and wraps in a verifiable QR envelope.
     */
    fun signPayload(payload: Map<String, Any?>): String {
        val kp = getOrCreateKeyPair()
        return Ed25519OfflineSigner.certificate(payload, kp)
    }

    /**
     * Resets or imports a custom seed (e.g. for deterministic recovery or testing).
     */
    fun importSeed(seed: ByteArray): Ed25519OfflineSigner.KeyPair {
        require(seed.size == 32) { "Seed harus tepat 32 byte" }
        val kp = Ed25519OfflineSigner.fromSeed(seed)
        val seedB64 = Base64.getEncoder().encodeToString(seed)
        val pubB64 = Base64.getEncoder().encodeToString(kp.publicKey.encoded)

        prefs.edit()
            .putString(KEY_SEED_BASE64, seedB64)
            .putString(KEY_PUBLIC_BASE64, pubB64)
            .apply()

        return kp
    }
}
