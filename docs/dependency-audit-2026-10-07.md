# 依赖同步记录：2026-10-07

本次按官方发布、Google Maven、Maven Central 和厂商 Maven 元数据核对直接依赖，选择稳定版。下表的“原版本”来自同步前的项目配置，“同步版本”对应本轮 `gradle/libs.versions.toml`、Gradle wrapper 和原生构建配置；alpha、beta、RC 不列为稳定升级。

参考实现通过协议或局部源码适配同步，不替换整个上游应用。既有海报墙、网易云试听标记、在线封面取色和拖动排序修改保留。

## 构建工具

| 组件 | 原版本 | 同步版本 | 官方来源 |
| --- | --- | --- | --- |
| Android Gradle Plugin | 9.2.1 | 9.4.1 | [Google Maven](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml) |
| Gradle wrapper | 9.4.1 | 9.8.0 | [Gradle releases](https://gradle.org/releases/) |
| Kotlin、Compose compiler、serialization plugin | 2.4.10 | 2.4.20 | [Kotlin releases](https://github.com/JetBrains/kotlin/releases) |
| Compose Multiplatform plugin | 1.10.3 | 1.12.1 | [官方 releases](https://github.com/JetBrains/compose-multiplatform/releases) |

项目继续使用 JDK 21、compile SDK 37 和 app min SDK 29。Coil 3.6.3 的 compile SDK 37／Kotlin 2.4.10 要求及 Miuix 0.9.4 的平台要求由当前配置覆盖。

## AndroidX、UI 与图片

| 组件 | 原版本 | 同步版本 | 处理与官方来源 |
| --- | --- | --- | --- |
| Activity Compose | 1.13.0 | 1.13.0 | 已是稳定最新版；[Google Maven](https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/maven-metadata.xml) |
| Navigation Compose | 2.9.0 | 2.10.2 | 更新；[Google Maven](https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-compose/maven-metadata.xml) |
| Lifecycle runtime／viewmodel Compose | 2.10.0 | 2.11.0 | 同组对齐；[Google Maven](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-compose/maven-metadata.xml) |
| Media3 ExoPlayer／HLS／decoder／OkHttp datasource／session／UI／cast／inspector | 1.11.0 | 1.11.1 | 同组对齐；[官方 releases](https://github.com/androidx/media/releases) |
| AppCompat | 1.7.0 | 1.8.0 | 更新并纳入 catalog；[Google Maven](https://dl.google.com/dl/android/maven2/androidx/appcompat/appcompat/maven-metadata.xml) |
| Annotation | 1.9.1 | 1.11.0 | 两个本地 library 模块纳入 catalog 并对齐；[Google Maven](https://dl.google.com/dl/android/maven2/androidx/annotation/annotation/maven-metadata.xml) |
| WebKit | 1.12.1 | 1.17.1 | 更新；[Google Maven](https://dl.google.com/dl/android/maven2/androidx/webkit/webkit/maven-metadata.xml) |
| DocumentFile | 1.1.0 | 1.1.0 | 已是稳定最新版；[Google Maven](https://dl.google.com/dl/android/maven2/androidx/documentfile/documentfile/maven-metadata.xml) |
| DataStore preferences | 1.2.0 | 1.2.1 | 更新；[Google Maven](https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-preferences/maven-metadata.xml) |
| ProfileInstaller | 1.4.1 | 1.4.1 | 已是稳定最新版；[Google Maven](https://dl.google.com/dl/android/maven2/androidx/profileinstaller/profileinstaller/maven-metadata.xml) |
| Miuix UI／icons／blur／preference | 0.9.4 | 0.9.4 | 四模块已是稳定最新版；[官方 releases](https://github.com/compose-miuix-ui/miuix/releases) |
| Coil compose／network-okhttp | 3.2.0 | 3.6.3 | 更新；[官方 changelog](https://coil-kt.github.io/coil/changelog/) |
| Reorderable | 3.1.0 | 3.1.0 | 已是稳定最新版；保留刚恢复的边缘自动滚动手感；[官方 releases](https://github.com/Calvin-LL/Reorderable/releases) |
| JetBrains Material icons extended | 1.7.3 | 1.7.3 | 上游已冻结此 artifact，不能随 Compose 版本号升级；[官方说明](https://github.com/JetBrains/compose-multiplatform/blob/master/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/ComposePlugin.kt) |

Coil 的现有 `AsyncImage`、`ImageRequest`、`SingletonImageLoader`、`toBitmap` 调用可继续使用；项目未实现此次改签名的自定义网络 cache strategy。3.6.3 包含 AGP 9.4／R8 Kotlin 模块元数据构建修复。Miuix 已使用 0.9 系列新模块和当前 blur／window API，无需重复迁移。

Media3 1.11.1 的 FFmpeg Java 方法签名与 1.11.0 一致。补上 `AUDIO_DTS_EXPRESS` 到 `dca` 的映射，保留本地 APE、格式过滤与离线解码支持。上游改为动态 JNI 注册；本地 JNI 保持参数兼容，并保留对应 Java 类的 R8 keep 规则。

## 网络、协程、插件运行时与系统 API

| 组件 | 原版本 | 同步版本 | 处理与官方来源 |
| --- | --- | --- | --- |
| OkHttp | 4.12.0 | 5.5.0 | 更新；Maven 坐标仍为 `com.squareup.okhttp3:okhttp`，官方仓已迁至 Lysine；[changelog](https://github.com/lysine-dev/okhttp/blob/main/CHANGELOG.md)、[Maven metadata](https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp/maven-metadata.xml) |
| kotlinx-coroutines Android | 1.10.2 | 1.11.0 | 更新；[官方 release](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0) |
| kotlinx-serialization JSON | 1.7.3 | 1.11.0 | 更新；未选择 1.12.0-RC；[官方 releases](https://github.com/Kotlin/kotlinx.serialization/releases) |
| Ktor CIO／content negotiation／JSON | 3.5.0 | 3.6.0 | 同组对齐；[官方 changelog](https://github.com/ktorio/ktor/blob/main/CHANGELOG.md) |
| MCP Kotlin SDK server | 0.13.0 | 0.15.0 | 更新；[官方 releases](https://github.com/modelcontextprotocol/kotlin-sdk/releases) |
| QuickJS wrapper Android | 2.4.0 | 3.2.3 | 更新；[官方 changelog](https://github.com/HarlonWang/quickjs-wrapper/blob/main/CHANGELOG.md) |
| Lyricon provider | 0.1.70 | 0.1.70 | 已是稳定最新版；[官方仓](https://github.com/proify/lyricon) |
| Lyric Getter API | 7.0.0 | 7.0.0 | 已是稳定最新版；[官方仓](https://github.com/HChenX/Lyric-Getter-Api) |
| SuperLyricApi | 3.4 | 3.6 | 更新并适配真实位置／时长与强制重发；[官方仓](https://github.com/HChenX/SuperLyricApi) |
| Shizuku API／provider | 13.1.5 | 13.1.5 | 已是稳定最新版；[官方仓](https://github.com/RikkaApps/Shizuku-API) |
| HyperNotification Focus API | 1.4 | 1.4 | 已是稳定最新版；[官方仓](https://github.com/xzakota/HyperNotification) |
| HiddenApiBypass | 6.1 | 6.1 | 已是稳定最新版；[官方仓](https://github.com/LSPosed/AndroidHiddenApiBypass) |
| HONOR media-audio SDK | 1.2.0.300 | 1.2.0.301 | 厂商非开源 SDK，单列；[官方 Maven metadata](https://developer.honor.com/repo/com/hihonor/mcs/media-audio/maven-metadata.xml) |

HONOR 的 metadata 在核对时将 latest／release 均设为 1.2.0.301。对照两版 AAR 中本项目使用的 `HnAudioClient`、`HnAudioPlayClient`、`IAudioServiceCallback`、`ResultCode`，公开 API 相同。厂商 POM 没有声明开源许可证，不能把它归入 Apache-2.0 依赖。

MCP SDK 0.15.0 的发布 POM 标为 MIT，但同 tag 的 [LICENSE](https://github.com/modelcontextprotocol/kotlin-sdk/blob/0.15.0/LICENSE) 已说明 MIT 到 Apache-2.0 的迁移与未完成重新许可的贡献保留 MIT；许可证清单据此保留两者，而不是把所有源码写成单一许可证。

## 本地协议与源码适配

| 项目 | 核对基线 | 本轮处理 |
| --- | --- | --- |
| ColorOS Live Lyrics Bridge | [v4.4.0](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/tree/v4.4.0)，`44d5d347623e93a008bf188ae96c060ee4791e98` | 模块 payload 的行时间使用毫秒；`rawLyric` 采用行标记加尖括号词标记，翻译仅通过独立 `translationLyric` 发送；原系统模式保留自己的 LRC 格式。 |
| LX Music Mobile | [v1.9.1](https://github.com/lyswhut/lx-music-mobile/tree/v1.9.1)，`fb8480728d875fa5e0da25eebd3a26bb71723aae` | 对照最新稳定 preload，保留 Halcyon 的 `qs` 扩展和二进制 Base64 修正；QQ 元数据搜索同步最新签名接口，保留元数据兼容回退。 |
| MusicFree | [v0.6.2](https://github.com/maotoumao/MusicFree/tree/v0.6.2)，`b3cb5e5829dc600af86731c582429f5eb99cad27`；[插件协议](https://musicfree.catcat.work/plugin/protocol.html) | 搜索条目已有 URL 时仍优先调用插件 `getMediaSource`；只有插件没声明 resolver 才读取条目／音质 URL。保留返回的 headers／userAgent，为 HLS 与下载提供独立流请求配置。 |
| Lyrico JNI | [v1.6.0](https://github.com/Replica0110/Lyrico/tree/v1.6.0)，`ad9c83f5e78561edf60cfae762556583f359a395` | 合入初始化检查、字符串释放和音频属性空结果回退；保留 Halcyon 的 FD 复位、多格式检测与标签兼容。 |
| Inter 字体 | [v4.1](https://github.com/rsms/inter/releases/tag/v4.1) 官方 `extras/ttf/Inter-Bold.ttf` | 本地实际资产从 3.010 更新为 4.1 的静态 Bold，保持原字号与字重调用；字体内部版本为 4.001，无 `fvar`。既有 OFL-1.1 与官方归档许可证一致。 |

ColorOS／LX／Lyrico 采用局部适配，不包含对应应用二进制。MusicFree 上游应用使用 AGPL-3.0；Halcyon 的本地协议适配不打包 MusicFree 应用代码或用户插件，导入的插件保持各自许可证。

MusicFree 每个流的 header 配置隔离。HLS manifest、segment、key 在同源请求中共享配置，跨源不转发该流凭据；主播放器与交叉淡入备用播放器均接入媒体工厂。带 header 的普通文件下载由应用 IO 任务执行，完成后发布到媒体库。HLS 离线合成尚未实现，不能把 manifest 保存成音频；应用下载任务不承诺系统杀进程后自动续传。

## 原生库与保留例外

| 组件 | 原基线 | 同步版本／目标 | 来源与适配 |
| --- | --- | --- | --- |
| TagLib | vendored 源码 2.3.0 | 2.3.2 | [官方 release](https://github.com/taglib/taglib/releases)，commit `deadc2990767dfbda0701e0ab35fdeea653db08f`；原局部回填已在新版上游包含。 |
| utfcpp | 4.0.9 | 4.1.1 | [官方 release](https://github.com/nemtrif/utfcpp/releases)，commit `819011bb01628fe1aa2f1da9f2c842a48fd5680b`；随 TagLib 重建。 |
| Oboe | 1.9.0 | 1.11.0 | [官方 releases](https://github.com/google/oboe/releases)、[Google Maven](https://dl.google.com/dl/android/maven2/com/google/oboe/oboe/maven-metadata.xml)；现有 blocking write／设备选择／独占配置接口保留。 |
| 本地 FFmpeg decoder | headers 6.0.2，JNI 确认 6.0 系列 | 9.0.2 | [官方发布](https://ffmpeg.org/download.html)；四个 ABI 静态库及 arm64 JNI 已重建，保留 LGPL 配置与本地 packet／frame 复用。 |
| FFmpegKit full | 6.0-2.LTS | 保持 6.0-2.LTS | [原项目](https://github.com/arthenica/ffmpeg-kit)已退休；原 Maven 坐标没有可以直接更新的稳定新版。 |

FFmpeg 9.0.2 官方源码归档的本次固定 SHA-256 为 `8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e`。构建继续关闭 GPL、nonfree、version3，使用 ALAC／AAC／APE／MP3／Vorbis／Opus／FLAC 等既有音频 decoder；它与转换工具用的 FFmpegKit 是两个独立组件。

四 ABI 构建在现有 WSL 的独立临时目录执行，全部成功后再替换静态库，公开头文件由 `make install-headers` 生成，移除旧版未使用的私有实现头文件。共享 `avconfig.h` 按 ABI 选择对应的原始生成配置。JNI 链接隐藏静态 FFmpeg 符号，避免与应用同时使用的 FFmpegKit 6 互相解析符号。构建流程需要 NDK 29 和 Linux 的 make／gcc／libc6-dev／xz-utils／pkg-config／unzip。

FFmpegKit 官方续作 [FFmpegKitNext](https://github.com/arthenica/ffmpeg-kit-next) 的 Android 最新发布为 9.0.0，提供源码，需要自行构建，未发布可直接替换的官方 Maven/AAR。Android wrapper 改为 Kotlin API，迁移会涉及转换、频谱提取、视频下载的执行／会话／取消／FFprobe 调用，属于独立迁移；本轮保持原 LGPL `full` 版本，不改成第三方来源不明的二进制。

原生源码版本和已打包 `.so` 版本分别核对。TagLib 源码升级后，arm64 默认构建仍可能使用旧的 checked-in prebuilt，必须显式原生重建并替换。FFmpeg 的新 headers、静态 archives 和 JNI 必须成套更新；旧二进制里的 `Lavc60.3.100` 或错误生成的 Git 版本字符串不能作为升级完成证据。来源与重建说明见 [native UPSTREAM.md](../lyrico-audiotag/src/main/cpp/UPSTREAM.md)。

## 测试工具依赖

其他引用项目也按实际用途核对了当前公共版本与格式：

| 项目 | 核对版本／revision | 结果 |
| --- | --- | --- |
| [RawS Music](https://github.com/QFDY-GZC/RawS-Music/releases/tag/v1.0.10-release) | 稳定版 1.0.10-release；HEAD `52a6049` | 本地 DSP 和手势移植不依赖 RawS 应用 ABI；手势参数与 version 2 听歌历史格式保持兼容。 |
| [163KeyDecrypter](https://github.com/lycode404/163KeyDecrypter) | HEAD `93d2a49` | Base64／AES 公共格式与本地解码器一致。 |
| [LySy](https://github.com/pxeemo/LySy) | tag v1.1.0；HEAD `d2e36bd` | 原生编辑交互参考，导出的 LRC／JSON／TTML／SRT 格式没有要求运行库升级。 |
| [LunaBeat](https://github.com/2755337087/LunaBeat/releases/tag/V2.7.5) | 2.7.5 | 公共仓库只有说明与截图，无法核实私有的历史和 MV 实现；保留现有适配。 |
| [LunaBeat TTML Hub](https://github.com/2755337087/ttml-hub) | HEAD `53ba5ef`；schema 2；revision `8f2b44557caca0de4976` | 使用真实 2784 首索引及歌曲样本运行本地 JS 的搜索／下载，两个 SHA-256 校验均通过。 |
| [AMLL TTML](https://github.com/amll-dev/applemusic-like-lyrics) | TTML package 1.0.1；HEAD `86200de` | 核心命名空间、时间、role 和 ruby 格式保持兼容；本地已有同时匹配注音开始／结束时间的规则。可选元信息不影响现有歌词解析。 |

详细只读证据保存于 `app/build/outputs/dependency-audit-20261007/reference-audit.json`。这些项目中独立移植、格式适配与视觉参考的版本核对，不表示引入了对应应用的全部源码或功能。

| 组件 | 原版本 | 同步版本 | 官方来源 |
| --- | --- | --- | --- |
| JUnit 4 | 4.13.2 | 4.13.2 | 已是稳定最新版；[Maven metadata](https://repo.maven.apache.org/maven2/junit/junit/maven-metadata.xml) |
| Robolectric | 4.17 | 4.17 | 已是稳定最新版；[官方 releases](https://github.com/robolectric/robolectric/releases) |
| JSON-java | 20240303 | 20260814 | 更新；[官方 releases](https://github.com/stleary/JSON-java/releases) |
| Compose ui-test-junit4／ui-test-manifest | 1.11.4 | 1.12.1 | 对齐稳定版；[Google Maven](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-test-junit4/maven-metadata.xml) |

## 核对证据与验证边界

本轮工作目录下保留了以下证据（构建输出，不作为应用资源打包）：

- `app/build/outputs/dependency-audit-20261007/runtime/runtime-audit.json`：网络／运行时版本、LX 上游 commit、真实搜索响应及 QuickJS ELF 检查。
- `app/build/outputs/dependency-audit-20261007/honor/honor-audit.json`：HONOR 官方 metadata 与两版 AAR 公开 API 对照。
- `app/build/outputs/native-upstream-audit-20261007/source-provenance.json`：TagLib／utfcpp 官方归档、commit、校验值和三方合并范围。

已核对的专项证据包括：QQ 新搜索接口第 2 页 HTTP 200／code 0，返回 30 条且 total 为 1004；网易云／酷狗元数据搜索 HTTP 200，各返回 30 条并带 total；MusicFree 当前 Java bridge 的 7 项无网络 Node 检查；QuickJS 四个 ABI ELF LOAD 对齐 16 KB；TagLib 所收录的 338 个上游文件及 utfcpp 12 个文件与新版来源一致；NDK 29 对本地 JNI bridge 的 C++17 语法检查通过。

后续正式验证已完成：

- FFmpeg 四 ABI 的 12 个静态库与 JNI 编译、链接通过；版本函数反汇编与头文件一致：avcodec `63.1.102`、avutil `61.1.102`、swresample `7.1.102`。15 个 decoder、LGPL 配置、16 KB ELF 对齐、动态符号隔离均已检查。
- `:lyrico-audiotag:assembleRelease :ffmpeg-decoder:assembleRelease -PellaBuildNative=true -PellaAbi=arm64-v8a` 通过，新版 TagLib 与 FFmpeg 的 stripped `.so` 已替换 checked-in arm64 prebuilt。
- 原生模块通过 AGP 的 `finalizeDsl` 新接口排除预编译库目录，避免开启原生构建时混入旧二进制。
- `:app:testDebugUnitTest` 完整套件：1152 项，1148 通过、4 项本地样本测试跳过，0 失败、0 错误。先前发现的 Robolectric 主线程环境、设置存储隔离和 Windows 文件 fixture 问题已在既有测试中修正，没有新增测试文件。
- MusicFree 与原用户汽水脚本各 7 项 Node 协议检查通过；8 种语言的 197 条缺失资源已补齐，保留原有翻译。

最终 Release 包的源码快照、验签、ZIP 完整性、ABI、打包原生库与本轮 prebuilt 的逐字节一致性，由 `app/build/outputs/dependency-refresh-20261007/release-verification.json` 记录。构建和模拟环境检查不能替代 Android 设备上的播放与 OEM 歌词显示验证；本轮未安装到设备。
