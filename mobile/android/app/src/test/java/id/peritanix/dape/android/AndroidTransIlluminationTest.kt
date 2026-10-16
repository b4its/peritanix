package id.peritanix.dape.android

import id.peritanix.dape.android.core.ai_edge.EdgeInferenceRunner
import id.peritanix.dape.android.core.hardware.DarkChamberFlashManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class AndroidTransIlluminationTest {

    private lateinit var runner: EdgeInferenceRunner

    @Before
    fun setUp() {
        runner = EdgeInferenceRunner(File("/tmp/models"))
    }

    @Test
    fun testHealthyLeafTransIlluminationShowsHighGreenRatio() {
        // Healthy leaf with high green transmittance: R=80, G=180, B=60 -> G / (R + B) = 180 / 140 = 1.28
        val result = runner.evaluateTransIllumination(rawR = 80, rawG = 180, rawB = 60)
        assertTrue("Rasio transmisi hijau harus > 0.90", result.greenAttenuationRatio > 0.90)
        assertFalse("Tidak boleh terdeteksi infeksi laten pada daun sehat", result.isLatentFungalDetected)
        assertTrue(result.latencyAdvisory.contains("Mesofil Normal"))
    }

    @Test
    fun testLatentFungalInfectionTriggersTransmittanceWarning() {
        // Attenuated leaf with latent dark mycelium: R=130, G=150, B=100 -> G / (R + B) = 150 / 230 = 0.65
        val result = runner.evaluateTransIllumination(rawR = 130, rawG = 150, rawB = 100)
        assertTrue("Rasio transmisi hijau harus <= 0.85", result.greenAttenuationRatio <= 0.85)
        assertTrue("Harus terdeteksi infeksi laten hifa jamur", result.isLatentFungalDetected)
        assertTrue(result.latencyAdvisory.contains("PERINGATAN DINI"))
    }

    @Test
    fun testYieldEstimationAndFruitCountProjection() {
        // 30 green, 10 turning, 10 ripe -> total 50 fruits per plant
        val yield = runner.estimateYieldAndFruitCount(
            greenCount = 30,
            turningCount = 10,
            ripeCount = 10,
            plantDensityPerHa = 18000
        )

        assertEquals(50, yield.totalFruitCount)
        // 50 * 0.012 kg = 0.60 kg per plant
        assertEquals(0.60, yield.projectedYieldKgPerPlant, 0.05)
        // 0.60 kg * 18000 / 1000 = 10.8 Ton/Ha
        assertEquals(10.8, yield.projectedYieldTonPerHa, 0.5)
        assertEquals(20.0, yield.harvestReadinessPct, 1.0)
    }

    @Test
    fun testSeveritySegmentationPrescriptionTiers() {
        val mild = runner.evaluateSeveritySegmentation(3.5f)
        assertEquals("MILD", mild.severityTier)
        assertEquals(7, mild.sprayIntervalDays)

        val mod = runner.evaluateSeveritySegmentation(12.0f)
        assertEquals("MODERATE", mod.severityTier)
        assertEquals(5, mod.sprayIntervalDays)

        val severe = runner.evaluateSeveritySegmentation(25.0f)
        assertEquals("SEVERE", severe.severityTier)
        assertEquals(3, severe.sprayIntervalDays)
    }

    @Test
    fun testSpectralWhiteReferenceCalibrationUnderDarkChamber() {
        // In sealed chamber (0.0 Lux), white paper reads (240, 245, 238)
        val calib = DarkChamberFlashManager.calibrateSpectralWhiteReference(
            rawR = 240,
            rawG = 245,
            rawB = 238,
            ambientLux = 0.0f
        )
        assertTrue("Kalibrasi harus berhasil saat 0 Lux", calib.isCalibrated)
        assertTrue("Lux harus terverifikasi", calib.luxVerified)
        assertTrue("Faktor pengali merah harus > 1.0", calib.rScale > 1.0)
        assertTrue("Faktor pengali hijau harus > 1.0", calib.gScale > 1.0)

        // If chamber is unsealed (e.g. 5.0 Lux)
        val failedCalib = DarkChamberFlashManager.calibrateSpectralWhiteReference(
            rawR = 240,
            rawG = 245,
            rawB = 238,
            ambientLux = 5.0f
        )
        assertFalse("Kalibrasi harus ditolak jika ada kebocoran cahaya", failedCalib.isCalibrated)
    }
}
