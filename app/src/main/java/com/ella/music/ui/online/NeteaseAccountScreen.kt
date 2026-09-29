package com.ella.music.ui.online

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.netease.NeteaseAccountStore
import com.ella.music.data.netease.NeteaseLibraryStore
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.LocalSettingsCloseAction
import com.ella.music.ui.folder.WebDavTextField
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NeteaseAccountScreen(onDismiss: () -> Unit, mainViewModel: MainViewModel?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { NeteaseLibraryStore.getInstance(context) }
    val accounts = remember { NeteaseAccountStore.getInstance(context) }
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val selectedQuality by settingsManager.neteaseQuality.collectAsState(initial = "auto")
    val account by accounts.account.collectAsState()
    val status by store.status.collectAsState()
    var cookie by remember { mutableStateOf("") }
    var uid by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var browser by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    fun connect(session: String) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                store.login(session, uid.toLongOrNull() ?: 0L)
                cookie = ""
                store.refresh(true)
                if (mainViewModel != null) mainViewModel.setLibrarySource(SettingsManager.LIBRARY_SOURCE_NETEASE)
                else SettingsManager.getInstance(context).setLibrarySource(SettingsManager.LIBRARY_SOURCE_NETEASE)
                message = context.getString(R.string.netease_connected)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { message = context.getString(R.string.netease_login_failed) }
            finally { busy = false }
        }
    }
    com.ella.music.ui.components.EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.netease_title),
        onDismissRequest = onDismiss
    ) {
        Column(Modifier.fillMaxWidth()
            .heightIn(max = androidx.compose.ui.platform.LocalWindowInfo.current.containerDpSize.height * .72f)
            .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.netease_summary))
                    com.ella.music.ui.components.EllaSheetCardGroup {
                    com.ella.music.ui.settings.SettingsSearchAnchor(R.string.netease_quality_title) {
                    top.yukonga.miuix.kmp.preference.WindowSpinnerPreference(
                        title = stringResource(R.string.netease_quality_title),
                        summary = stringResource(R.string.netease_quality_summary),
                        items = com.ella.music.data.netease.NeteaseQuality.entries.map {
                            top.yukonga.miuix.kmp.basic.DropdownItem(title = stringResource(it.titleRes))
                        },
                        selectedIndex = com.ella.music.data.netease.NeteaseQuality.fromId(selectedQuality).ordinal,
                        onSelectedIndexChange = { index -> scope.launch {
                            settingsManager.setNeteaseQuality(com.ella.music.data.netease.NeteaseQuality.entries[index].id)
                        } }
                    )
                    }
                    NeteaseDownloadPreferences(settingsManager)
                    }
                    if (account.loggedIn) {
                        Text("${account.nickname} · ${account.userId}")
                        Button(enabled = !busy, onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    store.refresh(true)
                                    mainViewModel?.setLibrarySource(SettingsManager.LIBRARY_SOURCE_NETEASE)
                                    message = context.getString(R.string.netease_connected)
                                } catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { message = context.getString(R.string.netease_sync_failed) }
                                finally { busy = false }
                            }
                        }) { Text(stringResource(R.string.netease_sync)) }
                        Button(enabled = !busy, onClick = {
                            scope.launch {
                                store.logout()
                                clearNeteaseWebSession()
                                mainViewModel?.setLibrarySource(SettingsManager.LIBRARY_SOURCE_LOCAL)
                                if (mainViewModel == null) SettingsManager.getInstance(context).setLibrarySource(SettingsManager.LIBRARY_SOURCE_LOCAL)
                                message = ""
                            }
                        }) { Text(stringResource(R.string.netease_logout)) }
                    } else {
                        Button(enabled = !busy, onClick = { browser = true }) { Text(stringResource(R.string.netease_browser_login)) }
                    }
                    Button(enabled = !busy, onClick = { advanced = !advanced }) { Text(stringResource(R.string.netease_cookie_login)) }
                    if (advanced) {
                        WebDavTextField("Cookie", cookie, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), onValueChange = { cookie = it })
                        WebDavTextField(stringResource(R.string.netease_uid), uid, onValueChange = { uid = it.filter(Char::isDigit) })
                        Button(enabled = !busy && cookie.isNotBlank(), onClick = { connect(cookie) }) {
                            Text(stringResource(R.string.netease_connect))
                        }
                    }
                    Text(if (busy) stringResource(R.string.netease_loading) else status.ifBlank { message })
                    Text(stringResource(R.string.netease_playback_note))
        }
    }
    if (browser) NeteaseBrowserLogin(onDismiss = { browser = false }, onSession = {
        browser = false
        cookie = it
        if (it.isNotBlank()) connect(it) else message = context.getString(R.string.netease_login_failed)
    })
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun NeteaseBrowserLogin(onDismiss: () -> Unit, onSession: (String) -> Unit) {
    val context = LocalContext.current
    val web = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val host = request.url.host.orEmpty()
                    return request.url.scheme != "https" || !(host == "163.com" || host.endsWith(".163.com"))
                }
            }
            loadUrl("https://music.163.com/#/login")
        }
    }
    DisposableEffect(web) { onDispose {
        web.stopLoading()
        clearNeteaseWebSession()
        web.destroy()
    } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onDismiss)
        Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
                Button(onClick = {
                    val cookies = CookieManager.getInstance().getCookie("https://music.163.com").orEmpty()
                    onSession(cookies)
                }) { Text(stringResource(R.string.netease_finish_login)) }
            }
            AndroidView(factory = { web }, modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}

private fun clearNeteaseWebSession() {
    val manager = CookieManager.getInstance()
    for (domain in listOf("music.163.com", ".music.163.com", ".163.com")) {
        for (name in listOf("MUSIC_U", "__csrf", "MUSIC_A", "NMTID")) {
            manager.setCookie("https://music.163.com", "$name=; Domain=$domain; Path=/; Max-Age=0; Secure")
        }
    }
    manager.flush()
}
