package com.ella.music.ui.online

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import top.yukonga.miuix.kmp.basic.CardDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.data.musicfree.MusicFreeOnlineSong
import com.ella.music.data.musicfree.MusicFreePluginService
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.MusicFreeOnlineViewModel
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
fun MusicFreeOnlineScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPluginSettings: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    state: MusicFreeOnlineViewModel = viewModel()
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val service = remember(context) { MusicFreePluginService(context) }
    val scope = rememberCoroutineScope()

    val loadedPlugins by settingsManager.musicFreePlugins.collectAsState(initial = null)
    val plugins = loadedPlugins.orEmpty()
    val selectedPluginId by settingsManager.selectedMusicFreePluginId.collectAsState(initial = "")
    val selectedPlugin = remember(plugins, selectedPluginId) {
        plugins.firstOrNull { it.id == selectedPluginId } ?: plugins.firstOrNull()
    }
    val openPlayerOnPlay by settingsManager.openPlayerOnPlay.collectAsState(initial = true)
    val showPlayNextInLists by settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val currentPluginId = selectedPlugin?.id.orEmpty()
    var observedPluginId by remember { mutableStateOf<String?>(null) }
    var actionItem by remember { mutableStateOf<MusicFreeOnlineSong?>(null) }
    LaunchedEffect(currentPluginId) {
        val previousPluginId = observedPluginId
        if (previousPluginId != null && previousPluginId != currentPluginId) {
            state.clearResults("")
        }
        observedPluginId = currentPluginId
    }

    fun showToast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    suspend fun playLazyOnlineQueue(startItem: MusicFreeOnlineSong) {
        val visible = state.results.ifEmpty { listOf(startItem) }
        val startIndex = visible.indexOfFirst { it.song.id == startItem.song.id }.coerceAtLeast(0)
        val resolved = service.resolvePlayableSong(startItem, selectedPlugin)
        val songs = visible.map { it.song }
        val itemById = visible.associateBy { it.song.id }
        playerViewModel.setLazyOnlinePlaylist(
            songs = songs,
            startIndex = startIndex,
            resolvedStartSong = resolved
        ) { song ->
            val target = itemById[song.id] ?: error("队列歌曲已失效")
            service.resolvePlayableSong(target, selectedPlugin)
        }
        state.message = "已获取 ${songs.size} 首队列歌曲，将在播放到对应歌曲时解析"
    }

    suspend fun resolveActionSong(song: Song): Song {
        val item = actionItem?.takeIf { it.song.id == song.id }
            ?: state.results.firstOrNull { it.song.id == song.id }
            ?: error("在线歌曲已失效")
        return service.resolvePlayableSong(item, selectedPlugin)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = "MusicFree",
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
                IconButton(onClick = onNavigateToPluginSettings) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Settings,
                        contentDescription = stringResource(R.string.lx_online_source_management),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.padding(vertical = 4.dp),
                colors = CardDefaults.defaultColors(color = onlineSourceCardColor()),
                onClick = onNavigateToPluginSettings
            ) {
                BasicComponent(
                    title = selectedPlugin?.name ?: stringResource(R.string.lx_online_no_source_selected),
                    summary = selectedPlugin?.url ?: stringResource(R.string.lx_online_no_source_hint)
                )
            }

            OnlineSearchControls(
                providers = plugins.map { it.name },
                selectedIndex = plugins.indexOfFirst { it.id == selectedPlugin?.id },
                onProviderSelected = { index ->
                    plugins.getOrNull(index)?.let { plugin ->
                        if (!state.isBusy) scope.launch { settingsManager.selectMusicFreePlugin(plugin.id) }
                    }
                },
                query = state.searchQuery,
                onQueryChange = { state.searchQuery = it },
                onSearch = {
                    if (state.searchQuery.isNotBlank() && !state.isBusy) {
                        if (selectedPlugin == null) {
                            showToast(context.getString(R.string.lx_online_no_source_hint))
                        } else scope.launch {
                            state.isBusy = true
                            try {
                                state.results = service.search(state.searchQuery, selectedPlugin)
                                state.message = if (state.results.isEmpty()) "没有找到相关歌曲" else "找到 ${state.results.size} 首歌曲"
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                state.message = error.localizedMessage ?: "搜索失败"
                                showToast(state.message)
                            } finally {
                                state.isBusy = false
                            }
                        }
                    }
                }
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

            if (state.results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.lx_online_search_hint),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.results) { item ->
                    SongItem(
                        song = item.song,
                        loadSongTagInfo = mainViewModel::getSongTagInfo,
                        showPlayNextInLists = showPlayNextInLists,
                        albumArtUri = item.coverUrl.takeIf { it.isNotBlank() }?.let(Uri::parse),
                        onClick = {
                            scope.launch {
                                state.isBusy = true
                                runCatching {
                                    playLazyOnlineQueue(item)
                                    if (openPlayerOnPlay) onNavigateToPlayer()
                                }.onFailure {
                                    state.message = it.localizedMessage ?: "播放失败"
                                    showToast(state.message)
                                }
                                state.isBusy = false
                            }
                        },
                        onPlayNext = {
                            scope.launch {
                                state.isBusy = true
                                runCatching {
                                    val playable = service.resolvePlayableSong(item, selectedPlugin)
                                    playerViewModel.playNext(playable)
                                    showToast("已加入下一首")
                                }.onFailure {
                                    state.message = it.localizedMessage ?: "加入队列失败"
                                    showToast(state.message)
                                }
                                state.isBusy = false
                            }
                        },
                        onDownload = {
                            scope.launch {
                                state.isBusy = true
                                runCatching {
                                    val playable = service.resolvePlayableSong(item, selectedPlugin)
                                    enqueueMusicFreeDownload(context, playable)
                                    showToast("已开始下载到 Music/Ella")
                                }.onFailure {
                                    state.message = it.localizedMessage ?: "下载失败"
                                    showToast(state.message)
                                }
                                state.isBusy = false
                            }
                        },
                        onMore = {
                            actionItem = item
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(120.dp)) }
                }
            }


        }
    }

    SongMoreActionHost(
        actionSong = actionItem?.song,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        onDismissAction = { actionItem = null },
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist,
        showDelete = false,
        showLocalFileActions = false,
        resolveSongForAction = ::resolveActionSong
    )
}

private fun enqueueMusicFreeDownload(context: Context, song: com.ella.music.data.model.Song) {
    val fileName = song.fileName.ifBlank { "${song.title}-${song.artist}.mp3" }.sanitizeMusicFreeFileName()
    val request = DownloadManager.Request(Uri.parse(song.path))
        .setTitle(fileName)
        .setDescription("${song.title} - ${song.artist}")
        .setMimeType(song.mimeType.ifBlank { "audio/*" })
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Ella/$fileName")
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}

private fun String.sanitizeMusicFreeFileName(): String {
    return replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifBlank { "Ella Music.mp3" }
}

