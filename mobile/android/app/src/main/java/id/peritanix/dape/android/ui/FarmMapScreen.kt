package id.peritanix.dape.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import id.peritanix.dape.android.core.hardware.GpsPolygonTracker
import id.peritanix.dape.android.core.physics.TwiLocalCompute
import id.peritanix.dape.android.data.SyncQueueStore
import id.peritanix.dape.android.ui.components.FarmMapMarker
import id.peritanix.dape.android.ui.components.InteractiveFarmMap

/**
 * Peta GIS Interaktif Terintegrasi (Tahap 1.1 GPS & FR-1.3 Topografi TWI & FR-2 Pathology).
 *
 * Visualizes:
 *   1. Polygon boundary recorded by GPS perimeter walk.
 *   2. 2D TWI Hydrological drainage risk cells (color-coded).
 *   3. Downslope runoff flow direction & wind spore dispersion vectors.
 *   4. Spatial disease pathology markers (Anthracnose, Gemini, Ralstonia, Phytophthora).
 *   5. Real-time farmer GPS position with accuracy radius.
 */
@Composable
fun FarmMapScreen(queue: SyncQueueStore? = null) {
    val scrollState = rememberScrollState()

    // --- State: GPS Polygon Tracker ---
    val polygonTracker = remember {
        GpsPolygonTracker().apply {
            // Seed initial smallholder plot (~0.25 Ha)
            val baseLat = -7.123456
            val baseLon = 110.456789
            addPoint(baseLat, baseLon, 850.0, 3.2f)
            addPoint(baseLat + 0.00045, baseLon, 852.0, 3.8f)
            addPoint(baseLat + 0.00045, baseLon + 0.00055, 851.0, 3.5f)
            addPoint(baseLat, baseLon + 0.00055, 849.0, 4.0f)
        }
    }

    var pointCount by remember { mutableStateOf(polygonTracker.getPoints().size) }
    var areaHa by remember { mutableStateOf(polygonTracker.calculateAreaHectares()) }
    var perimeterMeters by remember { mutableStateOf(polygonTracker.calculatePerimeterMeters()) }
    var isClosed by remember { mutableStateOf(polygonTracker.isPolygonClosed()) }

    // --- State: Topography & TWI Grid ---
    var baseSlope by remember { mutableStateOf(4.5) }
    var baseUpslope by remember { mutableStateOf(180.0) }
    val localGrid = remember(baseUpslope, baseSlope) {
        TwiLocalCompute.computeLocalGrid(baseUpslope, baseSlope, rows = 3, cols = 4)
    }

    // --- State: Disease Pathology Waypoints ---
    val markers = remember {
        mutableStateListOf(
            FarmMapMarker(
                id = "m1",
                lat = -7.123200,
                lon = 110.457000,
                title = "Antraknosa (Colletotrichum)",
                diseaseType = "ANTHRACNOSE",
                severityPct = 35.0,
                localTwi = 7.4,
                protocolCode = "P-CU-01",
                actionNote = "Semprot fungisida tembaga Cu(OH)2 2g/L dan pangkas buah busuk."
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
                actionNote = "Zona genangan tinggi! Tinggikan bedengan 50cm, cabut pohon layu, tabur kalsium oksida."
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
                actionNote = "Vektor kutu kebul searah angin ENE! Pasang perangkap kuning & semprot insektisida nabati."
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
                actionNote = "Busuk pangkal batang akibat drainase lambat. Buka parit pembuangan sedalam 60cm."
            )
        )
    }

    // Load any offline diagnoses from SQLite into map markers
    LaunchedEffect(Unit) {
        queue?.let { q ->
            try {
                val dbDiagnoses = q.getDiagnoses()
                dbDiagnoses.forEach { d ->
                    if (markers.none { it.id == d.id }) {
                        val dType = when {
                            d.confirmedDisease.contains("Antraknosa", ignoreCase = true) -> "ANTHRACNOSE"
                            d.confirmedDisease.contains("Gemini", ignoreCase = true) -> "BEGOMOVIRUS"
                            d.confirmedDisease.contains("Bakteri", ignoreCase = true) || d.confirmedDisease.contains("Ralstonia", ignoreCase = true) -> "RALSTONIA"
                            d.confirmedDisease.contains("Phytophthora", ignoreCase = true) -> "PHYTOPHTHORA"
                            else -> "HEALTHY"
                        }
                        markers.add(
                            FarmMapMarker(
                                id = d.id,
                                lat = d.lat,
                                lon = d.lon,
                                title = d.confirmedDisease,
                                diseaseType = dType,
                                severityPct = d.severityPct,
                                localTwi = d.localTwi,
                                protocolCode = d.protocolCode,
                                actionNote = "Tercatat dari diagnosis lapangan SQLite."
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    var selectedMarkerInfo by remember { mutableStateOf<FarmMapMarker?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- Header Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "🗺️ Peta GIS Lahan & Spasial Penyakit",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Peta interaktif offline: batas pematang GPS, kontur hidrologi TWI, titik infeksi tanaman, dan vektor aliran genangan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Stats Dashboard Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Luas Lahan", style = MaterialTheme.typography.labelSmall)
                        Text("${"%.3f".format(areaHa)} Ha", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    Column {
                        Text("Keliling", style = MaterialTheme.typography.labelSmall)
                        Text("${"%.1f".format(perimeterMeters)} m", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    Column {
                        Text("Titik Penyakit", style = MaterialTheme.typography.labelSmall)
                        Text("${markers.size} Lokasi", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
                    }
                    Column {
                        Text("Status Batas", style = MaterialTheme.typography.labelSmall)
                        Text(if (isClosed) "Tertutup ✓" else "Terbuka", fontWeight = FontWeight.Bold, color = if (isClosed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        // --- Interactive Vector Canvas Map ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InteractiveFarmMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    polygonPoints = polygonTracker.getPoints(),
                    twiCells = localGrid,
                    diseaseMarkers = markers,
                    currentGps = if (pointCount > 0) polygonTracker.getPoints().last() else null,
                    flowBearingDeg = 140f,
                    windBearingDeg = 65f,
                    isInteractive = true,
                    showControls = true,
                    showLayerToggles = true,
                    onMarkerClick = { selectedMarkerInfo = it }
                )

                // Legend row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🟢 Batas Lahan", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("🔴 Kritis TWI (≥7.0)", fontSize = 11.sp, color = Color(0xFFD32F2F), fontWeight = FontWeight.SemiBold)
                    Text("🟡 Gemini", fontSize = 11.sp, color = Color(0xFFFBC02D), fontWeight = FontWeight.SemiBold)
                    Text("🟣 Ralstonia", fontSize = 11.sp, color = Color(0xFF7B1FA2), fontWeight = FontWeight.SemiBold)
                    Text("💨 Angin (ENE)", fontSize = 11.sp, color = Color(0xFFFFB74D), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // --- Map Simulation & Action Controls ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Kontrol Interaktif & Simulasi Lahan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            // Expand polygon walking step
                            val baseLat = -7.123456
                            val baseLon = 110.456789
                            val cur = polygonTracker.getPoints().size
                            when (cur % 5) {
                                0 -> polygonTracker.addPoint(baseLat + 0.00060, baseLon + 0.00020, 853.0, 3.1f)
                                1 -> polygonTracker.addPoint(baseLat + 0.00050, baseLon + 0.00070, 852.0, 3.4f)
                                2 -> polygonTracker.addPoint(baseLat + 0.00010, baseLon + 0.00065, 850.0, 3.7f)
                                3 -> polygonTracker.addPoint(baseLat - 0.00010, baseLon + 0.00030, 848.0, 3.2f)
                                else -> polygonTracker.addPoint(baseLat, baseLon, 850.0, 3.5f)
                            }
                            pointCount = polygonTracker.getPoints().size
                            areaHa = polygonTracker.calculateAreaHectares()
                            perimeterMeters = polygonTracker.calculatePerimeterMeters()
                            isClosed = polygonTracker.isPolygonClosed()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ Titik GPS Pematang")
                    }

                    OutlinedButton(
                        onClick = {
                            // Add a new disease waypoint
                            val newId = "m-${System.currentTimeMillis().toString().takeLast(4)}"
                            val latOff = ((-20..20).random()) * 0.000015
                            val lonOff = ((-20..20).random()) * 0.000015
                            markers.add(
                                FarmMapMarker(
                                    id = newId,
                                    lat = -7.123250 + latOff,
                                    lon = 110.457050 + lonOff,
                                    title = "Antraknosa Buah #$newId",
                                    diseaseType = "ANTHRACNOSE",
                                    severityPct = (20..75).random().toDouble(),
                                    localTwi = 7.1,
                                    protocolCode = "P-CU-01",
                                    actionNote = "Deteksi baru: lakukan sanitasi buah busuk dan kurangi pupuk N."
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ Titik Infeksi")
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            // Toggle heavy rain / high catchment pooling
                            baseUpslope = if (baseUpslope > 250.0) 150.0 else 380.0
                            baseSlope = if (baseSlope < 3.0) 4.5 else 2.1
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (baseUpslope > 250.0) "Kembali Normal" else "Simulasi Hujan Lebat (Genangan)")
                    }

                    OutlinedButton(
                        onClick = {
                            polygonTracker.clear()
                            val baseLat = -7.123456
                            val baseLon = 110.456789
                            polygonTracker.addPoint(baseLat, baseLon, 850.0, 3.5f)
                            polygonTracker.addPoint(baseLat + 0.00045, baseLon, 852.0, 4.0f)
                            polygonTracker.addPoint(baseLat + 0.00045, baseLon + 0.00055, 851.0, 3.8f)
                            polygonTracker.addPoint(baseLat, baseLon + 0.00055, 849.0, 4.2f)
                            pointCount = polygonTracker.getPoints().size
                            areaHa = polygonTracker.calculateAreaHectares()
                            perimeterMeters = polygonTracker.calculatePerimeterMeters()
                            isClosed = polygonTracker.isPolygonClosed()
                        },
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Text("Reset Peta")
                    }
                }
            }
        }

        // --- Daftar Titik Pantau Infeksi & Tindakan Agronomis ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Daftar Titik Pantau Infeksi di Lahan (${markers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                markers.forEach { marker ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(marker.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Keparahan: ${marker.severityPct.toInt()}% | TWI: ${marker.localTwi}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (marker.actionNote.isNotEmpty()) {
                                    Text(marker.actionNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            AssistChip(
                                onClick = { selectedMarkerInfo = marker },
                                label = { Text("Lihat") }
                            )
                        }
                    }
                }
            }
        }
    }
}
