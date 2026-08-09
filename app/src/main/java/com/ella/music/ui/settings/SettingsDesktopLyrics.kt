package com.ella.music.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.DesktopLyricSettings
import com.ella.music.data.SettingsManager
import com.ella.music.player.DesktopLyricService
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.ColorSpace
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference

@Composable
internal fun SettingsDesktopLyricControls(
    playerViewModel: PlayerViewModel?,
    highlightKey: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val desktopLyricEnabled by settingsManager.desktopLyricEnabled.collectSettingsState(initialValue = false)
    val desktopLyricHideWhenPaused by settingsManager.desktopLyricHideWhenPaused.collectSettingsState(initialValue = false)
    val desktopLyricHideInLandscape by settingsManager.desktopLyricHideInLandscape.collectSettingsState(initialValue = false)
    val desktopLyricHideOnPlayerPage by settingsManager.desktopLyricHideOnPlayerPage.collectSettingsState(initialValue = false)
    val desktopLyricHideOnLyricsPage by settingsManager.desktopLyricHideOnLyricsPage.collectSettingsState(initialValue = false)
    val desktopLyricStatusBarMode by settingsManager.desktopLyricStatusBarMode.collectSettingsState(initialValue = false)
    val desktopLyricStatusBarHideWhenPaused by settingsManager.desktopLyricStatusBarHideWhenPaused.collectSettingsState(initialValue = false)
    val desktopLyricStatusBarHideInLandscape by settingsManager.desktopLyricStatusBarHideInLandscape.collectSettingsState(initialValue = false)
    val desktopLyricWidth by settingsManager.desktopLyricWidth.collectSettingsState(initialValue = 72)
    val desktopLyricStatusBarTopOffset by settingsManager.desktopLyricStatusBarTopOffset.collectSettingsState(initialValue = 16)
    val desktopLyricStatusBarPosition by settingsManager.desktopLyricStatusBarPosition.collectSettingsState(initialValue = SettingsManager.DESKTOP_LYRIC_STATUS_POSITION_CENTER)
    val desktopLyricStatusBarWidth by settingsManager.desktopLyricStatusBarWidth.collectSettingsState(initialValue = 72)
    val desktopLyricStatusBarXOffset by settingsManager.desktopLyricStatusBarXOffset.collectSettingsState(initialValue = 0)
    val desktopLyricStatusBarTextAlign by settingsManager.desktopLyricStatusBarTextAlign.collectSettingsState(initialValue = SettingsManager.DESKTOP_LYRIC_STATUS_ALIGN_LEFT)
    val desktopLyricStatusBarVerticalAlign by settingsManager.desktopLyricStatusBarVerticalAlign.collectSettingsState(initialValue = SettingsManager.DESKTOP_LYRIC_STATUS_VERTICAL_TOP)
    val desktopLyricStatusBarSecondary by settingsManager.desktopLyricStatusBarSecondary.collectSettingsState(initialValue = SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_OFF)
    val desktopLyricStatusBarSecondaryOpacity by settingsManager.desktopLyricStatusBarSecondaryOpacity.collectSettingsState(initialValue = 67)
    val desktopLyricStatusBarMergeSecondary by settingsManager.desktopLyricStatusBarMergeSecondary.collectSettingsState(initialValue = false)
    val desktopLyricStatusBarFontScale by settingsManager.desktopLyricStatusBarFontScale.collectSettingsState(initialValue = 100)
    val desktopLyricStatusBarTranslationScale by settingsManager.desktopLyricStatusBarTranslationScale.collectSettingsState(initialValue = 90)
    val desktopLyricStatusBarOpacity by settingsManager.desktopLyricStatusBarOpacity.collectSettingsState(initialValue = 100)
    val desktopLyricLocked by settingsManager.desktopLyricLocked.collectSettingsState(initialValue = false)
    val desktopLyricFontScale by settingsManager.desktopLyricFontScale.collectSettingsState(initialValue = 100)
    val desktopLyricTranslationScale by settingsManager.desktopLyricTranslationScale.collectSettingsState(initialValue = 110)
    val desktopLyricOpacity by settingsManager.desktopLyricOpacity.collectSettingsState(initialValue = 100)
    val desktopLyricTextColor by settingsManager.desktopLyricTextColor.collectSettingsState(initialValue = -1)
    val activeLyricFontScale = if (desktopLyricStatusBarMode) desktopLyricStatusBarFontScale else desktopLyricFontScale
    val activeLyricTranslationScale = if (desktopLyricStatusBarMode) desktopLyricStatusBarTranslationScale else desktopLyricTranslationScale
    val activeLyricOpacity = if (desktopLyricStatusBarMode) desktopLyricStatusBarOpacity else desktopLyricOpacity
    // Status-bar lyrics share the desktop lyric primary color. The status-bar-specific key is
    // retained only for backup compatibility with older installations.
    val activeLyricTextColor = desktopLyricTextColor
    val desktopLyricColorPresets = listOf(
        stringResource(R.string.settings_color_white) to android.graphics.Color.WHITE,
        stringResource(R.string.settings_color_silver_gray) to android.graphics.Color.rgb(191, 191, 191),
        stringResource(R.string.settings_color_light_blue) to android.graphics.Color.rgb(145, 205, 255),
        stringResource(R.string.settings_color_sky_blue) to android.graphics.Color.rgb(3, 169, 244),
        stringResource(R.string.settings_color_soft_pink) to android.graphics.Color.rgb(255, 188, 214),
        stringResource(R.string.settings_color_mint_green) to android.graphics.Color.rgb(166, 235, 203),
        stringResource(R.string.settings_color_neon_green) to android.graphics.Color.rgb(26, 201, 125),
        stringResource(R.string.settings_color_light_purple) to android.graphics.Color.rgb(179, 136, 255),
        stringResource(R.string.settings_color_soft_red) to android.graphics.Color.rgb(255, 112, 112),
        stringResource(R.string.settings_color_warm_yellow) to android.graphics.Color.rgb(255, 224, 150),
        stringResource(R.string.settings_color_orange) to android.graphics.Color.rgb(255, 87, 34)
    )
    val desktopLyricColorEntries = remember(desktopLyricColorPresets) {
        desktopLyricColorPresets.map { DropdownItem(title = it.first) }
    }
    val selectedDesktopLyricColorIndex =
        desktopLyricColorPresets.indexOfFirst { it.second == activeLyricTextColor }.takeIf { it >= 0 } ?: 0
    var showColorPickerSheet by remember { mutableStateOf(false) }
    val statusLyricPositionLeft = stringResource(R.string.settings_status_position_left)
    val statusLyricPositionCenter = stringResource(R.string.settings_status_position_center)
    val statusLyricPositionRight = stringResource(R.string.settings_status_position_right)
    val statusLyricPositionLabels = remember(
        statusLyricPositionLeft,
        statusLyricPositionCenter,
        statusLyricPositionRight
    ) {
        listOf(statusLyricPositionLeft, statusLyricPositionCenter, statusLyricPositionRight)
    }
    val statusLyricPositionEntries = remember(statusLyricPositionLabels) {
        statusLyricPositionLabels.map { DropdownItem(title = it) }
    }
    val statusLyricTextAlignLabels = listOf(
        stringResource(R.string.settings_status_align_left),
        stringResource(R.string.settings_status_align_center),
        stringResource(R.string.settings_status_align_right)
    )
    val statusLyricTextAlignEntries = remember(statusLyricTextAlignLabels) {
        statusLyricTextAlignLabels.map { DropdownItem(title = it) }
    }
    val statusLyricVerticalAlignLabels = listOf(
        stringResource(R.string.settings_status_vertical_top),
        stringResource(R.string.settings_status_vertical_center),
        stringResource(R.string.settings_status_vertical_bottom)
    )
    val statusLyricVerticalAlignEntries = remember(statusLyricVerticalAlignLabels) {
        statusLyricVerticalAlignLabels.map { DropdownItem(title = it) }
    }
    val statusLyricSecondaryLabels = listOf(
        stringResource(R.string.settings_status_secondary_off),
        stringResource(R.string.settings_status_secondary_translation),
        stringResource(R.string.settings_status_secondary_pronunciation)
    )
    val statusLyricSecondaryEntries = remember(statusLyricSecondaryLabels) {
        statusLyricSecondaryLabels.map { DropdownItem(title = it) }
    }

    fun applyDesktopLyricSettings() {
        context.startService(
            Intent(context, DesktopLyricService::class.java)
                .setAction(DesktopLyricService.ACTION_APPLY_SETTINGS)
        )
        playerViewModel?.applyDesktopLyricSettings()
    }

    SettingsFocusAnchor(active = highlightKey == "desktop_lyric") {
        SwitchPreference(
            title = stringResource(R.string.settings_enable_desktop_lyric),
            summary = stringResource(R.string.settings_enable_desktop_lyric_summary),
            checked = desktopLyricEnabled,
            onCheckedChange = { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                    Toast.makeText(context, context.getString(R.string.desktop_lyric_overlay_permission_required), Toast.LENGTH_SHORT).show()
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                } else {
                    playerViewModel?.setDesktopLyricEnabled(enabled)
                        ?: scope.launch { settingsManager.setDesktopLyricEnabled(enabled) }
                }
            }
        )
    }

    SwitchPreference(
        title = stringResource(R.string.desktop_lyric_status_bar_mode),
        summary = stringResource(R.string.desktop_lyric_status_bar_mode_summary),
        enabled = desktopLyricEnabled,
        checked = desktopLyricStatusBarMode,
        onCheckedChange = { enabled ->
            scope.launch {
                settingsManager.setDesktopLyricStatusBarMode(enabled)
                if (enabled) settingsManager.resetDesktopLyricPosition()
                applyDesktopLyricSettings()
            }
        }
    )

    SwitchPreference(
        title = stringResource(R.string.settings_floating_lyric_hide_when_paused),
        summary = stringResource(R.string.settings_floating_lyric_hide_when_paused_summary),
        enabled = desktopLyricEnabled && !desktopLyricStatusBarMode,
        checked = desktopLyricHideWhenPaused,
        onCheckedChange = { enabled ->
            playerViewModel?.setDesktopLyricHideWhenPaused(enabled)
                ?: scope.launch { settingsManager.setDesktopLyricHideWhenPaused(enabled) }
        }
    )

    SwitchPreference(
        title = stringResource(R.string.settings_desktop_lyric_hide_in_landscape),
        summary = stringResource(R.string.settings_desktop_lyric_hide_in_landscape_summary),
        enabled = desktopLyricEnabled && !desktopLyricStatusBarMode,
        checked = desktopLyricHideInLandscape,
        onCheckedChange = { enabled ->
            scope.launch {
                settingsManager.setDesktopLyricHideInLandscape(enabled)
                applyDesktopLyricSettings()
            }
        }
    )

    SwitchPreference(
        title = stringResource(R.string.settings_desktop_lyric_hide_on_player_page),
        summary = stringResource(R.string.settings_desktop_lyric_hide_on_player_page_summary),
        enabled = desktopLyricEnabled,
        checked = desktopLyricHideOnPlayerPage,
        onCheckedChange = { enabled ->
            scope.launch {
                settingsManager.setDesktopLyricHideOnPlayerPage(enabled)
                applyDesktopLyricSettings()
            }
        }
    )

    SwitchPreference(
        title = stringResource(R.string.settings_desktop_lyric_hide_on_lyrics_page),
        summary = stringResource(R.string.settings_desktop_lyric_hide_on_lyrics_page_summary),
        enabled = desktopLyricEnabled,
        checked = desktopLyricHideOnLyricsPage,
        onCheckedChange = { enabled ->
            scope.launch {
                settingsManager.setDesktopLyricHideOnLyricsPage(enabled)
                applyDesktopLyricSettings()
            }
        }
    )

    SwitchPreference(
        title = stringResource(R.string.settings_status_lyric_hide_when_paused),
        summary = stringResource(R.string.settings_status_lyric_hide_when_paused_summary),
        enabled = desktopLyricEnabled && desktopLyricStatusBarMode,
        checked = desktopLyricStatusBarHideWhenPaused,
        onCheckedChange = { enabled ->
            scope.launch {
                settingsManager.setDesktopLyricStatusBarHideWhenPaused(enabled)
                applyDesktopLyricSettings()
            }
        }
    )

    SwitchPreference(
        title = stringResource(R.string.settings_status_lyric_hide_in_landscape),
        summary = stringResource(R.string.settings_status_lyric_hide_in_landscape_summary),
        enabled = desktopLyricEnabled && desktopLyricStatusBarMode,
        checked = desktopLyricStatusBarHideInLandscape,
        onCheckedChange = { enabled ->
            scope.launch {
                settingsManager.setDesktopLyricStatusBarHideInLandscape(enabled)
                applyDesktopLyricSettings()
            }
        }
    )

    SettingsIntSliderPreference(
        title = stringResource(R.string.settings_status_lyric_top_offset_value, desktopLyricStatusBarTopOffset),
        summary = stringResource(R.string.settings_status_lyric_top_offset_summary),
        value = desktopLyricStatusBarTopOffset,
        valueRange = 0..120,
        valueText = "${desktopLyricStatusBarTopOffset.coerceIn(0, 120)}dp",
        enabled = desktopLyricEnabled && desktopLyricStatusBarMode,
        onValueChange = { offset ->
                scope.launch {
                    settingsManager.setDesktopLyricStatusBarTopOffset(offset)
                    applyDesktopLyricSettings()
                }
        }
    )

    SettingsIntSliderPreference(
        title = stringResource(R.string.settings_desktop_lyric_width_value, desktopLyricWidth),
        summary = stringResource(R.string.settings_desktop_lyric_width_summary),
        value = desktopLyricWidth,
        valueRange = DesktopLyricSettings.MIN_WIDTH_PERCENT..DesktopLyricSettings.MAX_WIDTH_PERCENT,
        valueText = "${desktopLyricWidth.coerceIn(DesktopLyricSettings.MIN_WIDTH_PERCENT, DesktopLyricSettings.MAX_WIDTH_PERCENT)}%",
        enabled = desktopLyricEnabled && !desktopLyricStatusBarMode,
        onValueChange = { width ->
            scope.launch {
                settingsManager.setDesktopLyricWidth(width)
                applyDesktopLyricSettings()
            }
        }
    )

    WindowSpinnerPreference(
        title = stringResource(R.string.settings_status_bar_lyric_position),
        summary = stringResource(
            R.string.settings_current_value,
            statusLyricPositionLabels[desktopLyricStatusBarPosition.coerceIn(0, 2)]
        ),
        enabled = desktopLyricEnabled && desktopLyricStatusBarMode,
        items = statusLyricPositionEntries,
        selectedIndex = desktopLyricStatusBarPosition.coerceIn(0, 2),
        onSelectedIndexChange = { index ->
            scope.launch {
                settingsManager.setDesktopLyricStatusBarPosition(index)
                applyDesktopLyricSettings()
            }
        }
    )

    SettingsIntSliderPreference(
        title = stringResource(R.string.settings_status_lyric_width_value, desktopLyricStatusBarWidth),
        summary = stringResource(R.string.settings_status_lyric_width_summary),
        value = desktopLyricStatusBarWidth,
        valueRange = DesktopLyricSettings.MIN_WIDTH_PERCENT..DesktopLyricSettings.MAX_WIDTH_PERCENT,
        valueText = "${desktopLyricStatusBarWidth.coerceIn(DesktopLyricSettings.MIN_WIDTH_PERCENT, DesktopLyricSettings.MAX_WIDTH_PERCENT)}%",
        enabled = desktopLyricEnabled && desktopLyricStatusBarMode,
        onValueChange = { width ->
            scope.launch {
                settingsManager.setDesktopLyricStatusBarWidth(width)
                applyDesktopLyricSettings()
            }
        }
    )

    SettingsIntSliderPreference(
        title = stringResource(R.string.settings_status_lyric_x_offset_value, desktopLyricStatusBarXOffset),
        summary = stringResource(R.string.settings_status_lyric_x_offset_summary),
        value = desktopLyricStatusBarXOffset,
        valueRange = -640..640,
        valueText = "${desktopLyricStatusBarXOffset.coerceIn(-640, 640)}dp",
        enabled = desktopLyricEnabled && desktopLyricStatusBarMode,
        onValueChange = { offset ->
            scope.launch {
                settingsManager.setDesktopLyricStatusBarXOffset(offset)
                applyDesktopLyricSettings()
            }
        }
    )

    WindowSpinnerPreference(
        title = stringResource(R.string.settings_status_bar_lyric_text_align),
        summary = stringResource(
            R.string.settings_current_value,
            statusLyricTextAlignLabels[desktopLyricStatusBarTextAlign.coerceIn(0, 2)]
        ),
        enabled = desktopLyricEnabled && desktopLyricStatusBarMode,
        items = statusLyricTextAlignEntries,
        selectedIndex = desktopLyricStatusBarTextAlign.coerceIn(0, 2),
        onSelectedIndexChange = { index ->
            scope.launch {
                settingsManager.setDesktopLyricStatusBarTextAlign(index)
                applyDesktopLyricSettings()
            }
        }
    )

    WindowSpinnerPreference(
        title = stringResource(R.string.settings_status_bar_lyric_vertical_align),
        summary = stringResource(
            R.string.settings_current_value,
            statusLyricVerticalAlignLabels[desktopLyricStatusBarVerticalAlign.coerceIn(0, 2)]
        ),
        enabled = desktopLyricEnabled && desktopLyricStatusBarMode,
        items = statusLyricVerticalAlignEntries,
        selectedIndex = desktopLyricStatusBarVerticalAlign.coerceIn(0, 2),
        onSelectedIndexChange = { index ->
            scope.launch {
                settingsManager.setDesktopLyricStatusBarVerticalAlign(index)
                applyDesktopLyricSettings()
            }
        }
    )

    WindowSpinnerPreference(
        title = stringResource(R.string.settings_status_bar_lyric_secondary),
        summary = stringResource(
            R.string.settings_current_value,
            statusLyricSecondaryLabels[desktopLyricStatusBarSecondary.coerceIn(0, 2)]
        ),
        enabled = desktopLyricEnabled && desktopLyricStatusBarMode,
        items = statusLyricSecondaryEntries,
        selectedIndex = desktopLyricStatusBarSecondary.coerceIn(0, 2),
        onSelectedIndexChange = { index ->
            scope.launch {
                settingsManager.setDesktopLyricStatusBarSecondary(index)
                applyDesktopLyricSettings()
            }
        }
    )

    SettingsIntSliderPreference(
        title = stringResource(R.string.settings_status_lyric_secondary_opacity_value, desktopLyricStatusBarSecondaryOpacity),
        summary = stringResource(R.string.settings_status_lyric_secondary_opacity_summary),
        value = desktopLyricStatusBarSecondaryOpacity,
        valueRange = 20..100,
        valueText = "${desktopLyricStatusBarSecondaryOpacity.coerceIn(20, 100)}%",
        enabled = desktopLyricEnabled && desktopLyricStatusBarMode && desktopLyricStatusBarSecondary != SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_OFF,
        onValueChange = { opacity ->
            scope.launch {
                settingsManager.setDesktopLyricStatusBarSecondaryOpacity(opacity)
                applyDesktopLyricSettings()
            }
        }
    )

    SwitchPreference(
        title = stringResource(R.string.settings_status_lyric_merge_secondary),
        summary = stringResource(R.string.settings_status_lyric_merge_secondary_summary),
        enabled = desktopLyricEnabled && desktopLyricStatusBarMode && desktopLyricStatusBarSecondary != SettingsManager.DESKTOP_LYRIC_STATUS_SECONDARY_OFF,
        checked = desktopLyricStatusBarMergeSecondary,
        onCheckedChange = { enabled ->
            scope.launch {
                settingsManager.setDesktopLyricStatusBarMergeSecondary(enabled)
                applyDesktopLyricSettings()
            }
        }
    )

    SwitchPreference(
        title = stringResource(R.string.settings_lock_desktop_lyric),
        summary = stringResource(R.string.settings_lock_desktop_lyric_summary),
        enabled = desktopLyricEnabled,
        checked = desktopLyricLocked,
        onCheckedChange = { enabled ->
            scope.launch {
                settingsManager.setDesktopLyricLocked(enabled)
                applyDesktopLyricSettings()
            }
        }
    )

    ArrowPreference(
        title = stringResource(R.string.desktop_lyric_reset_position),
        summary = stringResource(R.string.desktop_lyric_reset_position_summary),
        enabled = desktopLyricEnabled,
        onClick = {
            scope.launch {
                settingsManager.resetDesktopLyricPosition()
                withContext(Dispatchers.Main) {
                    context.startService(
                        Intent(context, DesktopLyricService::class.java)
                            .setAction(DesktopLyricService.ACTION_RESET_POSITION)
                    )
                    Toast.makeText(context, context.getString(R.string.desktop_lyric_reset_position_done), Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    SettingsIntSliderPreference(
        title = stringResource(
            if (desktopLyricStatusBarMode) R.string.settings_status_lyric_font_scale else R.string.settings_desktop_lyric_font_scale,
            activeLyricFontScale
        ),
        summary = stringResource(
            if (desktopLyricStatusBarMode) R.string.settings_status_lyric_font_scale_summary else R.string.settings_desktop_lyric_font_scale_summary
        ),
        value = activeLyricFontScale,
        valueRange = 80..220,
        valueText = "${activeLyricFontScale.coerceIn(80, 220)}%",
        enabled = desktopLyricEnabled,
        onValueChange = { scale ->
            scope.launch {
                if (desktopLyricStatusBarMode) {
                    settingsManager.setDesktopLyricStatusBarFontScale(scale)
                } else {
                    settingsManager.setDesktopLyricFontScale(scale)
                }
                applyDesktopLyricSettings()
            }
        }
    )

    SettingsIntSliderPreference(
        title = stringResource(
            if (desktopLyricStatusBarMode) R.string.settings_status_lyric_translation_scale else R.string.settings_desktop_lyric_translation_scale,
            activeLyricTranslationScale
        ),
        summary = stringResource(
            if (desktopLyricStatusBarMode) R.string.settings_status_lyric_translation_scale_summary else R.string.settings_desktop_lyric_translation_scale_summary
        ),
        value = activeLyricTranslationScale,
        valueRange = 80..220,
        valueText = "${activeLyricTranslationScale.coerceIn(80, 220)}%",
        enabled = desktopLyricEnabled,
        onValueChange = { scale ->
            scope.launch {
                if (desktopLyricStatusBarMode) {
                    settingsManager.setDesktopLyricStatusBarTranslationScale(scale)
                } else {
                    settingsManager.setDesktopLyricTranslationScale(scale)
                }
                applyDesktopLyricSettings()
            }
        }
    )

    SettingsIntSliderPreference(
        title = stringResource(
            if (desktopLyricStatusBarMode) R.string.settings_status_lyric_opacity else R.string.settings_desktop_lyric_opacity,
            activeLyricOpacity
        ),
        summary = stringResource(
            if (desktopLyricStatusBarMode) R.string.settings_status_lyric_opacity_summary else R.string.settings_desktop_lyric_opacity_summary
        ),
        value = activeLyricOpacity,
        valueRange = 35..100,
        valueText = "${activeLyricOpacity.coerceIn(35, 100)}%",
        enabled = desktopLyricEnabled,
        onValueChange = { opacity ->
            scope.launch {
                if (desktopLyricStatusBarMode) {
                    settingsManager.setDesktopLyricStatusBarOpacity(opacity)
                } else {
                    settingsManager.setDesktopLyricOpacity(opacity)
                }
                applyDesktopLyricSettings()
            }
        }
    )

    WindowSpinnerPreference(
        title = stringResource(R.string.settings_desktop_lyric_color),
        summary = stringResource(
            R.string.settings_current_value,
            desktopLyricColorPresets[selectedDesktopLyricColorIndex].first
        ),
        enabled = desktopLyricEnabled,
        items = desktopLyricColorEntries,
        selectedIndex = selectedDesktopLyricColorIndex,
        onSelectedIndexChange = { index ->
            val color = desktopLyricColorPresets.getOrNull(index)?.second ?: android.graphics.Color.WHITE
            scope.launch {
                settingsManager.setDesktopLyricTextColor(color)
                applyDesktopLyricSettings()
            }
        }
    )

    ArrowPreference(
        title = stringResource(R.string.common_custom),
        summary = String.format("#%06X", 0xFFFFFF and activeLyricTextColor),
        enabled = desktopLyricEnabled,
        onClick = { showColorPickerSheet = true }
    )

    EllaMiuixBottomSheet(
        show = showColorPickerSheet,
        title = stringResource(R.string.settings_desktop_lyric_color),
        onDismissRequest = { showColorPickerSheet = false }
    ) {
        var pickerColor by remember(showColorPickerSheet, activeLyricTextColor) {
            mutableStateOf(Color(activeLyricTextColor))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            ColorPicker(
                color = pickerColor,
                onColorChanged = { pickerColor = it },
                colorSpace = ColorSpace.HSV,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    showColorPickerSheet = false
                    scope.launch {
                        settingsManager.setDesktopLyricTextColor(pickerColor.toArgb())
                        applyDesktopLyricSettings()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.common_confirm))
            }
        }
    }
}
