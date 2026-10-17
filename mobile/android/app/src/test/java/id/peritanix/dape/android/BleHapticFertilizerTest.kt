package id.peritanix.dape.android

import id.peritanix.dape.android.core.hardware.BleClimateSensor
import id.peritanix.dape.android.core.hardware.HapticPatterns
import id.peritanix.dape.android.core.hardware.HapticPatterns.FieldEvent
import id.peritanix.dape.android.core.physics.FertilizerCostOptimizer
import id.peritanix.dape.android.core.physics.FertilizerCostOptimizer.Product
import id.peritanix.dape.android.core.physics.FertilizerCostOptimizer.Requirement
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for three Tahap 4 / 2 / 6 deliverables:
 *  - BleClimateSensor (ESP32 BLE frame parse + offline GDD accumulation)
 *  - HapticPatterns (distinct tactile waveforms + sunlight amplitude)
 *  - FertilizerCostOptimizer (cheapest equivalent-nutrient mix)
 */
class BleHapticFertilizerTest {

    // ---- BleClimateSensor ---------------------------------------------------

    @Test
    fun testBleRoundTrip() {
        val frame = BleClimateSensor.buildFrame(28.5, 82.0, 3300)
        val r = BleClimateSensor.parseManufacturerData(frame)
        assertNotNull(r)
        r!!
        assertEquals(28.5, r.temperatureC, 0.001)
        assertEquals(82.0, r.relativeHumidityPct, 0.001)
        assertEquals(3300, r.batteryMv)
    }

    @Test
    fun testBleRejectsWrongManufacturer() {
        val frame = BleClimateSensor.buildFrame(25.0, 70.0, 3200)
        frame[0] = 0x00; frame[1] = 0x00 // foreign company id
        assertNull(BleClimateSensor.parseManufacturerData(frame))
    }

    @Test
    fun testBleRejectsShortPayload() {
        assertNull(BleClimateSensor.parseManufacturerData(null))
        assertNull(BleClimateSensor.parseManufacturerData(byteArrayOf(0x01, 0x02)))
    }

    @Test
    fun testBleRejectsImplausible() {
        // 999.9 °C is corruption → rejected.
        val bad = BleClimateSensor.buildFrame(999.9, 50.0, 3300)
        assertNull(BleClimateSensor.parseManufacturerData(bad))
    }

    @Test
    fun testBleGddAccumulation() {
        val readings = listOf(
            BleClimateSensor.Reading(25.0, 70.0, 3300),
            BleClimateSensor.Reading(29.0, 75.0, 3300)   // mean 27 → GDD 12
        )
        val g = BleClimateSensor.accumulateGdd(readings)
        assertEquals(2, g.sampleCount)
        assertEquals(27.0, g.meanTempC, 0.01)
        assertEquals(12.0, g.accumulatedGdd, 0.01)
    }

    @Test
    fun testBleGddBelowBaseIsZero() {
        val g = BleClimateSensor.accumulateGdd(listOf(BleClimateSensor.Reading(10.0, 60.0, 3300)))
        assertEquals(0.0, g.accumulatedGdd, 0.001)
    }

    @Test
    fun testBleBatteryLow() {
        assertTrue(BleClimateSensor.isBatteryLow(3100))
        assertFalse(BleClimateSensor.isBatteryLow(3300))
        assertFalse(BleClimateSensor.isBatteryLow(0)) // 0 = unknown, not "low"
    }

    // ---- HapticPatterns -----------------------------------------------------

    @Test
    fun testHapticEventsDistinct() {
        val patterns = FieldEvent.values().map { HapticPatterns.forEvent(it) }
        // Each event maps to a distinct waveform (except intentional reuse if any).
        assertEquals(FieldEvent.values().size, patterns.toSet().size)
    }

    @Test
    fun testHapticPatternsNonEmpty() {
        FieldEvent.values().forEach { e ->
            val p = HapticPatterns.forEvent(e)
            assertTrue(p.timingsMs.isNotEmpty())
            assertTrue(p.label.isNotBlank())
        }
    }

    @Test
    fun testUreaHazardIsStrongTriple() {
        val p = HapticPatterns.UREA_HAZARD
        // 3 strong 400ms buzzes.
        assertEquals(3, p.timingsMs.count { it == 400L })
    }

