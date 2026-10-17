package id.peritanix.dape.android.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import id.peritanix.dape.android.core.calendar.ActivityCalendar
import id.peritanix.dape.android.core.guide.ChiliGrowingGuide
import id.peritanix.dape.android.core.guide.TreatmentCameraVerifier
import id.peritanix.dape.android.core.hardware.IndonesianTtsVoiceAdvisor
import id.peritanix.dape.android.core.physics.WeatherRepository
import id.peritanix.dape.android.data.SyncQueueStore
import id.peritanix.dape.android.ui.components.AppIcons
import id.peritanix.dape.android.ui.components.NotificationType
import id.peritanix.dape.android.ui.components.PentasixNotificationHub
import id.peritanix.dape.android.ui.theme.OrangePrimary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Panduan Budidaya Cabai Terpadu DAPE — Real-Time Monitoring & Camera Treatment Verification.
 *
 * Menyediakan panduan lengkap:
 *   1. Cara Menanam (Persiapan lahan, bedengan, TWI, mulsa, lubang tanam).
 *   2. Cara Memelihara (Nutrisi NPK/Ca/B/Si, wiwilan LAI, pencegahan hama/penyakit).
 *   3. Cara Panen Terbaik (Kematangan > 85% merah, sortasi grade, sterilisasi 52°C).
 *   4. Pemantauan Real-Time HST, GDD, dan kondisi cuaca lapangan.
 *   5. Inspeksi Kamera Hasil Perawatan untuk verifikasi kepatuhan SOP secara visual.
 */
