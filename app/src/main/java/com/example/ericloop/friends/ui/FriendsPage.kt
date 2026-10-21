package com.example.ericloop.friends.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ericloop.R
import com.example.ericloop.friends.data.FriendSummary
import com.example.ericloop.ui.EmptyPanel

@Composable
fun FriendsPage(
    summaries: List<FriendSummary>,
    listState: LazyListState,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onRanking: () -> Unit,
    onSync: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible =
        summaries
            .filter { it.friend.name.contains(query.trim(), ignoreCase = true) }
            .sortedByDescending { it.friend.createdAt }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "${summaries.size} 人 · 记录每一次相处",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FriendActionIcon(R.drawable.ic_add, "新增人物", filled = true, onClick = onAdd)
                FriendActionIcon(R.drawable.ic_friends_ranking, "好感度排行", onClick = onRanking)
                FriendActionIcon(R.drawable.ic_sync, "备份", onClick = onSync)
            }
        }
        item {
            OutlinedTextField(
                query,
                { query = it },
                label = { Text("搜索昵称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (visible.isEmpty())
            item {
                EmptyPanel(
                    if (summaries.isEmpty()) "还没有人物" else "没有匹配的人物",
                    if (summaries.isEmpty()) "点击加号，开始记录相处点滴。" else "试试其他昵称。",
                )
            }
        items(visible, key = { it.friend.id }) { summary ->
            FriendCard(summary, { onOpen(summary.friend.id) })
        }
    }
}
