package com.remotwrt.bot

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.remotwrt.bot.data.LuciClient
import com.remotwrt.bot.data.Prefs
import com.remotwrt.bot.work.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RemotFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val prefs = Prefs(applicationContext)
        if (!prefs.isConfigured) return
        // Best-effort: if this fails (e.g. router unreachable right now), the
        // token will simply get re-sent next time FCM refreshes it or the app
        // is reopened -- not worth surfacing an error for a background event.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LuciClient(prefs).registerFcmToken(token)
            } catch (_: Exception) {
                // ignored, see comment above
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data

        when (data["type"]) {
            "pending_device" -> showPendingDeviceNotification(
                mac = data["mac"] ?: return,
                ip = data["ip"] ?: "-",
                name = data["name"] ?: "-"
            )
            else -> {
                // Generic push (e.g. future alert types): just show whatever
                // title/body the server sent, no special actions.
                val title = message.notification?.title ?: data["title"] ?: "RemotWRT Bot"
                val body = message.notification?.body ?: data["body"] ?: ""
                NotificationHelper.notify(applicationContext, System.currentTimeMillis().toInt(), title, body)
            }
        }
    }

    private fun showPendingDeviceNotification(mac: String, ip: String, name: String) {
        NotificationHelper.ensureChannel(applicationContext)
        val notifId = mac.hashCode()

        fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
            val intent = Intent(applicationContext, DeviceActionReceiver::class.java).apply {
                putExtra(DeviceActionReceiver.EXTRA_MAC, mac)
                putExtra(DeviceActionReceiver.EXTRA_ACTION, action)
                putExtra(DeviceActionReceiver.EXTRA_NOTIF_ID, notifId)
            }
            return PendingIntent.getBroadcast(
                applicationContext, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            applicationContext, notifId, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Device Baru Terdeteksi")
            .setContentText("$name ($ip) menunggu approval")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Nama: $name\nIP: $ip\nMAC: $mac\n\nTap tombol untuk langsung memutuskan.")
            )
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "✅ Whitelist", actionPendingIntent("whitelist", notifId * 10 + 1))
            .addAction(0, "🚫 Blokir", actionPendingIntent("block", notifId * 10 + 2))
            .build()

        NotificationManagerCompat.from(applicationContext).notify(notifId, notification)
    }
}
