package com.example.ericloop.friends.data

fun allocateFriendColor(used: Set<Long>): Long {
    val palette =
        listOf(
            0xFF547BC7,
            0xFFB45E80,
            0xFF3F8B76,
            0xFF9670BA,
            0xFFB57C39,
            0xFF3D8EAA,
            0xFFAD6454,
            0xFF7D8841,
        )
    palette
        .firstOrNull { it !in used }
        ?.let {
            return it
        }
    // Odd multiplication permutes the 24-bit RGB space; no color can repeat.
    for (index in 0 until 0x1000000) {
        val rgb = (index.toLong() * 0x9E3779L) and 0xFFFFFF
        val channels =
            listOf((rgb shr 16).toInt(), ((rgb shr 8) and 255).toInt(), (rgb and 255).toInt())
        if (channels.max() - channels.min() < 35 || channels.sum() !in 210..590) continue
        val color = 0xFF000000L or rgb
        if (color !in used) return color
    }
    error("可分配的人物颜色已用完")
}
