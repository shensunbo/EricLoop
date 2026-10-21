package com.example.ericloop.sync

import android.util.Base64
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
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

class GithubFailure(message: String) : IOException(message)

class GithubHttpFailure(val code: Int, message: String) : IOException(message)

data class GithubRemote(val head: String, val tree: String, val blob: String?)

/** The same single-file Git API behavior for both independent backup formats. */
class GithubFileClient {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    suspend fun remote(config: SyncSettings, token: String, path: String): GithubRemote {
        val head =
            api(config, token, "GET", listOf("git", "ref", "heads") + config.branch.split('/'))[
                    "object"]!!
                .jsonObject
                .required("sha")
        val tree =
            api(config, token, "GET", listOf("git", "commits", head))["tree"]!!
                .jsonObject
                .required("sha")
        var subtree = tree
        val segments = path.split('/')
        for ((index, segment) in segments.withIndex()) {
            val entries = api(config, token, "GET", listOf("git", "trees", subtree))
            if (entries["truncated"]?.jsonPrimitive?.booleanOrNull == true)
                throw GithubFailure("云端目录过大，无法可靠定位备份。")
            val entry =
                entries["tree"]!!
                    .jsonArray
                    .map { it.jsonObject }
                    .find { it["path"]?.jsonPrimitive?.content == segment }
                    ?: return GithubRemote(head, tree, null)
            val type = entry.required("type")
            if (index == segments.lastIndex) {
                if (type != "blob" || entry.required("mode") !in listOf("100644", "100755"))
                    throw GithubFailure("备份路径被其他类型的文件占用。")
                return GithubRemote(head, tree, entry.required("sha"))
            }
            if (type != "tree") throw GithubFailure("备份目录路径被文件占用。")
            subtree = entry.required("sha")
        }
        throw GithubFailure("无法读取云端备份路径。")
    }

    suspend fun read(config: SyncSettings, token: String, sha: String): String {
        val blob = api(config, token, "GET", listOf("git", "blobs", sha))
        if (blob["size"]?.jsonPrimitive?.longOrNull?.let { it > MAX_BYTES } == true)
            throw GithubFailure("备份文件超过 20 MB，请检查云端数据。")
        if (blob.required("encoding") != "base64") throw GithubFailure("云端备份编码无效。")
        return try {
            val bytes = Base64.decode(blob.required("content"), Base64.DEFAULT)
            if (bytes.size > MAX_BYTES || gitBlobSha(bytes) != sha)
                throw GithubFailure("云端备份内容校验失败。")
            val text =
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString()
            text
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw GithubFailure("云端备份格式或内容校验失败，未修改手机数据。")
        }
    }

    // null means HEAD moved; callers re-read and recheck their conflict baseline.
    suspend fun writeFile(
        config: SyncSettings,
        token: String,
        remote: GithubRemote,
        path: String,
        bytes: ByteArray,
        message: String,
    ): String? {
        val blob =
            api(
                    config,
                    token,
                    "POST",
                    listOf("git", "blobs"),
                    buildJsonObject {
                        put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
                        put("encoding", "base64")
                    },
                )
                .required("sha")
        val tree =
            api(
                    config,
                    token,
                    "POST",
                    listOf("git", "trees"),
                    buildJsonObject {
                        put("base_tree", remote.tree)
                        put(
                            "tree",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("path", path)
                                        put("mode", "100644")
                                        put("type", "blob")
                                        put("sha", blob)
                                    }
                                )
                            },
                        )
                    },
                )
                .required("sha")
        val commit =
            api(
                    config,
                    token,
                    "POST",
                    listOf("git", "commits"),
                    buildJsonObject {
                        put("message", message)
                        put("tree", tree)
                        put("parents", buildJsonArray { add(remote.head) })
                    },
                )
                .required("sha")
        try {
            api(
                config,
                token,
                "PATCH",
                listOf("git", "refs", "heads") + config.branch.split('/'),
                buildJsonObject {
                    put("sha", commit)
                    put("force", false)
                },
            )
            return blob
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IOException) {
            // A lost response can mean the write succeeded. Verify before retrying.
            val latest = remote(config, token, path)
            if (latest.blob == blob) {
                return blob
            }
            if (failure is GithubHttpFailure && failure.code !in listOf(409, 422)) throw failure
            if (latest.head == remote.head) throw failure
        }
        return null
    }

    suspend fun api(
        config: SyncSettings,
        token: String,
        method: String,
        path: List<String>,
        body: JsonObject? = null,
    ): JsonObject {
        val url =
            HttpUrl.Builder()
                .scheme("https")
                .host("api.github.com")
                .addPathSegment("repos")
                .addPathSegment(config.owner)
                .addPathSegment(config.repo)
                .apply { path.forEach { addPathSegment(it) } }
                .build()
        val request =
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .method(
                    method,
                    body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType()),
                )
                .build()
        val response =
            suspendCancellableCoroutine<Response> { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }

                        override fun onResponse(call: Call, response: Response) {
                            continuation.resume(response) { _, value, _ -> value.close() }
                        }
                    }
                )
            }
        response.use {
            if (!it.isSuccessful)
                throw GithubHttpFailure(
                    it.code,
                    when (it.code) {
                        401 -> "GitHub 令牌无效或已过期，请更新令牌。"
                        403 -> "GitHub 拒绝操作，请检查令牌 Contents 读写权限、分支保护或访问频率限制。"
                        404 -> "无法访问仓库或分支，请检查名称与令牌权限。"
                        409,
                        422 -> "无法更新分支，请检查分支保护规则或稍后重试。"
                        else -> "GitHub 服务暂时无法完成请求，请稍后重试。"
                    },
                )
            val source = it.body?.source() ?: throw GithubFailure("GitHub 返回空响应。")
            // Limit JSON responses as well as decoded backup size.
            if (source.request(MAX_RESPONSE_BYTES + 1) && source.buffer.size > MAX_RESPONSE_BYTES)
                throw GithubFailure("云端响应过大，无法读取。")
            return Json.parseToJsonElement(source.readUtf8()).jsonObject
        }
    }

    private fun JsonObject.required(name: String): String =
        this[name]?.jsonPrimitive?.content ?: throw GithubFailure("GitHub 响应格式无效。")

    fun gitBlobSha(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
        return digest.digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    companion object {
        const val MAX_BYTES = 20 * 1024 * 1024
        private const val MAX_RESPONSE_BYTES = 32L * 1024 * 1024
    }
}
