package id.peritanix.dape.android

import id.peritanix.dape.android.core.physics.RainEvent
import id.peritanix.dape.android.core.physics.WeatherCondition
import id.peritanix.dape.android.core.physics.WeatherRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WeatherRepositoryTest {

    private lateinit var repo: WeatherRepository

    @Before
    fun setUp() {
        // Runs completely in-memory without needing Android Context or mock framework
        repo = WeatherRepository()
    }

    @Test
    fun testWeatherConditionDefaultsAndWmoCodes() {
        // Verify default physical attributes
        assertEquals("Cerah Terik", WeatherCondition.CERAH.label)
        assertFalse(WeatherCondition.CERAH.isRaining)
        assertEquals(0.0, WeatherCondition.CERAH.defaultRainRateMmH, 0.01)

        assertEquals("Cerah Berawan", WeatherCondition.BERAWAN.label)
        assertFalse(WeatherCondition.BERAWAN.isRaining)

        assertEquals("Hujan Sedang", WeatherCondition.HUJAN_SEDANG.label)
        assertTrue(WeatherCondition.HUJAN_SEDANG.isRaining)
        assertEquals(12.0, WeatherCondition.HUJAN_SEDANG.defaultRainRateMmH, 0.01)

        assertEquals("Hujan Deras / Lebat", WeatherCondition.HUJAN_LEBAT.label)
        assertTrue(WeatherCondition.HUJAN_LEBAT.isRaining)
        assertEquals(30.0, WeatherCondition.HUJAN_LEBAT.defaultRainRateMmH, 0.01)

        // Verify WMO Code Conversions
        assertEquals(WeatherCondition.CERAH, WeatherCondition.fromWmoCode(0))
        assertEquals(WeatherCondition.CERAH, WeatherCondition.fromWmoCode(1))
        assertEquals(WeatherCondition.BERAWAN, WeatherCondition.fromWmoCode(2))
        assertEquals(WeatherCondition.MENDUNG, WeatherCondition.fromWmoCode(3))
        assertEquals(WeatherCondition.HUJAN_RINGAN, WeatherCondition.fromWmoCode(61))
        assertEquals(WeatherCondition.HUJAN_RINGAN, WeatherCondition.fromWmoCode(51))
        assertEquals(WeatherCondition.HUJAN_SEDANG, WeatherCondition.fromWmoCode(63))
        assertEquals(WeatherCondition.HUJAN_LEBAT, WeatherCondition.fromWmoCode(65))
        assertEquals(WeatherCondition.BADAI, WeatherCondition.fromWmoCode(95)) // Thunderstorm
    }

    @Test
    fun testOfflineWeatherConditionSelection() {
        // Set to Cerah
        repo.setOfflineWeather(WeatherCondition.CERAH)
        assertEquals(WeatherCondition.CERAH, repo.currentCondition)
        assertEquals(WeatherCondition.CERAH.defaultTempC, repo.currentTempC, 0.1)
        assertEquals(WeatherCondition.CERAH.defaultRhPct, repo.currentRhPct, 0.1)
        assertFalse("Cerah should not be raining", repo.isRaining)

        // Set to Hujan Sedang
        repo.setOfflineWeather(WeatherCondition.HUJAN_SEDANG)
        assertEquals(WeatherCondition.HUJAN_SEDANG, repo.currentCondition)
        assertEquals(WeatherCondition.HUJAN_SEDANG.defaultTempC, repo.currentTempC, 0.1)
        assertEquals(WeatherCondition.HUJAN_SEDANG.defaultRhPct, repo.currentRhPct, 0.1)
        assertTrue("Hujan sedang should automatically trigger isRaining=true", repo.isRaining)
        assertTrue("Rain start timestamp should be set", repo.rainStartTimeMs > 0L)
    }

    @Test
    fun testRainStopwatchLifecycleAndDurationCalculation() {
        // Start rain tracking
        val startMs = repo.startRain(WeatherCondition.HUJAN_LEBAT)
        assertTrue(repo.isRaining)
        assertEquals(WeatherCondition.HUJAN_LEBAT, repo.currentCondition)
        assertEquals(startMs, repo.rainStartTimeMs)

        // Stop rain tracking
        val event = repo.stopRain()
        assertFalse(repo.isRaining)
        assertEquals(0L, repo.rainStartTimeMs)
        assertEquals(WeatherCondition.MENDUNG, repo.currentCondition)
        assertNotNull(repo.lastFinishedRainEvent)
        assertEquals(event.id, repo.lastFinishedRainEvent?.id)

        // Verify duration and rainfall calculation
        assertTrue("Duration seconds should be >= 1", event.durationSeconds >= 1)
        assertTrue("Estimated rainfall should be > 0", event.estimatedRainfallMm > 0.0)
        assertNotNull(event.pathogenRiskWarning)

        // Verify history list
        val history = repo.getRainEvents()
        assertEquals(1, history.size)
        assertEquals(event.id, history[0].id)
    }

    @Test
    fun testRainEventFormattingAndPathogenRiskAssessment() {
        // 1. Short rain (< 1 hour): 1800 seconds (30 mins)
        val shortEvent = RainEvent(
            startTimeMs = 1700000000000L,
            endTimeMs = 1700001800000L,
            durationSeconds = 1800L,
            condition = WeatherCondition.HUJAN_SEDANG,
            estimatedRainfallMm = 5.0,
            pathogenRiskWarning = "RISIKO RENDAH: Pembasahan singkat di bawah ambang sporulasi jamur patek."
        )
        assertEquals("30 mnt 0 dtk", shortEvent.durationFormatted)
        assertTrue(shortEvent.pathogenRiskWarning.contains("RISIKO RENDAH"))

        // 2. Long rain (> 3 hours): 12600 seconds (3.5 hours)
        val severeEvent = RainEvent(
            startTimeMs = 1700000000000L,
            endTimeMs = 1700012600000L,
            durationSeconds = 12600L,
            condition = WeatherCondition.HUJAN_LEBAT,
            estimatedRainfallMm = 98.0,
            pathogenRiskWarning = "RISIKO SANGAT TINGGI: Daun basah >3 jam. Spora Colletotrichum berkecambah masif & parit jenuh air!"
        )
        assertEquals("3 jam 30 mnt", severeEvent.durationFormatted)
        assertTrue(severeEvent.pathogenRiskWarning.contains("RISIKO SANGAT TINGGI"))
    }

    @Test
    fun testOfflineKopengHighlandAgroclimatologyBaseline() {
        val result = repo.lastSyncResult
        assertNotNull("Offline baseline must be automatically initialized", result)
        assertEquals(30, result!!.history30Days.size)
        assertTrue("7-day forecast should contain today + 7 days (8 items)", result.forecast7Days.size >= 8)

        // Verify GDD calculations
        assertTrue("30-day accumulated GDD should be greater than 100 °C-h", result.accumulatedGdd30Days > 100.0)
        assertTrue("30-day total rainfall should be greater than 0 mm", result.totalRainfall30DaysMm > 0.0)
        assertTrue("Rainy days count should be > 0", result.rainyDays30DaysCount > 0)

        // Verify each daily summary satisfies T_base = 15°C
        result.history30Days.forEach { day ->
            val expectedMean = (day.tMinC + day.tMaxC) / 2.0
            val expectedGdd = kotlin.math.max(0.0, expectedMean - 15.0)
            assertEquals(expectedGdd, day.dailyGdd, 0.15)
        }
    }

    @Test
    fun testClearRainEvents() {
        repo.startRain(WeatherCondition.HUJAN_SEDANG)
        repo.stopRain()
        assertEquals(1, repo.getRainEvents().size)
        assertNotNull(repo.lastFinishedRainEvent)

        repo.clearRainEvents()
        assertEquals(0, repo.getRainEvents().size)
        assertNull(repo.lastFinishedRainEvent)
    }
}
