package com.remotwrt.bot.data

data class DiskPartitionInfo(
    val name: String,
    val size: String,
    val fileSystem: String,
    val mountPoint: String,
    val usage: String
)

data class DiskInfo(
    val path: String,
    val model: String,
    val serialNumber: String,
    val size: String,
    val temperature: String,
    val partitionTable: String,
    val sataVersion: String,
    val health: String,
    val status: String,
    val partitions: List<DiskPartitionInfo>
)
