# 移植记录与验收清单

基线：amend 后的 Android `main`：`28ba483293cd424c5a0329a5c73ac2978ec33c51`。根目录 Android 业务代码与该提交一致。

统一分支：`multiplatform`，同时保存 Android、iOS、桌面工程与共享代码。移植历史按共享功能、桌面实现、iOS 实现、统一构建与测试四组整理，包含原 `port/ios` 和 `port/desktop` 的最终改动。独立 port 分支停止使用；后续改动直接在统一分支维护，暂不合入 main。

`.github/workflows/multiplatform-build.yml` 在推送 / PR / 手动触发时并行调用 Android、iOS、桌面三个可复用工作流，所有产物出现在同一个运行页面。

## 模块边界

- `shared/src/model.ts`：曲库、队列策略、版本化备份与输入校验。
- `shared/src/lyrics.ts`：歌词时间解析和二分定位。
- `shared/src/remote.ts`：WebDAV XML 目录及远程协议分页、歌曲映射。
- `shared/src/bridge.ts`：原生命令接口；网页模式仅用于界面开发。
- `shared/src/player.ts`：桌面 HTMLAudioElement 与 iOS 原生队列的适配。
- `desktop/library.mjs`：文件扫描、music-metadata 标签读取、持久化音频注册表。
- `desktop/main.mjs`：隔离的 preload IPC、只允许访问已导入歌曲的媒体协议、字节范围读取、文件对话框、安全凭据。
- `ios/Halcyon/NativePlayer.swift`：原生 AVAudioEngine / AVPlayer 队列、控制中心、音频事件处理。
- `ios/Halcyon/NativeStore.swift`：沙盒文件、Files 导入、Keychain 与 AVFoundation 标签读取。
- `ios/Halcyon/RemoteClient.swift`：受配置服务器边界约束的原生远程请求。

服务器凭据保存在 Keychain / Electron safeStorage，渲染界面接收的来源对象不包含密码或 token。远程音频 URL 在原生层生成，不写进共享备份。服务端重定向在元数据请求中拒绝，避免认证信息离开配置服务器。用户可配置 HTTP 服务器，因此 iOS 的 ATS 配置允许 HTTP；界面建议 HTTPS。

## 必须继续验证

- [ ] Windows、macOS、Linux 真正安装并导入含中文路径的大型曲库。
- [ ] iPhone / iPad 的 Files 单文件、文件夹、iCloud 导入和同名歌词匹配。
- [ ] 两首短音频锁屏连续播放，在切换点观察标题和封面。
- [ ] 逐一验证锁屏播放、暂停、上一首、下一首和拖动进度。
- [ ] 耳机拔出、蓝牙路由丢失、电话中断与恢复；AirPlay 真实设备。
- [ ] 后台睡眠定时、随机、单曲循环、整队列结束行为。
- [ ] 使用自己的 WebDAV、Navidrome 和 Emby 服务核对登录、分页和可寻址流媒体播放。
- [ ] 备份恢复后重新导入文件、检查收藏 / 歌单引用，特别是跨机器桌面文件路径变化。

本地自动化测试覆盖歌词时间与定位、队列边界、搜索、备份校验、媒体路径边界、范围读取、认证参数、HTTP 错误 / 重定向、实际 WAV 与侧车歌词导入和重启后的索引。

## 本轮移植（0.2）

- 队列增删与排序、暂停切歌语义、播放列表完整编辑、曲库排序与来源 / 格式过滤。
- 逐词进度歌词、常见 TTML 重叠 / 翻译 / 罗马音 / 背景人声 / 时序、歌词打轴与导出、歌词分享卡、自定义封面。
- 桌面 EQ / ReplayGain / 倍速；iOS 原生音频图 EQ / 倍速、后台播放统计。iOS 系统播放器作为不支持本地引擎的解码回退。
- WebDAV 递归、Subsonic 专辑枚举回退、服务器封面 / 歌词、受来源边界约束的原生离线下载和取消。
- 原生安全存储的自定义 AI 服务与曲库推荐；本地 MV 关联播放。
- 单元 / 本地 HTTP 协议测试新增离线后文件可读、下载取消无残留、AI 两种协议、队列当前项保持、排序 / 离线过滤和设置兼容性。浏览器测试会记录其环境，不能替代 iOS 原生验证。

## 后续优先级

1. 真机回归、真实 WebDAV / Navidrome / Emby 验证、缓存配额与清理、iOS ReplayGain 标签读取。
2. FFmpeg 解码兼容、原音频标签写回、无缝 / 交叉淡化、完整 TTML / Lyricify 与 AMLL 动画。
3. 在线账号、插件运行时、Last.fm、MCP、小组件和桌面悬浮歌词。

跨平台实现仍在迭代，不代表 Android 全功能对等；模拟器、协议夹具与自动打包结果需和真机 / 真实服务器验收分开看待。
