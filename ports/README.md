# Halcyon iOS 与桌面移植

统一开发分支为 `multiplatform`：同一分支包含根目录 Android 工程、`ports/ios` 原生工程和 `ports/desktop` 桌面端，共用 `ports/shared` 界面与业务逻辑。共享界面使用 React / TypeScript，桌面采用 Electron，iOS 采用 WKWebView 界面与 AVAudioEngine 本地音频与 AVPlayer 在线音频。最低 iOS 16，支持 iPhone 和 iPad；桌面打包目标为 Windows、macOS、Linux。

## 一次构建所有平台

推送到 `multiplatform` 会触发 **Multiplatform Build**，在同一个 Actions run 中并行构建：

- Android：arm64-v8a debug APK；手动运行可选择 release（沿用现有 CI 的 debug 签名 release 配置）。
- iOS：iPhone / iPad 模拟器应用、未签名真机应用，以及模拟器 UI / 音频测试结果。
- 桌面：Windows 安装版 / 便携 EXE、macOS DMG / ZIP、Linux AppImage / DEB。

在 GitHub **Actions → Multiplatform Build → 本次运行 → Artifacts** 下载全部产物。单个平台失败不会阻止其他平台完成构建，最后的 Build summary 汇总三类平台结果。同一版本需重新打包时，可在该次运行页面点击 **Re-run all jobs**。暂未合入默认分支时，新的工作流可能不显示 **Run workflow** 按钮；完成首次自动运行后，可用 `gh workflow run multiplatform-build.yml --ref multiplatform -f android_build_type=debug` 手动同时构建（`release` 选项沿用现有 Android CI 签名配置）。不自动发布 Release。工作流复用现有三份平台构建配置，保证 Android 与移植版使用同一提交。

## 运行桌面版

需要 Node.js 24：

```bash
cd ports
npm ci
npm run desktop
```

生成安装包：`npm run package:desktop -- --publish never`。产物在 `ports/desktop-dist/`。当前构建没有发行者签名或 macOS 公证，发布正式安装包前需配置签名。首次启动是空曲库，选择“导入音乐”或“导入文件夹”即可使用。桌面保存音频引用，移走原文件后需重新导入。

Linux 的密码曲库需要系统密钥服务（例如 GNOME Keyring 或 KWallet）；安全存储不可用时不会把密码以明文保存。无密码曲库和本地播放不依赖密钥服务。

## 构建 iOS

在 macOS 上安装 Xcode 16 或更新版本以及 Node.js 24：

先切换统一分支：`git fetch origin multiplatform && git switch multiplatform`（首次使用可执行 `git switch --track origin/multiplatform`）。

```bash
cd ports
npm ci
npm run prepare:ios
open ios/Halcyon.xcodeproj
```

在 Xcode 的 Signing & Capabilities 选择自己的 Team；必要时修改 Bundle Identifier。选择 iPhone / iPad 或模拟器运行。未准备 Web 资源时不能加载界面；共享代码变更后重新执行 `npm run prepare:ios`。

无需 CocoaPods 或第三方 Swift 包。iOS 文件通过 Files 选择器导入并复制到应用 Documents，支持 iCloud 与本地文件、文件夹导入；首次从云端导入需要等待文件下载。直接选择单个音频时，iOS 可能不允许访问相邻歌词文件，可以再从歌曲菜单导入歌词；选择文件夹时会同时复制同名歌词。导入的原文件不会修改或删除。

命令行构建模拟器：

```bash
xcodebuild -project ios/Halcyon.xcodeproj -scheme Halcyon \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath ios/build CODE_SIGNING_ALLOWED=NO build
```

GitHub Actions 的 iOS 构建产物是未签名 `.app`，不能当作可直接安装的 IPA。真机安装和 TestFlight 需使用你的 Apple 开发者签名与分发配置。

## 0.2 功能与边界

