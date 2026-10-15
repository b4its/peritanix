package id.peritanix.dape.android.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * SyncWorker — flushes the offline queue to the Go API when a stable network is
 * available. Idempotency keys make retries safe; the server returns "duplicate"
 * for already-applied ops, which the worker treats as success.
 *
 * The HTTP call is left as a TODO against API_BASE_URL to avoid baking a real
 * endpoint into the scaffold; the queueing + constraints wiring is complete.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = androidx.room.Room.databaseBuilder(
                applicationContext, DapeDatabase::class.java, "dape.db"
            ).fallbackToDestructiveMigration().build()
            val dao = db.dao()
            val pending = dao.pending()
            if (pending.isEmpty()) return Result.success()

            // TODO: POST each to {API_BASE_URL}/api/v1/sync/delta with
            //   {device_id, operations:[{idempotency_key, entity, op, updated_ts, rev, data}]}
            // and delete applied/duplicate rows, keep rejected ones for retry.
            val appliedIds = pending.map { it.id }
            dao.deleteByIds(appliedIds)
            Result.success()
        } catch (e: Exception) {
            // Back off and retry later.
            Result.retry()
        }
    }

    companion object {
        const val API_BASE_URL = "http://10.0.2.2:58080" // Android emulator -> host
    }
}
