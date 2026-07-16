package com.remotwrt.bot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.remotwrt.bot.data.LuciClient
import com.remotwrt.bot.data.Prefs
import com.remotwrt.bot.work.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the "✅ Whitelist" / "🚫 Blokir" action buttons attached to the
 * pending-device FCM notification (see RemotFcmService), so the admin can
 * approve/block a device with one tap from the lock screen without opening
 * the app at all.
 */
class DeviceActionReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_MAC = "extra_mac"
        const val EXTRA_ACTION = "extra_action"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val mac = intent.getStringExtra(EXTRA_MAC) ?: return
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, mac.hashCode())

        // onReceive() must return quickly, but we need to make a network call
        // -- goAsync() extends our lifetime a little so the process isn't
        // killed mid-request. We still keep the actual work as short as
        // possible (a single HTTP call) and always call finish().
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = Prefs(appContext)
                LuciClient(prefs).setDeviceCategory(mac, action)
                updateNotification(appContext, notifId, action, success = true)
            } catch (_: Exception) {
                updateNotification(appContext, notifId, action, success = false)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateNotification(context: Context, notifId: Int, action: String, success: Boolean) {
        NotificationHelper.ensureChannel(context)

        val label = when (action) {
            "whitelist" -> "✅ Diizinkan (Whitelist)"
            "block" -> "🚫 Diblokir"
            else -> action
        }
        val text = if (success) "Status diubah: $label" else "Gagal mengubah status. Buka app untuk coba lagi."

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Device Baru Terdeteksi")
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Replace the original notification (same id) with a plain result
        // notification instead of the one with action buttons, so a repeat
        // tap can't double-fire the same action.
        NotificationManagerCompat.from(context).notify(notifId, notification)
    }
}
