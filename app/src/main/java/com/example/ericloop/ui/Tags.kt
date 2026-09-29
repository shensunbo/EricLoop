package com.example.ericloop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ericloop.R
import com.example.ericloop.data.Backup

@Composable fun TagsPage(
    backup: Backup, onOpenTag: (String?) -> Unit,
    onAddTag: (String) -> Unit, onRenameTag: (String, String) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    val records = backup.records.filter { it.deletedAt == null }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionHeading("COLLECTIONS", "按标签探索", "收纳想法与计划，也留住每一次完成。") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("标签库", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { name = ""; renameId = null; dialogOpen = true }) { LoopIcon(R.drawable.ic_add); Text("新标签") }
            }
        }
        items(backup.tags.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { tag ->
                    Card(onClick = { onOpenTag(tag.id) }, modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LoopIcon(R.drawable.ic_sell)
                                Spacer(Modifier.weight(1f))
                                if (!tag.preset) IconButton(onClick = { renameId = tag.id; name = tag.name; dialogOpen = true }, modifier = Modifier.size(48.dp)) { LoopIcon(R.drawable.ic_edit, "重命名 ${tag.name}") }
                            }
                            Text(tag.name, style = MaterialTheme.typography.titleMedium)
                            Text("${records.count { tag.id in it.tagIds }} 条记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            OutlinedCard(onClick = { onOpenTag(null) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("全部记录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${records.size} 条记录"); LoopIcon(R.drawable.ic_arrow_forward)
                }
            }
        }
        item {
            OutlinedCard(onClick = { onOpenTag("untagged") }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("无标签", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${records.count { it.tagIds.isEmpty() }} 条记录"); LoopIcon(R.drawable.ic_arrow_forward)
                }
            }
        }
    }
    if (dialogOpen) AlertDialog(
        onDismissRequest = { dialogOpen = false }, title = { Text(if (renameId == null) "创建标签" else "重命名标签") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("标签名称") }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank() && backup.tags.none { it.id != renameId && it.name.equals(name.trim(), true) }, onClick = { if (renameId == null) onAddTag(name) else onRenameTag(renameId!!, name); dialogOpen = false }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { dialogOpen = false }) { Text("取消") } },
    )
}
