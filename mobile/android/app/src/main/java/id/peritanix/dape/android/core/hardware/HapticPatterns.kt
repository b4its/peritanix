package id.peritanix.dape.android.core.hardware

/**
 * Matriks Sensor — Motor Getar (Haptic) → Panduan Taktil Matahari Terik.
 *
 * Under glaring equatorial sun the farmer cannot read the screen, and in the
 * field it is noisy. Distinct vibration patterns communicate critical states
 * without looking: clinometer level, burst capture done, Urea hazard, strip
 * finish, rain warning, etc.
 *
 * The patterns are pure data (timings) so they are unit-testable; the Android
 * layer translates them to `VibrationEffect.createWaveform`.
 */
object HapticPatterns {

    /** A waveform: alternating ON/OFF durations (ms). Repeating = loop from index 0. */
    data class Pattern(val label: String, val timingsMs: LongArray, val repeat: Boolean = false) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Pattern) return false
            return label == other.label && timingsMs.contentEquals(other.timingsMs) && repeat == other.repeat
        }
        override fun hashCode(): Int = 31 * (31 * label.hashCode() + timingsMs.contentHashCode()) + repeat.hashCode()
    }

    // Canonical, distinct patterns (short so they read clearly on cheap motors).
    val LEVEL_STEADY = Pattern("clinometer level", longArrayOf(0, 120), repeat = false)   // single firm tap
    val TILTED_PULSE = Pattern("tilted", longArrayOf(50, 120, 50, 120), repeat = false)    // double pulse
    val BURST_DONE = Pattern("burst captured", longArrayOf(0, 80, 60, 80), repeat = false) // two quick ticks
    val UREA_HAZARD = Pattern("urea hazard", longArrayOf(0, 400, 100, 400, 100, 400), repeat = false) // strong triple
    val RAIN_ALARM = Pattern("rain warning", longArrayOf(0, 250, 100, 250, 100, 250, 100, 250), repeat = false) // 4 buzzes
    val PENDING_SYNC = Pattern("pending sync", longArrayOf(0, 60), repeat = true)          // gentle pulse loop

    /** Choose the pattern for a given field event. */
    enum class FieldEvent { LEVEL_OK, TILTED, BURST_DONE, UREA_HAZARD, RAIN_WARNING, PENDING_SYNC }

    fun forEvent(event: FieldEvent): Pattern = when (event) {
        FieldEvent.LEVEL_OK -> LEVEL_STEADY
        FieldEvent.TILTED -> TILTED_PULSE
        FieldEvent.BURST_DONE -> BURST_DONE
        FieldEvent.UREA_HAZARD -> UREA_HAZARD
        FieldEvent.RAIN_WARNING -> RAIN_ALARM
        FieldEvent.PENDING_SYNC -> PENDING_SYNC
    }

    /**
     * Sunlight-adaptive intensity: haptics must be stronger in bright sun to be
     * felt through thick gloves. Returns an amplitude 1..255 (VibrationEffect).
     */
    fun amplitudeForAmbientLux(ambientLux: Float): Int = when {
        ambientLux >= 25_000f -> 255   // glaring sun → max
        ambientLux >= 5_000f -> 200
        ambientLux >= 500f -> 160
        else -> 120
    }
}
