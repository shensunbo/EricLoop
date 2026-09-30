package com.example.ericloop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ericloop.R
import com.example.ericloop.data.*

@Composable fun ChoiceMenu(label: String, options: List<String>, value: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.height(48.dp).clickable { expanded = true }, contentAlignment = Alignment.Center) {
            Box(Modifier.height(36.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text("$label · $value", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun RecordCard(record: LoopRecord, tags: List<LoopTag>, onClick: () -> Unit) {
    val completed = record.kind == RecordKind.PLAN && record.status == PlanStatus.COMPLETED
    val accent = accentColors(if (completed) "completed" else if (record.kind == RecordKind.IDEA) "ideas" else "plans")
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().then(if (completed) Modifier.border(BorderStroke(1.dp, accent.content.copy(alpha = 0.18f)), MaterialTheme.shapes.medium) else Modifier), colors = CardDefaults.cardColors(
        containerColor = accent.container, contentColor = accent.content)) {
        Column(Modifier.fillMaxWidth().metallicGold(completed).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (record.kind == RecordKind.IDEA) "想法" else record.planType.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                if (record.kind == RecordKind.PLAN) Text(record.status.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordIcon(record)
                Text(record.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (completed) CompletionMark()
            }
            if (record.body.isNotBlank()) HandwritingText(record.body,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp, lineHeight = 22.sp,
                    color = accent.content),
                maxLines = 2, overflow = TextOverflow.Ellipsis, useNotePreferences = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.filter { it.id in record.tagIds }.forEach { tag ->
                    Text("${tagEmoji(tag)} #${tag.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
    initialTag: String? = null,
) {
    var query by rememberSaveable(mode, initialTag) { mutableStateOf("") }
    var category by rememberSaveable(mode, initialTag) { mutableStateOf("全部") }
    var status by rememberSaveable(mode, initialTag) { mutableStateOf("全部") }
    var selectedTags by rememberSaveable(mode, initialTag) { mutableStateOf(initialTag?.let { listOf(it) } ?: emptyList<String>()) }
    val active = backup.records.filter { it.deletedAt == null }
    val completedCount = active.count { it.kind == RecordKind.PLAN && it.status == PlanStatus.COMPLETED }
    val candidates = backup.records.filter {
        when (mode) {
            "trash" -> it.deletedAt != null
            "completed" -> it.deletedAt == null && it.kind == RecordKind.PLAN && it.status == PlanStatus.COMPLETED
            "active" -> it.deletedAt == null && it.kind == RecordKind.PLAN && it.status == PlanStatus.ACTIVE
            "ideas" -> it.deletedAt == null && it.kind == RecordKind.IDEA
            "tagrecords" -> it.deletedAt == null
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
    }.sortedWith(compareBy<LoopRecord> { mode == "tagrecords" && it.kind == RecordKind.PLAN && it.status == PlanStatus.COMPLETED }.thenByDescending { it.updatedAt })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            when (mode) {
                "tagrecords" -> SectionHeading(initialTag?.let { id -> backup.tags.find { it.id == id }?.let { "${tagEmoji(it)} ${it.name}" } ?: "无标签" } ?: "全部记录", "想法与计划 · 已完成排在最后")
                "completed" -> SectionHeading("🎉 已完成", "每一次完成，都值得留下。")
                "active" -> SectionHeading("正在推进", "专注当下正在进行的计划。")
                "ideas" -> SectionHeading("灵感收集", "把值得继续想的念头留在这里。")
                "trash" -> SectionHeading("回收站", "恢复后会回到原来的收纳位置。")
                else -> SectionHeading("想法，慢慢成真。", "记录灵感 · 推进计划 · 留下过程", prominent = true)
            }
        }
        if (mode == "home") item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("正在推进", active.count { it.kind == RecordKind.PLAN && it.status == PlanStatus.ACTIVE }.toString(), Modifier.weight(1f), accentColors("plans")) { onFolder("active") }
                StatCard("灵感收集", active.count { it.kind == RecordKind.IDEA }.toString(), Modifier.weight(1f), accentColors("ideas")) { onFolder("ideas") }
            }
            Spacer(Modifier.height(8.dp))
            val celebration = accentColors("completed")
            OutlinedCard(onClick = { onFolder("completed") }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = celebration.container, contentColor = celebration.content)) {
                Row(Modifier.fillMaxWidth().metallicGold().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎉", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.width(8.dp)); Text("已完成", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f)); Text("$completedCount 个计划", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(8.dp)); LoopIcon(R.drawable.ic_arrow_forward)
                }
            }
        }
        item {
            CompactTextField(query, { query = it }, "搜索标题或正文", Modifier.fillMaxWidth(), leadingIcon = R.drawable.ic_search)
            if (mode != "active" && mode != "ideas") Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceMenu("类型", if (mode == "completed") listOf("全部", "一次性计划", "长期计划") else listOf("全部", "想法", "一次性计划", "长期计划"), category, { category = it })
                if (mode != "completed") ChoiceMenu("状态", listOf("全部") + PlanStatus.entries.map { it.label }, status, { status = it })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selectedTags.isEmpty(), { selectedTags = emptyList() }, label = { Text("全部标签") })
                FilterChip("untagged" in selectedTags, { selectedTags = if ("untagged" in selectedTags) selectedTags - "untagged" else selectedTags + "untagged" }, label = { Text("无标签") })
                backup.tags.forEach { tag -> FilterChip(tag.id in selectedTags, { selectedTags = if (tag.id in selectedTags) selectedTags - tag.id else selectedTags + tag.id }, label = { Text("${tagEmoji(tag)} ${tag.name}") }) }
            }
            Text("${filtered.size} 条记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (filtered.isEmpty()) item { EmptyPanel(if (candidates.isEmpty()) "这里还是一片留白" else "没有匹配的记录", if (mode == "home" && candidates.isEmpty()) "点击右下角，留下你的第一个想法或计划。" else "试试其他筛选条件，或创建新的记录。", R.drawable.ic_note_add) }
        items(filtered, key = { it.id }) { record -> RecordCard(record, backup.tags) { onOpen(record.id) } }
        item { Spacer(Modifier.height(84.dp)) }
    }
}

@Composable private fun StatCard(label: String, value: String, modifier: Modifier, accent: UiAccent, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = accent.container, contentColor = accent.content)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = accent.content)
            Text(label, style = MaterialTheme.typography.labelMedium, color = accent.content)
        }
    }
}
