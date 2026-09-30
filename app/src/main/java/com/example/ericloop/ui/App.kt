package com.example.ericloop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ericloop.R
import com.example.ericloop.data.*
import com.example.ericloop.sync.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EricLoopApp(repository: DataRepository, sync: GithubSync) = LoopTheme {
    val backup by repository.data.collectAsStateWithLifecycle()
    val ready by repository.ready.collectAsStateWithLifecycle()
    val initError by repository.initializationError.collectAsStateWithLifecycle()
    val syncState by sync.status.collectAsStateWithLifecycle()
    val settings by sync.settings.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf("home") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var historyRecord by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var editorReturn by rememberSaveable { mutableStateOf("home") }
    var detailReturn by rememberSaveable { mutableStateOf("home") }
    var busy by remember { mutableStateOf(false) }
    var confirmForce by remember { mutableStateOf(false) }
    var restorePreview by remember { mutableStateOf<Backup?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val selected = backup.records.find { it.id == selectedId }
    fun work(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { snackbar.showSnackbar(failure.message ?: "操作失败，请重试") }
            finally { busy = false }
        }
    }
    fun networkWork(action: suspend () -> Unit) {
        if (sync.status.value.busy) return
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { snackbar.showSnackbar(failure.message ?: "同步失败，请重试") }
        }
    }
    fun open(id: String) { detailReturn = route; selectedId = id; route = "detail" }
    fun back() { route = when (route) { "edit" -> editorReturn; "detail" -> detailReturn; "completed", "active", "ideas" -> "home"; "trash" -> "settings"; "tagrecords" -> "tags"; "recordhistory" -> "history"; else -> "home" } }
    BackHandler(route !in listOf("home", "tags", "history", "settings")) { back() }
    LaunchedEffect(ready, backup.datasetId, selectedId) { if (ready && route == "detail" && selected == null) route = "home" }
    val tabs = listOf(Triple("home", "记录", R.drawable.ic_dashboard), Triple("tags", "标签", R.drawable.ic_sell), Triple("history", "变更", R.drawable.ic_timeline), Triple("settings", "设置", R.drawable.ic_settings))
    val isTab = route in tabs.map { it.first }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(title = { Text(when (route) { "edit" -> "编辑记录"; "detail" -> "记录详情"; "completed" -> "已完成"; "active" -> "正在推进"; "ideas" -> "灵感收集"; "trash" -> "回收站"; "tagrecords" -> "标签记录"; "recordhistory" -> "变更记录"; else -> "EricLoop" }, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { if (!isTab) IconButton(onClick = { back() }) { LoopIcon(R.drawable.ic_arrow_back, "返回") } },
                actions = { if (isTab && ready) Text("${backup.records.count { it.deletedAt == null }} 条记录", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 20.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        },
        bottomBar = {
            if (isTab) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { (key, label, icon) ->
                    val accent = accentColors(key)
                    NavigationBarItem(selected = route == key, onClick = { route = key; if (key == "history") historyRecord = null }, icon = { LoopIcon(icon) }, label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = accent.content, selectedTextColor = accent.content, indicatorColor = accent.container))
                }
            }
        },
        floatingActionButton = {
            if (route == "home" && ready && !busy) ExtendedFloatingActionButton(onClick = { selectedId = null; editorReturn = "home"; route = "edit" }, icon = { LoopIcon(R.drawable.ic_add) }, text = { Text("新记录") }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        }, snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy || syncState.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (initError != null) { Box(Modifier.padding(24.dp)) { EmptyPanel("本地数据读取失败", initError!!, R.drawable.ic_history) } }
            else if (!ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else when (route) {
                "home", "completed", "active", "ideas", "trash", "tagrecords" -> RecordsPage(backup, route, ::open, { route = it }, initialTag = if (route == "tagrecords") selectedTag else null)
                "tags" -> TagsPage(backup,
                    onOpenTag = { id -> selectedTag = id; route = "tagrecords" },
                    onAddTag = { name, emoji -> work { repository.addTag(name, emoji) } },
                    onEditTag = { id, name, emoji -> work { repository.editTag(id, name, emoji) } })
                "edit" -> RecordEditor(selected, backup.tags) { record -> work { repository.saveRecord(record); selectedId = record.id; detailReturn = if (editorReturn == "detail") detailReturn else editorReturn; route = "detail" } }
                "detail" -> selected?.let { record -> RecordDetail(record, backup,
                    onEdit = { editorReturn = "detail"; route = "edit" }, onConvert = { type -> work { repository.convert(record.id, type) } },
                    onStatus = { status -> work { repository.changeStatus(record.id, status) } }, onTrash = { work { repository.trash(record.id); route = detailReturn } },
                    onRestore = { work { repository.restoreRecord(record.id) } }, onCheckIn = { checkIn -> work { repository.saveCheckIn(checkIn) } },
                    onDeleteCheckIn = { id -> work { repository.deleteCheckIn(id) } }, onHistory = { id -> historyRecord = id; route = "recordhistory" }) }
                "history" -> HistoryDirectory(backup) { id -> historyRecord = id; route = "recordhistory" }
                "recordhistory" -> historyRecord?.let { HistoryPage(backup, it, ::open) }
                "settings" -> SettingsPage(settings, syncState, backup,
                    onSave = { owner, repo, branch, token -> work { sync.saveSettings(owner, repo, branch, token); snackbar.showSnackbar("连接设置已保存") } },
                    onUpload = { networkWork { sync.upload(); snackbar.showSnackbar(sync.status.value.message ?: "备份完成") } }, onForce = { confirmForce = true },
                    onDownload = { networkWork { restorePreview = sync.download() } }, onTrash = { route = "trash" },
                    onClearToken = { work { sync.clearToken(); snackbar.showSnackbar("GitHub 授权已清除") } },
                )
            }
        }
    }
    if (confirmForce) AlertDialog(onDismissRequest = { confirmForce = false }, title = { Text("用手机数据覆盖云端备份？") }, text = { Text("上传当前全部记录、标签和历史，替换云端数据备份。代码与已有提交历史会保留。") }, confirmButton = { TextButton(onClick = { confirmForce = false; networkWork { sync.upload(force = true); snackbar.showSnackbar(sync.status.value.message ?: "上传完成") } }) { Text("确认强制上传") } }, dismissButton = { TextButton(onClick = { confirmForce = false }) { Text("取消") } })
    restorePreview?.let { remote ->
        AlertDialog(onDismissRequest = { restorePreview = null }, title = { Text("恢复云端备份？") }, text = { Text("备份时间：${displayTime(remote.exportedAt, "yyyy年MM月dd日 HH:mm")}\n${remote.records.size} 条记录 · ${remote.tags.size} 个标签\n${remote.checkIns.size} 次打卡 · ${remote.events.size} 次变更\n\n将替换手机当前数据。替换前会保存本地安全副本。") }, confirmButton = { TextButton(onClick = { restorePreview = null; work { sync.restore(remote); selectedId = null; route = "home"; snackbar.showSnackbar("已恢复云端备份") } }) { Text("确认恢复") } }, dismissButton = { TextButton(onClick = { restorePreview = null }) { Text("取消") } })
    }
}

@Composable private fun SettingsPage(
    settings: SyncSettings, status: SyncStatus, backup: Backup,
    onSave: (String, String, String, String) -> Unit, onUpload: () -> Unit, onForce: () -> Unit,
    onDownload: () -> Unit, onTrash: () -> Unit, onClearToken: () -> Unit,
) {
    var owner by rememberSaveable(settings.owner) { mutableStateOf(settings.owner) }
    var repo by rememberSaveable(settings.repo) { mutableStateOf(settings.repo) }
    var branch by rememberSaveable(settings.branch) { mutableStateOf(settings.branch) }
    var token by remember { mutableStateOf("") }
    val changed = owner != settings.owner || repo != settings.repo || branch != settings.branch || token.isNotEmpty()
    val canSync = !status.busy && settings.hasToken && !changed
    val pending = if (status.uploadedDatasetId == backup.datasetId) (backup.revision - status.uploadedRevision).coerceAtLeast(0) else backup.revision
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionHeading("CONNECTED, ON YOUR TERMS", "让记录有个备份", "本地即时保存 · 云端手动上传") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$pending 次变更待上传", style = MaterialTheme.typography.titleLarge)
                    Text(status.lastSuccess?.let { "上次成功：${displayTime(it, "yyyy-MM-dd HH:mm")}" } ?: "尚未同步", style = MaterialTheme.typography.bodyMedium)
                    status.message?.let { Text(it, color = if (status.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer) }
                }
            }
        }
        item {
            Text("GitHub 连接", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(owner, { owner = it }, Modifier.fillMaxWidth(), label = { Text("仓库所有者") }, singleLine = true)
                OutlinedTextField(repo, { repo = it }, Modifier.fillMaxWidth(), label = { Text("仓库名称") }, singleLine = true)
                OutlinedTextField(branch, { branch = it }, Modifier.fillMaxWidth(), label = { Text("分支") }, singleLine = true)
                OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("GitHub Token") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), supportingText = { Text(if (settings.hasToken) "已保存 Token；留空保留现有授权" else "使用此仓库的细粒度 Token，Contents 读写权限") })
                Button(enabled = !status.busy && owner.isNotBlank() && repo.isNotBlank() && branch.isNotBlank(), onClick = { onSave(owner.trim(), repo.trim(), branch.trim(), token.trim()); token = "" }, modifier = Modifier.fillMaxWidth()) { Text("保存连接设置") }
                if (settings.hasToken) TextButton(enabled = !status.busy, onClick = { token = ""; onClearToken() }) { Text("清除 GitHub 授权") }
            }
        }
        item {
            Text("云端备份", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("备份包含记录、标签、打卡及全部变更历史。当前仓库公开，备份也以公开明文保存。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onUpload, enabled = canSync, modifier = Modifier.fillMaxWidth().height(52.dp)) { LoopIcon(R.drawable.ic_sync); Spacer(Modifier.width(8.dp)); Text("手动同步") }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDownload, enabled = canSync, modifier = Modifier.weight(1f)) { Text("从云端恢复") }
                OutlinedButton(onClick = onForce, enabled = canSync, modifier = Modifier.weight(1f)) { Text("强制上传") }
            }
            if (changed) Text("请先保存连接设置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        item {
            OutlinedCard(onClick = onTrash, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    LoopIcon(R.drawable.ic_delete); Spacer(Modifier.width(12.dp)); Text("回收站", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("${backup.records.count { it.deletedAt != null }} 条", color = MaterialTheme.colorScheme.onSurfaceVariant); LoopIcon(R.drawable.ic_arrow_forward)
                }
            }
        }
        item { Text("EricLoop 1.0\n想法有归处，行动有回声。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(24.dp)) }
    }
}
