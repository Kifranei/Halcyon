/**
 * 汽水音乐 — Halcyon 音源插件（Source Plugin API v1）
 *
 * 登录方式：扫码（抖音 App）/ 粘贴 Cookie / 免登录。
 *   - 免登录：可播放（seo_track 试听流，30s 左右）、歌词、评论、榜单/相关推荐、推荐歌单。
 *   - 登录后：解锁 PC 搜索、我的歌单、收藏混合流（完整曲目取决于账号权益）。
 *
 * 端点（参考 guowenye/qishui-api，实测 2026-11）：
 *   QR   GET  https://api.qishui.com/passport/web/get_qrcode/
 *        POST https://api.qishui.com/passport/web/check_qrconnect/   （需带回建码时的 Cookie）
 *   我   GET  https://api.qishui.com/luna/pc/me | me/playlist | me/collection/mixed
 *   搜索 GET  https://api.qishui.com/luna/pc/search/track            （匿名返回空体 → 需登录）
 *   详情 GET  https://beta-luna.douyin.com/luna/h5/seo_track          （曲目+歌词+播放器+评论+相关）
 *   播放 seo_track.track_player.url_player_info 拼 &Vid=<track.vid> → Data.PlayInfoList
 *   推荐 POST https://beta-luna.douyin.com/luna/discover/mix          （匿名可用）
 *   评论 GET  https://beta-luna.douyin.com/luna/comments              （group_id=track_id）
 *
 * 注意：PC 端 X-Helios/X-Medusa 签名未实现，匿名搜索不可用是已知边界。
 */

var PC = "https://api.qishui.com";
var LUNA = "https://beta-luna.douyin.com";
var AID = "386088";
var IID = "27960026095955";
var VC = "30020100";
var WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36";

var RECENT_KEY = "qs_recent_plays";
var SEED_KEY = "qs_last_seed";
var DID_KEY = "qs_device_id";
var IID_KEY = "qs_install_id";
var QR_COOKIE_PREFIX = "qs_qr_cookie:";
var RECENT_MAX = 100;
var FALLBACK_SEED = "7497192086862071809";   // 无播放历史时的推荐种子曲

function digits(n) {
    var s = "";
    for (var i = 0; i < n; i++) s += Math.floor(Math.random() * 10);
    return s;
}
function deviceId() {
    var v = localStorage.getItem(DID_KEY);
    if (!v) { v = digits(16); localStorage.setItem(DID_KEY, v); }
    return v;
}
function installId() {
    var v = localStorage.getItem(IID_KEY);
    if (!v) { v = digits(15); localStorage.setItem(IID_KEY, v); }
    return v;
}

function headers(cookie) {
    var h = { "User-Agent": WEB_UA };
    if (cookie) h.Cookie = cookie;
    return h;
}
function cookieOf(ctx) {
    var a = ctx && ctx.auth;
    if (!a) return "";
    if (typeof a === "string") return a;
    return a.cookie || a.sessionid && ("sessionid=" + a.sessionid) || "";
}

/** PC 端公共参数（device_platform=windows 那套）。 */
function pcParams(extra) {
    var p = {
        aid: AID, app_name: "luna_pc",
        region: "cn", geo_region: "cn", os_region: "cn", sim_region: "",
        device_id: deviceId(), cdid: "", iid: installId(),
        version_name: "3.0.0", version_code: "30000000",
        channel: "official", build_mode: "master",
        network_carrier: "", ac: "wifi", tz_name: "Asia/Shanghai",
        resolution: "", device_platform: "windows", device_type: "Windows",
        os_version: "Windows 11", fp: deviceId(),
    };
    if (extra) for (var k in extra) p[k] = extra[k];
    return p;
}

function pickUrl(v) {
    if (!v) return "";
    if (typeof v === "string") return v;
    if (Array.isArray(v)) return v[0] || "";
    if (Array.isArray(v.urls) && v.urls.length) return (v.urls[0] || "") + (v.uri || "");
    return v.url || v.uri || v.template || "";
}

/** 汽水道轨对象 → musicItem。duration 单位是毫秒。 */
function qsItem(t) {
    if (!t || typeof t !== "object") return null;
    var id = t.id != null ? String(t.id) : "";
    if (!id) return null;
    var artists = (t.artists || []).map(function (a) {
        return a && (a.name || a.simple_display_name || (a.user_info || {}).nickname) || "";
    }).filter(Boolean).join("/");
    var album = t.album || {};
    return {
        id: id,
        vid: String(t.vid || ""),
        title: String(t.name || t.trackName || ""),
        artist: artists,
        album: String(album.name || ""),
        duration: Number(t.duration || t.duration_ms) || 0,
        artwork: pickUrl(album.url_cover || t.url_cover || t.cover_url),
    };
}

