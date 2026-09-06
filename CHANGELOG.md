# 1.2.8

From `1.2.7` to `1.2.8`.

中文更新日志
- 新增可选的「所有文件访问权限」入口，方便完整扫描和编辑本地音频；快速增量扫描加入轻量文件内容指纹，外部编辑歌曲标签但保留原修改时间时也能识别变化，避免继续沿用旧缓存。
- 非全标签扫描改为按系统媒体库收录音频（扩展名与文件大小），不再因时长未写入或文件对应用不可见而漏歌。
- 列表、播放页、通知和更多菜单统一按当前歌曲解析封面，同一专辑不同内嵌封面不再串图。
- 音乐库支持单行列表、双列/多列列表和封面网格，可通过双指缩放切换布局，并按手机/平板分别设置封面网格列数；切换布局会尽量保持当前位置，侧边快速定位条在各布局中都支持拖动。
- 双列歌曲标题支持来回滚动显示；可从歌曲名识别 `feat.` / `ft.` / `featuring` 艺术家，并改进歌曲、歌单和文件夹歌单的排序、筛选、范围选择及拖拽操作。
- 艺术家图片支持 Last.fm、Spotify 和网易云来源、独立优先级与自动下载策略；语言、图片地区和来源分开保存，精确匹配失败时再回退到不区分大小写，并提供缓存清理和占位图过滤。艺术家简介下载默认改为所有网络。
- WebDAV 目录支持搜索、收藏整个目录和按目录创建歌单；远程目录加载、分页和 FLAC 元数据处理更稳定。LX 源导入会识别网页、JSON 配置和网盘分享页并给出明确提示。
- 播放队列保留重复歌曲的实际队列位置，恢复队列和跨来源跳转更可靠；播放/暂停状态增加异步命令确认与乐观状态投影，修复按钮图标反转、暂停后继续反向切换等问题。
- 蓝牙音频断开和系统音频路由丢失时会按设置暂停播放，连接后可自动播放；交叉淡入淡出、系统 Dolby/FFmpeg 自动解码、音频输出状态和投放设备同步得到强化。
- 重做歌词解析和打轴流程，支持更完整的 LRC/ELRC/TTML 处理、逐词时间轴、内嵌歌词与旁车文件导出；歌词页和非沉浸迷你歌词恢复羽化、逐字推进、弹簧换行、偏移与对齐过渡。罗马音 + 原词 + 翻译时，原词与上方罗马音、下方翻译的垂直间距对齐（`#602`）。
- ColorOS Live Lyrics Bridge 4.0 的锁屏歌词发送改为跟随当前 MediaItem，换曲清理旧歌词并保留一次兼容重发；Android 16 实时活动、桌面歌词、状态栏歌词和小米超级岛的同步与失败重试更稳定。
- 底栏统一接入普通、悬浮和液态玻璃样式，支持圆角和液态玻璃参数自定义；系统栏隐藏/显示不再留下错误占位，ColorOS 手势栏也保持底栏抬高，底栏与迷你播放条保留连续收缩动画，收缩态播放条贴住两侧按钮。
- 歌曲评分、定时关闭、变速变调、歌词偏移、音频工具和歌曲标签入口统一使用 Miuix BottomSheet 风格，整理卡片边界、滑块、确认按钮和底部留白；播放页与列表操作菜单支持独立排序和隐藏。
- ZIP 备份可包含自定义字体、歌词源插件、音乐库展示设置和更多应用数据，并支持按分类恢复；维护页补充缓存清理与恢复默认配置。
- 日志页直接读取当前进程 logcat，导出报告自动附带应用/设备信息、`getprop` 与可读取的 `build.prop`，便于提交可复现的播放、歌词和扫描问题。
- 增加 Android TV 入口和本地 MV 画中画/横屏体验，艺术家、专辑、听歌统计和封面加载继续采用延迟解析与缓存，降低大曲库打开时的卡顿。

