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
    onNavigateToBottomNavigationSettings: () -> Unit = onNavigateToAppearanceSettings,
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
        onNavigateToBottomNavigationSettings = onNavigateToBottomNavigationSettings,
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
        if (query.isBlank()) {
            emptyList()
        } else {
            searchEntries
                .mapNotNull { entry -> entry.matchScore(query)?.let { score -> entry to score } }
                .sortedWith(compareByDescending<Pair<SettingsSearchEntry, Int>> { it.second }.thenBy { it.first.title })
                .map { it.first }
                .distinctBy { "${it.title}\\u0000${it.summary}" }
                .take(24)
        }
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
            },
            titleStartPadding = if (showBackButton) 64.dp else 20.dp
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
    fun matchScore(query: String): Int? {
        val terms = query.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return null
        val titleMatches = terms.count { title.contains(it, ignoreCase = true) }
        val keywordMatches = terms.count { keywords.contains(it, ignoreCase = true) }
        val summaryMatches = terms.count { summary.contains(it, ignoreCase = true) }
        if (titleMatches + keywordMatches + summaryMatches < terms.size) return null

        // A direct setting-name match should remain ahead of a broad category hit.
        return titleMatches * 100 + keywordMatches * 10 + summaryMatches
    }
}

@Composable
private fun settingsSearchEntries(
    onNavigateToAppearanceSettings: () -> Unit,
    onNavigateToBottomNavigationSettings: () -> Unit,
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
        entry(stringResource(R.string.settings_appearance_home), stringResource(R.string.settings_appearance_home_summary), "主题 深色 浅色 跟随系统 语言 图标 壁纸 启动画面 底栏 沉浸 播放页 背景") { onNavigateToHighlightedAppearanceSettings("appearance") },
        entry(stringResource(R.string.settings_bottom_dock_items), stringResource(R.string.settings_bottom_dock_items_summary), "底栏 底部导航 导航栏 入口 顺序 预览 搜索") { onNavigateToBottomNavigationSettings() },
        entry(stringResource(R.string.settings_home_display), stringResource(R.string.settings_home_display_items_summary), "首页 功能块 宫格 顺序 隐藏 二级页") { onNavigateToHomeDisplaySettings("home_sections") },
        entry(stringResource(R.string.settings_home_tile_colors_title), stringResource(R.string.settings_home_tile_colors_summary), "首页 功能块 颜色 卡片 透明度") { onNavigateToHomeDisplaySettings("home_tile_colors") },
        entry(stringResource(R.string.settings_auto_show_search_keyboard), stringResource(R.string.settings_auto_show_search_keyboard_summary), "搜索 输入法 键盘 自动弹出") { onNavigateToHighlightedAppearanceSettings("auto_show_search_keyboard") },
        entry(stringResource(R.string.settings_font_settings), stringResource(R.string.settings_lyric_font), "字体 歌词字体 三级页") { onNavigateToHighlightedAppearanceSettings("lyric_font") },
        entry(stringResource(R.string.settings_dynamic_cover), stringResource(R.string.settings_dynamic_cover_summary), "视频封面 动态封面 mp4 MV 文件夹 相册权限") { onNavigateToHighlightedAppearanceSettings("dynamic_cover") },
        entry(stringResource(R.string.settings_player_show_total_duration), stringResource(R.string.settings_player_show_total_duration_summary), "进度条 总时长 剩余时间 播放时间 拖动预览") { onNavigateToHighlightedAppearanceSettings("player_show_total_duration") },
        entry(stringResource(R.string.settings_player_tap_seek), stringResource(R.string.settings_player_tap_seek_summary), "进度条 点击 跳转 拖动") { onNavigateToHighlightedAppearanceSettings("player_tap_seek") },
        entry(stringResource(R.string.settings_transport_button_outlines), stringResource(R.string.settings_transport_button_outlines_summary), "播放页 控制 按钮 轮廓 外框 描边") { onNavigateToHighlightedAppearanceSettings("transport_button_outlines") },
        entry(stringResource(R.string.settings_player_immersive_cover), stringResource(R.string.settings_player_immersive_cover_summary), "沉浸 播放页 封面 全屏") { onNavigateToHighlightedAppearanceSettings("player_immersive") },
        entry(stringResource(R.string.settings_player_page_style), stringResource(R.string.settings_player_page_style_summary), "播放页 Apple Music 封面 歌词 样式") { onNavigateToHighlightedAppearanceSettings("player_page") },
        entry(stringResource(R.string.settings_system_bars_mode), stringResource(R.string.settings_system_bars_mode_summary, ""), "沉浸模式 全屏 状态栏 导航栏 隐藏 显示 车机") { onNavigateToHighlightedAppearanceSettings("system_bars") },
        entry(stringResource(R.string.settings_player_landscape_style), stringResource(R.string.settings_player_landscape_style_summary, ""), "横屏播放 宽屏 歌词 CoverFlow MV 流光") { onNavigateToHighlightedAppearanceSettings("player_landscape") },
        entry(stringResource(R.string.settings_beautiful_lyrics_background), stringResource(R.string.settings_beautiful_lyrics_background_summary), "Apple Music 动态背景 歌词页 流光 取色") { onNavigateToHighlightedAppearanceSettings("beautiful_lyrics") },
        entry(stringResource(R.string.settings_library_source), stringResource(R.string.settings_library_source_summary), "音乐来源 音乐库来源 本地 Navidrome Emby 远程 曲库") { onNavigateToHighlightedLibrarySettings("library_source") },
        entry(stringResource(R.string.settings_library_scan), stringResource(R.string.settings_library_scan_summary), "音乐库 扫描 标签 全标签 搜索 分隔符 艺术家 歌手") { onNavigateToHighlightedLibrarySettings("scan") },
        entry(stringResource(R.string.settings_scan_folders), stringResource(R.string.settings_scan_folders_summary), "文件夹 USB 隐藏目录 三级页") { onNavigateToHighlightedScanFolders("scan_folders") },
        entry(stringResource(R.string.settings_full_tag_search), stringResource(R.string.settings_full_tag_search_summary_on), "全字段 全字段搜索 全标签 标签 元数据 作曲 作词 注释 别名 自定义标签 扫描 速度") { onNavigateToHighlightedScanFolders("scan_media_source") },
        entry(stringResource(R.string.settings_show_album_artists), stringResource(R.string.settings_show_album_artists_summary), "艺术家 歌手 歌者 artist singer performer 专辑艺术家 发行专辑") { onNavigateToHighlightedLibrarySettings("show_album_artists") },
        entry(stringResource(R.string.settings_artist_cover_folder), stringResource(R.string.settings_artist_cover_folder_summary), "艺术家 歌手 artist 封面 动态封面 视频封面 mp4 轮播 图片目录") { onNavigateToHighlightedLibrarySettings("artist_cover_folder") },
        entry(stringResource(R.string.settings_artist_cover_carousel), stringResource(R.string.settings_artist_cover_carousel_summary), "艺术家 歌手 artist 封面 动态封面 轮播") { onNavigateToHighlightedLibrarySettings("artist_cover_carousel") },
        entry(stringResource(R.string.settings_artist_separators), stringResource(R.string.settings_artist_separators_summary), "艺术家 歌手 artist 分隔符 feat 合作 作曲 作词") { onNavigateToHighlightedLibrarySettings("artist_separators") },
        entry(stringResource(R.string.settings_artist_protected_names), stringResource(R.string.settings_artist_protected_names_summary), "艺术家 歌手 artist 不拆分 分隔符 保护名称") { onNavigateToHighlightedLibrarySettings("artist_protected_names") },
        entry(stringResource(R.string.settings_search_all_song_match_types), stringResource(R.string.settings_search_all_song_match_types_summary), "搜索 所有 歌曲 艺术家 歌手 专辑 专辑艺术家 元数据 歌词") { onNavigateToHighlightedLibrarySettings("search_all_song_match_types") },
        entry(stringResource(R.string.settings_search_all_categories), stringResource(R.string.settings_search_all_categories_summary), "搜索 所有 分类 艺术家 歌手 文件夹 作曲 作词 流派 年份") { onNavigateToHighlightedLibrarySettings("search_all_categories") },
        entry(stringResource(R.string.settings_library_tile_artist), stringResource(R.string.settings_library_tile_artist_summary), "首页 艺术家 歌手 artist 音乐库 宫格") { onNavigateToHomeDisplaySettings("home_sections") },
        entry(stringResource(R.string.settings_lyric_timing_editor), stringResource(R.string.settings_editor_builtin_lyric_timing), "歌词 打轴 时间轴 LRC 内置 编辑器 LySy") { onNavigateToHighlightedLibrarySettings("tag_scraping") },
        entry(stringResource(R.string.settings_lyrics), stringResource(R.string.settings_lyrics_summary), "歌词 逐字 翻译 音译 字体 对齐 大小 黑名单 歌词源") { onNavigateToHighlightedLyricSettings("lyric_basic") },
        entry(stringResource(R.string.settings_mini_player_lyrics), stringResource(R.string.settings_mini_player_lyrics_summary), "迷你歌词 小窗 翻译 音译 迷你播放器") { onNavigateToHighlightedLyricSettings("mini_lyrics") },
        entry(stringResource(R.string.desktop_lyric_status_bar_mode), stringResource(R.string.desktop_lyric_status_bar_mode_summary), "桌面歌词 悬浮窗 状态栏歌词 暂停隐藏 横屏隐藏 宽度 位置 对齐") { onNavigateToHighlightedLyricSettings("desktop_lyric") },
        entry(stringResource(R.string.settings_enable_lyricon), stringResource(R.string.settings_enable_lyricon_summary), "词幕 Lyricon 外部歌词 翻译 音译") { onNavigateToHighlightedLyricSettings("lyricon") },
        entry(stringResource(R.string.settings_enable_super_lyric), stringResource(R.string.settings_enable_super_lyric_summary), "超级歌词 状态栏 通知 横幅 蓝牙 ColorOS") { onNavigateToHighlightedLyricSettings("lyric_output") },
        entry(stringResource(R.string.settings_enable_coloros_lock_screen_lyric), stringResource(R.string.settings_enable_coloros_lock_screen_lyric_summary), "ColorOS 锁屏岛 歌词 lyricInfo MediaMetadata OPPO 一加") { onNavigateToHighlightedLyricSettings("coloros_lock_screen_lyric") },
        entry(stringResource(R.string.settings_lyric_plugin_sources), stringResource(R.string.settings_lyric_plugin_sources_summary), "在线歌词 匹配 插件 三级页") { onNavigateToHighlightedLyricSettings("lyric_plugin_sources") },
        entry(stringResource(R.string.settings_audio), stringResource(R.string.settings_audio_summary), "播放 无缝 gapless 淡入淡出 crossfade ReplayGain 回放增益 随机 下一首 解码 焦点 蓝牙") { onNavigateToHighlightedAudioSettings("audio_playback") },
        entry(stringResource(R.string.settings_usb_dac_mode), stringResource(R.string.settings_usb_dac_mode_summary), "USB DAC 独占 高解析 输出 位深 采样率") { onNavigateToHighlightedAudioSettings("audio_output") },
        entry(stringResource(R.string.settings_decoder), stringResource(R.string.settings_audio_decoder_auto_summary), "解码 FFmpeg 系统 音频焦点") { onNavigateToHighlightedAudioSettings("audio_system") },
        entry(stringResource(R.string.equalizer_screen_title), stringResource(R.string.settings_audio_equalizer_summary), "均衡器 EQ 低音 高音 压缩器 立体声 360 环绕音 混响") { onNavigateToHighlightedEqualizer("equalizer") },
        entry(stringResource(R.string.equalizer_surround_360_enable), stringResource(R.string.equalizer_surround_360_summary), "360 环绕音 空间音频 spatial 音场 强度 旋转") { onNavigateToHighlightedEqualizer("equalizer") },
        entry(stringResource(R.string.settings_integrations), stringResource(R.string.settings_integrations_summary), "AI OpenAI MCP Last.fm 集成 API") { onNavigateToHighlightedIntegrationSettings("ai") },
        entry(stringResource(R.string.settings_mcp_server), stringResource(R.string.settings_mcp_server_summary), "MCP 服务 本地 端口 集成") { onNavigateToHighlightedIntegrationSettings("mcp") },
        entry(stringResource(R.string.web_music_beta_title), stringResource(R.string.web_music_beta_summary), "Web 网页 局域网 上传 播放 Beta") { onNavigateToHighlightedIntegrationSettings("web_music") },
        entry(stringResource(R.string.settings_backup), stringResource(R.string.settings_backup_summary), "备份 恢复 WebDAV 自动备份 播放记录 设置") { onNavigateToHighlightedBackupSettings("backup_settings") },
        entry(stringResource(R.string.settings_logs), stringResource(R.string.settings_logs_summary), "日志 logcat 崩溃 警告") { onNavigateToLogs() },
        entry(stringResource(R.string.about), BuildConfig.VERSION_NAME, "版本 更新 关于") { onNavigateToAbout() }
    ) + settingsSearchAliases(
        entry = ::entry,
        onAppearance = onNavigateToHighlightedAppearanceSettings,
        onHome = onNavigateToHomeDisplaySettings,
        onLibrary = onNavigateToHighlightedLibrarySettings,
        onLyrics = onNavigateToHighlightedLyricSettings,
        onAudio = onNavigateToHighlightedAudioSettings,
        onBackup = onNavigateToHighlightedBackupSettings,
        onEqualizer = onNavigateToHighlightedEqualizer,
        onIntegration = onNavigateToHighlightedIntegrationSettings,
        onLyricFont = onNavigateToLyricFont,
        onLyricPlugins = onNavigateToLyricPluginSources,
        onLogs = onNavigateToLogs,
        onAbout = onNavigateToAbout
    ) + settingsSearchFallbackEntries(
        entry = ::entry,
        onAppearance = onNavigateToHighlightedAppearanceSettings,
        onHome = onNavigateToHomeDisplaySettings,
        onLibrary = onNavigateToHighlightedLibrarySettings,
        onLyrics = onNavigateToHighlightedLyricSettings,
        onAudio = onNavigateToHighlightedAudioSettings,
        onBackup = onNavigateToHighlightedBackupSettings,
        onIntegration = onNavigateToHighlightedIntegrationSettings
    )
}

