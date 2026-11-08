package com.ella.music.ui.poster

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.ella.music.R
import com.ella.music.data.CategoryResumeKeys
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.model.Song
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Switch
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PosterWallScreen(
    mainViewModel: MainViewModel, playerViewModel: PlayerViewModel,
    onBack: () -> Unit, onOpenPlayer: () -> Unit, onAlbum: (Long) -> Unit, onArtist: (String) -> Unit,
    bottomContentPadding: Dp = 0.dp,
    onFullscreenChanged: (Boolean) -> Unit = {}
) {
    val library by mainViewModel.songs.collectAsState()
    val queue by playerViewModel.playlist.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val queueIndex by playerViewModel.currentQueueIndex.collectAsState()
    val stacked by mainViewModel.settingsManager.posterWallStacked.collectAsState(initial = false)
    val savedLayouts by mainViewModel.settingsManager.posterWallStackLayouts.collectAsState(initial = "{}")
    val librarySource by mainViewModel.settingsManager.librarySource.collectAsState(initial = "local")
    val infinite by mainViewModel.settingsManager.posterWallInfiniteScroll.collectAsState(initial = false)
    var wallFullscreen by rememberSaveable { mutableStateOf(false) }
    var lyricTheater by rememberSaveable { mutableStateOf(false) }
    val fullscreenChanged by rememberUpdatedState(onFullscreenChanged)
    LaunchedEffect(wallFullscreen, lyricTheater) { fullscreenChanged(wallFullscreen || lyricTheater) }
    DisposableEffect(Unit) { onDispose { fullscreenChanged(false) } }
    if (wallFullscreen && !lyricTheater) PosterImmersiveWindow(landscape = false)
    var source by rememberSaveable { mutableStateOf(if (queue.isEmpty()) "library" else "queue") }
    val savedSource by mainViewModel.settingsManager.posterWallSource.collectAsState(initial = null)
    var sourceRestored by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(savedSource) {
        if (!sourceRestored && savedSource != null) {
            if (savedSource == "queue" || savedSource == "library") source = savedSource!!
            sourceRestored = true
        }
    }
    LaunchedEffect(source, sourceRestored) {
        if (sourceRestored) mainViewModel.settingsManager.setPosterWallSource(source)
    }
    var query by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf(false) }
    var locateRequest by remember { mutableIntStateOf(0) }
    var shuffleSeed by rememberSaveable { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf<ExpandedPoster?>(null) }
    BackHandler(enabled = wallFullscreen && expanded == null && !lyricTheater) { wallFullscreen = false }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val remoteInput = LocalInputModeManager.current.inputMode == InputMode.Keyboard ||
        LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    val sourceSongs = if (source == "queue") queue else library
    val stackScope = "$librarySource:$source"
    var pendingLayout by remember(stackScope) { mutableStateOf<String?>(null) }
    val storedLayout = remember(savedLayouts, stackScope) {
        runCatching { org.json.JSONObject(savedLayouts).optJSONArray(stackScope)?.toString() }.getOrNull().orEmpty()
    }
    LaunchedEffect(storedLayout) { if (storedLayout == pendingLayout) pendingLayout = null }
    val layoutJson = pendingLayout ?: storedLayout
    val dataset by produceState<PosterWallDataset?>(null, sourceSongs, query, source, shuffleSeed, layoutJson) {
        value = withContext(Dispatchers.Default) {
            val all = buildPosterWallItems(sourceSongs, source, "")
            val visible = if (query.isBlank()) all else buildPosterWallItems(sourceSongs, source, query)
            PosterWallDataset(source, query, rearrangePosterWallItems(visible, shuffleSeed), shuffleSeed,
                PosterStackLayout.resolve(all, layoutJson), all)
        }
    }
    val ready = dataset?.let { it.source == source && it.query == query } == true
    val items = if (ready) dataset?.items.orEmpty() else emptyList()
    val stackLayout = if (ready) dataset?.stackLayout else null
    val itemKeys = remember(items) { items.map { it.stackKey } }
    val geometry = remember(itemKeys, infinite, stacked, stackLayout) {
        PosterWallGeometry(items.size, infinite = infinite, stacked = stacked,
            stackGroups = stackLayout?.indices(items), stackKeys = itemKeys)
    }
    val wall = rememberSaveable(source, query, stacked, saver = PosterWallState.Saver) { PosterWallState() }
    val currentIndex = remember(items, currentSong, queueIndex, source) {
        val key = currentSong?.playlistIdentityKey()
        if (source == "queue") items.indexOfFirst { it.sourceIndex == queueIndex && it.song.playlistIdentityKey() == key }
        else items.indexOfFirst { it.song.playlistIdentityKey() == key }
    }
    PosterWallPositioning(wall, items, geometry, currentIndex, locateRequest)
    DisposableEffect(wall) { onDispose { wall.stop() } }
    BackHandler(enabled = !lyricTheater && !wallFullscreen && expanded == null && (search || query.isNotBlank())) {
        query = ""; search = false; focusManager.clearFocus()
    }
    fun playPoster(poster: ExpandedPoster, startPlayback: Boolean = true) {
        if (source == "queue") {
            // Never replace a shuffled/lazy online queue from its filtered visual snapshot.
            val index = resolvePosterQueueIndex(queue, poster.song, poster.sourceIndex)
            if (index >= 0) playerViewModel.playQueueIndex(index, startPlayback)
        } else {
            val group = if (stacked) poster.stackKey?.let { key ->
                val members = stackLayout?.members(key).orEmpty().asReversed()
                members.mapNotNull { dataset?.itemsByStackKey?.get(it) }.map { it.song }
            }.orEmpty() else emptyList()
            val songs = group.ifEmpty { library }
            val index = songs.indexOfFirst { it.playlistIdentityKey() == poster.song.playlistIdentityKey() }
            if (index >= 0) playerViewModel.setPlaylist(songs, index, resumeCategoryKey = CategoryResumeKeys.HOME, startPlayback = startPlayback)
        }
    }
    fun swipeStack(direction: Int) {
        val poster = expanded ?: return
        val members = poster.stackKey?.let { stackLayout?.members(it) }.orEmpty().asReversed()
        if (members.size < 2) return
        val index = members.indexOf(poster.stackKey)
        if (index < 0) return
        val nextKey = members[Math.floorMod(index + direction, members.size)]
        val next = dataset?.itemsByStackKey?.get(nextKey) ?: return
        val changed = poster.copy(song = next.song, sourceIndex = next.sourceIndex, stackKey = next.stackKey)
        expanded = changed
    }
    val accent = MiuixTheme.colorScheme.primary
    val density = LocalDensity.current.density
    val context = LocalContext.current
    var canvasOrigin by remember { mutableStateOf(Offset.Zero) }
    // Covers fill the entire page behind the app's glass mini-player. Only the actionable
    // overlays and expanded card reserve the dock; there is no opaque footer strip.
    BoxWithConstraints(Modifier.fillMaxSize().background(PosterInk)
        .then(if (wallFullscreen || lyricTheater) Modifier else
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)))) {
        val bottomInset = if (wallFullscreen || lyricTheater) 0.dp else maxOf(bottomContentPadding,
            WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding())
        val focusViewport = PosterRect(0f, 0f, maxWidth.value, (maxHeight - bottomInset).value)
        Column(Modifier.fillMaxSize()) {
            if (!wallFullscreen) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PosterIconButton(stringResource(R.string.common_back), onBack) {
                    Icon(MiuixIcons.Regular.Back, stringResource(R.string.common_back), tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(stringResource(R.string.poster_wall_title), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.poster_wall_count, items.size), color = PosterMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
                PosterIconButton(stringResource(R.string.poster_wall_refresh), {
                    wall.stop(); expanded = null; focusManager.clearFocus()
                    val seed = shuffleSeed
                    scope.launch {
                        if (stacked && stackLayout != null) {
                            val rearranged = withContext(Dispatchers.Default) { stackLayout.rearranged(seed + 1).encode() }
                            pendingLayout = rearranged
                            mainViewModel.settingsManager.setPosterWallStackLayout(stackScope, rearranged)
                            shuffleSeed = seed + 1
                        } else {
                            val next = withContext(Dispatchers.Default) { nextPosterWallShuffleSeed(items, seed) }
                            if (shuffleSeed == seed) shuffleSeed = next
                        }
                    }
                }) {
                    Icon(MiuixIcons.Regular.Refresh, stringResource(R.string.poster_wall_refresh), tint = Color.White, modifier = Modifier.size(22.dp))
                }
                PosterIconButton(stringResource(R.string.poster_wall_fullscreen_wall), {
                    focusManager.clearFocus(); search = false; expanded = null; wallFullscreen = true
                }) {
                    Icon(PosterFullscreenIcon, stringResource(R.string.poster_wall_fullscreen_wall), tint = Color.White, modifier = Modifier.size(22.dp))
                }
                PosterIconButton(stringResource(R.string.common_search), { search = !search; if (!search) { query = ""; focusManager.clearFocus() } }) {
                    Icon(MiuixIcons.Basic.Search, stringResource(R.string.common_search), tint = if (search) accent else Color.White, modifier = Modifier.size(22.dp))
                }
                PosterIconButton(stringResource(R.string.player_locate_current_song), {
                    focusManager.clearFocus(); query = ""
                    if (currentIndex < 0 && queue.isNotEmpty()) source = "queue"
                    locateRequest++
                }) {
                    Icon(painterResource(R.drawable.ic_my_location), stringResource(R.string.player_locate_current_song), tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("queue" to R.string.poster_wall_queue, "library" to R.string.poster_wall_library).forEach { (id, label) ->
                    val selected = source == id
                    Text(stringResource(label), color = if (selected) Color.White else PosterMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).background(if (selected) PosterPanel else Color.Transparent)
                            .border(0.5.dp, if (selected) Color.White.copy(alpha = 0.15f) else Color.Transparent, CircleShape)
                            .clickable { source = id; expanded = null; query = ""; focusManager.clearFocus() }.padding(horizontal = 18.dp, vertical = 9.dp))
                }
            }
            if (search) {
                PosterWallSearch(query, { query = it }, { search = false; query = ""; focusManager.clearFocus() })
            }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds().onGloballyPositioned { canvasOrigin = it.positionInParent() / density }
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.08f), Color.Transparent), radius = 1200f))) {
                if (items.isNotEmpty()) {
                    PosterWallCanvas(items, geometry, wall, currentIndex,
                        artwork = { song, modifier, size -> PosterArtwork(song, mainViewModel, modifier, sizePx = size) }, scope = scope,
                        onExpand = { index, rect ->
                            focusManager.clearFocus()
                            val item = items[index]
                            val poster = ExpandedPoster(item.song, item.sourceIndex,
                                rect.copy(x = rect.x + canvasOrigin.x, y = rect.y + canvasOrigin.y), 13f * wall.camera.scale,
                                stackKey = if (stacked) item.stackKey else null, originRotation = geometry.rotation(index))
                            expanded = poster
                        },
                        onMoveStack = { from, to, before ->
                            val layout = stackLayout
                            val next = layout?.move(items[from].stackKey, items[to].stackKey, before)
                            if (next != null) {
                                val encoded = next.encode()
                                pendingLayout = encoded
                                scope.launch { mainViewModel.settingsManager.setPosterWallStackLayout(stackScope, encoded) }
                            } else if (items[from].stackKey !in layout?.members(items[to].stackKey).orEmpty() &&
                                layout?.members(items[to].stackKey)?.size == MAX_POSTER_STACK_SIZE) {
                                Toast.makeText(context, R.string.poster_wall_stack_full, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onMore = { actionSong = it },
                        remoteEnabled = expanded == null && !lyricTheater && actionSong == null,
                        fullscreen = wallFullscreen, searching = search)
                    if (!wallFullscreen) Column(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.padding(end = 8.dp).clip(CircleShape).background(PosterPanel.copy(alpha = 0.92f)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val label = stringResource(R.string.poster_wall_stacked)
                        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                        Switch(stacked, { enabled -> expanded = null; scope.launch { mainViewModel.settingsManager.setPosterWallStacked(enabled) } },
                            Modifier.semantics { contentDescription = label })
                    }
                    Row(Modifier.padding(end = 8.dp).clip(CircleShape).background(PosterPanel.copy(alpha = 0.92f)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val label = stringResource(R.string.poster_wall_infinite_scroll)
                        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                        Switch(infinite, { enabled -> scope.launch { mainViewModel.settingsManager.setPosterWallInfiniteScroll(enabled) } },
                            Modifier.semantics { contentDescription = label })
                    }
                    Row(Modifier.clip(CircleShape).background(PosterPanel.copy(alpha = 0.92f)).border(0.5.dp, Color.White.copy(alpha = 0.1f), CircleShape), verticalAlignment = Alignment.CenterVertically) {
                        fun zoom(factor: Float) { wall.stop(); wall.transform(Offset.Zero, factor, wall.viewport / 2f, geometry) }
                        PosterIconButton(stringResource(R.string.poster_wall_zoom_out), { zoom(0.85f) }) { Text("−", color = Color.White, fontSize = 22.sp) }
                        // Read zoom only in this small leaf; camera drags do not invalidate page chrome.
                        PosterZoomLabel(wall)
                        PosterIconButton(stringResource(R.string.poster_wall_zoom_in), { zoom(1.18f) }) { Text("+", color = Color.White, fontSize = 22.sp) }
                    }
                    }
                    Text(stringResource(if (remoteInput) R.string.poster_wall_remote_hint else if (stacked) R.string.poster_wall_stack_gesture_hint else R.string.poster_wall_gesture_hint), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp,
                        modifier = Modifier.padding(top = 6.dp).clip(CircleShape).background(PosterInk.copy(alpha = 0.65f)).padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                } else {
                    Column(Modifier.align(Alignment.Center).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(PosterWallIcon, null, tint = PosterMuted.copy(alpha = 0.4f), modifier = Modifier.size(54.dp))
                        Text(stringResource(if (!ready) R.string.poster_wall_loading else if (query.isNotBlank()) R.string.poster_wall_no_results else if (source == "queue") R.string.poster_wall_empty_queue else R.string.poster_wall_empty_library),
                            color = PosterMuted, fontSize = 15.sp, modifier = Modifier.padding(top = 18.dp))
                        if (ready && source == "queue" && query.isBlank()) Text(stringResource(R.string.poster_wall_browse_library), color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 18.dp).clip(CircleShape).background(PosterPanel).clickable { source = "library" }.padding(horizontal = 20.dp, vertical = 12.dp))
                    }
                }
            }
        }
        if (!lyricTheater) expanded?.let { poster ->
            val playingKey = currentSong?.playlistIdentityKey()
            val isCurrent = poster.song.playlistIdentityKey() == playingKey && (source != "queue" || poster.sourceIndex == queueIndex)
            val stackKeys = poster.stackKey?.let { stackLayout?.members(it) }.orEmpty().asReversed()
            val stackSongs = stackKeys.mapNotNull { dataset?.itemsByStackKey?.get(it)?.song }
            PosterExpandedCard(poster, focusViewport, isCurrent,
                artwork = { song, modifier -> PosterArtwork(song, mainViewModel, modifier, large = true) },
                lyrics = { modifier -> PosterPlayerLyrics(playerViewModel, onOpenPlayer, modifier) },
                seekBar = { compact -> PosterPlayerProgress(playerViewModel, compact) },
                controls = {
                    val playing by playerViewModel.isPlaying.collectAsState()
                    PosterPlayControls(playing, {}, playerViewModel::togglePlayPause, {}, showSkipButtons = false)
                },
                onPlay = { playPoster(poster) }, onMore = { actionSong = poster.song; expanded = null }, onClosed = { expanded = null },
                onFullscreen = if (isCurrent) ({ lyricTheater = true }) else null,
                onStackSwipe = if (stacked && poster.stackKey != null) ({ direction -> swipeStack(direction) }) else null,
                stackPosition = poster.stackKey?.let { key -> "${stackKeys.indexOf(key) + 1} / ${stackKeys.size}" },
                stackSongs = stackSongs, stackIndex = stackKeys.indexOf(poster.stackKey).coerceAtLeast(0))
        }
        if (lyricTheater) PosterLyricTheaterScreen(playerViewModel, onBack = { lyricTheater = false })
    }
    SongMoreActionHost(actionSong, mainViewModel, playerViewModel, onDismissAction = { actionSong = null }, onNavigateToAlbum = onAlbum, onNavigateToArtist = onArtist)
}

@Composable
private fun PosterZoomLabel(state: PosterWallState) {
    val percent by remember(state) { derivedStateOf { (state.camera.scale * 100).roundToInt() } }
    Text("$percent%", color = PosterMuted, fontSize = 11.sp, modifier = Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}
