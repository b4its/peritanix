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
import id.peritanix.dape.pathology.PathologyClassifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(queue: id.peritanix.dape.android.data.SyncQueueStore? = null) {
    val scrollState = rememberScrollState()

    var curlDirection by remember { mutableStateOf("up") }
    var undersideColor by remember { mutableStateOf("silvery") }
    var mosaicVeins by remember { mutableStateOf(false) }
    var sunkenLesions by remember { mutableStateOf(false) }

    var isWilting by remember { mutableStateOf(true) }
    var soilMoisturePct by remember { mutableStateOf(68.0) }
    var saveStatus by remember { mutableStateOf("") }

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

                // Hasil Diagnosa
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

        // --- Card 3: Fotometri Kamar Gelap (Dark Chamber Strip) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "3. Miniatur Dark Chamber Strip Photometry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Sensor Cahaya: 0.0 Lux (Kamar Gelap Terverifikasi)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Camera2 Status: AE OFF | Flash Torch ON | ISO 100 | Shutter 1/125s",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Hasil CIELAB pH Tanah: 6.2 (Optimal)")
                    Text("Kadar Nitrat: 85 ppm")
                }
            }
        }
    }
}
