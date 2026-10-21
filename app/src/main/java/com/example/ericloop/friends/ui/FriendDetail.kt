package com.example.ericloop.friends.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.example.ericloop.R
import com.example.ericloop.friends.data.*
import com.example.ericloop.ui.displayTime

@Composable
fun FriendDetail(
    summary: FriendSummary,
    events: List<FriendEvent>,
    busy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSaveEvent: (String?, String, String, () -> Unit) -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var eventDialog by rememberSaveable { mutableStateOf(false) }
    var editingEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var delta by rememberSaveable { mutableStateOf("1") }
    val editingEvent = events.find { it.id == editingEventId }
    LaunchedEffect(events, editingEventId) {
        if (editingEventId != null && editingEvent == null) {
            eventDialog = false
            editingEventId = null
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FriendAvatar(summary.friend)
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            summary.friend.name,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        GenderIcon(summary.friend.gender)
                    }
                }
                FriendActionIcon(R.drawable.ic_edit, "编辑资料", enabled = !busy, onClick = onEdit)
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = friendCardColors(summary.friend)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("当前好感度", style = MaterialTheme.typography.labelLarge)
                    Text(
                        summary.score.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "初始值 ${summary.friend.initialScore} · ${events.size} 次互动",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FriendActionIcon(
                            R.drawable.ic_add,
                            "好感度加一",
                            tint = eventScoreColor(java.math.BigInteger.ONE),
                            enabled = !busy,
                            onClick = {
                                editingEventId = null
                                delta = "1"
                                eventDialog = true
                            },
                        )
                        FriendActionIcon(
                            R.drawable.ic_remove,
                            "好感度减一",
                            tint = eventScoreColor(java.math.BigInteger.ONE.negate()),
                            enabled = !busy,
                            onClick = {
                                editingEventId = null
                                delta = "-1"
                                eventDialog = true
                            },
                        )
                    }
                }
            }
        }
        item {
            Text(
                "简介",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                summary.friend.intro.ifBlank { "暂无简介" },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "备注",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                summary.friend.notes.ifBlank { "暂无备注" },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "创建于 ${displayTime(summary.friend.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Text("互动记录", style = MaterialTheme.typography.titleMedium) }
        if (events.isEmpty())
            item {
                Text(
                    "还没有互动事件，点击加减图标记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        items(
            events.sortedWith(compareByDescending<FriendEvent> { it.occurredAt }.thenBy { it.id }),
            key = { it.id },
        ) { event ->
            val score = parseScore(event.delta)
            val tone = eventScoreColor(score)
            OutlinedCard(
                onClick = {
                    if (!busy) {
                        editingEventId = event.id
                        eventDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor =
                            tone
                                .copy(alpha = 0.12f)
                                .compositeOver(MaterialTheme.colorScheme.surface)
                    ),
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            displayTime(event.occurredAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        EventScoreBadge(score)
                        FriendActionIcon(R.drawable.ic_edit, "编辑事件", enabled = !busy) {
                            editingEventId = event.id
                            eventDialog = true
                        }
                    }
                    Text(
                        event.description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
        item {
            FriendActionIcon(
                R.drawable.ic_delete,
                "删除人物",
                enabled = !busy,
                tint = MaterialTheme.colorScheme.error,
            ) {
                confirmDelete = true
            }
        }
    }
    if (eventDialog)
        FriendEventEditor(
            editingEvent,
            delta,
            busy,
            onDismiss = { eventDialog = false },
            onSave = { score, description ->
                onSaveEvent(editingEventId, score, description) { eventDialog = false }
            },
        )
    if (confirmDelete)
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDelete = false },
            title = { Text("删除这位人物？") },
            text = { Text("将永久删除人物资料和全部 ${events.size} 条互动事件，无法撤销。") },
            confirmButton = { TextButton(enabled = !busy, onClick = onDelete) { Text("确认删除") } },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
}
