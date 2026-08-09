package com.ella.music.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.player.PlayerLyricLayoutProfile
import com.ella.music.ui.player.isUltraWideLandscapePlayerLayout
import com.ella.music.ui.player.primaryScaleRangePercent
import com.ella.music.ui.player.primaryTextSizeRangeSp
import com.ella.music.ui.player.resolvePlayerLyricLayoutProfile
import com.ella.music.ui.player.secondaryScaleRangePercent
import com.ella.music.ui.player.secondaryTextSizeRangeSp
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference

@Composable
internal fun SettingsLyricsSection(
    playerViewModel: PlayerViewModel?,
    highlightKey: String? = null,
    onNavigateToLyricPluginSources: () -> Unit = {}
) {
    SmallTitle(text = stringResource(R.string.settings_lyrics))
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val lyricLineBlacklist by settingsManager.lyricLineBlacklist.collectAsState(initial = emptyList())
    val ignoreLyricHeaderTags by settingsManager.ignoreLyricHeaderTags.collectAsState(initial = true)
    val hideLyricExtraInfo by settingsManager.hideLyricExtraInfo.collectAsState(initial = true)
    var showBlacklistSheet by remember { mutableStateOf(false) }
    var showLyricSizingSheet by remember { mutableStateOf(false) }
    var showXiaomiSuperIslandSheet by remember { mutableStateOf(false) }
    var blacklistDraft by remember(lyricLineBlacklist) { mutableStateOf(lyricLineBlacklist.joinToString("\n")) }

    SettingsCardGroup(highlight = highlightKey == "lyric_basic" || highlightKey == "lyric_plugin_sources") {
        Column {
            ArrowPreference(
                title = stringResource(R.string.settings_lyric_plugin_sources),
                summary = stringResource(R.string.settings_lyric_plugin_sources_summary),
                onClick = onNavigateToLyricPluginSources
            )
            SettingsFocusAnchor(active = highlightKey == "lyric_basic") {
                SettingsPlayerLyricAlignmentPreference()
            }
            ArrowPreference(
                title = stringResource(R.string.player_lyric_style_settings),
                summary = stringResource(R.string.settings_lyrics_summary),
                onClick = { showLyricSizingSheet = true }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_ignore_lyric_header_tags),
                summary = stringResource(R.string.settings_ignore_lyric_header_tags_summary),
                checked = ignoreLyricHeaderTags,
                onCheckedChange = { enabled ->
                    scope.launch { settingsManager.setIgnoreLyricHeaderTags(enabled) }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_hide_lyric_extra_info),
                summary = stringResource(R.string.settings_hide_lyric_extra_info_summary),
                checked = hideLyricExtraInfo,
                onCheckedChange = { enabled ->
                    scope.launch { settingsManager.setHideLyricExtraInfo(enabled) }
                }
            )
            ArrowPreference(
                title = stringResource(R.string.settings_lyric_line_blacklist),
                summary = stringResource(R.string.settings_lyric_line_blacklist_summary, lyricLineBlacklist.size),
                onClick = {
                    blacklistDraft = lyricLineBlacklist.joinToString("\n")
                    showBlacklistSheet = true
                }
            )
        }
    }

    EllaMiuixBottomSheet(
        show = showLyricSizingSheet,
        title = stringResource(R.string.player_lyric_style_settings),
        onDismissRequest = { showLyricSizingSheet = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsPlayerLyricSizingControls()
        }
    }

    SettingsCardGroup(highlight = highlightKey == "mini_lyrics") {
        Column {
            SettingsMiniLyricsControls(highlightKey = highlightKey)
        }
    }

    SettingsCardGroup(highlight = highlightKey == "lyricon") {
        Column {
            SettingsLyriconControls(playerViewModel = playerViewModel, highlightKey = highlightKey)
        }
    }

    SettingsCardGroup(highlight = highlightKey == "live_update_lyric") {
        Column {
            SettingsLiveUpdateLyricControls(
                playerViewModel = playerViewModel,
                highlightKey = highlightKey,
                onOpenXiaomiSuperIslandSettings = { showXiaomiSuperIslandSheet = true }
            )
        }
    }

    EllaMiuixBottomSheet(
        show = showXiaomiSuperIslandSheet,
        title = stringResource(R.string.settings_xiaomi_super_island_custom),
        onDismissRequest = { showXiaomiSuperIslandSheet = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 650.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsXiaomiSuperIslandControls()
        }
    }

    SettingsCardGroup(highlight = highlightKey == "desktop_lyric") {
        Column {
            SettingsDesktopLyricControls(playerViewModel = playerViewModel, highlightKey = highlightKey)
        }
    }

    SettingsCardGroup(highlight = highlightKey == "lyric_output" || highlightKey == "coloros_lock_screen_lyric") {
        Column {
            SettingsLyricOutputControls(
                playerViewModel = playerViewModel,
                highlightKey = highlightKey
            )
        }
    }

    EllaMiuixBottomSheet(
        show = showBlacklistSheet,
        title = stringResource(R.string.settings_lyric_line_blacklist),
        onDismissRequest = { showBlacklistSheet = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            EllaMiuixTextField(
                value = blacklistDraft,
                onValueChange = { blacklistDraft = it },
                label = stringResource(R.string.settings_lyric_line_blacklist_editor_hint),
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    showBlacklistSheet = false
                    scope.launch {
                        settingsManager.setLyricLineBlacklist(blacklistDraft.lineSequence().toList())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                Text(text = stringResource(R.string.common_save))
            }
        }
    }
}