/** feed/资源对象里剥出 track。 */
function unwrapTrack(res) {
    var e = (res && res.entity) || res || {};
    return (e.track_wrapper && e.track_wrapper.track) || e.track || res.track || null;
}

/** h5/seo_track 完整响应（track+lyric+player+comments+相关曲）。 */
async function seoTrack(trackId, cookie) {
    var r = await axios.get(LUNA + "/luna/h5/seo_track", {
        params: { track_id: String(trackId), device_platform: "web" },
        headers: headers(cookie),
    });
    var d = r.data;
    if (!d || typeof d !== "object" || !(d.seo_track && d.seo_track.track)) {
        throw new Error("seo_track 无数据（track_id=" + trackId + "）");
    }
    return d;
}

/** VOD 播放信息：url_player_info + Vid → Data.PlayInfoList[]。 */
async function playInfoList(track, cookie) {
    var r = await seoTrack(track.id, cookie);
    var t = (r.seo_track || {}).track || track;
    var upi = ((r.track_player || {}).url_player_info) || "";
    if (!upi) throw new Error("无播放信息");
    var vid = t.vid || track.vid || "";
    if (vid && upi.indexOf("Vid=") < 0) upi += (upi.indexOf("?") < 0 ? "?" : "&") + "Vid=" + encodeURIComponent(vid);
    var v = await axios.get(upi, { headers: headers() });
    var data = v.data || {};
    var list = ((data.Result || data).Data || {}).PlayInfoList || [];
    if (!list.length) throw new Error("VOD 无可用播放地址");
    return { list: list, track: t };
}

/** 音质 → PlayInfo.Quality 偏好顺序（observed: medium/higher/highest，m4a AAC）。 */
function qualityLadder(q) {
    switch (q) {
        case "flac24bit":
        case "flac":
        case "320k": return ["highest", "higher", "medium"];
        case "128k": return ["medium", "higher", "highest"];
        default: return ["higher", "highest", "medium"];   // auto
    }
}
function pickPlay(list, quality) {
    var ladder = qualityLadder(quality);
    for (var i = 0; i < ladder.length; i++) {
        for (var j = 0; j < list.length; j++) {
            if (String(list[j].Quality || "").toLowerCase() === ladder[i]) return list[j];
        }
    }
    return list[0];
}

function pushRecent(item) {
    try {
        var list = JSON.parse(localStorage.getItem(RECENT_KEY) || "[]");
        list.unshift({ item: item, playedAt: Date.now() });
        if (list.length > RECENT_MAX) list.length = RECENT_MAX;
        localStorage.setItem(RECENT_KEY, JSON.stringify(list));
        if (item.id) localStorage.setItem(SEED_KEY, String(item.id));
    } catch (e) {}
}

/** karaoke 歌词 [ms,dur]<a,b,c>字 转 LRC。 */
function karaokeToLrc(raw) {
    if (!raw || raw.indexOf("[") < 0) return raw || "";
    var lines = String(raw).split(/\r?\n/), out = [];
    for (var i = 0; i < lines.length; i++) {
        var m = /^\[(\d+)(?:,\d+)?\]/.exec(lines[i]);
        if (!m) { out.push(lines[i]); continue; }
        var ms = Number(m[1]);
        var mm = Math.floor(ms / 60000), ss = (ms % 60000) / 1000;
        var stamp = "[" + (mm < 10 ? "0" : "") + mm + ":" + (ss < 10 ? "0" : "") + ss.toFixed(2) + "]";
        var text = lines[i].replace(/^\[\d+(?:,\d+)?\]/, "").replace(/<[^>]*>/g, "");
        out.push(stamp + text);
    }
    return out.join("\n");
}

