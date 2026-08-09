# 1.2.5

From `1.2.4` to the latest `main` commit on 2026-08-09.

中文更新日志
- 更新 AndroidX Media3 至 `1.11.0`。
- 新增 Android 16 Live Update 歌词通知，支持原文/翻译/注音选择、封面、Promoted Ongoing Notification，以及逐字歌词实时更新。
- 新增小米 HyperOS 超级岛歌词，通过 Shizuku 接入 XMSF，支持可配置 Focus 歌词、封面、更新节流和桌面歌词重新同步，并避免影响媒体通知歌词与歌词页 seek。
- 新增 iOS 风格液态玻璃悬浮底栏；移除 Kyant Backdrop，玻璃表面迁移至 miuix blur，支持拖拽切换、阻尼回弹、高光折射、按压气泡、内阴影和色散效果。
- 修复底栏宽度塌陷、点击/滑动切换、指示气泡同步、拖拽回首页路由和从底栏进入设置页的标题间距；统一 GlassPill、MiniPlayer 与底栏的阴影和浮层高度，并放大 HyperOS 播放模式、随机和队列图标。
- 修复暂停、seek、逐字、逐行、后台/不可见、静态歌词和暂无歌词渲染，避免暂停或多行歌词错误全亮、重复推进和滚动错位。
- 恢复迷你播放条旧版播放图标，增加“播放条上滑进入播放页”开关，首页最近内容可在“最近听过/最近添加”之间切换；补充桌面小组件歌词和播放会话统计。
- 频谱频率刻度改为自适应，新增荣耀 HD Audio 播放支持。
- 完善音乐库多选拖拽、排序、文件夹歌单管理、批量置顶和置顶状态持久化；修复艺术家、专辑、流派、年份、作词家、作曲家、编曲家分类页多选置顶按钮点击区域问题。
- 优化评分筛选、行内随机、导航状态、搜索分类顺序、内容筛选和歌词标签去重；合并 `#390`、`#410`、`#432`、`#435`、`#437` 修复，并保留歌词/自定义标签元数据供筛选使用。
- 优化小米媒体岛分享、歌词分享卡片取色、歌词视频分享进度弹窗和全局拖拽音频 MIME 类型；恢复 Web 播放器和 Beautiful Lyrics 播放器封面显示。
- 完善 MV 浏览、专辑歌曲计数、MV 加载和歌词视频分享；保留远程音乐源切换，并更新设置分类和本地化文案。

English Changelog
- Updated AndroidX Media3 to `1.11.0`.
- Added Android 16 Live Update lyric notifications with original / translation / pronunciation selection, artwork, promoted ongoing notification support, and word-level updates.
- Added Xiaomi HyperOS Super Island lyric delivery through a Shizuku-backed XMSF bridge, with configurable Focus lyrics, artwork, throttled updates, and desktop-lyric resynchronization without disturbing media-notification lyrics or lyric-page seeking.
- Added an iOS-style liquid-glass floating bottom bar. Removed Kyant Backdrop and migrated glass surfaces to miuix blur, with drag-to-switch navigation, damped rubber-band motion, highlight refraction, press bubbles, inner shadows, and chromatic aberration.
- Fixed bottom-bar width collapse, click/slide switching, indicator synchronization, drag-to-home routing, and Settings title spacing; aligned GlassPill and MiniPlayer shadows with the bottom bar and enlarged HyperOS playback-mode, shuffle, and queue icons.
- Fixed pause, seek, word-by-word, multi-line, off-screen, static, and no-lyrics rendering so paused or multi-line lyrics do not highlight, advance, or scroll incorrectly.
- Restored the legacy mini-player play glyph, added the swipe-up-to-open-player setting, made the home recent section switchable between played and added songs, and added widget lyrics and playback-session statistics.
- Made the spectrum frequency scale adaptive and added Honor HD Audio playback support.
- Improved library multi-select drag, sorting, folder-playlist management, batch pinning, and pin persistence; fixed the multi-select pin hit area in artist, album, genre, year, lyricist, composer, and arranger category pages.
- Refined rating filters, inline shuffle, navigation state, search category ordering, content filters, and lyric-tag de-duplication; included fixes for `#390`, `#410`, `#432`, `#435`, and `#437`, while preserving lyric and custom-tag metadata for filtering.
- Improved Xiaomi media-island sharing, lyric-share-card palette handling, lyric-video progress presentation, and global drag payload MIME types; restored artwork in the web and Beautiful Lyrics players.
- Improved MV browsing, album track counts, MV loading, and lyric-video sharing; retained remote-provider switching and updated settings organization and localization.

