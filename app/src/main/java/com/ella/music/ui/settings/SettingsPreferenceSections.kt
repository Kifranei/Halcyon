package com.ella.music.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.TagEditorOptionIds
import com.ella.music.ui.components.SpectrumViewerLauncher
import com.ella.music.ui.components.EllaMiuixBottomSheet
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference

@Composable
internal fun SettingsHomeCustomizeSection(
    highlightKey: String? = null,
    onOpenHomeDisplay: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val homeDailyMixVisible by settingsManager.homeDailyMixVisible.collectAsState(initial = true)
    val homeAiMixVisible by settingsManager.homeAiMixVisible.collectAsState(initial = true)
    val continuePlaybackRowVisible by settingsManager.continuePlaybackRowVisible.collectAsState(initial = true)

    SmallTitle(text = stringResource(R.string.settings_home_customize))

    SettingsCardGroup(highlight = highlightKey == "home_customize") {
        Column {
            SwitchPreference(
                title = stringResource(R.string.settings_daily_mix),
                summary = stringResource(R.string.settings_daily_mix_summary),
                checked = homeDailyMixVisible,
                onCheckedChange = {
                    scope.launch { settingsManager.setHomeDailyMixVisible(it) }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_ai_mix),
                summary = stringResource(R.string.settings_ai_mix_summary),
                checked = homeAiMixVisible,
                onCheckedChange = {
                    scope.launch { settingsManager.setHomeAiMixVisible(it) }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_continue_playback_row),
                summary = stringResource(R.string.settings_continue_playback_row_summary),
                checked = continuePlaybackRowVisible,
                onCheckedChange = {
                    scope.launch { settingsManager.setContinuePlaybackRowVisible(it) }
                }
            )
            ArrowPreference(
                title = stringResource(R.string.settings_home_display_items),
                summary = stringResource(R.string.settings_home_display_items_summary),
                onClick = onOpenHomeDisplay
            )
        }
    }
}

@Composable
internal fun SettingsLibrarySourceSection(
    highlightKey: String? = null,
    onOpenScanFolders: (() -> Unit)?,
    onOpenNavidromeConfig: (() -> Unit)? = null,
    onOpenOpenSubsonicConfig: (() -> Unit)? = null,
    onOpenEmbyConfig: (() -> Unit)? = null,
    onOpenWebDavConfig: (() -> Unit)? = null,
    mainViewModel: com.ella.music.viewmodel.MainViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val librarySource by settingsManager.librarySource.collectAsState(initial = "")
    val librarySourceOptions = listOf(
        SettingsManager.LIBRARY_SOURCE_LOCAL to stringResource(R.string.settings_library_source_local),
        SettingsManager.LIBRARY_SOURCE_NAVIDROME to stringResource(R.string.remote_source_navidrome),
        SettingsManager.LIBRARY_SOURCE_OPENSUBSONIC to stringResource(R.string.remote_source_opensubsonic),
        SettingsManager.LIBRARY_SOURCE_EMBY to stringResource(R.string.remote_source_emby),
        SettingsManager.LIBRARY_SOURCE_WEBDAV to stringResource(R.string.webdav_library_title)
    )
    val librarySourceEntries = librarySourceOptions.map { DropdownItem(title = it.second) }
    val selectedLibrarySourceIndex = librarySourceOptions
        .indexOfFirst { it.first == librarySource }
        .takeIf { it >= 0 } ?: 0

    SmallTitle(text = stringResource(R.string.settings_library_source))

    SettingsCardGroup(highlight = highlightKey == "library_source") {
        Column {
            SettingsFocusAnchor(active = highlightKey == "library_source") {
                if (librarySource.isNotBlank()) {
                    WindowSpinnerPreference(
                        title = stringResource(R.string.settings_library_source),
                        summary = stringResource(R.string.settings_library_source_summary),
                        items = librarySourceEntries,
                        selectedIndex = selectedLibrarySourceIndex,
                        onSelectedIndexChange = { index ->
                            librarySourceOptions.getOrNull(index)?.first?.let { source ->
                                if (mainViewModel != null) {
                                    mainViewModel.setLibrarySource(source)
                                } else {
                                    scope.launch { settingsManager.setLibrarySource(source) }
                                }
                            }
                        }
                    )
                }
            }
            ArrowPreference(
                title = stringResource(R.string.settings_scan_folders),
                summary = stringResource(R.string.settings_scan_folders_summary),
                onClick = { onOpenScanFolders?.invoke() }
            )
            ArrowPreference(
                title = stringResource(R.string.remote_server_manage_title, stringResource(R.string.remote_source_navidrome)),
                summary = stringResource(R.string.remote_server_manage_summary),
                onClick = { onOpenNavidromeConfig?.invoke() }
            )
            ArrowPreference(
                title = stringResource(R.string.remote_server_manage_title, stringResource(R.string.remote_source_opensubsonic)),
                summary = stringResource(R.string.remote_server_manage_summary),
                onClick = { onOpenOpenSubsonicConfig?.invoke() }
            )
            ArrowPreference(
                title = stringResource(R.string.remote_server_manage_title, stringResource(R.string.remote_source_emby)),
                summary = stringResource(R.string.remote_server_manage_summary),
                onClick = { onOpenEmbyConfig?.invoke() }
            )
            ArrowPreference(
                title = stringResource(R.string.webdav_settings),
                summary = stringResource(R.string.home_connect_cloud_music),
                onClick = { onOpenWebDavConfig?.invoke() }
            )
        }
    }
}

