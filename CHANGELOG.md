# 1.3.1

From `1.3.0` to `1.3.1` — 列表、自定义分类与文件夹 / lists, custom categories, and folders.

中文更新日志

- #733：音乐库筛选面板可以筛出重复歌曲，并可一键去重删除多余条目。
- #735：切换随机/顺序播放改为一次性应用目标队列，千级歌曲列表下不再逐渐卡顿或卡死，当前歌曲与播放进度保持不变。
- #737：全局搜索底部的「已加载全部结果」改为所有结果源都加载完成后才显示。
- #738：各页面列表条目的置顶标记移到标题最后一行文字末尾。
- #739：自定义分类卡片高度比例恢复 x:257，右侧图片改为 183:232，按专辑数排序时副标题显示「n 张」，减小标题左边距。
- #740：自定义分类列表样式副标题由「n 张参与专辑」改为「n 张专辑」。
- #741：首页含大量功能块时，从分类页返回首页恢复此前的滚动位置。
- 新增 2 种播放页样式：黑胶唱机、卡片扇形（设置 → 外观与主页 → 播放页）。
- #722：专辑、艺术家、分类和文件夹的列表条目把置顶标记放到最后一行文字末尾。
- #723：自定义分类卡片标题最多四行，标题和歌曲数作为一组垂直居中，歌曲数显示为「n 首」。
- #724：每个自定义分类可以单独选择列表或卡片，并决定详情页是否显示专辑标签。列表封面为圆角方形。
- #726：自定义分类和艺术家传记匹配规则的添加按钮移到列表顶部，新增或编辑后的条目排在最前。
- #729：文件夹层次结构每一级右上角增加关闭，回到首页并清空该层导航；下次从首页进入时打开文件夹根目录。
- #730：全局搜索或筛选加载完成后，结果列表底部显示「已加载全部结果」。
- #721：Spotify Canvas 铺满整个播放页。Canvas 设置和播放页右上角可以在数秒后自动收起标题与控制，点按画面恢复。
- 检查更新在 64 位设备上优先下载 arm64 包。部分系统把 32 位 ABI 列在前面时，不再自动下载 armeabi-v7a。
- 歌曲信息增加制作人、版权、语言，以及文件里的其他标签。这些字段和原有字段都可以在「外观与主页 - 歌曲信息」里显示、隐藏和排序。空字段仍然不显示。
- 播放页进度条下方的音质标签默认去掉和播放按钮一样的半透明轮廓，只留文字。设置 → 外观与主页 → 播放页里可以重新打开「音质标签轮廓」。列表里的音质标记不变。
- 竖屏播放页的 MV 点全屏后继续显示视频。歌词改由麦克风按钮打开，不再盖住画面。
- 长按首页「听音乐」打开音乐来源菜单，选项与音乐库星级筛选同一类弹出列表。
- #229：当前播放列表的拖拽排序按钮改为长按后才可拖动，误触滑动不再误排序（其他列表不受影响）。
- #742：全局搜索输入框持有光标时，上下滑动结果列表会先收起键盘，不再反复弹出输入法。
- #743：各页面右上角搜索按钮唤起的输入框遵循「进入搜索页自动弹出输入法」开关。
- #720：底栏跨多页切换直接平滑过渡到目标页，不再依次闪现中间页面。
- #728：读取 PRODUCER 标签新增「制作人」分类，与作曲家全链路一致：首页磁贴、底栏、桌面快捷方式（新图标）、搜索与最近播放标签页、专辑/艺人/分类/歌曲信息跳转、详情页区块、内置元数据编辑器，以及艺术家分隔符与「不拆分」名单；制作人图片沿用艺人图优先、再按专辑封面回退的规则。
- 分类批量修改扩展：作曲家、编曲家、作词家、制作人分类也支持批量写入，并可附加作曲与歌词字段；输入框支持 <TAG> 占位符按歌曲替换，例如 <ARTIST> 写入每首歌的艺术家、[00:00.000]作曲：<COMPOSER> 写入歌词前缀加作曲家。
- 海报墙全屏歌词新增「流岚」样式：歌词纵向层叠，当前句居中逐字点亮，上下句随距离模糊、淡出、缩小，整列以弹性动画跟随播放进度（自研实现）。
- 新增 3 种播放页样式：胶卷（电影胶片队列 + 时间码 + 字幕式歌词）、电波（示波器网格 + 合成波形 + 进度环唱片盘）、杂志（浅色纸面排版 + 衬线标题 + 页码进度）（设置 → 外观与主页 → 播放页）。
- #741：首页返回恢复滚动改为持续重试到目标位置；此前滚动值被未长够的内容钳制在中间时不再写入，恢复会停在半路。
- #742：搜索页滚动期间持续收起键盘并强制清焦点，输入法不再因焦点残留或弹层抢占而反复弹回。
- 桌面小部件的歌词/进度/封面更新改到后台串行线程执行，不再在主线程做 AppWidget binder 同步调用（此前实测单次阻塞可达数秒，极端情况超一分钟）。
- 文件夹歌单详情页的歌曲/文件夹聚合改到后台线程计算，不再在每一帧重组里做全库 × 文件夹的过滤，大曲库下进入与刷新页面不再卡帧。
- 黑胶唱机页唱片高光改为柔和扫掠渐变，盘面不再被亮块分成几节；标题行重复的播放/暂停键换成播放模式与队列图标，按钮大小统一。
- 黑胶唱机与卡片扇形页补上歌词区（当前句 + 下一句淡显），不再只有一句或空着。
- 主题播放页（黑胶/扇形/胶卷/电波/杂志）进度条填充色改用高对比前景色，封面取色与底色相近时不再看不到已播进度。
- 主题播放页补齐 MV 能力：主封面位可显示 MV 画面、MV 切换钮与全屏按钮，进入全屏 MV 与默认页一致。
- 屏蔽文件夹列表顶部新增「手动添加屏蔽文件夹」，点击弹出与设置一致的编辑面板（左上 × 右上 √），手动输入文件夹路径即可加入屏蔽。
- 液态玻璃底栏上滑进入播放页时，胶囊先向上拉伸、横向边缘滞后收拢，再铺成整个播放页，形变过程与参考录屏一致。
- 默认应用图标换回蓝色渐变底的音符图标；二次元图标保留为可选样式（设置 → 外观与主页 → 应用图标）。「传统」选项与原版为同一音符资源，已并入原版，存量选择自动迁移。
- 主题播放页（黑胶/扇形/胶卷/电波/杂志）进度条加 24dp 横向边距，不再贴到屏幕两侧。
- 隐藏状态栏且保留占位时，沉浸封面向上覆盖状态栏区域、画面顶到屏幕边缘，下方控件仍保留内缩。
- #742 修复：搜索页的自动聚焦只在进入或显式恢复查询时触发一次；页内横向翻页手势令页面激活态短暂翻转时，输入法不再在滑动结束后弹回（上下左右滑动均不再弹出输入法）。
- 修复快速连续切歌或切回上一首时，播放页和进度条停在上一首的问题。
- #745：从 Deeplink、桌面快捷方式、ShortCut 跳转后按返回，回到跳转前所在的页面，不再回到底栏主页；从快捷方式打开搜索页时，悬浮底栏左侧的圆形按钮同样返回跳转前页面。
- #748：列表条目的置顶标识不再占用标题宽度，无论标题多长都不压缩；末行文字放不下时文字末尾淡出、标识停在行尾，不与文字重叠，仍在最后一行末尾。
- #750：从 Deeplink、桌面快捷方式、ShortCut 跳转后按返回，回到跳转前页面时记住之前的滚动位置，不再回到顶部。
- #751：自定义分类允许添加重复的标签键，同名同键的多个分类可以并存，各自独立设置列表样式、分类合集与专辑合并策略。
- #752：设置 → 音乐库新增「专辑合并策略」：专辑名必选，可再勾选专辑艺术家、艺术家、作曲家、发行时间，所选字段全部相同才算同一张专辑；默认专辑名加专辑艺术家，只影响默认「专辑」分类，改动即时生效、不重新扫描。
- #753：「自动下载艺术家图片」改为「自动缓存艺术家图片」，设置为「从不」时不再自动下载；搜索图片页点「保存图片」改为跟随「艺术家图片保存路径」，不再写入缓存路径；「艺术家图片缓存路径」默认改为应用内部存储（/storage/emulated/0/Android/data/com.ella.music/files/artist_images/）。
- #754：艺术家图片搜索结果显示图片分辨率；「所有」标签页按搜索词匹配程度加来源优先级综合排序；支持 deeplink：`halcyon://artist-image-search?source=%s&keyword=%s`，source 可取 qq、netease、kugou、kuwo、spotify、last.fm。
- #755：关闭「播放页显示收藏按钮」后歌词页收藏按钮同步隐藏，标题副标题右端补上空出的位置；新增「副标题滚动」开关，同时控制播放页与歌词页过长的歌手名；「标题滚动」开关同样作用于歌词页歌名。
- #756：音乐库页长按迷你播放条定位当前歌曲不再有明显延迟；进入全局搜索页自动弹出输入法不再有明显延迟。
- #744：自定义分类新增「分类合集」模式：填写「合并分类项名称」和「合并分类项」，两栏按行一一对应，成员之间用顿号或逗号分隔（如 `国语流行、华语流行、Pop`）。开启后分类页只显示合并项，点进去显示其中所有成员的歌曲。
- #749：标签为艺术家、专辑艺术家、作词、作曲、编曲、制作人、流派的自定义分类，可以关闭「应用自定义分隔符」，关闭后该分类不再使用「不拆分」名单和自定义分隔符。标签为 ALBUM 时可以选专辑合并策略：专辑名、专辑艺术家、艺术家、作曲家、发行时间，默认是专辑名加专辑艺术家，所选字段全部相同才算同一张专辑。以上都不影响默认分类。
- #746：新增「搜索图片」，入口在艺术家、作词家、作曲家、编曲家、制作人分类长按「更多操作」，以及艺术家详情页查看大图时「保存图片」左边的按钮。打开后是独立的艺术家图片搜索页，「所有」和各来源标签按「艺术家图片来源优先级」排序；点结果可以查看大图，保存路径跟随「艺术家图片保存路径」。
- #747：播放页快捷操作：长按「播放模式」选列表循环、单曲循环、随机或顺序（顺序是否出现跟随「取消顺序播放模式」开关）；长按「收藏」添加到歌单；长按「更多操作」打开播放页竖向操作菜单设置；长按「上一首」设置上一曲是切歌还是重放；长按「暂停」设置暂停时的切歌行为；非沉浸模式下长按进度条上方的「歌曲信息 / 查看频谱 / 编辑元数据 / 歌词打轴」打开对应设置。设置新增「播放页显示收藏按钮」「标题滚动」「点击歌曲名跳转专辑」三个开关。
- 新增「艺术家艺术字目录」（设置 → 封面媒体）：目录中若存在与艺术家同名的图片（如 `艺术家名.png`），艺术家页的名称文字会替换为该艺术字图片显示，效果类似 Apple Music 的艺术家艺术字，可配合 AM++ 下载的艺术字使用。
- 新增「专辑盒效果」开关（设置 → 封面媒体，默认开启）：专辑详情页的封面显示为立体的实体 CD 盒，带书脊、厚度与投影，点击翻开盒盖露出碟片，再次点击合上，长按仍可查看大图（自研实现，不含 Salt Player 代码）。
- #758：「自动缓存艺术家图片」设置为「从不」后，冷启动首帧仍可能触发一次自动缓存——收集设置的初始值被放宽为「仅 Wi-Fi」造成的竞态已修复，「从不」现在从第一帧就生效。
- #757：艺术家图片搜索的网易云源结果显示分辨率（URL 没有尺寸参数时自动升级为 1000y1000 大图）；配置 Last.fm API Key 后，Last.fm 标签返回多个尺寸的图片候选，不再只有最大一张，网页抓取仍是兜底。
- #759：播放页关闭「显示收藏按钮」后，标题与副标题右端与封面右端对齐——补齐非居中标题行遗留的 230dp 宽度上限。
- #544：歌单与文件夹歌单详情页顶栏，标题文字到右侧按钮的间距改为按实际按钮宽度预留，不再按写死值留出大块空白，间距与文件夹详情页一致。
- #750（重做）：分类详情页、文件夹详情页被 Deeplink/快捷方式打开的页面覆盖后返回，滚动位置经返回栈 savedStateHandle 兜底恢复；分类详情页列表状态同步改为可保存。
- 新增「在线歌曲下载路径」（设置 → 封面媒体）：默认各在线源写入各自内置目录（网易云 Music/Halcyon/NcmOfflineSongs、LX Music/Halcyon、MusicFree Music/MF）；选自定义存储目录（SAF 授权）后，网易云、MusicFree、LX 的歌曲下载统一写入所选文件夹，文件名冲突自动改名不覆盖。
- 修复顶栏按钮重叠：#544 的标题间距改动误把右上角操作按钮叠成一层，已还原为横向排布，同时保留按实际按钮宽度预留标题间距。
- 新增「播放模式加入播完停止」开关（设置 → 播放，默认关闭）：开启后播放模式选项里多出「播完停止」，选中后当前歌曲播完自动暂停（单次生效，单曲循环模式下也能在播完时停下，不再多放一遍）。
- 底栏新增「悬浮胶囊」样式：移植 Miuix Glass 导航胶囊，图标+文字项均分排布、滑动玻璃指示器随手指移动，毛玻璃折射质感并保留右侧独立搜索圆钮（设置 → 外观与主页 → 底栏样式）。
- 新增「顶栏液态玻璃」开关（设置 → 外观与主页 → 主题，默认关闭）：顶栏背景为强渐进模糊带，返回与操作按钮各自落在带折射透镜和泛光描边的玻璃圆片上，边缘沿渐进曲线淡出（需开启渐进模糊）。
- 淡入淡出增加智能过渡：默认淡入点落在唱词中间时，自动顺延到该句结束边界再起淡；同专辑同碟的连续曲目（N → N+1）直接走无缝衔接不再交叉淡化；无歌词时仍由波形包络兜底快速交接。
- 水波纹播放页样式移除（竖屏页面与横屏流动效果一并下线，相关设置项同步删除）。
- 播放页歌词区改为多行居中：横屏封面流显示上/当前/下三行，黑胶唱机页歌词区在顶部留白带内垂直居中、最多显示上下共四句。
- 海报墙堆叠卡片修复：左右滑动换牌不再触发切歌，非正在播放的卡片不再闪出歌词与播放控制界面，只有真正在播的卡才进入播放态。
- 海报墙全屏歌词效果按公开参考整体重写（自研实现，未使用 Folia 代码）：「回环」改为整句沿倾斜椭圆轨道环绕推进、当前句居中放大；「时计」为齿轮钟盘 + 弧线排布歌词；「凝彩」把当前句切成 2~4 词分镜，逐镜切换双色/灰阶色块并带反色双通道；「商籁」为印刷版框 + 超大描边首字母 + 胶片颗粒/半调 + RGB 偏移；「绘光」保留光场词回声与逐字点亮。
- 专辑盒效果从专辑详情页移到「介绍」页：专辑页封面恢复普通圆角封面；介绍页封面在开关开启时显示为可开合的实体 CD 盒，点击翻开盒盖查看碟片，长按仍可查看大图。
- 修复关闭收藏按钮后播放页标题/副标题右端与封面右端差 18dp 不对齐的问题：无尾随操作按钮时不再预留按钮空隙（#759）。
- 修复从播放页跳转到当前路由同名的页面（如专辑→同路由专辑）时播放覆盖层不收起、看似无法跳转的问题：导航效果改按路由与返回栈条目双键触发（#761）。
- 迷你播放条左右滑切歌时，副标题预览目标歌曲名并以主题色高亮（#762）。
- 艺术家图片源不再人为压低分辨率：网易云图片去掉 param= 尺寸参数直取原图，Last.fm 各尺寸候选不再统一改写为 770x0 而互相去重；Last.fm 在 API 无图或未填 key 时回退抓取艺术家公开页的 og:image 与内联图片（#764）。
- 详情页返回恢复滚动位置扩大到专辑页、艺术家页、歌单页、文件夹歌单页（歌曲/文件夹两个列表分别记忆），不再只有元数据分类页生效（#750）。
- 底栏默认样式改为「HyperOS 4 悬浮」；开启悬浮底栏进入搜索页时，底部搜索条与返回按钮改用与 OS4 胶囊一致的 54dp 尺寸。
- 专辑盒改为双轴全向 3D 倾斜跟手：手指任意方向拖动整盒实时旋转（过界橡皮筋），松手弹性回正；点按开合盒盖不变。
- 首页顶栏不再整体下沉到状态栏下方：模糊带直接延伸到屏幕顶端，消除与状态栏区域的割裂与采样错位重影。
- 切歌不再闪默认封面：封面模型解析期间继续沿用上一张已解析封面，新图解码期间保留上一张成功绘制的画面（播放页封面、黑胶碟心、封面大图等全部生效）。
- 应用图标恢复 1.3.0 样式；「传统样式」选项重新使用独立图标。
- #750 分类详情页滚动恢复补全：流派/年份/作词家/作曲家/编曲家/文件夹/自定义分类详情页与文件夹歌单页经 Deeplink/快捷方式跳转返回后回到原滚动位置（此前列表异步加载未就绪时恢复请求被钳到顶部）。
- #760 专辑合并策略改为冷启动生效：修改后展示「需重启应用生效」提示，进程内策略冻结于启动时读取的值，全程一致合并，避免新旧分组混用。
- 海报墙堆叠滑动换牌不再闪出旧卡：换牌后新卡在隐身状态下归位再淡入，替代原来的偏移量瞬移归零；播放页流光背景切歌不再闪——流动时钟改为真正进程级连续、容器底色 400ms 过渡、新旧渲染帧 220ms 交叉淡化。
- 专辑盒支持跟手拖动：介绍页的实体 CD 盒可横向拖动实时翻转盒盖，松手按开合角度弹簧吸附到开/关，点按开合与长按大图保持不变；「流光效果」选项 OS1 更名「禁用」（该项本就关闭流光动效）。
- 悬浮胶囊底栏与迷你播放条统一 16dp 水平内边距，上下边缘对齐；首页「听音乐」顶栏接入渐进模糊与液态玻璃圆片按钮管道（随滚动模糊，开启顶栏液态玻璃后四个操作按钮各自落在玻璃圆片上）；系统「打开方式」中的 M3U8 下载器改名「视频播放器」；底栏样式更名：高斯模糊悬浮→iOS 悬浮（高斯模糊）、液态玻璃悬浮→iOS 悬浮（液态玻璃）、悬浮胶囊→HyperOS 4 悬浮。
- 批量修改重做（#725）：新增「保持修改时间」（写回后恢复文件 lastModified，不打乱按修改时间排序）与「部分替换模式」（字段拆为「匹配/替换」双栏，只在命中片段内替换）；字段支持清空保存、混合值显示 <keep> 占位不动则不改、预填共同交集，并可从该分类歌曲的既有值中选择；专辑页与艺术家页长按菜单新增批量修改入口（专辑 11 项字段、艺术家 3 项），作曲家限改作曲/艺术家/专辑艺术家、编曲家/作词家/制作人限改自身字段，自定义标签追加标准字段集；「搜索图片」「批量修改」移至「永久删除」上方，并同步移植到全局搜索分类结果的长按菜单。
- 修复「专辑」分类块始终按默认字段（专辑名+专辑艺术家）分组的问题：未自定义合并字段的分类块跟随全局「专辑合并策略」，张数、封面与详情页歌曲恢复一致（#760）。
- 交叉淡入淡出（开关、时长、曲线）从「音频」移到新增的「设置 → 实验性功能」页，设置搜索索引同步指向新位置。
- #763：冷启动恢复播放时先等歌曲身份稳定再提交歌词与通知封面，不再短暂显示曲库第一首的歌词；歌词页可能永久停在「正在加载」的边界路径已清理。
- #732：MusicFree JS 适配层整体重写——补齐 CryptoJS（AES/DES/3DES/RC4、HMAC、PBKDF2、EvpKDF、OpenSSL 格式）、fetch/XMLHttpRequest、Buffer/btoa/atob、URL/FormData、node crypto/path/util/events 等 shim；Promise.race+setTimeout 的插件写法可正常超时；AsyncStorage/localStorage 落到应用内持久化，插件 token 不再每次调用丢失。
- 下拉/筛选菜单控件全部替换为 Miuix Glass 实现：排序菜单、来源菜单、评分筛选、库分析筛选、批量修改面板、在线源选择与全部设置下拉项改用 OS4 玻璃弹层（保留多选不自动收起等原交互）。
- #765：播放页长按歌手名直接打开「自定义艺人及创作人员分隔符」设置面板；「点击歌曲名跳转专辑」开关同步作用于歌词页歌名。
- 修复专辑盒点按无法翻开：双轴倾斜改造后点按仍走「就近吸附」逻辑，盒盖永远停在原状——点按改为直接开合取反，碟片内页恢复点击翻开。
- #760 还原：专辑合并策略恢复修改后即时生效（经 distinctUntilChanged 全链路刷新分组），「需重启应用生效」提示移除。
- 新建歌单改为对话框：全部六处「新建歌单」入口（艺术家页、分类详情、文件夹详情、歌曲更多菜单、首页选择面板、歌单页）由底部面板改为居中弹窗。
- 检查更新页「稳定版 / 预发布」渠道选择由底部面板改为顶栏 OS4 玻璃下拉菜单，两项直选。
- OS4 玻璃下拉菜单恢复系统预测性返回：弹层窗口内重新解析自己的返回事件分发器，侧滑全程显示面板进度，不再丢回宿主页面。
- 对话框与底部面板换用 Miuix Glass 材质：透明窗口底 + glassPanel 折射泛光面板，无模糊录制管道时回退原实色面板。
- 修复首页渐进模糊回滑重影：录制背景从滚动节点移到固定容器，快捷按钮与功能块不再被印在顶部模糊带里。
- 播放页切歌流光不再闪：切歌间隙封面短暂为空的空窗期继续沿用上一张封面，流光组件不再卸载重建（横屏布局同步修复）。

