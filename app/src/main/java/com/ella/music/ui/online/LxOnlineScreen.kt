package com.ella.music.ui.online

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.basic.CardDefaults
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.lx.LxOnlineService
import com.ella.music.data.lx.LxOnlineSong
import com.ella.music.data.lx.LxSearchPlatform
import com.ella.music.data.model.Song
import com.ella.music.data.sanitizeExportFileName
import com.ella.music.data.remote.EmbyService
import com.ella.music.data.remote.NavidromeService
import com.ella.music.data.remote.RemoteMusicProvider
import com.ella.music.data.remote.RemoteMusicSourceConfig
import com.ella.music.data.remote.RemoteOnlineSong
import com.ella.music.data.remote.isSubsonicLike
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.LxOnlineViewModel
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaSmallTopAppBar
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LxOnlineScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    providerOverride: RemoteMusicProvider? = null,
    titleOverride: String? = null,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToSourceSettings: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    state: LxOnlineViewModel = viewModel()
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val service = remember(context) { LxOnlineService(context) }
    val navidromeService = remember(context) { NavidromeService(context) }
    val embyService = remember(context) { EmbyService(context) }
    val scope = rememberCoroutineScope()

    val selectedProviderSetting by settingsManager.selectedOnlineProvider.collectAsState(initial = RemoteMusicProvider.Lx)
    val selectedProvider = providerOverride ?: selectedProviderSetting
    val navidromeConfig by settingsManager.navidromeConfig.collectAsState(
        initial = RemoteMusicSourceConfig(RemoteMusicProvider.Navidrome, "")
    )
    val openSubsonicConfig by settingsManager.openSubsonicConfig.collectAsState(
        initial = RemoteMusicSourceConfig(RemoteMusicProvider.OpenSubsonic, "")
    )
    val embyConfig by settingsManager.embyConfig.collectAsState(
        initial = RemoteMusicSourceConfig(RemoteMusicProvider.Emby, "")
    )
    val loadedSources by settingsManager.lxSources.collectAsState(initial = null)
    val sources = loadedSources.orEmpty()
    val selectedSourceId by settingsManager.selectedLxSourceId.collectAsState(initial = "")
    val selectedSource = remember(sources, selectedSourceId) {
        sources.firstOrNull { it.id == selectedSourceId } ?: sources.firstOrNull()
    }
    var declaredPlatforms by remember(selectedSource?.script) { mutableStateOf<Set<String>?>(null) }
    var platformDetectionError by remember(selectedSource?.script) { mutableStateOf(false) }
    val availablePlatforms = LxSearchPlatform.entries.filter {
        declaredPlatforms == null || it.declaredSourceKey(declaredPlatforms.orEmpty()) != null
    }
    val unsupportedPlatforms = declaredPlatforms.orEmpty() - LxSearchPlatform.entries.flatMap { it.sourceKeys }.toSet()
    LaunchedEffect(selectedSource?.script) {
        val config = selectedSource ?: return@LaunchedEffect
        try {
            declaredPlatforms = service.supportedSources(config)
            val detected = LxSearchPlatform.entries.filter { it.declaredSourceKey(declaredPlatforms.orEmpty()) != null }
            if (state.searchPlatform !in detected) {
                detected.firstOrNull()?.let { state.searchPlatform = it }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            platformDetectionError = true
        }
    }
    val openPlayerOnPlay by settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNextInLists by settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val currentSourceId = selectedSource?.id.orEmpty()
    val selectedLxSearchPlatform by settingsManager.selectedLxSearchPlatform.collectAsState(initial = "")
    var hasInitializedPlatform by remember { mutableStateOf(false) }
    LaunchedEffect(selectedLxSearchPlatform) {
        if (!hasInitializedPlatform && selectedLxSearchPlatform.isNotBlank()) {
            val matched = LxSearchPlatform.entries.firstOrNull {
                selectedLxSearchPlatform in it.sourceKeys || it.name.equals(selectedLxSearchPlatform, ignoreCase = true)
            }
            if (matched != null) {
                state.searchPlatform = matched
            }
            hasInitializedPlatform = true
        }
    }
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val resultsListState = rememberLazyListState()
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { searchJob?.cancel() } }
    var actionItem by remember { mutableStateOf<LxOnlineSong?>(null) }
    var remoteResults by remember { mutableStateOf<List<RemoteOnlineSong>>(emptyList()) }
    var remoteActionItem by remember { mutableStateOf<RemoteOnlineSong?>(null) }
    LaunchedEffect(currentSourceId, selectedProvider, loadedSources) {
        if (selectedProvider == RemoteMusicProvider.Lx && loadedSources == null) return@LaunchedEffect
        val previousSourceId = state.observedSourceId
        val marker = "${selectedProvider.id}:$currentSourceId"
        if (previousSourceId != null && previousSourceId != marker) {
            state.searchRequests.invalidate()
            searchJob?.cancel()
            state.isBusy = false
            state.clearResults()
            remoteResults = emptyList()
        }
        state.observedSourceId = marker
    }

    val remoteConfig = when (selectedProvider) {
        RemoteMusicProvider.Navidrome -> navidromeConfig
        RemoteMusicProvider.OpenSubsonic -> openSubsonicConfig
        RemoteMusicProvider.Emby -> embyConfig
        RemoteMusicProvider.Lx -> null
    }
    val remoteConfigured = selectedProvider == RemoteMusicProvider.Lx || remoteConfig?.isConfigured == true

    fun showToast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun searchSelectedProvider(automatic: Boolean = false, loadMore: Boolean = false) {
        if (loadMore && (state.isBusy || selectedProvider != RemoteMusicProvider.Lx)) return
        if (selectedProvider == RemoteMusicProvider.Lx && state.searchPlatform !in availablePlatforms) {
            showToast(context.getString(R.string.lx_no_supported_search_platform))
            return
        }
        if (!remoteConfigured || selectedProvider == RemoteMusicProvider.Lx && selectedSource == null) {
            showToast(context.getString(R.string.remote_source_configure_first))
            return
        }
        val request = (if (loadMore) state.searchRequests.nextPage(automatic)
            else state.searchRequests.request(state.searchQuery, automatic)) ?: return
        val platform = state.searchPlatform
        val platformSources = declaredPlatforms
        val source = selectedSource
        val provider = selectedProvider
        val config = remoteConfig
        searchJob?.cancel()
        if (!loadMore) {
            state.clearResults()
            remoteResults = emptyList()
        }
        searchJob = scope.launch {
            if (!loadMore) state.isBusy = true
            try {
                if (provider == RemoteMusicProvider.Lx) {
                    val found = service.search(request.query, source, page = request.page, platform = platform,
                        declaredSources = platformSources)
                    if (state.searchRequests.isCurrent(request)) {
                        val previousCount = state.results.size
                        state.results = appendDistinctSearchResults(state.results, found) { it.song.id }
                        state.searchRequests.complete(request, state.results.size - previousCount,
                            isEnd = if (platform.supportsNameSearch) null else true)
                        remoteResults = emptyList()
                        state.message = if (state.results.isEmpty()) context.getString(R.string.lx_online_no_songs_found)
                            else context.getString(R.string.lx_online_songs_found, state.results.size)
                    }
                } else {
                    val activeConfig = config ?: error(context.getString(R.string.remote_source_configure_first))
                    val found = when (provider) {
                        RemoteMusicProvider.Navidrome, RemoteMusicProvider.OpenSubsonic -> navidromeService.search(request.query, activeConfig)
                        RemoteMusicProvider.Emby -> embyService.search(request.query, activeConfig)
                        RemoteMusicProvider.Lx -> emptyList()
                    }
                    if (state.searchRequests.isCurrent(request)) {
                        remoteResults = found
                        state.results = emptyList()
                        state.searchRequests.complete(request, found.size, isEnd = true)
                        state.message = if (found.isEmpty()) context.getString(R.string.lx_online_no_songs_found)
                            else context.getString(R.string.lx_online_songs_found, found.size)
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (state.searchRequests.isCurrent(request)) {
                    state.searchRequests.fail(request)
                    state.message = error.localizedMessage ?: context.getString(R.string.lx_online_search_failed)
                    showToast(state.message)
                }
            } finally {
                state.searchRequests.cancel(request)
                if (!loadMore && state.searchRequests.isCurrent(request)) state.isBusy = false
            }
        }
    }

    suspend fun playLazyOnlineQueue(startItem: LxOnlineSong) {
        val visible = state.results.ifEmpty { listOf(startItem) }
        val startIndex = visible.indexOfFirst { it.song.id == startItem.song.id }.coerceAtLeast(0)
        val sourceScript = selectedSource?.script.orEmpty()
        val resolved = service.resolvePlayableSong(startItem, sourceScript)
        val songs = visible.map { it.song }
        val itemById = visible.associateBy { it.song.id }
        playerViewModel.setLazyOnlinePlaylist(
            songs = songs,
            startIndex = startIndex,
            resolvedStartSong = resolved
        ) { song ->
            val target = itemById[song.id] ?: error(context.getString(R.string.lx_online_queue_song_expired))
            service.resolvePlayableSong(target, sourceScript)
        }
        state.message = context.getString(R.string.lx_online_queue_obtained, songs.size)
    }

    suspend fun resolveActionSong(song: Song): Song {
        remoteActionItem?.takeIf { it.song.id == song.id }?.let { item ->
            return if (item.provider.isSubsonicLike) {
                navidromeService.resolvePlayableSong(item)
            } else when (item.provider) {
                RemoteMusicProvider.Emby -> embyService.resolvePlayableSong(item)
                RemoteMusicProvider.Lx -> item.song
                RemoteMusicProvider.Navidrome,
                RemoteMusicProvider.OpenSubsonic -> error("Unexpected Subsonic-like provider branch")
            }
        }
        val item = actionItem?.takeIf { it.song.id == song.id }
            ?: state.results.firstOrNull { it.song.id == song.id }
            ?: error(context.getString(R.string.lx_online_song_expired))
        return service.resolvePlayableSong(item, selectedSource?.script.orEmpty())
    }

    suspend fun resolveActionRemoteSong(item: RemoteOnlineSong): Song {
        return if (item.provider.isSubsonicLike) {
            navidromeService.resolvePlayableSong(item)
        } else when (item.provider) {
            RemoteMusicProvider.Emby -> embyService.resolvePlayableSong(item)
            RemoteMusicProvider.Lx -> item.song
            RemoteMusicProvider.Navidrome,
            RemoteMusicProvider.OpenSubsonic -> error("Unexpected Subsonic-like provider branch")
        }
    }

    fun resolveRemoteDownloadSong(item: RemoteOnlineSong): Song {
        val sourceConfig = when (item.provider) {
            RemoteMusicProvider.Navidrome -> navidromeConfig
            RemoteMusicProvider.OpenSubsonic -> openSubsonicConfig
            else -> return if (item.provider == RemoteMusicProvider.Emby) {
                embyService.resolvePlayableSong(item)
            } else {
                item.song
            }
        }
        return navidromeService.resolveDownloadableSong(item, sourceConfig)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = titleOverride ?: selectedProvider.displayName(context),
            color = Color.Transparent,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            actions = {
                IconButton(onClick = onNavigateToSourceSettings) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Settings,
                        contentDescription = stringResource(R.string.lx_online_source_management),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        val sourceCardColor = onlineSourceCardColor()

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.padding(vertical = 4.dp),
                colors = CardDefaults.defaultColors(color = sourceCardColor),
                onClick = onNavigateToSourceSettings
            ) {
                BasicComponent(
                    title = when (selectedProvider) {
                        RemoteMusicProvider.Lx -> selectedSource?.name ?: stringResource(R.string.lx_online_no_source_selected)
                        RemoteMusicProvider.Navidrome -> stringResource(R.string.remote_source_navidrome)
                        RemoteMusicProvider.OpenSubsonic -> stringResource(R.string.remote_source_opensubsonic)
                        RemoteMusicProvider.Emby -> stringResource(R.string.remote_source_emby)
                    },
                    summary = when (selectedProvider) {
                        RemoteMusicProvider.Lx -> selectedSource?.url ?: stringResource(R.string.lx_online_no_source_hint)
                        RemoteMusicProvider.Navidrome -> navidromeConfig.baseUrl.ifBlank { stringResource(R.string.remote_source_not_configured) }
                        RemoteMusicProvider.OpenSubsonic -> openSubsonicConfig.baseUrl.ifBlank { stringResource(R.string.remote_source_not_configured) }
                        RemoteMusicProvider.Emby -> embyConfig.serverName.ifBlank { embyConfig.baseUrl }.ifBlank { stringResource(R.string.remote_source_not_configured) }
                    },
                )
            }

            if (selectedProvider == RemoteMusicProvider.Lx && (unsupportedPlatforms.isNotEmpty() || platformDetectionError)) {
                Text(
                    text = if (platformDetectionError) stringResource(R.string.lx_platform_detection_failed)
                    else stringResource(R.string.lx_platforms_unavailable, unsupportedPlatforms.joinToString(", ")),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            OnlineSearchControls(
                providers = if (selectedProvider == RemoteMusicProvider.Lx) availablePlatforms.map { it.displayName } else emptyList(),
                selectedIndex = availablePlatforms.indexOf(state.searchPlatform),
                onProviderSelected = { index ->
                    availablePlatforms.getOrNull(index)?.let { platform ->
                        if (state.searchPlatform != platform) {
                            state.searchPlatform = platform
                            state.searchRequests.invalidate()
                            searchJob?.cancel()
                            state.isBusy = false
                            state.clearResults()
                            remoteResults = emptyList()
                            scope.launch { settingsManager.setSelectedLxSearchPlatform(platform.source) }
                            if (platform.supportsNameSearch) searchSelectedProvider(automatic = true)
                        }
                    }
                },
                query = state.searchQuery,
                onQueryChange = { state.searchQuery = it },
                onSearch = { searchSelectedProvider() },
                placeholder = stringResource(
                    if (selectedProvider == RemoteMusicProvider.Lx && !state.searchPlatform.supportsNameSearch)
                        R.string.lx_qishui_input_placeholder else R.string.lx_online_search_placeholder
                )
            )

            val statusMessage = if (state.isBusy) stringResource(R.string.lx_online_processing) else state.message
            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }

            val showingRemote = selectedProvider != RemoteMusicProvider.Lx
            if ((!showingRemote && state.results.isEmpty()) || (showingRemote && remoteResults.isEmpty())) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(when {
                            !remoteConfigured -> R.string.remote_source_configure_first
                            !showingRemote && !state.searchPlatform.supportsNameSearch -> R.string.lx_qishui_input_hint
                            else -> R.string.lx_online_search_hint
                        }),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            } else if (showingRemote) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(remoteResults, key = { it.song.id }) { item ->
                        SongItem(
                            song = item.song,
                            albumArtUri = item.coverUrl.takeIf { it.isNotBlank() }?.let(Uri::parse),
                            loadSongTagInfo = mainViewModel::getSongTagInfo,
                            showPlayNextInLists = showPlayNextInLists,
                            onClick = {
                                val visible = remoteResults.ifEmpty { listOf(item) }
                                val startIndex = visible.indexOfFirst { it.song.id == item.song.id }.coerceAtLeast(0)
                                val resolver: suspend (Song) -> Song = { song ->
                                    val target = visible.firstOrNull { it.song.id == song.id }
                                        ?: error(context.getString(R.string.lx_online_song_expired))
                                    if (target.provider.isSubsonicLike) {
                                        navidromeService.resolvePlayableSong(target)
                                    } else when (target.provider) {
                                        RemoteMusicProvider.Emby -> embyService.resolvePlayableSong(target)
                                        RemoteMusicProvider.Lx -> target.song
                                        RemoteMusicProvider.Navidrome,
                                        RemoteMusicProvider.OpenSubsonic -> error("Unexpected Subsonic-like provider branch")
                                    }
                                }
                                scope.launch {
                                    state.isBusy = true
                                    runCatching {
                                        playerViewModel.setLazyOnlinePlaylist(
                                            songs = visible.map { it.song },
                                            startIndex = startIndex,
                                            resolvedStartSong = resolver(item.song),
                                            resolver = resolver
                                        )
                                        state.message = context.getString(R.string.lx_online_queue_obtained, visible.size)
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }.onFailure {
                                        state.message = it.localizedMessage ?: context.getString(R.string.lx_online_playback_failed)
                                        showToast(state.message)
                                    }
                                    state.isBusy = false
                                }
                            },
                            onPlayNext = {
                                scope.launch {
                                    state.isBusy = true
                                    runCatching {
                                        val playable = resolveActionRemoteSong(item)
                                        playerViewModel.playNext(playable)
                                        showToast(context.getString(R.string.song_more_added_to_play_next))
                                    }.onFailure {
                                        state.message = it.localizedMessage ?: context.getString(R.string.lx_online_add_to_queue_failed)
                                        showToast(state.message)
                                    }
                                    state.isBusy = false
                                }
                            },
                            onDownload = {
                                scope.launch {
                                    state.isBusy = true
                                    runCatching {
                                        enqueueDownload(context, resolveRemoteDownloadSong(item))
                                        showToast(context.getString(R.string.player_download_started))
                                    }.onFailure {
                                        state.message = it.localizedMessage ?: context.getString(R.string.lx_online_download_failed)
                                        showToast(state.message)
                                    }
                                    state.isBusy = false
                                }
                            },
                            onMore = {
                                remoteActionItem = item
                                actionItem = null
                            }
                        )
                    }
                }
            } else {
                OnlineSearchPaginationEffect(resultsListState, state.searchRequests, state.results.size,
                    state.isBusy || selectedSource == null || loadedSources == null) {
                    searchSelectedProvider(automatic = it, loadMore = true)
                }
                LazyColumn(state = resultsListState, modifier = Modifier.fillMaxSize()) {
                    items(state.results, key = { it.song.id }) { item ->
                        SongItem(
                            song = item.song,
                            albumArtUri = item.coverUrl.takeIf { it.isNotBlank() }?.let(Uri::parse),
                            loadSongTagInfo = mainViewModel::getSongTagInfo,
                            showPlayNextInLists = showPlayNextInLists,
                            onClick = {
                                scope.launch {
                                    state.isBusy = true
                                    runCatching {
                                        playLazyOnlineQueue(item)
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }.onFailure {
                                        state.message = it.localizedMessage ?: context.getString(R.string.lx_online_playback_failed)
                                        showToast(state.message)
                                    }
                                    state.isBusy = false
                                }
                            },
                            onPlayNext = {
                                scope.launch {
                                    state.isBusy = true
                                    runCatching {
                                        val playable = service.resolvePlayableSong(item, selectedSource?.script.orEmpty())
                                        playerViewModel.playNext(playable)
                                        showToast(context.getString(R.string.song_more_added_to_play_next))
                                    }.onFailure {
                                        state.message = it.localizedMessage ?: context.getString(R.string.lx_online_add_to_queue_failed)
                                        showToast(state.message)
                                    }
                                    state.isBusy = false
                                }
                            },
                            onDownload = {
                                scope.launch {
                                    state.isBusy = true
                                    runCatching {
                                        downloadLxSong(context, service, item, selectedSource?.script.orEmpty())
                                        showToast(context.getString(R.string.player_download_started))
                                    }.onFailure {
                                        state.message = it.localizedMessage ?: context.getString(R.string.lx_online_download_failed)
                                        showToast(state.message)
                                    }
                                    state.isBusy = false
                                }
                            },
                            onMore = {
                                actionItem = item
                                remoteActionItem = null
                            }
                        )
                    }
                    item(key = "pagination") {
                        OnlineSearchPageFooter(state.searchRequests) { searchSelectedProvider(loadMore = true) }
                    }
                    item { Spacer(modifier = Modifier.height(120.dp)) }
                }
            }
        }
    }

    SongMoreActionHost(
        actionSong = remoteActionItem?.song ?: actionItem?.song,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        onDismissAction = {
            actionItem = null
            remoteActionItem = null
        },
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist,
        showDelete = false,
        showLocalFileActions = false,
        resolveSongForAction = ::resolveActionSong
    )
}

