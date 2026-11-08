/**
 * QQ 音乐 — Halcyon 音源插件（Source Plugin API v1）
 *
 * 登录方式：Cookie / 扫码 / 免登录。
 *   - Cookie：粘贴 y.qq.com 登录后的整段 Cookie（需含 uin/p_uin 与 skey/p_skey）。
 *   - 扫码：ptlogin2 流程，QQ 手机 App 扫码确认。
 *   - 免登录：搜索/榜单/免费曲目播放照常；VIP 曲目明确报错。
 * 登录后：GetPlaylistByUin 同步自建+收藏歌单，"我喜欢"(dirid 201) 作为 favorites；
 *         vkey 携带 uin+loginflag+cookie，可解锁账号权限内的曲目。
 *
 * 端点（实测 2026-11）：
 *   搜索  GET  https://c.y.qq.com/soso/fcgi-bin/client_search_cp
 *   播放  POST https://u.y.qq.com/cgi-bin/musicu.fcg   （vkey → sip + purl，m4a）
 *   歌词  GET  https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?nobase64=1
 *   榜单  GET  https://c.y.qq.com/v8/fcg-bin/fcg_myqq_toplist.fcg / fcg_v8_toplist_cp.fcg
 *   歌单  POST musicu GetPlaylistByUin / CgiGetDiss
 *   MV    GET  https://vv.video.qq.com/getinfo          （QZOutputJson= 前缀需剥掉）
 *   扫码  GET  https://ssl.ptlogin2.qq.com/ptqrshow → 轮询 ptqrlogin → check_sig 取 cookie
 */

var RECENT_KEY = "qq_recent_plays";
var RECENT_MAX = 100;
var TOP_COUNT = 8;
var TOP_SIZE = 60;
var LIST_SIZE = 200;
var GUID = "4825192738";
var QR_KEY_PREFIX = "qq_qr_sig:";

var PT = {
    appid: "716027609",
    daid: "383",
    thirdAid: "100497308",
    u1: "https://graph.qq.com/oauth2.0/login_jump",
};

function yHeaders(cookie) {
    var h = { Referer: "https://y.qq.com/", "User-Agent": "Mozilla/5.0" };
    if (cookie) h.Cookie = cookie;
    return h;
}

/** hash33：QQ 的 g_tk 与 ptqrtoken 用的是同一个算法。 */
function hash33(s) {
    var t = 0;
    for (var i = 0; i < s.length; i++) t += (t << 5) + s.charCodeAt(i);
    return t & 2147483647;
}

function cookieVal(cookie, name) {
    var m = new RegExp("(?:^|;\\s*)" + name + "=([^;]+)").exec(cookie || "");
    return m ? m[1] : "";
}

/** ctx.auth → {cookie, uin, gtk}。 */
function authOf(ctx) {
    var a = ctx && ctx.auth;
    var cookie = typeof a === "string" ? a : (a && a.cookie) || "";
    if (!cookie) return null;
    var uin = cookieVal(cookie, "p_uin") || cookieVal(cookie, "uin");
    uin = uin.replace(/^o0*/, "");
    var skey = cookieVal(cookie, "p_skey") || cookieVal(cookie, "skey");
    return { cookie: cookie, uin: uin, gtk: skey ? hash33(skey) : 0 };
}

function coverOf(row) {
    var mid = row.albummid || "";
    return mid ? "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + mid + ".jpg" : "";
}

function qqItem(row) {
    var singer = "";
    if (row.singer && row.singer.length) {
        singer = row.singer.map(function (s) { return s.name || ""; }).filter(Boolean).join("/");
    }
    return {
        id: String(row.songmid || ""),
        songmid: String(row.songmid || ""),
        title: he.decode(String(row.songname || "")),
        artist: he.decode(singer),
        album: he.decode(String(row.albumname || "")),
        duration: Number(row.interval) || 0,
        artwork: coverOf(row),
        mvId: String(row.vid || ""),
    };
}

/** musicu.fcg POST；auth 非空时携带 uin/gtk/cookie。 */
async function musicu(moduleName, method, param, auth) {
    var body = {
        req_0: { module: moduleName, method: method, param: param },
        comm: {
            uin: auth ? Number(auth.uin) || 0 : 0,
            format: "json", ct: 24, cv: 0,
        },
    };
    if (auth && auth.gtk) body.comm.g_tk = auth.gtk;
    var r = await axios.post("https://u.y.qq.com/cgi-bin/musicu.fcg", JSON.stringify(body), {
        headers: yHeaders(auth && auth.cookie),
    });
    return ((r.data || {}).req_0) || {};
}

