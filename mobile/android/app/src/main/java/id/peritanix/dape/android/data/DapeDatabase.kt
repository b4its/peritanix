package id.peritanix.dape.android.data

import androidx.room.*

/**
 * Room entities mirror the server schema for the records a field worker creates
 * offline. `synced` flags rows still needing a delta-sync push.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entity: String,           // soil_assessment | disease_log | harvest_batch
    val idempotencyKey: String,
    val updatedTs: Long,
    val rev: Long,
    val payloadJson: String
)

@Entity(tableName = "lands")
data class LandEntity(
    @PrimaryKey val id: String,
    val name: String,
    val boundaryWkt: String,
    val elevationM: Double,
    val accumulatedGdd: Double,
    val currentPhase: String,
    val synced: Int = 1
)

@Entity(tableName = "diagnoses")
data class DiagnosisEntity(
    @PrimaryKey val id: String,
    val landId: String,
    val lon: Double,
    val lat: Double,
    val localTwi: Double,
    val confirmedDisease: String,
    val severityPct: Double,
    val confidence: String,
    val protocolCode: String,
    val synced: Int = 0
)

@Entity(tableName = "soil_assessments")
data class SoilAssessmentEntity(
    @PrimaryKey val id: String,
    val landId: String,
    val sandPct: Double,
    val siltPct: Double,
    val clayPct: Double,
    val soilClass: String,
    val cecMeq: Double,
    val phDeterministic: Double,
    val nitratePpm: Double,
    val darkChamberLuxCheck: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "harvest_batches")
data class HarvestBatchEntity(
    @PrimaryKey val id: String,
    val landId: String,
    val boilingPointCalc: Double,
    val hotWaterRatio: String,
    val archimedesSinkRatio: Double,
    val gradeResult: String,
    val cryptoSignature: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "device_calibrations")
data class DeviceCalibrationEntity(
    @PrimaryKey val id: String,
    val whiteRefR: Double,
    val whiteRefG: Double,
    val whiteRefB: Double,
    val luxVerified: Boolean,
    val opticalMmScale: Double = 1.0,
    val pitchZeroOffset: Double = 0.0,
    val rollZeroOffset: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * A logged farm activity for the 30-year activity calendar. `epochDay` is
 * [java.time.LocalDate.toEpochDay] so a day lookup is a simple indexed query.
 */
@Entity(tableName = "farm_activities", indices = [Index("epochDay")])
data class FarmActivityEntity(
    @PrimaryKey val id: String,
    val epochDay: Long,
    val kind: String,          // TANAM | PUPUK | SEMPROT | PANEN | PANEN_UJI | LAIN
    val title: String,
    val notes: String = "",
    val landId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Completion record for a daily mission on a given day. A row exists only when
 * the mission is marked done; deleting the row un-marks it.
 */
@Entity(tableName = "daily_mission_done", primaryKeys = ["epochDay", "missionId"])
data class DailyMissionDoneEntity(
    val epochDay: Long,
    val missionId: String,
    val completedAt: Long = System.currentTimeMillis()
)

@Dao
interface DapeDao {
    // --- sync queue ---
    @Insert
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Query("SELECT * FROM sync_queue ORDER BY updatedTs ASC")
    suspend fun pending(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun pendingCount(): Int

    @Query("DELETE FROM sync_queue WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    // --- lands ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLand(land: LandEntity)

    @Query("SELECT * FROM lands")
    suspend fun lands(): List<LandEntity>

    @Query("SELECT * FROM lands")
    fun allLandsSync(): List<LandEntity>

    @Query("SELECT COUNT(*) FROM lands")
    fun landCount(): Int

    // --- diagnoses ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagnosis(d: DiagnosisEntity)

    @Query("SELECT * FROM diagnoses WHERE landId = :landId")
    suspend fun diagnosesFor(landId: String): List<DiagnosisEntity>

    @Query("SELECT * FROM diagnoses ORDER BY rowid DESC")
    suspend fun allDiagnoses(): List<DiagnosisEntity>

    @Query("SELECT * FROM diagnoses ORDER BY rowid DESC")
    fun allDiagnosesSync(): List<DiagnosisEntity>

    @Query("SELECT COUNT(*) FROM diagnoses")
    fun diagnosisCount(): Int

    // --- soil assessments ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSoilAssessment(s: SoilAssessmentEntity)

    @Query("SELECT * FROM soil_assessments ORDER BY timestamp DESC")
    suspend fun soilAssessments(): List<SoilAssessmentEntity>

    @Query("SELECT COUNT(*) FROM soil_assessments")
    fun soilAssessmentCount(): Int

    // --- harvest batches ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHarvest(h: HarvestBatchEntity)

    @Query("SELECT * FROM harvest_batches ORDER BY timestamp DESC")
    suspend fun harvests(): List<HarvestBatchEntity>

    @Query("SELECT COUNT(*) FROM harvest_batches")
    fun harvestCount(): Int

    // --- device calibrations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalibration(c: DeviceCalibrationEntity)

    @Query("SELECT * FROM device_calibrations ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestCalibration(): DeviceCalibrationEntity?

    @Query("SELECT * FROM device_calibrations ORDER BY timestamp DESC LIMIT 1")
    fun latestCalibrationSync(): DeviceCalibrationEntity?

    // --- farm activities (30-year calendar) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(a: FarmActivityEntity)

    @Query("SELECT * FROM farm_activities WHERE epochDay = :epochDay ORDER BY createdAt ASC")
    suspend fun activitiesForDay(epochDay: Long): List<FarmActivityEntity>

    @Query("SELECT * FROM farm_activities WHERE epochDay = :epochDay ORDER BY createdAt ASC")
    fun activitiesForDaySync(epochDay: Long): List<FarmActivityEntity>

    @Query("SELECT DISTINCT epochDay FROM farm_activities WHERE epochDay BETWEEN :fromDay AND :toDay")
    suspend fun activityDaysInRange(fromDay: Long, toDay: Long): List<Long>

    @Query("DELETE FROM farm_activities WHERE id = :id")
    suspend fun deleteActivity(id: String)

    @Query("SELECT COUNT(*) FROM farm_activities")
    fun activityCount(): Int

    // --- daily mission completion ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markMissionDone(d: DailyMissionDoneEntity)

    @Query("DELETE FROM daily_mission_done WHERE epochDay = :epochDay AND missionId = :missionId")
    suspend fun unmarkMissionDone(epochDay: Long, missionId: String)

    @Query("SELECT missionId FROM daily_mission_done WHERE epochDay = :epochDay")
    suspend fun completedMissions(epochDay: Long): List<String>

    @Query("SELECT missionId FROM daily_mission_done WHERE epochDay = :epochDay")
    fun completedMissionsSync(epochDay: Long): List<String>
}

@Database(
    entities = [
        SyncQueueEntity::class,
        LandEntity::class,
        DiagnosisEntity::class,
        SoilAssessmentEntity::class,
        HarvestBatchEntity::class,
        DeviceCalibrationEntity::class,
        FarmActivityEntity::class,
        DailyMissionDoneEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class DapeDatabase : RoomDatabase() {
    abstract fun dao(): DapeDao
}