English Changelog
- Added an optional “All files access” entry for complete local-library scanning and audio-tag editing. Fast incremental scans now use a lightweight content fingerprint, so tag edits that preserve the file timestamp still invalidate stale cache rows.
- Non-full-tag scans now keep system MediaStore audio by extension and file size, so tracks are no longer dropped when duration is missing or the file is hidden from the app process.
- Artwork is resolved per current song across lists, the player, notifications, and more menus, so mixed embedded covers in one album no longer share a single picture.
- The library now supports single-column list, two-/multi-column rows, and cover grids with pinch-to-switch layouts and separate phone/tablet grid-column settings. Layout changes preserve the current position when possible, and the side fast-index bar remains draggable in every layout.
- Long titles in the two-column list can marquee, and `feat.` / `ft.` / `featuring` artists can be extracted from song titles. Sorting, filtering, range selection, and drag actions are refined across songs, playlists, and folder playlists.
- Artist artwork supports Last.fm, Spotify, and NetEase sources with independent priority and download policies. Language, image region, and source are stored separately; exact-case matching is preferred before case-insensitive fallback, with cache cleanup and placeholder filtering. Artist-biography downloads now default to all networks.
- WebDAV directories support search, collecting a whole directory to Favorites, and creating a playlist from a directory. Remote loading, pagination, and FLAC metadata handling are more reliable. LX imports now explain when a URL is an HTML page, JSON configuration, or cloud-share page instead of a JavaScript source.
- Queue entries retain their actual position even when the same song appears more than once. Queue restoration and cross-source navigation are more reliable, while asynchronous transport acknowledgement and optimistic projection fix play/pause glyph inversion and repeated reverse toggles.
- Bluetooth audio disconnects and lost system audio routes can pause playback according to the setting, while reconnecting can auto-play. Crossfade handoff, system Dolby/FFmpeg auto-decoding, realtime output status, and casting-device synchronization are improved.
- Reworked lyric parsing and timing flows with broader LRC/ELRC/TTML handling, word timing, embedded lyrics, and sidecar export. Lyric pages and non-immersive mini lyrics restore feathered word progression, spring line-wrap transitions, offset control, and alignment animation. Romanization-above and translation-below now share the same gap from the original line (`#602`).
- ColorOS Live Lyrics Bridge 4.0 now publishes lock-screen lyrics through the current MediaItem, clears stale lyrics on track changes, and keeps one compatibility republish. Android 16 Live Updates, desktop/status-bar lyrics, and Xiaomi Super Island synchronization and retry handling are more robust.
- Bottom docks now share Normal, Floating, and Liquid Glass implementations with configurable corner radius and Liquid Glass parameters. System-bar hide/show no longer leaves incorrect reserved space, ColorOS gesture-bar layouts keep the dock lifted, and the compact mini-player stays adjacent to the side buttons during the collapse animation.
- Rating, sleep timer, speed/pitch, lyric-offset, audio-tool, and tag-entry sheets now follow the Miuix BottomSheet presentation with cleaner card boundaries, sliders, confirmation buttons, and spacing. Player and list action menus can be independently reordered or hidden.
- ZIP backups can include custom fonts, lyric-source plugins, library-presentation settings, and more app data, with category-based restore. Maintenance adds cache cleanup and restore-defaults controls.
- The log screen reads the current process logcat directly. Exported reports include app/device context, `getprop`, and readable `build.prop` files to make playback, lyric, and scan reports self-describing.
- Added Android TV entry points and improved local-MV picture-in-picture/landscape handling. Artist, album, listening-statistics, and artwork screens use deferred parsing and caching to reduce large-library startup work.

Version
- Version name: `1.2.8`
- Version code: `36`

# 1.2.7

From `1.2.6` to `1.2.7`.