/** vkey 解析 → {index: url}；auth 时带登录态（VIP 解锁取决于账号）。 */
async function vkey(songmids, auth) {
    var body = {
        req_0: {
            module: "vkey.GetVkeyServer",
            method: "CgiGetVkey",
            param: {
                guid: GUID,
                songmid: songmids,
                songtype: songmids.map(function () { return 0; }),
                uin: auth ? auth.uin : "0",
                loginflag: auth ? 1 : 0,
                platform: "20",
            },
        },
        comm: { uin: auth ? Number(auth.uin) || 0 : 0, format: "json", ct: 24, cv: 0 },
    };
    var r = await axios.post("https://u.y.qq.com/cgi-bin/musicu.fcg", JSON.stringify(body), {
        headers: yHeaders(auth && auth.cookie),
    });
    var data = (((r.data || {}).req_0 || {}).data) || {};
    var sips = data.sip || [];
    var infos = data.midurlinfo || [];
    var out = {};
    for (var i = 0; i < infos.length; i++) {
        var purl = infos[i].purl || "";
        if (purl && sips.length) out[i] = sips[0] + purl;
    }
    return out;
}

function pushRecent(item) {
    try {
        var list = JSON.parse(localStorage.getItem(RECENT_KEY) || "[]");
        list.unshift({ item: item, playedAt: Date.now() });
        if (list.length > RECENT_MAX) list.length = RECENT_MAX;
        localStorage.setItem(RECENT_KEY, JSON.stringify(list));
    } catch (e) {}
}

/** 榜单（匿名 collections）。 */
async function topCollections() {
    var r = await axios.get("https://c.y.qq.com/v8/fcg-bin/fcg_myqq_toplist.fcg", {
        params: { format: "json", g_tk: "5381", uin: "0", inCharset: "utf-8", outCharset: "utf-8" },
        headers: yHeaders(),
    });
    var tops = ((((r.data || {}).data || {}).topList) || []).slice(0, TOP_COUNT);
    var playlists = [];
    for (var i = 0; i < tops.length; i++) {
        var top = tops[i];
        var tid = top.id;
        if (tid == null) continue;
        var songs = [];
        try {
            var d = await axios.get("https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg", {
                params: {
                    tpl: "3", page: "detail", topid: tid, type: "top",
                    song_begin: "0", song_num: String(TOP_SIZE),
                    format: "json", g_tk: "5381", uin: "0", inCharset: "utf-8", outCharset: "utf-8",
                },
                headers: yHeaders(),
            });
            var rows = (d.data || {}).songlist || [];
            songs = rows.map(function (row) { return qqItem(row.data || row); })
                .filter(function (it) { return it.title && it.id; });
        } catch (e) { /* 单榜失败不阻塞 */ }
        playlists.push({
            id: "top:" + tid,
            name: top.topTitle || top.title || "榜单",
            coverUrl: top.picUrl || "",
            songs: songs,
        });
    }
    return playlists;
}

/** disstid/dirid 的曲目详情（musicu CgiGetDiss）。 */
async function dissSongs(id, auth) {
    var req = await musicu("music.srfDissInfo.DissInfo", "CgiGetDiss", {
        disstid: Number(id) || 0,
        userinfo: 0, song_begin: 0, song_num: LIST_SIZE, order_type: 1, onlysonglist: 0,
        dirinfo: 0, enc_host_uin: "",
    }, auth);
    var rows = (((req.data || {}).songlist) || []).map(function (r2) { return qqItem(r2.data || r2); });
    return rows.filter(function (it) { return it.title && it.id; });
}

