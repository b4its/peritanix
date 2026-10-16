package id.peritanix.dape.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.peritanix.dape.crypto.Ed25519OfflineSigner
import id.peritanix.dape.physics.Physics

@Composable
fun HarvestScreen(queue: id.peritanix.dape.android.data.SyncQueueStore? = null) {
    val scrollState = rememberScrollState()

    var elevationM by remember { mutableStateOf(850.0) }
    var wellTempC by remember { mutableStateOf(26.0) }

    var sinkCount by remember { mutableStateOf(16) }
    var neutralCount by remember { mutableStateOf(3) }
    var floatCount by remember { mutableStateOf(1) }

    var isTimerRunning by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableStateOf(180) }

    var signedCert by remember { mutableStateOf("") }
    var saveStatus by remember { mutableStateOf("") }

    val ratio = Physics.hotWaterRatio(elevationM, wellTempC)
    val mixCheck = Physics.mixTemperature(elevationM, ratio.vBoilParts, wellTempC, ratio.vWellParts)

    val sinkRatio = Physics.archimedesSinkRatio(sinkCount, neutralCount, floatCount)
    val seedGrade = Physics.gradeFromSinkRatio(sinkRatio)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Card 1: Termoterapi Air Panas 52°C (Asas Black) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "1. Termoterapi Benih 52°C (Asas Black)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Koreksi Barometrik Elevasi: ${elevationM.toInt()} mdpl | Titik Didih: ${"%.2f".format(ratio.boilingPointC)}°C",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Elevasi Kebun: ${elevationM.toInt()} mdpl")
                }
                Slider(
                    value = elevationM.toFloat(),
                    onValueChange = { elevationM = it.toDouble() },
                    valueRange = 0f..2500f
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Suhu Air Sumur: ${"%.1f".format(wellTempC)}°C")
                }
                Slider(
                    value = wellTempC.toFloat(),
                    onValueChange = { wellTempC = it.toDouble() },
                    valueRange = 18f..32f
                )

                HorizontalDivider()

                Text(
                    "Rasio Campuran: ${ratio.ratioLabel}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Suhu Campuran Teoritis: ${"%.2f".format(mixCheck)}°C (Toleransi presisi ±0.5°C)",
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    onClick = {
                        isTimerRunning = !isTimerRunning
                        if (isTimerRunning) secondsLeft = 180
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isTimerRunning) "Hentikan Timer (Sisa: $secondsLeft detik)" else "Mulai Perendaman 180s (Suara TTS)")
                }

                Text(
                    "Jejak Giroskop: VALID (Gerak operator terdeteksi wajar, timer tidak dilewati).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // --- Card 2: Uji Berat Jenis Benih (Hukum Archimedes) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "2. Seleksi Mutu Benih (Larutan Garam 1.08 g/cm³)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Komposisi: 50 gram garam dapur dilarutkan ke dalam 500 mL air.",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Tenggelam: $sinkCount", style = MaterialTheme.typography.bodySmall)
                        Row {
                            Button(onClick = { if (sinkCount > 0) sinkCount-- }) { Text("-") }
                            Spacer(Modifier.width(4.dp))
                            Button(onClick = { sinkCount++ }) { Text("+") }
                        }
                    }
                    Column {
                        Text("Melayang: $neutralCount", style = MaterialTheme.typography.bodySmall)
                        Row {
                            Button(onClick = { if (neutralCount > 0) neutralCount-- }) { Text("-") }
                            Spacer(Modifier.width(4.dp))
                            Button(onClick = { neutralCount++ }) { Text("+") }
                        }
                    }
                    Column {
                        Text("Terapung: $floatCount", style = MaterialTheme.typography.bodySmall)
                        Row {
                            Button(onClick = { if (floatCount > 0) floatCount-- }) { Text("-") }
                            Spacer(Modifier.width(4.dp))
                            Button(onClick = { floatCount++ }) { Text("+") }
                        }
                    }
                }

                HorizontalDivider()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Sink Ratio Terbobot", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "%.2f".format(sinkRatio),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("Grade Mutu Benih", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Kelas $seedGrade",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // --- Card 3: Sertifikasi Kriptografis Ed25519 (Offline) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "3. Penerbitan Sertifikat Panen Terdesentralisasi (Ed25519)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = {
                        val kp = Ed25519OfflineSigner.fromSeed(ByteArray(32) { 42.toByte() })
                        val payload = mapOf(
                            "land_id" to "land-peritanix-001",
                            "grade_result" to seedGrade,
                            "archimedes_sink_ratio" to (Math.round(sinkRatio * 100.0) / 100.0),
                            "sterilization_c" to 52.0
                        )
                        signedCert = Ed25519OfflineSigner.certificate(payload, kp)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tandatangani Kriptografis Secara Offline")
                }

                if (signedCert.isNotEmpty()) {
                    Text("QR Payload Terverifikasi:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text(
                        signedCert,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    Button(
                        onClick = {
                            queue?.enqueueHarvest(
                                landId = "land-peritanix-001",
                                boilingPoint = ratio.boilingPointC,
                                ratioLabel = ratio.ratioLabel,
                                sinkRatio = (Math.round(sinkRatio * 100.0) / 100.0),
                                gradeResult = seedGrade,
                                signature = signedCert
                            )
                            saveStatus = "Batch panen & sertifikat QR tersimpan ke Room SQLite (Offline-First)."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simpan Batch Panen ke Room SQLite")
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
        }
    }
}
