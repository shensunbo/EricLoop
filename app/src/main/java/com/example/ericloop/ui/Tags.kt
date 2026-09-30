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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.example.ericloop.R
import com.example.ericloop.data.Backup
import com.example.ericloop.data.tagEmoji

@Composable fun TagsPage(
    backup: Backup, onOpenTag: (String?) -> Unit,
    onAddTag: (String, String?) -> Unit, onEditTag: (String, String, String?) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    var renameId by remember { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf<String?>(null) }
    var emojiOpen by rememberSaveable { mutableStateOf(false) }
    val records = backup.records.filter { it.deletedAt == null }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionHeading("按标签探索", "收纳想法与计划，也留住每一次完成。") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("标签库", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { name = ""; emoji = null; renameId = null; dialogOpen = true }) { LoopIcon(R.drawable.ic_add); Text("新标签") }
            }
        }
        items(backup.tags.chunked(3)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tag ->
                    Card(onClick = { onOpenTag(tag.id) }, modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(8.dp)) {
                            Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(tagEmoji(tag), fontSize = 22.sp)
                                Spacer(Modifier.weight(1f))
                                if (!tag.preset) IconButton(onClick = { renameId = tag.id; name = tag.name; emoji = tag.emoji; dialogOpen = true }, modifier = Modifier.size(48.dp)) { Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_edit), "编辑 ${tag.name}", Modifier.size(18.dp)) }
                            }
                            Text(tag.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${records.count { tag.id in it.tagIds }} 条", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
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
    if (dialogOpen && !emojiOpen) AlertDialog(
        onDismissRequest = { dialogOpen = false }, title = { Text(if (renameId == null) "创建标签" else "编辑标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("标签名称") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(emoji ?: "🏷️", fontSize = 26.sp)
                    OutlinedButton(onClick = { emojiOpen = true }) { Text("选择 Emoji") }
                    if (emoji != null) TextButton(onClick = { emoji = null }) { Text("清除") }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && backup.tags.none { it.id != renameId && it.name.equals(name.trim(), true) }, onClick = { if (renameId == null) onAddTag(name, emoji) else onEditTag(renameId!!, name, emoji); dialogOpen = false }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { dialogOpen = false }) { Text("取消") } },
    )
    if (emojiOpen) EmojiPicker(emoji, onDismiss = { emojiOpen = false }, onSelect = { emoji = it; emojiOpen = false })
}
