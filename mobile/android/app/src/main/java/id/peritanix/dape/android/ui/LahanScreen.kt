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
import id.peritanix.dape.android.core.hardware.DarkChamberFlashManager
import id.peritanix.dape.android.core.hardware.GpsPolygonTracker
import id.peritanix.dape.android.core.physics.TwiLocalCompute
import id.peritanix.dape.physics.Physics

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Card 0: Perekaman Poligon GPS Lahan (Tahap 1.1) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "0. Pemetaan Poligon Batas Lahan (GPS Presisi)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Petani berjalan menyusuri pematang lahan untuk merekam poligon tertutup PostGIS (Target: 0,1 - 2,0 Ha).",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Titik GPS: $pointCount")
                    Text("Keliling: ${"%.1f".format(perimeterMeters)} m")
                    Text("Luas: ${"%.3f".format(areaHa)} Ha", fontWeight = FontWeight.Bold)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            // Demo walk adding points around a smallholder chili plot (~0.25 Ha)
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
                        Text("Reset")
                    }
                }

                if (areaHa > 0) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (polygonTracker.isWithinSmallholderRange())
                                    "✓ Skala Mikro-Perkebunan Cabai (0,1 - 2,0 Ha)"
                                else "Di luar rentang mikro-lahan"
                            )
                        }
                    )
                }

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
                        Text("Simpan Poligon Lahan ke Room SQLite")
                    }

                    if (gpsSaveStatus.isNotEmpty()) {
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

        // --- Card 1: Hidrologi Mikro & TWI (Hukum Darcy & Topografi) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "1. Hidrologi Mikro & TWI (Topografi Bedengan)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Sensor Klinometer: Rotasi lereng β=${"%.1f".format(slopeDeg)}° | Daerah tangkapan a=${upslopeArea.toInt()} m²/m",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kemiringan Lereng: ${"%.1f".format(slopeDeg)}°")
                }
                Slider(
                    value = slopeDeg.toFloat(),
                    onValueChange = { slopeDeg = it.toDouble() },
                    valueRange = 0.1f..30.0f
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Daerah Tangkapan (a): ${upslopeArea.toInt()} m²/m")
                }
                Slider(
                    value = upslopeArea.toFloat(),
                    onValueChange = { upslopeArea = it.toDouble() },
                    valueRange = 10f..500f
                )

                HorizontalDivider()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                        .background(Color(0xFF202020), RoundedCornerShape(8.dp))
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "2. Analisis Tekstur Tanah & Panduan Kocok Botol (Hukum Stokes)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Penyelarasan Water-Level Shutter Lock: HP wajib tegak lurus 90° ± 5° terhadap botol.",
                    style = MaterialTheme.typography.bodySmall
                )

                // Water-Level Camera Tilt Bar
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Orientasi HP (Sensor Gravitasi):", style = MaterialTheme.typography.labelMedium)
                            Text("Pitch: 0.8° | Roll: -0.4°", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            "✓ POSISI 90° TEGAK LURUS: Shutter Kamera Terbuka (Bebas Distorsi Perspektif)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Stage selection tabs
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Tahap 1: 40s Pasir", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    ) {
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "3. Dosis Pemupukan Preskriptif (Kocor / L)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Umur Tanaman (HST): $hst HST")
                    Text("pH Tanah: ${"%.1f".format(soilPh)}")
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
                    Column {
                        Text("NPK 16-16-16", style = MaterialTheme.typography.bodySmall)
                        Text("${fertPlan.npk16_16_16GPerL} g/L", fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("KNO3 Kalium", style = MaterialTheme.typography.bodySmall)
                        Text("${fertPlan.kno3GPerL} g/L", fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Kalsium Nitrat", style = MaterialTheme.typography.bodySmall)
                        Text("${fertPlan.calciumGPerL} g/L", fontWeight = FontWeight.Bold)
                    }
                }

                if (fertPlan.dolomiteGPerM2 > 0) {
                    Text(
                        "Advis pH Masam: Aplikasi dolomit ${fertPlan.dolomiteGPerM2} g/m² sebelum pemupukan.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    fertPlan.recommendation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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
                    Text("Simpan Penilaian Tanah (Room SQLite Offline-First)")
                }

                if (saveStatus.isNotEmpty()) {
                    Text(
                        saveStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- Card 4: Kalibrasi Spektral LED Flash Onboarding (Tahap 1.1) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "4. Kalibrasi Spektral LED Flash (Onboarding 0 Lux)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Kalibrasi perangkat terhadap kertas HVS putih dalam mangkuk gelap untuk kompensasi deviasi warna antar-merek HP.",
                    style = MaterialTheme.typography.bodySmall
                )

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
                    Text("Jalankan Kalibrasi Mangkuk Gelap (0 Lux)")
                }

                if (calibStatus.isNotEmpty()) {
                    Text(
                        calibStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
