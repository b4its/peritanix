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

    // --- diagnoses ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagnosis(d: DiagnosisEntity)

    @Query("SELECT * FROM diagnoses WHERE landId = :landId")
    suspend fun diagnosesFor(landId: String): List<DiagnosisEntity>
}

@Database(
    entities = [SyncQueueEntity::class, LandEntity::class, DiagnosisEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DapeDatabase : RoomDatabase() {
    abstract fun dao(): DapeDao
}
