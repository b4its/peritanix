package id.peritanix.dape.android.core.physics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Agro-meteorological models for Pentasix.
 *
 * Supports offline farmer observation, real-time rain stopwatch,
 * 30-day historical agroclimate GDD, and Open-Meteo multi-model forecast.
 */

enum class WeatherCondition(
    val code: String,
    val label: String,
    val isRaining: Boolean,
    val defaultTempC: Double,
    val defaultRhPct: Double,
    val defaultWindKmh: Double,
    val defaultRainRateMmH: Double
) {
    CERAH("CERAH", "Cerah Terik", false, 29.5, 62.0, 7.2, 0.0),
    BERAWAN("BERAWAN", "Cerah Berawan", false, 25.5, 74.0, 8.5, 0.0),
    MENDUNG("MENDUNG", "Mendung Tebal", false, 22.0, 86.0, 11.0, 0.0),
    HUJAN_RINGAN("HUJAN_RINGAN", "Hujan Gerimis / Ringan", true, 20.5, 93.0, 12.5, 3.5),
    HUJAN_SEDANG("HUJAN_SEDANG", "Hujan Sedang", true, 19.5, 96.0, 15.0, 12.0),
    HUJAN_LEBAT("HUJAN_LEBAT", "Hujan Deras / Lebat", true, 18.0, 99.0, 18.5, 30.0),
    BADAI("BADAI", "Hujan Badai & Angin", true, 17.0, 99.0, 28.0, 50.0);

    companion object {
        fun fromWmoCode(code: Int): WeatherCondition {
            return when (code) {
                0, 1 -> CERAH
                2 -> BERAWAN
                3 -> MENDUNG
                51, 53, 55, 61 -> HUJAN_RINGAN
                63, 80 -> HUJAN_SEDANG
                65, 81, 82 -> HUJAN_LEBAT
                95, 96, 99 -> BADAI
                else -> if (code in 50..99) HUJAN_RINGAN else BERAWAN
            }
        }
    }
}

data class RainEvent(
    val id: String = UUID.randomUUID().toString(),
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationSeconds: Long,
    val condition: WeatherCondition,
    val estimatedRainfallMm: Double,
    val pathogenRiskWarning: String
) {
    val durationFormatted: String
        get() {
            val minutes = durationSeconds / 60
            val hours = minutes / 60
            val remMinutes = minutes % 60
            val seconds = durationSeconds % 60
            return when {
                hours > 0 -> "${hours} jam ${remMinutes} mnt"
                minutes > 0 -> "${minutes} mnt ${seconds} dtk"
                else -> "${seconds} dtk"
            }
        }

    val dateFormatted: String
        get() {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
            return sdf.format(Date(startTimeMs))
        }
}

data class DailyWeatherSummary(
    val date: String, // YYYY-MM-DD
    val tMinC: Double,
    val tMaxC: Double,
    val precipitationMm: Double,
    val precipProbabilityPct: Int,
    val windSpeedKmh: Double,
    val windDirectionDeg: Double,
    val weatherCode: Int,
    val weatherLabel: String,
    val dailyGdd: Double,
    val isHistorical: Boolean
) {
    val dateDisplayFormatted: String
        get() {
            return try {
                val input = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
                SimpleDateFormat("EEE, dd MMM", Locale("id", "ID")).format(input ?: Date())
            } catch (e: Exception) {
                date
            }
        }
}

data class WeatherSyncResult(
    val currentTempC: Double,
    val currentRhPct: Double,
    val currentWindKmh: Double,
    val currentWindDirectionDeg: Double,
    val currentCondition: WeatherCondition,
    val currentPrecipitationMm: Double,
    val accumulatedGdd30Days: Double,
    val totalRainfall30DaysMm: Double,
    val rainyDays30DaysCount: Int,
    val history30Days: List<DailyWeatherSummary>,
    val forecast7Days: List<DailyWeatherSummary>,
    val lastSyncTimestampMs: Long,
    val isOnlineSynced: Boolean,
    val dataSource: String = "Open-Meteo ECMWF / ERA5"
)
