package com.remotwrt.bot.data

data class NamedDeviceInfo(
    val ip: String,
    val name: String,
    val icon: String,
    val online: Boolean,
    val status: String,
    val categories: Map<String, Int> = emptyMap() // e.g. {"social": 3, "game": 1}
)
