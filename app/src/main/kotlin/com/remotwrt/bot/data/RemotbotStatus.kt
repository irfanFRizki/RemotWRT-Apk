package com.remotwrt.bot.data

/**
 * Mirrors the JSON returned by action_status() in luci-app-remotbot's
 * controller (usr/lib/lua/luci/controller/remotbot.lua).
 */
data class RemotbotStatus(
    val ok: Boolean,
    val error: String? = null,

    val running: Boolean = false,
    val enabled: Boolean = false,
    val tokenSet: Boolean = false,
    val tokenPreview: String = "",
    val allowedUsers: String = "",
    val allowedCount: Int = 0,

    val cpuTemp: Double? = null,
    val cpuTempLimit: Double = 75.0,
    val load1: String = "N/A",

    val memPct: Int = 0,
    val memUsedMb: Int = 0,
    val memTotalMb: Int = 0,
    val ramLimit: Int = 85,

    val diskPct: Int? = null,
    val uptimeSec: Long = 0,

    val wanUp: Boolean = false,

    val devicesOnline: Int = 0,
    val whitelistCount: Int = 0,
    val blockedCount: Int = 0,
    val pendingCount: Int = 0,

    val pkgVersion: String = "-",

    val myIp: String = "-",
    val myIpIsp: String = "-",
    val myIpCity: String = "-",
    val myIpRegion: String = "-",
    val myIpCountry: String = "-",
    val openclashEnabled: Boolean = false,
    val openclashRunning: Boolean = false,
    val cloudflaredEnabled: Boolean = false,
    val cloudflaredRunning: Boolean = false
)
