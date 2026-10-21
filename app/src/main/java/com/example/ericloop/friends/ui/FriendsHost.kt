package com.example.ericloop.friends.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ericloop.R
import com.example.ericloop.friends.FriendsViewModel
import com.example.ericloop.friends.data.summaries
import com.example.ericloop.ui.EmptyPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsHost(model: FriendsViewModel, onRootChanged: (Boolean) -> Unit) {
    val data by model.data.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf("list") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailReturn by rememberSaveable { mutableStateOf("list") }
    var editorReturn by rememberSaveable { mutableStateOf("list") }
    val snackbar = remember { SnackbarHostState() }
    val holder = rememberSaveableStateHolder()
    val listState = rememberLazyListState()
    val rankingState = rememberLazyListState()
    val backup = data
    val summaries = remember(backup) { backup?.summaries().orEmpty() }
    val selected = summaries.find { it.friend.id == selectedId }
    fun back() {
        val previous = route
        route =
            when (route) {
                "edit" -> editorReturn
                "detail" -> detailReturn
                else -> "list"
            }
        if (previous == "edit" || previous == "detail") holder.removeState(previous)
    }
    fun open(id: String) {
        holder.removeState("detail")
        selectedId = id
        detailReturn = route
        route = "detail"
    }
    LaunchedEffect(route) { onRootChanged(route == "list") }
    LaunchedEffect(model) { model.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(backup?.datasetId, backup?.revision, selectedId, busy) {
        // A save can select its new ID before the lifecycle collector receives the new snapshot.
        // Only redirect for a missing person after the UI has caught up with committed data.
        if (
            !busy &&
                backup != null &&
                backup === model.repository.data.value &&
                selectedId != null &&
                selected == null
        ) {
            selectedId = null
            route = "list"
        }
    }
    BackHandler(route != "list" && route != "edit") { if (!busy) back() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (route == "list") "ppl"
                        else
                            when (route) {
                                "ranking" -> "排行"
                                "edit" -> "资料"
                                "sync" -> "备份"
                                else -> "详情"
                            }
                    )
                },
                navigationIcon = {
                    // The editor owns its cancel action so unsaved changes cannot be bypassed.
                    if (route != "list" && route != "edit")
                        FriendActionIcon(R.drawable.ic_arrow_back, "返回", enabled = !busy) { back() }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                error != null ->
                    Column(Modifier.padding(24.dp)) {
                        EmptyPanel("数据读取失败", error!!)
                        Button(onClick = model::reload) { Text("重试") }
                    }
                backup == null ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                else ->
                    holder.SaveableStateProvider(route) {
                        when (route) {
                            "list" ->
                                FriendsPage(
                                    summaries,
                                    listState,
                                    ::open,
                                    {
                                        holder.removeState("edit")
                                        selectedId = null
                                        editorReturn = "list"
                                        route = "edit"
                                    },
                                    { route = "ranking" },
                                    { route = "sync" },
                                )
                            "ranking" -> FriendsRanking(summaries, rankingState, ::open)
                            "edit" ->
                                FriendEditor(selected?.friend, busy, ::back) {
                                    name,
                                    gender,
                                    intro,
                                    notes,
                                    score ->
                                    model.work {
                                        val id =
                                            model.repository.saveFriend(
                                                selectedId,
                                                name,
                                                gender,
                                                intro,
                                                notes,
                                                score,
                                            )
                                        selectedId = id
                                        if (editorReturn != "detail") detailReturn = "list"
                                        holder.removeState("edit")
                                        route = "detail"
                                    }
                                }
                            "detail" ->
                                selected?.let { summary ->
                                    FriendDetail(
                                        summary,
                                        backup.events.filter { it.friendId == summary.friend.id },
                                        busy,
                                        onEdit = {
                                            holder.removeState("edit")
                                            editorReturn = "detail"
                                            route = "edit"
                                        },
                                        onDelete = {
                                            model.work {
                                                model.repository.deleteFriend(summary.friend.id)
                                                selectedId = null
                                                route = detailReturn
                                                holder.removeState("detail")
                                            }
                                        },
                                        onSaveEvent = { id, score, description, done ->
                                            model.work {
                                                model.repository.saveEvent(
                                                    summary.friend.id,
                                                    id,
                                                    score,
                                                    description,
                                                )
                                                done()
                                            }
                                        },
                                    )
                                }
                            "sync" ->
                                FriendsSyncSection(
                                    model.sync,
                                    backup,
                                    onUpload = { force ->
                                        model.networkWork { model.sync.upload(force) }
                                    },
                                    onDownload = { model.networkWork { model.sync.download() } },
                                    onRestore = {
                                        model.networkWork {
                                            model.sync.restore()
                                            selectedId = null
                                            holder.removeState("detail")
                                            holder.removeState("edit")
                                        }
                                    },
                                )
                        }
                    }
            }
        }
    }
}
