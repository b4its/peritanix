package id.peritanix.dape.android.data

import android.content.Context
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import id.peritanix.dape.android.core.calendar.ActivityCalendar
import java.time.LocalDate
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
    ).fallbackToDestructiveMigration().allowMainThreadQueries().build()

    fun pendingCount(): Int = db.dao().pendingCount()
    fun landCount(): Int = db.dao().landCount()
    fun diagnosisCount(): Int = db.dao().diagnosisCount()
    fun soilAssessmentCount(): Int = db.dao().soilAssessmentCount()
    fun harvestCount(): Int = db.dao().harvestCount()

    fun getLands(): List<LandEntity> = db.dao().allLandsSync()
    fun getDiagnoses(): List<DiagnosisEntity> = db.dao().allDiagnosesSync()

    /** Add a simulated mutation (for offline demo). */
    fun enqueueSimulatedSync() {
        val key = "android-${UUID.randomUUID()}"
        Thread {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO sync_queue (entity, idempotencyKey, updatedTs, rev, payloadJson) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf("soil_assessment", key, System.currentTimeMillis(), 1L, "{\"type\":\"simulated\"}")
            )
            schedule()
        }.start()
    }

    /** Enqueue a real soil assessment into Room DB and the offline sync queue */
    fun enqueueSoilAssessment(
        landId: String,
        sandPct: Double,
        siltPct: Double,
        clayPct: Double,
        soilClass: String,
        cecMeq: Double,
        phDeterministic: Double,
        nitratePpm: Double
    ) {
        val key = "android-soil-${UUID.randomUUID()}"
        val assessmentId = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()
        val json = """{"land_id":"$landId","sand_pct":$sandPct,"silt_pct":$siltPct,"clay_pct":$clayPct,"soil_class":"$soilClass","cec_meq":$cecMeq,"ph_deterministic":$phDeterministic,"nitrate_ppm":$nitratePpm}"""

        Thread {
            db.openHelper.writableDatabase.execSQL(
                "INSERT OR REPLACE INTO soil_assessments (id, landId, sandPct, siltPct, clayPct, soilClass, cecMeq, phDeterministic, nitratePpm, darkChamberLuxCheck, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(assessmentId, landId, sandPct, siltPct, clayPct, soilClass, cecMeq, phDeterministic, nitratePpm, 1, ts)
            )
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO sync_queue (entity, idempotencyKey, updatedTs, rev, payloadJson) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf("soil_assessment", key, ts, 1L, json)
            )
            schedule()
        }.start()
    }

    /** Enqueue a diagnosis into Room DB and the offline sync queue */
    fun enqueueDiagnosis(
        landId: String,
        confirmedDisease: String,
        severityPct: Double,
        confidence: String,
        protocolCode: String,
        localTwi: Double,
        lon: Double = 110.456789,
        lat: Double = -7.123456
    ) {
        val key = "android-diag-${UUID.randomUUID()}"
        val diagId = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()
        val json = """{"land_id":"$landId","confirmed_disease":"$confirmedDisease","severity_pct":$severityPct,"confidence":"$confidence","protocol_code":"$protocolCode","local_twi":$localTwi,"lon":$lon,"lat":$lat}"""

        Thread {
            db.openHelper.writableDatabase.execSQL(
                "INSERT OR REPLACE INTO diagnoses (id, landId, lon, lat, localTwi, confirmedDisease, severityPct, confidence, protocolCode, synced) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(diagId, landId, lon, lat, localTwi, confirmedDisease, severityPct, confidence, protocolCode, 0)
            )
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO sync_queue (entity, idempotencyKey, updatedTs, rev, payloadJson) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf("disease_log", key, ts, 1L, json)
            )
            schedule()
        }.start()
    }

    /** Enqueue a harvest batch into Room DB and the offline sync queue */
    fun enqueueHarvest(
        landId: String,
        boilingPoint: Double,
        ratioLabel: String,
        sinkRatio: Double,
        gradeResult: String,
        signature: String
    ) {
        val key = "android-harvest-${UUID.randomUUID()}"
        val harvestId = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()
        val json = """{"land_id":"$landId","boiling_point_calc":$boilingPoint,"hot_water_ratio":"$ratioLabel","archimedes_sink_ratio":$sinkRatio,"grade_result":"$gradeResult","crypto_signature":"$signature"}"""

        Thread {
            db.openHelper.writableDatabase.execSQL(
                "INSERT OR REPLACE INTO harvest_batches (id, landId, boilingPointCalc, hotWaterRatio, archimedesSinkRatio, gradeResult, cryptoSignature, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(harvestId, landId, boilingPoint, ratioLabel, sinkRatio, gradeResult, signature, ts)
            )
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO sync_queue (entity, idempotencyKey, updatedTs, rev, payloadJson) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf("harvest_batch", key, ts, 1L, json)
            )
            schedule()
        }.start()
    }

    /** Save or update land boundary into Room DB and the offline sync queue */
    fun enqueueLand(
        landId: String,
        name: String,
        boundaryWkt: String,
        elevationM: Double,
        accumulatedGdd: Double = 0.0,
        currentPhase: String = "VEGETATIVE"
    ) {
        val key = "android-land-${UUID.randomUUID()}"
        val ts = System.currentTimeMillis()
        val json = """{"id":"$landId","name":"$name","boundary":"$boundaryWkt","elevation_m":$elevationM,"accumulated_gdd":$accumulatedGdd,"current_phase":"$currentPhase"}"""

        Thread {
            db.openHelper.writableDatabase.execSQL(
                "INSERT OR REPLACE INTO lands (id, name, boundaryWkt, elevationM, accumulatedGdd, currentPhase, synced) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf(landId, name, boundaryWkt, elevationM, accumulatedGdd, currentPhase, 0)
            )
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO sync_queue (entity, idempotencyKey, updatedTs, rev, payloadJson) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf("land_init", key, ts, 1L, json)
            )
            schedule()
        }.start()
    }

    /** Save white reference calibration into Room DB */
    fun saveCalibration(
        id: String,
        whiteRefR: Double,
        whiteRefG: Double,
        whiteRefB: Double,
        luxVerified: Boolean
    ) {
        val ts = System.currentTimeMillis()
        Thread {
            db.openHelper.writableDatabase.execSQL(
                "INSERT OR REPLACE INTO device_calibrations (id, whiteRefR, whiteRefG, whiteRefB, luxVerified, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf(id, whiteRefR, whiteRefG, whiteRefB, if (luxVerified) 1 else 0, ts)
            )
        }.start()
    }

    /**
     * Queue the Ed25519 public key + white-reference matrix for the delta-sync
     * worker to flush (`user_key` entity). Used when there is no JWT yet or the
     * device is offline during onboarding (item 2.2 ONLINE).
     */
    fun enqueueUserKey(
        userId: String,
        publicKeyBase64: String,
        whiteRefR: Double,
        whiteRefG: Double,
        whiteRefB: Double
    ) {
        val key = "android-userkey-${UUID.randomUUID()}"
        val ts = System.currentTimeMillis()
        val json = """{"user_id":"$userId","ed25519_public_key":"$publicKeyBase64","white_reference":[$whiteRefR,$whiteRefG,$whiteRefB]}"""
        Thread {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO sync_queue (entity, idempotencyKey, updatedTs, rev, payloadJson) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf("user_key", key, ts, 1L, json)
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
    fun database(): DapeDatabase = db

    // --- 30-year calendar activities -----------------------------------------

    /** Add a farm activity on a given epoch-day. Runs off the UI thread. */
    fun addActivity(epochDay: Long, kind: String, title: String, notes: String = "", landId: String = "") {
        val id = "act-${UUID.randomUUID()}"
        Thread {
            db.openHelper.writableDatabase.execSQL(
                "INSERT OR REPLACE INTO farm_activities (id, epochDay, kind, title, notes, landId, createdAt) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf(id, epochDay, kind, title, notes, landId, System.currentTimeMillis())
            )
        }.start()
    }

    /** Activities for one day (blocking; call off UI thread or use activitiesForDaySuspend). */
    fun activitiesForDay(epochDay: Long): List<FarmActivityEntity> = db.dao().activitiesForDaySync(epochDay)

    /** Suspend variant for coroutine use. */
    suspend fun activitiesForDaySuspend(epochDay: Long): List<FarmActivityEntity> = db.dao().activitiesForDay(epochDay)

    /** Set of epoch-days that have at least one activity within [fromDay, toDay]. */
    suspend fun activityDaysInRange(fromDay: Long, toDay: Long): Set<Long> =
        db.dao().activityDaysInRange(fromDay, toDay).toSet()

    /** Remove an activity by id. */
    fun deleteActivity(id: String) {
        Thread { db.openHelper.writableDatabase.execSQL("DELETE FROM farm_activities WHERE id = ?", arrayOf(id)) }.start()
    }

    // --- daily mission completion --------------------------------------------

    /** Mark a daily mission done for [epochDay]. */
    fun markMissionDone(epochDay: Long, missionId: String) {
        Thread {
            db.openHelper.writableDatabase.execSQL(
                "INSERT OR REPLACE INTO daily_mission_done (epochDay, missionId, completedAt) VALUES (?, ?, ?)",
                arrayOf(epochDay, missionId, System.currentTimeMillis())
            )
        }.start()
    }

    /** Un-mark a daily mission for [epochDay]. */
    fun unmarkMissionDone(epochDay: Long, missionId: String) {
        Thread {
            db.openHelper.writableDatabase.execSQL(
                "DELETE FROM daily_mission_done WHERE epochDay = ? AND missionId = ?",
                arrayOf(epochDay, missionId)
            )
        }.start()
    }

    /** Completed mission ids for [epochDay] (blocking; call off UI thread or use suspend). */
    fun completedMissions(epochDay: Long): Set<String> = db.dao().completedMissionsSync(epochDay).toSet()

    /** Suspend variant of [completedMissions] for coroutine use. */
    suspend fun completedMissionsSuspend(epochDay: Long): Set<String> = db.dao().completedMissions(epochDay).toSet()

    /** Add activity suspend variant for coroutines. */
    suspend fun addActivitySuspend(
        epochDay: Long,
        kind: String,
        title: String,
        notes: String = "",
        landId: String = ""
    ): FarmActivityEntity {
        val entity = FarmActivityEntity(
            id = "act-${UUID.randomUUID()}",
            epochDay = epochDay,
            kind = kind,
            title = title,
            notes = notes,
            landId = landId,
            createdAt = System.currentTimeMillis()
        )
        db.dao().insertActivity(entity)
        return entity
    }

    /** Delete activity suspend variant for coroutines. */
    suspend fun deleteActivitySuspend(id: String) {
        db.dao().deleteActivity(id)
    }

    /**
     * Seeds initial precision micro-farming agronomic calendar schedule if the
     * table is currently empty.
     */
    suspend fun seedDefaultActivitiesIfEmpty(today: LocalDate = LocalDate.now()) {
        val count = db.dao().activityCount()
        if (count == 0) {
            for (spec in ActivityCalendar.sampleSeedActivities()) {
                val actDate = today.plusDays(spec.offsetDays)
                val entity = FarmActivityEntity(
                    id = "act-${UUID.randomUUID()}",
                    epochDay = ActivityCalendar.toEpochDay(actDate),
                    kind = spec.kind,
                    title = spec.title,
                    notes = spec.notes,
                    landId = spec.landId,
                    createdAt = System.currentTimeMillis()
                )
                db.dao().insertActivity(entity)
            }
        }
    }
}