中文更新日志
- 设置下拉项不再用「当前：xxx」当说明，改为功能注释；「保留隐藏系统栏的占位」默认关闭。搜索跳转按条目键打开对应三级页并滚到那一行（如 Apple Music 流光速度），对齐小米设置的 preference key 定位。
- 设置搜索会记下历史；点结果跳到那一条设置并高亮，不再滚到整张卡片或同页另一块。歌曲信息修改时间用系统 utime 写入（`#581`）。
- 传记源菜单改为左右分栏：语言名（English / 日本語）左对齐，右侧用维基 / Last.fm / 网易云图标切换来源，不再按 VPN 自动换源（`#579`）。
- 歌词来源优先级用右侧复选框开关，不再用滑动开关（`#580`）。
- 从播放页进入艺术家 / 专辑后再返回，直接回到播放页，不再闪一下底下的列表（`#564`）。
- 艺术家传记语言与艺术家图片地区分开保存，互不影响（`#578`）。
- 底栏「设置」改为一级页：选中后保留底栏，不再当成叠在首页上的二级页。从设置长按迷你条跳回歌曲列表后，设置按钮仍可切换回去。
- 歌曲信息条目和播放列表顶栏可在设置里排序、隐藏；点击修改时间可编辑文件时间。
- 列表可在单行 / 多行 / 网格间切换。开头空白歌词支持拖动跳转。沉浸歌词标题下移，不再压在封面上。
- ZIP 备份会打包自定义字体，恢复时写回本机并保留系统字体路径；播放页迷你歌词不再为未唱到的 x-bg 预留下空白行。
- 艺术家封面：文件夹匹配跟随「标签忽略大小写」；下载源优先精确大小写并跳过占位图；长按预览当前显示的图，下载图标题带上来源。专辑分类进详情不再先转圈；听歌统计最爱专辑用原图。
- 歌词来源可单独开关并修正文案；听歌统计阈值改为 0–100% / 0–6 分钟滑块；歌曲信息显示播放次数和听歌时长。
- 日志页改为直接读取本进程 logcat（不再落盘 TSV、取消 3000 条上限）。导出完整 logcat，清空只清界面快照。
- 播放页会读取歌曲目录里的 cover.jpg / folder.jpg 等封面。自动解码时系统有杜比解码器走系统，没有才回退 FFmpeg，避免 FFmpeg 音量偏小、放不出 6 声道。
- LX 源导入会拒绝网页 HTML（提示用 .js 直链）；在线歌词覆盖更多平台；下载会写入封面和歌词。介绍页会抽取 latest.js，青听 JSON/网盘分享页给出明确错误；聚合 API 源的 303 跳转和请求体解析已对齐。
- 新增统一「投放设备」页：集中提供系统音频输出、Chromecast 和 DLNA MediaRenderer；DLNA 使用应用内局域网媒体服务投放当前歌曲，并同步投放状态。
- 播放页新增流光、波形和分段刻度进度条，以及全宽流光曲线 / RawS 镜像频谱可视化；可视化样式统一收进设置，Apple Music 播放页保持原本布局（`#539`）。
- Apple Music 流光增加独立速度调节；Beautiful Lyrics 补充速度、亮度和模糊调节。当前歌曲流光可延伸到首页、音乐库和列表页，并与沉浸播放页 / 歌词页统一坐标、取色和过渡（`#522`、`#551`）。
- 封面改为按当前歌曲解析：同一专辑含多张内嵌封面时，播放页、迷你播放条、更多菜单、通知、锁屏与封面取色保持一致；补充旧版 MediaSession / OEM 锁屏封面兼容（`#405`、`#514`、`#541`）。
- 新增实时音频输出面板，显示音源、实际播放链、重采样状态与输出设备；原有长按音频面板 / 系统输出选择手势与信息胶囊切换开关互不冲突（`#393`、`#507`）。
- 均衡器新增独立总增益，在全部软件音效之后以平滑增益统一调整音量，并提示正增益的峰值限制与削波风险。
- 频谱查看器补充 5 kHz / 10 kHz / 15 kHz / 20 kHz 频率刻度，优化高采样率频谱布局并保留 Aspect Pro / Kaspek 外部入口。
- 完善交叉淡入淡出：提前把界面交给已经淡入的下一首，去除切换点的暂停与爆音，稳定长时长交叉区间，并在暂停状态切歌时继续保持暂停（`#403`、`#512`、`#513`）。
- 播放页音质 / 音频信息 / 输出设备胶囊可分别显示；ReplayGain 可合并进音频信息或作为独立胶囊，胶囊字体跟随播放页字体。Hi-Res 封面标识默认关闭（`#393`）。
- 播放页和歌曲列表的「更多操作」支持分别排序、隐藏和恢复默认布局；封面长按预览新增独立开关（`#508`、`#549`）。
- 播放页迷你歌词新增独立缩放、字号、行距与对齐设置；暂无歌词保持居中，修复 00:00 歌词顶边、滚动越界和回退抖动（`#301`）。未唱到的 x-bg 和声不再预留下空白行。
- 内置 LunaBeat TTML Hub 歌词源，支持搜索高精度逐拍 TTML；Lyrico 匹配结果显示封面，并完善插件运行时与来源导入（`#506`）。
- 歌词点击支持逐字精确定位与连续时间跳转，新增可关闭的扩散轮廓反馈；开头模板歌词按连续进度 seek，避免短标题只能跳到 00:00（`#548`）。
- 开头空白可用模板显示歌曲名、艺术家、专辑、流派、年份等元数据；完善长音高亮、Apple 风格间奏等待符、暂停时仅高亮当前句和沉浸歌词区域横滑切歌（`#516`、`#517`、`#518`、`#551`）。
- 歌词样式中的非当前歌词模糊同步到设置页；桌面歌词修复逐字推进卡顿、锁定触控死区、宽度与封面内容色同步，SuperLyric 增加失败重试（`#509`、`#510`、`#515`、`#536`、`#547`）。
- LX 在线搜索增加网易云、QQ 音乐、酷狗和咪咕横向来源切换，并完善加密 LX 用户源运行时；修复酷狗结果无法播放、网易云结果缺少封面、返回后退出 LX 页和冗余来源提示（`#201`、`#540`）。
- WebDAV 自动备份会发现并提示较新的云端备份，可预设默认恢复分类；本地 / WebDAV 备份新增艺术家与专辑置顶等音乐库展示设置（`#345`）。
- 歌单与文件夹歌单的星级 / 红心筛选新增独立开关；音乐库增加列表 / 网格布局并在切换时保持当前位置，修复文件夹歌单排序跳到底部（`#267`、`#374`、`#530`、`#551`）。
- 搜索结果新增插入下一首、追加队尾或替换队列三种播放策略；修复重复条目身份、播放后键盘未收起和 LX 播放页返回链路（`#543`、`#546`）。
- 听歌统计可分别设置计次所需的播放比例与时长；修复日历切换时封面 / 全局图片资源异常，曲库分析图例支持长按查看对应歌曲（`#172`、`#521`）。
- 点击通知或锁屏媒体卡片可按设置直接进入播放页；修复锁屏、第三方灵动岛等 MediaSession 控制切歌卡住或上一首被解释为重播当前曲，并保持暂停切歌不自动播放（`#415`）。
- 艺术家传记增加网易云与 Wikipedia 跳转，国内网络下可回退加载；匹配优先精确大小写并在无精确结果时回退到不区分大小写（`#505`、`#545`）。
- 首页 AI 播放助手默认开启并显示实际曲库歌曲数，卡片改为浅蓝 / 浅绿渐变；设置向导流光与关于页统一，改善浅色 / 深色标题对比度和悬浮底栏高亮状态（`#550`）。
- 修复「全部随机」只改队列未切换播放模式、继续播放来源判断、最近听过手动播放来源、液态玻璃高亮与底栏状态、封面网格清晰度和布局切换定位等问题（`#519`、`#520`、`#542`、`#550`）。

