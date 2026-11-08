/**
 * 酷我音乐 — Halcyon 音源插件（Source Plugin API v1）
 *
 * 登录方式：Cookie（需含 kw_token）/ 免登录。
 *   - 免登录：收藏列表为空；"歌单"取酷我公开推荐歌单；最近播放本地记录
 *     （getMediaSource 被调用即视为一次播放）。
 *   - 登录：kw_token 作为 m.kuwo.cn 移动端 csrf 头，尝试拉用户歌单，
 *     拉不到退回公开歌单；antiserver 播放链不吃登录态。
 *
 * 实测可用端点（2026-11，全部无需鉴权）：
 *   搜索  GET http://search.kuwo.cn/r.s            （单引号 JS 字面量，用 eval 解析）
 *   播放  GET http://antiserver.kuwo.cn/anti.s     （128k mp3）
 *   歌词  GET https://m.kuwo.cn/newh5/singles/songinfoandlrc
 *   歌单  GET http://wapi.kuwo.cn/api/www/classify/playlist/getRcmPlayList
 *         GET http://wapi.kuwo.cn/api/www/playlist/playListInfo
 *   封面  http://img1.kwcdn.kuwo.cn/star/albumcover/<web_albumpic_short>
 *
 * www.kuwo.cn 主站 API（csrf/Secret 认证）已多轮变更，本插件不使用。
 */

var ANTI = "http://antiserver.kuwo.cn/anti.s";
var WAPI = "http://wapi.kuwo.cn";
var COVER_BASE = "http://img1.kwcdn.kuwo.cn/star/albumcover/";
var RECENT_KEY = "kw_recent_plays";
var RECENT_MAX = 100;
var PLAYLIST_COUNT = 8;      // 每次同步抓取的推荐歌单数
var PLAYLIST_SIZE = 60;      // 每个歌单抓取的曲目数

function stripRid(musicrid) {
    return String(musicrid || "").replace(/^MUSIC_/i, "");
}

function kwItem(row) {
    var rid = stripRid(row.MUSICRID || row.musicrid || row.rid || row.id);
    var hasmv = row.MVFLAG === "1" || row.hasmv === 1 || row.hasmv === "1";
    var cover = row.web_albumpic_short
        ? COVER_BASE + row.web_albumpic_short
        : (row.pic120 || row.albumpic || row.pic || "");
    return {
        id: rid,
        rid: rid,
        title: he.decode(String(row.SONGNAME || row.NAME || row.name || row.songname || "")),
        artist: he.decode(String(row.ARTIST || row.artist || "")),
        album: he.decode(String(row.ALBUM || row.album || "")),
        duration: Number(row.DURATION || row.duration) || 0,   // 秒
        artwork: cover,
        mvId: hasmv ? rid : "",
    };
}

/** r.s 返回单引号 JS 对象字面量；JSON.parse 不认，用 eval 解析。 */
function evalLiteral(text) {
    try { return eval("(" + text + ")"); } catch (e) { return null; }
}

async function rSearch(keyword, pn, rn) {
    var r = await axios.get("http://search.kuwo.cn/r.s", {
        params: {
            all: keyword, ft: "music", itemset: "web_2013", client: "kt",
            pn: pn, rn: rn, rformat: "json", encoding: "utf8",
        },
    });
    var obj = typeof r.data === "string" ? evalLiteral(r.data) : r.data;
    if (!obj) throw new Error("搜索响应解析失败");
    return obj;
}

/** antiserver 解析：rid 形如 MUSIC_123；format = mp3 | mkv | mp4；返回纯文本 URL。 */
async function antiUrl(rid, format) {
    var r = await axios.get(ANTI, {
        params: { type: "convert_url", rid: "MUSIC_" + rid, format: format, response: "url" },
    });
    var data = r && r.data;
    var url = typeof data === "string" ? data.trim() : "";
    return /^https?:\/\//.test(url) ? url : "";
}

/** ctx.auth → {kw_token, uid, cookie}：kw_token 同时作为 m.kuwo.cn API 的 csrf 头。 */
function authOf(ctx) {
    var a = ctx && ctx.auth;
    var cookie = typeof a === "string" ? a : (a && a.cookie) || "";
    if (!cookie) return null;
    var tok = (/(?:^|;\s*)kw_token=([^;\s]+)/i.exec(cookie) || [])[1];
    var uid = (/(?:^|;\s*)(?:userid|u_id|uid|u_c)=([^;\s]+)/i.exec(cookie) || [])[1];
    if (!tok) return null;
    return { kwToken: tok, uid: uid || "", cookie: cookie };
}

