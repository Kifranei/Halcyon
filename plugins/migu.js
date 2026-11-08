/**
 * 咪咕音乐 — Halcyon 音源插件（Source Plugin API v1）
 *
 * 免登录匿名模式：无需账号，收藏列表为空；"歌单"取自歌单搜索，
 * "最近播放"由插件本地记录。
 *
 * 实测可用端点（2026-11）：
 *   搜索  GET  http://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/search_all.do
 *              （searchSwitch 是 JSON 字符串：{"song":1} 搜单曲 / {"songlist":1} 搜歌单）
 *   播放  GET  http://app.pd.nf.migu.cn/MIGUM2.0/v1.0/content/sub/listenSong.do
 *              返回 302，Location 即 mp3 地址 —— 必须 followRedirects:false 取头
 *   歌词  直接 GET 搜索行里的 lyricUrl（LRC 文本）
 *   歌单  GET  http://c.musicapp.migu.cn/MIGUM3.0/resource/playlist/song/v2.0
 *
 * 音质阶梯：SQ(无损)→HQ(320k)→PQ(128k)，逐个尝试，302 才有效。
 */

var RECENT_KEY = "migu_recent_plays";
var RECENT_MAX = 100;
var LIST_SEED = "华语";        // 歌单搜索种子词（匿名模式没有"我的歌单"）
var LIST_COUNT = 8;
var LIST_SIZE = 60;

function miguHeaders(extra) {
    var h = {
        channel: "0146921",
        Referer: "http://music.migu.cn/v3/music/player/audio",
        "User-Agent": "Mozilla/5.0",
    };
    if (extra) for (var k in extra) h[k] = extra[k];
    return h;
}

function pickCover(items) {
    if (!items || !items.length) return "";
    var pref = { "03": 0, "02": 1, "01": 2 };
    var best = items[0], bestRank = 99;
    for (var i = 0; i < items.length; i++) {
        var rank = pref[items[i].imgSizeType];
        if (rank == null) rank = 50;
        if (rank < bestRank && items[i].img) { best = items[i]; bestRank = rank; }
    }
    return best.img || "";
}

function joinNames(list, field) {
    if (!list) return "";
    return list.map(function (x) { return x[field] || x.name || ""; }).filter(Boolean).join("/");
}

/** search_all.do 单曲行 → musicItem */
function miguItem(row) {
    return {
        id: String(row.id || row.songId || ""),
        contentId: String(row.contentId || ""),
        copyrightId: String(row.copyrightId || ""),
        title: String(row.name || row.songName || ""),
        artist: joinNames(row.singers || row.singerList),
        album: joinNames(row.albums) || String(row.album || ""),
        duration: Number(row.duration) || 0,
        artwork: pickCover(row.imgItems) || row.img3 || row.img2 || row.img1 || "",
        lrcUrl: row.lyricUrl || row.lrcUrl || "",
        trcUrl: row.trcUrl || "",
        mvId: String(row.mvId || ""),
    };
}

/** 音质 → toneFlag 尝试顺序 */
function toneLadder(quality) {
    switch (quality) {
        case "flac24bit":
        case "flac": return ["SQ", "HQ", "PQ"];
        case "320k": return ["HQ", "PQ"];
        case "128k": return ["PQ", "HQ"];
        default: return ["HQ", "PQ", "SQ"];   // auto：先 320k，退回 128k，再试无损
    }
}

var TONE_LABEL = { SQ: "flac", HQ: "320k", PQ: "128k" };

function pushRecent(item) {
    try {
        var list = JSON.parse(localStorage.getItem(RECENT_KEY) || "[]");
        list.unshift({ item: item, playedAt: Date.now() });
        if (list.length > RECENT_MAX) list.length = RECENT_MAX;
        localStorage.setItem(RECENT_KEY, JSON.stringify(list));
    } catch (e) {}
}

