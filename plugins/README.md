# Halcyon 音源插件（Source Plugin API v1）

本目录存放**账号型音源插件**的示例与参考实现。插件是独立的 `.js` 文件，由用户在
应用内自行导入，运行于 QuickJS 沙箱，与主程序完全分离——应用不内置、不分发任何
平台解析逻辑。

## 使用方法

1. 应用内：**设置 → 音乐库与扫描 → 音源插件管理 → 导入**（本地文件或链接）。
2. 导入后插件按声明的登录方式出现在账号面板：Cookie / 扫码 / 免登录。
3. 登录（或免登录）后，插件会出现在首页"音乐库来源"菜单中，可像网易云账号一样
   浏览歌单、搜索、播放、查看歌词与评论。

## 文件

| 文件 | 说明 |
| --- | --- |
| `plugin-template.js` | 带完整注释的插件模板——契约的每个方法、参数与返回结构都有说明，写新插件从这里开始 |
| `kuwo.js` | 酷我音乐参考实现（免登录）：搜索 / 播放(128k) / 歌词 / 推荐歌单 / 本地最近播放 |
| `kugou.js` | 酷狗音乐参考实现（免登录）：搜索 / 播放(≤320k·依权限) / 歌词 / 排行榜歌单 / 本地最近播放 |
| `qq.js` | QQ音乐参考实现（免登录）：搜索 / 播放(m4a) / 歌词 / 榜单歌单 / MV / 本地最近播放 |
| `migu.js` | 咪咕音乐参考实现（免登录）：搜索 / 播放(302 跳转·音质阶梯) / 歌词 / 歌单 / 本地最近播放 |
| `qishui.js` | 汽水音乐参考实现（扫码 / Cookie / 免登录）：播放(m4a) / 歌词 / 评论 / 榜单·相关推荐 / 推荐歌单；搜索与我的歌单需登录 |
| `test-harness.js` | 本地联调桩：`node test-harness.js <插件名> [关键词]` 用真实网络跑通插件方法 |
| `test-mv.js` | MV 单测：`node test-mv.js <插件名> <mvId>` |

## 契约速览

`module.exports` 导出插件对象，宿主按需调用 `plugin.<method>(ctx, ...args)`：

- 清单字段：`platform`、`id`、`version`、`apiVersion: 1`、`account.methods`
  （`"cookie"` / `"qrcode"` / `"none"`）、`capabilities`（可选，按已实现函数自动推导）
- `ctx`：`{ auth, userId, nickname, quality, pluginId }`；`auth` 是 `login()`/`checkQrCode()`
  返回并经宿主加密保存的凭据
- 方法：`login` / `getQrCode` / `checkQrCode` / `getCollections` / `search` /
  `getMediaSource` / `getLyric` / `getRecentPlays` / `setFavorite` / `getDailySongs` /
  `getDailyPlaylists` / `getRadio` / `getComments` / `getCommentFloor` / `likeComment` /
  `postComment` / `getMvUrl` / `getMvCover` / `getSongDetail`
- `item`（musicItem）按 `id` 持久化并在后续调用中原样回传，可把解析需要的字段
  （hash、rid、albumId……）都塞进去

完整契约注释见 `plugin-template.js` 与
`app/src/main/java/com/ella/music/data/sourceplugin/SourcePluginModels.kt` 的文件头。

## 沙箱环境

与 MusicFree 兼容层共享：`axios`/`request`（`data` 自动 JSON 解析）、`CryptoJS`
（MD5/HMAC/AES/DES/RC4…）、`Buffer`、`btoa`/`atob`、`TextEncoder`/`TextDecoder`、
`URL`/`URLSearchParams`/`FormData`、`fetch`、`XMLHttpRequest`、`qs`、`he`、`cheerio`、
`dayjs`、`bigInt`、`localStorage`（按插件 id 持久化）、`AsyncStorage`、定时器、`require`。

## 免责声明

示例插件调用各平台未公开的网页接口，仅用于演示插件契约与联调。第三方平台接口
可能随时失效、限流或受其服务条款约束；插件脚本由使用者自备并自行承担合规责任，
与应用作者无关。