private fun RemoteMusicProvider.displayName(context: Context): String =
    when (this) {
        RemoteMusicProvider.Lx -> "LX Music"
        RemoteMusicProvider.Navidrome -> context.getString(R.string.remote_source_navidrome)
        RemoteMusicProvider.OpenSubsonic -> context.getString(R.string.remote_source_opensubsonic)
        RemoteMusicProvider.Emby -> context.getString(R.string.remote_source_emby)
    }

private fun enqueueDownload(context: Context, song: com.ella.music.data.model.Song) {
    val fileName = song.fileName.ifBlank { "${song.title}-${song.artist}.mp3" }
        .sanitizeExportFileName(fallback = "Halcyon.mp3", maxLength = 160)
    val request = DownloadManager.Request(Uri.parse(song.path))
        .setTitle(fileName)
        .setDescription("${song.title} - ${song.artist}")
        .setMimeType(song.mimeType.ifBlank { "audio/*" })
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Halcyon/$fileName")
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}

private suspend fun downloadLxSong(
    context: Context,
    service: com.ella.music.data.lx.LxOnlineService,
    item: com.ella.music.data.lx.LxOnlineSong,
    sourceScript: String
) {
    val fileName = item.song.fileName.ifBlank { "${item.song.title}-${item.song.artist}.mp3" }
        .sanitizeExportFileName(fallback = "Halcyon.mp3", maxLength = 160)
    val target = java.io.File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
        "Halcyon/$fileName"
    )
    service.downloadWithMetadata(item, sourceScript, target)
}
