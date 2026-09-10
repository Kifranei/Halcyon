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
import com.ella.music.ui.components.EllaMiuixSheetColumn
import top.yukonga.miuix.kmp.basic.TextField
import com.ella.music.ui.player.PlayerLyricLayoutProfile
import com.ella.music.ui.player.isUltraWideLandscapePlayerLayout
import com.ella.music.ui.player.primaryScaleRangePercent
import com.ella.music.ui.player.primaryTextSizeRangeSp
import com.ella.music.ui.player.resolvePlayerLyricLayoutProfile
import com.ella.music.ui.player.secondaryScaleRangePercent
import com.ella.music.ui.player.secondaryTextSizeRangeSp
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
    val lyricOpeningTemplate by settingsManager.lyricOpeningTemplate.collectAsState(initial = "")
    val lyricOpeningAsFallback by settingsManager.lyricOpeningAsFallback.collectAsState(initial = false)
    val lyricShareLongPressEnabled by settingsManager.lyricShareLongPressEnabled.collectAsState(initial = true)
    val lyricRainbowEnabled by settingsManager.lyricRainbowEnabled.collectAsState(initial = false)
    val appleMusicLyricsFullTrailBreath by settingsManager.appleMusicLyricsFullTrailBreath.collectAsState(initial = false)
    val lyricHdrHighlightEnabled by settingsManager.lyricHdrHighlightEnabled.collectAsState(initial = false)
    val lyricWordSeekEnabled by settingsManager.lyricWordSeekEnabled.collectAsState(initial = false)
    val lyricTouchFeedbackEnabled by settingsManager.lyricTouchFeedbackEnabled.collectAsState(initial = false)
    val lyricPauseCurrentOnly by settingsManager.lyricPauseCurrentOnly.collectAsState(initial = true)
    val immersiveLyricSwipe by settingsManager.playerImmersiveLyricSwipe.collectAsState(initial = false)
    val lyricNonCurrentBlurPercent by settingsManager.lyricNonCurrentBlurPercent.collectAsState(initial = 70)
    var showBlacklistSheet by remember { mutableStateOf(false) }
    var showLyricSizingSheet by remember { mutableStateOf(false) }
    var showPlayerMiniLyricsSheet by remember { mutableStateOf(false) }
    var showOpeningTemplateSheet by remember { mutableStateOf(false) }
    var showXiaomiSuperIslandSheet by remember { mutableStateOf(false) }
    var blacklistDraft by remember(lyricLineBlacklist) { mutableStateOf(lyricLineBlacklist.joinToString("\n")) }
    var openingTemplateDraft by remember(lyricOpeningTemplate) { mutableStateOf(lyricOpeningTemplate) }

    SettingsCardGroup(
        highlight = highlightKey == "lyric_basic" ||
            highlightKey == "lyric_plugin_sources" ||
            highlightKey == "lyric_word_seek" ||
            highlightKey == "lyric_touch_feedback"
    ) {
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
                title = stringResource(R.string.settings_player_mini_lyrics),
                summary = stringResource(R.string.settings_player_mini_lyrics_summary),
                onClick = { showPlayerMiniLyricsSheet = true }
            )
            ArrowPreference(
                title = stringResource(R.string.player_lyric_style_settings),
                summary = stringResource(R.string.settings_lyrics_summary),
                onClick = { showLyricSizingSheet = true }
            )
            SettingsFocusAnchor(active = highlightKey == "lyric_word_seek") {
                SwitchPreference(
                    title = stringResource(R.string.settings_lyric_word_seek),
                    summary = stringResource(R.string.settings_lyric_word_seek_summary),
                    checked = lyricWordSeekEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsManager.setLyricWordSeekEnabled(enabled) }
                    }
                )
            }
            SettingsFocusAnchor(active = highlightKey == "lyric_touch_feedback") {
                SwitchPreference(
                    title = stringResource(R.string.settings_lyric_touch_feedback),
                    summary = stringResource(R.string.settings_lyric_touch_feedback_summary),
                    checked = lyricTouchFeedbackEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsManager.setLyricTouchFeedbackEnabled(enabled) }
                    }
                )
            }
            SwitchPreference(
                title = stringResource(R.string.settings_lyric_pause_current_only),
                summary = stringResource(R.string.settings_lyric_pause_current_only_summary),
                checked = lyricPauseCurrentOnly,
                onCheckedChange = { enabled ->
                    scope.launch { settingsManager.setLyricPauseCurrentOnly(enabled) }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_immersive_lyric_swipe),
                summary = stringResource(R.string.settings_immersive_lyric_swipe_summary),
                checked = immersiveLyricSwipe,
                onCheckedChange = { enabled ->
                    scope.launch { settingsManager.setPlayerImmersiveLyricSwipe(enabled) }
                }
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
            ArrowPreference(
                title = stringResource(R.string.settings_lyric_opening_template),
                summary = lyricOpeningTemplate.ifBlank {
                    stringResource(R.string.settings_lyric_opening_template_summary)
                },
                onClick = {
                    openingTemplateDraft = lyricOpeningTemplate
                    showOpeningTemplateSheet = true
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_lyric_opening_as_fallback),
                summary = stringResource(R.string.settings_lyric_opening_as_fallback_summary),
                checked = lyricOpeningAsFallback,
                onCheckedChange = { enabled ->
                    scope.launch { settingsManager.setLyricOpeningAsFallback(enabled) }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_lyric_share_long_press),
                summary = stringResource(R.string.settings_lyric_share_long_press_summary),
                checked = lyricShareLongPressEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { settingsManager.setLyricShareLongPressEnabled(enabled) }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_lyric_rainbow),
                summary = stringResource(R.string.settings_lyric_rainbow_summary),
                checked = lyricRainbowEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { settingsManager.setLyricRainbowEnabled(enabled) }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_apple_music_lyrics_full_trail_breath),
                summary = stringResource(R.string.settings_apple_music_lyrics_full_trail_breath_summary),
                checked = appleMusicLyricsFullTrailBreath,
                onCheckedChange = { enabled ->
                    scope.launch { settingsManager.setAppleMusicLyricsFullTrailBreath(enabled) }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_lyric_hdr_highlight),
                summary = stringResource(R.string.settings_lyric_hdr_highlight_summary),
                checked = lyricHdrHighlightEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { settingsManager.setLyricHdrHighlightEnabled(enabled) }
                }
            )
        }
    }

    EllaMiuixBottomSheet(
        show = showOpeningTemplateSheet,
        title = stringResource(R.string.settings_lyric_opening_template),
        enableNestedScroll = false,
        startAction = {
            IconButton(onClick = { showOpeningTemplateSheet = false }) {
                Icon(
                    imageVector = MiuixIcons.Regular.Close,
                    contentDescription = stringResource(R.string.common_cancel),
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        endAction = {
            IconButton(
                onClick = {
                    showOpeningTemplateSheet = false
                    scope.launch { settingsManager.setLyricOpeningTemplate(openingTemplateDraft) }
                }
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Ok,
                    contentDescription = stringResource(R.string.common_save),
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        onDismissRequest = { showOpeningTemplateSheet = false }
    ) {
        EllaMiuixSheetColumn(
            verticalPadding = 8.dp,
            spacing = 8.dp,
            showHandle = false
        ) {
            Text(
                text = stringResource(R.string.settings_lyric_opening_template_tokens),
                modifier = Modifier.padding(horizontal = 18.dp)
            )
            TextField(
                value = openingTemplateDraft,
                onValueChange = { openingTemplateDraft = it },
                label = stringResource(R.string.settings_lyric_opening_template_hint),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
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
            SettingsPlayerLyricSizingControls(initialBlurPercent = lyricNonCurrentBlurPercent)
        }
    }

    EllaMiuixBottomSheet(
        show = showPlayerMiniLyricsSheet,
        title = stringResource(R.string.settings_player_mini_lyrics),
        onDismissRequest = { showPlayerMiniLyricsSheet = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsPlayerMiniLyricControls()
        }
    }

    SettingsCardGroup(
        highlight = highlightKey == "mini_lyrics" ||
            (highlightKey?.startsWith("mini_player") == true && highlightKey != "mini_player_long_press")
    ) {
        Column {
            SettingsMiniLyricsControls(highlightKey = highlightKey)
        }
    }

    SettingsCardGroup(highlight = highlightKey == "lyricon") {
        Column {
            SettingsLyriconControls(playerViewModel = playerViewModel, highlightKey = highlightKey)
        }
    }

    SettingsCardGroup(
        highlight = highlightKey == "live_update_lyric" || highlightKey == "vivo_atom_walkman_whitelist"
    ) {
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
        enableNestedScroll = false,
        startAction = {
            IconButton(onClick = { showBlacklistSheet = false }) {
                Icon(
                    imageVector = MiuixIcons.Regular.Close,
                    contentDescription = stringResource(R.string.common_cancel),
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        endAction = {
            IconButton(
                onClick = {
                    showBlacklistSheet = false
                    scope.launch {
                        settingsManager.setLyricLineBlacklist(blacklistDraft.lineSequence().toList())
                    }
                }
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Ok,
                    contentDescription = stringResource(R.string.common_save),
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        onDismissRequest = { showBlacklistSheet = false }
    ) {
        EllaMiuixSheetColumn(
            verticalPadding = 8.dp,
            spacing = 8.dp,
            showHandle = false
        ) {
            TextField(
                value = blacklistDraft,
                onValueChange = { blacklistDraft = it },
                label = stringResource(R.string.settings_lyric_line_blacklist_editor_hint),
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SettingsPlayerLyricSizingControls(initialBlurPercent: Int? = null) {
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
    val lyricNonCurrentBlurPercent by settingsManager.lyricNonCurrentBlurPercent.collectAsState(initial = initialBlurPercent ?: 70)
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
        title = stringResource(R.string.settings_lyric_non_current_blur),
        summary = stringResource(R.string.settings_lyric_non_current_blur_summary),
        value = lyricNonCurrentBlurPercent,
        valueRange = 0..100,
        valueText = "$lyricNonCurrentBlurPercent%",
        onValueChange = { value ->
            scope.launch { settingsManager.setLyricNonCurrentBlurPercent(value) }
        }
    )
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
private fun SettingsPlayerMiniLyricControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val scale by settingsManager.playerMiniLyricScale.collectAsState(initial = 100)
    val primarySize by settingsManager.playerMiniLyricPrimarySize.collectAsState(initial = 19)
    val secondarySize by settingsManager.playerMiniLyricSecondarySize.collectAsState(initial = 14)
    val lineSpacing by settingsManager.playerMiniLyricLineSpacing.collectAsState(initial = 7)
    val textAlign by settingsManager.playerMiniLyricTextAlign.collectAsState(initial = 0)
    val alignLabels = listOf(
        stringResource(R.string.settings_status_align_left),
        stringResource(R.string.settings_status_align_center),
        stringResource(R.string.settings_status_align_right)
    )
    WindowSpinnerPreference(
        title = stringResource(R.string.settings_player_lyric_text_align),
        summary = stringResource(R.string.settings_player_lyric_text_align_summary),
        items = alignLabels.map { DropdownItem(title = it) },
        selectedIndex = textAlign.coerceIn(0, 2),
        onSelectedIndexChange = { value ->
            scope.launch { settingsManager.setPlayerMiniLyricTextAlign(value) }
        }
    )
    SettingsIntSliderPreference(
        title = stringResource(R.string.player_lyric_font_scale),
        summary = stringResource(R.string.settings_player_mini_lyrics_scale_summary),
        value = scale,
        valueRange = 50..150,
        valueText = "$scale%",
        onValueChange = { value -> scope.launch { settingsManager.setPlayerMiniLyricScale(value) } }
    )
    SettingsIntSliderPreference(
        title = stringResource(R.string.player_lyric_font_size),
        summary = stringResource(R.string.settings_lyric_font_size_summary, 12, 32),
        value = primarySize,
        valueRange = 12..32,
        valueText = "${primarySize}sp",
        onValueChange = { value -> scope.launch { settingsManager.setPlayerMiniLyricPrimarySize(value) } }
    )
    SettingsIntSliderPreference(
        title = stringResource(R.string.player_lyric_secondary_font_size),
        summary = stringResource(R.string.settings_lyric_font_size_summary, 10, 28),
        value = secondarySize,
        valueRange = 10..28,
        valueText = "${secondarySize}sp",
        onValueChange = { value -> scope.launch { settingsManager.setPlayerMiniLyricSecondarySize(value) } }
    )
    SettingsIntSliderPreference(
        title = stringResource(R.string.settings_player_mini_lyrics_line_spacing),
        summary = stringResource(R.string.settings_player_mini_lyrics_line_spacing_summary),
        value = lineSpacing,
        valueRange = 0..24,
        valueText = "${lineSpacing}dp",
        onValueChange = { value -> scope.launch { settingsManager.setPlayerMiniLyricLineSpacing(value) } }
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
        summary = stringResource(R.string.settings_player_lyric_text_align_summary),
        items = entries,
        selectedIndex = playerLyricTextAlign.coerceIn(0, 2),
        onSelectedIndexChange = { index ->
            scope.launch { settingsManager.setPlayerLyricTextAlign(index) }
        }
    )

}
