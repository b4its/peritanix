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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.peritanix.dape.android.core.ai_edge.EdgeInferenceRunner
import id.peritanix.dape.android.core.hardware.GpsPolygonTracker
import id.peritanix.dape.android.core.physics.BayesianSpatialInfectionEngine
import id.peritanix.dape.android.core.physics.OozeVideoStabilizer
import id.peritanix.dape.android.core.physics.PericarpFortificationManager
import id.peritanix.dape.android.core.physics.TwiLocalCompute
import id.peritanix.dape.android.ui.components.AppIcons
import id.peritanix.dape.android.ui.components.FarmMapMarker
import id.peritanix.dape.android.ui.components.InteractiveFarmMap
import id.peritanix.dape.android.ui.components.NotificationType
import id.peritanix.dape.android.ui.components.PentasixNotificationHub
import id.peritanix.dape.pathology.PathologyClassifier
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(queue: id.peritanix.dape.android.data.SyncQueueStore? = null) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // --- State: Morfologi Keriting & Lesi ---
    var curlDirection by remember { mutableStateOf("up") }
    var undersideColor by remember { mutableStateOf("silvery") }
    var mosaicVeins by remember { mutableStateOf(false) }
    var sunkenLesions by remember { mutableStateOf(false) }

    var isWilting by remember { mutableStateOf(true) }
    var soilMoisturePct by remember { mutableStateOf(68.0) }
    var saveStatus by remember { mutableStateOf("") }

    // --- State: Tahap 4.3 Ooze Test & Local Bayesian Spatial Engine ---
    var oozeStreamingSeen by remember { mutableStateOf(true) }
    var leavesGreenWilt by remember { mutableStateOf(true) }
    var oozeShakeDeg by remember { mutableStateOf(3.2) }
    var oozeRecordedSuccess by remember { mutableStateOf(false) }

    val bayesianPerimeter = remember(isWilting, leavesGreenWilt, oozeStreamingSeen, soilMoisturePct) {
        BayesianSpatialInfectionEngine.evaluatePathogenAndPerimeter(
            BayesianSpatialInfectionEngine.MultiModalEvidence(
                isWilting = isWilting,
                leavesRemainGreen = leavesGreenWilt,
                oozeStreamingDetected = oozeStreamingSeen,
                twiScore = 8.1,
                soilMoisturePct = soilMoisturePct,
                prevailingWindBearingDeg = 65.0,
                infectedBedIndex = 3,
                infectedHoleIndex = 12,
                totalBedsInBlock = 12
            )
        )
    }

    // --- State: Spatial Pathology GIS Mapping ---
    val diagMarkers = remember {
        mutableStateListOf(
            FarmMapMarker(
                id = "d1",
                lat = -7.123220,
                lon = 110.457020,
                title = "Antraknosa (Colletotrichum)",
                diseaseType = "ANTHRACNOSE",
                severityPct = 30.0,
                localTwi = 7.2,
                protocolCode = "P-CU-01",
                actionNote = "Semprot fungisida tembaga Cu(OH)2 2g/L dan pangkas tajuk lembab."
            ),
            FarmMapMarker(
                id = "d2",
                lat = -7.123380,
                lon = 110.457220,
                title = "Layu Bakteri (Ralstonia)",
                diseaseType = "RALSTONIA",
                severityPct = 65.0,
                localTwi = 8.1,
                protocolCode = "P-BA-01",
                actionNote = "Karantina zona genangan tinggi & naikkan bedengan 50cm."
            )
        )
    }
    val demoPolygon = remember {
        val baseLat = -7.123456
        val baseLon = 110.456789
        listOf(
            GpsPolygonTracker.GpsPoint(baseLat, baseLon, 850.0, 3.5f),
            GpsPolygonTracker.GpsPoint(baseLat + 0.00045, baseLon, 852.0, 4.0f),
            GpsPolygonTracker.GpsPoint(baseLat + 0.00045, baseLon + 0.00055, 851.0, 3.8f),
            GpsPolygonTracker.GpsPoint(baseLat, baseLon + 0.00055, 849.0, 4.2f)
        )
    }
    val diagTwiGrid = remember {
        TwiLocalCompute.computeLocalGrid(160.0, 4.0, rows = 3, cols = 4)
    }

    // --- State: Active Leaf Trans-Illumination (FR-2.2) ---
    val edgeRunner = remember { EdgeInferenceRunner(File("/tmp/models")) }
    var transR by remember { mutableStateOf(110) }
    var transG by remember { mutableStateOf(160) }
    var transB by remember { mutableStateOf(90) }
    val transResult = remember(transR, transG, transB) {
        edgeRunner.evaluateTransIllumination(transR, transG, transB)
    }

    // --- State: U-Net Lite 4-Class Lesion Segmentation ---
    var chloroticPct by remember { mutableStateOf(18.0f) }
    var necroticPct by remember { mutableStateOf(12.0f) }
    val healthyPct = remember(chloroticPct, necroticPct) {
        (100.0f - chloroticPct - necroticPct).coerceAtLeast(0.0f)
    }
    val unetResult = remember(healthyPct, chloroticPct, necroticPct) {
        edgeRunner.segmentLesionClasses(
            healthy = healthyPct / 100.0f,
            chlorotic = chloroticPct / 100.0f,
            necrotic = necroticPct / 100.0f,
            background = 0.0f
        )
    }

    // --- State: Yield Estimation Fruit Counting ---
    var greenFruits by remember { mutableStateOf(35) }
    var turningFruits by remember { mutableStateOf(12) }
    var ripeFruits by remember { mutableStateOf(8) }
    val yieldResult = remember(greenFruits, turningFruits, ripeFruits) {
        edgeRunner.estimateYieldAndFruitCount(greenFruits, turningFruits, ripeFruits)
    }

    // --- State: Pericarp Fortification Lock & Nitrogen Restriction (FR-3.2) ---
    var currentPhase by remember { mutableStateOf("FLOWERING") }
    var gddAccumulated by remember { mutableStateOf(480.0) }
    var plantHst by remember { mutableStateOf(45) }
    var proposedFertilizer by remember { mutableStateOf("Urea 46%") }
    var proposedNitrogenPct by remember { mutableStateOf(46.0) }

    val fortStatus = remember(currentPhase, gddAccumulated, plantHst) {
        PericarpFortificationManager.evaluateLockStatus(currentPhase, gddAccumulated, plantHst)
    }
    val fertValidation = remember(proposedFertilizer, proposedNitrogenPct, fortStatus.isLocked) {
        PericarpFortificationManager.validateFertilizerProposal(
            fertilizerName = proposedFertilizer,
            nitrogenPct = proposedNitrogenPct,
            isLocked = fortStatus.isLocked
        )
    }

    val diag = PathologyClassifier.differentiateLeafCurl(
        curlDirection = curlDirection,
        undersideColor = undersideColor,
        mosaicVeins = mosaicVeins,
        sunkenLesions = sunkenLesions
    )

    val oozeResult = PathologyClassifier.evaluateDestructiveOozeTrigger(
        isWilting = isWilting,
        soilMoisturePct = soilMoisturePct
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Card 1: Diferensiasi Morfologi Keriting Daun On-Device ---
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
                        Icon(AppIcons.Microscope, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            "1. Diferensiasi Keriting Daun & Lesi On-Device",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Pemisahan patologi daun: Thrips vs Tungau vs Gemini vs Antraknosa.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text("Arah Lengkungan Daun:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("up" to "Ke Atas (Perahu)", "down" to "Ke Bawah (Sendok)", "none" to "Datar").forEach { (valKey, label) ->
                        FilterChip(
                            selected = curlDirection == valKey,
                            onClick = { curlDirection = valKey },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Warna Permukaan Bawah Daun:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("silvery" to "Keperakan (Thrips)", "bronze" to "Tembaga (Tungau)", "normal" to "Normal").forEach { (valKey, label) ->
                        FilterChip(
                            selected = undersideColor == valKey,
                            onClick = { undersideColor = valKey },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = mosaicVeins,
                        onCheckedChange = { mosaicVeins = it }
                    )
                    Text("Pola Mosaik Kuning / Tulang Daun Tebal (Virus Gemini)", style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = sunkenLesions,
                        onCheckedChange = { sunkenLesions = it }
                    )
                    Text("Bercak Nekrotik Cekung Melingkar / Spora Gelap (Antraknosa)", style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider()

                Text(
                    diag.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    diag.description,
                    style = MaterialTheme.typography.bodySmall
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Bahan Aktif Kimia: ${diag.activeIngredient}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text("Dosis: ${diag.dosagePerLiter}", style = MaterialTheme.typography.bodySmall)
                        Text("Panduan Semprot: ${diag.sprayingInstructions}", style = MaterialTheme.typography.bodySmall)
                        Text("Solusi PHT Hayati: ${diag.organicAlternative}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }

                // U-Net Lite 4-Class Lesion Severity Analysis
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Segmentasi Lesi U-Net Lite (Edge AI)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text("Severity: ${unetResult.calculatedSeverityPct}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("• Jaringan Sehat: ${healthyPct.toInt()}% | Klorosis: ${chloroticPct.toInt()}% | Nekrosis: ${necroticPct.toInt()}%", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Simulasi Lesi:", style = MaterialTheme.typography.bodySmall)
                            FilterChip(
                                selected = necroticPct == 12.0f,
                                onClick = {
                                    necroticPct = 12.0f
                                    chloroticPct = 18.0f
                                },
                                label = { Text("Ringan") }
                            )
                            FilterChip(
                                selected = necroticPct == 35.0f,
                                onClick = {
                                    necroticPct = 35.0f
                                    chloroticPct = 25.0f
                                },
                                label = { Text("Sedang") }
                            )
                            FilterChip(
                                selected = necroticPct == 60.0f,
                                onClick = {
                                    necroticPct = 60.0f
                                    chloroticPct = 25.0f
                                },
                                label = { Text("Berat") }
                            )
                        }
                    }
                }

                HorizontalDivider()

                Button(
                    onClick = {
                        val markLat = -7.123280 + ((-15..15).random() * 0.000015)
                        val markLon = 110.457000 + ((-15..15).random() * 0.000015)
                        val dType = when {
                            diag.condition.contains("Antraknosa", ignoreCase = true) -> "ANTHRACNOSE"
                            diag.condition.contains("Gemini", ignoreCase = true) -> "BEGOMOVIRUS"
                            diag.condition.contains("Bakteri", ignoreCase = true) || diag.condition.contains("Ralstonia", ignoreCase = true) -> "RALSTONIA"
                            diag.condition.contains("Phytophthora", ignoreCase = true) -> "PHYTOPHTHORA"
                            else -> "HEALTHY"
                        }
                        queue?.enqueueDiagnosis(
                            landId = "land-demo-001",
                            confirmedDisease = diag.condition,
                            severityPct = unetResult.calculatedSeverityPct.toDouble(),
                            confidence = "HIGH",
                            protocolCode = diag.condition,
                            localTwi = 6.5,
                            lon = markLon,
                            lat = markLat
                        )
                        diagMarkers.add(
                            FarmMapMarker(
                                id = "d-${System.currentTimeMillis().toString().takeLast(4)}",
                                lat = markLat,
                                lon = markLon,
                                title = diag.condition,
                                diseaseType = dType,
                                severityPct = unetResult.calculatedSeverityPct.toDouble(),
                                localTwi = 6.5,
                                protocolCode = diag.condition,
                                actionNote = "${diag.activeIngredient} (${diag.dosagePerLiter})"
                            )
                        )
                        saveStatus = "Diagnosis tersimpan ke Room SQLite & ditambahkan ke Peta Spasial Lahan."

                        PentasixNotificationHub.notify(
                            context = context,
                            title = "Diagnosis AI Disimpan",
                            message = "${diag.condition} (${unetResult.calculatedSeverityPct}%). Disimpan ke SQLite & ditandai pada Peta GIS.",
                            type = NotificationType.SUCCESS,
                            postSystemNotification = true
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(AppIcons.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Simpan Diagnosis & Petakan ke Peta GIS")
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

        // --- Card 2: Uji Ooze Destruktif & Mesin Bayesian Isolasi Spasial (Tahap 4.3) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (bayesianPerimeter.bacterialWiltProbability >= 0.50) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                .background(if (bayesianPerimeter.bacterialWiltProbability >= 0.50) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.Science, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(
                                "2. Uji Ooze & Mesin Bayesian Spasial",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Diferensiasi Ralstonia vs Fusarium & Isolasi Bedeng (Tahap 4.3)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = if (bayesianPerimeter.bacterialWiltProbability >= 0.50) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = bayesianPerimeter.urgencyLevel,
                            color = if (bayesianPerimeter.bacterialWiltProbability >= 0.50) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isWilting,
                        onCheckedChange = { isWilting = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isWilting) "Gejala Layu Terdeteksi di Bedengan #3" else "Tanaman Tidak Layu", fontWeight = FontWeight.SemiBold)
                }

                if (isWilting) {
                    Text("Kelembaban Tanah Zona TWI: ${soilMoisturePct.toInt()}%", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = soilMoisturePct.toFloat(),
                        onValueChange = { soilMoisturePct = it.toDouble() },
                        valueRange = 10f..100f
                    )

                    // Video Ooze 10s Recording & Gyro Stability Gate
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Stabilisasi Video Uji Ooze (10 Detik)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (oozeShakeDeg <= 4.0) "Stabil (${"%.1f".format(oozeShakeDeg)}°)" else "Goyangan (${"%.1f".format(oozeShakeDeg)}°)",
                                    fontWeight = FontWeight.Bold,
                                    color = if (oozeShakeDeg <= 4.0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(
                                "Potong batang cabai 2 cm di atas leher akar, celupkan pangkal ke gelas air bening selama 10 detik.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = {
                                    oozeShakeDeg = 2.8
                                    oozeRecordedSuccess = true
                                    PentasixNotificationHub.notify(
                                        context = context,
                                        title = "Video Uji Ooze Terekam",
                                        message = "10 detik video terekam stabil (goyangan ${oozeShakeDeg}°). Optical flow aktif.",
                                        type = NotificationType.SUCCESS,
                                        postSystemNotification = false
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Mulai Perekaman Video Ooze 10s (Gyro Gate)", fontSize = 12.sp)
                            }
                        }
                    }

                    // Multi-Modal Observations (Foliar Phenotype & Bacterial Streaming)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Checkbox(
                                checked = oozeStreamingSeen,
                                onCheckedChange = { oozeStreamingSeen = it }
                            )
                            Text("Asap Putih Mengalir (Ooze Positif)", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Checkbox(
                                checked = leavesGreenWilt,
                                onCheckedChange = { leavesGreenWilt = it }
                            )
                            Text("Daun Layu Tetap Hijau", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider()

                    // Bayesian Posterior Odds Card
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Diagnosis Bayesian Multi-Modal:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Probabilitas: ${"%.1f".format(bayesianPerimeter.bacterialWiltProbability * 100)}%",
                                    fontWeight = FontWeight.Bold,
                                    color = if (bayesianPerimeter.bacterialWiltProbability >= 0.50) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                bayesianPerimeter.diagnosedPathogen,
                                fontWeight = FontWeight.Bold,
                                color = if (bayesianPerimeter.bacterialWiltProbability >= 0.50) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                bayesianPerimeter.agronomicSummary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // Spatial Quarantine Buffer Guidance
                    Surface(
                        color = if (bayesianPerimeter.bacterialWiltProbability >= 0.50) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(AppIcons.GpsFixed, contentDescription = null, tint = if (bayesianPerimeter.bacterialWiltProbability >= 0.50) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text(
                                    "Perimeter Karantina: Bedengan #${bayesianPerimeter.quarantinedBedStart} s/d #${bayesianPerimeter.quarantinedBedEnd} (Lubang #${bayesianPerimeter.quarantinedHoleStart} - #${bayesianPerimeter.quarantinedHoleEnd})",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Text("Tindakan Parit: ${bayesianPerimeter.isolationTrenchAction}", style = MaterialTheme.typography.labelSmall)
                            Text("Resep Kocor: ${bayesianPerimeter.biologicalDrenchingRecipe}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = {
                            val markLat = -7.123380
                            val markLon = 110.457220
                            queue?.enqueueDiagnosis(
                                landId = "land-demo-001",
                                confirmedDisease = bayesianPerimeter.diagnosedPathogen,
                                severityPct = (bayesianPerimeter.bacterialWiltProbability * 100),
                                confidence = "HIGH",
                                protocolCode = if (bayesianPerimeter.bacterialWiltProbability >= 0.5) "P-BA-RALSTONIA" else "P-FU-FUSARIUM",
                                localTwi = 8.1,
                                lon = markLon,
                                lat = markLat
                            )
                            diagMarkers.add(
                                FarmMapMarker(
                                    id = "d-${System.currentTimeMillis().toString().takeLast(4)}",
                                    lat = markLat,
                                    lon = markLon,
                                    title = bayesianPerimeter.diagnosedPathogen,
                                    diseaseType = if (bayesianPerimeter.bacterialWiltProbability >= 0.5) "RALSTONIA" else "FUSARIUM",
                                    severityPct = (bayesianPerimeter.bacterialWiltProbability * 100),
                                    localTwi = 8.1,
                                    protocolCode = "BAYES-QUARANTINE",
                                    actionNote = "Karantina Bedengan #${bayesianPerimeter.quarantinedBedStart}-#${bayesianPerimeter.quarantinedBedEnd}"
                                )
                            )
                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Karantina Spasial Disimpan",
                                message = "Perimeter Bedengan #${bayesianPerimeter.quarantinedBedStart} s/d #${bayesianPerimeter.quarantinedBedEnd} ditandai pada Peta GIS.",
                                type = NotificationType.SUCCESS,
                                postSystemNotification = true
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(AppIcons.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Terapkan Karantina Spasial ke Peta GIS")
                    }
                } else {
                    Text(
                        "Tanaman cabai dalam keadaan segar. Pantau terus zona TWI tinggi agar perakaran terhindar dari genangan air.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- Card 3: Active Leaf Trans-Illumination (FR-2.2) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (transResult.isLatentFungalDetected) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
            ),
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
                        Icon(AppIcons.Camera, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            "3. Uji Trans-Iluminasi Daun Kontak 0 cm (FR-2.2)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Kamera kontak langsung ke daun dengan LED Flash untuk deteksi hifa laten.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Rasio Transmisi Hijau: ${transResult.greenAttenuationRatio}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text("Kerapatan Optik: ${transResult.mesophyllOpticalDensity}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        if (transResult.isLatentFungalDetected) AppIcons.Warning else AppIcons.CheckCircle,
                        contentDescription = null,
                        tint = if (transResult.isLatentFungalDetected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        transResult.latencyAdvisory,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (transResult.isLatentFungalDetected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- Card 4: Pericarp Fortification Lock & Nitrogen Blocking (FR-3.2) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (fertValidation.isCriticalBlock) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            "4. Penguncian Fortifikasi Dinding Sel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        modifier = Modifier.wrapContentSize(),
                        onClick = {},
                        leadingIcon = {
                            Icon(
                                if (fortStatus.isLocked) AppIcons.Warning else AppIcons.CheckCircle,
                                contentDescription = null,
                                tint = if (fortStatus.isLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = { Text(if (fortStatus.isLocked) "LOCKED" else "NORMAL", maxLines = 1, softWrap = false) }
                    )
                }

                Text(fortStatus.advisory, style = MaterialTheme.typography.bodySmall)

                HorizontalDivider()

                Text("Simulasi Input Pupuk Petani:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Urea 46%" to 46.0, "Kalsium Nitrat" to 15.0, "NPK 16-16-16" to 16.0).forEach { (name, nPct) ->
                        FilterChip(
                            selected = proposedFertilizer == name,
                            onClick = {
                                proposedFertilizer = name
                                proposedNitrogenPct = nPct

                                val check = PericarpFortificationManager.validateFertilizerProposal(
                                    fertilizerName = name,
                                    nitrogenPct = nPct,
                                    isLocked = fortStatus.isLocked
                                )
                                if (check.isCriticalBlock) {
                                    PentasixNotificationHub.notify(
                                        context = context,
                                        title = "Peringatan Fortifikasi",
                                        message = check.warningMessage,
                                        type = NotificationType.WARNING,
                                        postSystemNotification = true
                                    )
                                } else {
                                    PentasixNotificationHub.notify(
                                        context = context,
                                        title = "Pupuk Terverifikasi",
                                        message = "Dosis $name ($nPct% N) aman bagi perikarp buah.",
                                        type = NotificationType.INFO,
                                        postSystemNotification = false
                                    )
                                }
                            },
                            label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if (fertValidation.isCriticalBlock) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Text(
                            fertValidation.warningMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Tindakan Korektif Wajib: ${fertValidation.correctiveAction}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else {
                    Text(
                        fertValidation.warningMessage,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Stoichiometry Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Protokol Stoikiometri Fortifikasi Perikarp:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text("• Kalsium Nitrat (Ca(NO3)2): ${fortStatus.prescription.calciumNitrateGPerL} g/L", style = MaterialTheme.typography.bodySmall)
                        Text("• Boron (B): ${fortStatus.prescription.boronGPerL} g/L", style = MaterialTheme.typography.bodySmall)
                        Text("• Asam Silikat: ${fortStatus.prescription.silicicAcidGPerL} g/L", style = MaterialTheme.typography.bodySmall)
                        Text("• Interval: Tiap ${fortStatus.prescription.frequencyDays} hari sekali kocor/semprot kabut.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // --- Card 5: Estimasi Populasi Buah & Yield On-Device (FR-2.1) ---
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
                        Icon(AppIcons.TrendingUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            "5. Estimasi Populasi Buah & Hasil Panen (YOLO Yield)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Deteksi on-device buah hijau, semburat, dan merah matang.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Buah Hijau: $greenFruits", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    Text("Semburat: $turningFruits", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    Text("Merah Matang: $ripeFruits", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Total Buah/Pohon", style = MaterialTheme.typography.bodySmall)
                        Text("${yieldResult.totalFruitCount} buah", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Column {
                        Text("Estimasi per Pohon", style = MaterialTheme.typography.bodySmall)
                        Text("${yieldResult.projectedYieldKgPerPlant} kg", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Column {
                        Text("Proyeksi Tonase", style = MaterialTheme.typography.bodySmall)
                        Text("${yieldResult.projectedYieldTonPerHa} Ton/Ha", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        // --- Card 6: Early Warning System (EWS) Mikroklimat 72 Jam (FR-3.1) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            var ewsTemp by remember { mutableStateOf(28.0) }
            var ewsHumidity by remember { mutableStateOf(85.0) }
            val ewsRisk = remember(ewsTemp, ewsHumidity) {
                id.peritanix.dape.android.core.physics.MicroclimateEwsEvaluator.evaluate72hFungalRisk(
                    relativeHumidityPct = ewsHumidity,
                    temperatureC = ewsTemp
                )
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            "6. EWS Mikroklimat 72 Jam (Spora)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        modifier = Modifier.wrapContentSize(),
                        onClick = {},
                        leadingIcon = {
                            Icon(
                                if (ewsRisk.riskLevel == "CRITICAL" || ewsRisk.riskLevel == "HIGH") AppIcons.Warning else AppIcons.CheckCircle,
                                contentDescription = null,
                                tint = if (ewsRisk.riskLevel == "CRITICAL" || ewsRisk.riskLevel == "HIGH") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = { Text(ewsRisk.riskLevel, maxLines = 1, softWrap = false) }
                    )
                }

                Text("Kelembaban Relatif (RH): ${ewsHumidity.toInt()}%")
                Slider(
                    value = ewsHumidity.toFloat(),
                    onValueChange = { ewsHumidity = it.toDouble() },
                    valueRange = 40f..100f
                )

                Text("Suhu Rata-rata: ${"%.1f".format(ewsTemp)}°C")
                Slider(
                    value = ewsTemp.toFloat(),
                    onValueChange = { ewsTemp = it.toDouble() },
                    valueRange = 18f..38f
                )

                HorizontalDivider()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Skor Risiko Perkecambahan Spora:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${(ewsRisk.sporulationRiskScore * 100).toInt()}%",
                        fontWeight = FontWeight.Bold,
                        color = if (ewsRisk.riskLevel == "CRITICAL" || ewsRisk.riskLevel == "HIGH") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    ewsRisk.alertHeader,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (ewsRisk.riskLevel == "CRITICAL") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text(
                    ewsRisk.preventiveSprayAdvisory,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Irrigation schedule (menit/bedengan) hedged against fungal risk.
                val irrigation = id.peritanix.dape.android.core.physics.MicroclimateEwsEvaluator
                    .recommendIrrigation(
                        riskScore = ewsRisk.sporulationRiskScore,
                        soilMoisturePct = ewsHumidity.coerceIn(20.0, 80.0) // proxy: RH→soil moisture
                    )
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Jadwal Irigasi Presisi:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text("${irrigation.minutesPerBed} mnt/bedengan · tiap ${irrigation.intervalHours} j", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    irrigation.rationale,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- Card 7: Peta Sebaran Spasial Infeksi Penyakit & Korelasi TWI ---
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
                        Icon(AppIcons.Map, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            "7. Peta Sebaran Spasial Infeksi & Korelasi TWI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Korelasi titik infeksi patologi dengan zona genangan air dan arah angin spora.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                InteractiveFarmMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    polygonPoints = demoPolygon,
                    twiCells = diagTwiGrid,
                    diseaseMarkers = diagMarkers,
                    flowBearingDeg = 140f,
                    windBearingDeg = 65f,
                    isInteractive = true,
                    showControls = true,
                    showLayerToggles = true
                )
            }
        }
    }
}
