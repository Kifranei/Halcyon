package com.ella.music.ui.online

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.netease.NeteaseQuality
import com.ella.music.ui.settings.SettingsSearchAnchor
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference

@Composable
internal fun NeteaseDownloadPreferences(settings: SettingsManager) {
    val scope = rememberCoroutineScope()
    val quality by settings.neteaseDownloadQuality.collectAsState(initial = "auto")
    val playback by settings.neteaseMvResolution.collectAsState(initial = 720)
    val download by settings.neteaseMvDownloadResolution.collectAsState(initial = 720)
    SettingsSearchAnchor(R.string.netease_download_quality_title) {
        WindowSpinnerPreference(
            title = stringResource(R.string.netease_download_quality_title),
            items = NeteaseQuality.entries.map { DropdownItem(title = stringResource(it.titleRes)) },
            selectedIndex = NeteaseQuality.fromId(quality).ordinal,
            onSelectedIndexChange = { scope.launch { settings.setNeteaseDownloadQuality(NeteaseQuality.entries[it].id) } }
        )
    }
    val resolutions = listOf(240, 480, 720, 1080)
    listOf(R.string.netease_mv_resolution_title to playback, R.string.netease_mv_download_resolution_title to download).forEach { (title, value) ->
        SettingsSearchAnchor(title) {
            WindowSpinnerPreference(
                title = stringResource(title),
                items = resolutions.map { DropdownItem(title = "${it}P") },
                selectedIndex = resolutions.indexOf(value).coerceAtLeast(0),
                onSelectedIndexChange = { index -> scope.launch {
                    if (title == R.string.netease_mv_resolution_title) settings.setNeteaseMvResolution(resolutions[index])
                    else settings.setNeteaseMvDownloadResolution(resolutions[index])
                } }
            )
        }
    }
}
