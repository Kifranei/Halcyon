package com.ella.music.ui.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.components.EllaLoadingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.net.URLEncoder

@Composable
fun DynamicCoverWebViewSheet(
    show: Boolean,
    song: Song?,
    onDismissRequest: () -> Unit
) {
    if (!show || song == null) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    WindowBottomSheet(
        show = true,
        enableNestedScroll = false,
        title = context.getString(R.string.player_match_dynamic_cover),
        onDismissRequest = onDismissRequest
    ) {
        DynamicCoverWebViewContent(
            song = song,
            onDownloadComplete = {
                scope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_dynamic_cover_downloaded),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDownloadFailed = { error ->
                scope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_dynamic_cover_download_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e("DynamicCoverWebView", "Download failed", error)
                }
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DynamicCoverWebViewContent(
    song: Song,
    onDownloadComplete: (String) -> Unit,
    onDownloadFailed: (Throwable) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }

    val searchUrl = remember(song.title, song.artist, song.album) {
        val query = listOf(song.title, song.artist, song.album)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        "https://covers.musichoarders.xyz/?q=${URLEncoder.encode(query, "UTF-8")}"
    }
    val autofillScript = remember(song) { buildDynamicCoverAutofillScript(song) }

    val downloadHelper = remember(song) {
        DynamicCoverDownloadHelper(context, song)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.6f)
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            allowContentAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            userAgentString = settings.userAgentString.replace(
                                "wv",
                                ""
                            ).trim()
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                isLoading = newProgress < 100
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                injectScripts(view, autofillScript)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return false // Let WebView handle all URLs
                            }
                        }

                        // Bridge for JS to call Kotlin
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onVideoUrlDetected(videoUrl: String) {
                                    if (videoUrl.isBlank()) return
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            downloadHelper.downloadVideo(videoUrl)
                                            withContext(Dispatchers.Main) {
                                                onDownloadComplete(videoUrl)
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                onDownloadFailed(e)
                                            }
                                        }
                                    }
                                }
                            },
                            "AndroidBridge"
                        )

                        // Set download listener for direct download buttons
                        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                            if (url != null && mimeType?.contains("video") == true) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        downloadHelper.downloadVideo(url)
                                        withContext(Dispatchers.Main) {
                                            onDownloadComplete(url)
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            onDownloadFailed(e)
                                        }
                                    }
                                }
                            }
                        }

                        loadUrl(searchUrl)
                    }
                },
                update = { webView ->
                    if (webView.url != searchUrl) {
                        isLoading = true
                        webView.loadUrl(searchUrl)
                    }
                },
                onRelease = { webView ->
                    webView.apply {
                        stopLoading()
                        setDownloadListener(null)
                        removeJavascriptInterface("AndroidBridge")
                        clearHistory()
                        loadUrl("about:blank")
                        destroy()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                EllaLoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                )
            }
        }
    }
}

private fun injectScripts(view: WebView?, autofillScript: String) {
    if (view == null) return
    view.evaluateJavascript(VIDEO_INTERCEPT_JS, null)
    view.evaluateJavascript(autofillScript, null)
    view.postDelayed({ view.evaluateJavascript(autofillScript, null) }, 500)
    view.postDelayed({ view.evaluateJavascript(autofillScript, null) }, 1500)
}

private fun buildDynamicCoverAutofillScript(song: Song): String {
    val title = JSONObject.quote(song.title)
    val artist = JSONObject.quote(song.artist)
    val album = JSONObject.quote(song.album)
    val query = JSONObject.quote(
        listOf(song.title, song.artist, song.album)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    )
    return """
(function() {
    if (window.__ellaDynamicCoverAutofillDone) return;
    window.__ellaDynamicCoverAutofillDone = true;

    var payload = {
        title: $title,
        artist: $artist,
        album: $album,
        query: $query
    };

    function emitInput(el, value) {
        if (!el || typeof value !== 'string' || value.length === 0) return false;
        var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
        if (nativeSetter && nativeSetter.set) nativeSetter.set.call(el, value);
        else el.value = value;
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        return true;
    }

    function byText(patterns) {
        var nodes = document.querySelectorAll('button, a, span, div');
        for (var i = 0; i < nodes.length; i++) {
            var text = (nodes[i].innerText || nodes[i].textContent || '').trim().toLowerCase();
            if (!text) continue;
            for (var j = 0; j < patterns.length; j++) {
                if (text === patterns[j] || text.indexOf(patterns[j]) >= 0) return nodes[i];
            }
        }
        return null;
    }

    function matchField(field, keywords) {
        var id = ((field.id || '') + ' ' + (field.name || '') + ' ' + (field.placeholder || '') + ' ' + (field.getAttribute('aria-label') || '')).toLowerCase();
        for (var i = 0; i < keywords.length; i++) {
            if (id.indexOf(keywords[i]) >= 0) return true;
        }
        return false;
    }

    function fillFields() {
        var fields = Array.prototype.slice.call(document.querySelectorAll('input[type="text"], input[type="search"], textarea'));
        if (!fields.length) return false;

        var queryField = null;
        var titleField = null;
        var artistField = null;
        var albumField = null;

        for (var i = 0; i < fields.length; i++) {
            var field = fields[i];
            if (!queryField && matchField(field, ['search', 'query', 'keyword', '关键词', '搜索'])) queryField = field;
            if (!titleField && matchField(field, ['title', 'song', 'track', '歌曲', '标题'])) titleField = field;
            if (!artistField && matchField(field, ['artist', 'performer', '歌手', '艺术家'])) artistField = field;
            if (!albumField && matchField(field, ['album', 'release', '专辑'])) albumField = field;
        }

        var filled = false;
        filled = emitInput(titleField, payload.title) || filled;
        filled = emitInput(artistField, payload.artist) || filled;
        filled = emitInput(albumField, payload.album) || filled;

        if (!filled) {
            var primary = queryField || fields[0];
            filled = emitInput(primary, payload.query) || filled;
        } else if (queryField && !queryField.value) {
            emitInput(queryField, payload.query);
        }

        return filled;
    }

    function submitSearch() {
        var submit = document.querySelector('button[type="submit"], input[type="submit"]')
            || byText(['search', 'go', 'find', '搜索', '查找']);
        if (submit && typeof submit.click === 'function') {
            submit.click();
            return true;
        }
        var form = document.querySelector('form');
        if (form && typeof form.requestSubmit === 'function') {
            form.requestSubmit();
            return true;
        }
        var field = document.querySelector('input[type="search"], input[type="text"], textarea');
        if (field) {
            field.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true, key: 'Enter', code: 'Enter' }));
            field.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, cancelable: true, key: 'Enter', code: 'Enter' }));
            return true;
        }
        return false;
    }

    function run() {
        if (!fillFields()) {
            window.__ellaDynamicCoverAutofillDone = false;
            return;
        }
        submitSearch();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', run, { once: true });
    } else {
        run();
    }
})();
"""
}
