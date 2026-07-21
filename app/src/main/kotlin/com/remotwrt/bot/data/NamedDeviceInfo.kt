package com.remotwrt.bot.data

data class NamedDeviceInfo(
    val ip: String,
    val name: String,
    val icon: String,
    val online: Boolean,
    val status: String
)
