package com.remotwrt.bot.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * All app settings (server URL, LuCI credentials, session cookie, and the
 * last-seen values used to detect state transitions for notifications) are
 * stored in an EncryptedSharedPreferences file so the router password never
 * sits on disk in plain text.
 */
class Prefs(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "remotwrt_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var baseUrl: String
        get() = prefs.getString("base_url", "") ?: ""
        set(value) = prefs.edit().putString("base_url", value.trimEnd('/')).apply()

    var username: String
        get() = prefs.getString("username", "") ?: ""
        set(value) = prefs.edit().putString("username", value).apply()

    var password: String
        get() = prefs.getString("password", "") ?: ""
        set(value) = prefs.edit().putString("password", value).apply()

    /** Raw "name=value; name2=value2" cookie header captured after a successful LuCI login. */
    var cookie: String
        get() = prefs.getString("cookie", "") ?: ""
        set(value) = prefs.edit().putString("cookie", value).apply()

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    fun clear() = prefs.edit().clear().apply()

    // ---- last-seen snapshot, used by MonitorWorker to only notify on state changes ----

    var lastWanUp: Boolean
        get() = prefs.getBoolean("last_wan_up", true)
        set(value) = prefs.edit().putBoolean("last_wan_up", value).apply()

    var lastRunning: Boolean
        get() = prefs.getBoolean("last_running", true)
        set(value) = prefs.edit().putBoolean("last_running", value).apply()

    var lastPendingCount: Int
        get() = prefs.getInt("last_pending_count", 0)
        set(value) = prefs.edit().putInt("last_pending_count", value).apply()

    var lastWhitelistCount: Int
        get() = prefs.getInt("last_whitelist_count", 0)
        set(value) = prefs.edit().putInt("last_whitelist_count", value).apply()

    var lastBlockedCount: Int
        get() = prefs.getInt("last_blocked_count", 0)
        set(value) = prefs.edit().putInt("last_blocked_count", value).apply()

    // ---- background auto-update: tracks an APK already downloaded by MonitorWorker ----

    var downloadedUpdateVersionCode: Int
        get() = prefs.getInt("downloaded_update_version_code", 0)
        set(value) = prefs.edit().putInt("downloaded_update_version_code", value).apply()

    var downloadedUpdateFilePath: String
        get() = prefs.getString("downloaded_update_file_path", "") ?: ""
        set(value) = prefs.edit().putString("downloaded_update_file_path", value).apply()

    var lastCpuAlert: Boolean
        get() = prefs.getBoolean("last_cpu_alert", false)
        set(value) = prefs.edit().putBoolean("last_cpu_alert", value).apply()

    var lastRamAlert: Boolean
        get() = prefs.getBoolean("last_ram_alert", false)
        set(value) = prefs.edit().putBoolean("last_ram_alert", value).apply()
}
