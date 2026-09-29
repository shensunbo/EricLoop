package com.example.ericloop.data

import java.time.LocalDate
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private val envelopeKeys = setOf("schemaVersion", "datasetId", "revision", "exportedAt", "records", "tags", "checkIns", "events")
private val recordKeys = setOf("id", "title", "body", "kind", "planType", "status", "tagIds", "deadline", "createdAt", "updatedAt", "deletedAt")
private val tagKeys = setOf("id", "name", "preset")
private val checkInKeys = setOf("id", "recordId", "note", "occurredAt", "createdAt", "updatedAt", "deletedAt")
private val eventKeys = setOf("id", "recordId", "operation", "title", "operatedAt", "sequence", "beforeJson", "afterJson")

private fun requiredObject(element: JsonElement, keys: Set<String>): JsonObject {
    require(element is JsonObject && element.keys.containsAll(keys)) { "备份字段缺失或格式无效" }
    return element
}

/** Verify required fields before decoding: missing fields must never generate new IDs or times. */
fun decodeBackup(text: String): Backup {
    val root = requiredObject(backupJson.parseToJsonElement(text), envelopeKeys)
    mapOf("records" to recordKeys, "tags" to tagKeys, "checkIns" to checkInKeys, "events" to eventKeys).forEach { (table, keys) ->
        val rows = root[table]
        require(rows is JsonArray) { "备份数据表格式无效" }
        rows.forEach { requiredObject(it, keys) }
    }
    return backupJson.decodeFromString<Backup>(text).also(::validateBackup)
}

private fun validateTimes(created: Long, updated: Long, deleted: Long?) {
    require(created >= 0 && updated >= created && (deleted == null || deleted in created..updated)) { "时间戳无效" }
}

private fun validateTag(tag: LoopTag) {
    require(tag.id.isNotBlank() && tag.name.isNotBlank() && tag.name == tag.name.trim()) { "标签名称或 ID 无效" }
}

private fun validateRecord(record: LoopRecord, tags: List<LoopTag>) {
    require(record.id.isNotBlank() && record.title.isNotBlank() && record.title == record.title.trim()) { "标题或 ID 无效" }
    val tagIds = tags.map { it.id }.toSet()
    require(record.tagIds.distinct().size == record.tagIds.size && record.tagIds.all(tagIds::contains)) { "标签引用无效" }
    record.deadline?.let { require(LocalDate.parse(it).toString() == it) { "截止日期无效" } }
    validateTimes(record.createdAt, record.updatedAt, record.deletedAt)
}

private fun validateCheckIn(checkIn: CheckIn, records: Map<String, LoopRecord>) {
    val owner = records[checkIn.recordId]
    require(checkIn.id.isNotBlank() && owner != null && owner.kind == RecordKind.PLAN && owner.planType == PlanType.LONG_TERM) { "打卡所属计划无效" }
    require(checkIn.occurredAt in 0..System.currentTimeMillis()) { "打卡时间不能在未来" }
    validateTimes(checkIn.createdAt, checkIn.updatedAt, checkIn.deletedAt)
}

/** Throws before any persistent change if a backup is malformed or unsupported. */
private fun validateCurrent(backup: Backup) {
    require(backup.schemaVersion == 1) { "不支持的备份版本" }
    require(backup.datasetId.isNotBlank() && backup.revision >= 0 && backup.exportedAt >= 0) { "备份元数据无效" }
    fun ids(values: List<String>) = require(values.all { it.isNotBlank() } && values.distinct().size == values.size) { "备份包含重复或空 ID" }
    ids(backup.records.map { it.id }); ids(backup.tags.map { it.id })
    ids(backup.checkIns.map { it.id })
    backup.tags.forEach(::validateTag)
    require(backup.tags.map { it.name.lowercase(Locale.ROOT) }.distinct().size == backup.tags.size) { "标签名称重复" }
    backup.records.forEach { validateRecord(it, backup.tags) }
    val records = backup.records.associateBy { it.id }
    backup.checkIns.forEach { validateCheckIn(it, records) }
    val presets = initialTags().associateBy { it.id }
    require(backup.tags.filter { it.preset }.associateBy { it.id } == presets) { "内置标签缺失或无效" }
}

fun validateBackup(backup: Backup) {
    validateCurrent(backup)
    require(backup.events.map { it.id }.all { it.isNotBlank() } && backup.events.map { it.id }.distinct().size == backup.events.size) { "历史 ID 无效" }
    require(backup.revision == backup.events.size.toLong()) { "历史记录不完整" }
    val replay = ReplayState(emptyList(), initialTags(), emptyList())
    backup.events.sortedBy { it.sequence }.forEachIndexed { index, event ->
        require(event.sequence == index.toLong() + 1 && event.operatedAt >= 0 && event.title.isNotBlank()) { "历史序号或时间无效" }
        require(event.recordId == null || backup.records.any { it.id == event.recordId }) { "历史引用无效" }
        validateEvent(event, backup)
        replay.apply(event)
    }
    replay.requireMatches(backup)
}

