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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.netease.CatClawNeteaseClient
import com.ella.music.data.netease.NeteaseLibraryStore
import com.ella.music.data.model.Song
import com.ella.music.ui.components.*
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Settings

@Composable
internal fun NeteaseSearchScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val client = remember(context) { CatClawNeteaseClient(context) }
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
    val openPlayer by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNext by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)

    fun search(nextPage: Boolean) {
        if (busy || (!nextPage && query.isBlank())) return
        val targetQuery = if (nextPage) submittedQuery else query.trim()
        val targetPage = if (nextPage) page + 1 else 1
        busy = true
        scope.launch {
            try {
                val found = client.searchSongs(targetQuery, targetPage)
                results = if (nextPage) (results + found).distinctBy { it.onlineId } else found
                submittedQuery = targetQuery
                page = targetPage
                more = found.size == 30
                message = if (results.isEmpty()) context.getString(R.string.lx_online_no_songs_found) else context.getString(R.string.lx_online_songs_found, results.size)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { message = error.localizedMessage.orEmpty() }
            finally { busy = false }
        }
    }
    if (showAccount) NeteaseAccountScreen(onDismiss = { showAccount = false }, mainViewModel = mainViewModel)
    Column(Modifier.fillMaxSize().background(ellaPageBackground()).windowInsetsPadding(WindowInsets.statusBars)) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.netease_search_title), color = Color.Transparent,
            navigationIcon = { IconButton(onClick = onBack) { Icon(MiuixIcons.Regular.Back, stringResource(R.string.common_back)) } },
            actions = { IconButton(onClick = { showAccount = true }) { Icon(MiuixIcons.Regular.Settings, stringResource(R.string.lx_online_source_management)) } }
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            OnlineSearchControls(emptyList(), 0, {}, query, { query = it }, { search(false) })
            if (busy || message.isNotBlank()) Text(
                if (busy) stringResource(R.string.lx_online_processing) else message,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            LazyColumn(Modifier.fillMaxSize()) {
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
                if (more) item { Button(enabled = !busy, onClick = { search(true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.netease_search_more)) } }
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
