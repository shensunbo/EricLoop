package com.example.ericloop.friends.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ericloop.R
import com.example.ericloop.friends.data.*

@Composable
fun FriendEditor(
    friend: Friend?,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (String, FriendGender, String, String, String) -> Unit,
) {
    var name by rememberSaveable(friend?.id) { mutableStateOf(friend?.name.orEmpty()) }
    var genderName by
        rememberSaveable(friend?.id) { mutableStateOf((friend?.gender ?: FriendGender.BOY).name) }
    var intro by rememberSaveable(friend?.id) { mutableStateOf(friend?.intro.orEmpty()) }
    var notes by rememberSaveable(friend?.id) { mutableStateOf(friend?.notes.orEmpty()) }
    var score by rememberSaveable(friend?.id) { mutableStateOf(friend?.initialScore ?: "0") }
    var discard by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val validScore = runCatching { parseScore(score.trim()) }.isSuccess
    val dirty =
        name != friend?.name.orEmpty() ||
            genderName != (friend?.gender ?: FriendGender.BOY).name ||
            intro != friend?.intro.orEmpty() ||
            notes != friend?.notes.orEmpty() ||
            score != (friend?.initialScore ?: "0")
    fun back() {
        if (busy) return
        if (dirty) discard = true else onBack()
    }
    BackHandler { back() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(if (friend == null) "新增人物" else "编辑资料", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            name,
            { name = it },
            label = { Text("昵称（必填）") },
            isError = submitted && name.isBlank(),
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("性别", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(FriendGender.BOY, FriendGender.GIRL).forEach { gender ->
                FilterChip(
                    selected = genderName == gender.name,
                    onClick = { genderName = gender.name },
                    enabled = !busy,
                    label = { GenderIcon(gender) },
                )
            }
        }
        if (submitted && genderName == FriendGender.UNSPECIFIED.name)
            Text(
                "请选择性别",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        OutlinedTextField(
            intro,
            { intro = it },
            label = { Text("简介") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            notes,
            { notes = it },
            label = { Text("备注") },
            minLines = 3,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            score,
            { score = it },
            label = { Text("初始好感度") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            supportingText = { Text("当前好感度 = 初始值 + 全部事件分值") },
            singleLine = true,
            isError = !validScore,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FriendActionIcon(
                R.drawable.ic_check,
                "保存资料",
                filled = true,
                enabled = !busy,
                onClick = {
                    submitted = true
                    if (
                        name.isNotBlank() &&
                            validScore &&
                            genderName != FriendGender.UNSPECIFIED.name
                    )
                        onSave(name, FriendGender.valueOf(genderName), intro, notes, score)
                },
            )
            FriendActionIcon(R.drawable.ic_close, "取消编辑", enabled = !busy) { back() }
        }
    }
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text("放弃未保存的修改？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        discard = false
                        onBack()
                    }
                ) {
                    Text("放弃修改")
                }
            },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } },
        )
}