module.exports = {
    platform: "咪咕音乐",
    id: "migu",
    version: "1.0.0",
    author: "Halcyon",
    apiVersion: 1,
    account: { methods: ["none"] },
    capabilities: { collections: true, search: true, lyric: true, recentPlays: true },

    /** 搜索：page 1 起始。 */
    async search(ctx, keyword, page) {
        var r = await axios.get("http://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/search_all.do", {
            params: {
                isCopyright: "1", isCorrect: "1",
                pageNo: String(Math.max(1, page || 1)), pageSize: "30",
                searchSwitch: JSON.stringify({ song: 1 }),
                text: keyword,
            },
            headers: miguHeaders(),
        });
        var rows = ((((r.data || {}).songResultData || {}).result) || []);
        var data = rows.map(miguItem).filter(function (it) { return it.title && it.copyrightId; });
        return { data: data, isEnd: data.length === 0 };
    },

    /**
     * 播放解析：listenSong.do 返回 302 Location=mp3。
     * 需要 copyrightId + contentId；按音质阶梯尝试，取到第一个 302 为止。
     */
    async getMediaSource(ctx, item, quality) {
        if (!item.copyrightId) throw new Error("缺少 copyrightId");
        var ladder = toneLadder(quality || ctx.quality);
        var lastErr = "未取到播放地址";
        for (var i = 0; i < ladder.length; i++) {
            var tone = ladder[i];
            try {
                var r = await axios.get("http://app.pd.nf.migu.cn/MIGUM2.0/v1.0/content/sub/listenSong.do", {
                    params: {
                        toneFlag: tone, netType: "01",
                        userId: "15548614588710179085069",
                        ua: "Android_migu", version: "5.1",
                        copyrightId: item.copyrightId,
                        contentId: item.contentId || "",
                        resourceType: "2", channel: "0",
                    },
                    headers: miguHeaders(),
                    followRedirects: false,
                });
                var loc = r.headers && r.headers.get ? r.headers.get("location") : null;
                if (r.status >= 300 && r.status < 400 && loc) {
                    pushRecent(item);
                    return {
                        url: loc,
                        format: "mp3",
                        mimeType: "audio/mpeg",
                        bitRate: tone === "SQ" ? 0 : (tone === "HQ" ? 320000 : 128000),
                        qualityLabel: TONE_LABEL[tone] || tone,
                    };
                }
                lastErr = "音质 " + tone + " 不可用（HTTP " + r.status + "）";
            } catch (e) {
                lastErr = String(e && e.message || e);
            }
        }
        throw new Error(lastErr);
    },

    /** 歌词：搜索行自带 lyricUrl，直接拉文本。 */
    async getLyric(ctx, item) {
        var url = item.lrcUrl || item.lyricUrl || "";
        if (!url) return { rawLrc: "" };
        try {
            var r = await axios.get(url, { headers: miguHeaders() });
            var text = typeof r.data === "string" ? r.data : "";
            return { rawLrc: text };
        } catch (e) { return { rawLrc: "" }; }
    },

    /**
     * 全量同步（匿名）：歌单搜索找一组歌单，逐个取曲目；favorites 为空。
     */
    async getCollections(ctx) {
        var r = await axios.get("http://app.c.nf.migu.cn/MIGUM2.0/v1.0/content/search_all.do", {
            params: {
                isCopyright: "1", isCorrect: "1", pageNo: "1", pageSize: String(LIST_COUNT),
                searchSwitch: JSON.stringify({ songlist: 1 }),
                text: LIST_SEED,
            },
            headers: miguHeaders(),
        });
        var lists = ((((r.data || {}).songListResultData || {}).result) || []).slice(0, LIST_COUNT);
        var playlists = [];
        for (var i = 0; i < lists.length; i++) {
            var pl = lists[i];
            var pid = pl.id || pl.txtId;
            if (pid == null) continue;
            var songs = [];
            try {
                var d = await axios.get("http://c.musicapp.migu.cn/MIGUM3.0/resource/playlist/song/v2.0", {
                    params: { pageNo: "1", pageSize: String(LIST_SIZE), playlistId: String(pid) },
                    headers: miguHeaders(),
                });
                var rows = ((((d.data || {}).data) || {}).songList) || (d.data || {}).songList || [];
                songs = rows.map(miguItem).filter(function (it) { return it.title && it.copyrightId; });
            } catch (e) { /* 单个歌单失败不阻塞整体同步 */ }
            playlists.push({
                id: "pl:" + pid,
                name: pl.name || pl.title || "歌单",
                coverUrl: pl.musicListPicUrl || pl.img || "",
                songs: songs,
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