/** m.kuwo.cn 登录态接口：Cookie 整段回传 + csrf=kw_token。 */
function mobileAuthHeaders(auth) {
    return { Referer: "https://m.kuwo.cn/", Cookie: auth.cookie, csrf: auth.kwToken };
}

function pushRecent(item) {
    try {
        var list = JSON.parse(localStorage.getItem(RECENT_KEY) || "[]");
        list.unshift({ item: item, playedAt: Date.now() });
        if (list.length > RECENT_MAX) list.length = RECENT_MAX;
        localStorage.setItem(RECENT_KEY, JSON.stringify(list));
    } catch (e) {}
}

module.exports = {
    platform: "酷我音乐",
    id: "kuwo",
    version: "1.0.0",
    author: "Halcyon",
    apiVersion: 1,
    account: { methods: ["cookie", "none"] },
    capabilities: { collections: true, search: true, lyric: true, recentPlays: true },

    /**
     * Cookie 登录：粘贴 kuwo.cn 登录后的 Cookie（必须含 kw_token；uid/userid
     * 可选）。kw_token 同时充当 m.kuwo.cn 移动端接口的 csrf 头。
     * 校验：尽力请求 user/info；接口异常不阻塞登录（无匿名可达的校验端点）。
     */
    async login(ctx, params) {
        var raw = String((params && params.cookie) || "").trim();
        if (!raw) throw new Error("Cookie 为空");
        var tok = (/(?:^|;\s*)kw_token=([^;\s]+)/i.exec(raw) || [])[1];
        if (!tok) throw new Error("Cookie 缺少 kw_token（请粘贴 kuwo.cn 登录后的完整 Cookie）");
        var uid = (/(?:^|;\s*)(?:userid|u_id|uid|u_c)=([^;\s]+)/i.exec(raw) || [])[1] || "";
        var nickname = "";
        try {
            var r = await axios.get("https://m.kuwo.cn/newh5app/api/mobile/v1/user/info", {
                params: { uid: uid }, headers: mobileAuthHeaders({ kwToken: tok, cookie: raw }),
            });
            var d = (r.data && r.data.data) || r.data || {};
            nickname = d.uname || d.name || d.nickname || "";
            if (!uid) uid = String(d.uid || d.userid || "");
        } catch (e) {}
        return {
            userId: String(uid),
            nickname: nickname || "酷我用户",
            avatar: "",
            credential: { cookie: raw },
        };
    },

    /** 搜索：r.s 的 pn 是 0 起始，宿主 page 从 1 开始。 */
    async search(ctx, keyword, page) {
        var rn = 30;
        var pn = Math.max(0, (page || 1) - 1);
        var obj = await rSearch(keyword, pn, rn);
        var rows = obj.abslist || [];
        var total = parseInt(obj.TOTAL || "0", 10) || 0;
        var data = rows.map(kwItem).filter(function (it) { return it.title; });
        return { data: data, isEnd: data.length === 0 || (pn + 1) * rn >= total };
    },

    /**
     * 播放解析：antiserver 仅提供 128k mp3。无论请求什么音质档都返回这条链路，
     * 如实标注 qualityLabel；付费/受限曲目会返回空串，抛错让宿主提示。
     */
    async getMediaSource(ctx, item, quality) {
        var rid = stripRid(item.rid || item.id);
        if (!rid) throw new Error("缺少歌曲 rid");
        var url = await antiUrl(rid, "mp3");
        if (!url) throw new Error(authOf(ctx)
            ? "未取到播放地址（VIP 曲目：antiserver 只出 128k 公开流，登录态不经过此链）"
            : "未取到播放地址（可能为付费/受限曲目）");
        pushRecent(item);
        return {
            url: url,
            format: "mp3",
            mimeType: "audio/mpeg",
            bitRate: 128000,
            qualityLabel: "128k",
        };
    },

    /** 歌词：m.kuwo.cn 的 lrclist[{time:秒.小数, lineLyric}] → 标准 LRC。 */
    async getLyric(ctx, item) {
        var rid = stripRid(item.rid || item.id);
        if (!rid) return { rawLrc: "" };
        var r = await axios.get("https://m.kuwo.cn/newh5/singles/songinfoandlrc", {
            params: { musicId: rid },
            headers: { Referer: "https://m.kuwo.cn/" },
        });
        var list = (((r.data || {}).data || {}).lrclist) || [];
        if (!list.length) return { rawLrc: "" };
        var pad = function (n, l) { n = String(n); while (n.length < (l || 2)) n = "0" + n; return n; };
        var lrc = list.map(function (e) {
            var t = parseFloat(e.time) || 0;
            var m = Math.floor(t / 60), s = Math.floor(t % 60), cs = Math.floor((t % 1) * 100);
            return "[" + pad(m) + ":" + pad(s) + "." + pad(cs) + "]" + (e.lineLyric || "");
        }).join("\n");
        return { rawLrc: lrc };
    },

    /**
     * 全量同步：登录态先试 like/songList（用户收藏歌单）等移动端接口，
     * 拿不到就退回公开"热门推荐歌单"；favorites 恒为空（无公开可达的
     * "我喜欢"曲目接口）。
     */
    async getCollections(ctx) {
        var auth = authOf(ctx);
        if (auth) {
            var userLists = [];
            var tried = [
                "https://m.kuwo.cn/newh5app/api/mobile/v1/like/songList",
                "https://m.kuwo.cn/newh5app/api/mobile/v1/myPlaylist",
            ];
            for (var t = 0; t < tried.length && !userLists.length; t++) {
                try {
                    var ur = await axios.get(tried[t], {
                        params: { uid: auth.uid }, headers: mobileAuthHeaders(auth),
                    });
                    var rows = (ur.data && (ur.data.data || ur.data.list)) || [];
                    if (!Array.isArray(rows)) continue;
                    for (var i = 0; i < rows.length; i++) {
                        var pl = rows[i] || {};
                        var pid = pl.id || pl.pid || pl.listid;
                        if (pid != null) userLists.push({ id: String(pid), name: pl.name || pl.title || "歌单", coverUrl: pl.pic || pl.img || "" });
                    }
                } catch (e) { /* 试下一个端点 */ }
            }
            if (userLists.length) {
                var playlists = [];
                for (var j = 0; j < userLists.length; j++) {
                    var p = userLists[j];
                    var songs = [];
                    try {
                        var dd = await axios.get(WAPI + "/api/www/playlist/playListInfo", {
                            params: { pid: p.id, pn: 1, rn: PLAYLIST_SIZE, httpsStatus: 1 },
                        });
                        var rr = (((dd.data || {}).data || {}).musicList) || [];
                        songs = rr.map(kwItem).filter(function (it) { return it.title && it.id; });
                    } catch (e) {}
                    playlists.push({ id: p.id, name: p.name, coverUrl: p.coverUrl, songs: songs });
                }
                return { favorites: [], playlists: playlists, favoritePlaylistId: "" };
            }
            // 用户接口无数据 → 落到公开歌单
        }
        var r = await axios.get(WAPI + "/api/www/classify/playlist/getRcmPlayList", {
            params: { pn: 1, rn: PLAYLIST_COUNT, order: "hot", httpsStatus: 1 },
        });
        var pls = (((r.data || {}).data || {}).data) || [];
        var playlists = [];
        for (var i = 0; i < pls.length; i++) {
            var pl = pls[i];
            var pid = String(pl.id || "");
            if (!pid) continue;
            var songs = [];
            try {
                var d = await axios.get(WAPI + "/api/www/playlist/playListInfo", {
                    params: { pid: pid, pn: 1, rn: PLAYLIST_SIZE, httpsStatus: 1 },
                });
                var rows = (((d.data || {}).data || {}).musicList) || [];
                songs = rows.map(kwItem).filter(function (it) { return it.title && it.id; });
            } catch (e) { /* 单个歌单失败不阻塞整体同步 */ }
            playlists.push({ id: pid, name: pl.name || "歌单", coverUrl: pl.img || "", songs: songs });
        }
        return { favorites: [], playlists: playlists, favoritePlaylistId: "" };
    },

    /** 本地记录的最近播放（最新在前）。 */
    async getRecentPlays(ctx) {
        try {
            return JSON.parse(localStorage.getItem(RECENT_KEY) || "[]");
        } catch (e) { return []; }
    },

    // MV 能力已移除：r.s 的 MVFLAG 恒为 0（拿不到 mvid），
    // 且 antiserver convert_mv_url 当前对所有 rid 返回 "failed"。
};
