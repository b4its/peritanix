package id.peritanix.dape.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import id.peritanix.dape.android.core.calendar.ActivityCalendar
import id.peritanix.dape.android.core.missions.DailyMissions
import id.peritanix.dape.android.data.FarmActivityEntity
import id.peritanix.dape.android.data.SyncQueueStore
import id.peritanix.dape.android.ui.components.AppIcons
import id.peritanix.dape.android.ui.components.MissionDetailDialog
import id.peritanix.dape.android.ui.components.NotificationType
import id.peritanix.dape.android.ui.components.PentasixNotificationHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/**
 * Kalender Aktivitas Cabai Presisi (Rentang 30 Tahun ke Depan).
 *
 * Mengakomodasi perencanaan dan pencatatan agronomi cabai presisi (0-120 HST hingga
 * rotasi lahan jangka panjang) selama 30 tahun ke depan (2026 - 2056).
 *
 * Seluruh data tersimpan 100% offline di Room DB lokal dan dapat disinkronkan saat daring.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    queue: SyncQueueStore? = null,
    onNavigateToTab: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    // State navigasi bulan/tahun dan tanggal terpilih
    var viewMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDay by remember { mutableStateOf(today) }
    var dayActivities by remember { mutableStateOf<List<FarmActivityEntity>>(emptyList()) }
    var activityDaysInMonth by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val selectedEpochDay = remember(selectedDay) { selectedDay.toEpochDay() }
    var dayMissionsCompleted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedCalendarMission by remember { mutableStateOf<DailyMissions.Mission?>(null) }

    // Dialog & Form State
    var showYearDialog by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var newKind by remember { mutableStateOf("PUPUK") }
    var newTitle by remember { mutableStateOf("") }
    var newNotes by remember { mutableStateOf("") }
    var newLandId by remember { mutableStateOf("Blok A1 Kopeng") }

    fun reload() {
        scope.launch(Dispatchers.IO) {
            // Seed initial precision activities if empty
            queue?.seedDefaultActivitiesIfEmpty(today)

            val acts = queue?.activitiesForDaySuspend(ActivityCalendar.toEpochDay(selectedDay)) ?: emptyList()
            val first = viewMonth.atDay(1)
            val last = viewMonth.atEndOfMonth()
            val days = queue?.activityDaysInRange(
                ActivityCalendar.toEpochDay(first), ActivityCalendar.toEpochDay(last)
            ) ?: emptySet()
            withContext(Dispatchers.Main.immediate) {
                dayActivities = acts
                activityDaysInMonth = days
            }
        }
    }

    LaunchedEffect(selectedDay, viewMonth) {
        reload()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // =====================================================================
        // 1. Header Kalender 30 Tahun
        // =====================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Calendar,
                        contentDescription = "Kalender",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Kalender Aktivitas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "30 Tahun",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        "${today.year} - ${today.year + ActivityCalendar.YEARS_AHEAD} · Ketuk tanggal untuk melihat & mencatat aktivitas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // =====================================================================
        // 2. Toolbar Navigasi Tahun & Bulan (30 Tahun)
        // =====================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Baris Navigasi Utama: Chevron < [ Bulan Tahun ▾ ] Chevron >
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val canGoBack = viewMonth.isAfter(YearMonth.of(today.year, 1))
                    val canGoForward = viewMonth.isBefore(YearMonth.of(today.year + ActivityCalendar.YEARS_AHEAD, 12))

                    IconButton(
                        onClick = {
                            if (canGoBack) {
                                val prev = viewMonth.minusMonths(1)
                                viewMonth = ActivityCalendar.clampYearMonth(prev.year, prev.monthValue, today)
                            }
                        },
                        enabled = canGoBack
                    ) {
                        Icon(
                            imageVector = AppIcons.ChevronLeft,
                            contentDescription = "Bulan sebelumnya",
                            tint = if (canGoBack) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { showYearDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "${ActivityCalendar.monthName(viewMonth.monthValue)} ${viewMonth.year}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = AppIcons.ArrowDropDown,
                                contentDescription = "Pilih tahun",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (canGoForward) {
                                val next = viewMonth.plusMonths(1)
                                viewMonth = ActivityCalendar.clampYearMonth(next.year, next.monthValue, today)
                            }
                        },
                        enabled = canGoForward
                    ) {
                        Icon(
                            imageVector = AppIcons.ChevronRight,
                            contentDescription = "Bulan berikutnya",
                            tint = if (canGoForward) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Quick Jump Chips: Hari Ini, +1 Th, +5 Th, +10 Th, +30 Th, Pilih Tahun
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = {
                            viewMonth = YearMonth.from(today)
                            selectedDay = today
                        },
                        label = { Text("Hari Ini", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(AppIcons.Calendar, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                    AssistChip(
                        onClick = {
                            val targetYear = (viewMonth.year + 1).coerceAtMost(today.year + ActivityCalendar.YEARS_AHEAD)
                            viewMonth = YearMonth.of(targetYear, viewMonth.monthValue)
                        },
                        label = { Text("+1 Th", fontSize = 11.sp) }
                    )
                    AssistChip(
                        onClick = {
                            val targetYear = (viewMonth.year + 5).coerceAtMost(today.year + ActivityCalendar.YEARS_AHEAD)
                            viewMonth = YearMonth.of(targetYear, viewMonth.monthValue)
                        },
                        label = { Text("+5 Th", fontSize = 11.sp) }
                    )
                    AssistChip(
                        onClick = {
                            val targetYear = (viewMonth.year + 10).coerceAtMost(today.year + ActivityCalendar.YEARS_AHEAD)
                            viewMonth = YearMonth.of(targetYear, viewMonth.monthValue)
                        },
                        label = { Text("+10 Th", fontSize = 11.sp) }
                    )
                    AssistChip(
                        onClick = {
                            viewMonth = YearMonth.of(today.year + ActivityCalendar.YEARS_AHEAD, viewMonth.monthValue)
                        },
                        label = { Text("+30 Th (${today.year + 30})", fontSize = 11.sp) }
                    )
                    AssistChip(
                        onClick = { showYearDialog = true },
                        label = { Text("Pilih Tahun ▾", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                // Horizontal Month Selector (12 Bulan Singkat)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val monthShorts = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Ags", "Sep", "Okt", "Nov", "Des")
                    monthShorts.forEachIndexed { idx, shortName ->
                        val mNum = idx + 1
                        val isSelectedMonth = viewMonth.monthValue == mNum
                        Surface(
                            color = if (isSelectedMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable {
                                viewMonth = YearMonth.of(viewMonth.year, mNum)
                            }
                        ) {
                            Text(
                                shortName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelectedMonth) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelectedMonth) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Baris Nama Hari (Sen s/d Min)
                Row(Modifier.fillMaxWidth()) {
                    listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach { d ->
                        Text(
                            d,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Grid Kalender Bulanan
                val cells = ActivityCalendar.monthGrid(viewMonth.year, viewMonth.monthValue)
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            if (date == null) {
                                Spacer(Modifier.weight(1f).height(46.dp))
                            } else {
                                val isSelected = date == selectedDay
                                val isToday = date == today
                                val hasActivity = ActivityCalendar.toEpochDay(date) in activityDaysInMonth
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .padding(2.dp)
                                        .then(
                                            if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                            else if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                            else Modifier
                                        )
                                        .clickable { selectedDay = date },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            date.dayOfMonth.toString(),
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (hasActivity) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 2.dp)
                                                    .size(6.dp)
                                                    .background(
                                                        if (isSelected) Color.White else MaterialTheme.colorScheme.tertiary,
                                                        CircleShape
                                                    )
                                            )
                                        } else {
                                            Spacer(Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 3. Detail Aktivitas pada Tanggal Terpilih (View & Tambah Aktivitas)
        // =====================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header Tanggal Terpilih
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            ActivityCalendar.fullLabel(selectedDay),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    ActivityCalendar.relativeDayLabel(selectedDay, today),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                "${dayActivities.size} aktivitas tercatat",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            addOpen = !addOpen
                            if (addOpen) {
                                newTitle = ""
                                newNotes = ""
                                newKind = "PUPUK"
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (addOpen) AppIcons.Close else AppIcons.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (addOpen) "Tutup" else "Tambah", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Form Tambah Aktivitas Manual
                if (addOpen) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Tambah Jadwal / Log Aktivitas",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )

                            // Kategori selector
                            Text("Pilih Kategori:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ActivityCalendar.CATEGORIES.forEach { cat ->
                                    val isSelected = newKind == cat.code
                                    Surface(
                                        color = if (isSelected) Color(cat.colorHex) else MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.clickable { newKind = cat.code }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        ) {
                                            Icon(
                                                imageVector = categoryIcon(cat.code),
                                                contentDescription = null,
                                                tint = if (isSelected) Color.White else Color(cat.colorHex),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                cat.label,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = newTitle,
                                onValueChange = { newTitle = it },
                                label = { Text("Judul Aktivitas (wajib)") },
                                placeholder = { Text("Contoh: Pemupukan NPK Cor / Semprot Antraknosa") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = newLandId,
                                onValueChange = { newLandId = it },
                                label = { Text("Lokasi / Blok Lahan") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = newNotes,
                                onValueChange = { newNotes = it },
                                label = { Text("Catatan Agronomi (opsional)") },
                                placeholder = { Text("Dosis, volume tangki, rekomendasi sensor, dll.") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { addOpen = false },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Batal")
                                }
                                Button(
                                    onClick = {
                                        if (newTitle.isNotBlank()) {
                                            scope.launch(Dispatchers.IO) {
                                                queue?.addActivitySuspend(
                                                    epochDay = ActivityCalendar.toEpochDay(selectedDay),
                                                    kind = newKind,
                                                    title = newTitle.trim(),
                                                    notes = newNotes.trim(),
                                                    landId = newLandId.trim()
                                                )
                                                withContext(Dispatchers.Main.immediate) {
                                                    addOpen = false
                                                    PentasixNotificationHub.notify(
                                                        context = context,
                                                        title = "Aktivitas Dijadwalkan",
                                                        message = "${newKind}: ${newTitle.trim()} pada ${ActivityCalendar.fullLabel(selectedDay)}.",
                                                        type = NotificationType.SUCCESS,
                                                        postSystemNotification = false
                                                    )
                                                    reload()
                                                }
                                            }
                                        }
                                    },
                                    enabled = newTitle.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Simpan", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Daftar Aktivitas pada Hari Terpilih
                if (dayActivities.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Belum ada aktivitas tercatat pada tanggal ini.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Pilih template cepat di bawah untuk menambahkan:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Template Cepat Tambah Aktivitas
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "PUPUK" to "Pemupukan NPK Cor",
                                "SEMPROT" to "Semprot Antraknosa",
                                "WIWILAN" to "Wiwilan & Cek LAI",
                                "PANEN" to "Panen & Sortasi Archimedes",
                                "SENSOR" to "Kalibrasi Barometer & TWI"
                            ).forEach { (kind, defaultTitle) ->
                                OutlinedButton(
                                    onClick = {
                                        addOpen = true
                                        newKind = kind
                                        newTitle = defaultTitle
                                        newNotes = "Dijadwalkan via template agronomi cepat DAPE."
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(categoryIcon(kind), contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("+ $defaultTitle", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dayActivities.forEach { a ->
                            val cat = ActivityCalendar.findCategory(a.kind)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(cat.colorHex), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = categoryIcon(a.kind),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                color = Color(cat.colorHex).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    cat.label,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(cat.colorHex),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                            if (a.landId.isNotBlank()) {
                                                Text(
                                                    "• ${a.landId}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Text(
                                            a.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        if (a.notes.isNotBlank()) {
                                            Text(
                                                a.notes,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                queue?.deleteActivitySuspend(a.id)
                                                withContext(Dispatchers.Main.immediate) {
                                                    PentasixNotificationHub.notify(
                                                        context = context,
                                                        title = "Aktivitas Dihapus",
                                                        message = "Log aktivitas ${a.title} berhasil dihapus.",
                                                        type = NotificationType.INFO,
                                                        postSystemNotification = false
                                                    )
                                                    reload()
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.Remove,
                                            contentDescription = "Hapus",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 3B. Misi Harian Kualitas Cabai pada Tanggal Terpilih
        // =====================================================================
        val dayMissionsPlan = remember(selectedDay) {
            val diffDays = (selectedDay.toEpochDay() - today.toEpochDay()).toInt()
            val computedHst = (45 + diffDays).coerceIn(-21, 120)
            DailyMissions.planFor(selectedDay, computedHst)
        }
        val dayQualityScore = remember(dayMissionsPlan, dayMissionsCompleted) {
            DailyMissions.dailyQualityScore(dayMissionsPlan, dayMissionsCompleted)
        }

        LaunchedEffect(selectedEpochDay) {
            queue?.let { q ->
                val done = q.completedMissionsSuspend(selectedEpochDay)
                dayMissionsCompleted = done
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                                .size(28.dp)
                                .background(Color(0xFFE65100), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Column {
                            Text(
                                "Misi Kualitas Cabai (${dayMissionsPlan.stageTitle})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${dayMissionsPlan.phaseName} · Skor Mutu: $dayQualityScore%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "${dayMissionsCompleted.size}/${dayMissionsPlan.missions.size} Selesai",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { dayQualityScore / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )

                // List of missions for this day
                dayMissionsPlan.missions.forEach { mission ->
                    val isDone = mission.id in dayMissionsCompleted
                    Surface(
                        color = if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCalendarMission = mission }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = isDone,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        queue?.markMissionDone(selectedEpochDay, mission.id)
                                        PentasixNotificationHub.notify(
                                            context = context,
                                            title = "Misi Mutu Selesai",
                                            message = "${mission.title} dicatat di kalender.",
                                            type = NotificationType.SUCCESS,
                                            postSystemNotification = false
                                        )
                                    } else {
                                        queue?.unmarkMissionDone(selectedEpochDay, mission.id)
                                    }
                                    dayMissionsCompleted = if (checked) dayMissionsCompleted + mission.id else dayMissionsCompleted - mission.id
                                },
                                modifier = Modifier.size(24.dp)
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                mission.qualityTarget.title.split(" ").first(),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        Text(
                                            mission.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            textDecoration = if (isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                            color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        "SOP ➜",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                }
                                Text(
                                    mission.qualityImpact,
                                    fontSize = 10.sp,
                                    color = Color(0xFFE65100),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 4. Informasi Proyeksi Siklus Tanam Jangka Panjang
        // =====================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        "Informasi Kalender 30 Tahun",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Kalender ini dirancang untuk pemantauan siklus tanam cabai jangka pendek (0-120 HST) serta perencanaan rotasi tanah dan multi-dekade hingga tahun ${today.year + ActivityCalendar.YEARS_AHEAD}. Seluruh log aktivitas tersimpan aman tanpa internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // =========================================================================
    // 5. Dialog Pemilih Tahun (30 Tahun: 2026 s/d 2056)
    // =========================================================================
    if (showYearDialog) {
        Dialog(onDismissRequest = { showYearDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Pilih Tahun (Rentang 30 Tahun)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showYearDialog = false }) {
                            Icon(AppIcons.Close, contentDescription = "Tutup")
                        }
                    }

                    Text(
                        "Pilih tahun dari ${today.year} hingga ${today.year + ActivityCalendar.YEARS_AHEAD} untuk melihat proyeksi & aktivitas:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val years = remember(today) { ActivityCalendar.years(today) }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(years) { y ->
                            val isSelectedYear = y == viewMonth.year
                            val isCurrentYear = y == today.year
                            Surface(
                                color = if (isSelectedYear) MaterialTheme.colorScheme.primary
                                else if (isCurrentYear) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewMonth = YearMonth.of(y, viewMonth.monthValue)
                                        showYearDialog = false
                                    }
                            ) {
                                Text(
                                    y.toString(),
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelectedYear || isCurrentYear) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelectedYear) Color.White
                                    else if (isCurrentYear) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { showYearDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Tutup")
                    }
                }
            }
        }
    }

    // Modal Dialog: Detail Panduan Lengkap SOP Misi dari Kalender
    if (selectedCalendarMission != null) {
        val detail = selectedCalendarMission!!
        val isDone = detail.id in dayMissionsCompleted
        MissionDetailDialog(
            mission = detail,
            isCompleted = isDone,
            onSaveObservationNote = { note ->
                scope.launch {
                    queue?.addActivitySuspend(
                        epochDay = selectedEpochDay,
                        kind = "MISI_MUTU",
                        title = "${detail.title} (Selesai)",
                        notes = note,
                        landId = "Lahan Cabai"
                    )
                    reload()
                }
            },
            onToggleCompleted = { checked ->
                if (checked) {
                    queue?.markMissionDone(selectedEpochDay, detail.id)
                    PentasixNotificationHub.notify(
                        context = context,
                        title = "Misi Mutu Selesai",
                        message = "${detail.title} dicatat di kalender.",
                        type = NotificationType.SUCCESS,
                        postSystemNotification = false
                    )
                } else {
                    queue?.unmarkMissionDone(selectedEpochDay, detail.id)
                }
                dayMissionsCompleted = if (checked) dayMissionsCompleted + detail.id else dayMissionsCompleted - detail.id
            },
            onDismiss = { selectedCalendarMission = null },
            onNavigateToTab = onNavigateToTab
        )
    }
}

/**
 * Pemetaan icon vektor kategori agronomi.
 */
private fun categoryIcon(kind: String) = when (kind.uppercase()) {
    "TANAM" -> AppIcons.Sprout
    "PUPUK" -> AppIcons.Terrain
    "SEMPROT" -> AppIcons.BugReport
    "IRIGASI" -> AppIcons.WaterDrop
    "WIWILAN" -> AppIcons.Straighten
    "PENGAMATAN" -> AppIcons.Microscope
    "PANEN", "PANEN_UJI" -> AppIcons.Chili
    "SENSOR" -> AppIcons.Compass
    else -> AppIcons.CheckCircle
}
