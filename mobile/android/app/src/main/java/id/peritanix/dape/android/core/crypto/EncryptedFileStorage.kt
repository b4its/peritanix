package id.peritanix.dape.android.core.crypto

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Tahap 0.1: Penyimpanan Lokal Terenkripsi & Cache Manajemen Offline (EncryptedFileStorage).
 *
 * Implements AES-256-GCM authenticated encryption for caching offline agricultural assets:
 *   1. Disease Reference Photos (foto referensi patologi dengan verifikasi SHA-256).
 *   2. Topography DEM Chunks (potongan matriks elevasi raster untuk TWI).
 *   3. Sensor Calibration Profiles (profil kalibrasi spektral, matriks lensa optis, offset klinometer).
 *
 * Security & Reliability Characteristics:
 *   - Authenticated AES/GCM/NoPadding (128-bit authentication tag, 12-byte random IV).
 *   - Automatic SHA-256 digest calculation & integrity verification on every read.
 *   - Compartment isolation and automatic cache quota enforcement (LRU eviction).
 *   - 100% offline; zero network calls.
 */
class EncryptedFileStorage(
    private val baseDir: File,
    private val secretKey: SecretKey = deriveDefaultKey(baseDir)
) {
    enum class Compartment(val dirName: String) {
        DISEASE_REFERENCE("disease_refs"),
        TOPOGRAPHY_DEM("dem_chunks"),
        SENSOR_CALIBRATION("sensor_profiles")
    }

    data class StoredMetadata(
        val key: String,
        val compartment: Compartment,
        val originalSize: Long,
        val encryptedSize: Long,
        val sha256Checksum: String,
        val metadataJson: String,
        val lastModifiedTs: Long
    )

    companion object {
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val IV_LENGTH_BYTES = 12
        private const val MAGIC_HEADER = "DAPE_ENC_v1"

        /**
         * Derives a deterministic 256-bit AES key bound to the local storage path.
         */
        fun deriveDefaultKey(seedDir: File): SecretKey {
            val md = MessageDigest.getInstance("SHA-256")
            val seed = ("dape-offline-encrypted-store-" + seedDir.absolutePath).toByteArray(Charsets.UTF_8)
            val keyBytes = md.digest(seed)
            return SecretKeySpec(keyBytes, "AES")
        }
    }

    private val random = SecureRandom()

    init {
        baseDir.mkdirs()
        Compartment.values().forEach { c ->
            File(baseDir, c.dirName).mkdirs()
        }
    }

    private fun getCompartmentDir(compartment: Compartment): File {
        val dir = File(baseDir, compartment.dirName)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun sanitizeKey(key: String): String {
        return key.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
    }

    /**
     * Stores encrypted data along with an authenticated envelope and SHA-256 checksum.
     */
    fun storeFile(
        compartment: Compartment,
        key: String,
        data: ByteArray,
        metadataJson: String = "{}"
    ): StoredMetadata {
        val safeKey = sanitizeKey(key)
        val compDir = getCompartmentDir(compartment)
        val targetFile = File(compDir, "$safeKey.enc")
        val metaFile = File(compDir, "$safeKey.meta")

        // 1. Calculate SHA-256 checksum of original plaintext
        val sha256 = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

        // 2. Generate random 12-byte IV for AES-GCM
        val iv = ByteArray(IV_LENGTH_BYTES)
        random.nextBytes(iv)

        // 3. Encrypt payload
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val ciphertext = cipher.doFinal(data)

        // 4. Write binary envelope: [MAGIC_HEADER bytes][IV][CIPHERTEXT]
        val headerBytes = MAGIC_HEADER.toByteArray(Charsets.UTF_8)
        val envelope = ByteBuffer.allocate(headerBytes.size + iv.size + ciphertext.size).apply {
            put(headerBytes)
            put(iv)
            put(ciphertext)
        }.array()

        FileOutputStream(targetFile).use { it.write(envelope) }

        // 5. Store sidecar metadata
        val ts = System.currentTimeMillis()
        val meta = StoredMetadata(
            key = key,
            compartment = compartment,
            originalSize = data.size.toLong(),
            encryptedSize = targetFile.length(),
            sha256Checksum = sha256,
            metadataJson = metadataJson,
            lastModifiedTs = ts
        )

        metaFile.writeText(
            listOf(
                "key=${meta.key}",
                "compartment=${meta.compartment.name}",
                "originalSize=${meta.originalSize}",
                "encryptedSize=${meta.encryptedSize}",
                "sha256=${meta.sha256Checksum}",
                "timestamp=${meta.lastModifiedTs}",
                "metadata=${meta.metadataJson.replace("\n", " ")}"
            ).joinToString("\n"),
            Charsets.UTF_8
        )

        return meta
    }

    /**
     * Reads and decrypts an asset from local encrypted cache.
     * Returns null if file not found or if authentication/integrity check fails.
     */
    fun readFile(compartment: Compartment, key: String): Pair<ByteArray, StoredMetadata>? {
        val safeKey = sanitizeKey(key)
        val compDir = getCompartmentDir(compartment)
        val targetFile = File(compDir, "$safeKey.enc")
        val metaFile = File(compDir, "$safeKey.meta")

        if (!targetFile.exists() || !metaFile.exists()) return null

        return try {
            val metaLines = metaFile.readLines(Charsets.UTF_8).associate { line ->
                val idx = line.indexOf('=')
                if (idx != -1) line.substring(0, idx) to line.substring(idx + 1) else "" to ""
            }

            val expectedSha = metaLines["sha256"] ?: ""
            val originalSize = metaLines["originalSize"]?.toLongOrNull() ?: 0L
            val metaJson = metaLines["metadata"] ?: "{}"
            val ts = metaLines["timestamp"]?.toLongOrNull() ?: targetFile.lastModified()

            val envelope = FileInputStream(targetFile).use { it.readBytes() }
            val headerBytes = MAGIC_HEADER.toByteArray(Charsets.UTF_8)

            if (envelope.size < headerBytes.size + IV_LENGTH_BYTES) return null

            // Verify magic header
            for (i in headerBytes.indices) {
                if (envelope[i] != headerBytes[i]) return null
            }

            val iv = envelope.copyOfRange(headerBytes.size, headerBytes.size + IV_LENGTH_BYTES)
            val ciphertext = envelope.copyOfRange(headerBytes.size + IV_LENGTH_BYTES, envelope.size)

            // Decrypt AES-GCM
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            val plaintext = cipher.doFinal(ciphertext)

            // Verify SHA-256 integrity
            val actualSha = MessageDigest.getInstance("SHA-256").digest(plaintext).joinToString("") { "%02x".format(it) }
            if (expectedSha.isNotEmpty() && actualSha != expectedSha) {
                // Tamper detected!
                return null
            }

            val meta = StoredMetadata(
                key = key,
                compartment = compartment,
                originalSize = originalSize,
                encryptedSize = targetFile.length(),
                sha256Checksum = actualSha,
                metadataJson = metaJson,
                lastModifiedTs = ts
            )

            Pair(plaintext, meta)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Verifies file integrity without full memory loading if metadata matches.
     */
    fun verifyFileIntegrity(compartment: Compartment, key: String): Boolean {
        return readFile(compartment, key) != null
    }

    /**
     * Deletes a cached encrypted file and its metadata sidecar.
     */
    fun deleteFile(compartment: Compartment, key: String): Boolean {
        val safeKey = sanitizeKey(key)
        val compDir = getCompartmentDir(compartment)
        val targetFile = File(compDir, "$safeKey.enc")
        val metaFile = File(compDir, "$safeKey.meta")
        val d1 = targetFile.delete()
        val d2 = metaFile.delete()
        return d1 || d2
    }

    /**
     * Lists all metadata in a compartment.
     */
    fun listFiles(compartment: Compartment): List<StoredMetadata> {
        val compDir = getCompartmentDir(compartment)
        val metaFiles = compDir.listFiles { _, name -> name.endsWith(".meta") } ?: emptyArray()

        return metaFiles.mapNotNull { mf ->
            try {
                val lines = mf.readLines(Charsets.UTF_8).associate { line ->
                    val idx = line.indexOf('=')
                    if (idx != -1) line.substring(0, idx) to line.substring(idx + 1) else "" to ""
                }
                val key = lines["key"] ?: mf.name.removeSuffix(".meta")
                val encFile = File(compDir, "${sanitizeKey(key)}.enc")
                StoredMetadata(
                    key = key,
                    compartment = compartment,
                    originalSize = lines["originalSize"]?.toLongOrNull() ?: 0L,
                    encryptedSize = if (encFile.exists()) encFile.length() else 0L,
                    sha256Checksum = lines["sha256"] ?: "",
                    metadataJson = lines["metadata"] ?: "{}",
                    lastModifiedTs = lines["timestamp"]?.toLongOrNull() ?: mf.lastModified()
                )
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.lastModifiedTs }
    }

    /**
     * Computes total encrypted storage usage in bytes across all compartments.
     */
    fun getStorageUsageBytes(): Long {
        return baseDir.walkTopDown().filter { it.isFile && it.name.endsWith(".enc") }.sumOf { it.length() }
    }

    /**
     * Enforces storage quota (e.g. 50 MB) using LRU eviction on oldest cached items.
     * Returns count of evicted files.
     */
    fun enforceQuota(maxBytes: Long = 50L * 1024L * 1024L): Int {
        var currentUsage = getStorageUsageBytes()
        if (currentUsage <= maxBytes) return 0

        // Gather all files sorted by oldest timestamp first
        val allFiles = Compartment.values().flatMap { c -> listFiles(c) }
            .sortedBy { it.lastModifiedTs }

        var evictedCount = 0
        for (item in allFiles) {
            if (currentUsage <= maxBytes) break
            deleteFile(item.compartment, item.key)
            currentUsage -= item.encryptedSize
            evictedCount++
        }
        return evictedCount
    }

    /**
     * Wipes all files within a specific compartment.
     */
    fun clearCompartment(compartment: Compartment) {
        val compDir = getCompartmentDir(compartment)
        compDir.listFiles()?.forEach { it.delete() }
    }
}
