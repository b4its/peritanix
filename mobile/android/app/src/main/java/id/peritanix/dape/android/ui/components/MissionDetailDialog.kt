package id.peritanix.dape.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.peritanix.dape.android.core.hardware.IndonesianTtsVoiceAdvisor
import id.peritanix.dape.android.core.missions.DailyMissions
import kotlinx.coroutines.delay

private val OrangePrimary = Color(0xFFE65100)
private val GreenSuccess = Color(0xFF2E7D32)
private val CrimsonWarning = Color(0xFFC2185B)
private val AmberSafety = Color(0xFFF57F17)
private val BlueTroubleshoot = Color(0xFF0277BD)

/**
 * Dialog Komprehensif Panduan SOP Misi Harian Kualitas Cabai Terbaik.
 *
 * Menyediakan panduan operasional mandiri:
 *  - 2 Mode Tampilan: Mode Tinjauan SOP Lengkap & Mode Panduan Lapangan Bertahap (Step-by-Step Wizard)
 *  - Checklist Kesiapan Kerja Mandiri (Pre-Flight Agronomi: APD, Cuaca, Alat, Air)
 *  - Checklist langkah kerja interaktif langkah-demi-langkah dengan audio per langkah & persistensi memori
 *  - Standar APD & Keselamatan Kerja (K3 Agronomi)
 *  - Kalkulator Takaran Tangki Sprayer Presisi & Luas Lahan (Preset Chips + Stepper +/- 1L & +/- 100m²)
 *  - Panduan Urutan Pencampuran Baku (Metode W-A-L-E-S & Uji Kompatibilitas Toples 5 Menit)
 *  - Stopwatch / Timer Operasional Terintegrasi untuk proses berdurasi
 *  - Waktu optimal, alat & bahan, pantangan fatal, dan verifikasi fisik
 *  - Solusi Kendala Lapangan (Troubleshooting) untuk situasi tak terduga di kebun
 *  - Tanda Kualitas Fisik yang Dihasilkan pada tanaman dan buah cabai
 *  - Pencatatan Catatan Lapangan & Observasi Kepatuhan GAP (Good Agricultural Practices)
 *  - Landasan ilmiah agro-fisika & biokimia tanaman
 *  - Pintasan instrumen hardware (Kamera, Barometer, Klinometer, Sensor Lab)
 *  - Narasi suara TTS Bahasa Indonesia offline-first
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionDetailDialog(
    mission: DailyMissions.Mission,
    isCompleted: Boolean,
    onToggleCompleted: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSpeak: (String) -> Unit = {},
    isTtsSpeaking: Boolean = false,
    onNavigateToTab: ((Int) -> Unit)? = null,
    initialWizardMode: Boolean = false,
    onSaveObservationNote: ((String) -> Unit)? = null
) {
    // Mode Switcher: false = Tinjauan SOP Penuh, true = Mode Panduan Lapangan Bertahap
    var isWizardMode by remember(mission.id, initialWizardMode) { mutableStateOf(initialWizardMode) }
    var currentWizardStep by remember(mission.id) { mutableStateOf(0) }

    // Pre-Flight Readiness Checklist
    val preFlightItems = remember(mission.id) { DailyMissions.getPreFlightChecklist(mission) }
    var checkedPreFlight by remember(mission.id) { mutableStateOf(setOf<Int>()) }

    // Interactive Step Progress (persisted in DailyMissions cache)
    var checkedSteps by remember(mission.id) {
        mutableStateOf(DailyMissions.getCheckedSteps(mission.id))
    }

    // Live Tank & Land Area Dosage Calculator (Presets + Stepper Custom)
    var selectedTankSize by remember(mission.id) { mutableStateOf(16) } // Standar knapsack: 16 Liter
    var selectedLandAreaM2 by remember(mission.id) { mutableStateOf(1000) } // Standar: 1.000 m² (0.1 Ha)
    var showMixingOrderGuide by remember(mission.id) { mutableStateOf(false) }

    // Observation notes & tags
    var observationNoteText by remember(mission.id) { mutableStateOf("") }
    var selectedNoteTags by remember(mission.id) { mutableStateOf(setOf<String>()) }
    val quickNoteTagOptions = listOf(
        "Cuaca Cerah Pagi",
        "Air Bersih pH 6.5",
        "Nozel Kabut Halus",
        "Semua Tanaman Terjangkau",
        "Dosis Sesuai SOP"
    )

    // Integrated Operational Countdown Timer
    val totalSeconds = remember(mission.id, mission.durationMinutes) { mission.durationMinutes * 60 }
    var remainingSeconds by remember(mission.id) { mutableStateOf(totalSeconds) }
    var isTimerRunning by remember(mission.id) { mutableStateOf(false) }

    LaunchedEffect(isTimerRunning, remainingSeconds) {
        if (isTimerRunning && remainingSeconds > 0) {
            delay(1000)
            remainingSeconds -= 1
            if (remainingSeconds == 0) {
                isTimerRunning = false
                onSpeak("Waktu operasional misi ${mission.title} telah selesai. Silakan periksa hasil fisik di kebun.")
            }
        }
    }

    // Context-aware hardware & screen shortcut
    val titleLower = mission.title.lowercase()
    val detailLower = mission.detail.lowercase()
    val (shortcutLabel, shortcutIcon, shortcutTab) = when {
        titleLower.contains("barometer") || detailLower.contains("barometer") || titleLower.contains("tekanan") ->
            Triple("Cek Barometer", AppIcons.WbSunny, 7)
        titleLower.contains("klinometer") || detailLower.contains("klinometer") || titleLower.contains("kemiringan") ->
            Triple("Klinometer", AppIcons.Compass, 6)
        titleLower.contains("ceptometer") || titleLower.contains("sensor hp") || detailLower.contains("sensor hp") || titleLower.contains("lux") ->
            Triple("Sensor Lab", AppIcons.Sensors, 10)
        titleLower.contains("cielab") || titleLower.contains("foto kamera") || titleLower.contains("kamera dape") || mission.category == DailyMissions.Category.INSPEKSI ->
            Triple("Buka Kamera Kualitas", AppIcons.Camera, 8)
        mission.category == DailyMissions.Category.PANEN ->
            Triple("Uji Panen Archimedes", AppIcons.Chili, 3)
        else ->
            Triple("Panduan Tanam", AppIcons.Book, 9)
    }

    fun completeMissionWithNotes() {
        val composedNote = buildString {
            if (selectedNoteTags.isNotEmpty()) {
                append(selectedNoteTags.joinToString(", "))
            }
            if (observationNoteText.isNotBlank()) {
                if (isNotEmpty()) append(". ")
                append(observationNoteText.trim())
            }
        }
        if (composedNote.isNotBlank()) {
            onSaveObservationNote?.invoke(composedNote)
        }
        DailyMissions.setAllStepsChecked(mission.id, mission.executionSteps.size, true)
        checkedSteps = mission.executionSteps.indices.toSet()
        onToggleCompleted(true)
        onSpeak("Luar biasa! Seluruh langkah operasional misi ${mission.title} telah selesai. Skor mutu tercatat.")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            mission.qualityTarget.title,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Bobot: ${mission.weight}x",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (mission.targetAmount > 1) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "${mission.targetAmount} Sampel",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                Text(mission.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text(mission.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Segmented Mode Switcher: Tinjauan SOP vs Mode Panduan Bertahap
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Surface(
                        color = if (!isWizardMode) OrangePrimary else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isWizardMode = false }
                    ) {
                        Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Tinjauan SOP Lengkap",
                                fontSize = 10.sp,
                                fontWeight = if (!isWizardMode) FontWeight.Bold else FontWeight.Normal,
                                color = if (!isWizardMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        color = if (isWizardMode) OrangePrimary else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isWizardMode = true }
                    ) {
                        Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Panduan Bertahap (Wizard)",
                                fontSize = 10.sp,
                                fontWeight = if (isWizardMode) FontWeight.Bold else FontWeight.Normal,
                                color = if (isWizardMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isWizardMode && mission.executionSteps.isNotEmpty()) {
                    // =========================================================================
                    // MODE PANDUAN LAPANGAN BERTAHAP (STEP-BY-STEP FIELD WIZARD)
                    // =========================================================================
                    val totalSteps = mission.executionSteps.size
                    val safeStepIndex = currentWizardStep.coerceIn(0, totalSteps - 1)
                    val activeStepText = mission.executionSteps[safeStepIndex]
                    val isStepChecked = safeStepIndex in checkedSteps

                    // Step indicator and progress bar
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Langkah ${safeStepIndex + 1} dari $totalSteps",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = OrangePrimary
                            )
                            Text(
                                "${checkedSteps.size}/$totalSteps Selesai",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (checkedSteps.size == totalSteps) GreenSuccess else OrangePrimary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (safeStepIndex + 1).toFloat() / totalSteps.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = OrangePrimary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    // Active Step Big Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isStepChecked) GreenSuccess.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = if (isStepChecked) GreenSuccess else OrangePrimary,
                                    shape = CircleShape,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isStepChecked) {
                                            Icon(AppIcons.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        } else {
                                            Text("${safeStepIndex + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }

                                FilledTonalButton(
                                    onClick = {
                                        val safety = mission.safetyGear.firstOrNull() ?: ""
                                        val script = IndonesianTtsVoiceAdvisor.composeStepScript(
                                            stepNumber = safeStepIndex + 1,
                                            totalSteps = totalSteps,
                                            stepText = activeStepText,
                                            safetyReminder = safety
                                        )
                                        onSpeak(script)
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(AppIcons.VolumeUp, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Dengarkan Langkah", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Text(
                                text = activeStepText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 19.sp
                            )

                            // Quick tip / APD reminder for this step
                            if (mission.safetyGear.isNotEmpty()) {
                                Surface(
                                    color = AmberSafety.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(AppIcons.Shield, contentDescription = null, tint = AmberSafety, modifier = Modifier.size(14.dp))
                                        Text(
                                            "APD: ${mission.safetyGear.joinToString(", ")}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            // Integrated timer if applicable
                            if (mission.durationMinutes > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(AppIcons.Timer, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(15.dp))
                                            val mins = remainingSeconds / 60
                                            val secs = remainingSeconds % 60
                                            Text(String.format("%02d:%02d", mins, secs), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                        FilledTonalButton(
                                            onClick = { isTimerRunning = !isTimerRunning },
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.height(24.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(if (isTimerRunning) "Jeda" else "Mulai", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Checkbox: Tandai Langkah Ini Selesai
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val newSet = if (isStepChecked) checkedSteps - safeStepIndex else checkedSteps + safeStepIndex
                                        checkedSteps = newSet
                                        DailyMissions.setStepChecked(mission.id, safeStepIndex, !isStepChecked)
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = isStepChecked,
                                    onCheckedChange = { checked ->
                                        val newSet = if (checked) checkedSteps + safeStepIndex else checkedSteps - safeStepIndex
                                        checkedSteps = newSet
                                        DailyMissions.setStepChecked(mission.id, safeStepIndex, checked)
                                    }
                                )
                                Text(
                                    if (isStepChecked) "Langkah ini sudah selesai dilaksanakan" else "Centang bila langkah ini sudah selesai",
                                    fontSize = 11.sp,
                                    fontWeight = if (isStepChecked) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isStepChecked) GreenSuccess else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Stepper Navigation Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (safeStepIndex > 0) currentWizardStep -= 1 },
                            enabled = safeStepIndex > 0,
                            modifier = Modifier.weight(1f).height(32.dp),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Sebelumnya", fontSize = 10.sp)
                        }

                        if (safeStepIndex < totalSteps - 1) {
                            Button(
                                onClick = {
                                    // Mark current step done if not already, then proceed
                                    val newSet = checkedSteps + safeStepIndex
                                    checkedSteps = newSet
                                    DailyMissions.setStepChecked(mission.id, safeStepIndex, true)
                                    currentWizardStep += 1
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Lanjut Langkah", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val newSet = checkedSteps + safeStepIndex
                                    checkedSteps = newSet
                                    DailyMissions.setStepChecked(mission.id, safeStepIndex, true)
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Tandai Tuntas", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    // Final Step / All Steps Completed Celebration Box
                    if (checkedSteps.size == totalSteps) {
                        Surface(
                            color = GreenSuccess.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(AppIcons.CheckCircle, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(18.dp))
                                    Text("Seluruh langkah SOP lapangan tuntas!", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenSuccess)
                                }

                                Text("Catatan Lapangan & Observasi Kepatuhan GAP (Opsional):", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)

                                // Quick Note Tags
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    quickNoteTagOptions.take(3).forEach { tag ->
                                        val isSelected = tag in selectedNoteTags
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedNoteTags = if (isSelected) selectedNoteTags - tag else selectedNoteTags + tag
                                            },
                                            label = { Text(tag, fontSize = 9.sp) },
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = observationNoteText,
                                    onValueChange = { observationNoteText = it },
                                    placeholder = { Text("Tambahkan catatan lapangan, kondisi tanaman, dll...", fontSize = 10.sp) },
                                    modifier = Modifier.fillMaxWidth().height(60.dp),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                                    maxLines = 2
                                )

                                Button(
                                    onClick = { completeMissionWithNotes() },
                                    modifier = Modifier.fillMaxWidth().height(34.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess, contentColor = Color.White)
                                ) {
                                    Icon(AppIcons.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Selesaikan Misi & Catat ke Jurnal (100%)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                } else {
                    // =========================================================================
                    // MODE TINJAUAN SOP LENGKAP
                    // =========================================================================

                    // Audio TTS in dialog
                    FilledTonalButton(
                        onClick = {
                            val sopScript = IndonesianTtsVoiceAdvisor.composeMissionSopScript(
                                title = mission.title,
                                qualityTarget = mission.qualityTarget.title,
                                howToExecute = mission.howToExecute,
                                targetMetric = mission.targetMetric,
                                optimalTiming = mission.optimalTiming,
                                executionSteps = mission.executionSteps,
                                commonMistakesToAvoid = mission.commonMistakesToAvoid,
                                safetyGear = mission.safetyGear,
                                durationMinutes = mission.durationMinutes,
                                troubleshootingTip = mission.troubleshootingTip,
                                qualitySigns = mission.qualitySigns
                            )
                            onSpeak(sopScript)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(34.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isTtsSpeaking) OrangePrimary else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (isTtsSpeaking) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(if (isTtsSpeaking) AppIcons.Stop else AppIcons.VolumeUp, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isTtsSpeaking) "Hentikan Narasi Suara" else "Dengarkan SOP Suara Lengkap", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // 1. Checklist Kesiapan Kerja (Pre-Flight Agronomi)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(AppIcons.Shield, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(15.dp))
                                    Text("Pemeriksaan Kesiapan Kerja (Pre-Flight):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(
                                    onClick = {
                                        val script = IndonesianTtsVoiceAdvisor.composePreFlightScript(mission.safetyGear, mission.title)
                                        onSpeak(script)
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(AppIcons.VolumeUp, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            preFlightItems.forEachIndexed { idx, item ->
                                val isChecked = idx in checkedPreFlight
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            checkedPreFlight = if (isChecked) checkedPreFlight - idx else checkedPreFlight + idx
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (isChecked) GreenSuccess else MaterialTheme.colorScheme.surface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isChecked) {
                                            Icon(AppIcons.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                    Text(
                                        item,
                                        fontSize = 10.sp,
                                        color = if (isChecked) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // 2. Waktu Pelaksanaan Optimal
                    if (mission.optimalTiming.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(AppIcons.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("Waktu & Kondisi Cuaca Optimal:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(mission.optimalTiming, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }

                    // 3. Standar APD & Keselamatan Kerja (K3 Agronomi)
                    if (mission.safetyGear.isNotEmpty()) {
                        Surface(
                            color = AmberSafety.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(AppIcons.Shield, contentDescription = null, tint = AmberSafety, modifier = Modifier.size(16.dp))
                                    Text("Standar APD & Keselamatan Kerja:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberSafety)
                                }
                                mission.safetyGear.forEach { gear ->
                                    Row(
                                        modifier = Modifier.padding(start = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(Modifier.size(5.dp).background(AmberSafety, CircleShape))
                                        Text(gear, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }

                    // 4. Kalkulator Takaran Tangki Sprayer Presisi & Luas Lahan (+ Stepper Custom)
                    if (mission.standardDosagePerLiter > 0.0) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(AppIcons.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Text("Kalkulator Takaran Tangki & Lahan Presisi:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(
                                        onClick = {
                                            val totalDose = (selectedTankSize * mission.standardDosagePerLiter).toInt().toString()
                                            val script = IndonesianTtsVoiceAdvisor.composeMixingGuideScript(
                                                tankLiters = selectedTankSize,
                                                dosePerTank = totalDose,
                                                unit = mission.dosageUnit
                                            )
                                            onSpeak(script)
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(AppIcons.VolumeUp, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Text(
                                    "Dosis baku agro-fisika: ${mission.standardDosagePerLiter} ${mission.dosageUnit} per 1 Liter air",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Tank selector chips + Stepper
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("1. Ukuran Tangki Semprot Petani:", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        FilledTonalButton(
                                            onClick = { if (selectedTankSize > 1) selectedTankSize -= 1 },
                                            modifier = Modifier.size(24.dp),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("-", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("${selectedTankSize}L", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = OrangePrimary)
                                        FilledTonalButton(
                                            onClick = { if (selectedTankSize < 30) selectedTankSize += 1 },
                                            modifier = Modifier.size(24.dp),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("+", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(5, 10, 14, 16, 20).forEach { tankLiters ->
                                        val isSelected = selectedTankSize == tankLiters
                                        Surface(
                                            color = if (isSelected) OrangePrimary else MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selectedTankSize = tankLiters }
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "${tankLiters}L",
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                // Land Area Selector Chips + Stepper
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("2. Luas Lahan Perkebunan Cabai:", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        FilledTonalButton(
                                            onClick = { if (selectedLandAreaM2 > 100) selectedLandAreaM2 -= 100 },
                                            modifier = Modifier.size(24.dp),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("-", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("${selectedLandAreaM2}m²", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = OrangePrimary)
                                        FilledTonalButton(
                                            onClick = { if (selectedLandAreaM2 < 20000) selectedLandAreaM2 += 100 },
                                            modifier = Modifier.size(24.dp),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("+", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        200 to "200m²",
                                        500 to "500m²",
                                        1000 to "0.1Ha",
                                        2500 to "0.25Ha",
                                        5000 to "0.5Ha",
                                        10000 to "1Ha"
                                    ).forEach { (areaM2, label) ->
                                        val isSelected = selectedLandAreaM2 == areaM2
                                        Surface(
                                            color = if (isSelected) OrangePrimary else MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selectedLandAreaM2 = areaM2 }
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    label,
                                                    fontSize = 9.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                // Dynamic Calculation Output Box: Per Tank & Total Land
                                val totalDosePerTank = (selectedTankSize * mission.standardDosagePerLiter)
                                val formattedDosePerTank = if (totalDosePerTank % 1.0 == 0.0) totalDosePerTank.toInt().toString() else String.format("%.1f", totalDosePerTank)

                                val estimatedWaterTotalLiters = maxOf(5, (selectedLandAreaM2 * 0.05).toInt()) // Standar 500 L/Ha
                                val estimatedTanksNeeded = (estimatedWaterTotalLiters + selectedTankSize - 1) / selectedTankSize
                                val totalDoseOverall = estimatedWaterTotalLiters * mission.standardDosagePerLiter
                                val formattedTotalOverall = if (totalDoseOverall % 1.0 == 0.0) totalDoseOverall.toInt().toString() else String.format("%.1f", totalDoseOverall)

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(
                                        color = OrangePrimary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(AppIcons.CheckCircle, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(14.dp))
                                            Text(
                                                "Per Tangki $selectedTankSize Liter: larutkan $formattedDosePerTank ${mission.dosageUnit} bahan ke dalam $selectedTankSize Liter air bersih.",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = OrangePrimary
                                            )
                                        }
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(AppIcons.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                            Text(
                                                "Kebutuhan Total Lahan ${selectedLandAreaM2} m²: siapkan ~$estimatedWaterTotalLiters L air (~$estimatedTanksNeeded tangki) dan timbang total $formattedTotalOverall ${mission.dosageUnit} bahan baku.",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                // Panduan Urutan Pencampuran Baku (Metode W-A-L-E-S & Jar Test)
                                OutlinedButton(
                                    onClick = { showMixingOrderGuide = !showMixingOrderGuide },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth().height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(AppIcons.Science, contentDescription = null, modifier = Modifier.size(12.dp), tint = OrangePrimary)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (showMixingOrderGuide) "Sembunyikan Urutan Pencampuran Tangki" else "Lihat Urutan Pencampuran Baku Tangki (Metode W-A-L-E-S)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OrangePrimary
                                    )
                                }

                                AnimatedVisibility(visible = showMixingOrderGuide) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("Urutan Pencampuran Baku Lapangan (W-A-L-E-S):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                                            DailyMissions.MixingGuide.walesSteps.forEach { step ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.Top,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Surface(
                                                        color = OrangePrimary,
                                                        shape = RoundedCornerShape(3.dp),
                                                        modifier = Modifier.size(16.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(step.code, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                        }
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(step.stageName, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        Text(step.instruction, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }

                                            Surface(
                                                color = AmberSafety.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    DailyMissions.MixingGuide.jarTestGuide,
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. Timer / Stopwatch Operasional Terintegrasi
                    if (mission.durationMinutes > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(AppIcons.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Text("Timer Pengerjaan (${mission.durationMinutes} Menit):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }

                                    val mins = remainingSeconds / 60
                                    val secs = remainingSeconds % 60
                                    Text(
                                        String.format("%02d:%02d", mins, secs),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (remainingSeconds == 0) GreenSuccess else OrangePrimary
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds.toFloat() else 0f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = OrangePrimary,
                                    trackColor = MaterialTheme.colorScheme.surface
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { isTimerRunning = !isTimerRunning },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f).height(28.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(if (isTimerRunning) AppIcons.Stop else AppIcons.VolumeUp, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isTimerRunning) "Jeda" else "Mulai Timer", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            isTimerRunning = false
                                            remainingSeconds = totalSeconds
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f).height(28.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Reset Timer", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 6. Peralatan & Bahan yang Diperlukan
                    if (mission.toolsAndMaterials.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(AppIcons.Layers, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(14.dp))
                                    Text("Peralatan & Bahan yang Diperlukan:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                mission.toolsAndMaterials.forEach { item ->
                                    Row(
                                        modifier = Modifier.padding(start = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(Modifier.size(5.dp).background(OrangePrimary, CircleShape))
                                        Text(item, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    // 7. Langkah Pengerjaan Terstruktur & Checklist Mandiri
                    if (mission.executionSteps.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(AppIcons.TrendingUp, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(14.dp))
                                        Text("Langkah Pengerjaan Mandiri:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "${checkedSteps.size}/${mission.executionSteps.size} Selesai",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (checkedSteps.size == mission.executionSteps.size) GreenSuccess else OrangePrimary
                                        )
                                        TextButton(
                                            onClick = {
                                                val allDone = checkedSteps.size == mission.executionSteps.size
                                                val newSet = if (allDone) emptySet() else mission.executionSteps.indices.toSet()
                                                checkedSteps = newSet
                                                DailyMissions.setAllStepsChecked(mission.id, mission.executionSteps.size, !allDone)
                                            },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                            modifier = Modifier.height(22.dp)
                                        ) {
                                            Text(
                                                if (checkedSteps.size == mission.executionSteps.size) "Batal Semua" else "Tandai Semua",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                mission.executionSteps.forEachIndexed { idx, step ->
                                    val stepDone = idx in checkedSteps
                                    Surface(
                                        color = if (stepDone) GreenSuccess.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val isCheckedNow = !stepDone
                                                val newSet = if (isCheckedNow) checkedSteps + idx else checkedSteps - idx
                                                checkedSteps = newSet
                                                DailyMissions.setStepChecked(mission.id, idx, isCheckedNow)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                color = if (stepDone) GreenSuccess else OrangePrimary,
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (stepDone) {
                                                        Icon(AppIcons.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                    } else {
                                                        Text("${idx + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    }
                                                }
                                            }
                                            Text(
                                                step,
                                                fontSize = 11.sp,
                                                fontWeight = if (stepDone) FontWeight.Medium else FontWeight.Normal,
                                                color = if (stepDone) GreenSuccess else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { onSpeak("Langkah ${idx + 1}, $step") },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(AppIcons.VolumeUp, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }

                                // Quick button to launch interactive step wizard
                                OutlinedButton(
                                    onClick = { isWizardMode = true },
                                    modifier = Modifier.fillMaxWidth().height(28.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(AppIcons.TrendingUp, contentDescription = null, modifier = Modifier.size(12.dp), tint = OrangePrimary)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Buka Mode Panduan Lapangan (Bertahap)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                                }

                                if (checkedSteps.size == mission.executionSteps.size && mission.executionSteps.isNotEmpty()) {
                                    Surface(
                                        color = GreenSuccess.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(AppIcons.CheckCircle, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(16.dp))
                                                Text("Semua langkah SOP selesai! Skor kualitas siap ditingkatkan.", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GreenSuccess)
                                            }
                                            if (!isCompleted) {
                                                Button(
                                                    onClick = { completeMissionWithNotes() },
                                                    shape = RoundedCornerShape(6.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess, contentColor = Color.White),
                                                    modifier = Modifier.fillMaxWidth().height(28.dp),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(AppIcons.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Tandai Misi Selesai Sekarang (100%)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 8. Pantangan & Kesalahan Fatal yang Harus Dihindari
                    if (mission.commonMistakesToAvoid.isNotEmpty()) {
                        Surface(
                            color = CrimsonWarning.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(AppIcons.Warning, contentDescription = null, tint = CrimsonWarning, modifier = Modifier.size(16.dp))
                                    Text("Pantangan & Kesalahan Fatal:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CrimsonWarning)
                                }
                                mission.commonMistakesToAvoid.forEach { mistake ->
                                    Row(
                                        modifier = Modifier.padding(start = 4.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("•", fontSize = 11.sp, color = CrimsonWarning, fontWeight = FontWeight.Bold)
                                        Text(mistake, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }

                    // 9. Solusi Kendala Lapangan (Troubleshooting)
                    if (mission.troubleshootingTip.isNotBlank()) {
                        Surface(
                            color = BlueTroubleshoot.copy(alpha = 0.09f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(AppIcons.Info, contentDescription = null, tint = BlueTroubleshoot, modifier = Modifier.size(16.dp))
                                    Text("Solusi Kendala Lapangan (Troubleshooting):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BlueTroubleshoot)
                                }
                                Text(mission.troubleshootingTip, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    // 10. Tanda Kualitas Fisik yang Dihasilkan pada Cabai
                    if (mission.qualitySigns.isNotBlank()) {
                        Surface(
                            color = GreenSuccess.copy(alpha = 0.09f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(AppIcons.Chili, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(16.dp))
                                    Text("Tanda Kualitas Fisik yang Dihasilkan:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenSuccess)
                                }
                                Text(mission.qualitySigns, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    // 11. Kriteria Verifikasi Keberhasilan Fisik & Metrik Target
                    if (mission.successVerification.isNotBlank() || mission.targetMetric.isNotBlank()) {
                        Surface(
                            color = GreenSuccess.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(AppIcons.CheckCircle, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(16.dp))
                                    Text("Kriteria Verifikasi Keberhasilan:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenSuccess)
                                }
                                if (mission.successVerification.isNotBlank()) {
                                    Text(mission.successVerification, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                if (mission.targetMetric.isNotBlank()) {
                                    Text("Target Parameter: ${mission.targetMetric}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = GreenSuccess)
                                }
                            }
                        }
                    }

                    // 12. Landasan Ilmiah Agro-Fisika
                    if (mission.scientificBasis.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(AppIcons.Science, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(14.dp))
                                    Text("Landasan Ilmiah Agro-Fisika:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                                }
                                Text(mission.scientificBasis, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // 13. Context-aware Hardware/Screen Shortcut Button
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onNavigateToTab?.invoke(shortcutTab)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(shortcutIcon, contentDescription = null, modifier = Modifier.size(14.dp), tint = OrangePrimary)
                        Spacer(Modifier.width(6.dp))
                        Text(shortcutLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (!isCompleted) {
                            completeMissionWithNotes()
                        } else {
                            DailyMissions.setAllStepsChecked(mission.id, mission.executionSteps.size, false)
                            checkedSteps = emptySet()
                            onToggleCompleted(false)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompleted) MaterialTheme.colorScheme.surfaceVariant else OrangePrimary,
                        contentColor = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                    )
                ) {
                    Icon(if (isCompleted) AppIcons.Close else AppIcons.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isCompleted) "Batal Selesai" else "Tandai Selesai")
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("Tutup")
                }
            }
        }
    )
}
