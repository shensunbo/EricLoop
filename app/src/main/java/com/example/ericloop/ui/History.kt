package com.example.ericloop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.ericloop.R
import com.example.ericloop.data.*
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.Instant

private fun operationAccent(operation: String): UiAccent {
    return when (operation) {
        "打卡", "补记打卡" -> UiAccent(Color(0xFFEAF7EE), Color(0xFF286844))
        "编辑想法", "编辑计划", "编辑打卡" -> UiAccent(Color(0xFFF3ECFA), Color(0xFF724895))
        "创建想法", "创建计划" -> UiAccent(Color(0xFFECF4FD), Color(0xFF395F8A))
        "状态变更" -> UiAccent(Color(0xFFFCF3E2), Color(0xFF806019))
        "转为计划" -> UiAccent(Color(0xFFE8F7F7), Color(0xFF2E6C70))
        "删除记录", "删除打卡" -> UiAccent(Color(0xFFFCECEF), Color(0xFF9A4654))
        "恢复记录" -> UiAccent(Color(0xFFE9F7F2), Color(0xFF326F5D))
        else -> UiAccent(Color(0xFFF0F2F7), Color(0xFF58627A))
    }
}

private data class CheckInPreview(val note: String, val occurredAt: Long?)

private fun checkInPreview(event: HistoryEvent): CheckInPreview? {
    if (event.operation !in setOf("打卡", "补记打卡", "编辑打卡", "删除打卡")) return null
    val raw = if (event.operation == "删除打卡") event.beforeJson else event.afterJson
    return runCatching {
        val fields = backupJson.parseToJsonElement(requireNotNull(raw)).jsonObject
        CheckInPreview(fields["note"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "无备注" },
            fields["occurredAt"]?.jsonPrimitive?.longOrNull)
    }.getOrNull()
}

