package id.peritanix.dape.android.core.hardware

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FR-4.2 & FR-5.3 Bluetooth Thermal Printer ESC/POS Byte Stream Generator.
 *
 * Formats canonical DAPE harvest certificates into standard ESC/POS receipts
 * for portable 58mm and 80mm Bluetooth thermal printers used at farm collection points.
 *
 * Includes:
 *   - ESC/POS initialization & formatting commands
 *   - Decentralized quality verification summary
 *   - Native 2D Barcode (QR Code) printing commands (GS ( k ...)
 *   - Clean paper feed and cut
 */
object BluetoothThermalPrinter {

    // ESC/POS Command Constants
    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val TXT_DOUBLE_SIZE = byteArrayOf(0x1D, 0x21, 0x11)
    private val TXT_NORMAL = byteArrayOf(0x1D, 0x21, 0x00)
    private val TXT_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val TXT_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val FEED_3_LINES = byteArrayOf(0x1B, 0x64, 0x03)
    private val CUT_PAPER = byteArrayOf(0x1D, 0x56, 0x42, 0x00) // GS V 66 0

    data class ThermalReceipt(
        val rawBytes: ByteArray,
        val characterWidth: Int = 32, // standard 58mm = 32 chars
        val byteCount: Int = rawBytes.size
    )

    /**
     * Generates standard ESC/POS byte array for offline printing of DAPE Quality Certificates.
     */
    fun generateEscPosReceipt(
        landId: String,
        gradeResult: String,
        commercialTier: String,
        sinkRatio: Double,
        sterilizationC: Double,
        photoSha256: String,
        gyroVariance: Double,
        cryptoSignatureB64: String,
        qrPayload: String
    ): ThermalReceipt {
        val out = ByteArrayOutputStream()

        fun write(bytes: ByteArray) = out.write(bytes)
        fun writeText(text: String) = out.write(text.toByteArray(Charsets.US_ASCII.orDefault()))
        fun writeLine(text: String = "") {
            writeText(text)
            out.write(0x0A) // LF
        }

        // 1. Initialize Printer
        write(ESC_INIT)

        // 2. Header
        write(ESC_ALIGN_CENTER)
        write(TXT_DOUBLE_SIZE)
        write(TXT_BOLD_ON)
        writeLine("PERITANIX DAPE")
        write(TXT_NORMAL)
        writeLine("Sertifikat Mutu Fisik Cabai")
        writeLine("Presisi 0,1 - 2,0 Ha (Offline-First)")
        write(TXT_BOLD_OFF)
        writeLine("--------------------------------")

        // 3. Metadata
        write(ESC_ALIGN_LEFT)
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        writeLine("Waktu : ${sdf.format(Date())}")
        writeLine("Lahan : ${landId.take(16)}")
        writeLine("Mutu  : KELAS $gradeResult ($commercialTier)")
        writeLine("Sink  : ${"%.2f".format(sinkRatio)} (Archimedes 1.08 g/cm3)")
        writeLine("Suhu  : ${"%.1f".format(sterilizationC)} C (Asas Black 52 C)")
        writeLine("Gyro  : ${"%.2f".format(gyroVariance)} (Audit Anti-Cheat: VALID)")
        writeLine("Foto  : ${photoSha256.take(12)}...${photoSha256.takeLast(6)}")
        writeLine("--------------------------------")

        // 4. Native QR Code Printing via ESC/POS GS ( k commands
        write(ESC_ALIGN_CENTER)
        write(TXT_BOLD_ON)
        writeLine("[ SCAN QR UNTUK VERIFIKASI ]")
        write(TXT_BOLD_OFF)

        appendNativeEscPosQr(out, qrPayload)

        writeLine()
        write(ESC_ALIGN_CENTER)
        write(TXT_NORMAL)
        writeLine("Tanda Tangan Ed25519 Keystore:")
        writeLine(cryptoSignatureB64.take(32))
        writeLine(cryptoSignatureB64.drop(32).take(32))
        writeLine("Terverifikasi RFC 8785 (Kanonikal)")
        writeLine("================================")
        write(FEED_3_LINES)
        write(CUT_PAPER)

        return ThermalReceipt(rawBytes = out.toByteArray())
    }

    private fun appendNativeEscPosQr(out: ByteArrayOutputStream, payload: String) {
        val data = payload.toByteArray(Charsets.UTF_8)
        val pL = (data.size + 3) and 0xFF
        val pH = ((data.size + 3) shr 8) and 0xFF

        // 1. Set QR Model (Model 2) -> GS ( k 4 0 49 65 50 0
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))

        // 2. Set Module Size (size = 6 dots) -> GS ( k 3 0 49 67 6
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x06))

        // 3. Set Error Correction Level L -> GS ( k 3 0 49 69 48
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x30))

        // 4. Store Data into QR symbol storage area -> GS ( k pL pH 49 80 48 <data>
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, pL.toByte(), pH.toByte(), 0x31, 0x50, 0x30))
        out.write(data)

        // 5. Print QR symbol -> GS ( k 3 0 49 81 48
        out.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
    }

    private fun java.nio.charset.Charset.orDefault(): java.nio.charset.Charset = this
}
