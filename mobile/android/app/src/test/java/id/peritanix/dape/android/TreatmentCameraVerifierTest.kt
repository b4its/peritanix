package id.peritanix.dape.android

import id.peritanix.dape.android.core.guide.ChiliGrowingGuide
import id.peritanix.dape.android.core.guide.TreatmentCameraVerifier
import id.peritanix.dape.android.core.hardware.IndonesianTtsVoiceAdvisor
import org.junit.Assert.*
import org.junit.Test

class TreatmentCameraVerifierTest {

    @Test
    fun testGrowingGuideStagesCompleteness() {
        assertEquals(5, ChiliGrowingGuide.stages.size)

        for (stage in ChiliGrowingGuide.stages) {
            assertTrue(stage.index in 1..5)
            assertTrue(stage.title.isNotBlank())
            assertTrue(stage.phaseName.isNotBlank())
            assertTrue(stage.hstRange.isNotBlank())
            assertTrue(stage.bestYieldGoal.isNotBlank())
            assertTrue("Planting guide must not be empty", stage.plantingGuide.isNotEmpty())
            assertTrue("Maintenance guide must not be empty", stage.maintenanceGuide.isNotEmpty())
            assertTrue("Harvest guide must not be empty", stage.harvestGuide.isNotEmpty())
            assertTrue("Checklist must not be empty", stage.checklist.isNotEmpty())
            assertTrue("Risk must not be blank", stage.risk.isNotBlank())

            // Camera Protocol Verification
            val protocol = stage.cameraProtocol
            assertTrue(protocol.targetSubject.isNotBlank())
            assertTrue(protocol.photoInstructions.isNotBlank())
            assertTrue(protocol.successCriteria.isNotBlank())
            assertTrue(protocol.inspectionKeyPoints.isNotEmpty())
            assertTrue(protocol.failAction.isNotBlank())

            // Structured Treatments Verification
            assertTrue("Stage treatments must not be empty", stage.treatments.isNotEmpty())
            for (treatment in stage.treatments) {
                assertTrue(treatment.id.isNotBlank())
                assertTrue(treatment.name.isNotBlank())
                assertTrue(treatment.category.isNotBlank())
                assertTrue(treatment.description.isNotBlank())
                assertTrue(treatment.targetCameraSubject.isNotBlank())
                assertTrue(treatment.visualSuccessCriteria.isNotBlank())
            }
        }
    }

    @Test
    fun testRealtimeAdvisoryCalculations() {
        // -10 HST (Persemaian)
        val advSemai = ChiliGrowingGuide.generateRealtimeAdvisory(-10, isRaining = false, tempC = 27.0, rhPct = 70.0)
        assertEquals(1, advSemai.currentStage.index)
        assertTrue(advSemai.daysToHarvest > 90)
        assertTrue(advSemai.progressPct in 0.0f..0.2f)
        assertFalse(advSemai.weatherAlert.contains("Hujan"))

        // 20 HST (Vegetatif)
        val advVeg = ChiliGrowingGuide.generateRealtimeAdvisory(20, isRaining = true, tempC = 24.0, rhPct = 90.0)
        assertEquals(2, advVeg.currentStage.index)
        assertEquals(70, advVeg.daysToHarvest)
        assertTrue(advVeg.weatherAlert.contains("Hujan"))

        // 45 HST (Pembungaan)
        val advBunga = ChiliGrowingGuide.generateRealtimeAdvisory(45, isRaining = false, tempC = 33.5, rhPct = 60.0)
        assertEquals(3, advBunga.currentStage.index)
        assertEquals(45, advBunga.daysToHarvest)
        assertTrue(advBunga.weatherAlert.contains("Terik"))

        // 75 HST (Pembuahan)
        val advBuah = ChiliGrowingGuide.generateRealtimeAdvisory(75, isRaining = false, tempC = 26.0, rhPct = 88.0)
        assertEquals(4, advBuah.currentStage.index)
        assertEquals(15, advBuah.daysToHarvest)
        assertTrue(advBuah.weatherAlert.contains("Kelembaban Tinggi"))

        // 100 HST (Panen)
        val advPanen = ChiliGrowingGuide.generateRealtimeAdvisory(100, isRaining = false, tempC = 25.0, rhPct = 65.0)
        assertEquals(5, advPanen.currentStage.index)
        assertEquals(0, advPanen.daysToHarvest)
    }

