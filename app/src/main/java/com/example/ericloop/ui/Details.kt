package com.example.ericloop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.ericloop.R
import com.example.ericloop.data.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable fun RecordEditor(record: LoopRecord?, tags: List<LoopTag>, onSave: (LoopRecord) -> Unit) {
    val initial = remember(record?.id) { record ?: LoopRecord(title = "") }
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var body by rememberSaveable(initial.id) { mutableStateOf(initial.body) }
    var kind by rememberSaveable(initial.id) { mutableStateOf(initial.kind.name) }
    var planType by rememberSaveable(initial.id) { mutableStateOf(initial.planType.name) }
    var deadline by rememberSaveable(initial.id) { mutableStateOf(initial.deadline ?: "") }
    var selectedTags by rememberSaveable(initial.id) { mutableStateOf(initial.tagIds) }
    var emoji by rememberSaveable(initial.id) { mutableStateOf(initial.emoji) }
    var emojiOpen by rememberSaveable(initial.id) { mutableStateOf(false) }
    var dateOpen by remember { mutableStateOf(false) }
    val dateValid = kind != RecordKind.PLAN.name || deadline.isBlank() || runCatching { LocalDate.parse(deadline) }.isSuccess
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { SectionHeading(if (record == null) "NEW CHAPTER" else "KEEP EVOLVING", if (record == null) "留下一点新想法" else "完善这条记录") }
        if (record == null) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordKind.entries.forEach { value -> FilterChip(kind == value.name, { kind = value.name }, label = { Text(value.label) }) }
            }
        }
        item { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("标题") }, placeholder = { Text("给它一个名字") }, singleLine = true, shape = MaterialTheme.shapes.medium) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RecordIcon(initial.copy(kind = RecordKind.valueOf(kind), emoji = emoji))
                OutlinedButton(onClick = { emojiOpen = true }) { Text("选择图标") }
                if (emoji != null) TextButton(onClick = { emoji = null }) { Text("恢复默认") }
            }
            Text("可选，未设置时使用默认图标", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().heightIn(min = 200.dp), label = { Text("正文") }, placeholder = { Text("想做什么？为什么想做？慢慢写下来。") }, shape = MaterialTheme.shapes.medium) }
        if (kind == RecordKind.PLAN.name) {
            item {
                Text("计划类型", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PlanType.entries.forEach { type -> FilterChip(planType == type.name, { planType = type.name }, label = { Text(type.label) }) } }
            }
            item {
                OutlinedTextField(deadline, { deadline = it }, Modifier.fillMaxWidth(), label = { Text("截止日期 · 可选") }, placeholder = { Text("yyyy-MM-dd") }, singleLine = true, isError = !dateValid, supportingText = { Text(if (dateValid) "留空表示没有截止日期" else "请输入有效日期，如 2026-10-01") }, trailingIcon = { IconButton(onClick = { dateOpen = true }) { LoopIcon(R.drawable.ic_event, "选择截止日期") } })
            }
        }
        item {
            Text("标签", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag -> FilterChip(tag.id in selectedTags, { selectedTags = if (tag.id in selectedTags) selectedTags - tag.id else selectedTags + tag.id }, label = { Text(tag.name) }) }
            }
            Text("在标签页创建更多分类", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Button(onClick = { onSave(initial.copy(title = title.trim(), body = body, emoji = emoji, kind = RecordKind.valueOf(kind), planType = PlanType.valueOf(planType), tagIds = selectedTags, deadline = if (kind == RecordKind.PLAN.name) deadline.trim().ifBlank { null } else null)) }, enabled = title.isNotBlank() && dateValid, modifier = Modifier.fillMaxWidth().height(54.dp)) { LoopIcon(R.drawable.ic_check); Spacer(Modifier.width(8.dp)); Text("保存记录") }
        }
    }
    if (emojiOpen) EmojiPicker(emoji, onDismiss = { emojiOpen = false }, onSelect = { emoji = it; emojiOpen = false })
    if (dateOpen) {
        val state = rememberDatePickerState()
        DatePickerDialog(onDismissRequest = { dateOpen = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { deadline = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() }; dateOpen = false }) { Text("确定") } }, dismissButton = { TextButton(onClick = { dateOpen = false }) { Text("取消") } }) { DatePicker(state) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun RecordDetail(
    record: LoopRecord, backup: Backup, onEdit: () -> Unit, onConvert: (PlanType) -> Unit,
    onStatus: (PlanStatus) -> Unit, onTrash: () -> Unit, onRestore: () -> Unit,
    onCheckIn: (CheckIn) -> Unit, onDeleteCheckIn: (String) -> Unit, onHistory: (String) -> Unit,
) {
    var checkInEditor by rememberSaveable(record.id) { mutableStateOf(false) }
    var editingCheckInId by rememberSaveable(record.id) { mutableStateOf<String?>(null) }
    val editingCheckIn = backup.checkIns.find { it.id == editingCheckInId }
    var deleteConfirm by remember { mutableStateOf(false) }
    var deletingCheckIn by remember { mutableStateOf<String?>(null) }
    val checkIns = backup.checkIns.filter { it.recordId == record.id && it.deletedAt == null }.sortedByDescending { it.occurredAt }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text(if (record.deletedAt != null) "回收站" else if (record.kind == RecordKind.IDEA) "IDEA / 想法" else "PLAN / ${record.planType.label}", style = MaterialTheme.typography.labelMedium, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RecordIcon(record)
                Text(record.title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                if (record.kind == RecordKind.PLAN && record.status == PlanStatus.COMPLETED) CompletionMark()
            }
            Spacer(Modifier.height(12.dp)); Text("创建于 ${displayTime(record.createdAt, "yyyy年MM月dd日 HH:mm")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("最近变更 ${displayTime(record.updatedAt)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { backup.tags.filter { it.id in record.tagIds }.forEach { AssistChip(onClick = {}, label = { Text(it.name) }) } }
        }
        if (record.kind == RecordKind.PLAN) item {
            val accent = accentColors(if (record.status == PlanStatus.COMPLETED) "completed" else "plans")
            Card(colors = CardDefaults.cardColors(containerColor = accent.container, contentColor = accent.content)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (record.status == PlanStatus.COMPLETED) Text("🎉") else LoopIcon(R.drawable.ic_task_alt)
                        Spacer(Modifier.width(10.dp)); Text(record.status.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (record.deletedAt == null) ChoiceMenu("调整", PlanStatus.entries.map { it.label }, record.status.label) { label -> onStatus(PlanStatus.entries.first { it.label == label }) }
                    }
                    record.deadline?.let { Text("截止日期  $it", style = MaterialTheme.typography.bodyMedium) }
                    if (record.status == PlanStatus.COMPLETED) Text("已收纳到「已完成」", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(20.dp))
            androidx.compose.foundation.text.selection.SelectionContainer { Text(record.body.ifBlank { "还没有正文。" }, style = MaterialTheme.typography.bodyLarge, color = if (record.body.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface) }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (record.deletedAt != null) Button(onClick = onRestore) { LoopIcon(R.drawable.ic_restore); Text("恢复记录") }
                else {
                    OutlinedButton(onClick = onEdit) { LoopIcon(R.drawable.ic_edit); Spacer(Modifier.width(6.dp)); Text("编辑") }
                    if (record.kind == RecordKind.IDEA) PlanType.entries.forEach { type -> OutlinedButton(onClick = { onConvert(type) }) { Text("转为${type.label}计划") } }
                    OutlinedButton(onClick = { deleteConfirm = true }) { LoopIcon(R.drawable.ic_delete); Text("删除") }
                }
            }
            TextButton(onClick = { onHistory(record.id) }) { LoopIcon(R.drawable.ic_history); Spacer(Modifier.width(6.dp)); Text("查看完整变更历史") }
        }
        if (record.kind == RecordKind.PLAN && record.planType == PlanType.LONG_TERM) {
            item {
                HorizontalDivider(); Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("进展笔记", style = MaterialTheme.typography.titleLarge); Text("${checkIns.size} 次打卡，每一步都有意义", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (record.deletedAt == null) FilledTonalButton(onClick = { editingCheckInId = null; checkInEditor = true }) { LoopIcon(R.drawable.ic_add); Text("打卡") }
                }
            }
            if (checkIns.isEmpty()) item { EmptyPanel("记录今天的一小步", "打卡无需固定频率，也可以补记过去的进展。", R.drawable.ic_note_add) }
            items(checkIns, key = { it.id }) { checkIn ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(displayTime(checkIn.occurredAt, "yyyy年MM月dd日 HH:mm"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(checkIn.note.ifBlank { "完成了一次打卡" }, style = MaterialTheme.typography.bodyLarge)
                        if (record.deletedAt == null) Row {
                            TextButton(onClick = { editingCheckInId = checkIn.id; checkInEditor = true }) { Text("编辑") }
                            TextButton(onClick = { deletingCheckIn = checkIn.id }) { Text("删除") }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (checkInEditor) CheckInDialog(record.id, editingCheckIn, { checkInEditor = false }) { onCheckIn(it); checkInEditor = false }
    if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false }, title = { Text("移入回收站？") }, text = { Text("记录及历史会保留，可以随时恢复。") }, confirmButton = { TextButton(onClick = { onTrash(); deleteConfirm = false }) { Text("移入回收站") } }, dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("取消") } })
    if (deletingCheckIn != null) AlertDialog(onDismissRequest = { deletingCheckIn = null }, title = { Text("删除这次打卡？") }, text = { Text("原内容仍可在变更历史中查看。") }, confirmButton = { TextButton(onClick = { onDeleteCheckIn(deletingCheckIn!!); deletingCheckIn = null }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deletingCheckIn = null }) { Text("取消") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CheckInDialog(recordId: String, editing: CheckIn?, onDismiss: () -> Unit, onSave: (CheckIn) -> Unit) {
    val initial = remember { editing ?: CheckIn(recordId = recordId) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var time by rememberSaveable { mutableStateOf(displayTime(initial.occurredAt, "yyyy-MM-dd HH:mm")) }
    var dateOpen by remember { mutableStateOf(false) }
    var timeOpen by remember { mutableStateOf(false) }
    var chosenDate by rememberSaveable { mutableStateOf(time.take(10)) }
    val parsed = runCatching { LocalDateTime.parse(time, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
    val valid = parsed != null && parsed <= System.currentTimeMillis()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (editing == null) "记录进展" else "编辑打卡") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(note, { note = it }, label = { Text("这次做了什么？ · 可选") }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp))
            OutlinedTextField(time, {}, readOnly = true, label = { Text("发生时间") }, singleLine = true, isError = !valid,
                trailingIcon = { IconButton(onClick = { dateOpen = true }) { LoopIcon(R.drawable.ic_event, "选择打卡日期") } },
                supportingText = { Text(if (valid) "点击日历补记过去的进展" else "发生时间不能晚于现在，请重新选择") })
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(initial.copy(note = note,
        occurredAt = if (time == displayTime(initial.occurredAt, "yyyy-MM-dd HH:mm")) initial.occurredAt else parsed!!)) }) { Text("保存打卡") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
    if (dateOpen) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(chosenDate).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate() <= LocalDate.now()
            })
        DatePickerDialog(onDismissRequest = { dateOpen = false },
            confirmButton = { TextButton(enabled = state.selectedDateMillis != null, onClick = { state.selectedDateMillis?.let { chosenDate = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() }; dateOpen = false; timeOpen = true }) { Text("选择时间") } },
            dismissButton = { TextButton(onClick = { dateOpen = false }) { Text("取消") } }) { DatePicker(state) }
    }
    if (timeOpen) {
        val local = LocalDateTime.parse(time, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val state = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute, is24Hour = true)
        AlertDialog(onDismissRequest = { timeOpen = false }, title = { Text("发生时间") },
            text = { TimeInput(state) }, confirmButton = { TextButton(onClick = { time = LocalDate.parse(chosenDate).atTime(state.hour, state.minute).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); timeOpen = false }) { Text("确定时间") } },
            dismissButton = { TextButton(onClick = { timeOpen = false }) { Text("取消") } })
    }
}
