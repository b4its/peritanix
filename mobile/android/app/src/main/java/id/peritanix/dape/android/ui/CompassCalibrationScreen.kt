package id.peritanix.dape.android.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.peritanix.dape.android.core.hardware.CompassSensorManager
import id.peritanix.dape.android.core.hardware.SensorCalibrationMath
import id.peritanix.dape.android.ui.components.AppIcons
import id.peritanix.dape.android.ui.components.NotificationType
import id.peritanix.dape.android.ui.components.PentasixNotificationHub
import id.peritanix.dape.android.ui.theme.OrangePrimary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Screen for Field Compass Navigation, Waterpass Leveling,
 * Sensor Figure-8 Calibration, and Device Hardware Diagnostics.
 */
@Composable
fun CompassCalibrationScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val compassManager = remember { CompassSensorManager(context) }
    var compassState by remember { mutableStateOf(CompassSensorManager.CompassState()) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Kompas & Waterpass, 1: Kalibrasi, 2: Diagnostik

    // Start/stop listening tied to Compose lifecycle
    DisposableEffect(compassManager) {
        compassManager.onStateChanged = { newState ->
            compassState = newState
        }
        compassManager.startListening()
        onDispose {
            compassManager.stopListening()
        }
    }

    val agronomicInfo = remember(compassState.smoothedAzimuth) {
        SensorCalibrationMath.evaluateAgronomicHeading(compassState.smoothedAzimuth.toDouble())
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- Header Section ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(OrangePrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Compass,
                            contentDescription = "Kompas",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            "Kompas Lahan & Kalibrasi Sensor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Orientasi Bedengan Cabai & Waterpass Digital",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Tab Switcher (Segmented buttons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TabSegmentButton(
                        text = "Kompas",
                        icon = AppIcons.Compass,
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Mode: Kompas & Waterpass",
                                message = "Menampilkan arah derajat azimuth & indikator perataan gelembung air.",
                                type = NotificationType.CALIBRATION,
                                postSystemNotification = false
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TabSegmentButton(
                        text = "Kalibrasi",
                        icon = AppIcons.RotateRight,
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Mode: Kalibrasi Sensor",
                                message = "Prosedur kalibrasi pola angka 8 & penetapan zero-tilt waterpass.",
                                type = NotificationType.CALIBRATION,
                                postSystemNotification = false
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TabSegmentButton(
                        text = "Sensor HP",
                        icon = AppIcons.Sensors,
                        selected = selectedTab == 2,
                        onClick = {
                            selectedTab = 2
                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Mode: Diagnostik HW Sensor",
                                message = "Memeriksa ketersediaan hardware magnetometer, akselerometer & giroskop.",
                                type = NotificationType.INFO,
                                postSystemNotification = false
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- Tab Content ---
        when (selectedTab) {
            0 -> CompassTabContent(compassState, agronomicInfo)
            1 -> CalibrationTabContent(compassManager, compassState)
            2 -> DiagnosticsTabContent(compassManager)
        }

        Spacer(Modifier.height(16.dp))
    }
}

// =============================================================================
// SUB-TAB 0: KOMPAS & WATERPASS
// =============================================================================
@Composable
private fun CompassTabContent(
    state: CompassSensorManager.CompassState,
    agronomic: SensorCalibrationMath.AgronomicHeading
) {
    // 1. Interactive Compass Rose & Bubble Level Canvas
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Compass Rose with rotating dial
            CompassRoseCanvas(
                azimuthDeg = state.smoothedAzimuth,
                pitchDeg = state.calibratedPitch,
                rollDeg = state.calibratedRoll,
                isLevel = state.isLevel,
                modifier = Modifier
                    .size(240.dp)
                    .padding(8.dp)
            )

            Spacer(Modifier.height(12.dp))

            // Digital Azimuth & Cardinal Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${agronomic.azimuthDeg.toInt()}°",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${agronomic.cardinalCode} - ${agronomic.cardinalName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Accuracy & Waterpass Level Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Accuracy Badge
                val (accColor, accLabel) = when (state.accuracy) {
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> Pair(Color(0xFF2E7D32), "Akurasi Tinggi")
                    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> Pair(Color(0xFFF57F17), "Akurasi Sedang")
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> Pair(Color(0xFFD32F2F), "Akurasi Rendah")
                    else -> Pair(Color.Gray, "Menyesuaikan...")
                }
                Surface(
                    color = accColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(Modifier.size(8.dp).background(accColor, CircleShape))
                        Text(accLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accColor)
                    }
                }

                // Waterpass Level Badge
                val (levelColor, levelLabel) = if (state.isLevel) {
                    Pair(Color(0xFF2E7D32), "Waterpass Datar (≤2.5°)")
                } else {
                    Pair(Color(0xFFE65100), "Miring (${"%.1f".format(state.calibratedPitch)}° / ${"%.1f".format(state.calibratedRoll)}°)")
                }
                Surface(
                    color = levelColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(AppIcons.Straighten, contentDescription = null, tint = levelColor, modifier = Modifier.size(13.dp))
                        Text(levelLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = levelColor)
                    }
                }
            }
        }
    }

    // 2. Agronomic Ridge / Guludan Intelligence Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (agronomic.isIdealBedOrientation) Color(0xFFF1F8E9) else Color(0xFFFFF8E1)
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            if (agronomic.isIdealBedOrientation) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (agronomic.isIdealBedOrientation) AppIcons.CheckCircle else AppIcons.Sprout,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = agronomic.orientationTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (agronomic.isIdealBedOrientation) Color(0xFF1B5E20) else Color(0xFFE65100)
                )
            }

            Text(
                text = agronomic.orientationAdvice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Wind Spore Vector Indicator
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Wind,
                    contentDescription = null,
                    tint = if (agronomic.isSporeWindAligned) Color(0xFFD32F2F) else Color(0xFF546E7A),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = agronomic.sporeWindRisk,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (agronomic.isSporeWindAligned) Color(0xFFB71C1C) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (agronomic.isSporeWindAligned) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// =============================================================================
// SUB-TAB 1: KALIBRASI SENSOR
// =============================================================================
@Composable
private fun CalibrationTabContent(
    manager: CompassSensorManager,
    state: CompassSensorManager.CompassState
) {
    val context = LocalContext.current

    // 1. Magnetometer Figure-8 Calibration Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.size(32.dp).background(OrangePrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.RotateRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text("Kalibrasi Magnetometer (Angka-8)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("Menghilangkan deviasi logam (Hard-Iron Bias)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(
                "Gerakkan smartphone membentuk pola angka 8 (∞) di udara selama 4-5 detik untuk memetakan deviasi medan geomagnetik lokal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Live Field Strength Monitor
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Kekuatan Medan Magnet", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${state.magneticMagnitude} µT", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    val statusColor = if (state.magneticStatus.isReliable) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                    Surface(
                        color = statusColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (state.magneticStatus.isReliable) "Normal" else "Interferensi",
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Calibration Progress Bar (when active)
            if (state.isCalibrating) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Mengumpulkan Sampel Vektor...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OrangePrimary)
                        Text("${state.calibrationProgressPct}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                    }
                    LinearProgressIndicator(
                        progress = { state.calibrationProgressPct / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = OrangePrimary,
                        trackColor = OrangePrimary.copy(alpha = 0.2f)
                    )
                }

                Button(
                    onClick = {
                        manager.cancelFigure8Calibration()
                        PentasixNotificationHub.notify(
                            context = context,
                            title = "Kalibrasi Dibatalkan",
                            message = "Pengambilan sampel vektor angka-8 dihentikan.",
                            type = NotificationType.WARNING,
                            postSystemNotification = false
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(AppIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Batalkan Kalibrasi")
                }
            } else {
                // Calibrated Offset Summary
                if (state.hardIronOffsets.isCalibrated) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                "Offset Terpasang: X: ${"%.1f".format(state.hardIronOffsets.offsetX)} | Y: ${"%.1f".format(state.hardIronOffsets.offsetY)} | Z: ${"%.1f".format(state.hardIronOffsets.offsetZ)} µT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            manager.startFigure8Calibration()
                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Kalibrasi Angka-8 Dimulai",
                                message = "Gerakkan ponsel membentuk angka 8 di udara selama 4-5 detik.",
                                type = NotificationType.CALIBRATION,
                                postSystemNotification = true
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                    ) {
                        Icon(AppIcons.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Mulai Kalibrasi Angka-8", fontSize = 12.sp)
                    }
                    if (state.hardIronOffsets.isCalibrated) {
                        OutlinedButton(
                            onClick = {
                                manager.resetFigure8Calibration()
                                PentasixNotificationHub.notify(
                                    context = context,
                                    title = "Offset Magnetik Direset",
                                    message = "Koreksi hard-iron bias dikembalikan ke default pabrik.",
                                    type = NotificationType.INFO,
                                    postSystemNotification = false
                                )
                            },
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text("Reset", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // 2. Zero-Tilt Waterpass Level Calibration Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.size(32.dp).background(Color(0xFF2E7D32), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.Straighten, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text("Kalibrasi Titik Nol (Zero Tilt)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("Menetapkan bidang datar terkalibrasi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(
                "Letakkan smartphone pada permukaan datar terverifikasi (misal: papan kayu bedengan rata), lalu tekan tombol kalibrasi agar waterpass menunjukkan 0.0° sempurna.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Live Offset Display
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Kompensasi Offset Kemiringan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Pitch: ${"%.1f".format(state.tiltOffset.pitchZeroOffset)}° | Roll: ${"%.1f".format(state.tiltOffset.rollZeroOffset)}°",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = if (state.tiltOffset.isCalibrated) Color(0xFF2E7D32).copy(alpha = 0.12f) else Color.Gray.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (state.tiltOffset.isCalibrated) "Terkalibrasi" else "Default Pabrik",
                            color = if (state.tiltOffset.isCalibrated) Color(0xFF2E7D32) else Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        manager.calibrateZeroTilt()
                        PentasixNotificationHub.notify(
                            context = context,
                            title = "Kalibrasi Nol Waterpass Disimpan",
                            message = "Posisi ponsel saat ini disimpan sebagai acuan bidang datar (0.0°).",
                            type = NotificationType.SUCCESS,
                            postSystemNotification = true
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Icon(AppIcons.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kalibrasi Titik Nol (0.0°)", fontSize = 12.sp)
                }
                if (state.tiltOffset.isCalibrated) {
                    OutlinedButton(
                        onClick = {
                            manager.resetTiltCalibration()
                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Kalibrasi Kemiringan Direset",
                                message = "Offset pitch & roll waterpass dinetralkan kembali.",
                                type = NotificationType.INFO,
                                postSystemNotification = false
                            )
                        },
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Text("Reset", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// =============================================================================
// SUB-TAB 2: DIAGNOSTIK HARDWARE SENSOR PERANGKAT
// =============================================================================
@Composable
private fun DiagnosticsTabContent(manager: CompassSensorManager) {
    val sensors = remember { manager.getAvailableSensors() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.Sensors, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text("Inventaris Sensor Hardware (${sensors.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("Sensor fisik terintegrasi pada perangkat Anda", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider()

            sensors.forEach { sensor ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sensor.typeName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "Aktif",
                                    color = Color(0xFF2E7D32),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            "Model: ${sensor.name} (${sensor.vendor} v${sensor.version})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Rentang Max: ${sensor.maxRange}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Daya: ${"%.2f".format(sensor.powerMa)} mA", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// COMPASS ROSE CANVAS WITH SPIRIT BUBBLE LEVEL
// =============================================================================
@Composable
private fun CompassRoseCanvas(
    azimuthDeg: Float,
    pitchDeg: Double,
    rollDeg: Double,
    isLevel: Boolean,
    modifier: Modifier = Modifier
) {
    val animAzimuth by animateFloatAsState(
        targetValue = azimuthDeg,
        animationSpec = tween(durationMillis = 150),
        label = "compassAzimuth"
    )

    val primaryOrangeArgb = OrangePrimary.toArgb()
    val slateNavyArgb = Color(0xFF37474F).toArgb()
    val tickColorArgb = Color(0xFF9E9E9E).toArgb()
    val whiteArgb = Color.White.toArgb()

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = (size.minDimension / 2f) - 12f

        // 1. Outer Ring Background
        drawCircle(
            color = Color(0xFF263238),
            radius = radius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = OrangePrimary.copy(alpha = 0.2f),
            radius = radius,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
        )

        // 2. Rotating Dial (Rotates opposite to device azimuth so North stays true North)
        rotate(degrees = -animAzimuth, pivot = Offset(cx, cy)) {
            // Ticks around the circle
            for (deg in 0 until 360 step 5) {
                val rad = Math.toRadians(deg.toDouble())
                val isMajor = deg % 30 == 0
                val isCardinal = deg % 90 == 0
                val tickLength = if (isCardinal) 16f else if (isMajor) 10f else 5f
                val strokeW = if (isCardinal) 3.5f else if (isMajor) 2f else 1f

                val startX = (cx + (radius - tickLength) * sin(rad)).toFloat()
                val startY = (cy - (radius - tickLength) * cos(rad)).toFloat()
                val endX = (cx + radius * sin(rad)).toFloat()
                val endY = (cy - radius * cos(rad)).toFloat()

                drawLine(
                    color = if (deg == 0) OrangePrimary else Color(tickColorArgb),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = strokeW
                )
            }

            // Cardinal Letters (U, T, S, B, and intercardinal)
            val textPaint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }

            val cardinals = listOf(
                0 to Pair("U", primaryOrangeArgb),
                45 to Pair("TL", whiteArgb),
                90 to Pair("T", whiteArgb),
                135 to Pair("TG", whiteArgb),
                180 to Pair("S", whiteArgb),
                225 to Pair("BD", whiteArgb),
                270 to Pair("B", whiteArgb),
                315 to Pair("BL", whiteArgb)
            )

            for ((deg, data) in cardinals) {
                val rad = Math.toRadians(deg.toDouble())
                val labelRadius = radius - 28f
                val lx = (cx + labelRadius * sin(rad)).toFloat()
                val ly = (cy - labelRadius * cos(rad) + 6f).toFloat() // vertical center adjustment

                textPaint.color = data.second
                textPaint.textSize = if (deg % 90 == 0) 18f else 12f
                drawContext.canvas.nativeCanvas.drawText(data.first, lx, ly, textPaint)
            }

            // Degree marks (0, 60, 120, 180, 240, 300)
            val degPaint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = 9f
                color = Color(0xFFB0BEC5).toArgb()
            }
            for (deg in listOf(30, 60, 120, 150, 210, 240, 300, 330)) {
                val rad = Math.toRadians(deg.toDouble())
                val rDeg = radius - 44f
                val dx = (cx + rDeg * sin(rad)).toFloat()
                val dy = (cy - rDeg * cos(rad) + 3f).toFloat()
                drawContext.canvas.nativeCanvas.drawText("$deg°", dx, dy, degPaint)
            }

            // Compass Needle (Points North-South)
            val needleLength = radius - 55f
            val needleWidth = 14f

            // North Half (Orange)
            val northPathLeft = Path().apply {
                moveTo(cx, cy)
                lineTo(cx - needleWidth / 2f, cy)
                lineTo(cx, cy - needleLength)
                close()
            }
            val northPathRight = Path().apply {
                moveTo(cx, cy)
                lineTo(cx + needleWidth / 2f, cy)
                lineTo(cx, cy - needleLength)
                close()
            }
            drawPath(northPathLeft, Color(0xFFFF9800))
            drawPath(northPathRight, OrangePrimary)

            // South Half (Slate Gray)
            val southPathLeft = Path().apply {
                moveTo(cx, cy)
                lineTo(cx - needleWidth / 2f, cy)
                lineTo(cx, cy + needleLength)
                close()
            }
            val southPathRight = Path().apply {
                moveTo(cx, cy)
                lineTo(cx + needleWidth / 2f, cy)
                lineTo(cx, cy + needleLength)
                close()
            }
            drawPath(southPathLeft, Color(0xFF78909C))
            drawPath(southPathRight, Color(0xFF455A64))
        }

        // 3. Fixed Lubber Line (Top Center Marker indicating phone direction)
        val lubberPath = Path().apply {
            moveTo(cx, cy - radius - 8f)
            lineTo(cx - 8f, cy - radius + 8f)
            lineTo(cx + 8f, cy - radius + 8f)
            close()
        }
        drawPath(lubberPath, OrangePrimary)

        // 4. Center Spirit Bubble Level (Waterpass)
        val levelRadius = 36f
        val toleranceRadius = 12f

        // Outer level circle
        drawCircle(
            color = Color(0x33000000),
            radius = levelRadius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = if (isLevel) Color(0xFF2E7D32) else Color(0x88FFFFFF),
            radius = levelRadius,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
        )

        // Inner target tolerance ring (represents ±2.5° flat zone)
        drawCircle(
            color = if (isLevel) Color(0x442E7D32) else Color(0x22FFFFFF),
            radius = toleranceRadius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = if (isLevel) Color(0xFF4CAF50) else Color(0x66FFFFFF),
            radius = toleranceRadius,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
        )

        // Center crosshair
        drawLine(
            color = if (isLevel) Color(0xFF4CAF50) else Color(0x66FFFFFF),
            start = Offset(cx - 6f, cy),
            end = Offset(cx + 6f, cy),
            strokeWidth = 1f
        )
        drawLine(
            color = if (isLevel) Color(0xFF4CAF50) else Color(0x66FFFFFF),
            start = Offset(cx, cy - 6f),
            end = Offset(cx, cy + 6f),
            strokeWidth = 1f
        )

        // Spirit Bubble: Position calculated from roll (X) and pitch (Y)
        val pixelsPerDegree = 3.0f
        val bubbleOffsetMax = levelRadius - 8f
        val bx = (cx + (rollDeg * pixelsPerDegree).toFloat()).coerceIn(cx - bubbleOffsetMax, cx + bubbleOffsetMax)
        val by = (cy + (pitchDeg * pixelsPerDegree).toFloat()).coerceIn(cy - bubbleOffsetMax, cy + bubbleOffsetMax)

        // Bubble graphic
        drawCircle(
            color = if (isLevel) Color(0xEE2E7D32) else Color(0xEEFF9800),
            radius = 8f,
            center = Offset(bx, by)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.8f),
            radius = 3f,
            center = Offset(bx - 2f, by - 2f) // Light highlight reflection
        )
    }
}

// =============================================================================
// TAB SEGMENT BUTTON HELPER
// =============================================================================
@Composable
private fun TabSegmentButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

