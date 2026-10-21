package com.example.ericloop.friends.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ericloop.R
import com.example.ericloop.friends.data.FriendsBackup
import com.example.ericloop.friends.sync.FriendsGithubSync
import com.example.ericloop.ui.displayTime

@Composable
fun FriendsSyncSection(
    sync: FriendsGithubSync,
    backup: FriendsBackup,
    onUpload: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onRestore: () -> Unit,
) {
    val status by sync.status.collectAsStateWithLifecycle()
    val settings by sync.settings.collectAsStateWithLifecycle()
    val preview by sync.preview.collectAsStateWithLifecycle()
    var confirmForce by rememberSaveable { mutableStateOf(false) }
    val pending =
        backup.datasetId != status.uploadedDatasetId || backup.revision != status.uploadedRevision
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${settings.owner}/${settings.repo}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "分支 ${settings.branch}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    FriendsGithubSync.BACKUP_PATH,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        "${backup.friends.size} 人 · ${backup.events.size} 条事件",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Surface(
                        color =
                            if (pending) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            if (pending) "待同步" else "已备份",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        Text(
            if (settings.hasToken) "使用设置中的 GitHub 连接" else "请先在设置中配置 GitHub 连接与令牌",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FriendActionIcon(
                R.drawable.ic_sync,
                "手动同步",
                enabled = !status.busy && settings.hasToken,
                filled = true,
            ) {
                onUpload(false)
            }
            FriendActionIcon(
                R.drawable.ic_friends_download,
                "下载并预览恢复",
                enabled = !status.busy && settings.hasToken,
                onClick = onDownload,
            )
            FriendActionIcon(
                R.drawable.ic_friends_upload,
                "强制上传手机数据",
                enabled = !status.busy && settings.hasToken,
            ) {
                confirmForce = true
            }
        }
        Text(
            "上次成功：${status.lastSuccess?.let { displayTime(it, "yyyy年MM月dd日 HH:mm") } ?: "尚无"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        status.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (status.error) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "恢复会替换当前人物与事件，操作前自动保存本地副本。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (confirmForce)
        AlertDialog(
            onDismissRequest = { confirmForce = false },
            title = { Text("替换云端 ppl.json？") },
            text = { Text("将以手机上的人物资料和事件覆盖云端文件。") },
            confirmButton = {
                TextButton(
                    enabled = !status.busy,
                    onClick = {
                        confirmForce = false
                        onUpload(true)
                    },
                ) {
                    Text("确认强制上传")
                }
            },
            dismissButton = { TextButton(onClick = { confirmForce = false }) { Text("取消") } },
        )
    preview?.let { remote ->
        AlertDialog(
            onDismissRequest = { if (!status.busy) sync.cancelPreview() },
            title = { Text("恢复云端数据？") },
            text = {
                Text(
                    "备份时间：${displayTime(remote.exportedAt, "yyyy年MM月dd日 HH:mm")}\n${remote.friends.size} 人 · ${remote.events.size} 条事件\n\n将替换当前手机数据，替换前会保存本地安全副本。"
                )
            },
            confirmButton = {
                TextButton(enabled = !status.busy, onClick = onRestore) { Text("确认恢复") }
            },
            dismissButton = {
                TextButton(enabled = !status.busy, onClick = sync::cancelPreview) { Text("取消") }
            },
        )
    }
}
