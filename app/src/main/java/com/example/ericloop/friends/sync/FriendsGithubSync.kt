package com.example.ericloop.friends.sync

import android.content.Context
import com.example.ericloop.friends.data.*
import com.example.ericloop.sync.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

class FriendsGithubSync(
    context: Context,
    private val repository: FriendsRepository,
    scope: CoroutineScope,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences("friends_github_sync", Context.MODE_PRIVATE)
    private val connection = GithubConnection.get(context)
    private val client = GithubFileClient()
    val settings = connection.settings
    private var activeIdentity = connection.identity
    private val state = MutableStateFlow(readStatus())
    val status = state.asStateFlow()
    private val previewState = MutableStateFlow<FriendsBackup?>(null)
    val preview = previewState.asStateFlow()

    private data class Preview(
        val backup: FriendsBackup,
        val blob: String,
        val identity: String,
        val authorizationVersion: Long,
        val localDataset: String,
        val localRevision: Long,
    )

    private var downloaded: Preview? = null

    init {
        scope.launch {
            connection.changes.collect { version ->
                connection.operationMutex.withLock {
                    refreshConnection()
                    if (downloaded?.authorizationVersion != version) cancelPreview()
                }
            }
        }
    }

    private fun readStatus(): SyncStatus =
        if (matchesBackupTarget()) {
            SyncStatus(
                lastSuccess = prefs.getLong("success", 0).takeIf { it > 0 },
                uploadedRevision = prefs.getLong("revision", 0),
                uploadedDatasetId = prefs.getString("dataset", null),
            )
        } else SyncStatus()

    private fun refreshConnection() {
        if (activeIdentity != connection.identity) {
            activeIdentity = connection.identity
            state.value = readStatus()
            cancelPreview()
        }
    }

    private suspend fun <T> operation(block: suspend () -> T): T =
        connection.operationMutex.withLock {
            refreshConnection()
            state.value = state.value.copy(busy = true, message = null, error = false)
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val message =
                    when (failure) {
                        is GithubFailure,
                        is GithubHttpFailure,
                        is IllegalArgumentException,
                        is IllegalStateException -> failure.message ?: "备份操作失败"
                        else -> "无法完成备份，请检查网络和 GitHub 连接设置。"
                    }
                state.value = state.value.copy(error = true, message = message)
                throw GithubFailure(message)
            } finally {
                state.value = state.value.copy(busy = false)
            }
        }

    suspend fun upload(force: Boolean = false) = operation {
        val config = settings.value
        val token = connection.readToken()
        val snapshot = canonical(repository.snapshot())
        val bytes = friendsJson.encodeToString(snapshot).toByteArray(Charsets.UTF_8)
        require(bytes.size <= GithubFileClient.MAX_BYTES) { "备份超过 20 MiB，无法上传" }
        val baseline = if (matchesBackupTarget()) prefs.getString("blob", null) else null
        repeat(3) {
            val remote = client.remote(config, token, BACKUP_PATH)
            if (!force && remote.blob != baseline)
                throw GithubFailure(
                    if (baseline == null) "云端已有 ppl.json，请先下载恢复，或确认强制上传。"
                    else "云端 ppl.json 已变化，请重新下载，或确认强制上传。"
                )
            val sameData =
                remote.blob?.let { sha ->
                    val remoteBackup =
                        try {
                            decodeFriendsBackup(client.read(config, token, sha))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (invalid: IllegalArgumentException) {
                            if (force) null else throw GithubFailure("ppl.json 格式无效，未覆盖云端文件。")
                        }
                    remoteBackup != null &&
                        canonical(remoteBackup).copy(exportedAt = 0) ==
                            snapshot.copy(exportedAt = 0)
                } ?: false
            if (sameData) {
                acknowledge(snapshot, remote.blob!!, "云端备份已是最新数据")
                return@operation
            }
            val blob =
                client.writeFile(
                    config,
                    token,
                    remote,
                    BACKUP_PATH,
                    bytes,
                    "Back up friends data (revision ${snapshot.revision})",
                )
            if (blob != null) {
                acknowledge(snapshot, blob, "备份上传成功")
                return@operation
            }
        }
        throw GithubFailure("云端分支持续变化，请稍后重试。")
    }

    suspend fun download() = operation {
        cancelPreview()
        val config = settings.value
        val token = connection.readToken()
        val local = repository.snapshot()
        val blob =
            client.remote(config, token, BACKUP_PATH).blob
                ?: throw GithubFailure("主仓库中尚无 $BACKUP_PATH")
        val backup =
            try {
                decodeFriendsBackup(client.read(config, token, blob))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalArgumentException) {
                throw GithubFailure("ppl.json 格式或数据校验失败，未修改手机数据。")
            }
        downloaded =
            Preview(
                backup,
                blob,
                connection.identity,
                connection.changes.value,
                local.datasetId,
                local.revision,
            )
        previewState.value = backup
        state.value = state.value.copy(message = "已下载，请确认后恢复数据")
    }

    suspend fun restore() = operation {
        val current = downloaded ?: throw GithubFailure("请先重新下载备份")
        require(
            current.identity == connection.identity &&
                current.authorizationVersion == connection.changes.value
        ) {
            "连接设置已变化，请重新下载预览"
        }
        val remote = client.remote(settings.value, connection.readToken(), BACKUP_PATH)
        if (remote.blob != current.blob) {
            cancelPreview()
            throw GithubFailure("云端 ppl.json 已变化，请重新下载预览")
        }
        repository.restore(current.backup, current.localDataset, current.localRevision)
        cancelPreview()
        acknowledge(current.backup, current.blob, "数据已恢复，恢复前已保存本地安全副本")
    }

    fun cancelPreview() {
        downloaded = null
        previewState.value = null
    }

    private fun acknowledge(backup: FriendsBackup, blob: String, message: String) {
        val now = System.currentTimeMillis()
        check(
            prefs
                .edit()
                .putString("connection", connection.identity)
                .putString("backup_path", BACKUP_PATH)
                .putString("blob", blob)
                .putString("dataset", backup.datasetId)
                .putLong("revision", backup.revision)
                .putLong("success", now)
                .commit()
        ) {
            "云端操作已完成，但无法保存同步状态，请重新同步确认"
        }
        state.value =
            SyncStatus(
                busy = true,
                lastSuccess = now,
                message = message,
                uploadedRevision = backup.revision,
                uploadedDatasetId = backup.datasetId,
            )
    }

    private fun canonical(value: FriendsBackup) =
        value.copy(
            friends = value.friends.sortedBy { it.id },
            events = value.events.sortedBy { it.id },
        )

    private fun matchesBackupTarget(): Boolean =
        prefs.getString("connection", null) == connection.identity &&
            prefs.getString("backup_path", null) == BACKUP_PATH

    companion object {
        const val BACKUP_PATH = "data/ericloop/ppl.json"
    }
}
