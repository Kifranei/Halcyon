package com.ella.music.ui.online

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.ella.music.ui.components.EllaMiuixChip
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.LxOnlineViewModel
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaSmallTopAppBar
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
    BackHandler(onBack = onBack)

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
    val openPlayerOnPlay by settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNextInLists by settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val currentSourceId = selectedSource?.id.orEmpty()
    var observedSourceId by remember { mutableStateOf<String?>(null) }
    var actionItem by remember { mutableStateOf<LxOnlineSong?>(null) }
    var remoteResults by remember { mutableStateOf<List<RemoteOnlineSong>>(emptyList()) }
    var remoteActionItem by remember { mutableStateOf<RemoteOnlineSong?>(null) }
    LaunchedEffect(currentSourceId, selectedProvider) {
        val previousSourceId = observedSourceId
        val marker = "${selectedProvider.id}:$currentSourceId"
        if (previousSourceId != null && previousSourceId != marker) {
            state.clearResults(
                context.getString(R.string.lx_online_source_switched, selectedProvider.displayName(context))
            )
            remoteResults = emptyList()
        }
        observedSourceId = marker
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

    suspend fun searchSelectedProvider() {
        if (state.searchQuery.isBlank()) return
        if (!remoteConfigured || selectedProvider == RemoteMusicProvider.Lx && selectedSource == null) {
            showToast(context.getString(R.string.remote_source_configure_first))
            return
        }
        state.isBusy = true
        runCatching {
            if (selectedProvider == RemoteMusicProvider.Lx) {
                state.results = service.search(state.searchQuery, selectedSource, platform = state.searchPlatform)
                remoteResults = emptyList()
                state.message = if (state.results.isEmpty()) context.getString(R.string.lx_online_no_songs_found)
                else context.getString(R.string.lx_online_songs_found, state.results.size)
            } else {
                val config = remoteConfig ?: error(context.getString(R.string.remote_source_configure_first))
                remoteResults = when (selectedProvider) {
                    RemoteMusicProvider.Navidrome -> navidromeService.search(state.searchQuery, config)
                    RemoteMusicProvider.OpenSubsonic -> navidromeService.search(state.searchQuery, config)
                    RemoteMusicProvider.Emby -> embyService.search(state.searchQuery, config)
                    RemoteMusicProvider.Lx -> emptyList()
                }
                state.results = emptyList()
                state.message = if (remoteResults.isEmpty()) context.getString(R.string.lx_online_no_songs_found)
                else context.getString(R.string.lx_online_songs_found, remoteResults.size)
            }
        }.onFailure {
            state.message = it.localizedMessage ?: context.getString(R.string.lx_online_search_failed)
            showToast(state.message)
        }
        state.isBusy = false
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
            color = ellaPageBackground(),
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

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.padding(vertical = 4.dp),
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

            OnlineTextField(
                value = state.searchQuery,
                onValueChange = { state.searchQuery = it },
                onSearch = {
                    scope.launch { searchSelectedProvider() }
                },
                placeholder = stringResource(R.string.lx_online_search_placeholder),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )

            if (selectedProvider == RemoteMusicProvider.Lx) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LxSearchPlatform.entries.forEach { platform ->
                        EllaMiuixChip(
                            text = platform.displayName,
                            selected = state.searchPlatform == platform,
                            onClick = {
                                if (state.searchPlatform != platform) {
                                    state.searchPlatform = platform
                                    state.clearResults(platform.displayName)
                                    remoteResults = emptyList()
                                }
                            }
                        )
                    }
                }
            }

            Button(
                enabled = !state.isBusy && state.searchQuery.isNotBlank() && remoteConfigured,
                onClick = {
                    scope.launch { searchSelectedProvider() }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.common_search))
            }

            Text(
                text = if (state.isBusy) stringResource(R.string.lx_online_processing)
                else if (state.hasCustomMessage) state.message
                else stringResource(state.messageId),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )

            val showingRemote = selectedProvider != RemoteMusicProvider.Lx
            if ((!showingRemote && state.results.isEmpty()) || (showingRemote && remoteResults.isEmpty())) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (!remoteConfigured) stringResource(R.string.remote_source_configure_first) else stringResource(R.string.lx_online_search_hint),
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
                LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                                        val playable = service.resolvePlayableSong(item, selectedSource?.script.orEmpty())
                                        enqueueDownload(context, playable)
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
