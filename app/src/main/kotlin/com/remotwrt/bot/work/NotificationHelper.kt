package com.remotwrt.bot.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.remotwrt.bot.MainActivity
import android.app.PendingIntent
import android.content.Intent

object NotificationHelper {
    const val CHANNEL_ID = "remotwrt_alerts"
    private const val CHANNEL_NAME = "RemotWRT Alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Peringatan suhu, RAM, WAN, dan device baru dari router"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun notify(context: Context, id: Int, title: String, text: String) {
        ensureChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /**
     * Shown once MonitorWorker has finished silently downloading a newer APK
     * in the background. Tapping the action button installs it immediately --
     * the one tap Android requires from any non-privileged app (there's no way
     * to skip this system confirmation without root/Device Owner status).
     */
    fun notifyUpdateReady(context: Context, versionTag: String, apkFile: java.io.File) {
        ensureChannel(context)

        val installIntent = Intent(context, com.remotwrt.bot.update.UpdateInstallReceiver::class.java).apply {
            putExtra(com.remotwrt.bot.update.UpdateInstallReceiver.EXTRA_APK_PATH, apkFile.absolutePath)
        }
        val installPendingIntent = PendingIntent.getBroadcast(
            context, 2001, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update $versionTag Siap Dipasang")
            .setContentText("Sudah diunduh otomatis di background. Tap tombol untuk pasang sekarang.")
            .setAutoCancel(true)
            .addAction(0, "Install Sekarang", installPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(2001, notification)
    }
}
