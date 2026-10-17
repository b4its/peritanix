package id.peritanix.dape.android.core.mode

import android.content.Context

/**
 * Application operating mode.
 *
 * - [SIMULATION] uses deterministic demo data so the whole app can be explored
 *   (fresh install, emulator, demo/pitching) without any device sensors or
 *   camera. Every screen shows seeded "Blok A1" sample content.
 *
 * - [REAL] uses only data captured from the user's device: live GPS, sensors,
 *   camera-captured chili quality analysis, and a growing guide. No fabricated
 *   values are shown; screens read real hardware and user input.
 *
 * The pure logic (mode resolution + label/description) lives here so it is
 * unit-testable without the Android framework; [AppModeStore] adds persistence.
 */
enum class AppMode(val id: String, val label: String, val tagline: String, val description: String) {
    SIMULATION(
        id = "simulation",
        label = "Mode Simulasi",
        tagline = "Data demo",
        description = "Jelajahi seluruh fitur dengan data contoh deterministik — tanpa perlu sensor, GPS, atau kamera."
    ),
    REAL(
        id = "real",
        label = "Mode Riil",
        tagline = "Data perangkat",
        description = "Menggunakan data asli perangkat: GPS, sensor, deteksi kualitas cabai via kamera, dan panduan tanam."
    );

    val isSimulation: Boolean get() = this == SIMULATION
    val isReal: Boolean get() = this == REAL

    companion object {
        /** Resolve a mode from its persisted [id]; unknown/blank falls back to simulation. */
        fun fromId(id: String?): AppMode =
            values().firstOrNull { it.id == id } ?: SIMULATION

        /** The next mode when the user toggles the switch. */
        fun toggle(current: AppMode): AppMode =
            if (current == SIMULATION) REAL else SIMULATION
    }
}

/**
 * Persists the selected [AppMode] across launches using SharedPreferences.
 *
 * Kept deliberately tiny and side-effect free until read/write is called, so it
 * can be constructed in tests with a mocked/in-memory [Context] if needed; the
 * mode logic itself is exercised via [AppMode] directly.
 */
class AppModeStore(private val context: Context) {

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Current persisted mode; defaults to [AppMode.SIMULATION] on first launch. */
    fun load(): AppMode = AppMode.fromId(prefs.getString(KEY_MODE, null))

    /** Persist the chosen mode and return it. */
    fun save(mode: AppMode): AppMode {
        prefs.edit().putString(KEY_MODE, mode.id).apply()
        return mode
    }

    /** Flip to the other mode, persist, and return the new mode. */
    fun toggle(): AppMode = save(AppMode.toggle(load()))

    /** Whether the first-run onboarding (spectral calib + key) has completed. */
    fun isOnboardingDone(): Boolean = prefs.getBoolean(KEY_ONBOARDING, false)

    /** Mark first-run onboarding complete so it is not shown again. */
    fun setOnboardingDone() {
        prefs.edit().putBoolean(KEY_ONBOARDING, true).apply()
    }

    companion object {
        private const val PREFS = "dape.mode"
        private const val KEY_MODE = "app_mode"
        private const val KEY_ONBOARDING = "onboarding_done"
    }
}
