package com.example.ericloop.data

/** Preset icons are derived from stable IDs so existing backups remain unchanged. */
fun tagEmoji(tag: LoopTag): String = tag.emoji ?: when (tag.id) {
    "work" -> "💼"
    "study" -> "📚"
    "life" -> "🏡"
    "health" -> "💪"
    "project" -> "🚀"
    else -> "🏷️"
}
