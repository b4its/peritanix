package id.peritanix.dape.android.core.physics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import id.peritanix.dape.android.ui.components.NotificationType
import id.peritanix.dape.android.ui.components.PentasixNotificationHub

/**
 * Tahap 1.3: Stokes Sedimentation Analyzer Alarm Receiver.
 *
 * Receives exact AlarmManager broadcasts for Stokes sedimentation settling intervals:
 *   1. ACTION_STOKES_SAND (t = 40 s): Sand layer settled.
 *   2. ACTION_STOKES_SILT (t = 7200 s / 2 hours): Silt layer settled, clay remaining in suspension.
 *
 * Emits high-priority tactile alerts and system notifications to alert the farmer in the field.
 */
class StokesAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOKES_SAND = "id.peritanix.dape.android.STOKES_SAND_EXPIRED"
        const val ACTION_STOKES_SILT = "id.peritanix.dape.android.STOKES_SILT_EXPIRED"
        const val EXTRA_STAGE_NAME = "extra_stage_name"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOKES_SAND -> {
                PentasixNotificationHub.notify(
                    context = context,
                    title = "Uji Stokes: Pasir Mengendap (40s)",
                    message = "Fraksi pasir telah mengendap sempurna di dasar botol. Posisikan kamera tegak 90° ± 0.5° untuk memotret batas pasir.",
                    type = NotificationType.WARNING,
                    postSystemNotification = true
                )
            }
            ACTION_STOKES_SILT -> {
                PentasixNotificationHub.notify(
                    context = context,
                    title = "Uji Stokes: Debu & Liat Siap (2 Jam)",
                    message = "Pengendapan 2 jam selesai. Garis batas debu dan liat terbentuk. Ambil foto kedua untuk kalkulasi fraksi tekstur USDA dan KTK.",
                    type = NotificationType.SUCCESS,
                    postSystemNotification = true
                )
            }
        }
    }
}
