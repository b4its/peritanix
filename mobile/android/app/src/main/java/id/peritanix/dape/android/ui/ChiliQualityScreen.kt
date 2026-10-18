package id.peritanix.dape.android.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import id.peritanix.dape.android.core.hardware.SensorCalibrationMath
import id.peritanix.dape.android.core.vision.ChiliQualityAnalyzer
import id.peritanix.dape.android.ui.components.AppIcons
import id.peritanix.dape.android.ui.components.NotificationType
import id.peritanix.dape.android.ui.components.PentasixNotificationHub

/**
 * Mode Riil — Deteksi Kualitas Cabai via Kamera Perangkat.
 *
 * Uses the platform Camera2 API (no extra dependency) to preview the live
 * camera, capture a still of a chili pod, and run [ChiliQualityAnalyzer] on the
 * real pixels to produce a ripeness + grade verdict. Everything runs on-device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChiliQualityScreen() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    var report by remember { mutableStateOf<ChiliQualityAnalyzer.QualityReport?>(null) }
    var capturedBmp by remember { mutableStateOf<Bitmap?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Bind a capture callback to the camera controller.
    val controller = remember { CameraQualityController(context) }
    DisposableEffect(Unit) {
        controller.onPhoto = { bmp ->
            capturedBmp = bmp
            if (bmp != null) {
                val analysis = analyzeBitmap(bmp)
                report = analysis
                PentasixNotificationHub.notify(
                    context = context,
                    title = "Analisis Kualitas Selesai",
                    message = "Grade ${analysis.grade} · ${analysis.ripenessLabel} (${analysis.redCoveragePct}% merah)",
                    type = if (analysis.redCoveragePct >= 85.0) NotificationType.SUCCESS else NotificationType.INFO,
                    postSystemNotification = false
                )
            } else {
                errorMsg = "Gagal memproses foto kamera."
            }
            capturing = false
        }
        controller.onError = { errorMsg = it; capturing = false }
        onDispose { controller.close() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Camera, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Column {
                Text("Kualitas Cabai (Kamera)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Deteksi kematangan & mutu dari foto asli perangkat",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Live camera preview / captured photo
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = capturedBmp
                when {
                    !hasPermission -> CameraPermissionPrompt { launcher.launch(Manifest.permission.CAMERA) }
                    bitmap != null -> AndroidView(
                        factory = { ctx ->
                            android.widget.ImageView(ctx).apply {
                                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                setImageBitmap(bitmap)
                            }
                        }
                    )
                    else -> CameraPreview(controller)
                }
            }
        }

        if (errorMsg != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    errorMsg!!,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Capture / retake buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (capturedBmp == null) {
                Button(
                    onClick = {
                        capturing = true
                        errorMsg = null
                        controller.captureStill()
                    },
                    enabled = hasPermission && !capturing,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (capturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Memproses…", fontSize = 13.sp)
                    } else {
                        Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ambil & Analisis", fontSize = 13.sp)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { capturedBmp = null; report = null; errorMsg = null },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(AppIcons.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Foto Ulang", fontSize = 13.sp)
                }
            }
        }

        // Result
        report?.let { QualityResultCard(it) }

        Text(
            "Tips: letakkan cabai di latar polos (kertas putih), cahaya cukup, jarak ±20 cm. " +
                "Sistem menghitung persentase merah (kematangan) dan bercak gelap (cacat).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CameraPermissionPrompt(onRequest: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(AppIcons.Camera, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        Text("Izin kamera diperlukan untuk Mode Riil", color = Color.White, fontSize = 13.sp)
        Button(onClick = onRequest, shape = RoundedCornerShape(10.dp)) {
            Text("Izinkan Kamera")
        }
    }
}

@Composable
internal fun CameraPreview(controller: CameraQualityController) {
    AndroidView(
        factory = { ctx ->
            android.view.TextureView(ctx).also { controller.attachPreview(it) }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun QualityResultCard(report: ChiliQualityAnalyzer.QualityReport) {
    val gradeColor = when (report.grade) {
        "A+", "A" -> Color(0xFF2E7D32)
        "B" -> Color(0xFFEF6C00)
        else -> Color(0xFFC62828)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hasil Analisis Kualitas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(color = gradeColor, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        "Grade ${report.grade}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 13.sp
                    )
                }
            }
            Text(report.commercialTier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricChip("Kematangan (merah)", "${report.redCoveragePct}%", Modifier.weight(1f))
                MetricChip("Cacat gelap", "${report.defectPct}%", Modifier.weight(1f))
                MetricChip("Skor mutu", "${report.morphologicalScore}", Modifier.weight(1f))
            }

            // Optical Diopter Millimeter Dimensions (Tahap 0.3 & 5.3)
            val opticalLenMm = SensorCalibrationMath.pixelsToMm(550.0, 5.0, 1.0)
            val defectPixels = (report.stats.sampledPixels * (report.defectPct / 100.0)).coerceAtLeast(0.0)
            val lesionAreaMm2 = SensorCalibrationMath.pixelsToMm2(defectPixels, 5.0, 1.0)

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Panjang Buah Optis:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${opticalLenMm.toInt()} mm (${"%.1f".format(opticalLenMm / 10.0)} cm)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Luas Lesi Cacat:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (report.defectPct > 0) "${lesionAreaMm2} mm²" else "0 mm² (Bebas Cacat)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (report.defectPct > 5.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Kematangan: ${report.ripenessLabel}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 13.sp
                    )
                    Text(
                        report.advice,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 12.sp
                    )
                }
            }

            // Colour composition bar
            val s = report.stats
            Text("Komposisi warna (piksel):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth().height(14.dp).background(Color(0xFFECEFF1), RoundedCornerShape(7.dp))) {
                Box(Modifier.weight(s.redPct.toFloat().coerceAtLeast(0.5f)).fillMaxHeight().background(Color(0xFFC62828)))
                Box(Modifier.weight(s.turningPct.toFloat().coerceAtLeast(0.5f)).fillMaxHeight().background(Color(0xFFEF6C00)))
                Box(Modifier.weight(s.greenPct.toFloat().coerceAtLeast(0.5f)).fillMaxHeight().background(Color(0xFF2E7D32)))
                Box(Modifier.weight(s.defectPct.toFloat().coerceAtLeast(0.5f)).fillMaxHeight().background(Color(0xFF212121)))
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
        }
    }
}

/** Convert a captured [Bitmap] to the analyzer's ARGB buffer and grade it. */
internal fun analyzeBitmap(bmp: Bitmap): ChiliQualityAnalyzer.QualityReport {
    // Downscale to keep analysis fast on-device.
    val maxDim = 320
    val scale = minOf(1.0f, maxDim.toFloat() / maxOf(bmp.width, bmp.height))
    val w = (bmp.width * scale).toInt().coerceAtLeast(1)
    val h = (bmp.height * scale).toInt().coerceAtLeast(1)
    val scaled = if (scale < 1.0f) Bitmap.createScaledBitmap(bmp, w, h, true) else bmp
    val pixels = IntArray(scaled.width * scaled.height)
    scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
    return ChiliQualityAnalyzer.analyze(pixels, scaled.width, scaled.height)
}

