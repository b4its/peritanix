package id.peritanix.dape.android

import id.peritanix.dape.android.core.hardware.NativeBurstAligner
import id.peritanix.dape.android.core.physics.NativeArchimedesCounter
import id.peritanix.dape.android.core.physics.NativeSedimentDetector
import org.junit.Assert.*
import org.junit.Test

class AndroidNativeBridgesTest {

    @Test
    fun testNativeSedimentDetectorSafeFallback() {
        val dummyBytes = ByteArray(100) { 128.toByte() }
        val fractions = NativeSedimentDetector.detectLayersSafe(dummyBytes, 10, 10)
        assertEquals(3, fractions.size)
        assertEquals(40.0, fractions[0], 0.01) // 40% sand
        assertEquals(40.0, fractions[1], 0.01) // 40% silt
        assertEquals(20.0, fractions[2], 0.01) // 20% clay
    }

    @Test
    fun testNativeBurstAlignerSafeFallback() {
        val dummyYChannel = ByteArray(64) { 100.toByte() }
        val sharpness = NativeBurstAligner.computeSharpnessSafe(dummyYChannel, 8, 8)
        assertTrue(sharpness >= 100.0)
    }

    @Test
    fun testNativeArchimedesCounterSafeFallback() {
        val dummyNv21 = ByteArray(100)
        val res = NativeArchimedesCounter.evaluateSafe(dummyNv21, 10, 10)
        assertTrue(res.sinkCount + res.neutralCount + res.floatCount > 0)
        assertEquals("A+", res.grade)
        assertTrue(res.sinkRatio >= 0.80)
    }
}
