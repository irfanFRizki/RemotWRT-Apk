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
    private fun rebootUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/reboot"
    private fun logsUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/logs"
    private fun adguardOverviewUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/adguard_overview"
    private fun adguardProtectionUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/adguard_protection"
    private fun adguardQuerylogUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/adguard_querylog"
    private fun adguardQuerylogActionUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/adguard_querylog_action"
    private fun adguardFiltersUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/adguard_filters"
    private fun adguardFilterActionUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/adguard_filter_action"
    private fun adguardSecurityUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/adguard_security"
    private fun adguardSecurityToggleUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/adguard_security_toggle"
    private fun adguardRulesUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/adguard_rules"
    private fun adguardRulesSaveUrl() = "${prefs.baseUrl}/cgi-bin/luci/admin/services/remotbot/adguard_rules_save"
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
            netIface = json.optString("net_iface", "eth1"),
            netRxBytes = json.optLong("net_rx_bytes", 0),
            netTxBytes = json.optLong("net_tx_bytes", 0),
            netRxRateBps = json.optDouble("net_rx_rate_bps", 0.0),
            netTxRateBps = json.optDouble("net_tx_rate_bps", 0.0),
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

    /** Reboots the router. The router delays the actual reboot ~2s so this call can return cleanly. */
    fun rebootRouter() {
        val form = FormBody.Builder().build()
        val json = withSession { postRaw(rebootUrl(), form) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Gagal reboot"))
        }
    }

    /** Fetches the last N lines of the router's system log (logread). */
    fun fetchSystemLog(): String {
        val json = withSession { getRaw(logsUrl()) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Gagal memuat log"))
        }
        return json.optString("log", "")
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

    // ==================== AdGuard Home ====================

    fun fetchAdguardOverview(): AdguardOverview {
        val json = withSession { getRaw(adguardOverviewUrl()) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Gagal memuat status AdGuard Home"))
        }
        val upstream = mutableListOf<String>()
        json.optJSONArray("upstream_dns")?.let { arr ->
            for (i in 0 until arr.length()) upstream.add(arr.optString(i))
        }
        return AdguardOverview(
            running = json.optBoolean("running", false),
            protectionEnabled = json.optBoolean("protection_enabled", false),
            version = json.optString("version", "-"),
            dnsPort = json.optInt("dns_port", 0),
            numDnsQueries = json.optLong("num_dns_queries", 0),
            numBlockedFiltering = json.optLong("num_blocked_filtering", 0),
            blockedPct = json.optDouble("blocked_pct", 0.0),
            avgProcessingMs = json.optDouble("avg_processing_ms", 0.0),
            numClients = json.optInt("num_clients", 0),
            dnssecEnabled = json.optBoolean("dnssec_enabled", false),
            upstreamDns = upstream
        )
    }

    fun setAdguardProtection(enabled: Boolean) {
        val form = FormBody.Builder()
            .add("enabled", enabled.toString())
            .build()
        val json = withSession { postRaw(adguardProtectionUrl(), form) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Gagal mengubah protection"))
        }
    }

    fun fetchAdguardQueryLog(search: String = "", limit: Int = 50): List<AdguardQueryLogEntry> {
        val url = buildString {
            append(adguardQuerylogUrl())
            append("?limit=").append(limit)
            if (search.isNotBlank()) {
                append("&search=").append(java.net.URLEncoder.encode(search, "UTF-8"))
            }
        }
        val json = withSession { getRaw(url) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Gagal memuat query log"))
        }
        val arr = json.optJSONArray("entries") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val e = arr.getJSONObject(i)
            val reason = e.optString("reason", "")
            AdguardQueryLogEntry(
                time = e.optString("time", "-"),
                domain = e.optJSONObject("question")?.optString("host") ?: e.optString("question", "-"),
                type = e.optJSONObject("question")?.optString("type") ?: "-",
                client = e.optString("client", "-"),
                blocked = reason.startsWith("Filtered") || reason == "Blocked" || reason.contains("Block"),
                reason = reason,
                upstream = e.optString("upstream", "-"),
                elapsedMs = e.optString("elapsedMs", "0").toDoubleOrNull() ?: 0.0
            )
        }
    }

    /** [action] must be "block" or "whitelist". Appends a single-domain rule, never touches other rules. */
    fun setAdguardQueryLogAction(domain: String, action: String) {
        val form = FormBody.Builder()
            .add("domain", domain)
            .add("action", action)
            .build()
        val json = withSession { postRaw(adguardQuerylogActionUrl(), form) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Aksi gagal"))
        }
    }

    fun fetchAdguardFilters(): List<AdguardFilter> {
        val json = withSession { getRaw(adguardFiltersUrl()) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Gagal memuat filter"))
        }
        val arr = json.optJSONArray("filters") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val f = arr.getJSONObject(i)
            AdguardFilter(
                url = f.optString("url", ""),
                name = f.optString("name", "-"),
                enabled = f.optBoolean("enabled", false),
                rulesCount = f.optInt("rules_count", 0),
                lastUpdated = f.optString("last_updated", "-")
            )
        }
    }

    /** [action] must be one of "add", "remove", "toggle", "refresh". */
    fun setAdguardFilterAction(action: String, url: String = "", name: String = "", enabled: Boolean = false) {
        val form = FormBody.Builder()
            .add("action", action)
            .add("url", url)
            .add("name", name)
            .add("enabled", enabled.toString())
            .build()
        val json = withSession { postRaw(adguardFilterActionUrl(), form) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Aksi filter gagal"))
        }
    }

    fun fetchAdguardSecurity(): AdguardSecurity {
        val json = withSession { getRaw(adguardSecurityUrl()) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Gagal memuat pengaturan keamanan"))
        }
        val upstream = mutableListOf<String>()
        json.optJSONArray("upstream_dns")?.let { arr ->
            for (i in 0 until arr.length()) upstream.add(arr.optString(i))
        }
        return AdguardSecurity(
            safeSearchEnabled = json.optBoolean("safesearch_enabled", false),
            safeBrowsingEnabled = json.optBoolean("safebrowsing_enabled", false),
            parentalEnabled = json.optBoolean("parental_enabled", false),
            dnssecEnabled = json.optBoolean("dnssec_enabled", false),
            cacheSize = json.optInt("cache_size", 0),
            upstreamDns = upstream
        )
    }

    /** [feature] must be one of "safesearch", "safebrowsing", "parental", "dnssec". */
    fun setAdguardSecurityToggle(feature: String, enabled: Boolean) {
        val form = FormBody.Builder()
            .add("feature", feature)
            .add("enabled", enabled.toString())
            .build()
        val json = withSession { postRaw(adguardSecurityToggleUrl(), form) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Gagal mengubah pengaturan"))
        }
    }

    fun fetchAdguardRules(): String {
        val json = withSession { getRaw(adguardRulesUrl()) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Gagal memuat custom rules"))
        }
        return json.optString("rules", "")
    }

    fun saveAdguardRules(rules: String) {
        val form = FormBody.Builder()
            .add("rules", rules)
            .build()
        val json = withSession { postRaw(adguardRulesSaveUrl(), form) }
        if (!json.optBoolean("ok", false)) {
            throw LuciActionException(json.optString("error", "Gagal menyimpan custom rules"))
        }
    }
}
