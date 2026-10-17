package id.peritanix.dape.android.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.peritanix.dape.android.core.calendar.ActivityCalendar
import id.peritanix.dape.android.data.FarmActivityEntity
import id.peritanix.dape.android.data.SyncQueueStore
import id.peritanix.dape.android.ui.components.AppIcons
import id.peritanix.dape.android.ui.components.NotificationType
import id.peritanix.dape.android.ui.components.PentasixNotificationHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/**
 * Kalender Aktivitas 30 Tahun (Tahap — field log).
 *
 * Browse months/years up to 30 years ahead, tap any day to see the agronomic
 * activities logged on that date (tanam, pupuk, semprot, panen, uji, …) and add
 * a new one. All date math comes from the tested [ActivityCalendar]; activities
 * are stored offline in Room (`farm_activities`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(queue: SyncQueueStore? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    // Currently browsed month + selected day.
    var viewMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDay by remember { mutableStateOf(today) }
    var dayActivities by remember { mutableStateOf<List<FarmActivityEntity>>(emptyList()) }
    var activityDaysInMonth by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // Add-activity form state.
    var addOpen by remember { mutableStateOf(false) }
    var newKind by remember { mutableStateOf("PUPUK") }
    var newTitle by remember { mutableStateOf("") }
    var newNotes by remember { mutableStateOf("") }

    fun reload() {
        scope.launch(Dispatchers.IO) {
            val acts = queue?.activitiesForDaySuspend(ActivityCalendar.toEpochDay(selectedDay)) ?: emptyList()
            val first = viewMonth.atDay(1)
            val last = viewMonth.atEndOfMonth()
            val days = queue?.activityDaysInRange(
                ActivityCalendar.toEpochDay(first), ActivityCalendar.toEpochDay(last)
            ) ?: emptySet()
            withContextMain {
                dayActivities = acts
                activityDaysInMonth = days
            }
        }
    }

    LaunchedEffect(selectedDay, viewMonth) { reload() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Calendar, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Column {
                Text("Kalender Aktivitas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("30 tahun ke depan · ketuk tanggal untuk melihat aktivitas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Year / month navigation
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val prev = viewMonth.minusMonths(1)
                        val clamped = ActivityCalendar.clampYearMonth(prev.year, prev.monthValue, today)
                        viewMonth = clamped
                    }) { Icon(AppIcons.ArrowDropDown, contentDescription = "Bulan sebelumnya", modifier = Modifier.size(22.dp)) }
                    Text(
                        "${ActivityCalendar.monthName(viewMonth.monthValue)} ${viewMonth.year}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = {
                        val next = viewMonth.plusMonths(1)
                        val clamped = ActivityCalendar.clampYearMonth(next.year, next.monthValue, today)
                        viewMonth = clamped
                    }) { Icon(AppIcons.Add, contentDescription = "Bulan berikutnya", modifier = Modifier.size(22.dp)) }

                    // Year jump (to reach up to +30 years quickly)
                    OutlinedButton(onClick = {
                        viewMonth = YearMonth.of((viewMonth.year + 5).coerceAtMost(today.year + ActivityCalendar.YEARS_AHEAD), viewMonth.monthValue)
                    }, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("+5 th", fontSize = 11.sp)
                    }
                }

                // Weekday header (Monday first)
                Row(Modifier.fillMaxWidth()) {
                    listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach { d ->
                        Text(d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Month grid
                val cells = ActivityCalendar.monthGrid(viewMonth.year, viewMonth.monthValue)
                // chunk into weeks
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            if (date == null) {
                                Spacer(Modifier.weight(1f).height(44.dp))
                            } else {
                                val isSelected = date == selectedDay
                                val isToday = date == today
                                val hasActivity = ActivityCalendar.toEpochDay(date) in activityDaysInMonth
                                Box(
                                    modifier = Modifier.weight(1f).height(44.dp).padding(2.dp)
                                        .then(
                                            if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                            else if (isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                            else Modifier
                                        )
                                        .clickable { selectedDay = date },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            date.dayOfMonth.toString(),
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (hasActivity) {
                                            Box(Modifier.padding(top = 1.dp).size(5.dp).background(
                                                if (isSelected) Color.White else MaterialTheme.colorScheme.tertiary, CircleShape))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected day + its activities
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(ActivityCalendar.fullLabel(selectedDay), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("${dayActivities.size} aktivitas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { addOpen = !addOpen; newTitle = ""; newNotes = "" }, shape = RoundedCornerShape(10.dp)) {
                        Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Tambah", fontSize = 12.sp)
                    }
                }

                if (addOpen) {
                    // kind chooser
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("TANAM", "PUPUK", "SEMPROT", "PANEN", "LAIN").forEach { k ->
                            val on = newKind == k
                            Surface(
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { newKind = k }
                            ) { Text(k, fontSize = 10.sp, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("Judul aktivitas") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = newNotes, onValueChange = { newNotes = it }, label = { Text("Catatan (opsional)") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            if (newTitle.isNotBlank()) {
                                queue?.addActivity(
                                    epochDay = ActivityCalendar.toEpochDay(selectedDay),
                                    kind = newKind, title = newTitle.trim(), notes = newNotes.trim()
                                )
                                addOpen = false
                                PentasixNotificationHub.notify(
                                    context = context, title = "Aktivitas Dicatat",
                                    message = "${newKind}: ${newTitle.trim()} pada ${ActivityCalendar.fullLabel(selectedDay)}.",
                                    type = NotificationType.SUCCESS, postSystemNotification = false
                                )
                                // Reload after the background insert has had a moment.
                                scope.launch { kotlinx.coroutines.delay(150); reload() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), enabled = newTitle.isNotBlank()
                    ) { Text("Simpan Aktivitas") }
                }

                if (dayActivities.isEmpty()) {
                    Text("Belum ada aktivitas pada tanggal ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    dayActivities.forEach { a ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KindDot(a.kind)
                            Column(Modifier.weight(1f)) {
                                Text("${kindLabel(a.kind)} · ${a.title}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                if (a.notes.isNotBlank()) Text(a.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                queue?.deleteActivity(a.id)
                                scope.launch { kotlinx.coroutines.delay(150); reload() }
                            }) { Icon(AppIcons.Remove, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
        }

        Text(
            "Tips: gunakan tombol << >> untuk pindah bulan dan +5 th untuk melompat tahun. Titik kecil menandai tanggal yang memiliki aktivitas.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun KindDot(kind: String) {
    val color = when (kind) {
        "TANAM" -> Color(0xFF2E7D32)
        "PUPUK" -> Color(0xFF1565C0)
        "SEMPROT" -> Color(0xFFAD1457)
        "PANEN" -> Color(0xFFF57C00)
        else -> Color(0xFF6D4C41)
    }
    Box(Modifier.size(10.dp).background(color, CircleShape))
}

private fun kindLabel(kind: String): String = when (kind) {
    "TANAM" -> "Tanam"
    "PUPUK" -> "Pupuk"
    "SEMPROT" -> "Semprot"
    "PANEN" -> "Panen"
    "PANEN_UJI" -> "Uji Panen"
    else -> "Lain"
}

/** Small helper: switch back to the Compose frame dispatcher for state writes. */
private suspend fun withContextMain(block: () -> Unit) {
    withContext(Dispatchers.Main.immediate) { block() }
}