@Composable
private fun SettingsPlayerLyricSizingControls() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val layoutProfile = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.smallestScreenWidthDp
    ) {
        resolvePlayerLyricLayoutProfile(
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp,
            smallestScreenWidthDp = configuration.smallestScreenWidthDp
        )
    }
    val ultraWideLandscape = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        isUltraWideLandscapePlayerLayout(
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp
        )
    }
    val fontScaleRange = remember(layoutProfile, ultraWideLandscape) {
        layoutProfile.primaryScaleRangePercent(ultraWideLandscape)
    }
    val secondaryFontScaleRange = remember(layoutProfile, ultraWideLandscape) {
        layoutProfile.secondaryScaleRangePercent(ultraWideLandscape)
    }
    val primaryTextSizeRange = remember(layoutProfile) { layoutProfile.primaryTextSizeRangeSp() }
    val secondaryTextSizeRange = remember(layoutProfile) { layoutProfile.secondaryTextSizeRangeSp() }
    val lyricFontScale by settingsManager.lyricFontScale.collectAsState(initial = 100)
    val lyricSecondaryFontScale by settingsManager.lyricSecondaryFontScale.collectAsState(initial = 100)
    val lyricPrimaryTextSize by when (layoutProfile) {
        PlayerLyricLayoutProfile.Compact -> settingsManager.lyricCompactPrimaryTextSize
            .collectAsState(initial = SettingsManager.LYRIC_COMPACT_PRIMARY_TEXT_SIZE_DEFAULT_SP)
        PlayerLyricLayoutProfile.Wide -> settingsManager.lyricWidePrimaryTextSize
            .collectAsState(initial = SettingsManager.LYRIC_WIDE_PRIMARY_TEXT_SIZE_DEFAULT_SP)
    }
    val lyricSecondaryTextSize by when (layoutProfile) {
        PlayerLyricLayoutProfile.Compact -> settingsManager.lyricCompactSecondaryTextSize
            .collectAsState(initial = SettingsManager.LYRIC_COMPACT_SECONDARY_TEXT_SIZE_DEFAULT_SP)
        PlayerLyricLayoutProfile.Wide -> settingsManager.lyricWideSecondaryTextSize
            .collectAsState(initial = SettingsManager.LYRIC_WIDE_SECONDARY_TEXT_SIZE_DEFAULT_SP)
    }
    SettingsIntSliderPreference(
        title = stringResource(R.string.player_lyric_font_scale),
        summary = stringResource(
            R.string.settings_lyric_scale_summary,
            fontScaleRange.first,
            fontScaleRange.last
        ),
        value = lyricFontScale.coerceIn(fontScaleRange),
        valueRange = fontScaleRange,
        valueText = "${lyricFontScale.coerceIn(fontScaleRange)}%",
        onValueChange = { value ->
            scope.launch { settingsManager.setLyricFontScale(value) }
        }
    )
    SettingsIntSliderPreference(
        title = stringResource(R.string.player_lyric_font_size),
        summary = stringResource(
            R.string.settings_lyric_font_size_summary,
            primaryTextSizeRange.first,
            primaryTextSizeRange.last
        ),
        value = lyricPrimaryTextSize.coerceIn(primaryTextSizeRange),
        valueRange = primaryTextSizeRange,
        valueText = "${lyricPrimaryTextSize.coerceIn(primaryTextSizeRange)}sp",
        onValueChange = { value ->
            scope.launch {
                when (layoutProfile) {
                    PlayerLyricLayoutProfile.Compact -> settingsManager.setLyricCompactPrimaryTextSize(value)
                    PlayerLyricLayoutProfile.Wide -> settingsManager.setLyricWidePrimaryTextSize(value)
                }
            }
        }
    )
    SettingsIntSliderPreference(
        title = stringResource(R.string.player_lyric_secondary_font_scale),
        summary = stringResource(
            R.string.settings_lyric_scale_summary,
            secondaryFontScaleRange.first,
            secondaryFontScaleRange.last
        ),
        value = lyricSecondaryFontScale.coerceIn(secondaryFontScaleRange),
        valueRange = secondaryFontScaleRange,
        valueText = "${lyricSecondaryFontScale.coerceIn(secondaryFontScaleRange)}%",
        onValueChange = { value ->
            scope.launch { settingsManager.setLyricSecondaryFontScale(value) }
        }
    )
    SettingsIntSliderPreference(
        title = stringResource(R.string.player_lyric_secondary_font_size),
        summary = stringResource(
            R.string.settings_lyric_font_size_summary,
            secondaryTextSizeRange.first,
            secondaryTextSizeRange.last
        ),
        value = lyricSecondaryTextSize.coerceIn(secondaryTextSizeRange),
        valueRange = secondaryTextSizeRange,
        valueText = "${lyricSecondaryTextSize.coerceIn(secondaryTextSizeRange)}sp",
        onValueChange = { value ->
            scope.launch {
                when (layoutProfile) {
                    PlayerLyricLayoutProfile.Compact -> settingsManager.setLyricCompactSecondaryTextSize(value)
                    PlayerLyricLayoutProfile.Wide -> settingsManager.setLyricWideSecondaryTextSize(value)
                }
            }
        }
    )
}

@Composable
private fun SettingsPlayerLyricAlignmentPreference() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val playerLyricTextAlign by settingsManager.playerLyricTextAlign.collectAsState(initial = SettingsManager.PLAYER_LYRIC_ALIGN_LEFT)
    val labels = listOf(
        stringResource(R.string.settings_status_align_left),
        stringResource(R.string.settings_status_align_center),
        stringResource(R.string.settings_status_align_right)
    )
    val entries = remember(labels) {
        labels.map { DropdownItem(title = it) }
    }

    WindowSpinnerPreference(
        title = stringResource(R.string.settings_player_lyric_text_align),
        summary = stringResource(
            R.string.settings_current_value,
            labels[playerLyricTextAlign.coerceIn(0, 2)]
        ),
        items = entries,
        selectedIndex = playerLyricTextAlign.coerceIn(0, 2),
        onSelectedIndexChange = { index ->
            scope.launch { settingsManager.setPlayerLyricTextAlign(index) }
        }
    )
}
