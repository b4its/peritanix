package id.peritanix.dape.android.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Pentasix Signature Harvest Orange Palette
val OrangePrimary = Color(0xFFF57C00)           // Vivid Harvest Orange
val OrangeOnPrimary = Color(0xFFFFFFFF)
val OrangePrimaryContainer = Color(0xFFFFE0B2)    // Warm peach tint
val OrangeOnPrimaryContainer = Color(0xFFBF360C) // Deep burnt orange

val OrangeSecondary = Color(0xFF6D4C41)         // Rich fertile soil brown
val OrangeOnSecondary = Color(0xFFFFFFFF)
val OrangeSecondaryContainer = Color(0xFFD7CCC8)
val OrangeOnSecondaryContainer = Color(0xFF3E2723)

val OrangeTertiary = Color(0xFF2E7D32)          // Lush agricultural foliage green
val OrangeOnTertiary = Color(0xFFFFFFFF)
val OrangeTertiaryContainer = Color(0xFFC8E6C9)
val OrangeOnTertiaryContainer = Color(0xFF1B5E20)

val LightBackground = Color(0xFFFFFBF7)        // Warm off-white
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFFFF3E0)     // Warm orange-tinted card surface
val LightOnSurface = Color(0xFF212121)
val LightOnSurfaceVariant = Color(0xFF5D4037)

val DarkBackground = Color(0xFF19120C)         // Deep warm charcoal
val DarkSurface = Color(0xFF231A14)
val DarkSurfaceVariant = Color(0xFF33251D)
val DarkOnSurface = Color(0xFFF5EBE6)
val DarkOnSurfaceVariant = Color(0xFFD7C2B8)

val PentasixLightColorScheme = lightColorScheme(
    primary = OrangePrimary,
    onPrimary = OrangeOnPrimary,
    primaryContainer = OrangePrimaryContainer,
    onPrimaryContainer = OrangeOnPrimaryContainer,
    secondary = OrangeSecondary,
    onSecondary = OrangeOnSecondary,
    secondaryContainer = OrangeSecondaryContainer,
    onSecondaryContainer = OrangeOnSecondaryContainer,
    tertiary = OrangeTertiary,
    onTertiary = OrangeOnTertiary,
    tertiaryContainer = OrangeTertiaryContainer,
    onTertiaryContainer = OrangeOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = Color(0xFFD32F2F),
    errorContainer = Color(0xFFFFEBEE),
    onError = Color.White,
    onErrorContainer = Color(0xFFB71C1C)
)

val PentasixDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFFE65100),
    onPrimaryContainer = Color(0xFFFFE0B2),
    secondary = Color(0xFFBCAAA4),
    onSecondary = Color(0xFF3E2723),
    secondaryContainer = Color(0xFF5D4037),
    onSecondaryContainer = Color(0xFFD7CCC8),
    tertiary = Color(0xFF81C784),
    onTertiary = Color(0xFF003300),
    tertiaryContainer = Color(0xFF2E7D32),
    onTertiaryContainer = Color(0xFFC8E6C9),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = Color(0xFFEF5350),
    errorContainer = Color(0xFFB71C1C),
    onError = Color.Black,
    onErrorContainer = Color(0xFFFFCDD2)
)

/**
 * Pentasix Material 3 Theme with signature Harvest Orange primary styling.
 */
@Composable
fun PentasixTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PentasixDarkColorScheme else PentasixLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
