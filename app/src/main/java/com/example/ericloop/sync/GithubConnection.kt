package com.example.ericloop.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

data class SyncSettings(
    val owner: String = "shensunbo",
    val repo: String = "EricLoop",
    val branch: String = "main",
    val hasToken: Boolean = false,
)

/** Shared credentials; synchronization baselines remain owned by each dataset. */
class GithubConnection private constructor(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("github_sync_private", Context.MODE_PRIVATE)
    val operationMutex = Mutex()
    private val settingsState =
        MutableStateFlow(
            SyncSettings(
                prefs.getString("owner", "shensunbo")!!,
                prefs.getString("repo", "EricLoop")!!,
                prefs.getString("branch", "main")!!,
                prefs.contains("token"),
            )
        )
    val settings: StateFlow<SyncSettings> = settingsState.asStateFlow()
    private val changeState = MutableStateFlow(0L)
    val changes = changeState.asStateFlow()
    val identity: String
        get() =
            prefs.getString("connection_identity", null)
                ?: "${settings.value.owner}/${settings.value.repo}:${settings.value.branch}"

    fun saveSettings(owner: String, repo: String, branch: String, token: String) {
        val next =
            SyncSettings(
                owner.trim(),
                repo.trim(),
                branch.trim(),
                settingsState.value.hasToken || token.isNotBlank(),
            )
        if (
            !next.owner.matches(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}")) ||
                !next.repo.matches(Regex("[A-Za-z0-9_.-]{1,100}")) ||
                next.repo in listOf(".", "..") ||
                next.branch.isBlank() ||
                next.branch.startsWith('/') ||
                next.branch.endsWith('/') ||
                next.branch.contains("..") ||
                next.branch.contains("@{") ||
                next.branch.contains("//") ||
                next.branch.any { it.isWhitespace() || it.code < 32 || it in "~^:?*[\\" } ||
                next.branch.split('/').any {
                    it.startsWith('.') || it.endsWith(".lock") || it.endsWith('.')
                }
        ) {
            throw GithubFailure("请输入有效的 GitHub 用户、仓库和分支名称。")
        }
        val previous = settingsState.value
        val editor =
            prefs
                .edit()
                .putString("owner", next.owner)
                .putString("repo", next.repo)
                .putString("branch", next.branch)
        if (token.isNotBlank()) editor.putString("token", encrypt(token.trim()))
        if (
            previous.owner != next.owner ||
                previous.repo != next.repo ||
                previous.branch != next.branch
        ) {
            editor.remove("blob").remove("revision").remove("dataset").remove("success")
            editor.putString("connection_identity", java.util.UUID.randomUUID().toString())
        }
        if (!editor.commit()) throw GithubFailure("无法保存同步设置，请重试。")
        settingsState.value = next
        changeState.value += 1
    }

    fun clearToken() {
        if (!prefs.edit().remove("token").commit()) throw GithubFailure("无法清除授权，请重试。")
        settingsState.value = settingsState.value.copy(hasToken = false)
        changeState.value += 1
    }

    fun readToken(): String {
        val stored = prefs.getString("token", null) ?: throw GithubFailure("请先填写 GitHub 令牌。")
        return try {
            val pieces = stored.split(':')
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)),
            )
            cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            throw GithubFailure("无法解密 GitHub 令牌，请重新填写并保存。")
        }
    }

    private fun encrypt(token: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "ericloop.github.token.v1"
        @Volatile private var instance: GithubConnection? = null

        fun get(context: Context): GithubConnection =
            instance
                ?: synchronized(this) {
                    instance ?: GithubConnection(context.applicationContext).also { instance = it }
                }
    }
}
