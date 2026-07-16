package com.remotwrt.bot.update

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Int,
    val versionTag: String,
    val releaseNotes: String,
    val assetApiUrl: String // GitHub API asset URL, not browser_download_url -- this is what lets a private repo download work with a token too
)

/**
 * Talks to the GitHub Releases API for this app's own repo. Works against a
 * public repo with no configuration at all; if the repo is private, pass a
 * personal access token (repo scope, read-only is enough) so the API calls
 * and asset download are authorized.
 */
class GithubUpdater(private val githubToken: String? = null) {

    companion object {
        // Update these if the repo is ever renamed or moved to another account.
        private const val OWNER = "irfanFRizki"
        private const val REPO = "RemotWRT-Apk"
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun applyAuth(builder: Request.Builder) {
        if (!githubToken.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $githubToken")
        }
    }

    /** Returns null if there's no release yet, or the response was unparseable. */
    fun fetchLatestRelease(): UpdateInfo? {
        val requestBuilder = Request.Builder()
            .url("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
        applyAuth(requestBuilder)

        http.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            // Tag is always "vN" where N is the workflow run number used as
            // versionCode for that build -- see build-apk.yml.
            val tag = json.optString("tag_name", "")
            val versionCode = tag.removePrefix("v").toIntOrNull() ?: return null

            val assets = json.optJSONArray("assets") ?: return null
            var apkAssetUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk")) {
                    apkAssetUrl = asset.optString("url")
                    break
                }
            }
            val assetUrl = apkAssetUrl ?: return null

            return UpdateInfo(
                versionCode = versionCode,
                versionTag = tag,
                releaseNotes = json.optString("body", ""),
                assetApiUrl = assetUrl
            )
        }
    }

    /**
     * Downloads the APK asset to [destFile], reporting progress via
     * [onProgress] (0f..1f; -1f if the server didn't send a content length).
     */
    fun downloadApk(assetApiUrl: String, destFile: File, onProgress: (Float) -> Unit) {
        val requestBuilder = Request.Builder()
            .url(assetApiUrl)
            .header("Accept", "application/octet-stream")
        applyAuth(requestBuilder)

        http.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Gagal download (HTTP ${response.code})")
            }
            val body = response.body ?: throw Exception("Respons kosong dari server")
            val total = body.contentLength()
            var readSoFar = 0L

            destFile.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        out.write(buffer, 0, n)
                        readSoFar += n
                        onProgress(if (total > 0) readSoFar.toFloat() / total else -1f)
                    }
                }
            }
        }
    }
}
