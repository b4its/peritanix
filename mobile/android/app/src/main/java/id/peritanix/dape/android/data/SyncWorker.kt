package id.peritanix.dape.android.data

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * SyncWorker — flushes the offline queue to the Go API when network connectivity
 * is available.
 *
 * Implements idempotent delta synchronization:
 * 1. Collects pending operations from Room SQLite `sync_queue` table.
 * 2. Bundles operations into a canonical `syncDeltaReq` JSON payload.
 * 3. POSTs payload to `{API_BASE_URL}/api/v1/sync/delta`.
 * 4. Parses the per-operation statuses (`applied` | `duplicate` | `rejected`).
 * 5. Deletes successfully handled (`applied` or `duplicate`) records from local SQLite.
 * 6. Safely retries if transient network failures occur.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = Room.databaseBuilder(
                applicationContext, DapeDatabase::class.java, "dape.db"
            ).fallbackToDestructiveMigration().build()
            val dao = db.dao()
            val pending = dao.pending()
            if (pending.isEmpty()) return Result.success()

            val deviceId = "android-${android.os.Build.ID ?: "client"}"
            val opsArray = JSONArray()

            for (item in pending) {
                val opObj = JSONObject()
                opObj.put("idempotency_key", item.idempotencyKey)
                opObj.put("entity", item.entity)
                opObj.put("op", "create")
                opObj.put("updated_ts", item.updatedTs / 1000L)
                opObj.put("rev", item.rev)

                val dataObj = try {
                    JSONObject(item.payloadJson)
                } catch (e: Exception) {
                    JSONObject().put("raw", item.payloadJson)
                }
                opObj.put("data", dataObj)
                opsArray.put(opObj)
            }

            val requestBody = JSONObject()
            requestBody.put("device_id", deviceId)
            requestBody.put("operations", opsArray)

            val url = URL("$API_BASE_URL/api/v1/sync/delta")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 6000
                readTimeout = 10000
                doOutput = true
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val responseJson = JSONObject(responseStr)
                val resultsArray = responseJson.optJSONArray("results") ?: JSONArray()

                val succeededKeys = mutableSetOf<String>()
                for (i in 0 until resultsArray.length()) {
                    val res = resultsArray.getJSONObject(i)
                    val status = res.optString("status")
                    val key = res.optString("idempotency_key")
                    if (status == "applied" || status == "duplicate") {
                        succeededKeys.add(key)
                    }
                }

                val idsToDelete = pending.filter { it.idempotencyKey in succeededKeys }.map { it.id }
                if (idsToDelete.isNotEmpty()) {
                    dao.deleteByIds(idsToDelete)
                }

                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            // Network outage or timeout: back off and retry later
            Result.retry()
        }
    }

    companion object {
        const val API_BASE_URL = "http://10.0.2.2:58080" // Android emulator -> host Go Fiber API
    }
}
