package com.remotwrt.bot.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File

/**
 * Triggered by the "Install Sekarang" action button on the update-ready
 * notification (see NotificationHelper.notifyUpdateReady()). This is the one
 * tap Android requires before installing a package from a non-privileged
 * app -- there's no way to skip that confirmation without root or Device
 * Owner status, so this is the closest thing to a one-tap update.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_APK_PATH = "extra_apk_path"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val path = intent.getStringExtra(EXTRA_APK_PATH) ?: return
        val file = File(path)
        if (!file.exists()) return

        if (!UpdateInstaller.canInstallPackages(context)) {
            // Can't launch the "grant install permission" settings screen from
            // a bare broadcast receiver in a way that reliably comes to the
            // foreground on every OEM skin -- fall back to opening the app,
            // where the existing update card can request the permission and
            // finish the install normally.
            val openApp = Intent(context, com.remotwrt.bot.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(openApp)
            return
        }

        UpdateInstaller.installApk(context, file)
    }
}
