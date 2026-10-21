package com.example.ericloop.friends.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigInteger
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val friendsJson = Json {
    prettyPrint = true
    encodeDefaults = true
}

@Serializable
enum class FriendGender(val label: String) {
    UNSPECIFIED("未设置"),
    BOY("男"),
    GIRL("女"),
}

@Serializable
@Entity(tableName = "friends", indices = [Index(value = ["colorArgb"], unique = true)])
data class Friend(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val gender: FriendGender = FriendGender.UNSPECIFIED,
    val intro: String = "",
    val notes: String = "",
    val initialScore: String = "0",
    val colorArgb: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Serializable
@Entity(
    tableName = "friend_events",
    foreignKeys =
        [
            ForeignKey(
                entity = Friend::class,
                parentColumns = ["id"],
                childColumns = ["friendId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("friendId")],
)
data class FriendEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val friendId: String,
    val delta: String,
    val description: String,
    val occurredAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = occurredAt,
)

@Serializable
data class FriendsBackup(
    val schemaVersion: Int = 1,
    val datasetId: String = UUID.randomUUID().toString(),
    val revision: Long = 0,
    val exportedAt: Long = System.currentTimeMillis(),
    val friends: List<Friend> = emptyList(),
    val events: List<FriendEvent> = emptyList(),
)

fun parseScore(text: String): BigInteger {
    val normalized = buildString {
        text.forEach { character ->
            append(
                when (character) {
                    '－',
                    '−' -> '-'
                    '＋' -> '+'
                    in '０'..'９' -> '0' + (character - '０')
                    else -> character
                }
            )
        }
    }
    require(normalized.matches(Regex("[+-]?[0-9]+"))) { "请输入整数分数" }
    return BigInteger(normalized)
}

fun signedScore(value: BigInteger): String = if (value.signum() > 0) "+$value" else value.toString()

data class FriendSummary(val friend: Friend, val score: BigInteger, val lastInteraction: Long?)

fun FriendsBackup.summaries(): List<FriendSummary> {
    val grouped = events.groupBy { it.friendId }
    return friends.map { friend ->
        val history = grouped[friend.id].orEmpty()
        FriendSummary(
            friend,
            history.fold(parseScore(friend.initialScore)) { score, event ->
                score + parseScore(event.delta)
            },
            history.maxOfOrNull { it.occurredAt },
        )
    }
}

enum class FriendSort(val label: String) {
    SCORE("好感度"),
    INTERACTION("最近互动"),
    CREATED("创建时间"),
}

fun List<FriendSummary>.ranked(sort: FriendSort, ascending: Boolean): List<FriendSummary> =
    sortedWith { a, b ->
        val primary =
            when (sort) {
                FriendSort.SCORE -> a.score.compareTo(b.score)
                FriendSort.CREATED -> a.friend.createdAt.compareTo(b.friend.createdAt)
                FriendSort.INTERACTION -> {
                    if (a.lastInteraction == null && b.lastInteraction != null) return@sortedWith 1
                    if (a.lastInteraction != null && b.lastInteraction == null) return@sortedWith -1
                    (a.lastInteraction ?: 0).compareTo(b.lastInteraction ?: 0)
                }
            }
        val ordered = if (ascending) primary else -primary
        if (ordered != 0) ordered
        else {
            val recent = (b.lastInteraction ?: 0).compareTo(a.lastInteraction ?: 0)
            if (recent != 0) recent
            else {
                val created = b.friend.createdAt.compareTo(a.friend.createdAt)
                if (created != 0) created else a.friend.id.compareTo(b.friend.id)
            }
        }
    }
