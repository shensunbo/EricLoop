package com.example.ericloop.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class DataRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = Room.databaseBuilder(appContext, LoopDatabase::class.java, "ericloop.db").build()
    private val dao = db.loopDao()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val current = MutableStateFlow(Backup(datasetId = "loading", exportedAt = 0))
    private val loaded = MutableStateFlow(false)
    private val loadError = MutableStateFlow<String?>(null)
    val data: StateFlow<Backup> = current.asStateFlow()
    val ready: StateFlow<Boolean> = loaded.asStateFlow()
    val initializationError: StateFlow<String?> = loadError.asStateFlow()
    private val initialization = scope.async {
        try {
            mutex.withLock {
                val initial = db.withTransaction {
                    val metadata = dao.metadata()
                    if (metadata == null) {
                        require(dao.records().isEmpty() && dao.tags().isEmpty() && dao.checkIns().isEmpty() && dao.events().isEmpty()) { "数据库元数据缺失" }
                        val seed = Backup(tags = listOf(
                            LoopTag("work", "工作", true), LoopTag("study", "学习", true),
                            LoopTag("life", "生活", true), LoopTag("health", "健康", true),
                            LoopTag("project", "个人项目", true),
                        ))
                        replaceAll(seed)
                        seed
                    } else {
                        val document = backupJson.parseToJsonElement(metadata.payload).jsonObject.toMutableMap()
                        document["records"] = kotlinx.serialization.json.JsonArray(dao.records().map { backupJson.parseToJsonElement(it.payload) })
                        document["tags"] = kotlinx.serialization.json.JsonArray(dao.tags().map { backupJson.parseToJsonElement(it.payload) })
                        document["checkIns"] = kotlinx.serialization.json.JsonArray(dao.checkIns().map { backupJson.parseToJsonElement(it.payload) })
                        document["events"] = kotlinx.serialization.json.JsonArray(dao.events().map { backupJson.parseToJsonElement(it.payload) })
                        decodeBackup(JsonObject(document).toString()).also { migrated ->
                            if (document["schemaVersion"]?.toString() == "1") replaceAll(migrated)
                        }
                    }
                }
                current.value = initial
                loaded.value = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            loadError.value = "无法读取本地数据，请重启应用后重试。"
            throw failure
        }
    }

    private suspend fun replaceAll(value: Backup) {
        dao.clearRecords(); dao.clearTags(); dao.clearCheckIns(); dao.clearEvents()
        dao.records(value.records.map { RecordEntity(it.id, backupJson.encodeToString(it)) })
        dao.tags(value.tags.map { TagEntity(it.id, backupJson.encodeToString(it)) })
        dao.checkIns(value.checkIns.map { CheckInEntity(it.id, backupJson.encodeToString(it)) })
        dao.events(value.events.map { EventEntity(it.id, it.sequence, it.recordId, backupJson.encodeToString(it)) })
        dao.metadata(MetadataEntity(payload = backupJson.encodeToString(value.copy(records = emptyList(), tags = emptyList(), checkIns = emptyList(), events = emptyList()))))
    }

    private suspend fun persistChanges(previous: Backup, value: Backup) {
        val records = previous.records.associateBy { it.id }
        val tags = previous.tags.associateBy { it.id }
        val checkIns = previous.checkIns.associateBy { it.id }
        dao.records(value.records.filter { records[it.id] != it }.map { RecordEntity(it.id, backupJson.encodeToString(it)) })
        dao.tags(value.tags.filter { tags[it.id] != it }.map { TagEntity(it.id, backupJson.encodeToString(it)) })
        dao.checkIns(value.checkIns.filter { checkIns[it.id] != it }.map { CheckInEntity(it.id, backupJson.encodeToString(it)) })
        dao.events(value.events.drop(previous.events.size).map { EventEntity(it.id, it.sequence, it.recordId, backupJson.encodeToString(it)) })
        dao.metadata(MetadataEntity(payload = backupJson.encodeToString(value.copy(records = emptyList(), tags = emptyList(), checkIns = emptyList(), events = emptyList()))))
    }

    private fun recordJson(record: LoopRecord, source: Backup): String {
        val properties = backupJson.parseToJsonElement(backupJson.encodeToString(record)).jsonObject.toMutableMap()
        properties["tags"] = backupJson.parseToJsonElement(backupJson.encodeToString(source.tags.filter { it.id in record.tagIds }))
        return backupJson.encodeToString(JsonObject(properties))
    }

    private fun event(source: Backup, changed: Backup, operation: String, title: String, recordId: String?, after: String): Backup {
        require(source.revision < Long.MAX_VALUE) { "数据版本已达上限" }
        val nextSequence = maxOf(source.revision, source.events.maxOfOrNull { it.sequence } ?: 0) + 1
        return changed.copy(revision = nextSequence, events = source.events + HistoryEvent(
            recordId = recordId, operation = operation, title = title, sequence = nextSequence,
            beforeJson = null, afterJson = after,
        ))
    }

    private suspend fun mutate(block: (Backup) -> Backup) {
        initialization.await()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val next = block(current.value)
                if (next == current.value) return@withLock
                validateMutation(current.value, next)
                db.withTransaction { persistChanges(current.value, next) }
                current.value = next
            }
        }
    }

    suspend fun saveRecord(record: LoopRecord) = mutate { source ->
        val old = source.records.find { it.id == record.id }
        require(old?.deletedAt == null) { "请先恢复记录" }
        val normalized = record.copy(title = record.title.trim(), emoji = record.emoji?.trim()?.ifBlank { null }, createdAt = old?.createdAt ?: record.createdAt,
            updatedAt = old?.updatedAt ?: record.createdAt, deletedAt = old?.deletedAt)
        if (old == normalized) source else {
            val next = normalized.copy(updatedAt = maxOf(System.currentTimeMillis(), normalized.createdAt, old?.updatedAt ?: 0))
            val changed = source.copy(records = source.records.filterNot { it.id == next.id } + next)
            event(source, changed, if (old == null) "创建${next.kind.label}" else "编辑${next.kind.label}", next.title, next.id,
                recordJson(next, changed))
        }
    }

    private suspend fun updateRecord(id: String, operation: String, transform: (LoopRecord) -> LoopRecord) = mutate { source ->
        val old = source.records.find { it.id == id } ?: error("记录不存在")
        val candidate = transform(old)
        if (candidate == old) source else {
            val next = candidate.copy(updatedAt = maxOf(System.currentTimeMillis(), old.updatedAt))
            val changed = source.copy(records = source.records.map { if (it.id == id) next else it })
            event(source, changed, operation, next.title, id, recordJson(next, changed))
        }
    }
    suspend fun changeStatus(id: String, status: PlanStatus) = updateRecord(id, "状态变更") {
        require(it.kind == RecordKind.PLAN && it.deletedAt == null) { "计划无效" }; it.copy(status = status)
    }
    suspend fun convert(id: String, type: PlanType) = updateRecord(id, "转为计划") {
        require(it.deletedAt == null) { "请先恢复记录" }; it.copy(kind = RecordKind.PLAN, planType = type)
    }
    suspend fun trash(id: String) = updateRecord(id, "删除记录") {
        if (it.deletedAt != null) it else it.copy(deletedAt = maxOf(System.currentTimeMillis(), it.updatedAt))
    }
    suspend fun restoreRecord(id: String) = updateRecord(id, "恢复记录") { it.copy(deletedAt = null) }

    suspend fun saveCheckIn(checkIn: CheckIn) = mutate { source ->
        val owner = source.records.find { it.id == checkIn.recordId }
        require(owner != null && owner.deletedAt == null && owner.kind == RecordKind.PLAN && owner.planType == PlanType.LONG_TERM) { "只能为长期计划打卡" }
        val old = source.checkIns.find { it.id == checkIn.id }
        require(old == null || (old.recordId == checkIn.recordId && old.deletedAt == null)) { "打卡记录无效" }
        val candidate = checkIn.copy(createdAt = old?.createdAt ?: checkIn.createdAt, updatedAt = old?.updatedAt ?: checkIn.createdAt, deletedAt = null)
        if (candidate == old) source else {
            val now = System.currentTimeMillis()
            val next = candidate.copy(updatedAt = maxOf(now, candidate.createdAt, old?.updatedAt ?: 0))
            val changed = source.copy(checkIns = source.checkIns.filterNot { it.id == next.id } + next,
                records = source.records.map { if (it.id == owner.id) it.copy(updatedAt = maxOf(next.updatedAt, it.updatedAt)) else it })
            val operation = if (old != null) "编辑打卡" else if (java.time.Instant.ofEpochMilli(next.occurredAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().isBefore(LocalDate.now())) "补记打卡" else "打卡"
            event(source, changed, operation, owner.title, owner.id, backupJson.encodeToString(next))
        }
    }
    suspend fun deleteCheckIn(id: String) = mutate { source ->
        val old = source.checkIns.find { it.id == id } ?: error("打卡不存在")
        if (old.deletedAt != null) source else {
            val owner = source.records.first { it.id == old.recordId }
            val now = System.currentTimeMillis()
            val next = old.copy(deletedAt = maxOf(now, old.updatedAt), updatedAt = maxOf(now, old.updatedAt))
            val changed = source.copy(checkIns = source.checkIns.map { if (it.id == id) next else it },
                records = source.records.map { if (it.id == owner.id) it.copy(updatedAt = maxOf(next.updatedAt, it.updatedAt)) else it })
            event(source, changed, "删除打卡", owner.title, owner.id, backupJson.encodeToString(next))
        }
    }
    suspend fun addTag(name: String, emoji: String? = null) = mutate { source ->
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && source.tags.none { it.name.equals(trimmed, ignoreCase = true) }) { "标签名称不能为空或重复" }
        val next = LoopTag(name = trimmed, emoji = emoji?.trim()?.ifBlank { null })
        event(source, source.copy(tags = source.tags + next), "新增标签", trimmed, null, backupJson.encodeToString(next))
    }
    suspend fun editTag(id: String, name: String, emoji: String?) = mutate { source ->
        val old = source.tags.find { it.id == id } ?: error("标签不存在")
        require(!old.preset) { "内置标签保持固定名称，请创建自定义标签" }
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && source.tags.none { it.id != id && it.name.equals(trimmed, ignoreCase = true) }) { "标签名称不能为空或重复" }
        val next = old.copy(name = trimmed, emoji = emoji?.trim()?.ifBlank { null })
        if (old == next) source else {
            event(source, source.copy(tags = source.tags.map { if (it.id == id) next else it }),
                if (old.emoji == next.emoji) "重命名标签" else "编辑标签", trimmed, null,
                backupJson.encodeToString(next))
        }
    }
    suspend fun snapshot(): Backup {
        initialization.await()
        return mutex.withLock { current.value.copy(exportedAt = System.currentTimeMillis()) }
    }
    suspend fun restoreBackup(backup: Backup) {
        validateBackup(backup)
        initialization.await()
        withContext(Dispatchers.IO) {
            mutex.withLock {
                validateBackup(backup)
                val safety = File(appContext.filesDir, "pre-restore-${System.currentTimeMillis()}-${newId()}.json")
                java.io.FileOutputStream(safety).use { stream ->
                    stream.write(backupJson.encodeToString(current.value.copy(exportedAt = System.currentTimeMillis())).toByteArray(Charsets.UTF_8))
                    stream.fd.sync()
                }
                db.withTransaction { replaceAll(backup) }
                current.value = backup
            }
        }
    }
}
