package com.ella.music.ui.player

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.netease.NeteaseLinks
import com.ella.music.ui.components.EllaMiuixBottomSheet

@SuppressLint("SetJavaScriptEnabled")
@Composable internal fun NeteaseWebSheetHost() {
    val url by NeteaseLinks.webSheetUrl.collectAsState()
    val target = url ?: return
    var webView by remember(target) { mutableStateOf<WebView?>(null) }
    androidx.activity.compose.BackHandler {
        if (webView?.canGoBack() == true) webView?.goBack() else NeteaseLinks.webSheetUrl.value = null
    }
    EllaMiuixBottomSheet(show = true, enableNestedScroll = false,
        title = stringResource(R.string.netease_link_target_web),
        onDismissRequest = { NeteaseLinks.webSheetUrl.value = null }) {
        key(target) {
            AndroidView(modifier = Modifier.fillMaxWidth().height(520.dp), factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            return request.url.scheme !in setOf("https", "http")
                        }
                    }
                    loadUrl(target)
                    webView = this
                }
            }, onRelease = { it.stopLoading(); it.destroy(); webView = null })
        }
    }
}
