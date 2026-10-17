package id.peritanix.dape.android.core.ai_edge

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * TAHAP 0.2 & 7.2 — OTA model manifest fetcher (Android/network layer).
 *
 * Posts this device's currently-installed model versions to
 * `POST /api/v1/models/manifest` and parses the returned update plan. Guarded by
 * [ModelManifest.OtaPolicy] so downloads only happen on Wi-Fi while charging (or
 * with ample battery), protecting the farmer's metered data.
 */
class ModelOtaClient(
    private val apiBaseUrl: String = DEFAULT_BASE_URL,
    private val connectTimeoutMs: Int = 8000,
    private val readTimeoutMs: Int = 15000
) {
    sealed class Result {
        data class Success(val plan: ModelManifest.Plan) : Result()
        data object Skipped : Result()                 // policy says don't fetch now
        data class Failed(val message: String) : Result()
    }

    /**
     * Fetch the manifest. [installedVersions] = map of model name → version the
     * device already has (empty on first run = download everything).
     */
    fun fetchManifest(
        installedVersions: Map<String, String>,
        policy: ModelManifest.OtaPolicy
    ): Result {
        if (!policy.shouldFetch) return Result.Skipped
        return try {
            val clientVersions = JSONObject()
            installedVersions.forEach { (k, v) -> clientVersions.put(k, v) }
            val body = JSONObject().apply {
                put("client_versions", clientVersions)
                put("base_url", "$apiBaseUrl/models")
            }
            val url = URL("${apiBaseUrl.trimEnd('/')}/api/v1/models/manifest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body.toString()) }
            when (val code = conn.responseCode) {
                in 200..299 -> {
                    val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    val plan = ModelManifest.parse(text)
                    if (plan != null) Result.Success(plan) else Result.Failed("Manifest kosong / format salah")
                }
                else -> Result.Failed("HTTP $code dari server model")
            }
        } catch (e: Exception) {
            Result.Failed("Gagal mengambil manifest: ${e.message}")
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://10.0.2.2:58139"
    }
}