English Changelog
- Dropdown settings use descriptive summaries instead of “Current: …”, and hidden system-bar space reservation is off by default. Search opens the matching tertiary page and scrolls to that row (for example Apple Music flow speed), following Xiaomi Settings’ preference-key jump.
- Settings search stores history and jumps to the matched preference instead of the whole card. Song-info modified time uses utime so saving no longer fails on scoped storage (`#581`).
- The biography source sheet uses left-aligned language names with Wikipedia / Last.fm / NetEase icons, and source choice is manual instead of VPN-based (`#579`).
- Lyric source priority uses checkboxes on the right instead of switches (`#580`).
- Returning from the artist or album page opened from the player goes straight back to the player instead of flashing the page underneath (`#564`).
- Artist biography language and artist-image region are stored separately and no longer affect each other (`#578`).
- Dock Settings is now a first-level tab: the bottom bar stays selected on Settings instead of pushing it as a secondary page. Long-pressing the mini player from Settings still lets the Settings tab switch back from the song list.
- Song-info fields and the queue toolbar can be reordered or hidden in Settings. Tapping Modified time edits the file timestamp.
- Song lists cycle list / multi-row / grid. Opening lyrics can be dragged to seek. Immersive lyric titles sit below the cover.
- ZIP backups pack custom fonts and restore them locally while keeping system font paths. Player mini lyrics no longer reserve empty x-bg rows.
- Artist covers follow the library ignore-case setting, skip placeholder download images, preview the currently visible image, and show the download source. Album-category details no longer flash a spinner; analytics favorite albums use original artwork.
- Lyric sources can be enabled independently. Play-count thresholds are 0–100% / 0–6 minute sliders. Song info shows play count and listen time.
- The log screen now reads this process logcat directly (no TSV snapshot, no 3000-entry cap). Export dumps full logcat.
- The player reads folder sidecar covers such as cover.jpg. Auto decoder uses the system Dolby decoder when present and falls back to FFmpeg only when the device has none, so 6-channel output is preserved.
- LX source import rejects HTML pages, online lyrics cover more platforms, and downloads embed cover art and lyrics.
- Added a unified Casting Devices page for system audio routes, Chromecast, and DLNA MediaRenderers. DLNA serves the current track through an in-app local-network media server and reports active casting state.
- Added glow, waveform, and segmented progress styles plus full-width flowing-curve / RawS mirrored-spectrum visualizers. Visualizer selection now lives in settings while the Apple Music player keeps its original layout (`#539`).
- Added an independent Apple Music flow-speed control and Beautiful Lyrics speed, brightness, and blur controls. The current-track flow can extend across Home, Library, and list pages with shared coordinates, palette, and transitions on immersive player / lyrics pages (`#522`, `#551`).
- Artwork is resolved per current track: albums containing multiple embedded covers now stay consistent across the player, MiniPlayer, action sheets, notifications, lock screen, and palette extraction. Legacy MediaSession / OEM lock-screen artwork compatibility is included (`#405`, `#514`, `#541`).
- Added a realtime audio-output panel for source format, effective playback chain, resampling state, and output device. Existing long-press audio-panel / system-route gestures remain independent from the information-capsule cycling option (`#393`, `#507`).
- Added an independent equalizer master gain applied smoothly after all software effects, with peak-limiter and clipping guidance for positive gain.
- Added 5 kHz / 10 kHz / 15 kHz / 20 kHz frequency marks to the spectrum viewer, refined high-sample-rate layout, and retained Aspect Pro / Kaspek launchers.
- Refined crossfade to hand the UI to the incoming track as it fades in, remove the pause/click at the transition boundary, stabilize long overlap intervals, and preserve the paused state when skipping (`#403`, `#512`, `#513`).
- Quality, audio-information, and output-device capsules can be shown independently. ReplayGain may be merged into audio details or shown as a separate capsule, capsule typography follows the player font, and the Hi-Res cover badge is off by default (`#393`).
- Player and list action menus can be independently reordered, hidden, or reset. Artwork long-press preview now has its own switch (`#508`, `#549`).
- Added independent scale, font-size, spacing, and alignment controls for player mini lyrics. No-lyrics content remains centered, and 00:00 top alignment, overscroll, and backward-jump issues are fixed (`#301`). Inactive x-bg backing vocals no longer reserve empty rows.
- Bundled a LunaBeat TTML Hub source for high-precision beat-timed TTML. Lyrico matches now show artwork, and plugin runtime / source importing is more complete (`#506`).
- Lyric taps support exact word timing and continuous timestamp seeking with an optional expanding outline response. Opening-template lyrics use continuous seeking so short titles no longer remain stuck at 00:00 (`#548`).
- Opening gaps can show title, artist, album, genre, year, and other metadata through a template. Sustained-word highlights, Apple-style interlude wait marks, paused current-line-only highlighting, and optional lyric-area swipe-to-skip are refined (`#516`, `#517`, `#518`, `#551`).
- Non-current lyric blur is synchronized with the Settings page. Floating lyrics fix word-progress stutter, locked touch dead zones, width / artwork-content-color sync, and SuperLyric gains retry handling (`#509`, `#510`, `#515`, `#536`, `#547`).
- LX online search now switches horizontally between NetEase Cloud Music, QQ Music, KuGou, and Migu, with expanded encrypted LX user-source runtime support. Fixed unplayable KuGou results, missing NetEase artwork, incorrect back navigation, and redundant source notices (`#201`, `#540`).
- WebDAV automatic backup detects newer cloud backups and can remember default restore categories. Local / WebDAV backups now include library-presentation state such as pinned artists and albums (`#345`).
- Added independent rating / favorite filter switches to playlist and folder-playlist pages. The library gains list / grid layouts that keep the current position, and folder-playlist sorting no longer jumps to the bottom (`#267`, `#374`, `#530`, `#551`).
- Search results can insert after the current item, append to the queue, or replace the queue. Fixed duplicate queue-entry identity, keyboards remaining open after playback, and LX player back navigation (`#543`, `#546`).
- Listening-count percentage and elapsed-time thresholds are configurable. Fixed calendar navigation causing artwork / global-image loss, and analytics legends can be long-pressed to inspect matching songs (`#172`, `#521`).
- Notification or lock-screen media cards can open the player directly. Fixed lock-screen and third-party Dynamic Island MediaSession controls freezing on skip or treating Previous as replay-current, while paused skips remain paused (`#415`).
- Artist biographies add NetEase and Wikipedia destinations, with fallback loading on mainland networks. Matching prefers exact case and falls back to case-insensitive results when needed (`#505`, `#545`).
- The Home AI playlist assistant is enabled by default, shows the actual library track count, and uses a light-blue / light-green gradient. Setup-wizard flow now matches the About page, with improved light / dark title contrast and floating-dock selection state (`#550`).
- Fixed Shuffle All changing only the queue, continue-playback source detection, manual Recent Plays source tracking, Liquid Glass highlights / bottom-dock state, grid artwork sharpness, and layout-switch positioning (`#519`, `#520`, `#542`, `#550`).

