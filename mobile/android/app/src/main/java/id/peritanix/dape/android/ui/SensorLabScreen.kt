package id.peritanix.dape.android.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.LocationManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.peritanix.dape.android.core.hardware.CompassCalibration
import id.peritanix.dape.android.core.hardware.GnssBiomassEstimator
import id.peritanix.dape.android.core.hardware.ProximityAutoTrigger
import id.peritanix.dape.android.ui.components.AppIcons

/**
 * "Sensor Lab" — surfaces three sensor-matrix capabilities that have no other
 * screen in the app:
 *   1. GNSS biomass estimator  — raw C/N0 under canopy → leaf-density proxy.
 *   2. Proximity auto-trigger  — 0 cm leaf contact → auto trans-illumination.
 *   3. Figure-eight compass    — octant-coverage convergence + tilt zero-point.
 *
 * All three use real device sensors; the deterministic math lives in
 * core/hardware (unit-tested). This screen is the thin Compose wrapper.
 */
@Composable
fun SensorLabScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Sensors, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Column {
                Text("Sensor Lab", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Eksploitasi sensor bawaan HP (100% offline)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        GnssBiomassCard(context)
        ProximityTriggerCard(context)
        CompassFigure8Card(context)
        Spacer(Modifier.height(8.dp))
    }
}

// -----------------------------------------------------------------------------
// 1. GNSS biomass (raw C/N0)
// -----------------------------------------------------------------------------

@Composable
private fun GnssBiomassCard(context: Context) {
    var estimate by remember { mutableStateOf<GnssBiomassEstimator.BiomassEstimate?>(null) }
    var status by remember { mutableStateOf("Menunggu sinyal GPS…") }
    var listening by remember { mutableStateOf(false) }

    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager }
    val callback = remember { object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(s: GnssStatus) {
            val cn0 = (0 until s.satelliteCount).map { s.getCn0DbHz(it) }
            estimate = GnssBiomassEstimator.estimate(cn0)
        }
    } }

    DisposableEffect(listening) {
        onDispose {
            if (listening) try { locationManager?.unregisterGnssStatusCallback(callback) } catch (_: Exception) {}
        }
    }

    SensorSectionCard(
        icon = AppIcons.Satellite,
        title = "Kerapatan Tajuk dari Sinyal GNSS (C/N0)",
        subtitle = "Atenuasi sinyal satelit di bawah tajuk → estimasi biomassa daun"
    ) {
        Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        estimate?.let { e ->
            if (e.satelliteCount > 0) {
                InfoRow("Satelit valid", "${e.satelliteCount}")
                InfoRow("Rata-rata C/N0", "${e.meanCn0DbHz} dB-Hz")
                InfoRow("Atenuasi tajuk", "${e.attenuationDb} dB")
                InfoRow("Kerapatan tajuk", "${e.canopyDensityPct}% → ${e.densityClass}")
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                    Text(e.biomassNote, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(10.dp))
                }
            }
        }
        Button(
            onClick = {
                listening = !listening
                if (listening) {
                    try {
                        locationManager?.registerGnssStatusCallback(context.mainExecutor, callback)
                        status = "Mengukur C/N0 satelit… arahkan HP ke atas di samping tanaman."
                    } catch (e: Exception) {
                        status = "GPS tidak tersedia: ${e.message}"
                        listening = false
                    }
                } else {
                    try { locationManager?.unregisterGnssStatusCallback(callback) } catch (_: Exception) {}
                    status = "Pengukuran dihentikan."
                }
            },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        ) { Text(if (listening) "Stop Ukur GNSS" else "Mulai Ukur GNSS") }
    }
}

// -----------------------------------------------------------------------------
// 2. Proximity auto-trigger (trans-illumination)
// -----------------------------------------------------------------------------

@Composable
private fun ProximityTriggerCard(context: Context) {
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val proximity = remember { sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY) }
    val debouncer = remember { ProximityAutoTrigger.Debouncer() }
    var state by remember { mutableStateOf(ProximityAutoTrigger.ProximityState.FAR) }
    var lastAction by remember { mutableStateOf("—") }
    var nearMs by remember { mutableStateOf(0L) }
    var available by remember { mutableStateOf(proximity != null) }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_PROXIMITY) return
                // NEAR when value < maxRange (device-specific); most phones report 0 for near.
                val max = event.sensor.maximumRange
                val isNear = event.values[0] < max - 0.0001f
                val ps = if (isNear) ProximityAutoTrigger.ProximityState.NEAR else ProximityAutoTrigger.ProximityState.FAR
                state = ps
                val decision = debouncer.update(ps, System.currentTimeMillis())
                nearMs = decision.nearDurationMs
                if (decision.action != ProximityAutoTrigger.Action.NONE && decision.message.isNotBlank()) {
                    lastAction = "${decision.action} — ${decision.message}"
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (proximity != null) sensorManager?.registerListener(listener, proximity, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    SensorSectionCard(
        icon = AppIcons.Sensors,
        title = "Trigger Trans-Illumination Otomatis (Proximity)",
        subtitle = "Tempelkan daun ke layar (0 cm) → flash & foto otomatis"
    ) {
        if (!available) {
            Text("Sensor jarak tidak tersedia di perangkat ini.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        } else {
            InfoRow("Status sensor", if (state == ProximityAutoTrigger.ProximityState.NEAR) "NEAR (daun menempel)" else "FAR")
            InfoRow("Durasi kontak", "$nearMs ms")
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                Text(lastAction, fontSize = 11.sp, modifier = Modifier.padding(10.dp))
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 3. Figure-eight compass calibration
// -----------------------------------------------------------------------------

@Composable
private fun CompassFigure8Card(context: Context) {
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val magSensor = remember { sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    val acc = remember { CompassCalibration.Accumulator() }
    var stage by remember { mutableStateOf(CompassCalibration.evaluateOctants(BooleanArray(8), 0, 0.0, 0.0)) }
    var running by remember { mutableStateOf(false) }

    DisposableEffect(running) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_MAGNETIC_FIELD) return
                acc.addSample(CompassCalibration.MagSample(event.values[0], event.values[1], event.values[2]))
                stage = acc.evaluate()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (running && magSensor != null) sensorManager?.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    SensorSectionCard(
        icon = AppIcons.Compass,
        title = "Kalibrasi Figure-Eight Kompas",
        subtitle = "Gerakkan HP membentuk angka 8 untuk offset besi keras"
    ) {
        if (magSensor == null) {
            Text("Magnetometer tidak tersedia.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        } else {
            LinearProgressIndicator(
                progress = { stage.octantsCovered / 8f },
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
            Text("Oktan: ${stage.octantsCovered}/8 · Sampel: ${stage.samples} · Sebaran: ${stage.spreadRatio}",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(color = if (stage.isConverged) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                Text(stage.message, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
            }
            Button(
                onClick = {
                    running = !running
                    if (running) { acc.reset(); stage = acc.evaluate() }
                },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            ) { Text(if (running) "Selesai / Stop" else "Mulai Kalibrasi Figure-Eight") }
        }
    }
}

// --- shared helpers ----------------------------------------------------------

@Composable
private fun SensorSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
