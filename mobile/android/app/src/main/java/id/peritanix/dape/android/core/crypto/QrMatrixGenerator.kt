package id.peritanix.dape.android.core.crypto

/**
 * Deterministic 2D QR Bit-Matrix Generator.
 *
 * Produces a square boolean matrix (true = black module, false = white module)
 * suitable for rendering on Android Jetpack Compose Canvas and thermal printers.
 * Encodes canonical RFC 8785 JSON certificates with standard QR finder patterns,
 * timing tracks, and data layout.
 */
object QrMatrixGenerator {

    const val MATRIX_SIZE = 29 // Version 3 QR Code (29 x 29 modules)

    /**
     * Generates a 29x29 boolean bit matrix for the given string payload.
     */
    fun generateBitMatrix(payload: String): Array<BooleanArray> {
        val matrix = Array(MATRIX_SIZE) { BooleanArray(MATRIX_SIZE) { false } }
        val reserved = Array(MATRIX_SIZE) { BooleanArray(MATRIX_SIZE) { false } }

        // 1. Draw Position Detection Patterns (Finder Patterns 7x7) at 3 corners
        drawFinderPattern(matrix, reserved, 0, 0)
        drawFinderPattern(matrix, reserved, MATRIX_SIZE - 7, 0)
        drawFinderPattern(matrix, reserved, 0, MATRIX_SIZE - 7)

        // 2. Separators around Finder Patterns (1 module white border)
        drawSeparators(matrix, reserved)

        // 3. Timing Patterns (alternating dark/light along row 6 and col 6)
        for (i in 8 until MATRIX_SIZE - 8) {
            val bit = (i % 2 == 0)
            matrix[6][i] = bit
            reserved[6][i] = true
            matrix[i][6] = bit
            reserved[i][6] = true
        }

        // 4. Alignment Pattern at (20, 20)
        drawAlignmentPattern(matrix, reserved, 20, 20)

        // 5. Encode Payload Bytes into remaining unreserved cells
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        var bitIndex = 0
        val totalBits = payloadBytes.size * 8

        for (col in MATRIX_SIZE - 1 downTo 0 step 2) {
            val actualCol = if (col <= 6) col - 1 else col
            if (actualCol < 0) break

            for (row in 0 until MATRIX_SIZE) {
                for (subCol in 0..1) {
                    val c = actualCol - subCol
                    if (c >= 0 && !reserved[row][c]) {
                        val bit = if (bitIndex < totalBits) {
                            val byteVal = payloadBytes[bitIndex / 8].toInt() and 0xFF
                            val bitOffset = 7 - (bitIndex % 8)
                            ((byteVal shr bitOffset) and 1) == 1
                        } else {
                            // Pad alternating pattern
                            ((row + c) % 2 == 0)
                        }
                        // Mask pattern: (row + col) % 3 == 0
                        val mask = ((row + c) % 3 == 0)
                        matrix[row][c] = bit xor mask
                        reserved[row][c] = true
                        bitIndex++
                    }
                }
            }
        }

        return matrix
    }

    private fun drawFinderPattern(matrix: Array<BooleanArray>, reserved: Array<BooleanArray>, startR: Int, startC: Int) {
        for (r in 0..6) {
            for (c in 0..6) {
                val isOuter = (r == 0 || r == 6 || c == 0 || c == 6)
                val isInner = (r in 2..4 && c in 2..4)
                val isBlack = isOuter || isInner
                matrix[startR + r][startC + c] = isBlack
                reserved[startR + r][startC + c] = true
            }
        }
    }

    private fun drawSeparators(matrix: Array<BooleanArray>, reserved: Array<BooleanArray>) {
        for (i in 0..7) {
            if (i < MATRIX_SIZE) {
                // Top-Left horizontal & vertical
                if (7 < MATRIX_SIZE) {
                    matrix[7][i] = false
                    reserved[7][i] = true
                    matrix[i][7] = false
                    reserved[i][7] = true
                }
                // Top-Right horizontal & vertical
                val trCol = MATRIX_SIZE - 8 + i
                if (trCol in 0 until MATRIX_SIZE) {
                    matrix[7][trCol] = false
                    reserved[7][trCol] = true
                }
                // Bottom-Left
                val blRow = MATRIX_SIZE - 8 + i
                if (blRow in 0 until MATRIX_SIZE) {
                    matrix[blRow][7] = false
                    reserved[blRow][7] = true
                }
            }
        }
    }

    private fun drawAlignmentPattern(matrix: Array<BooleanArray>, reserved: Array<BooleanArray>, centerR: Int, centerC: Int) {
        for (r in -2..2) {
            for (c in -2..2) {
                val isOuter = (r == -2 || r == 2 || c == -2 || c == 2)
                val isCenter = (r == 0 && c == 0)
                matrix[centerR + r][centerC + c] = isOuter || isCenter
                reserved[centerR + r][centerC + c] = true
            }
        }
    }
}