@Composable
internal fun SettingsAiInterpretationSection(
    highlightKey: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val openAiApiKey by settingsManager.openAiApiKey.collectAsState(initial = "")
    val openAiBaseUrl by settingsManager.openAiBaseUrl.collectAsState(initial = SettingsManager.DEFAULT_OPENAI_BASE_URL)
    val openAiModel by settingsManager.openAiModel.collectAsState(initial = SettingsManager.DEFAULT_OPENAI_MODEL)

    SmallTitle(text = stringResource(R.string.settings_ai_interpretation))

    SettingsCardGroup(highlight = highlightKey == "ai") {
        Column {
            SettingsFocusAnchor(active = highlightKey == "ai") {
                SplitSettingTextField(
                    label = "OpenAI API Key",
                    value = openAiApiKey,
                    summary = stringResource(R.string.settings_openai_api_key_summary),
                    onValueChange = { value -> scope.launch { settingsManager.setOpenAiApiKey(value) } }
                )
            }
            SplitSettingTextField(
                label = "OpenAI Base URL",
                value = openAiBaseUrl,
                summary = stringResource(R.string.settings_openai_base_url_summary),
                onValueChange = { value -> scope.launch { settingsManager.setOpenAiBaseUrl(value) } }
            )
            SplitSettingTextField(
                label = stringResource(R.string.settings_openai_model),
                value = openAiModel,
                summary = stringResource(R.string.settings_openai_model_summary, SettingsManager.DEFAULT_OPENAI_MODEL),
                onValueChange = { value -> scope.launch { settingsManager.setOpenAiModel(value) } }
            )
        }
    }
}

