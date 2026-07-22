package com.remotwrt.bot.data

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LuciAuthException(message: String) : Exception(message)
class LuciActionException(message: String) : Exception(message)

/**
 * Talks to the stock LuCI login page and to the JSON endpoints exposed by
 * luci-app-remotbot (status, devices, device_action). LuCI's classic auth
 * flow is: POST luci_username/luci_password to /cgi-bin/luci/, which sets a
 * sysauth cookie on success. We don't hardcode the cookie name since it can
 * vary by LuCI theme/version -- instead we grab every Set-Cookie pair we're
 * given and replay all of them, then confirm success by checking whether an
 * endpoint actually returns JSON (a login page comes back as HTML).
 */
class LuciClient(private val prefs: Prefs) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // LuCI's login response is typically a 302 redirect that carries the
    // Set-Cookie header on that redirect itself. If we let OkHttp follow the
    // redirect automatically, response.headers() only reflects the *final*
    // page, which usually doesn't repeat Set-Cookie -- so the cookie looks
    // "missing" even though the server did send it. This client stops at the
    // first hop so we can read it.
    private val loginHttp = http.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private fun statusUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/status"
    private fun devicesUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/devices"
    private fun deviceActionUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/device_action"
    private fun registerFcmUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/register_fcm"
    private fun commandsUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/commands"
    private fun namedDevicesUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/named_devices"
    private fun runCommandUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/run_command"
    private fun loginUrl() = "${prefs.baseUrl}/cgi-bin/luci/"

    /** Attempts to log in with the stored credentials and stores the resulting cookie. */
    fun login() {
        val body = FormBody.Builder()
            .add("luci_username", prefs.username)
            .add("luci_password", prefs.password)
            .build()

        val request = Request.Builder()
            .url(loginUrl())
            .post(body)
            .build()

        loginHttp.newCall(request).execute().use { response ->
            val cookies = response.headers("Set-Cookie")
            if (cookies.isEmpty()) {
                throw LuciAuthException("Server tidak mengirim cookie sesi. Cek URL/login LuCI.")
            }
            // Keep only the "name=value" part of each Set-Cookie header.
            val cookieHeader = cookies.joinToString("; ") { it.substringBefore(';') }
            prefs.cookie = cookieHeader
        }

        // Verify the cookie actually works before declaring success.
        val check = getRaw(statusUrl())
        if (check == null) {
            throw LuciAuthException("Login gagal. Periksa username/password.")
        }
    }

    /** Raw GET with the stored cookie. Returns null if we got redirected to an HTML login page. */
    private fun getRaw(url: String): String? {
        if (prefs.cookie.isBlank()) return null
        val request = Request.Builder()
            .url(url)
            .header("Cookie", prefs.cookie)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: return null
            val trimmed = text.trim()
            if (!trimmed.startsWith("{")) return null
            return trimmed
        }
    }

    /** Raw POST with the stored cookie. Returns null if we got redirected to an HTML login page. */
    private fun postRaw(url: String, form: FormBody): String? {
        if (prefs.cookie.isBlank()) return null
        val request = Request.Builder()
            .url(url)
            .header("Cookie", prefs.cookie)
            .post(form)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: return null
            val trimmed = text.trim()
            if (!trimmed.startsWith("{")) return null
            return trimmed
        }
    }

    /**
     * Runs [action], transparently re-logging in once and retrying if the
     * stored session turned out to be expired (action returns null).
     */
    private fun withSession(action: () -> String?): JSONObject {
        var raw = action()
        if (raw == null) {
            login()
            raw = action() ?: throw LuciAuthException("Sesi tidak valid setelah login ulang.")
        }
        return JSONObject(raw)
    }

    fun fetchStatus(): RemotbotStatus {
        val json = withSession { getRaw(statusUrl()) }
        return RemotbotStatus(
            ok = json.optBoolean("ok", false),
            error = json.optString("error", "").ifBlank { null },
            running = json.optBoolean("running", false),
            enabled = json.optBoolean("enabled", false),
            tokenSet = json.optBoolean("token_set", false),
            tokenPreview = json.optString("token_preview", ""),
            allowedUsers = json.optString("allowed_users", ""),
            allowedCount = json.optInt("allowed_count", 0),
            cpuTemp = if (json.isNull("cpu_temp")) null else json.optDouble("cpu_temp"),
            cpuTempLimit = json.optDouble("cpu_temp_limit", 75.0),
            load1 = json.optString("load1", "N/A"),
            memPct = json.optInt("mem_pct", 0),
            memUsedMb = json.optInt("mem_used_mb", 0),
            memTotalMb = json.optInt("mem_total_mb", 0),
            ramLimit = json.optInt("ram_limit", 85),
            diskPct = if (json.isNull("disk_pct")) null else json.optInt("disk_pct"),
            uptimeSec = json.optLong("uptime_sec", 0),
            wanUp = json.optBoolean("wan_up", false),
            devicesOnline = json.optInt("devices_online", 0),
            whitelistCount = json.optInt("whitelist_count", 0),
            blockedCount = json.optInt("blocked_count", 0),
            pendingCount = json.optInt("pending_count", 0),
            pkgVersion = json.optString("pkg_version", "-"),
            myIp = json.optString("my_ip", "-"),
            myIpIsp = json.optString("my_ip_isp", "-"),
            myIpCity = json.optString("my_ip_city", "-"),
            myIpRegion = json.optString("my_ip_region", "-"),
            myIpCountry = json.optString("my_ip_country", "-"),
            openclashEnabled = json.optBoolean("openclash_enabled", false),
            openclashRunning = json.optBoolean("openclash_running", false),
            cloudflaredEnabled = json.optBoolean("cloudflared_enabled", false),
            cloudflaredRunning = json.optBoolean("cloudflared_running", false)
        )
    }

    /** Fetches the full per-device list (name/ip/mac/status/category). */
    fun fetchDevices(): List<DeviceInfo> {
        val json = withSession { getRaw(devicesUrl()) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Gagal memuat daftar device"))
        }
        val arr: JSONArray = json.optJSONArray("devices") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            DeviceInfo(
                mac = d.optString("mac", ""),
                ip = d.optString("ip", "-"),
                name = d.optString("name", "-"),
                status = d.optString("status", "TIDAK TERHUBUNG"),
                category = d.optString("category", "unknown"),
                online = d.optBoolean("online", false),
                onlineSinceSec = if (d.has("online_since_sec")) d.optLong("online_since_sec") else null
            )
        }
    }

    /**
     * Sets a device's category. [action] must be one of "whitelist", "block",
     * or "unblock" (clears it back to unknown/no special access).
     */
    fun setDeviceCategory(mac: String, action: String) {
        val form = FormBody.Builder()
            .add("mac", mac)
            .add("action", action)
            .build()
        val json = withSession { postRaw(deviceActionUrl(), form) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Aksi gagal"))
        }
    }

    /**
     * Registers this device's FCM token with the router so pi4Bot.py can push
     * an instant notification straight to this phone (e.g. a new unknown
     * device detected) instead of the app having to wait for its next
     * background poll.
     */
    /** Fetches vpn.html's custom-named devices, cross-referenced with live online status. */
    fun fetchNamedDevices(): List<NamedDeviceInfo> {
        val json = withSession { getRaw(namedDevicesUrl()) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Gagal memuat perangkat"))
        }
        val arr: JSONArray = json.optJSONArray("devices") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            val catObj = d.optJSONObject("categories")
            val categories = mutableMapOf<String, List<AppUsage>>()
            catObj?.keys()?.forEach { key ->
                val appsArr = catObj.optJSONArray(key)
                if (appsArr != null) {
                    val apps = (0 until appsArr.length()).map { j ->
                        val a = appsArr.getJSONObject(j)
                        AppUsage(name = a.optString("name", "-"), count = a.optInt("count", 0))
                    }
                    categories[key] = apps
                }
            }
            NamedDeviceInfo(
                ip = d.optString("ip", "-"),
                name = d.optString("name", "-"),
                icon = d.optString("icon", "📱"),
                online = d.optBoolean("online", false),
                status = d.optString("status", "TIDAK TERHUBUNG"),
                categories = categories
            )
        }
    }

    fun registerFcmToken(token: String) {
        val form = FormBody.Builder()
            .add("token", token)
            .build()
        val json = withSession { postRaw(registerFcmUrl(), form) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Gagal mendaftarkan token notifikasi"))
        }
    }

    /** Lists the admin-defined one-tap commands (config command sections in /etc/config/remotbot). */
    fun fetchCommands(): List<CommandInfo> {
        val json = withSession { getRaw(commandsUrl()) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Gagal memuat daftar command"))
        }
        val arr: JSONArray = json.optJSONArray("commands") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            CommandInfo(
                id = c.optString("id", ""),
                label = c.optString("label", "-")
            )
        }
    }

    /** Runs a predefined command by id, returning its captured stdout/stderr. */
    fun runCommand(id: String): String {
        val form = FormBody.Builder()
            .add("id", id)
            .build()
        val json = withSession { postRaw(runCommandUrl(), form) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Gagal menjalankan command"))
        }
        return json.optString("output", "")
    }
}
