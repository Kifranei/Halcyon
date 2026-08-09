<!--suppress ALL -->

<h1 align="center">Halcyon</h1>

<p align="center">
  <b>一款贴近 MIUI / HyperOS 体验的 Android 音乐播放器</b>
</p>

<p align="center">
  <a href="https://github.com/Kifranei/Halcyon/releases"><img src="https://img.shields.io/github/v/release/Kifranei/Halcyon?style=flat&color=6750A4" alt="Version"></a>
  <a href="https://github.com/Kifranei/Halcyon/releases"><img src="https://img.shields.io/github/downloads/Kifranei/Halcyon/total?style=flat&color=orange" alt="Downloads"></a>
  <a href="https://github.com/Kifranei/Halcyon/commits"><img src="https://img.shields.io/github/last-commit/Kifranei/Halcyon?style=flat" alt="Last Commit"></a>
  <a href="https://github.com/Kifranei/Halcyon/blob/main/LICENSE"><img src="https://img.shields.io/github/license/Kifranei/Halcyon?style=flat" alt="License"></a>
  <a href="README_en.md"><img src="https://img.shields.io/badge/Document-English-blue.svg" alt="EN"></a>
</p>

<p align="center">
  <a href="https://qm.qq.com/q/6MHSXRrjTq"><img src="https://img.shields.io/badge/QQ交流群-0084FF?style=flat&logo=qq&logoColor=white" alt="QQ Group"></a>
  <!-- <a href="https://t.me/halcyonplayer"><img src="https://img.shields.io/badge/Telegram-0084FF?style=flat&logo=telegram&logoColor=white" alt="Telegram"></a> -->
</p>

<p align="center">
  <b>本地音乐 · 在线曲库 · 动态播放页 · 逐字歌词 · 桌面歌词 · 状态栏歌词 · 多语言界面</b>
</p>

---

## ✨ 项目简介

**Halcyon** 是一款基于 **Jetpack Compose、Miuix 和 AndroidX Media3** 构建的 Android 本地音乐播放器。

它以本地音乐和歌词体验为核心，提供 MIUI / HyperOS 风格界面、Compose 逐字歌词、桌面歌词、状态栏歌词、动态封面、应用内均衡器、Monet 动态取色、在线歌词匹配、WebDAV / Navidrome / Emby 远程曲库、LX Music API 在线音源、Last.fm 听歌历史、音乐库统计、完整应用数据备份和高度可定制的播放页体验。

---

## 🚀 功能特性

### 🎵 音乐库与歌单

- 支持本地媒体库与自定义文件夹扫描，提供专辑、艺术家、文件夹、流派、年份、作曲家和作词家等多维度浏览；长按扫描按钮可触发完整读取标签的重扫。
- 首次扫描前会询问是否启用全标签搜索：启用后可搜索作曲、作词、注释、别名和自定义标签；关闭后使用更快的基础媒体库扫描，仅搜索歌名、艺术家、专辑等基本信息。
- 音乐库来源可在本地、Navidrome 和 Emby 间切换，切换后会刷新当前来源，避免继续显示上一来源的歌曲。
- 提供独立音乐库搜索页，支持歌曲、专辑、歌手、歌词、重复歌曲和全标签搜索，支持搜索历史、批量选择和范围选择。
- 支持本地歌单、收藏歌单、五星歌曲、歌单导入 / 导出、桌面快捷方式和自定义拖拽排序。
- 专辑识别会同时考虑专辑名与专辑艺术家，避免不同艺术家的同名专辑互相合并。
- 专辑详情页支持独立的专辑介绍页面和手动编辑；本地专辑优先写入同目录 `album.nfo` 的 `<review>`，目录不可写时自动保存到应用内。
- 支持音乐库统计分析、听歌日历、播放次数排行、听歌时长排行、格式分布和音质分布。
- 支持 Last.fm 授权、完整历史同步、自动 Scrobble 与本地 / Last.fm / 合并听歌历史视图；记录可逐项删除，Last.fm 缓存记录可仅在本地隐藏；敏感凭据由 Android Keystore 加密且不会写入备份。
- 音乐库分析支持缓存与扫描后预热，较大的本地曲库也能更快打开统计页面。

### 🖼 播放页与动态封面