/** Local writes only need to validate the new event against the previously verified state. */
internal fun validateMutation(previous: Backup, next: Backup) {
    validateCurrent(next)
    require(next.datasetId == previous.datasetId && next.revision == previous.revision + 1 &&
        next.events.size == previous.events.size + 1 && next.events.dropLast(1) == previous.events) { "数据版本或历史变更无效" }
    val event = next.events.last()
    require(event.id.isNotBlank() && previous.events.none { it.id == event.id } && event.sequence == next.revision &&
        event.operatedAt >= 0 && event.title.isNotBlank()) { "新增历史元数据无效" }
    validateEvent(event, next)
    val replay = ReplayState(previous.records, previous.tags, previous.checkIns)
    replay.apply(event)
    replay.requireMatches(next)
}

private fun initialTags() = listOf(
    LoopTag("work", "工作", true), LoopTag("study", "学习", true),
    LoopTag("life", "生活", true), LoopTag("health", "健康", true), LoopTag("project", "个人项目", true),
)

private class ReplayState(records: List<LoopRecord>, tags: List<LoopTag>, checkIns: List<CheckIn>) {
    private val records = records.associateBy { it.id }.toMutableMap()
    private val tags = tags.associateBy { it.id }.toMutableMap()
    private val checkIns = checkIns.associateBy { it.id }.toMutableMap()

    private fun recordSnapshot(text: String): LoopRecord {
        val obj = requiredObject(backupJson.parseToJsonElement(text), recordKeys + "tags")
        val record = backupJson.decodeFromString<LoopRecord>(text)
        val names = (obj.getValue("tags") as JsonArray).map { backupJson.decodeFromString<LoopTag>(it.toString()) }.associateBy { it.id }
        require(names == tags.filterKeys { it in record.tagIds }) { "历史标签名称与操作时数据不一致" }
        return record
    }

    fun apply(event: HistoryEvent) {
        when (event.operation) {
            in recordOperations -> {
                val after = recordSnapshot(requireNotNull(event.afterJson))
                val old = records[after.id]
                if (event.operation in creationOperations) {
                    require(old == null && after.deletedAt == null) { "记录创建历史重复或无效" }
                } else {
                    val before = recordSnapshot(requireNotNull(event.beforeJson))
                    require(old != null && before == old) { "记录历史前后不连续" }
                    require(after.updatedAt >= before.updatedAt && after.createdAt == before.createdAt) { "记录历史时间无效" }
                    when (event.operation) {
                        "恢复记录" -> require(before.deletedAt != null && after.deletedAt == null) { "恢复记录历史无效" }
                        else -> require(before.deletedAt == null) { "已删除记录的操作无效" }
                    }
                }
                require(event.title == after.title) { "历史标题与快照不一致" }
                require(checkIns.values.none { it.recordId == after.id } ||
                    (after.kind == RecordKind.PLAN && after.planType == PlanType.LONG_TERM)) { "历史计划类型与打卡不一致" }
                records[after.id] = after
            }
            in checkInOperations -> {
                val after = backupJson.decodeFromString<CheckIn>(requireNotNull(event.afterJson))
                val old = checkIns[after.id]
                val owner = records[after.recordId]
                require(owner != null && owner.kind == RecordKind.PLAN && owner.planType == PlanType.LONG_TERM) { "操作时的打卡所属计划无效" }
                if (event.operation != "删除打卡") require(owner.deletedAt == null) { "不能为已删除计划打卡" }
                if (event.operation in creationOperations) {
                    require(old == null) { "打卡创建历史重复" }
                } else {
                    val before = backupJson.decodeFromString<CheckIn>(requireNotNull(event.beforeJson))
                    require(old != null && before == old && before.deletedAt == null && after.updatedAt >= before.updatedAt) { "打卡历史前后不连续" }
                }
                require(event.title == owner.title) { "打卡历史标题与所属计划不一致" }
                checkIns[after.id] = after
                records[owner.id] = owner.copy(updatedAt = maxOf(owner.updatedAt, after.updatedAt))
            }
            in tagOperations -> {
                val after = backupJson.decodeFromString<LoopTag>(requireNotNull(event.afterJson))
                val old = tags[after.id]
                if (event.operation == "新增标签") {
                    require(old == null && !after.preset) { "标签创建历史重复或无效" }
                } else {
                    val before = backupJson.decodeFromString<LoopTag>(requireNotNull(event.beforeJson))
                    require(old != null && before == old && after.preset == before.preset) { "标签历史前后不连续" }
                }
                require(tags.values.none { it.id != after.id && it.name.equals(after.name, ignoreCase = true) }) { "历史标签名称重复" }
                require(event.title == after.name) { "标签历史标题无效" }
                tags[after.id] = after
            }
        }
    }