/**
 * Most settings are declared in their own preference sections. Keep a resource-backed safety net
 * here so a newly added setting cannot silently be omitted from global search again (#376).
 */
@Composable
private fun settingsSearchFallbackEntries(
    entry: (String, String, String, () -> Unit) -> SettingsSearchEntry,
    onAppearance: (String) -> Unit,
    onHome: (String) -> Unit,
    onLibrary: (String) -> Unit,
    onLyrics: (String) -> Unit,
    onAudio: (String) -> Unit,
    onBackup: (String) -> Unit,
    onIntegration: (String) -> Unit
): List<SettingsSearchEntry> {
    val resources = LocalContext.current.resources
    return R.string::class.java.fields
        .asSequence()
        .mapNotNull { field ->
            val name = field.name
            if (!name.startsWith("settings_") || name.endsWith("_summary")) return@mapNotNull null
            val id = runCatching { field.getInt(null) }.getOrNull() ?: return@mapNotNull null
            val title = runCatching { resources.getString(id) }.getOrNull()?.trim().orEmpty()
            if (title.isBlank() || title.contains("%")) return@mapNotNull null
            val route = when {
                name == "settings_enable_live_update_lyric" -> { { onLyrics("live_update_lyric") } }
                name == "settings_live_update_lyric_content" -> { { onLyrics("live_update_lyric_content") } }
                name == "settings_live_update_lyric_display" -> { { onLyrics("live_update_lyric_display") } }
                name == "settings_live_update_lyric_secondary" -> { { onLyrics("live_update_lyric_secondary") } }
                name.contains("backup") -> { { onBackup("backup_settings") } }
                name.contains("openai") || name.contains("mcp") || name.contains("lastfm") ||
                    name.contains("ai_") -> { { onIntegration("ai") } }
                name.contains("lyric") || name.contains("desktop") || name.contains("status_") ||
                    name.contains("coloros") || name.contains("flyme") -> { { onLyrics("lyric_basic") } }
                name.contains("audio") || name.contains("decoder") || name.contains("usb") ||
                    name.contains("crossfade") || name.contains("replay") || name.contains("gapless") ||
                    name.contains("shuffle") || name.contains("playback") || name.contains("previous_button") ||
                    name.contains("resume_") || name.contains("startup_play") -> { { onAudio("audio_playback") } }
                name.contains("scan") || name.contains("library") || name.contains("metadata") ||
                    name.contains("tag_") || name.contains("artist_") || name.contains("genre_") ||
                    name.contains("full_tag") -> { { onLibrary("scan") } }
                name.contains("home_") || name.contains("bottom_dock") || name.contains("category_grid") -> {
                    { onHome("home_sections") }
                }
                else -> { { onAppearance("appearance") } }
            }
            val summaryId = resources.getIdentifier("${name}_summary", "string", resources.getResourcePackageName(id))
            val summary = if (summaryId != 0) resources.getString(summaryId) else ""
            entry(title, summary, name.removePrefix("settings_").replace('_', ' '), route)
        }
        .toList()
}

