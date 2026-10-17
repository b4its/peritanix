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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import id.peritanix.dape.android.core.hardware.GpsPolygonTracker
import id.peritanix.dape.android.core.missions.DailyMissions
import id.peritanix.dape.android.core.physics.TwiLocalCompute
import id.peritanix.dape.android.core.physics.WeatherRepository
import id.peritanix.dape.android.data.SyncQueueStore
import id.peritanix.dape.android.ui.components.AppIcons
import id.peritanix.dape.android.ui.components.FarmMapMarker
import id.peritanix.dape.android.ui.components.InteractiveFarmMap
import id.peritanix.dape.android.ui.components.MissionDetailDialog
import id.peritanix.dape.android.ui.components.NotificationType
import id.peritanix.dape.android.ui.components.PentasixNotificationHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Halaman Dashboard Utama Pentasix.
 *
 * Merangkum keseluruhan data mikro-pertanian cabai presisi:
 *   1. Lahan & Geometri Poligon GPS PostGIS
 *   2. Hidrologi Mikro, Topografi, & Grid TWI
 *   3. Patologi & Diagnosis Penyakit Edge AI
 *   4. Proyeksi Panen, GDD Thermal Time, & Uji Densitas Archimedes
 *   5. Peta Spasial GIS Terintegrasi
 *   6. Matriks Risiko Agronomi & Peringatan Dini
 *   7. Antrean Sinkronisasi Offline Room SQLite & Kriptografi
 */
