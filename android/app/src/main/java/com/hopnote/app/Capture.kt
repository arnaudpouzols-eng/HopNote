package com.hopnote.app

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

enum class CaptureSource { TEXT, VOICE }
enum class SyncStatus { LOCAL_ONLY, SYNCING, SYNCED, FAILED }

@Entity(tableName = "captures")
data class Capture(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val source: CaptureSource,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val syncedAt: Long? = null,
    val notionBlockId: String? = null
)

@Dao
interface CaptureDao {
    @Query("SELECT * FROM captures ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Capture>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(capture: Capture)

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM captures WHERE syncStatus != 'SYNCED' ORDER BY createdAt ASC")
    suspend fun unsynced(): List<Capture>

    @Query("UPDATE captures SET syncStatus = 'SYNCING' WHERE id = :id")
    suspend fun markSyncing(id: String)

    @Query("UPDATE captures SET syncStatus = 'SYNCED', syncedAt = :syncedAt, notionBlockId = :notionBlockId WHERE id = :id")
    suspend fun markSynced(id: String, syncedAt: Long, notionBlockId: String)

    @Query("UPDATE captures SET syncStatus = 'LOCAL_ONLY' WHERE id = :id")
    suspend fun markPending(id: String)

    /** A stopped worker must never leave a note visually stuck on “Synchronisation…”. */
    @Query("UPDATE captures SET syncStatus = 'LOCAL_ONLY' WHERE syncStatus != 'SYNCED'")
    suspend fun resetPendingForRetry()

    @Query("DELETE FROM captures WHERE syncStatus = 'SYNCED' AND syncedAt < :before")
    suspend fun deleteSyncedBefore(before: Long): Int

    @Query("DELETE FROM captures")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM captures WHERE syncStatus != 'SYNCED'")
    fun pendingCount(): Flow<Int>
}

@Database(entities = [Capture::class], version = 2, exportSchema = false)
abstract class HopNoteDatabase : RoomDatabase() {
    abstract fun captures(): CaptureDao
}