@Composable
internal fun SettingsMcpSection(
    highlightKey: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val mcpServerEnabled by settingsManager.mcpServerEnabled.collectAsState(initial = false)
    val webMusicServerEnabled by settingsManager.webMusicServerEnabled.collectAsState(initial = false)
    val uriHandler = LocalUriHandler.current
    val webAddresses = remember(webMusicServerEnabled) {
        if (webMusicServerEnabled) com.ella.music.web.WebMusicService.accessAddresses() else emptyList()
    }

    SmallTitle(text = stringResource(R.string.settings_mcp_server))

    SettingsCardGroup(highlight = highlightKey == "mcp") {
        Column {
            SettingsFocusAnchor(active = highlightKey == "mcp") {
                SwitchPreference(
                    title = stringResource(R.string.settings_mcp_server),
                    summary = stringResource(R.string.settings_mcp_server_summary),
                    checked = mcpServerEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsManager.setMcpServerEnabled(enabled)
                            if (enabled) {
                                com.ella.music.mcp.McpServerService.start(context)
                            } else {
                                com.ella.music.mcp.McpServerService.stop(context)
                            }
                        }
                    }
                )
            }
        }
    }

    SmallTitle(text = stringResource(R.string.web_music_beta_title))
    SettingsCardGroup(highlight = highlightKey == "web_music") {
        Column {
            SettingsFocusAnchor(active = highlightKey == "web_music") {
                SwitchPreference(
                    title = stringResource(R.string.web_music_beta_title),
                    summary = stringResource(R.string.web_music_beta_summary),
                    checked = webMusicServerEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsManager.setWebMusicServerEnabled(enabled)
                            if (enabled) {
                                com.ella.music.web.WebMusicService.start(context)
                            } else {
                                com.ella.music.web.WebMusicService.stop(context)
                            }
                        }
                    }
                )
            }
            if (webMusicServerEnabled) {
                ArrowPreference(
                    title = stringResource(R.string.web_music_beta_address),
                    summary = webAddresses.joinToString("\n")
                        .ifBlank { stringResource(R.string.web_music_beta_address_unavailable) },
                    onClick = { webAddresses.firstOrNull()?.let(uriHandler::openUri) }
                )
            }
        }
    }
}

@Composable
internal fun SettingsLastFmSection(
    highlightKey: String? = null,
    onOpenLastFmSettings: () -> Unit
) {
    SmallTitle(text = stringResource(R.string.settings_lastfm))
    SettingsCardGroup(highlight = highlightKey == "lastfm") {
        ArrowPreference(
            title = stringResource(R.string.settings_lastfm),
            summary = stringResource(R.string.settings_lastfm_summary),
            onClick = onOpenLastFmSettings
        )
    }
}

@Composable
internal fun SettingsLyricShareSection(
    highlightKey: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val lyricShareCustomInfo by settingsManager.lyricShareCustomInfo.collectAsState(initial = "")

    SmallTitle(text = stringResource(R.string.settings_lyric_share_card))

    SettingsCardGroup(highlight = highlightKey == "lyric_share") {
        Column {
            SplitSettingTextField(
                label = stringResource(R.string.settings_lyric_share_custom_info),
                value = lyricShareCustomInfo,
                summary = stringResource(R.string.settings_lyric_share_custom_info_summary),
                onValueChange = { value -> scope.launch { settingsManager.setLyricShareCustomInfo(value) } }
            )
        }
    }
}

