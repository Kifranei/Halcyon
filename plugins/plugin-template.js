/**
 * Halcyon Source Plugin 模板（Source Plugin API v1）
 * =================================================
 *
 * Halcyon 账号型音源插件是一个运行在 QuickJS 沙箱里的 CommonJS 模块。
 * 宿主把 module.exports 当作插件对象，按需调用其中的方法：
 *
 *     plugin.<method>(ctx, ...args)  ->  返回值或 Promise<返回值>
 *
 * - ctx   = { auth, userId, nickname, quality, pluginId }
 *     auth    = login()/checkQrCode() 返回的 credential 对象（未登录为 null）
 *     quality = "auto" | "128k" | "320k" | "flac" | "flac24bit"（用户在设置里选的音质档）
 * - item  = 插件自己产出的 musicItem。宿主会按 onlineId 原样持久化，
 *           之后调用 getMediaSource/getLyric/getComments 时原样回传——
 *           想把 hash、rid、albumId 之类塞进去随便塞，字段全部保留。
 * - item 至少要有 { id, title }；识别字段（可任选别名）：
 *     id | songmid | mid | hash | rid | songId | song_id | rid_str
 *     title | name | songname | songName            （必填）
 *     artist | singer | author | creator | singerName，或 artists[]/singers[]/ar[]/singer[]
 *     album | albumName | albumname | album_name
 *     duration（秒/毫秒/"mm:ss"）| durationMs | interval | dt | playTime | duration_ms
 *     artwork | cover | coverImg | coverUrl | pic | picUrl | albumArt | img
 *     mvId | mvid | mv_id | vid
 *     lrc | lyric | rawLrc | lyrics | plainLrcTxt 之类（内嵌歌词可选）
 *
 * - 未实现的函数会收到 PLUGIN_NO_METHOD 错误并被宿主当作"不支持该能力"跳过；
 *   删除不需要的函数即可，capabilities 也会按已实现的函数自动推导。
 * - 任何 throw 都会以 {ok:false,error:消息} 的形式反馈给用户。
 *
 * 沙箱内可用的全局对象（与 MusicFree 兼容层共享）：
 *   axios / request            —— axios.get(url, {headers, params, followRedirects?}) -> Promise<{status, headers{get(k)}, data}>
 *                                 data 自动按 JSON 解析（响应体以 { 或 [ 开头时），否则是原始字符串；
 *                                 followRedirects:false 时不跟随 3xx，可自行读 headers.get('location')
 *   CryptoJS                   —— MD5/SHA1/SHA256/Hmac 系列/AES/DES/TripleDES/RC4 + enc + WordArray
 *   qs, he, cheerio, dayjs, bigInt
 *   Buffer (from/alloc/concat/isBuffer/byteLength), btoa, atob, TextEncoder, TextDecoder
 *   URL, URLSearchParams, FormData, fetch, XMLHttpRequest
 *   localStorage / AsyncStorage —— localStorage 按插件 id 持久化到设备（跨调用保留）
 *   setTimeout / setInterval / clearTimeout / clearInterval, console.log…
 *   require(name)              —— 上述模块的 node 风格导入
 *
 * 插件脚本由用户自备并自行负责：平台接口随时可能失效或受服务条款约束。
 */
