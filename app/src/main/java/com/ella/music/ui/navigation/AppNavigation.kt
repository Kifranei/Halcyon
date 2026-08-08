package com.ella.music.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ella.music.data.SettingsManager
import com.ella.music.data.remote.RemoteMusicProvider
import com.ella.music.ui.about.AboutScreen
import com.ella.music.ui.about.UpdateScreen
import com.ella.music.ui.analytics.AnalyticsScreen
import com.ella.music.ui.analytics.LibraryAnalysisScreen
import com.ella.music.ui.analytics.PlaybackHistoryScreen
import com.ella.music.ui.ai.AiChatScreen
import com.ella.music.ui.album.AlbumDetailScreen
import com.ella.music.ui.album.AlbumScreen
import com.ella.music.ui.artist.ArtistListScreen
import com.ella.music.ui.artist.ArtistScreen
import com.ella.music.ui.category.MetadataCategoryDetailScreen
import com.ella.music.ui.category.MetadataCategoryScreen
import com.ella.music.ui.folder.FolderDetailScreen
import com.ella.music.ui.folder.FolderPlaylistDetailScreen
import com.ella.music.ui.folder.FolderPlaylistsScreen
import com.ella.music.ui.folder.FolderScreen
import com.ella.music.ui.folder.ScanSettingsScreen
import com.ella.music.ui.folder.WebDavScreen
import com.ella.music.ui.home.HomeScreen
import com.ella.music.ui.home.LibraryScreen
import com.ella.music.ui.online.LxOnlineScreen
import com.ella.music.ui.online.LxSourceSettingsScreen
import com.ella.music.ui.online.RemoteServerSettingsScreen
import com.ella.music.ui.playlist.PlaylistDetailScreen
import com.ella.music.ui.playlist.PlaylistScreen
import com.ella.music.ui.search.LibrarySearchScreen
import com.ella.music.ui.settings.AudioSettingsScreen
import com.ella.music.ui.settings.EqualizerScreen
import com.ella.music.ui.settings.BackupSettingsScreen
import com.ella.music.ui.settings.BottomNavigationSettingsScreen
import com.ella.music.ui.settings.LyricFontScreen
import com.ella.music.ui.settings.LyricPluginSourceSettingsScreen
import com.ella.music.ui.settings.LogScreen
import com.ella.music.ui.settings.LastFmSettingsScreen
import com.ella.music.ui.settings.SettingsDetailScreen
import com.ella.music.ui.settings.SettingsDetailMode
import com.ella.music.ui.settings.SettingsScreen
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel

