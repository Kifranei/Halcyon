package com.ella.music.ui.settings

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference

@Composable
internal fun SettingsLiveUpdateLyricControls(
    playerViewModel: PlayerViewModel?,
    highlightKey: String? = null,
    onOpenXiaomiSuperIslandSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val enabled by settingsManager.liveUpdateLyricEnabled.collectAsState(initial = false)
    val mode by settingsManager.liveUpdateLyricMode.collectAsState(
        initial = SettingsManager.LIVE_UPDATE_LYRIC_MODE_ORIGINAL
    )
    val displayMode by settingsManager.liveUpdateLyricDisplayMode.collectAsState(
        initial = SettingsManager.LIVE_UPDATE_LYRIC_DISPLAY_MODE_COMPACT
    )
    val secondaryMode by settingsManager.liveUpdateLyricSecondaryMode.collectAsState(
        initial = SettingsManager.LIVE_UPDATE_LYRIC_SECONDARY_MODE_SONG
    )
    val labels = listOf(
        stringResource(R.string.settings_live_update_lyric_original),
        stringResource(R.string.settings_live_update_lyric_translation),
        stringResource(R.string.settings_live_update_lyric_pronunciation)
    )
    val entries = remember(labels) { labels.map { DropdownItem(title = it) } }
    val selectedMode = mode.coerceIn(0, labels.lastIndex)

    SettingsFocusAnchor(active = highlightKey == "live_update_lyric") {
        SwitchPreference(
            title = stringResource(R.string.settings_enable_live_update_lyric),
            summary = stringResource(R.string.settings_enable_live_update_lyric_summary),
            checked = enabled,
            onCheckedChange = { nextEnabled ->
                playerViewModel?.setLiveUpdateLyricEnabled(nextEnabled)
                    ?: scope.launch { settingsManager.setLiveUpdateLyricEnabled(nextEnabled) }
            }
        )
    }

    SettingsFocusAnchor(active = highlightKey == "live_update_lyric_content") {
        WindowSpinnerPreference(
            title = stringResource(R.string.settings_live_update_lyric_content),
            summary = stringResource(R.string.settings_current_value, labels[selectedMode]),
            enabled = enabled,
            items = entries,
            selectedIndex = selectedMode,
            onSelectedIndexChange = { index ->
                playerViewModel?.setLiveUpdateLyricMode(index)
                    ?: scope.launch { settingsManager.setLiveUpdateLyricMode(index) }
            }
        )
    }

    val displayLabels = listOf(
        stringResource(R.string.settings_live_update_lyric_display_compact),
        stringResource(R.string.settings_live_update_lyric_display_full)
    )
    val displayEntries = remember(displayLabels) { displayLabels.map { DropdownItem(title = it) } }
    val selectedDisplayMode = displayMode.coerceIn(0, displayLabels.lastIndex)
    SettingsFocusAnchor(active = highlightKey == "live_update_lyric_display") {
        WindowSpinnerPreference(
            title = stringResource(R.string.settings_live_update_lyric_display),
            summary = stringResource(R.string.settings_current_value, displayLabels[selectedDisplayMode]),
            enabled = enabled,
            items = displayEntries,
            selectedIndex = selectedDisplayMode,
            onSelectedIndexChange = { index ->
                playerViewModel?.setLiveUpdateLyricDisplayMode(index)
                    ?: scope.launch { settingsManager.setLiveUpdateLyricDisplayMode(index) }
            }
        )
    }

    val secondaryLabels = listOf(
        stringResource(R.string.settings_live_update_lyric_secondary_song),
        stringResource(R.string.settings_live_update_lyric_secondary_translation),
        stringResource(R.string.settings_live_update_lyric_secondary_pronunciation)
    )
    val secondaryEntries = remember(secondaryLabels) { secondaryLabels.map { DropdownItem(title = it) } }
    val selectedSecondaryMode = secondaryMode.coerceIn(0, secondaryLabels.lastIndex)
    SettingsFocusAnchor(active = highlightKey == "live_update_lyric_secondary") {
        WindowSpinnerPreference(
            title = stringResource(R.string.settings_live_update_lyric_secondary),
            summary = stringResource(R.string.settings_current_value, secondaryLabels[selectedSecondaryMode]),
            enabled = enabled,
            items = secondaryEntries,
            selectedIndex = selectedSecondaryMode,
            onSelectedIndexChange = { index ->
                playerViewModel?.setLiveUpdateLyricSecondaryMode(index)
                    ?: scope.launch { settingsManager.setLiveUpdateLyricSecondaryMode(index) }
            }
        )
    }

    val xiaomiSuperIslandEnabled by settingsManager.xiaomiSuperIslandLyricEnabled.collectAsState(initial = false)
    SwitchPreference(
        title = stringResource(R.string.settings_enable_xiaomi_super_island_lyric),
        summary = stringResource(R.string.settings_enable_xiaomi_super_island_lyric_summary),
        checked = xiaomiSuperIslandEnabled,
        onCheckedChange = { nextEnabled ->
            playerViewModel?.setXiaomiSuperIslandLyricEnabled(nextEnabled)
                ?: scope.launch { settingsManager.setXiaomiSuperIslandLyricEnabled(nextEnabled) }
        }
    )
    ArrowPreference(
        title = stringResource(R.string.settings_xiaomi_super_island_custom),
        summary = stringResource(R.string.settings_xiaomi_super_island_custom_summary),
        onClick = onOpenXiaomiSuperIslandSettings
    )
}

