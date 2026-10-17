package id.peritanix.dape.android.ui

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.peritanix.dape.android.core.hardware.IndonesianTtsVoiceAdvisor
import id.peritanix.dape.android.core.missions.DailyMissions
import id.peritanix.dape.android.data.SyncQueueStore
import id.peritanix.dape.android.ui.components.AppIcons
import id.peritanix.dape.android.ui.components.MissionDetailDialog
import id.peritanix.dape.android.ui.components.NotificationType
import id.peritanix.dape.android.ui.components.PentasixNotificationHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val OrangePrimary = Color(0xFFE65100)
private val GreenSuccess = Color(0xFF2E7D32)
private val BlueWater = Color(0xFF1565C0)
private val PurpleInspect = Color(0xFF6A1B9A)
private val CrimsonSanitation = Color(0xFFC2185B)

/**
 * Misi Harian Kualitas Cabai Terbaik (Grade A+ Ekspor).
 *
 * Menampilkan misi-misi harian deterministik per HST dan fase pertumbuhan tanaman,
 * berorientasi langsung pada 5 pilar mutu cabai:
 *  - Ketebalan pericarp (>1.8 mm, Ca/B/Si)
 *  - Kepedasan & konsentrasi kapsaisin (SHU >80.000)
 *  - Warna merah cerah mengkilap (Kapsantin >85%)
 *  - 0% patogen & antraknosa (sanitasi presisi)
 *  - Panen turgor prima pagi hari & sterilisasi 52°C.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyMissionsScreen(
    queue: SyncQueueStore? = null,
    onNavigateToTab: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val epochDay = remember { today.toEpochDay() }

    var hst by remember { mutableStateOf(45) } // Demo default: fase pembungaan / ovarium buah
    var completed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var riskBoost by remember { mutableStateOf(0.0) }
    var selectedTargetFilter by remember { mutableStateOf<DailyMissions.QualityTarget?>(null) }
    var selectedStatusFilter by remember { mutableStateOf("Semua") } // "Semua", "Belum", "Selesai"
    var searchQuery by remember { mutableStateOf("") }
    var expandedMissionId by remember { mutableStateOf<String?>(null) }
    var showQualityStandardDialog by remember { mutableStateOf(false) }
    var showGlossaryDialog by remember { mutableStateOf(false) }
    var feedbackToastText by remember { mutableStateOf<String?>(null) }
    var selectedMissionForDetailDialog by remember { mutableStateOf<DailyMissions.Mission?>(null) }
    var startInWizardMode by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    // Text to Speech Engine for farmer voice guidance in the field
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

    fun speak(text: String, utteranceId: String = "MISSION_TTS") {
        if (isTtsSpeaking) {
            ttsInstance?.stop()
            isTtsSpeaking = false
        } else {
            ttsInstance?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            isTtsSpeaking = true
        }
    }

    val plan = remember(hst, riskBoost) { DailyMissions.planFor(today, hst, riskBoost) }
    val score = DailyMissions.dailyQualityScore(plan, completed)
    val grade = DailyMissions.scoreGrade(score)
    val exportBadge = DailyMissions.exportGradeBadge(score)
    val pillarProgress = remember(plan, completed) { DailyMissions.getPillarProgress(plan, completed) }
    val filteredMissions = remember(plan, selectedTargetFilter, selectedStatusFilter, searchQuery, completed) {
        plan.missions.filter { mission ->
            val matchTarget = selectedTargetFilter == null || mission.qualityTarget == selectedTargetFilter
            val matchStatus = when (selectedStatusFilter) {
                "Belum" -> mission.id !in completed
                "Selesai" -> mission.id in completed
                else -> true
            }
            val q = searchQuery.trim().lowercase()
            val matchSearch = q.isEmpty() ||
                mission.title.lowercase().contains(q) ||
                mission.detail.lowercase().contains(q) ||
                mission.qualityImpact.lowercase().contains(q) ||
                mission.howToExecute.lowercase().contains(q) ||
                mission.executionSteps.any { it.lowercase().contains(q) } ||
                mission.toolsAndMaterials.any { it.lowercase().contains(q) } ||
                mission.safetyGear.any { it.lowercase().contains(q) }
            matchTarget && matchStatus && matchSearch
        }
    }

    fun reload() {
        scope.launch(Dispatchers.IO) {
            val c = queue?.completedMissionsSuspend(epochDay) ?: emptySet()
            withContext(Dispatchers.Main.immediate) { completed = c }
        }
    }
    LaunchedEffect(epochDay) { reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(OrangePrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Misi Harian Mutu Terbaik",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${plan.stageTitle} · ${plan.phaseName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Audio Briefing TTS Button
            FilledTonalButton(
                onClick = {
                    val briefingScript = IndonesianTtsVoiceAdvisor.composeDailyMissionsBriefingScript(
                        stageTitle = plan.stageTitle,
                        phaseName = plan.phaseName,
                        hst = hst,
                        score = score,
                        exportBadge = exportBadge,
                        totalMissions = plan.missions.size,
                        completedMissions = completed.count { id -> plan.missions.any { it.id == id } },
                        nextFocusTitle = DailyMissions.nextUnfinished(plan, completed)?.title
                    )
                    speak(briefingScript, "BRIEFING_TTS")
                },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isTtsSpeaking) OrangePrimary else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isTtsSpeaking) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    if (isTtsSpeaking) AppIcons.Stop else AppIcons.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isTtsSpeaking) "Stop" else "Audio", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = { showQualityStandardDialog = true },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(AppIcons.Science, contentDescription = null, modifier = Modifier.size(13.dp), tint = OrangePrimary)
                Spacer(Modifier.width(3.dp))
                Text("Standar", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = { showGlossaryDialog = true },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(AppIcons.Book, contentDescription = null, modifier = Modifier.size(13.dp), tint = OrangePrimary)
                Spacer(Modifier.width(3.dp))
                Text("Kamus", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Feedback Banner (e.g. copied GAP log)
        AnimatedVisibility(visible = feedbackToastText != null) {
            Surface(
                color = GreenSuccess,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(AppIcons.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Text(feedbackToastText ?: "", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { feedbackToastText = null }, modifier = Modifier.size(20.dp)) {
                        Icon(AppIcons.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // HST Control & Phase Presets
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Umur Tanaman: $hst HST (${plan.phaseName})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "Tahap ${plan.stageIndex}/5",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Slider(
                    value = hst.toFloat(),
                    onValueChange = { hst = it.toInt() },
                    valueRange = -21f..120f
                )

                // Quick phase presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val presets = listOf(
                        Pair("Semai", -10),
                        Pair("Vegetatif", 20),
                        Pair("Bunga", 45),
                        Pair("Buah", 65),
                        Pair("Panen", 95)
                    )
                    presets.forEach { (label, targetHst) ->
                        val isSelected = (plan.stageIndex == 1 && targetHst == -10) ||
                            (plan.stageIndex == 2 && targetHst == 20) ||
                            (plan.stageIndex == 3 && targetHst == 45) ||
                            (plan.stageIndex == 4 && targetHst == 65) ||
                            (plan.stageIndex == 5 && targetHst == 95)

                        FilledTonalButton(
                            onClick = { hst = targetHst },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isSelected) OrangePrimary else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
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

                // Defensive EWS Risk Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(AppIcons.Shield, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(16.dp))
                    Text("Risiko EWS Jamur/Antraknosa:", style = MaterialTheme.typography.labelSmall)
                    Checkbox(
                        checked = riskBoost >= 0.5,
                        onCheckedChange = { riskBoost = if (it) 0.8 else 0.0 },
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        if (riskBoost >= 0.5) "Aktif (Risiko 80%)" else "Normal",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (riskBoost >= 0.5) FontWeight.Bold else FontWeight.Normal,
                        color = if (riskBoost >= 0.5) Color(0xFFC2185B) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Quality Scorecard & Export Grade
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    score >= 90 -> Color(0xFF1B5E20)
                    score >= 70 -> Color(0xFF2E7D32)
                    score >= 40 -> Color(0xFFE65100)
                    else -> Color(0xFFC2185B)
                }
            )
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Skor Kualitas Harian",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Text(
                            exportBadge,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                    Text(
                        "$score%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                LinearProgressIndicator(
                    progress = { score / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )

                Text(
                    grade,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.95f)
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)

                // 5-Pillar Live Achievement Matrix
                Text(
                    "Pencapaian Pilar Mutu Cabai Hari Ini:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    pillarProgress.entries.take(4).forEach { (target, counts) ->
                        val (done, total) = counts
                        val isComplete = done == total && total > 0
                        Surface(
                            color = if (isComplete) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    target.title.split(" ").first(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Text(
                                    "$done/$total",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isComplete) Color(0xFFC8E6C9) else Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Focus Next Unfinished Mission
        DailyMissions.nextUnfinished(plan, completed)?.let { next ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedMissionForDetailDialog = next },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.TrendingUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "FOKUS PRIORITAS KUALITAS HARI INI",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "Buka SOP ➜",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = OrangePrimary
                            )
                        }
                        Text(
                            next.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            next.qualityImpact,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        // Search Bar & Filter Controls
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari misi, langkah SOP, alat, bahan, atau APD...", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(AppIcons.Search, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(AppIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OrangePrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Status Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val notDoneCount = plan.missions.count { it.id !in completed }
                val doneCount = plan.missions.count { it.id in completed }
                listOf(
                    "Semua" to "Semua (${plan.missions.size})",
                    "Belum" to "Belum Selesai ($notDoneCount)",
                    "Selesai" to "Selesai ($doneCount)"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedStatusFilter == key,
                        onClick = { selectedStatusFilter = key },
                        label = { Text(label, fontSize = 11.sp, fontWeight = if (selectedStatusFilter == key) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            // Sub-filter tabs by Quality Target
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Pilar Kualitas (${filteredMissions.size} Misi Ditampilkan)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (selectedTargetFilter != null || selectedStatusFilter != "Semua" || searchQuery.isNotBlank()) {
                    TextButton(
                        onClick = {
                            selectedTargetFilter = null
                            selectedStatusFilter = "Semua"
                            searchQuery = ""
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("Reset Semua", fontSize = 11.sp, color = OrangePrimary)
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedTargetFilter == null,
                        onClick = { selectedTargetFilter = null },
                        label = { Text("Semua Pilar", fontSize = 11.sp) }
                    )
                }
                val targetsWithMissions = plan.missions.map { it.qualityTarget }.distinct()
                items(targetsWithMissions) { target ->
                    val count = plan.missions.count { it.qualityTarget == target }
                    val done = plan.missions.count { it.qualityTarget == target && it.id in completed }
                    FilterChip(
                        selected = selectedTargetFilter == target,
                        onClick = {
                            selectedTargetFilter = if (selectedTargetFilter == target) null else target
                        },
                        label = {
                            Text(
                                "${target.title.split(" ").first()} ($done/$count)",
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }
        }

        // Mission Cards
        filteredMissions.forEach { mission ->
            val done = mission.id in completed
            val isExpanded = expandedMissionId == mission.id

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (done) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (done) 0.dp else 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked = done,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    queue?.markMissionDone(epochDay, mission.id)
                                    PentasixNotificationHub.notify(
                                        context = context,
                                        title = "Misi Mutu Selesai",
                                        message = "${mission.title} — skor mutu cabai meningkat.",
                                        type = NotificationType.SUCCESS,
                                        postSystemNotification = false
                                    )
                                } else {
                                    queue?.unmarkMissionDone(epochDay, mission.id)
                                }
                                completed = if (checked) completed + mission.id else completed - mission.id
                                scope.launch {
                                    kotlinx.coroutines.delay(120)
                                    reload()
                                }
                            }
                        )

                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Category & Quality Pillar Badges
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CategoryDot(mission.category)
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        mission.qualityTarget.title,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                if (mission.targetAmount > 1) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "Target: ${mission.targetAmount} sampel",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Title
                            Text(
                                mission.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )

                            // Detail text
                            Text(
                                mission.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Quality Impact Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    AppIcons.TrendingUp,
                                    contentDescription = null,
                                    tint = OrangePrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    mission.qualityImpact,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OrangePrimary
                                )
                            }

                            // Target metric pill
                            if (mission.targetMetric.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            AppIcons.Shield,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            "Metrik: ${mission.targetMetric}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Action buttons: Ringkasan SOP, Panduan Bertahap (Wizard), SOP Lengkap & Pintasan Hardware
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    expandedMissionId = if (isExpanded) null else mission.id
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp).weight(1f)
                            ) {
                                Icon(
                                    if (isExpanded) AppIcons.Close else AppIcons.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    if (isExpanded) "Tutup" else "Ringkasan",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    startInWizardMode = true
                                    selectedMissionForDetailDialog = mission
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp).weight(1.1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = OrangePrimary.copy(alpha = 0.15f),
                                    contentColor = OrangePrimary
                                )
                            ) {
                                Icon(AppIcons.TrendingUp, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Bertahap", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = {
                                    startInWizardMode = false
                                    selectedMissionForDetailDialog = mission
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp).weight(1f)
                            ) {
                                Icon(AppIcons.Book, contentDescription = null, modifier = Modifier.size(12.dp), tint = OrangePrimary)
                                Spacer(Modifier.width(3.dp))
                                Text("Lengkap", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Context-aware shortcuts for mobile sensors & agro-physics tools
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
                                Triple("Buka Kamera", AppIcons.Camera, 8)
                            mission.category == DailyMissions.Category.PANEN ->
                                Triple("Uji Panen", AppIcons.Chili, 3)
                            else ->
                                Triple("Panduan Tanam", AppIcons.Book, 9)
                        }

                        OutlinedButton(
                            onClick = { onNavigateToTab?.invoke(shortcutTab) },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.fillMaxWidth().height(26.dp)
                        ) {
                            Icon(shortcutIcon, contentDescription = null, modifier = Modifier.size(12.dp), tint = OrangePrimary)
                            Spacer(Modifier.width(4.dp))
                            Text(shortcutLabel, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Expandable SOP Details with Rich Structured Guidance
                    AnimatedVisibility(visible = isExpanded) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Spoken SOP audio playback row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "PANDUAN OPERASIONAL LAPANGAN",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

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
                                            speak(sopScript, "SOP_${mission.id}")
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Icon(AppIcons.VolumeUp, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Dengarkan SOP Suara", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                // Pre-Flight Agronomi & Kesiapan Kerja Mandiri
                                val preFlightChecklist = remember(mission.id) { DailyMissions.getPreFlightChecklist(mission) }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(AppIcons.Shield, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(13.dp))
                                            Text("Kesiapan Kerja Mandiri (Pre-Flight):", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        preFlightChecklist.forEach { item ->
                                            Row(
                                                modifier = Modifier.padding(start = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Box(Modifier.size(4.dp).background(OrangePrimary, CircleShape))
                                                Text(item, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }

                                // 1. Waktu Pelaksanaan Optimal
                                if (mission.optimalTiming.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(AppIcons.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Column {
                                                Text("Waktu Pelaksanaan Optimal:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                Text(mission.optimalTiming, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            }
                                        }
                                    }
                                }

                                // Standar APD & Keselamatan Kerja
                                if (mission.safetyGear.isNotEmpty()) {
                                    Surface(
                                        color = Color(0xFFF57F17).copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(AppIcons.Shield, contentDescription = null, tint = Color(0xFFF57F17), modifier = Modifier.size(14.dp))
                                                Text("Standar APD & Keselamatan Kerja (K3):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF57F17))
                                            }
                                            mission.safetyGear.forEach { gear ->
                                                Row(
                                                    modifier = Modifier.padding(start = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(Modifier.size(4.dp).background(Color(0xFFF57F17), CircleShape))
                                                    Text(gear, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Takaran Tangki Sprayer & Durasi Kerja
                                if (mission.standardDosagePerLiter > 0.0 || mission.durationMinutes > 0) {
                                    Surface(
                                        color = BlueWater.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (mission.standardDosagePerLiter > 0.0) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Icon(AppIcons.WaterDrop, contentDescription = null, tint = BlueWater, modifier = Modifier.size(13.dp))
                                                    Text(
                                                        "Takaran Larutan: ${mission.standardDosagePerLiter} ${mission.dosageUnit}/L (Tangki 16L = ${"%.1f".format(mission.standardDosagePerLiter * 16)} ${mission.dosageUnit})",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = BlueWater
                                                    )
                                                }
                                            }
                                            if (mission.durationMinutes > 0) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Icon(AppIcons.Timer, contentDescription = null, tint = BlueWater, modifier = Modifier.size(13.dp))
                                                    Text(
                                                        "Estimasi Waktu Pengerjaan: ${mission.durationMinutes} Menit Operasional",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = BlueWater
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. Peralatan & Bahan yang Diperlukan
                                if (mission.toolsAndMaterials.isNotEmpty()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(AppIcons.Layers, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(14.dp))
                                                Text("Peralatan & Bahan yang Diperlukan:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                            mission.toolsAndMaterials.forEach { item ->
                                                Row(
                                                    modifier = Modifier.padding(start = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(Modifier.size(4.dp).background(OrangePrimary, CircleShape))
                                                    Text(item, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3. Langkah Pengerjaan Terstruktur
                                if (mission.executionSteps.isNotEmpty()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(AppIcons.TrendingUp, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(14.dp))
                                                Text("Langkah Pengerjaan Terstruktur:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                            mission.executionSteps.forEachIndexed { idx, step ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                    verticalAlignment = Alignment.Top,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Surface(
                                                        color = OrangePrimary,
                                                        shape = RoundedCornerShape(4.dp),
                                                        modifier = Modifier.size(18.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text("${idx + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                        }
                                                    }
                                                    Text(step, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                } else if (mission.howToExecute.isNotBlank()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Langkah Eksekusi SOP:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text(mission.howToExecute, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }

                                // 4. Pantangan & Kesalahan Fatal
                                if (mission.commonMistakesToAvoid.isNotEmpty()) {
                                    Surface(
                                        color = Color(0xFFC2185B).copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(AppIcons.Warning, contentDescription = null, tint = Color(0xFFC2185B), modifier = Modifier.size(15.dp))
                                                Text("Pantangan & Kesalahan yang Harus Dihindari:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2185B))
                                            }
                                            mission.commonMistakesToAvoid.forEach { mistake ->
                                                Row(
                                                    modifier = Modifier.padding(start = 4.dp),
                                                    verticalAlignment = Alignment.Top,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text("•", fontSize = 11.sp, color = Color(0xFFC2185B), fontWeight = FontWeight.Bold)
                                                    Text(mistake, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                            }
                                        }
                                    }
                                }

                                // 5. Solusi Kendala Lapangan (Troubleshooting)
                                if (mission.troubleshootingTip.isNotBlank()) {
                                    Surface(
                                        color = BlueWater.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(AppIcons.Info, contentDescription = null, tint = BlueWater, modifier = Modifier.size(14.dp))
                                                Text("Solusi Kendala Lapangan (Troubleshooting):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BlueWater)
                                            }
                                            Text(mission.troubleshootingTip, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }

                                // 6. Tanda Kualitas Fisik yang Dihasilkan
                                if (mission.qualitySigns.isNotBlank()) {
                                    Surface(
                                        color = GreenSuccess.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(AppIcons.Chili, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(14.dp))
                                                Text("Tanda Kualitas Fisik yang Dihasilkan:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GreenSuccess)
                                            }
                                            Text(mission.qualitySigns, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }

                                // 5. Verifikasi Keberhasilan Fisik & Metrik
                                if (mission.successVerification.isNotBlank() || mission.targetMetric.isNotBlank()) {
                                    Surface(
                                        color = GreenSuccess.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(AppIcons.CheckCircle, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(15.dp))
                                                Text("Verifikasi Keberhasilan Fisik Lapangan:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GreenSuccess)
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

                                // 6. Landasan Ilmiah Agro-Fisika
                                if (mission.scientificBasis.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(AppIcons.Science, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(13.dp))
                                                Text("Landasan Ilmiah Agro-Fisika:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                                            }
                                            Text(mission.scientificBasis, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                // 7. Matriks Ambang Batas Cuaca & Jam Operasional
                                val weatherDecision = DailyMissions.getWeatherDecision(mission)
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(AppIcons.Timer, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(13.dp))
                                            Text("Ambang Batas Cuaca & Jam Kritis:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                                        }
                                        Text("Ideal: ${weatherDecision.idealWindow} (${weatherDecision.idealTempRange}, RH ${weatherDecision.idealHumidity})", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Tunda Jika: ${weatherDecision.delayCondition}", fontSize = 10.sp, color = Color(0xFFC2185B), fontWeight = FontWeight.Medium)
                                        Text("Tindakan: ${weatherDecision.contingencyAction}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                // 8. Kiat Ahli Agronomi (Pro-Tip)
                                val proTips = DailyMissions.getAgronomistProTips(mission)
                                if (proTips.isNotEmpty()) {
                                    Surface(
                                        color = Color(0xFFFF8F00).copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(AppIcons.Science, contentDescription = null, tint = Color(0xFFFF8F00), modifier = Modifier.size(13.dp))
                                                Text("Kiat Ahli Agronomi (Pro-Tip):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF8F00))
                                            }
                                            Text(proTips.first(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }

                                // 9. Titik Kendali Mutu Akhir (Quality Gate)
                                val checkpoints = DailyMissions.getQualityGateCheckpoints(mission)
                                Surface(
                                    color = Color(0xFF6A1B9A).copy(alpha = 0.06f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(AppIcons.Shield, contentDescription = null, tint = Color(0xFF6A1B9A), modifier = Modifier.size(13.dp))
                                            Text("Titik Kendali Mutu Akhir (Quality Gate):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6A1B9A))
                                        }
                                        checkpoints.forEach { cp ->
                                            Row(
                                                modifier = Modifier.padding(start = 4.dp),
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text("•", fontSize = 10.sp, color = Color(0xFF6A1B9A), fontWeight = FontWeight.Bold)
                                                Text(cp, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                    }
                                }

                                // Open Dialog Options & Quick GAP Copy
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            startInWizardMode = true
                                            selectedMissionForDetailDialog = mission
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f).height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = OrangePrimary.copy(alpha = 0.15f),
                                            contentColor = OrangePrimary
                                        )
                                    ) {
                                        Icon(AppIcons.TrendingUp, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("Panduan Bertahap", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            startInWizardMode = false
                                            selectedMissionForDetailDialog = mission
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f).height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(AppIcons.Book, contentDescription = null, modifier = Modifier.size(12.dp), tint = OrangePrimary)
                                        Spacer(Modifier.width(3.dp))
                                        Text("SOP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val log = DailyMissions.formatGapWorkLog(
                                                mission = mission,
                                                date = today,
                                                checkedStepsCount = if (mission.id in completed) mission.executionSteps.size else 0,
                                                checkedQualityGateCount = if (mission.id in completed) DailyMissions.getQualityGateCheckpoints(mission).size else 0
                                            )
                                            clipboardManager.setText(AnnotatedString(log))
                                            feedbackToastText = "Catatan GAP \"${mission.title}\" disalin!"
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(AppIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp), tint = OrangePrimary)
                                        Spacer(Modifier.width(3.dp))
                                        Text("Salin GAP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quality Principles Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(AppIcons.Science, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(16.dp))
                    Text("Sinergi Kualitas Cabai DAPE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text(
                    "Misi harian ini didesain agar petani menghasilkan cabai mutu terbaik: dinding pericarp tebal (>1.8 mm) via Ca/B/Si, rasa pedas tajam via defisit air terukur, kilap merah merata via Kalium/Magnesium, bebas cacat berkat sanitasi EWS, dan kesegaran maksimal saat petik pagi 07:00-10:00 WIB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Modal Dialog: Standar Kualitas Cabai Grade A+ Ekspor
    if (showQualityStandardDialog) {
        AlertDialog(
            onDismissRequest = { showQualityStandardDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(AppIcons.Chili, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(24.dp))
                    Text("Standar Mutu Cabai Grade A+", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Kriteria Fisik & Kimia Cabai Kualitas Terbaik:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    QualityMetricItem("Tebal Dinding Buah (Pericarp)", "> 1,8 mm (tahan simpan >14 hari)")
                    QualityMetricItem("Tingkat Kepedasan", "> 80.000 SHU (Scoville Heat Units)")
                    QualityMetricItem("Keseragaman Warna", "Merah cerah mengkilap > 85% (CIELAB a* > 35)")
                    QualityMetricItem("Keutuhan Fisik", "Tangkai utuh 2-3 cm, 0% busuk/antraknosa")
                    QualityMetricItem("Kepadatan & Rongga", "Uji apung Archimedes >80% tenggelam")
                    QualityMetricItem("Sterilisasi Spora Laten", "Termoterapi air hangat 52°C selama 3 menit")
                    QualityMetricItem("Pengemasan Pascapanen", "Krat berventilasi maks 15 kg di ruang teduh")
                }
            },
            confirmButton = {
                Button(
                    onClick = { showQualityStandardDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    Text("Tutup", color = Color.White)
                }
            }
        )
    }

    // Modal Dialog: Detail Panduan Lengkap SOP Misi
    if (selectedMissionForDetailDialog != null) {
        val detailMission = selectedMissionForDetailDialog!!
        val isMissionDone = detailMission.id in completed

        MissionDetailDialog(
            mission = detailMission,
            isCompleted = isMissionDone,
            initialWizardMode = startInWizardMode,
            onSaveObservationNote = { note ->
                scope.launch {
                    queue?.addActivitySuspend(
                        epochDay = epochDay,
                        kind = "MISI_MUTU",
                        title = "${detailMission.title} (Selesai)",
                        notes = note,
                        landId = "Lahan Cabai"
                    )
                }
            },
            onToggleCompleted = { checked ->
                if (checked) {
                    queue?.markMissionDone(epochDay, detailMission.id)
                    PentasixNotificationHub.notify(
                        context = context,
                        title = "Misi Mutu Selesai",
                        message = "${detailMission.title} — skor mutu cabai meningkat.",
                        type = NotificationType.SUCCESS,
                        postSystemNotification = false
                    )
                } else {
                    queue?.unmarkMissionDone(epochDay, detailMission.id)
                }
                completed = if (checked) completed + detailMission.id else completed - detailMission.id
                scope.launch {
                    kotlinx.coroutines.delay(120)
                    reload()
                }
            },
            onDismiss = { selectedMissionForDetailDialog = null },
            onSpeak = { text -> speak(text, "SOP_DIALOG_${detailMission.id}") },
            isTtsSpeaking = isTtsSpeaking,
            onNavigateToTab = onNavigateToTab
        )
    }

    // Modal Dialog: Kamus Istilah Agronomi Praktis GAP Cabai
    if (showGlossaryDialog) {
        AlertDialog(
            onDismissRequest = { showGlossaryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(AppIcons.Book, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(24.dp))
                    Text("Kamus Istilah Agronomi GAP Cabai", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "10 Istilah Standar Operasional GAP Budidaya Cabai Grade A+ Ekspor:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DailyMissions.AgronomyGlossary.entries.forEach { entry ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(entry.term, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                                Text(entry.definition, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "Manfaat Mutu: ${entry.practicalSignificance}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showGlossaryDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary, contentColor = Color.White)
                ) {
                    Text("Tutup Kamus")
                }
            }
        )
    }
}

@Composable
private fun QualityMetricItem(title: String, spec: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            Text(spec, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun CategoryDot(category: DailyMissions.Category) {
    val color = when (category) {
        DailyMissions.Category.AIR -> BlueWater
        DailyMissions.Category.NUTRISI -> GreenSuccess
        DailyMissions.Category.SANITASI -> CrimsonSanitation
        DailyMissions.Category.INSPEKSI -> PurpleInspect
        DailyMissions.Category.PANEN -> OrangePrimary
        DailyMissions.Category.UMUM -> Color(0xFF6D4C41)
    }
    Box(Modifier.size(9.dp).background(color, CircleShape))
}