- 提供可指定的横屏播放样式，并支持分别显示或隐藏状态栏、导航栏以及保留被隐藏系统栏的布局占位，兼顾手机、平板、车机和超宽屏。
- 新安装默认使用非沉浸圆角封面播放页；非 1:1 封面会按实际图片边界应用圆角。
- 支持动态视频封面，可按歌曲、专辑或全局 fallback 匹配视频封面。
- 支持自定义艺术家封面文件夹，并可在艺术家页使用动态艺术家视频封面。
- 艺术家静态封面按自定义资源、独占/合作专辑艺术家、独占/合作歌曲艺术家的顺序选择。
- 支持全局自定义壁纸、开屏海报、自定义 Hi-Res 标识和播放页按钮轮廓。
- 支持 Beautiful Lyrics 风格动态背景，可应用于歌词页、平板横屏播放页和横屏封面页，并提供速度、模糊和亮度调节。
- Compose 歌词页支持 Apple Music 风格动态背景、逐词上浮、平滑重排及沉浸式歌词 / 播放页之间的一致过渡。
- 支持 Monet 动态取色，可从系统壁纸或当前歌曲封面生成全局强调色。
- 非沉浸播放页可为 Hi-Res / MQ 音质歌曲显示封面角标。
- 播放页支持跟手下拉关闭、动态背景、模糊背景、封面左右滑切歌和横屏队列封面切换；平板横屏可在底部播放条和紧凑导航条显示当前歌词。
- 支持本地 MV 与网易云 MV：本地视频支持 MP4、MKV、WebM 和 MOV，可在播放页同步静音播放并在详情页独立有声播放；`163 key` 含 `mvid` 时，详情页会额外显示明确标注的“网易云 MV”跳转。两者可以同时存在。
- 信息页打开本地 MV 默认直接进入横屏，并支持手动/后台自动画中画、辉光进度条、截图分享、半透明可滚动字幕设置侧栏、可拖动/锁定字幕、KTV 歌词、伴奏测试以及 LunaBeat `mv_offsets.json` 字幕偏移。
- 新安装默认开启 Apple Music 背景流动、播放控制按钮轮廓、进度条总时长和同步播放无声 MV；升级用户已保存的选择不会被覆盖。
- 长按播放页封面可预览原图，支持双击缩放、单指拖动、分享和保存。

### 🎤 歌词体验

- 支持 LRC、增强 LRC、ELRC、TTML、AMLL TTML 和 Lyricify 歌词解析。
- 支持逐字 / 逐词歌词、逐行 TTML、翻译、罗马音 / 注音、背景人声、TTML 对唱和 ELRC V1/V2 对唱。
- 歌词页更多菜单将字号、缩放和透视角度等滑动条收纳到二级「歌词样式」页，减少一级菜单长度。
- 支持外置歌词和内嵌歌词读取，并可从同名 `.lrc`、`.ttml`、`.elrc` 文件自动匹配。
- 支持本地歌曲在线匹配歌词：基于 Lyrico 兼容插件，可从 zip 合集导入 / 删除歌词源插件，支持插件配置，匹配结果可写入内嵌标签、`TTMLLYRIC` 标签或 `.lrc`。
- 提供桌面歌词悬浮窗、状态栏歌词、媒体通知歌词、词幕、SuperLyricApi 和 Lyric Getter API 集成。
- 状态栏歌词的长文本会带间隔连续循环，避免滚动结束时闪跳。
- 可按需隐藏歌词中的作词、作曲及简写“词 / 曲”等额外信息；MV 字幕可独立控制是否显示歌词翻译。
- 支持歌词卡片分享、字体导入与系统字体选择器、歌词偏移、歌词点击跳转和副行内容配置。
- 内置全屏歌词打轴器：支持逐行 / 逐词打点、固定底部传输控制、撤销 / 重做、V1 / V2 / V1000 对唱角色、翻译、音译、`x-bg` 背景人声，并可内嵌或导出 LRC、ELRC、TTML。

### 🌐 WebDAV、Navidrome、Emby 与 LX 在线音乐

- 支持 WebDAV 远程曲库，提供连接测试、Digest 认证、远程目录浏览和远程音频播放。
- 支持 Navidrome / Subsonic 与 Emby 音乐库入口，使用和 WebDAV 一致的目录浏览式体验与输入框样式。
- Navidrome / Emby 远程音乐库支持分页加载和整库缓存，Navidrome 超过 500 首的曲库会继续翻页加载。
- 支持 LX Music API 音源导入、在线搜索、在线播放、封面 / 歌词获取和下载到本地。
- 提供局域网 Web 音乐服务 Beta，可在可信局域网内通过浏览器浏览、播放和上传音乐。当前版本没有访问密码，请勿在公共网络或端口映射环境中开启。

### 🎚 音效、解码、标签与音质