module.exports = {
    platform: "汽水音乐",
    id: "qishui",
    version: "1.0.0",
    author: "Halcyon",
    apiVersion: 1,
    account: { methods: ["qrcode", "cookie", "none"] },
    capabilities: {
        collections: true, search: true, lyric: true,
        recentPlays: true, comments: true, daily: true, radio: true,
    },

    // ---------------------------------------------------------- 登录

    /** cookie 登录：粘贴 "sessionid=xxx" 或整段 Cookie，用 /luna/pc/me 校验。 */
    async login(ctx, params) {
        var raw = String((params && params.cookie) || "").trim();
        if (!raw) throw new Error("Cookie 为空");
        var cookie = /[;=]/.test(raw) ? raw : "sessionid=" + raw;
        var r = await axios.get(PC + "/luna/pc/me", {
            params: { aid: AID }, headers: headers(cookie),
        });
        var d = r.data;
        var u = (d && (d.user || d.profile || d.data)) || {};
        if (!d || typeof d !== "object" || (!u.id && !u.user_id && !u.nickname)) {
            throw new Error("Cookie 无效或已过期");
        }
        return {
            userId: String(u.id || u.user_id || ""),
            nickname: u.nickname || u.name || "汽水用户",
            avatar: pickUrl(u.medium_avatar_url || u.url_avatar || u.avatar),
            credential: { cookie: cookie },
        };
    },

    /** 扫码：返回 {qr:dataURI, key:token}；建码响应的 Set-Cookie 存起来供轮询用。 */
    async getQrCode(ctx) {
        var r = await axios.get(PC + "/passport/web/get_qrcode/", {
            params: {
                passport_jssdk_version: "2.4.13", passport_jssdk_type: "normal",
                is_from_ttaccountsdk: "1", aid: AID, next: PC,
                need_logo: "false", need_short_url: "false", is_new_login: "1",
            },
            headers: headers(),
        });
        var d = (r.data && r.data.data) || r.data || {};
        if (!d.token || !d.qrcode) {
            throw new Error("二维码获取失败：" + ((d && d.description) || r.status));
        }
        localStorage.setItem(QR_COOKIE_PREFIX + d.token, r.headers.get("set-cookie") || "");
        return { qr: d.qrcode, key: String(d.token) };
    },

    /** 轮询：status waiting/scanned/confirmed/expired/failed。确认后取 set-cookie 里的 sessionid。 */
    async checkQrCode(ctx, key) {
        var cookie = localStorage.getItem(QR_COOKIE_PREFIX + key) || "";
        var r = await axios.post(
            PC + "/passport/web/check_qrconnect/" +
            "?passport_jssdk_version=2.4.13&passport_jssdk_type=normal" +
            "&is_from_ttaccountsdk=1&aid=" + AID + "&iid=" + IID,
            "need_logo=false&need_short_url=false&is_frontier=true&is_new_login=1&next=" +
            encodeURIComponent(PC) + "&token=" + encodeURIComponent(key),
            { headers: { "Content-Type": "application/x-www-form-urlencoded", "User-Agent": WEB_UA, Cookie: cookie } }
        );
        var body = (r.data && r.data.data) || r.data || {};
        var st = String(body.status || "").toLowerCase();
        var err = Number(body.error_code) || 0;
        var sc = r.headers.get("set-cookie") || "";
        var sid = (/(?:^|;\s*)sessionid=([^;\s]+)/i.exec(sc) || [])[1] || "";
        if (sid || /confirm/.test(st)) {
            var cred = { cookie: sid ? "sessionid=" + sid : sc };
            var user = { userId: "", nickname: "汽水用户", avatar: "" };
            try {
                var me = await module.exports.login(ctx, { cookie: cred.cookie });
                user = { userId: me.userId, nickname: me.nickname, avatar: me.avatar };
            } catch (e) {}
            return { status: "confirmed", userId: user.userId, nickname: user.nickname, avatar: user.avatar, credential: cred };
        }
        if (err === 2046) return { status: "failed", error: "需要二次验证" };
        if (err === 2 || /expire|invalid/.test(st)) return { status: "expired" };
        if (/scan/.test(st)) return { status: "scanned" };
        return { status: "waiting" };   // error_code=7 是限流，继续轮询
    },

    // ---------------------------------------------------------- 曲库

    /**
     * 全量同步：登录 → 我的歌单 + 收藏混合流；匿名 → discover/mix 推荐歌单。
     */
    async getCollections(ctx) {
        var cookie = cookieOf(ctx);
        if (cookie) {
            var playlists = [];
            try {
                var r = await axios.get(PC + "/luna/pc/me/playlist", {
                    params: { aid: AID, iid: IID, version_code: VC },
                    headers: headers(cookie),
                });
                var rows = (r.data && r.data.playlists) || [];
                for (var i = 0; i < rows.length; i++) {
                    var pl = rows[i];
                    var pid = pl.id != null ? String(pl.id) : "";
                    if (!pid) continue;
                    playlists.push({
                        id: pid,
                        name: pl.title || pl.name || "歌单",
                        coverUrl: pickUrl(pl.url_cover || pl.cover_url),
                        description: pl.description || pl.intro || "",
                        songs: await playlistSongs(pid, cookie),
                    });
                }
            } catch (e) {}
            var favorites = [];
            try {
                var c = await axios.get(PC + "/luna/pc/me/collection/mixed", {
                    params: { aid: AID }, headers: headers(cookie),
                });
                favorites = collectTracks(c.data).map(qsItem).filter(Boolean);
            } catch (e) {}
            return { favorites: favorites, playlists: playlists, favoritePlaylistId: "" };
        }
        // 匿名：discover/mix 推荐歌单
        var d = await axios.post(LUNA + "/luna/discover/mix", JSON.stringify({ count: 10 }), {
            headers: { "Content-Type": "application/json", "User-Agent": WEB_UA },
        });
        var blocks = (d.data && d.data.inner_block) || [];
        var pls = [];
        for (var b = 0; b < blocks.length; b++) {
            var res = (blocks[b] && blocks[b].resources) || [];
            for (var j = 0; j < res.length; j++) {
                var e = res[j] && (res[j].entity || res[j]);
                var p = e && (e.playlist || e);
                var pid2 = p && (p.id != null ? String(p.id) : "");
                if (!pid2) continue;
                pls.push({
                    id: pid2,
                    name: p.title || p.name || "推荐歌单",
                    coverUrl: pickUrl(p.url_cover || p.cover_url),
                    songs: await playlistSongs(pid2, ""),
                });
            }
        }
        return { favorites: [], playlists: pls.slice(0, 12), favoritePlaylistId: "" };
    },

    /** 日推（实为当前榜单）：以最近播放曲目为种子取 chart_tracks。 */
    async getDailySongs(ctx) {
        var seed = localStorage.getItem(SEED_KEY) || FALLBACK_SEED;
        try {
            var d = await seoTrack(seed, cookieOf(ctx));
            return ((d.chart_tracks) || []).map(function (x) { return qsItem(x.track || x); }).filter(Boolean);
        } catch (e) { return []; }
    },

    /** 电台：以传入曲目（或最近播放）为种子取 artist_tracks。 */
    async getRadio(ctx, seedItem) {
        var seed = (seedItem && seedItem.id) || localStorage.getItem(SEED_KEY) || FALLBACK_SEED;
        var d = await seoTrack(seed, cookieOf(ctx));
        return ((d.artist_tracks) || []).map(function (x) { return qsItem(x.track || x); }).filter(Boolean);
    },

    // ---------------------------------------------------------- 搜索

    /** PC 搜索：需要登录态，匿名时上游返回空体 → 明确报错。 */
    async search(ctx, keyword, page) {
        var cookie = cookieOf(ctx);
        var r = await axios.get(PC + "/luna/pc/search/track", {
            params: pcParams({
                q: keyword, cursor: Math.max(0, (page || 1) - 1) * 20,
                search_method: "input",
            }),
            headers: headers(cookie),
        });
        var d = r.data;
        if (!d || typeof d !== "object") {
            throw new Error(cookie ? "搜索失败（HTTP " + r.status + "）" : "汽水搜索需要登录：请在插件账号里扫码或粘贴 Cookie");
        }
        var items = collectTracks(d).map(qsItem).filter(Boolean);
        return { data: items, isEnd: items.length === 0 };
    },

    // ---------------------------------------------------------- 播放/歌词

    /**
     * 播放：seo_track → url_player_info + Vid → PlayInfoList 按音质挑档。
     * 匿名拿到的是试听流（约 30s），标记 isTrial；登录与否不改变 VOD 链本身。
     */
    async getMediaSource(ctx, item, quality) {
        if (!item.id) throw new Error("缺少 track_id");
        var pi = await playInfoList(item, cookieOf(ctx));
        var pick = pickPlay(pi.list, quality || ctx.quality);
        var url = pick.MainPlayUrl || pick.BackupPlayUrl || "";
        if (!url) throw new Error("播放地址为空");
        pushRecent(item);
        return {
            url: url,
            headers: { "User-Agent": WEB_UA },
            format: String(pick.Format || "m4a").toLowerCase(),
            mimeType: "audio/mp4",
            bitRate: Number(pick.Bitrate) || 0,
            qualityLabel: String(pick.Quality || ""),
            isTrial: !ctx.auth,
        };
    },

    /** 歌词：seo_track.lyric.content（karaoke 格式 → 转标准 LRC）。 */
    async getLyric(ctx, item) {
        try {
            var d = await seoTrack(item.id, cookieOf(ctx));
            var raw = ((d.lyric || {}).content) || "";
            return { rawLrc: karaokeToLrc(raw) };
        } catch (e) { return { rawLrc: "" }; }
    },

    /** 最近播放：本地记录。 */
    async getRecentPlays(ctx) {
        try { return JSON.parse(localStorage.getItem(RECENT_KEY) || "[]"); }
        catch (e) { return []; }
    },

    // ---------------------------------------------------------- 评论

    /** 评论：/luna/comments（group_id=track_id，cursor 分页）；失败回退 seo_track 自带评论。 */
    async getComments(ctx, item, page) {
        var cookie = cookieOf(ctx);
        var cursor = page && page.cursor ? String(page.cursor) : "";
        try {
            var r = await axios.get(LUNA + "/luna/comments", {
                params: {
                    group_id: String(item.id), cursor: cursor, count: "20",
                    group_type: "track", image_strategy: "tplv-luna-shrink:200:200",
                    sort_type: "hot",
                },
                headers: headers(cookie),
            });
            var d = r.data || {};
            var rows = d.comments || d.data || d.comment_list || [];
            if (Array.isArray(rows) && rows.length) {
                return {
                    comments: rows.map(qsComment),
                    totalCount: Number(d.total || d.total_count || d.count) || rows.length,
                    hasMore: Boolean(d.has_more || d.hasMore),
                    cursor: String(d.cursor || d.next_cursor || ""),
                    pageNo: (page && page.pageNo) || 1,
                };
            }
        } catch (e) {}
        // 回退：seo_track 首屏评论
        var s = await seoTrack(item.id, cookie);
        var cc = ((s.comments || {}).comments) || [];
        return {
            comments: cc.map(qsComment),
            totalCount: Number((s.comments || {}).count) || cc.length,
            hasMore: false, cursor: "", pageNo: 1,
        };
    },
};