private const val AlbumListRestoreScrollRequestKey = "album_list_restore_scroll_request"
private const val AlbumListRestoreAnchorIdKey = "album_list_restore_anchor_id"
private const val AlbumListRestoreAnchorOffsetKey = "album_list_restore_anchor_offset"

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Library : Screen("library")
    data object LibrarySearch : Screen("library_search?type={type}&keyword={keyword}&focus={focus}") {
        const val baseRoute = "library_search"
        fun createRoute(type: String? = null, keyword: String? = null, focus: Boolean = false): String {
            val params = buildList {
                type?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    add("type=${java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")}")
                }
                keyword?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    add("keyword=${java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")}")
                }
                if (focus) add("focus=true")
            }
            return if (params.isEmpty()) baseRoute else "$baseRoute?${params.joinToString("&")}"
        }
    }
    data object Album : Screen("album?fromDock={fromDock}") {
        const val baseRoute = "album"
        fun createRoute(fromDock: Boolean = false) = "$baseRoute?fromDock=$fromDock"
    }
    data object Artist : Screen("artist?fromDock={fromDock}") {
        const val baseRoute = "artist"
        fun createRoute(fromDock: Boolean = false) = "$baseRoute?fromDock=$fromDock"
    }
    data object AlbumDetail : Screen("album/{albumId}") {
        fun createRoute(albumId: Long) = "album/$albumId"
    }
    data object ArtistDetail : Screen("artist/{artistName}") {
        fun createRoute(artistName: String) = "artist/${java.net.URLEncoder.encode(artistName, "UTF-8")}"
    }
    data object Folder : Screen("folder?fromDock={fromDock}") {
        const val baseRoute = "folder"
        fun createRoute(fromDock: Boolean = false) = "$baseRoute?fromDock=$fromDock"
    }
    data object ScanSettings : Screen("scan_settings?highlight={highlight}&fromDock={fromDock}") {
        const val baseRoute = "scan_settings"
        fun createRoute(highlight: String = "", fromDock: Boolean = false): String {
            val encodedHighlight = java.net.URLEncoder.encode(highlight, "UTF-8")
            return "$baseRoute?highlight=$encodedHighlight&fromDock=$fromDock"
        }
    }
    data object MetadataCategory : Screen("category/{type}?fromDock={fromDock}") {
        const val baseRoute = "category"
        fun createRoute(type: String, fromDock: Boolean = false): String {
            val route = "$baseRoute/${java.net.URLEncoder.encode(type, "UTF-8")}"
            return if (fromDock) "$route?fromDock=true" else route
        }
    }
    data object MetadataCategoryDetail : Screen("category/{type}/{name}") {
        fun createRoute(type: String, name: String) =
            "category/${java.net.URLEncoder.encode(type, "UTF-8")}/${java.net.URLEncoder.encode(name, "UTF-8")}"
    }
    data object Playlists : Screen("playlists?fromDock={fromDock}") {
        const val baseRoute = "playlists"
        fun createRoute(fromDock: Boolean = false) = "$baseRoute?fromDock=$fromDock"
    }
    data object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist/${java.net.URLEncoder.encode(playlistId, "UTF-8")}"
    }
    data object WebDav : Screen("webdav")
    data object FolderDetail : Screen("folder/{folderPath}") {
        fun createRoute(folderPath: String) = "folder/${java.net.URLEncoder.encode(folderPath, "UTF-8")}"
    }
    data object FolderPlaylists : Screen("folder_playlists")
    data object FolderPlaylistDetail : Screen("folder_playlist/{playlistId}") {
        fun createRoute(playlistId: String) = "folder_playlist/${java.net.URLEncoder.encode(playlistId, "UTF-8")}"
    }
    data object LibraryAnalysis : Screen("library_analysis")
    data object Settings : Screen("settings?fromDock={fromDock}") {
        const val baseRoute = "settings"
        fun createRoute(fromDock: Boolean = false) = "$baseRoute?fromDock=$fromDock"
    }
    data object SettingsDetail : Screen("settings_detail?highlight={highlight}") {
        fun createRoute(highlight: String = "") = "settings_detail?highlight=${java.net.URLEncoder.encode(highlight, "UTF-8")}"
    }
    data object HomeDisplaySettings : Screen("settings_home_display?highlight={highlight}") {
        fun createRoute(highlight: String = "") = "settings_home_display?highlight=${java.net.URLEncoder.encode(highlight, "UTF-8")}"
    }
    data object BottomNavigationSettings : Screen("settings_bottom_navigation")
    data object LibrarySettings : Screen("library_settings?highlight={highlight}") {
        fun createRoute(highlight: String = "") = "library_settings?highlight=${java.net.URLEncoder.encode(highlight, "UTF-8")}"
    }
    data object IntegrationSettings : Screen("integration_settings?highlight={highlight}") {
        fun createRoute(highlight: String = ""): String {
            val encodedHighlight = java.net.URLEncoder.encode(highlight, "UTF-8")
            return "integration_settings?highlight=$encodedHighlight"
        }
    }
    data object LastFmSettings : Screen("lastfm_settings")
    data object LyricSettings : Screen("lyric_settings?highlight={highlight}") {
        fun createRoute(highlight: String = "") = "lyric_settings?highlight=${java.net.URLEncoder.encode(highlight, "UTF-8")}"
    }
    data object LyricPluginSources : Screen("lyric_plugin_sources")
    data object AudioSettings : Screen("audio_settings?highlight={highlight}") {
        fun createRoute(highlight: String = "") = "audio_settings?highlight=${java.net.URLEncoder.encode(highlight, "UTF-8")}"
    }
    data object Equalizer : Screen("equalizer?highlight={highlight}") {
        fun createRoute(highlight: String = "") = "equalizer?highlight=${java.net.URLEncoder.encode(highlight, "UTF-8")}"
    }
    data object BackupSettings : Screen("backup_settings?highlight={highlight}") {
        fun createRoute(highlight: String = "") = "backup_settings?highlight=${java.net.URLEncoder.encode(highlight, "UTF-8")}"
    }
    data object LyricFont : Screen("lyric_font")
    data object Logs : Screen("logs")
    data object LxOnline : Screen("lx_online")
    data object LxSourceSettings : Screen("lx_source_settings")
    data object NavidromeServerSettings : Screen("navidrome_server_settings")
    data object OpenSubsonicServerSettings : Screen("opensubsonic_server_settings")
    data object EmbyServerSettings : Screen("emby_server_settings")
    data object Analytics : Screen("analytics")
    data object AiChat : Screen("ai_chat")
    data object PlaybackHistory : Screen("playback_history")
    data object About : Screen("about")
    data object Update : Screen("update")
    data object Player : Screen("player")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    initialBottomDockItems: List<String> = SettingsManager.DEFAULT_BOTTOM_DOCK_ITEMS.split(','),
    modifier: Modifier = Modifier,
    onNavigateToPlayer: () -> Unit = {}
) {
    val bottomDockItems by mainViewModel.settingsManager.bottomDockItems.collectAsState(
        initial = initialBottomDockItems
    )
    fun isDockItem(itemId: String): Boolean = itemId in bottomDockItems

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(300)) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start, tween(300)
            )
        },
        exitTransition = { fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { fadeIn(animationSpec = tween(300)) },
        popExitTransition = {
            fadeOut(animationSpec = tween(300)) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End, tween(300)
            )
        }
    ) {
        fun navigateRestorableTopLevel(route: String) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        composable(Screen.Home.route) {
            HomeScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onNavigateToLibrary = { navigateRestorableTopLevel(Screen.Library.route) },
                onNavigateToArtist = { navigateRestorableTopLevel(Screen.Artist.createRoute()) },
                onNavigateToAlbum = { navigateRestorableTopLevel(Screen.Album.createRoute()) },
                onNavigateToFolder = { navigateRestorableTopLevel(Screen.Folder.createRoute()) },
                onNavigateToFolderPlaylists = { navigateRestorableTopLevel(Screen.FolderPlaylists.route) },
                onNavigateToPlaylists = { navigateRestorableTopLevel(Screen.Playlists.createRoute()) },
                onNavigateToLxOnline = { navController.navigate(Screen.LxOnline.route) },
                onNavigateToWebDav = { navController.navigate(Screen.WebDav.route) },
                onNavigateToAnalytics = { navController.navigate(Screen.Analytics.route) },
                onNavigateToAiChat = { navController.navigate(Screen.AiChat.route) },
                onNavigateToMetadataCategory = { type -> navigateRestorableTopLevel(Screen.MetadataCategory.createRoute(type)) },
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToSettings = { navController.navigate(Screen.Settings.createRoute()) }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onNavigateToSearch = { navController.navigate(Screen.LibrarySearch.createRoute()) },
                onNavigateToAlbum = { albumId -> navController.navigate(Screen.AlbumDetail.createRoute(albumId)) },
                onNavigateToArtist = { artistName -> navController.navigate(Screen.ArtistDetail.createRoute(artistName)) }
            )
        }

        composable(
            route = Screen.LibrarySearch.route,
            arguments = listOf(
                navArgument("type") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("keyword") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("focus") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            LibrarySearchScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                initialFilterType = backStackEntry.arguments?.getString("type"),
                initialQuery = backStackEntry.arguments?.getString("keyword"),
                autoFocusSearch = backStackEntry.arguments?.getBoolean("focus") == true,
                showBackButton = false,
                onBack = { navController.popBackStack() },
                onNavigateToAlbum = { albumId -> navController.navigate(Screen.AlbumDetail.createRoute(albumId)) },
                onNavigateToArtist = { artistName -> navController.navigate(Screen.ArtistDetail.createRoute(artistName)) },
                onNavigateToPlaylist = { playlistId -> navController.navigate(Screen.PlaylistDetail.createRoute(playlistId)) },
                onNavigateToMetadataCategory = { type, name ->
                    navController.navigate(Screen.MetadataCategoryDetail.createRoute(type, name))
                },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(
            route = Screen.Album.route,
            arguments = listOf(navArgument("fromDock") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
            val fromDock = backStackEntry.arguments?.getBoolean("fromDock") == true
            val restoreScrollRequest by backStackEntry.savedStateHandle
                .getStateFlow(AlbumListRestoreScrollRequestKey, 0)
                .collectAsState()
            val restoreAnchorAlbumId by backStackEntry.savedStateHandle
                .getStateFlow<Long?>(AlbumListRestoreAnchorIdKey, null)
                .collectAsState()
            val restoreAnchorOffset by backStackEntry.savedStateHandle
                .getStateFlow(AlbumListRestoreAnchorOffsetKey, 0)
                .collectAsState()
            AlbumScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                showBackButton = !(fromDock && isDockItem(SettingsManager.BOTTOM_DOCK_ITEM_ALBUM)),
                onBack = { navController.popBackStack() },
                onAlbumClick = { albumId, anchorAlbumId, anchorOffset ->
                    backStackEntry.savedStateHandle[AlbumListRestoreAnchorIdKey] = anchorAlbumId
                    backStackEntry.savedStateHandle[AlbumListRestoreAnchorOffsetKey] = anchorOffset
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                },
                restoreScrollRequest = restoreScrollRequest,
                restoreAnchorAlbumId = restoreAnchorAlbumId,
                restoreAnchorOffset = restoreAnchorOffset
            )
        }

        composable(
            route = Screen.Artist.route,
            arguments = listOf(navArgument("fromDock") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
            val fromDock = backStackEntry.arguments?.getBoolean("fromDock") == true
            ArtistListScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                showBackButton = !(fromDock && isDockItem(SettingsManager.BOTTOM_DOCK_ITEM_ARTIST)),
                onBack = { navController.popBackStack() },
                onArtistClick = { artistName ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                }
            )
        }

        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
            AlbumDetailScreen(
                albumId = albumId,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = {
                    val previousEntry = navController.previousBackStackEntry
                    if (previousEntry?.destination?.route == Screen.Album.route) {
                        val nextRequest = previousEntry.savedStateHandle
                            .get<Int>(AlbumListRestoreScrollRequestKey)
                            ?: 0
                            .plus(1)
                        previousEntry.savedStateHandle[AlbumListRestoreScrollRequestKey] = nextRequest
                    }
                    navController.popBackStack()
                },
                onNavigateToAlbum = { targetAlbumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(targetAlbumId))
                },
                onNavigateToArtist = { artistName ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                },
                onNavigateToMetadataCategory = { type, name ->
                    navController.navigate(Screen.MetadataCategoryDetail.createRoute(type, name))
                },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(
            route = Screen.Folder.route,
            arguments = listOf(navArgument("fromDock") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
            val fromDock = backStackEntry.arguments?.getBoolean("fromDock") == true
            FolderScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                showBackButton = !(fromDock && isDockItem(SettingsManager.BOTTOM_DOCK_ITEM_FOLDER_TREE)),
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToLibraryAnalysis = { navController.navigate(Screen.LibraryAnalysis.route) },
                onNavigateToScanSettings = { navController.navigate(Screen.ScanSettings.createRoute()) },
                onFolderClick = { folderPath ->
                    navController.navigate(Screen.FolderDetail.createRoute(folderPath))
                }
            )
        }

        composable(
            route = Screen.ScanSettings.route,
            arguments = listOf(
                navArgument("highlight") { defaultValue = "" },
                navArgument("fromDock") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val fromDock = backStackEntry.arguments?.getBoolean("fromDock") == true
            ScanSettingsScreen(
                mainViewModel = mainViewModel,
                showBackButton = !(fromDock && isDockItem(SettingsManager.BOTTOM_DOCK_ITEM_SCAN_SETTINGS)),
                onBack = { navController.popBackStack() },
                highlightKey = backStackEntry.arguments?.getString("highlight").orEmpty()
            )
        }

        composable(Screen.FolderPlaylists.route) {
            FolderPlaylistsScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onOpenPlaylist = { playlistId ->
                    navController.navigate(Screen.FolderPlaylistDetail.createRoute(playlistId))
                }
            )
        }

        composable(
            route = Screen.FolderPlaylistDetail.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val playlistId = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("playlistId").orEmpty(),
                "UTF-8"
            )
            FolderPlaylistDetailScreen(
                playlistId = playlistId,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToFolder = { path ->
                    navController.navigate(Screen.FolderDetail.createRoute(path))
                },
                onNavigateToAlbum = { albumId -> navController.navigate(Screen.AlbumDetail.createRoute(albumId)) },
                onNavigateToArtist = { artistName -> navController.navigate(Screen.ArtistDetail.createRoute(artistName)) }
            )
        }

        composable(
            route = Screen.MetadataCategory.route,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("fromDock") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val type = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("type").orEmpty(),
                "UTF-8"
            )
            val fromDock = backStackEntry.arguments?.getBoolean("fromDock") == true
            MetadataCategoryScreen(
                type = type,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                showBackButton = !(fromDock && type.bottomDockItemIdForMetadataCategory() in bottomDockItems),
                onBack = { navController.popBackStack() },
                onCategoryClick = { name ->
                    navController.navigate(Screen.MetadataCategoryDetail.createRoute(type, name))
                }
            )
        }

        composable(
            route = Screen.MetadataCategoryDetail.route,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val type = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("type").orEmpty(),
                "UTF-8"
            )
            val name = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("name").orEmpty(),
                "UTF-8"
            )
            MetadataCategoryDetailScreen(
                type = type,
                name = name,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onAlbumClick = { albumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                },
                onArtistClick = { artistName ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                },
                onMetadataCategoryClick = { categoryType, categoryName ->
                    navController.navigate(Screen.MetadataCategoryDetail.createRoute(categoryType, categoryName))
                },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(
            route = Screen.Playlists.route,
            arguments = listOf(navArgument("fromDock") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
            val fromDock = backStackEntry.arguments?.getBoolean("fromDock") == true
            PlaylistScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                showBackButton = !(fromDock && isDockItem(SettingsManager.BOTTOM_DOCK_ITEM_PLAYLISTS)),
                onBack = { navController.popBackStack() },
                onPlaylistClick = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                }
            )
        }

        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val playlistId = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("playlistId").orEmpty(),
                "UTF-8"
            )
            PlaylistDetailScreen(
                playlistId = playlistId,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToAlbum = { albumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                },
                onNavigateToArtist = { artistName ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(Screen.WebDav.route) {
            WebDavScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(
            route = Screen.ArtistDetail.route,
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) { backStackEntry ->
            val artistName = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("artistName") ?: "",
                "UTF-8"
            )
            ArtistScreen(
                artistName = artistName,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onAlbumClick = { albumId -> navController.navigate(Screen.AlbumDetail.createRoute(albumId)) },
                onArtistClick = { targetArtist -> navController.navigate(Screen.ArtistDetail.createRoute(targetArtist)) },
                onMetadataCategoryClick = { type, name ->
                    navController.navigate(Screen.MetadataCategoryDetail.createRoute(type, name))
                },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(
            route = Screen.FolderDetail.route,
            arguments = listOf(navArgument("folderPath") { type = NavType.StringType })
        ) { backStackEntry ->
            val folderPath = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("folderPath") ?: "",
                "UTF-8"
            )
            FolderDetailScreen(
                folderPath = folderPath,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToAlbum = { albumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                },
                onNavigateToArtist = { artistName ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                },
                onFolderClick = { childFolderPath ->
                    navController.navigate(Screen.FolderDetail.createRoute(childFolderPath))
                },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(
            route = Screen.Settings.route,
            arguments = listOf(navArgument("fromDock") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
            val fromDock = backStackEntry.arguments?.getBoolean("fromDock") == true
            SettingsScreen(
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onNavigateToAppearanceSettings = { navController.navigate(Screen.SettingsDetail.createRoute()) },
                onNavigateToLibrarySettings = { navController.navigate(Screen.LibrarySettings.createRoute()) },
                onNavigateToIntegrationSettings = { navController.navigate(Screen.IntegrationSettings.createRoute()) },
                onNavigateToLyricSettings = { navController.navigate(Screen.LyricSettings.createRoute()) },
                onNavigateToAudioSettings = { navController.navigate(Screen.AudioSettings.createRoute()) },
                onNavigateToBackupSettings = { navController.navigate(Screen.BackupSettings.createRoute()) },
                onNavigateToLogs = { navController.navigate(Screen.Logs.route) },
                onNavigateToBottomNavigationSettings = {
                    navController.navigate(Screen.BottomNavigationSettings.route)
                },
                onNavigateToHomeDisplaySettings = { highlight ->
                    navController.navigate(Screen.HomeDisplaySettings.createRoute(highlight))
                },
                onNavigateToScanFolders = { navController.navigate(Screen.ScanSettings.createRoute()) },
                onNavigateToHighlightedScanFolders = { highlight ->
                    navController.navigate(Screen.ScanSettings.createRoute(highlight))
                },
                onNavigateToLyricFont = { navController.navigate(Screen.LyricFont.route) },
                onNavigateToLyricPluginSources = { navController.navigate(Screen.LyricPluginSources.route) },
                onNavigateToHighlightedLyricSettings = { highlight ->
                    navController.navigate(Screen.LyricSettings.createRoute(highlight))
                },
                onNavigateToHighlightedAppearanceSettings = { highlight ->
                    navController.navigate(Screen.SettingsDetail.createRoute(highlight))
                },
                onNavigateToHighlightedLibrarySettings = { highlight ->
                    navController.navigate(Screen.LibrarySettings.createRoute(highlight))
                },
                onNavigateToHighlightedIntegrationSettings = { highlight ->
                    navController.navigate(Screen.IntegrationSettings.createRoute(highlight))
                },
                onNavigateToHighlightedAudioSettings = { highlight ->
                    navController.navigate(Screen.AudioSettings.createRoute(highlight))
                },
                onNavigateToHighlightedBackupSettings = { highlight ->
                    navController.navigate(Screen.BackupSettings.createRoute(highlight))
                },
                onNavigateToEqualizer = { navController.navigate(Screen.Equalizer.createRoute()) },
                onNavigateToHighlightedEqualizer = { highlight ->
                    navController.navigate(Screen.Equalizer.createRoute(highlight))
                },
                onBack = { navController.popBackStack() },
                showBackButton = !(fromDock && isDockItem(SettingsManager.BOTTOM_DOCK_ITEM_SETTINGS)),
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel
            )
        }

        composable(
            route = Screen.AudioSettings.route,
            arguments = listOf(navArgument("highlight") { defaultValue = "" })
        ) { backStackEntry ->
            AudioSettingsScreen(
                onBack = { navController.popBackStack() },
                playerViewModel = playerViewModel,
                onNavigateToEqualizer = { navController.navigate(Screen.Equalizer.createRoute()) },
                highlightKey = backStackEntry.arguments?.getString("highlight").orEmpty()
            )
        }

        composable(
            route = Screen.Equalizer.route,
            arguments = listOf(navArgument("highlight") { defaultValue = "" })
        ) { backStackEntry ->
            EqualizerScreen(
                onBack = { navController.popBackStack() },
                highlightKey = backStackEntry.arguments?.getString("highlight").orEmpty()
            )
        }

        composable(
            route = Screen.BackupSettings.route,
            arguments = listOf(navArgument("highlight") { defaultValue = "" })
        ) { backStackEntry ->
            BackupSettingsScreen(
                onBack = { navController.popBackStack() },
                mainViewModel = mainViewModel,
                highlightKey = backStackEntry.arguments?.getString("highlight").orEmpty()
            )
        }

        composable(
            route = Screen.SettingsDetail.route,
            arguments = listOf(navArgument("highlight") { defaultValue = "" })
        ) { backStackEntry ->
            SettingsDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLyricFont = { navController.navigate(Screen.LyricFont.route) },
                mode = SettingsDetailMode.AppearanceHome,
                onNavigateToBottomNavigationSettings = {
                    navController.navigate(Screen.BottomNavigationSettings.route)
                },
                highlightKey = backStackEntry.arguments?.getString("highlight").orEmpty()
            )
        }

        composable(Screen.BottomNavigationSettings.route) {
            BottomNavigationSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.LibrarySettings.route,
            arguments = listOf(navArgument("highlight") { defaultValue = "" })
        ) { backStackEntry ->
            SettingsDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLyricFont = { navController.navigate(Screen.LyricFont.route) },
                mode = SettingsDetailMode.LibraryScanning,
                highlightKey = backStackEntry.arguments?.getString("highlight").orEmpty(),
                onNavigateToScanFolders = { navController.navigate(Screen.ScanSettings.createRoute()) },
                onNavigateToNavidromeConfig = { navController.navigate(Screen.NavidromeServerSettings.route) },
                onNavigateToOpenSubsonicConfig = { navController.navigate(Screen.OpenSubsonicServerSettings.route) },
                onNavigateToEmbyConfig = { navController.navigate(Screen.EmbyServerSettings.route) },
                onNavigateToWebDavConfig = { navController.navigate(Screen.WebDav.route) },
                mainViewModel = mainViewModel
            )
        }

        composable(Screen.NavidromeServerSettings.route) {
            RemoteServerSettingsScreen(
                provider = RemoteMusicProvider.Navidrome,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.OpenSubsonicServerSettings.route) {
            RemoteServerSettingsScreen(
                provider = RemoteMusicProvider.OpenSubsonic,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.EmbyServerSettings.route) {
            RemoteServerSettingsScreen(
                provider = RemoteMusicProvider.Emby,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.IntegrationSettings.route,
            arguments = listOf(navArgument("highlight") { defaultValue = "" })
        ) { backStackEntry ->
            SettingsDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLyricFont = { navController.navigate(Screen.LyricFont.route) },
                mode = SettingsDetailMode.Integrations,
                highlightKey = backStackEntry.arguments?.getString("highlight").orEmpty(),
                onNavigateToLastFmSettings = { navController.navigate(Screen.LastFmSettings.route) }
            )
        }

        composable(Screen.LastFmSettings.route) {
            LastFmSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.LyricSettings.route,
            arguments = listOf(navArgument("highlight") { defaultValue = "" })
        ) { backStackEntry ->
            SettingsDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLyricFont = { navController.navigate(Screen.LyricFont.route) },
                onNavigateToLyricPluginSources = { navController.navigate(Screen.LyricPluginSources.route) },
                playerViewModel = playerViewModel,
                showOnlyLyrics = true,
                highlightKey = backStackEntry.arguments?.getString("highlight").orEmpty()
            )
        }

        composable(
            route = Screen.HomeDisplaySettings.route,
            arguments = listOf(navArgument("highlight") { defaultValue = "" })
        ) { backStackEntry ->
            SettingsDetailScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLyricFont = { navController.navigate(Screen.LyricFont.route) },
                mode = SettingsDetailMode.AppearanceHome,
                initialHomeDisplay = true,
                onNavigateToBottomNavigationSettings = {
                    navController.navigate(Screen.BottomNavigationSettings.route)
                },
                highlightKey = backStackEntry.arguments?.getString("highlight").orEmpty()
            )
        }

        composable(Screen.LyricPluginSources.route) {
            LyricPluginSourceSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.LyricFont.route) {
            LyricFontScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Logs.route) {
            LogScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.LxOnline.route) {
            LxOnlineScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                providerOverride = RemoteMusicProvider.Lx,
                titleOverride = "LX Music",
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToSourceSettings = { navController.navigate(Screen.LxSourceSettings.route) },
                onNavigateToAlbum = { albumId -> navController.navigate(Screen.AlbumDetail.createRoute(albumId)) },
                onNavigateToArtist = { artistName -> navController.navigate(Screen.ArtistDetail.createRoute(artistName)) }
            )
        }

        composable(Screen.LxSourceSettings.route) {
            LxSourceSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Analytics.route) {
            AnalyticsScreen(
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                showBackButton = !isDockItem(SettingsManager.BOTTOM_DOCK_ITEM_ANALYTICS),
                onNavigateToHistory = { navController.navigate(Screen.PlaybackHistory.route) }
            )
        }

        composable(Screen.AiChat.route) {
            AiChatScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        composable(Screen.PlaybackHistory.route) {
            PlaybackHistoryScreen(
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToAlbum = { albumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                },
                onNavigateToArtist = { artistName ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                }
            )
        }

        composable(Screen.LibraryAnalysis.route) {
            LibraryAnalysisScreen(
                mainViewModel = mainViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onNavigateToUpdate = { navController.navigate(Screen.Update.route) }
            )
        }

        composable(Screen.Update.route) {
            UpdateScreen(
                onBack = { navController.popBackStack() }
            )
        }

    }
}

private fun String.bottomDockItemIdForMetadataCategory(): String? = when (this) {
    "folder" -> SettingsManager.BOTTOM_DOCK_ITEM_FOLDER
    "year" -> SettingsManager.BOTTOM_DOCK_ITEM_YEAR
    "genre" -> SettingsManager.BOTTOM_DOCK_ITEM_GENRE
    "composer" -> SettingsManager.BOTTOM_DOCK_ITEM_COMPOSER
    "arranger" -> SettingsManager.BOTTOM_DOCK_ITEM_ARRANGER
    "lyricist" -> SettingsManager.BOTTOM_DOCK_ITEM_LYRICIST
    else -> null
}