- 提供应用内软件 10 段参数均衡器，不依赖系统 Equalizer，并根据设备能力显示低音增强和虚拟化选项。
- 支持原生 Oboe 音频输出和 USB DAC 独占模式；交叉淡入淡出支持恒定响度、线性、平滑和保持原音量曲线。
- 本地音频标签读取使用 lyrico-audiotag 主路径，支持常见音频格式的封面、基础标签、内嵌歌词和多值标签读取。
- 内置标签编辑器支持编辑基础标签、歌词、歌曲封面和交互式星级评分。
- 提供系统解码、FFmpeg 解码和自动解码模式，提升 ALAC / AAC / M4A 等格式兼容性。
- 支持 ReplayGain、随机队列恢复、音质标签展示和 24-bit / 96 kHz 等规格识别。
- 支持 163 key 读取，可从独立 tag、Comment 和 Description 中提取歌曲、专辑、歌手与 `mvid`，并跳转对应的网易云歌曲页、专辑页、歌手页和 MV 页。
- 提供本地音频工具，可进行格式转换、多音频流导出和 CUE 整轨分轨；内置频谱查看器也可直接跳转 Aspect Pro 或 Kaspek。

### 🎨 界面与扩展

- 基于 Miuix 0.9.3 构建 MIUI / HyperOS 风格界面，包含悬浮底部导航、迷你播放器、模糊 / 液态玻璃效果和统一的弹窗样式；深色系统下启动界面保持深色，避免启动遮罩闪白。
- 支持 8 种界面语言、应用内语言切换、应用字体大小与整体界面缩放、GitHub 更新页、应用日志、完整应用数据备份 / 恢复和 Prism Music 听歌历史导出。
- 支持切换应用图标、配置长按桌面图标的快捷方式、在首页分类卡片创建快捷方式，以及紧凑 / 扩展两种桌面播放小组件；小组件显示持久化封面、封面取色模糊背景、实时播放时间和控制按钮，并提供防止非 4×6 桌面网格裁切的兼容布局开关。
- 支持歌曲信息查看、标签编辑、歌词打轴软件跳转、外部标签编辑器适配和 AI 歌曲解读。
- 支持 MediaSession 自定义命令，通知 / 控制中心可显示收藏和播放模式按钮。

### 🤖 AI 与 MCP

- 支持 OpenAI 曲库听歌助手：可根据本地曲库、最近播放和听歌统计生成推荐歌单、回答音乐偏好；只会读取曲库并播放本地歌曲，不会删除或修改文件。
- 内置 MCP 服务器，基于官方 Kotlin SDK、Ktor CIO 和 Streamable HTTP，可让 Claude Desktop 等 MCP Host 控制 Halcyon 播放。
- 开启路径：设置 → MCP 服务器 → 开启；连接地址：`http://<设备IP>:8384/mcp`。
- 当前提供 10 个 tools：`play_song`、`search_music`、`get_now_playing`、`skip_next`、`skip_previous`、`toggle_play_pause`、`toggle_shuffle`、`seek_to`、`get_queue`、`get_library_stats`。
- 当前提供 2 个只读 resources：`halcyon://playback/current`、`halcyon://library/stats`。
- MCP 服务器以 Android Foreground Service 运行；关闭设置开关后会停止监听。

---

## 📱 运行要求

| 项目 | 要求                              |
|:--|:--------------------------------|
| Android 版本 | Android 11 / API 30 或更高版本       |
| Target SDK | Android 17 / API 37             |
| 默认 ABI | `arm64-v8a`                     |
| 网络 | WebDAV、LX 在线音源和在线歌词需要网络         |
| 视频权限 | Android 13+ 使用动态视频封面时可能需要视频媒体权限 |
| 悬浮窗权限 | 使用桌面歌词时需要                       |
| 通知权限 | Android 13 及以上需要                |

---

## 📦 下载