@Composable
internal fun SettingsTagScrapingSection(
    highlightKey: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val metadataEditorId by settingsManager.metadataEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
    val lyricTimingEditorId by settingsManager.lyricTimingEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
    val spectrumViewerId by settingsManager.spectrumViewerId.collectAsState(initial = SpectrumViewerLauncher.BUILTIN)

    val editorAskEveryTime = stringResource(R.string.settings_editor_ask_every_time)
    val editorBuiltinCustomTag = stringResource(R.string.settings_editor_builtin_custom_tag)
    val editorBuiltinLyricTiming = stringResource(R.string.settings_editor_builtin_lyric_timing)
    val editorLunaBeatMetadata = stringResource(R.string.settings_editor_lunabeat_metadata)
    val editorMusicTag = stringResource(R.string.settings_editor_music_tag)
    val editorLunaBeatLyricTiming = stringResource(R.string.settings_editor_lunabeat_lyric_timing)
    val metadataEditorOptions = listOf(
        TagEditorOptionIds.ASK_EACH_TIME to editorAskEveryTime,
        TagEditorOptionIds.BUILTIN_CUSTOM_TAG to editorBuiltinCustomTag,
        TagEditorOptionIds.LYRICO to "Lyrico",
        TagEditorOptionIds.LUNABEAT_METADATA to editorLunaBeatMetadata,
        TagEditorOptionIds.MUSIC_TAG to editorMusicTag
    )
    val lyricTimingEditorOptions = listOf(
        TagEditorOptionIds.ASK_EACH_TIME to editorAskEveryTime,
        TagEditorOptionIds.BUILTIN_LYRIC_TIMING to editorBuiltinLyricTiming,
        TagEditorOptionIds.LUNABEAT_LYRIC_TIMING to editorLunaBeatLyricTiming
    )
    val spectrumViewerOptions = listOf(
        SpectrumViewerLauncher.BUILTIN to stringResource(R.string.settings_spectrum_builtin),
        SpectrumViewerLauncher.ASPECT_PRO to stringResource(R.string.settings_spectrum_aspect_pro),
        SpectrumViewerLauncher.KASPEK to stringResource(R.string.settings_spectrum_kaspek),
        SpectrumViewerLauncher.HEARUSY to stringResource(R.string.settings_spectrum_hearusy)
    )
    val metadataEditorIndex = metadataEditorOptions
        .indexOfFirst { it.first == metadataEditorId }
        .takeIf { it >= 0 }
        ?: 0
    val lyricTimingEditorIndex = lyricTimingEditorOptions
        .indexOfFirst { it.first == lyricTimingEditorId }
        .takeIf { it >= 0 }
        ?: 0
    val spectrumViewerIndex = spectrumViewerOptions.indexOfFirst { it.first == spectrumViewerId }.takeIf { it >= 0 } ?: 0
    val metadataEditorEntries = remember(metadataEditorOptions) {
        metadataEditorOptions.map { DropdownItem(title = it.second) }
    }
    val lyricTimingEditorEntries = remember(lyricTimingEditorOptions) {
        lyricTimingEditorOptions.map { DropdownItem(title = it.second) }
    }
    val spectrumViewerEntries = remember(spectrumViewerOptions) {
        spectrumViewerOptions.map { DropdownItem(title = it.second) }
    }
    val offsetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
                .getOrNull()
            if (json.isNullOrBlank()) {
                Toast.makeText(context, R.string.music_video_offsets_import_failed, Toast.LENGTH_SHORT).show()
            } else {
                runCatching { com.ella.music.MusicVideoOffsetsParser.parse(json) }
                    .onSuccess { settingsManager.setMusicVideoOffsetsJson(json) }
                    .onFailure { Toast.makeText(context, R.string.music_video_offsets_import_failed, Toast.LENGTH_SHORT).show() }
            }
        }
    }

    SmallTitle(text = stringResource(R.string.settings_tag_scraping))

    SettingsCardGroup(highlight = highlightKey == "tag_scraping") {
        Column {
            SettingsFocusAnchor(active = highlightKey == "tag_scraping") {
                WindowSpinnerPreference(
                    title = stringResource(R.string.settings_metadata_editor),
                    summary = stringResource(R.string.settings_current_value, metadataEditorOptions.getOrNull(metadataEditorIndex)?.second.orEmpty()),
                    items = metadataEditorEntries,
                    selectedIndex = metadataEditorIndex,
                    onSelectedIndexChange = { index ->
                        scope.launch {
                            settingsManager.setMetadataEditorId(
                                metadataEditorOptions.getOrNull(index)?.first.orEmpty()
                            )
                        }
                    }
                )
            }
            WindowSpinnerPreference(
                title = stringResource(R.string.settings_lyric_timing_editor),
                summary = stringResource(R.string.settings_current_value, lyricTimingEditorOptions.getOrNull(lyricTimingEditorIndex)?.second.orEmpty()),
                items = lyricTimingEditorEntries,
                selectedIndex = lyricTimingEditorIndex,
                onSelectedIndexChange = { index ->
                    scope.launch {
                        settingsManager.setLyricTimingEditorId(
                            lyricTimingEditorOptions.getOrNull(index)?.first.orEmpty()
                        )
                    }
                }
            )
            WindowSpinnerPreference(
                title = stringResource(R.string.settings_spectrum_viewer),
                summary = stringResource(R.string.settings_current_value, spectrumViewerOptions[spectrumViewerIndex].second),
                items = spectrumViewerEntries,
                selectedIndex = spectrumViewerIndex,
                onSelectedIndexChange = { index ->
                    scope.launch {
                        settingsManager.setSpectrumViewerId(spectrumViewerOptions.getOrNull(index)?.first.orEmpty())
                    }
                }
            )
            ArrowPreference(
                title = stringResource(R.string.settings_music_video_offsets),
                summary = stringResource(R.string.settings_music_video_offsets_summary),
                onClick = { offsetPicker.launch(arrayOf("application/json", "text/plain")) }
            )
        }
    }
}

