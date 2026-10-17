package id.peritanix.dape.android.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
 *   1. Cara Menanam (Persiapan lahan, bedengan 40-50 cm, TWI, mulsa, lubang tanam).
 *   2. Cara Memelihara (Nutrisi NPK/Ca/B/Si, wiwilan LAI, pencegahan hama/penyakit).
 *   3. Cara Panen Terbaik (Kematangan > 85% merah, sortasi grade, sterilisasi 52°C Asas Black).
 *   4. Pemantauan Real-Time HST, GDD, dan kondisi cuaca lapangan.
 *   5. Inspeksi Kamera Hasil Setiap Perawatan untuk verifikasi kepatuhan SOP visual dan pencatatan otomatis.
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

    // --- State: Treatment Aktif yang Diverifikasi Kamera ---
    var selectedTreatment by remember {
        mutableStateOf<ChiliGrowingGuide.TreatmentItem?>(activeStage.treatments.firstOrNull())
    }

    LaunchedEffect(activeStage) {
        if (selectedTreatment == null || activeStage.treatments.none { it.id == selectedTreatment?.id }) {
            selectedTreatment = activeStage.treatments.firstOrNull()
        }
    }

    // --- State: Filter Navigasi Sub-Tab Panduan ---
    var selectedFilterTab by remember { mutableStateOf(0) }
    val filterTabs = listOf(
        "Semua Panduan",
        "Cara Menanam",
        "Cara Memelihara",
        "Kunci Panen",
        "Inspeksi Kamera"
    )

    // --- State: Dialog Panduan Kamera Perawatan ---
    var showCameraGuideDialog by remember { mutableStateOf(false) }
    var dialogTreatment by remember { mutableStateOf<ChiliGrowingGuide.TreatmentItem?>(null) }

    // --- State: Real-Time Weather & Iklim Mikro ---
    val isRaining = weatherRepo?.isRaining ?: false
    val currentTemp = weatherRepo?.currentTempC ?: 26.5
    val currentRh = weatherRepo?.currentRhPct ?: 72.0

    val advisory = remember(currentHst, isRaining, currentTemp, currentRh) {
        ChiliGrowingGuide.generateRealtimeAdvisory(currentHst, isRaining, currentTemp, currentRh)
    }

    // --- State: Inspeksi Kamera Hasil Perawatan ---
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val treatmentResults = remember { mutableStateMapOf<String, TreatmentCameraVerifier.InspectionResult>() }
    val treatmentBitmaps = remember { mutableStateMapOf<String, Bitmap>() }

    var latestInspection by remember {
        mutableStateOf<TreatmentCameraVerifier.InspectionResult?>(
            TreatmentCameraVerifier.createSimulatedSample(
                stage = activeStage,
                hst = currentHst,
                condition = TreatmentCameraVerifier.SampleCondition.OPTIMAL,
                treatmentName = activeStage.treatments.firstOrNull()?.name ?: "Perawatan Umum",
                treatmentId = activeStage.treatments.firstOrNull()?.id
            ).also { sample ->
                activeStage.treatments.firstOrNull()?.let { trt ->
                    treatmentResults[trt.id] = sample
                }
            }
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
            tts?.stop()
            tts?.shutdown()
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
            val currentTrt = selectedTreatment
            val treatmentName = currentTrt?.name ?: activeStage.cameraProtocol.targetSubject
            val treatmentId = currentTrt?.id
            val result = TreatmentCameraVerifier.analyzeBitmap(
                stage = activeStage,
                bitmap = bmp,
                hst = currentHst,
                treatmentName = treatmentName,
                treatmentId = treatmentId
            )
            latestInspection = result
            if (currentTrt != null) {
                treatmentResults[currentTrt.id] = result
                treatmentBitmaps[currentTrt.id] = bmp
            }
            inspectionHistory.add(0, result)

            PentasixNotificationHub.notify(
                context = context,
                title = "Verifikasi Hasil Perawatan",
                message = "${result.treatmentName}: ${result.statusTitle} (Skor: ${result.healthScore.toInt()}%)",
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

    fun triggerTreatmentCamera(treatment: ChiliGrowingGuide.TreatmentItem) {
        selectedTreatment = treatment
        if (hasCameraPermission) {
            cameraLauncher.launch(null)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun openCameraGuideDialog(treatment: ChiliGrowingGuide.TreatmentItem) {
        dialogTreatment = treatment
        selectedTreatment = treatment
        showCameraGuideDialog = true
    }

    fun triggerTreatmentSimulation(
        treatment: ChiliGrowingGuide.TreatmentItem,
        condition: TreatmentCameraVerifier.SampleCondition
    ) {
        selectedTreatment = treatment
        val sample = TreatmentCameraVerifier.createSimulatedSample(
            stage = activeStage,
            hst = currentHst,
            condition = condition,
            treatmentName = treatment.name,
            treatmentId = treatment.id
        )
        latestInspection = sample
        treatmentResults[treatment.id] = sample
        inspectionHistory.add(0, sample)
        PentasixNotificationHub.notify(
            context = context,
            title = "Verifikasi: ${treatment.name}",
            message = "${sample.statusTitle} (Skor ${sample.healthScore.toInt()}%).",
            type = if (condition == TreatmentCameraVerifier.SampleCondition.OPTIMAL) NotificationType.SUCCESS else NotificationType.WARNING,
            postSystemNotification = false
        )
    }

    fun saveInspectionToCalendar(result: TreatmentCameraVerifier.InspectionResult) {
        queue?.let { q ->
            val today = LocalDate.now()
            q.addActivity(
                epochDay = today.toEpochDay(),
                kind = "PENGAMATAN",
                title = "Hasil Verifikasi: ${result.treatmentName}",
                notes = "Skor: ${result.healthScore.toInt()}% (${result.statusTitle}). ${result.prescriptiveAction}",
                landId = "L-01"
            )
        }
        PentasixNotificationHub.notify(
            context = context,
            title = "Log Verifikasi Tersimpan",
            message = "Hasil perawatan '${result.treatmentName}' dicatat di Kalender 30 Tahun & Room DB.",
            type = NotificationType.SUCCESS,
            postSystemNotification = true
        )
    }

    fun speakInspection(result: TreatmentCameraVerifier.InspectionResult) {
        if (isTtsSpeaking) {
            ttsInstance?.stop()
            isTtsSpeaking = false
        } else {
            val narration = IndonesianTtsVoiceAdvisor.composeTreatmentInspectionScript(
                stageTitle = result.stageTitle,
                score = result.healthScore,
                statusLabel = result.statusTitle,
                actionAdvice = result.prescriptiveAction,
                treatmentName = result.treatmentName
            )
            ttsInstance?.speak(narration, TextToSpeech.QUEUE_FLUSH, null, "INSP_TTS")
            isTtsSpeaking = true
        }
    }

    // Checklist state per stage
    val checkedMap = remember { mutableStateMapOf<String, Boolean>() }

    // --- Dialog Panduan Kamera Perawatan SOP ---
    if (showCameraGuideDialog && dialogTreatment != null) {
        val trt = dialogTreatment!!
        AlertDialog(
            onDismissRequest = { showCameraGuideDialog = false },
            icon = { Icon(AppIcons.Camera, contentDescription = null, tint = OrangePrimary) },
            title = {
                Text(
                    "Panduan Kamera SOP: ${trt.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = OrangePrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "${trt.pillar.displayName} · ${trt.category}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrangePrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        "Subjek Foto: ${trt.targetCameraSubject}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Kriteria Sukses: ${trt.visualSuccessCriteria}",
                        fontSize = 11.sp,
                        color = Color(0xFF2E7D32)
                    )
                    HorizontalDivider()
                    Text("Sudut Bidik: ${trt.shootingAngle}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Pencahayaan: ${trt.recommendedLighting}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCameraGuideDialog = false
                        triggerTreatmentCamera(trt)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Buka Kamera HP", fontSize = 11.sp)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = {
                            showCameraGuideDialog = false
                            triggerTreatmentSimulation(trt, TreatmentCameraVerifier.SampleCondition.OPTIMAL)
                        }
                    ) {
                        Text("Uji Sehat", fontSize = 10.sp)
                    }
                    TextButton(onClick = { showCameraGuideDialog = false }) {
                        Text("Tutup", fontSize = 10.sp)
                    }
                }
            }
        )
    }

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

        // --- Filter Navigasi Sub-Tab Panduan ---
        val filterRowScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(filterRowScrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            filterTabs.forEachIndexed { idx, title ->
                val isSelected = selectedFilterTab == idx
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) OrangePrimary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { selectedFilterTab = idx }
                ) {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // --- TAMPILAN: Card 1 & Card 2 Selalu Tampil jika Tab != 4 (Inspeksi Kamera Murni) atau jika Tab == 0 ---
        if (selectedFilterTab != 4) {
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

                    // HST Quick Jumper Chips & Stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Posisi Hari Setelah Tanam (HST):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    val newH = (currentHst - 1).coerceAtLeast(-21)
                                    currentHst = newH
                                    expandedStageIndex = ChiliGrowingGuide.stageForHst(newH).index - 1
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(AppIcons.Remove, contentDescription = "Kurang HST", modifier = Modifier.size(14.dp))
                            }
                            Text(
                                "$currentHst",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.width(30.dp),
                                textAlign = TextAlign.Center
                            )
                            FilledTonalIconButton(
                                onClick = {
                                    val newH = (currentHst + 1).coerceAtMost(120)
                                    currentHst = newH
                                    expandedStageIndex = ChiliGrowingGuide.stageForHst(newH).index - 1
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(AppIcons.Add, contentDescription = "Tambah HST", modifier = Modifier.size(14.dp))
                            }
                        }
                    }

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

                    // Metric Stats Row: GDD, Suhu, RH
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Mikroklimat: ${currentTemp.toInt()}°C · RH ${currentRh.toInt()}%${if (isRaining) " · Sedang Hujan" else " · Cerah"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRaining) Color(0xFF0D47A1) else MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        OutlinedButton(
                            onClick = { onNavigateToTab?.invoke(7) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(AppIcons.WbSunny, contentDescription = null, modifier = Modifier.size(13.dp), tint = OrangePrimary)
                            Spacer(Modifier.width(4.dp))
                            Text("Buka Cuaca & Sync", fontSize = 10.sp)
                        }
                    }
                }
            }

            // --- Card 2.5: Dasbor Status 3-Pilar Budidaya Real-Time ---
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
                            Icon(AppIcons.CheckCircle, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(20.dp))
                            Text(
                                "Status 3-Pilar Budidaya Terpadu",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = OrangePrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "Real-Time",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrangePrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    val p1Treatments = remember { ChiliGrowingGuide.getTreatmentsForPillar(ChiliGrowingGuide.PillarType.PLANTING) }
                    val p1Verified = p1Treatments.count { treatmentResults.containsKey(it.id) }

                    val p2Treatments = remember { ChiliGrowingGuide.getTreatmentsForPillar(ChiliGrowingGuide.PillarType.MAINTENANCE) }
                    val p2Verified = p2Treatments.count { treatmentResults.containsKey(it.id) }

                    val p3Treatments = remember { ChiliGrowingGuide.getTreatmentsForPillar(ChiliGrowingGuide.PillarType.HARVEST) }
                    val p3Verified = p3Treatments.count { treatmentResults.containsKey(it.id) }

                    val totalCompliance = ChiliGrowingGuide.calculateTotalComplianceScore(treatmentResults.keys)

                    // 3 Pillar Columns / Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pilar 1: Menanam
                        val isP1Selected = selectedFilterTab == 1
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isP1Selected) OrangePrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (isP1Selected) OrangePrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedFilterTab = 1 }
                        ) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(AppIcons.Terrain, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(13.dp))
                                    Text("1. Menanam", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }
                                Text("Bedengan: 45 cm", fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                Text("Mulsa: Perak", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Kamera: $p1Verified/${p1Treatments.size} Teruji", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (p1Verified > 0) Color(0xFF2E7D32) else OrangePrimary)
                            }
                        }

                        // Pilar 2: Memelihara
                        val isP2Selected = selectedFilterTab == 2
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isP2Selected) OrangePrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (isP2Selected) OrangePrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedFilterTab = 2 }
                        ) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(AppIcons.Camera, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(13.dp))
                                    Text("2. Pelihara", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                                }
                                Text("Wiwilan: LAI < 3.5", fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                Text("Nutrisi: NPK/Ca", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Kamera: $p2Verified/${p2Treatments.size} Teruji", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (p2Verified > 0) Color(0xFF2E7D32) else OrangePrimary)
                            }
                        }

                        // Pilar 3: Panen Terbaik
                        val isP3Selected = selectedFilterTab == 3
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isP3Selected) OrangePrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (isP3Selected) OrangePrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedFilterTab = 3 }
                        ) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(AppIcons.Chili, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(13.dp))
                                    Text("3. Panen", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                                }
                                Text(if (advisory.daysToHarvest > 0) "${advisory.daysToHarvest} Hari Lagi" else "Masa Panen", fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                Text("Merah > 85%", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Kamera: $p3Verified/${p3Treatments.size} Teruji", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (p3Verified > 0) Color(0xFF2E7D32) else Color(0xFFD32F2F))
                            }
                        }
                    }

                    // Progress Bar Kepatuhan Kamera Total Seluruh Kebun
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Kepatuhan Kamera Budidaya:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${totalCompliance.toInt()}% (${treatmentResults.size}/26 Terverifikasi)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                        }
                        LinearProgressIndicator(
                            progress = { (totalCompliance / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = OrangePrimary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }
        }

        // --- Card 3: Fitur Utama — Inspeksi Kamera Hasil Perawatan (Tampil di Tab 0, 2, atau 4) ---
        if (selectedFilterTab == 0 || selectedFilterTab == 2 || selectedFilterTab == 4) {
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
                                    "Pilih perawatan spesifik & foto bukti hasil perawatannya",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Selector: Pilih Perawatan yang Ingin Diverifikasi Kamera
                    Text(
                        "Pilih Perawatan untuk Diverifikasi Kamera:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val treatmentScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(treatmentScrollState),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activeStage.treatments.forEach { t ->
                            val isSel = selectedTreatment?.id == t.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) OrangePrimary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { selectedTreatment = t }
                            ) {
                                Text(
                                    text = t.name,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Detailed Target Subject & Criteria for Selected Treatment
                    val currentTargetSubject = selectedTreatment?.targetCameraSubject ?: activeStage.cameraProtocol.targetSubject
                    val currentCriteria = selectedTreatment?.visualSuccessCriteria ?: activeStage.cameraProtocol.successCriteria

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
                                    "Target Foto: $currentTargetSubject",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            selectedTreatment?.let {
                                Text(
                                    "Kategori: ${it.category} — ${it.description}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "Kriteria Visual Sukses: $currentCriteria",
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
                                selectedTreatment?.let {
                                    triggerTreatmentSimulation(it, TreatmentCameraVerifier.SampleCondition.OPTIMAL)
                                } ?: run {
                                    val sample = TreatmentCameraVerifier.createSimulatedSample(
                                        stage = activeStage,
                                        hst = currentHst,
                                        condition = TreatmentCameraVerifier.SampleCondition.OPTIMAL
                                    )
                                    latestInspection = sample
                                    inspectionHistory.add(0, sample)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Text("Uji Sehat", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                selectedTreatment?.let {
                                    triggerTreatmentSimulation(it, TreatmentCameraVerifier.SampleCondition.MILD_DEFECT)
                                } ?: run {
                                    val sample = TreatmentCameraVerifier.createSimulatedSample(
                                        stage = activeStage,
                                        hst = currentHst,
                                        condition = TreatmentCameraVerifier.SampleCondition.MILD_DEFECT
                                    )
                                    latestInspection = sample
                                    inspectionHistory.add(0, sample)
                                }
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
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
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
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(statusColor, CircleShape)
                                        )
                                        Column {
                                            Text(
                                                result.treatmentName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                result.statusTitle,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp,
                                                color = statusColor
                                            )
                                        }
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
                                        onClick = { saveInspectionToCalendar(result) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(AppIcons.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Simpan ke Kalender", fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { speakInspection(result) },
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
        }

        // --- Kartu Khusus: Panduan Cara Menanam & Olah Lahan Presisi (Tampil jika Tab == 1) ---
        if (selectedFilterTab == 1) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                border = BorderStroke(1.dp, Color(0xFF2E7D32))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(AppIcons.Terrain, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                        Text(
                            "Pilar 1: Panduan Presisi Cara Menanam Cabai DAPE",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20)
                        )
                    }
                    Text(
                        "1. Bedengan 40-50 cm: Memastikan perakaran tidak jenuh air dan mencegah layu bakteri Ralstonia.",
                        fontSize = 11.sp,
                        color = Color(0xFF1B5E20)
                    )
                    Text(
                        "2. Drainase Parit TWI < 7.5: Parit sedalam 50 cm menjamin air tuntas surut < 15 menit saat hujan lebat.",
                        fontSize = 11.sp,
                        color = Color(0xFF1B5E20)
                    )
                    Text(
                        "3. Mulsa Perak-Hitam: Memantulkan sinar UV mengusir thrips dan menstabilkan fluktuasi suhu tanah.",
                        fontSize = 11.sp,
                        color = Color(0xFF1B5E20)
                    )
                    Text(
                        "4. Jarak Tanam Staggered: 50 cm antar-lubang x 60 cm antar-baris pola segitiga zig-zag untuk aerasi kanopi.",
                        fontSize = 11.sp,
                        color = Color(0xFF1B5E20)
                    )
                    Text(
                        "5. Penanaman Sore Hari: Mengurangi stres transpirasi bibit, disertai inokulasi PGPR & mikoriza perakaran.",
                        fontSize = 11.sp,
                        color = Color(0xFF1B5E20)
                    )
                    OutlinedButton(
                        onClick = { onNavigateToTab?.invoke(1) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(AppIcons.Terrain, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Buka Manajemen Lahan & Petak", fontSize = 11.sp)
                    }
                }
            }
        }

        // --- Kartu Emas: Kunci Panen Mutu Terbaik & Pascapanen (Tampil jika Tab == 3) ---
        if (selectedFilterTab == 3) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, OrangePrimary)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(AppIcons.Chili, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(20.dp))
                        Text(
                            "Kunci Emas Panen Cabai Mutu Grade A+ Super",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = OrangePrimary
                        )
                    }

                    Text(
                        "1. Waktu Petik Terbaik: Pukul 07.00 - 10.00 pagi setelah embun mengering sempurna pada permukaan kulit buah cabai.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "2. Kriteria Buah Super: Kematangan merah > 85%, kulit mengkilap padat tanpa kerutan, tangkai hijau utuh 2-3 cm.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "3. Standar Sortasi Meja DAPE: Sortir objektif Grade A+ (panjang > 12 cm, diameter > 1.2 cm, merah merata 95%).",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "4. Termoterapi Asas Black 52°C: Rendam buah selama 180 detik pada air 52°C untuk inaktivasi spora laten antraknosa.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "5. Uji Apung Archimedes: Rasio tenggelam > 80% menjamin daging buah padat bernas dan tahan simpan > 14 hari.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedButton(
                        onClick = { onNavigateToTab?.invoke(3) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(AppIcons.Chili, contentDescription = null, modifier = Modifier.size(14.dp), tint = OrangePrimary)
                        Spacer(Modifier.width(6.dp))
                        Text("Buka Layar Panen & Grading DAPE", fontSize = 11.sp)
                    }
                }
            }
        }

        // --- Cockpit Verifikasi Kamera Seluruh Perawatan (Tampil jika Tab == 4) ---
        if (selectedFilterTab == 4) {
            Text(
                "Cockpit Kepatuhan Kamera: ${activeStage.title}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeStage.treatments.forEach { trt ->
                        val trtResult = treatmentResults[trt.id]
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (trtResult != null) AppIcons.CheckCircle else AppIcons.Warning,
                                        contentDescription = null,
                                        tint = if (trtResult != null) Color(0xFF2E7D32) else OrangePrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(trt.name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            if (trtResult != null) "Terverifikasi (${trtResult.healthScore.toInt()}%)" else "Belum Diverifikasi Kamera",
                                            fontSize = 10.sp,
                                            color = if (trtResult != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Button(
                                        onClick = { triggerTreatmentCamera(trt) },
                                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("Foto", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { triggerTreatmentSimulation(trt, TreatmentCameraVerifier.SampleCondition.OPTIMAL) },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Uji", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Card 4: 5-Tahap Panduan Lengkap Budidaya Cabai (Accordion) (Tampil jika Tab != 4) ---
        if (selectedFilterTab != 4) {
            Text(
                when (selectedFilterTab) {
                    1 -> "Panduan Khusus Cara Menanam & Persiapan Lahan"
                    2 -> "Panduan Khusus Cara Memelihara & Kamera Perawatan"
                    3 -> "Panduan Khusus Panen & Kualitas Terbaik"
                    else -> "Tahapan Lengkap Budidaya Cabai DAPE"
                },
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

                                // 1. Cara Menanam & Verifikasi Kamera Menanam (Tampil jika tab 0 atau 1)
                                if (selectedFilterTab == 0 || selectedFilterTab == 1) {
                                    SectionLabel("1. Cara Menanam & Persiapan Lahan")
                                    stage.plantingGuide.forEach { item ->
                                        BulletRow(item, MaterialTheme.colorScheme.onPrimaryContainer)
                                    }

                                    val plantingTreatments = stage.treatments.filter { it.pillar == ChiliGrowingGuide.PillarType.PLANTING }
                                    if (plantingTreatments.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Kamera Verifikasi Hasil Menanam & Olah Lahan:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OrangePrimary
                                        )
                                        plantingTreatments.forEach { trt ->
                                            TreatmentItemCard(
                                                trt = trt,
                                                trtResult = treatmentResults[trt.id],
                                                trtBitmap = treatmentBitmaps[trt.id],
                                                onTriggerCamera = { openCameraGuideDialog(it) },
                                                onTriggerSimulation = { t, cond -> triggerTreatmentSimulation(t, cond) },
                                                onSaveCalendar = { saveInspectionToCalendar(it) },
                                                onSpeak = { speakInspection(it) },
                                                isTtsSpeaking = isTtsSpeaking
                                            )
                                        }
                                    }
                                }

                                // 2. Cara Memelihara & Verifikasi Kamera Pemeliharaan (Tampil jika tab 0 atau 2)
                                if (selectedFilterTab == 0 || selectedFilterTab == 2) {
                                    SectionLabel("2. Cara Memelihara & Pemupukan Rutin")
                                    stage.maintenanceGuide.forEach { item ->
                                        BulletRow(item, MaterialTheme.colorScheme.onPrimaryContainer)
                                    }

                                    val maintenanceTreatments = stage.treatments.filter { it.pillar == ChiliGrowingGuide.PillarType.MAINTENANCE }
                                    if (maintenanceTreatments.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Kamera Verifikasi Hasil Pemeliharaan & SOP:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OrangePrimary
                                        )
                                        maintenanceTreatments.forEach { trt ->
                                            TreatmentItemCard(
                                                trt = trt,
                                                trtResult = treatmentResults[trt.id],
                                                trtBitmap = treatmentBitmaps[trt.id],
                                                onTriggerCamera = { openCameraGuideDialog(it) },
                                                onTriggerSimulation = { t, cond -> triggerTreatmentSimulation(t, cond) },
                                                onSaveCalendar = { saveInspectionToCalendar(it) },
                                                onSpeak = { speakInspection(it) },
                                                isTtsSpeaking = isTtsSpeaking
                                            )
                                        }
                                    }
                                }

                                // 3. Cara Panen Kualitas Terbaik & Verifikasi Kamera Panen (Tampil jika tab 0 atau 3)
                                if (selectedFilterTab == 0 || selectedFilterTab == 3) {
                                    SectionLabel("3. Panduan Panen & Kualitas Mutu Terbaik")
                                    stage.harvestGuide.forEach { item ->
                                        BulletRow(item, MaterialTheme.colorScheme.onPrimaryContainer)
                                    }

                                    val harvestTreatments = stage.treatments.filter { it.pillar == ChiliGrowingGuide.PillarType.HARVEST }
                                    if (harvestTreatments.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Kamera Verifikasi Hasil Panen & Pasca-Panen Mutu Terbaik:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OrangePrimary
                                        )
                                        harvestTreatments.forEach { trt ->
                                            TreatmentItemCard(
                                                trt = trt,
                                                trtResult = treatmentResults[trt.id],
                                                trtBitmap = treatmentBitmaps[trt.id],
                                                onTriggerCamera = { openCameraGuideDialog(it) },
                                                onTriggerSimulation = { t, cond -> triggerTreatmentSimulation(t, cond) },
                                                onSaveCalendar = { saveInspectionToCalendar(it) },
                                                onSpeak = { speakInspection(it) },
                                                isTtsSpeaking = isTtsSpeaking
                                            )
                                        }
                                    }
                                }

                                // 4. Protokol Kamera Hasil Perawatan
                                if (selectedFilterTab == 0) {
                                    SectionLabel("4. Protokol Kamera Standar Operasional")
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
                                                fontWeight = if (isChecked) FontWeight.Normal else FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            // Quick camera icon per checklist item
                                            IconButton(
                                                onClick = {
                                                    val dummyItem = ChiliGrowingGuide.TreatmentItem(
                                                        id = "CHK_${stage.index}",
                                                        name = chk,
                                                        category = "Checklist Tindakan",
                                                        description = "Verifikasi pelaksanaan SOP: $chk",
                                                        targetCameraSubject = "Bukti Visual: $chk",
                                                        visualSuccessCriteria = "Tindakan SOP terverifikasi selesai dan rapi"
                                                    )
                                                    triggerTreatmentCamera(dummyItem)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = AppIcons.Camera,
                                                    contentDescription = "Foto SOP",
                                                    tint = OrangePrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(recColor, CircleShape)
                                    )
                                    Column {
                                        Text(
                                            rec.treatmentName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "${rec.stageTitle} (${rec.hst} HST) · ${rec.statusTitle}",
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

@Composable
private fun TreatmentItemCard(
    trt: ChiliGrowingGuide.TreatmentItem,
    trtResult: TreatmentCameraVerifier.InspectionResult?,
    trtBitmap: Bitmap?,
    onTriggerCamera: (ChiliGrowingGuide.TreatmentItem) -> Unit,
    onTriggerSimulation: (ChiliGrowingGuide.TreatmentItem, TreatmentCameraVerifier.SampleCondition) -> Unit,
    onSaveCalendar: (TreatmentCameraVerifier.InspectionResult) -> Unit,
    onSpeak: (TreatmentCameraVerifier.InspectionResult) -> Unit,
    isTtsSpeaking: Boolean
) {
    val bColor = if (trtResult != null) Color(trtResult.status.badgeColorHex) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (trtResult != null) bColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Category & Status Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = OrangePrimary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "${trt.pillar.displayName} · ${trt.category}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrangePrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (trtResult != null) {
                    Surface(
                        color = bColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(bColor, CircleShape))
                            Text(
                                "Terverifikasi: ${trtResult.healthScore.toInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = bColor
                            )
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "Belum Diverifikasi Kamera",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                trt.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                trt.description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Visual Target, Criteria, Angle, and Lighting
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "Subjek Foto Kamera: ${trt.targetCameraSubject}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Kriteria Visual Sukses: ${trt.visualSuccessCriteria}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2E7D32)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Sudut: ${trt.shootingAngle}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Cahaya: ${trt.recommendedLighting}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Dedicated Per-Treatment Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { onTriggerCamera(trt) },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Foto Kamera HP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        onTriggerSimulation(trt, TreatmentCameraVerifier.SampleCondition.OPTIMAL)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(0.9f)
                ) {
                    Text("Uji Sehat", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = {
                        onTriggerSimulation(trt, TreatmentCameraVerifier.SampleCondition.MILD_DEFECT)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(0.9f)
                ) {
                    Text("Uji Gejala", fontSize = 11.sp)
                }
            }

            // INLINE INSPECTION RESULT (when verified)
            if (trtResult != null) {
                val resColor = Color(trtResult.status.badgeColorHex)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, resColor.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(modifier = Modifier.size(8.dp).background(resColor, CircleShape))
                                Text(
                                    trtResult.statusTitle,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = resColor
                                )
                            }
                            Text(
                                "Skor: ${trtResult.healthScore.toInt()}%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = resColor
                            )
                        }

                        // Captured Photo Preview
                        if (trtBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = trtBitmap.asImageBitmap(),
                                    contentDescription = "Foto Hasil Perawatan",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Metrics row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Klorofil: ${trtResult.greenPct}%", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                            Text("Merah: ${trtResult.redPct}%", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD32F2F))
                            Text("Klorosis: ${trtResult.yellowPct}%", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF57F17))
                            Text("Nekrosis: ${trtResult.defectPct}%", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = if (trtResult.defectPct > 5) Color.Red else Color.Gray)
                        }

                        // Diagnosis
                        Text(
                            "Diagnosis: ${trtResult.diagnosisText}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Prescriptive Action
                        Text(
                            "Tindakan SOP: ${trtResult.prescriptiveAction}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OrangePrimary
                        )

                        // Inline Actions: Save to Calendar & TTS Listen
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { onSaveCalendar(trtResult) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Icon(AppIcons.Save, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Simpan Kalender", fontSize = 10.sp)
                            }

                            OutlinedButton(
                                onClick = { onSpeak(trtResult) },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Icon(
                                    imageVector = if (isTtsSpeaking) AppIcons.Stop else AppIcons.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = OrangePrimary
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(if (isTtsSpeaking) "Stop" else "Dengarkan", fontSize = 10.sp)
                            }

                            OutlinedButton(
                                onClick = { onTriggerCamera(trt) },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                                modifier = Modifier.weight(0.8f)
                            ) {
                                Text("Foto Ulang", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

