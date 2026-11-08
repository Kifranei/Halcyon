package com.ella.music.ui.folder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.smb.*
import com.ella.music.data.webdav.WebDavItem
import com.ella.music.data.repository.RemoteAudioCache
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val smbAudioExtensions = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "aiff", "aif", "alac", "wma", "ape", "dsf", "dff")
internal fun SmbEntry.toSong(): Song = toLibrarySong()

@Composable
internal fun SmbScreen(mainViewModel: MainViewModel, playerViewModel: PlayerViewModel,
    onBack: () -> Unit, onNavigateToPlayer: () -> Unit) {
    val context = LocalContext.current
    val settings = mainViewModel.settingsManager
    val scope = rememberCoroutineScope()
    val savedUrl by settings.smbUrl.collectAsState(initial = "")
    val savedUser by settings.smbUsername.collectAsState(initial = "")
    val savedDomain by settings.smbDomain.collectAsState(initial = "")
    val savedProtocol by settings.smbProtocol.collectAsState(initial = "auto")
    val openPlayer by settings.openPlayerOnPlay.collectAsState(initial = false)
    var settingsVisible by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var password by remember { mutableStateOf(SmbPasswordStore.getInstance(context).password) }
    var protocol by remember { mutableStateOf(SmbProtocol.Auto) }
    var location by remember { mutableStateOf<SmbLocation?>(null) }
    var entries by remember { mutableStateOf<List<SmbEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(savedUrl, savedUser, savedDomain, savedProtocol) {
        url = savedUrl; username = savedUser; domain = savedDomain
        protocol = SmbProtocol.fromId(savedProtocol)
        location = runCatching { SmbLocation.parse(savedUrl) }.getOrNull()
    }
    val root = remember(savedUrl) { runCatching { SmbLocation.parse(savedUrl) }.getOrNull() }
    fun back() {
        val current = location
        if (root != null && current != null && current.segments.size > root.segments.size) location = current.copy(segments = current.segments.dropLast(1))
        else onBack()
    }
    BackHandler(enabled = root != null && location != root) { back() }
    LaunchedEffect(location, savedUser, savedDomain, savedProtocol, reload) {
        val current = location ?: return@LaunchedEffect
        loading = true; error = null; entries = emptyList()
        try {
            entries = withContext(Dispatchers.IO) {
                SmbSession(smbAccount(context)).use { it.list(current) }
            }.filter { it.directory || it.name.substringAfterLast('.', "").lowercase() in smbAudioExtensions }
            com.ella.music.data.AppLogStore.info(context, "SMB", "Directory listing succeeded; protocol=$savedProtocol; entries=${entries.size}", com.ella.music.data.AppLogType.NETWORK)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            val code = smbFailureCode(failure)
            val reason = when {
                code in setOf("STATUS_LOGON_FAILURE", "STATUS_WRONG_PASSWORD", "STATUS_ACCOUNT_DISABLED", "STATUS_ACCOUNT_RESTRICTION") -> R.string.smb_error_authentication
                code in setOf("STATUS_BAD_NETWORK_NAME", "STATUS_OBJECT_PATH_NOT_FOUND", "STATUS_OBJECT_NAME_NOT_FOUND") -> R.string.smb_error_share
                code == "STATUS_ACCESS_DENIED" -> R.string.smb_error_permission
                else -> R.string.smb_connection_failed
            }
            val detail = code ?: generateSequence<Throwable>(failure) { it.cause }.last().javaClass.simpleName
            error = context.getString(reason) + "\n$detail"
            com.ella.music.data.AppLogStore.warn(context, "SMB", "Directory listing failed; protocol=$savedProtocol; stage=${(failure as? SmbConnectionException)?.stage ?: "list"}", failure, com.ella.music.data.AppLogType.NETWORK)
        } finally { loading = false }
    }
    val songs = remember(entries) { entries.filterNot { it.directory }.map { it.toSong() } }
    Column(Modifier.fillMaxSize().background(ellaPageBackground()).windowInsetsPadding(WindowInsets.statusBars)) {
        EllaSmallTopAppBar(title = stringResource(R.string.smb_title), color = ellaPageBackground(),
            navigationIcon = { IconButton(onClick = { back() }) { Icon(MiuixIcons.Regular.Back, stringResource(R.string.common_back)) } },
            actions = { IconButton(onClick = { settingsVisible = !settingsVisible }) { Icon(MiuixIcons.Regular.Settings, stringResource(R.string.smb_settings)) } })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
            if (settingsVisible || savedUrl.isBlank()) item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.smb_address_summary), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    WebDavTextField(label = stringResource(R.string.smb_address), value = url, onValueChange = { url = it })
                    WebDavTextField(label = stringResource(R.string.webdav_username), value = username, onValueChange = { username = it })
                    WebDavTextField(label = stringResource(R.string.webdav_password), value = password, onValueChange = { password = it }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    WebDavTextField(label = stringResource(R.string.smb_domain), value = domain, onValueChange = { domain = it })
                    com.ella.music.ui.components.EllaSpinnerPreference(
                        title = stringResource(R.string.smb_protocol),
                        items = listOf(DropdownItem(stringResource(R.string.smb_protocol_auto)), DropdownItem("SMB 2"), DropdownItem("SMB 3")),
                        selectedIndex = protocol.ordinal,
                        onSelectedIndexChange = { protocol = SmbProtocol.entries[it] }
                    )
                    Button(onClick = { scope.launch {
                        try {
                            val normalized = SmbLocation.parse(url).url()
                            withContext(Dispatchers.IO) { SmbPasswordStore.getInstance(context).setPassword(password) }
                            settings.setSmbConfig(normalized, username, domain)
                            settings.setSmbProtocol(protocol.id)
                            settingsVisible = false; reload++
                        } catch (failure: Exception) {
                            if (failure is CancellationException) throw failure
                            error = context.getString(R.string.smb_invalid_settings)
                        }
                    } }) { Text(stringResource(R.string.smb_connect)) }
                }
            }
            item {
                location?.let { Text(it.url(), modifier = Modifier.padding(16.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                if (loading) EllaCenteredLoadingIndicator()
                error?.let { message -> Column(Modifier.padding(16.dp)) {
                    Text(message, color = MiuixTheme.colorScheme.error)
                    Button(onClick = { reload++ }) { Text(stringResource(R.string.smb_retry)) }
                } }
                if (!loading && location != null && entries.isEmpty() && error == null) Text(stringResource(R.string.smb_empty), Modifier.padding(16.dp))
            }
            items(entries, key = { it.location.url() }) { entry ->
                WebDavItemRow(item = WebDavItem(entry.name, entry.location.url(), entry.directory, entry.bytes),
                    onClick = {
                        if (entry.directory) location = entry.location else {
                            val index = songs.indexOfFirst { it.path == entry.location.url() }
                            if (index >= 0) { playerViewModel.setPlaylist(songs, index); if (openPlayer) onNavigateToPlayer() }
                        }
                    },
                    onAddToQueue = { if (!entry.directory) playerViewModel.addToPlaylist(entry.toSong()) },
                    onLongClick = { if (!entry.directory) scope.launch { mainViewModel.addSongsToPlaylist(FAVORITES_PLAYLIST_ID, listOf(entry.toSong())) } })
            }
            if (songs.isNotEmpty()) item {
                Button(onClick = { RemoteAudioCache.cacheSongs(songs, scope) }, modifier = Modifier.padding(16.dp)) { Text(stringResource(R.string.smb_cache_folder)) }
                val progress by RemoteAudioCache.progress.collectAsState()
                if (progress.active || progress.completed > 0 || progress.failed > 0) {
                    Text(stringResource(R.string.smb_cache_progress, progress.completed, progress.total, progress.failed), Modifier.padding(horizontal = 16.dp))
                    if (progress.active) Button(onClick = RemoteAudioCache::cancel, modifier = Modifier.padding(16.dp)) { Text(stringResource(R.string.common_cancel)) }
                }
            }
        }
    }
}
