package id.peritanix.dape.android.core.sync

/**
 * TAHAP 7.2 (online maximization) — Predictive Pre-Fetching planner.
 *
 * When the phone is on Wi-Fi and charging, the app should quietly download the
 * data the farmer will need next time they are offline: a 7-day microclimate
 * forecast, a 5 km neighbour disease-risk map (spatial epidemiology), and any
 * new AI model weights (OTA). This pure planner decides WHICH jobs to run given
 * the connection & battery state, so the download burst is bounded and polite.
 *
 * Pure and unit-tested; the actual HTTP fetch (feeds / docs) lives elsewhere.
 */
object PrefetchPlanner {

    enum class Job(val label: String, val approxKb: Int) {
        WEATHER_7D("Prakiraan cuaca mikro 7 hari", 120),
        NEIGHBOR_RISK_5KM("Peta risiko penyakit kebun tetangga (5 km)", 300),
        OTA_MODELS("Bobot model AI terbaru (OTA)", 12000)
    }

    data class ConnectionState(
        val isWifi: Boolean,
        val isCharging: Boolean,
        val batteryPct: Int,
        val freeStorageMb: Long
    )

    data class Plan(val jobs: List<Job>, val totalKb: Int, val rationale: String)

    /**
     * Plan the pre-fetch jobs.
     *  - Forecast + neighbour risk: on any Wi-Fi (cheap, ≤ ~0.5 MB).
     *  - OTA weights: only Wi-Fi + charging + enough free storage (≥ 50 MB).
     */
    fun plan(state: ConnectionState): Plan {
        if (!state.isWifi) {
            return Plan(emptyList(), 0, "Bukan Wi-Fi: pre-fetch ditunda untuk hemat kuota seluler.")
        }
        val jobs = mutableListOf<Job>()
        jobs.add(Job.WEATHER_7D)
        jobs.add(Job.NEIGHBOR_RISK_5KM)

        val otaAllowed = state.isCharging && state.freeStorageMb >= 50
        if (otaAllowed) jobs.add(Job.OTA_MODELS)

        val totalKb = jobs.sumOf { it.approxKb }
        val rationale = if (otaAllowed) {
            "Wi-Fi + charging: unduh prakiraan 7 hari, peta risiko tetangga 5 km, dan bobot model AI OTA (~${"%.1f".format(totalKb / 1024.0)} MB)."
        } else {
            "Wi-Fi (tanpa charging/penyimpanan cukup): unduh prakiraan 7 hari & peta risiko tetangga saja (~${totalKb} KB); bobot AI ditunda."
        }
        return Plan(jobs, totalKb, rationale)
    }
}
