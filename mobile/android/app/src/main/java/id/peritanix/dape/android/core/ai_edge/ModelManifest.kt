package id.peritanix.dape.android.core.ai_edge

/**
 * TAHAP 0.2 & 7.2 — Over-The-Air (OTA) Continuous Learning: Model Manifest Client.
 *
 * On Wi-Fi (+ charging) the phone checks the server's model registry manifest
 * (`POST /api/v1/models/manifest`), which flags only the models whose version
 * changed since this device's last-known set. The client decides which
 * artefacts to download so metered data is minimised — the "SoC-compiled model"
 * download loop.
 *
 * The parsing + update-decision logic here is pure and unit-tested; the actual
 * HTTP fetch lives in [ModelOtaClient] (Android layer).
 */
object ModelManifest {

    /** One model entry from the registry manifest. */
    data class ModelEntry(
        val name: String,
        val version: String,
        val sha256: String,
        val sizeBytes: Long,
        val quantisation: String,
        val updateAvailable: Boolean,
        val url: String
    ) {
        val sizeMb: Double get() = Math.round(sizeBytes / (1024.0 * 1024.0) * 100.0) / 100.0
    }

    /** Parsed manifest with the registry version + entries. */
    data class Plan(
        val registryVersion: String,
        val updateCount: Int,
        val models: List<ModelEntry>
    ) {
        /** Only the models this device actually needs to (re)download. */
        val toDownload: List<ModelEntry> get() = models.filter { it.updateAvailable && it.url.isNotBlank() }

        val totalDownloadMb: Double
            get() = Math.round(toDownload.sumOf { it.sizeBytes } / (1024.0 * 1024.0) * 100.0) / 100.0
    }

    /**
     * Parse the JSON body returned by `POST /api/v1/models/manifest`. Tolerant of
     * unknown/missing fields (returns null on malformed input).
     *
     * Uses the minimal org.json API available on Android; the same logic is
     * exercised in unit tests via the Android-agnostic [decide] path.
     */
    fun parse(json: String): Plan? = try {
        val root = org.json.JSONObject(json)
        val arr = root.optJSONArray("models") ?: org.json.JSONArray()
        val entries = ArrayList<ModelEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            entries.add(
                ModelEntry(
                    name = o.optString("name"),
                    version = o.optString("version"),
                    sha256 = o.optString("sha256"),
                    sizeBytes = o.optLong("size_bytes", 0L),
                    quantisation = o.optString("quantisation", "fp32"),
                    updateAvailable = o.optBoolean("update_available", false),
                    url = o.optString("url", "")
                )
            )
        }
        Plan(
            registryVersion = root.optString("registry_version", ""),
            updateCount = root.optInt("update_count", 0),
            models = entries
        )
    } catch (_: Exception) {
        null
    }

    /** Whether an OTA check should even run (Wi-Fi + charging preferred). */
    data class OtaPolicy(val isWifi: Boolean, val isCharging: Boolean, val batteryPct: Int) {
        /** Only fetch weights on Wi-Fi while charging (or with plenty of battery). */
        val shouldFetch: Boolean get() = isWifi && (isCharging || batteryPct >= 50)
    }

    /**
     * Pure download decision: given the manifest and this device's currently
     * installed versions, return the entries to download. This is the exact logic
     * the manifest's `update_available` flag encodes — reproduced here so it is
     * testable without a network round-trip.
     */
    fun decide(registry: List<ModelEntry>, installedVersions: Map<String, String>): List<ModelEntry> =
        registry.filter { entry ->
            val have = installedVersions[entry.name]
            (have == null || have != entry.version) && entry.url.isNotBlank()
        }
}
