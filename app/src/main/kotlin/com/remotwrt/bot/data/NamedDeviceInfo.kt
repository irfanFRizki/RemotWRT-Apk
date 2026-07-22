package com.remotwrt.bot.data

data class AppUsage(val name: String, val count: Int)

data class NamedDeviceInfo(
    val ip: String,
    val name: String,
    val icon: String,
    val online: Boolean,
    val status: String,
    val categories: Map<String, List<AppUsage>> = emptyMap() // e.g. {"game": [{Roblox, 5}]}
)
