package com.example.ericloop.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.ericloop.data.Backup
import com.example.ericloop.data.DataRepository
import com.example.ericloop.data.backupJson
import com.example.ericloop.data.decodeBackup
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SyncSettings(val owner: String = "shensunbo", val repo: String = "EricLoop", val branch: String = "main", val hasToken: Boolean = false)
data class SyncStatus(val busy: Boolean = false, val lastSuccess: Long? = null, val message: String? = null,
    val error: Boolean = false, val uploadedRevision: Long = 0, val uploadedDatasetId: String? = null)

/** Manual single-file backups; the token never enters database snapshots or observable state. */
class GithubSync(context: Context, private val repository: DataRepository) {
    private val prefs = context.applicationContext.getSharedPreferences("github_sync_private", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val settingsState = MutableStateFlow(SyncSettings(prefs.getString("owner", "shensunbo")!!,
        prefs.getString("repo", "EricLoop")!!, prefs.getString("branch", "main")!!, prefs.contains("token")))
    val settings: StateFlow<SyncSettings> = settingsState.asStateFlow()
    private val statusState = MutableStateFlow(SyncStatus(lastSuccess = prefs.getLong("success", 0).takeIf { it > 0 },
        uploadedRevision = prefs.getLong("revision", 0), uploadedDatasetId = prefs.getString("dataset", null)))
    val status: StateFlow<SyncStatus> = statusState.asStateFlow()
    private data class Remote(val head: String, val tree: String, val blob: String?)
    private data class Preview(val settings: SyncSettings, val blob: String, val backup: Backup)
    private var preview: Preview? = null
    private class SyncFailure(message: String) : IOException(message)
    private class InvalidBackup(message: String) : IOException(message)
    private class HttpFailure(val code: Int, message: String) : IOException(message)

    suspend fun saveSettings(owner: String, repo: String, branch: String, token: String) = operation {
        val next = SyncSettings(owner.trim(), repo.trim(), branch.trim(), settingsState.value.hasToken || token.isNotBlank())
        if (!next.owner.matches(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}")) ||
            !next.repo.matches(Regex("[A-Za-z0-9_.-]{1,100}")) || next.repo in listOf(".", "..") ||
            next.branch.isBlank() || next.branch.startsWith('/') || next.branch.endsWith('/') ||
            next.branch.contains("..") || next.branch.contains("@{") || next.branch.contains("//") ||
            next.branch.any { it.isWhitespace() || it.code < 32 || it in "~^:?*[\\" } ||
            next.branch.split('/').any { it.startsWith('.') || it.endsWith(".lock") || it.endsWith('.') }) {
            throw SyncFailure("请输入有效的 GitHub 用户、仓库和分支名称。")
        }
        val previous = settingsState.value
        val editor = prefs.edit().putString("owner", next.owner).putString("repo", next.repo).putString("branch", next.branch)
        if (token.isNotBlank()) editor.putString("token", encrypt(token.trim()))
        if (previous.owner != next.owner || previous.repo != next.repo || previous.branch != next.branch) {
            editor.remove("blob").remove("revision").remove("dataset").remove("success")
        }
        if (!editor.commit()) throw SyncFailure("无法保存同步设置，请重试。")
        preview = null
        settingsState.value = next
        statusState.value = SyncStatus(busy = true, lastSuccess = prefs.getLong("success", 0).takeIf { it > 0 },
            uploadedRevision = prefs.getLong("revision", 0), uploadedDatasetId = prefs.getString("dataset", null), message = "连接设置已保存")
    }

    suspend fun clearToken() = operation {
        if (!prefs.edit().remove("token").commit()) throw SyncFailure("无法清除授权，请重试。")
        preview = null
        settingsState.value = settingsState.value.copy(hasToken = false)
        statusState.value = statusState.value.copy(message = "GitHub 授权已清除")
    }

    suspend fun upload(force: Boolean = false) = operation {
        val config = settingsState.value
        val token = readToken()
        val snapshot = canonical(repository.snapshot())
        val content = backupJson.encodeToString(snapshot)
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) throw SyncFailure("本地备份超过 20 MB，无法上传。")
        val desiredSha = gitBlobSha(bytes)
        val baseline = prefs.getString("blob", null)
        repeat(3) {
            val remote = remote(config, token)
            if (!force && remote.blob != baseline) {
                throw SyncFailure(if (baseline == null) "云端已有备份，请先下载并恢复，或确认强制上传。" else "云端备份已变化，请重新下载，或确认强制上传。")
            }
            val sameData = remote.blob?.let { sha ->
                if (sha == desiredSha) true else try { semanticallyEqual(readBackup(config, token, sha), snapshot) }
                catch (invalid: InvalidBackup) { if (force) false else throw invalid }
            } ?: false
            if (sameData) {
                acknowledge(snapshot, remote.blob!!, "云端备份已是最新数据")
                return@operation
            }
            val blob = api(config, token, "POST", listOf("git", "blobs"), buildJsonObject {
                put("content", Base64.encodeToString(bytes, Base64.NO_WRAP)); put("encoding", "base64")
            }).required("sha")
            val tree = api(config, token, "POST", listOf("git", "trees"), buildJsonObject {
                put("base_tree", remote.tree)
                put("tree", buildJsonArray { add(buildJsonObject {
                    put("path", BACKUP_PATH); put("mode", "100644"); put("type", "blob"); put("sha", blob)
                }) })
            }).required("sha")
            val commit = api(config, token, "POST", listOf("git", "commits"), buildJsonObject {
                put("message", "Back up EricLoop data (revision ${snapshot.revision})"); put("tree", tree)
                put("parents", buildJsonArray { add(remote.head) })
            }).required("sha")
            try {
                api(config, token, "PATCH", listOf("git", "refs", "heads") + config.branch.split('/'), buildJsonObject {
                    put("sha", commit); put("force", false)
                })
                acknowledge(snapshot, blob, "备份上传成功")
                return@operation
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: IOException) {
                // A lost response can mean the write succeeded. Verify before retrying.
                val latest = remote(config, token)
                if (latest.blob == blob) {
                    acknowledge(snapshot, blob, "备份上传成功")
                    return@operation
                }
                if (failure is HttpFailure && failure.code !in listOf(409, 422)) throw failure
                if (latest.head == remote.head) throw failure
            }
        }
        throw SyncFailure("云端分支持续变化，请稍后重试。")
    }

