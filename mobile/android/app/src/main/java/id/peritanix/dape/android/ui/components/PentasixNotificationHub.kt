package id.peritanix.dape.android.ui.components

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tipe klasifikasi notifikasi aksi Pentasix.
 */
enum class NotificationType(
    val label: String,
    val color: Color
) {
    SUCCESS("Berhasil", Color(0xFF2E7D32)),
    INFO("Info", Color(0xFFE65100)),
    WARNING("Peringatan", Color(0xFFF57F17)),
    ERROR("Gagal", Color(0xFFD32F2F)),
    RAIN("Cuaca Hujan", Color(0xFF0288D1)),
    SYNC("Sinkronisasi", Color(0xFFFF6F00)),
    CALIBRATION("Sensor & Arah", Color(0xFF7B1FA2));

    fun icon(): ImageVector = when (this) {
        SUCCESS -> AppIcons.CheckCircle
        INFO -> AppIcons.Notifications
        WARNING -> AppIcons.Warning
        ERROR -> AppIcons.Close
        RAIN -> AppIcons.Umbrella
        SYNC -> AppIcons.Sync
        CALIBRATION -> AppIcons.Sensors
    }
}

/**
 * Model entitas notifikasi aksi Pentasix.
 */
data class NotificationItem(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val message: String,
    val type: NotificationType = NotificationType.INFO,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Hub terpusat untuk notifikasi interaktif dalam aplikasi (Heads-Up Floating Banner)
 * dan notifikasi bilah status sistem Android (System Status Bar Notification + Haptic Feedback).
 */
object PentasixNotificationHub {
    const val CHANNEL_ID = "pentasix_action_channel"
    const val CHANNEL_NAME = "Notifikasi Aksi Pentasix"

    // Reactive State untuk Banner In-App
    val currentNotification = mutableStateOf<NotificationItem?>(null)
    val history = mutableStateListOf<NotificationItem>()

    private var dismissJob: Job? = null
    private val hubScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var channelInitialized = false

    /**
     * Memicu notifikasi aksi terpusat:
     *  1. Menampilkan Banner Melayang In-App dengan animasi & auto-dismiss (3500ms).
     *  2. Memancarkan getaran Haptic feedback perangkat.
     *  3. Menampilkan Android System Notification di Status Bar.
     */
    fun notify(
        context: Context? = null,
        title: String,
        message: String,
        type: NotificationType = NotificationType.INFO,
        postSystemNotification: Boolean = true,
        haptic: Boolean = true
    ): NotificationItem {
        val item = NotificationItem(
            id = System.currentTimeMillis(),
            title = title,
            message = message,
            type = type
        )

        // Update in-app banner state
        currentNotification.value = item
        if (history.size >= 50) {
            history.removeAt(history.lastIndex)
        }
        history.add(0, item)

        // Auto dismiss banner setelah 3.5 detik
        try {
            dismissJob?.cancel()
            dismissJob = hubScope.launch {
                delay(3500L)
                if (currentNotification.value?.id == item.id) {
                    currentNotification.value = null
                }
            }
        } catch (_: Throwable) {
            // JVM test fallback jika Coroutine scope tidak aktif
        }

        // Haptic feedback
        if (haptic && context != null) {
            triggerHaptic(context, type)
        }

        // System status bar notification
        if (postSystemNotification && context != null) {
            postAndroidNotification(context, item)
        }

        return item
    }

    /**
     * Menutup paksa banner in-app saat ini.
     */
    fun dismissCurrent() {
        currentNotification.value = null
        try {
            dismissJob?.cancel()
        } catch (_: Throwable) {}
    }

    /**
     * Membersihkan riwayat notifikasi sesi ini.
     */
    fun clearHistory() {
        history.clear()
        dismissCurrent()
    }

    private fun triggerHaptic(context: Context, type: NotificationType) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val (durationMs, amplitude) = when (type) {
                    NotificationType.ERROR -> 75L to 255
                    NotificationType.WARNING, NotificationType.RAIN -> 45L to 200
                    NotificationType.SUCCESS -> 25L to 180
                    NotificationType.INFO, NotificationType.SYNC, NotificationType.CALIBRATION -> 20L to 150
                }
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(30L)
            }
        } catch (_: Throwable) {
            // Ignore in restricted environments / JVM unit tests
        }
    }

    private fun postAndroidNotification(context: Context, item: NotificationItem) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // Buat Notification Channel (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !channelInitialized) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Umpan balik notifikasi aksi lapangan Pentasix"
                    enableLights(true)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
                channelInitialized = true
            }

            // Periksa izin Android 13+ (Tiramisu / API 33)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) return
            }

            // Resolusi icon yang aman
            val iconRes = try {
                context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info
            } catch (_: Throwable) {
                android.R.drawable.ic_dialog_info
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setContentTitle(item.title)
                .setContentText(item.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            val notificationId = (item.id % 100000).toInt()
            notificationManager.notify(notificationId, notification)
        } catch (_: Throwable) {
            // Safe fallback jika berjalan di JVM test tanpa Android subsystem
        }
    }
}

/**
 * Komponen Banner Melayang In-App Pentasix.
 * Diletakkan di atas tampilan layar untuk memberikan konfirmasi visual interaktif.
 */
@Composable
fun PentasixNotificationBanner(
    modifier: Modifier = Modifier
) {
    val notification = PentasixNotificationHub.currentNotification.value

    AnimatedVisibility(
        visible = notification != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        if (notification != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                border = BorderStroke(1.5.dp, notification.type.color)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Bulatan Icon Tipe Notifikasi
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(notification.type.color, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = notification.type.icon(),
                            contentDescription = notification.type.label,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Teks Notifikasi (Judul & Pesan)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = notification.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = notification.type.color.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = notification.type.label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = notification.type.color,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = notification.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp
                        )
                    }

                    // Tombol Tutup Banner Cepat
                    IconButton(
                        onClick = { PentasixNotificationHub.dismissCurrent() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
