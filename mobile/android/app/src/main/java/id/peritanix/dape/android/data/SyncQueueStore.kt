package id.peritanix.dape.android.data

import android.content.Context
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.UUID

/**
 * SyncQueueStore — the app-side offline queue.
 *
 * Wraps the Room DB and schedules a WorkManager job that fires the delta sync
 * when a stable network returns. Idempotency keys are generated per mutation so
 * retries are safe on the server.
 */
class SyncQueueStore(private val context: Context) {

    private val db: DapeDatabase = Room.databaseBuilder(
        context, DapeDatabase::class.java, "dape.db"
    ).fallbackToDestructiveMigration().build()

    fun pendingCount(): Int = db.dao().pendingCount()

    /** Add a simulated mutation (real UI would pass captured data). */
    fun enqueueSimulatedSync() {
        val dao = db.dao()
        val key = "android-${UUID.randomUUID()}"
        // Room is synchronous here for brevity in the scaffold.
        Thread {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO sync_queue (entity, idempotencyKey, updatedTs, rev, payloadJson) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf("soil_assessment", key, System.currentTimeMillis(), 1L, "{}")
            )
            schedule()
        }.start()
    }

    /** Schedule a constraints-gated background sync (Wi-Fi / 4G). */
    fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "dape-sync",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun dao(): DapeDao = db.dao()
}
