package id.peritanix.dape.android.core.onboarding

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Online maximization step for onboarding (item 2.2).
 *
 * When the device has a JWT (registered profile) and connectivity, it pushes
 * the Ed25519 public key + white-reference matrix to
 * `POST /api/v1/users/register-key` so the buyer QR verifier and the FastAPI
 * CIELAB calibrator can use them. Offline, or without a token, it returns
 * [Result.Queued] so the caller can push the same payload through the existing
 * delta-sync queue (`user_key` entity) instead.
 *
 * The HTTP call is tiny and framework-free so it is straightforward to reason
 * about; the decision logic ([decide]) is pure and unit-testable.
 */
class OnboardingSyncClient(
    private val apiBaseUrl: String = DEFAULT_BASE_URL,
    private val connectTimeoutMs: Int = 6000,
    private val readTimeoutMs: Int = 10000
) {

    sealed class Result {
        data object Pushed : Result()
        data object Queued : Result()
        data class Failed(val message: String) : Result()
    }

    /** What the caller should do, given token + connectivity (pure). */
    fun decide(hasToken: Boolean, isOnline: Boolean): Action = when {
        !hasToken -> Action.QUEUE      // no JWT yet → defer via delta sync
        !isOnline -> Action.QUEUE      // offline → defer
        else -> Action.PUSH_NOW        // online + token → post now
    }

    enum class Action { PUSH_NOW, QUEUE }

    /**
     * Attempt to register the key online. Returns:
     *  - [Result.Pushed] on HTTP 2xx,
     *  - [Result.Queued] when there is no token / no network (caller should queue),
     *  - [Result.Failed] on a hard error (bad request / server error).
     */
    fun registerKey(
        jwt: String?,
        publicKeyBase64: String,
        whiteReference: List<Double>
    ): Result {
        if (jwt.isNullOrBlank()) return Result.Queued
        return try {
            val body = JSONObject().apply {
                put("ed25519_public_key", publicKeyBase64)
                put("white_reference", JSONArray(whiteReference))
            }
            val url = URL("${apiBaseUrl.trimEnd('/')}/api/v1/users/register-key")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $jwt")
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body.toString()) }
            when (val code = conn.responseCode) {
                in 200..299 -> Result.Pushed
                in 400..499 -> Result.Failed("HTTP $code: permintaan ditolak")
                else -> Result.Failed("HTTP $code: server error")
            }
        } catch (e: Exception) {
            // Network flaked mid-flight → defer to the queue rather than fail hard.
            Result.Queued
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://10.0.2.2:58139"
    }
}
