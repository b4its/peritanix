package id.peritanix.dape.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.peritanix.dape.android.core.hardware.AcousticAnemometer
import id.peritanix.dape.android.core.hardware.BarometricStormGuard
import id.peritanix.dape.android.core.physics.DailyWeatherSummary
import id.peritanix.dape.android.core.physics.RainEvent
import id.peritanix.dape.android.core.physics.WeatherCondition
import id.peritanix.dape.android.core.physics.WeatherRepository
import id.peritanix.dape.android.core.physics.WeatherSyncResult
import id.peritanix.dape.android.ui.components.AppIcons
import id.peritanix.dape.android.ui.components.NotificationType
import id.peritanix.dape.android.ui.components.PentasixNotificationHub
import id.peritanix.dape.android.ui.theme.OrangePrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Weather & Rain Monitoring Screen.
 *
 * Provides:
 * 1. Offline current weather condition observation.
 * 2. Real-time Rain Stopwatch with duration tracking & pathogen risk logging.
 * 3. Historical recorded rain events list.
 * 4. Online multi-model weather sync (Open-Meteo) for 30-day past history (GDD) & 7-day forecast.
 */
@Composable
fun WeatherScreen(
    weatherRepo: WeatherRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isRaining by remember { mutableStateOf(weatherRepo.isRaining) }
    var currentCondition by remember { mutableStateOf(weatherRepo.currentCondition) }
    var rainStartTime by remember { mutableStateOf(weatherRepo.rainStartTimeMs) }
    var rainEvents by remember { mutableStateOf(weatherRepo.getRainEvents()) }
    var syncResult by remember { mutableStateOf(weatherRepo.lastSyncResult) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("") }

    // Live Stopwatch timer ticker
    var elapsedSeconds by remember {
        mutableStateOf(
            if (isRaining && rainStartTime > 0L)
                (System.currentTimeMillis() - rainStartTime) / 1000L
            else 0L
        )
    }

    LaunchedEffect(isRaining, rainStartTime) {
        while (isRaining && rainStartTime > 0L) {
            elapsedSeconds = (System.currentTimeMillis() - rainStartTime) / 1000L
            delay(1000L)
        }
    }

    // State: Tahap 4.4 Barometric Storm Guard & Acoustic Anemometer
    val anemometer = remember { AcousticAnemometer(context) }
    var windSpeedKmh by remember { mutableStateOf(6.5) }
    var baroPressureCurrent by remember { mutableStateOf(1012.4) }
    var baroPressureDelta2Hr by remember { mutableStateOf(-0.4) }
    var windStatusMsg by remember { mutableStateOf("") }

    val stormVerdict = remember(baroPressureCurrent, baroPressureDelta2Hr) {
        val dummySamples = listOf(
            BarometricStormGuard.PressureReading(System.currentTimeMillis() - 7200000L, baroPressureCurrent - baroPressureDelta2Hr),
            BarometricStormGuard.PressureReading(System.currentTimeMillis(), baroPressureCurrent)
        )
        BarometricStormGuard.evaluateBarometricTrend(dummySamples)
    }

    val sprayLockout = remember(windSpeedKmh, stormVerdict) {
        BarometricStormGuard.evaluateSprayLockout(windSpeedKmh, stormVerdict)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. Header & Online Sync Action Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
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
                                imageVector = AppIcons.WbSunny,
                                contentDescription = "Cuaca",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                "Cuaca & Hujan Lahan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                "Kopeng (850 mdpl) • Agroklimat",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    // Online/Offline Pill
                    Surface(
                        color = if (syncResult?.isOnlineSynced == true) Color(0xFF2E7D32).copy(alpha = 0.12f) else OrangePrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.wrapContentSize()
                    ) {
                        Text(
                            text = if (syncResult?.isOnlineSynced == true) "Online ECMWF" else "Offline Mode",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (syncResult?.isOnlineSynced == true) Color(0xFF2E7D32) else OrangePrimary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

                // Sync Weather Button & Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Sinkronisasi Cuaca Online (Open-Meteo)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            if (syncMessage.isNotEmpty()) syncMessage
                            else "Historis 30 hari + Prakiraan 7 hari",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            if (!isSyncing) {
                                isSyncing = true
                                syncMessage = "Mengunduh data..."
                                coroutineScope.launch {
                                    val res = weatherRepo.syncWeatherOnline()
                                    isSyncing = false
                                    res.onSuccess { data ->
                                        syncResult = data
                                        currentCondition = data.currentCondition
                                        syncMessage = "Berhasil disinkronkan (${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())})"

                                        PentasixNotificationHub.notify(
                                            context = context,
                                            title = "Sinkronisasi Cuaca Berhasil",
                                            message = "GDD 30 hari (${data.accumulatedGdd30Days.toInt()} °Cd) dan prakiraan 7 hari diperbarui dari Open-Meteo.",
                                            type = NotificationType.SUCCESS,
                                            postSystemNotification = true
                                        )
                                    }.onFailure { err ->
                                        syncMessage = "Gagal koneksi. Menggunakan baseline offline."

                                        PentasixNotificationHub.notify(
                                            context = context,
                                            title = "Sinkronisasi Cuaca Gagal",
                                            message = "Tidak dapat terhubung ke Open-Meteo (${err.message ?: "Periksa koneksi"}). Menggunakan data baseline lokal.",
                                            type = NotificationType.ERROR,
                                            postSystemNotification = true
                                        )
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        modifier = Modifier.wrapContentSize(),
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(AppIcons.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sinkron Cuaca", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // --- 2. Live Rain Tracker & Stopwatch (If Raining or Controlled) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isRaining) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surface
            ),
            border = if (isRaining) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF1976D2)) else null,
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(if (isRaining) Color(0xFF1976D2) else Color.Gray, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isRaining) AppIcons.Thunderstorm else AppIcons.Umbrella,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                if (isRaining) "SEDANG HUJAN DI LAHAN!" else "Pemantau Hujan & Stopwatch",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isRaining) Color(0xFF0D47A1) else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                if (isRaining) "Stopwatch durasi pembasahan kanopi aktif"
                                else "Tandai saat hujan mulai untuk mencatat durasi",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    if (isRaining) {
                        Surface(
                            color = Color(0xFFD32F2F),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "LIVE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // If Raining: Stopwatch Display
                if (isRaining) {
                    val hrs = elapsedSeconds / 3600
                    val mins = (elapsedSeconds % 3600) / 60
                    val secs = elapsedSeconds % 60
                    val timeStr = String.format("%02d:%02d:%02d", hrs, mins, secs)

                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("DURASI HUJAN SAAT INI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = timeStr,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF0D47A1)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Intensitas: ${currentCondition.label} (~${currentCondition.defaultRainRateMmH} mm/jam)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Leaf Wetness Alert based on elapsed duration
                    val wetnessAlert = when {
                        elapsedSeconds >= 10800 -> "PERINGATAN KRITIS: Daun basah > 3 jam! Spora Colletotrichum berkecambah, risiko infeksi antraknosa buah tinggi!"
                        elapsedSeconds >= 3600 -> "Waspada: Pembasahan daun > 1 jam. Pantau kelancaran saluran parit drainase bedengan."
                        else -> "Pembasahan kanopi awal. Kelembapan tanah meningkat."
                    }
                    val alertColor = if (elapsedSeconds >= 10800) Color(0xFFD32F2F) else Color(0xFFE65100)

                    Surface(
                        color = alertColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(AppIcons.Warning, contentDescription = null, tint = alertColor, modifier = Modifier.size(16.dp))
                            Text(wetnessAlert, fontSize = 11.sp, color = alertColor, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Button: End Rain
                    Button(
                        onClick = {
                            val event = weatherRepo.stopRain()
                            isRaining = false
                            rainStartTime = 0L
                            currentCondition = weatherRepo.currentCondition
                            rainEvents = weatherRepo.getRainEvents()

                            val durStr = event.durationFormatted
                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Hujan Ditandai Berakhir",
                                message = "Pencatatan hujan selesai ($durStr). Data tersimpan ke histori kejadian hujan.",
                                type = NotificationType.RAIN,
                                postSystemNotification = true
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(AppIcons.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tandai Hujan Berakhir", fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Not Raining: Option to Start Rain Stopwatch
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                rainStartTime = weatherRepo.startRain(WeatherCondition.HUJAN_SEDANG)
                                isRaining = true
                                currentCondition = WeatherCondition.HUJAN_SEDANG

                                PentasixNotificationHub.notify(
                                    context = context,
                                    title = "Pencatatan Hujan Dimulai",
                                    message = "Kondisi: Hujan Sedang. Stopwatch presipitasi aktif memantau spora patogen.",
                                    type = NotificationType.RAIN,
                                    postSystemNotification = true
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(AppIcons.Umbrella, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Mulai Hujan", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                rainStartTime = weatherRepo.startRain(WeatherCondition.HUJAN_LEBAT)
                                isRaining = true
                                currentCondition = WeatherCondition.HUJAN_LEBAT

                                PentasixNotificationHub.notify(
                                    context = context,
                                    title = "Peringatan Hujan Deras",
                                    message = "Kondisi: Hujan Lebat / Badai. Waspada risiko antraknosa & kelayuan tinggi.",
                                    type = NotificationType.WARNING,
                                    postSystemNotification = true
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(AppIcons.Thunderstorm, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Hujan Deras / Badai", fontSize = 12.sp)
                        }
                    }

                    // Last rain event pill if any
                    weatherRepo.lastFinishedRainEvent?.let { last ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Hujan Terakhir: ${last.dateFormatted}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Durasi: ${last.durationFormatted} • Curah: ~${last.estimatedRainfallMm} mm", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Surface(
                                    color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("Tercatat", color = Color(0xFF2E7D32), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 2.5. Pengunci Keamanan Semprot & Detektor Badai Barometer (Tahap 4.4) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (!sprayLockout.isPermitted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                .background(
                                    if (!sprayLockout.isPermitted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (!sprayLockout.isPermitted) AppIcons.Shield else AppIcons.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                "Kunci Keamanan Semprot & Detektor Badai",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Barometer (ΔP/2h) & Anemometer Mikrofon (Tahap 4.4)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = if (sprayLockout.isPermitted) Color(0xFF2E7D32).copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (sprayLockout.isPermitted) "SEMPROT DIIZINKAN" else "KUNCI SEMPROT AKTIF",
                            color = if (sprayLockout.isPermitted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Status message & corrective advice
                Text(
                    text = sprayLockout.reason,
                    fontWeight = FontWeight.SemiBold,
                    color = if (!sprayLockout.isPermitted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = sprayLockout.correctiveAdvice,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

                // Live Sensor Metrics
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Kecepatan Angin (Acoustic)", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${"%.1f".format(windSpeedKmh)} km/jam",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (windSpeedKmh > 12.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Text("Batas aman: ≤ 12.0 km/jam", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Laju Barometer (2 Jam)", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${"%.2f".format(baroPressureDelta2Hr)} hPa / 2h",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (stormVerdict.isRainImminent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Text("Batas badai: ≤ -1.50 hPa", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Interactive Buttons & Wind Measurement
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val res = anemometer.measureWindSpeed(durationMs = 2000)
                            windSpeedKmh = res.windSpeedKmh
                            windStatusMsg = res.statusMessage
                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Ukur Angin Akustik",
                                message = "Kecepatan angin terdeteksi ${res.windSpeedKmh} km/jam. ${if (res.isDriftHazard) "Bahaya drift!" else "Aman semprot."}",
                                type = if (res.isDriftHazard) NotificationType.WARNING else NotificationType.SUCCESS,
                                postSystemNotification = false
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(AppIcons.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ukur Angin (Mic FFT)", fontSize = 11.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            // Toggle storm simulation
                            baroPressureDelta2Hr = if (baroPressureDelta2Hr <= -1.5) -0.3 else -1.8
                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Simulasi Tren Barometer",
                                message = "ΔP diset ke ${"%.2f".format(baroPressureDelta2Hr)} hPa / 2h.",
                                type = if (baroPressureDelta2Hr <= -1.5) NotificationType.WARNING else NotificationType.INFO,
                                postSystemNotification = false
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(AppIcons.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (baroPressureDelta2Hr <= -1.5) "Reset Baro" else "Simulasi Badai", fontSize = 11.sp, maxLines = 1)
                    }
                }

                if (windStatusMsg.isNotEmpty()) {
                    Text(
                        windStatusMsg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- 3. Offline Mode Weather Condition Selector ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Pilihan Cuaca Saat Ini (Observasi Lapangan)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Pilih kondisi cuaca riil di petak lahan untuk menyesuaikan estimasi mikroklimat & EWS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Grid of Weather Conditions
                val conditions = listOf(
                    WeatherCondition.CERAH to AppIcons.WbSunny,
                    WeatherCondition.BERAWAN to AppIcons.Cloud,
                    WeatherCondition.MENDUNG to AppIcons.Cloud,
                    WeatherCondition.HUJAN_RINGAN to AppIcons.Umbrella,
                    WeatherCondition.HUJAN_SEDANG to AppIcons.WaterDrop,
                    WeatherCondition.HUJAN_LEBAT to AppIcons.Thunderstorm
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in conditions.chunked(2)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((cond, icon) in row) {
                                val isSelected = currentCondition == cond
                                Surface(
                                    selected = isSelected,
                                    onClick = {
                                        weatherRepo.setOfflineWeather(cond)
                                        currentCondition = cond
                                        isRaining = weatherRepo.isRaining
                                        rainStartTime = weatherRepo.rainStartTimeMs

                                        PentasixNotificationHub.notify(
                                            context = context,
                                            title = "Kondisi Lapangan Diperbarui",
                                            message = "${cond.label} (${cond.defaultTempC}°C, RH ${cond.defaultRhPct}%).",
                                            type = NotificationType.INFO,
                                            postSystemNotification = false
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) OrangePrimary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else OrangePrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = cond.label,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "${cond.defaultTempC}°C • ${cond.defaultRhPct.toInt()}% RH",
                                                fontSize = 10.sp,
                                                color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. Historical 30 Days Agro-Climate & GDD Summary ---
        syncResult?.let { res ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Historis Cuaca 1 Bulan Lalu (30 Hari)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("Akumulasi GDD & Frekuensi Curah Hujan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(AppIcons.TrendingUp, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(20.dp))
                    }

                    // Key Metrics Row
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Akumulasi GDD", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${res.accumulatedGdd30Days.toInt()} °C-h", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                                Text("T_base = 15°C", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Curah Hujan", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${res.totalRainfall30DaysMm.toInt()} mm", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                                Text("${res.rainyDays30DaysCount} Hari Hujan", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Fase Fisiologis", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Pembungaan", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                Text("Fortifikasi Si", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Horizontal Scroll of 30-day daily records
                    Text("Catatan Harian 30 Hari Terakhir:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(res.history30Days.takeLast(10).reversed()) { day ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.width(100.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(day.dateDisplayFormatted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text("${day.tMinC.toInt()}° - ${day.tMaxC.toInt()}°C", fontSize = 11.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Icon(AppIcons.WaterDrop, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(10.dp))
                                        Text("${day.precipitationMm} mm", fontSize = 10.sp, color = Color(0xFF1976D2))
                                    }
                                    Text("GDD +${day.dailyGdd}", fontSize = 9.sp, color = OrangePrimary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // --- 5. Future 7-Day Forecast & Pathogen Risk ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Prakiraan Cuaca 7 Hari Kedepan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("Peringatan Dini EWS Sporulasi Jamur", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(AppIcons.Thunderstorm, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(20.dp))
                    }

                    res.forecast7Days.forEach { day ->
                        val isHighRisk = day.precipitationMm >= 10.0 || day.precipProbabilityPct >= 70
                        Surface(
                            color = if (isHighRisk) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (day.precipitationMm > 0.0) AppIcons.Thunderstorm else AppIcons.Cloud,
                                        contentDescription = null,
                                        tint = if (isHighRisk) Color(0xFFD32F2F) else OrangePrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(day.dateDisplayFormatted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(
                                            "${day.weatherLabel} • ${day.tMinC.toInt()}°-${day.tMaxC.toInt()}°C",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(AppIcons.WaterDrop, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(11.dp))
                                        Text("${day.precipitationMm} mm (${day.precipProbabilityPct}%)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (isHighRisk) {
                                        Text(
                                            "Risiko Patek Tinggi",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD32F2F)
                                        )
                                    } else {
                                        Text(
                                            "Risiko Aman",
                                            fontSize = 10.sp,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 6. Riwayat Kejadian Hujan Terakhir (Log Event) ---
        if (rainEvents.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Riwayat Kejadian Hujan Lapangan", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = {
                            weatherRepo.clearRainEvents()
                            rainEvents = emptyList()
                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Riwayat Hujan Dikosongkan",
                                message = "Semua rekaman kejadian hujan lapangan telah dihapus.",
                                type = NotificationType.INFO,
                                postSystemNotification = false
                            )
                        }) {
                            Text("Bersihkan", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    rainEvents.take(5).forEach { event ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(event.dateFormatted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Surface(
                                        color = Color(0xFF1976D2).copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            event.durationFormatted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1976D2),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    "${event.condition.label} • Estimasi Curah: ~${event.estimatedRainfallMm} mm",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    event.pathogenRiskWarning,
                                    fontSize = 10.sp,
                                    color = if (event.durationSeconds >= 10800) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