Version
- Version name: `1.2.5`
- Version code: `33` (updated from `32`)

# 1.2.4

From `1.2.3` to `1.2.4`.

中文更新日志
- 完善本地 MV：支持配置独立 MV 文件夹，并按歌手与歌名、音频文件名及 `_MV` / `-MV` 后缀匹配 MP4、MKV、WebM 和 MOV；修复切歌后详情页 MV 残留、播放器重复创建、后台播放和歌词页被视频加载阻塞等问题。
- 加入网易云 MV 与 LunaBeat 偏移兼容：可从独立 `163 key`、Comment 或 Description 中读取 `mvid` 并跳转网易云 MV；支持导入 `mv_offsets.json`，分别校正不同 MV 的歌词/字幕时间。
- 信息页打开 MV 默认直接进入横屏；重做横屏交互，扩大进度、亮度和音量手势区域并将反馈条显示在操作手势的对侧；字幕设置改为半透明可滚动侧栏，并加入字幕翻译、双击播放暂停、自动隐藏控制、可拖动/锁定字幕、截图分享、KTV 歌词与伴奏测试。
- MV 支持手动进入画中画，播放期间切到后台也会自动进入 PiP；画中画与信息页横屏 MV 共用同一播放器、进度和播放状态，避免系统误控歌曲播放器造成二重奏；为 MV 分配独立媒体会话 ID，修复与歌曲播放会话冲突导致打开 MV 立即崩溃；画中画中只保留视频及已启用的普通字幕，退出后恢复完整控制层。
- 新增横屏播放样式、系统栏显示方式和保留系统栏占位设置；加入应用字体大小与界面缩放，改善车机、平板和超宽屏上的可读性。
- 新增可编辑的专辑介绍页：优先读写专辑目录 `album.nfo` 的 `<review>`，无写入权限时安全保存到应用内部；同时完善艺术家封面选择和发行时间降序规则。
- 更新桌面播放小组件：恢复并持久化封面，使用封面取色的模糊背景、实时计时和紧凑/展开布局；加入防止非 4×6 桌面网格裁切控制按钮的兼容布局。
- 改善交叉淡入淡出：支持恒定响度、线性、平滑和保持原音量曲线，避免淡入前段过静；修复局部渐变切歌卡顿及播放状态交接。
- 完善歌词与系统歌词：可隐藏“作词/作曲/词/曲”等额外信息，状态栏歌词支持独立颜色和字号，桌面歌词与状态栏歌词的宽度最低可调至 30%；优化歌词载入、翻译显示和长句布局。
- 修复 MediaInfo 跳转、WAV 目录迁移后的完整扫描、CUE 分轨乱码、频谱高采样率显示和超过 22 kHz 的频段；完善多音频流预览与导出。
- 新增局域网 Web 音乐服务 Beta，可在可信局域网内浏览、播放和上传音乐；当前版本没有访问密码，请勿在公共网络开启。
- 更新 Media3、Miuix、Lyrico 等依赖并拆分大型播放器、设置、扫描器和歌词解析模块，降低维护成本并补充回归测试。
- MKV、WebM 和 MOV 表示容器支持，实际视频仍由设备解码能力决定；H.264 High 10、部分 HEVC Main 10 / TrueHD 组合在不支持相应硬解的设备上仍可能无法播放。

