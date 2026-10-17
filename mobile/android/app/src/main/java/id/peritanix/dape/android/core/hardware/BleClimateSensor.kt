package id.peritanix.dape.android.core.hardware

import kotlin.math.max

/**
 * TAHAP 4.3 — BLE ESP32 microclimate sensor pull (offline GDD).
 *
 * In-field ESP32 data-loggers broadcast temperature/humidity via BLE advertising
 * (no bonding, no internet). This module parses the manufacturer-specific data
 * payload (a tiny packed frame) into readings and accumulates offline GDD
 * (Base 15 °C). The Android `BluetoothLeScanner` that delivers `ScanResult`s
 * lives in the listener layer; the frame parsing + GDD accumulation here is pure
 * and unit-tested.
 *
 * Packed frame (little-endian), 6 bytes, after the 2-byte manufacturer ID:
 *   [0..1] temperature  int16, tenths of °C  (e.g. 285 = 28.5 °C)
 *   [2..3] humidity     uint16, tenths of %  (e.g. 820 = 82.0 %)
 *   [4..5] battery mV   uint16               (e.g. 3300 = 3.30 V)
 */
object BleClimateSensor {

    /** DAPE ESP32 manufacturer company ID (0xFFFF-style demo id). */
    const val DAPE_MANUFACTURER_ID = 0x0DA9

    data class Reading(val temperatureC: Double, val relativeHumidityPct: Double, val batteryMv: Int)

    /**
     * Parse a BLE manufacturer-data frame. `data` must start with the 2-byte
     * manufacturer ID. Returns null on wrong id or short payload (safe for the
     * chaotic RF field where foreign devices are also advertising).
     */
    fun parseManufacturerData(data: ByteArray?): Reading? {
        if (data == null || data.size < 8) return null
        val mfr = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
        if (mfr != DAPE_MANUFACTURER_ID) return null
        val tempRaw = (data[2].toInt() and 0xFF) or (data[3].toInt() shl 8) // signed int16
        val humRaw = (data[4].toInt() and 0xFF) or ((data[5].toInt() and 0xFF) shl 8)
        val battRaw = (data[6].toInt() and 0xFF) or ((data[7].toInt() and 0xFF) shl 8)
        val tempC = tempRaw / 10.0
        val rh = humRaw / 10.0
        // Reject implausible readings (RF corruption).
        if (tempC < -20.0 || tempC > 60.0 || rh < 0.0 || rh > 100.0) return null
        return Reading(temperatureC = tempC, relativeHumidityPct = rh, batteryMv = battRaw)
    }

    /**
     * Build a frame for round-trip / test harness use (10th-of-a-unit encoding).
     */
    fun buildFrame(temperatureC: Double, relativeHumidityPct: Double, batteryMv: Int): ByteArray {
        val t = Math.round(temperatureC * 10.0).toInt()
        val h = Math.round(relativeHumidityPct * 10.0).toInt()
        return byteArrayOf(
            (DAPE_MANUFACTURER_ID and 0xFF).toByte(),
            ((DAPE_MANUFACTURER_ID shr 8) and 0xFF).toByte(),
            (t and 0xFF).toByte(), ((t shr 8) and 0xFF).toByte(),
            (h and 0xFF).toByte(), ((h shr 8) and 0xFF).toByte(),
            (batteryMv and 0xFF).toByte(), ((batteryMv shr 8) and 0xFF).toByte()
        )
    }

    /**
     * Accumulate offline GDD from a series of BLE Readings sampled at [intervalHours].
     * GDD_day = max(0, mean(Tmax,Tmin) − 15). For a single reading per interval we
     * approximate the daily mean by the average temperature of the readings seen.
     */
    data class GddAccumulation(val accumulatedGdd: Double, val sampleCount: Int, val meanTempC: Double)

    fun accumulateGdd(readings: List<Reading>, baseTempC: Double = 15.0): GddAccumulation {
        if (readings.isEmpty()) return GddAccumulation(0.0, 0, 0.0)
        val meanT = readings.map { it.temperatureC }.average()
        // Treat the average as one representative day's mean (offline daily estimate).
        val daily = max(0.0, meanT - baseTempC)
        return GddAccumulation(
            accumulatedGdd = Math.round(daily * 10.0) / 10.0,
            sampleCount = readings.size,
            meanTempC = Math.round(meanT * 10.0) / 10.0
        )
    }

    /** Battery threshold (mV) below which the logger should be recharged. */
    fun isBatteryLow(batteryMv: Int): Boolean = batteryMv in 1..3199
}
