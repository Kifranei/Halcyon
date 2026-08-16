package com.ella.music.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.BottomBarGlassEffect
import com.ella.music.data.SettingsManager
import com.ella.music.player.PlaybackWidgetUpdater
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference

@Composable
internal fun SettingsAppearanceSection(
    highlightKey: String? = null,
    onNavigateToBottomNavigationSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }

    val themeMode by settingsManager.themeMode.collectAsState(initial = 0)
    val appLanguage by settingsManager.appLanguage.collectAsState(initial = SettingsManager.APP_LANGUAGE_SYSTEM)
    val appFontScalePercent by settingsManager.appFontScalePercent.collectAsState(
        initial = SettingsManager.DEFAULT_APP_FONT_SCALE_PERCENT
    )
    val appDisplayScalePercent by settingsManager.appDisplayScalePercent.collectAsState(
        initial = SettingsManager.DEFAULT_APP_DISPLAY_SCALE_PERCENT
    )
    val appIconStyle by settingsManager.appIconStyle.collectAsState(initial = SettingsManager.APP_ICON_STYLE_DEFAULT)
    val widgetSafeLayout by settingsManager.widgetSafeLayout.collectAsState(initial = false)
    val bottomBarGlassEffect by settingsManager.bottomBarGlassEffect.collectAsState(initial = BottomBarGlassEffect.LiquidGlass)
    val systemBarsMode by settingsManager.systemBarsMode.collectAsState(
        initial = SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH
    )
    val systemBarsReserveSpace by settingsManager.systemBarsReserveSpace.collectAsState(
        initial = SettingsManager.DEFAULT_SYSTEM_BARS_RESERVE_SPACE
    )
    val startupPosterEnabled by settingsManager.startupPosterEnabled.collectAsState(initial = false)
    val startupPosterUri by settingsManager.startupPosterUri.collectAsState(initial = "")
    val startupPosterDurationMs by settingsManager.startupPosterDurationMs.collectAsState(
        initial = SettingsManager.DEFAULT_STARTUP_POSTER_DURATION_MS
    )
    val appWallpaperEnabled by settingsManager.appWallpaperEnabled.collectAsState(initial = false)
    val appWallpaperUri by settingsManager.appWallpaperUri.collectAsState(initial = "")
    val appWallpaperOpacity by settingsManager.appWallpaperOpacity.collectAsState(initial = 100)
    val appWallpaperDim by settingsManager.appWallpaperDim.collectAsState(initial = 30)
    val appWallpaperContentOverlay by settingsManager.appWallpaperContentOverlay.collectAsState(initial = 24)
    val playerBackgroundEnabled by settingsManager.playerBackgroundEnabled.collectAsState(initial = false)
    val playerBackgroundUri by settingsManager.playerBackgroundUri.collectAsState(initial = "")
    val playerBackgroundOpacity by settingsManager.playerBackgroundOpacity.collectAsState(initial = 100)
    val playerBackgroundDim by settingsManager.playerBackgroundDim.collectAsState(initial = 26)
    val beautifulLyricsBackground by settingsManager.playerBeautifulLyricsBackground.collectAsState(initial = false)
    val playerDynamicFlowEnabled by settingsManager.playerDynamicFlowEnabled.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_DYNAMIC_FLOW_ENABLED
    )
    val beautifulLyricsSpeed by settingsManager.playerBeautifulLyricsSpeed.collectAsState(initial = 25)
    val beautifulLyricsBlur by settingsManager.playerBeautifulLyricsBlur.collectAsState(initial = 32)
    val beautifulLyricsBrightness by settingsManager.playerBeautifulLyricsBrightness.collectAsState(initial = 70)
    val homeCardColor by settingsManager.homeCardColor.collectAsState(initial = "")
    val homeCardOpacity by settingsManager.homeCardOpacity.collectAsState(initial = 58)
    val dynamicCoverEnabled by settingsManager.dynamicCoverEnabled.collectAsState(initial = false)
    val musicVideoSyncEnabled by settingsManager.musicVideoSyncEnabled.collectAsState(
        initial = SettingsManager.DEFAULT_MUSIC_VIDEO_SYNC_ENABLED
    )
    val musicVideoCaptureSubtitles by settingsManager.musicVideoCaptureSubtitles.collectAsState(initial = false)
    val musicVideoOrientation by settingsManager.musicVideoOrientation.collectAsState(
        initial = SettingsManager.DEFAULT_MUSIC_VIDEO_ORIENTATION
    )
    val showLocalMusicVideoInLists by settingsManager.showLocalMusicVideoInLists.collectAsState(initial = true)
    val showOnlineMusicVideoInLists by settingsManager.showOnlineMusicVideoInLists.collectAsState(initial = true)
    val dynamicCoverCustomFolders by settingsManager.dynamicCoverCustomFoldersRaw.collectAsState(initial = "")
    val musicVideoCustomFolders by settingsManager.musicVideoCustomFoldersRaw.collectAsState(initial = "")
    val hiResLogoEnabled by settingsManager.hiResLogoEnabled.collectAsState(initial = false)
    val hiResLogoUri by settingsManager.hiResLogoUri.collectAsState(initial = "")
    val playerImmersiveCover by settingsManager.playerImmersiveCover.collectAsState(initial = false)
    val playerCoverContentColor by settingsManager.playerCoverContentColor.collectAsState(initial = false)
    val transportButtonOutlines by settingsManager.transportButtonOutlines.collectAsState(
        initial = SettingsManager.DEFAULT_TRANSPORT_BUTTON_OUTLINES
    )
    val playerTapSeekEnabled by settingsManager.playerTapSeekEnabled.collectAsState(initial = true)
    val playerShowTotalDuration by settingsManager.playerShowTotalDuration.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_SHOW_TOTAL_DURATION
    )
    val playerShowSongAnnotation by settingsManager.playerShowSongAnnotation.collectAsState(initial = true)
    val playerCoverSwipeEnabled by settingsManager.playerCoverSwipeEnabled.collectAsState(initial = true)
    val playerTitlePosition by settingsManager.playerTitlePosition.collectAsState(
        initial = SettingsManager.PLAYER_TITLE_POSITION_BELOW_COVER
    )
    val playerPageStyle by settingsManager.playerPageStyle.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_PAGE_STYLE
    )
    val playerLandscapeStyle by settingsManager.playerLandscapeStyle.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_LANDSCAPE_STYLE
    )
    val playlistSpecialEntriesVisible by settingsManager.playlistSpecialEntriesVisible.collectAsState(initial = false)
    val showPlayNextInLists by settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val showRemoveFromPlaylistButton by settingsManager.showRemoveFromPlaylistButton.collectAsState(initial = true)
    val excludeSearchResultsFromPlaylist by settingsManager.excludeSearchResultsFromPlaylist.collectAsState(initial = false)
    val autoShowSearchKeyboard by settingsManager.autoShowSearchKeyboard.collectAsState(initial = true)
    val openPlayerOnPlay by settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val categoryGridColumns by settingsManager.categoryGridColumns.collectAsState(initial = 2)
    val playerBgTheme by settingsManager.playerBackgroundTheme.collectAsState(initial = SettingsManager.PLAYER_BG_THEME_DARK)
    val beautifulLyricsBackgroundLabels = listOf(
        stringResource(R.string.settings_beautiful_lyrics_background_static),
        stringResource(R.string.settings_beautiful_lyrics_background_dynamic)
    )
    val beautifulLyricsBackgroundEntries = remember(beautifulLyricsBackgroundLabels) {
        beautifulLyricsBackgroundLabels.map { DropdownItem(title = it) }
    }
    val selectedBeautifulLyricsBackground = if (beautifulLyricsBackground) 1 else 0
    val playerTitlePositionLabels = listOf(
        stringResource(R.string.settings_player_title_position_below_cover),
        stringResource(R.string.settings_player_title_position_above_cover)
    )
    val selectedPlayerTitlePosition = playerTitlePosition.coerceIn(playerTitlePositionLabels.indices)
    val playerTitlePositionEntries = remember(playerTitlePositionLabels) {
        playerTitlePositionLabels.map { DropdownItem(title = it) }
    }
    val playerPageStyleOptions = listOf(
        SettingsManager.PLAYER_PAGE_STYLE_HALCYON to
            stringResource(R.string.settings_player_page_style_halcyon),
        SettingsManager.PLAYER_PAGE_STYLE_APPLE_MUSIC to
            stringResource(R.string.settings_player_page_style_apple_music),
        SettingsManager.PLAYER_PAGE_STYLE_IMMERSIVE_LYRICS to
            stringResource(R.string.settings_player_page_style_immersive_lyrics)
    )
    val selectedPlayerPageStyle = playerPageStyleOptions
        .indexOfFirst { (style, _) -> style == playerPageStyle }
        .takeIf { it >= 0 }
        ?: 0
    val playerPageStyleEntries = remember(playerPageStyleOptions) {
        playerPageStyleOptions.map { (_, label) -> DropdownItem(title = label) }
    }
    val playerLandscapeStyleOptions = listOf(
        SettingsManager.PLAYER_LANDSCAPE_STYLE_WIDE to
            stringResource(R.string.settings_player_landscape_style_wide),
        SettingsManager.PLAYER_LANDSCAPE_STYLE_COVER_FLOW to
            stringResource(R.string.settings_player_landscape_style_cover_flow),
        SettingsManager.PLAYER_LANDSCAPE_STYLE_MUSIC_VIDEO to
            stringResource(R.string.settings_player_landscape_style_music_video)
    )
    val selectedPlayerLandscapeStyle = playerLandscapeStyleOptions
        .indexOfFirst { (style, _) -> style == playerLandscapeStyle }
        .takeIf { it >= 0 }
        ?: 0
    val playerLandscapeStyleEntries = remember(playerLandscapeStyleOptions) {
        playerLandscapeStyleOptions.map { (_, label) -> DropdownItem(title = label) }
    }
    val musicVideoOrientationOptions = listOf(
        SettingsManager.MUSIC_VIDEO_ORIENTATION_SYSTEM to
            stringResource(R.string.settings_music_video_orientation_system),
        SettingsManager.MUSIC_VIDEO_ORIENTATION_VIDEO to
            stringResource(R.string.settings_music_video_orientation_video),
        SettingsManager.MUSIC_VIDEO_ORIENTATION_LANDSCAPE to
            stringResource(R.string.settings_music_video_orientation_landscape),
        SettingsManager.MUSIC_VIDEO_ORIENTATION_PORTRAIT to
            stringResource(R.string.settings_music_video_orientation_portrait)
    )
    val selectedMusicVideoOrientation = musicVideoOrientationOptions
        .indexOfFirst { (orientation, _) -> orientation == musicVideoOrientation }
        .takeIf { it >= 0 }
        ?: 1
    val musicVideoOrientationEntries = remember(musicVideoOrientationOptions) {
        musicVideoOrientationOptions.map { (_, label) -> DropdownItem(title = label) }
    }
    val systemBarsModeLabels = listOf(
        stringResource(R.string.settings_system_bars_show_both),
        stringResource(R.string.settings_system_bars_hide_status),
        stringResource(R.string.settings_system_bars_hide_navigation),
        stringResource(R.string.settings_system_bars_hide_both)
    )
    val selectedSystemBarsMode = systemBarsMode.coerceIn(systemBarsModeLabels.indices)
    val systemBarsModeEntries = remember(systemBarsModeLabels) {
        systemBarsModeLabels.map { DropdownItem(title = it) }
    }

    val themeLabels = listOf(
        stringResource(R.string.theme_follow_system),
        stringResource(R.string.theme_light),
        stringResource(R.string.theme_dark)
    )
    val selectedThemeMode = themeMode.coerceIn(themeLabels.indices)
    val themeEntries = remember(themeLabels) { themeLabels.map { DropdownItem(title = it) } }

    val monetMode by settingsManager.monetColorMode.collectAsState(initial = 0)
    val monetLabels = listOf(
        stringResource(R.string.settings_monet_off),
        stringResource(R.string.settings_monet_wallpaper),
        stringResource(R.string.settings_monet_cover)
    )
    val selectedMonetMode = monetMode.coerceIn(monetLabels.indices)
    val monetEntries = remember(monetLabels) { monetLabels.map { DropdownItem(title = it) } }

    val languageOptions = listOf(
        SettingsManager.APP_LANGUAGE_SYSTEM to stringResource(R.string.settings_language_system),
        SettingsManager.APP_LANGUAGE_ZH_CN to stringResource(R.string.settings_language_simplified_chinese),
        SettingsManager.APP_LANGUAGE_ZH_TW to stringResource(R.string.settings_language_traditional_chinese),
        SettingsManager.APP_LANGUAGE_EN to stringResource(R.string.settings_language_english),
        SettingsManager.APP_LANGUAGE_JA to stringResource(R.string.settings_language_japanese),
        SettingsManager.APP_LANGUAGE_KO to stringResource(R.string.settings_language_korean),
        SettingsManager.APP_LANGUAGE_DE to stringResource(R.string.settings_language_german),
        SettingsManager.APP_LANGUAGE_FR to stringResource(R.string.settings_language_french),
        SettingsManager.APP_LANGUAGE_RU to stringResource(R.string.settings_language_russian)
    )
    val selectedLanguageIndex = languageOptions.indexOfFirst { it.first == appLanguage }.takeIf { it >= 0 } ?: 0
    val languageEntries = remember(languageOptions) {
        languageOptions.map { (_, label) -> DropdownItem(title = label) }
    }
    val languageSummary = when (languageOptions.getOrNull(selectedLanguageIndex)?.first) {
        SettingsManager.APP_LANGUAGE_ZH_CN -> stringResource(R.string.settings_language_summary_simplified_chinese)
        SettingsManager.APP_LANGUAGE_ZH_TW -> stringResource(R.string.settings_language_summary_traditional_chinese)
        SettingsManager.APP_LANGUAGE_EN -> stringResource(R.string.settings_language_summary_english)
        SettingsManager.APP_LANGUAGE_JA -> stringResource(R.string.settings_language_summary_japanese)
        SettingsManager.APP_LANGUAGE_KO -> stringResource(R.string.settings_language_summary_korean)
        SettingsManager.APP_LANGUAGE_DE -> stringResource(R.string.settings_language_summary_german)
        SettingsManager.APP_LANGUAGE_FR -> stringResource(R.string.settings_language_summary_french)
        SettingsManager.APP_LANGUAGE_RU -> stringResource(R.string.settings_language_summary_russian)
        else -> stringResource(R.string.settings_language_summary_system)
    }
    val appIconOptions = listOf(
        SettingsManager.APP_ICON_STYLE_DEFAULT to stringResource(R.string.settings_app_icon_default),
        SettingsManager.APP_ICON_STYLE_ANIME to stringResource(R.string.settings_app_icon_anime),
        SettingsManager.APP_ICON_STYLE_BLACK_HAIR to stringResource(R.string.settings_app_icon_black_hair),
        SettingsManager.APP_ICON_STYLE_LOLI to stringResource(R.string.settings_app_icon_loli)
    )
    val selectedAppIconIndex = appIconOptions.indexOfFirst { it.first == appIconStyle }
        .takeIf { it >= 0 }
        ?: 0
    val appIconEntries = remember(appIconOptions) {
        appIconOptions.map { (_, label) -> DropdownItem(title = label) }
    }

    val bottomBarGlassEffects = remember {
        listOf(BottomBarGlassEffect.Blur, BottomBarGlassEffect.LiquidGlass)
    }
    val bottomBarGlassBlurLabel = stringResource(R.string.bottom_bar_glass_effect_blur)
    val bottomBarGlassLiquidLabel = stringResource(R.string.bottom_bar_glass_effect_liquid)
    val bottomBarGlassEntries = remember(bottomBarGlassBlurLabel, bottomBarGlassLiquidLabel) {
        listOf(
            DropdownItem(title = bottomBarGlassBlurLabel),
            DropdownItem(title = bottomBarGlassLiquidLabel)
        )
    }
    val selectedBottomBarGlassEffectIndex =
        bottomBarGlassEffects.indexOf(bottomBarGlassEffect).takeIf { it >= 0 } ?: 0
    val bottomBarGlassSummary = when (bottomBarGlassEffect) {
        BottomBarGlassEffect.Blur -> stringResource(R.string.settings_bottom_bar_glass_effect_summary_blur)
        BottomBarGlassEffect.LiquidGlass -> stringResource(R.string.settings_bottom_bar_glass_effect_summary_liquid)
    }

    val isTabletDevice = context.resources.configuration.smallestScreenWidthDp >= 600
    val categoryGridRange = if (isTabletDevice) 5..8 else 1..4
    val categoryGridEntries = remember(context, isTabletDevice) {
        categoryGridRange.map { columns ->
            DropdownItem(
                title = context.getString(R.string.settings_category_grid_columns_option, columns),
                summary = when (columns) {
                    1 -> context.getString(R.string.settings_category_grid_columns_option_summary_single)
                    4, 8 -> context.getString(R.string.settings_category_grid_columns_option_summary_dense)
                    else -> context.getString(R.string.settings_category_grid_columns_option_summary_default)
                }
            )
        }
    }

    val startupPosterPicker = rememberAppearanceImagePicker(
        currentUri = startupPosterUri,
        imageName = "startup_poster",
        onImagePersisted = settingsManager::setStartupPosterUri
    )
    val appWallpaperPicker = rememberAppearanceImagePicker(
        currentUri = appWallpaperUri,
        imageName = "app_wallpaper",
        onImagePersisted = settingsManager::setAppWallpaperUri
    )
    val playerBackgroundPicker = rememberAppearanceImagePicker(
        currentUri = playerBackgroundUri,
        imageName = "player_background",
        onImagePersisted = settingsManager::setPlayerBackgroundUri
    )
    val hiResLogoPicker = rememberAppearanceImagePicker(
        currentUri = hiResLogoUri,
        imageName = "hi_res_logo",
        onImagePersisted = settingsManager::setHiResLogoUri
    )
    val dynamicCoverPermissionLauncher = rememberDynamicCoverPermissionLauncher(settingsManager)
    val musicVideoSyncPermissionLauncher = rememberMusicVideoSyncPermissionLauncher(settingsManager)
    val dynamicCoverFolderPicker = rememberDynamicCoverFolderPicker(
        currentFolders = dynamicCoverCustomFolders,
        settingsManager = settingsManager
    )
    val musicVideoFolderPicker = rememberMusicVideoFolderPicker(
        currentFolders = musicVideoCustomFolders,
        settingsManager = settingsManager
    )

    SmallTitle(text = stringResource(R.string.settings_appearance))

    fun isHighlighted(vararg keys: String): Boolean =
        highlightKey == "appearance" || keys.any { it == highlightKey }

    SettingsCardGroup(highlight = isHighlighted("app_icon")) {
        Column {
            WindowSpinnerPreference(
                title = stringResource(R.string.settings_theme_mode),
                summary = stringResource(R.string.settings_theme_mode_summary),
                items = themeEntries,
                selectedIndex = selectedThemeMode,
                onSelectedIndexChange = { index ->
                    scope.launch { settingsManager.setThemeMode(index) }
                }
            )
            WindowSpinnerPreference(
                title = stringResource(R.string.settings_monet_color),
                summary = stringResource(R.string.settings_monet_color_summary),
                items = monetEntries,
                selectedIndex = selectedMonetMode,
                onSelectedIndexChange = { index ->
                    scope.launch { settingsManager.setMonetColorMode(index) }
                }
            )
            WindowSpinnerPreference(
                title = stringResource(R.string.settings_player_bg_theme),
                summary = stringResource(
                    R.string.settings_current_value,
                    themeLabels[playerBgTheme.coerceIn(themeLabels.indices)]
                ),
                items = themeEntries,
                selectedIndex = playerBgTheme.coerceIn(themeLabels.indices),
                onSelectedIndexChange = { index ->
                    scope.launch { settingsManager.setPlayerBackgroundTheme(index) }
                }
            )
            WindowSpinnerPreference(
                title = stringResource(R.string.settings_language),
                summary = languageSummary,
                items = languageEntries,
                selectedIndex = selectedLanguageIndex,
                onSelectedIndexChange = { index ->
                    languageOptions.getOrNull(index)?.first?.let { language ->
                        scope.launch { settingsManager.setAppLanguage(language) }
                    }
                }
            )
            SettingsIntSliderPreference(
                title = stringResource(R.string.settings_app_font_scale),
                summary = stringResource(R.string.settings_app_font_scale_summary),
                value = appFontScalePercent,
                valueRange = SettingsManager.APP_FONT_SCALE_MIN_PERCENT..
                    SettingsManager.APP_FONT_SCALE_MAX_PERCENT,
                valueText = "$appFontScalePercent%",
                steps = SettingsManager.APP_FONT_SCALE_MAX_PERCENT -
                    SettingsManager.APP_FONT_SCALE_MIN_PERCENT - 1,
                showKeyPoints = false,
                onValueChange = {
                    scope.launch { settingsManager.setAppFontScalePercent(it) }
                }
            )
            SettingsIntSliderPreference(
                title = stringResource(R.string.settings_app_display_scale),
                summary = stringResource(R.string.settings_app_display_scale_summary),
                value = appDisplayScalePercent,
                valueRange = SettingsManager.APP_DISPLAY_SCALE_MIN_PERCENT..
                    SettingsManager.APP_DISPLAY_SCALE_MAX_PERCENT,
                valueText = "$appDisplayScalePercent%",
                steps = SettingsManager.APP_DISPLAY_SCALE_MAX_PERCENT -
                    SettingsManager.APP_DISPLAY_SCALE_MIN_PERCENT - 1,
                showKeyPoints = false,
                onValueChange = {
                    scope.launch { settingsManager.setAppDisplayScalePercent(it) }
                }
            )
            SettingsFocusAnchor(active = highlightKey == "app_icon") {
                WindowSpinnerPreference(
                    title = stringResource(R.string.settings_app_icon),
                    summary = stringResource(
                        R.string.settings_app_icon_summary,
                        appIconOptions[selectedAppIconIndex].second
                    ),
                    items = appIconEntries,
                    selectedIndex = selectedAppIconIndex,
                    onSelectedIndexChange = { index ->
                        appIconOptions.getOrNull(index)?.first?.let { style ->
                            scope.launch { settingsManager.setAppIconStyle(style) }
                        }
                    }
                )
            }
            SwitchPreference(
                title = stringResource(R.string.settings_widget_safe_layout),
                summary = stringResource(R.string.settings_widget_safe_layout_summary),
                checked = widgetSafeLayout,
                onCheckedChange = { enabled ->
                    scope.launch {
                        settingsManager.setWidgetSafeLayout(enabled)
                        PlaybackWidgetUpdater.setSafeLayout(context, enabled)
                    }
                }
            )
            WindowSpinnerPreference(
                title = stringResource(R.string.settings_bottom_bar_glass_effect),
                summary = bottomBarGlassSummary,
                items = bottomBarGlassEntries,
                selectedIndex = selectedBottomBarGlassEffectIndex,
                onSelectedIndexChange = { index ->
                    bottomBarGlassEffects.getOrNull(index)?.let { effect ->
                        scope.launch { settingsManager.setBottomBarGlassEffect(effect) }
                    }
                }
            )
        }
    }

    SettingsCardGroup(highlight = isHighlighted("system_bars")) {
        Column {
            ArrowPreference(
                title = stringResource(R.string.settings_bottom_dock_items),
                summary = stringResource(R.string.settings_bottom_dock_items_summary),
                onClick = onNavigateToBottomNavigationSettings
            )
            SettingsFocusAnchor(active = highlightKey == "system_bars") {
                WindowSpinnerPreference(
                    title = stringResource(R.string.settings_system_bars_mode),
                    summary = stringResource(
                        R.string.settings_system_bars_mode_summary,
                        systemBarsModeLabels[selectedSystemBarsMode]
                    ),
                    items = systemBarsModeEntries,
                    selectedIndex = selectedSystemBarsMode,
                    onSelectedIndexChange = { index ->
                        scope.launch { settingsManager.setSystemBarsMode(index) }
                    }
                )
            }
            SwitchPreference(
                title = stringResource(R.string.settings_system_bars_reserve_space),
                summary = stringResource(R.string.settings_system_bars_reserve_space_summary),
                checked = systemBarsReserveSpace,
                enabled = systemBarsMode != SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH,
                onCheckedChange = {
                    scope.launch { settingsManager.setSystemBarsReserveSpace(it) }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_startup_poster),
                summary = stringResource(
                    R.string.settings_startup_poster_summary,
                    startupPosterDurationMs / 1_000f
                ),
                checked = startupPosterEnabled,
                onCheckedChange = {
                    scope.launch { settingsManager.setStartupPosterEnabled(it) }
                }
            )
            SettingsIntSliderPreference(
                title = stringResource(R.string.settings_startup_poster_duration),
                summary = stringResource(R.string.settings_startup_poster_duration_summary),
                value = startupPosterDurationMs / 100,
                valueRange = 1..30,
                valueText = stringResource(
                    R.string.settings_startup_poster_duration_value,
                    startupPosterDurationMs / 1_000f
                ),
                enabled = startupPosterEnabled && startupPosterUri.isNotBlank(),
                steps = 28,
                onValueChange = { scope.launch { settingsManager.setStartupPosterDurationMs(it * 100) } }
            )
            ArrowPreference(
                title = stringResource(R.string.settings_startup_poster_image),
                summary = if (startupPosterUri.isBlank()) {
                    stringResource(R.string.settings_custom_image_not_selected)
                } else {
                    stringResource(R.string.settings_custom_image_selected)
                },
                onClick = { startupPosterPicker.launch(arrayOf("image/*")) }
            )
            if (startupPosterUri.isNotBlank()) {
                ArrowPreference(
                    title = stringResource(R.string.settings_custom_image_remove),
                    summary = stringResource(R.string.settings_custom_image_remove_summary),
                    onClick = {
                        scope.launch {
                            context.deletePersistedCustomImage(startupPosterUri)
                            settingsManager.setStartupPosterUri("")
                        }
                    }
                )
            }
        }
    }

    SettingsCardGroup(highlight = isHighlighted("wallpaper", "beautiful_lyrics")) {
        Column {
            SettingsFocusAnchor(active = highlightKey == "wallpaper") {
                SwitchPreference(
                    title = stringResource(R.string.settings_app_wallpaper),
                    summary = stringResource(R.string.settings_app_wallpaper_summary),
                    checked = appWallpaperEnabled,
                    onCheckedChange = {
                        scope.launch { settingsManager.setAppWallpaperEnabled(it) }
                    }
                )
            }
            ArrowPreference(
                title = stringResource(R.string.settings_app_wallpaper_image),
                summary = if (appWallpaperUri.isBlank()) {
                    stringResource(R.string.settings_custom_image_not_selected)
                } else {
                    stringResource(R.string.settings_custom_image_selected)
                },
                onClick = { appWallpaperPicker.launch(arrayOf("image/*")) }
            )
            if (appWallpaperUri.isNotBlank()) {
                ArrowPreference(
                    title = stringResource(R.string.settings_custom_image_remove),
                    summary = stringResource(R.string.settings_custom_image_remove_summary),
                    onClick = {
                        scope.launch {
                            context.deletePersistedCustomImage(appWallpaperUri)
                            settingsManager.setAppWallpaperUri("")
                        }
                    }
                )
            }
            SettingsIntSliderPreference(
                title = stringResource(R.string.settings_wallpaper_opacity),
                summary = stringResource(R.string.settings_wallpaper_opacity_summary),
                value = appWallpaperOpacity,
                valueRange = 20..100,
                valueText = "$appWallpaperOpacity%",
                enabled = appWallpaperEnabled,
                onValueChange = { scope.launch { settingsManager.setAppWallpaperOpacity(it) } }
            )
            SettingsIntSliderPreference(
                title = stringResource(R.string.settings_wallpaper_dim),
                summary = stringResource(R.string.settings_wallpaper_dim_summary),
                value = appWallpaperDim,
                valueRange = 0..80,
                valueText = "$appWallpaperDim%",
                enabled = appWallpaperEnabled,
                onValueChange = { scope.launch { settingsManager.setAppWallpaperDim(it) } }
            )
            SettingsIntSliderPreference(
                title = stringResource(R.string.settings_wallpaper_content_overlay),
                summary = stringResource(R.string.settings_wallpaper_content_overlay_summary),
                value = appWallpaperContentOverlay,
                valueRange = 0..80,
                valueText = "$appWallpaperContentOverlay%",
                enabled = appWallpaperEnabled,
                onValueChange = { scope.launch { settingsManager.setAppWallpaperContentOverlay(it) } }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_player_background),
                summary = stringResource(R.string.settings_player_background_summary),
                checked = playerBackgroundEnabled,
                onCheckedChange = {
                    scope.launch { settingsManager.setPlayerBackgroundEnabled(it) }
                }
            )
            ArrowPreference(
                title = stringResource(R.string.settings_player_background_image),
                summary = if (playerBackgroundUri.isBlank()) {
                    stringResource(R.string.settings_custom_image_not_selected)
                } else {
                    stringResource(R.string.settings_custom_image_selected)
                },
                onClick = { playerBackgroundPicker.launch(arrayOf("image/*")) }
            )
            if (playerBackgroundUri.isNotBlank()) {
                ArrowPreference(
                    title = stringResource(R.string.settings_custom_image_remove),
                    summary = stringResource(R.string.settings_custom_image_remove_summary),
                    onClick = {
                        scope.launch {
                            context.deletePersistedCustomImage(playerBackgroundUri)
                            settingsManager.setPlayerBackgroundUri("")
                        }
                    }
                )
            }
            SettingsIntSliderPreference(
                title = stringResource(R.string.settings_player_background_opacity),
                summary = stringResource(R.string.settings_player_background_opacity_summary),
                value = playerBackgroundOpacity,
                valueRange = 20..100,
                valueText = "$playerBackgroundOpacity%",
                enabled = playerBackgroundEnabled,
                onValueChange = { scope.launch { settingsManager.setPlayerBackgroundOpacity(it) } }
            )
            SettingsIntSliderPreference(
                title = stringResource(R.string.settings_player_background_dim),
                summary = stringResource(R.string.settings_player_background_dim_summary),
                value = playerBackgroundDim,
                valueRange = 0..80,
                valueText = "$playerBackgroundDim%",
                enabled = playerBackgroundEnabled,
                onValueChange = { scope.launch { settingsManager.setPlayerBackgroundDim(it) } }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_player_dynamic_flow),
                summary = stringResource(R.string.settings_player_dynamic_flow_summary),
                checked = playerDynamicFlowEnabled,
                enabled = !beautifulLyricsBackground,
                onCheckedChange = {
                    scope.launch { settingsManager.setPlayerDynamicFlowEnabled(it) }
                }
            )
            SettingsFocusAnchor(active = highlightKey == "beautiful_lyrics") {
                WindowSpinnerPreference(
                    title = stringResource(R.string.settings_beautiful_lyrics_background),
                    summary = stringResource(R.string.settings_beautiful_lyrics_background_summary),
                    items = beautifulLyricsBackgroundEntries,
                    selectedIndex = selectedBeautifulLyricsBackground,
                    onSelectedIndexChange = { index ->
                        scope.launch { settingsManager.setPlayerBeautifulLyricsBackground(index == 1) }
                    }
                )
            }
            SettingsIntSliderPreference(
                title = stringResource(R.string.settings_beautiful_lyrics_speed),
                summary = stringResource(R.string.settings_beautiful_lyrics_speed_summary),
                value = beautifulLyricsSpeed,
                valueRange = 5..60,
                valueText = beautifulLyricsSpeed.formatBeautifulLyricsSpeed(),
                enabled = beautifulLyricsBackground,
                onValueChange = { scope.launch { settingsManager.setPlayerBeautifulLyricsSpeed(it) } }
            )
            SettingsIntSliderPreference(
                title = stringResource(R.string.settings_beautiful_lyrics_blur),
                summary = stringResource(R.string.settings_beautiful_lyrics_blur_summary),
                value = beautifulLyricsBlur,
                valueRange = 0..80,
                valueText = "${beautifulLyricsBlur}px",
                enabled = beautifulLyricsBackground,
                onValueChange = { scope.launch { settingsManager.setPlayerBeautifulLyricsBlur(it) } }
            )
            SettingsIntSliderPreference(
                title = stringResource(R.string.settings_beautiful_lyrics_brightness),
                summary = stringResource(R.string.settings_beautiful_lyrics_brightness_summary),
                value = beautifulLyricsBrightness,
                valueRange = 30..120,
                valueText = "$beautifulLyricsBrightness%",
                enabled = beautifulLyricsBackground,
                onValueChange = { scope.launch { settingsManager.setPlayerBeautifulLyricsBrightness(it) } }
            )
        }
    }

    SettingsCardGroup(
        highlight = isHighlighted(
            "auto_show_search_keyboard",
            "dynamic_cover",
            "player_show_total_duration",
            "player_tap_seek",
            "transport_button_outlines"
        )
    ) {
        Column {
            WindowSpinnerPreference(
                title = stringResource(R.string.settings_category_grid_columns),
                summary = stringResource(
                    R.string.settings_category_grid_columns_summary,
                    categoryGridColumns.coerceIn(categoryGridRange.first, categoryGridRange.last)
                ),
                items = categoryGridEntries,
                selectedIndex = (categoryGridColumns - categoryGridRange.first).coerceIn(categoryGridEntries.indices),
                onSelectedIndexChange = { index ->
                    scope.launch { settingsManager.setCategoryGridColumns(categoryGridRange.first + index) }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_open_player_on_play),
                summary = stringResource(R.string.settings_open_player_on_play_summary),
                checked = openPlayerOnPlay,
                onCheckedChange = {
                    scope.launch { settingsManager.setOpenPlayerOnPlay(it) }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_show_play_next_in_lists),
                summary = stringResource(R.string.settings_show_play_next_in_lists_summary),
                checked = showPlayNextInLists,
                onCheckedChange = {
                    scope.launch { settingsManager.setShowPlayNextInLists(it) }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_show_remove_from_playlist_button),
                summary = stringResource(R.string.settings_show_remove_from_playlist_button_summary),
                checked = showRemoveFromPlaylistButton,
                onCheckedChange = {
                    scope.launch { settingsManager.setShowRemoveFromPlaylistButton(it) }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_exclude_search_results_from_playlist),
                summary = stringResource(R.string.settings_exclude_search_results_from_playlist_summary),
                checked = excludeSearchResultsFromPlaylist,
                onCheckedChange = {
                    scope.launch { settingsManager.setExcludeSearchResultsFromPlaylist(it) }
                }
            )
            SettingsFocusAnchor(active = highlightKey == "auto_show_search_keyboard") {
                SwitchPreference(
                    title = stringResource(R.string.settings_auto_show_search_keyboard),
                    summary = stringResource(R.string.settings_auto_show_search_keyboard_summary),
                    checked = autoShowSearchKeyboard,
                    onCheckedChange = {
                        scope.launch { settingsManager.setAutoShowSearchKeyboard(it) }
                    }
                )
            }
            SwitchPreference(
                title = stringResource(R.string.settings_playlist_special_entries),
                summary = stringResource(R.string.settings_playlist_special_entries_summary),
                checked = playlistSpecialEntriesVisible,
                onCheckedChange = {
                    scope.launch { settingsManager.setPlaylistSpecialEntriesVisible(it) }
                }
            )
            SettingsFocusAnchor(active = highlightKey == "dynamic_cover") {
                SwitchPreference(
                    title = stringResource(R.string.settings_dynamic_cover),
                    summary = stringResource(R.string.settings_dynamic_cover_summary),
                    checked = dynamicCoverEnabled,
                    onCheckedChange = {
                        setDynamicCoverEnabled(context, scope, settingsManager, dynamicCoverPermissionLauncher, it)
                    }
                )
            }
            SwitchPreference(
                title = stringResource(R.string.settings_music_video_sync),
                summary = stringResource(R.string.settings_music_video_sync_summary),
                checked = musicVideoSyncEnabled,
                onCheckedChange = {
                    setMusicVideoSyncEnabled(context, scope, settingsManager, musicVideoSyncPermissionLauncher, it)
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_music_video_capture_subtitles),
                summary = stringResource(R.string.settings_music_video_capture_subtitles_summary),
                checked = musicVideoCaptureSubtitles,
                onCheckedChange = {
                    scope.launch { settingsManager.setMusicVideoCaptureSubtitles(it) }
                }
            )
            WindowSpinnerPreference(
                title = stringResource(R.string.settings_music_video_orientation),
                summary = stringResource(
                    R.string.settings_current_value,
                    musicVideoOrientationOptions[selectedMusicVideoOrientation].second
                ),
                items = musicVideoOrientationEntries,
                selectedIndex = selectedMusicVideoOrientation,
                onSelectedIndexChange = { index ->
                    musicVideoOrientationOptions.getOrNull(index)?.first?.let { orientation ->
                        scope.launch { settingsManager.setMusicVideoOrientation(orientation) }
                    }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_show_local_mv_in_lists),
                summary = stringResource(R.string.settings_show_local_mv_in_lists_summary),
                checked = showLocalMusicVideoInLists,
                onCheckedChange = {
                    scope.launch { settingsManager.setShowLocalMusicVideoInLists(it) }
                }
            )
            SwitchPreference(
                title = stringResource(R.string.settings_show_online_mv_in_lists),
                summary = stringResource(R.string.settings_show_online_mv_in_lists_summary),
                checked = showOnlineMusicVideoInLists,
                onCheckedChange = {
                    scope.launch { settingsManager.setShowOnlineMusicVideoInLists(it) }
                }
            )
            ArrowPreference(
                title = stringResource(R.string.settings_music_video_custom_folders),
                summary = if (musicVideoCustomFolders.isBlank()) {
                    stringResource(R.string.settings_music_video_custom_folders_summary)
                } else {
                    stringResource(
                        R.string.settings_music_video_custom_folders_selected,
                        musicVideoCustomFolders.lineSequence().filter { it.isNotBlank() }.count()
                    )
                },
                onClick = { musicVideoFolderPicker.launch(null) }
            )
            if (musicVideoCustomFolders.isNotBlank()) {
                ArrowPreference(
                    title = stringResource(R.string.settings_music_video_custom_folders_remove),
                    summary = stringResource(R.string.settings_music_video_custom_folders_remove_summary),
                    onClick = {
                        scope.launch { settingsManager.setMusicVideoCustomFolders("") }
                    }
                )
            }
            ArrowPreference(
                title = stringResource(R.string.settings_dynamic_cover_custom_folders),
                summary = if (dynamicCoverCustomFolders.isBlank()) {
                    stringResource(R.string.settings_dynamic_cover_custom_folders_summary)
                } else {
                    stringResource(
                        R.string.settings_dynamic_cover_custom_folders_selected,
                        dynamicCoverCustomFolders.lineSequence().filter { it.isNotBlank() }.count()
                    )
                },
                onClick = { dynamicCoverFolderPicker.launch(null) }
            )
            if (dynamicCoverCustomFolders.isNotBlank()) {
                ArrowPreference(
                    title = stringResource(R.string.settings_dynamic_cover_custom_folders_remove),
                    summary = stringResource(R.string.settings_dynamic_cover_custom_folders_remove_summary),
                    onClick = {
                        scope.launch { settingsManager.setDynamicCoverCustomFolders("") }
                    }
                )
            }
        }
    }

    SettingsCardGroup(highlight = highlightKey == "appearance") {
        Column {
            SettingsFocusAnchor(active = highlightKey == "appearance") {
                SwitchPreference(
                    title = stringResource(R.string.settings_hi_res_logo),
                    summary = stringResource(R.string.settings_hi_res_logo_summary),
                    checked = hiResLogoEnabled,
                    onCheckedChange = {
                        scope.launch { settingsManager.setHiResLogoEnabled(it) }
                    }
                )
            }
            ArrowPreference(
                title = stringResource(R.string.settings_hi_res_logo_image),
                summary = if (hiResLogoUri.isBlank()) {
                    stringResource(R.string.settings_hi_res_logo_default)
                } else {
                    stringResource(R.string.settings_custom_image_selected)
                },
                onClick = { hiResLogoPicker.launch(arrayOf("image/*")) }
            )
            if (hiResLogoUri.isNotBlank()) {
                ArrowPreference(
                    title = stringResource(R.string.settings_custom_image_remove),
                    summary = stringResource(R.string.settings_custom_image_remove_summary),
                    onClick = {
                        scope.launch {
                            context.deletePersistedCustomImage(hiResLogoUri)
                            settingsManager.setHiResLogoUri("")
                        }
                    }
                )
            }
        }
    }

    SettingsCardGroup(highlight = isHighlighted("player_immersive", "player_page", "player_landscape")) {
        Column {
            SettingsFocusAnchor(active = highlightKey == "player_immersive") {
                SwitchPreference(
                    title = stringResource(R.string.settings_player_immersive_cover),
                    summary = stringResource(R.string.settings_player_immersive_cover_summary),
                    checked = playerImmersiveCover,
                    onCheckedChange = {
                        scope.launch { settingsManager.setPlayerImmersiveCover(it) }
                    }
                )
            }
            SwitchPreference(
                title = stringResource(R.string.settings_player_cover_content_color),
                summary = stringResource(R.string.settings_player_cover_content_color_summary),
                checked = playerCoverContentColor,
                onCheckedChange = {
                    scope.launch { settingsManager.setPlayerCoverContentColor(it) }
                }
            )
            WindowSpinnerPreference(
                title = stringResource(R.string.settings_player_title_position),
                summary = stringResource(
                    R.string.settings_current_value,
                    playerTitlePositionLabels[selectedPlayerTitlePosition]
                ),
                items = playerTitlePositionEntries,
                selectedIndex = selectedPlayerTitlePosition,
                onSelectedIndexChange = { index ->
                    scope.launch { settingsManager.setPlayerTitlePosition(index) }
                }
            )
            SettingsFocusAnchor(active = highlightKey == "player_page") {
                WindowSpinnerPreference(
                    title = stringResource(R.string.settings_player_page_style),
                    summary = stringResource(
                        R.string.settings_current_value,
                        playerPageStyleOptions[selectedPlayerPageStyle].second
                    ),
                    items = playerPageStyleEntries,
                    selectedIndex = selectedPlayerPageStyle,
                    onSelectedIndexChange = { index ->
                        playerPageStyleOptions.getOrNull(index)?.first?.let { style ->
                            scope.launch { settingsManager.setPlayerPageStyle(style) }
                        }
                    }
                )
            }
            SettingsFocusAnchor(active = highlightKey == "player_landscape") {
                WindowSpinnerPreference(
                    title = stringResource(R.string.settings_player_landscape_style),
                    summary = stringResource(
                        R.string.settings_player_landscape_style_summary,
                        playerLandscapeStyleOptions[selectedPlayerLandscapeStyle].second
                    ),
                    items = playerLandscapeStyleEntries,
                    selectedIndex = selectedPlayerLandscapeStyle,
                    onSelectedIndexChange = { index ->
                        playerLandscapeStyleOptions.getOrNull(index)?.first?.let { style ->
                            scope.launch { settingsManager.setPlayerLandscapeStyle(style) }
                        }
                    }
                )
            }
            SettingsFocusAnchor(active = highlightKey == "transport_button_outlines") {
                SwitchPreference(
                    title = stringResource(R.string.settings_transport_button_outlines),
                    summary = stringResource(R.string.settings_transport_button_outlines_summary),
                    checked = transportButtonOutlines,
                    onCheckedChange = {
                        scope.launch { settingsManager.setTransportButtonOutlines(it) }
                    }
                )
            }
            SettingsFocusAnchor(active = highlightKey == "player_tap_seek") {
                SwitchPreference(
                    title = stringResource(R.string.settings_player_tap_seek),
                    summary = stringResource(R.string.settings_player_tap_seek_summary),
                    checked = playerTapSeekEnabled,
                    onCheckedChange = {
                        scope.launch { settingsManager.setPlayerTapSeekEnabled(it) }
                    }
                )
            }
            SwitchPreference(
                title = stringResource(R.string.settings_player_cover_swipe),
                summary = stringResource(R.string.settings_player_cover_swipe_summary),
                checked = playerCoverSwipeEnabled,
                onCheckedChange = {
                    scope.launch { settingsManager.setPlayerCoverSwipeEnabled(it) }
                }
            )
            SettingsFocusAnchor(active = highlightKey == "player_show_total_duration") {
                SwitchPreference(
                    title = stringResource(R.string.settings_player_show_total_duration),
                    summary = stringResource(R.string.settings_player_show_total_duration_summary),
                    checked = playerShowTotalDuration,
                    onCheckedChange = {
                        scope.launch { settingsManager.setPlayerShowTotalDuration(it) }
                    }
                )
            }
            SwitchPreference(
                title = stringResource(R.string.settings_player_show_song_annotation),
                summary = stringResource(R.string.settings_player_show_song_annotation_summary),
                checked = playerShowSongAnnotation,
                onCheckedChange = {
                    scope.launch { settingsManager.setPlayerShowSongAnnotation(it) }
                }
            )
        }
    }

}

private fun Int.formatBeautifulLyricsSpeed(): String {
    val whole = this / 10
    val decimal = this % 10
    return if (decimal == 0) "${whole}x" else "$whole.${decimal}x"
}

private fun String.parseSettingsColorOrNull(): Color? {
    val hex = trim().removePrefix("#")
    val value = hex.toLongOrNull(16) ?: return null
    return when (hex.length) {
        6 -> Color((0xFF000000 or value).toInt())
        8 -> Color(value.toInt())
        else -> null
    }
}