English Changelog
- Expanded local MV support with configurable MV-only folders and artist/title, audio-file-name, `_MV`, and `-MV` matching for MP4, MKV, WebM, and MOV. Fixed stale detail-page entries after track changes, duplicate players, background playback, and video loading blocking the lyric page.
- Added NetEase MV and LunaBeat offset compatibility. `mvid` can be read from a standalone `163 key`, Comment, or Description, while imported `mv_offsets.json` entries adjust lyric/caption timing per MV.
- Detail-page MVs now open directly in landscape. Landscape interaction has larger seek/brightness/volume gesture regions with feedback shown opposite the gesture side. Caption settings use a translucent, scrollable side panel and include translation control, full-screen double-tap play/pause, auto-hidden controls, draggable/lockable captions, screenshot sharing, KTV lyrics, and accompaniment testing.
- MVs can enter picture-in-picture manually and automatically when a playing MV is sent to the background. PiP and the landscape detail MV now share the same player, progress, and playback state, preventing system controls from resuming the song player and causing doubled audio. Each MV now receives a distinct media-session ID, fixing an immediate crash caused by colliding with the song playback session. PiP keeps only the video and enabled regular captions, restoring the complete controls after return.
- Added selectable landscape playback styles, system-bar visibility/reserved-space behavior, app font sizing, and full interface scaling for car displays, tablets, and ultra-wide screens.
- Added a dedicated editable album-introduction page. Local albums prefer the `<review>` field in `album.nfo` and safely fall back to app storage when the folder is not writable. Artist-art selection and descending release-date sorting were also refined.
- Refreshed playback widgets with persisted artwork, blurred artwork-derived backgrounds, live elapsed time, compact/expanded layouts, and a compatibility layout for launcher grids that crop control outlines.
- Improved crossfade with equal-power, linear, smooth, and full-volume curves to avoid a nearly silent fade-in, while stabilizing local transition timing and playback handoff.
- Refined lyrics and system lyrics: optional filtering now covers composer/lyricist credit lines including short `词` / `曲` forms; status-bar lyrics have independent color and size controls, while desktop and status-bar lyric widths can be reduced to 30%; lyric loading, translations, and long-line layout are improved.
- Fixed MediaInfo launching, full rescans after WAV folder moves, CUE filename/tag decoding, high-sample-rate spectrum rendering above 22 kHz, and multi-stream preview/export.
- Added a Beta LAN Web music service for browsing, playback, and upload on trusted local networks. This release has no access password, so it must not be exposed to public networks.
- Updated Media3, Miuix, Lyrico, and related dependencies, split large player/settings/scanner/lyric-parser modules, and added regression coverage.
- MKV, WebM, and MOV support refers to their containers. Actual video playback still depends on the device decoder; H.264 High 10 and some HEVC Main 10 / TrueHD combinations can still fail on devices without compatible hardware decoding.

# 1.2.3

From `1.2.2` to `1.2.3`.

中文更新日志
- 大幅优化 MV 横屏体验：详情页 MV 与播放页静音 MV 的暂停/继续状态保持同步，避免后台重复播放和双重声音；完善返回、全屏、截图分享、字幕显示及安全区域避让。
- 完善横屏 KTV 歌词与普通字幕：过滤 `x-bg` 背景人声元数据，修复长句截断、对唱左右交替、间奏等待符、描边和歌词时间同步问题；CoverFlow 与详情页各自保留合适的视觉样式。
- 调整播放页布局与取色：恢复可选的封面取色文字/图标，默认使用深色流光背景；优化迷你歌词、封面和播放控制区的对齐，并合并平板信息胶囊及统一 ReplayGain 胶囊的半透明取色。
- 歌曲评分改为可直接点选星级并保存；未评分状态使用空心星，音乐库、播放页和标签编辑器保持一致。
- 重写听歌历史的本地持久化与删除流程：为每条记录保留稳定 ID、使用原子写入，并允许本地隐藏错误的 Last.fm 缓存记录，避免同步后重新出现。
- 修复内存紧张后封面被错误降级为默认封面、日志短暂显示 0 条的问题；缓存释放后会重新解析封面，日志读取失败时保留上次成功内容。
- 完善内置频谱与外部频谱入口，并加入本地音频格式转换、多音频流导出和 CUE 整轨分轨工具。

