package com.ella.music.ui.artist

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ella.music.R
import com.ella.music.data.lastfm.isLastFmWebsiteUrl
import com.ella.music.ui.components.EllaMiuixBottomSheet
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun LastFmVerificationSheet(
    url: String,
    onDismissRequest: () -> Unit,
    onVerified: () -> Unit,
    onOpenApiSettings: () -> Unit
) {
    var isVerifying by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    val browserHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.5f).coerceAtMost(440.dp)

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }

    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.lastfm_verification_title),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.lastfm_verification_hint),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(browserHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .background(MiuixTheme.colorScheme.surface)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                            }
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                    super.onPageFinished(view, pageUrl)
                                    if (view == null || pageUrl == null || isVerifying ||
                                        !isLastFmWebsiteUrl(pageUrl)) return
                                    // Wait for a real biography page. A cached title or clearance
                                    // cookie may still be present while the challenge is loading.
                                    view.evaluateJavascript(
                                        """(function() {
                                            var wiki = document.querySelector('.wiki-content');
                                            return !!wiki && !!wiki.textContent.trim();
                                        })()""".trimIndent()
                                    ) { result ->
                                        if (result == "true" && !isVerifying && view.url == pageUrl) {
                                            isVerifying = true
                                            cookieManager.flush()
                                            onVerified()
                                        }
                                    }
                                }
                            }

                            loadUrl(url)
                            webViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(browserHeight)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.lastfm_biography_api_hint),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Button(onClick = onOpenApiSettings, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.lastfm_biography_api_settings))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        CookieManager.getInstance().flush()
                        onVerified()
                    },
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.lastfm_verification_complete),
                        color = MiuixTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