@Composable
fun GrowingGuideScreen(
    queue: SyncQueueStore? = null,
    weatherRepo: WeatherRepository? = null,
    onNavigateToTab: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // --- State: Real-Time HST & Fase Tumbuh ---
    var currentHst by remember { mutableStateOf(38) } // Default di fase pembungaan
    var expandedStageIndex by remember { mutableStateOf(2) } // Buka fase saat ini (index 2: Pembungaan)

    val activeStage = remember(currentHst) { ChiliGrowingGuide.stageForHst(currentHst) }

    // --- State: Real-Time Weather & Iklim Mikro ---
    val isRaining = weatherRepo?.isRaining ?: false
    val currentTemp = weatherRepo?.currentTempC ?: 26.5
    val currentRh = weatherRepo?.currentRhPct ?: 72.0

    val advisory = remember(currentHst, isRaining, currentTemp, currentRh) {
        ChiliGrowingGuide.generateRealtimeAdvisory(currentHst, isRaining, currentTemp, currentRh)
    }

    // --- State: Inspeksi Kamera Hasil Perawatan ---
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var latestInspection by remember {
        mutableStateOf<TreatmentCameraVerifier.InspectionResult?>(
            TreatmentCameraVerifier.createSimulatedSample(activeStage, currentHst, TreatmentCameraVerifier.SampleCondition.OPTIMAL)
        )
    }
    val inspectionHistory = remember { mutableStateListOf<TreatmentCameraVerifier.InspectionResult>() }

    // TTS Engine
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsSpeaking by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = IndonesianTtsVoiceAdvisor.getIndonesianLocale()
            }
        }
        ttsInstance = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    // Check camera permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            capturedBitmap = bmp
            val result = TreatmentCameraVerifier.analyzeBitmap(activeStage, bmp, currentHst)
            latestInspection = result
            inspectionHistory.add(0, result)

            PentasixNotificationHub.notify(
                context = context,
                title = "Verifikasi Kamera Selesai",
                message = "${result.statusTitle} (Skor: ${result.healthScore.toInt()}%) pada ${activeStage.title}",
                type = when (result.status) {
                    TreatmentCameraVerifier.VerificationStatus.VERIFIED_OPTIMAL,
                    TreatmentCameraVerifier.VerificationStatus.VERIFIED_GOOD -> NotificationType.SUCCESS
                    TreatmentCameraVerifier.VerificationStatus.ATTENTION_NEEDED -> NotificationType.WARNING
                    TreatmentCameraVerifier.VerificationStatus.CRITICAL_RISK -> NotificationType.ERROR
                },
                postSystemNotification = false
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            PentasixNotificationHub.notify(
                context = context,
                title = "Izin Kamera Dibutuhkan",
                message = "Berikan izin kamera untuk memotret dan memverifikasi hasil perawatan tanaman.",
                type = NotificationType.WARNING,
                postSystemNotification = false
            )
        }
    }

    // Checklist state per stage
    val checkedMap = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- Header Section ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(OrangePrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Book,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    "Panduan Budidaya & Perawatan Cabai",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Menanam, Memelihara, hingga Panen Terbaik · Real-Time & Kamera",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- Card 1: Pemantau Pertumbuhan Real-Time (Live HST & GDD) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Sprout,
                            contentDescription = null,
                            tint = OrangePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Pemantauan Real-Time Lahan",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "${currentHst} HST",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

                // Growth progress bar
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            activeStage.phaseName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            if (advisory.daysToHarvest > 0) "Panen dalam ${advisory.daysToHarvest} hari" else "Masa Panen Raya Aktif",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LinearProgressIndicator(
                        progress = { advisory.progressPct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = OrangePrimary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                }

                // HST Quick Jumper Chips
                Text(
                    "Pilih Posisi Hari Setelah Tanam (HST):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        -7 to "Semai",
                        15 to "Veg 15",
                        45 to "Bunga 45",
                        75 to "Buah 75",
                        105 to "Panen 105"
                    ).forEach { (h, label) ->
                        val isSelected = (activeStage.minHst <= h && h <= activeStage.maxHst)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) OrangePrimary else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    currentHst = h
                                    expandedStageIndex = ChiliGrowingGuide.stageForHst(h).index - 1
                                }
                        ) {
                            Text(
                                label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Metric Stats Row: GDD, Suhu, RH, Target Mutu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Akumulasi GDD", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${advisory.gddEstimated.toInt()} / ${activeStage.targetGdd.toInt()} °C·d", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Column {
                        Text("Suhu Ideal", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(activeStage.optimalTempRange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Column {
                        Text("Kelembaban (RH)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(activeStage.optimalRhRange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Target Panen Terbaik Badge
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            activeStage.bestYieldGoal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // --- Card 2: Peringatan Iklim Mikro & Tindakan Real-Time Hari Ini ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isRaining) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isRaining) AppIcons.WaterDrop else AppIcons.WbSunny,
                        contentDescription = null,
                        tint = if (isRaining) Color(0xFF1976D2) else OrangePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        if (isRaining) "Kondisi Lapangan: Sedang Hujan" else "Rekomendasi Tindakan Hari Ini (${currentHst} HST)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isRaining) Color(0xFF0D47A1) else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Text(
                    advisory.weatherAlert,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isRaining) Color(0xFF1565C0) else MaterialTheme.colorScheme.onPrimaryContainer
                )

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Tindakan SOP Disarankan:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrangePrimary
                        )
                        Text(
                            advisory.recommendedActionToday,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // --- Card 3: Fitur Utama — Inspeksi Kamera Hasil Perawatan ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(OrangePrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.Camera,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                "Inspeksi Kamera Hasil Perawatan",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Foto kondisi tanaman untuk verifikasi SOP & hasil perawatan",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Camera Protocol Instructions for Active Stage
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(AppIcons.Straighten, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(16.dp))
                            Text(
                                "Target: ${activeStage.cameraProtocol.targetSubject}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            activeStage.cameraProtocol.photoInstructions,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Kriteria Visual Sukses: ${activeStage.cameraProtocol.successCriteria}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                // Live Camera Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (hasCameraPermission) {
                                cameraLauncher.launch(null)
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Buka Kamera HP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val sample = TreatmentCameraVerifier.createSimulatedSample(
                                stage = activeStage,
                                hst = currentHst,
                                condition = TreatmentCameraVerifier.SampleCondition.OPTIMAL
                            )
                            latestInspection = sample
                            inspectionHistory.add(0, sample)
                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Uji Sampel Foto Optimal",
                                message = "Hasil perawatan terverifikasi sangat sehat (Skor ${sample.healthScore.toInt()}%).",
                                type = NotificationType.SUCCESS,
                                postSystemNotification = false
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Text("Uji Sehat", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val sample = TreatmentCameraVerifier.createSimulatedSample(
                                stage = activeStage,
                                hst = currentHst,
                                condition = TreatmentCameraVerifier.SampleCondition.MILD_DEFECT
                            )
                            latestInspection = sample
                            inspectionHistory.add(0, sample)
                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Uji Sampel Anomali",
                                message = "Terdeteksi defisiensi/gejala nekrosis (Skor ${sample.healthScore.toInt()}%).",
                                type = NotificationType.WARNING,
                                postSystemNotification = false
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Text("Uji Gejala", fontSize = 11.sp)
                    }
                }

                // Latest Inspection Result Display
                latestInspection?.let { result ->
                    val statusColor = Color(result.status.badgeColorHex)

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Header of result
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(statusColor, CircleShape)
                                    )
                                    Text(
                                        result.statusTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = statusColor
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = statusColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "Skor: ${result.healthScore.toInt()}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Captured photo thumbnail (if real camera used)
                            capturedBitmap?.let { bmp ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Foto Perawatan",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            // Metrics breakdown row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Klorofil Hijau", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${result.greenPct}%", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF2E7D32))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Kematangan Merah", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${result.redPct}%", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFD32F2F))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Klorosis / Kuning", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${result.yellowPct}%", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFF57F17))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Nekrosis / Patek", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${result.defectPct}%", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (result.defectPct > 5) Color.Red else Color.Gray)
                                }
                            }

                            HorizontalDivider()

                            // Diagnosis & Prescription
                            Text(
                                "Diagnosis Visual:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                result.diagnosisText,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                "Tindakan Preskriptif SOP:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrangePrimary
                            )
                            Text(
                                result.prescriptiveAction,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Action buttons: Save to Calendar & TTS Listen
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        queue?.let { q ->
                                            val today = LocalDate.now()
                                            q.addActivity(
                                                epochDay = today.toEpochDay(),
                                                kind = "PENGAMATAN",
                                                title = "Verifikasi Kamera: ${result.stageTitle}",
                                                notes = "Skor: ${result.healthScore.toInt()}% (${result.statusTitle}). ${result.prescriptiveAction}",
                                                landId = "L-01"
                                            )
                                        }

                                        PentasixNotificationHub.notify(
                                            context = context,
                                            title = "Log Verifikasi Tersimpan",
                                            message = "Hasil verifikasi kamera dicatat di Kalender & Room SQLite (Offline-First).",
                                            type = NotificationType.SUCCESS,
                                            postSystemNotification = true
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(AppIcons.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Simpan ke Kalender", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (isTtsSpeaking) {
                                            ttsInstance?.stop()
                                            isTtsSpeaking = false
                                        } else {
                                            val narration = IndonesianTtsVoiceAdvisor.composeTreatmentInspectionScript(
                                                stageTitle = result.stageTitle,
                                                score = result.healthScore,
                                                statusLabel = result.statusTitle,
                                                actionAdvice = result.prescriptiveAction
                                            )
                                            ttsInstance?.speak(narration, TextToSpeech.QUEUE_FLUSH, null, "INSP_TTS")
                                            isTtsSpeaking = true
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(0.9f)
                                ) {
                                    Icon(
                                        imageVector = if (isTtsSpeaking) AppIcons.Stop else AppIcons.VolumeUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = OrangePrimary
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isTtsSpeaking) "Stop Suara" else "Dengarkan", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Card 4: 5-Tahap Panduan Lengkap Budidaya Cabai (Accordion) ---
        Text(
            "Tahapan Lengkap Budidaya Cabai DAPE",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        ChiliGrowingGuide.stages.forEachIndexed { i, stage ->
            val isOpen = expandedStageIndex == i
            val isCurrentActive = stage.index == activeStage.index

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOpen) MaterialTheme.colorScheme.primaryContainer
                    else if (isCurrentActive) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isOpen) 3.dp else 1.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedStageIndex = if (isOpen) -1 else i }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    if (isOpen) OrangePrimary else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stage.index.toString(),
                                fontWeight = FontWeight.Bold,
                                color = if (isOpen) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }

                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    stage.title,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isOpen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                if (isCurrentActive) {
                                    Surface(
                                        color = OrangePrimary,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "FASE AKTIF",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                stage.hstRange,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOpen) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                else OrangePrimary
                            )
                        }

                        Icon(
                            if (isOpen) AppIcons.Remove else AppIcons.Add,
                            contentDescription = null,
                            tint = if (isOpen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = isOpen) {
                        Column(
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                stage.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            // Target Panen Terbaik
                            Surface(
                                color = Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(AppIcons.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                    Text(stage.bestYieldGoal, fontSize = 11.sp, color = Color(0xFF1B5E20), fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // 1. Cara Menanam
                            SectionLabel("1. Cara Menanam & Persiapan Lahan")
                            stage.plantingGuide.forEach { item ->
                                BulletRow(item, MaterialTheme.colorScheme.onPrimaryContainer)
                            }

                            // 2. Cara Memelihara / Merawat
                            SectionLabel("2. Cara Memelihara & Perawatan Rutin")
                            stage.maintenanceGuide.forEach { item ->
                                BulletRow(item, MaterialTheme.colorScheme.onPrimaryContainer)
                            }

                            // 3. Cara Panen Kualitas Terbaik
                            SectionLabel("3. Panduan Panen & Kualitas Terbaik")
                            stage.harvestGuide.forEach { item ->
                                BulletRow(item, MaterialTheme.colorScheme.onPrimaryContainer)
                            }

                            // 4. Protokol Kamera Hasil Perawatan
                            SectionLabel("4. Protokol Kamera Hasil Perawatan")
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Objek Foto: ${stage.cameraProtocol.targetSubject}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                                    Text("Instruksi: ${stage.cameraProtocol.photoInstructions}", fontSize = 11.sp)
                                    Text("Kriteria Keberhasilan: ${stage.cameraProtocol.successCriteria}", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                    stage.cameraProtocol.inspectionKeyPoints.forEach { pt ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("•", fontSize = 10.sp, color = OrangePrimary)
                                            Text(pt, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }

                            // 5. Risiko Utama
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Column {
                                        Text("Risiko & Pantangan Utama:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        Text(stage.risk, fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }

                            // 6. Checklist Tindakan Perawatan
                            SectionLabel("Checklist Tindakan Perawatan")
                            stage.checklist.forEach { chk ->
                                val key = "${stage.index}-$chk"
                                val isChecked = checkedMap[key] ?: false
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { checkedMap[key] = !isChecked }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checkedMap[key] = it },
                                        colors = CheckboxDefaults.colors(checkedColor = OrangePrimary)
                                    )
                                    Text(
                                        chk,
                                        fontSize = 12.sp,
                                        color = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = if (isChecked) FontWeight.Normal else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Card 5: Riwayat Hasil Inspeksi Kamera Terbaru ---
        if (inspectionHistory.isNotEmpty()) {
            Text(
                "Riwayat Inspeksi Kamera Perawatan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    inspectionHistory.take(5).forEach { rec ->
                        val recColor = Color(rec.status.badgeColorHex)
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(recColor, CircleShape)
                                    )
                                    Column {
                                        Text(
                                            "${rec.stageTitle} (${rec.hst} HST)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            rec.statusTitle,
                                            fontSize = 10.sp,
                                            color = recColor
                                        )
                                    }
                                }

                                Surface(
                                    color = recColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "${rec.healthScore.toInt()}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = recColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun BulletRow(text: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(5.dp)
                .background(OrangePrimary, CircleShape)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = color,
            lineHeight = 16.sp
        )
    }
}
