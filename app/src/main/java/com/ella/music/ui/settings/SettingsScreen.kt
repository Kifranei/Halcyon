package com.ella.music.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.BuildConfig
import com.ella.music.R
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    onNavigateToAppearanceSettings: () -> Unit,
    onNavigateToLibrarySettings: () -> Unit,
    onNavigateToIntegrationSettings: () -> Unit,
    onNavigateToLyricSettings: () -> Unit,
    onNavigateToAudioSettings: () -> Unit,
    onNavigateToBackupSettings: () -> Unit,
    onNavigateToLogs: () -> Unit,
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
    onBack: () -> Unit = {},
    showBackButton: Boolean = true,
    mainViewModel: MainViewModel? = null,
    playerViewModel: PlayerViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)
    val searchEntries = settingsSearchEntries(
        onNavigateToAppearanceSettings = onNavigateToAppearanceSettings,
        onNavigateToHomeDisplaySettings = onNavigateToHomeDisplaySettings,
        onNavigateToLibrarySettings = onNavigateToLibrarySettings,
        onNavigateToScanFolders = onNavigateToScanFolders,
        onNavigateToHighlightedScanFolders = onNavigateToHighlightedScanFolders,
        onNavigateToIntegrationSettings = onNavigateToIntegrationSettings,
        onNavigateToLyricSettings = onNavigateToLyricSettings,
        onNavigateToLyricFont = onNavigateToLyricFont,
        onNavigateToLyricPluginSources = onNavigateToLyricPluginSources,
        onNavigateToHighlightedLyricSettings = onNavigateToHighlightedLyricSettings,
        onNavigateToHighlightedAppearanceSettings = onNavigateToHighlightedAppearanceSettings,
        onNavigateToHighlightedLibrarySettings = onNavigateToHighlightedLibrarySettings,
        onNavigateToHighlightedIntegrationSettings = onNavigateToHighlightedIntegrationSettings,
        onNavigateToHighlightedAudioSettings = onNavigateToHighlightedAudioSettings,
        onNavigateToHighlightedBackupSettings = onNavigateToHighlightedBackupSettings,
        onNavigateToAudioSettings = onNavigateToAudioSettings,
        onNavigateToEqualizer = onNavigateToEqualizer,
        onNavigateToHighlightedEqualizer = onNavigateToHighlightedEqualizer,
        onNavigateToBackupSettings = onNavigateToBackupSettings,
        onNavigateToLogs = onNavigateToLogs,
        onNavigateToAbout = onNavigateToAbout
    )
    val searchResults = remember(searchQuery, searchEntries) {
        val query = searchQuery.trim()
        if (query.isBlank()) emptyList() else searchEntries.filter { it.matches(query) }.take(16)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.settings),
            color = pageBackground,
            centeredTitle = showBackButton,
            navigationIcon = {
                if (showBackButton) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            EllaSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = {},
                placeholder = stringResource(R.string.settings_search_placeholder),
                autoFocus = false,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            if (searchQuery.isNotBlank()) {
                SmallTitle(text = stringResource(R.string.settings_search_results))
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
                                        searchQuery = ""
                                        entry.onClick()
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                SmallTitle(text = stringResource(R.string.settings_customize))

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

                SmallTitle(text = stringResource(R.string.settings_music_playback))

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
                    }
                }

                SmallTitle(text = stringResource(R.string.settings_services))

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

                SmallTitle(text = stringResource(R.string.settings_maintenance))

                SettingsCardGroup {
                    Column {
                        ArrowPreference(
                            title = stringResource(R.string.settings_clear_online_cache),
                            summary = stringResource(R.string.settings_clear_online_cache_summary),
                            onClick = {
                                scope.launch {
                                    mainViewModel?.clearOnlineMetadataCache()
                                    playerViewModel?.clearOnlineMetadataCache()
                                    Toast.makeText(context, context.getString(R.string.settings_clear_online_cache_done), Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_clear_library_snapshot_cache),
                            summary = stringResource(R.string.settings_clear_library_snapshot_cache_summary),
                            onClick = {
                                mainViewModel?.clearLibrarySnapshotCache()
                                Toast.makeText(context, context.getString(R.string.settings_clear_library_snapshot_cache_done), Toast.LENGTH_SHORT).show()
                            }
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
    }
}

private data class SettingsSearchEntry(
    val title: String,
    val summary: String,
    val keywords: String,
    val onClick: () -> Unit
) {
    fun matches(query: String): Boolean {
        val haystack = "$title $summary $keywords"
        return query.split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
            .all { haystack.contains(it, ignoreCase = true) }
    }
}

@Composable
private fun settingsSearchEntries(
    onNavigateToAppearanceSettings: () -> Unit,
    onNavigateToHomeDisplaySettings: (String) -> Unit,
    onNavigateToLibrarySettings: () -> Unit,
    onNavigateToScanFolders: () -> Unit,
    onNavigateToHighlightedScanFolders: (String) -> Unit,
    onNavigateToIntegrationSettings: () -> Unit,
    onNavigateToLyricSettings: () -> Unit,
    onNavigateToLyricFont: () -> Unit,
    onNavigateToLyricPluginSources: () -> Unit,
    onNavigateToHighlightedLyricSettings: (String) -> Unit,
    onNavigateToHighlightedAppearanceSettings: (String) -> Unit,
    onNavigateToHighlightedLibrarySettings: (String) -> Unit,
    onNavigateToHighlightedIntegrationSettings: (String) -> Unit,
    onNavigateToHighlightedAudioSettings: (String) -> Unit,
    onNavigateToHighlightedBackupSettings: (String) -> Unit,
    onNavigateToAudioSettings: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToHighlightedEqualizer: (String) -> Unit,
    onNavigateToBackupSettings: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToAbout: () -> Unit
): List<SettingsSearchEntry> {
    fun entry(title: String, summary: String, keywords: String = "", onClick: () -> Unit) =
        SettingsSearchEntry(title, summary, keywords, onClick)

    return listOf(
        entry(stringResource(R.string.settings_appearance_home), stringResource(R.string.settings_appearance_home_summary), "主题 语言 外观 壁纸 播放页") { onNavigateToHighlightedAppearanceSettings("appearance") },
        entry(stringResource(R.string.settings_home_display), stringResource(R.string.settings_home_display_items_summary), "首页 功能块 宫格 顺序 隐藏 二级页") { onNavigateToHomeDisplaySettings("home_sections") },
        entry(stringResource(R.string.settings_home_tile_colors_title), stringResource(R.string.settings_home_tile_colors_summary), "首页 功能块 颜色 卡片 透明度") { onNavigateToHomeDisplaySettings("home_tile_colors") },
        entry(stringResource(R.string.settings_auto_show_search_keyboard), stringResource(R.string.settings_auto_show_search_keyboard_summary), "搜索 输入法 键盘 自动弹出") { onNavigateToHighlightedAppearanceSettings("appearance") },
        entry(stringResource(R.string.settings_font_settings), stringResource(R.string.settings_lyric_font), "字体 歌词字体 三级页") { onNavigateToHighlightedAppearanceSettings("lyric_font") },
        entry(stringResource(R.string.settings_library_source), stringResource(R.string.settings_library_source_summary), "音乐来源 音乐库来源 本地 Navidrome Emby 远程 曲库") { onNavigateToHighlightedLibrarySettings("library_source") },
        entry(stringResource(R.string.settings_library_scan), stringResource(R.string.settings_library_scan_summary), "音乐库 扫描 标签 全标签 搜索 分隔符") { onNavigateToHighlightedLibrarySettings("scan") },
        entry(stringResource(R.string.settings_scan_folders), stringResource(R.string.settings_scan_folders_summary), "文件夹 USB 隐藏目录 三级页") { onNavigateToHighlightedScanFolders("scan_folders") },
        entry(stringResource(R.string.settings_lyrics), stringResource(R.string.settings_lyrics_summary), "歌词 词幕 桌面歌词 状态栏 蓝牙 ColorOS") { onNavigateToHighlightedLyricSettings("lyric_basic") },
        entry(stringResource(R.string.settings_enable_coloros_lock_screen_lyric), stringResource(R.string.settings_enable_coloros_lock_screen_lyric_summary), "ColorOS 锁屏岛 歌词 lyricInfo MediaMetadata OPPO 一加") { onNavigateToHighlightedLyricSettings("coloros_lock_screen_lyric") },
        entry(stringResource(R.string.settings_lyric_plugin_sources), stringResource(R.string.settings_lyric_plugin_sources_summary), "在线歌词 匹配 插件 三级页") { onNavigateToHighlightedLyricSettings("lyric_plugin_sources") },
        entry(stringResource(R.string.settings_audio), stringResource(R.string.settings_audio_summary), "播放 解码 随机 音频焦点") { onNavigateToHighlightedAudioSettings("audio_playback") },
        entry(stringResource(R.string.equalizer_screen_title), stringResource(R.string.settings_audio_equalizer_summary), "均衡器 EQ 三级页") { onNavigateToHighlightedEqualizer("equalizer") },
        entry(stringResource(R.string.settings_integrations), stringResource(R.string.settings_integrations_summary), "AI OpenAI MCP 集成") { onNavigateToHighlightedIntegrationSettings("ai") },
        entry(stringResource(R.string.settings_backup), stringResource(R.string.settings_backup_summary), "备份 恢复 WebDAV 数据") { onNavigateToHighlightedBackupSettings("backup_settings") },
        entry(stringResource(R.string.settings_logs), stringResource(R.string.settings_logs_summary), "日志 logcat 崩溃 警告") { onNavigateToLogs() },
        entry(stringResource(R.string.about), BuildConfig.VERSION_NAME, "版本 更新 关于") { onNavigateToAbout() }
    )
}
