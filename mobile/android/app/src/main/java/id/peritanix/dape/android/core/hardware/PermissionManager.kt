package id.peritanix.dape.android.core.hardware

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Centralized Hardware Permission Manager for DAPE Pentasix.
 *
 * Checks, organizes, and requests runtime permissions for all hardware sensors:
 *   - Camera & Flash: 12-frame burst, trans-illumination, dark chamber, Stokes sedimentation
 *   - GPS / GNSS & Activity Recognition: Polygon boundary, PDR mulch hole tracking
 *   - Microphone: Acoustic Anemometer, Boiling Sound Verifier, Offline Hands-free Voice
 *   - Bluetooth & Wi-Fi: ESC/POS thermal printing, offline P2P mesh sync
 *   - System Notifications & Haptics: Action alerts, rain stopwatch, pest warnings
 */
object PermissionManager {

    data class PermissionDetail(
        val permission: String,
        val label: String,
        val purpose: String,
        val isGranted: Boolean
    )

    fun getRequiredPermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )

        // Activity Recognition for Pedestrian Dead Reckoning (PDR) step detector
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            list.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        // Android 13+ (API 33) Notifications & Nearby Wi-Fi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
            list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        // Android 12+ (API 31) Bluetooth Connect & Scan
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
            list.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        return list.toTypedArray()
    }

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun checkMissingPermissions(context: Context): List<String> {
        return getRequiredPermissions().filter { !hasPermission(context, it) }
    }

    fun areAllPermissionsGranted(context: Context): Boolean {
        return checkMissingPermissions(context).isEmpty()
    }

    fun getPermissionDetails(context: Context): List<PermissionDetail> {
        return getRequiredPermissions().map { perm ->
            val granted = hasPermission(context, perm)
            val (label, purpose) = when (perm) {
                Manifest.permission.CAMERA -> Pair(
                    "Kamera & Lensa Fokus",
                    "12-frame burst super-resolution, fotometri gelap, & jarak fokus diopter"
                )
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION -> Pair(
                    "GPS & GNSS Presisi",
                    "Pemetaan poligon batas lahan, TWI, dan sinkronisasi spasial PostGIS"
                )
                Manifest.permission.RECORD_AUDIO -> Pair(
                    "Mikrofon Internal",
                    "Anemometer akustik kecepatan angin, deteksi air mendidih, & perintah suara"
                )
                Manifest.permission.ACTIVITY_RECOGNITION -> Pair(
                    "Sensor Langkah Fisik (PDR)",
                    "Pelacakan posisi antar-lubang tanam mulsa 50 cm saat sinyal GPS terhalang tajuk"
                )
                Manifest.permission.POST_NOTIFICATIONS -> Pair(
                    "Notifikasi Sistem",
                    "Peringatan aksi lapangan, stopwatch hujan aktif, & alarm badai mikro"
                )
                Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN -> Pair(
                    "Bluetooth Perangkat",
                    "Pencetakan tiket panen ke printer termal ESC/POS & P2P mesh sync"
                )
                Manifest.permission.NEARBY_WIFI_DEVICES -> Pair(
                    "Wi-Fi P2P Mesh",
                    "Sinkronisasi data antar-HP pekerja kebun tanpa kuota internet (Data Mule)"
                )
                else -> Pair(perm.substringAfterLast("."), "Akses hardware sensor")
            }
            PermissionDetail(perm, label, purpose, granted)
        }
    }
}
