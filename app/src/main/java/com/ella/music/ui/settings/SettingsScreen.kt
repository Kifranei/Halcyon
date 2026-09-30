package com.ella.music.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.BuildConfig
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.EllaMiuixChip
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    onNavigateToSearchPage: (String) -> Unit = {},
    onNavigateToSearchAppearancePage: (String) -> Unit = {},
    onNavigateToAppearanceSettings: () -> Unit,
    onNavigateToLibrarySettings: () -> Unit,
    onNavigateToIntegrationSettings: () -> Unit,
    onNavigateToLyricSettings: () -> Unit,
    onNavigateToAudioSettings: () -> Unit,
    onNavigateToBackupSettings: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToBottomNavigationSettings: () -> Unit = onNavigateToAppearanceSettings,
    onNavigateToPlayerShortcutSettings: (String) -> Unit = { onNavigateToAppearanceSettings() },
    onNavigateToHomeDisplaySettings: (String) -> Unit = { onNavigateToAppearanceSettings() },
    onNavigateToScanFolders: () -> Unit = onNavigateToLibrarySettings,
    onNavigateToHighlightedScanFolders: (String) -> Unit = { onNavigateToScanFolders() },
    onNavigateToLyricFont: () -> Unit = onNavigateToLyricSettings,
    onNavigateToLyricPluginSources: () -> Unit = onNavigateToLyricSettings,
    onNavigateToHighlightedLyricSettings: (String) -> Unit = { onNavigateToLyricSettings() },
    onNavigateToHighlightedAppearanceSettings: (String) -> Unit = { onNavigateToAppearanceSettings() },
    onNavigateToHighlightedLibrarySettings: (String) -> Unit = { onNavigateToLibrarySettings() },
    onNavigateToHighlightedIntegrationSettings: (String) -> Unit = { onNavigateToIntegrationSettings() },
    onNavigateToHighlightedAudioSettings: (String) -> Unit = { onNavigateToAudioSettings() },
    onNavigateToHighlightedBackupSettings: (String) -> Unit = { onNavigateToBackupSettings() },
    onNavigateToEqualizer: () -> Unit = onNavigateToAudioSettings,
    onNavigateToHighlightedEqualizer: (String) -> Unit = { onNavigateToEqualizer() },
    onNavigateToCoverMediaSettings: () -> Unit = onNavigateToAppearanceSettings,
    onNavigateToHighlightedCoverMediaSettings: (String) -> Unit = { onNavigateToCoverMediaSettings() },
    onNavigateToSetupWizard: () -> Unit = {},
    onNavigateToMaintenance: () -> Unit = {},
    onNavigateToOther: () -> Unit = {},
    onBack: () -> Unit = {},
    showBackButton: Boolean = true,
    mainViewModel: MainViewModel? = null,
    playerViewModel: PlayerViewModel? = null
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { SettingsSearchFocus.reset() }
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val searchHistory by settingsManager.settingsSearchHistory.collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val inSearchMode = searchFocused || searchQuery.isNotBlank()
    androidx.activity.compose.BackHandler(enabled = inSearchMode) {
        searchQuery = ""
        searchFocused = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = com.ella.music.ui.components.ellaPageBackground()
    val settingsBackdrop = rememberLayerBackdrop()
    val searchEntries = remember(context) {
        settingsSearchCatalog.mapNotNull { definition ->
            val title = context.getString(definition.titleRes).replace(Regex("%([0-9]+\\$)?[sdif]"), "").trim()
            if (title.isBlank()) return@mapNotNull null
            SettingsSearchEntry(
                titleRes = definition.titleRes,
                sheet = definition.sheet,
                title = title,
                summary = listOfNotNull(
                    when (definition.sheet) {
                        "sizing" -> context.getString(R.string.player_lyric_style_settings)
                        "mini" -> context.getString(R.string.settings_player_mini_lyrics)
                        "island" -> context.getString(R.string.settings_xiaomi_super_island_custom)
                        else -> null
                    },
                    definition.summaryRes?.let { settingsSearchSummary(context, it) }?.takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                keywords = definition.keywords,
                target = definition.target
            )
        }
    }
    val searchResults = remember(searchQuery, searchEntries) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            emptyList()
        } else {
            searchEntries
                .mapNotNull { entry -> entry.matchScore(query)?.let { score -> entry to score } }
                .sortedWith(compareByDescending<Pair<SettingsSearchEntry, Int>> { it.second }.thenBy { it.first.title })
                .map { it.first }
                .distinctBy { "${it.title}\u0000${it.target}\u0000${it.sheet}" }

        }
    }
    fun openSettingsSearchTarget(target: SettingsSearchTarget) {
        when (target) {
            is SettingsSearchTarget.Page -> onNavigateToSearchPage(target.route)
            is SettingsSearchTarget.AppearancePage -> onNavigateToSearchAppearancePage(target.page)
            SettingsSearchTarget.SetupWizard -> onNavigateToSetupWizard()
            SettingsSearchTarget.AppearanceHub -> onNavigateToHighlightedAppearanceSettings("appearance")
            SettingsSearchTarget.BottomNavigation -> onNavigateToBottomNavigationSettings()
            is SettingsSearchTarget.PlayerShortcuts -> onNavigateToPlayerShortcutSettings(target.mode)
            is SettingsSearchTarget.HomeDisplay -> onNavigateToHomeDisplaySettings(target.highlight)
            is SettingsSearchTarget.Appearance -> onNavigateToHighlightedAppearanceSettings(target.highlight)
            is SettingsSearchTarget.Library -> onNavigateToHighlightedLibrarySettings(target.highlight)
            is SettingsSearchTarget.Scan -> onNavigateToHighlightedScanFolders(target.highlight)
            is SettingsSearchTarget.Lyrics -> onNavigateToHighlightedLyricSettings(target.highlight)
            SettingsSearchTarget.LyricFont -> onNavigateToLyricFont()
            SettingsSearchTarget.LyricPlugins -> onNavigateToLyricPluginSources()
            is SettingsSearchTarget.Audio -> onNavigateToHighlightedAudioSettings(target.highlight)
            is SettingsSearchTarget.Equalizer -> onNavigateToHighlightedEqualizer(target.highlight)
            is SettingsSearchTarget.Integrations -> onNavigateToHighlightedIntegrationSettings(target.highlight)
            is SettingsSearchTarget.Backup -> onNavigateToHighlightedBackupSettings(target.highlight)
            is SettingsSearchTarget.CoverMedia -> onNavigateToHighlightedCoverMediaSettings(target.highlight)
            SettingsSearchTarget.Maintenance -> onNavigateToMaintenance()
            SettingsSearchTarget.Logs -> onNavigateToLogs()
            SettingsSearchTarget.About -> onNavigateToAbout()
        }
    }
    val systemBarsMode by settingsManager.systemBarsMode.collectAsState(
        initial = SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH
    )
    val systemBarsReserveSpace by settingsManager.systemBarsReserveSpace.collectAsState(
        initial = SettingsManager.DEFAULT_SYSTEM_BARS_RESERVE_SPACE
    )
    val shouldReserveStatus = systemBarsReserveSpace || systemBarsMode !in setOf(
        SettingsManager.SYSTEM_BARS_MODE_HIDE_STATUS,
        SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH
    )
    val statusBarHeight = if (shouldReserveStatus) {
        WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    } else {
        0.dp
    }
    val topBarHeight = 56.dp + statusBarHeight

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(settingsBackdrop)
                .verticalScroll(rememberSettingsScrollState("settings_root"))
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(topBarHeight + 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EllaSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = {
                        if (searchQuery.isNotBlank()) {
                            scope.launch { settingsManager.recordSettingsSearchQuery(searchQuery) }
                        }
                    },
                    placeholder = stringResource(R.string.settings_search_placeholder),
                    autoFocus = false,
                    onFocusChange = { searchFocused = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp)
                )
                if (inSearchMode) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                            searchQuery = ""
                            searchFocused = false
                            focusManager.clearFocus()
                            }
                            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
                    )
                }
            }

            if (inSearchMode) {
                if (searchQuery.isNotBlank()) {
                    SmallTitle(text = stringResource(R.string.settings_search_results), insideMargin = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, bottom = 8.dp))
                    SettingsCardGroup {
                        Column {
                            if (searchResults.isEmpty()) {
                                BasicComponent(
                                    title = stringResource(R.string.settings_search_no_results),
                                    summary = searchQuery
                                )
                            } else {
                                searchResults.forEach { entry ->
                                    BasicComponent(
                                        title = entry.title,
                                        summary = entry.summary,
                                        modifier = Modifier.clickable {
                                            scope.launch { settingsManager.recordSettingsSearchQuery(searchQuery) }
                                            searchQuery = ""
                                            searchFocused = false
                                            focusManager.clearFocus()
                                            SettingsSearchFocus.select(entry.titleRes, entry.sheet)
                                            openSettingsSearchTarget(entry.target)
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else if (searchHistory.isNotEmpty()) {
                    SmallTitle(text = stringResource(R.string.settings_search_history), insideMargin = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, bottom = 8.dp))
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        searchHistory.forEach { query ->
                            EllaMiuixChip(
                                text = query,
                                selected = false,
                                onClick = {
                                    searchQuery = query
                                    searchFocused = false
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    scope.launch { settingsManager.recordSettingsSearchQuery(query) }
                                },
                                modifier = Modifier.widthIn(max = 220.dp),
                                horizontalPadding = 16.dp,
                                verticalPadding = 9.dp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.settings_search_history_clear),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    scope.launch { settingsManager.clearSettingsSearchHistory() }
                                }
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                }
            }
            if (!inSearchMode) {
                SmallTitle(text = stringResource(R.string.settings_customize), insideMargin = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, bottom = 8.dp))

                SettingsCardGroup {
                    Column {
                        ArrowPreference(
                            title = stringResource(R.string.settings_appearance_home),
                            summary = stringResource(R.string.settings_appearance_home_summary),
                            onClick = onNavigateToAppearanceSettings
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_lyrics),
                            summary = stringResource(R.string.settings_lyrics_summary),
                            onClick = onNavigateToLyricSettings
                        )
                    }
                }

                SmallTitle(text = stringResource(R.string.settings_music_playback), insideMargin = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, bottom = 8.dp))

                SettingsCardGroup {
                    Column {
                        ArrowPreference(
                            title = stringResource(R.string.settings_audio),
                            summary = stringResource(R.string.settings_audio_summary),
                            onClick = onNavigateToAudioSettings
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_library_scan),
                            summary = stringResource(R.string.settings_library_scan_summary),
                            onClick = onNavigateToLibrarySettings
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_cover_media),
                            summary = stringResource(R.string.settings_cover_media_summary),
                            onClick = onNavigateToCoverMediaSettings
                        )
                    }
                }

                SmallTitle(text = stringResource(R.string.settings_services), insideMargin = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, bottom = 8.dp))

                SettingsCardGroup {
                    Column {
                        ArrowPreference(
                            title = stringResource(R.string.settings_integrations),
                            summary = stringResource(R.string.settings_integrations_summary),
                            onClick = onNavigateToIntegrationSettings
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_backup),
                            summary = stringResource(R.string.settings_backup_summary),
                            onClick = onNavigateToBackupSettings
                        )
                    }
                }

                SettingsCardGroup {
                    ArrowPreference(title = stringResource(R.string.settings_other),
                        summary = stringResource(R.string.video_tools_summary), onClick = onNavigateToOther)
                }

                SmallTitle(text = stringResource(R.string.settings_maintenance), insideMargin = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, bottom = 8.dp))

                SettingsCardGroup {
                    Column {
                        ArrowPreference(
                            title = stringResource(R.string.settings_maintenance),
                            summary = stringResource(R.string.settings_maintenance_summary),
                            onClick = onNavigateToMaintenance
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_logs),
                            summary = stringResource(R.string.settings_logs_summary),
                            onClick = onNavigateToLogs
                        )
                        ArrowPreference(
                            title = stringResource(R.string.about),
                            summary = "${context.getString(R.string.app_name)} v${BuildConfig.VERSION_NAME}",
                            onClick = onNavigateToAbout
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(160.dp))
        }

        EllaSmallTopAppBar(
            backdrop = settingsBackdrop,
            enableProgressiveBlur = true,
            title = stringResource(R.string.settings),
            color = pageBackground,
            centeredTitle = showBackButton,
            defaultWindowInsetsPadding = shouldReserveStatus,
            navigationIcon = {
                if (showBackButton) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            },
            titleStartPadding = if (showBackButton) 64.dp else 20.dp,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

private data class SettingsSearchEntry(
    val titleRes: Int,
    val sheet: String,
    val title: String,
    val summary: String,
    val keywords: String,
    val target: SettingsSearchTarget
) {
    fun matchScore(query: String): Int? {
        val terms = query.trim().lowercase(java.util.Locale.ROOT)
            .split(Regex("""\s+"""))
            .filter(String::isNotBlank)
        if (terms.isEmpty()) return null
        val normalizedTitle = title.lowercase(java.util.Locale.ROOT)
        val normalizedSummary = summary.lowercase(java.util.Locale.ROOT)
        val normalizedKeywords = keywords.lowercase(java.util.Locale.ROOT)
        val titleMatches = terms.count { normalizedTitle.contains(it) }
        val keywordMatches = terms.count { normalizedKeywords.contains(it) }
        val summaryMatches = terms.count { normalizedSummary.contains(it) }
        if (titleMatches + keywordMatches + summaryMatches < terms.size) return null
        return titleMatches * 100 + keywordMatches * 10 + summaryMatches
    }
}

private fun settingsSearchSummary(context: android.content.Context, stringRes: Int): String {
    val summary = context.resources.getText(stringRes).toString().trim()
    // Some summaries are live-value format strings, not useful search previews.
    return if (Regex("""%(?:\d+\$)?[a-zA-Z]""").containsMatchIn(summary)) "" else summary
}
