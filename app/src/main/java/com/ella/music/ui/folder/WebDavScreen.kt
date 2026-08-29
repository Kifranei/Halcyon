package com.ella.music.ui.folder

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.AppLogStore
import com.ella.music.data.AppLogType
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavConfig
import com.ella.music.data.webdav.WebDavItem
import com.ella.music.data.webdav.WebDavTestResult
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun WebDavScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedUrl by mainViewModel.settingsManager.webDavUrl.collectAsState(initial = "")
    val savedUser by mainViewModel.settingsManager.webDavUsername.collectAsState(initial = "")
    val savedPassword by mainViewModel.settingsManager.webDavPassword.collectAsState(initial = "")
    val savedLastUrl by mainViewModel.settingsManager.webDavLastUrl.collectAsState(initial = "")
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = false)

    var showSettings by remember { mutableStateOf(false) }
    var webDavUrl by remember { mutableStateOf("") }
    var webDavUser by remember { mutableStateOf("") }
    var webDavPassword by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<WebDavItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var loadedKey by remember { mutableStateOf("") }
    var loadRequestId by remember { mutableStateOf(0) }
    var folderMenuTarget by remember { mutableStateOf<WebDavItem?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    val playlists by mainViewModel.playlists.collectAsState()

    fun logWebDavError(action: String, error: Throwable) {
        AppLogStore.error(
            context,
            "WebDavScreen",
            "$action: ${error.localizedMessage ?: error.javaClass.name}",
            error,
            AppLogType.NETWORK
        )
    }

    fun load(url: String, forceRefresh: Boolean = false) {
        val requestId = ++loadRequestId
        scope.launch {
            val config = WebDavConfig(webDavUrl, webDavUser, webDavPassword)
            loading = true
            error = null
            items = emptyList()
            if (forceRefresh) WebDavClient.clearListCache()
            runCatching {
                withContext(Dispatchers.IO) {
                    WebDavClient.listBatched(
                        config = config,
                        url = url,
                        forceRefresh = forceRefresh,
                        onBatch = { batch ->
                            // listBatched parses on IO. Hop back to the Compose scope before
                            // publishing each cumulative snapshot, and ignore an older refresh
                            // if the user has already navigated or refreshed again.
                            scope.launch {
                                if (requestId == loadRequestId) items = batch
                            }
                        }
                    )
                }
            }.onSuccess {
                if (requestId == loadRequestId) items = it
            }.onFailure {
                if (requestId == loadRequestId) {
                    items = emptyList()
                    error = it.localizedMessage ?: context.getString(R.string.webdav_load_failed)
                    logWebDavError("Load failed", it)
                }
            }
            if (requestId == loadRequestId) {
                loading = false
            }
        }
    }

    suspend fun collectFolderSongs(folder: WebDavItem): List<Song> {
        val config = WebDavConfig(webDavUrl, webDavUser, webDavPassword)
        val items = withContext(Dispatchers.IO) {
            WebDavClient.listAudioRecursive(config, folder.url)
        }
        return withContext(Dispatchers.IO) {
            items.map { item -> mainViewModel.resolveSongForPlayback(item.toRemoteSong()) }
        }
    }

    fun goParent() {
        val rootUrl = webDavUrl.trimEnd('/')
        val current = currentUrl.ifBlank { rootUrl }.trimEnd('/')
        val parent = parentWebDavUrl(current, rootUrl) ?: return
        currentUrl = parent
        searchQuery = ""
        scope.launch { mainViewModel.settingsManager.setWebDavLastUrl(parent) }
        load(parent)
    }

    LaunchedEffect(savedUrl, savedUser, savedPassword, savedLastUrl) {
        webDavUrl = savedUrl
        webDavUser = savedUser
        webDavPassword = savedPassword
        if (savedUrl.isBlank()) {
            currentUrl = ""
            items = emptyList()
            searchQuery = ""
            error = null
            return@LaunchedEffect
        }
        val startUrl = savedLastUrl.ifBlank { savedUrl }
        val key = listOf(savedUrl, savedUser, savedPassword, startUrl).joinToString("|")
        if (loadedKey == key && items.isNotEmpty()) return@LaunchedEffect
        loadedKey = key
        currentUrl = startUrl
        load(startUrl)
    }

    LaunchedEffect(currentUrl, items, savedUrl, savedUser, savedPassword) {
        if (loading || savedUrl.isBlank() || items.isEmpty()) return@LaunchedEffect
        val songsToPrefetch = items
            .asSequence()
            .filterNot { it.isDirectory }
            .filter { WebDavClient.isAudioFile(it.name) }
            .map { it.toRemoteSong() }
            .take(80)
            .toList()
        if (songsToPrefetch.isEmpty()) return@LaunchedEffect
        runCatching {
            mainViewModel.prefetchWebDavMetadataHeaders(songsToPrefetch, maxItems = 80)
        }.onFailure {
            logWebDavError("Metadata prefetch failed", it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.webdav_library_title),
            color = ellaPageBackground(),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Settings,
                        contentDescription = stringResource(R.string.webdav_settings),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        if (showSettings) {
            WebDavSettingsDialog(
                url = webDavUrl,
                username = webDavUser,
                password = webDavPassword,
                onUrlChange = { webDavUrl = it },
                onUsernameChange = { webDavUser = it },
                onPasswordChange = { webDavPassword = it },
                testStatus = testStatus,
                onDismiss = { showSettings = false },
                onTest = {
                    scope.launch {
                        testStatus = context.getString(R.string.webdav_testing)
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                WebDavClient.testDetailed(WebDavConfig(webDavUrl, webDavUser, webDavPassword))
                            }
                        }.getOrElse {
                            logWebDavError("Connection test failed", it)
                            WebDavTestResult(ok = false, message = it.localizedMessage ?: context.getString(R.string.webdav_connection_failed))
                        }
                        testStatus = result.message
                        error = if (result.ok) null else result.message
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }
                },
                onSave = {
                    scope.launch {
                        mainViewModel.settingsManager.setWebDavConfig(webDavUrl, webDavUser, webDavPassword)
                    }
                    currentUrl = webDavUrl
                    searchQuery = ""
                    showSettings = false
                    load(webDavUrl, forceRefresh = true)
                    Toast.makeText(context, R.string.webdav_config_saved, Toast.LENGTH_SHORT).show()
                },
                onClear = {
                    scope.launch { mainViewModel.settingsManager.clearWebDavConfig() }
                    webDavUrl = ""
                    webDavUser = ""
                    webDavPassword = ""
                    currentUrl = ""
                    items = emptyList()
                    searchQuery = ""
                    error = null
                    testStatus = null
                    showSettings = false
                    Toast.makeText(context, R.string.webdav_config_removed, Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (savedUrl.isBlank() && items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Folder,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Text(
                        text = stringResource(R.string.webdav_configure_first),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp)
            ) {
                item {
                    WebDavBrowserCard(
                        currentUrl = WebDavClient.displayUrl(currentUrl.ifBlank { savedUrl }),
                        canGoParent = parentWebDavUrl(
                            currentUrl.ifBlank { webDavUrl }.trimEnd('/'),
                            webDavUrl.trimEnd('/')
                        ) != null,
                        loading = loading,
                        error = error,
                        onGoParent = ::goParent,
                        remoteItems = items,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onRefresh = { load(currentUrl.ifBlank { webDavUrl }, forceRefresh = true) },
                        onItemClick = { item ->
                            if (item.isDirectory) {
                                currentUrl = item.url
                                searchQuery = ""
                                scope.launch { mainViewModel.settingsManager.setWebDavLastUrl(item.url) }
                                load(item.url)
                            } else {
                                scope.launch {
                                    runCatching {
                                        val resolvedSong = withContext(Dispatchers.IO) {
                                            mainViewModel.resolveSongForPlayback(item.toRemoteSong())
                                        }
                                        playerViewModel.setPlaylist(listOf(resolvedSong), 0)
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }.onFailure {
                                        logWebDavError("Play remote item failed", it)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.webdav_load_failed) + ": " + (it.localizedMessage ?: ""),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        },
                        onItemLongClick = { item ->
                            if (item.isDirectory) {
                                folderMenuTarget = item
                            } else {
                                scope.launch {
                                    runCatching {
                                        val resolvedSong = withContext(Dispatchers.IO) {
                                            mainViewModel.resolveSongForPlayback(item.toRemoteSong())
                                        }
                                        playlistPickerSongs = listOf(resolvedSong)
                                    }.onFailure {
                                        logWebDavError("Collect remote item failed", it)
                                    }
                                }
                            }
                        },
                        onAddToQueue = { item ->
                            scope.launch {
                                runCatching {
                                    val resolvedSong = withContext(Dispatchers.IO) {
                                        mainViewModel.resolveSongForPlayback(item.toRemoteSong())
                                    }
                                    playerViewModel.addToPlaylist(resolvedSong)
                                    Toast.makeText(context, R.string.webdav_added_to_queue, Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    logWebDavError("Add remote item to queue failed", it)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.webdav_load_failed) + ": " + (it.localizedMessage ?: ""),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    folderMenuTarget?.let { folder ->
        WebDavFolderActionSheet(
            title = folder.name,
            onDismiss = { folderMenuTarget = null },
            onFavorite = {
                val target = folder
                folderMenuTarget = null
                scope.launch {
                    Toast.makeText(context, R.string.webdav_folder_collecting, Toast.LENGTH_SHORT).show()
                    runCatching {
                        val songs = collectFolderSongs(target)
                        if (songs.isEmpty()) {
                            Toast.makeText(context, R.string.webdav_folder_collect_empty, Toast.LENGTH_SHORT).show()
                        } else {
                            mainViewModel.addSongsToPlaylist(FAVORITES_PLAYLIST_ID, songs)
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_folder_favorited, songs.size),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }.onFailure {
                        logWebDavError("Favorite folder failed", it)
                        Toast.makeText(context, it.localizedMessage ?: context.getString(R.string.webdav_load_failed), Toast.LENGTH_LONG).show()
                    }
                }
            },
            onCreatePlaylist = {
                val target = folder
                folderMenuTarget = null
                scope.launch {
                    Toast.makeText(context, R.string.webdav_folder_collecting, Toast.LENGTH_SHORT).show()
                    runCatching {
                        val songs = collectFolderSongs(target)
                        if (songs.isEmpty()) {
                            Toast.makeText(context, R.string.webdav_folder_collect_empty, Toast.LENGTH_SHORT).show()
                        } else {
                            createPlaylistSongs = songs
                        }
                    }.onFailure {
                        logWebDavError("Create playlist from folder failed", it)
                        Toast.makeText(context, it.localizedMessage ?: context.getString(R.string.webdav_load_failed), Toast.LENGTH_LONG).show()
                    }
                }
            },
            onAddToPlaylist = {
                val target = folder
                folderMenuTarget = null
                scope.launch {
                    Toast.makeText(context, R.string.webdav_folder_collecting, Toast.LENGTH_SHORT).show()
                    runCatching {
                        val songs = collectFolderSongs(target)
                        if (songs.isEmpty()) {
                            Toast.makeText(context, R.string.webdav_folder_collect_empty, Toast.LENGTH_SHORT).show()
                        } else {
                            playlistPickerSongs = songs
                        }
                    }.onFailure {
                        logWebDavError("Add folder to playlist failed", it)
                        Toast.makeText(context, it.localizedMessage ?: context.getString(R.string.webdav_load_failed), Toast.LENGTH_LONG).show()
                    }
                }
            },
            onAddToQueue = {
                val target = folder
                folderMenuTarget = null
                scope.launch {
                    Toast.makeText(context, R.string.webdav_folder_collecting, Toast.LENGTH_SHORT).show()
                    runCatching {
                        val songs = collectFolderSongs(target)
                        if (songs.isEmpty()) {
                            Toast.makeText(context, R.string.webdav_folder_collect_empty, Toast.LENGTH_SHORT).show()
                        } else {
                            playerViewModel.addToPlaylist(songs)
                            Toast.makeText(context, R.string.webdav_added_to_queue, Toast.LENGTH_SHORT).show()
                        }
                    }.onFailure {
                        logWebDavError("Add folder to queue failed", it)
                        Toast.makeText(context, it.localizedMessage ?: context.getString(R.string.webdav_load_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    playlistPickerSongs?.let { songsToAdd ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_add_to_playlist_title),
            onDismissRequest = { playlistPickerSongs = null }
        ) {
            AddToPlaylistSheet(
                playlists = playlists,
                songsToAdd = songsToAdd,
                songCount = songsToAdd.size,
                onDismiss = { playlistPickerSongs = null },
                onCreatePlaylist = {
                    createPlaylistSongs = songsToAdd
                    playlistPickerSongs = null
                },
                onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                    selectedPlaylists.forEach { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd, appendToEnd)
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlists, selectedPlaylists.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    playlistPickerSongs = null
                }
            )
        }
    }

    createPlaylistSongs?.let { songsToAdd ->
        CreatePlaylistAndAddSheet(
            onDismiss = { createPlaylistSongs = null },
            onCreate = { name ->
                mainViewModel.createPlaylistOrShowDuplicateToast(context, name) { playlist ->
                    mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                    Toast.makeText(
                        context,
                        context.getString(R.string.webdav_folder_playlist_created, name, songsToAdd.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    createPlaylistSongs = null
                }
            }
        )
    }
}

private fun parentWebDavUrl(currentUrl: String, rootUrl: String): String? {
    if (currentUrl.isBlank() || rootUrl.isBlank()) return null
    val root = rootUrl.trimEnd('/')
    val current = currentUrl.trimEnd('/')
    if (current == root || !current.startsWith(root)) return null
    return runCatching {
        val uri = URI(current)
        val path = uri.path.orEmpty().trimEnd('/')
        val parentPath = path.substringBeforeLast('/', missingDelimiterValue = "")
        if (parentPath.isBlank()) root else {
            val rebuilt = URI(uri.scheme, uri.userInfo, uri.host, uri.port, parentPath + "/", uri.query, uri.fragment).toString()
            if (rebuilt.length < root.length) root else rebuilt
        }
    }.getOrNull()?.trimEnd('/')?.coerceAtLeastUrl(root)
}

private fun String.coerceAtLeastUrl(rootUrl: String): String =
    if (length < rootUrl.length || !startsWith(rootUrl)) rootUrl else this
