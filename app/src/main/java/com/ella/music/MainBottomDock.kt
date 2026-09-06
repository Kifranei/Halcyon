package com.ella.music

import com.ella.music.ui.components.EllaMiuixBottomSheet

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.BottomBarGlassEffect
import com.ella.music.data.BottomBarStyle
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.CompactMiniPlayer
import com.ella.music.ui.components.BottomBarLiquidGlassConfig
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.GlassPill
import com.ella.music.ui.components.FloatingBottomBar
import com.ella.music.ui.components.FloatingBottomBarItem
import com.ella.music.ui.components.FloatingBottomBarMode
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.MiniPlayer
import com.ella.music.ui.components.MiniPlayerLyricTiming
import com.ella.music.ui.components.LocalBottomBarCornerRadiusDp
import com.ella.music.ui.components.LocalBottomBarLiquidGlassConfig
import com.ella.music.ui.search.LibrarySearchDockState
import com.ella.music.ui.search.LocalLibrarySearchDockState
import com.ella.music.ui.search.searchDockReturnTabRoute
import com.ella.music.ui.search.usesSearchBottomDock
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.simpleLuminance
import com.ella.music.ui.navigation.Screen
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class BottomDockMode {
    Expanded,
    Compact
}

internal data class BottomDockTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
internal fun FloatingBottomControls(
    showMiniPlayer: Boolean,
    showBottomBar: Boolean,
    currentSong: Song?,
    isPlaying: Boolean,
    coverRotationEnabled: Boolean,
    currentPosition: Long,
    duration: Long,
    lyricText: String?,
    lyricTranslation: String?,
    lyricProgress: Float,
    lyricPositionMs: Long,
    lyricTiming: MiniPlayerLyricTiming?,
    miniPlayerRightButton: Int = 0,
    miniPlayerSwipeToOpenPlayer: Boolean = true,
    tabs: List<BottomDockTab>,
    currentTabRoute: String?,
    currentRoute: String?,
    bottomDockMode: BottomDockMode,
    canCompact: Boolean,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    bottomBarStyle: BottomBarStyle,
    glassEffect: BottomBarGlassEffect,
    bottomBarCornerRadiusDp: Int,
    liquidGlassConfig: BottomBarLiquidGlassConfig,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onNavigate: (String) -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigatePlayer: () -> Unit,
    onNavigatePlaybackSource: () -> Unit,
    onExpand: () -> Unit,
    onExitSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
    useGlass: Boolean = true,
    stabilizeOverWallpaper: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var queueSheetExpanded by remember { mutableStateOf(false) }
    var queueSongsToAdd by remember { mutableStateOf<List<Song>?>(null) }
    var queueSongsForNewPlaylist by remember { mutableStateOf<List<Song>?>(null) }
    val playlist by playerViewModel.playlist.collectAsState()
    val shuffleEnabled by playerViewModel.shuffleEnabled.collectAsState()
    val queueLocked by playerViewModel.queueLocked.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()
    val currentQueueIndex by playerViewModel.currentQueueIndex.collectAsState()
    val userPlaylists by mainViewModel.playlists.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val ratingRevision by mainViewModel.ratingRevision.collectAsState()
    val miniPlayerLongPressSource by SettingsManager.getInstance(context)
        .miniPlayerLongPressSource.collectAsState(initial = false)
    val currentSongKey = currentSong?.playlistIdentityKey()
    val effectiveMode = if (bottomBarStyle == BottomBarStyle.Normal) {
        BottomDockMode.Expanded
    } else if (showMiniPlayer && canCompact) {
        bottomDockMode
    } else {
        BottomDockMode.Expanded
    }
    // Keep the compacting gesture continuous.  MeiloX uses this same spring progress for the
    // mini-player and the two adjacent controls instead of swapping between two fixed-size rows.
    val compactProgress by animateFloatAsState(
        targetValue = if (effectiveMode == BottomDockMode.Compact) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 520f),
        label = "BottomDockCompactProgress"
    )
    val searchDock = LocalLibrarySearchDockState.current
    val inSearchDock = usesSearchBottomDock(currentRoute)
    var lastTabRoute by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(currentTabRoute, currentRoute) {
        if (!currentRoute.isSearchRoute() && !currentTabRoute.isNullOrBlank() && !currentTabRoute.isSearchRoute()) {
            lastTabRoute = currentTabRoute
        }
    }
    val searchReturnTab = tabs.firstOrNull { tab ->
        tab.route == searchDockReturnTabRoute(currentRoute, currentTabRoute, lastTabRoute)
    }
    val floatingBottomBar = bottomBarStyle != BottomBarStyle.Normal
    // Normal mode intentionally keeps the same Miuix surface for both the mini-player and
    // the navigation bar. Artwork colours belong to the immersive player background only.
    val normalDockSurfaceColor = MiuixTheme.colorScheme.surfaceContainer
    val visualMode = when {
        inSearchDock && floatingBottomBar -> "search"
        effectiveMode == BottomDockMode.Compact && currentSong != null -> "compact"
        else -> "expanded"
    }
    val dockBackdrop = if (useGlass && floatingBottomBar) backdrop else null
    val dockLiquidGlass = useGlass && floatingBottomBar
    CompositionLocalProvider(
        LocalBottomBarCornerRadiusDp provides bottomBarCornerRadiusDp.toFloat(),
        LocalBottomBarLiquidGlassConfig provides liquidGlassConfig,
    ) {
        AnimatedContent(
            targetState = visualMode,
            transitionSpec = {
                val collapsing = initialState == "expanded" && targetState == "compact"
                val expanding = initialState == "compact" && targetState == "expanded"
                val center = androidx.compose.ui.graphics.TransformOrigin.Center
                if (collapsing) {
                    (fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 230,
                            delayMillis = 70,
                        ),
                    ) + androidx.compose.animation.scaleIn(
                        animationSpec = androidx.compose.animation.core.tween(280),
                        initialScale = 0.84f,
                        transformOrigin = center,
                    )) togetherWith
                        (fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(120),
                        ) + androidx.compose.animation.scaleOut(
                            animationSpec = androidx.compose.animation.core.tween(180),
                            targetScale = 0.72f,
                            transformOrigin = center,
                        ))
                } else if (expanding) {
                    (fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(260),
                    ) + androidx.compose.animation.scaleIn(
                        animationSpec = androidx.compose.animation.core.tween(300),
                        initialScale = 0.82f,
                        transformOrigin = center,
                    )) togetherWith
                        (fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(130),
                        ) + androidx.compose.animation.scaleOut(
                            animationSpec = androidx.compose.animation.core.tween(180),
                            targetScale = 0.86f,
                            transformOrigin = center,
                        ))
                } else {
                    fadeIn() togetherWith fadeOut()
                }.using(androidx.compose.animation.SizeTransform(clip = false))
            },
            label = "BottomDockMode",
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (floatingBottomBar || !showBottomBar) {
                        // Keep a small visual lift even on OEMs that report a zero navigation inset
                        // while the gesture handle is visible (ColorOS does this in some modes).
                        Modifier
                            .navigationBarsPadding()
                            .padding(bottom = 8.dp)
                    } else {
                        Modifier
                    }
                )
                .then(if (inSearchDock && floatingBottomBar) Modifier.imePadding() else Modifier)
                .consumeBottomDockPassthrough(showMiniPlayer, showBottomBar, visualMode)
        ) { mode ->
        if (mode == "search") {
            SearchBottomDock(
                showMiniPlayer = showMiniPlayer,
                currentSong = currentSong,
                isPlaying = isPlaying,
                progress = if (duration > 0L) currentPosition.toFloat() / duration.toFloat() else 0f,
                coverRotationEnabled = coverRotationEnabled,
                lyricText = lyricText,
                lyricTranslation = lyricTranslation,
                lyricProgress = lyricProgress,
                lyricPositionMs = lyricPositionMs,
                lyricTiming = lyricTiming,
                miniPlayerRightButton = miniPlayerRightButton,
                swipeUpToOpenPlayer = miniPlayerSwipeToOpenPlayer,
                albumArtUri = currentSong?.let { mainViewModel.getAlbumArtUri(it.albumId) },
                loadCoverArt = mainViewModel::getMiniPlayerCoverArtBitmap,
                backdrop = dockBackdrop,
                glassEffect = glassEffect,
                disableRefraction = stabilizeOverWallpaper,
                liquidGlass = dockLiquidGlass,
                searchDock = searchDock,
                onOpenPlayer = onNavigatePlayer,
                onPlayPause = { playerViewModel.togglePlayPause() },
                onSkipNext = { playerViewModel.skipToNext() },
                onSkipPrevious = { playerViewModel.skipToPrevious() },
                onShowQueue = { queueSheetExpanded = true },
                onLongClick = if (miniPlayerLongPressSource) onNavigatePlaybackSource else null,
                onExitSearch = onExitSearch,
                returnTab = searchReturnTab
            )
        } else if (mode == "compact" && currentSong != null) {
            CompactBottomDock(
                song = currentSong,
                isPlaying = isPlaying,
                progress = if (duration > 0L) currentPosition.toFloat() / duration.toFloat() else 0f,
                lyricText = lyricText,
                lyricTranslation = lyricTranslation,
                lyricProgress = lyricProgress,
                lyricPositionMs = lyricPositionMs,
                lyricTiming = lyricTiming,
                coverRotationEnabled = coverRotationEnabled,
                swipeUpToOpenPlayer = miniPlayerSwipeToOpenPlayer,
                albumArtUri = mainViewModel.getAlbumArtUri(currentSong.albumId),
                loadCoverArt = mainViewModel::getMiniPlayerCoverArtBitmap,
                backdrop = dockBackdrop,
                glassEffect = glassEffect,
                disableRefraction = stabilizeOverWallpaper,
                currentTab = tabs.firstOrNull { it.route == currentTabRoute },
                currentTabRoute = currentTabRoute,
                isSearchSelected = currentRoute.isSearchRoute(),
                onOpenPlayer = onNavigatePlayer,
                onOpenPlaybackSource = if (miniPlayerLongPressSource) onNavigatePlaybackSource else null,
                onPlayPause = { playerViewModel.togglePlayPause() },
                onSkipNext = { playerViewModel.skipToNext() },
                onNavigateSearch = onNavigateSearch,
                onExpand = onExpand,
                compactProgress = compactProgress,
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AnimatedVisibility(
                        visible = showMiniPlayer,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        currentSong?.let { song ->
                            MiniPlayer(
                                song = song,
                                isPlaying = isPlaying,
                                progress = if (duration > 0L) currentPosition.toFloat() / duration.toFloat() else 0f,
                                coverRotationEnabled = coverRotationEnabled,
                                lyricText = lyricText,
                                lyricTranslation = lyricTranslation,
                                albumArtUri = mainViewModel.getAlbumArtUri(song.albumId),
                                loadCoverArt = mainViewModel::getMiniPlayerCoverArtBitmap,
                                backdrop = dockBackdrop,
                                liquidGlass = dockLiquidGlass,
                                glassEffect = glassEffect,
                                disableRefraction = stabilizeOverWallpaper,
                                surfaceColor = if (floatingBottomBar) null else normalDockSurfaceColor,
                                compactProgress = compactProgress,
                                showQueueButton = miniPlayerRightButton == SettingsManager.MINI_PLAYER_RIGHT_QUEUE,
                                swipeUpToOpenPlayer = miniPlayerSwipeToOpenPlayer,
                                onClick = onNavigatePlayer,
                                onPlayPause = { playerViewModel.togglePlayPause() },
                                onSkipNext = { playerViewModel.skipToNext() },
                                onSkipPrevious = { playerViewModel.skipToPrevious() },
                                onShowQueue = { queueSheetExpanded = true },
                                onLongClick = if (miniPlayerLongPressSource) onNavigatePlaybackSource else null,
                                lyricProgress = lyricProgress,
                                lyricPositionMs = lyricPositionMs,
                                lyricTiming = lyricTiming,
                            )
                        }
                    }

                    if (showMiniPlayer && showBottomBar && floatingBottomBar) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    AnimatedVisibility(visible = showBottomBar) {
                        if (!floatingBottomBar) {
                            NormalBottomNavigationBar(
                                tabs = tabs,
                                currentTabRoute = currentTabRoute,
                                currentRoute = currentRoute,
                                color = normalDockSurfaceColor,
                                onNavigate = onNavigate,
                                onNavigateSearch = onNavigateSearch
                            )
                        } else if (useGlass) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (tabs.isNotEmpty()) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        val selectedBottomTabIndex = tabs
                                            .indexOfFirst { currentTabRoute == it.route }
                                            .takeIf { it >= 0 }
                                        val barMode = when (glassEffect) {
                                            BottomBarGlassEffect.LiquidGlass -> FloatingBottomBarMode.LiquidGlass
                                            BottomBarGlassEffect.Blur -> FloatingBottomBarMode.Blur
                                        }
                                        FloatingBottomBar(
                                            selectedIndex = { selectedBottomTabIndex ?: 0 },
                                            onSelected = { index ->
                                                tabs.getOrNull(index)?.let { onNavigate(it.route) }
                                            },
                                            backdrop = dockBackdrop,
                                            tabsCount = tabs.size,
                                            mode = barMode,
                                            disableRefraction = stabilizeOverWallpaper
                                        ) {
                                            tabs.forEachIndexed { index, tab ->
                                                val selected = currentTabRoute == tab.route
                                                FloatingBottomBarItem(
                                                    onClick = {
                                                        if (!selected) {
                                                            tabs.getOrNull(index)?.let { onNavigate(it.route) }
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = tab.icon,
                                                        contentDescription = tab.label,
                                                        tint = if (selected) MiuixTheme.colorScheme.primary
                                                        else MiuixTheme.colorScheme.onSurface,
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                    top.yukonga.miuix.kmp.basic.Text(
                                                        text = tab.label,
                                                        fontSize = 11.sp,
                                                        color = if (selected) MiuixTheme.colorScheme.primary
                                                        else MiuixTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                BottomDockActionPill(
                                    icon = MiuixIcons.Basic.Search,
                                    label = stringResource(R.string.common_search),
                                    selected = currentRoute.isSearchRoute(),
                                    onClick = onNavigateSearch,
                                    backdrop = backdrop,
                                    glassEffect = glassEffect,
                                    disableRefraction = stabilizeOverWallpaper,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (queueSheetExpanded) {
        androidx.compose.runtime.key(
            com.ella.music.ui.player.queueSnapshotKey(playlist)
        ) {
            EllaMiuixBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.player_queue_title),
                onDismissRequest = { queueSheetExpanded = false }
            ) {
                com.ella.music.ui.player.PlayerQueueMenu(
                    playlist = playlist,
                    currentSongKey = currentSongKey,
                    currentSongSourceKey = currentSong?.playbackSourceKey,
                    currentQueueIndexHint = currentQueueIndex,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    queueLocked = queueLocked,
                    favoriteSongKeys = favoriteSongKeys,
                    loadSongRating = mainViewModel::getSongRating,
                    ratingRevision = ratingRevision,
                    onCyclePlaybackMode = { playerViewModel.cyclePlaybackMode() },
                    onToggleQueueLock = { playerViewModel.toggleQueueLock() },
                    onSongClick = { index ->
                        queueSheetExpanded = false
                        playerViewModel.playQueueIndex(index)
                    },
                    onRemoveSong = { index -> playerViewModel.removeFromPlaylist(index) },
                    onMoveSong = { fromIndex, toIndex -> playerViewModel.movePlaylistItem(fromIndex, toIndex) },
                    onRandomizeQueue = { playerViewModel.randomizePlaylistOrder() },
                    onAddQueueToPlaylist = {
                        queueSheetExpanded = false
                        queueSongsToAdd = playlist
                    },
                    onClearQueue = {
                        queueSheetExpanded = false
                        playerViewModel.clearPlaylist()
                    },
                    onNavigateToPlaybackSource = {
                        queueSheetExpanded = false
                        onNavigatePlaybackSource()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    queueSongsToAdd?.let { songsToAdd ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.player_add_to_playlist),
            onDismissRequest = { queueSongsToAdd = null }
        ) {
            AddToPlaylistSheet(
                playlists = userPlaylists.sortedWith(
                    compareByDescending<com.ella.music.data.model.UserPlaylist> { it.id == FAVORITES_PLAYLIST_ID }
                        .thenByDescending { it.createdAt }
                ),
                songsToAdd = songsToAdd,
                onDismiss = { queueSongsToAdd = null },
                onCreatePlaylist = {
                    queueSongsForNewPlaylist = songsToAdd
                    queueSongsToAdd = null
                },
                onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                    selectedPlaylists.forEach { target ->
                        mainViewModel.addSongsToPlaylist(target.id, songsToAdd, appendToEnd)
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlists, selectedPlaylists.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    queueSongsToAdd = null
                }
            )
        }
    }

    queueSongsForNewPlaylist?.let { songsToAdd ->
        CreatePlaylistAndAddSheet(
            onDismiss = { queueSongsForNewPlaylist = null },
            onCreate = { name ->
                mainViewModel.createPlaylistOrShowDuplicateToast(context, name) { target ->
                    mainViewModel.addSongsToPlaylist(target.id, songsToAdd)
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlist_named, target.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    queueSongsForNewPlaylist = null
                }
            }
        )
    }
}
}

@Composable
private fun NormalBottomNavigationBar(
    tabs: List<BottomDockTab>,
    currentTabRoute: String?,
    currentRoute: String?,
    color: ComposeColor,
    onNavigate: (String) -> Unit,
    onNavigateSearch: () -> Unit
) {
    NavigationBar(color = color) {
        tabs.forEach { tab ->
            val selected = !currentRoute.isSearchRoute() && currentTabRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onNavigate(tab.route) },
                icon = tab.icon,
                label = tab.label
            )
        }
        NavigationBarItem(
            selected = currentRoute.isSearchRoute(),
            onClick = { if (!currentRoute.isSearchRoute()) onNavigateSearch() },
            icon = MiuixIcons.Basic.Search,
            label = stringResource(R.string.common_search)
        )
    }
}

/**
 * Switches the configured normal bottom-dock tabs with a horizontal swipe on the page.
 *
 * The search destination is appended because it is always the last item in the normal
 * navigation bar, even though it is not part of the user-configurable tab list.
 */
@Composable
internal fun normalBottomDockSwipeModifier(
    enabled: Boolean,
    tabs: List<BottomDockTab>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onNavigateSearch: () -> Unit
): Modifier {
    val latestOnNavigate by rememberUpdatedState(onNavigate)
    val latestOnNavigateSearch by rememberUpdatedState(onNavigateSearch)
    val swipeRoutes = tabs.map { it.route } + Screen.LibrarySearch.createRoute()
    val currentIndex = swipeRoutes.indexOfFirst { currentRoute.matchesRoute(it) }

    if (!enabled || currentIndex < 0 || swipeRoutes.size < 2) return Modifier

    return Modifier.pointerInput(enabled, swipeRoutes, currentRoute) {
        val touchSlop = viewConfiguration.touchSlop
        val swipeThresholdPx = 72.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var lockedHorizontal = false
            var lockedVertical = false
            var cancelled = false
            var totalDx = 0f
            var totalDy = 0f

            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } > 1) {
                    // Do not compete with the library's two-finger pinch gesture.
                    cancelled = true
                    lockedHorizontal = false
                    lockedVertical = true
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val delta = change.position - change.previousPosition
                totalDx += delta.x
                totalDy += delta.y

                if (!cancelled && !lockedHorizontal && !lockedVertical) {
                    val absX = kotlin.math.abs(totalDx)
                    val absY = kotlin.math.abs(totalDy)
                    when {
                        absY > touchSlop && absY > absX * 1.12f -> {
                            // Leave vertical movement to the page's own scroll container.
                            lockedVertical = true
                            cancelled = true
                        }
                        absX > touchSlop && absX > absY * 1.12f -> {
                            lockedHorizontal = true
                            change.consume()
                        }
                    }
                } else if (lockedHorizontal && !cancelled) {
                    change.consume()
                }
            } while (true)

            if (!cancelled && lockedHorizontal && kotlin.math.abs(totalDx) >= swipeThresholdPx) {
                val targetIndex = if (totalDx < 0f) currentIndex + 1 else currentIndex - 1
                swipeRoutes.getOrNull(targetIndex)?.let { targetRoute ->
                    if (targetRoute.startsWith(Screen.LibrarySearch.baseRoute)) {
                        latestOnNavigateSearch()
                    } else {
                        latestOnNavigate(targetRoute)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBottomDock(
    showMiniPlayer: Boolean,
    currentSong: Song?,
    isPlaying: Boolean,
    progress: Float,
    coverRotationEnabled: Boolean,
    lyricText: String?,
    lyricTranslation: String?,
    lyricProgress: Float,
    lyricPositionMs: Long,
    lyricTiming: MiniPlayerLyricTiming?,
    miniPlayerRightButton: Int,
    swipeUpToOpenPlayer: Boolean,
    albumArtUri: Uri?,
    loadCoverArt: ((Song) -> android.graphics.Bitmap?)?,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    glassEffect: BottomBarGlassEffect,
    disableRefraction: Boolean,
    liquidGlass: Boolean,
    searchDock: LibrarySearchDockState?,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onShowQueue: () -> Unit,
    onLongClick: (() -> Unit)?,
    onExitSearch: () -> Unit,
    returnTab: BottomDockTab?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = showMiniPlayer && currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            currentSong?.let { song ->
                MiniPlayer(
                    song = song,
                    isPlaying = isPlaying,
                    progress = progress,
                    coverRotationEnabled = coverRotationEnabled,
                    lyricText = lyricText,
                    lyricTranslation = lyricTranslation,
                    albumArtUri = albumArtUri,
                    loadCoverArt = loadCoverArt,
                    backdrop = backdrop,
                    liquidGlass = liquidGlass,
                    glassEffect = glassEffect,
                    disableRefraction = disableRefraction,
                    showQueueButton = miniPlayerRightButton == SettingsManager.MINI_PLAYER_RIGHT_QUEUE,
                    swipeUpToOpenPlayer = swipeUpToOpenPlayer,
                    onClick = onOpenPlayer,
                    onPlayPause = onPlayPause,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onShowQueue = onShowQueue,
                    onLongClick = onLongClick,
                    lyricProgress = lyricProgress,
                    lyricPositionMs = lyricPositionMs,
                    lyricTiming = lyricTiming
                )
            }
        }
        if (showMiniPlayer && currentSong != null) {
            Spacer(modifier = Modifier.height(4.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomDockActionPill(
                icon = returnTab?.icon ?: MiuixIcons.Regular.Home,
                label = returnTab?.label ?: stringResource(R.string.bottom_dock_tabs),
                selected = false,
                onClick = onExitSearch,
                backdrop = backdrop,
                glassEffect = glassEffect,
                disableRefraction = disableRefraction,
                modifier = Modifier.size(64.dp)
            )
            GlassPill(
                backdrop = backdrop,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                glassEffect = glassEffect,
                disableRefraction = disableRefraction
            ) {
                EllaSearchBar(
                    query = searchDock?.query.orEmpty(),
                    onQueryChange = { value -> searchDock?.query = value },
                    onSearch = { searchDock?.onSearch?.invoke() },
                    placeholder = stringResource(R.string.library_search_dock_placeholder),
                    autoFocus = searchDock?.autoFocus,
                    autoSelectAll = searchDock?.selectAll == true,
                    onAutoSelectAllConsumed = { searchDock?.selectAll = false },
                    containerColor = ComposeColor.Transparent,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CompactBottomDock(
    song: Song,
    isPlaying: Boolean,
    progress: Float,
    lyricText: String?,
    lyricTranslation: String?,
    lyricProgress: Float,
    lyricPositionMs: Long,
    lyricTiming: MiniPlayerLyricTiming?,
    coverRotationEnabled: Boolean,
    swipeUpToOpenPlayer: Boolean,
    albumArtUri: Uri?,
    loadCoverArt: ((Song) -> android.graphics.Bitmap?)?,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    glassEffect: BottomBarGlassEffect,
    currentTab: BottomDockTab?,
    currentTabRoute: String?,
    isSearchSelected: Boolean,
    onOpenPlayer: () -> Unit,
    onOpenPlaybackSource: (() -> Unit)?,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onNavigateSearch: () -> Unit,
    onExpand: () -> Unit,
    disableRefraction: Boolean,
    compactProgress: Float,
) {
    val collapse = compactProgress.coerceIn(0f, 1f)
    // Side actions shrink with the compact spring. The centre mini-player keeps the remaining
    // width so it stays adjacent to both buttons instead of collapsing into a short island.
    val compactControlSize = androidx.compose.ui.unit.lerp(64.dp, 60.dp, collapse)
    val compactIconScale = 1f - 0.14f * collapse
    val showCompactLyrics = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val isHomeSelected = currentTabRoute == Screen.Home.route
    val leftIcon = currentTab?.icon ?: if (isHomeSelected) MiuixIcons.Regular.Home else MiuixIcons.Regular.Music
    val leftLabel = currentTab?.label ?: if (isHomeSelected) stringResource(R.string.tab_home) else stringResource(R.string.tab_library)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomDockActionPill(
            icon = leftIcon,
            label = leftLabel,
            selected = !isSearchSelected,
            onClick = onExpand,
            backdrop = backdrop,
            glassEffect = glassEffect,
            disableRefraction = disableRefraction,
            modifier = Modifier.size(compactControlSize),
            controlSize = compactControlSize,
            contentScale = compactIconScale,
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            CompactMiniPlayer(
                song = song,
                isPlaying = isPlaying,
                progress = progress,
                lyricText = if (showCompactLyrics) lyricText else null,
                lyricTranslation = if (showCompactLyrics) lyricTranslation else null,
                lyricProgress = if (showCompactLyrics) lyricProgress else 0f,
                lyricPositionMs = lyricPositionMs,
                lyricTiming = if (showCompactLyrics) lyricTiming else null,
                coverRotationEnabled = coverRotationEnabled,
                albumArtUri = albumArtUri,
                loadCoverArt = loadCoverArt,
                backdrop = backdrop,
                glassEffect = glassEffect,
                disableRefraction = disableRefraction,
                compactProgress = collapse,
                onClick = onOpenPlayer,
                onLongClick = onOpenPlaybackSource,
                onPlayPause = onPlayPause,
                onSkipNext = onSkipNext,
                showSkipButton = false,
                swipeUpToOpenPlayer = swipeUpToOpenPlayer,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BottomDockActionPill(
            icon = MiuixIcons.Basic.Search,
            label = stringResource(R.string.common_search),
            selected = isSearchSelected,
            onClick = onNavigateSearch,
            backdrop = backdrop,
            glassEffect = glassEffect,
            disableRefraction = disableRefraction,
            modifier = Modifier.size(compactControlSize),
            controlSize = compactControlSize,
            contentScale = compactIconScale,
        )
    }
}

private fun Modifier.consumeBottomDockPassthrough(vararg keys: Any?): Modifier =
    pointerInput(*keys) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Final)
                event.changes.forEach { change ->
                    if (!change.pressed) return@forEach
                    val dx = change.position.x - change.previousPosition.x
                    val dy = change.position.y - change.previousPosition.y
                    val movedX = kotlin.math.abs(dx)
                    val movedY = kotlin.math.abs(dy)
                    val threshold = viewConfiguration.touchSlop
                    if (movedX < threshold && movedY < threshold) return@forEach
                    if (movedY > movedX) {
                        change.consume()
                    }
                }
            }
        }
    }

@Composable
private fun BottomDockActionPill(
    icon: ImageVector? = null,
    painter: Painter? = null,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    glassEffect: BottomBarGlassEffect,
    disableRefraction: Boolean,
    modifier: Modifier = Modifier,
    controlSize: androidx.compose.ui.unit.Dp = 64.dp,
    contentScale: Float = 1f,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pillScale by animateFloatAsState(
        targetValue = if (pressed) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 620f),
        label = "BottomDockActionPillScale"
    )
    val overlayAlpha by animateFloatAsState(
        targetValue = when {
            pressed -> 1f
            selected -> 0.72f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.88f, stiffness = 700f),
        label = "BottomDockActionPillOverlay"
    )
    val isLight = MiuixTheme.colorScheme.background.simpleLuminance() > 0.5f
    val overlayColor = when {
        selected -> if (isLight) ComposeColor.Black.copy(alpha = 0.08f) else ComposeColor.White.copy(alpha = 0.13f)
        isLight -> ComposeColor.White.copy(alpha = 0.32f)
        else -> ComposeColor.White.copy(alpha = 0.16f)
    }
    val bottomBarCornerRadius = LocalBottomBarCornerRadiusDp.current
    val overlayShape = remember(bottomBarCornerRadius) {
        RoundedCornerShape((bottomBarCornerRadius - 4f).coerceAtLeast(0f).dp)
    }

    GlassPill(
        backdrop = backdrop,
        modifier = modifier.size(controlSize),
        glassEffect = glassEffect,
        disableRefraction = disableRefraction
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .graphicsLayer {
                    scaleX = pillScale
                    scaleY = pillScale
                }
                .background(
                    color = overlayColor.copy(alpha = overlayColor.alpha * overlayAlpha),
                    shape = overlayShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            val iconTint = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
            if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp * contentScale)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp * contentScale)
                )
            }
        }
    }
}