    @Test
    fun testCameraInspectionOptimalCondition() {
        val stageVeg = ChiliGrowingGuide.stageForHst(25)
        val optimalSample = TreatmentCameraVerifier.createSimulatedSample(
            stage = stageVeg,
            hst = 25,
            condition = TreatmentCameraVerifier.SampleCondition.OPTIMAL
        )

        assertEquals(stageVeg.index, optimalSample.stageIndex)
        assertEquals(25, optimalSample.hst)
        assertTrue("Health score should be high for optimal sample", optimalSample.healthScore >= 80.0)
        assertTrue("Green percentage should be dominant", optimalSample.greenPct >= 60.0)
        assertTrue("Defect percentage should be low", optimalSample.defectPct <= 4.0)
        assertEquals(TreatmentCameraVerifier.VerificationStatus.VERIFIED_OPTIMAL, optimalSample.status)
        assertTrue(optimalSample.diagnosisText.isNotBlank())
        assertTrue(optimalSample.prescriptiveAction.isNotBlank())
    }

    @Test
    fun testSpecificTreatmentInspectionWiwil() {
        val stageVeg = ChiliGrowingGuide.stageForHst(20)
        val sample = TreatmentCameraVerifier.createSimulatedSample(
            stage = stageVeg,
            hst = 20,
            condition = TreatmentCameraVerifier.SampleCondition.OPTIMAL,
            treatmentName = "Pewiwilan Tunas Air (Bawah Cabang Y)"
        )

        assertEquals("Pewiwilan Tunas Air (Bawah Cabang Y)", sample.treatmentName)
        assertTrue("Diagnosis should mention wiwilan or tunas", sample.diagnosisText.contains("wiwilan") || sample.diagnosisText.contains("tunas"))
        assertTrue("Prescriptive action should advise maintaining clean axils", sample.prescriptiveAction.contains("LAI") || sample.prescriptiveAction.contains("cabang Y"))
    }

    @Test
    fun testSpecificTreatmentInspectionFertigation() {
        val stageBunga = ChiliGrowingGuide.stageForHst(45)
        val sample = TreatmentCameraVerifier.createSimulatedSample(
            stage = stageBunga,
            hst = 45,
            condition = TreatmentCameraVerifier.SampleCondition.OPTIMAL,
            treatmentName = "Fortifikasi Kalsium Nitrat & Boron"
        )

        assertEquals("Fortifikasi Kalsium Nitrat & Boron", sample.treatmentName)
        assertTrue(sample.diagnosisText.contains("pemupukan") || sample.diagnosisText.contains("nutrisi") || sample.diagnosisText.contains("klorofil"))
        assertTrue(sample.prescriptiveAction.contains("kocor") || sample.prescriptiveAction.contains("fertigasi"))
    }

    @Test
    fun testCameraInspectionHarvestRipeness() {
        val stagePanen = ChiliGrowingGuide.stageForHst(105)
        val harvestSample = TreatmentCameraVerifier.createSimulatedSample(
            stage = stagePanen,
            hst = 105,
            condition = TreatmentCameraVerifier.SampleCondition.OPTIMAL,
            treatmentName = "Sortasi Meja Grading Mutu DAPE"
        )

        assertEquals(5, harvestSample.stageIndex)
        assertEquals("Sortasi Meja Grading Mutu DAPE", harvestSample.treatmentName)
        assertTrue("Red coverage should be high at harvest", harvestSample.redPct >= 75.0)
        assertTrue("Health score should be high", harvestSample.healthScore >= 80.0)
        assertTrue(harvestSample.diagnosisText.contains("Grade A+") || harvestSample.diagnosisText.contains("merah"))
    }

    @Test
    fun testCameraInspectionDefectDetection() {
        val stageBuah = ChiliGrowingGuide.stageForHst(70)
        val defectSample = TreatmentCameraVerifier.createSimulatedSample(
            stage = stageBuah,
            hst = 70,
            condition = TreatmentCameraVerifier.SampleCondition.SEVERE_DISEASE,
            treatmentName = "Sanitasi Petik Buah Gejala Patek (Antraknosa)"
        )

        assertTrue("Defect percentage should be elevated", defectSample.defectPct >= 10.0)
        assertTrue("Health score should be penalized", defectSample.healthScore < 60.0)
        assertEquals(TreatmentCameraVerifier.VerificationStatus.CRITICAL_RISK, defectSample.status)
        assertTrue(defectSample.prescriptiveAction.contains("fungisida") || defectSample.prescriptiveAction.contains("Antraknosa") || defectSample.prescriptiveAction.contains("musnahkan") || defectSample.prescriptiveAction.contains("BS"))
    }