    fun requireMatches(backup: Backup) {
        require(records == backup.records.associateBy { it.id } && tags == backup.tags.associateBy { it.id } &&
            checkIns == backup.checkIns.associateBy { it.id }) { "当前数据与完整历史不一致" }
    }
}

private val recordOperations = setOf("创建想法", "创建计划", "编辑想法", "编辑计划", "转为计划", "状态变更", "删除记录", "恢复记录")
private val checkInOperations = setOf("打卡", "补记打卡", "编辑打卡", "删除打卡")
private val tagOperations = setOf("新增标签", "重命名标签")
private val creationOperations = setOf("创建想法", "创建计划", "打卡", "补记打卡", "新增标签")

private fun validateEvent(event: HistoryEvent, backup: Backup) {
    require(event.operation in recordOperations + checkInOperations + tagOperations) { "历史操作无效" }
    require(event.afterJson != null && ((event.operation in creationOperations && event.beforeJson == null) ||
        (event.operation !in creationOperations && event.beforeJson != null))) { "历史快照缺失" }
    val records = backup.records.associateBy { it.id }
    val snapshots = listOfNotNull(event.beforeJson, event.afterJson)
    when (event.operation) {
        in recordOperations -> {
            require(event.recordId != null) { "记录历史引用无效" }
            val values = snapshots.map { text ->
                val obj = requiredObject(backupJson.parseToJsonElement(text), recordKeys + "tags")
                val tags = obj["tags"]
                require(tags is JsonArray) { "历史标签快照无效" }
                val tagValues = tags.map {
                    requiredObject(it, tagKeys)
                    backupJson.decodeFromString<LoopTag>(it.toString()).also(::validateTag)
                }
                val record = backupJson.decodeFromString<LoopRecord>(text)
                require(tagValues.map { it.id }.distinct().size == tagValues.size && tagValues.map { it.id }.toSet() == record.tagIds.toSet()) { "历史标签引用无效" }
                validateRecord(record, tagValues)
                require(record.id == event.recordId) { "历史记录 ID 不匹配" }
                record
            }
            val after = values.last()
            when (event.operation) {
                "创建想法", "编辑想法" -> require(after.kind == RecordKind.IDEA) { "历史记录类型无效" }
                "创建计划", "编辑计划", "转为计划", "状态变更" -> require(after.kind == RecordKind.PLAN) { "历史记录类型无效" }
                "删除记录" -> require(values.first().deletedAt == null && after.deletedAt != null) { "删除历史无效" }
                "恢复记录" -> require(values.first().deletedAt != null && after.deletedAt == null) { "恢复历史无效" }
            }
            require(values.all { it.createdAt == after.createdAt }) { "历史创建时间不一致" }
        }
        in checkInOperations -> {
            require(event.recordId != null) { "打卡历史引用无效" }
            val values = snapshots.map { text ->
                requiredObject(backupJson.parseToJsonElement(text), checkInKeys)
                backupJson.decodeFromString<CheckIn>(text).also {
                    validateCheckIn(it, records)
                    require(it.recordId == event.recordId && backup.checkIns.any { current -> current.id == it.id && current.recordId == it.recordId }) { "打卡历史 ID 不匹配" }
                }
            }
            require(values.map { it.id }.distinct().size == 1 && values.map { it.createdAt }.distinct().size == 1) { "打卡历史 ID 或创建时间不一致" }
            if (event.operation == "删除打卡") require(values.first().deletedAt == null && values.last().deletedAt != null) { "删除打卡历史无效" }
            else require(values.all { it.deletedAt == null }) { "打卡历史状态无效" }
        }
        in tagOperations -> {
            require(event.recordId == null) { "标签历史引用无效" }
            val values = snapshots.map { text ->
                requiredObject(backupJson.parseToJsonElement(text), tagKeys)
                backupJson.decodeFromString<LoopTag>(text).also {
                    validateTag(it)
                    require(backup.tags.any { current -> current.id == it.id }) { "标签历史 ID 不匹配" }
                }
            }
            require(values.map { it.id }.distinct().size == 1) { "标签历史 ID 不一致" }
        }
    }
}
