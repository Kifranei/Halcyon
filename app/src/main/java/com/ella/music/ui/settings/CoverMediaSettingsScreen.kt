package com.ella.music.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.EllaSmallTopAppBar
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CoverMediaSettingsScreen(
    onBack: () -> Unit,
    highlightKey: String? = null
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.settings_cover_media),
            color = pageBackground,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberSettingsScrollState("settings_cover_media"))
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsArtistCoverSection(highlightKey = highlightKey)
            SettingsDynamicCoverSection(highlightKey = highlightKey)
            SettingsMusicVideoSection(highlightKey = highlightKey)
            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}

@Composable
internal fun SettingsArtistCoverSection(highlightKey: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val artistCoverFolderUri by settingsManager.artistCoverFolderUri.collectCachedAsState("artistCoverFolderUri", "")
    val artistCoverCarousel by settingsManager.artistCoverCarousel.collectCachedAsState("artistCoverCarousel", true)
    val coverExportFolderUri by settingsManager.coverExportFolderUri.collectCachedAsState("coverExportFolderUri", "")
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
        scope.launch { settingsManager.setArtistCoverFolderUri(uri.toString()) }
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

    SmallTitle(text = stringResource(R.string.settings_artist_cover_folder))
    SettingsCardGroup(
        highlight = highlightKey in setOf("artist_cover_folder", "artist_cover_carousel", "cover_export_folder", "cover_media")
    ) {
        Column {
            ArrowPreference(
                title = stringResource(R.string.settings_artist_cover_folder),
                summary = if (artistCoverFolderUri.isBlank()) {
                    stringResource(R.string.settings_artist_cover_folder_summary)
                } else {
                    stringResource(R.string.settings_artist_cover_folder_selected)
                },
                onClick = { artistCoverFolderPicker.launch(null) }
            )
            if (artistCoverFolderUri.isNotBlank()) {
                SwitchPreference(
                    title = stringResource(R.string.settings_artist_cover_carousel),
                    summary = stringResource(R.string.settings_artist_cover_carousel_summary),
                    checked = artistCoverCarousel,
                    onCheckedChange = { scope.launch { settingsManager.setArtistCoverCarousel(it) } }
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_artist_cover_folder_remove),
                    summary = stringResource(R.string.settings_artist_cover_folder_remove_summary),
                    onClick = {
                        scope.launch { settingsManager.setArtistCoverFolderUri("") }
                        Toast.makeText(context, context.getString(R.string.settings_artist_cover_folder_cleared), Toast.LENGTH_SHORT).show()
                    }
                )
            }
            ArrowPreference(
                title = stringResource(R.string.settings_cover_export_folder),
                summary = if (coverExportFolderUri.isBlank()) {
                    stringResource(R.string.settings_cover_export_folder_summary)
                } else {
                    stringResource(R.string.settings_cover_export_folder_selected)
                },
                onClick = { coverExportFolderPicker.launch(null) }
            )
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
internal fun SettingsDynamicCoverSection(highlightKey: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val dynamicCoverEnabled by settingsManager.dynamicCoverEnabled.collectCachedAsState("dynamicCoverEnabled", false)
    val dynamicCoverCustomFolders by settingsManager.dynamicCoverCustomFoldersRaw.collectCachedAsState(
        "dynamicCoverCustomFolders",
        ""
    )
    val dynamicCoverPermissionLauncher = rememberDynamicCoverPermissionLauncher(settingsManager)
    val dynamicCoverFolderPicker = rememberDynamicCoverFolderPicker(
        currentFolders = dynamicCoverCustomFolders,
        settingsManager = settingsManager
    )

    SmallTitle(text = stringResource(R.string.settings_dynamic_cover))
    SettingsCardGroup(
        highlight = highlightKey in setOf("dynamic_cover", "cover_media")
    ) {
        Column {
            SwitchPreference(
                title = stringResource(R.string.settings_dynamic_cover),
                summary = stringResource(R.string.settings_dynamic_cover_summary),
                checked = dynamicCoverEnabled,
                onCheckedChange = {
                    setDynamicCoverEnabled(context, scope, settingsManager, dynamicCoverPermissionLauncher, it)
                }
            )
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
}

@Composable
internal fun SettingsMusicVideoSection(highlightKey: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val musicVideoSyncEnabled by settingsManager.musicVideoSyncEnabled.collectCachedAsState(
        "musicVideoSyncEnabled",
        SettingsManager.DEFAULT_MUSIC_VIDEO_SYNC_ENABLED
    )
    val musicVideoCaptureSubtitles by settingsManager.musicVideoCaptureSubtitles.collectCachedAsState(
        "musicVideoCaptureSubtitles",
        false
    )
    val musicVideoStretchEnabled by settingsManager.musicVideoStretchEnabled.collectCachedAsState(
        "musicVideoStretchEnabled",
        SettingsManager.DEFAULT_MUSIC_VIDEO_STRETCH_ENABLED
    )
    val musicVideoOrientation by settingsManager.musicVideoOrientation.collectCachedAsState(
        "musicVideoOrientation",
        SettingsManager.DEFAULT_MUSIC_VIDEO_ORIENTATION
    )
    val showLocalMusicVideoInLists by settingsManager.showLocalMusicVideoInLists.collectCachedAsState(
        "showLocalMusicVideoInLists",
        true
    )
    val showOnlineMusicVideoInLists by settingsManager.showOnlineMusicVideoInLists.collectCachedAsState(
        "showOnlineMusicVideoInLists",
        true
    )
    val musicVideoCustomFolders by settingsManager.musicVideoCustomFoldersRaw.collectCachedAsState(
        "musicVideoCustomFolders",
        ""
    )
    val musicVideoSyncPermissionLauncher = rememberMusicVideoSyncPermissionLauncher(settingsManager)
    val musicVideoFolderPicker = rememberMusicVideoFolderPicker(
        currentFolders = musicVideoCustomFolders,
        settingsManager = settingsManager
    )
    val offsetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (json.isNullOrBlank()) {
                Toast.makeText(context, R.string.music_video_offsets_import_failed, Toast.LENGTH_SHORT).show()
            } else {
                runCatching { com.ella.music.MusicVideoOffsetsParser.parse(json) }
                    .onSuccess { settingsManager.setMusicVideoOffsetsJson(json) }
                    .onFailure {
                        Toast.makeText(context, R.string.music_video_offsets_import_failed, Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }
    val musicVideoOrientationOptions = listOf(
        SettingsManager.MUSIC_VIDEO_ORIENTATION_SYSTEM to stringResource(R.string.settings_music_video_orientation_system),
        SettingsManager.MUSIC_VIDEO_ORIENTATION_VIDEO to stringResource(R.string.settings_music_video_orientation_video),
        SettingsManager.MUSIC_VIDEO_ORIENTATION_LANDSCAPE to stringResource(R.string.settings_music_video_orientation_landscape),
        SettingsManager.MUSIC_VIDEO_ORIENTATION_PORTRAIT to stringResource(R.string.settings_music_video_orientation_portrait)
    )
    val selectedMusicVideoOrientation = musicVideoOrientationOptions.indexOfFirst { it.first == musicVideoOrientation }
        .takeIf { it >= 0 } ?: 0
    val musicVideoOrientationEntries = remember(musicVideoOrientationOptions) {
        musicVideoOrientationOptions.map { DropdownItem(title = it.second) }
    }

    SmallTitle(text = stringResource(R.string.settings_music_video_sync))
    SettingsCardGroup(
        highlight = highlightKey in setOf("music_video", "cover_media")
    ) {
        Column {
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
            SwitchPreference(
                title = stringResource(R.string.settings_music_video_stretch),
                summary = stringResource(R.string.settings_music_video_stretch_summary),
                checked = musicVideoStretchEnabled,
                onCheckedChange = {
                    scope.launch { settingsManager.setMusicVideoStretchEnabled(it) }
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
                title = stringResource(R.string.settings_music_video_offsets),
                summary = stringResource(R.string.settings_music_video_offsets_summary),
                onClick = { offsetPicker.launch(arrayOf("application/json", "text/plain")) }
            )
        }
    }
}
