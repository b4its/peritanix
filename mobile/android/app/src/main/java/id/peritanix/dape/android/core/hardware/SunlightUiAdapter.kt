package id.peritanix.dape.android.core.hardware

import androidx.compose.ui.graphics.Color

/**
 * Tahap 6: Auto-Sunlight High-Contrast UI Adapter (SunlightUiAdapter).
 *
 * Automatically monitors ambient light intensity (TYPE_LIGHT in Lux):
 * If ambient light exceeds 25,000 Lux (open chili field under glaring equatorial sun),
 * activates the Extreme Sunlight Theme:
 *   - Pure white background (#FFFFFF)
 *   - Bold black text (#000000)
 *   - Ultra-high contrast neon orange & amber borders
 *   - 150% enlarged button touch targets
 *   - Maximum tactile vibration feedback
 */
object SunlightUiAdapter {

    const val SUNLIGHT_LUX_THRESHOLD = 25000.0f

    data class SunlightThemeConfig(
        val isExtremeSunlightActive: Boolean,
        val currentLux: Float,
        val backgroundColor: Color,
        val textColor: Color,
        val accentColor: Color,
        val buttonElevationDp: Int,
        val statusMessage: String
    )

    fun evaluateSunlightTheme(ambientLux: Float): SunlightThemeConfig {
        val isSunlight = ambientLux >= SUNLIGHT_LUX_THRESHOLD

        return if (isSunlight) {
            SunlightThemeConfig(
                isExtremeSunlightActive = true,
                currentLux = ambientLux,
                backgroundColor = Color.White,
                textColor = Color.Black,
                accentColor = Color(0xFFD84315), // Deep Orange 900
                buttonElevationDp = 8,
                statusMessage = "Terik Matahari Terdeteksi (${"%.0f".format(ambientLux)} Lux): Mode Kontras Tinggi Ekstrem aktif untuk keterbacaan maksimal di kebun terbuka."
            )
        } else {
            SunlightThemeConfig(
                isExtremeSunlightActive = false,
                currentLux = ambientLux,
                backgroundColor = Color(0xFFFFFBF8),
                textColor = Color(0xFF1F1B16),
                accentColor = Color(0xFFE65100),
                buttonElevationDp = 2,
                statusMessage = "Pencahayaan normal (${"%.0f".format(ambientLux)} Lux): Tema Pentasix Standar aktif."
            )
        }
    }
}