module.exports = {
    platform: "QQ音乐",
    id: "qqmusic",
    version: "1.0.0",
    author: "Halcyon",
    apiVersion: 1,
    account: { methods: ["cookie", "qrcode", "none"] },
    capabilities: { collections: true, search: true, lyric: true, mv: true, recentPlays: true },

    // ------------------------------------------------------------- 账号

    /** cookie 登录：需含 uin/p_uin 与 skey/p_skey；用 GetPlaylistByUin 验证。 */
    async login(ctx, params) {
        var raw = String((params && params.cookie) || "").trim();
        if (!raw) throw new Error("Cookie 为空");
        var uin = (cookieVal(raw, "p_uin") || cookieVal(raw, "uin")).replace(/^o0*/, "");
        var skey = cookieVal(raw, "p_skey") || cookieVal(raw, "skey");
        if (!uin || !skey) throw new Error("Cookie 缺少 uin 或 skey（请粘贴 y.qq.com 登录后的完整 Cookie）");
        var auth = { cookie: raw, uin: uin, gtk: hash33(skey) };
        var req = await musicu("music.musicasset.PlaylistBaseRead", "GetPlaylistByUin", { uin: Number(uin) }, auth);
        if (Number(req.code) !== 0) throw new Error("Cookie 无效或已过期（code " + req.code + "）");
        var nickname = "";
        try {
            var pr = await axios.get("https://c.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg", {
                params: { format: "json", uin: uin, ct: "24", cv: "1808", inCharset: "utf-8", outCharset: "utf-8" },
                headers: yHeaders(raw),
            });
            nickname = (((pr.data || {}).data || {}).creator || {}).nick || "";
        } catch (e) {}
        return {
            userId: uin,
            nickname: nickname || ("QQ用户 " + uin),
            avatar: "https://q.qlogo.cn/qqapp/" + PT.thirdAid + "/" + uin + "/100",
            credential: { cookie: raw, uin: uin },
        };
    },

    /** 扫码：ptqrshow 出二维码图；qrsig cookie 存起来给轮询。 */
    async getQrCode(ctx) {
        var qrUrl = "https://ssl.ptlogin2.qq.com/ptqrshow?appid=" + PT.appid +
            "&e=2&l=M&s=4&d=72&v=4&t=" + Math.random() +
            "&daid=" + PT.daid + "&pt_3rd_aid=" + PT.thirdAid;
        var r = await axios.get(qrUrl, { headers: { Referer: "https://xui.ptlogin2.qq.com/" } });
        var sig = cookieVal(r.headers.get("set-cookie") || "", "qrsig");
        if (!sig) throw new Error("未取到 qrsig");
        localStorage.setItem(QR_KEY_PREFIX + sig, sig);
        // qr 直接用图片 URL——宿主按图片地址渲染
        return { qr: qrUrl, key: sig };
    },

    /** 轮询 ptqrlogin；确认后跳 check_sig 收集登录 cookie。 */
    async checkQrCode(ctx, key) {
        var qrsig = localStorage.getItem(QR_KEY_PREFIX + key) || key;
        var url = "https://ssl.ptlogin2.qq.com/ptqrlogin" +
            "?u1=" + encodeURIComponent(PT.u1) +
            "&ptqrtoken=" + hash33(qrsig) +
            "&ptredirect=0&h=1&t=1&g=1&from_ui=1&ptlang=2052" +
            "&action=0-1-" + Date.now() +
            "&js_ver=24071102&js_type=1&login_sig=&pt_uistyle=40" +
            "&appid=" + PT.appid + "&daid=" + PT.daid + "&pt_3rd_aid=" + PT.thirdAid;
        var r = await axios.get(url, {
            headers: { Referer: "https://xui.ptlogin2.qq.com/", Cookie: "qrsig=" + qrsig },
            followRedirects: false,
        });
        var text = typeof r.data === "string" ? r.data : "";
        var m = /ptuiCB\('([^']*)'(?:,'([^']*)')?(?:,'([^']*)')?/.exec(text) || [];
        var code = m[1] || "";
        if (code === "65" || code === "68") return { status: "expired" };
        if (code === "66") return { status: "waiting" };
        if (code === "67") return { status: "scanned" };
        if (code !== "0") return { status: "waiting" };
        // 确认：arg3 是 check_sig 链接，GET 后 Set-Cookie 里有 uin/skey/p_skey
        var sigUrl = m[3] || "";
        if (!sigUrl) return { status: "failed", error: "无 check_sig 链接" };
        var s = await axios.get(sigUrl, {
            headers: { Referer: "https://xui.ptlogin2.qq.com/" },
            followRedirects: false,
        });
        var sc = s.headers.get("set-cookie") || "";
        var uin = cookieVal(sc, "p_uin") || cookieVal(sc, "uin") || "";
        var pSkey = cookieVal(sc, "p_skey");
        var skey = cookieVal(sc, "skey");
        if (!pSkey && !skey) return { status: "failed", error: "未取到登录态 cookie" };
        var cookie = sc;   // 已是 "a=1; b=2" 扁平串
        var account = { userId: uin.replace(/^o0*/, ""), nickname: "", avatar: "" };
        try {
            var me = await module.exports.login(ctx, { cookie: cookie });
            account = { userId: me.userId, nickname: me.nickname, avatar: me.avatar };
        } catch (e) {}
        return { status: "confirmed", userId: account.userId, nickname: account.nickname, avatar: account.avatar, credential: { cookie: cookie, uin: account.userId } };
    },

    // ------------------------------------------------------------- 曲库

    /** 登录 → GetPlaylistByUin 自建+收藏歌单；匿名 → 榜单。 */
    async getCollections(ctx) {
        var auth = authOf(ctx);
        if (!auth) {
            return { favorites: [], playlists: await topCollections(), favoritePlaylistId: "" };
        }
        var req = await musicu("music.musicasset.PlaylistBaseRead", "GetPlaylistByUin",
            { uin: Number(auth.uin) }, auth);
        if (Number(req.code) !== 0) throw new Error("歌单拉取失败（code " + req.code + "）");
        var d = req.data || {};
        var dirs = d.v_playlist || d.dirlist || d.playlist || [];
        var playlists = [];
        var favorites = [];
        var favId = "";
        for (var i = 0; i < dirs.length; i++) {
            var dir = dirs[i];
            var dirid = dir.dirid != null ? String(dir.dirid) : (dir.disstid != null ? String(dir.disstid) : (dir.id != null ? String(dir.id) : ""));
            if (!dirid) continue;
            var name = dir.title || dir.dissname || dir.name || "歌单";
            var isFav = /喜欢|fav/i.test(name) || dirid === "201";
            var songs = [];
            try { songs = await dissSongs(dirid, auth); } catch (e) {}
            if (isFav) {
                favorites = songs;
                favId = dirid;
            }
            playlists.push({
                id: dirid,
                name: name,
                coverUrl: dir.picurl || dir.iconurl || dir.coverurl || "",
                description: dir.desc || "",
                songs: songs,
            });
        }
        return { favorites: favorites, playlists: playlists, favoritePlaylistId: favId };
    },

    // ------------------------------------------------------------- 搜索/播放

    /** 搜索：page 1 起始。 */
    async search(ctx, keyword, page) {
        var rn = 30;
        var p = Math.max(1, page || 1);
        var r = await axios.get("https://c.y.qq.com/soso/fcgi-bin/client_search_cp", {
            params: { w: keyword, p: p, n: rn, format: "json", t: 0, dataType: "music" },
            headers: yHeaders(),
        });
        var song = (((r.data || {}).data || {}).song) || {};
        var rows = song.list || [];
        var data = rows.map(qqItem).filter(function (it) { return it.title && it.id; });
        return { data: data, isEnd: data.length === 0 };
    },

    /** 播放：vkey；登录后带 uin/loginflag/cookie，可解 VIP 曲目（取决于账号）。 */
    async getMediaSource(ctx, item, quality) {
        var mid = item.songmid || item.id;
        if (!mid) throw new Error("缺少 songmid");
        var auth = authOf(ctx);
        var urls = await vkey([mid], auth);
        var url = (urls[0] || "").replace(/^http:\/\//, "https://");
        if (!url) throw new Error(auth ? "未取到播放地址（曲目可能仍需绿钻或不可用）" : "未取到播放地址（VIP 曲目：可在插件账号里登录 QQ）");
        pushRecent(item);
        return {
            url: url,
            format: "m4a",
            mimeType: "audio/mp4",
            bitRate: 128000,
            qualityLabel: "128k",
        };
    },

    /** 歌词：nobase64=1 直接给 LRC；tlyric 为翻译。 */
    async getLyric(ctx, item) {
        var mid = item.songmid || item.id;
        if (!mid) return { rawLrc: "" };
        try {
            var r = await axios.get("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg", {
                params: { songmid: mid, format: "json", nobase64: "1", platform: "yqq" },
                headers: yHeaders(),
            });
            var d = r.data || {};
            var lrc = d.lyric || "";
            var trans = d.trans || d.tlyric || "";
            if (!lrc && !trans) return { rawLrc: "" };
            return { rawLrc: lrc, translation: trans || undefined };
        } catch (e) { return { rawLrc: "" }; }
    },

    /** 本地记录的最近播放（最新在前）。 */
    async getRecentPlays(ctx) {
        try {
            return JSON.parse(localStorage.getItem(RECENT_KEY) || "[]");
        } catch (e) { return []; }
    },

    // ------------------------------------------------------------- MV

    /** MV：item.mvId 为 vid；getinfo 响应是 "QZOutputJson=" 前缀且以 ';' 结尾。 */
    async getMvUrl(ctx, mvId, resolution) {
        var vid = String(mvId || "");
        if (!vid) throw new Error("缺少 MV vid");
        var r = await axios.get("https://vv.video.qq.com/getinfo", {
            params: { vids: vid, platform: "11001", otype: "json", charge: "0", defn: "shd" },
            headers: yHeaders(),
        });
        var text = typeof r.data === "string" ? r.data : "";
        var json = evalLiteral(text.replace(/^QZOutputJson=/, "").replace(/;\s*$/, ""));
        var vi = (((json || {}).vl || {}).vi || [])[0] || {};
        var ui = (vi.ul && vi.ul.ui) || [];
        var fn = vi.fn || "";
        var fvkey = vi.fvkey || "";
        for (var i = 0; i < ui.length; i++) {
            var base = ui[i].url || "";
            if (base && fn) {
                base = base.replace(/^http:\/\//, "https://");
                return { url: base + fn + (fvkey ? "?vkey=" + fvkey : "") };
            }
        }
        throw new Error("未取到 MV 地址");
    },

    async getMvCover(ctx, mvId) {
        return "https://shp.qpic.cn/qqvideo_ori/0/" + String(mvId || "") + "_360_180/0";
    },
};

function evalLiteral(text) {
    try { return eval("(" + text + ")"); } catch (e) { return null; }
}
