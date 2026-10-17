package id.peritanix.dape.android.core.ai_edge

/**
 * TAHAP 0.2 — Hybrid Split-Computing (Edge ROI + Cloud Deep Inference).
 *
 * When the link is fast (low latency) the phone can skip heavy on-device
 * inference and instead:
 *   1. Crop only the Region of Interest (leaf/fruit) locally (a few ms), then
 *   2. Stream those crops over gRPC to the FastAPI server, which runs a large
 *      Vision Transformer far too heavy for a 2 GB phone — saving battery while
 *      gaining accuracy.
 *
 * The decision (offload or not) and the ROI geometry are pure math, unit-tested;
 * the gRPC transport itself lives in the sync layer.
 */
object HybridSplitComputing {

    /** Minimum latency quality bands for offloading. */
    const val FAST_LATENCY_MS = 50L      // checklist: < 50 ms ⇒ eligible
    const val MAX_LATENCY_MS = 120L      // above this the round-trip is worse than local

    enum class Strategy { ON_DEVICE, SPLIT_OFFLOAD, FAST_LANE_ONLY }

    data class Decision(
        val strategy: Strategy,
        val latencyMs: Long,
        val reason: String
    )

    /**
     * Pure offload decision.
     *
     * Split-computing is only worthwhile when online with low latency AND the
     * on-device model is either absent, or the device is resource-constrained
     * (low memory / thermally throttled). Otherwise run fully on-device.
     */
    fun decide(
        latencyMs: Long,
        isOnline: Boolean,
        deviceConstrained: Boolean,
        modelAvailableOnDevice: Boolean
    ): Decision = when {
        !isOnline ->
            Decision(Strategy.ON_DEVICE, latencyMs, "Offline: inferensi penuh on-device.")
        latencyMs > MAX_LATENCY_MS ->
            Decision(Strategy.ON_DEVICE, latencyMs, "Latensi ${latencyMs} ms terlalu tinggi; model lokal lebih cepat.")
        (latencyMs <= FAST_LATENCY_MS) && (deviceConstrained || !modelAvailableOnDevice) ->
            Decision(
                Strategy.SPLIT_OFFLOAD, latencyMs,
                "Latensi ${latencyMs} ms cepat & perangkat terbatas/model lokal absen: crop ROI dikirim via gRPC ke server (ViT besar)."
            )
        latencyMs <= FAST_LATENCY_MS ->
            Decision(
                Strategy.FAST_LANE_ONLY, latencyMs,
                "Latensi ${latencyMs} ms cepat tetapi perangkat mampu: inferensi on-device (hemat kuota & privasi)."
            )
        else ->
            Decision(Strategy.ON_DEVICE, latencyMs, "Latensi sedang; gunakan model lokal.")
    }

    data class Roi(val x: Int, val y: Int, val width: Int, val height: Int)

    /**
     * Compute a centered ROI crop for a frame, padded by [padPct] then clamped to
     * the frame bounds. Cropping reduces upload bytes and lets the server model
     * focus on the subject.
     */
    fun centerRoi(frameWidth: Int, frameHeight: Int, padPct: Int = 10): Roi {
        require(frameWidth > 0 && frameHeight > 0) { "frame size must be > 0" }
        // Base ROI: central 70% of the frame (the subject the farmer framed).
        val baseW = (frameWidth * 0.70).toInt().coerceAtLeast(1)
        val baseH = (frameHeight * 0.70).toInt().coerceAtLeast(1)
        val padW = baseW * padPct / 100
        val padH = baseH * padPct / 100
        var w = (baseW + 2 * padW).coerceAtMost(frameWidth)
        var h = (baseH + 2 * padH).coerceAtMost(frameHeight)
        w = w.coerceAtLeast(1); h = h.coerceAtLeast(1)
        val x = ((frameWidth - w) / 2).coerceAtLeast(0)
        val y = ((frameHeight - h) / 2).coerceAtLeast(0)
        return Roi(x, y, w, h)
    }

    /** Approximate byte savings from sending the ROI instead of the full frame. */
    fun bytesSavedPct(frameWidth: Int, frameHeight: Int, roi: Roi): Double {
        val full = frameWidth.toDouble() * frameHeight
        if (full <= 0.0) return 0.0
        val roiArea = roi.width.toDouble() * roi.height
        val saved = ((full - roiArea) / full * 100.0).coerceIn(0.0, 100.0)
        return Math.round(saved * 10.0) / 10.0
    }
}
