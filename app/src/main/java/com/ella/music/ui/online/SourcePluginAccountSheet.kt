package com.ella.music.ui.online

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.sourceplugin.SourcePluginConfig
import com.ella.music.data.sourceplugin.SourcePluginRegistry
import com.ella.music.data.sourceplugin.optFirstString
import com.ella.music.data.sourceplugin.sourcePluginSourceKey
import com.ella.music.ui.folder.WebDavTextField
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text

/**
 * Account sheet for one account-source plugin. Renders only the login methods the plugin
 * declares: cookie credentials, QR-code login, or anonymous (no-login) mode.
 */
@Composable
internal fun SourcePluginAccountSheet(
    config: SourcePluginConfig,
    onDismiss: () -> Unit,
    mainViewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(context) { SourcePluginRegistry.client(context) }
    val accountStore = remember(context) { SourcePluginRegistry.accountStore(context) }
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val allAccounts by accountStore.accounts.collectAsState()
    val account = allAccounts[config.id]
    var cookie by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var qr by remember { mutableStateOf<JSONObject?>(null) }
    var qrStatus by remember { mutableStateOf("") }
    var showCookie by remember { mutableStateOf("cookie" in config.methods) }

    val sourceKey = remember(config.id) { sourcePluginSourceKey(config.id) }

    fun afterLogin() {
        scope.launch {
            SourcePluginRegistry.libraryStore(context, config.id)?.let { store ->
                runCatching { store.refresh(true) }
            }
            if (mainViewModel != null) mainViewModel.setLibrarySource(sourceKey)
            else settingsManager.setLibrarySource(sourceKey)
            message = context.getString(R.string.source_plugin_connected)
        }
    }

    fun login(cookieText: String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                client.login(config, cookieText)
                cookie = ""
                afterLogin()
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                message = error.localizedMessage
                    ?: context.getString(R.string.source_plugin_login_failed)
            } finally { busy = false }
        }
    }

    fun startQrLogin() {
        if (busy) return
        busy = true
        qrStatus = ""
        scope.launch {
            try {
                val code = client.getQrCode(config)
                qr = code
                val key = code.keys().asSequence().firstOrNull { it.equals("key", true) || it.equals("qrKey", true) }
                    ?.let { code.optString(it) }
                    ?: code.optFirstString("unikey", "qrcode_key", "qrkey", "key")
                busy = false
                while (isActive) {
                    delay(1500)
                    val result = client.checkQrCode(config, key)
                    val status = result.optFirstString("status", "state", "code", "message")
                    when {
                        status.contains("ok", true) || status.contains("confirm", true) ||
                            status == "800" || status == "803" || result.opt("credential") != null -> {
                            client.saveQrLogin(config, result)
                            qr = null
                            afterLogin()
                            break
                        }
                        status.contains("expire", true) || status == "801" -> {
                            qrStatus = context.getString(R.string.source_plugin_qr_expired)
                            break
                        }
                        else -> qrStatus = context.getString(R.string.source_plugin_qr_waiting)
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                qrStatus = error.localizedMessage
                    ?: context.getString(R.string.source_plugin_qrcode_failed)
            } finally { busy = false }
        }
    }

    com.ella.music.ui.components.EllaMiuixBottomSheet(
        show = true,
        title = config.name,
        onDismissRequest = onDismiss
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = LocalWindowInfo.current.containerDpSize.height * .72f)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.source_plugin_account_summary, config.author.ifBlank { config.name }))
            if (account != null) {
                Text("${account!!.nickname.ifBlank { account!!.userId }} · ${account!!.userId}")
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            SourcePluginRegistry.libraryStore(context, config.id)?.refresh(true)
                            if (mainViewModel != null) mainViewModel.setLibrarySource(sourceKey)
                            else settingsManager.setLibrarySource(sourceKey)
                            message = context.getString(R.string.source_plugin_connected)
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (error: Exception) { message = error.localizedMessage.orEmpty() }
                        finally { busy = false }
                    }
                }) { Text(stringResource(R.string.source_plugin_sync)) }
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        client.logout(config)
                        if (settingsManager.librarySource.first() == sourceKey) {
                            if (mainViewModel != null) mainViewModel.setLibrarySource(SettingsManager.LIBRARY_SOURCE_LOCAL)
                            else settingsManager.setLibrarySource(SettingsManager.LIBRARY_SOURCE_LOCAL)
                        }
                        message = ""
                    }
                }) { Text(stringResource(R.string.source_plugin_logout)) }
            } else {
                if ("qrcode" in config.methods) {
                    Button(enabled = !busy, onClick = { startQrLogin() }) {
                        Text(stringResource(R.string.source_plugin_qr_login))
                    }
                }
                if ("none" in config.methods && "cookie" !in config.methods && "qrcode" !in config.methods) {
                    Text(stringResource(R.string.source_plugin_anonymous_hint))
                    Button(enabled = !busy, onClick = {
                        scope.launch {
                            if (mainViewModel != null) mainViewModel.setLibrarySource(sourceKey)
                            else settingsManager.setLibrarySource(sourceKey)
                            onDismiss()
                        }
                    }) { Text(stringResource(R.string.source_plugin_use_anonymous)) }
                }
                if ("cookie" in config.methods) {
                    Button(enabled = !busy, onClick = { showCookie = !showCookie }) {
                        Text(stringResource(R.string.source_plugin_cookie_login))
                    }
                    if (showCookie) {
                        WebDavTextField(
                            "Cookie", cookie,
                            visualTransformation = PasswordVisualTransformation(),
                            onValueChange = { cookie = it }
                        )
                        Button(enabled = !busy && cookie.isNotBlank(), onClick = { login(cookie) }) {
                            Text(stringResource(R.string.source_plugin_connect))
                        }
                    }
                }
            }
            qr?.let { code ->
                val image = code.optFirstString("qr", "qrCode", "qrImg", "image", "url")
                QrCodeImage(image)
                if (qrStatus.isNotBlank()) Text(qrStatus)
            }
            Text(if (busy) stringResource(R.string.netease_loading) else message)
        }
    }
}

@Composable
private fun QrCodeImage(data: String) {
    val bitmap = remember(data) {
        runCatching {
            val bytes = when {
                data.startsWith("data:image", true) ->
                    Base64.decode(data.substringAfter("base64,"), Base64.DEFAULT)
                data.startsWith("http://") || data.startsWith("https://") -> return@runCatching null
                else -> Base64.decode(data, Base64.DEFAULT)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.source_plugin_qr_login),
            modifier = Modifier.size(220.dp)
        )
    } else {
        Text(data)
    }
}