| 功能 | 当前实现 |
| --- | --- |
| 本地音乐导入 | 文件、文件夹递归导入；读取标题、艺术家、专辑、时长、封面及采样率 / 位深 / 码率；重复导入按文件身份合并 |
| 曲库浏览 | 歌曲、专辑、艺术家、文件夹；专辑按专辑名与艺术家区分；排序、倒序、音源 / 格式筛选 |
| 搜索 | 歌名、艺术家、专辑、文件名、可用流派信息 |
| 播放 | 播放 / 暂停、上一首 / 下一首、拖动进度、音量、随机、列表 / 单曲循环、下一首播放、队列追加 / 上下移动 / 删除 / 清空，暂停状态切歌保持暂停，0.5–3 倍速与保持音高 |
| iOS 系统播放 | 原生队列管理、后台自动切歌、锁屏控制与元数据、耳机断开暂停、音频中断恢复、AirPlay 选择 |
| 收藏与歌单 | 收藏、创建 / 重命名 / 删除歌单、加入 / 删除 / 上下移动歌曲；JSON / M3U 导入与导出 |
| 歌词 | 同名外置 LRC / ELRC / TTML，基础内嵌歌词读取，逐行同步、增强 LRC 逐词进度高亮；常见 TTML 逐词、重叠人声、翻译 / 罗马音 / 背景人声、时序继承与帧时间；点击跳转、偏移、字号 |
| 远程曲库 | WebDAV Basic 认证与目录浏览；Navidrome / OpenSubsonic search3 分页、专辑枚举回退与 token 认证；Emby 用户登录与分页；流媒体播放、封面；WebDAV 递归扫描与侧车歌词、Subsonic 服务端歌词、可取消的离线下载 |
| 显示信息修改 | 应用内修改标题、艺术家、专辑，单独保存；尚不写回音频标签 |
| 最近播放与统计 | 达到实际播放阈值后计次、最近播放、基础数量统计；iOS 原生统计覆盖后台，回前台后同步记录，支持删除历史记录 |
| 设置与备份 | 系统 / 浅色 / 深色、强调色、动态背景、睡眠定时 / 播完当前歌曲、数据备份 / 恢复；不含音频或凭据 |

| 音效 | 桌面 Web Audio 10 段 EQ / 总增益 / ReplayGain；iOS 原生 10 段 EQ / 总增益，本地与下载歌曲生效；预设及自定义参数持久化 |
| 歌词工具 | 逐行 / 逐词打轴、撤销 / 重做、导出增强 LRC、保存同名外置歌词、SVG 歌词分享卡 |
| 封面与 MV | 自定义图片封面、本地 MV 手动关联；桌面视频控件 / iOS 系统视频播放器 |
| AI 助手 | 自定义 OpenAI 兼容 / DeepSeek / Anthropic 服务，曲库内推荐、保存推荐歌单、当前歌曲解读；Key 原生加密保存 |

远程来源在客户端原生层发送请求，不要求服务器额外配置浏览器 CORS。WebDAV 当前支持 Basic，尚未接 Digest。Subsonic 在空关键词 search3 不支持或无结果时尝试专辑枚举；服务端能力各不相同，使用前仍需用真实服务器核对。远程收藏与歌单目前保存在 Halcyon 本地，不写回服务器。下载保留歌曲身份，完成后可从“离线下载”筛选并断网播放；移除曲库记录不会删除音频文件，当前没有缓存配额管理。

音频是否可播放取决于 Chromium 或 Apple 系统解码器。例如 WMA / 部分 Ogg / Opus 在 iOS 上可能不可用，尚未移植 FFmpeg 回退。iOS 在线播放走 AVPlayer，倍速可用，EQ 与总增益需要先下载；iOS ReplayGain 元数据读取尚未接齐。复杂 TTML 全规范、Lyricify 全格式和 AMLL 原动画仍未完整移植，打轴器导出增强 LRC 时不会保留 TTML 角色与样式。MV 为手动关联本地文件，不抓取在线 MV。

AI 功能需自行配置服务和密钥，点击请求会发送明确提示的歌曲元数据 / 歌词或曲库名称。协议测试使用本地测试服务器，没有调用实际付费模型。

仍未移植：FFmpeg、标签写回、无缝 / 交叉淡化、在线账号与 JS 音源插件、Last.fm、MCP、小组件和桌面悬浮歌词。Android 状态栏歌词、HyperOS 小岛、USB 独占及系统音效不能直接复用到 iOS。待办与真机验收见 [PORTING.md](PORTING.md)。

## 验证与自动构建

```bash
cd ports
npm test
npm run build
```

Linux 的 Electron 实际播放集成测试：`xvfb-run -a node desktop/smoke.mjs`（先执行 `npm run build`，并安装 Xvfb 与 FFmpeg）。该测试使用独立临时数据目录，验证原生文件 / 文件夹导入、真实媒体协议播放与 Web Audio 信号、EQ / 倍速 / 进度、队列、歌单、歌词工具、封面 / 显示信息、备份恢复、完整进程重启、离线歌曲移除、MV 与缺失文件恢复。

`Desktop Port` 工作流分别在 Windows / macOS / Linux 上测试和打包，Linux 额外运行真实 Electron 播放测试。`iOS Port` 工作流编译模拟器与未签名真机应用，并在 iPhone 模拟器检查界面加载、原生数据桥就绪、歌单创建和重启后的持久化，以及本地 PCM 原生音频引擎、EQ、播放进度、暂停切歌和队列编辑。原生 XCTest 另外使用临时目录与独立 Keychain 账户，验证文件 / 歌词持久化、凭据、HTTP 协议、下载 / 取消、断网播放、播放结束策略、统计与睡眠定时；iPad 也执行播放与队列测试。详见 [QA.md](QA.md)。编译或模拟器测试通过不等于后台播放、系统路由或实际服务器连接已在真机验证。
