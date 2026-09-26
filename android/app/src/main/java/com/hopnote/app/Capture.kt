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
enum class SyncStatus { LOCAL_ONLY }

@Entity(tableName = "captures")
data class Capture(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val source: CaptureSource,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)

@Dao
interface CaptureDao {
    @Query("SELECT * FROM captures ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Capture>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(capture: Capture)
}

@Database(entities = [Capture::class], version = 1, exportSchema = false)
abstract class HopNoteDatabase : RoomDatabase() {
    abstract fun captures(): CaptureDao
}