@Composable
internal fun SettingsDesktopShortcutSection(
    highlightKey: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val appShortcutOrder by settingsManager.appShortcutOrder.collectAsState(
        initial = SettingsManager.DEFAULT_APP_SHORTCUT_ORDER
    )

    SmallTitle(text = stringResource(R.string.settings_desktop_shortcuts))

    SettingsCardGroup(highlight = highlightKey == "desktop_shortcuts") {
        Column {
            SettingsAppShortcutsPreference(
                shortcutIds = appShortcutOrder,
                onShortcutIdsChange = { ids ->
                    scope.launch { settingsManager.setAppShortcutOrder(ids) }
                }
            )
        }
    }
}

private enum class ScanAdvancedSheet {
    SplitRules,
    SearchScope,
    CoverStorage
}

@Composable
internal fun SettingsScanSection(
    highlightKey: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val autoScanLocalPlaylists by settingsManager.autoScanLocalPlaylists.collectAsState(initial = false)
    val minDurationSec by settingsManager.minDurationSec.collectAsState(initial = 15)
    val artistSeparators by settingsManager.artistSeparators.collectAsState(
        initial = SettingsManager.DEFAULT_ARTIST_SEPARATORS
    )
    val artistProtectedNames by settingsManager.artistProtectedNames.collectAsState(initial = "")
    val genreSeparators by settingsManager.genreSeparators.collectAsState(
        initial = SettingsManager.DEFAULT_GENRE_SEPARATORS
    )
    val genreProtectedNames by settingsManager.genreProtectedNames.collectAsState(initial = "")
    val tagIgnoreCase by settingsManager.tagIgnoreCase.collectAsState(initial = false)
    val showAlbumArtists by settingsManager.showAlbumArtists.collectAsState(initial = true)
    val artistCoverFolderUri by settingsManager.artistCoverFolderUri.collectAsState(initial = "")
    val coverExportFolderUri by settingsManager.coverExportFolderUri.collectAsState(initial = "")
    val artistCoverCarousel by settingsManager.artistCoverCarousel.collectAsState(initial = true)
    val searchAllCategoryTypes by settingsManager.searchAllCategoryTypes.collectAsState(
        initial = SettingsManager.SEARCH_ALL_CATEGORY_TYPES
    )
    val searchAllSongMatchTypes by settingsManager.searchAllSongMatchTypes.collectAsState(
        initial = SettingsManager.SEARCH_ALL_SONG_MATCH_TYPES
    )
    val songRatingDisplayMode by settingsManager.songRatingDisplayMode.collectAsState(
        initial = SettingsManager.SONG_RATING_DISPLAY_STAR_NUMBER
    )

    val artistCoverFolderPicker = rememberLauncherForActivityResult(
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
        scope.launch {
            settingsManager.setArtistCoverFolderUri(uri.toString())
        }
        Toast.makeText(context, context.getString(R.string.settings_artist_cover_folder_saved), Toast.LENGTH_SHORT).show()
    }
    val coverExportFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, readWrite) }
        scope.launch { settingsManager.setCoverExportFolderUri(uri.toString()) }
        Toast.makeText(context, context.getString(R.string.settings_cover_export_folder_saved), Toast.LENGTH_SHORT).show()
    }

    var activeSheet by remember { mutableStateOf<ScanAdvancedSheet?>(null) }
    LaunchedEffect(highlightKey) {
        activeSheet = when (highlightKey) {
            "artist_separators", "artist_protected_names", "genre_separators", "genre_protected_names" ->
                ScanAdvancedSheet.SplitRules
            "search_all_categories", "search_all_song_match_types" -> ScanAdvancedSheet.SearchScope
            "artist_cover_folder", "artist_cover_carousel", "cover_export_folder" ->
                ScanAdvancedSheet.CoverStorage
            else -> null
        }
    }

    SmallTitle(text = stringResource(R.string.settings_scan))

    // Keep the frequently used scan switches and the one duration slider visible. The rules,
    // search scope and storage pickers are secondary settings and live in focused sheets below.
    SettingsCardGroup(highlight = highlightKey == "scan") {
        Column {
            SettingsFocusAnchor(active = highlightKey == "auto_scan_local_playlists") {
                SwitchPreference(
                    title = stringResource(R.string.settings_auto_scan_local_playlists),
                    summary = stringResource(R.string.settings_auto_scan_local_playlists_summary),
                    checked = autoScanLocalPlaylists,
                    onCheckedChange = {
                        scope.launch {
                            settingsManager.setAutoScanLocalPlaylists(it)
                            settingsManager.setLocalPlaylistScanPromptHandled(true)
                        }
                    }
                )
            }
            SettingsFocusAnchor(active = highlightKey == "min_duration_filter") {
                SettingsIntSliderPreference(
                    title = stringResource(R.string.settings_min_duration_filter),
                    summary = stringResource(R.string.settings_min_duration_filter_summary, minDurationSec),
                    value = minDurationSec,
                    valueRange = 0..60,
                    valueText = stringResource(R.string.settings_seconds_value, minDurationSec.coerceIn(0, 60)),
                    onValueChange = { sec -> scope.launch { settingsManager.setMinDurationSec(sec) } }
                )
            }
            SettingsFocusAnchor(active = highlightKey == "tag_ignore_case") {
                SwitchPreference(
                    title = stringResource(R.string.settings_tag_ignore_case),
                    summary = stringResource(R.string.settings_tag_ignore_case_summary),
                    checked = tagIgnoreCase,
                    onCheckedChange = { scope.launch { settingsManager.setTagIgnoreCase(it) } }
                )
            }
            SettingsFocusAnchor(active = highlightKey == "show_album_artists") {
                SwitchPreference(
                    title = stringResource(R.string.settings_show_album_artists),
                    summary = stringResource(R.string.settings_show_album_artists_summary),
                    checked = showAlbumArtists,
                    onCheckedChange = { scope.launch { settingsManager.setShowAlbumArtists(it) } }
                )
            }
            SettingsFocusAnchor(active = highlightKey == "song_rating_display_stars") {
                SwitchPreference(
                    title = stringResource(R.string.settings_song_rating_display_stars),
                    summary = if (songRatingDisplayMode == SettingsManager.SONG_RATING_DISPLAY_STARS) {
                        stringResource(R.string.settings_song_rating_display_stars_summary)
                    } else {
                        stringResource(R.string.settings_song_rating_display_number_summary)
                    },
                    checked = songRatingDisplayMode == SettingsManager.SONG_RATING_DISPLAY_STARS,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsManager.setSongRatingDisplayMode(
                                if (enabled) SettingsManager.SONG_RATING_DISPLAY_STARS
                                else SettingsManager.SONG_RATING_DISPLAY_STAR_NUMBER
                            )
                        }
                    }
                )
            }
        }
    }

    SettingsCardGroup(highlight = highlightKey == "scan") {
        Column {
            SettingsFocusAnchor(active = highlightKey == "artist_separators") {
                ArrowPreference(
                    title = stringResource(R.string.settings_artist_separators),
                    summary = stringResource(R.string.settings_artist_separators_summary),
                    onClick = { activeSheet = ScanAdvancedSheet.SplitRules }
                )
            }
            SettingsFocusAnchor(active = highlightKey == "search_all_categories" || highlightKey == "search_all_song_match_types") {
                ArrowPreference(
                    title = stringResource(R.string.settings_search_all_categories),
                    summary = stringResource(R.string.settings_search_all_categories_summary),
                    onClick = { activeSheet = ScanAdvancedSheet.SearchScope }
                )
            }
            SettingsFocusAnchor(active = highlightKey == "artist_cover_folder" || highlightKey == "artist_cover_carousel") {
                ArrowPreference(
                    title = stringResource(R.string.settings_artist_cover_folder),
                    summary = if (artistCoverFolderUri.isBlank()) {
                        stringResource(R.string.settings_artist_cover_folder_summary)
                    } else {
                        stringResource(R.string.settings_artist_cover_folder_selected)
                    },
                    onClick = { activeSheet = ScanAdvancedSheet.CoverStorage }
                )
            }
        }
    }

    EllaMiuixBottomSheet(
        show = activeSheet == ScanAdvancedSheet.SplitRules,
        title = stringResource(R.string.settings_artist_separators),
        onDismissRequest = { activeSheet = null }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsFocusAnchor(active = highlightKey == "artist_separators") {
                SplitSettingTextField(
                    label = stringResource(R.string.settings_artist_separators),
                    value = artistSeparators,
                    summary = stringResource(R.string.settings_artist_separators_summary),
                    onValueChange = { value -> scope.launch { settingsManager.setArtistSeparators(value) } }
                )
            }
            SettingsFocusAnchor(active = highlightKey == "artist_protected_names") {
                SplitSettingTextField(
                    label = stringResource(R.string.settings_artist_protected_names),
                    value = artistProtectedNames,
                    summary = stringResource(R.string.settings_artist_protected_names_summary),
                    onValueChange = { value -> scope.launch { settingsManager.setArtistProtectedNames(value) } }
                )
            }
            SettingsFocusAnchor(active = highlightKey == "genre_separators") {
                SplitSettingTextField(
                    label = stringResource(R.string.settings_genre_separators),
                    value = genreSeparators,
                    summary = stringResource(R.string.settings_genre_separators_summary),
                    onValueChange = { value -> scope.launch { settingsManager.setGenreSeparators(value) } }
                )
            }
            SettingsFocusAnchor(active = highlightKey == "genre_protected_names") {
                SplitSettingTextField(
                    label = stringResource(R.string.settings_genre_protected_names),
                    value = genreProtectedNames,
                    summary = stringResource(R.string.settings_genre_protected_names_summary),
                    onValueChange = { value -> scope.launch { settingsManager.setGenreProtectedNames(value) } }
                )
            }
        }
    }

    EllaMiuixBottomSheet(
        show = activeSheet == ScanAdvancedSheet.SearchScope,
        title = stringResource(R.string.settings_search_all_categories),
        onDismissRequest = { activeSheet = null }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsFocusAnchor(active = highlightKey == "search_all_categories") {
                SearchAllCategoryTypesPreference(
                    enabledTypes = searchAllCategoryTypes,
                    onEnabledChange = { type, enabled ->
                        scope.launch { settingsManager.setSearchAllCategoryTypeEnabled(type, enabled) }
                    }
                )
            }
            SettingsFocusAnchor(active = highlightKey == "search_all_song_match_types") {
                SearchAllSongMatchTypesPreference(
                    enabledTypes = searchAllSongMatchTypes,
                    onEnabledChange = { type, enabled ->
                        scope.launch { settingsManager.setSearchAllSongMatchTypeEnabled(type, enabled) }
                    }
                )
            }
        }
    }

    EllaMiuixBottomSheet(
        show = activeSheet == ScanAdvancedSheet.CoverStorage,
        title = stringResource(R.string.settings_artist_cover_folder),
        onDismissRequest = { activeSheet = null }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsFocusAnchor(active = highlightKey == "artist_cover_folder" || highlightKey == "artist_cover_carousel") {
                ArrowPreference(
                    title = stringResource(R.string.settings_artist_cover_folder),
                    summary = if (artistCoverFolderUri.isBlank()) {
                        stringResource(R.string.settings_artist_cover_folder_summary)
                    } else {
                        stringResource(R.string.settings_artist_cover_folder_selected)
                    },
                    onClick = { artistCoverFolderPicker.launch(null) }
                )
            }
            if (artistCoverFolderUri.isNotBlank()) {
                SettingsFocusAnchor(active = highlightKey == "artist_cover_carousel") {
                    SwitchPreference(
                        title = stringResource(R.string.settings_artist_cover_carousel),
                        summary = stringResource(R.string.settings_artist_cover_carousel_summary),
                        checked = artistCoverCarousel,
                        onCheckedChange = { scope.launch { settingsManager.setArtistCoverCarousel(it) } }
                    )
                }
                ArrowPreference(
                    title = stringResource(R.string.settings_artist_cover_folder_remove),
                    summary = stringResource(R.string.settings_artist_cover_folder_remove_summary),
                    onClick = {
                        scope.launch { settingsManager.setArtistCoverFolderUri("") }
                        Toast.makeText(context, context.getString(R.string.settings_artist_cover_folder_cleared), Toast.LENGTH_SHORT).show()
                    }
                )
            }
            SettingsFocusAnchor(active = highlightKey == "cover_export_folder") {
                ArrowPreference(
                    title = stringResource(R.string.settings_cover_export_folder),
                    summary = if (coverExportFolderUri.isBlank()) {
                        stringResource(R.string.settings_cover_export_folder_summary)
                    } else {
                        stringResource(R.string.settings_cover_export_folder_selected)
                    },
                    onClick = { coverExportFolderPicker.launch(null) }
                )
            }
            if (coverExportFolderUri.isNotBlank()) {
                ArrowPreference(
                    title = stringResource(R.string.settings_cover_export_folder_remove),
                    summary = stringResource(R.string.settings_cover_export_folder_remove_summary),
                    onClick = {
                        scope.launch { settingsManager.setCoverExportFolderUri("") }
                        Toast.makeText(context, context.getString(R.string.settings_cover_export_folder_cleared), Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchAllCategoryTypesPreference(
    enabledTypes: Set<String>,
    onEnabledChange: (String, Boolean) -> Unit
) {
    val options = listOf(
        "folder" to R.string.library_search_folders,
        "composer" to R.string.library_search_composers,
        "arranger" to R.string.library_search_arrangers,
        "lyricist" to R.string.library_search_lyricists,
        "genre" to R.string.library_search_genres,
        "year" to R.string.library_search_years
    )
    Column {
        top.yukonga.miuix.kmp.basic.Text(
            text = stringResource(R.string.settings_search_all_categories),
            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface,
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
        top.yukonga.miuix.kmp.basic.Text(
            text = stringResource(R.string.settings_search_all_categories_summary),
            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            modifier = androidx.compose.ui.Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
        )
        options.forEach { (type, labelRes) ->
            SwitchPreference(
                title = stringResource(labelRes),
                checked = type in enabledTypes,
                onCheckedChange = { onEnabledChange(type, it) }
            )
        }
    }
}

@Composable
private fun SearchAllSongMatchTypesPreference(
    enabledTypes: Set<String>,
    onEnabledChange: (String, Boolean) -> Unit
) {
    val options = listOf(
        "title" to R.string.library_search_match_title,
        "artist" to R.string.library_search_match_artist,
        "album" to R.string.library_search_match_album,
        "file_name" to R.string.library_search_match_file_name,
        "translated_name" to R.string.library_search_match_translated_name,
        "alias" to R.string.library_search_match_alias,
        "comment" to R.string.library_search_match_comment,
        "tag" to R.string.library_search_match_tag,
        "lyricist" to R.string.library_search_match_lyricist,
        "composer" to R.string.library_search_match_composer,
        "arranger" to R.string.library_search_match_arranger,
        "album_artist" to R.string.library_search_match_album_artist,
        "genre" to R.string.library_search_match_genre,
        "year" to R.string.library_search_match_year,
        "lyrics" to R.string.library_search_lyrics
    )
    Column {
        top.yukonga.miuix.kmp.basic.Text(
            text = stringResource(R.string.settings_search_all_song_match_types),
            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface,
            modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
        top.yukonga.miuix.kmp.basic.Text(
            text = stringResource(R.string.settings_search_all_song_match_types_summary),
            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            modifier = androidx.compose.ui.Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
        )
        options.forEach { (type, labelRes) ->
            SwitchPreference(
                title = stringResource(labelRes),
                checked = type in enabledTypes,
                onCheckedChange = { onEnabledChange(type, it) }
            )
        }
    }

}
