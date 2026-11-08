/**
 * 酷狗音乐 — Halcyon 音源插件（Source Plugin API v1）
 *
 * 登录方式：Cookie（userid+token）/ 免登录。
 *   - 免登录：收藏为空；"歌单"取自酷狗公开排行榜；最近播放本地记录。
 *   - 登录：凭据透传 playInfo 解锁 VIP 曲目（取决于账号）；酷狗用户曲库
 *     接口当前不可达，歌单同步仍为榜单。
 *
 * 实测可用端点（2026-11，全部无需鉴权）：
 *   搜索  GET http://mobilecdn.kugou.com/api/v3/search/song
 *   播放  GET http://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo
 *         （免费曲目返回 url + backup_url；按 sqhash/320hash/hash 逐级尝试）
 *   歌词  GET http://krcs.kugou.com/search      → 候选 id + accesskey
 *         GET http://lyrics.kugou.com/download  → base64 LRC
 *   榜单  GET http://mobilecdnbj.kugou.com/api/v3/rank/list
 *         GET http://mobilecdnbj.kugou.com/api/v3/rank/song
 *
 * 已放弃：MV（trackermv 签名公式已失效）、扫码（v2/qr_login 无响应）、
 *         用户歌单同步（user.kugou.com / usercenter 端点已下线）。
 */

var RECENT_KEY = "kg_recent_plays";
var RECENT_MAX = 100;
var RANK_COUNT = 6;          // 每次同步抓取的榜单数
var RANK_SIZE = 60;          // 每个榜单抓取的曲目数

function coverOf(row) {
    var tp = row.trans_param || {};
    var u = tp.union_cover || row.union_cover || "";
    return u ? String(u).replace("{size}", "150") : "";
}

function kgItem(row) {
    var filename = String(row.filename || "");
    var artist = String(row.singername || "");
    var title = String(row.songname || "");
    if (!artist && filename.indexOf(" - ") >= 0) artist = filename.split(" - ")[0].trim();
    if (!title && filename.indexOf(" - ") >= 0) title = filename.split(" - ")[1].trim();
    return {
        id: String(row.hash || ""),
        hash: String(row.hash || ""),
        sqhash: String(row.sqhash || ""),
        hash320: String(row["320hash"] || ""),
        albumId: String(row.album_id || ""),
        albumAudioId: String(row.album_audio_id || ""),
        title: title,
        artist: artist,
        album: String(row.album_name || ""),
        duration: Number(row.duration) || 0,      // 秒
        artwork: coverOf(row),
        mvId: String(row.mvhash || ""),
    };
}

/** ctx.auth → {userid, token}；粘贴的 cookie/凭据里抽取。 */
function authOf(ctx) {
    var a = ctx && ctx.auth;
    if (!a) return null;
    if (typeof a === "string") {
        var uid = (/(?:^|[;\s])userid=([^;\s]+)/i.exec(a) || [])[1];
        var tk = (/(?:^|[;\s])(?:token|KugooToken)=([^;\s]+)/i.exec(a) || [])[1];
        return (uid && tk) ? { userid: uid, token: tk } : null;
    }
    var u = a.userid || a.uid || "";
    var t = a.token || a.KugooToken || "";
    return (u && t) ? { userid: String(u), token: String(t) } : null;
}

