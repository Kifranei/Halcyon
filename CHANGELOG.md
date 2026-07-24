# 1.2.2

From tag `1.2.1` to `1.2.2`.

中文更新日志
- 重构逐字歌词为 Compose 实现，新增 / 完善 Apple Music 风格动态歌词背景、逐词上浮、平滑重排和沉浸歌词页过渡；优化桌面歌词、状态栏歌词、TTML / ELRC 及歌词字体体验。
- 大幅完善播放页与动态封面：统一沉浸与非沉浸取色，修复动态封面匹配、切换与预览问题；原图预览支持缩放、跟手拖动、分享和保存，播放页 / 队列补全评分、收藏和播放模式等交互。
- 完善 MV 播放：预加载静音 MV，进入 MV 时暂停歌曲音频并使用视频声音，退出后恢复歌曲；修复切歌残留、横屏入口和进度同步问题。
- 首次扫描会询问是否启用全标签搜索；全标签模式可搜索完整元数据，快速模式改用基础媒体库扫描以提升大曲库速度，并避免冷启动或后台重复自动扫描。
- 设置搜索现在会索引具体的音乐库、艺术家、封面、分隔符、全标签搜索和歌词打轴设置；新增内置逐行 LRC 歌词打轴，可按播放进度打点、微调并写入歌曲内嵌歌词。
- 新增 Last.fm 历史：支持授权、完整历史同步、自动 Scrobble、离线缓存和本地 / Last.fm / 合并历史视图；凭据由 Android Keystore 加密且不写入备份。
- 新增交叉淡入淡出、紧凑 / 扩展桌面播放小组件、可配置的应用图标与桌面快捷方式。
- 优化专辑 / 艺术家元数据、封面预览、歌曲评分、歌单拖拽与排序、搜索滚动恢复、文件夹交互和听歌统计等音乐库体验。
- 改善 Android / HyperOS 系统适配：深色启动界面避免系统遮罩闪白，接入内存回收回调，修复启动恢复、预测性返回、蓝牙自动播放和多项播放器稳定性问题。

English Changelog
- Rebuilt word-by-word lyrics with Compose and added / refined Apple Music-style dynamic lyric backgrounds, word lift, smooth relayout, and immersive lyric transitions; desktop lyrics, status-bar lyrics, TTML / ELRC, and lyric-font behavior were also improved.
- Extensively refined the player and dynamic covers: immersive and non-immersive palette handling is now aligned, dynamic-cover matching / switching / preview issues are fixed, original-cover preview supports zoom, direct panning, sharing, and saving, and player / queue rating, favorite, and playback-mode interactions are completed.
- Improved MV playback: silent MVs are preloaded, entering MV pauses the song audio and uses the video audio, and leaving it resumes the track; fixed track-change residue, landscape entry, and progress synchronization.
- The first scan now asks whether to enable full-tag search. Full-tag mode searches complete metadata, while fast mode uses the basic media-library scanner for large libraries and avoids repeated automatic scans during cold start or in the background.
- Settings search now indexes individual library, artist, artwork, separator, full-tag-search, and lyric-timing settings. Added built-in line-by-line LRC timing with playback-position capture, fine adjustment, and embedded-lyric writing.
- Added Last.fm listening history with authorization, full-history sync, automatic scrobbling, offline cache, and Local / Last.fm / combined views. Credentials are encrypted with Android Keystore and excluded from backups.
- Added crossfade, compact / expanded playback widgets, configurable app icons, and launcher shortcuts.
- Improved album / artist metadata, cover preview, song ratings, playlist reordering and sorting, search scroll restoration, folder interactions, and listening statistics.
- Improved Android / HyperOS integration: a dark launch screen avoids bright flashes beneath system masks, memory-trim callbacks are handled, and startup restore, predictive back, Bluetooth auto-play, and player stability have been fixed in multiple places.

# 1.2.1

From tag `1.2.0` to `1.2.1`.

中文更新日志
- 重写播放进度交互，修复部分歌曲无法拖到末尾、MV 切歌后状态残留等问题，并完善动态封面与横屏播放体验。
- 播放页默认改为非沉浸圆角封面布局；非 1:1 封面按图片实际边界裁圆角，迷你歌词固定占位，避免 TTML 背景歌词挤压控制区。
- 完善歌词字体设置、罗马音/翻译显示和 TTML 解析；状态栏歌词长文本改为带间隔的连续循环滚动，合并副歌词时使用单空格。
- 新增西文字体、默认字体与中日韩默认字体的独立配置，并修复歌词非当前行字重、换行和分享文字显示问题。
- 优化艺术家页：艺术家封面按“自定义 → 独占专辑艺术家 → 独占歌曲艺术家 → 合作专辑艺术家 → 合作歌曲艺术家”选择。
- 完善文件夹层次结构：子文件夹长按支持完整操作菜单与置顶，桌面快捷方式使用专用层次结构图标。
- 切换歌曲、专辑、艺术家、文件夹、歌单及分类排序时立即更新列表，减少排序菜单点击后的卡顿感。
- 优化专辑发行方展示、歌单多选/拖拽、媒体通知歌词、远程音乐源与下载音质地址等细节，并修复多项播放器和设置页问题。
- 支持显示歌曲MV，请将”歌曲文件名-MV.mp4”或“歌曲文件名_MV.mp4”放到与歌曲同目录，播放到有MV的歌曲时候会显示MV按钮。

