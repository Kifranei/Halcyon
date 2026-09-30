package com.ella.music.ui.settings

import com.ella.music.R

/** A search result is a concrete settings destination, never inferred from its label. */
internal sealed interface SettingsSearchTarget {
    data class AppearancePage(val page: String) : SettingsSearchTarget
    data class Page(val route: String) : SettingsSearchTarget
    data object SetupWizard : SettingsSearchTarget
    data object AppearanceHub : SettingsSearchTarget
    data object BottomNavigation : SettingsSearchTarget
    data class PlayerShortcuts(val mode: String) : SettingsSearchTarget
    data class HomeDisplay(val highlight: String) : SettingsSearchTarget
    data class Appearance(val highlight: String) : SettingsSearchTarget
    data class Library(val highlight: String) : SettingsSearchTarget
    data class Scan(val highlight: String) : SettingsSearchTarget
    data class Lyrics(val highlight: String) : SettingsSearchTarget
    data object LyricFont : SettingsSearchTarget
    data object LyricPlugins : SettingsSearchTarget
    data class Audio(val highlight: String) : SettingsSearchTarget
    data class Equalizer(val highlight: String) : SettingsSearchTarget
    data class Integrations(val highlight: String) : SettingsSearchTarget
    data class Backup(val highlight: String) : SettingsSearchTarget
    data class CoverMedia(val highlight: String) : SettingsSearchTarget
    data object Maintenance : SettingsSearchTarget
    data object Logs : SettingsSearchTarget
    data object About : SettingsSearchTarget
}

internal data class SettingsSearchDefinition(
    val titleRes: Int,
    val summaryRes: Int? = null,
    val keywords: String = "",
    val target: SettingsSearchTarget,
    val sheet: String = ""
)

/**
 * The single, explicit settings search catalog. Keep the destination beside each label so moving
 * a preference between settings pages cannot silently leave a stale name-based fallback route.
 */