@Composable
fun DashboardScreen(
    queue: SyncQueueStore? = null,
    weatherRepo: WeatherRepository? = null,
    onNavigateToTab: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Observe dynamic weather and live rain state from weatherRepo
    val isRaining = weatherRepo?.isRaining ?: false
    val rainStartTime = weatherRepo?.rainStartTimeMs ?: 0L
    val currentTemp = weatherRepo?.currentTempC ?: 21.5
    val currentRh = weatherRepo?.currentRhPct ?: 82.0
    val currentWind = weatherRepo?.currentWindKmh ?: 8.5
    val accumulatedGdd = weatherRepo?.lastSyncResult?.accumulatedGdd30Days ?: 780.0
    val weatherLabel = weatherRepo?.currentCondition?.label ?: "Optimal"

    var elapsedRainSec by remember {
        mutableStateOf(if (isRaining && rainStartTime > 0L) (System.currentTimeMillis() - rainStartTime) / 1000L else 0L)
    }

    LaunchedEffect(isRaining, rainStartTime) {
        while (isRaining && rainStartTime > 0L) {
            elapsedRainSec = (System.currentTimeMillis() - rainStartTime) / 1000L
            delay(1000L)
        }
    }

    // Query Room SQLite data counts
    var landCount by remember { mutableStateOf(queue?.landCount() ?: 1) }
    var diagnosisCount by remember { mutableStateOf(queue?.diagnosisCount() ?: 4) }
    var pendingSyncCount by remember { mutableStateOf(queue?.pendingCount() ?: 0) }

    LaunchedEffect(Unit) {
        queue?.let {
            landCount = it.landCount().coerceAtLeast(1)
            diagnosisCount = it.diagnosisCount().coerceAtLeast(4)
            pendingSyncCount = it.pendingCount()
        }
    }

    // Daily Quality Missions state for Dashboard
    val today = remember { LocalDate.now() }
    val epochDay = remember { today.toEpochDay() }
    val dashboardHst = 45 // fase pembungaan / kritis pericarp & kualitas
    var completedMissions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedDashboardMission by remember { mutableStateOf<DailyMissions.Mission?>(null) }
    val missionsPlan = remember { DailyMissions.planFor(today, dashboardHst) }
    val qualityScore = remember(completedMissions) { DailyMissions.dailyQualityScore(missionsPlan, completedMissions) }
    val exportGrade = remember(qualityScore) { DailyMissions.exportGradeBadge(qualityScore) }
    val nextFocusMission = remember(completedMissions) { DailyMissions.nextUnfinished(missionsPlan, completedMissions) }
    val pillarProgress = remember(completedMissions) { DailyMissions.getPillarProgress(missionsPlan, completedMissions) }

    fun reloadMissions() {
        queue?.let { q ->
            CoroutineScope(Dispatchers.IO).launch {
                val done = q.completedMissionsSuspend(epochDay)
                withContext(Dispatchers.Main.immediate) {
                    completedMissions = done
                }
            }
        }
    }
    LaunchedEffect(epochDay) {
        reloadMissions()
    }

    // Default Farm Geometry for Dashboard Preview
    val polygonTracker = remember {
        GpsPolygonTracker().apply {
            val baseLat = -7.123456
            val baseLon = 110.456789
            addPoint(baseLat, baseLon, 850.0, 3.2f)
            addPoint(baseLat + 0.00045, baseLon, 852.0, 3.8f)
            addPoint(baseLat + 0.00045, baseLon + 0.00055, 851.0, 3.5f)
            addPoint(baseLat, baseLon + 0.00055, 849.0, 4.0f)
        }
    }

    val areaHa = remember { polygonTracker.calculateAreaHectares() }
    val perimeterM = remember { polygonTracker.calculatePerimeterMeters() }
    val localGrid = remember {
        TwiLocalCompute.computeLocalGrid(baseUpslope = 180.0, baseSlope = 4.5, rows = 3, cols = 4)
    }

    val defaultMarkers = remember {
        listOf(
            FarmMapMarker(
                id = "m1",
                lat = -7.123200,
                lon = 110.457000,
                title = "Antraknosa (Colletotrichum)",
                diseaseType = "ANTHRACNOSE",
                severityPct = 35.0,
                localTwi = 7.4,
                protocolCode = "P-CU-01",
                actionNote = "Semprot fungisida tembaga Cu(OH)2 2g/L"
            ),
            FarmMapMarker(
                id = "m2",
                lat = -7.123350,
                lon = 110.457200,
                title = "Layu Bakteri (Ralstonia)",
                diseaseType = "RALSTONIA",
                severityPct = 60.0,
                localTwi = 8.2,
                protocolCode = "P-BA-01",
                actionNote = "Tinggikan bedengan 50cm, cabut pohon layu"
            ),
            FarmMapMarker(
                id = "m3",
                lat = -7.123100,
                lon = 110.456850,
                title = "Virus Gemini / Begomovirus",
                diseaseType = "BEGOMOVIRUS",
                severityPct = 25.0,
                localTwi = 5.8,
                protocolCode = "P-IN-02",
                actionNote = "Vektor kutu kebul searah angin ENE"
            ),
            FarmMapMarker(
                id = "m4",
                lat = -7.123400,
                lon = 110.456820,
                title = "Phytophthora Capsici",
                diseaseType = "PHYTOPHTHORA",
                severityPct = 40.0,
                localTwi = 7.9,
                protocolCode = "P-PH-01",
                actionNote = "Buka parit pembuangan sedalam 60cm"
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // =====================================================================
        // 1. Executive Agro-Climate Header Card
        // =====================================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToTab(7) }, // Navigate to Weather
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.Dashboard, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                "Dasbor Lahan Cabai",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                "Blok A1 • Kopeng (850 mdpl)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Surface(
                        color = if (isRaining) Color(0xFF1976D2) else Color(0xFF2E7D32),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.wrapContentSize()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                if (isRaining) AppIcons.Umbrella else AppIcons.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                if (isRaining) "Hujan Aktif" else weatherLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Microclimate Sensor Badges Row
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Suhu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${"%.1f".format(currentTemp)}°C", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Kelembapan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${currentRh.toInt()}% RH", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Thermal GDD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${accumulatedGdd.toInt()} °C-h", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Angin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Icon(AppIcons.Wind, contentDescription = null, tint = Color(0xFFEF6C00), modifier = Modifier.size(12.dp))
                                Text("${"%.1f".format(currentWind)} km/j", fontWeight = FontWeight.Bold, color = Color(0xFFEF6C00), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        // Live Rain Alert & Running Stopwatch Banner
        if (isRaining) {
            val hrs = elapsedRainSec / 3600
            val mins = (elapsedRainSec % 3600) / 60
            val secs = elapsedRainSec % 60
            val timeStr = String.format("%02d:%02d:%02d", hrs, mins, secs)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToTab(7) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF1976D2)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                    .size(32.dp)
                                    .background(Color(0xFF1976D2), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(AppIcons.Umbrella, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("SEDANG HUJAN DI LAHAN", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color(0xFF0D47A1))
                                    Surface(color = Color(0xFFD32F2F), shape = RoundedCornerShape(4.dp)) {
                                        Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }
                                Text("Intensitas: $weatherLabel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Text("Cuaca ➜", fontSize = 11.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                    }

                    // Stopwatch ticker row
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("DURASI HUJAN BERJALAN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                                Text(
                                    timeStr,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF0D47A1)
                                )
                            }
                            Button(
                                onClick = {
                                    val event = weatherRepo?.stopRain()
                                    val durationStr = event?.durationFormatted ?: "0 dtk"
                                    PentasixNotificationHub.notify(
                                        context = context,
                                        title = "Hujan Ditandai Berakhir",
                                        message = "Pencatatan hujan selesai ($durationStr). Data disimpan ke log cuaca.",
                                        type = NotificationType.RAIN
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(AppIcons.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tandai Berakhir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 2. Executive KPI Summary Grid (4 Primary Cards)
        // =====================================================================
        Text(
            "Ringkasan Indikator Agronomi",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Lahan & Geometri
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToTab(1) }, // Navigate to Lahan
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFF2E7D32), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.Sprout, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Text("Detail ➜", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Text("Luas Lahan Cabai", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${"%.3f".format(areaHa)} Ha", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    Text("Keliling ${"%.1f".format(perimeterM)} m • 4 Titik Batas Poligon", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Card 2: Topografi & TWI
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToTab(1) }, // Navigate to Lahan
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFF0277BD), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.WaterDrop, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Text("Detail ➜", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Text("Topografi & TWI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("TWI 6.84", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0277BD))
                    Text("Lereng 4.5° • Bedengan 30-40 cm disarankan", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 3: Patologi & Edge AI
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToTab(2) }, // Navigate to Diagnosis
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFFC62828), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.BugReport, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Text("Detail ➜", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Text("Kesehatan Tanaman", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$diagnosisCount Lokasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                    Text("1 Antraknosa 35% • Protokol P-CU-01 aktif", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Card 4: Panen & Archimedes
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToTab(3) }, // Navigate to Panen
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFFEF6C00), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.Chili, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Text("Detail ➜", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Text("Estimasi Panen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("1.85 Ton/Ha", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFEF6C00))
                    Text("Sortasi 94% Biji Bernas (Archimedes)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // =====================================================================
        // 3. Kartu Misi Harian Kualitas Cabai Terbaik (Grade A+ Ekspor)
        // =====================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    qualityScore >= 90 -> Color(0xFF1B5E20)
                    qualityScore >= 70 -> Color(0xFF2E7D32)
                    qualityScore >= 40 -> Color(0xFFE65100)
                    else -> Color(0xFFC2185B)
                }
            ),
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
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(
                                "Misi Kualitas Cabai Hari Ini",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                            Text(
                                exportGrade,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "$qualityScore%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { qualityScore / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )

                // 5-Pillar Matrix Mini
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
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    target.title.split(" ").first(),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Text(
                                    "$done/$total",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isComplete) Color(0xFFC8E6C9) else Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // Interactive Next Focus Mission or Completion Message
                if (nextFocusMission != null) {
                    val isDone = nextFocusMission.id in completedMissions
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDashboardMission = nextFocusMission }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = isDone,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        queue?.markMissionDone(epochDay, nextFocusMission.id)
                                        PentasixNotificationHub.notify(
                                            context = context,
                                            title = "Misi Mutu Selesai",
                                            message = "${nextFocusMission.title} — skor kualitas meningkat.",
                                            type = NotificationType.SUCCESS,
                                            postSystemNotification = false
                                        )
                                    } else {
                                        queue?.unmarkMissionDone(epochDay, nextFocusMission.id)
                                    }
                                    completedMissions = if (checked) completedMissions + nextFocusMission.id else completedMissions - nextFocusMission.id
                                    reloadMissions()
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Fokus: ${nextFocusMission.title}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF1B5E20),
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Text(
                                        "Panduan SOP ➜",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                }
                                Text(
                                    nextFocusMission.qualityImpact,
                                    fontSize = 10.sp,
                                    color = Color(0xFF333333),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(AppIcons.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text(
                                "Seluruh misi mutu cabai hari ini telah tuntas 100%!",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Button to jump to Tab 12
                Button(
                    onClick = { onNavigateToTab(12) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                ) {
                    Text(
                        "Kelola Semua Misi Kualitas (${missionsPlan.missions.size}) ➜",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                }
            }
        }

        // =====================================================================
        // 4. Mini Integrated GIS Map Card
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(AppIcons.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Pratinjau Peta GIS Lahan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { onNavigateToTab(4) }) { // Navigate to Peta GIS
                        Text("Buka Peta Penuh ➜", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Interactive Farm GIS Map Component
                InteractiveFarmMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    polygonPoints = polygonTracker.getPoints(),
                    twiCells = localGrid,
                    diseaseMarkers = defaultMarkers,
                    currentGps = polygonTracker.getPoints().lastOrNull(),
                    flowBearingDeg = 140f,
                    windBearingDeg = 65f,
                    isInteractive = true,
                    showControls = true,
                    showLayerToggles = true
                )

                // Legend summary row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(Color(0xFF2E7D32), CircleShape))
                        Text("Poligon", fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(Color(0xFF0277BD), CircleShape))
                        Text("Grid TWI", fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(Color(0xFFD32F2F), CircleShape))
                        Text("Penyakit", fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(AppIcons.Wind, contentDescription = null, tint = Color(0xFFEF6C00), modifier = Modifier.size(11.dp))
                        Text("Angin ENE", fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(Color(0xFF1976D2), CircleShape))
                        Text("GPS Petani", fontSize = 10.sp)
                    }
                }
            }
        }

        // =====================================================================
        // 4. Agronomic Early Warning & Risk Matrix
        // =====================================================================
        Text(
            "Peringatan Dini & Rekomendasi Lapangan",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Warning 1: Genangan TWI
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0x220277BD), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.WaterDrop, contentDescription = null, tint = Color(0xFF0277BD), modifier = Modifier.size(16.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Drainase Kritis di Barat Laut", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Sel TWI [2, 2] mencapai 7.84. Risiko pembusukan akar & Phytophthora tinggi saat hujan lebat. Buka saluran pembuangan 50-60 cm.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

                // Warning 2: Angin & Vektor Begomovirus
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0x22EF6C00), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Wind, contentDescription = null, tint = Color(0xFFEF6C00), modifier = Modifier.size(16.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Vektor Kutu Kebul Searah Angin ENE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Kecepatan angin 8.5 km/j ENE membawa spora dan kutu kebul (vektor virus Gemini). Pasang perangkap likat kuning di pematang timur.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

                // Warning 3: Preskripsi Tanah Dolomit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0x222E7D32), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Science, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Preskripsi Netralisasi Keasaman Tanah", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "pH tanah terdeteksi 5.4 (agak masam). Taburkan dolomit dosis 1.2 Ton/Ha untuk mencegah kekurangan kalsium & busuk ujung buah (BER).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 5. Quick Action Field Shortcuts (2x2 Grid)
        // =====================================================================
        Text(
            "Pintasan Tindakan Lapangan",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    PentasixNotificationHub.notify(
                        context = context,
                        title = "Navigasi: Lahan",
                        message = "Membuka modul pemetaan poligon GPS & analisis tanah TWI.",
                        type = NotificationType.INFO,
                        postSystemNotification = false
                    )
                    onNavigateToTab(1)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(AppIcons.Sprout, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ukur Lahan", fontSize = 12.sp)
            }
            Button(
                onClick = {
                    PentasixNotificationHub.notify(
                        context = context,
                        title = "Navigasi: Diagnosa AI",
                        message = "Membuka deteksi patologi daun & buah cabai on-device.",
                        type = NotificationType.INFO,
                        postSystemNotification = false
                    )
                    onNavigateToTab(2)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(AppIcons.Microscope, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Scan AI", fontSize = 12.sp)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    PentasixNotificationHub.notify(
                        context = context,
                        title = "Navigasi: Uji Panen",
                        message = "Membuka termoterapi, densitas benih & sertifikat QR.",
                        type = NotificationType.INFO,
                        postSystemNotification = false
                    )
                    onNavigateToTab(3)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(AppIcons.Chili, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Uji Panen", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = {
                    PentasixNotificationHub.notify(
                        context = context,
                        title = "Navigasi: Peta GIS",
                        message = "Membuka visualisasi spasial lahan, TWI & infeksi.",
                        type = NotificationType.INFO,
                        postSystemNotification = false
                    )
                    onNavigateToTab(4)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(AppIcons.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Peta GIS", fontSize = 12.sp)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    PentasixNotificationHub.notify(
                        context = context,
                        title = "Navigasi: Kompas Lahan",
                        message = "Membuka sensor arah mata angin & waterpass perataan.",
                        type = NotificationType.CALIBRATION,
                        postSystemNotification = false
                    )
                    onNavigateToTab(6)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(AppIcons.Compass, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Kompas Lahan", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = {
                    PentasixNotificationHub.notify(
                        context = context,
                        title = "Navigasi: Kalibrasi Sensor",
                        message = "Membuka kalibrasi sensor magnetik figure-8 & zero tilt.",
                        type = NotificationType.CALIBRATION,
                        postSystemNotification = false
                    )
                    onNavigateToTab(6)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(AppIcons.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Kalibrasi Sensor", fontSize = 12.sp)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    PentasixNotificationHub.notify(
                        context = context,
                        title = "Navigasi: Cuaca & Hujan",
                        message = "Membuka pemantau presipitasi & sinkronisasi Open-Meteo.",
                        type = NotificationType.RAIN,
                        postSystemNotification = false
                    )
                    onNavigateToTab(7)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(AppIcons.WbSunny, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cuaca & Hujan", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = {
                    PentasixNotificationHub.notify(
                        context = context,
                        title = "Navigasi: Sinkron Data",
                        message = "Membuka antrean mutasi Room SQLite & WorkManager.",
                        type = NotificationType.SYNC,
                        postSystemNotification = false
                    )
                    onNavigateToTab(5)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(AppIcons.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sinkron Data", fontSize = 12.sp)
            }
        }

        // Mode Riil shortcuts: camera quality detection + growing guide
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    PentasixNotificationHub.notify(
                        context = context,
                        title = "Kualitas Cabai via Kamera",
                        message = "Deteksi kematangan & mutu buah dari foto kamera perangkat.",
                        type = NotificationType.INFO,
                        postSystemNotification = false
                    )
                    onNavigateToTab(8)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Kualitas (Kamera)", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = {
                    PentasixNotificationHub.notify(
                        context = context,
                        title = "Panduan Tanam Cabai",
                        message = "Tutorial budidaya dari bibit hingga siap panen.",
                        type = NotificationType.INFO,
                        postSystemNotification = false
                    )
                    onNavigateToTab(9)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(AppIcons.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Panduan Tanam", fontSize = 12.sp)
            }
        }

        // Calendar quick-action (30-year activity calendar)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    PentasixNotificationHub.notify(
                        context = context,
                        title = "Kalender Aktivitas",
                        message = "Lihat & catat aktivitas kebun pada tanggal mana pun (30 tahun).",
                        type = NotificationType.INFO,
                        postSystemNotification = false
                    )
                    onNavigateToTab(11)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(AppIcons.Calendar, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Kalender Aktivitas", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = {
                    PentasixNotificationHub.notify(
                        context = context,
                        title = "Misi Harian Kualitas",
                        message = "Tuntaskan misi hari ini untuk kualitas cabai terbaik.",
                        type = NotificationType.INFO,
                        postSystemNotification = false
                    )
                    onNavigateToTab(12)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(AppIcons.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Misi Harian", fontSize = 12.sp)
            }
        }

        // =====================================================================
        // 6. Offline-First Sync & Cryptography Audit Status
        // =====================================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToTab(5) }, // Navigate to Sinkron
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Room SQLite & Ed25519", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Icon(AppIcons.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(13.dp))
                        }
                        Text(
                            if (pendingSyncCount > 0)
                                "$pendingSyncCount mutasi menunggu sinkronisasi online"
                            else
                                "Semua entitas lokal tersimpan aman offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text("Sinkron ➜", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    // Modal Dialog: Detail Panduan Lengkap SOP Misi dari Dashboard
    if (selectedDashboardMission != null) {
        val detail = selectedDashboardMission!!
        val isDone = detail.id in completedMissions
        MissionDetailDialog(
            mission = detail,
            isCompleted = isDone,
            onToggleCompleted = { checked ->
                if (checked) {
                    queue?.markMissionDone(epochDay, detail.id)
                    PentasixNotificationHub.notify(
                        context = context,
                        title = "Misi Mutu Selesai",
                        message = "${detail.title} — skor kualitas meningkat.",
                        type = NotificationType.SUCCESS,
                        postSystemNotification = false
                    )
                } else {
                    queue?.unmarkMissionDone(epochDay, detail.id)
                }
                completedMissions = if (checked) completedMissions + detail.id else completedMissions - detail.id
                reloadMissions()
            },
            onDismiss = { selectedDashboardMission = null },
            onNavigateToTab = onNavigateToTab
        )
    }
}
