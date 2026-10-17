package id.peritanix.dape.android

import id.peritanix.dape.android.core.hardware.SensorCalibrationMath
import org.junit.Assert.*
import org.junit.Test

class SensorCalibrationMathTest {

    @Test
    fun testAzimuthToCardinalPoints() {
        // North (Utara)
        assertEquals(Pair("U", "Utara"), SensorCalibrationMath.azimuthToCardinal(0.0))
        assertEquals(Pair("U", "Utara"), SensorCalibrationMath.azimuthToCardinal(359.0))
        assertEquals(Pair("U", "Utara"), SensorCalibrationMath.azimuthToCardinal(10.0))

        // North-East (Timur Laut)
        assertEquals(Pair("TL", "Timur Laut"), SensorCalibrationMath.azimuthToCardinal(45.0))

        // East (Timur)
        assertEquals(Pair("T", "Timur"), SensorCalibrationMath.azimuthToCardinal(90.0))

        // South-East (Tenggara)
        assertEquals(Pair("TG", "Tenggara"), SensorCalibrationMath.azimuthToCardinal(135.0))

        // South (Selatan)
        assertEquals(Pair("S", "Selatan"), SensorCalibrationMath.azimuthToCardinal(180.0))

        // South-West (Barat Daya)
        assertEquals(Pair("BD", "Barat Daya"), SensorCalibrationMath.azimuthToCardinal(225.0))

        // West (Barat)
        assertEquals(Pair("B", "Barat"), SensorCalibrationMath.azimuthToCardinal(270.0))

        // North-West (Barat Laut)
        assertEquals(Pair("BL", "Barat Laut"), SensorCalibrationMath.azimuthToCardinal(315.0))
    }

    @Test
    fun testSmoothAzimuthAcrossZeroDegreeBoundary() {
        // When transitioning clockwise across 360/0 boundary (358° -> 2°)
        val smoothedFwd = SensorCalibrationMath.smoothAzimuth(358f, 2f, 0.5f)
        // Expected: diff = +4°, half step = +2°, result = 360° = 0°
        assertEquals(0f, smoothedFwd, 0.1f)

        // When transitioning counter-clockwise across 0/360 boundary (2° -> 358°)
        val smoothedRev = SensorCalibrationMath.smoothAzimuth(2f, 358f, 0.5f)
        // Expected: diff = -4°, half step = -2°, result = 0°
        assertEquals(0f, smoothedRev, 0.1f)

        // General rotation test (100° to 120° with alpha 0.25)
        val smoothedGen = SensorCalibrationMath.smoothAzimuth(100f, 120f, 0.25f)
        assertEquals(105f, smoothedGen, 0.1f)
    }

    @Test
    fun testHardIronOffsetCalculation() {
        // Simulated magnetometer figure-8 sweeps:
        // True center is shifted: X centered at +5.0, Y centered at -8.0, Z centered at +12.0
        val minX = -45f + 5f // -40f
        val maxX = 45f + 5f  // +50f
        val minY = -42f - 8f // -50f
        val maxY = 42f - 8f  // +34f
        val minZ = -38f + 12f // -26f
        val maxZ = 38f + 12f  // +50f

        val offsets = SensorCalibrationMath.calculateHardIronOffsets(minX, maxX, minY, maxY, minZ, maxZ)

        assertEquals(5f, offsets.offsetX, 0.01f)
        assertEquals(-8f, offsets.offsetY, 0.01f)
        assertEquals(12f, offsets.offsetZ, 0.01f)
        assertTrue("Offset harus terdeteksi terkalibrasi", offsets.isCalibrated)
    }

    @Test
    fun testMagneticMagnitudeAndStatus() {
        // Normal geomagnetic field: ~45 µT
        val normalMag = SensorCalibrationMath.calculateMagneticMagnitude(20f, 25f, 30f)
        assertEquals(43.87, normalMag, 0.1)
        assertEquals(SensorCalibrationMath.MagneticStatus.NORMAL, SensorCalibrationMath.evaluateMagneticStatus(normalMag))

        // High magnetic interference (near speaker / iron rebar): 95 µT
        val highMag = SensorCalibrationMath.calculateMagneticMagnitude(50f, 60f, 55f)
        assertEquals(SensorCalibrationMath.MagneticStatus.INTERFERENCE_HIGH, SensorCalibrationMath.evaluateMagneticStatus(highMag))

        // Weak shielded magnetic field: 15 µT
        val weakMag = 15.0
        assertEquals(SensorCalibrationMath.MagneticStatus.WEAK_SHIELDED, SensorCalibrationMath.evaluateMagneticStatus(weakMag))
    }

    @Test
    fun testTiltZeroLevelCalibration() {
        // Raw sensor has a mounting error of +2.0° pitch and -1.5° roll when placed flat
        val rawPitch = 2.0
        val rawRoll = -1.5

        val zeroOffset = SensorCalibrationMath.TiltOffset(
            pitchZeroOffset = 2.0,
            rollZeroOffset = -1.5
        )

        val (calPitch, calRoll) = SensorCalibrationMath.calibrateTilt(rawPitch, rawRoll, zeroOffset)
        assertEquals(0.0, calPitch, 0.01)
        assertEquals(0.0, calRoll, 0.01)
        assertTrue("Device harus dinilai datar/level", SensorCalibrationMath.isDeviceLevel(calPitch, calRoll))

        // When tilted beyond tolerance (e.g. pitch 5.0°)
        val (tiltedPitch, tiltedRoll) = SensorCalibrationMath.calibrateTilt(7.0, -1.5, zeroOffset)
        assertEquals(5.0, tiltedPitch, 0.01)
        assertFalse("Device harus dinilai tidak datar", SensorCalibrationMath.isDeviceLevel(tiltedPitch, tiltedRoll))
    }

    @Test
    fun testAgronomicHeadingAnalysis() {
        // North-South Ideal bed orientation
        val headingNorth = SensorCalibrationMath.evaluateAgronomicHeading(5.0)
        assertEquals(SensorCalibrationMath.BedOrientationType.NORTH_SOUTH, headingNorth.bedOrientationType)
        assertTrue("Orientasi Utara-Selatan harus ideal", headingNorth.isIdealBedOrientation)
        assertFalse("Tidak searah angin spora", headingNorth.isSporeWindAligned)

        val headingSouth = SensorCalibrationMath.evaluateAgronomicHeading(185.0)
        assertEquals(SensorCalibrationMath.BedOrientationType.NORTH_SOUTH, headingSouth.bedOrientationType)
        assertTrue(headingSouth.isIdealBedOrientation)

        // East-West warning
        val headingEast = SensorCalibrationMath.evaluateAgronomicHeading(95.0)
        assertEquals(SensorCalibrationMath.BedOrientationType.EAST_WEST, headingEast.bedOrientationType)
        assertFalse("Orientasi Timur-Barat bukan yang ideal", headingEast.isIdealBedOrientation)

        // Spore wind vector aligned (ENE ~65° ± 20°)
        val headingSporeWind = SensorCalibrationMath.evaluateAgronomicHeading(65.0)
        assertTrue("Harus terdeteksi searah vektor angin spora lereng Merbabu", headingSporeWind.isSporeWindAligned)
        assertTrue("Pesan harus menyebut risiko vektor angin spora", headingSporeWind.sporeWindRisk.contains("vektor angin spora", ignoreCase = true))
    }
}
