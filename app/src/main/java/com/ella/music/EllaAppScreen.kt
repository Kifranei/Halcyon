package com.ella.music

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.ella.music.data.BottomBarGlassEffect
import com.ella.music.data.SettingsManager
import com.ella.music.data.repository.MusicScanSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.ella.music.ui.components.MiniPlayerLyricTiming
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.TagEditorEditTracker
import com.ella.music.ui.components.updateEllaDynamicShortcuts
import com.ella.music.ui.navigation.AppNavigation
import com.ella.music.ui.navigation.EXTRA_SHORTCUT_ROUTE
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.navigation.EXTRA_SHORTCUT_ACTION
import com.ella.music.ui.navigation.SHORTCUT_ACTION_PLAY
import com.ella.music.ui.navigation.SHORTCUT_ACTION_SHUFFLE_ALL
import com.ella.music.ui.player.PlayerScreen
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun EllaApp(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    isDarkTheme: Boolean
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = when (val route = navBackStackEntry?.destination?.route) {
        Screen.MetadataCategory.route -> navBackStackEntry
            ?.arguments
            ?.getString("type")
            ?.let(Screen.MetadataCategory::createRoute)
            ?: route
        else -> route
    }
    val view = LocalView.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val activity = context as? Activity
    val mainActivity = context as? MainActivity
    val currentProcessingIntent = remember { mutableStateOf(activity?.intent) }
    DisposableEffect(mainActivity) {
        mainActivity?.onNewIntentCallback = { intent -> currentProcessingIntent.value = intent }
        onDispose { mainActivity?.onNewIntentCallback = null }
    }
    var showPlayerOverlay by remember { mutableStateOf(false) }
    var playerDismissProgress by remember { mutableFloatStateOf(0f) }
    var playerOverlayOpenToken by remember { mutableIntStateOf(0) }
    // Keep the player surface resident in the composition tree once it has been opened, and
    // drive open/close with a pure translationY slide instead of adding/removing the whole
    // (very heavy) PlayerScreen each time. This removes the first-composition cost that used
    // to land on the slide-in animation frames and caused a stutter on every open.
    var playerEverShown by remember { mutableStateOf(false) }
    val playerResident = showPlayerOverlay || playerEverShown
    // 0f = fully open (on screen), 1f = fully closed (translated one screen-height down).
    val playerOpenAnim = remember { Animatable(1f) }
    LaunchedEffect(showPlayerOverlay) {
        if (showPlayerOverlay) playerEverShown = true
        playerOpenAnim.animateTo(
            targetValue = if (showPlayerOverlay) 0f else 1f,
            animationSpec = tween(
                durationMillis = if (showPlayerOverlay) 320 else 260,
                easing = if (showPlayerOverlay) FastOutSlowInEasing else LinearOutSlowInEasing
            )
        )
    }
    val isPlayerVisible = showPlayerOverlay || currentRoute == Screen.Player.route
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    val initialScanPromptHandled by settingsManager.initialScanPromptHandled.collectAsState(initial = true)
    val fullTagSearchPromptHandled by settingsManager.fullTagSearchPromptHandled.collectAsState(initial = true)
    val localPlaylistScanPromptHandled by settingsManager.localPlaylistScanPromptHandled.collectAsState(initial = true)
    val autoScanLocalPlaylists by settingsManager.autoScanLocalPlaylists.collectAsState(initial = false)
    val shortcutLibraryLabel by settingsManager.shortcutLibraryLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_LIBRARY_LABEL)
    val shortcutPlaylistsLabel by settingsManager.shortcutPlaylistsLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_PLAYLISTS_LABEL)
    val shortcutFolderLabel by settingsManager.shortcutFolderLabel.collectAsState(initial = SettingsManager.DEFAULT_SHORTCUT_FOLDER_LABEL)
    val appShortcutOrder by settingsManager.appShortcutOrder.collectAsState(initial = SettingsManager.DEFAULT_APP_SHORTCUT_ORDER)
    val isScanning by mainViewModel.isScanning.collectAsState()
    var showInitialScanPrompt by remember { mutableStateOf(false) }
    var showFullTagSearchPrompt by remember { mutableStateOf(false) }
    var showLocalPlaylistScanPrompt by remember { mutableStateOf(false) }
    var localPlaylistAutoScanHandled by rememberSaveable { mutableStateOf(false) }

    // #200: a single scan — or a burst of quick back-to-back scans (e.g. after toggling several
    // folders) — should show the "scanning" / "scan finished" toasts at most once each, not once per
    // individual scan/summary. Treat the whole scanning burst as one unit: show "scanning" on the
    // first rising edge, and "finished" only once the library has stayed idle for a short grace
    // window (LaunchedEffect(isScanning) is cancelled + relaunched whenever isScanning flips, so a
    // new scan starting within the grace window seamlessly extends the same burst).
    var scanBurstActive by remember { mutableStateOf(false) }
    var latestScanSummary by remember { mutableStateOf<MusicScanSummary?>(null) }

    LaunchedEffect(mainViewModel) {
        mainViewModel.scanSummaryEvents.collect { summary -> latestScanSummary = summary }
    }

    LaunchedEffect(isScanning) {
        if (isScanning) {
            if (!scanBurstActive) {
                scanBurstActive = true
                latestScanSummary = null
                Toast.makeText(context, context.getString(R.string.library_scan_started), Toast.LENGTH_SHORT).show()
            }
        } else if (scanBurstActive) {
            delay(900)
            scanBurstActive = false
            latestScanSummary?.let { summary ->
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.library_scan_finished_summary,
                        summary.total,
                        summary.added,
                        summary.updated,
                        summary.deleted
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    LaunchedEffect(currentProcessingIntent.value) {
        val activity = context as? Activity
        val shortcutAction = currentProcessingIntent.value?.resolveShortcutAction().orEmpty()
        when (shortcutAction) {
            SHORTCUT_ACTION_PLAY -> {
                when {
                    playerViewModel.currentSong.value != null || playerViewModel.hasSavedPlaybackQueue() -> {
                        playerViewModel.playRestoredQueue()
                    }
                    else -> {
                        val songs = mainViewModel.songs.first { it.isNotEmpty() }
                        playerViewModel.setPlaylist(songs, 0)
                    }
                }
                runCatching {
                    navController.navigate(Screen.Player.route) {
                        launchSingleTop = true
                    }
                }
            }
            SHORTCUT_ACTION_SHUFFLE_ALL -> {
                val songs = mainViewModel.songs.first { it.isNotEmpty() }
                playerViewModel.setPlaylist(songs.shuffled(), 0)
                runCatching {
                    navController.navigate(Screen.Player.route) {
                        launchSingleTop = true
                    }
                }
            }
        }

        val shortcutRoute = currentProcessingIntent.value?.resolveShortcutRoute().orEmpty()
        if (shortcutRoute.isNotBlank()) {
            runCatching {
                navController.navigate(shortcutRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = false
                    }
                    launchSingleTop = true
                }
            }
        }
        currentProcessingIntent.value?.removeExtra(EXTRA_SHORTCUT_ACTION)
        currentProcessingIntent.value?.removeExtra(EXTRA_SHORTCUT_ROUTE)
        currentProcessingIntent.value?.setData(null)
    }

    LaunchedEffect(appShortcutOrder, shortcutLibraryLabel, shortcutPlaylistsLabel, shortcutFolderLabel) {
        updateEllaDynamicShortcuts(
            context = context,
            shortcutIds = appShortcutOrder,
            libraryLabel = shortcutLibraryLabel,
            searchLabel = shortcutPlaylistsLabel,
            shuffleLabel = shortcutFolderLabel
        )
    }

    val initialScanFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val readOnly = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val readWrite = readOnly or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, readWrite)
        }.recoverCatching {
            context.contentResolver.takePersistableUriPermission(uri, readOnly)
        }
        val folderPath = uri.toPrimaryStoragePath()
        if (folderPath == null) {
            Toast.makeText(context, context.getString(R.string.unsupported_system_folder_path), Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                settingsManager.setUseAndroidMediaLibrary(false)
                settingsManager.setScanIncludeFolders(folderPath)
                settingsManager.setAutoScan(false)
                mainViewModel.scanMusic()
            }
            Toast.makeText(context, context.getString(R.string.scan_folder_added), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isPlayerVisible, isDarkTheme) {
        val window = (view.context as ComponentActivity).window
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = if (isPlayerVisible) false else !isDarkTheme
            isAppearanceLightNavigationBars = if (isPlayerVisible) false else !isDarkTheme
        }
    }

    val showBottomBar = currentRoute.isBottomDockRoute()
    val canCompactBottomDock = showBottomBar
    var bottomDockMode by rememberSaveable { mutableStateOf(BottomDockMode.Expanded) }

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val librarySongs by mainViewModel.songs.collectAsState()

    LaunchedEffect(currentRoute, canCompactBottomDock) {
        bottomDockMode = BottomDockMode.Expanded
    }

    LaunchedEffect(
        libraryCacheLoaded,
        initialScanPromptHandled,
        fullTagSearchPromptHandled,
        isScanning,
        librarySongs
    ) {
        if (!libraryCacheLoaded || initialScanPromptHandled) return@LaunchedEffect
        if (librarySongs.isNotEmpty()) {
            settingsManager.setInitialScanPromptHandled(true)
        } else if (!fullTagSearchPromptHandled) {
            showFullTagSearchPrompt = true
        } else if (!isScanning) {
            showInitialScanPrompt = true
        }
    }

    LaunchedEffect(
        libraryCacheLoaded,
        initialScanPromptHandled,
        fullTagSearchPromptHandled,
        showInitialScanPrompt,
        librarySongs
    ) {
        if (
            libraryCacheLoaded &&
            initialScanPromptHandled &&
            !fullTagSearchPromptHandled &&
            !showInitialScanPrompt &&
            librarySongs.isNotEmpty()
        ) {
            showFullTagSearchPrompt = true
        }
    }

    LaunchedEffect(
        libraryCacheLoaded,
        localPlaylistScanPromptHandled,
        autoScanLocalPlaylists,
        librarySongs,
        showInitialScanPrompt
    ) {
        if (!libraryCacheLoaded || librarySongs.isEmpty() || showInitialScanPrompt) return@LaunchedEffect
        if (!localPlaylistScanPromptHandled) {
            showLocalPlaylistScanPrompt = true
            return@LaunchedEffect
        }
        if (autoScanLocalPlaylists && !localPlaylistAutoScanHandled) {
            localPlaylistAutoScanHandled = true
            mainViewModel.scanLocalPlaylistFiles()
        }
    }

    DisposableEffect(lifecycleOwner, mainViewModel, playerViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                playerViewModel.ensurePlayerConnected()
                TagEditorEditTracker.consume()?.let { editedSong ->
                    mainViewModel.refreshSongAfterExternalEdit(editedSong) { updatedSong ->
                        playerViewModel.refreshCurrentSongAfterExternalEdit(updatedSong)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    val lyrics by playerViewModel.lyrics.collectAsState()
    val currentLyricIndex by playerViewModel.currentLyricIndex.collectAsState()
    val miniPlayerLyricSecondary by settingsManager.miniPlayerLyricSecondary.collectAsState(initial = SettingsManager.LYRIC_SECONDARY_TRANSLATION)
    val miniPlayerCoverRotation by settingsManager.miniPlayerCoverRotation.collectAsState(initial = true)
    val miniPlayerLyricsEnabled by settingsManager.miniPlayerLyricsEnabled.collectAsState(initial = true)
    val miniPlayerRightButton by settingsManager.miniPlayerRightButton.collectAsState(initial = 0)
    val bottomBarGlassEffect by settingsManager.bottomBarGlassEffect.collectAsState(initial = BottomBarGlassEffect.LiquidGlass)
    val bottomDockItemIds by settingsManager.bottomDockItems.collectAsState(
        initial = SettingsManager.DEFAULT_BOTTOM_DOCK_ITEMS.split(',')
    )
    val appWallpaperEnabled by settingsManager.appWallpaperEnabled.collectAsState(initial = false)
    val appWallpaperUri by settingsManager.appWallpaperUri.collectAsState(initial = "")
    val appWallpaperOpacity by settingsManager.appWallpaperOpacity.collectAsState(initial = 100)
    val appWallpaperDim by settingsManager.appWallpaperDim.collectAsState(initial = 30)
    val startupPosterEnabled by settingsManager.startupPosterEnabled.collectAsState(initial = false)
    val startupPosterUri by settingsManager.startupPosterUri.collectAsState(initial = "")
    val startupPosterDurationMs by settingsManager.startupPosterDurationMs.collectAsState(
        initial = SettingsManager.DEFAULT_STARTUP_POSTER_DURATION_MS
    )
    val notificationPermissionPromptHandled by settingsManager.notificationPermissionPromptHandled.collectAsState(initial = false)
    var showStartupPoster by rememberSaveable { mutableStateOf(true) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scope.launch { settingsManager.setNotificationPermissionPromptHandled(true) }
        if (!granted) {
            Toast.makeText(
                context,
                context.getString(R.string.notification_permission_denied_hint),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(startupPosterEnabled, startupPosterUri, startupPosterDurationMs) {
        if (startupPosterEnabled && startupPosterUri.isNotBlank() && showStartupPoster) {
            kotlinx.coroutines.delay(startupPosterDurationMs.toLong())
            showStartupPoster = false
        }
    }

    val currentLyricLine = lyrics.getOrNull(currentLyricIndex)
    val isTabletDevice = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val allowTabletExpandedMiniLyrics = isTabletDevice && bottomDockMode == BottomDockMode.Expanded
    val miniPlayerLyricsVisible = miniPlayerLyricsEnabled || allowTabletExpandedMiniLyrics
    val miniPlayerLyricText = if (isPlaying && miniPlayerLyricsVisible) {
        currentLyricLine?.text?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
    } else {
        null
    }
    val miniPlayerLyricSecondaryText = if (isPlaying && miniPlayerLyricsVisible) {
        when (miniPlayerLyricSecondary) {
            SettingsManager.LYRIC_SECONDARY_TRANSLATION -> currentLyricLine?.translation?.takeIf { it.isNotBlank() }
            SettingsManager.LYRIC_SECONDARY_PRONUNCIATION -> currentLyricLine?.pronunciation?.takeIf { it.isNotBlank() }
            else -> null
        }
    } else {
        null
    }

    val nextLyricLine = lyrics.getOrNull(currentLyricIndex + 1)
    val miniPlayerLyricTiming = if (isPlaying && miniPlayerLyricsVisible && currentLyricLine != null) {
        val lineStartMs = currentLyricLine.timeMs
        MiniPlayerLyricTiming(
            lineStartMs = lineStartMs,
            lineEndMs = currentLyricLine.endMs
                ?: nextLyricLine?.timeMs
                ?: (lineStartMs + 5_000L),
            words = currentLyricLine.words
        )
    } else {
        null
    }

    val miniPlayerLyricProgress = miniPlayerLyricTiming?.progressAt(currentPosition) ?: 0f

    val showMiniPlayer = currentSong != null &&
        currentRoute != Screen.Player.route &&
        currentRoute != Screen.AiChat.route &&
        !showPlayerOverlay
    LaunchedEffect(showMiniPlayer, canCompactBottomDock) {
        if (!showMiniPlayer || !canCompactBottomDock) bottomDockMode = BottomDockMode.Expanded
    }

    val dockScrollConnection = remember(showMiniPlayer, canCompactBottomDock) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!showMiniPlayer || !canCompactBottomDock || source != NestedScrollSource.UserInput) return Offset.Zero
                when {
                    available.y < -12f -> bottomDockMode = BottomDockMode.Compact
                    available.y > 16f -> bottomDockMode = BottomDockMode.Expanded
                }
                return Offset.Zero
            }
        }
    }

    val backdrop = rememberLayerBackdrop()
    val useGlass = true
    val bottomDockSpecs = bottomDockTabCatalog()
    val tabs = bottomDockItemIds
        .mapNotNull { bottomDockSpecs[it] }
        .take(SettingsManager.MAX_BOTTOM_DOCK_ITEMS)
        .ifEmpty {
            listOfNotNull(
                bottomDockSpecs[SettingsManager.BOTTOM_DOCK_ITEM_HOME],
                bottomDockSpecs[SettingsManager.BOTTOM_DOCK_ITEM_LIBRARY],
                bottomDockSpecs[SettingsManager.BOTTOM_DOCK_ITEM_SETTINGS],
                bottomDockSpecs[SettingsManager.BOTTOM_DOCK_ITEM_PLAYLISTS]
            )
        }
    val currentTabRoute = currentRoute.toCurrentTabRoute()

    val wallpaperVisible = appWallpaperEnabled && appWallpaperUri.isNotBlank()
    val startupPosterVisible = startupPosterEnabled && startupPosterUri.isNotBlank() && showStartupPoster
    LaunchedEffect(startupPosterVisible, notificationPermissionPromptHandled) {
        if (startupPosterVisible) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        if (notificationPermissionPromptHandled) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            scope.launch { settingsManager.setNotificationPermissionPromptHandled(true) }
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val contentModifier = Modifier
        .fillMaxSize()
        .then(if (wallpaperVisible) Modifier else Modifier.background(MiuixTheme.colorScheme.background))
        .layerBackdrop(backdrop)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
    ) {
        if (startupPosterVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeColor.Black)
            ) {
                SafeCoverImage(
                    model = Uri.parse(startupPosterUri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    sizePx = 1800,
                    showDefaultPlaceholder = false
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ComposeColor.Black.copy(alpha = 0.10f))
                )
            }
        } else {
            if (wallpaperVisible) {
                val wallpaperDimAlpha = appWallpaperDim.coerceIn(0, 80) / 100f
                val wallpaperWash = if (isDarkTheme) ComposeColor.Black else ComposeColor.White
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = appWallpaperOpacity.coerceIn(20, 100) / 100f }
                ) {
                    SafeCoverImage(
                        model = Uri.parse(appWallpaperUri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        sizePx = 1600,
                        showDefaultPlaceholder = false
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        wallpaperWash.copy(alpha = wallpaperDimAlpha * 0.95f),
                                        wallpaperWash.copy(alpha = wallpaperDimAlpha * 0.55f),
                                        wallpaperWash.copy(alpha = (wallpaperDimAlpha * 1.15f).coerceAtMost(0.9f))
                                    )
                                )
                            )
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                AppNavigation(
                    navController = navController,
                    mainViewModel = mainViewModel,
                    playerViewModel = playerViewModel,
                    modifier = contentModifier.nestedScroll(dockScrollConnection),
                    onNavigateToPlayer = {
                        playerDismissProgress = 0f
                        playerOverlayOpenToken++
                        showPlayerOverlay = true
                    }
                )
                FloatingBottomControls(
                    showMiniPlayer = showMiniPlayer,
                    showBottomBar = showBottomBar,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    coverRotationEnabled = miniPlayerCoverRotation,
                    currentPosition = currentPosition,
                    duration = duration,
                    lyricText = miniPlayerLyricText,
                    lyricTranslation = miniPlayerLyricSecondaryText,
                    lyricProgress = miniPlayerLyricProgress,
                    lyricPositionMs = currentPosition,
                    lyricTiming = miniPlayerLyricTiming,
                    miniPlayerRightButton = miniPlayerRightButton,
                    tabs = tabs,
                    currentTabRoute = currentTabRoute,
                    currentRoute = currentRoute,
                    bottomDockMode = bottomDockMode,
                    canCompact = canCompactBottomDock,
                    backdrop = backdrop,
                    glassEffect = bottomBarGlassEffect,
                    mainViewModel = mainViewModel,
                    playerViewModel = playerViewModel,
                    onNavigate = { route ->
                        bottomDockMode = BottomDockMode.Expanded
                        if (!currentRoute.matchesRoute(route)) {
                            navController.navigateBottomDockRoute(route, currentRoute)
                        }
                    },
                    onNavigateSearch = {
                        bottomDockMode = BottomDockMode.Expanded
                        val route = Screen.LibrarySearch.createRoute()
                        if (!currentRoute.matchesRoute(route)) {
                            navController.navigateBottomDockRoute(route, currentRoute)
                        }
                    },
                    onNavigatePlayer = {
                        playerDismissProgress = 0f
                        playerOverlayOpenToken++
                        showPlayerOverlay = true
                    },
                    onExpand = {
                        bottomDockMode = BottomDockMode.Expanded
                    },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                )
            }
            if (playerResident) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = playerOpenAnim.value * size.height
                        }
                ) {
                PlayerScreen(
                    mainViewModel = mainViewModel,
                    playerViewModel = playerViewModel,
                    playerVisible = showPlayerOverlay,
                    onBack = {
                        playerViewModel.setShowLyrics(false)
                        showPlayerOverlay = false
                        playerDismissProgress = 0f
                    },
                    onNavigateToAlbum = { albumId ->
                        playerViewModel.setShowLyrics(false)
                        showPlayerOverlay = false
                        playerDismissProgress = 0f
                        navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                    },
                    onNavigateToArtist = { artistName ->
                        playerViewModel.setShowLyrics(false)
                        showPlayerOverlay = false
                        playerDismissProgress = 0f
                        navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                    },
                    onNavigateToMetadataCategory = { type, name ->
                        playerViewModel.setShowLyrics(false)
                        showPlayerOverlay = false
                        playerDismissProgress = 0f
                        navController.navigate(Screen.MetadataCategoryDetail.createRoute(type, name))
                    },
                    onNavigateToEqualizer = {
                        playerViewModel.setShowLyrics(false)
                        showPlayerOverlay = false
                        playerDismissProgress = 0f
                        navController.navigate(Screen.Equalizer.createRoute())
                    },
                    onDismissProgressChange = { progress ->
                        playerDismissProgress = progress
                    },
                    openToken = playerOverlayOpenToken
                )
                }
            }

            InitialScanPromptDialog(
                show = showInitialScanPrompt,
                onDismiss = {
                    showInitialScanPrompt = false
                    scope.launch {
                        settingsManager.setInitialScanPromptHandled(true)
                        settingsManager.setAutoScan(false)
                    }
                },
                onCustomFolderScan = {
                    showInitialScanPrompt = false
                    scope.launch {
                        settingsManager.setInitialScanPromptHandled(true)
                        settingsManager.setUseAndroidMediaLibrary(false)
                        settingsManager.setAutoScan(false)
                    }
                    initialScanFolderPicker.launch(null)
                },
                onMediaLibraryScan = {
                    showInitialScanPrompt = false
                    scope.launch {
                        settingsManager.setInitialScanPromptHandled(true)
                        settingsManager.setUseAndroidMediaLibrary(true)
                        settingsManager.setAutoScan(false)
                        mainViewModel.scanMusic()
                    }
                }
            )

            FullTagSearchPromptDialog(
                show = showFullTagSearchPrompt,
                onChoose = { enabled ->
                    showFullTagSearchPrompt = false
                    scope.launch {
                        settingsManager.setFullTagSearchEnabled(enabled)
                        settingsManager.setFullTagSearchPromptHandled(true)
                        // On a fresh library, choose the scan strategy before showing the
                        // initial scan confirmation so the first scan never uses the old mode.
                        if (!initialScanPromptHandled && librarySongs.isEmpty()) {
                            showInitialScanPrompt = true
                        }
                    }
                }
            )

            LocalPlaylistScanPromptDialog(
                show = showLocalPlaylistScanPrompt,
                onDismiss = {
                    showLocalPlaylistScanPrompt = false
                    scope.launch {
                        settingsManager.setLocalPlaylistScanPromptHandled(true)
                        settingsManager.setAutoScanLocalPlaylists(false)
                    }
                },
                onScan = {
                    showLocalPlaylistScanPrompt = false
                    scope.launch {
                        settingsManager.setLocalPlaylistScanPromptHandled(true)
                        settingsManager.setAutoScanLocalPlaylists(true)
                    }
                    mainViewModel.scanLocalPlaylistFiles { result ->
                        result
                            .onSuccess { importResult ->
                                val message = if (importResult.importedPlaylists == 0) {
                                    context.getString(R.string.local_playlist_scan_none)
                                } else {
                                    context.getString(
                                        R.string.playlist_import_result,
                                        context.getString(
                                            R.string.playlist_import_playlist_prefix,
                                            importResult.importedPlaylists
                                        ),
                                        importResult.importedCount,
                                        importResult.matchedCount,
                                        if (importResult.missingCount > 0) {
                                            context.getString(R.string.playlist_import_missing_paths, importResult.missingCount)
                                        } else {
                                            ""
                                        },
                                        if (importResult.duplicateCount > 0) {
                                            context.getString(R.string.playlist_import_duplicates, importResult.duplicateCount)
                                        } else {
                                            ""
                                        }
                                    )
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.playlist_import_failed, it.message.orEmpty()),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                }
            )
        }
    }
}
