package com.example.ericloop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ericloop.data.isRecordEmoji

private val emojiChoices = listOf("🎯", "📚", "💻", "💡", "📝", "🎨", "🎵", "🎾", "⚽", "🏃", "💪", "🌱", "🏠", "✈️", "🚀", "❤️", "⭐", "☕", "👩‍💻", "👍🏽")

@Composable fun EmojiPicker(initial: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    var custom by rememberSaveable { mutableStateOf(initial ?: "") }
    val value = custom.trim()
    val valid = value.isEmpty() || isRecordEmoji(value)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择图标") }, text = {
        LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(custom, { custom = it }, label = { Text("自定义 Emoji") }, singleLine = true, isError = !valid,
                    supportingText = { Text(if (valid) "也可用系统键盘输入一个 Emoji" else "请输入一个 Emoji，不能是文字或多个图标") })
            }
            items(emojiChoices.chunked(4)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { emoji ->
                        IconButton(onClick = { onSelect(emoji) }, modifier = Modifier.size(48.dp)) { Text(emoji, fontSize = 26.sp, fontFamily = FontFamily.Default) }
                    }
                }
            }
            item { TextButton(onClick = { onSelect(null) }) { Text("使用默认图标") } }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSelect(value.ifEmpty { null }) }) { Text("使用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
