package id.peritanix.dape.android.ui

import android.graphics.Bitmap
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.peritanix.dape.android.core.crypto.AndroidKeystoreEd25519Bridge
import id.peritanix.dape.android.core.hardware.DarkChamberFlashManager
import id.peritanix.dape.android.core.hardware.LightSensorReader
import id.peritanix.dape.android.core.onboarding.OnboardingState
import id.peritanix.dape.android.core.onboarding.OnboardingStep
import id.peritanix.dape.android.core.onboarding.WhiteReferenceReading
import id.peritanix.dape.android.data.SyncQueueStore
import id.peritanix.dape.android.ui.components.AppIcons
import id.peritanix.dape.android.ui.components.NotificationType
import id.peritanix.dape.android.ui.components.PentasixNotificationHub

/**
 * First-run onboarding gate (item 2.2): mandatory one-time spectral calibration
 * + Ed25519 key generation, 100% offline. When both are done, [onComplete] fires
 * and the app proceeds to the main shell.
 *
 * The lux check uses the real light sensor; the white-reference RGB comes from a
 * real camera capture of white paper in a sealed dark chamber.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    queue: SyncQueueStore?,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val lightReader = remember { LightSensorReader(context) }
    var state by remember { mutableStateOf(OnboardingState()) }
    var lux by remember { mutableStateOf<Float?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val controller = remember { CameraQualityController(context) }

    // Declared BEFORE the effect wiring so the camera callback can reference it.
    fun handleWhiteCapture(bmp: Bitmap) {
        // Average the centre third of the frame (the white paper fills the view).
        val w = bmp.width; val h = bmp.height
        val x0 = w / 3; val y0 = h / 3; val side = (minOf(w, h) / 3).coerceAtLeast(1)
        var sr = 0L; var sg = 0L; var sb = 0L; var n = 0
        for (y in y0 until (y0 + side).coerceAtMost(h)) {
            for (x in x0 until (x0 + side).coerceAtMost(w)) {
                val px = bmp.getPixel(x, y)
                sr += (px shr 16) and 0xFF; sg += (px shr 8) and 0xFF; sb += px and 0xFF; n++
            }
        }
        if (n == 0) { message = "Foto kosong, coba lagi."; busy = false; return }
        val r = (sr / n).toInt().coerceIn(1, 255)
        val g = (sg / n).toInt().coerceIn(1, 255)
        val b = (sb / n).toInt().coerceIn(1, 255)
        val ambient = lux ?: -1f
        val wr = DarkChamberFlashManager.calibrateSpectralWhiteReference(r, g, b, ambient)
        if (!wr.luxVerified) {
            message = "Mangkuk belum gelap (≤0.5 Lux). Tutup rapat & coba lagi."
            busy = false
            return
        }
        val reading = WhiteReferenceReading(
            rawR = r, rawG = g, rawB = b,
            rScale = wr.rScale, gScale = wr.gScale, bScale = wr.bScale,
            ambientLux = ambient, luxVerified = true
        )
        queue?.saveCalibration("device-${System.currentTimeMillis()}", wr.rScale, wr.gScale, wr.bScale, true)
        state = state.withSpectral(reading)
        busy = false
        message = null
        PentasixNotificationHub.notify(
            context = context, title = "Kalibrasi Spektral Tersimpan",
            message = "White reference R=${"%.2f".format(wr.rScale)} G=${"%.2f".format(wr.gScale)} B=${"%.2f".format(wr.bScale)}",
            type = NotificationType.SUCCESS, postSystemNotification = false
        )
        controller.close()
    }

    fun generateKey() {
        busy = true
        message = null
        try {
            val bridge = AndroidKeystoreEd25519Bridge(context)
            val pub = bridge.getPublicKeyBase64()
            state = state.withKeyPair(pub)
            // Queue the public key + white reference for the delta-sync worker.
            val wr = state.whiteReference
            if (wr != null) {
                queue?.enqueueUserKey(
                    userId = "local-device",
                    publicKeyBase64 = pub,
                    whiteRefR = wr.rScale, whiteRefG = wr.gScale, whiteRefB = wr.bScale
                )
            }
            PentasixNotificationHub.notify(
                context = context, title = "Kunci Ed25519 Dibuat",
                message = "Kunci privat tersimpan di Android Keystore (tidak dapat diekstrak).",
                type = NotificationType.SUCCESS, postSystemNotification = false
            )
        } catch (e: Exception) {
            message = "Gagal membuat kunci: ${e.message}"
        }
        busy = false
    }

    DisposableEffect(Unit) {
        lightReader.start()
        controller.onPhoto = { bmp -> if (bmp != null) handleWhiteCapture(bmp) }
        controller.onError = { message = it }
        onDispose { lightReader.stop(); controller.close() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            lux = lightReader.latestLux
            kotlinx.coroutines.delay(400)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Chili, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Text("Setup Perangkat", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Step indicator
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OnboardingStep.ordered.filter { it != OnboardingStep.DONE }.forEach { s ->
                    val active = state.step == s || (state.step == OnboardingStep.DONE)
                    val done = (s == OnboardingStep.SPECTRAL && state.spectralDone) ||
                        (s == OnboardingStep.KEYPAIR && state.keyPairDone)
                    Box(
                        Modifier.weight(1f).height(6.dp).background(
                            when {
                                done -> Color(0xFF2E7D32)
                                active -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            RoundedCornerShape(3.dp)
                        )
                    )
                }
            }

            val step = state.step
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(step.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(step.instruction, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            when (step) {
                OnboardingStep.WELCOME -> {
                    Button(
                        onClick = { state = state.copy(step = OnboardingStep.SPECTRAL) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    ) { Text("Mulai Kalibrasi") }
                }
                OnboardingStep.SPECTRAL -> {
                    // Lux readout
                    Card(
                        Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (lightReader.isDarkEnough()) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(AppIcons.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Sensor Cahaya", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(
                                    if (!lightReader.isAvailable) "Sensor cahaya tidak tersedia di perangkat ini"
                                    else "Ambient: ${lux?.let { "%.1f".format(it) } ?: "…"} Lux — " +
                                        if (lightReader.isDarkEnough()) "Gelap ✓" else "perlu ≤0.5 Lux",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (state.spectralDone) {
                        Text("White reference tersimpan ✓", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Button(onClick = { state = state.copy(step = OnboardingStep.KEYPAIR) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Text("Lanjut: Buat Kunci")
                        }
                    } else {
                        // Live camera preview for white capture
                        Card(Modifier.fillMaxWidth().height(260.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.Black)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CameraPreview(controller)
                            }
                        }
                        Button(
                            onClick = {
                                busy = true; message = null
                                if (!lightReader.isDarkEnough()) {
                                    message = "Mangkuk belum gelap (${lux?.let { "%.1f".format(it) } ?: "?"} Lux). Tutup rapat."
                                    busy = false
                                } else {
                                    controller.captureStill()
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (busy) "Memproses…" else "Ambil Referensi Putih")
                        }
                        TextButton(onClick = { state = state.copy(step = OnboardingStep.KEYPAIR) }) {
                            Text("Lewati kalibrasi (tidak disarankan)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OnboardingStep.KEYPAIR -> {
                    if (state.keyPairDone) {
                        Text("Kunci publik: ${state.publicKeyBase64?.take(16)}…", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { state = state.copy(step = OnboardingStep.DONE) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Text("Lanjut")
                        }
                    } else {
                        Button(onClick = { generateKey() }, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Icon(AppIcons.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (busy) "Membuat kunci…" else "Buat Kunci Ed25519")
                        }
                    }
                }
                OnboardingStep.DONE -> {
                    Card(
                        Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(AppIcons.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                Text("Setup selesai", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                            Text(
                                "Kalibrasi spektral & kunci kriptografi siap. Anda dapat mulai memetakan lahan dan mendiagnosis tanaman secara offline.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Masuk ke Aplikasi", fontWeight = FontWeight.Bold)
                    }
                }
            }

            message?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(it, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(10.dp), fontSize = 12.sp)
                }
            }
        }
    }
}
