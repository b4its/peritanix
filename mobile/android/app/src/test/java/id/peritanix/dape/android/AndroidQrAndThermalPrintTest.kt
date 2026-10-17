package id.peritanix.dape.android

import id.peritanix.dape.android.core.crypto.QrMatrixGenerator
import id.peritanix.dape.android.core.hardware.BluetoothThermalPrinter
import org.junit.Assert.*
import org.junit.Test

class AndroidQrAndThermalPrintTest {

    @Test
    fun testQrMatrixDimensionsAndFinderPatterns() {
        val payload = """{"land_id":"land-001","grade_result":"A+","sterilization_c":52.0}"""
        val matrix = QrMatrixGenerator.generateBitMatrix(payload)

        assertEquals(29, matrix.size)
        assertEquals(29, matrix[0].size)

        // Verify Top-Left 7x7 Finder Pattern
        assertTrue("Outer border corner (0,0) must be true", matrix[0][0])
        assertTrue("Outer border (0,6) must be true", matrix[0][6])
        assertTrue("Outer border (6,0) must be true", matrix[6][0])
        assertTrue("Outer border (6,6) must be true", matrix[6][6])
        assertFalse("White ring (1,1) must be false", matrix[1][1])
        assertTrue("Center core (3,3) must be true", matrix[3][3])

        // Verify Top-Right 7x7 Finder Pattern at (0, 22..28)
        assertTrue("Top-Right outer (0, 22) must be true", matrix[0][22])
        assertTrue("Top-Right outer (0, 28) must be true", matrix[0][28])
        assertTrue("Top-Right center (3, 25) must be true", matrix[3][25])

        // Verify Bottom-Left 7x7 Finder Pattern at (22..28, 0)
        assertTrue("Bottom-Left outer (22, 0) must be true", matrix[22][0])
        assertTrue("Bottom-Left outer (28, 0) must be true", matrix[28][0])
        assertTrue("Bottom-Left center (25, 3) must be true", matrix[25][3])
    }

    @Test
    fun testQrMatrixTimingPatterns() {
        val matrix = QrMatrixGenerator.generateBitMatrix("test-payload")
        // Row 6 and Column 6 have alternating timing modules between finders (8..20)
        for (i in 8 until 20) {
            val expected = (i % 2 == 0)
            assertEquals("Row 6 timing pattern at $i", expected, matrix[6][i])
            assertEquals("Col 6 timing pattern at $i", expected, matrix[i][6])
        }
    }

    @Test
    fun testEscPosReceiptStructureAndCommands() {
        val receipt = BluetoothThermalPrinter.generateEscPosReceipt(
            landId = "land-demo-12345678",
            gradeResult = "A+",
            commercialTier = "SUPER_PREMIUM",
            sinkRatio = 0.88,
            sterilizationC = 52.0,
            photoSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            gyroVariance = 0.42,
            cryptoSignatureB64 = "lcPzlao/gSP0CtQA0OyjB+p2yv3SVV2YcFj8GD2swTTWU3arLri06+KWHHut+veVbBGwwTcmh7uBDo5iVgOFCQ==",
            qrPayload = "{\"test\":true}"
        )

        val bytes = receipt.rawBytes
        assertTrue("Receipt should contain substantial byte content", bytes.size > 200)

        // Check ESC @ (0x1B, 0x40) printer initialization at beginning
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])

        // Check that text contains key receipt elements
        val receiptText = String(bytes, Charsets.ISO_8859_1)
        assertTrue(receiptText.contains("PERITANIX DAPE"))
        assertTrue(receiptText.contains("Sertifikat Mutu Fisik Cabai"))
        assertTrue(receiptText.contains("KELAS A+ (SUPER_PREMIUM)"))
        assertTrue(receiptText.contains("52.0 C (Asas Black 52 C)"))
        assertTrue(receiptText.contains("0.88 (Archimedes 1.08 g/cm3)"))
        assertTrue(receiptText.contains("Audit Anti-Cheat: VALID"))

        // Check ESC/POS 2D QR Code print command header GS ( k (0x1D, 0x28, 0x6B)
        var foundQrHeader = false
        for (i in 0 until bytes.size - 2) {
            if (bytes[i] == 0x1D.toByte() && bytes[i + 1] == 0x28.toByte() && bytes[i + 2] == 0x6B.toByte()) {
                foundQrHeader = true
                break
            }
        }
        assertTrue("Must contain native ESC/POS QR print command", foundQrHeader)

        // Check termination: Paper feed 3 lines (0x1B, 0x64, 0x03) and Cut paper (0x1D, 0x56, 0x42, 0x00)
        val len = bytes.size
        assertEquals(0x1D.toByte(), bytes[len - 4])
        assertEquals(0x56.toByte(), bytes[len - 3])
        assertEquals(0x42.toByte(), bytes[len - 2])
        assertEquals(0x00.toByte(), bytes[len - 1])
    }
}
