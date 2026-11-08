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
import com.ella.music.data.ftp.*
import com.ella.music.data.smb.SmbPasswordStore
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.folder.WebDavTextField
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import com.ella.music.ui.components.GlassChevronBackwardIcon
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TransferLibrarySettingsScreen(source: String, mainViewModel: MainViewModel, onBack: () -> Unit) {
    require(source in setOf("ftp", "sftp"))
    val sftp = source == "sftp"
    val context = LocalContext.current
    val settings = mainViewModel.settingsManager
    val scope = rememberCoroutineScope()
    val activeSource by settings.librarySource.collectAsStateSeeded(initial = "")
    val songs by mainViewModel.songs.collectAsState()
    val scanning by mainViewModel.isScanning.collectAsState()
    var url by remember(source) { mutableStateOf("") }
    var username by remember(source) { mutableStateOf("") }
    var password by remember(source) { mutableStateOf("") }
    var hostKey by remember(source) { mutableStateOf("") }
    var loaded by remember(source) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingKey by remember { mutableStateOf<SftpHostKeyException?>(null) }
    var trustedAddress by remember(source) { mutableStateOf("") }
    LaunchedEffect(source) {
        val config = settings.transferLibraryConfig(source).first()
        url = config.url; username = config.username; hostKey = config.hostKey
        trustedAddress = config.url
        password = withContext(Dispatchers.IO) { SmbPasswordStore.getInstance(context, source).password }
        loaded = true
    }
    fun connect(save: Boolean) {
        if (!loaded || busy) return
        val draftUrl = url
        val draftUsername = username
        val draftPassword = password
        val draftKey = hostKey.takeIf { draftUrl == trustedAddress }.orEmpty()
        busy = true; status = null; pendingKey = null
        scope.launch {
            try {
                val location = TransferLocation.parse(draftUrl)
                require(sftp == (location.scheme == "sftp")) { context.getString(R.string.transfer_library_address_mismatch) }
                withContext(Dispatchers.IO) { transferSession(TransferAccount(location, draftUsername, draftPassword, draftKey)).use { it.list(location) } }
                if (save) {
                    withContext(Dispatchers.IO) { SmbPasswordStore.getInstance(context, source).setPassword(draftPassword) }
                    val normalized = location.url()
                    settings.setTransferLibraryConfig(source, TransferLibraryConfig(normalized, draftUsername, draftKey))
                    url = normalized; trustedAddress = normalized
                    mainViewModel.setLibrarySource(source, forceRefresh = true)
                }
                status = context.getString(R.string.file_library_connection_ok)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: SftpHostKeyException) {
                pendingKey = error
                trustedAddress = draftUrl
            } catch (error: Exception) {
                status = error.localizedMessage ?: context.getString(R.string.webdav_load_failed)
            } finally { busy = false }
        }
    }
    val background = ellaPageBackground()
    val settingsBackdrop = rememberLayerBackdrop()
    Column(Modifier.fillMaxSize().background(background)) {
        EllaSmallTopAppBar(title = stringResource(if (sftp) R.string.sftp_library_title else R.string.ftp_library_title), color = background, backdrop = settingsBackdrop,
            navigationIcon = { IconButton(onClick = onBack) { Icon(GlassChevronBackwardIcon, stringResource(R.string.common_back)) } })
        Column(Modifier.weight(1f).layerBackdrop(settingsBackdrop).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.file_library_summary), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            WebDavTextField(stringResource(R.string.transfer_library_address), url, {
                if (!busy) { url = it; pendingKey = null }
            })
            Text(if (sftp) "sftp://server:22/music" else "ftp://server:21/music · ftps://server:21/music",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            WebDavTextField(stringResource(R.string.webdav_username), username, { if (!busy) username = it })
            WebDavTextField(stringResource(R.string.webdav_password), password, { if (!busy) password = it },
                visualTransformation = PasswordVisualTransformation())
            if (!sftp) Text(stringResource(R.string.ftp_library_security_summary), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            if (sftp && hostKey.isNotBlank() && url == trustedAddress) Text(hostKey)
            pendingKey?.let { pending ->
                Text(stringResource(if (pending.changed) R.string.sftp_host_key_changed else R.string.sftp_host_key_confirm,
                    pending.host, pending.fingerprint))
                Button(onClick = { hostKey = pending.fingerprint; pendingKey = null }, enabled = !busy) {
                    Text(stringResource(R.string.sftp_host_key_trust))
                }
            }
            Button(onClick = { connect(false) }, enabled = loaded && !busy) { Text(stringResource(R.string.file_library_test)) }
            Button(onClick = { connect(true) }, enabled = loaded && !busy) { Text(stringResource(R.string.file_library_save_use)) }
            if (busy) CircularProgressIndicator()
            status?.let { Text(it) }
            if (activeSource == source) {
                Text(stringResource(R.string.file_library_song_count, songs.size))
                if (scanning) CircularProgressIndicator()
                Button(onClick = { mainViewModel.scanMusic(fullRescan = true) }, enabled = !scanning && !busy) {
                    Text(stringResource(R.string.file_library_refresh))
                }
            }
            Spacer(Modifier.height(120.dp))
        }
    }
}