English Changelog
- Substantially refined landscape MV playback: detail-page MVs and the player's silent MV stay synchronized through pause/resume, preventing duplicated background playback and double audio; back/full-screen behavior, screenshot sharing, subtitles, and display-cutout handling are improved.
- Refined landscape KTV lyrics and regular subtitles: `x-bg` backing-vocal metadata is filtered, and long-line clipping, alternating duet sides, interlude wait marks, outlines, and lyric timing are corrected. CoverFlow and the detail MV keep their appropriate visual styles.
- Adjusted player layout and color handling: optional cover-derived text/icon color is restored while the default flowing background remains dark; mini lyrics, artwork, and controls align more cleanly, and tablet information capsules now share consistent translucent ReplayGain coloring.
- Song rating is now selected directly with stars and saved explicitly. Unrated songs use outlined stars consistently in the library, player, and built-in tag editor.
- Reworked local listening-history persistence and deletion: every record has a stable ID, writes are atomic, and invalid cached Last.fm entries can be hidden locally so they do not return after synchronization.
- Fixed artwork incorrectly falling back to placeholders after memory pressure and logs briefly showing zero entries; artwork is resolved again after cache eviction and log reads retain the last successful result on failure.
- Refined the built-in spectrum viewer and external spectrum launchers, and added local format conversion, multi-stream audio export, and CUE album splitting tools.

# 1.2.2

From tag `1.2.1` to `1.2.2`.

中文更新日志
- 重构逐字歌词为 Compose 实现，并完善 Apple Music 风格动态歌词背景、逐词上浮、平滑重排和沉浸歌词页过渡；优化桌面歌词、状态栏歌词、TTML / ELRC 及歌词字体体验。
- 大幅完善播放页与动态封面：统一沉浸与非沉浸取色，修复动态封面匹配、切换与预览问题；原图预览支持缩放、跟手拖动、分享和保存，播放页 / 队列补全评分、收藏和播放模式等交互。
- 完善 MV 播放：预加载静音 MV，进入 MV 时暂停歌曲音频并使用视频声音，退出后恢复歌曲；修复切歌残留、横屏入口和进度同步问题。
- 首次扫描会询问是否启用全标签搜索；全标签模式可搜索完整元数据，快速模式改用基础媒体库扫描以提升大曲库速度，并避免冷启动或后台重复自动扫描。
- 设置搜索现在会索引具体的音乐库、艺术家、封面、分隔符、全标签搜索和歌词打轴设置；内置逐行 LRC 歌词打轴可按播放进度打点、微调并写入歌曲内嵌歌词。
- 完善 Last.fm 历史的授权、完整历史同步、自动 Scrobble、离线缓存和本地 / Last.fm / 合并历史视图；凭据由 Android Keystore 加密且不写入备份。
- 完善交叉淡入淡出、紧凑 / 扩展桌面播放小组件、可配置的应用图标与桌面快捷方式。
- 优化专辑 / 艺术家元数据、封面预览、歌曲评分、歌单拖拽与排序、搜索滚动恢复、文件夹交互和听歌统计等音乐库体验。
- 改善 Android / HyperOS 系统适配：深色启动界面避免系统遮罩闪白，接入内存回收回调，修复启动恢复、预测性返回、蓝牙自动播放和多项播放器稳定性问题。

English Changelog
- Rebuilt word-by-word lyrics with Compose and refined Apple Music-style dynamic lyric backgrounds, word lift, smooth relayout, and immersive lyric transitions; desktop lyrics, status-bar lyrics, TTML / ELRC, and lyric-font behavior were also improved.
- Extensively refined the player and dynamic covers: immersive and non-immersive palette handling is now aligned, dynamic-cover matching / switching / preview issues are fixed, original-cover preview supports zoom, direct panning, sharing, and saving, and player / queue rating, favorite, and playback-mode interactions are completed.
- Improved MV playback: silent MVs are preloaded, entering MV pauses the song audio and uses the video audio, and leaving it resumes the track; fixed track-change residue, landscape entry, and progress synchronization.
- The first scan now asks whether to enable full-tag search. Full-tag mode searches complete metadata, while fast mode uses the basic media-library scanner for large libraries and avoids repeated automatic scans during cold start or in the background.
- Settings search now indexes individual library, artist, artwork, separator, full-tag-search, and lyric-timing settings. The built-in line-by-line LRC timing tool captures playback positions, supports fine adjustment, and writes embedded lyrics.
- Refined Last.fm listening-history authorization, full-history sync, automatic scrobbling, offline cache, and Local / Last.fm / combined views. Credentials are encrypted with Android Keystore and excluded from backups.
- Refined crossfade, compact / expanded playback widgets, configurable app icons, and launcher shortcuts.
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

From `1.1.97` to current `HEAD`.

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
