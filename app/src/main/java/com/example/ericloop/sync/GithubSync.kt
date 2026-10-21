package com.example.ericloop.sync

import android.content.Context
import com.example.ericloop.data.Backup
import com.example.ericloop.data.DataRepository
import com.example.ericloop.data.backupJson
import com.example.ericloop.data.decodeBackup
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

data class SyncStatus(
    val busy: Boolean = false,
    val lastSuccess: Long? = null,
    val message: String? = null,
    val error: Boolean = false,
    val uploadedRevision: Long = 0,
    val uploadedDatasetId: String? = null,
)

/** Manual single-file backups; the token never enters database snapshots or observable state. */
class GithubSync(context: Context, private val repository: DataRepository) {
    private val prefs =
        context.applicationContext.getSharedPreferences("github_sync_private", Context.MODE_PRIVATE)
    private val connection = GithubConnection.get(context)
    private val mutex = connection.operationMutex
    private val client = GithubFileClient()
    val settings = connection.settings
    private val statusState =
        MutableStateFlow(
            SyncStatus(
                lastSuccess = prefs.getLong("success", 0).takeIf { it > 0 },
                uploadedRevision = prefs.getLong("revision", 0),
                uploadedDatasetId = prefs.getString("dataset", null),
            )
        )
    val status: StateFlow<SyncStatus> = statusState.asStateFlow()

    private data class RemoteBackup(val backup: Backup, val schemaVersion: Int)

    private data class Preview(val settings: SyncSettings, val blob: String, val backup: Backup)

    private var preview: Preview? = null

    private class SyncFailure(message: String) : IOException(message)

    private class InvalidBackup(message: String) : IOException(message)

    suspend fun saveSettings(owner: String, repo: String, branch: String, token: String) =
        operation {
            connection.saveSettings(owner, repo, branch, token)
            preview = null
            statusState.value =
                SyncStatus(
                    busy = true,
                    lastSuccess = prefs.getLong("success", 0).takeIf { it > 0 },
                    uploadedRevision = prefs.getLong("revision", 0),
                    uploadedDatasetId = prefs.getString("dataset", null),
                    message = "连接设置已保存",
                )
        }

    suspend fun clearToken() = operation {
        connection.clearToken()
        preview = null
        statusState.value = statusState.value.copy(message = "GitHub 授权已清除")
    }

    suspend fun upload(force: Boolean = false) = operation {
        val config = settings.value
        val token = connection.readToken()
        val snapshot = canonical(repository.snapshot())
        val content = backupJson.encodeToString(snapshot)
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) throw SyncFailure("本地备份超过 20 MB，无法上传。")
        val desiredSha = client.gitBlobSha(bytes)
        val baseline = prefs.getString("blob", null)
        repeat(3) {
            val remote = client.remote(config, token, BACKUP_PATH)
            if (!force && remote.blob != baseline) {
                throw SyncFailure(
                    if (baseline == null) "云端已有备份，请先下载并恢复，或确认强制上传。" else "云端备份已变化，请重新下载，或确认强制上传。"
                )
            }
            val sameData =
                remote.blob?.let { sha ->
                    if (sha == desiredSha) true
                    else
                        try {
                            val backup = readBackup(config, token, sha)
                            backup.schemaVersion == snapshot.schemaVersion &&
                                semanticallyEqual(backup.backup, snapshot)
                        } catch (invalid: InvalidBackup) {
                            if (force) false else throw invalid
                        }
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
                    "Back up EricLoop data (revision ${snapshot.revision})",
                )
            if (blob != null) {
                acknowledge(snapshot, blob, "备份上传成功")
                return@operation
            }
        }
        throw SyncFailure("云端分支持续变化，请稍后重试。")
    }

    suspend fun download(): Backup = operation {
        preview = null
        val config = settings.value
        val token = connection.readToken()
        val remote = client.remote(config, token, BACKUP_PATH)
        val blob = remote.blob ?: throw SyncFailure("此分支尚无 EricLoop 备份。")
        val backup = readBackup(config, token, blob).backup
        preview = Preview(config, blob, backup)
        statusState.value = statusState.value.copy(message = "备份已下载，请确认后恢复", error = false)
        backup
    }

    suspend fun restore(backup: Backup) = operation {
        val downloaded = preview ?: throw SyncFailure("请先下载云端备份，再确认恢复。")
        if (downloaded.settings != settings.value || downloaded.backup != backup) {
            preview = null
            throw SyncFailure("备份预览已失效，请重新下载。")
        }
        val remote = client.remote(downloaded.settings, connection.readToken(), BACKUP_PATH)
        if (remote.blob != downloaded.blob) {
            preview = null
            throw SyncFailure("云端备份在预览后已变化，请重新下载。")
        }
        repository.restoreBackup(backup)
        acknowledge(backup, downloaded.blob, "已恢复云端备份；恢复前数据已保存为本地安全副本")
        preview = null
    }

    private suspend fun <T> operation(block: suspend () -> T): T =
        mutex.withLock {
            statusState.value = statusState.value.copy(busy = true, message = null, error = false)
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val safe =
                    when (failure) {
                        is SyncFailure,
                        is GithubFailure,
                        is GithubHttpFailure,
                        is InvalidBackup -> failure.message ?: "同步失败，请重试。"
                        else -> "无法完成同步，请检查网络、令牌及备份格式后重试。"
                    }
                statusState.value = statusState.value.copy(message = safe, error = true)
                throw SyncFailure(safe)
            } finally {
                statusState.value = statusState.value.copy(busy = false)
            }
        }

    private suspend fun readBackup(config: SyncSettings, token: String, sha: String): RemoteBackup {
        val text = client.read(config, token, sha)
        return try {
            val schema =
                Json.parseToJsonElement(text).jsonObject["schemaVersion"]?.jsonPrimitive?.intOrNull
                    ?: throw InvalidBackup("备份版本无效。")
            RemoteBackup(decodeBackup(text), schema)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw InvalidBackup("云端备份格式或数据校验失败，未修改手机数据。")
        }
    }

    private fun acknowledge(backup: Backup, blob: String, message: String) {
        val now = System.currentTimeMillis()
        if (
            !prefs
                .edit()
                .putString("blob", blob)
                .putLong("revision", backup.revision)
                .putString("dataset", backup.datasetId)
                .putLong("success", now)
                .commit()
        ) {
            throw SyncFailure("云端操作已完成，但无法保存同步状态；请重新同步确认。")
        }
        statusState.value =
            SyncStatus(
                busy = true,
                lastSuccess = now,
                message = message,
                uploadedRevision = backup.revision,
                uploadedDatasetId = backup.datasetId,
            )
    }

    private fun canonical(backup: Backup) =
        backup.copy(
            records = backup.records.sortedBy { it.id },
            tags = backup.tags.sortedBy { it.id },
            checkIns = backup.checkIns.sortedBy { it.id },
            events = backup.events.sortedBy { it.sequence },
        )

    private fun semanticallyEqual(first: Backup, second: Backup) =
        canonical(first).copy(exportedAt = 0) == canonical(second).copy(exportedAt = 0)

    private companion object {
        const val BACKUP_PATH = "data/ericloop/backup.json"
        const val MAX_BYTES = 20 * 1024 * 1024
    }
}