private val manualSettingsSearchCatalog = listOf(
    SettingsSearchDefinition(R.string.video_tools_title, R.string.video_tools_summary, "视频 m3u m3u8 mp4 播放 下载 video player", SettingsSearchTarget.Page("video_player")),
    SettingsSearchDefinition(R.string.settings_other, null, "其他 视频 other", SettingsSearchTarget.Page("other_settings")),
    SettingsSearchDefinition(R.string.settings_setup_wizard, R.string.settings_setup_wizard_summary, "向导 引导 初始设置 新手 setup", SettingsSearchTarget.SetupWizard),
    SettingsSearchDefinition(R.string.settings_appearance_home, R.string.settings_appearance_home_summary, "主题 深色 浅色 跟随系统 语言 图标 壁纸 启动画面 底栏 沉浸 播放页 背景 theme language appearance", SettingsSearchTarget.AppearanceHub),
    SettingsSearchDefinition(R.string.settings_bottom_dock_items, R.string.settings_bottom_dock_items_summary, "底栏 底部导航 导航栏 入口 顺序 预览 搜索 dock navigation", SettingsSearchTarget.BottomNavigation),
    SettingsSearchDefinition(R.string.settings_bottom_dock_merge_search, R.string.settings_bottom_dock_merge_search_summary, "底栏 搜索 合并 收缩 迷你播放条 歌词", SettingsSearchTarget.BottomNavigation),
    SettingsSearchDefinition(R.string.settings_player_shortcut_items, R.string.settings_player_shortcut_items_summary, "播放页 快捷操作 快捷功能 菜单 预览 排序 player shortcut", SettingsSearchTarget.PlayerShortcuts("horizontal")),
    SettingsSearchDefinition(R.string.settings_non_immersive_player_shortcuts, R.string.settings_non_immersive_player_shortcuts_summary, "非沉浸 播放页 快捷操作 底部", SettingsSearchTarget.PlayerShortcuts("non_immersive")),

    SettingsSearchDefinition(R.string.settings_home_display, R.string.settings_home_display_items_summary, "首页 功能块 宫格 顺序 隐藏 home sections", SettingsSearchTarget.HomeDisplay("home_sections")),
    SettingsSearchDefinition(R.string.home_daily_shuffle, R.string.home_daily_shuffle_summary, "首页 每日 随机 daily shuffle", SettingsSearchTarget.HomeDisplay("home_top_actions")),
    SettingsSearchDefinition(R.string.settings_home_shortcuts, null, "首页 快捷按钮 艺术家 歌手 排序 隐藏", SettingsSearchTarget.HomeDisplay("home_shortcuts")),
    SettingsSearchDefinition(R.string.settings_home_features, null, "首页 功能块 宫格 排序 隐藏", SettingsSearchTarget.HomeDisplay("home_features")),
    SettingsSearchDefinition(R.string.settings_home_top_action_settings, null, "首页 顶栏 设置 统计 AI 快捷入口", SettingsSearchTarget.HomeDisplay("home_top_actions")),
    SettingsSearchDefinition(R.string.home_recent_played, R.string.settings_home_section_recent_playback_summary, "首页 最近播放 最近听过 历史", SettingsSearchTarget.HomeDisplay("home_recent_section_mode")),

    SettingsSearchDefinition(R.string.settings_auto_show_search_keyboard, R.string.settings_auto_show_search_keyboard_summary, "搜索 输入法 键盘 自动弹出", SettingsSearchTarget.Appearance("auto_show_search_keyboard")),
    SettingsSearchDefinition(R.string.settings_search_reopen_behavior, R.string.settings_search_reopen_behavior_summary, "搜索 搜索框 清空 保留 选择 上次", SettingsSearchTarget.Appearance("search_reopen_behavior")),
    SettingsSearchDefinition(R.string.settings_search_click_playback_mode, R.string.settings_search_click_playback_mode_summary, "搜索 点击 下一首 队列 替换", SettingsSearchTarget.Appearance("search_click_playback_mode")),
    SettingsSearchDefinition(R.string.settings_theme_mode, null, "主题 深色 浅色 跟随系统 外观", SettingsSearchTarget.Appearance("theme_mode")),
    SettingsSearchDefinition(R.string.settings_progressive_top_bar_blur, R.string.settings_progressive_top_bar_blur_summary, "顶栏 渐进 模糊 blur", SettingsSearchTarget.Appearance("progressive_top_bar_blur")),
    SettingsSearchDefinition(R.string.settings_language, null, "语言 language app language system 系统 英文 中文", SettingsSearchTarget.Appearance("language")),
    SettingsSearchDefinition(R.string.settings_app_icon, R.string.settings_app_icon_summary, "图标 启动器 传统 音符 海浪 icon anime loli traditional", SettingsSearchTarget.Appearance("app_icon")),
    SettingsSearchDefinition(R.string.settings_recents_icon_system_theme, R.string.settings_recents_icon_system_theme_summary, "最近任务 图标 系统主题 MIUI 方形 recents icon theme", SettingsSearchTarget.Appearance("app_icon")),
    SettingsSearchDefinition(R.string.settings_custom_launcher_icon, R.string.settings_custom_launcher_icon_summary, "图标 自定义 桌面 shortcut launcher", SettingsSearchTarget.Appearance("app_icon")),
    SettingsSearchDefinition(R.string.settings_app_wallpaper, R.string.settings_app_wallpaper_summary, "壁纸 图片 背景 模糊 毛玻璃 wallpaper", SettingsSearchTarget.Appearance("wallpaper")),
    SettingsSearchDefinition(R.string.settings_app_now_playing_flow_background, R.string.settings_app_now_playing_flow_background_summary, "首页 音乐库 艺术家 专辑 当前歌曲 流光 动态背景", SettingsSearchTarget.Appearance("app_now_playing_flow_background")),
    SettingsSearchDefinition(R.string.settings_system_bars_mode, null, "沉浸模式 全屏 状态栏 导航栏 隐藏 显示", SettingsSearchTarget.Appearance("system_bars")),
    SettingsSearchDefinition(R.string.settings_player_immersive_cover, R.string.settings_player_immersive_cover_summary, "沉浸 播放页 封面 全屏", SettingsSearchTarget.Appearance("player_immersive")),
    SettingsSearchDefinition(R.string.settings_player_page_style, R.string.settings_player_page_style_summary, "播放页 Apple Music 封面 歌词 样式", SettingsSearchTarget.Appearance("player_page")),
    SettingsSearchDefinition(R.string.settings_apple_music_player_immersive_cover, R.string.settings_apple_music_player_immersive_cover_summary, "Apple Music 正方形 1:1 沉浸 封面", SettingsSearchTarget.Appearance("player_apple_music_immersive_cover")),
    SettingsSearchDefinition(R.string.settings_player_show_total_duration, R.string.settings_player_show_total_duration_summary, "进度条 总时长 剩余时间 播放时间", SettingsSearchTarget.Appearance("player_show_total_duration")),
    SettingsSearchDefinition(R.string.settings_player_show_song_annotation, R.string.settings_player_show_song_annotation_summary, "播放页 歌曲注释 annotation", SettingsSearchTarget.Appearance("player_show_song_annotation")),
    SettingsSearchDefinition(R.string.settings_player_tap_seek, R.string.settings_player_tap_seek_summary, "进度条 点击 跳转 拖动", SettingsSearchTarget.Appearance("player_tap_seek")),
    SettingsSearchDefinition(R.string.settings_transport_button_outlines, R.string.settings_transport_button_outlines_summary, "播放页 控制 按钮 轮廓 外框", SettingsSearchTarget.Appearance("transport_button_outlines")),
    SettingsSearchDefinition(R.string.settings_player_landscape_style, null, "横屏播放 宽屏 歌词 CoverFlow MV 流光 经典分栏 铺满封面", SettingsSearchTarget.Appearance("player_landscape")),
    SettingsSearchDefinition(R.string.settings_player_clock_color, null, "时钟 横屏 封面取色 白色 浅灰色 clock color", SettingsSearchTarget.Appearance("player_landscape")),
    SettingsSearchDefinition(R.string.settings_beautiful_lyrics_background, R.string.settings_beautiful_lyrics_background_summary, "Apple Music 动态背景 歌词页 流光 取色", SettingsSearchTarget.Appearance("beautiful_lyrics")),
    SettingsSearchDefinition(R.string.settings_player_dynamic_flow, R.string.settings_player_dynamic_flow_summary, "Apple Music 流光 动态 背景 流动", SettingsSearchTarget.Appearance("player_dynamic_flow")),
    SettingsSearchDefinition(R.string.settings_apple_flow_speed, R.string.settings_apple_flow_speed_summary, "Apple Music 流光速度 动态背景 封面", SettingsSearchTarget.Appearance("apple_flow_speed")),

    SettingsSearchDefinition(R.string.settings_cover_media, R.string.settings_cover_media_summary, "封面 动态封面 MV 艺术家封面 影像", SettingsSearchTarget.CoverMedia("cover_media")),
    SettingsSearchDefinition(R.string.settings_dynamic_cover, R.string.settings_dynamic_cover_summary, "视频封面 动态封面 mp4 MV 文件夹", SettingsSearchTarget.CoverMedia("dynamic_cover")),
    SettingsSearchDefinition(R.string.settings_music_video_sync, R.string.settings_music_video_sync_summary, "MV 音乐视频 同步 静音", SettingsSearchTarget.CoverMedia("music_video")),
    SettingsSearchDefinition(R.string.settings_artist_cover_folder, R.string.settings_artist_cover_folder_summary, "艺术家 歌手 封面 动态封面 视频 图片目录", SettingsSearchTarget.CoverMedia("artist_cover_folder")),
    SettingsSearchDefinition(R.string.settings_artist_image_download, R.string.settings_artist_image_download_summary, "艺术家 图片 封面 自动下载 Last.fm Spotify 网易云", SettingsSearchTarget.CoverMedia("artist_image_download")),
    SettingsSearchDefinition(R.string.settings_artist_image_sources, R.string.settings_artist_image_sources_summary, "艺术家 图片 封面 来源 优先级 Last.fm Spotify 网易云 酷狗 QQ音乐", SettingsSearchTarget.CoverMedia("artist_image_sources")),

    SettingsSearchDefinition(R.string.settings_library_source, R.string.settings_library_source_summary, "音乐来源 音乐库来源 本地 Navidrome Emby 远程 曲库", SettingsSearchTarget.Library("library_source")),
    SettingsSearchDefinition(R.string.netease_playback_provider_title, R.string.netease_playback_provider_summary, "网易云 账号 歌单 收藏 搜索 播放解析 LX MusicFree 音源 插件", SettingsSearchTarget.Library("library_source")),
    SettingsSearchDefinition(R.string.settings_library_scan, R.string.settings_library_scan_summary, "音乐库 扫描 标签 搜索 艺术家 歌手", SettingsSearchTarget.Library("scan")),
    SettingsSearchDefinition(R.string.settings_scan_folders, R.string.settings_scan_folders_summary, "扫描 文件夹 USB 隐藏目录 存储权限", SettingsSearchTarget.Scan("scan_folders")),
    SettingsSearchDefinition(R.string.settings_full_tag_search, R.string.settings_full_tag_search_summary_on, "全字段 全标签 元数据 作曲 作词 注释 别名 标签", SettingsSearchTarget.Scan("scan_media_source")),
    SettingsSearchDefinition(R.string.folder_force_full_rescan, R.string.folder_force_full_rescan_summary, "强制重扫 全量扫描 标签 缓存", SettingsSearchTarget.Scan("scan_media_source")),
    SettingsSearchDefinition(R.string.settings_auto_scan_local_playlists, R.string.settings_auto_scan_local_playlists_summary, "自动扫描 本地歌单 m3u 播放列表", SettingsSearchTarget.Library("auto_scan_local_playlists")),
    SettingsSearchDefinition(R.string.settings_min_duration_filter, R.string.settings_min_duration_filter_summary, "扫描 最小时长 过滤 短音频", SettingsSearchTarget.Library("min_duration_filter")),
    SettingsSearchDefinition(R.string.settings_tag_ignore_case, R.string.settings_tag_ignore_case_summary, "标签 大小写 忽略 英文", SettingsSearchTarget.Library("tag_ignore_case")),
    SettingsSearchDefinition(R.string.settings_show_album_artists, R.string.settings_show_album_artists_summary, "艺术家 歌手 performer 专辑艺术家", SettingsSearchTarget.Library("show_album_artists")),
    SettingsSearchDefinition(R.string.settings_artist_separators, R.string.settings_artist_separators_summary, "艺术家 歌手 分隔符 feat 合作", SettingsSearchTarget.Library("artist_separators")),
    SettingsSearchDefinition(R.string.settings_artist_protected_names, R.string.settings_artist_protected_names_summary, "艺术家 歌手 不拆分 分隔符 保护名称", SettingsSearchTarget.Library("artist_protected_names")),
    SettingsSearchDefinition(R.string.settings_search_all_categories, R.string.settings_search_all_categories_summary, "搜索 所有 分类 艺术家 歌手 文件夹 作曲 流派 年份", SettingsSearchTarget.Library("search_all_categories")),
    SettingsSearchDefinition(R.string.settings_search_all_song_match_types, R.string.settings_search_all_song_match_types_summary, "搜索 所有 歌曲 专辑 元数据 歌词", SettingsSearchTarget.Library("search_all_song_match_types")),
    SettingsSearchDefinition(R.string.settings_song_rating_display_stars, R.string.settings_song_rating_display_stars_summary, "评分 星级 五星 列表", SettingsSearchTarget.Library("song_rating_display_stars")),
    SettingsSearchDefinition(R.string.settings_metadata_editor, null, "元数据 标签 编辑 ID3 FLAC MusicTag LunaBeat", SettingsSearchTarget.Library("tag_scraping")),
    SettingsSearchDefinition(R.string.settings_lyric_timing_editor, R.string.settings_editor_builtin_lyric_timing, "歌词 打轴 时间轴 LRC ELRC TTML 编辑器", SettingsSearchTarget.Library("tag_scraping")),

    SettingsSearchDefinition(R.string.settings_lyrics, R.string.settings_lyrics_summary, "歌词 逐字 翻译 音译 字体 对齐 大小 黑名单 歌词源", SettingsSearchTarget.Lyrics("lyric_basic")),
    SettingsSearchDefinition(R.string.settings_font_screen_title, R.string.settings_lyric_font, "字体 歌词字体 原文 翻译 CJK 西文 导入", SettingsSearchTarget.LyricFont),
    SettingsSearchDefinition(R.string.settings_lyric_plugin_sources, R.string.settings_lyric_plugin_sources_summary, "歌词 源 插件 导入 在线 匹配", SettingsSearchTarget.LyricPlugins),
    SettingsSearchDefinition(R.string.settings_lyric_line_blacklist, R.string.settings_lyric_line_blacklist_summary, "歌词 黑名单 过滤 行", SettingsSearchTarget.Lyrics("lyric_basic")),
    SettingsSearchDefinition(R.string.settings_lyric_word_seek, R.string.settings_lyric_word_seek_summary, "歌词 逐字 精确 定位 点击 跳转", SettingsSearchTarget.Lyrics("lyric_word_seek")),
    SettingsSearchDefinition(R.string.settings_lyric_touch_feedback, R.string.settings_lyric_touch_feedback_summary, "歌词 点击 按下 轮廓 泛光 水波纹 触控", SettingsSearchTarget.Lyrics("lyric_touch_feedback")),
    SettingsSearchDefinition(R.string.settings_mini_player_lyrics, R.string.settings_mini_player_lyrics_summary, "迷你歌词 小窗 翻译 音译 迷你播放器", SettingsSearchTarget.Lyrics("mini_lyrics")),
    SettingsSearchDefinition(R.string.desktop_lyric_status_bar_mode, R.string.desktop_lyric_status_bar_mode_summary, "桌面歌词 悬浮窗 状态栏 暂停隐藏 横屏隐藏 宽度 位置", SettingsSearchTarget.Lyrics("desktop_lyric")),
    SettingsSearchDefinition(R.string.settings_mini_player_swipe_to_open_player, R.string.settings_mini_player_swipe_to_open_player_summary, "迷你播放条 上滑 播放页 手势", SettingsSearchTarget.Lyrics("mini_player_swipe_to_open_player")),
    SettingsSearchDefinition(R.string.settings_mini_player_cover_rotation, R.string.settings_mini_player_cover_rotation_summary, "迷你播放器 封面 旋转", SettingsSearchTarget.Lyrics("mini_player_cover_rotation")),
    SettingsSearchDefinition(R.string.settings_mini_player_right_button, R.string.settings_mini_player_right_button_summary, "迷你播放条 右侧 下一首 队列", SettingsSearchTarget.Lyrics("mini_player_right_button")),
    SettingsSearchDefinition(R.string.settings_enable_lyricon, R.string.settings_enable_lyricon_summary, "Lyricon 外部歌词 翻译 音译", SettingsSearchTarget.Lyrics("lyricon")),
    SettingsSearchDefinition(R.string.settings_enable_super_lyric, R.string.settings_enable_super_lyric_summary, "超级歌词 状态栏 通知 横幅 蓝牙", SettingsSearchTarget.Lyrics("lyric_output")),
    SettingsSearchDefinition(R.string.settings_enable_coloros_lock_screen_lyric, R.string.settings_enable_coloros_lock_screen_lyric_summary, "ColorOS 锁屏岛 歌词 OPPO 一加", SettingsSearchTarget.Lyrics("coloros_lock_screen_lyric")),

    SettingsSearchDefinition(R.string.settings_audio, R.string.settings_audio_summary, "播放 无缝 gapless 淡入淡出 crossfade ReplayGain 随机 解码 焦点 蓝牙 伴奏", SettingsSearchTarget.Audio("audio_playback")),
    SettingsSearchDefinition(R.string.settings_usb_dac_mode, R.string.settings_usb_dac_mode_summary, "USB DAC 独占 高解析 输出 位深 采样率", SettingsSearchTarget.Audio("audio_output")),
    SettingsSearchDefinition(R.string.settings_decoder, R.string.settings_audio_decoder_auto_summary, "解码 FFmpeg 系统 音频焦点", SettingsSearchTarget.Audio("audio_system")),
    SettingsSearchDefinition(R.string.equalizer_screen_title, R.string.settings_audio_equalizer_summary, "均衡器 EQ 低音 高音 音效", SettingsSearchTarget.Equalizer("equalizer")),
    SettingsSearchDefinition(R.string.equalizer_surround_360_enable, R.string.equalizer_surround_360_summary, "360 环绕音 空间音频 spatial 音场", SettingsSearchTarget.Equalizer("equalizer")),
    SettingsSearchDefinition(R.string.equalizer_crossfeed_enable, R.string.equalizer_crossfeed_summary, "串音 耳机 crossfeed", SettingsSearchTarget.Equalizer("equalizer")),

    SettingsSearchDefinition(R.string.settings_integrations, R.string.settings_integrations_summary, "AI Anthropic DeepSeek MCP Last.fm 集成 API", SettingsSearchTarget.Integrations("ai")),
    SettingsSearchDefinition(R.string.settings_ai_interpretation, R.string.settings_openai_api_key_summary, "AI 供应商 模型 API Anthropic DeepSeek", SettingsSearchTarget.Integrations("ai")),
    SettingsSearchDefinition(R.string.settings_mcp_server, R.string.settings_mcp_server_summary, "MCP 服务 本地 端口 集成", SettingsSearchTarget.Integrations("mcp")),
    SettingsSearchDefinition(R.string.web_music_beta_title, R.string.web_music_beta_summary, "Web 网页 局域网 上传 播放", SettingsSearchTarget.Integrations("web_music")),
    SettingsSearchDefinition(R.string.settings_lastfm, R.string.settings_lastfm_summary, "Last.fm scrobble 听歌记录", SettingsSearchTarget.Integrations("lastfm")),
    SettingsSearchDefinition(R.string.settings_backup, R.string.settings_backup_summary, "备份 恢复 WebDAV 自动备份 播放记录 设置", SettingsSearchTarget.Backup("backup_settings")),
    SettingsSearchDefinition(R.string.settings_logs, R.string.settings_logs_summary, "日志 logcat 崩溃 警告 调试", SettingsSearchTarget.Logs),
    SettingsSearchDefinition(R.string.settings_maintenance, R.string.settings_maintenance_summary, "维护 清理 修复 重置", SettingsSearchTarget.Maintenance),
    SettingsSearchDefinition(R.string.about, null, "版本 更新 关于 开源协议 第三方许可", SettingsSearchTarget.About)
)

internal val settingsSearchCatalog: List<SettingsSearchDefinition> = run {
    val manual = manualSettingsSearchCatalog.associateBy { it.titleRes }
    val generatedIds = generatedSettingsSearchCatalog.map { it.titleRes }.toSet()
    manualSettingsSearchCatalog.filter { it.titleRes !in generatedIds } +
        generatedSettingsSearchCatalog.map { generated ->
            val original = manual[generated.titleRes]
            if (original == null || generated.sheet.isNotEmpty()) generated
            else generated.copy(target = original.target, keywords = original.keywords)
        }.distinctBy { Triple(it.titleRes, it.target, it.sheet) }
}
