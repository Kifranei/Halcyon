package com.ella.music.ui.online

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.data.sourceplugin.SourcePluginConfig
import com.ella.music.data.sourceplugin.SourcePluginRegistry
import com.ella.music.data.sourceplugin.isSourcePluginKey
import com.ella.music.data.sourceplugin.sourcePluginKeyOf
import com.ella.music.ui.components.*
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import com.ella.music.ui.components.GlassChevronBackwardIcon
import top.yukonga.miuix.kmp.icon.extended.Settings

/**
 * Search page for the active account-source plugin (librarySource = plugin:<id>), following the
 * NetEase search screen's flow. Results carry plugin source keys so playback resolves through
 * the source-plugin media-source factory.
 */
@Composable
internal fun SourcePluginSearchScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember(context) { SettingsManager.getInstance(context) }
    val librarySource by settings.librarySource.collectAsState(initial = "")
    val pluginId = remember(librarySource) {
        if (isSourcePluginKey(librarySource)) sourcePluginKeyOf(librarySource) else ""
    }
    var config by remember(pluginId) {
        mutableStateOf<SourcePluginConfig?>(
            pluginId.takeIf { it.isNotBlank() }?.let { SourcePluginRegistry.config(context, it) }
        )
    }
    val client = remember(context) { SourcePluginRegistry.client(context) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var submittedQuery by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Song>()) }
    var page by remember { mutableIntStateOf(0) }
    var more by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var showAccount by remember { mutableStateOf(false) }
    val openPlayer by settings.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNext by settings.showPlayNextInLists.collectAsState(initial = false)

    fun search(nextPage: Boolean) {
        val current = config ?: return
        if (busy || (!nextPage && query.isBlank())) return
        val targetQuery = if (nextPage) submittedQuery else query.trim()
        val targetPage = if (nextPage) page + 1 else 1
        busy = true
        scope.launch {
            try {
                val result = client.search(current, targetQuery, targetPage)
                val found = result.songs
                results = if (nextPage) (results + found).distinctBy { it.onlineId } else found
                submittedQuery = targetQuery
                page = targetPage
                more = !result.isEnd
                message = if (results.isEmpty()) context.getString(R.string.lx_online_no_songs_found)
                    else context.getString(R.string.lx_online_songs_found, results.size)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { message = error.localizedMessage.orEmpty() }
            finally { busy = false }
        }
    }
    if (showAccount && config != null) {
        SourcePluginAccountSheet(config!!, onDismiss = { showAccount = false })
    }
    val pageBackground = ellaPageBackground()
    val onlineBackdrop = rememberLayerBackdrop()
    Column(Modifier.fillMaxSize().background(pageBackground)) {
        EllaSmallTopAppBar(
            backdrop = onlineBackdrop,
            title = config?.name ?: stringResource(R.string.source_plugin_search_title), color = pageBackground,
            navigationIcon = { IconButton(onClick = onBack) { Icon(GlassChevronBackwardIcon, stringResource(R.string.common_back)) } },
            actions = {
                IconButton(onClick = { showAccount = true }) {
                    Icon(MiuixIcons.Regular.Settings, stringResource(R.string.lx_online_source_management))
                }
            }
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            OnlineSearchControls(emptyList(), 0, {}, query, { query = it }, { search(false) })
            if (config == null) {
                Text(stringResource(R.string.source_plugin_not_installed), modifier = Modifier.padding(vertical = 6.dp))
            }
            if (busy || message.isNotBlank()) Text(
                if (busy) stringResource(R.string.lx_online_processing) else message,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            LazyColumn(Modifier.fillMaxSize().layerBackdrop(onlineBackdrop)) {
                items(results, key = { it.onlineId }) { song ->
                    SongItem(
                        song = song, albumArtUri = song.coverUrl.takeIf { it.isNotBlank() }?.let(Uri::parse),
                        loadSongTagInfo = mainViewModel::getSongTagInfo,
                        showPlayNextInLists = showPlayNext,
                        onClick = {
                            playerViewModel.setPlaylist(results, results.indexOf(song).coerceAtLeast(0))
                            if (openPlayer) onNavigateToPlayer()
                        },
                        onPlayNext = { playerViewModel.playNext(song) },
                        onMore = { actionSong = song }
                    )
                }
                if (more) item {
                    Button(enabled = !busy, onClick = { search(true) }, modifier = Modifier.fillMaxWidth(), colors = os4NeutralButtonColors()) {
                        Text(stringResource(R.string.netease_search_more))
                    }
                }
                item { Spacer(Modifier.height(120.dp)) }
            }
        }
    }
    SongMoreActionHost(
        actionSong = actionSong,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        onDismissAction = { actionSong = null },
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist,
        showDelete = false,
        showLocalFileActions = false
    )
}