@Composable
internal fun SettingsLyriconControls(
    playerViewModel: PlayerViewModel?,
    highlightKey: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val lyriconEnabled by settingsManager.lyriconEnabled.collectAsState(initial = false)
    val lyriconTranslation by settingsManager.lyriconTranslation.collectAsState(initial = true)
    val lyriconPronunciation by settingsManager.lyriconPronunciation.collectAsState(initial = false)
    val labels = rememberLyricSecondaryLabels()
    val entries = remember(labels) { labels.map { DropdownItem(title = it) } }

    SettingsFocusAnchor(active = highlightKey == "lyricon") {
        SwitchPreference(
            title = stringResource(R.string.settings_enable_lyricon),
            summary = stringResource(R.string.settings_enable_lyricon_summary),
            checked = lyriconEnabled,
            onCheckedChange = { enabled ->
                playerViewModel?.setLyriconEnabled(enabled)
                    ?: scope.launch { settingsManager.setLyriconEnabled(enabled) }
            }
        )
    }

    WindowSpinnerPreference(
        title = stringResource(R.string.settings_secondary_delivery_content),
        summary = stringResource(
            R.string.settings_current_value,
            labels[lyricSecondaryIndex(lyriconTranslation, lyriconPronunciation)]
        ),
        enabled = lyriconEnabled,
        items = entries,
        selectedIndex = lyricSecondaryIndex(lyriconTranslation, lyriconPronunciation),
        onSelectedIndexChange = { index ->
            when (index) {
                SettingsManager.LYRIC_SECONDARY_TRANSLATION -> {
                    playerViewModel?.setLyriconTranslation(true)
                        ?: scope.launch {
                            settingsManager.setLyriconTranslation(true)
                            settingsManager.setLyriconPronunciation(false)
                        }
                }
                SettingsManager.LYRIC_SECONDARY_PRONUNCIATION -> {
                    playerViewModel?.setLyriconPronunciation(true)
                        ?: scope.launch {
                            settingsManager.setLyriconPronunciation(true)
                            settingsManager.setLyriconTranslation(false)
                        }
                }
                else -> {
                    playerViewModel?.let {
                        it.setLyriconTranslation(false)
                        it.setLyriconPronunciation(false)
                    } ?: scope.launch {
                        settingsManager.setLyriconTranslation(false)
                        settingsManager.setLyriconPronunciation(false)
                    }
                }
            }
        }
    )
}

