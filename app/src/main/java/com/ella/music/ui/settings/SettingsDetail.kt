package com.ella.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class SettingsDetailMode {
    AppearanceHome,
    LibraryScanning,
    Integrations,
    Lyrics
}

@Composable
fun SettingsDetailScreen(
    onBack: () -> Unit,
    onNavigateToLyricFont: () -> Unit,
    playerViewModel: PlayerViewModel? = null,
    showOnlyLyrics: Boolean = false,
    mode: SettingsDetailMode = SettingsDetailMode.AppearanceHome,
    initialHomeDisplay: Boolean = false,
    highlightKey: String? = null,
    onNavigateToScanFolders: (() -> Unit)? = null,
    onNavigateToNavidromeConfig: (() -> Unit)? = null,
    onNavigateToOpenSubsonicConfig: (() -> Unit)? = null,
    onNavigateToEmbyConfig: (() -> Unit)? = null,
    onNavigateToWebDavConfig: (() -> Unit)? = null,
    onNavigateToLyricPluginSources: () -> Unit = {},
    onNavigateToLastFmSettings: () -> Unit = {},
    onNavigateToBottomNavigationSettings: () -> Unit = {},
    mainViewModel: com.ella.music.viewmodel.MainViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)

    val lyricWesternFontName by settingsManager.lyricWesternFontName.collectAsState(initial = "")
    val lyricCjkFontName by settingsManager.lyricCjkFontName.collectAsState(initial = "")
    val homeSectionOrder by settingsManager.homeSectionOrder.collectAsState(initial = SettingsManager.DEFAULT_HOME_SECTION_ORDER)
    val homeRecentSectionMode by settingsManager.homeRecentSectionMode.collectAsState(
        initial = SettingsManager.HOME_RECENT_SECTION_MODE_ADDED
    )
    val homeHiddenSections by settingsManager.homeHiddenSections.collectAsState(initial = "")
    val homeLibraryTileOrder by settingsManager.homeLibraryTileOrder.collectAsState(initial = SettingsManager.DEFAULT_HOME_LIBRARY_TILE_ORDER)
    val homeHiddenLibraryTiles by settingsManager.homeHiddenLibraryTiles.collectAsState(initial = "")
    val homeOnlineTileOrder by settingsManager.homeOnlineTileOrder.collectAsState(initial = SettingsManager.DEFAULT_HOME_ONLINE_TILE_ORDER)
    val homeHiddenOnlineTiles by settingsManager.homeHiddenOnlineTiles.collectAsState(initial = "")
    val homeTilePinButtonsVisible by settingsManager.homeTilePinButtonsVisible.collectAsState(initial = false)
    val homeCardColor by settingsManager.homeCardColor.collectAsState(initial = "")
    val homeCardOpacity by settingsManager.homeCardOpacity.collectAsState(initial = 58)
    val homeTileColors by settingsManager.homeTileColors.collectAsState(initial = "")
    val homeTileGradientEnabled by settingsManager.homeTileGradientEnabled.collectAsState(initial = false)
    val homeTileGradientStartColor by settingsManager.homeTileGradientStartColor.collectAsState(initial = "")
    val homeSectionItems = listOf(
        HomePreferenceItem("library", stringResource(R.string.settings_home_section_library), stringResource(R.string.settings_home_section_library_summary)),
        HomePreferenceItem("online", stringResource(R.string.settings_home_section_online), stringResource(R.string.settings_home_section_online_summary)),
        HomePreferenceItem("recent", stringResource(R.string.settings_home_section_recent), stringResource(R.string.settings_home_section_recent_summary))
    )
    val homeLibraryTileItems = listOf(
        HomePreferenceItem("artist", stringResource(R.string.settings_library_tile_artist), stringResource(R.string.settings_library_tile_artist_summary)),
        HomePreferenceItem("album", stringResource(R.string.settings_library_tile_album), stringResource(R.string.settings_library_tile_album_summary)),
        HomePreferenceItem("folder", stringResource(R.string.settings_library_tile_folder), stringResource(R.string.settings_library_tile_folder_summary)),
        HomePreferenceItem("folder_tree", stringResource(R.string.settings_library_tile_folder_tree), stringResource(R.string.settings_library_tile_folder_tree_summary)),
        HomePreferenceItem("folder_playlist", stringResource(R.string.settings_library_tile_folder_playlist), stringResource(R.string.settings_library_tile_folder_playlist_summary)),
        HomePreferenceItem("playlist", stringResource(R.string.settings_library_tile_playlist), stringResource(R.string.settings_library_tile_playlist_summary)),
        HomePreferenceItem("analytics", stringResource(R.string.settings_library_tile_analytics), stringResource(R.string.settings_library_tile_analytics_summary)),
        HomePreferenceItem("genre", stringResource(R.string.settings_library_tile_genre), stringResource(R.string.settings_library_tile_genre_summary)),
        HomePreferenceItem("year", stringResource(R.string.settings_library_tile_year), stringResource(R.string.settings_library_tile_year_summary)),
        HomePreferenceItem("composer", stringResource(R.string.settings_library_tile_composer), stringResource(R.string.settings_library_tile_composer_summary)),
        HomePreferenceItem("arranger", stringResource(R.string.settings_library_tile_arranger), stringResource(R.string.settings_library_tile_arranger_summary)),
        HomePreferenceItem("lyricist", stringResource(R.string.settings_library_tile_lyricist), stringResource(R.string.settings_library_tile_lyricist_summary))
    )
    val homeOnlineTileItems = listOf(
        HomePreferenceItem("lx", "LX Music", stringResource(R.string.home_import_api_source)),
        HomePreferenceItem("webdav", "WebDAV", stringResource(R.string.home_connect_cloud_music))
    )
    var showHomeDisplayPage by remember(initialHomeDisplay) { mutableStateOf(initialHomeDisplay) }
    val contentScrollState = rememberScrollState()
    LaunchedEffect(showHomeDisplayPage) {
        contentScrollState.scrollTo(0)
    }
    val effectiveMode = if (showOnlyLyrics) SettingsDetailMode.Lyrics else mode

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = when {
                showHomeDisplayPage -> stringResource(R.string.settings_home_display)
                effectiveMode == SettingsDetailMode.AppearanceHome -> stringResource(R.string.settings_appearance_home)
                effectiveMode == SettingsDetailMode.LibraryScanning -> stringResource(R.string.settings_library_scan)
                effectiveMode == SettingsDetailMode.Integrations -> stringResource(R.string.settings_integrations)
                else -> stringResource(R.string.settings_lyrics)
            },
            color = pageBackground,
            navigationIcon = {
                IconButton(onClick = { if (showHomeDisplayPage && !initialHomeDisplay) showHomeDisplayPage = false else onBack() }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(contentScrollState)
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (showHomeDisplayPage) {
                HomeDisplaySettingsPage(
                    sectionItems = homeSectionItems,
                    sectionOrder = homeSectionOrder,
                    recentSectionMode = homeRecentSectionMode,
                    hiddenSections = homeHiddenSections,
                    tileItems = homeLibraryTileItems,
                    tileOrder = homeLibraryTileOrder,
                    hiddenTiles = homeHiddenLibraryTiles,
                    onlineItems = homeOnlineTileItems,
                    onlineOrder = homeOnlineTileOrder,
                    hiddenOnlineTiles = homeHiddenOnlineTiles,
                    tilePinButtonsVisible = homeTilePinButtonsVisible,
                    homeCardColor = homeCardColor,
                    homeCardOpacity = homeCardOpacity,
                    homeTileColors = homeTileColors,
                    homeTileGradientEnabled = homeTileGradientEnabled,
                    homeTileGradientStartColor = homeTileGradientStartColor,
                    highlightKey = highlightKey,
                    onHiddenSectionsChange = { value ->
                        scope.launch { settingsManager.setHomeHiddenSections(value) }
                    },
                    onHiddenTilesChange = { value ->
                        scope.launch { settingsManager.setHomeHiddenLibraryTiles(value) }
                    },
                    onHiddenOnlineTilesChange = { value ->
                        scope.launch { settingsManager.setHomeHiddenOnlineTiles(value) }
                    },
                    onSectionOrderChange = { value ->
                        scope.launch { settingsManager.setHomeSectionOrder(value) }
                    },
                    onRecentSectionModeChange = { value ->
                        scope.launch { settingsManager.setHomeRecentSectionMode(value) }
                    },
                    onTileOrderChange = { value ->
                        scope.launch { settingsManager.setHomeLibraryTileOrder(value) }
                    },
                    onOnlineOrderChange = { value ->
                        scope.launch { settingsManager.setHomeOnlineTileOrder(value) }
                    },
                    onTilePinButtonsVisibleChange = { value ->
                        scope.launch { settingsManager.setHomeTilePinButtonsVisible(value) }
                    },
                    onHomeCardColorChange = { value ->
                        scope.launch { settingsManager.setHomeCardColor(value) }
                    },
                    onHomeCardOpacityChange = { value ->
                        scope.launch { settingsManager.setHomeCardOpacity(value) }
                    },
                    onHomeTileColorChange = { id, value ->
                        scope.launch { settingsManager.setHomeTileColor(id, value) }
                    },
                    onHomeTileGradientEnabledChange = { value ->
                        scope.launch { settingsManager.setHomeTileGradientEnabled(value) }
                    },
                    onHomeTileGradientStartColorChange = { value ->
                        scope.launch { settingsManager.setHomeTileGradientStartColor(value) }
                    }
                )
                Spacer(modifier = Modifier.height(160.dp))
                return@Column
            }

            when (effectiveMode) {
                SettingsDetailMode.AppearanceHome -> {
                    SettingsAppearanceSection(
                        highlightKey = highlightKey,
                        onNavigateToBottomNavigationSettings = onNavigateToBottomNavigationSettings
                    )
                    SettingsCardGroup(highlight = highlightKey == "lyric_font") {
                        ArrowPreference(
                            title = stringResource(R.string.settings_font_settings),
                            summary = listOf(
                                lyricWesternFontName.ifBlank { stringResource(R.string.settings_lyric_font_western_default) },
                                lyricCjkFontName.ifBlank { stringResource(R.string.settings_lyric_font_cjk_default) }
                            ).joinToString(" / "),
                            onClick = onNavigateToLyricFont
                        )
                    }
                    SettingsHomeCustomizeSection(
                        highlightKey = highlightKey,
                        onOpenHomeDisplay = { showHomeDisplayPage = true }
                    )
                }
                SettingsDetailMode.LibraryScanning -> {
                    SettingsLibrarySourceSection(
                        highlightKey = highlightKey,
                        onOpenScanFolders = onNavigateToScanFolders,
                        onOpenNavidromeConfig = onNavigateToNavidromeConfig,
                        onOpenOpenSubsonicConfig = onNavigateToOpenSubsonicConfig,
                        onOpenEmbyConfig = onNavigateToEmbyConfig,
                        onOpenWebDavConfig = onNavigateToWebDavConfig,
                        mainViewModel = mainViewModel
                    )
                    SettingsScanSection(highlightKey = highlightKey)
                    SettingsTagScrapingSection(highlightKey = highlightKey)
                    SettingsDesktopShortcutSection(highlightKey = highlightKey)
                }
                SettingsDetailMode.Integrations -> {
                    SettingsAiInterpretationSection(highlightKey = highlightKey)
                    SettingsMcpSection(highlightKey = highlightKey)
                    SettingsLastFmSection(
                        highlightKey = highlightKey,
                        onOpenLastFmSettings = onNavigateToLastFmSettings
                    )
                }
                SettingsDetailMode.Lyrics -> {
                    SettingsLyricsSection(
                        playerViewModel = playerViewModel,
                        highlightKey = highlightKey,
                        onNavigateToLyricPluginSources = onNavigateToLyricPluginSources
                    )
                    SettingsLyricShareSection(highlightKey = highlightKey)
                }
            }

            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}
