package com.example.ericloop.friends.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

class FriendsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database =
        Room.databaseBuilder(appContext, FriendsDatabase::class.java, "friends.db").build()
    private val dao = database.dao()
    private val mutex = Mutex()
    private val state = MutableStateFlow<FriendsBackup?>(null)
    val data = state.asStateFlow()

    suspend fun load() =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (state.value != null) return@withLock
                state.value =
                    database.withTransaction {
                        val metadata =
                            dao.metadata()
                                ?: FriendsMetadata(
                                        datasetId = UUID.randomUUID().toString(),
                                        revision = 0,
                                    )
                                    .also {
                                        require(dao.friends().isEmpty() && dao.events().isEmpty()) {
                                            "朋友数据库元数据缺失"
                                        }
                                        dao.metadata(it)
                                    }
                        FriendsBackup(
                                datasetId = metadata.datasetId,
                                revision = metadata.revision,
                                friends = dao.friends(),
                                events = dao.events(),
                            )
                            .also(::validateFriendsBackup)
                    }
            }
        }

    private suspend fun mutate(transform: (FriendsBackup) -> FriendsBackup) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val before = checkNotNull(state.value) { "朋友数据尚未加载" }
                val changed = transform(before)
                if (changed == before) return@withLock
                require(before.revision < Long.MAX_VALUE) { "数据版本已达上限" }
                val next =
                    changed.copy(
                        revision = before.revision + 1,
                        exportedAt = System.currentTimeMillis(),
                    )
                validateFriendsBackup(next)
                database.withTransaction {
                    val nextIds = next.friends.map { it.id }.toSet()
                    before.friends.filter { it.id !in nextIds }.forEach { dao.deleteFriend(it.id) }
                    val oldFriends = before.friends.associateBy { it.id }
                    next.friends.forEach {
                        if (it.id !in oldFriends) dao.insertFriend(it)
                        else if (oldFriends[it.id] != it) dao.updateFriend(it)
                    }
                    val oldEvents = before.events.associateBy { it.id }
                    next.events.forEach {
                        if (it.id !in oldEvents) dao.insertEvent(it)
                        else if (oldEvents[it.id] != it) dao.updateEvent(it)
                    }
                    dao.metadata(
                        FriendsMetadata(datasetId = next.datasetId, revision = next.revision)
                    )
                }
                state.value = next
            }
        }

    suspend fun saveFriend(
        id: String?,
        name: String,
        gender: FriendGender,
        intro: String,
        notes: String,
        initialScore: String,
    ): String {
        val savedId = id ?: UUID.randomUUID().toString()
        mutate { source ->
            val old = source.friends.find { it.id == savedId }
            require(id == null || old != null) { "人物已不存在，请返回列表" }
            val now = System.currentTimeMillis()
            val friend =
                Friend(
                    savedId,
                    name.trim(),
                    gender,
                    intro.trim(),
                    notes.trim(),
                    parseScore(initialScore.trim()).toString(),
                    old?.colorArgb
                        ?: allocateFriendColor(source.friends.map { it.colorArgb }.toSet()),
                    old?.createdAt ?: now,
                    old?.updatedAt ?: now,
                )
            if (old == friend) source
            else
                source.copy(
                    friends =
                        source.friends.filterNot { it.id == savedId } +
                            friend.copy(updatedAt = maxOf(now, friend.createdAt, friend.updatedAt))
                )
        }
        return savedId
    }

    suspend fun deleteFriend(id: String) = mutate { source ->
        require(source.friends.any { it.id == id }) { "人物已不存在" }
        source.copy(
            friends = source.friends.filterNot { it.id == id },
            events = source.events.filterNot { it.friendId == id },
        )
    }

    suspend fun saveEvent(friendId: String, eventId: String?, delta: String, description: String) =
        mutate { source ->
            require(source.friends.any { it.id == friendId }) { "人物已不存在" }
            val old =
                eventId?.let { id -> source.events.find { it.id == id && it.friendId == friendId } }
            require(eventId == null || old != null) { "事件已不存在，请刷新页面" }
            val normalized = parseScore(delta.trim()).toString()
            require(old != null || normalized in listOf("1", "-1")) { "新增事件每次只能增加或减少 1 分" }
            val now = System.currentTimeMillis()
            val event =
                old?.copy(delta = normalized, description = description.trim())
                    ?: FriendEvent(
                        friendId = friendId,
                        delta = normalized,
                        description = description.trim(),
                        occurredAt = now,
                    )
            if (event == old) source
            else
                source.copy(
                    events =
                        source.events.filterNot { it.id == event.id } +
                            event.copy(updatedAt = maxOf(now, event.occurredAt, event.updatedAt))
                )
        }

    suspend fun snapshot(): FriendsBackup =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                checkNotNull(state.value) { "朋友数据尚未加载" }
                    .copy(exportedAt = System.currentTimeMillis())
            }
        }

    suspend fun restore(backup: FriendsBackup, expectedDataset: String, expectedRevision: Long) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                validateFriendsBackup(backup)
                val before = checkNotNull(state.value)
                require(
                    before.datasetId == expectedDataset && before.revision == expectedRevision
                ) {
                    "本地数据已变化，请重新下载预览"
                }
                File(
                        appContext.filesDir,
                        "friends-pre-restore-${System.currentTimeMillis()}-${UUID.randomUUID()}.json",
                    )
                    .writeText(friendsJson.encodeToString(before))
                database.withTransaction {
                    dao.clearFriends()
                    backup.friends.forEach { dao.insertFriend(it) }
                    backup.events.forEach { dao.insertEvent(it) }
                    dao.metadata(
                        FriendsMetadata(datasetId = backup.datasetId, revision = backup.revision)
                    )
                }
                state.value = backup
            }
        }

    fun close() = database.close()
}
