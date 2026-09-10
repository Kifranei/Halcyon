package com.ella.music.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.CategoryResumeKeys
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.data.model.FolderPlaylist
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.artistNamesForSong
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.navigation.Screen
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToLibrary: () -> Unit,
    onNavigateToArtist: () -> Unit,
    onNavigateToAlbum: () -> Unit,
    onNavigateToFolder: () -> Unit,
    onNavigateToFolderPlaylists: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToLxOnline: () -> Unit,
    onNavigateToWebDav: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToAiChat: () -> Unit = {},
    onNavigateToMetadataCategory: (String) -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val songs by mainViewModel.songs.collectAsState()
    val albums by mainViewModel.albums.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val playbackHistory by mainViewModel.recentPlaybackHistory.collectAsState()
    com.ella.music.ui.components.RememberPlaybackSourceScreen(CategoryResumeKeys.DASHBOARD)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val initialSettings = remember(settingsManager) {
        runBlocking(Dispatchers.IO) {
            HomeInitialSettings(
                folderPlaylists = settingsManager.folderPlaylists.first(),
                openPlayerOnPlay = settingsManager.openPlayerOnPlay.first(),
                showAlbumArtists = settingsManager.showAlbumArtists.first(),
                tagIgnoreCase = settingsManager.tagIgnoreCase.first(),
                homeFeatureWallpaperUri = settingsManager.homeFeatureWallpaperUri.first(),
                homeRecentSectionMode = settingsManager.homeRecentSectionMode.first(),
                homeSectionOrder = settingsManager.homeSectionOrder.first(),
                homeHiddenSections = settingsManager.homeHiddenSections.first(),
                homeLibraryTileOrder = settingsManager.homeLibraryTileOrder.first(),
                homeHiddenLibraryTiles = settingsManager.homeHiddenLibraryTiles.first(),
                homeOnlineTileOrder = settingsManager.homeOnlineTileOrder.first(),
                homeHiddenOnlineTiles = settingsManager.homeHiddenOnlineTiles.first(),
                homeTilePinButtonsVisible = settingsManager.homeTilePinButtonsVisible.first(),
                homeCardColor = settingsManager.homeCardColor.first()
            )
        }
    }
    val folderPlaylists by settingsManager.folderPlaylists.collectAsState(initial = initialSettings.folderPlaylists)
    val openPlayerOnPlay by settingsManager.openPlayerOnPlay.collectAsState(initial = initialSettings.openPlayerOnPlay)
    val showAlbumArtists by settingsManager.showAlbumArtists.collectAsState(initial = initialSettings.showAlbumArtists)
    val tagIgnoreCase by settingsManager.tagIgnoreCase.collectAsState(initial = initialSettings.tagIgnoreCase)
    val parseFeaturedArtists by settingsManager.parseFeaturedArtists.collectAsState(initial = false)
    val homeFeatureWallpaperUri by settingsManager.homeFeatureWallpaperUri.collectAsState(
        initial = initialSettings.homeFeatureWallpaperUri
    )
    val homeRecentSectionMode by settingsManager.homeRecentSectionMode.collectAsState(initial = initialSettings.homeRecentSectionMode)
    val homeSectionOrder by settingsManager.homeSectionOrder.collectAsState(initial = initialSettings.homeSectionOrder)
    val homeHiddenSections by settingsManager.homeHiddenSections.collectAsState(initial = initialSettings.homeHiddenSections)
    val homeLibraryTileOrder by settingsManager.homeLibraryTileOrder.collectAsState(initial = initialSettings.homeLibraryTileOrder)
    val homeHiddenLibraryTiles by settingsManager.homeHiddenLibraryTiles.collectAsState(initial = initialSettings.homeHiddenLibraryTiles)
    val homeOnlineTileOrder by settingsManager.homeOnlineTileOrder.collectAsState(initial = initialSettings.homeOnlineTileOrder)
    val homeHiddenOnlineTiles by settingsManager.homeHiddenOnlineTiles.collectAsState(initial = initialSettings.homeHiddenOnlineTiles)
    val homeTilePinButtonsVisible by settingsManager.homeTilePinButtonsVisible.collectAsState(initial = initialSettings.homeTilePinButtonsVisible)
    val homeCardColorRaw by settingsManager.homeCardColor.collectAsState(initial = initialSettings.homeCardColor)
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = ellaPageBackground()
    val cardText = if (isDark) Color.White else Color(0xFF15151A)
    val baseHomeCardColor = homeCardColorRaw.parseHomeCardColorOrNull()
        ?: MiuixTheme.colorScheme.surfaceContainer
    val homeTileCardColor = baseHomeCardColor
    val artistCount = remember(songs, showAlbumArtists, tagIgnoreCase, parseFeaturedArtists) {
        songs
            .flatMap {
                if (showAlbumArtists) artistNamesForSong(it, parseFeaturedArtists) + splitArtistNames(it.albumArtist)
                else artistNamesForSong(it, parseFeaturedArtists)
            }
            .distinctBy { it.tagIdentityKey() }
            .size
    }
    val metadataCategoryCounts = remember(songs) {
        mainViewModel.getMetadataCategoryCounts(listOf("folder", "genre", "year", "composer", "arranger", "lyricist"))
    }
    val folderCount = metadataCategoryCounts["folder"] ?: 0
    val genreCount = metadataCategoryCounts["genre"] ?: 0
    val yearCount = metadataCategoryCounts["year"] ?: 0
    val composerCount = metadataCategoryCounts["composer"] ?: 0
    val arrangerCount = metadataCategoryCounts["arranger"] ?: 0
    val lyricistCount = metadataCategoryCounts["lyricist"] ?: 0
    val recentlyAddedSongs = remember(songs) {
        songs
            .sortedWith(compareByDescending<Song> { it.dateAdded }.thenByDescending { it.dateModified })
            .take(5)
    }
    val recentlyPlayedSongs = remember(songs, playbackHistory) {
        val songsById = songs.associateBy(Song::id)
        playbackHistory
            .asSequence()
            .sortedByDescending { it.playedAt }
            .mapNotNull { entry ->
                songsById[entry.songId] ?: songs.firstOrNull { song ->
                    song.title.equals(entry.title, ignoreCase = true) &&
                        song.artist.equals(entry.artist, ignoreCase = true) &&
                        song.album.equals(entry.album, ignoreCase = true)
                }
            }
            .distinctBy { it.id to it.path }
            .take(5)
            .toList()
    }
    val recentSongs = if (homeRecentSectionMode == SettingsManager.HOME_RECENT_SECTION_MODE_PLAYED) {
        recentlyPlayedSongs
    } else {
        recentlyAddedSongs
    }
    val recentSectionTitle = if (homeRecentSectionMode == SettingsManager.HOME_RECENT_SECTION_MODE_PLAYED) {
        stringResource(R.string.home_recent_played)
    } else {
        stringResource(R.string.home_recent_added)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.home_title),
            color = pageBackground,
            titleStartPadding = 20.dp,
            titleEndPadding = 156.dp,
            actions = {
                IconButton(onClick = onNavigateToAnalytics) {
                    Icon(
                        imageVector = com.ella.music.ui.components.AnalyticsTrendIcon,
                        contentDescription = stringResource(R.string.analytics_title),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onNavigateToAiChat) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Community,
                        contentDescription = stringResource(R.string.ai_chat_title),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Settings,
                        contentDescription = stringResource(R.string.tab_settings),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (homeFeatureWallpaperUri.isNotBlank()) {
                HomeFeatureWallpaperCard(
                    uri = homeFeatureWallpaperUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            val hiddenSections = remember(homeHiddenSections) { homeHiddenSections.csvIdSet() }
            val sectionOrder = remember(homeSectionOrder) {
                homeSectionOrder.csvIds(SettingsManager.DEFAULT_HOME_SECTION_ORDER)
            }
            val hiddenTiles = remember(homeHiddenLibraryTiles) { homeHiddenLibraryTiles.csvIdSet() }
            val tileOrder = remember(homeLibraryTileOrder) {
                homeLibraryTileOrder.csvIds(SettingsManager.DEFAULT_HOME_LIBRARY_TILE_ORDER)
            }
            val hiddenOnlineTiles = remember(homeHiddenOnlineTiles) { homeHiddenOnlineTiles.csvIdSet() }
            val onlineTileOrder = remember(homeOnlineTileOrder) {
                homeOnlineTileOrder.csvIds(SettingsManager.DEFAULT_HOME_ONLINE_TILE_ORDER)
            }
            val libraryTiles = remember(
                context,
                tileOrder,
                hiddenTiles,
                artistCount,
                albums.size,
                folderCount,
                folderPlaylists.size,
                playlists.size,
                genreCount,
                yearCount,
                composerCount,
                arrangerCount,
                lyricistCount
            ) {
                val all = mapOf(
                    "artist" to HomeTileSpec("artist", context.getString(R.string.category_artist), context.getString(R.string.home_count_artists, artistCount), Screen.Artist.createRoute(), onNavigateToArtist),
                    "album" to HomeTileSpec("album", context.getString(R.string.category_album), context.getString(R.string.home_count_albums, albums.size), Screen.Album.createRoute(), onNavigateToAlbum),
                    "folder" to HomeTileSpec("folder", context.getString(R.string.category_folder), context.getString(R.string.home_count_folders, folderCount), Screen.MetadataCategory.createRoute("folder")) { onNavigateToMetadataCategory("folder") },
                    "folder_tree" to HomeTileSpec("folder_tree", context.getString(R.string.category_folder_tree), context.getString(R.string.home_browse_nested_folders), Screen.Folder.createRoute(), onNavigateToFolder),
                    "folder_playlist" to HomeTileSpec("folder_playlist", context.getString(R.string.folder_playlist_title), context.getString(R.string.home_count_folder_playlists, folderPlaylists.size), Screen.FolderPlaylists.route, onNavigateToFolderPlaylists),
                    "playlist" to HomeTileSpec("playlist", context.getString(R.string.category_playlist), context.getString(R.string.home_count_playlists, playlists.size), Screen.Playlists.createRoute(), onNavigateToPlaylists),
                    "genre" to HomeTileSpec("genre", context.getString(R.string.category_genre), context.getString(R.string.home_count_genres, genreCount), Screen.MetadataCategory.createRoute("genre")) { onNavigateToMetadataCategory("genre") },
                    "year" to HomeTileSpec("year", context.getString(R.string.category_year), context.getString(R.string.home_count_folders, yearCount), Screen.MetadataCategory.createRoute("year")) { onNavigateToMetadataCategory("year") },
                    "composer" to HomeTileSpec("composer", context.getString(R.string.category_composer), context.getString(R.string.home_count_artists, composerCount), Screen.MetadataCategory.createRoute("composer")) { onNavigateToMetadataCategory("composer") },
                    "arranger" to HomeTileSpec("arranger", context.getString(R.string.category_arranger), context.getString(R.string.home_count_artists, arrangerCount), Screen.MetadataCategory.createRoute("arranger")) { onNavigateToMetadataCategory("arranger") },
                    "lyricist" to HomeTileSpec("lyricist", context.getString(R.string.category_lyricist), context.getString(R.string.home_count_artists, lyricistCount), Screen.MetadataCategory.createRoute("lyricist")) { onNavigateToMetadataCategory("lyricist") }
                )
                tileOrder.mapNotNull { all[it] }.filterNot { it.id in hiddenTiles }
            }
            val onlineTiles = remember(context, onlineTileOrder, hiddenOnlineTiles) {
                val all = mapOf(
                    "lx" to HomeTileSpec("lx", "LX Music", context.getString(R.string.home_import_api_source), Screen.LxOnline.route, onNavigateToLxOnline),
                    "webdav" to HomeTileSpec("webdav", "WebDAV", context.getString(R.string.home_connect_cloud_music), Screen.WebDav.route, onNavigateToWebDav)
                )
                onlineTileOrder.mapNotNull { all[it] }.filterNot { it.id in hiddenOnlineTiles }
            }

            sectionOrder.filterNot { it in hiddenSections }.forEach { section ->
                when (section) {
                    "library" -> HomeTileSection(
                        stringResource(R.string.home_library),
                        libraryTiles,
                        context,
                        homeTilePinButtonsVisible,
                        cardColor = homeTileCardColor
                    )
                    "online" -> {
                        if (onlineTiles.isNotEmpty()) {
                            SectionTitle(stringResource(R.string.home_online_music))
                            HomeTileGrid(
                                tiles = onlineTiles,
                                context = context,
                                showPinButtons = homeTilePinButtonsVisible,
                                cardColor = homeTileCardColor
                            )
                        }
                    }
                    "recent" -> {
                        SectionTitle(recentSectionTitle)
                        if (recentSongs.isEmpty()) {
                            Text(
                                text = stringResource(R.string.home_no_history),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            recentSongs.forEach { song ->
                                CompactRecentSongRow(
                                    song = song,
                                    mainViewModel = mainViewModel,
                                    cardText = cardText,
                                    onClick = {
                                        playerViewModel.playSongUncategorized(song)
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}

private data class HomeInitialSettings(
    val folderPlaylists: List<FolderPlaylist>,
    val openPlayerOnPlay: Boolean,
    val showAlbumArtists: Boolean,
    val tagIgnoreCase: Boolean,
    val homeFeatureWallpaperUri: String,
    val homeRecentSectionMode: Int,
    val homeSectionOrder: String,
    val homeHiddenSections: String,
    val homeLibraryTileOrder: String,
    val homeHiddenLibraryTiles: String,
    val homeOnlineTileOrder: String,
    val homeHiddenOnlineTiles: String,
    val homeTilePinButtonsVisible: Boolean,
    val homeCardColor: String
)

private fun String.parseHomeCardColorOrNull(): Color? {
    val value = trim()
    if (value.isBlank()) return null
    val normalized = if (value.startsWith("#")) value else "#$value"
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrNull()
}
