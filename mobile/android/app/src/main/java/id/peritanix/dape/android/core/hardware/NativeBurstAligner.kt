package id.peritanix.dape.android.core.hardware

object NativeBurstAligner {
    init {
        try {
            System.loadLibrary("dape_native")
        } catch (e: UnsatisfiedLinkError) {
            // Graceful fallback for non-NDK test environments
        }
    }

    external fun computeSharpnessLaplacian(yChannel: ByteArray, width: Int, height: Int): Double

    fun computeSharpnessSafe(yChannel: ByteArray, width: Int, height: Int): Double {
        return try {
            computeSharpnessLaplacian(yChannel, width, height)
        } catch (e: UnsatisfiedLinkError) {
            120.0 // Standard sharpness baseline
        }
    }
}