请从 [Releases](https://github.com/Kifranei/Halcyon/releases) 下载最新版本。

首次使用建议流程：

1. 安装 Halcyon。
2. 授予音乐文件访问权限，并选择扫描模式（媒体库扫描或自定义文件夹扫描）。
3. 扫描完成即可使用。 如需在其他页面上显示歌词，请前往设置页面开启。
4. 如使用远程曲库，请自行配置 WebDAV。
---

## 🖼 动态视频封面

动态视频封面用于播放页封面区域。推荐使用专辑级配置：

```text
Music/
├── 专辑名.mp4
├── 歌曲A.flac
├── 歌曲B.flac
└── 歌曲C.flac
```

或
```text
Music/专辑名文件夹/
├── cover.mp4
├── 歌曲A.flac
├── 歌曲B.flac
└── 歌曲C.flac
```

同一专辑中的所有歌曲可以共用同一个视频，避免为每首歌曲重复存放视频文件。

支持集中管理：

```text
Movies/Halcyon/DynamicCovers/
├── Album/
│   └── Album Name.mp4
├── Song/
│   └── Artist - Title.mp4
└── cover.mp4
```

也支持单文件配置：
```text
Music/歌曲文件名.m4a
Music/歌曲文件名.mp4
```

实际匹配顺序以实现为准：通常会先检查歌曲所在本地文件夹，再检查 DynamicCovers 下的歌曲 / 专辑视频，最后使用全局 fallback 视频。

---

## 🎬 本地 MV、网易云 MV 与字幕偏移

### 推荐方式：使用独立 MV 文件夹

前往「设置 → 外观 → MV 文件夹」选择一个或多个只存放 MV 的目录。独立 MV 文件夹支持：

- 容器格式：`.mp4`、`.mkv`、`.webm`、`.mov`。
- 文件名：`歌手 - 歌名`、`歌手-歌名`、`歌名 - 歌手` 或音频文件名。
- 多歌手歌曲会同时尝试完整艺术家字符串和拆分后的首位艺术家。
- 文件名可以直接使用，也可以追加 `_MV` 或 `-MV`。

示例：

```text
Movies/My Music Videos/
├── Charlie Puth - Attention.mkv
├── Charlie Puth-We Don't Talk Anymore.mp4
├── See You Again - Wiz Khalifa.webm
└── 01 One Call Away-MV.mov
```

### 与歌曲放在一起

为避免把普通动态封面误判成 MV，歌曲同目录和传统 `DynamicCovers` 目录中的 MV 必须带 `_MV` 或 `-MV` 后缀：

```text
Music/Nine Track Mind/
├── 01 One Call Away.flac
├── 01 One Call Away_MV.mkv
├── 02 Dangerously.flac
└── Charlie Puth - Dangerously-MV.mp4
```

匹配优先级为：独立 MV 文件夹 → 歌曲同目录 → 传统动态封面目录。普通动态视频封面仍只使用 MP4，不会把 MKV MV 当作循环封面。

播放页左侧信息页会分别显示：

- **MV 分类 → 本地 MV**：显示视频缩略图与时长，点击后使用 Halcyon 内置播放器有声播放。
- **网易云分类 → 网易云 MV**：当歌曲的 `163 key` 含非零 `mvid` 时显示，点击跳转 `https://y.music.163.com/m/mv?id=<mvid>`。

若两者都存在，会在各自分类中同时显示；网易云 MV 不会挤占或替代本地 MV，也不会参与播放页静音同步。

### LunaBeat MV 偏移

Halcyon 支持 LunaBeat 的 `mv_offsets.json`。可以把文件放在本地 MV 同目录，或从「设置 → 音乐库 → 标签与刮削 → LunaBeat MV 偏移值」导入：

```json
{
  "offsets": {
    "Charlie Puth - Attention.mkv": 1.25,
    "01 One Call Away_MV.mp4": -0.4
  }
}
```

键名是 MV 的完整文件名，数值单位为秒；正数让字幕/歌词更晚出现，负数让字幕/歌词提前出现。导入的文件同时适用于详情页 MV 与播放页横屏 MV。

> MKV、WebM 和 MOV 是容器格式，能够识别文件不等于设备一定能够解码其中的视频与音频。常见 8-bit H.264、H.265 和 VP9 通常可以直接播放；H.264 High 10、部分 HEVC Main 10 以及 TrueHD 组合需要设备提供相应硬件解码能力。Halcyon 当前没有 FFmpeg 视频软解回退，不兼容时会播放失败。

---

## 🛠 构建

```bash
git clone https://github.com/Kifranei/Halcyon.git
cd Halcyon
./gradlew :app:assembleDebug -PellaAbi=arm64-v8a
```

Windows PowerShell：

```powershell
git clone https://github.com/Kifranei/Halcyon.git
cd Halcyon
.\gradlew.bat :app:assembleDebug -PellaAbi=arm64-v8a
```

Release 构建会优先读取以下环境变量：

```bash
RELEASE_STORE_FILE
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

如果未设置这些变量，会使用项目根目录下的 `release.jks`。如果没有可用的 release keystore，本地 release 构建默认失败；在 CI 或显式设置 `ALLOW_DEBUG_SIGNED_RELEASE=true` 时，会改用 debug 签名产出 release APK，便于 GitHub Actions 提供可测试安装包。

日常开发建议使用 `assembleDebug` 验证；`fastRelease` / release 构建仅在发版时使用。默认 native 库走预编译 `.so` 打包，如需更新 FFmpeg 或 lyrico-audiotag native，再手动运行对应脚本重新生成。发版后请同步推送 GitHub 与 GitLab 远端。

---

## 🎧 native 库

预编译的 FFmpeg 与 lyrico-audiotag native 库默认位于：

```text
ffmpeg-decoder/src/main/jniLibs/arm64-v8a/libffmpegJNI.so
lyrico-audiotag/src/main/jniLibs/arm64-v8a/liblyrico_taglib.so
```

如需在 fresh clone 后恢复 FFmpeg 预编译输入，请先运行：

```powershell
.\scripts\download_ffmpeg_prebuilt.ps1
```

如需在 Windows 上手动更新 FFmpeg native，请运行：

```powershell
.\build_ffmpeg.ps1
```

如需更新 lyrico-audiotag / TagLib native 产物，请运行：

```powershell
.\build_lyrico_taglib.ps1
```

普通 `assembleDebug` 不会默认重新编译 native；发版前确认 APK 内包含所需 arm64-v8a `.so`。如果只需要日常构建，无需拉取完整 FFmpeg 源码。

`liblyrico_taglib.so` 是 lyrico-audiotag 的 native 标签读写产物，用于本地音频文件的元数据读写。

---

## 🧱 开源与许可

Halcyon 主项目以 **Apache-2.0** 协议开源。第三方组件保留其各自许可证，详见 [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)。

---

## 👥 致谢

- **BetterLyrics** — 为模糊封面背景和歌词展示提供视觉参考。
- **Beautiful Lyrics** — 为动态背景、全屏歌词与歌词视觉体验提供参考。
- **LySy** — 为内置原生 Kotlin / Compose 歌词打轴页提供交互与时间轴算法参考（MIT；不引入其 Web 源码或依赖）。
- **Lyrico** — 为外部标签编辑器适配、歌曲标签读取和日志页面交互提供参考。
- **LX Music Mobile** — 提供 LX Music API 兼容实现与测试参考。
- **RawS Music** — 为 10 段均衡器、BiQuad 参数均衡、360° 环绕 / 全景音、等响度、动态均衡/齿音抑制、Moog 梯形滤波器与峰值保护提供 Apache-2.0 的算法参考与 Kotlin 移植基础。
- **光锥音乐** — 界面设计与功能实现参考。
- 感谢 Halcyon 所使用的 Miuix、Media3、FFmpeg、Lyricon、SuperLyricApi、LyricGetter-API、lyrico-audiotag / Lyrico、TagLib、163KeyDecrypter、Coil、OkHttp、Reorderable、accompanist-lyrics-core、LySy、Beautiful Lyrics、RawS Music 以及其它开源项目。

* 以及感谢各位群友积极的测试反馈。Halcyon 的开发与测试过程，也离不开各位群友的支持与鼓励。

---

## 赞助

此项目确实让我感受到个人开发者的艰难。开发不易，即使是用 Codex/Claude Code去处理 issue，有些问题也并非一次就能解决的。且已经为爱发电3个多月了。如果您觉得 Halcyon 对您有帮助，欢迎赞助支持，谢谢。

<img src="./fundingimg/alipay.jpg" alt="支付宝" width="200" />
<img src="./fundingimg/weixin.png" alt="微信赞赏码" width="200" />

---

## 👀 访问统计

<p align="center">
  <img src="https://count.getloli.com/get/@kifranei_halcyon?theme=capoo-2" alt="Visitor Count" />
</p>

---
## 友情推广链接

- [Lyrico](https://github.com/Replica0110/Lyrico)
 强大的歌曲标签编辑工具，同样使用 Miuix 构建，支持匹配歌词、封面、ReplayGain、网易云注释等。

- [RawS Music](https://github.com/QFDY-GZC/RawS-Music)
  一个支持 USB DAC 独占，且支持EQ、环绕音效等的开源本地播放器，同样使用 Miuix 构建，推荐试试。

- [词幕](https://github.com/tomakino/Lyricon)
  为 Android 提供逐字和对唱显示的状态栏歌词插件。需要启用 Xposed 或 LSPosed 框架。

- [棱镜音乐](https://github.com/Ryderwe/PrismMusic-Release)
由 leguan 开发的本地播放器。播放页面等也非常美观，推荐一试。

- [LunaBeat](https://github.com/2755337087/LunaBeat)
可以在手机上实现歌词打轴，同时也是具有美观的播放和歌词页面的播放器。支持为歌曲匹配歌词。
