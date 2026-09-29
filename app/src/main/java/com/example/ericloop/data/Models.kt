package com.example.ericloop.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

val backupJson = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }
fun newId(): String = UUID.randomUUID().toString()

@Serializable enum class RecordKind(val label: String) { IDEA("想法"), PLAN("计划") }
@Serializable enum class PlanType(val label: String) { ONCE("一次性"), LONG_TERM("长期") }
@Serializable enum class PlanStatus(val label: String) {
    NOT_STARTED("未开始"), ACTIVE("进行中"), PAUSED("暂停"), COMPLETED("完成"), ABANDONED("放弃")
}
@Serializable data class LoopRecord(
    val id: String = newId(), val title: String, val body: String = "",
    val kind: RecordKind = RecordKind.IDEA, val planType: PlanType = PlanType.ONCE,
    val status: PlanStatus = PlanStatus.NOT_STARTED, val tagIds: List<String> = emptyList(),
    val deadline: String? = null, val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt, val deletedAt: Long? = null,
)
@Serializable data class LoopTag(val id: String = newId(), val name: String, val preset: Boolean = false)
@Serializable data class CheckIn(
    val id: String = newId(), val recordId: String, val note: String = "",
    val occurredAt: Long = System.currentTimeMillis(), val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt, val deletedAt: Long? = null,
)
@Serializable data class HistoryEvent(
    val id: String = newId(), val recordId: String? = null, val operation: String,
    val title: String, val operatedAt: Long = System.currentTimeMillis(), val sequence: Long,
    val beforeJson: String? = null, val afterJson: String? = null,
)
@Serializable data class Backup(
    val schemaVersion: Int = 1, val datasetId: String = newId(), val revision: Long = 0,
    val exportedAt: Long = System.currentTimeMillis(),
    val records: List<LoopRecord> = emptyList(), val tags: List<LoopTag> = emptyList(),
    val checkIns: List<CheckIn> = emptyList(), val events: List<HistoryEvent> = emptyList(),
)