module.exports = {

    /* ============================ 清单（必填字段） ============================ */

    platform: "示例平台",            // 显示名称
    id: "demo-source",              // 唯一 id：小写字母/数字/下划线/中划线；会成为文件名和 prefs 键
    version: "1.0.0",
    author: "your-name",
    apiVersion: 1,

    /**
     * 声明登录方式（可叠加）：
     *   "cookie" —— 用户提供 Cookie/凭据串，宿主调用 login()
     *   "qrcode" —— 扫码登录，宿主调用 getQrCode() + 轮询 checkQrCode()
     *   "none"   —— 免登录；宿主把该插件视为匿名已登录
     * 若整个 account 字段省略，默认 ["cookie"]。
     */
    account: { methods: ["cookie", "none"] },

    /**
     * 可选的显式能力声明；没写时按已实现的函数自动推导：
     *   collections / search / mediaSource / lyric / recentPlays /
     *   setFavorite / comments / mv / daily / radio
     */
    capabilities: { collections: true, search: true },

    /* ============================ 账号 ============================ */

    /**
     * Cookie/凭据登录。参数 = { cookie: 用户粘贴的整段凭据 }。
     * 必须返回 { userId, nickname, avatar, credential }；
     * credential 会被宿主加密保存，之后每次调用通过 ctx.auth 回传（可为对象）。
     * userId 与 credential 不能同时为空，否则视为登录失败。
     */
    async login(ctx, { cookie }) {
        // 例：拿 cookie 调一次用户信息接口，校验有效性并取回用户信息
        // var me = await axios.get("https://api.example.com/me", { headers: { Cookie: cookie } });
        // if (me.data.code !== 0) throw new Error("Cookie 无效或已过期");
        // return { userId: String(me.data.uid), nickname: me.data.name,
        //          avatar: me.data.pic, credential: { cookie: cookie } };
        throw new Error("未实现 login");
    },

    /**
     * 扫码登录第一步：返回 { qr, key }。
     *   qr  = 二维码内容：图片 URL / data:URI / base64 图片数据均可（宿主会渲染成图）
     *   key = 轮询时回传给 checkQrCode 的凭据（如 qrcode key / token）
     */
    async getQrCode(ctx) {
        throw new Error("未实现 getQrCode");
    },

    /**
     * 扫码登录轮询：checkQrCode(ctx, key)
     * 返回 { status }，status ∈ "waiting" | "scanned" | "confirmed" | "expired" | "failed"
     * 当 status==="confirmed" 时必须附带 { userId?, nickname?, avatar?, credential }——
     * credential 与 login() 的语义相同，会被持久化并在 ctx.auth 里回传。
     */
    async checkQrCode(ctx, key) {
        return { status: "waiting" };
    },

    /* ============================ 曲库 ============================ */

    /**
     * 全量同步：收藏 + 歌单。宿主把它当作这个"音乐库来源"的完整快照。
     * 返回：
     * {
     *   favorites: [item],            // 我喜欢/收藏（免登录插件可给空数组）
     *   favoritePlaylistId: "…",      // 收藏对应的歌单 id（可省略）
     *   playlists: [{
     *     id, name, coverUrl?, description?,
     *     songs: [item]               // 歌单内歌曲（可为空数组，但建议给真实曲目）
     *   }]
     * }
     */
    async getCollections(ctx) {
        return { favorites: [], playlists: [], favoritePlaylistId: "" };
    },

    /**
     * 搜索。page 从 1 开始；返回 { data: [item], isEnd }（或 { list, hasMore } 或直接返回数组）。
     * isEnd=true 之后宿主不再翻页。
     */
    async search(ctx, keyword, page) {
        var r = await axios.get("https://api.example.com/search", {
            params: { keyword: keyword, page: page, pagesize: 30 },
        });
        var list = (r.data && r.data.list) || [];
        return {
            data: list.map(function (row) {
                return {
                    id: String(row.id),
                    title: row.songname,
                    artist: row.singer,
                    album: row.album,
                    duration: row.duration,          // 秒
                    artwork: row.cover,
                    mvId: row.mvid || "",
                    // —— 其余字段自由附加，getMediaSource 时能拿回来 ——
                };
            }),
            isEnd: list.length === 0,
        };
    },

    /* ============================ 播放 ============================ */

    /**
     * 解析播放地址。item 为插件产出的原始对象；quality 为音质档字符串。
     * 返回 { url, headers?, format?/mimeType?, bitRate?, sampleRate?, qualityLabel?, isTrial? }
     * ——或直接返回 url 字符串。url 必须以 http(s) 开头；headers 会随播放/下载请求透传。
     */
    async getMediaSource(ctx, item, quality) {
        var r = await axios.get("https://api.example.com/url", { params: { id: item.id } });
        if (!r.data || !r.data.url) throw new Error("未取到播放地址");
        return { url: r.data.url, format: "mp3", bitRate: 128000, qualityLabel: "128k" };
    },

    /**
     * 歌词。返回 { rawLrc, translation?, pronunciation? }，rawLrc 是标准 LRC 文本。
     * 不需要可删除本函数（capability 自动关闭）。
     */
    async getLyric(ctx, item) {
        return { rawLrc: "" };
    },

    /* ============================ 动态数据（可选） ============================ */

    /** 云端最近播放：[{ item, playedAt(毫秒时间戳) }] 或直接 [item]。 */
    async getRecentPlays(ctx) {
        return [];
    },

    /** 收藏/取消收藏。songId = item.id（宿主传的是 onlineId）。 */
    async setFavorite(ctx, songId, favorite) {
        // await axios.post("…/fav", { id: songId, like: favorite ? 1 : 0 }, { headers: { Cookie: ctx.auth.cookie } });
    },

    /** 每日推荐歌曲：[item] 或 { data:[item] }。 */
    async getDailySongs(ctx) { return []; },

    /** 每日/推荐歌单：[{ id, name, coverUrl?, songs:[item] }] 或 { data:[…] }。 */
    async getDailyPlaylists(ctx) { return []; },

    /** 电台/相似推荐：传种子 item，返回 [item]。 */
    async getRadio(ctx, seedItem) { return []; },

    /* ============================ 评论（可选） ============================ */

    /**
     * getComments(ctx, item, { pageNo, page, sortType, cursor, pageSize })
     * 返回：
     * {
     *   comments: [{
     *     commentId,                 // 数字或字符串
     *     user: { userId, nickname, avatarUrl, vipIconUrl? },
     *     content, time,             // time = 毫秒时间戳；timeStr 可选显示文本
     *     timeStr?, ipLocation?, likedCount?, replyCount?, parentCommentId?,
     *     replied?: { commentId, user:{ nickname }, content },   // 被回复的评论
     *     liked?
     *   }],
     *   totalCount, hasMore, cursor  // cursor 翻页游标，下一页原样传回
     * }
     */
    async getComments(ctx, item, page) {
        return { comments: [], totalCount: 0, hasMore: false, cursor: "" };
    },

    /**
     * 楼层回复：getCommentFloor(ctx, item, { commentId, time, limit })
     * 返回 { comments 或 replies: [...同上 comment 结构...], totalCount, hasMore, time }
     * ——time 是下一次翻页游标（毫秒时间戳）。
     */
    async getCommentFloor(ctx, item, params) {
        return { comments: [], hasMore: false, time: 0 };
    },

    /** 点赞：likeComment(ctx, { item, commentId, like }) */
    async likeComment(ctx, params) {},

    /** 发评论/回复：postComment(ctx, { item, content, replyToCommentId, parentCommentId }) -> { comment? } */
    async postComment(ctx, params) {
        return { comment: null };
    },

    /* ============================ MV（可选） ============================ */

    /** getMvUrl(ctx, mvId, resolution) -> { url, headers? } 或 url 字符串。 */
    async getMvUrl(ctx, mvId, resolution) {
        throw new Error("无 MV");
    },

    /** getMvCover(ctx, mvId) -> 封面 url 字符串或 { url }。 */
    async getMvCover(ctx, mvId) {
        return "";
    },
};
