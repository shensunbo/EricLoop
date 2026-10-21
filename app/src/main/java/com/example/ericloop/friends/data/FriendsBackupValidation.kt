package com.example.ericloop.friends.data

import kotlinx.serialization.json.*

fun decodeFriendsBackup(text: String): FriendsBackup {
    val root = friendsJson.parseToJsonElement(text).jsonObject
    require(
        root.keys.containsAll(
            setOf("schemaVersion", "datasetId", "revision", "exportedAt", "friends", "events")
        )
    ) {
        "ppl.json 缺少必要字段"
    }
    root.getValue("friends").jsonArray.forEach {
        require(
            it.jsonObject.keys.containsAll(
                setOf(
                    "id",
                    "name",
                    "gender",
                    "intro",
                    "notes",
                    "initialScore",
                    "colorArgb",
                    "createdAt",
                    "updatedAt",
                )
            )
        ) {
            "人物备份缺少必要字段"
        }
    }
    root.getValue("events").jsonArray.forEach {
        require(
            it.jsonObject.keys.containsAll(
                setOf("id", "friendId", "delta", "description", "occurredAt", "updatedAt")
            )
        ) {
            "事件备份缺少必要字段"
        }
    }
    return friendsJson.decodeFromString<FriendsBackup>(text).also(::validateFriendsBackup)
}

fun validateFriendsBackup(value: FriendsBackup) {
    require(value.schemaVersion == 1) { "不支持的备份版本" }
    require(value.datasetId.isNotBlank() && value.revision >= 0 && value.exportedAt > 0) {
        "备份元数据无效"
    }
    require(value.friends.map { it.id }.toSet().size == value.friends.size) { "人物 ID 重复" }
    require(value.events.map { it.id }.toSet().size == value.events.size) { "事件 ID 重复" }
    require(value.friends.map { it.colorArgb }.toSet().size == value.friends.size) { "人物颜色重复" }
    val ids = value.friends.map { it.id }.toSet()
    value.friends.forEach {
        require(it.id.isNotBlank() && it.name.isNotBlank()) { "人物昵称不能为空" }
        require(it.colorArgb in 0xFF000000L..0xFFFFFFFFL) { "人物颜色无效" }
        require(it.createdAt > 0 && it.updatedAt >= it.createdAt) { "人物时间无效" }
        require(parseScore(it.initialScore).toString() == it.initialScore) { "初始分数格式无效" }
    }
    value.events.forEach {
        require(it.id.isNotBlank() && it.friendId in ids && it.description.isNotBlank()) {
            "事件说明或人物引用无效"
        }
        require(it.occurredAt > 0 && it.updatedAt >= it.occurredAt) { "事件时间无效" }
        require(parseScore(it.delta).toString() == it.delta) { "事件分数格式无效" }
    }
}
