package com.example.ericloop.friends.data

import androidx.room.*

@Entity(tableName = "friends_metadata")
data class FriendsMetadata(@PrimaryKey val id: Int = 1, val datasetId: String, val revision: Long)

class FriendConverters {
    @TypeConverter fun gender(value: FriendGender): String = value.name

    @TypeConverter fun gender(value: String): FriendGender = FriendGender.valueOf(value)
}

@Dao
interface FriendsDao {
    @Query("SELECT * FROM friends") suspend fun friends(): List<Friend>

    @Query("SELECT * FROM friend_events") suspend fun events(): List<FriendEvent>

    @Query("SELECT * FROM friends_metadata WHERE id = 1") suspend fun metadata(): FriendsMetadata?

    @Insert suspend fun insertFriend(friend: Friend)

    @Update suspend fun updateFriend(friend: Friend)

    @Insert suspend fun insertEvent(event: FriendEvent)

    @Update suspend fun updateEvent(event: FriendEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun metadata(value: FriendsMetadata)

    @Query("DELETE FROM friends WHERE id = :id") suspend fun deleteFriend(id: String)

    @Query("DELETE FROM friends") suspend fun clearFriends()
}

@Database(
    entities = [Friend::class, FriendEvent::class, FriendsMetadata::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(FriendConverters::class)
abstract class FriendsDatabase : RoomDatabase() {
    abstract fun dao(): FriendsDao
}
