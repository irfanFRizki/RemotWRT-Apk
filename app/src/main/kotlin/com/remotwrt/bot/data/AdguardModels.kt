package com.remotwrt.bot.data

data class AdguardOverview(
    val running: Boolean,
    val protectionEnabled: Boolean,
    val version: String,
    val dnsPort: Int,
    val numDnsQueries: Long,
    val numBlockedFiltering: Long,
    val blockedPct: Double,
    val avgProcessingMs: Double,
    val numClients: Int,
    val dnssecEnabled: Boolean,
    val upstreamDns: List<String>
)

data class AdguardFilter(
    val url: String,
    val name: String,
    val enabled: Boolean,
    val rulesCount: Int,
    val lastUpdated: String
)

data class AdguardQueryLogEntry(
    val time: String,
    val domain: String,
    val type: String,
    val client: String,
    val blocked: Boolean,
    val reason: String,
    val upstream: String,
    val elapsedMs: Double
)

data class AdguardSecurity(
    val safeSearchEnabled: Boolean,
    val safeBrowsingEnabled: Boolean,
    val parentalEnabled: Boolean,
    val dnssecEnabled: Boolean,
    val cacheSize: Int,
    val upstreamDns: List<String>
)