@Composable
private fun settingsSearchAliases(
    entry: (String, String, String, () -> Unit) -> SettingsSearchEntry,
    onAppearance: (String) -> Unit,
    onHome: (String) -> Unit,
    onLibrary: (String) -> Unit,
    onLyrics: (String) -> Unit,
    onAudio: (String) -> Unit,
    onBackup: (String) -> Unit,
    onEqualizer: (String) -> Unit,
    onIntegration: (String) -> Unit,
    onLyricFont: () -> Unit,
    onLyricPlugins: () -> Unit,
    onLogs: () -> Unit,
    onAbout: () -> Unit
): List<SettingsSearchEntry> = listOf(
    entry(stringResource(R.string.settings_app_wallpaper), stringResource(R.string.settings_app_wallpaper_summary), "壁纸 图片 背景 模糊 毛玻璃 透明") { onAppearance("wallpaper") },
    entry(stringResource(R.string.settings_app_icon), stringResource(R.string.settings_app_icon_summary), "图标 启动器 图标包 anime loli") { onAppearance("app_icon") },
    entry(stringResource(R.string.settings_player_immersive_cover), stringResource(R.string.settings_player_immersive_cover_summary), "沉浸播放页 封面取色 文字 图标 背景 动态背景") { onAppearance("player_immersive") },
    entry(stringResource(R.string.settings_player_page_style), stringResource(R.string.settings_player_page_style_summary), "播放页 Apple Music 封面 歌词 样式") { onAppearance("player_page") },
    entry(stringResource(R.string.settings_system_bars_mode), stringResource(R.string.settings_system_bars_mode_summary, ""), "沉浸模式 全屏 状态栏 导航栏 隐藏 显示 车机") { onAppearance("system_bars") },
    entry(stringResource(R.string.settings_player_landscape_style), stringResource(R.string.settings_player_landscape_style_summary, ""), "横屏播放 宽屏 歌词 CoverFlow MV 流光") { onAppearance("player_immersive") },
    entry(stringResource(R.string.settings_dynamic_cover), stringResource(R.string.settings_dynamic_cover_summary), "动态封面 视频封面 MV mp4") { onAppearance("dynamic_cover") },
    entry(stringResource(R.string.settings_home_display), stringResource(R.string.settings_home_display_items_summary), "首页 显示 项目 排序 隐藏 宫格") { onHome("home_sections") },
    entry(stringResource(R.string.settings_home_tile_colors_title), stringResource(R.string.settings_home_tile_colors_summary), "首页 卡片 颜色 渐变 置顶") { onHome("home_tile_colors") },
    entry(stringResource(R.string.settings_scan_folders), stringResource(R.string.settings_scan_folders_summary), "扫描 文件夹 排除 隐藏目录 存储权限") { onLibrary("scan") },
    entry(stringResource(R.string.settings_auto_scan_local_playlists), stringResource(R.string.settings_auto_scan_local_playlists_summary), "自动扫描 本地歌单 m3u 播放列表") { onLibrary("auto_scan_local_playlists") },
    entry(stringResource(R.string.settings_min_duration_filter), stringResource(R.string.settings_min_duration_filter_summary), "扫描 最小时长 过滤 短音频") { onLibrary("min_duration_filter") },
    entry(stringResource(R.string.settings_tag_ignore_case), stringResource(R.string.settings_tag_ignore_case_summary), "标签 大小写 忽略 英文") { onLibrary("tag_ignore_case") },
    entry(stringResource(R.string.settings_metadata_editor), "MusicTag LunaBeat 内置编辑器", "元数据 标签 编辑 ID3 FLAC") { onLibrary("tag_scraping") },
    entry(stringResource(R.string.settings_editor_ask_every_time), "选择标签和歌词编辑器", "编辑器 每次询问 MusicTag LunaBeat") { onLibrary("tag_scraping") },
    entry(stringResource(R.string.settings_song_rating_display_stars), stringResource(R.string.settings_song_rating_display_stars_summary), "评分 星级 五星 列表") { onLibrary("song_rating_display_stars") },
    entry(stringResource(R.string.settings_lyric_timing_editor), stringResource(R.string.settings_editor_builtin_lyric_timing), "打轴 歌词 时间轴 LRC ELRC TTML LySy") { onLibrary("tag_scraping") },
    entry(stringResource(R.string.settings_font_screen_title), stringResource(R.string.settings_lyric_font), "歌词 字体 原文 翻译 CJK 西文 导入") { onLyricFont() },
    entry(stringResource(R.string.settings_lyric_plugin_sources), stringResource(R.string.settings_lyric_plugin_sources_summary), "歌词 源 插件 导入 在线 匹配") { onLyricPlugins() },
    entry(stringResource(R.string.settings_lyric_line_blacklist), stringResource(R.string.settings_lyric_line_blacklist_summary), "歌词 黑名单 过滤 行") { onLyrics("lyric_basic") },
    entry(stringResource(R.string.settings_player_lyric_text_align), stringResource(R.string.settings_lyric_scale_summary), "歌词 对齐 左 中 右 大小 缩放") { onLyrics("lyric_basic") },
    entry(stringResource(R.string.settings_mini_player_cover_rotation), stringResource(R.string.settings_mini_player_cover_rotation_summary), "迷你播放器 封面 旋转") { onLyrics("mini_lyrics") },
    entry(stringResource(R.string.settings_enable_bluetooth_lyric), stringResource(R.string.settings_enable_bluetooth_lyric_summary), "蓝牙 歌词 设备") { onLyrics("lyric_output") },
    entry(stringResource(R.string.settings_enable_flyme_ticker), stringResource(R.string.settings_enable_flyme_ticker_summary), "Flyme 魅族 状态栏 歌词") { onLyrics("lyric_output") },
    entry(stringResource(R.string.settings_enable_lyric_getter), stringResource(R.string.settings_enable_lyric_getter_summary), "歌词 获取器 广播") { onLyrics("lyric_output") },
    entry(stringResource(R.string.settings_heads_up_lyric_notifications), stringResource(R.string.settings_heads_up_lyric_notifications_summary), "通知 横幅 歌词") { onLyrics("lyric_output") },
    entry(stringResource(R.string.settings_usb_dac_mode), stringResource(R.string.settings_usb_dac_mode_summary), "USB DAC 独占 输出 采样率 位深") { onAudio("audio_output") },
    entry(stringResource(R.string.settings_decoder), stringResource(R.string.settings_audio_decoder_auto_summary), "解码 FFmpeg 系统 音频焦点") { onAudio("audio_system") },
    entry(stringResource(R.string.equalizer_screen_title), stringResource(R.string.settings_audio_equalizer_summary), "均衡器 EQ 音效") { onEqualizer("equalizer") },
    entry(stringResource(R.string.equalizer_surround_360_enable), stringResource(R.string.equalizer_surround_360_summary), "360 环绕音 空间音频 全景") { onEqualizer("equalizer") },
    entry(stringResource(R.string.equalizer_crossfeed_enable), stringResource(R.string.equalizer_crossfeed_summary), "串音 耳机 crossfeed") { onEqualizer("equalizer") },
    entry(stringResource(R.string.equalizer_compressor_enable), "压缩器动态范围控制", "压缩器 compressor 阈值 比率") { onEqualizer("equalizer") },
    entry(stringResource(R.string.settings_openai_model), stringResource(R.string.settings_openai_model_summary), "OpenAI AI 模型 GPT API") { onIntegration("ai") },
    entry(stringResource(R.string.settings_mcp_server), stringResource(R.string.settings_mcp_server_summary), "MCP 服务 本地 端口") { onIntegration("mcp") },
    entry(stringResource(R.string.web_music_beta_title), stringResource(R.string.web_music_beta_summary), "Web 网页 局域网 上传 播放 Beta") { onIntegration("web_music") },
    entry(stringResource(R.string.settings_lastfm), stringResource(R.string.settings_lastfm_summary), "Last.fm scrobble 听歌记录") { onIntegration("lastfm") },
    entry(stringResource(R.string.settings_backup), stringResource(R.string.settings_backup_summary), "备份 恢复 WebDAV 自动备份") { onBackup("backup_settings") },
    entry(stringResource(R.string.settings_logs), stringResource(R.string.settings_logs_summary), "日志 崩溃 调试 logcat") { onLogs() },
    entry(stringResource(R.string.about), BuildConfig.VERSION_NAME, "版本 更新 开源协议 第三方许可") { onAbout() }
)
