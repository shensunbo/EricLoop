package com.example.ericloop.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ericloop.R
import com.example.ericloop.data.*

@Composable fun ChoiceMenu(label: String, options: List<String>, value: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label · $value") }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun RecordCard(record: LoopRecord, tags: List<LoopTag>, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoopIcon(if (record.kind == RecordKind.IDEA) R.drawable.ic_lightbulb else R.drawable.ic_task_alt)
                Spacer(Modifier.width(8.dp))
                Text(if (record.kind == RecordKind.IDEA) "想法" else record.planType.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                if (record.kind == RecordKind.PLAN) Text(record.status.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
            Text(record.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (record.body.isNotBlank()) Text(record.body, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.filter { it.id in record.tagIds }.forEach { tag ->
                    Text("# ${tag.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(displayTime(record.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                record.deadline?.let { Text("截止 $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable fun RecordsPage(
    backup: Backup, mode: String, onOpen: (String) -> Unit, onFolder: (String) -> Unit,
    onNew: () -> Unit, tagActions: ((String) -> Unit)? = null, renameTag: ((String, String) -> Unit)? = null,
) {
    var query by rememberSaveable(mode) { mutableStateOf("") }
    var category by rememberSaveable(mode) { mutableStateOf("全部") }
    var status by rememberSaveable(mode) { mutableStateOf("全部") }
    var selectedTags by rememberSaveable(mode) { mutableStateOf(listOf<String>()) }
    var includeCompleted by rememberSaveable { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf(false) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var tagName by rememberSaveable { mutableStateOf("") }
    val active = backup.records.filter { it.deletedAt == null }
    val completedCount = active.count { it.kind == RecordKind.PLAN && it.status == PlanStatus.COMPLETED }
    val candidates = backup.records.filter {
        when (mode) {
            "trash" -> it.deletedAt != null
            "completed" -> it.deletedAt == null && it.kind == RecordKind.PLAN && it.status == PlanStatus.COMPLETED
            "tags" -> it.deletedAt == null && it.kind == RecordKind.PLAN && (includeCompleted || it.status != PlanStatus.COMPLETED)
            else -> it.deletedAt == null && !(it.kind == RecordKind.PLAN && it.status == PlanStatus.COMPLETED)
        }
    }
    val filtered = candidates.filter { record ->
        (query.isBlank() || record.title.contains(query, true) || record.body.contains(query, true)) &&
        (category == "全部" || when (category) {
            "想法" -> record.kind == RecordKind.IDEA
            "一次性计划" -> record.kind == RecordKind.PLAN && record.planType == PlanType.ONCE
            else -> record.kind == RecordKind.PLAN && record.planType == PlanType.LONG_TERM
        }) && (status == "全部" || (record.kind == RecordKind.PLAN && record.status.label == status)) &&
        (selectedTags.isEmpty() || selectedTags.any { if (it == "untagged") record.tagIds.isEmpty() else it in record.tagIds })
    }.sortedByDescending { it.updatedAt }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            when (mode) {
                "tags" -> SectionHeading("COLLECTIONS", "按标签探索", "为计划找到属于它的位置。")
                "completed" -> SectionHeading("COMPLETED", "已完成", "每一次完成，都值得留下。")
                "trash" -> SectionHeading("RECYCLE BIN", "回收站", "恢复后会回到原来的收纳位置。")
                else -> SectionHeading("ERICLOOP / YOUR SPACE", "想法，慢慢成真。", "记录灵感 · 推进计划 · 留下过程")
            }
        }
        if (mode == "home") item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("正在推进", active.count { it.kind == RecordKind.PLAN && it.status == PlanStatus.ACTIVE }.toString(), Modifier.weight(1f))
                StatCard("灵感收集", active.count { it.kind == RecordKind.IDEA }.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedCard(onClick = { onFolder("completed") }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    LoopIcon(R.drawable.ic_folder); Spacer(Modifier.width(12.dp)); Text("已完成", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f)); Text("$completedCount 个计划", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(10.dp)); LoopIcon(R.drawable.ic_arrow_forward)
                }
            }
        }
        if (mode == "tags") {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("标签库", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { tagName = ""; renameId = null; tagDialog = true }) { LoopIcon(R.drawable.ic_add); Text("新标签") }
                }
            }
            items(backup.tags.chunked(2)) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { tag ->
                        val count = candidates.count { tag.id in it.tagIds }
                        Card(onClick = { selectedTags = if (tag.id in selectedTags) selectedTags - tag.id else selectedTags + tag.id }, modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = if (tag.id in selectedTags) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LoopIcon(R.drawable.ic_sell)
                                    Spacer(Modifier.weight(1f))
                                    if (!tag.preset) IconButton(onClick = { renameId = tag.id; tagName = tag.name; tagDialog = true }, modifier = Modifier.size(48.dp)) { LoopIcon(R.drawable.ic_edit, "重命名 ${tag.name}") }
                                }
                                Text(tag.name, style = MaterialTheme.typography.titleMedium)
                                Text("$count 个计划", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("包含已完成", Modifier.weight(1f)); Switch(includeCompleted, { includeCompleted = it })
                }
            }
        }
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("搜索标题或正文") }, leadingIcon = { LoopIcon(R.drawable.ic_search) }, shape = MaterialTheme.shapes.large)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceMenu("类型", if (mode == "tags" || mode == "completed") listOf("全部", "一次性计划", "长期计划") else listOf("全部", "想法", "一次性计划", "长期计划"), category, { category = it })
                if (mode != "completed") ChoiceMenu("状态", listOf("全部") + PlanStatus.entries.map { it.label }, status, { status = it })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selectedTags.isEmpty(), { selectedTags = emptyList() }, label = { Text("全部标签") })
                FilterChip("untagged" in selectedTags, { selectedTags = if ("untagged" in selectedTags) selectedTags - "untagged" else selectedTags + "untagged" }, label = { Text("无标签") })
                backup.tags.forEach { tag -> FilterChip(tag.id in selectedTags, { selectedTags = if (tag.id in selectedTags) selectedTags - tag.id else selectedTags + tag.id }, label = { Text(tag.name) }) }
            }
            Text("${filtered.size} 条记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (filtered.isEmpty()) item { EmptyPanel(if (candidates.isEmpty()) "这里还是一片留白" else "没有匹配的记录", if (mode == "home" && candidates.isEmpty()) "点击右下角，留下你的第一个想法或计划。" else "试试其他筛选条件，或创建新的记录。", R.drawable.ic_note_add) }
        items(filtered, key = { it.id }) { record -> RecordCard(record, backup.tags) { onOpen(record.id) } }
        item { Spacer(Modifier.height(84.dp)) }
    }
    if (tagDialog) AlertDialog(
        onDismissRequest = { tagDialog = false }, title = { Text(if (renameId == null) "创建标签" else "重命名标签") },
        text = { OutlinedTextField(tagName, { tagName = it }, label = { Text("标签名称") }, singleLine = true) },
        confirmButton = { TextButton(enabled = tagName.isNotBlank() && backup.tags.none { it.id != renameId && it.name.equals(tagName.trim(), true) }, onClick = { if (renameId == null) tagActions?.invoke(tagName) else renameTag?.invoke(renameId!!, tagName); tagDialog = false }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { tagDialog = false }) { Text("取消") } },
    )
}

@Composable private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