    @Test
    fun testCameraInspectionMildDefectCondition() {
        val stageVeg = ChiliGrowingGuide.stageForHst(20)
        val mildSample = TreatmentCameraVerifier.createSimulatedSample(
            stage = stageVeg,
            hst = 20,
            condition = TreatmentCameraVerifier.SampleCondition.MILD_DEFECT
        )

        assertTrue(mildSample.yellowPct >= 15.0 || mildSample.defectPct >= 6.0)
        assertTrue(
            mildSample.status == TreatmentCameraVerifier.VerificationStatus.ATTENTION_NEEDED ||
            mildSample.status == TreatmentCameraVerifier.VerificationStatus.CRITICAL_RISK
        )
    }

    @Test
    fun testTtsNarrationScriptComposition() {
        val script = IndonesianTtsVoiceAdvisor.composeTreatmentInspectionScript(
            stageTitle = "Pembungaan & Transisi Generatif",
            score = 92.5,
            statusLabel = "SOP Optimal & Sangat Sehat",
            actionAdvice = "Semprot Kalsium Nitrat + Boron dan hentikan Urea.",
            treatmentName = "Fortifikasi Kalsium Nitrat & Boron"
        )

        assertTrue(script.contains("Pembungaan"))
        assertTrue(script.contains("Fortifikasi Kalsium Nitrat"))
        assertTrue(script.contains("92 persen"))
        assertTrue(script.contains("SOP Optimal"))
        assertTrue(script.contains("Kalsium Nitrat"))
    }

    @Test
    fun testTreatmentIdPreservation() {
        val stageVeg = ChiliGrowingGuide.stageForHst(20)
        val trt = stageVeg.treatments.first()
        val sample = TreatmentCameraVerifier.createSimulatedSample(
            stage = stageVeg,
            hst = 20,
            condition = TreatmentCameraVerifier.SampleCondition.OPTIMAL,
            treatmentName = trt.name,
            treatmentId = trt.id
        )

        assertEquals(trt.id, sample.treatmentId)
        assertEquals(trt.name, sample.treatmentName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAnalyzePixelsZeroDimensions() {
        val stage = ChiliGrowingGuide.stages[0]
        TreatmentCameraVerifier.analyzePixels(stage, IntArray(10), 0, 10, 0)
    }

    @Test
    fun testPillarTypeCategorizationAndComplianceScores() {
        val planting = ChiliGrowingGuide.getTreatmentsForPillar(ChiliGrowingGuide.PillarType.PLANTING)
        val maintenance = ChiliGrowingGuide.getTreatmentsForPillar(ChiliGrowingGuide.PillarType.MAINTENANCE)
        val harvest = ChiliGrowingGuide.getTreatmentsForPillar(ChiliGrowingGuide.PillarType.HARVEST)

        assertEquals(7, planting.size)
        assertEquals(14, maintenance.size)
        assertEquals(5, harvest.size)
        assertEquals(26, planting.size + maintenance.size + harvest.size)

        for (stage in ChiliGrowingGuide.stages) {
            for (trt in stage.treatments) {
                assertTrue("Shooting angle must not be blank for ${trt.id}", trt.shootingAngle.isNotBlank())
                assertTrue("Recommended lighting must not be blank for ${trt.id}", trt.recommendedLighting.isNotBlank())
                assertNotNull("Pillar must be defined for ${trt.id}", trt.pillar)
            }
        }

        // Test stage compliance scoring
        val stage1 = ChiliGrowingGuide.stages[0]
        assertEquals(0.0, ChiliGrowingGuide.calculateComplianceScore(stage1, emptySet()), 0.001)
        val stage1Ids = stage1.treatments.map { it.id }.toSet()
        assertEquals(100.0, ChiliGrowingGuide.calculateComplianceScore(stage1, stage1Ids), 0.001)

        // Test total compliance scoring
        assertEquals(0.0, ChiliGrowingGuide.calculateTotalComplianceScore(emptySet()), 0.001)
        val partialIds = setOf("T1_1", "T1_2", "T2_1")
        val expectedScore = (3.0 / 26.0) * 100.0
        assertEquals(expectedScore, ChiliGrowingGuide.calculateTotalComplianceScore(partialIds), 0.001)

        val allIds = ChiliGrowingGuide.stages.flatMap { it.treatments }.map { it.id }.toSet()
        assertEquals(100.0, ChiliGrowingGuide.calculateTotalComplianceScore(allIds), 0.001)
    }
}