/** m.kugou playInfo：免费曲目 data.url 非空；带 userid/token 可解 VIP（取决于账号）。 */
async function playInfo(hash, auth) {
    try {
        var params = { cmd: "playInfo", hash: hash };
        if (auth) { params.userid = auth.userid; params.token = auth.token; }
        var r = await axios.get("http://m.kugou.com/app/i/getSongInfo.php", {
            params: params,
        });
        var d = (r && r.data) || {};
        if (!d || d.errcode) return null;
        var url = d.url || ((d.backup_url || [])[0]) || "";
        if (!/^https?:\/\//.test(url)) return null;
        return d;
    } catch (e) { return null; }
}

function pushRecent(item) {
    try {
        var list = JSON.parse(localStorage.getItem(RECENT_KEY) || "[]");
        list.unshift({ item: item, playedAt: Date.now() });
        if (list.length > RECENT_MAX) list.length = RECENT_MAX;
        localStorage.setItem(RECENT_KEY, JSON.stringify(list));
    } catch (e) {}
}

async function rankList() {
    var hosts = ["http://mobilecdnbj.kugou.com", "http://mobilecdn.kugou.com"];
    for (var i = 0; i < hosts.length; i++) {
        try {
            var r = await axios.get(hosts[i] + "/api/v3/rank/list", {
                params: {
                    version: "9108", plat: "0", showtype: "2", parentid: "0",
                    apiver: "6", area_code: "1",
                },
            });
            var info = (((r.data || {}).data || {}).info) || [];
            if (info.length) return info;
        } catch (e) {}
    }
    return [];
}

async function rankSongs(rankid) {
    var hosts = ["http://mobilecdnbj.kugou.com", "http://mobilecdn.kugou.com"];
    for (var i = 0; i < hosts.length; i++) {
        try {
            var r = await axios.get(hosts[i] + "/api/v3/rank/song", {
                params: {
                    version: "9108", ranktype: "1", plat: "0", pagesize: String(RANK_SIZE),
                    area_code: "1", page: "1", rankid: String(rankid), apiver: "6",
                },
            });
            return (((r.data || {}).data || {}).info) || [];
        } catch (e) {}
    }
    return [];
}

module.exports = {
    platform: "酷狗音乐",
    id: "kugou",
    version: "1.0.0",
    author: "Halcyon",
    apiVersion: 1,
    account: { methods: ["cookie", "none"] },
    capabilities: { collections: true, search: true, lyric: true, recentPlays: true },

    /**
     * 凭据登录：粘贴 "userid=xxx; token=yyy"（或 "userid:token" 形式 / 含
     * KugooToken 的整段 Cookie）。公开网没有可用的用户校验接口——
     * user.kugou.com / usercenter-dataservice 均已失效——所以本地只校验
     * 字段是否齐全，凭据透传给 playInfo 尝试解锁 VIP 曲目；
     * 曲库同步仍是匿名榜单（用户歌单接口当前不可达）。
     */
    async login(ctx, params) {
        var raw = String((params && params.cookie) || "").trim();
        if (!raw) throw new Error("凭据为空");
        var uid = (/(?:^|[;\s])userid[:=]\s*([^;\s]+)/i.exec(raw) || [])[1]
            || (/^(\d{5,})[:;|]/.exec(raw) || [])[1];
        var token = (/(?:^|[;\s])(?:token|KugooToken)[:=]\s*([^;\s]+)/i.exec(raw) || [])[1]
            || (/[:;|]\s*([0-9a-z]{20,})$/i.exec(raw) || [])[1];
        if (!uid || !token) {
            throw new Error("需要 userid 与 token：粘贴 \"userid=xxx; token=yyy\" 或含 KugooToken 的 Cookie");
        }
        return {
            userId: String(uid),
            nickname: "酷狗用户 " + uid,
            avatar: "",
            credential: { userid: String(uid), token: String(token) },
        };
    },

    /** 搜索：page 1 起始；isEnd 按 total 判断。 */
    async search(ctx, keyword, page) {
        var pagesize = 30;
        var p = Math.max(1, page || 1);
        var r = await axios.get("http://mobilecdn.kugou.com/api/v3/search/song", {
            params: {
                format: "json", keyword: keyword, page: p,
                pagesize: pagesize, showtype: 1,
            },
        });
        var d = (r.data || {}).data || {};
        var rows = d.info || [];
        var total = d.total || 0;
        var data = rows.map(kgItem).filter(function (it) { return it.title && it.id; });
        return { data: data, isEnd: data.length === 0 || p * pagesize >= total };
    },

    /**
     * 播放解析：按音质档在 sqhash（无损）/ 320hash / hash（128k）之间逐级尝试。
     * 免费曲目取到 url；全部失败说明付费或受限，抛错。
     */
    async getMediaSource(ctx, item, quality) {
        var candidates = [];
        var want = String(quality || "auto");
        if (want === "flac" || want === "flac24bit") {
            candidates = [item.sqhash, item.hash320, item.hash];
        } else if (want === "320k") {
            candidates = [item.hash320, item.hash];
        } else {
            candidates = [item.hash, item.hash320, item.sqhash];
        }
        var auth = authOf(ctx);
        for (var i = 0; i < candidates.length; i++) {
            var h = candidates[i];
            if (!h) continue;
            var d = await playInfo(h, auth);
            if (!d) continue;
            var url = d.url || (d.backup_url || [])[0];
            var labels = { mp3: (d.bitRate || 128) + "k", flac: "FLAC", ogg: "ogg" };
            pushRecent(item);
            return {
                url: url,
                format: d.extName || "mp3",
                mimeType: "audio/" + (d.extName === "flac" ? "flac" : "mpeg"),
                bitRate: (d.bitRate || 0) * 1000,
                qualityLabel: labels[d.extName] || ((d.bitRate || 128) + "k"),
            };
        }
        throw new Error(auth
            ? "未取到播放地址（曲目可能仍需 VIP 或不可用）"
            : "未取到播放地址（付费/受限曲目：可在插件账号里填酷狗凭据）");
    },

    /**
     * 歌词：krcs 按 hash 找候选（挑时长最接近的），download 拿 base64 LRC。
     * Buffer.from(base64,'base64').toString() 完成 utf8 解码。
     */
    async getLyric(ctx, item) {
        var hash = item.hash || item.id;
        if (!hash) return { rawLrc: "" };
        try {
            var r = await axios.get("http://krcs.kugou.com/search", {
                params: {
                    ver: "1", man: "yes", client: "mobi",
                    keyword: "", duration: "", hash: hash, album_audio_id: "",
                },
            });
            var candidates = (r.data || {}).candidates || [];
            if (!candidates.length) return { rawLrc: "" };
            var best = candidates[0], want = (item.duration || 0) * 1000;
            for (var i = 0; i < candidates.length; i++) {
                if (Math.abs((candidates[i].duration || 0) - want) < Math.abs((best.duration || 0) - want)) best = candidates[i];
            }
            var d = await axios.get("http://lyrics.kugou.com/download", {
                params: {
                    ver: "1", client: "pc", id: best.id,
                    accesskey: best.accesskey, fmt: "lrc", charset: "utf8",
                },
            });
            var content = (d.data || {}).content || "";
            if (!content) return { rawLrc: "" };
            var lrc = Buffer.from(content, "base64").toString("utf8");
            return { rawLrc: lrc.replace(/^﻿/, "") };
        } catch (e) { return { rawLrc: "" }; }
    },

    /**
     * 全量同步（匿名）：取酷狗排行榜前若干名作为 playlists；favorites 为空。
     */
    async getCollections(ctx) {
        var ranks = (await rankList()).slice(0, RANK_COUNT);
        var playlists = [];
        for (var i = 0; i < ranks.length; i++) {
            var rk = ranks[i];
            var rid = String(rk.rankid || rk.id || "");
            if (!rid) continue;
            var rows = await rankSongs(rid);
            playlists.push({
                id: "rank:" + rid,
                name: rk.rankname || "榜单",
                coverUrl: String(rk.img_9 || rk.banner_9 || "").replace("{size}", "400"),
                songs: rows.map(kgItem).filter(function (it) { return it.title && it.id; }),
            });
        }
        return { favorites: [], playlists: playlists, favoritePlaylistId: "" };
    },

    /** 本地记录的最近播放（最新在前）。 */
    async getRecentPlays(ctx) {
        try {
            return JSON.parse(localStorage.getItem(RECENT_KEY) || "[]");
        } catch (e) { return []; }
    },
};
