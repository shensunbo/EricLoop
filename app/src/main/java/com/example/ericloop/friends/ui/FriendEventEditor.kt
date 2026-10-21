package com.example.ericloop.friends.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ericloop.R
import com.example.ericloop.friends.data.*

@Composable
fun FriendEventEditor(
    event: FriendEvent?,
    delta: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var description by
        rememberSaveable(event?.id, delta) { mutableStateOf(event?.description.orEmpty()) }
    var score by rememberSaveable(event?.id, delta) { mutableStateOf(event?.delta ?: delta) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    val valid = runCatching { parseScore(score.trim()) }.isSuccess
    fun dismiss() {
        if (busy) return
        if (description != event?.description.orEmpty() || score != (event?.delta ?: delta))
            discard = true
        else onDismiss()
    }
    AlertDialog(
        onDismissRequest = { dismiss() },
        title = { Text(if (event == null) "记录${if (delta == "1") "+1" else "-1"} 事件" else "编辑事件") },
        text = {
            BackHandler { dismiss() }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (event != null)
                    OutlinedTextField(
                        score,
                        { score = it },
                        enabled = !busy,
                        label = { Text("变动分数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        singleLine = true,
                        isError = !valid,
                    )
                OutlinedTextField(
                    description,
                    { description = it },
                    label = { Text("事件说明（必填）") },
                    isError = submitted && description.isBlank(),
                    minLines = 3,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (event == null) "保存时记录当前时间" else "编辑保留原发生时间，不保留编辑历史",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            FriendActionIcon(
                R.drawable.ic_check,
                "保存事件",
                filled = true,
                enabled = !busy,
                onClick = {
                    submitted = true
                    if (valid && description.isNotBlank()) onSave(score, description)
                },
            )
        },
        dismissButton = {
            FriendActionIcon(R.drawable.ic_close, "取消事件编辑", enabled = !busy) { dismiss() }
        },
    )
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text("放弃未保存的事件？") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("放弃") } },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } },
        )
}