    @Test
    fun testPendingSyncRepeats() {
        assertTrue(HapticPatterns.PENDING_SYNC.repeat)
        assertFalse(HapticPatterns.LEVEL_STEADY.repeat)
    }

    @Test
    fun testHapticAmplitudeScalesWithSun() {
        assertEquals(255, HapticPatterns.amplitudeForAmbientLux(30_000f))
        assertEquals(200, HapticPatterns.amplitudeForAmbientLux(6_000f))
        assertEquals(160, HapticPatterns.amplitudeForAmbientLux(1_000f))
        assertEquals(120, HapticPatterns.amplitudeForAmbientLux(100f))
        // Bright sun is stronger than shade.
        assertTrue(HapticPatterns.amplitudeForAmbientLux(30_000f) > HapticPatterns.amplitudeForAmbientLux(100f))
    }

    // ---- FertilizerCostOptimizer -------------------------------------------

    private val urea = Product("Urea 46%", nPct = 46.0, pPct = 0.0, kPct = 0.0, pricePerKg = 12000.0)
    private val tsp = Product("TSP 46%", nPct = 0.0, pPct = 46.0, kPct = 0.0, pricePerKg = 14000.0)
    private val kcl = Product("KCl 60%", nPct = 0.0, pPct = 0.0, kPct = 60.0, pricePerKg = 11000.0)
    private val npk = Product("NPK 16-16-16", nPct = 16.0, pPct = 16.0, kPct = 16.0, pricePerKg = 15000.0)

    @Test
    fun testOptimizeEmptyProducts() {
        val mix = FertilizerCostOptimizer.optimize(Requirement(100.0, 50.0, 50.0), emptyList())
        assertTrue(mix.allocations.isEmpty())
        assertTrue(mix.note.contains("internet", ignoreCase = true))
    }

    @Test
    fun testOptimizePicksCheapestPerNutrient() {
        val mix = FertilizerCostOptimizer.optimize(
            Requirement(nG = 92.0, pG = 46.0, kG = 60.0),
            listOf(urea, tsp, kcl, npk)
        )
        // Cheapest N = Urea, P = TSP, K = KCl (single-nutrient products).
        val names = mix.allocations.map { it.product.name }.toSet()
        assertTrue(names.contains("Urea 46%"))
        assertTrue(names.contains("TSP 46%"))
        assertTrue(names.contains("KCl 60%"))
        // Each nutrient requirement is met (within rounding).
        assertTrue(mix.delivered.nG >= 91.0)
        assertTrue(mix.delivered.pG >= 45.0)
        assertTrue(mix.delivered.kG >= 59.0)
    }

    @Test
    fun testOptimizeBeatsSingleNpk() {
        val req = Requirement(160.0, 160.0, 160.0)
        val mixed = FertilizerCostOptimizer.optimize(req, listOf(urea, tsp, kcl))
        val npkOnly = FertilizerCostOptimizer.optimize(req, listOf(npk))
        assertTrue("mixed should be cheaper than NPK-only",
            mixed.totalCostIdr < npkOnly.totalCostIdr)
        // Sanity: NPK-only needs 1 kg per 160 g of each nutrient (=1.0 kg for 16% *10).
        assertEquals(1.0, npkOnly.totalKg, 0.01)
    }

    @Test
    fun testOptimizeOnlyKnownNutrients() {
        // Requirement with only N → only a nitrogen product chosen.
        val mix = FertilizerCostOptimizer.optimize(Requirement(46.0, 0.0, 0.0), listOf(urea, tsp, kcl))
        assertEquals(1, mix.allocations.size)
        assertEquals("Urea 46%", mix.allocations[0].product.name)
    }

    @Test
    fun testOptimizeIgnoresZeroPrice() {
        val freeButInvalid = urea.copy(name = "Urea Gratis Basi", pricePerKg = 0.0)
        val mix = FertilizerCostOptimizer.optimize(Requirement(46.0, 0.0, 0.0), listOf(freeButInvalid))
        // Zero-price filtered → falls back to "no products" note.
        assertTrue(mix.allocations.isEmpty())
    }
}
