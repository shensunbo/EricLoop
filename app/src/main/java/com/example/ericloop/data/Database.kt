package com.example.ericloop.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "records")
data class RecordEntity(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "tags")
data class TagEntity(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "checkins")
data class CheckInEntity(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "events")
data class EventEntity(@PrimaryKey val id: String, val sequence: Long, val recordId: String?, val payload: String)
@Entity(tableName = "metadata")
data class MetadataEntity(@PrimaryKey val id: Int = 1, val payload: String)

@Dao
interface LoopDao {
    @Query("SELECT * FROM records") suspend fun records(): List<RecordEntity>
    @Query("SELECT * FROM tags") suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM checkins") suspend fun checkIns(): List<CheckInEntity>
    @Query("SELECT * FROM events ORDER BY sequence") suspend fun events(): List<EventEntity>
    @Query("SELECT * FROM metadata WHERE id = 1") suspend fun metadata(): MetadataEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun records(rows: List<RecordEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun tags(rows: List<TagEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun checkIns(rows: List<CheckInEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun events(rows: List<EventEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun metadata(row: MetadataEntity)
    @Query("DELETE FROM records") suspend fun clearRecords()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM checkins") suspend fun clearCheckIns()
    @Query("DELETE FROM events") suspend fun clearEvents()
}

@Database(entities = [RecordEntity::class, TagEntity::class, CheckInEntity::class, EventEntity::class, MetadataEntity::class], version = 1, exportSchema = false)
abstract class LoopDatabase : RoomDatabase() {
    abstract fun loopDao(): LoopDao
}