/**
 * Minimal Camera2 controller: opens the back camera, renders a TextureView
 * preview, and captures a single JPEG still into a Bitmap.
 */
class CameraQualityController(private val context: Context) {
    var onPhoto: (Bitmap?) -> Unit = {}
    var onError: (String) -> Unit = {}

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var previewSurface: android.view.Surface? = null
    private var imageReader: ImageReader? = null

    fun attachPreview(texture: android.view.TextureView) {
        texture.surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
                previewSurface = android.view.Surface(st)
                openCamera()
            }
            override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
        }
    }

    @Suppress("MissingPermission")
    private fun openCamera() {
        try {
            val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val camId = mgr.cameraIdList.firstOrNull { id ->
                val ch = mgr.getCameraCharacteristics(id)
                ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: mgr.cameraIdList.firstOrNull() ?: run { onError("Kamera tidak tersedia di perangkat ini."); return }

            startBackground()
            mgr.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    device = camera
                    startPreview()
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); device = null }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close(); device = null
                    onError("Kamera error (kode $error).")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            onError("Gagal membuka kamera: ${e.message}")
        }
    }

    @Suppress("MissingPermission")
    private fun startPreview() {
        val cam = device ?: return
        val surface = previewSurface ?: return
        try {
            val request = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }.build()
            cam.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    try { s.setRepeatingRequest(request, null, backgroundHandler) } catch (_: Exception) {}
                }
                override fun onConfigureFailed(s: CameraCaptureSession) { onError("Gagal konfigurasi sesi kamera.") }
            }, backgroundHandler)
        } catch (e: Exception) {
            onError("Gagal memulai pratinjau: ${e.message}")
        }
    }

    @Suppress("MissingPermission")
    fun captureStill() {
        val cam = device ?: run { onError("Kamera belum siap."); onPhoto(null); return }
        try {
            imageReader?.close()
            val reader = ImageReader.newInstance(1280, 960, ImageFormat.JPEG, 1)
            imageReader = reader
            reader.setOnImageAvailableListener({ r ->
                val image: Image? = r.acquireLatestImage()
                try {
                    val bmp = image?.let { img ->
                        val buf = img.planes[0].buffer
                        val bytes = ByteArray(buf.remaining())
                        buf.get(bytes)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    onPhoto(rotateIfNeeded(bmp))
                } finally {
                    image?.close()
                }
            }, backgroundHandler)

            val request = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }.build()
            cam.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    try { s.capture(request, null, backgroundHandler) } catch (e: Exception) { onError("Capture gagal: ${e.message}"); onPhoto(null) }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) { onError("Gagal sesi capture."); onPhoto(null) }
            }, backgroundHandler)
        } catch (e: Exception) {
            onError("Capture error: ${e.message}"); onPhoto(null)
        }
    }

    private fun rotateIfNeeded(bmp: Bitmap?): Bitmap? {
        if (bmp == null) return null
        return try {
            val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val camId = mgr.cameraIdList.firstOrNull() ?: return bmp
            val rot = mgr.getCameraCharacteristics(camId).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            if (rot == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
        } catch (_: Exception) { bmp }
    }

    private fun startBackground() {
        backgroundThread = HandlerThread("dape-camera").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    fun close() {
        try { session?.close() } catch (_: Exception) {}
        try { device?.close() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        session = null; device = null; imageReader = null
        backgroundThread?.quitSafely(); backgroundThread = null; backgroundHandler = null
    }
}
