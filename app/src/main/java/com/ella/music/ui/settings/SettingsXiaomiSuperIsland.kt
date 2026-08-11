package com.ella.music.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.ella.music.data.SettingsManager
import com.ella.music.data.XiaomiSuperIslandSettings
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.ColorSpace
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference

@Composable
internal fun SettingsXiaomiSuperIslandControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val settings by settingsManager.xiaomiSuperIslandSettings.collectAsState(
        initial = XiaomiSuperIslandSettings()
    )
    val update: (XiaomiSuperIslandSettings) -> Unit = { next ->
        scope.launch { settingsManager.setXiaomiSuperIslandSettings(next) }
    }

    SmallTitle(text = stringResource(R.string.settings_xiaomi_super_island_display_section))

    val contentLabels = listOf(
        stringResource(R.string.settings_live_update_lyric_original),
        stringResource(R.string.settings_live_update_lyric_translation),
        stringResource(R.string.settings_live_update_lyric_pronunciation)
    )
    SuperIslandSpinner(
        title = stringResource(R.string.settings_xiaomi_super_island_lyric_content),
        labels = contentLabels,
        selectedIndex = settings.lyricTextMode,
        onSelected = { update(settings.copy(lyricTextMode = it)) }
    )

    val lyricModeLabels = listOf(
        stringResource(R.string.settings_xiaomi_super_island_mode_standard),
        stringResource(R.string.settings_xiaomi_super_island_mode_full)
    )
    SuperIslandSpinner(
        title = stringResource(R.string.settings_xiaomi_super_island_lyric_mode),
        labels = lyricModeLabels,
        selectedIndex = settings.lyricMode,
        onSelected = { update(settings.copy(lyricMode = it)) }
    )
    if (settings.lyricMode == XiaomiSuperIslandSettings.LYRIC_MODE_FULL) {
        SwitchPreference(
            title = stringResource(R.string.settings_xiaomi_super_island_left_cover),
            summary = stringResource(R.string.settings_xiaomi_super_island_left_cover_summary),
            checked = settings.fullLyricShowLeftCover,
            onCheckedChange = { update(settings.copy(fullLyricShowLeftCover = it)) }
        )
    }
    SwitchPreference(
        title = stringResource(R.string.settings_xiaomi_super_island_scrolling),
        summary = stringResource(R.string.settings_xiaomi_super_island_scrolling_summary),
        checked = settings.scrollingEnabled,
        enabled = settings.lyricMode == XiaomiSuperIslandSettings.LYRIC_MODE_STANDARD,
        onCheckedChange = { update(settings.copy(scrollingEnabled = it)) }
    )
    SettingsIntSliderPreference(
        title = stringResource(R.string.settings_xiaomi_super_island_right_limit),
        summary = stringResource(R.string.settings_xiaomi_super_island_text_limit_summary),
        value = settings.rightTextChars,
        valueRange = 6..14,
        valueText = stringResource(R.string.settings_xiaomi_super_island_chars, settings.rightTextChars),
        steps = 7,
        onValueChange = { update(settings.copy(rightTextChars = it)) }
    )
    if (settings.lyricMode == XiaomiSuperIslandSettings.LYRIC_MODE_FULL) {
        val leftValue = if (settings.fullLyricShowLeftCover) {
            settings.leftWithCoverTextChars
        } else {
            settings.leftWithoutCoverTextChars
        }
        val leftRange = if (settings.fullLyricShowLeftCover) 4..10 else 6..14
        SettingsIntSliderPreference(
            title = stringResource(R.string.settings_xiaomi_super_island_left_limit),
            summary = stringResource(R.string.settings_xiaomi_super_island_text_limit_summary),
            value = leftValue,
            valueRange = leftRange,
            valueText = stringResource(R.string.settings_xiaomi_super_island_chars, leftValue),
            steps = (leftRange.last - leftRange.first - 1).coerceAtLeast(0),
            onValueChange = { value ->
                update(
                    if (settings.fullLyricShowLeftCover) {
                        settings.copy(leftWithCoverTextChars = value)
                    } else {
                        settings.copy(leftWithoutCoverTextChars = value)
                    }
                )
            }
        )
    }

    SwitchPreference(
        title = stringResource(R.string.settings_xiaomi_super_island_colorize),
        summary = stringResource(R.string.settings_xiaomi_super_island_colorize_summary),
        checked = settings.textColorEnabled,
        onCheckedChange = {
            update(settings.copy(textColorEnabled = it, progressColorEnabled = it))
        }
    )
    if (settings.textColorEnabled) {
        val colorSourceLabels = listOf(
            stringResource(R.string.settings_xiaomi_super_island_color_album),
            stringResource(R.string.common_custom)
        )
        SuperIslandSpinner(
            title = stringResource(R.string.settings_xiaomi_super_island_color_source),
            labels = colorSourceLabels,
            selectedIndex = settings.colorSource,
            onSelected = { update(settings.copy(colorSource = it)) }
        )
        if (settings.colorSource == XiaomiSuperIslandSettings.COLOR_SOURCE_CUSTOM) {
            var pickerColor by remember(settings.customColor) {
                mutableStateOf(Color(settings.customColor))
            }
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(text = stringResource(R.string.settings_xiaomi_super_island_custom_color))
                Spacer(modifier = Modifier.height(8.dp))
                ColorPicker(
                    color = pickerColor,
                    onColorChanged = { pickerColor = it },
                    colorSpace = ColorSpace.HSV,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { update(settings.copy(customColor = pickerColor.toArgb())) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.common_save))
                }
            }
        }
    }

    SmallTitle(text = stringResource(R.string.settings_xiaomi_super_island_notification_section))
    SwitchPreference(
        title = stringResource(R.string.settings_xiaomi_super_island_progress_color),
        checked = settings.progressColorEnabled,
        onCheckedChange = { update(settings.copy(progressColorEnabled = it)) }
    )
    val actionLabels = listOf(
        stringResource(R.string.settings_xiaomi_super_island_actions_off),
        stringResource(R.string.settings_xiaomi_super_island_actions_media)
    )
    SuperIslandSpinner(
        title = stringResource(R.string.settings_xiaomi_super_island_actions),
        labels = actionLabels,
        selectedIndex = settings.actionStyle,
        onSelected = { update(settings.copy(actionStyle = it)) }
    )
    if (settings.actionStyle == XiaomiSuperIslandSettings.ACTION_STYLE_MEDIA_CONTROLS) {
        val notificationStyleLabels = listOf(
            stringResource(R.string.settings_xiaomi_super_island_style_standard),
            stringResource(R.string.settings_xiaomi_super_island_style_advanced)
        )
        SuperIslandSpinner(
            title = stringResource(R.string.settings_xiaomi_super_island_notification_style),
            labels = notificationStyleLabels,
            selectedIndex = settings.notificationStyle,
            onSelected = { update(settings.copy(notificationStyle = it)) }
        )
        if (settings.notificationStyle == XiaomiSuperIslandSettings.NOTIFICATION_STYLE_STANDARD) {
            val mediaLayoutLabels = listOf(
                stringResource(R.string.settings_xiaomi_super_island_buttons_two),
                stringResource(R.string.settings_xiaomi_super_island_buttons_three)
            )
            SuperIslandSpinner(
                title = stringResource(R.string.settings_xiaomi_super_island_button_layout),
                labels = mediaLayoutLabels,
                selectedIndex = settings.mediaButtonLayout,
                onSelected = { update(settings.copy(mediaButtonLayout = it)) }
            )
        }
    }
    SmallTitle(text = stringResource(R.string.settings_xiaomi_super_island_compat_section))
    val xmsfLabels = listOf(
        stringResource(R.string.settings_xiaomi_super_island_xmsf_disabled),
        stringResource(R.string.settings_xiaomi_super_island_xmsf_standard),
        stringResource(R.string.settings_xiaomi_super_island_xmsf_custom),
        stringResource(R.string.settings_xiaomi_super_island_xmsf_aggressive)
    )
    val xmsfSummaries = listOf(
        stringResource(R.string.settings_xiaomi_super_island_xmsf_disabled_summary),
        stringResource(R.string.settings_xiaomi_super_island_xmsf_standard_summary),
        stringResource(R.string.settings_xiaomi_super_island_xmsf_custom_summary),
        stringResource(R.string.settings_xiaomi_super_island_xmsf_aggressive_summary)
    )
    val xmsfEntries = remember(xmsfLabels, xmsfSummaries) {
        xmsfLabels.mapIndexed { index, label ->
            DropdownItem(title = label, summary = xmsfSummaries[index])
        }
    }
    val selectedXmsfMode = settings.xmsfBypassMode.coerceIn(0, xmsfLabels.lastIndex)
    WindowSpinnerPreference(
        title = stringResource(R.string.settings_xiaomi_super_island_xmsf_mode),
        summary = xmsfSummaries[selectedXmsfMode],
        items = xmsfEntries,
        selectedIndex = selectedXmsfMode,
        onSelectedIndexChange = { update(settings.copy(xmsfBypassMode = it)) }
    )
    if (settings.xmsfBypassMode == XiaomiSuperIslandSettings.XMSF_MODE_CUSTOM) {
        val durationStep = (settings.xmsfCustomDurationMs / 50).coerceIn(2, 10)
        SettingsIntSliderPreference(
            title = stringResource(R.string.settings_xiaomi_super_island_xmsf_duration),
            summary = stringResource(R.string.settings_xiaomi_super_island_xmsf_duration_summary),
            value = durationStep,
            valueRange = 2..10,
            valueText = "${durationStep * 50} ms",
            steps = 7,
            onValueChange = { update(settings.copy(xmsfCustomDurationMs = it * 50)) }
        )
    }
    val dismissValues = listOf(0, 1_000, 3_000, 5_000)
    val dismissLabels = listOf(
        stringResource(R.string.settings_xiaomi_super_island_dismiss_immediate),
        stringResource(R.string.settings_xiaomi_super_island_dismiss_one),
        stringResource(R.string.settings_xiaomi_super_island_dismiss_three),
        stringResource(R.string.settings_xiaomi_super_island_dismiss_five)
    )
    SuperIslandSpinner(
        title = stringResource(R.string.settings_xiaomi_super_island_dismiss_delay),
        labels = dismissLabels,
        selectedIndex = dismissValues.indexOf(settings.dismissDelayMs).coerceAtLeast(0),
        onSelected = { index -> update(settings.copy(dismissDelayMs = dismissValues[index])) }
    )
}

@Composable
private fun SuperIslandSpinner(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    val safeIndex = selectedIndex.coerceIn(0, labels.lastIndex)
    val entries = remember(labels) { labels.map { DropdownItem(title = it) } }
    WindowSpinnerPreference(
        title = title,
        summary = stringResource(R.string.settings_current_value, labels[safeIndex]),
        items = entries,
        selectedIndex = safeIndex,
        onSelectedIndexChange = onSelected
    )
}
