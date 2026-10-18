package id.peritanix.dape.android.core.physics

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Tahap 1.3: Stokes Sedimentation Automated Alarm Scheduler.
 *
 * Schedules hardware-level exact alarms using AlarmManager:
 * - t = 40 seconds: Sand settling phase
 * - t = 7200 seconds (2 hours): Silt settling phase
 *
 * Works 100% offline and wakes up the device to prompt the farmer to capture
 * the water-level verified image.
 */
object StokesAlarmScheduler {

    private const val REQUEST_CODE_SAND = 10040
    private const val REQUEST_CODE_SILT = 17200

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
    }

    /**
     * Schedules the 40-second alarm for sand settlement.
     * @return Scheduled epoch time in milliseconds.
     */
    fun scheduleSandAlarm(context: Context, delaySeconds: Long = 40L): Long {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTimeMs = System.currentTimeMillis() + (delaySeconds * 1000L)

        val intent = Intent(context, StokesAlarmReceiver::class.java).apply {
            action = StokesAlarmReceiver.ACTION_STOKES_SAND
            putExtra(StokesAlarmReceiver.EXTRA_STAGE_NAME, "SAND_SETTLING")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SAND,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
        }

        return triggerTimeMs
    }

    /**
     * Schedules the 7200-second (2 hours) alarm for silt & clay settlement.
     * @return Scheduled epoch time in milliseconds.
     */
    fun scheduleSiltClayAlarm(context: Context, delaySeconds: Long = 7200L): Long {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTimeMs = System.currentTimeMillis() + (delaySeconds * 1000L)

        val intent = Intent(context, StokesAlarmReceiver::class.java).apply {
            action = StokesAlarmReceiver.ACTION_STOKES_SILT
            putExtra(StokesAlarmReceiver.EXTRA_STAGE_NAME, "SILT_CLAY_SETTLING")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SILT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
        }

        return triggerTimeMs
    }

    /**
     * Cancels pending Stokes alarms.
     */
    fun cancelAllAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val sandIntent = Intent(context, StokesAlarmReceiver::class.java).apply {
            action = StokesAlarmReceiver.ACTION_STOKES_SAND
        }
        val sandPi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SAND,
            sandIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (sandPi != null) {
            alarmManager.cancel(sandPi)
            sandPi.cancel()
        }

        val siltIntent = Intent(context, StokesAlarmReceiver::class.java).apply {
            action = StokesAlarmReceiver.ACTION_STOKES_SILT
        }
        val siltPi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SILT,
            siltIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (siltPi != null) {
            alarmManager.cancel(siltPi)
            siltPi.cancel()
        }
    }
}
