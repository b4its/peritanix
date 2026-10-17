package id.peritanix.dape.android.core.hardware

/**
 * Matriks Sensor — Sensor Jarak (Proximity) → Active Trans-Illumination Trigger.
 *
 * When the farmer presses a leaf flat against the screen/lens (0 cm, "NEAR"),
 * the phone should auto-fire the flash torch and capture a trans-illumination
 * frame without the farmer touching the screen (their hands are muddy). This
 * pure state machine debounces the proximity signal and decides when to trigger
 * the automatic capture, guarding against accidental triggers.
 *
 * The Android ProximitySensor listener feeds [update]; the camera controller
 * reacts to the [ProximityTrigger.Action] it returns.
 */
object ProximityAutoTrigger {

    /** Distance semantics differ per device; NEAR is usually the small maxRange. */
    enum class ProximityState { NEAR, FAR, UNAVAILABLE }

    enum class Action {
        NONE,                 // nothing to do
        ARM,                  // leaf detected at 0 cm → arm continuous auto-capture
        TRIGGER_CAPTURE,      // stable contact reached → fire flash + capture now
        RELEASE               // leaf removed → disarm
    }

    data class TriggerDecision(
        val action: Action,
        val nearDurationMs: Long,
        val message: String
    )

    /** A brief, stable contact (this long at NEAR) is required to avoid misfires. */
    const val STABLE_CONTACT_MS = 300L
    /** After a capture, ignore further triggers for this cooldown. */
    const val POST_CAPTURE_COOLDOWN_MS = 1500L

    /**
     * Stateful debouncer. Feed raw proximity readings + timestamps; it returns
     * the action to perform. Pure logic so it is fully unit-testable.
     */
    class Debouncer {
        private var isNear = false
        private var nearSinceMs = 0L
        private var lastCaptureMs = Long.MIN_VALUE / 2

        fun reset() {
            isNear = false; nearSinceMs = 0L; lastCaptureMs = Long.MIN_VALUE / 2
        }

        fun update(state: ProximityState, nowMs: Long): TriggerDecision = decide(
            state = state,
            nowMs = nowMs,
            wasNear = isNear,
            nearSinceMs = nearSinceMs,
            lastCaptureMs = lastCaptureMs,
            apply = { newNear, newNearSince, newCapture ->
                isNear = newNear
                if (newNearSince != null) nearSinceMs = newNearSince
                if (newCapture != null) lastCaptureMs = newCapture
            }
        )
    }

    /**
     * Pure decision function (used by [Debouncer] and tested directly).
     * `apply` receives the updated (isNear, nearSince-or-null, capture-or-null).
     */
    fun decide(
        state: ProximityState,
        nowMs: Long,
        wasNear: Boolean,
        nearSinceMs: Long,
        lastCaptureMs: Long,
        apply: (Boolean, Long?, Long?) -> Unit
    ): TriggerDecision {
        when (state) {
            ProximityState.UNAVAILABLE ->
                { apply(false, null, null); return TriggerDecision(Action.NONE, 0, "Sensor jarak tidak tersedia.") }

            ProximityState.FAR -> {
                val was = wasNear
                apply(false, null, null)
                return if (was) TriggerDecision(Action.RELEASE, 0, "Daun dilepas. Mode trans-illumination nonaktif.")
                else TriggerDecision(Action.NONE, 0, "")
            }

            ProximityState.NEAR -> {
                if (!wasNear) {
                    // First contact — start the stability timer, arm the torch.
                    apply(true, nowMs, null)
                    return TriggerDecision(Action.ARM, 0, "Daun menempel (0 cm). Menyiapkan flash trans-illumination…")
                }
                val duration = nowMs - nearSinceMs
                val inCooldown = (nowMs - lastCaptureMs) < POST_CAPTURE_COOLDOWN_MS
                if (duration >= STABLE_CONTACT_MS && !inCooldown) {
                    apply(true, nearSinceMs, nowMs)
                    return TriggerDecision(Action.TRIGGER_CAPTURE, duration,
                        "Kontak stabil ${duration} ms. Menembakkan flash & menangkap citra trans-illumination otomatis.")
                }
                return TriggerDecision(Action.NONE, duration, "")
            }
        }
    }
}
