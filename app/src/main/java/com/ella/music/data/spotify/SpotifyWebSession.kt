package com.ella.music.data.spotify

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

internal const val SPOTIFY_WEB_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
internal data class SpotifyWebSession(val accessToken: String, val clientToken: String?, val expiresAt: Long)
internal fun parseSpotifyWebSession(raw: String, now: Long): SpotifyWebSession? = runCatching {
    val data = JSONObject(raw)
    if (data.optBoolean("isAnonymous", true)) return null
    val token = data.optString("accessToken").takeIf { it.isNotBlank() && it.length < 16384 } ?: return null
    val expiration = data.optLong("accessTokenExpirationTimestampMs").takeIf { it > now } ?: return null
    SpotifyWebSession(token, null, expiration)
}.getOrNull()

/** Let Spotify's web player mint its own session and client tokens; no third-party token service. */
internal class SpotifyWebSessionProvider(private val context: Context) {
    private val mutex = Mutex()
    private var session: SpotifyWebSession? = null
    private var sessionCookie = ""
    suspend fun session(cookie: String, refresh: Boolean = false): SpotifyWebSession? = mutex.withLock {
        if (cookie.isBlank()) return@withLock null
        if (!refresh && cookie == sessionCookie) session?.takeIf { it.expiresAt > System.currentTimeMillis() + 30_000 }?.let { return@withLock it }
        session = null
        sessionCookie = cookie
        withContext(Dispatchers.Main.immediate) { harvest(cookie) }.also { session = it }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun harvest(cookie: String): SpotifyWebSession? {
        var access: SpotifyWebSession? = null
        var clientToken: String? = null
        val webView = WebView(context.applicationContext)
        var scriptHandler: androidx.webkit.ScriptHandler? = null
        try {
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                userAgentString = SPOTIFY_WEB_USER_AGENT
                mediaPlaybackRequiresUserGesture = true
            }
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            android.webkit.WebStorage.getInstance().deleteOrigin("https://open.spotify.com")
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                cookieManager.setCookie("https://open.spotify.com/", "sp_dc=$cookie; Domain=.spotify.com; Path=/; Secure; HttpOnly") {
                    if (continuation.isActive) continuation.resume(Unit) { _, _, _ -> }
                }
            }
            cookieManager.flush()
            val script = TOKEN_HOOK
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                scriptHandler = WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("https://open.spotify.com"))
            }
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame && (request.url.scheme != "https" || request.url.host != "open.spotify.com")
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    if (url?.startsWith("https://open.spotify.com/") == true) view.evaluateJavascript(script, null)
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    if (url?.startsWith("https://open.spotify.com/") == true) view.evaluateJavascript(script, null)
                }
            }
            webView.loadUrl("https://open.spotify.com/")
            // Poll the isolated page's hook rather than exposing a native JavaScript interface.
            return withTimeoutOrNull(25_000) {
                var tokenArrivedAt = 0L
                while (true) {
                    if (webView.url?.startsWith("https://open.spotify.com/") == true) {
                        val payload = kotlinx.coroutines.suspendCancellableCoroutine<String?> { continuation ->
                            webView.evaluateJavascript("JSON.stringify(window.__halcyonSpotifySession || {})") { result ->
                                if (continuation.isActive) continuation.resume(result) { _, _, _ -> }
                            }
                        }
                        val parsed = runCatching { JSONObject("{\"value\":$payload}").getString("value").let(::JSONObject) }.getOrNull()
                        parsed?.optJSONObject("access")?.let { data ->
                            parseSpotifyWebSession(data.toString(), System.currentTimeMillis())?.let {
                                access = it
                                if (tokenArrivedAt == 0L) tokenArrivedAt = System.currentTimeMillis()
                            }
                        }
                        parsed?.optString("client")?.takeIf(String::isNotBlank)?.let { clientToken = it }
                    }
                    if (access != null && (clientToken != null || System.currentTimeMillis() - tokenArrivedAt > 3000)) break
                    delay(250)
                }
                access.copy(clientToken = clientToken)
            }
        } finally {
            scriptHandler?.remove()
            webView.stopLoading()
            webView.destroy()
            // The WebView uses the cookie only to mint a session; keep its persistent credential
            // in our encrypted store instead of leaving it in WebView's shared cookie database.
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main.immediate) {
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                    CookieManager.getInstance().setCookie("https://open.spotify.com/",
                        "sp_dc=; Domain=.spotify.com; Path=/; Max-Age=0; Secure; HttpOnly") {
                        if (continuation.isActive) continuation.resume(Unit) { _, _, _ -> }
                    }
                }
                CookieManager.getInstance().flush()
            }
        }
    }

    private companion object {
        val TOKEN_HOOK = """
            (() => {
              if (location.origin !== 'https://open.spotify.com' || window.__halcyonSpotifySession) return;
              const state = window.__halcyonSpotifySession = {};
              // Only this provider's origin; never clear storage for other application WebViews.
              try { localStorage.removeItem('access_token'); sessionStorage.removeItem('access_token'); } catch (_) {}
              function inspect(url, body) {
                try {
                  const u = new URL(url, location.href), p = JSON.parse(body);
                  if (u.origin === 'https://open.spotify.com' && u.pathname === '/api/token' && p.accessToken && p.isAnonymous === false) state.access = p;
                  if (u.origin === 'https://clienttoken.spotify.com' && u.pathname === '/v1/clienttoken' && p.granted_token) state.client = p.granted_token.token;
                } catch (_) {}
              }
              const fetch = window.fetch;
              const isToken = url => { try { const u = new URL(url, location.href); return (
                (u.origin === 'https://open.spotify.com' && u.pathname === '/api/token') ||
                (u.origin === 'https://clienttoken.spotify.com' && u.pathname === '/v1/clienttoken')); } catch (_) { return false; } };
              window.fetch = function(input) {
                const url = input && input.url ? input.url : input;
                const response = fetch.apply(this, arguments);
                if (isToken(url)) response.then(r => r.clone().text().then(b => inspect(url, b)).catch(() => {})).catch(() => {});
                return response;
              };
              const open = XMLHttpRequest.prototype.open, send = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.open = function(method, url) { this.__halcyonUrl = url; return open.apply(this, arguments); };
              XMLHttpRequest.prototype.send = function() {
                if (isToken(this.__halcyonUrl)) this.addEventListener('load', () => { try { inspect(this.__halcyonUrl, this.responseText); } catch (_) {} });
                return send.apply(this, arguments);
              };
            })();
        """.trimIndent()
    }
}
