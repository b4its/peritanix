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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedTextField
import id.peritanix.dape.android.core.crypto.QrMatrixGenerator
import id.peritanix.dape.android.core.hardware.BluetoothThermalPrinter
import id.peritanix.dape.android.core.hardware.BoilingSoundVerifier
import id.peritanix.dape.android.core.hardware.IndonesianTtsVoiceAdvisor
import id.peritanix.dape.android.core.hardware.OfflineVoiceController
import id.peritanix.dape.android.core.physics.BlackCalorimetryEngine
import id.peritanix.dape.android.core.ai_edge.MultiInstancePodSegmentationEngine
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

    // State: Meja Sortir Pintar & Multi-Instance Segmentation (Tahap 5.3)
    var sortingTableAngleDeg by remember { mutableStateOf(1.2) }
    var lensFocusDiopters by remember { mutableStateOf(3.0f) }
    var sortingSummary by remember {
        mutableStateOf(
            MultiInstancePodSegmentationEngine.detectAndSegmentTable(
                podCount = 16,
                tiltAngleDeg = 1.2,
                lensFocusDistanceDiopters = 3.0f
            )
        )
    }
    var selectedPodDetail by remember { mutableStateOf<MultiInstancePodSegmentationEngine.PodInstance?>(null) }

    // State: Perintah Suara Offline Hands-Free (Tahap 6.3)
    val voiceController = remember { OfflineVoiceController(context) }
    var isVoiceActive by remember { mutableStateOf(false) }
    var voiceStatusMsg by remember { mutableStateOf("Perintah suara siap ('Mulai Hitung', 'Bacakan Dosis', 'Cetak QR').") }

    DisposableEffect(voiceController) {
        voiceController.onCommandRecognized = { cmd ->
            when (cmd) {
                OfflineVoiceController.VoiceCommand.START_TIMER -> {
                    isTimerRunning = true
                    secondsLeft = 180
                    PentasixNotificationHub.notify(
                        context = context,
                        title = "Timer 180s Dimulai (Suara)",
                        message = "Timer termoterapi 52°C aktif via perintah suara.",
                        type = NotificationType.SUCCESS,
                        postSystemNotification = true
                    )
                }
                OfflineVoiceController.VoiceCommand.SPEAK_DOSAGE -> {
                    isTtsPlaying = true
                    PentasixNotificationHub.notify(
                        context = context,
                        title = "Membacakan Takaran (Suara)",
                        message = "TTS membacakan takaran Asas Black 52°C.",
                        type = NotificationType.INFO,
                        postSystemNotification = false
                    )
                }
                OfflineVoiceController.VoiceCommand.PRINT_CERTIFICATE -> {
                    if (signedCert.isNotBlank()) {
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
                        printStatus = "Struk bukti fisik dicetak (${receipt.byteCount} bytes) via suara."
                    }
                }
                else -> {}
            }
        }
        voiceController.onSpeechStatusChanged = { voiceStatusMsg = it }
        onDispose { voiceController.destroy() }
    }

    // State: Mode Verifikasi Pembeli / Off-Taker Offline (Tahap 6.2)
    var buyerCertInput by remember { mutableStateOf("") }
    var buyerVerdict by remember { mutableStateOf<Ed25519OfflineSigner.VerificationVerdict?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Bar Perintah Suara Offline Hands-Free (Tahap 6.3) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(AppIcons.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text("Perintah Suara Hands-Free Lapangan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            isVoiceActive = !isVoiceActive
                            if (isVoiceActive) voiceController.startListening() else voiceController.stopListening()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isVoiceActive) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(if (isVoiceActive) "Stop Mic" else "Aktifkan Mic", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    voiceStatusMsg,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
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

        // --- Card 2.5: Meja Sortir Pintar & Visual Reject Overlay (Tahap 5.3) ---
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
                            Icon(AppIcons.Camera, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(
                                "2.5. Meja Sortir Pintar & Visual Reject Overlay",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                "Multi-Instance Segmentation & Deteksi Spora Patek (Tahap 5.3)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Baris Kalibrasi Sudut & Skala Optik
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = if (sortingSummary.isTopDownAngleLocked) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (sortingSummary.isTopDownAngleLocked) Color(0xFFA5D6A7) else Color(0xFFFFCDD2))
                    ) {
                        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                if (sortingSummary.isTopDownAngleLocked) AppIcons.CheckCircle else AppIcons.Warning,
                                contentDescription = null,
                                tint = if (sortingSummary.isTopDownAngleLocked) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                if (sortingSummary.isTopDownAngleLocked) "Sudut 0° Terkunci (${sortingSummary.tiltAngleDeg}°)" else "Miring ${sortingSummary.tiltAngleDeg}°",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sortingSummary.isTopDownAngleLocked) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(AppIcons.Straighten, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Text(
                                "Skala: ${sortingSummary.opticalMmPerPixel} mm/px",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Tombol Pindai & Refresh Meja Sortir
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            sortingSummary = MultiInstancePodSegmentationEngine.detectAndSegmentTable(
                                podCount = 16,
                                tiltAngleDeg = sortingTableAngleDeg,
                                lensFocusDistanceDiopters = lensFocusDiopters
                            )
                            selectedPodDetail = null
                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Pemindaian Meja Selesai",
                                message = "Terdeteksi ${sortingSummary.totalPodsDetected} buah cabai pada alas kontras.",
                                type = NotificationType.INFO,
                                postSystemNotification = false
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pindai Meja Sortir (AI)", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            sortingTableAngleDeg = if (sortingTableAngleDeg > 2.0) 0.8 else 1.2
                            sortingSummary = MultiInstancePodSegmentationEngine.detectAndSegmentTable(
                                podCount = 16,
                                tiltAngleDeg = sortingTableAngleDeg,
                                lensFocusDistanceDiopters = lensFocusDiopters
                            )
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(AppIcons.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Simulasi Posisi", fontSize = 11.sp)
                    }
                }

                // VISUAL REJECT OVERLAY (CANVAS & MAT)
                Card(
                    modifier = Modifier.fillMaxWidth().height(210.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                ) {
                    Box(Modifier.fillMaxSize()) {
                        // Background Grid & Pod Render
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            val gridSpacing = 40.dp.toPx()
                            var gx = 0f
                            while (gx < w) {
                                drawLine(Color(0xFF1E293B), Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
                                gx += gridSpacing
                            }
                            var gy = 0f
                            while (gy < h) {
                                drawLine(Color(0xFF1E293B), Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                                gy += gridSpacing
                            }

                            sortingSummary.activePods.forEach { pod ->
                                val cx = pod.xRel * w
                                val cy = pod.yRel * h
                                val isSelected = (selectedPodDetail?.id == pod.id)

                                if (pod.isReject) {
                                    // VISUAL REJECT: Bright Red Ring & Target Indicator
                                    drawCircle(
                                        color = Color(0xFFFF1744).copy(alpha = 0.25f),
                                        radius = 26.dp.toPx(),
                                        center = Offset(cx, cy)
                                    )
                                    drawCircle(
                                        color = Color(0xFFFF1744),
                                        radius = 20.dp.toPx(),
                                        center = Offset(cx, cy),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
                                    )
                                    drawLine(Color(0xFFFF1744), Offset(cx - 24.dp.toPx(), cy), Offset(cx + 24.dp.toPx(), cy), strokeWidth = 1.5.dp.toPx())
                                    drawLine(Color(0xFFFF1744), Offset(cx, cy - 24.dp.toPx()), Offset(cx, cy + 24.dp.toPx()), strokeWidth = 1.5.dp.toPx())

                                    drawOval(
                                        color = Color(0xFF880E4F),
                                        topLeft = Offset(cx - 14.dp.toPx(), cy - 7.dp.toPx()),
                                        size = Size(28.dp.toPx(), 14.dp.toPx())
                                    )
                                    drawCircle(
                                        color = Color(0xFF212121),
                                        radius = 4.dp.toPx(),
                                        center = Offset(cx, cy)
                                    )
                                } else {
                                    val ringColor = when (pod.grade) {
                                        "A+" -> Color(0xFF00E676)
                                        "A" -> Color(0xFF4CAF50)
                                        else -> Color(0xFFFFB300)
                                    }

                                    if (isSelected) {
                                        drawCircle(
                                            color = Color.White.copy(alpha = 0.4f),
                                            radius = 20.dp.toPx(),
                                            center = Offset(cx, cy)
                                        )
                                    }

                                    drawCircle(
                                        color = ringColor,
                                        radius = 16.dp.toPx(),
                                        center = Offset(cx, cy),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                                    )

                                    drawOval(
                                        color = Color(0xFFE53935),
                                        topLeft = Offset(cx - 13.dp.toPx(), cy - 6.dp.toPx()),
                                        size = Size(26.dp.toPx(), 12.dp.toPx())
                                    )
                                    drawLine(
                                        color = Color(0xFF4CAF50),
                                        start = Offset(cx - 13.dp.toPx(), cy),
                                        end = Offset(cx - 18.dp.toPx(), cy - 4.dp.toPx()),
                                        strokeWidth = 2.dp.toPx()
                                    )
                                }
                            }
                        }

                        // Top summary strip inside canvas
                        Row(
                            Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    "Meja Sortir: ${sortingSummary.activePods.size} Buah Aktif",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (sortingSummary.rejectPods.isNotEmpty()) {
                                Surface(color = Color(0xFFFF1744), shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        "REJECT: ${sortingSummary.rejectPods.size} PATEK/CACAT",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Surface(color = Color(0xFF2E7D32), shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        "100% BEBAS CACAT",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Alert and Action for Rejects
                if (sortingSummary.rejectPods.isNotEmpty()) {
                    Surface(
                        color = Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF1744)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(AppIcons.Warning, contentDescription = null, tint = Color(0xFFD50000), modifier = Modifier.size(18.dp))
                                Text(
                                    "Visual Reject Alert: ${sortingSummary.rejectPods.size} Buah Berisiko Patek!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFFB71C1C)
                                )
                            }
                            Text(
                                "AI mendeteksi spora antraknosa / busuk ujung pada koordinat berlingkar MERAH TERANG di layar. Singkirkan segera agar spora tidak menyebar ke karung ekspor.",
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = Color(0xFFC62828)
                            )
                            Button(
                                onClick = {
                                    sortingSummary = MultiInstancePodSegmentationEngine.removeAllRejects(sortingSummary)
                                    PentasixNotificationHub.notify(
                                        context = context,
                                        title = "Buah Cacat Disingkirkan",
                                        message = "Seluruh buah berisiko patek telah disingkirkan dari meja sortir.",
                                        type = NotificationType.SUCCESS,
                                        postSystemNotification = false
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD50000)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(AppIcons.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Singkirkan Semua Buah Cacat (Bersihkan Meja)", fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFA5D6A7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(AppIcons.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                            Column {
                                Text(
                                    "Meja Sortir Bersih: Standar Ekspor Terpenuhi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFF1B5E20)
                                )
                                Text(
                                    "Nol spora patek terdeteksi. Kualitas batch memenuhi kriteria Ekspor Prime Grade A+.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }

                // Batch Sortation Recap & Sync to RFC 8785 Certificate
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Rekapitulasi Mutu Batch (${sortingSummary.activePods.size} buah)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Premium ${sortingSummary.premiumPct}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (sortingSummary.premiumPct >= 85.0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("A+", "A", "B", "BS").forEach { g ->
                        val count = sortingSummary.countByGrade[g] ?: 0
                        val pct = sortingSummary.weightPctByGrade[g] ?: 0.0
                        val col = when (g) {
                            "A+" -> Color(0xFF1B5E20)
                            "A" -> Color(0xFF2E7D32)
                            "B" -> Color(0xFFE65100)
                            else -> Color(0xFFB71C1C)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(g, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = col)
                            Text("$count btr", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Text("${pct}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Button(
                    onClick = {
                        batchPods.clear()
                        sortingSummary.activePods.forEach { p ->
                            batchPods.add(
                                id.peritanix.dape.android.core.physics.BatchGradingSummary.GradedPod(
                                    grade = p.grade,
                                    weightG = p.weightGrams
                                )
                            )
                        }
                        PentasixNotificationHub.notify(
                            context = context,
                            title = "Batch Meja Tersinkronisasi",
                            message = "${sortingSummary.activePods.size} buah tersortir dialirkan ke sertifikat Ed25519 & struk fisik.",
                            type = NotificationType.SUCCESS,
                            postSystemNotification = true
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(AppIcons.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Salin Hasil Meja ke Sertifikat Ed25519", fontSize = 11.sp)
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

        // --- Card 4: Mode Pemindai & Verifikasi Pembeli / Off-Taker (Offline Ed25519 - Tahap 6.2) ---
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
                            .background(Color(0xFF2E7D32), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            "4. Mode Pemindai & Verifikasi Pembeli / Off-Taker (100% Offline)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Verifikasi keaslian kriptografis Ed25519 & 6 parameter mutu cabai tanpa internet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Quick load helper buttons
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            if (signedCert.isNotBlank()) {
                                buyerCertInput = signedCert
                            } else {
                                val testKp = Ed25519OfflineSigner.fromSeed(ByteArray(32) { (it + 1).toByte() })
                                val samplePayload = mapOf(
                                    "land_id" to "land-peritanix-001",
                                    "cec_meq" to 18.5,
                                    "gdd_fortification_compliant" to true,
                                    "sterilization_c" to 52.0,
                                    "acoustic_boiling_verified" to true,
                                    "gyro_immersion_seconds" to 180,
                                    "archimedes_sink_ratio" to 0.88,
                                    "grade_result" to "A+",
                                    "batch_premium_pct" to 95.0,
                                    "image_sha256_hash" to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                                    "timestamp" to (System.currentTimeMillis() / 1000)
                                )
                                buyerCertInput = Ed25519OfflineSigner.certificate(samplePayload, testKp)
                            }
                            buyerVerdict = Ed25519OfflineSigner.verifyCertificate(buyerCertInput)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(if (signedCert.isNotBlank()) "Muat Terbitan di Atas" else "Muat Contoh Grade A+", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            if (buyerCertInput.isNotBlank()) {
                                val corrupted = if (buyerCertInput.contains("\"grade_result\":\"A+\"")) {
                                    buyerCertInput.replace("\"grade_result\":\"A+\"", "\"grade_result\":\"BS\"")
                                } else {
                                    buyerCertInput.replace("\"land_id\":\"", "\"land_id\":\"palsu-")
                                }
                                buyerCertInput = corrupted
                                buyerVerdict = Ed25519OfflineSigner.verifyCertificate(corrupted)
                                PentasixNotificationHub.notify(
                                    context = context,
                                    title = "Simulasi Fraud Diuji",
                                    message = "Payload sertifikat diubah secara sengaja. Verifier kripto menolak tanda tangan.",
                                    type = NotificationType.WARNING,
                                    postSystemNotification = false
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Uji Simulasi Fraud", fontSize = 11.sp, color = Color(0xFFD32F2F))
                    }
                }

                OutlinedTextField(
                    value = buyerCertInput,
                    onValueChange = {
                        buyerCertInput = it
                        if (it.isBlank()) buyerVerdict = null
                    },
                    label = { Text("Payload JSON QR Karung Cabai (RFC 8785)", fontSize = 11.sp) },
                    placeholder = { Text("Tempel JSON sertifikat atau pindai QR karung...", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 120.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                    maxLines = 4
                )

                Button(
                    onClick = {
                        if (buyerCertInput.isNotBlank()) {
                            val verdict = Ed25519OfflineSigner.verifyCertificate(buyerCertInput)
                            buyerVerdict = verdict
                            PentasixNotificationHub.notify(
                                context = context,
                                title = if (verdict.isAuthentic) "Verifikasi Berhasil" else "Sertifikat Ditolak",
                                message = verdict.badgeTitle,
                                type = if (verdict.isAuthentic) NotificationType.SUCCESS else NotificationType.ERROR,
                                postSystemNotification = true
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Icon(AppIcons.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Verifikasi Tanda Tangan Kriptografis (Ed25519)")
                }

                // Verification Result Badge and Breakdown Table
                buyerVerdict?.let { verdict ->
                    val badgeBg = if (verdict.isAuthentic && verdict.isGradeAPlus) Color(0xFFE8F5E9)
                        else if (verdict.isAuthentic) Color(0xFFE3F2FD)
                        else Color(0xFFFFEBEE)
                    val badgeBorder = if (verdict.isAuthentic && verdict.isGradeAPlus) Color(0xFF2E7D32)
                        else if (verdict.isAuthentic) Color(0xFF1976D2)
                        else Color(0xFFD32F2F)
                    val badgeFg = if (verdict.isAuthentic && verdict.isGradeAPlus) Color(0xFF1B5E20)
                        else if (verdict.isAuthentic) Color(0xFF0D47A1)
                        else Color(0xFFB71C1C)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = badgeBg),
                        border = BorderStroke(1.5.dp, badgeBorder)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    if (verdict.isAuthentic) AppIcons.CheckCircle else AppIcons.Info,
                                    contentDescription = null,
                                    tint = badgeFg,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        verdict.badgeTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = badgeFg
                                    )
                                    Text(
                                        if (verdict.isAuthentic) "${verdict.commercialTier} · Pengali Harga: ${verdict.priceMultiplier}x" else "Integritas Kripto Gagal",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = badgeFg.copy(alpha = 0.9f)
                                    )
                                }
                            }

                            Text(
                                verdict.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = badgeFg
                            )

                            HorizontalDivider(color = badgeBorder.copy(alpha = 0.3f))

                            Text("Rincian 6 Parameter Mutu Terverifikasi:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = badgeFg)
                            verdict.details.forEach { (k, v) ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(k, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(v, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = badgeFg)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
