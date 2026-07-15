package com.remotwrt.bot

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.remotwrt.bot.data.Prefs

class WebViewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PATH = "extra_path"
    }

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = Prefs(this)
        val path = intent.getStringExtra(EXTRA_PATH)
            ?: "/cgi-bin/luci/admin/services/remotbot/settings"
        val url = prefs.baseUrl + path

        // Replay the session cookie captured during the native login so the
        // WebView opens straight into the settings page instead of asking
        // the user to log in a second time.
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (prefs.cookie.isNotBlank()) {
            prefs.cookie.split("; ").forEach { pair ->
                cookieManager.setCookie(url, pair)
            }
        }
        cookieManager.flush()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
        }
        setContentView(webView)
        webView.loadUrl(url)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}