// -------------------------------------------------------------- helpers

/** 歌单详情 → 曲目数组。 */
async function playlistSongs(pid, cookie) {
    try {
        var r = await axios.post(LUNA + "/luna/playlist/detail",
            JSON.stringify({ playlist_id: String(pid), count: 100 }),
            { headers: Object.assign({ "Content-Type": "application/json" }, headers(cookie)) });
        var rows = (r.data && r.data.media_resources) || [];
        return rows.map(function (x) { return qsItem(unwrapTrack(x)); }).filter(Boolean);
    } catch (e) { return []; }
}

/** 在任意深度对象里收集 track 形对象（有 id + vid + name）。 */
function collectTracks(node, out) {
    out = out || [];
    if (!node || typeof node !== "object") return out;
    if (Array.isArray(node)) {
        for (var i = 0; i < node.length; i++) collectTracks(node[i], out);
        return out;
    }
    var t = unwrapTrack(node);
    if (t && t.id != null && (t.name || t.trackName)) { out.push(t); return out; }
    if (node.id != null && (node.name || node.trackName) && (node.artists || node.vid)) {
        out.push(node); return out;
    }
    for (var k in node) {
        var v = node[k];
        if (v && typeof v === "object") collectTracks(v, out);
    }
    return out;
}

/** 汽水评论 → 宿主评论模型。 */
function qsComment(c) {
    if (!c || typeof c !== "object") return {};
    var u = c.user || c.author || {};
    return {
        id: String(c.id || c.comment_id || ""),
        content: String(c.content || c.text || ""),
        time: Number(c.time_created || c.create_time || c.timestamp) * 1000 || undefined,
        likedCount: Number(c.count_digged || c.like_count || c.digg_count) || 0,
        replyCount: Number(c.count_reply || c.reply_count) || 0,
        user: {
            userId: Number(u.id || u.user_id) || 0,
            nickname: u.nickname || u.name || "",
            avatar: pickUrl(u.medium_avatar_url || u.thumb_avatar_url || u.url_avatar),
        },
    };
}
