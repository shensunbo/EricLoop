package com.example.ericloop.friends.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import com.example.ericloop.R
import com.example.ericloop.friends.data.*
import com.example.ericloop.ui.EmptyPanel

@Composable
fun FriendsRanking(
    summaries: List<FriendSummary>,
    listState: LazyListState,
    onOpen: (String) -> Unit,
) {
    var sortName by rememberSaveable { mutableStateOf(FriendSort.SCORE.name) }
    var ascending by rememberSaveable { mutableStateOf(false) }
    val ranked =
        remember(summaries, sortName, ascending) {
            summaries.ranked(FriendSort.valueOf(sortName), ascending)
        }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FriendSort.entries.forEach { sort ->
                        FilterChip(
                            selected = sortName == sort.name,
                            onClick = { sortName = sort.name },
                            label = { Text(sort.label) },
                        )
                    }
                }
                FriendActionIcon(
                    R.drawable.ic_arrow_forward,
                    if (ascending) "当前升序，切换为降序" else "当前降序，切换为升序",
                    rotation = if (ascending) -90f else 90f,
                    onClick = { ascending = !ascending },
                )
            }
        }
        if (ranked.isEmpty()) item { EmptyPanel("暂无排行", "新增人物后，这里会显示好感度排行。") }
        itemsIndexed(ranked, key = { _, it -> it.friend.id }) { index, summary ->
            FriendCard(summary, { onOpen(summary.friend.id) }, index + 1)
        }
    }
}