@Composable
internal fun SettingsLyricOutputControls(
    playerViewModel: PlayerViewModel?,
    highlightKey: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val superLyricEnabled by settingsManager.superLyricEnabled.collectAsState(initial = false)
    val superLyricTranslation by settingsManager.superLyricTranslation.collectAsState(initial = true)
    val superLyricPronunciation by settingsManager.superLyricPronunciation.collectAsState(initial = false)
    val lyricGetterEnabled by settingsManager.lyricGetterEnabled.collectAsState(initial = false)
    val tickerEnabled by settingsManager.tickerEnabled.collectAsState(initial = false)
    val tickerHeadsUpLyrics by settingsManager.tickerHeadsUpLyrics.collectAsState(initial = false)
    val samsungFloatingLyricTranslation by settingsManager.samsungFloatingLyricTranslation.collectAsState(initial = false)
    val statusBarAllowPhonetic by settingsManager.statusBarAllowPhonetic.collectAsState(initial = false)
    val bluetoothLyricEnabled by settingsManager.bluetoothLyricEnabled.collectAsState(initial = false)
    val bluetoothLyricTranslation by settingsManager.bluetoothLyricTranslation.collectAsState(initial = true)
    val bluetoothLyricPronunciation by settingsManager.bluetoothLyricPronunciation.collectAsState(initial = false)
    val colorOsLockScreenLyricEnabled by settingsManager.colorOsLockScreenLyricEnabled.collectAsState(initial = false)
    val colorOsLockScreenLyricMode by settingsManager.colorOsLockScreenLyricMode.collectAsState(
        initial = SettingsManager.OPLUS_LYRIC_MODE_SYSTEM
    )
    val isFlymeDevice = remember {
        Build.MANUFACTURER.orEmpty().contains("meizu", ignoreCase = true) ||
            Build.BRAND.orEmpty().contains("meizu", ignoreCase = true) ||
            Build.DISPLAY.orEmpty().contains("flyme", ignoreCase = true)
    }
    val labels = rememberLyricSecondaryLabels()
    val entries = remember(labels) { labels.map { DropdownItem(title = it) } }
    val oplusModeLabels = listOf(
        stringResource(R.string.settings_coloros_lock_screen_lyric_mode_system),
        stringResource(R.string.settings_coloros_lock_screen_lyric_mode_module)
    )
    val oplusModeEntries = remember(oplusModeLabels) { oplusModeLabels.map { DropdownItem(title = it) } }

    SettingsFocusAnchor(active = highlightKey == "lyric_output") {
        SwitchPreference(
            title = stringResource(R.string.settings_enable_super_lyric),
            summary = stringResource(R.string.settings_enable_super_lyric_summary),
            checked = superLyricEnabled,
            onCheckedChange = { enabled ->
                playerViewModel?.setSuperLyricEnabled(enabled)
                    ?: scope.launch { settingsManager.setSuperLyricEnabled(enabled) }
            }
        )
    }

    WindowSpinnerPreference(
        title = stringResource(R.string.settings_secondary_delivery_content),
        summary = stringResource(
            R.string.settings_current_value,
            labels[lyricSecondaryIndex(superLyricTranslation, superLyricPronunciation)]
        ),
        enabled = superLyricEnabled,
        items = entries,
        selectedIndex = lyricSecondaryIndex(superLyricTranslation, superLyricPronunciation),
        onSelectedIndexChange = { index ->
            when (index) {
                SettingsManager.LYRIC_SECONDARY_TRANSLATION -> {
                    playerViewModel?.setSuperLyricTranslation(true)
                        ?: scope.launch {
                            settingsManager.setSuperLyricTranslation(true)
                            settingsManager.setSuperLyricPronunciation(false)
                        }
                }
                SettingsManager.LYRIC_SECONDARY_PRONUNCIATION -> {
                    playerViewModel?.setSuperLyricPronunciation(true)
                        ?: scope.launch {
                            settingsManager.setSuperLyricPronunciation(true)
                            settingsManager.setSuperLyricTranslation(false)
                        }
                }
                else -> {
                    playerViewModel?.let {
                        it.setSuperLyricTranslation(false)
                        it.setSuperLyricPronunciation(false)
                    } ?: scope.launch {
                        settingsManager.setSuperLyricTranslation(false)
                        settingsManager.setSuperLyricPronunciation(false)
                    }
                }
            }
        }
    )

    SwitchPreference(
        title = stringResource(R.string.settings_enable_lyric_getter),
        summary = stringResource(R.string.settings_enable_lyric_getter_summary),
        checked = lyricGetterEnabled,
        onCheckedChange = { enabled ->
            playerViewModel?.setLyricGetterEnabled(enabled)
                ?: scope.launch { settingsManager.setLyricGetterEnabled(enabled) }
        }
    )

    SwitchPreference(
        title = stringResource(R.string.settings_enable_flyme_ticker),
        summary = stringResource(R.string.settings_enable_flyme_ticker_summary),
        checked = tickerEnabled,
        onCheckedChange = { enabled ->
            playerViewModel?.setTickerEnabled(enabled)
                ?: scope.launch {
                    settingsManager.setTickerEnabled(enabled)
                    if (enabled) settingsManager.setTickerHideNotification(true)
                }
        }
    )

    SwitchPreference(
        title = stringResource(R.string.settings_heads_up_lyric_notifications),
        summary = stringResource(R.string.settings_heads_up_lyric_notifications_summary),
        enabled = tickerEnabled && !isFlymeDevice,
        checked = tickerHeadsUpLyrics && !isFlymeDevice,
        onCheckedChange = { enabled ->
            if (!isFlymeDevice) {
                playerViewModel?.setTickerHeadsUpLyrics(enabled)
                    ?: scope.launch { settingsManager.setTickerHeadsUpLyrics(enabled) }
            }
        }
    )

    WindowSpinnerPreference(
        title = stringResource(R.string.settings_heads_up_lyric_secondary),
        summary = stringResource(
            R.string.settings_current_value,
            labels[lyricSecondaryIndex(samsungFloatingLyricTranslation, statusBarAllowPhonetic)]
        ),
        enabled = tickerEnabled && tickerHeadsUpLyrics && !isFlymeDevice,
        items = entries,
        selectedIndex = lyricSecondaryIndex(samsungFloatingLyricTranslation, statusBarAllowPhonetic),
        onSelectedIndexChange = { index ->
            when (index) {
                SettingsManager.LYRIC_SECONDARY_TRANSLATION -> {
                    playerViewModel?.setSamsungFloatingLyricTranslation(true)
                        ?: scope.launch {
                            settingsManager.setSamsungFloatingLyricTranslation(true)
                            settingsManager.setStatusBarAllowPhonetic(false)
                        }
                }
                SettingsManager.LYRIC_SECONDARY_PRONUNCIATION -> {
                    playerViewModel?.setStatusBarAllowPhonetic(true)
                        ?: scope.launch {
                            settingsManager.setStatusBarAllowPhonetic(true)
                            settingsManager.setSamsungFloatingLyricTranslation(false)
                        }
                }
                else -> {
                    playerViewModel?.let {
                        it.setSamsungFloatingLyricTranslation(false)
                        it.setStatusBarAllowPhonetic(false)
                    } ?: scope.launch {
                        settingsManager.setSamsungFloatingLyricTranslation(false)
                        settingsManager.setStatusBarAllowPhonetic(false)
                    }
                }
            }
        }
    )

    SettingsFocusAnchor(active = highlightKey == "coloros_lock_screen_lyric") {
        SwitchPreference(
            title = stringResource(R.string.settings_enable_coloros_lock_screen_lyric),
            summary = stringResource(R.string.settings_enable_coloros_lock_screen_lyric_summary),
            checked = colorOsLockScreenLyricEnabled,
            onCheckedChange = { enabled ->
                scope.launch { settingsManager.setColorOsLockScreenLyricEnabled(enabled) }
            }
        )
    }

    WindowSpinnerPreference(
        title = stringResource(R.string.settings_coloros_lock_screen_lyric_mode),
        summary = stringResource(
            R.string.settings_current_value,
            oplusModeLabels[colorOsLockScreenLyricMode.coerceIn(0, oplusModeLabels.lastIndex)]
        ),
        enabled = colorOsLockScreenLyricEnabled,
        items = oplusModeEntries,
        selectedIndex = colorOsLockScreenLyricMode.coerceIn(0, oplusModeLabels.lastIndex),
        onSelectedIndexChange = { index ->
            scope.launch {
                settingsManager.setColorOsLockScreenLyricMode(
                    if (index == SettingsManager.OPLUS_LYRIC_MODE_MODULE) {
                        SettingsManager.OPLUS_LYRIC_MODE_MODULE
                    } else {
                        SettingsManager.OPLUS_LYRIC_MODE_SYSTEM
                    }
                )
            }
        }
    )

    SwitchPreference(
        title = stringResource(R.string.settings_enable_bluetooth_lyric),
        summary = stringResource(R.string.settings_enable_bluetooth_lyric_summary),
        checked = bluetoothLyricEnabled,
        onCheckedChange = { enabled ->
            playerViewModel?.setBluetoothLyricEnabled(enabled)
                ?: scope.launch { settingsManager.setBluetoothLyricEnabled(enabled) }
        }
    )

    WindowSpinnerPreference(
        title = stringResource(R.string.settings_secondary_delivery_content),
        summary = stringResource(
            R.string.settings_current_value,
            labels[lyricSecondaryIndex(bluetoothLyricTranslation, bluetoothLyricPronunciation)]
        ),
        enabled = bluetoothLyricEnabled,
        items = entries,
        selectedIndex = lyricSecondaryIndex(bluetoothLyricTranslation, bluetoothLyricPronunciation),
        onSelectedIndexChange = { index ->
            when (index) {
                SettingsManager.LYRIC_SECONDARY_TRANSLATION -> {
                    playerViewModel?.setBluetoothLyricTranslation(true)
                        ?: scope.launch {
                            settingsManager.setBluetoothLyricTranslation(true)
                            settingsManager.setBluetoothLyricPronunciation(false)
                        }
                }
                SettingsManager.LYRIC_SECONDARY_PRONUNCIATION -> {
                    playerViewModel?.setBluetoothLyricPronunciation(true)
                        ?: scope.launch {
                            settingsManager.setBluetoothLyricPronunciation(true)
                            settingsManager.setBluetoothLyricTranslation(false)
                        }
                }
                else -> {
                    playerViewModel?.let {
                        it.setBluetoothLyricTranslation(false)
                        it.setBluetoothLyricPronunciation(false)
                    } ?: scope.launch {
                        settingsManager.setBluetoothLyricTranslation(false)
                        settingsManager.setBluetoothLyricPronunciation(false)
                    }
                }
            }
        }
    )

    val mediaNotificationButtonIds by settingsManager.mediaNotificationButtonIds.collectAsState(
        initial = SettingsManager.DEFAULT_MEDIA_NOTIFICATION_BUTTON_IDS
    )
    val mediaNotificationButtonPairs = remember {
        listOf(
            listOf(
                SettingsManager.MEDIA_NOTIFICATION_BUTTON_PLAYBACK_MODE,
                SettingsManager.MEDIA_NOTIFICATION_BUTTON_DESKTOP_LYRIC
            ),
            listOf(
                SettingsManager.MEDIA_NOTIFICATION_BUTTON_PLAYBACK_MODE,
                SettingsManager.MEDIA_NOTIFICATION_BUTTON_FAVORITE
            ),
            listOf(
                SettingsManager.MEDIA_NOTIFICATION_BUTTON_DESKTOP_LYRIC,
                SettingsManager.MEDIA_NOTIFICATION_BUTTON_FAVORITE
            )
        )
    }
    val mediaNotificationButtonLabels = listOf(
        stringResource(R.string.settings_media_notification_buttons_playback_desktop),
        stringResource(R.string.settings_media_notification_buttons_playback_favorite),
        stringResource(R.string.settings_media_notification_buttons_desktop_favorite)
    )
    val mediaNotificationButtonEntries = remember(mediaNotificationButtonLabels) {
        mediaNotificationButtonLabels.map { DropdownItem(title = it) }
    }
    val selectedMediaNotificationButtonPair = mediaNotificationButtonPairs
        .indexOfFirst { it.toSet() == mediaNotificationButtonIds.toSet() }
        .takeIf { it >= 0 }
        ?: 1
    WindowSpinnerPreference(
        title = stringResource(R.string.settings_media_notification_buttons),
        summary = stringResource(
            R.string.settings_current_value,
            mediaNotificationButtonLabels[selectedMediaNotificationButtonPair]
        ),
        items = mediaNotificationButtonEntries,
        selectedIndex = selectedMediaNotificationButtonPair,
        onSelectedIndexChange = { index ->
            mediaNotificationButtonPairs.getOrNull(index)?.let { selected ->
                scope.launch { settingsManager.setMediaNotificationButtonIds(selected) }
            }
        }
    )
}

@Composable
private fun rememberLyricSecondaryLabels(): List<String> {
    val off = stringResource(R.string.settings_status_secondary_off)
    val translation = stringResource(R.string.settings_status_secondary_translation)
    val pronunciation = stringResource(R.string.settings_status_secondary_pronunciation)
    return remember(off, translation, pronunciation) {
        listOf(off, translation, pronunciation)
    }
}

private fun lyricSecondaryIndex(translation: Boolean, pronunciation: Boolean): Int = when {
    pronunciation -> SettingsManager.LYRIC_SECONDARY_PRONUNCIATION
    translation -> SettingsManager.LYRIC_SECONDARY_TRANSLATION
    else -> SettingsManager.LYRIC_SECONDARY_OFF
}