English

- #733: The library filter panel can surface duplicate songs and remove the extras in one action.
- #735: Switching shuffle/sequential order now applies the target queue in one shot; thousand-track queues stay smooth and the current song and position are kept.
- #737: “All results loaded” in global search now appears only after every result source has finished loading.
- #738: Pin marks on list rows move to the end of the title’s last line.
- #739: Custom category cards restore the x:257 height ratio and 183:232 artwork, show “n” when sorted by album count, and use tighter title padding.
- #740: Custom category lists show “n albums” instead of “n participating albums”.
- #741: Returning home from a category restores the previous scroll position even with many feature blocks.
- Added two player page styles: vinyl turntable and fanned cards (Appearance → Player page).
- #722: Pin marks on album, artist, category, and folder list rows sit at the end of the last text line.
- #723: Custom category cards allow four title lines, keep the title and count centered together, and show the count as a short “n”.
- #724: Each custom category can use a list or a card, and can show an album tab on its detail page. List artwork is a rounded square.
- #726: Add buttons for custom categories and biography match rules move to the top. New and edited entries sort first.
- #729: Every folder-hierarchy page has a close button that returns home and clears that navigation. The next visit opens the folder root.
- #730: Global search shows “All results loaded” at the bottom after a search or filter finishes.
- #721: Spotify Canvas fills the player. Canvas settings and the player shortcut can hide the title and controls after a few seconds; tap the video to bring them back.
- Update checks on 64-bit devices download the arm64 package first. A ROM that lists the 32-bit ABI first no longer auto-downloads armeabi-v7a.
- Song info adds producer, copyright, language, and the file’s other tags. These fields, with the existing ones, can be shown, hidden, and reordered under Appearance → Song info. Empty fields stay hidden.
- The quality label under the player progress bar no longer uses the play button’s translucent outline. Turn it back on under Appearance → Player → Quality label outline. List quality marks are unchanged.
- Full screen from a portrait music video keeps the video. Lyrics open from the microphone button instead of covering the picture.
- Long-pressing the home title opens the library-source menu, using the same popup as the library rating filter.
- #229: The now-playing queue's drag handle now requires a long press; a stray swipe no longer reorders (other lists are unchanged).
- #742: Scrolling search results while the field is focused hides the keyboard instead of letting the IME pop back up.
- #743: Search boxes opened from page-top search buttons honor the “auto show keyboard on search” setting.
- #720: Bottom-bar jumps across several tabs transition straight to the target page without flashing intermediate pages.
- #728: A Producer category reads the PRODUCER tag and mirrors the composer pipeline end to end: home tile, bottom dock, home-screen shortcut (new icon), search and recent-playback tabs, jumps from album/artist/category/song-info pages, detail sections, the built-in metadata editor, and the artist separators and no-split list. Producer artwork resolves artist images first, then falls back to album covers.
- Category batch edit extended: composer, arranger, lyricist, and producer categories support batch writing with optional composer and lyrics fields, and every input accepts <TAG> placeholders resolved per song — e.g. <ARTIST> writes each song's artist, and “[00:00.000]作曲：<COMPOSER>” writes a lyrics prefix plus the composer.
- Poster-wall fullscreen lyrics gain a “Flowing” style: lines stack vertically, the active line stays sharp and word-lit at center while neighbours blur, fade and shrink with distance, and the column springs along with playback (original implementation).
- Three new player page styles: Cinemascope (film-strip queue with sprocket holes, timecode, subtitle lyrics), Signal (oscilloscope grid, synthetic waveform, progress-ring disc), and Editorial (light paper layout, serif headline, page-number progress) — Settings → Appearance & Home → Player page.
- #741: Home scroll restore now keeps retrying until it reaches the saved offset; previously a position clamped mid-way by still-growing content was never rewritten and restore stalled part-way.
- #742: While the search list is scrolling the keyboard is kept hidden and focus is forcibly cleared, so the IME can no longer pop back from a stuck focus or a popup stealing it.
- Widget lyric/progress/artwork updates now run on a serial background lane instead of issuing AppWidget binder calls on the main thread (measured stalls of several seconds, once over a minute).
- Folder-playlist detail aggregation now runs on a background thread instead of re-filtering the whole library per folder inside every recomposition, removing multi-second frame freezes on large libraries.
- Vinyl turntable: the record's highlight is now a soft sweep gradient so the disc no longer looks split into segments; the duplicate play/pause button in the meta row is replaced by playback-mode and queue icons with consistent sizing.
- Vinyl and fanned-cards pages gain a lyric area (active line plus a dimmed next line) instead of a single line or empty space.
- Themed player pages (vinyl/fan/cinemascope/signal/editorial) draw the progress fill in a high-contrast foreground color, so played progress stays visible when the cover-derived accent blends into the background.
- Themed player pages now support MV like the stock page: the main artwork slot can show the MV surface plus the MV toggle and fullscreen buttons.
- Blocked folders list gains a manual “Add blocked folder” entry that opens the settings-style edit sheet (× top-left, ✓ top-right) so a folder path can be typed and blocked directly.
- Swiping up on the liquid-glass bottom bar now stretches the pill upward first while its horizontal edges lag behind, morphing into the player like the reference recording.
- The default launcher icon is the note on a blue gradient again; the anime icon stays available as an option (Settings → Appearance → App icon). The “Traditional” entry rendered the same note, so it is folded into Default and existing selections migrate automatically.
- Themed player pages (vinyl/fan/cinemascope/signal/editorial) inset the progress bar by 24dp so it no longer touches the screen edges.
- With the status bar hidden but its space reserved, the immersive cover now lifts over the reserved band and reaches the window edge while controls below keep the inset.
- #742 follow-up: search autofocus now fires only once per entry or explicit query restore; the in-place page swipe briefly flips page-active state during any drag, and the IME no longer pops back when the gesture settles — swipes in every direction leave the keyboard down.
- Stuck-skip fix: a real track change during rapid skipping or skipping back is no longer discarded by the stale-transition guard, so the player page and progress bar no longer stay on the previous song.
- #745: Pages opened from deeplinks, launcher shortcuts, and app shortcuts stack on top of the current page, so Back returns to where you were. A search opened that way sends the floating dock's round button back to the same page (showing a back icon when the source is not a dock tab).
- #748: The pin mark no longer reserves any title width, so titles of any length keep their full layout; when the last line has no room, the text tail fades out and the icon still sits at the end of that line without overlapping glyphs.
- #750: Returning from a page opened by a deeplink, launcher shortcut, or app shortcut restores the previous scroll position instead of jumping to the top.
- #751: Custom categories now accept duplicate tag keys; same-key categories coexist with independent list styles, collections, and album merge rules.
- #752: New “Album merge rule” setting under Library: album name is mandatory and album artist, artist, composer, and release date can be added — songs count as one album only when every selected field matches. Defaults to name + album artist, affects only the built-in Album category, and applies instantly without a rescan.
- #753: “Auto download artist images” is renamed “Auto cache artist images” and the Never option now truly skips downloads; Save image on the search page follows the artist image save location instead of the cache path; the cache path defaults to app internal storage (/storage/emulated/0/Android/data/com.ella.music/files/artist_images/).
- #754: Artist image results show their resolution; the All tab sorts by keyword match quality then source priority; deeplinks `halcyon://artist-image-search?source=%s&keyword=%s` accept source values qq, netease, kugou, kuwo, spotify, and last.fm.
- #755: Turning off the player favorite button also hides it on the lyrics page and lets the title/subtitle fill the freed edge; a new “Subtitle scrolling” switch controls long artist names on both pages, and “Title scrolling” now applies to the lyrics page too.
- #756: Locating the current song after long-pressing the mini player on the library page is no longer visibly delayed, and the global search page's automatic keyboard pop-up is no longer delayed.
- #744: Custom categories gain a “Category collection” mode: list merged item names and their members line by line (members separated by 、 or commas). When on, the category lists only the merged items and each opens with all of its members’ songs.
- #749: Custom categories keyed on artist, album artist, lyricist, composer, arranger, producer, or genre can turn off “Apply custom separators”; ALBUM categories can choose the album merge rule (album name, album artist, artist, composer, release date; default name + album artist). Built-in categories are unaffected.
- #746: “Search images” is added to the long-press menu of artist, lyricist, composer, arranger, and producer categories and to the artist page image viewer. It opens a standalone artist image search page (also callable by other apps); the “All” tab and source tabs follow the artist image source priority, and results open full screen and save to the artist image folder.
- #747: Player shortcuts: long-press playback mode to pick repeat all / repeat one / shuffle / in order; favorite to add to a playlist; more for the vertical action menu; previous and pause for their behaviour settings; song info / spectrum / edit metadata / lyric timing for their app settings. New settings: show favorite button, scrolling title, and tap title to open album.
- Added an "Artist wordmark folder" setting (Settings → Cover & media): when the folder contains an image named like the artist (e.g. `Artist.png`), the artist name on the artist page is rendered as that wordmark image, similar to Apple Music artist wordmarks (works with wordmarks downloaded via AM++).
- Added an "Album case effect" switch (Settings → Cover & media, on by default): the album page cover is shown as a physical 3D CD jewel case with spine, thickness and drop shadow — tap to flip the lid open and reveal the disc, tap again to close, long-press still opens the large preview (original implementation, no Salt Player code).
- #758: With "Auto-cache artist images" set to "Never", a cold-start frame could still trigger one cache write — the collected setting's initial value was permissive. Fixed so "Never" applies from the first frame.
- #757: NetEase results in artist image search now show a resolution (URLs without a size parameter are upgraded to a 1000x1000 variant); with a Last.fm API key configured, the Last.fm tab returns multiple size candidates instead of only the largest image, with page scraping kept as fallback.
- #759: When the player favorite button is hidden, title and subtitle right edges now align with the cover's right edge — the leftover 230dp cap on the non-centered title row is removed.
- #544: Playlist and folder-playlist top bars reserve the title's trailing space by the measured width of the actual action buttons instead of a hardcoded estimate, removing the dead gap so spacing matches the folder detail page.
- #750 (redo): When a category or folder detail page is covered by a deeplink/shortcut destination, the scroll position is restored through the back-stack entry's savedStateHandle; the category detail list state is now saveable as well.
- Added "Online song download location" (Settings → Cover & media): online sources keep writing to their built-in folders by default (NetEase Music/Halcyon/NcmOfflineSongs, LX Music/Halcyon, MusicFree Music/MF); picking a custom storage folder (SAF) redirects NetEase, MusicFree and LX song downloads there, auto-renaming on filename conflicts.
- Fixed overlapping top-bar action buttons: the #544 title-spacing change stacked the action icons into one layer; they are laid out horizontally again while the title spacing still follows the measured button width.
- Added an "Add 'Stop after current' to playback modes" switch (Settings → Playback, off by default): the playback-mode options gain "Stop after current" — playback pauses when the current song finishes (one-shot, and repeat-one stops at the end of the current pass instead of playing once more).
- New "Floating capsule" bottom bar style: a Miuix Glass navigation capsule with evenly spaced icon + label items and a sliding glass indicator that follows your finger, plus the separate circular search button on the right (Settings → Appearance & Home → Bottom bar style).
- New "Liquid glass top bar" switch (Settings → Appearance & Home → Theme, off by default): the bar gains a strong progressive blur band while back and action buttons each sit on their own glass circle with edge refraction and a bloom stroke, fading out on the progressive ramp (requires progressive blur).
- Smarter crossfade: when the fixed fade window would open mid-lyric, the start slides to the line boundary so the vocal finishes first; consecutive album sides (same disc, track N → N+1) stay gapless instead of crossfading; the waveform-envelope fast-commit remains the fallback when no lyrics exist.
- Removed the water-ripple player page style (portrait page and the landscape flow effect, along with their settings entries).
- Player lyric areas are now centered multi-line stacks: landscape cover-flow shows previous/current/next lines, and the vinyl page centers up to four neighbouring lines inside its leftover top band.
- Poster wall fix: swiping within a stacked card no longer triggers playback, and non-playing cards no longer flash the lyric/playback state — only the actually playing card enters that presentation.
- Poster-wall fullscreen lyric effects rewritten from scratch against public references (native implementations, no Folia code): Orbit now rides whole lines along a tilted elliptical track with the current line enlarged at the front; Clock is a geared dial with lyrics on arcs; ColorField cuts the active line into 2–4-word shots over two-tone/grayscale blocks with an inverted pass; Sonnet gains a print frame, oversized outlined initial, film grain/halftone and RGB split; Glow keeps the light-field word echoes.
- Album case effect moved from the album page to the "Introduction" page: the album header cover is a plain rounded cover again; the intro page cover renders the openable CD jewel case when the setting is on — tap to flip the lid, long-press still opens the large preview.
- Fixed the player title/subtitle sitting 18dp short of the cover's right edge when the favorite action is hidden: no trailing button space is reserved without a button (#759).
- Fixed the player overlay staying open when navigating to another entry of the same route (e.g. album → same-route album), which looked like the jump silently failed: the route effect now keys on both route and back-stack entry identity (#761).
- Mini-player swipe-to-skip now previews the target song title in the subtitle and highlights it with the theme accent (#762).
- Artist image sources no longer cap resolution: NetEase URLs drop the param= sizing parameter to fetch the original, and Last.fm size variants are no longer all rewritten to 770x0 (which deduplicated them into one); when the API returns no usable image or no key is configured, Last.fm falls back to scraping the public artist page's og:image and inline images (#764).
- Scroll-position restore on return now covers the album, artist, playlist and folder-playlist detail pages (songs/folders lists remembered separately), not just metadata category pages (#750).
- The bottom bar now defaults to the HyperOS 4 floating capsule; in the search page the dock search field and back button adopt the same 54dp metrics as the OS4 capsule.
- The album case now tilts in full 3D following the finger: drags in any direction rotate the whole case with rubber-band overflow, springing back on release; tap-to-open is unchanged.
- The home top bar no longer sits below the status bar: the blur band now extends to the top screen edge, removing the visible seam and the sampling-offset ghosting.
- Track changes no longer flash the default cover: while the next artwork model resolves, the previous resolved model keeps serving, and while it decodes, the last successfully drawn frame stays on every player cover surface.
- The app icon is restored to the 1.3.0 artwork; the “traditional” option uses its own icon again.
- #750 Scroll restoration completed for genre/year/lyricist/composer/arranger/folder/custom-category detail pages and the folder-playlist page: returning from a deeplink/shortcut destination now restores the reading position (previously the restore request was clamped to the top while the asynchronously-loaded list was still empty).
- #760 The album merge strategy now applies on cold start: editing it shows a “takes effect after restart” hint and the in-process strategy stays frozen at the value read on launch, keeping grouping consistent end to end.
- Poster-wall stack swipes no longer flash the previous card: the incoming card returns home while invisible and fades in over the promoted rear card, replacing the instant offset snap; the player flow background no longer flickers on track change — the flow clock is now truly continuous, the container color eases over 400ms, and consecutive rendered frames crossfade for 220ms.
- The album case is now finger-tracked: drag horizontally on the intro page's CD jewel case to flip the lid in real time, releasing snaps it open or shut; tap-toggle and long-press preview are unchanged; the "流光效果" option OS1 is renamed to "Disabled" (it already turns the flow effect off).
- Floating capsule bottom bar and mini player now share the same 16dp horizontal inset so their edges align; the home "听音乐" top bar opts into the progressive-blur + liquid-glass pipeline (blurs on scroll, and with the liquid-glass switch the four actions sit on individual glass circles); the "M3U8 下载器" label in the system open-with sheet is renamed to "Video Player"; bottom bar styles renamed: 高斯模糊悬浮→iOS Floating (Gaussian blur), 液态玻璃悬浮→iOS Floating (Liquid glass), 悬浮胶囊→HyperOS 4 Floating.
- Batch edit rework (#725): new "Keep modified time" toggle (restores file lastModified so date-sorted lists stay put) and "Partial replace mode" (per-field match/replace columns that only rewrite the matched fragment); fields can be cleared, mixed values show a <keep> placeholder that leaves the tag untouched until edited, prefills pick the shared segment intersection, and every field can pick from values already present in the category; album and artist long-press menus gain batch edit (11 fields for albums, 3 for artists), composer is limited to composer/artist/album-artist, arranger/lyricist/producer to their own tag, and custom tags gain the standard field set; "Search image" and "Batch edit" move above "Delete permanently" and are ported to the global search category long-press menu.
- Fixed the "Album" category block always grouping by the default fields (name+artist): blocks without a customized field list now inherit the global album merge strategy, so counts, covers and detail songs agree again (#760).
- Crossfade (toggle, duration, curve) moves from Audio settings into a new Settings → Experimental page; the settings search index points to the new destination.
- #763: Cold-start restored playback now waits for a stable song identity before committing lyrics and notification artwork — the first library song's lyrics no longer flash; removed the edge paths that could pin the lyric page on "Loading" forever.
- #732: The MusicFree JS adapter layer is rewritten — full CryptoJS (AES/DES/3DES/RC4, HMAC, PBKDF2, EvpKDF, OpenSSL format), fetch/XMLHttpRequest, Buffer/btoa/atob, URL/FormData, and node crypto/path/util/events shims; Promise.race+setTimeout plugin patterns now time out correctly; AsyncStorage/localStorage are backed by persistent app storage so plugin tokens survive between calls.
- Every dropdown/filter menu now uses the Miuix Glass popup: sort menus, the source menu, rating filter, library-analysis filter, batch-edit sheet, online provider selector, and all settings spinners render as OS4 glass popups (multi-select keeps its stay-open behavior).
- #765: Long-pressing the artist name on the player page opens the custom artist/genre separator sheet directly; the "tap title to open album" switch now also applies to the song title on the lyrics page.
- Fixed the album case ignoring taps: after the dual-axis tilt rework a tap still ran the old "snap to nearest" logic so the lid could never flip — a tap now toggles open/closed directly and the disc inside is reachable again.
- #760 reverted: the album merge strategy applies live again (changes propagate through a distinctUntilChanged flow) and the "takes effect after restart" hint is removed.
- Create-playlist is now a dialog: all six "new playlist" entries (artist page, category detail, folder detail, song menu, home selection sheet, playlist page) open a centered dialog instead of a bottom sheet.
- The update page's stable / pre-release channel picker moves from a bottom sheet to an OS4 glass dropdown under the top-bar action.
- OS4 glass dropdowns restore predictive back: the popup re-resolves its own navigation-event dispatcher, so the system swipe gesture drives live panel progress instead of falling through to the host page.
- Dialogs and bottom sheets switch to the Miuix Glass material: transparent window base plus a glassPanel with refraction and bloom, falling back to the previous solid surfaces when no recorded backdrop is available.
- Fixed home progressive-blur ghosting: the recorded backdrop moved from the scrolling node to a fixed container, so shortcut buttons and feature blocks no longer imprint into the top blur band after scrolling back up.
- The player flow background no longer flashes on track change: the previous artwork keeps rendering through the brief null-cover window so the flow composable is never torn down and recreated (landscape layout fixed the same way).

Version
- Version name: `1.3.1`
- Version code: `41`

# 1.3.0

From `1.2.9` to `1.3.0` — 播放、歌词与曲库更新 / playback, lyrics, and library updates.

中文更新日志

### 歌词

- 立体歌词与边缘虚化改用单一图层的原生图像滤镜，移除递归录制歌词内容的多层渲染链，修复图层树递归导致的 RenderThread 栈溢出风险。
- 多选列表支持长按后连续拖选，覆盖歌曲、专辑、艺术家、分类、歌单、文件夹歌单、搜索和最近播放；支持边缘自动滚动与回拉收缩范围，保留此前选择与排序把手。
- 多选界面、歌词分享、备份类型和模块排序等选择面板增加反选；仅反转当前可选范围，保留其他范围的选择及数量上限。
- 绘光主歌词唱完后保持正常排版，装饰词片沿光场缓慢漂移并在有限时间内淡出，避免长期散落堆积在一侧。
- #716：恢复原有底栏交互，页面左右滑动完成后及时同步气泡位置，不再触发模拟按压放大；底栏拖动仍在松手后切换页面。
- 平板首页默认显示 8 个快捷按钮，功能块排除快捷区已有项目；保留手动配置。海报卡片修正标题分隔符，左右切歌复用已加载封面并避免旧播放状态覆盖新卡片。
- 艺术家传记增加名称匹配替换规则，可单独开关并指定数据源；按别名、原名、繁转简名称依次匹配，接入备份恢复。
- 液态底栏从 Miuix 0.9.4 的 Apache-2.0 示例重新适配，替换旧 GPL 动画与手势文件，保留外观设置与电视交互；增加来源与许可证检查。

- 海报墙点击封面只展开卡片，暂停时滑动切歌仍保持暂停；隐藏底部迷你播放条。堆叠布局缓存几何数据，弹窗与滑动使用稳定布局的图层动画，降低重复排版和解码开销。
- 凝彩、绘光、回环和商籁补齐逐字羽化扫光；凝彩黑白字按实际背景区域反转，回环保留英文词间空格，商籁保持歌词正常阅读角度，时计相邻歌词支持换行。
- 自定义分类条目使用卡片，删除按钮与箭头垂直对齐。
- SMB、WebDAV、FTP / FTPS 和 SFTP 音乐库扫描只建立目录索引，按需读取当前歌曲元数据；SMB / FTP / SFTP 播放流增加 256 KiB 有界缓冲以减少小块读取的网络往返，WebDAV 移除目录预取并沿用 HTTP 流缓冲，清理旧的大量元数据缓存。

- 歌词页补回立体歌词开关，并与播放页歌词显示菜单共用实时设置；角度也同步。补齐 AM 普通、沉浸、横屏和独立歌词页的立体效果；顶部信息区与底部控制区的边缘虚化固定在视口，立体旋转后仍有柔和过渡。

- 首页在线音乐移除 WebDAV；音乐来源只显示当前来源设置。频谱查看新增每次询问，内置频谱可调用其他应用。
- 海报墙堆叠保留适当间隙并错落排列；下层封面长按松手可置顶，拖动可在同组任意层插入或换组，每组最多 10 张并保存层序。播放保留原完整弹窗，后方叠放相邻弹窗，左右滑动切歌，移除上一曲和下一曲按钮。
- 对照 Folia 重做凝彩的大色块逐字拼贴、绘光的光场与飘散径迹、回环的倾斜椭圆字素环；新增商籁的大小字穿插与几何线稿。保留全局字体和独立歌词配色。
- 全局搜索返回进入前的底栏页面，悬浮底栏的返回图标与目标一致；远程曲库搜索、音质和红心筛选合并后台标签更新，保留完成结果，避免筛选进度反复重置。

- 修复 TTML 重叠演唱和逐字 LRC/ELRC 的多行高亮；拉长音文字与发光同步逐字羽化，限制旧播放采样停滞时的时钟提前。
- Beautiful Lyrics 背景支持非当前行歌词模糊，补齐设置与音频信息页面的应用字体。
- 歌词分享新增旧版 1.2.7、Spotify、杂志、电影感、拍立得、书法等样式；旧版补齐圆角，网易云样式支持 Halcyon 或自定义署名。

- 彩虹色歌词改为整句渐变：当前高亮行共用一条七彩光谱，每个字只取自己所在位置的色段，逐字不再各自循环一遍彩虹，长时间观看不再晃眼睛。

### 音乐库与扫描

- 首页支持按任意歌曲标签配置自定义分类（如编曲家 / ARRANGER），接入显示与排序及 JSON / ZIP 备份恢复；流派、年份和自定义分类可批量写入对应标签，分隔符设置统一为可保存或取消的底部面板。

- WebDAV、SMB、FTP / FTPS 和 SFTP 支持在音乐库设置中管理，递归扫描共享目录、搜索、分类及连续播放；SFTP 支持服务器密钥指纹确认与变化拦截。
- 搜索「所有」页的文件夹歌单分类默认开启，兼容旧分类设置和备份；修复 Release 混淆导致艺术家传记的繁转简匹配组件初始化失败。
- 空曲库可以下拉刷新，修正空状态说明；音质筛选使用底部面板并实际应用到曲库。
- 曲库分析与音质筛选复用缓存，减少重复构建；双指缩放进入详细列表后请求原始高清封面。
- 文件夹卡片随宽度自动排列并居中，显示调节入口仅放在文件夹层次结构首页。
- 修复非沉浸专辑页的随机播放入口与按钮高度，移除首页和文件夹长按的多余遮罩。

- 从「本地扫描目录」移除文件夹只保存设置并提示，不再自动触发扫描（与勾选行为保持一致，需要更新音乐库时手动扫描即可）。

### 播放页与视频

- 收缩迷你播放条支持左右滑动，并显示切歌方向与目标歌曲提示。
- 横屏新增经典分栏布局：左侧大封面与右侧动态背景渐变衔接，歌名、艺术家长文本循环滚动，顶部播放控制下方保留歌词区域。
- Miuix 横屏侧边栏默认收起并支持背景模糊；可视化增加彩虹色选项，缩短波形进度指示线。
- 歌曲、专辑和 MV 评论恢复应用内评论界面。
- 设置新增「其他 → 视频播放器」，支持外部调用、播放视频和 M3U/M3U8、视频下载及 MP4 导出。
- 普通视频隐藏歌手和伴奏入口，提供 0.5x–5x 倍速侧栏；默认长按临时 2x，松手恢复原倍速，长按倍速可自定义。

### 设置与界面

- 统一底栏返回规则：子页面逐级返回，其他标签主页回到首页，首页主页直接退出；搜索优先收起输入法，底栏搜索回到进入前的标签，其他搜索入口按页面栈返回。仅当前 Pager 页面接收返回，防止相邻隐藏页面拦截以及切换动画期间的旧页面覆盖返回目标。

- 恢复音符与海浪背景的「传统」应用图标作为可选项，并同步应用内通知图标。

- 新增「打开应用进入播放界面」（设置 → 外观 → 底栏入口 → 启动时打开）：开启后打开应用直接进入当前播放界面，而不是启动页。

### 远程曲库

- 网易云登录后同步内容新增：每日推荐歌单（含每日推荐单曲榜）和心动模式歌单——每日推荐歌单最多同步 20 个，心动模式以最近播放/喜欢的歌曲为种子生成，随「同步」一起刷新。
- 自建的 Navidrome / OpenSubsonic / Emby / WebDAV 服务器可以直接填写 `http://` 明文地址（含公网 IPv6），不再强制 HTTPS；只有在地址里内嵌账号密码时才会拒绝，并且 HTTPS 请求不会被允许跳转到 HTTP。使用 HTTP 时服务器编辑页会提示数据可能在网络中被窃听。

English Changelog

### Lyrics

- Fixed overlapping TTML vocals and simultaneous word-timed LRC/ELRC lines. Held-note text and glow now share the timed feather mask, with bounded interpolation when playback samples stall.
- Beautiful Lyrics backgrounds support inactive-line blur; settings and audio information surfaces follow the application font.
- Added historical 1.2.7, Spotify, magazine, cinematic, polaroid, calligraphy and other lyric-card styles. The historical card has rounded corners; NetEase cards use Halcyon or a custom footer name.

- Rainbow lyrics now paint one gradient per line: the highlighted line shares a single seven-color spectrum and each character only shows its own slice, instead of every character sweeping the whole rainbow.

### Library and scanning

- Empty libraries support pull-to-refresh and show accurate guidance. Audio-quality filtering uses a bottom sheet and applies to the library.
- Library analysis and quality filters reuse cached results. Pinching into detailed rows loads original high-resolution artwork.
- Folder cards wrap according to available width and center their contents; display controls appear at the hierarchy root.
- Restored shuffle in non-immersive album pages, refined action heights, and removed extra long-press overlays.

- Removing a folder from Local scan directories only saves the selection and shows a hint; it no longer starts a scan on its own (matching the row checkbox). Run a manual scan when the change should apply.

### Player and video

- Collapsed mini players support horizontal swipes with direction and target-song previews.
- Added a classic landscape split with a full-height cover, a transition into the shared dynamic background, marquee metadata, compact header controls and a larger lyric area.
- Miuix landscape navigation starts collapsed and supports background blur. Visualizers have a rainbow option; waveform seek markers are shorter.
- Song, album and MV comments open the native comments interface.
- Added Other → Video player for external intents, video and M3U/M3U8 playback, downloads and MP4 export.
- Generic videos hide artist and accompaniment actions and offer a 0.5x–5x speed sidebar. Hold for temporary 2x by default, release to restore the selected speed, and customize the hold speed in the sidebar.

### Settings and interface

- Restored the traditional note-and-wave launcher icon as an optional choice and synchronized in-app notification icons.

- New "Open the player on launch" switch (Settings → Appearance → Bottom bar entries → Startup destination): opening the app goes straight to the now-playing screen instead of the startup page.

### Remote libraries

- Signed-in NetEase sync now also brings the daily-recommended playlists (including the daily recommended songs list, capped at 20 playlists) and a Heartbeat Mode playlist seeded by a recent/favorite song; all refresh with the same sync action.
- Self-hosted Navidrome / OpenSubsonic / Emby / WebDAV servers may use a plain `http://` address (including a public IPv6 one) instead of requiring HTTPS. Credentials embedded in the address stay rejected and an HTTPS origin is never allowed to redirect down to HTTP; the server editor warns that HTTP traffic can be intercepted.

Version
- Version name: `1.3.0`
- Version code: `40`

# 1.2.9

From `1.2.8` to `1.2.9`.

中文更新日志

### 升级须知

- 从旧版本覆盖安装后会提示是否清除数据，可一键前往系统应用信息或维护页面；恢复旧版本备份时会比对设置项，发现已移除、新增或格式变化的设置会建议重新配置，也可坚持恢复。
- 反馈问题前请确认使用最新版本，并在清除数据后的干净安装中复现；旧版本覆盖安装且未清除数据时，残留设置可能导致正常使用中不会出现的问题。

### 播放、音频与播放队列

- USB DAC「独占模式」改为真正的 AAudio Exclusive 开流：bit-perfect、不做重采样，失败时回退共享模式，设置页显示实际状态。
- 修复切歌时音频格式切换时序导致的杂音与中断；MP3 统一使用 FFmpeg 解码，改善部分设备跳歌和播放中途无声的问题；播放队列改为逐首流式保存，降低大队列切歌时的内存与写入开销。
- 启动时不再在主线程等待音乐库与播放队列恢复，老设备冷启动明显加快。
- 新增「冷启动自动扫描新增歌曲」（默认开启），本地与远程曲库每次冷启动都会检查新增歌曲。
- 迷你播放条与播放页之间改为连续形变过渡，上滑打开与下滑关闭均跟手，展开态、收缩态与普通底栏共用同一效果。
- 播放列表删除、拖拽排序不再关闭面板（`#604`）；无声 MV 暂停/继续立即同步（`#605`）；定时关闭倒计时直接显示在快捷区与更多菜单。

### 在线音乐

- 新增在线音乐账号作为音乐库来源：收藏歌曲作为音乐库，账号歌单显示在歌单页，听歌记录可并入最近播放。
- 支持播放音质与下载音质分别选择、播放缓存（切回已播歌曲不再重复下载，可在维护中清除）、下载到本地并写入完整标签、歌词、封面与翻译 / 音译，下载内容显示在文件夹的「本地下载」中。
- 支持在线 MV 播放与画质切换、评论区浏览 / 点赞 / 回复，艺术家图片、简介与专辑介绍可直接在线获取；播放页长按音质标签可切换音质，长按音频信息查看详细信息。
- 恢复在线音源插件支持：可从本地文件、链接或插件仓库导入，搜索来源按插件声明的平台自动生成；应用不内置任何音源。
- 首页右下角搜索入口可选择搜索本地音乐库或在线来源。
- 软件更新页右上角可选择稳定版或预发布渠道，并修复预发布版本号比较。

### 播放页与可视化

- 进度条新增波形与分段刻度两种样式：打开即逐步显示真实波形，暂停时保持形态轻微缩放；可调波形密度、峰值高度与缩放动画开关（`#674`，已修复待验证）。
- 可视化重做：默认曲线参考 BetterLyrics，律动粒子 / 共鸣琴弦 / 经典柱状 / 律动水波参考光锥重写，新增覆盖在封面上的频谱样式；所有样式高度统一并可调节，支持透明度与模糊，可用于全部横竖屏播放页。
- 播放页新增标题居中选项、查看 MV 操作（可加入快捷操作与竖向菜单），Apple Music 风格的收藏与更多按钮在 Apple Music 播放页默认开启。
- 歌词 HDR 高亮在支持 HDR 的屏幕上真正提亮长音发光与辉光进度条（Android 14 及以上）。
- 平板与手机横屏统一使用可展开的侧边栏，横屏液态玻璃播放条保持样式。

### 歌词与分享

- 逐字歌词动画参考 Apple Music：逐词上浮、渐变高亮、逐行弹簧跟随；新增「长音上浮与呼吸」开关，并修复靠近底部的歌词行丢失弹簧动画。
- TTML 注音按整词居中对齐（含一个词对应多段读音的情况）；TTML 支持文件偏移、父级时间与更多时间格式（`#676`，已修复待验证）。
- 在线匹配歌词可选择保存为外置 `.lrc` / `.ttml` 文件，不修改歌曲文件本身；保存后该歌曲优先显示外置歌词，修复未标注演唱者的 TTML 覆盖歌词居中设置的问题。
- 歌词分享卡片为 Apple Music 贴纸样式并新增一种简约大字样式，歌曲来源可识别时附带歌曲二维码；歌词视频分享修复换句时上一句残留。
- 深度适配小米 HyperOS 超级小岛 / 焦点通知歌词，在线歌曲也显示封面；歌词源支持 Lyrico Plugin API 5 规范插件。

### 音乐库、扫描与标签

- 两阶段扫描：几秒内先显示全部歌曲与封面，后台再补全完整标签；音乐库改为下拉刷新。
- 扫描目录勾选仅保存设置并提示手动扫描，可按需选择扫描或强制重扫，离开设置页不再因此自动扫描。
- 修复最近播放切换标签及滑动预览时内容错位、日期标题被遮挡的问题；补全在线歌曲历史信息，支持封面展示、播放及更多菜单。
- 音乐库支持双指缩放在列表、封面网格与详细信息视图之间切换，切换时封面连续过渡。
- 修复专辑名与所在文件夹同名时显示为「未知专辑」；自定义艺术家分隔符修改后立即重新分组，支持一行输入多个符号，字母分隔符不再误拆单词（`#675`，已修复待验证）。
- 艺术家图片来源新增多个可选来源并可排序，同名艺术家优先精确大小写匹配（`#668`，已修复待验证）；专辑介绍与艺术家简介每次可选择获取来源。
- 音乐库与歌单支持红心 / 星级漏斗筛选；标签编辑器改为分卡片布局。

### 设置、界面与兼容性

- 界面整体打磨：统一弹层、卡片与对号样式，设置页顶栏支持渐进模糊（可关闭），首页快捷按钮与功能块分开配置并新增多种功能块风格；优化歌词页进入时的字号过渡、沉浸歌词封面切歌手势和升级提示文案，以及大量间距、图标与细节调整。
- 设置搜索覆盖各级页面中的具体选项，并能跳转到对应位置、自动展开所在弹层。
- 设置向导新增音乐库来源、冷启动自动扫描、底栏样式、浏览页流光（低端设备建议关闭）与播放页背景明暗选项；修复向导跳转设置页后反复覆盖的问题。
- 新增土耳其语与阿拉伯语，共支持 10 种界面语言并适配 RTL 布局；内置 Inter 与 MiSans 字体。
- 修复部分设备闪退与卡顿：超级岛歌词闪退、闪退日志过大导致打开卡顿甚至崩溃、老设备启动缓慢、车机合并搜索入口无法取消等。
- 桌面组件暂停时停止计时（`#611`，已修复待验证）；开头空白歌词可拖动跳转（`#615`，已修复待验证）。

English Changelog

### Upgrade notes

- After installing over an older version, Halcyon offers to clear app data and can open system App info or the Maintenance page. Restoring an older backup compares settings keys and suggests reconfiguring when settings were removed, added, or changed format; you can still restore anyway.
- Before reporting a problem, please use the latest version and reproduce it on a clean install after clearing app data. Stale settings from an old install can cause bugs that do not occur in normal use.

### Playback, audio, and queue

- USB DAC exclusive mode opens true AAudio Exclusive: bit-perfect without resampling, shared fallback on failure, and the real status in settings.
- Fixed crackles and dropouts caused by audio-format switching order on track changes; MP3 now uses FFmpeg in every decoder mode to address skipped tracks and mid-song silence on some devices. The queue is saved song by song, reducing memory and writes for large queues.
- Startup no longer waits on the main thread for the library and queue to restore, so cold starts on older devices are much faster.
- New "Scan for new songs on cold start" (on by default) for local and remote libraries.
- The mini player morphs continuously into the player: swipe up to open and down to close, both following the finger, for expanded, collapsed, and standard docks alike.
- Removing or reordering queue items no longer closes the sheet (`#604`); silent MV pause/resume syncs immediately (`#605`); an active sleep timer shows its countdown in shortcuts and the more menu.

### Online music

- An online music account can be used as the library source: liked songs become the library, account playlists appear under Playlists, and listening history can merge into Recently Played.
- Separate playback and download quality, a playback cache (replaying a song does not download it again; clearable in Maintenance), and downloads with full tags, lyrics, artwork, and translation / transliteration, listed under Folders → Local downloads.
- Online MV playback with quality switching, comments with likes and replies, and online artist images, biographies, and album introductions. Long-press the quality badge to switch quality; long-press audio details to see stream info.
- User-supplied online source plugins are supported again, imported from local files, links, or plugin repositories; search sources come from the platforms each plugin declares. No sources are bundled.
- The home search button can search the local library or an online source.
- The update page can check the Stable or Prerelease channel, and prerelease version comparison is fixed.

### Player and visualizers

- New Waveform and Segments progress bars: the real waveform appears progressively right away and keeps its shape (slightly scaled) when paused; density, peak height, and the scale animation are adjustable (`#674`, fixed, pending verification).
- Visualizers rebuilt: the default curve follows BetterLyrics, the particle / string / bar / wave styles are rewritten after ConePlayer, and a new spectrum overlays the album cover. All styles share one adjustable height, support opacity and blur, and work on every portrait and landscape player.
- The player adds a centered-title option and a View MV action (available in quick actions and the vertical menu). Apple Music-style favorite and more buttons are on by default on the Apple Music player.
- Lyric HDR highlight now truly brightens sustain glow and the glow progress bar on HDR-capable screens (Android 14+).
- Tablets and landscape phones use an expandable side rail; the landscape liquid-glass mini player keeps its style.

### Lyrics and sharing

- Word-by-word lyrics follow Apple Music: per-word lift, gradient fill, and per-line spring follow. A "long-note float and breathe" switch is added, and the lowest lyric lines no longer lose their spring animation.
- TTML readings are centered over whole words, including words with several reading segments. TTML honors file offsets, parent timing, and more time formats (`#676`, fixed, pending verification).
- Online lyric matches can be saved as external `.lrc` / `.ttml` files without modifying the audio file; the saved file is then preferred for that song. TTML without singer assignments no longer overrides the selected lyric alignment.
- Lyric share cards use the Apple Music sticker layout plus a new bold minimal style that adds a song QR code when the source is recognized. Lyric video sharing no longer leaves the previous line behind.
- Deep HyperOS Super Island / Focus notification lyrics, now also showing artwork for online songs; lyric sources support Lyrico Plugin API 5 plugins.

### Library, scanning, and tags

- Two-stage scanning shows all songs and artwork within seconds, then completes full tags in the background; the library uses pull-to-refresh.
- Selecting scan folders only saves the selection and prompts for a manual scan. Choose Scan or Force rescan as needed; leaving settings no longer starts a scan because of selection changes.
- Fixed incorrect content during Recently Played tab switches and swipe previews, and hidden date headings. Online song history now retains the information needed for artwork, playback, and the more menu.
- Pinch to switch the library between list, cover grid, and detail views, with continuous artwork transitions.
- Albums named like their folder no longer show as "Unknown Album"; custom artist separators regroup immediately, accept several symbols on one line, and letter separators no longer split words (`#675`, fixed, pending verification).
- More selectable, reorderable artist-image sources, preferring exact-case artist matches (`#668`, fixed, pending verification); album introductions and artist biographies let you pick the source each time.
- Library and playlist loved / star-rating funnel filters; the tag editor uses section cards.

### Settings, interface, and compatibility

- Broad interface polish: consistent sheets, cards, and check marks, optional progressive blur on settings top bars, separately configured home shortcuts and feature tiles with new tile styles, lyric-page entry sizing, immersive-lyrics cover gestures, upgrade prompts, and many spacing, icon, and detail refinements.
- Settings search covers specific options on every level and jumps to them, opening the containing sheet when needed.
- The setup wizard adds library source, cold-start scanning, dock style, browse-page flow background (not recommended on low-end devices), and player background brightness; it no longer keeps covering settings pages it opens.
- Turkish and Arabic added for 10 interface languages with RTL layout; bundled Inter and MiSans fonts.
- Fixed crashes and stalls on some devices, including the Super Island lyric crash, oversized crash logs that stalled or crashed the log viewer, slow startup on older devices, and the car-display merged search entry that could not be removed.
- Widgets stop their timer while paused (`#611`, fixed, pending verification); opening blank lyrics can be scrubbed (`#615`, fixed, pending verification).

Version
- Version name: `1.2.9`
- Version code: `39`

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
