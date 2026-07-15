package com.remotwrt.bot.data

data class DeviceInfo(
    val mac: String,
    val ip: String,
    val name: String,
    val status: String,
    val category: String, // "whitelist" | "blocked" | "pending" | "unknown"
    val online: Boolean = false,
    val onlineSinceSec: Long? = null
)
