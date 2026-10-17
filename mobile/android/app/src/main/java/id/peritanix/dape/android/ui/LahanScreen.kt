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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.peritanix.dape.android.core.ai_edge.EdgeInferenceRunner
import id.peritanix.dape.android.core.hardware.DarkChamberFlashManager
import id.peritanix.dape.android.core.hardware.GpsPolygonTracker
import id.peritanix.dape.android.core.physics.TwiLocalCompute
import id.peritanix.dape.android.ui.components.AppIcons
import id.peritanix.dape.android.ui.components.InteractiveFarmMap
import id.peritanix.dape.physics.Physics
import java.io.File

@Composable
fun LahanScreen(queue: id.peritanix.dape.android.data.SyncQueueStore? = null) {
    val scrollState = rememberScrollState()

    // --- State: GPS Polygon Tracker (Tahap 1.1) ---
    val polygonTracker = remember { GpsPolygonTracker() }
    var pointCount by remember { mutableStateOf(0) }
    var perimeterMeters by remember { mutableStateOf(0.0) }
    var areaHa by remember { mutableStateOf(0.0) }
    var isClosed by remember { mutableStateOf(false) }
    var wktPolygon by remember { mutableStateOf("") }
    var gpsSaveStatus by remember { mutableStateOf("") }

    // --- State: Topografi & TWI ---
    var slopeDeg by remember { mutableStateOf(4.5) }
    var upslopeArea by remember { mutableStateOf(150.0) }
    var sandPct by remember { mutableStateOf(40.0) }
    var siltPct by remember { mutableStateOf(40.0) }
    var clayPct by remember { mutableStateOf(20.0) }

    var hst by remember { mutableStateOf(40) }
    var soilPh by remember { mutableStateOf(5.8) }
    var saveStatus by remember { mutableStateOf("") }

    // --- State: Onboarding Spectral Calibration ---
    var calibR by remember { mutableStateOf(240) }
    var calibG by remember { mutableStateOf(245) }
    var calibB by remember { mutableStateOf(238) }
    var calibLux by remember { mutableStateOf(0.0f) }
    var calibStatus by remember { mutableStateOf("") }

    val twiAssessment = TwiLocalCompute.compute(upslopeArea, slopeDeg)
    val twi = twiAssessment.twiValue
    val riskClass = twiAssessment.drainageRisk
    val bedHeight = twiAssessment.requiredBedHeightCm
    val localGrid = remember(upslopeArea, slopeDeg) {
        TwiLocalCompute.computeLocalGrid(upslopeArea, slopeDeg, rows = 3, cols = 4)
    }

    val usdaClass = Physics.classifyUsda(sandPct, siltPct, clayPct)
    val cec = Physics.cecFromTexture(sandPct, siltPct, clayPct)
    val fertPlan = Physics.prescribeFertilizer(soilPh, cec, hst)
    val aiFertMix = remember(soilPh, cec, hst) {
        EdgeInferenceRunner(File("/tmp/models")).predictFertilizerMix(
            ph = soilPh.toFloat(),
            cecMeq = cec.toFloat(),
            hst = hst
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Card 0: Perekaman Poligon GPS Lahan (Tahap 1.1) ---
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
                        Icon(AppIcons.GpsFixed, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            "0. Pemetaan Poligon Batas Lahan (GPS Presisi)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Menyusuri pematang lahan untuk merekam poligon PostGIS (Target: 0,1 - 2,0 Ha).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Stats Surface
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Titik GPS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$pointCount Titik", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column {
                            Text("Keliling", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${"%.1f".format(perimeterMeters)} m", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column {
                            Text("Luas Lahan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${"%.3f".format(areaHa)} Ha", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val baseLat = -7.123456
                            val baseLon = 110.456789
                            when (pointCount) {
                                0 -> polygonTracker.addPoint(baseLat, baseLon, 850.0, 3.5f)
                                1 -> polygonTracker.addPoint(baseLat + 0.00045, baseLon, 852.0, 4.0f)
                                2 -> polygonTracker.addPoint(baseLat + 0.00045, baseLon + 0.00055, 851.0, 3.8f)
                                3 -> polygonTracker.addPoint(baseLat, baseLon + 0.00055, 849.0, 4.2f)
                                else -> polygonTracker.addPoint(baseLat, baseLon, 850.0, 3.5f)
                            }
                            pointCount = polygonTracker.getPoints().size
                            perimeterMeters = polygonTracker.calculatePerimeterMeters()
                            areaHa = polygonTracker.calculateAreaHectares()
                            isClosed = polygonTracker.isPolygonClosed()
                            wktPolygon = polygonTracker.toWktPolygon()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (pointCount == 0) "Mulai GPS" else "+ Titik Batas")
                    }

                    OutlinedButton(
                        onClick = {
                            polygonTracker.clear()
                            pointCount = 0
                            perimeterMeters = 0.0
                            areaHa = 0.0
                            isClosed = false
                            wktPolygon = ""
                            gpsSaveStatus = ""
                        },
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Icon(AppIcons.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset")
                    }
                }

                if (areaHa > 0) {
                    AssistChip(
                        onClick = {},
                        leadingIcon = {
                            Icon(
                                AppIcons.CheckCircle,
                                contentDescription = null,
                                tint = if (polygonTracker.isWithinSmallholderRange()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                if (polygonTracker.isWithinSmallholderRange())
                                    "Skala Mikro Cabai Sesuai (0,1 - 2,0 Ha)"
                                else "Di luar rentang mikro-lahan"
                            )
                        }
                    )
                }

                // Visual Interactive GIS Farm Map
                InteractiveFarmMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    polygonPoints = polygonTracker.getPoints(),
                    twiCells = localGrid,
                    currentGps = if (pointCount > 0) polygonTracker.getPoints().last() else null,
                    flowBearingDeg = 140f,
                    windBearingDeg = 65f,
                    isInteractive = true,
                    showControls = true,
                    showLayerToggles = true
                )

                if (wktPolygon.isNotEmpty()) {
                    Text(
                        "WKT PostGIS: $wktPolygon",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            queue?.enqueueLand(
                                landId = "land-demo-${System.currentTimeMillis().toString().takeLast(4)}",
                                name = "Blok Cabai Rawit Merah A1",
                                boundaryWkt = wktPolygon,
                                elevationM = 850.0,
                                currentPhase = "VEGETATIVE"
                            )
                            gpsSaveStatus = "Batas poligon lahan tersimpan ke Room SQLite & diantrekan ke server."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(AppIcons.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Simpan Poligon Lahan ke Room SQLite")
                    }

                    if (gpsSaveStatus.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(
                                gpsSaveStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // --- Card 1: Hidrologi Mikro & TWI (Hukum Darcy & Topografi) ---
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
                        Icon(AppIcons.WaterDrop, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            "1. Hidrologi Mikro & TWI (Topografi Bedengan)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Sensor Klinometer: β=${"%.1f".format(slopeDeg)}° | Daerah tangkapan a=${upslopeArea.toInt()} m²/m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kemiringan Lereng: ${"%.1f".format(slopeDeg)}°", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = slopeDeg.toFloat(),
                    onValueChange = { slopeDeg = it.toDouble() },
                    valueRange = 0.1f..30.0f
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Daerah Tangkapan (a): ${upslopeArea.toInt()} m²/m", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = upslopeArea.toFloat(),
                    onValueChange = { upslopeArea = it.toDouble() },
                    valueRange = 10f..500f
                )

                HorizontalDivider()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Indeks Kebasahan (TWI)", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "%.2f".format(twi),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("Tinggi Bedengan", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "$bedHeight cm",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (riskClass == "CRITICAL" || riskClass == "HIGH") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    AssistChip(
                        onClick = {},
                        leadingIcon = {
                            Icon(
                                if (riskClass == "CRITICAL" || riskClass == "HIGH") AppIcons.Warning else AppIcons.CheckCircle,
                                contentDescription = null,
                                tint = if (riskClass == "CRITICAL" || riskClass == "HIGH") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(riskClass) }
                    )
                }

                Text(
                    twiAssessment.recommendation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 2D Contour Map Matrix Preview
                Text(
                    "Peta Kontur TWI 2D Offline (Grid 3x4 Bedengan):",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF202020), RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    localGrid.forEach { rowCells ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            rowCells.forEach { cell ->
                                val cellColor = when (cell.riskClass) {
                                    "CRITICAL" -> Color(0xFFD32F2F)
                                    "HIGH" -> Color(0xFFF57C00)
                                    "MODERATE" -> Color(0xFF388E3C)
                                    else -> Color(0xFF1976D2)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .background(cellColor, RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${"%.1f".format(cell.twi)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Card 2: Sedimentasi Stokes & Water-Level Camera Tilt Lock ---
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
                            "2. Analisis Tekstur Tanah & Panduan Kocok (Hukum Stokes)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Penyelarasan Water-Level Shutter Lock: HP tegak lurus 90° ± 5°.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Water-Level Camera Tilt Bar
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(AppIcons.Straighten, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text("Orientasi HP (Sensor Gravitasi):", style = MaterialTheme.typography.labelMedium)
                            }
                            Text("Pitch: 0.8° | Roll: -0.4°", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                            Text(
                                "POSISI 90° TEGAK LURUS: Shutter Kamera Terbuka",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Stage selection tabs
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(AppIcons.Timer, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tahap 1: 40s Pasir", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(AppIcons.Timer, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tahap 2: 2h Debu", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Text(
                    "Fraksi Pasir (40s): ${sandPct.toInt()}% | Debu (2h): ${siltPct.toInt()}% | Liat: ${clayPct.toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )

                Slider(
                    value = sandPct.toFloat(),
                    onValueChange = {
                        sandPct = it.toDouble()
                        val rem = (100.0 - sandPct).coerceAtLeast(0.0)
                        siltPct = rem * 0.6
                        clayPct = rem * 0.4
                    },
                    valueRange = 0f..100f
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Kelas Tekstur USDA", style = MaterialTheme.typography.bodySmall)
                        Text(
                            usdaClass.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text("Kapasitas Tukar Kation (KTK)", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${"%.1f".format(cec)} meq/100g",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- Card 3: Pemupukan Preskriptif Terkalibrasi ---
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
                        Icon(AppIcons.Sprout, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            "3. Dosis Pemupukan Preskriptif (Kocor / L)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Dihitung matematis berdasarkan umur tanaman (HST) & pH tanah.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Umur Tanaman: $hst HST", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    Text("pH Tanah: ${"%.1f".format(soilPh)}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                }
                Slider(
                    value = hst.toFloat(),
                    onValueChange = { hst = it.toInt() },
                    valueRange = 5f..120f
                )
                Slider(
                    value = soilPh.toFloat(),
                    onValueChange = { soilPh = it.toDouble() },
                    valueRange = 4.0f..8.0f
                )

                HorizontalDivider()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NPK 16-16-16", style = MaterialTheme.typography.bodySmall)
                        Text("${fertPlan.npk16_16_16GPerL} g/L", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("KNO3 Kalium", style = MaterialTheme.typography.bodySmall)
                        Text("${fertPlan.kno3GPerL} g/L", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Kalsium Nitrat", style = MaterialTheme.typography.bodySmall)
                        Text("${fertPlan.calciumGPerL} g/L", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (fertPlan.dolomiteGPerM2 > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Text(
                            "Advis pH Masam: Aplikasi dolomit ${fertPlan.dolomiteGPerM2} g/m² sebelum pemupukan.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text(
                    fertPlan.recommendation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Distilasi Edge AI Tabular On-Device (< 10 ms)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Distilasi Edge AI Tabular (LightGBM)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text("< 10 ms (${aiFertMix.executionTimeMs} ms)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Text("• NPK 16-16-16: ${aiFertMix.npk16_16_16GPerL} g/L", style = MaterialTheme.typography.bodySmall)
                        Text("• KNO3 Kalium: ${aiFertMix.kno3GPerL} g/L", style = MaterialTheme.typography.bodySmall)
                        Text("• Kalsium/Dolomit: ${aiFertMix.calciumGPerL} g/L", style = MaterialTheme.typography.bodySmall)
                    }
                }

                HorizontalDivider()

                Button(
                    onClick = {
                        queue?.enqueueSoilAssessment(
                            landId = "land-demo-001",
                            sandPct = sandPct,
                            siltPct = siltPct,
                            clayPct = clayPct,
                            soilClass = usdaClass,
                            cecMeq = cec,
                            phDeterministic = soilPh,
                            nitratePpm = 85.0
                        )
                        saveStatus = "Penilaian tanah tersimpan ke Room SQLite & diantrekan untuk Delta Sync."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(AppIcons.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Simpan Penilaian Tanah (Room SQLite)")
                }

                if (saveStatus.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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

        // --- Card 4: Kalibrasi Spektral LED Flash Onboarding (Tahap 1.1) ---
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
                        Icon(AppIcons.FlashOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            "4. Kalibrasi Spektral LED Flash (Onboarding 0 Lux)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Kompensasi deviasi warna sensor antar-merek HP menggunakan referensi kertas HVS putih.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = {
                        val calib = DarkChamberFlashManager.calibrateSpectralWhiteReference(
                            rawR = calibR,
                            rawG = calibG,
                            rawB = calibB,
                            ambientLux = calibLux
                        )
                        if (calib.isCalibrated) {
                            queue?.saveCalibration(
                                id = "calib-hw-main",
                                whiteRefR = calib.rScale,
                                whiteRefG = calib.gScale,
                                whiteRefB = calib.bScale,
                                luxVerified = calib.luxVerified
                            )
                            calibStatus = "Kalibrasi Berhasil: Matriks R=${calib.rScale}, G=${calib.gScale}, B=${calib.bScale} tersimpan ke Room SQLite."
                        } else {
                            calibStatus = "Gagal: Mangkuk belum kedap cahaya (Sensor Lux > 0.5 Lux)."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Jalankan Kalibrasi Mangkuk Gelap (0 Lux)")
                }

                if (calibStatus.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            if (calibStatus.startsWith("Kalibrasi Berhasil")) AppIcons.CheckCircle else AppIcons.Warning,
                            contentDescription = null,
                            tint = if (calibStatus.startsWith("Kalibrasi Berhasil")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            calibStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (calibStatus.startsWith("Kalibrasi Berhasil")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
