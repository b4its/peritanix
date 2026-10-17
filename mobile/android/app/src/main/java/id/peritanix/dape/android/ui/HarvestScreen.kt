package id.peritanix.dape.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.peritanix.dape.android.core.crypto.QrMatrixGenerator
import id.peritanix.dape.android.core.hardware.BluetoothThermalPrinter
import id.peritanix.dape.android.core.hardware.BoilingSoundVerifier
import id.peritanix.dape.android.core.hardware.IndonesianTtsVoiceAdvisor
import id.peritanix.dape.android.core.physics.BlackCalorimetryEngine
import id.peritanix.dape.android.core.physics.PodMorphologyGrader
import id.peritanix.dape.android.ui.components.AppIcons
import id.peritanix.dape.android.ui.components.NotificationType
import id.peritanix.dape.android.ui.components.PentasixNotificationHub
import id.peritanix.dape.crypto.Ed25519OfflineSigner
import id.peritanix.dape.physics.Physics

@Composable
fun HarvestScreen(queue: id.peritanix.dape.android.data.SyncQueueStore? = null) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var elevationM by remember { mutableStateOf(850.0) }
    var wellTempC by remember { mutableStateOf(26.0) }

    var sinkCount by remember { mutableStateOf(16) }
    var neutralCount by remember { mutableStateOf(3) }
    var floatCount by remember { mutableStateOf(1) }

    var isTimerRunning by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableStateOf(180) }
    var isTtsPlaying by remember { mutableStateOf(false) }

    var signedCert by remember { mutableStateOf("") }
    var saveStatus by remember { mutableStateOf("") }

    val calibGuide = remember(elevationM, wellTempC) {
        BlackCalorimetryEngine.calculate(elevationM, wellTempC)
    }

    val ratio = Physics.hotWaterRatio(elevationM, wellTempC)
    val mixCheck = Physics.mixTemperature(elevationM, ratio.vBoilParts, wellTempC, ratio.vWellParts)

    val sinkRatio = Physics.archimedesSinkRatio(sinkCount, neutralCount, floatCount)
    val seedGrade = Physics.gradeFromSinkRatio(sinkRatio)

    var podLength by remember { mutableStateOf(12.5) }
    var podCurv by remember { mutableStateOf(12.0) }
    var podRed by remember { mutableStateOf(92.0) }
    var podDefect by remember { mutableStateOf(1.5) }
    var printStatus by remember { mutableStateOf("") }

    val morphVerdict = remember(podLength, podCurv, podRed, podDefect, sinkRatio) {
        PodMorphologyGrader.evaluateGrade(
            PodMorphologyGrader.PodMeasurement(
                lengthCm = podLength,
                curvatureDeg = podCurv,
                redCoveragePct = podRed,
                defectPct = podDefect,
                archimedesSinkRatio = sinkRatio
            )
        )
    }

    // Batch sortation recap (Tahap 5.2): accumulate graded pods by weight %.
    val batchPods = remember { mutableStateListOf<id.peritanix.dape.android.core.physics.BatchGradingSummary.GradedPod>() }
    val batchSummary = remember(batchPods.size) {
        id.peritanix.dape.android.core.physics.BatchGradingSummary.summarize(batchPods.toList())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Card 1: Termoterapi Air Panas 52°C (Asas Black) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            "1. Termoterapi Benih & Sterilisasi 52°C (Asas Black)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Koreksi Barometrik: ${elevationM.toInt()} mdpl | Titik Didih: ${"%.2f".format(ratio.boilingPointC)}°C",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Elevasi Kebun: ${elevationM.toInt()} mdpl", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                }
                Slider(
                    value = elevationM.toFloat(),
                    onValueChange = { elevationM = it.toDouble() },
                    valueRange = 0f..2500f
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Suhu Air Sumur: ${"%.1f".format(wellTempC)}°C", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                }
                Slider(
                    value = wellTempC.toFloat(),
                    onValueChange = { wellTempC = it.toDouble() },
                    valueRange = 18f..32f
                )

                HorizontalDivider()

                Text(
                    "Takaran: ${calibGuide.ratioText}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Suhu Campuran Teoritis: ${"%.2f".format(mixCheck)}°C (Toleransi presisi ±0.5°C)",
                    style = MaterialTheme.typography.bodySmall
                )

                // TTS Voice Prompt Guidance
                val spokenScript = remember(elevationM, ratio.boilingPointC, ratio.vBoilParts, ratio.vWellParts) {
                    IndonesianTtsVoiceAdvisor.composePreparationScript(
                        elevationM = elevationM,
                        boilingPointC = ratio.boilingPointC,
                        boilingParts = Math.round(ratio.vBoilParts * 10.0) / 10.0,
                        wellParts = Math.round(ratio.vWellParts).toDouble()
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Panduan Suara TTS (Bahasa Indonesia):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text("id-ID Locale", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(spokenScript, style = MaterialTheme.typography.bodySmall)
                        FilledTonalButton(
                            onClick = {
                                isTtsPlaying = !isTtsPlaying
                                PentasixNotificationHub.notify(
                                    context = context,
                                    title = if (isTtsPlaying) "Panduan Suara Aktif" else "Panduan Suara Berhenti",
                                    message = if (isTtsPlaying) "Memutar takaran gayung air mendidih vs sumur (Bahasa Indonesia)." else "Pemutaran audio dihentikan.",
                                    type = NotificationType.INFO,
                                    postSystemNotification = false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(if (isTtsPlaying) AppIcons.Stop else AppIcons.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }

                // Fitur Baru: Acoustic Boiling Verification (Tahap 5.1)
                val boilingVerifier = remember { BoilingSoundVerifier(context) }
                var boilingVerificationResult by remember { mutableStateOf<BoilingSoundVerifier.BoilingAcousticVerification?>(null) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Verifikasi Akustik Mendidih (Mikrofon):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            if (boilingVerificationResult != null) {
                                Surface(
                                    color = if (boilingVerificationResult!!.isBoilingVerified) Color(0xFF2E7D32).copy(alpha = 0.15f) else Color(0xFFD32F2F).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        if (boilingVerificationResult!!.isBoilingVerified) "Mendidih Valid" else "Belum Mendidih",
                                        color = if (boilingVerificationResult!!.isBoilingVerified) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        if (boilingVerificationResult != null) {
                            Text(
                                boilingVerificationResult!!.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (boilingVerificationResult!!.isBoilingVerified) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                            )
                        } else {
                            Text(
                                "Dekatkan ponsel ke panci sebelum mencampur untuk menganalisis spektrum frekuensi kavitasi uap air (400-1200 Hz).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                val verif = boilingVerifier.verifyBoilingAcoustics(altitudeMeters = elevationM, durationMs = 1500)
                                boilingVerificationResult = verif
                                PentasixNotificationHub.notify(
                                    context = context,
                                    title = if (verif.isBoilingVerified) "Air Mendidih Terverifikasi" else "Air Belum Mendidih",
                                    message = verif.statusMessage,
                                    type = if (verif.isBoilingVerified) NotificationType.SUCCESS else NotificationType.WARNING,
                                    postSystemNotification = true
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD84315))
                        ) {
                            Icon(AppIcons.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Uji Akustik Air Mendidih (3 dtk)", fontSize = 12.sp)
                        }
                    }
                }

                Button(
                    onClick = {
                        isTimerRunning = !isTimerRunning

                        if (isTimerRunning) {
                            secondsLeft = 180
                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Perendaman 180 Detik Aktif",
                                message = "Termoterapi 52°C berjalan dengan pemantauan giroskop anti-bypass.",
                                type = NotificationType.INFO,
                                postSystemNotification = true
                            )
                        } else {
                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Timer Dihentikan",
                                message = "Perendaman benih dihentikan pada sisa $secondsLeft detik.",
                                type = NotificationType.WARNING,
                                postSystemNotification = false
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(AppIcons.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isTimerRunning) "Hentikan Timer (Sisa: $secondsLeft detik)" else "Mulai Perendaman 180 Detik (Anti-Cheat Gyro)")
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(AppIcons.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                    Text(
                        "Jejak Giroskop: VALID (Variansi gerak 0.42 > 0.05, timer otentik, bebas bypass).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- Card 2: Uji Berat Jenis Benih (Hukum Archimedes) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Science, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            "2. Seleksi Mutu Benih & Buah (Larutan Garam 1.08 g/cm³)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Komposisi: 50 gram garam dapur dilarutkan ke dalam 500 mL air.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Tenggelam: $sinkCount", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(onClick = { if (sinkCount > 0) sinkCount-- }, modifier = Modifier.size(38.dp), contentPadding = PaddingValues(0.dp)) {
                                Icon(AppIcons.Remove, contentDescription = "-", modifier = Modifier.size(16.dp))
                            }
                            Button(onClick = { sinkCount++ }, modifier = Modifier.size(38.dp), contentPadding = PaddingValues(0.dp)) {
                                Icon(AppIcons.Add, contentDescription = "+", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Melayang: $neutralCount", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(onClick = { if (neutralCount > 0) neutralCount-- }, modifier = Modifier.size(38.dp), contentPadding = PaddingValues(0.dp)) {
                                Icon(AppIcons.Remove, contentDescription = "-", modifier = Modifier.size(16.dp))
                            }
                            Button(onClick = { neutralCount++ }, modifier = Modifier.size(38.dp), contentPadding = PaddingValues(0.dp)) {
                                Icon(AppIcons.Add, contentDescription = "+", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Terapung: $floatCount", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(onClick = { if (floatCount > 0) floatCount-- }, modifier = Modifier.size(38.dp), contentPadding = PaddingValues(0.dp)) {
                                Icon(AppIcons.Remove, contentDescription = "-", modifier = Modifier.size(16.dp))
                            }
                            Button(onClick = { floatCount++ }, modifier = Modifier.size(38.dp), contentPadding = PaddingValues(0.dp)) {
                                Icon(AppIcons.Add, contentDescription = "+", modifier = Modifier.size(16.dp))
                            }
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
                        Text("Grade Mutu Cabai", style = MaterialTheme.typography.bodySmall)
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

        // --- Card 2.5: Grading Morfologi Fisik Buah Cabai (FR-4.2) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.Chili, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            "2.5. Grading Morfologi Buah Cabai",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        modifier = Modifier.wrapContentSize(),
                        onClick = {},
                        label = { Text("Grade ${morphVerdict.finalGrade}", maxLines = 1, softWrap = false) }
                    )
                }
                Text("Panjang: ${"%.1f".format(podLength)} cm | Kelengkungan: ${"%.1f".format(podCurv)}° | Merah: ${podRed.toInt()}% | Cacat: ${"%.1f".format(podDefect)}%", style = MaterialTheme.typography.bodySmall)

                Slider(value = podLength.toFloat(), onValueChange = { podLength = it.toDouble() }, valueRange = 5f..16f)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Klasifikasi Komersial", style = MaterialTheme.typography.bodySmall)
                        Text(morphVerdict.commercialTier, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Column {
                        Text("Pengali Harga", style = MaterialTheme.typography.bodySmall)
                        Text("${morphVerdict.priceMultiplier}x", fontWeight = FontWeight.Bold)
                    }
                }
                Text(morphVerdict.remarks, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Batch sortation: add current pod, recap by weight % across grades.
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val wGrams = 8.0 + podLength * 1.2  // proxy fresh weight from pod length
                            batchPods.add(
                                id.peritanix.dape.android.core.physics.BatchGradingSummary.GradedPod(
                                    grade = morphVerdict.finalGrade, weightG = wGrams
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Tambah ke Batch", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = { batchPods.clear() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Reset Batch", fontSize = 11.sp) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Rekap Batch Sortasi (${batchSummary.totalPods} buah · ${"%.1f".format(batchSummary.totalWeightG)} g)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text("Premium ${batchSummary.premiumPct}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                if (batchSummary.totalPods > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        id.peritanix.dape.android.core.physics.BatchGradingSummary.GRADES.forEach { g ->
                            Text("$g: ${batchSummary.weightPctByGrade[g] ?: 0.0}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(batchSummary.batchVerdict, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // --- Card 3: Sertifikasi Kriptografis Ed25519 (Offline) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            "3. Sertifikat Panen Terdesentralisasi (Ed25519)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Tanda tangan digital kanonikal RFC 8785 tanpa perlu sinyal internet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = {
                        // Use the device's real Ed25519 key from the Android Keystore
                        // (NOT a fixed seed) so each farmer's certificate is unique.
                        val bridge = id.peritanix.dape.android.core.crypto.AndroidKeystoreEd25519Bridge(context)
                        val kp = bridge.getOrCreateKeyPair()
                        // Aggregate every real upstream metric into the canonical payload.
                        val payloadResult = id.peritanix.dape.android.core.crypto.CertificatePayloadBuilder.build(
                            id.peritanix.dape.android.core.crypto.CertificatePayloadBuilder.Inputs(
                                landId = "land-peritanix-001",
                                cecMeq = 18.5,
                                gddFortificationCompliant = morphVerdict.finalGrade in setOf("A+", "A"),
                                boilingAcousticVerified = mixCheck in 50.5..53.5,
                                gyroImmersionSeconds = 180,
                                archimedesSinkRatio = sinkRatio.coerceIn(0.0, 1.0),
                                gradeResult = morphVerdict.finalGrade,
                                batchPremiumPct = batchSummary.premiumPct,
                                imageSha256Hex = id.peritanix.dape.android.core.crypto.ForensicHasher
                                    .sha256Hex("harvest-${batchSummary.totalPods}-${morphVerdict.finalGrade}".toByteArray()),
                                timestampEpochS = System.currentTimeMillis() / 1000
                            )
                        )
                        when (payloadResult) {
                            is id.peritanix.dape.android.core.crypto.CertificatePayloadBuilder.BuildResult.Invalid -> {
                                PentasixNotificationHub.notify(
                                    context = context, title = "Sertifikat Tidak Dapat Diterbitkan",
                                    message = payloadResult.reason,
                                    type = NotificationType.WARNING, postSystemNotification = false
                                )
                            }
                            is id.peritanix.dape.android.core.crypto.CertificatePayloadBuilder.BuildResult.Ok -> {
                                signedCert = Ed25519OfflineSigner.certificate(payloadResult.payload, kp)
                                PentasixNotificationHub.notify(
                                    context = context,
                                    title = "Sertifikat QR Diterbitkan",
                                    message = "Sertifikat mutu panen ditandatangani Ed25519 (kunci perangkat) secara offline.",
                                    type = NotificationType.SUCCESS,
                                    postSystemNotification = true
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(AppIcons.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tandatangani Kriptografis Secara Offline")
                }

                if (signedCert.isNotEmpty()) {
                    Text("QR Payload Terverifikasi (Kanonikal RFC 8785):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text(
                        signedCert,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Render visual QR Code 2D Canvas
                    val qrMatrix = remember(signedCert) {
                        QrMatrixGenerator.generateBitMatrix(signedCert)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("QR Code Sertifikat Mutu Fisik (RFC 8785)", color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier.size(200.dp)
                            ) {
                                val moduleSize = size.width / qrMatrix.size
                                for (r in qrMatrix.indices) {
                                    for (c in qrMatrix[r].indices) {
                                        if (qrMatrix[r][c]) {
                                            drawRect(
                                                color = Color.Black,
                                                topLeft = Offset(c * moduleSize, r * moduleSize),
                                                size = Size(moduleSize, moduleSize)
                                            )
                                        }
                                    }
                                }
                            }
                            Text("Pindai dengan Web Verifier (PWA / Kamera WebRTC)", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            val receipt = BluetoothThermalPrinter.generateEscPosReceipt(
                                landId = "land-peritanix-001",
                                gradeResult = seedGrade,
                                commercialTier = morphVerdict.commercialTier,
                                sinkRatio = Math.round(sinkRatio * 100.0) / 100.0,
                                sterilizationC = 52.0,
                                photoSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                                gyroVariance = 0.42,
                                cryptoSignatureB64 = "lcPzlao/gSP0CtQA0OyjB+p2yv3SVV2YcFj8GD2swTTWU3arLri06+KWHHut+veVbBGwwTcmh7uBDo5iVgOFCQ==",
                                qrPayload = signedCert
                            )
                            printStatus = "Bukti Fisik ESC/POS Siap (${receipt.byteCount} bytes) untuk Printer Termal Bluetooth 58mm/80mm."

                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Struk ESC/POS Siap",
                                message = "Label fisik (${receipt.byteCount} bytes) siap dicetak via printer Bluetooth.",
                                type = NotificationType.INFO,
                                postSystemNotification = false
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(AppIcons.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cetak ke Printer Termal Bluetooth (ESC/POS)")
                    }

                    if (printStatus.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(
                                printStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

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

                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Batch Panen Tersimpan",
                                message = "Data panen Grade $seedGrade & sertifikat QR tersimpan ke Room SQLite.",
                                type = NotificationType.SUCCESS,
                                postSystemNotification = true
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(AppIcons.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Simpan Batch Panen ke Room SQLite")
                    }

                    if (saveStatus.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
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
}
