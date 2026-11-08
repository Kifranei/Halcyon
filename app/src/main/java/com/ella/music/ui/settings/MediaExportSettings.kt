package com.ella.music.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.DropdownItem
import com.ella.music.ui.components.EllaSpinnerPreference

@Composable
internal fun SettingsMediaExportSection() {
    val context = LocalContext.current
    val settings = remember(context) { SettingsManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val artistLocation by settings.artistImageSaveLocation.collectAsState(initial = "default")
    val mvLocation by settings.musicVideoDownloadLocation.collectAsState(initial = "default")
    val songLocation by settings.songDownloadLocation.collectAsState(initial = "default")
    val template by settings.musicVideoDownloadTemplate.collectAsState(initial = "{artist} - {title}")
    var customTemplate by remember { mutableStateOf(false) }
    val artistPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.isSuccess) scope.launch { settings.setArtistImageSaveLocation(uri.toString()) }
            else android.widget.Toast.makeText(context, R.string.cover_preview_save_failed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val mvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.isSuccess) scope.launch { settings.setMusicVideoDownloadLocation(uri.toString()) }
            else android.widget.Toast.makeText(context, R.string.cover_preview_save_failed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val songPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.isSuccess) scope.launch { settings.setSongDownloadLocation(uri.toString()) }
            else android.widget.Toast.makeText(context, R.string.cover_preview_save_failed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    SmallTitle(stringResource(R.string.settings_media_export))
    SettingsCardGroup {
        Column {
                // search-anchor:start
                SettingsSearchAnchor(R.string.settings_artist_image_save_location) {
                EllaSpinnerPreference(title = stringResource(R.string.settings_artist_image_save_location),
                    summary = stringResource(R.string.settings_artist_image_save_location_summary),
                    items = listOf(DropdownItem(stringResource(R.string.settings_artist_cover_folder)),
                        DropdownItem(stringResource(R.string.settings_media_export_default)),
                        DropdownItem(stringResource(R.string.settings_artist_cover_custom_storage))),
                    selectedIndex = when (artistLocation) { "cover" -> 0; "default" -> 1; else -> 2 },
                    onSelectedIndexChange = { index -> when (index) {
                        0 -> scope.launch { settings.setArtistImageSaveLocation("cover") }
                        1 -> scope.launch { settings.setArtistImageSaveLocation("default") }
                        else -> artistPicker.launch(null)
                    } })
                } // search-anchor:end
                // search-anchor:start
                SettingsSearchAnchor(R.string.settings_mv_download_location) {
                EllaSpinnerPreference(title = stringResource(R.string.settings_mv_download_location),
                    summary = stringResource(R.string.settings_mv_download_location_summary),
                    items = listOf(DropdownItem(stringResource(R.string.settings_music_video_custom_folders)),
                        DropdownItem(stringResource(R.string.settings_media_export_default)),
                        DropdownItem(stringResource(R.string.settings_artist_cover_custom_storage))),
                    selectedIndex = when (mvLocation) { "folder" -> 0; "default" -> 1; else -> 2 },
                    onSelectedIndexChange = { index -> when (index) {
                        0 -> scope.launch { settings.setMusicVideoDownloadLocation("folder") }
                        1 -> scope.launch { settings.setMusicVideoDownloadLocation("default") }
                        else -> mvPicker.launch(null)
                    } })
                } // search-anchor:end
                // search-anchor:start
                SettingsSearchAnchor(R.string.settings_song_download_location) {
                EllaSpinnerPreference(title = stringResource(R.string.settings_song_download_location),
                    summary = stringResource(R.string.settings_song_download_location_summary),
                    items = listOf(DropdownItem(stringResource(R.string.settings_media_export_default)),
                        DropdownItem(stringResource(R.string.settings_artist_cover_custom_storage))),
                    selectedIndex = if (songLocation == "default") 0 else 1,
                    onSelectedIndexChange = { index ->
                        if (index == 0) scope.launch { settings.setSongDownloadLocation("default") }
                        else songPicker.launch(null)
                    })
                } // search-anchor:end
                // search-anchor:start
                SettingsSearchAnchor(R.string.settings_mv_download_template) {
                EllaSpinnerPreference(title = stringResource(R.string.settings_mv_download_template),
                    items = listOf(DropdownItem(stringResource(R.string.settings_export_artist_title)),
                        DropdownItem(stringResource(R.string.settings_export_title_artist)),
                        DropdownItem(stringResource(R.string.settings_export_custom_template))),
                    selectedIndex = if (customTemplate) 2 else when (template) { "{artist} - {title}" -> 0; "{title} - {artist}" -> 1; else -> 2 },
                    onSelectedIndexChange = { index ->
                        customTemplate = index == 2
                        if (index != 2) scope.launch { settings.setMusicVideoDownloadTemplate(if (index == 0) "{artist} - {title}" else "{title} - {artist}") }
                    })
                } // search-anchor:end
            if (customTemplate || template !in listOf("{artist} - {title}", "{title} - {artist}") /* search-reveal */ || SettingsSearchFocus.reveals(R.string.settings_export_custom_template)) {
                // search-anchor:start
                SettingsSearchAnchor(R.string.settings_export_custom_template) {
                SplitSettingTextField(label = stringResource(R.string.settings_export_custom_template), value = template,
                    summary = stringResource(R.string.settings_mv_download_template_summary), singleLine = true,
                    onValueChange = { scope.launch { settings.setMusicVideoDownloadTemplate(it) } })
                } // search-anchor:end
            }
        }
    }
}