@Composable fun HistoryDirectory(backup: Backup, onSelect: (String) -> Unit) {
    val records = remember(backup.records) { backup.records.sortedByDescending { it.updatedAt } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionHeading("变更记录", "选择一条想法或计划，查看它的历程。") }
        if (records.isEmpty()) item { EmptyPanel("旅程从第一条记录开始", "创建想法或计划后，可以在这里查看它的变更。", R.drawable.ic_timeline) }
        items(records, key = { it.id }) { record ->
            Card(onClick = { onSelect(record.id) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RecordIcon(record)
                    Text(record.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HistoryPage(backup: Backup, recordId: String, onOpen: (String) -> Unit) {
    var operation by rememberSaveable(recordId) { mutableStateOf("全部") }
    var start by rememberSaveable(recordId) { mutableStateOf("") }
    var end by rememberSaveable(recordId) { mutableStateOf("") }
    var datePickerTarget by remember { mutableStateOf<String?>(null) }
    var selectedEvent by remember { mutableStateOf<HistoryEvent?>(null) }
    val startDate = runCatching { LocalDate.parse(start) }.getOrNull()
    val endDate = runCatching { LocalDate.parse(end) }.getOrNull()
    val datesValid = (start.isBlank() || startDate != null) && (end.isBlank() || endDate != null) && (startDate == null || endDate == null || !startDate.isAfter(endDate))
    val recordEvents = remember(backup.events, recordId) { backup.events.filter { it.recordId == recordId } }
    val events = recordEvents.filter {
        val date = java.time.Instant.ofEpochMilli(it.operatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        datesValid && (operation == "全部" || it.operation == operation) &&
            (startDate == null || !date.isBefore(startDate)) && (endDate == null || !date.isAfter(endDate))
    }.sortedByDescending { it.sequence }
    val ids = events.map { it.recordId ?: "tags" }.distinct()
    val lanes = ids.withIndex().associate { it.value to it.index }
    val ranges = ids.associateWith { id -> events.indices.filter { (events[it].recordId ?: "tags") == id }.let { it.first()..it.last() } }
    val graphScroll = rememberScrollState()
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        SectionHeading(backup.records.find { it.id == recordId }?.title ?: "记录已不存在", "${events.size} 次变更 · 点击节点查看当时的内容")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceMenu("操作", listOf("全部") + recordEvents.map { it.operation }.distinct(), operation) { operation = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactDateField("开始日期", start, Modifier.weight(1f), isError = start.isNotBlank() && startDate == null) { datePickerTarget = "start" }
            CompactDateField("结束日期", end, Modifier.weight(1f), isError = end.isNotBlank() && (endDate == null || !datesValid)) { datePickerTarget = "end" }
        }
        Spacer(Modifier.height(16.dp))
        if (events.isEmpty()) EmptyPanel(if (datesValid) "旅程从第一条记录开始" else "请检查日期范围", if (datesValid) "创建、编辑、打卡和标签变更都会出现在这里。" else "输入 yyyy-MM-dd 格式，起始日期不能晚于结束日期。", R.drawable.ic_timeline)
        else BoxWithConstraints(Modifier.weight(1f)) {
            val laneWidth = (ids.size.coerceAtLeast(1) * 22 + 12).dp
            val rowWidth = maxOf(maxWidth, laneWidth + 270.dp)
            Box(Modifier.horizontalScroll(graphScroll)) {
                LazyColumn(Modifier.width(rowWidth).fillMaxHeight(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(events, key = { _, event -> event.id }) { index, event ->
                        val lane = lanes.getValue(event.recordId ?: "tags")
                        val accent = operationAccent(event.operation)
                        val preview = remember(event) { checkInPreview(event) }
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            Canvas(Modifier.width(laneWidth).fillMaxHeight()) {
                                ranges.forEach { (id, range) ->
                                    if (index in range) {
                                        val track = lanes.getValue(id)
                                        val x = (12 + track * 22).dp.toPx()
                                        drawLine(trackColor, Offset(x, if (index == range.first) 30.dp.toPx() else 0f), Offset(x, if (index == range.last) 30.dp.toPx() else size.height), strokeWidth = 2.dp.toPx())
                                    }
                                }
                                drawCircle(accent.content, radius = 6.dp.toPx(), center = Offset((12 + lane * 22).dp.toPx(), 30.dp.toPx()))
                            }
                            Card(onClick = { selectedEvent = event }, modifier = Modifier.weight(1f).padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = accent.container)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${displayTime(event.operatedAt)}  ·  #${event.sequence}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(event.operation, style = MaterialTheme.typography.labelMedium, color = accent.content)
                                    preview?.let {
                                        Text(it.note, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        it.occurredAt?.takeIf { occurredAt -> displayTime(occurredAt) != displayTime(event.operatedAt) }
                                            ?.let { occurredAt -> Text("发生于 ${displayTime(occurredAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    selectedEvent?.let { event ->
        AlertDialog(onDismissRequest = { selectedEvent = null }, title = { Text(event.operation) }, text = {
            LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { Text(event.title, style = MaterialTheme.typography.titleMedium); Text(displayTime(event.operatedAt, "yyyy年MM月dd日 HH:mm:ss"), style = MaterialTheme.typography.labelMedium) }
                event.beforeJson?.let { item { SnapshotText("变更前", it) } }
                event.afterJson?.let { item { SnapshotText("变更后", it) } }
            }
        }, confirmButton = { TextButton(onClick = { selectedEvent = null }) { Text("关闭") } }, dismissButton = {
            event.recordId?.let { id -> TextButton(onClick = { selectedEvent = null; onOpen(id) }) { Text("打开记录") } }
        })
    }
    datePickerTarget?.let { target ->
        val currentDate = if (target == "start") startDate else endDate
        val state = rememberDatePickerState(initialSelectedDateMillis = currentDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(onDismissRequest = { datePickerTarget = null },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { selected ->
                    val date = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    if (target == "start") start = date else end = date
                }
                datePickerTarget = null
            }) { Text("确定") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { if (target == "start") start = "" else end = ""; datePickerTarget = null }) { Text("清除") }
                    TextButton(onClick = { datePickerTarget = null }) { Text("取消") }
                }
            }) { DatePicker(state) }
    }
}

@Composable private fun CompactDateField(label: String, value: String, modifier: Modifier = Modifier, isError: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Row(modifier.height(48.dp).semantics { contentDescription = if (value.isBlank()) label else "$label：$value" }
        .clickable(role = Role.Button, onClick = onClick).padding(vertical = 1.dp)
        .border(1.dp, if (isError) colors.error else colors.outline, shape).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_event), contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.onSurfaceVariant)
        Text(value.ifBlank { label }, style = MaterialTheme.typography.bodyMedium,
            color = if (value.isBlank()) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun SnapshotText(label: String, raw: String) {
    val text = remember(raw) { humanSnapshot(raw) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        androidx.compose.foundation.text.selection.SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium) }
    }
}

private fun humanSnapshot(raw: String): String = runCatching {
    val o = backupJson.parseToJsonElement(raw).jsonObject
    fun value(key: String) = (o[key] as? JsonPrimitive)?.contentOrNull
    buildString {
        value("title")?.let { appendLine(it) }
        if (o.containsKey("kind")) appendLine("图标：${value("emoji") ?: "默认"}")
        value("body")?.let { appendLine(it.ifBlank { "（正文为空）" }) }
        value("kind")?.let { appendLine("类型：" + if (it == "IDEA") "想法" else "计划") }
        if (value("kind") == "PLAN") {
            value("planType")?.let { appendLine("计划类型：${PlanType.valueOf(it).label}") }
            value("status")?.let { appendLine("状态：${PlanStatus.valueOf(it).label}") }
        }
        value("deadline")?.let { appendLine("截止日期：$it") }
        (o["tags"] as? JsonArray)?.let { tags -> appendLine("标签：" + tags.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }.joinToString("、").ifBlank { "无" }) }
        value("note")?.let { appendLine("打卡内容：${it.ifBlank { "无备注" }}") }
        value("occurredAt")?.toLongOrNull()?.let { appendLine("发生时间：${displayTime(it, "yyyy年MM月dd日 HH:mm")}") }
        value("name")?.let { appendLine("标签名称：$it") }
        value("createdAt")?.toLongOrNull()?.let { appendLine("创建：${displayTime(it, "yyyy年MM月dd日 HH:mm")}") }
        value("updatedAt")?.toLongOrNull()?.let { appendLine("变更：${displayTime(it, "yyyy年MM月dd日 HH:mm")}") }
        value("deletedAt")?.toLongOrNull()?.let { appendLine("删除：${displayTime(it, "yyyy年MM月dd日 HH:mm")}") }
    }.trim()
}.getOrDefault("无法显示这次变更的内容")
