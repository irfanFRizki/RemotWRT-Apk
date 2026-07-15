package com.remotwrt.bot.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.remotwrt.bot.data.LuciClient
import com.remotwrt.bot.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MonitorWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs(applicationContext)
        if (!prefs.isConfigured) return@withContext Result.success()

        val status = try {
            LuciClient(prefs).fetchStatus()
        } catch (e: Exception) {
            // Network hiccup or the tunnel being briefly down isn't worth
            // alarming the user about; just retry on the next scheduled run.
            return@withContext Result.success()
        }

        // WAN transition: only notify going up -> down, not every poll it stays down.
        if (prefs.lastWanUp && !status.wanUp) {
            NotificationHelper.notify(
                applicationContext, 1001,
                "WAN Terputus",
                "Koneksi WAN pada router terdeteksi terputus."
            )
        }
        prefs.lastWanUp = status.wanUp

        // Bot process stopped while it's supposed to be enabled.
        if (status.enabled && prefs.lastRunning && !status.running) {
            NotificationHelper.notify(
                applicationContext, 1002,
                "RemotWRT Bot Berhenti",
                "Proses bot Telegram tidak berjalan padahal enabled."
            )
        }
        prefs.lastRunning = status.running

        // New device(s) pending approval since the last check.
        if (status.pendingCount > prefs.lastPendingCount) {
            NotificationHelper.notify(
                applicationContext, 1003,
                "Device Baru Terdeteksi",
                "${status.pendingCount} device menunggu approval admin."
            )
        }
        prefs.lastPendingCount = status.pendingCount

        // Whitelist count changed since the last check -- covers approvals
        // made from Telegram (or anywhere else) too, since this just compares
        // the numbers regardless of where the change came from.
        if (status.whitelistCount > prefs.lastWhitelistCount) {
            NotificationHelper.notify(
                applicationContext, 1006,
                "Device Diizinkan",
                "Jumlah device whitelist berubah jadi ${status.whitelistCount}. Mungkin ada persetujuan baru dari Telegram atau app lain."
            )
        }
        prefs.lastWhitelistCount = status.whitelistCount

        if (status.blockedCount > prefs.lastBlockedCount) {
            NotificationHelper.notify(
                applicationContext, 1007,
                "Device Diblokir",
                "Jumlah device diblokir berubah jadi ${status.blockedCount}."
            )
        }
        prefs.lastBlockedCount = status.blockedCount

        // CPU temperature crossing the configured threshold (upward edge only).
        val cpuOver = status.cpuTemp != null && status.cpuTemp > status.cpuTempLimit
        if (cpuOver && !prefs.lastCpuAlert) {
            NotificationHelper.notify(
                applicationContext, 1004,
                "Suhu CPU Tinggi",
                "Suhu CPU ${status.cpuTemp}°C melebihi batas ${status.cpuTempLimit}°C."
            )
        }
        prefs.lastCpuAlert = cpuOver

        // RAM usage crossing the configured threshold (upward edge only).
        val ramOver = status.memPct > status.ramLimit
        if (ramOver && !prefs.lastRamAlert) {
            NotificationHelper.notify(
                applicationContext, 1005,
                "Pemakaian RAM Tinggi",
                "RAM terpakai ${status.memPct}% melebihi batas ${status.ramLimit}%."
            )
        }
        prefs.lastRamAlert = ramOver

        Result.success()
    }
}