    suspend fun download(): Backup = operation {
        preview = null
        val config = settingsState.value
        val token = readToken()
        val remote = remote(config, token)
        val blob = remote.blob ?: throw SyncFailure("此分支尚无 EricLoop 备份。")
        val backup = readBackup(config, token, blob)
        preview = Preview(config, blob, backup)
        statusState.value = statusState.value.copy(message = "备份已下载，请确认后恢复", error = false)
        backup
    }

    suspend fun restore(backup: Backup) = operation {
        val downloaded = preview ?: throw SyncFailure("请先下载云端备份，再确认恢复。")
        if (downloaded.settings != settingsState.value || downloaded.backup != backup) {
            preview = null
            throw SyncFailure("备份预览已失效，请重新下载。")
        }
        val remote = remote(downloaded.settings, readToken())
        if (remote.blob != downloaded.blob) {
            preview = null
            throw SyncFailure("云端备份在预览后已变化，请重新下载。")
        }
        repository.restoreBackup(backup)
        acknowledge(backup, downloaded.blob, "已恢复云端备份；恢复前数据已保存为本地安全副本")
        preview = null
    }

    private suspend fun <T> operation(block: suspend () -> T): T = mutex.withLock {
        statusState.value = statusState.value.copy(busy = true, message = null, error = false)
        try { withContext(Dispatchers.IO) { block() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            val safe = when (failure) {
                is SyncFailure, is HttpFailure, is InvalidBackup -> failure.message ?: "同步失败，请重试。"
                else -> "无法完成同步，请检查网络、令牌及备份格式后重试。"
            }
            statusState.value = statusState.value.copy(message = safe, error = true)
            throw SyncFailure(safe)
        } finally { statusState.value = statusState.value.copy(busy = false) }
    }

    private suspend fun remote(config: SyncSettings, token: String): Remote {
        val head = api(config, token, "GET", listOf("git", "ref", "heads") + config.branch.split('/'))["object"]!!.jsonObject.required("sha")
        val tree = api(config, token, "GET", listOf("git", "commits", head))["tree"]!!.jsonObject.required("sha")
        var subtree = tree
        val segments = BACKUP_PATH.split('/')
        for ((index, segment) in segments.withIndex()) {
            val entries = api(config, token, "GET", listOf("git", "trees", subtree))
            if (entries["truncated"]?.jsonPrimitive?.booleanOrNull == true) throw SyncFailure("云端目录过大，无法可靠定位备份。")
            val entry = entries["tree"]!!.jsonArray.map { it.jsonObject }.find { it["path"]?.jsonPrimitive?.content == segment }
                ?: return Remote(head, tree, null)
            val type = entry.required("type")
            if (index == segments.lastIndex) {
                if (type != "blob" || entry.required("mode") !in listOf("100644", "100755")) throw SyncFailure("备份路径被其他类型的文件占用。")
                return Remote(head, tree, entry.required("sha"))
            }
            if (type != "tree") throw SyncFailure("备份目录路径被文件占用。")
            subtree = entry.required("sha")
        }
        throw SyncFailure("无法读取云端备份路径。")
    }

    private suspend fun readBackup(config: SyncSettings, token: String, sha: String): Backup {
        val blob = api(config, token, "GET", listOf("git", "blobs", sha))
        if (blob["size"]?.jsonPrimitive?.longOrNull?.let { it > MAX_BYTES } == true) throw InvalidBackup("备份文件超过 20 MB，请检查云端数据。")
        if (blob.required("encoding") != "base64") throw InvalidBackup("云端备份编码无效。")
        return try {
            val bytes = Base64.decode(blob.required("content"), Base64.DEFAULT)
            if (bytes.size > MAX_BYTES || gitBlobSha(bytes) != sha) throw InvalidBackup("云端备份内容校验失败。")
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            decodeBackup(text)
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw InvalidBackup("云端备份格式或数据校验失败，未修改手机数据。") }
    }

    private suspend fun api(config: SyncSettings, token: String, method: String, path: List<String>, body: JsonObject? = null): JsonObject {
        val url = HttpUrl.Builder().scheme("https").host("api.github.com").addPathSegment("repos")
            .addPathSegment(config.owner).addPathSegment(config.repo).apply { path.forEach { addPathSegment(it) } }.build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")
            .method(method, body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        val response = suspendCancellableCoroutine<Response> { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        response.use {
            if (!it.isSuccessful) throw HttpFailure(it.code, when (it.code) {
                401 -> "GitHub 令牌无效或已过期，请更新令牌。"
                403 -> "GitHub 拒绝操作，请检查令牌 Contents 读写权限、分支保护或访问频率限制。"
                404 -> "无法访问仓库或分支，请检查名称与令牌权限。"
                409, 422 -> "无法更新分支，请检查分支保护规则或稍后重试。"
                else -> "GitHub 服务暂时无法完成请求，请稍后重试。"
            })
            val source = it.body?.source() ?: throw SyncFailure("GitHub 返回空响应。")
            // Limit JSON responses as well as decoded backup size.
            if (source.request(MAX_RESPONSE_BYTES + 1) && source.buffer.size > MAX_RESPONSE_BYTES) throw SyncFailure("云端响应过大，无法读取。")
            return Json.parseToJsonElement(source.readUtf8()).jsonObject
        }
    }

    private fun acknowledge(backup: Backup, blob: String, message: String) {
        val now = System.currentTimeMillis()
        if (!prefs.edit().putString("blob", blob).putLong("revision", backup.revision)
                .putString("dataset", backup.datasetId).putLong("success", now).commit()) {
            throw SyncFailure("云端操作已完成，但无法保存同步状态；请重新同步确认。")
        }
        statusState.value = SyncStatus(busy = true, lastSuccess = now, message = message,
            uploadedRevision = backup.revision, uploadedDatasetId = backup.datasetId)
    }

    private fun readToken(): String {
        val stored = prefs.getString("token", null) ?: throw SyncFailure("请先填写 GitHub 令牌。")
        return try {
            val pieces = stored.split(':')
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)))
            cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
        } catch (_: Exception) { throw SyncFailure("无法解密 GitHub 令牌，请重新填写并保存。") }
    }
    private fun encrypt(token: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun JsonObject.required(name: String): String = this[name]?.jsonPrimitive?.content
        ?: throw SyncFailure("GitHub 响应格式无效。")
    private fun canonical(backup: Backup) = backup.copy(
        records = backup.records.sortedBy { it.id }, tags = backup.tags.sortedBy { it.id },
        checkIns = backup.checkIns.sortedBy { it.id }, events = backup.events.sortedBy { it.sequence },
    )
    private fun semanticallyEqual(first: Backup, second: Backup) =
        canonical(first).copy(exportedAt = 0) == canonical(second).copy(exportedAt = 0)
    private fun gitBlobSha(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
        return digest.digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private companion object {
        const val BACKUP_PATH = "data/ericloop/backup.json"
        const val KEY_ALIAS = "ericloop.github.token.v1"
        const val MAX_BYTES = 20 * 1024 * 1024
        const val MAX_RESPONSE_BYTES = 32L * 1024 * 1024
    }
}
