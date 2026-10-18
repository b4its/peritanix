package id.peritanix.dape.android

import id.peritanix.dape.android.core.crypto.EncryptedFileStorage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class EncryptedFileStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storage: EncryptedFileStorage

    @Before
    fun setUp() {
        storage = EncryptedFileStorage(tempFolder.root)
    }

    @Test
    fun testStoreAndRetrieveAllCompartments() {
        val diseasePhoto = "fake_jpg_anthracnose_lesion_bytes_12345".toByteArray(Charsets.UTF_8)
        val demRaster = "dem_elevation_matrix_chunk_kopeng_850m".toByteArray(Charsets.UTF_8)
        val calibProfile = "{\"whiteRefR\":1.02,\"whiteRefG\":1.00,\"whiteRefB\":1.03}".toByteArray(Charsets.UTF_8)

        // 1. Store in each compartment
        val meta1 = storage.storeFile(
            EncryptedFileStorage.Compartment.DISEASE_REFERENCE,
            "colletotrichum_ref_01",
            diseasePhoto,
            "{\"disease\":\"ANTHRACNOSE\",\"severity\":25}"
        )
        val meta2 = storage.storeFile(
            EncryptedFileStorage.Compartment.TOPOGRAPHY_DEM,
            "tile_kopeng_grid_01",
            demRaster,
            "{\"bounds\":[-7.12,110.45]}"
        )
        val meta3 = storage.storeFile(
            EncryptedFileStorage.Compartment.SENSOR_CALIBRATION,
            "profile_sensor_default",
            calibProfile,
            "{\"model\":\"Infinix_X6885\"}"
        )

        assertEquals("colletotrichum_ref_01", meta1.key)
        assertTrue(meta1.encryptedSize > 0)
        assertTrue(meta1.sha256Checksum.isNotEmpty())

        // 2. Read and verify decryption
        val res1 = storage.readFile(EncryptedFileStorage.Compartment.DISEASE_REFERENCE, "colletotrichum_ref_01")
        assertNotNull(res1)
        assertArrayEquals(diseasePhoto, res1!!.first)
        assertEquals(meta1.sha256Checksum, res1.second.sha256Checksum)

        val res2 = storage.readFile(EncryptedFileStorage.Compartment.TOPOGRAPHY_DEM, "tile_kopeng_grid_01")
        assertNotNull(res2)
        assertArrayEquals(demRaster, res2!!.first)

        val res3 = storage.readFile(EncryptedFileStorage.Compartment.SENSOR_CALIBRATION, "profile_sensor_default")
        assertNotNull(res3)
        assertArrayEquals(calibProfile, res3!!.first)

        // 3. Verify integrity checker
        assertTrue(storage.verifyFileIntegrity(EncryptedFileStorage.Compartment.DISEASE_REFERENCE, "colletotrichum_ref_01"))
        assertFalse(storage.verifyFileIntegrity(EncryptedFileStorage.Compartment.DISEASE_REFERENCE, "non_existent_key"))
    }

    @Test
    fun testTamperDetectionFailsVerification() {
        val payload = "classified_farm_quality_dataset".toByteArray(Charsets.UTF_8)
        storage.storeFile(EncryptedFileStorage.Compartment.DISEASE_REFERENCE, "tamper_test", payload)

        val encFile = File(tempFolder.root, "disease_refs/tamper_test.enc")
        assertTrue(encFile.exists())

        // Deliberately tamper with ciphertext by modifying a byte near the end
        RandomAccessFile(encFile, "rw").use { raf ->
            raf.seek(encFile.length() - 5)
            val b = raf.readByte()
            raf.seek(encFile.length() - 5)
            raf.writeByte(b.toInt() xor 0xFF)
        }

        // Reading tampered file must fail (AES-GCM authentication tag mismatch)
        val result = storage.readFile(EncryptedFileStorage.Compartment.DISEASE_REFERENCE, "tamper_test")
        assertNull("File yang dimanipulasi harus ditolak oleh AES-GCM authentication", result)
    }

    @Test
    fun testStorageQuotaEnforcement() {
        // Store 3 items
        storage.storeFile(EncryptedFileStorage.Compartment.DISEASE_REFERENCE, "file_1", "AAA".toByteArray())
        Thread.sleep(10)
        storage.storeFile(EncryptedFileStorage.Compartment.DISEASE_REFERENCE, "file_2", "BBB".toByteArray())
        Thread.sleep(10)
        storage.storeFile(EncryptedFileStorage.Compartment.DISEASE_REFERENCE, "file_3", "CCC".toByteArray())

        val usage = storage.getStorageUsageBytes()
        assertTrue(usage > 0)

        // Set maxBytes smaller than current usage -> evicts oldest file (file_1)
        val evicted = storage.enforceQuota(usage - 10)
        assertTrue(evicted >= 1)

        // file_1 should be evicted, file_3 should remain
        assertNull(storage.readFile(EncryptedFileStorage.Compartment.DISEASE_REFERENCE, "file_1"))
        assertNotNull(storage.readFile(EncryptedFileStorage.Compartment.DISEASE_REFERENCE, "file_3"))
    }

    @Test
    fun testClearCompartmentIsolation() {
        storage.storeFile(EncryptedFileStorage.Compartment.DISEASE_REFERENCE, "p1", "photo".toByteArray())
        storage.storeFile(EncryptedFileStorage.Compartment.SENSOR_CALIBRATION, "s1", "calib".toByteArray())

        storage.clearCompartment(EncryptedFileStorage.Compartment.DISEASE_REFERENCE)

        assertNull(storage.readFile(EncryptedFileStorage.Compartment.DISEASE_REFERENCE, "p1"))
        assertNotNull(storage.readFile(EncryptedFileStorage.Compartment.SENSOR_CALIBRATION, "s1"))
    }
}