Version
- Version name: `1.2.7`
- Version code: `35`

# 1.2.6

From `1.2.5` to `1.2.6`.

中文更新日志
- 伴奏不再打包人声分离模型，改回实时中央声道衰减。平板 Apple Music 收起歌词时封面按剩余高度约束，不再盖住歌名。无损胶囊改用 Apple Music 波形标，Hi-Res 封面角标默认用内置金标。歌曲列表杜比角标改用 Apple Music 的双 D 标志。
- 动态封面不再先清空再扫盘：静态封面一直垫底，视频出第一帧再淡入；查找改为索引缓存，避免 SAF/大目录 listFiles 卡到十几秒并闪一下。
- 首次打开会进入全屏流光设置向导（miuix-blur 背景，风格对齐系统开机引导）。播放页杜比胶囊改用 Apple Music 的 Dolby Atmos 标志。
- 播放页背景选项改为短名「Apple Music / Beautiful Lyrics」；选中 Apple Music 后才出现「动态效果」开关。外观与主页拆成主题、系统栏、壁纸、播放页、列表等三级页。「封面与影像」改名为「封面与 MV」。传记地区改为国家名。播放列表「来源」增加链接图标。日志页背景与其他设置页对齐。
- 设置向导底部「下一步」避开迷你播放条。CoverFlow / MV 横屏作为默认样式时不再叠一层返回到自适应播放页。Apple Music 平板横屏改为收起时封面居中、展开时左封面右歌词。播放页时间/音质胶囊跟随西文字体，底部「词 / 播放模式 / 队列」图标加大。
- 播放页音质胶囊改为 Apple Music / Flamingo 风格：波形图标 +「无损 / Hi-Res」，不再用 ∞ Apple Lossless。
- 播放页专辑封面任意位置下拉即可最小化，不再只认顶部一小条。
- 艺术家封面、动态封面、MV 归到同一级「封面与影像」设置；扫描菜单不再夹艺术家封面目录。
- 专辑/艺术家/文件夹等列表在内容算完前显示加载，不再先闪「未找到」或点进去像卡住。
- 开启 Apple Music 播放页后，平板横屏使用封面居中的 AM 布局，不再沿用左封面右歌词。
- 设置增加向导；搜索命中后跳到整块开关卡片并闪两下（例如「播放页显示歌曲注释」）。
- 按最新 ColorOS Live Lyrics Bridge 4.0 播放器接入协议补回锁屏岛歌词：首曲首次发布前把完整 `lyricInfo` 写入当前 MediaItem，换曲立即清理旧歌词，800ms 仅做一次兼容性重发，开启锁屏歌词时 TITLE/ARTIST 保持歌曲身份。模块模式额外提供 `rawLyric` 逐字时间轴与同时间戳翻译行。
- 隐藏状态栏/导航栏时 BottomSheet 不再把系统栏顶回来挡住滑块；横屏歌词样式表可滚动。歌词页右上角更多菜单始终提供歌词显示/样式设置，不再仅限平板沉浸横屏。
- 修复约 392–393dp 宽屏上艺术家页 Tab 被挤扁（`#503`）；传记 Tab 隐藏多选/搜索/排序，地区列表将 English / 简体中文 / 日本語 置顶，并改用 Last.fm API + Wikipedia 以便国内无 VPN 加载（`#504`）。
- Apple Music 播放页底部中间按钮由歌曲信息改为播放模式。
- 艺术家页在「发行专辑」和「MV」之间新增 Last.fm 传记 Tab（`#496`），可按官方站点切换 English / Deutsch / Español / Français / Italiano / 日本語 / Polski / Português / Русский / Svenska / Türkçe / 简体中文。
- 文件夹层次详情页加入面包屑导航（`#497`）；非当前层改为灰色，点击父目录会进入该层但保留更深的路径，可再点回去（`#500`）。
- 搜索栏移到底栏：左侧为进入搜索前的 Tab 图标，右侧为加长搜索框；新增「再次进入搜索」设置（清空 / 保留 / 全选上次内容）。
- 歌曲信息条目可跳转到对应艺术家、专辑、目录、格式和音质页；从播放页跳转会收起播放覆盖层。在文件夹层次页打开「目录」不再把播放页留在上层（`#499`）。
- 「继续播放」只在当前分类就是播放来源时隐藏，歌曲碰巧在列表里不再误藏（`#492`）。
- 从当前播放列表或长按迷你条跳转播放来源时，会定位到正在播放的歌曲；覆盖音频格式/音质详情、文件夹层次、文件夹详情和文件夹歌单（`#494`、`#498`）。
- 从播放页、歌曲详情页、歌词页跳转到其他页面时，不再闪一下播放页下面的页面（`#495`）。
- Apple Music 歌词页的「文 A / 麦克风」仅在控制层可见时显示，全屏歌词两秒后随 chrome 隐藏；封面滑到歌词使用共享元素形变。
- 逐字假名按 TTML 时间跨度对齐，避免挤在一起或吞字；切歌/跳过改为本地队列立刻换曲。
- 液态玻璃底栏的发光描边补到迷你播放条和搜索圆块；小米超级岛控制图标和桌面歌词浮层边框更完整。
- 杜比（AC-3 / E-AC-3 / AC-4）音源播放页只保留双 D 杜比标识，不再叠加 Hi-Res 的 ∞。

