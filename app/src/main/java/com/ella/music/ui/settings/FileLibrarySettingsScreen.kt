package com.ella.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.smb.*
import com.ella.music.data.webdav.*
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.folder.WebDavSettingsDialog
import com.ella.music.ui.folder.WebDavTextField
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import com.ella.music.ui.components.EllaSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun FileLibrarySettingsScreen(source: String, mainViewModel: MainViewModel,
    onBack: () -> Unit, onBrowse: () -> Unit) {
    val smb = source == SettingsManager.LIBRARY_SOURCE_SMB
    val context = LocalContext.current
    val settings = mainViewModel.settingsManager
    val scope = rememberCoroutineScope()
    val activeSource by settings.librarySource.collectAsState(initial = "")
    val songs by mainViewModel.songs.collectAsState()
    val scanning by mainViewModel.isScanning.collectAsState()
    var loaded by remember(source) { mutableStateOf(false) }
    var editing by remember(source) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var url by remember(source) { mutableStateOf("") }
    var username by remember(source) { mutableStateOf("") }
    var password by remember(source) { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(SmbProtocol.Auto) }
    var headers by remember { mutableStateOf(emptyList<WebDavHeader>()) }
    val title = stringResource(if (smb) R.string.smb_title else R.string.webdav_library_title)
    LaunchedEffect(source) {
        if (smb) {
            url = settings.smbUrl.first(); username = settings.smbUsername.first()
            password = SmbPasswordStore.getInstance(context).password
            domain = settings.smbDomain.first(); protocol = SmbProtocol.fromId(settings.smbProtocol.first())
        } else {
            url = settings.webDavUrl.first(); username = settings.webDavUsername.first()
            password = settings.webDavPassword.first(); headers = settings.webDavCustomHeaders.first()
        }
        loaded = true
    }
    fun testOrSave(save: Boolean) {
        if (busy || !loaded) return
        busy = true; status = null
        scope.launch {
            try {
                val normalized = if (smb) SmbLocation.parse(url).url() else WebDavClient.normalizeFileUrl(url.trim())
                require(normalized.isNotBlank())
                withContext(Dispatchers.IO) {
                    if (smb) SmbSession(SmbAccount(SmbLocation.parse(normalized), username, password, domain, protocol))
                        .use { it.list(SmbLocation.parse(normalized)) }
                    else check(WebDavClient.test(WebDavConfig(normalized, username, password, customHeaders = headers))) {
                        context.getString(R.string.webdav_load_failed)
                    }
                }
                if (save) {
                    if (smb) {
                        withContext(Dispatchers.IO) { SmbPasswordStore.getInstance(context).setPassword(password) }
                        settings.setSmbConfig(normalized, username, domain)
                        settings.setSmbProtocol(protocol.id)
                    } else settings.setWebDavConfig(normalized, username, password, headers)
                    url = normalized; editing = false
                    mainViewModel.setLibrarySource(source, forceRefresh = true)
                }
                status = context.getString(R.string.file_library_connection_ok)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status = error.localizedMessage ?: context.getString(R.string.webdav_load_failed)
            } finally { busy = false }
        }
    }
    val background = ellaPageBackground()
    Column(Modifier.fillMaxSize().background(background).windowInsetsPadding(WindowInsets.statusBars)) {
        EllaSmallTopAppBar(title = title, color = background, navigationIcon = {
            IconButton(onClick = onBack) { Icon(MiuixIcons.Regular.Back, stringResource(R.string.common_back)) }
        })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)
            .navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.file_library_summary), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            SettingsCardGroup {
                ArrowPreference(title = stringResource(if (smb) R.string.smb_settings else R.string.webdav_settings),
                    summary = url.ifBlank { stringResource(R.string.home_connect_cloud_music) },
                    onClick = { if (!busy && loaded) editing = true })
            }
            if (smb && editing) {
                WebDavTextField(stringResource(R.string.smb_address), url, { url = it })
                WebDavTextField(stringResource(R.string.webdav_username), username, { username = it })
                WebDavTextField(stringResource(R.string.webdav_password), password, { password = it },
                    visualTransformation = PasswordVisualTransformation())
                WebDavTextField(stringResource(R.string.smb_domain), domain, { domain = it })
                EllaSpinnerPreference(title = stringResource(R.string.smb_protocol),
                    items = listOf(DropdownItem(stringResource(R.string.smb_protocol_auto)), DropdownItem("SMB 2"), DropdownItem("SMB 3")),
                    selectedIndex = SmbProtocol.entries.indexOf(protocol), onSelectedIndexChange = { protocol = SmbProtocol.entries[it] })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { testOrSave(false) }, enabled = !busy) { Text(stringResource(R.string.file_library_test)) }
                    Button(onClick = { testOrSave(true) }, enabled = !busy) { Text(stringResource(R.string.file_library_save_use)) }
                }
            }
            if (url.isNotBlank() && !editing) {
                Button(onClick = { testOrSave(true) }, enabled = loaded && !busy) { Text(stringResource(R.string.file_library_save_use)) }
            }
            if (activeSource == source) {
                Text(stringResource(R.string.file_library_song_count, songs.size))
                if (scanning) CircularProgressIndicator()
                Button(onClick = { mainViewModel.scanMusic(fullRescan = true) }, enabled = !scanning && !busy) {
                    Text(stringResource(R.string.file_library_refresh))
                }
            }
            status?.let { Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
            Button(onClick = onBrowse, enabled = url.isNotBlank() && loaded && !busy) { Text(stringResource(R.string.file_library_browse)) }
            Spacer(Modifier.height(120.dp))
        }
    }
    if (!smb && editing) WebDavSettingsDialog(url, username, password, headers,
        onUrlChange = { url = it }, onUsernameChange = { username = it }, onPasswordChange = { password = it },
        onCustomHeadersChange = { headers = it }, testStatus = status,
        onDismiss = { if (!busy) editing = false }, onTest = { testOrSave(false) }, onSave = { testOrSave(true) },
        onClear = { if (!busy) scope.launch { settings.clearWebDavConfig(); url = ""; editing = false } })
}
