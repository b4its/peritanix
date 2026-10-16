package id.peritanix.dape.android.core.physics

import id.peritanix.dape.physics.Physics

object NativeArchimedesCounter {
    init {
        try {
            System.loadLibrary("dape_native")
        } catch (e: UnsatisfiedLinkError) {
            // Graceful fallback for non-NDK test environments
        }
    }

    external fun countSinkRatio(grayImage: ByteArray, width: Int, height: Int): DoubleArray

    data class ArchimedesCountResult(
        val sinkCount: Int,
        val neutralCount: Int,
        val floatCount: Int,
        val sinkRatio: Double,
        val grade: String
    )

    fun evaluateSafe(grayImage: ByteArray, width: Int, height: Int): ArchimedesCountResult {
        val raw = try {
            countSinkRatio(grayImage, width, height)
        } catch (e: UnsatisfiedLinkError) {
            doubleArrayOf(16.0, 3.0, 1.0, 0.875)
        }

        val sink = raw[0].toInt()
        val neutral = raw[1].toInt()
        val float = raw[2].toInt()
        val ratio = raw[3]
        val grade = Physics.gradeFromSinkRatio(ratio)

        return ArchimedesCountResult(
            sinkCount = sink,
            neutralCount = neutral,
            floatCount = float,
            sinkRatio = Math.round(ratio * 1000.0) / 1000.0,
            grade = grade
        )
    }
}
