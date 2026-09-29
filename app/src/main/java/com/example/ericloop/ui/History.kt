package com.example.ericloop.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ericloop.R
import com.example.ericloop.data.*
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.ZoneId

private val laneColors = listOf(Color(0xFF7084D0), Color(0xFF6BA892), Color(0xFFD09F65), Color(0xFFBB82B0), Color(0xFF619BBB), Color(0xFFBD7979))
private fun historyColor(id: String?) = laneColors[(id ?: "tags").hashCode().ushr(1) % laneColors.size]

@Composable fun HistoryDirectory(backup: Backup, onSelect: (String) -> Unit) {
    val records = remember(backup.records) { backup.records.sortedByDescending { it.updatedAt } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeading("YOUR JOURNEY", "变更记录", "选择一条想法或计划，查看它的历程。") }
        if (records.isEmpty()) item { EmptyPanel("旅程从第一条记录开始", "创建想法或计划后，可以在这里查看它的变更。", R.drawable.ic_timeline) }
        items(records, key = { it.id }) { record ->
            Card(onClick = { onSelect(record.id) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text(record.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(20.dp))
            }
        }
    }
}

@Composable fun HistoryPage(backup: Backup, recordId: String, onOpen: (String) -> Unit) {
    var operation by rememberSaveable(recordId) { mutableStateOf("全部") }
    var start by rememberSaveable(recordId) { mutableStateOf("") }
    var end by rememberSaveable(recordId) { mutableStateOf("") }
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
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        SectionHeading("YOUR JOURNEY", backup.records.find { it.id == recordId }?.title ?: "记录已不存在", "${events.size} 次变更 · 点击节点查看当时的内容")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceMenu("操作", listOf("全部") + recordEvents.map { it.operation }.distinct(), operation) { operation = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(start, { start = it }, Modifier.weight(1f), singleLine = true, label = { Text("起始日期") }, placeholder = { Text("yyyy-MM-dd") }, isError = start.isNotBlank() && startDate == null)
            OutlinedTextField(end, { end = it }, Modifier.weight(1f), singleLine = true, label = { Text("结束日期") }, placeholder = { Text("yyyy-MM-dd") }, isError = end.isNotBlank() && (endDate == null || !datesValid))
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
                        val color = historyColor(event.recordId)
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            Canvas(Modifier.width(laneWidth).fillMaxHeight()) {
                                ranges.forEach { (id, range) ->
                                    if (index in range) {
                                        val track = lanes.getValue(id)
                                        val x = (12 + track * 22).dp.toPx()
                                        drawLine(historyColor(id.takeUnless { it == "tags" }).copy(alpha = 0.42f), Offset(x, if (index == range.first) 30.dp.toPx() else 0f), Offset(x, if (index == range.last) 30.dp.toPx() else size.height), strokeWidth = 2.dp.toPx())
                                    }
                                }
                                drawCircle(color, radius = 6.dp.toPx(), center = Offset((12 + lane * 22).dp.toPx(), 30.dp.toPx()))
                            }
                            Card(onClick = { selectedEvent = event }, modifier = Modifier.weight(1f).padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${displayTime(event.operatedAt)}  ·  #${event.sequence}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(event.operation, style = MaterialTheme.typography.labelMedium, color = color)
                                    Text(event.title, style = MaterialTheme.typography.titleMedium)
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
