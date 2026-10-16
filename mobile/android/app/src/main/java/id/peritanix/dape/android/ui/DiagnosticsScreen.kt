package id.peritanix.dape.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.peritanix.dape.android.core.ai_edge.EdgeInferenceRunner
import id.peritanix.dape.android.core.physics.PericarpFortificationManager
import id.peritanix.dape.pathology.PathologyClassifier
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(queue: id.peritanix.dape.android.data.SyncQueueStore? = null) {
    val scrollState = rememberScrollState()

    // --- State: Morfologi Keriting & Lesi ---
    var curlDirection by remember { mutableStateOf("up") }
    var undersideColor by remember { mutableStateOf("silvery") }
    var mosaicVeins by remember { mutableStateOf(false) }
    var sunkenLesions by remember { mutableStateOf(false) }

    var isWilting by remember { mutableStateOf(true) }
    var soilMoisturePct by remember { mutableStateOf(68.0) }
    var saveStatus by remember { mutableStateOf("") }

    // --- State: Active Leaf Trans-Illumination (FR-2.2) ---
    val edgeRunner = remember { EdgeInferenceRunner(File("/tmp/models")) }
    var transR by remember { mutableStateOf(110) }
    var transG by remember { mutableStateOf(160) }
    var transB by remember { mutableStateOf(90) }
    val transResult = remember(transR, transG, transB) {
        edgeRunner.evaluateTransIllumination(transR, transG, transB)
    }

    // --- State: Yield Estimation Fruit Counting ---
    var greenFruits by remember { mutableStateOf(35) }
    var turningFruits by remember { mutableStateOf(12) }
    var ripeFruits by remember { mutableStateOf(8) }
    val yieldResult = remember(greenFruits, turningFruits, ripeFruits) {
        edgeRunner.estimateYieldAndFruitCount(greenFruits, turningFruits, ripeFruits)
    }

    // --- State: Pericarp Fortification Lock & Nitrogen Restriction (FR-3.2) ---
    var currentPhase by remember { mutableStateOf("FLOWERING") }
    var gddAccumulated by remember { mutableStateOf(480.0) }
    var plantHst by remember { mutableStateOf(45) }
    var proposedFertilizer by remember { mutableStateOf("Urea 46%") }
    var proposedNitrogenPct by remember { mutableStateOf(46.0) }

    val fortStatus = remember(currentPhase, gddAccumulated, plantHst) {
        PericarpFortificationManager.evaluateLockStatus(currentPhase, gddAccumulated, plantHst)
    }
    val fertValidation = remember(proposedFertilizer, proposedNitrogenPct, fortStatus.isLocked) {
        PericarpFortificationManager.validateFertilizerProposal(
            fertilizerName = proposedFertilizer,
            nitrogenPct = proposedNitrogenPct,
            isLocked = fortStatus.isLocked
        )
    }

    val diag = PathologyClassifier.differentiateLeafCurl(
        curlDirection = curlDirection,
        undersideColor = undersideColor,
        mosaicVeins = mosaicVeins,
        sunkenLesions = sunkenLesions
    )

    val oozeResult = PathologyClassifier.evaluateDestructiveOozeTrigger(
        isWilting = isWilting,
        soilMoisturePct = soilMoisturePct
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Card 1: Diferensiasi Morfologi Keriting Daun On-Device ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "1. Diferensiasi Keriting Daun & Lesi On-Device",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text("Arah Lengkungan Daun:", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("up" to "Ke Atas (Perahu)", "down" to "Ke Bawah (Sendok)", "none" to "Datar").forEach { (valKey, label) ->
                        FilterChip(
                            selected = curlDirection == valKey,
                            onClick = { curlDirection = valKey },
                            label = { Text(label) }
                        )
                    }
                }

                Text("Warna Permukaan Bawah Daun:", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("silvery" to "Keperakan (Thrips)", "bronze" to "Tembaga (Tungau)", "normal" to "Normal").forEach { (valKey, label) ->
                        FilterChip(
                            selected = undersideColor == valKey,
                            onClick = { undersideColor = valKey },
                            label = { Text(label) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = mosaicVeins,
                        onCheckedChange = { mosaicVeins = it }
                    )
                    Text("Pola Mosaik Kuning / Tulang Daun Tebal (Virus Gemini)", style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = sunkenLesions,
                        onCheckedChange = { sunkenLesions = it }
                    )
                    Text("Bercak Nekrotik Cekung Melingkar / Spora Gelap (Antraknosa)", style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider()

                Text(
                    diag.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    diag.description,
                    style = MaterialTheme.typography.bodySmall
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Bahan Aktif Kimia: ${diag.activeIngredient}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text("Dosis: ${diag.dosagePerLiter}", style = MaterialTheme.typography.bodySmall)
                        Text("Panduan Semprot: ${diag.sprayingInstructions}", style = MaterialTheme.typography.bodySmall)
                        Text("Solusi PHT Hayati: ${diag.organicAlternative}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }

                HorizontalDivider()

                Button(
                    onClick = {
                        queue?.enqueueDiagnosis(
                            landId = "land-demo-001",
                            confirmedDisease = diag.condition,
                            severityPct = 15.0,
                            confidence = "HIGH",
                            protocolCode = diag.condition,
                            localTwi = 6.5
                        )
                        saveStatus = "Diagnosis & protokol tersimpan ke Room SQLite (Offline-First)."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Simpan Diagnosis & Resep ke Room SQLite")
                }

                if (saveStatus.isNotEmpty()) {
                    Text(
                        saveStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- Card 2: Pemicu Uji Ooze Destruktif (FR-2.3) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (oozeResult.isMandatory) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "2. Pemicu Uji Ooze Destruktif (Layu Bakteri vs Jamur)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isWilting,
                        onCheckedChange = { isWilting = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isWilting) "Gejala Layu Terdeteksi" else "Tanaman Tidak Layu")
                }

                Text("Kelembaban Tanah: ${soilMoisturePct.toInt()}%")
                Slider(
                    value = soilMoisturePct.toFloat(),
                    onValueChange = { soilMoisturePct = it.toDouble() },
                    valueRange = 10f..100f
                )

                if (oozeResult.isMandatory) {
                    Text(
                        "PERINGATAN: ${oozeResult.reason}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Prosedur Lapangan: Potong batang 2 cm di atas permukaan tanah, celupkan pangkal ke gelas berisi air bening. Jika keluar kabut/asap putih dalam 1-3 menit, positif Ralstonia solanacearum (cabut dan musnahkan segera).",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        oozeResult.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- Card 3: Active Leaf Trans-Illumination (FR-2.2) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (transResult.isLatentFungalDetected) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "3. Uji Trans-Iluminasi Daun Kontak 0 cm (FR-2.2)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Kamera kontak langsung ke daun dengan LED Flash menyala penuh untuk membaca transmisi optik mesofil dan hifa laten pra-gejala.",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Rasio Transmisi Hijau: ${transResult.greenAttenuationRatio}")
                    Text("Kerapatan Optik: ${transResult.mesophyllOpticalDensity}")
                }

                Text(
                    transResult.latencyAdvisory,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (transResult.isLatentFungalDetected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // --- Card 4: Pericarp Fortification Lock & Nitrogen Blocking (FR-3.2) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (fertValidation.isCriticalBlock) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "4. Penguncian Fortifikasi Dinding Sel (FR-3.2)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(if (fortStatus.isLocked) "LOCKED" else "NORMAL") }
                    )
                }

                Text(fortStatus.advisory, style = MaterialTheme.typography.bodySmall)

                HorizontalDivider()

                Text("Simulasi Input Pupuk Petani:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Urea 46%" to 46.0, "Kalsium Nitrat" to 15.0, "NPK 16-16-16" to 16.0).forEach { (name, nPct) ->
                        FilterChip(
                            selected = proposedFertilizer == name,
                            onClick = {
                                proposedFertilizer = name
                                proposedNitrogenPct = nPct
                            },
                            label = { Text(name) }
                        )
                    }
                }

                if (fertValidation.isCriticalBlock) {
                    Text(
                        fertValidation.warningMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Tindakan Korektif Wajib: ${fertValidation.correctiveAction}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else {
                    Text(
                        fertValidation.warningMessage,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Stoichiometry Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Protokol Stoikiometri Fortifikasi Perikarp:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text("• Kalsium Nitrat (Ca(NO3)2): ${fortStatus.prescription.calciumNitrateGPerL} g/L", style = MaterialTheme.typography.bodySmall)
                        Text("• Boron (B): ${fortStatus.prescription.boronGPerL} g/L", style = MaterialTheme.typography.bodySmall)
                        Text("• Asam Silikat: ${fortStatus.prescription.silicicAcidGPerL} g/L", style = MaterialTheme.typography.bodySmall)
                        Text("• Interval: Tiap ${fortStatus.prescription.frequencyDays} hari sekali kocor/semprot kabut.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // --- Card 5: Estimasi Populasi Buah & Yield On-Device (FR-2.1) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "5. Estimasi Populasi Buah & Hasil Panen (YOLO Yield)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Deteksi on-device buah hijau, semburat, dan merah matang untuk proyeksi tonase panen ke depan.",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Buah Hijau: $greenFruits")
                    Text("Semburat: $turningFruits")
                    Text("Merah Matang: $ripeFruits")
                }

                HorizontalDivider()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Total Buah/Pohon", style = MaterialTheme.typography.bodySmall)
                        Text("${yieldResult.totalFruitCount} buah", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Column {
                        Text("Estimasi per Pohon", style = MaterialTheme.typography.bodySmall)
                        Text("${yieldResult.projectedYieldKgPerPlant} kg", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Column {
                        Text("Proyeksi Tonase", style = MaterialTheme.typography.bodySmall)
                        Text("${yieldResult.projectedYieldTonPerHa} Ton/Ha", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        // --- Card 6: Early Warning System (EWS) Mikroklimat 72 Jam (FR-3.1) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            var ewsTemp by remember { mutableStateOf(28.0) }
            var ewsHumidity by remember { mutableStateOf(85.0) }
            val ewsRisk = remember(ewsTemp, ewsHumidity) {
                id.peritanix.dape.android.core.physics.MicroclimateEwsEvaluator.evaluate72hFungalRisk(
                    relativeHumidityPct = ewsHumidity,
                    temperatureC = ewsTemp
                )
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "6. EWS Mikroklimat 72 Jam (Spora Antraknosa)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(ewsRisk.riskLevel) }
                    )
                }

                Text("Kelembaban Relatif (RH): ${ewsHumidity.toInt()}%")
                Slider(
                    value = ewsHumidity.toFloat(),
                    onValueChange = { ewsHumidity = it.toDouble() },
                    valueRange = 40f..100f
                )

                Text("Suhu Rata-rata: ${"%.1f".format(ewsTemp)}°C")
                Slider(
                    value = ewsTemp.toFloat(),
                    onValueChange = { ewsTemp = it.toDouble() },
                    valueRange = 18f..38f
                )

                HorizontalDivider()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Skor Risiko Perkecambahan Spora:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${(ewsRisk.sporulationRiskScore * 100).toInt()}%",
                        fontWeight = FontWeight.Bold,
                        color = if (ewsRisk.riskLevel == "CRITICAL" || ewsRisk.riskLevel == "HIGH") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    ewsRisk.alertHeader,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (ewsRisk.riskLevel == "CRITICAL") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text(
                    ewsRisk.preventiveSprayAdvisory,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