English Changelog
- Karaoke accompaniment no longer ships a stem-separation model and uses realtime center-channel reduction again. Collapsed tablet Apple Music cover is sized to remaining height so it no longer covers the title. The lossless capsule uses Apple Music's waveform mark, the Hi-Res cover badge defaults to the bundled gold badge, and song-list Dolby tags use Apple Music's double-D mark.
- Dynamic covers keep the static artwork visible until the first video frame; lookup uses a folder index so SAF/large-folder scans no longer stall for ~20s or flash.
- First launch opens a full-screen flowing-light setup wizard. The player quality capsule uses Apple Music's Dolby Atmos mark.
- Player quality capsule now uses an Apple Music / Flamingo waveform + Lossless/Hi-Res mark instead of ∞ Apple Lossless.
- Pull down from anywhere on the album cover to minimize the player, not just the top edge.
- Artist covers, dynamic covers, and music videos share one Cover and video settings page instead of living under Scan vs Appearance.
- Album, artist, and folder screens show a spinner until their lists resolve instead of flashing “not found” or hanging on tap.
- Apple Music player style now has a matching centered landscape tablet layout.
- Settings gain a setup wizard, and search jumps to the whole switch card and flashes it twice.
- Restored ColorOS Live Lyrics Bridge 4.0 alignment: pre-seed `lyricInfo` in the first MediaItem, clear it on track changes, keep TITLE/ARTIST as song identity while lock-screen lyrics are on, and retain one delayed compatibility republish (`#444`).
- Stop artist tabs from being crushed on ~392–393dp widths (`#503`). Hide multi-select/search/sort on the biography tab, pin English / 简体中文 / 日本語 in the region list, and load bios via the Last.fm API plus Wikipedia without a VPN (`#504`).
- Apple Music player footer center button is now playback mode instead of song info.
- Artist pages gain a Last.fm biography tab between release albums and music videos (`#496`), with the official Last.fm language switcher (English, Deutsch, Español, Français, Italiano, 日本語, Polski, Português, Русский, Svenska, Türkçe, 简体中文).
- Folder-hierarchy details now show breadcrumbs (`#497`). Ancestors are gray; tapping a parent opens that folder while keeping the deeper trail so you can jump back (`#500`).
- Search moves into the bottom dock: the previous tab icon on the left and a long search field on the right. A setting controls reopen behavior (clear / keep / select the last query).
- Song-info rows jump to the matching artist, album, directory, format, or quality page and dismiss the player overlay. Opening Directory from a folder-hierarchy page no longer leaves the player stuck on top (`#499`).
- The continue-playback row hides only when this category is the current playback source, not merely because the playing song happens to be in the list (`#492`).
- Jumping to the playback source from the queue or a long-press on the mini player now locates the current song, including audio-format/quality buckets, folder hierarchy, folder details, and folder playlists (`#494`, `#498`).
- Navigating away from the player, song-details, or lyrics pages no longer flashes the page underneath (`#495`).
- Apple Music lyrics show 文A / mic only while chrome is visible; they hide with the 2-second fullscreen fade. Cover-to-lyrics uses a shared-element morph.
- Timed furigana follows TTML spans instead of concatenated glyphs. Next/previous skip updates the local queue immediately.
- Liquid-glass edge glow is applied to the mini player and search pill. Super Island control icons and desktop-lyric overlay chrome are completed.
- Dolby (AC-3 / E-AC-3 / AC-4) player capsules keep the double-D mark only and no longer also prefix the Hi-Res ∞.


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
