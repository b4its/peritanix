package id.peritanix.dape.android.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.peritanix.dape.android.core.missions.DailyMissions
import id.peritanix.dape.android.data.SyncQueueStore
import id.peritanix.dape.android.ui.components.AppIcons
import id.peritanix.dape.android.ui.components.NotificationType
import id.peritanix.dape.android.ui.components.PentasixNotificationHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Misi Harian Kualitas Cabai Terbaik.
 *
 * Menampilkan misi-misi harian (deterministik per HST & fase tumbuh) yang
 * diarahkan untuk memaksimalkan kualitas panen: nutrisi Ca/B/Si, sanitasi
 * Antraknosa, manajemen air, inspeksi kamera, dan panen tepat kematangan.
 * Penyelesaian misi disimpan offline di Room (daily_mission_done) dan memberi
 * skor kualitas harian berbobot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyMissionsScreen(queue: SyncQueueStore? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val epochDay = remember { today.toEpochDay() }

    var hst by remember { mutableStateOf(38) } // demo default: fase pembungaan
    var completed by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Optional EWS risk boost demo toggle for spore-defense mission.
    var riskBoost by remember { mutableStateOf(0.0) }

    val plan = remember(hst, riskBoost) { DailyMissions.planFor(today, hst, riskBoost) }
    val score = DailyMissions.dailyQualityScore(plan, completed)
    val grade = DailyMissions.scoreGrade(score)

    fun reload() {
        scope.launch(Dispatchers.IO) {
            val c = queue?.completedMissionsSuspend(epochDay) ?: emptySet()
            withContext(Dispatchers.Main.immediate) { completed = c }
        }
    }
    LaunchedEffect(epochDay) { reload() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(AppIcons.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Misi Harian Kualitas Terbaik", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${plan.stageTitle} · ${plan.phaseName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // HST control + risk toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Umur Tanaman (HST): $hst", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Slider(value = hst.toFloat(), onValueChange = { hst = it.toInt() }, valueRange = -21f..120f)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Risiko jamur (EWS): ${(riskBoost * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    Checkbox(checked = riskBoost >= 0.5, onCheckedChange = { riskBoost = if (it) 0.8 else 0.0 })
                    Text("aktifkan misi protektif", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Score card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Skor Kualitas Harian", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("$score%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                LinearProgressIndicator(
                    progress = { score / 100f },
                    modifier = Modifier.fillMaxWidth().height(10.dp)
                )
                Text(grade, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        // "Focus today" = next unfinished
        DailyMissions.nextUnfinished(plan, completed)?.let { next ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("FOKUS BERIKUTNYA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(next.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(next.qualityImpact, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }

        Text("Daftar Misi Hari Ini (${plan.missions.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        plan.missions.forEach { mission ->
            val done = mission.id in completed
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (done) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (done) 0.dp else 2.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Checkbox(
                        checked = done,
                        onCheckedChange = { checked ->
                            if (checked) {
                                queue?.markMissionDone(epochDay, mission.id)
                                PentasixNotificationHub.notify(
                                    context = context, title = "Misi Selesai",
                                    message = "${mission.title} — kualitas harian meningkat.",
                                    type = NotificationType.SUCCESS, postSystemNotification = false
                                )
                            } else {
                                queue?.unmarkMissionDone(epochDay, mission.id)
                            }
                            // optimistic UI
                            completed = if (checked) completed + mission.id else completed - mission.id
                            scope.launch { kotlinx.coroutines.delay(120); reload() }
                        }
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CategoryDot(mission.category)
                            Text(
                                mission.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                            )
                        }
                        Text(mission.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("★ ${mission.qualityImpact}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (mission.targetAmount > 1) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                            Text("×${mission.targetAmount}", fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }

        Text(
            "Misi dihasilkan otomatis sesuai fase tanaman (HST) dan diarahkan untuk memaksimalkan grade panen: nutrisi pericarp, sanitasi patogen, dan kematangan tepat waktu.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CategoryDot(category: DailyMissions.Category) {
    val color = when (category) {
        DailyMissions.Category.AIR -> Color(0xFF1565C0)
        DailyMissions.Category.NUTRISI -> Color(0xFF2E7D32)
        DailyMissions.Category.SANITASI -> Color(0xFFAD1457)
        DailyMissions.Category.INSPEKSI -> Color(0xFF6A1B9A)
        DailyMissions.Category.PANEN -> Color(0xFFF57C00)
        DailyMissions.Category.UMUM -> Color(0xFF6D4C41)
    }
    Box(Modifier.size(9.dp).background(color, CircleShape))
}