English Changelog
- Reworked playback seeking and fixed cases where some songs could not seek to the end, stale MV state after track changes, and several dynamic-cover and landscape-player issues.
- Made the non-immersive rounded-cover player layout the default. Non-square covers now round the actual artwork bounds, while mini lyrics keep a fixed viewport so TTML background lines do not push transport controls down.
- Improved lyric font settings, romanization/translation display, and TTML parsing. Long status-bar lyrics now loop continuously with a gap, and merged secondary lyrics use a single space.
- Added separate Western, default, and CJK default font settings, and fixed non-current lyric weight, wrapping, and lyric-share text rendering.
- Improved artist artwork selection with this priority: custom asset → sole album artist → sole song artist → collaborative album artist → collaborative song artist.
- Improved folder hierarchy actions: child folders now expose the full long-press menu and pinning, and hierarchy shortcuts use a dedicated icon.
- Made song, album, artist, folder, playlist, and category sorting update immediately after selection to reduce perceived UI stalls.
- Refined album publisher display, playlist multi-select/reordering, media-notification lyrics, remote music sources, download-quality URLs, and numerous player and settings details.
- Supports displaying the song's music video (MV). Please place "SongFileName-MV.mp4" or "SongFileName_MV.mp4" in the same directory as the song. When playing a song that has an MV, the MV button will be displayed.

# 1.2.0

From tag `1.1.97` to current `HEAD`.

中文更新日志
- 新增自定义艺术家封面文件夹，支持按艺术家名称匹配封面资源。
- 新增音乐库来源切换器，支持本地 / Navidrome / Emby，并扩展为多地址远程音乐源管理。
- 新增 WebDAV 接入音乐库来源，支持递归索引 WebDAV 音频并纳入歌曲、专辑、艺术家等库视图。
- 修复 Navidrome / Emby 大曲库只能加载部分歌曲的问题，远程曲库改为分页与完整加载策略。
- 支持远程 HTTP 音频读取内嵌歌词 / 标签头部缓存，改善 Navidrome / Emby 等远程歌曲内嵌歌词识别。
- 新增 Apple Music 风格动态流光背景，并加入低功耗可见性门控。
- 歌词更多菜单增加罗马音 / 注音显示位置设置，并优化菜单结构。
- 修复媒体通知歌词元数据补丁导致的歌词重载闪烁，并进一步平滑歌词换行与重排动效。
- 优化歌词插件搜索，并行化检索流程并增加超时控制。
- 新增软件均衡器能力，扩展参数 Q、音色、压缩器、立体声宽度、混响等 DSP 效果。
- 优化文件夹歌单分类页和详情页，支持多选、排序记忆、菜单跳转与封面。
- 新增全标签搜索开关，优化专辑艺术家 / 艺术家显示与搜索去重。
- 修复扫描 toast 重复弹出、隐藏播放页拦截返回键、163 key 解密结果显示等问题。
- 优化远程歌曲列表分页加载、歌词对唱显示、播放页和横屏页面细节。
- 打包字体去重，减小 APK 体积，并在 release APK 文件名中嵌入 git 短哈希便于溯源。
- 补全 RawS Music 开源引用与第三方许可信息。

English
- Added custom artist-cover folders with artist-name based cover matching.
- Added a library-source switcher for Local / Navidrome / Emby, then expanded it into multi-server remote source management.
- Added WebDAV as a music library source, including recursive WebDAV audio indexing for songs, albums, artists, and related library views.
- Fixed Navidrome / Emby large libraries only loading a partial song set by improving remote pagination and full-library loading.
- Added embedded lyric / tag-header caching for remote HTTP audio, improving embedded lyric detection for Navidrome / Emby and other remote songs.
- Added an Apple Music style flowing dynamic background with low-power visibility gating.
- Added romanization / pronunciation placement controls to the lyric menu and cleaned up the menu structure.
- Fixed lyric reload flicker caused by media-notification metadata patches and further smoothed lyric line wrapping / relayout animations.
- Optimized lyric plugin search with parallel lookup and timeout control.
- Expanded software equalizer support with parameter Q, tone, compressor, stereo width, reverb, and related DSP effects.
- Improved folder playlist category/detail pages with multi-select, sort persistence, menu navigation, and covers.
- Added a full-tag search toggle and improved album-artist / artist display and search deduplication.
- Fixed repeated scan toasts, hidden player pages intercepting back navigation, and missing 163 key decrypt result display.
- Improved remote song-list pagination, duet lyric display, player page details, and landscape playback details.
- Reduced APK size by deduplicating bundled fonts and embedded the git short hash in release APK filenames for traceability.
- Added RawS Music credits and third-party license references.
