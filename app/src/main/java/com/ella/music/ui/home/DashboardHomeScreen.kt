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
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.data.model.FolderPlaylist
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
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

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
    val playbackHistory by mainViewModel.playbackHistory.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
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
                homeDailyMixVisible = settingsManager.homeDailyMixVisible.first(),
                homeAiMixVisible = settingsManager.homeAiMixVisible.first(),
                homeRecentSectionMode = settingsManager.homeRecentSectionMode.first(),
                homeSectionOrder = settingsManager.homeSectionOrder.first(),
                homeHiddenSections = settingsManager.homeHiddenSections.first(),
                homeLibraryTileOrder = settingsManager.homeLibraryTileOrder.first(),
                homeHiddenLibraryTiles = settingsManager.homeHiddenLibraryTiles.first(),
                homeOnlineTileOrder = settingsManager.homeOnlineTileOrder.first(),
                homeHiddenOnlineTiles = settingsManager.homeHiddenOnlineTiles.first(),
                homeTilePinButtonsVisible = settingsManager.homeTilePinButtonsVisible.first(),
                homeTileColors = settingsManager.homeTileColors.first(),
                appWallpaperEnabled = settingsManager.appWallpaperEnabled.first(),
                appWallpaperUri = settingsManager.appWallpaperUri.first(),
                homeCardColor = settingsManager.homeCardColor.first(),
                homeCardOpacity = settingsManager.homeCardOpacity.first(),
                homeTileGradientEnabled = settingsManager.homeTileGradientEnabled.first(),
                homeTileGradientStartColor = settingsManager.homeTileGradientStartColor.first()
            )
        }
    }
    val folderPlaylists by settingsManager.folderPlaylists.collectAsState(initial = initialSettings.folderPlaylists)
    val openPlayerOnPlay by settingsManager.openPlayerOnPlay.collectAsState(initial = initialSettings.openPlayerOnPlay)
    val showAlbumArtists by settingsManager.showAlbumArtists.collectAsState(initial = initialSettings.showAlbumArtists)
    val tagIgnoreCase by settingsManager.tagIgnoreCase.collectAsState(initial = initialSettings.tagIgnoreCase)
    val homeDailyMixVisible by settingsManager.homeDailyMixVisible.collectAsState(initial = initialSettings.homeDailyMixVisible)
    val homeAiMixVisible by settingsManager.homeAiMixVisible.collectAsState(initial = initialSettings.homeAiMixVisible)
    val homeRecentSectionMode by settingsManager.homeRecentSectionMode.collectAsState(initial = initialSettings.homeRecentSectionMode)
    val homeSectionOrder by settingsManager.homeSectionOrder.collectAsState(initial = initialSettings.homeSectionOrder)
    val homeHiddenSections by settingsManager.homeHiddenSections.collectAsState(initial = initialSettings.homeHiddenSections)
    val homeLibraryTileOrder by settingsManager.homeLibraryTileOrder.collectAsState(initial = initialSettings.homeLibraryTileOrder)
    val homeHiddenLibraryTiles by settingsManager.homeHiddenLibraryTiles.collectAsState(initial = initialSettings.homeHiddenLibraryTiles)
    val homeOnlineTileOrder by settingsManager.homeOnlineTileOrder.collectAsState(initial = initialSettings.homeOnlineTileOrder)
    val homeHiddenOnlineTiles by settingsManager.homeHiddenOnlineTiles.collectAsState(initial = initialSettings.homeHiddenOnlineTiles)
    val homeTilePinButtonsVisible by settingsManager.homeTilePinButtonsVisible.collectAsState(initial = initialSettings.homeTilePinButtonsVisible)
    val homeTileColorsRaw by settingsManager.homeTileColors.collectAsState(initial = initialSettings.homeTileColors)
    val appWallpaperEnabled by settingsManager.appWallpaperEnabled.collectAsState(initial = initialSettings.appWallpaperEnabled)
    val appWallpaperUri by settingsManager.appWallpaperUri.collectAsState(initial = initialSettings.appWallpaperUri)
    val homeCardColorRaw by settingsManager.homeCardColor.collectAsState(initial = initialSettings.homeCardColor)
    val homeCardOpacity by settingsManager.homeCardOpacity.collectAsState(initial = initialSettings.homeCardOpacity)
    val homeTileGradientEnabled by settingsManager.homeTileGradientEnabled.collectAsState(initial = initialSettings.homeTileGradientEnabled)
    val homeTileGradientStartColorRaw by settingsManager.homeTileGradientStartColor.collectAsState(initial = initialSettings.homeTileGradientStartColor)
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    var aiPlaylistLoading by remember { mutableStateOf(false) }
    val pageBackground = ellaPageBackground()
    val cardText = if (isDark) Color.White else Color(0xFF15151A)
    val wallpaperVisible = appWallpaperEnabled && appWallpaperUri.isNotBlank()
    val baseHomeCardColor = homeCardColorRaw.parseHomeCardColorOrNull()
        ?: MiuixTheme.colorScheme.surfaceContainer
    val homeTileCardColor = baseHomeCardColor.copy(alpha = homeCardOpacity.coerceIn(20, 100) / 100f)
    val homeTileGradientStartColor = homeTileGradientStartColorRaw.parseHomeCardColorOrNull()
    val customTileColors = remember(homeTileColorsRaw) { homeTileColorsRaw.parseHomeTileColors() }
    fun tileColor(id: String, fallback: Color): Color = customTileColors[id] ?: fallback
    val featuredSongs = remember(songs) {
        when {
            songs.size <= 3 -> songs
            else -> listOf(songs.first(), songs[songs.size / 2], songs.last())
        }
    }
    val artistCount = remember(songs, showAlbumArtists, tagIgnoreCase) {
        songs
            .flatMap {
                if (showAlbumArtists) splitArtistNames(it.artist) + splitArtistNames(it.albumArtist)
                else splitArtistNames(it.artist)
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
            titleEndPadding = 68.dp,
            actions = {
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
            if (homeDailyMixVisible) {
                DailyMixCard(
                    songs = songs,
                    featuredSongs = featuredSongs,
                    currentSongTitle = currentSong?.title,
                    mainViewModel = mainViewModel,
                    onPlay = {
                        val randomSong = songs.randomOrNull()
                        if (randomSong != null) {
                            playerViewModel.setPlaylist(songs, songs.indexOf(randomSong))
                            if (openPlayerOnPlay) onNavigateToPlayer()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            if (homeAiMixVisible) {
                SectionTitle(stringResource(R.string.home_ai_section))
                AiMixCard(
                    songCount = songs.size,
                    isLoading = aiPlaylistLoading,
                    onChat = onNavigateToAiChat,
                    onPlay = {
                        if (aiPlaylistLoading) return@AiMixCard
                        if (songs.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.no_songs_found), Toast.LENGTH_SHORT).show()
                            return@AiMixCard
                        }
                        scope.launch {
                            aiPlaylistLoading = true
                            try {
                                runCatching { mainViewModel.recommendPlaylistWithOpenAi() }
                                    .onSuccess { recommendation ->
                                        playerViewModel.setPlaylist(recommendation.songs, 0)
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.home_ai_playlist_started,
                                                recommendation.title,
                                                recommendation.songs.size
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }
                                    .onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.home_ai_playlist_failed,
                                                error.message ?: context.getString(R.string.common_unknown)
                                            ),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            } finally {
                                aiPlaylistLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
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
                homeTileColorsRaw,
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
                    "artist" to HomeTileSpec("artist", context.getString(R.string.category_artist), context.getString(R.string.home_count_artists, artistCount), tileColor("artist", Color(0xFF118AB2)), Screen.Artist.createRoute(), onNavigateToArtist),
                    "album" to HomeTileSpec("album", context.getString(R.string.category_album), context.getString(R.string.home_count_albums, albums.size), tileColor("album", Color(0xFFFF9F1C)), Screen.Album.createRoute(), onNavigateToAlbum),
                    "folder" to HomeTileSpec("folder", context.getString(R.string.category_folder), context.getString(R.string.home_count_folders, folderCount), tileColor("folder", Color(0xFF5E60CE)), Screen.MetadataCategory.createRoute("folder")) { onNavigateToMetadataCategory("folder") },
                    "folder_tree" to HomeTileSpec("folder_tree", context.getString(R.string.category_folder_tree), context.getString(R.string.home_browse_nested_folders), tileColor("folder_tree", Color(0xFF8338EC)), Screen.Folder.createRoute(), onNavigateToFolder),
                    "folder_playlist" to HomeTileSpec("folder_playlist", context.getString(R.string.folder_playlist_title), context.getString(R.string.home_count_folder_playlists, folderPlaylists.size), tileColor("folder_playlist", Color(0xFF2A9D8F)), Screen.FolderPlaylists.route, onNavigateToFolderPlaylists),
                    "playlist" to HomeTileSpec("playlist", context.getString(R.string.category_playlist), context.getString(R.string.home_count_playlists, playlists.size), tileColor("playlist", Color(0xFFEF476F)), Screen.Playlists.createRoute(), onNavigateToPlaylists),
                    "analytics" to HomeTileSpec("analytics", context.getString(R.string.category_analytics), context.getString(R.string.home_analytics_summary), tileColor("analytics", Color(0xFFE71D36)), Screen.Analytics.route, onNavigateToAnalytics),
                    "genre" to HomeTileSpec("genre", context.getString(R.string.category_genre), context.getString(R.string.home_count_genres, genreCount), tileColor("genre", Color(0xFF06D6A0)), Screen.MetadataCategory.createRoute("genre")) { onNavigateToMetadataCategory("genre") },
                    "year" to HomeTileSpec("year", context.getString(R.string.category_year), context.getString(R.string.home_count_folders, yearCount), tileColor("year", Color(0xFF4CC9F0)), Screen.MetadataCategory.createRoute("year")) { onNavigateToMetadataCategory("year") },
                    "composer" to HomeTileSpec("composer", context.getString(R.string.category_composer), context.getString(R.string.home_count_artists, composerCount), tileColor("composer", Color(0xFFB5179E)), Screen.MetadataCategory.createRoute("composer")) { onNavigateToMetadataCategory("composer") },
                    "arranger" to HomeTileSpec("arranger", context.getString(R.string.category_arranger), context.getString(R.string.home_count_artists, arrangerCount), tileColor("arranger", Color(0xFF4361EE)), Screen.MetadataCategory.createRoute("arranger")) { onNavigateToMetadataCategory("arranger") },
                    "lyricist" to HomeTileSpec("lyricist", context.getString(R.string.category_lyricist), context.getString(R.string.home_count_artists, lyricistCount), tileColor("lyricist", Color(0xFFFF6D00)), Screen.MetadataCategory.createRoute("lyricist")) { onNavigateToMetadataCategory("lyricist") }
                )
                tileOrder.mapNotNull { all[it] }.filterNot { it.id in hiddenTiles }
            }
            val onlineTiles = remember(context, onlineTileOrder, hiddenOnlineTiles, homeTileColorsRaw) {
                val all = mapOf(
                    "lx" to HomeTileSpec("lx", "LX Music", context.getString(R.string.home_import_api_source), tileColor("lx", Color(0xFF00A896)), Screen.LxOnline.route, onNavigateToLxOnline),
                    "webdav" to HomeTileSpec("webdav", "WebDAV", context.getString(R.string.home_connect_cloud_music), tileColor("webdav", Color(0xFF5E60CE)), Screen.WebDav.route, onNavigateToWebDav)
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
                        cardColor = homeTileCardColor,
                        gradientEnabled = homeTileGradientEnabled,
                        gradientStartColor = homeTileGradientStartColor
                    )
                    "online" -> {
                        if (onlineTiles.isNotEmpty()) {
                            SectionTitle(stringResource(R.string.home_online_music))
                            HomeTileGrid(
                                tiles = onlineTiles,
                                context = context,
                                showPinButtons = homeTilePinButtonsVisible,
                                cardColor = homeTileCardColor,
                                gradientEnabled = homeTileGradientEnabled,
                                gradientStartColor = homeTileGradientStartColor
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
                                        playerViewModel.playSong(song)
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
    val homeDailyMixVisible: Boolean,
    val homeAiMixVisible: Boolean,
    val homeRecentSectionMode: Int,
    val homeSectionOrder: String,
    val homeHiddenSections: String,
    val homeLibraryTileOrder: String,
    val homeHiddenLibraryTiles: String,
    val homeOnlineTileOrder: String,
    val homeHiddenOnlineTiles: String,
    val homeTilePinButtonsVisible: Boolean,
    val homeTileColors: String,
    val appWallpaperEnabled: Boolean,
    val appWallpaperUri: String,
    val homeCardColor: String,
    val homeCardOpacity: Int,
    val homeTileGradientEnabled: Boolean,
    val homeTileGradientStartColor: String
)

private fun String.parseHomeCardColorOrNull(): Color? {
    val value = trim()
    if (value.isBlank()) return null
    val normalized = if (value.startsWith("#")) value else "#$value"
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrNull()
}

private fun String.parseHomeTileColors(): Map<String, Color> =
    runCatching {
        val json = JSONObject(this)
        buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                json.optString(key).parseHomeCardColorOrNull()?.let { put(key, it) }
            }
        }
    }.getOrDefault(emptyMap())
