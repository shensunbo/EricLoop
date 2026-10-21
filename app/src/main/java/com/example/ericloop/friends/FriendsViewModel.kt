package com.example.ericloop.friends

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ericloop.friends.data.*
import com.example.ericloop.friends.sync.FriendsGithubSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class FriendsViewModel(application: Application) : AndroidViewModel(application) {
    val repository = FriendsRepository(application)
    val sync = FriendsGithubSync(application, repository, viewModelScope)
    val data = repository.data
    private val loadingError = MutableStateFlow<String?>(null)
    val error = loadingError.asStateFlow()
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    private val notices = Channel<String>(Channel.BUFFERED)
    val messages = notices.receiveAsFlow()
    private var networkStarting = false

    init {
        reload()
    }

    fun reload() {
        loadingError.value = null
        viewModelScope.launch {
            try {
                repository.load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                loadingError.value = "无法读取朋友数据，请重试。"
            }
        }
    }

    fun work(action: suspend () -> Unit) {
        if (working.value) return
        working.value = true
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                notices.send(failure.message ?: "操作失败，请重试")
            } finally {
                working.value = false
            }
        }
    }

    fun networkWork(action: suspend () -> Unit) {
        if (networkStarting || sync.status.value.busy) return
        networkStarting = true
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                notices.send(failure.message ?: "朋友备份操作失败")
            } finally {
                networkStarting = false
            }
        }
    }

    override fun onCleared() {
        repository.close()
    }
}
