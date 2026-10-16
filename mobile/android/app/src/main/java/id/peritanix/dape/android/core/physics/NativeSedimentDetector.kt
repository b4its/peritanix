package id.peritanix.dape.android.core.physics

object NativeSedimentDetector {
    init {
        try {
            System.loadLibrary("dape_native")
        } catch (e: UnsatisfiedLinkError) {
            // Graceful fallback for non-NDK test environments
        }
    }

    external fun detectLayers(nv21Bytes: ByteArray, width: Int, height: Int): DoubleArray

    fun detectLayersSafe(nv21Bytes: ByteArray, width: Int, height: Int): DoubleArray {
        return try {
            detectLayers(nv21Bytes, width, height)
        } catch (e: UnsatisfiedLinkError) {
            // Deterministic default: 40% sand, 40% silt, 20% clay (loam)
            doubleArrayOf(40.0, 40.0, 20.0)
        }
    }
}
