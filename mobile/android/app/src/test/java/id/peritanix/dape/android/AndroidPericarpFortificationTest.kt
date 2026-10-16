package id.peritanix.dape.android

import id.peritanix.dape.android.core.physics.PericarpFortificationManager
import org.junit.Assert.*
import org.junit.Test

class AndroidPericarpFortificationTest {

    @Test
    fun testFortificationLockedDuringAnthesisFloweringStage() {
        // Vegetative stage (HST 20, GDD 200) -> Not locked
        val vegStatus = PericarpFortificationManager.evaluateLockStatus("VEGETATIVE", 200.0, 20)
        assertFalse("Fase vegetatif awal tidak boleh terkunci", vegStatus.isLocked)

        // Flowering transition (HST 45, GDD 480) -> Locked
        val flowStatus = PericarpFortificationManager.evaluateLockStatus("FLOWERING", 480.0, 45)
        assertTrue("Fase pembungaan harus berstatus FORTIFICATION_LOCKED", flowStatus.isLocked)
        assertEquals("FORTIFICATION_LOCKED", flowStatus.phaseName)
        assertTrue(flowStatus.advisory.contains("Kalsium Nitrat + Boron + Asam Silikat"))
    }

    @Test
    fun testHighNitrogenFertilizerBlockedDuringFortificationLock() {
        // Farmer attempts to apply Urea (46% N) during flowering
        val ureaValidation = PericarpFortificationManager.validateFertilizerProposal(
            fertilizerName = "Urea Kujang 46%",
            nitrogenPct = 46.0,
            isLocked = true
        )

        assertFalse("Urea harus diblokir pada fase pembungaan", ureaValidation.isAllowed)
        assertTrue("Harus memicu critical block flag", ureaValidation.isCriticalBlock)
        assertTrue("Pesan peringatan harus memuat kata BLOKIR", ureaValidation.warningMessage.contains("BLOKIR"))
        assertTrue("Tindakan korektif harus menyarankan Kalsium Nitrat", ureaValidation.correctiveAction.contains("Kalsium Nitrat"))
    }

    @Test
    fun testCalciumNitrateAndBoronAllowedDuringFortificationLock() {
        // Farmer applies Calcium Nitrate (15% N, 26% CaO) during flowering
        val caValidation = PericarpFortificationManager.validateFertilizerProposal(
            fertilizerName = "Kalsium Nitrat YaraLiva",
            nitrogenPct = 15.0,
            isLocked = true
        )

        assertTrue("Kalsium Nitrat harus diizinkan selama penguncian fortifikasi", caValidation.isAllowed)
        assertFalse("Tidak boleh ada critical block", caValidation.isCriticalBlock)
    }

    @Test
    fun testStoichiometryPrescriptionValues() {
        val rx = PericarpFortificationManager.StoichiometryPrescription()
        assertEquals(2.5, rx.calciumNitrateGPerL, 0.01)
        assertEquals(0.5, rx.boronGPerL, 0.01)
        assertEquals(1.0, rx.silicicAcidGPerL, 0.01)
        assertEquals(5, rx.frequencyDays)
    }
}
