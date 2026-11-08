// Local harness: stub the Halcyon QuickJS sandbox globals with Node equivalents,
// then exercise each plugin method with real network calls.
// Usage: node test-harness.js <kuwo|kugou>
const fs = require("fs");
const path = require("path");

// ---- globals the sandbox provides ----
globalThis.module = { exports: {} };
globalThis.exports = globalThis.module.exports;

const storage = new Map();
globalThis.localStorage = {
    getItem: (k) => (storage.has(k) ? storage.get(k) : null),
    setItem: (k, v) => storage.set(k, String(v)),
    removeItem: (k) => storage.delete(k),
    clear: () => storage.clear(),
};

globalThis.he = {
    decode: (s) => String(s == null ? "" : s)
        .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(+n))
        .replace(/&#x([0-9a-fA-F]+);/g, (_, n) => String.fromCharCode(parseInt(n, 16)))
        .replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&#39;/g, "'")
        .replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
        .replace(/&nbsp;/g, " "),
};

const crypto = require("crypto");
globalThis.CryptoJS = {
    MD5: (v) => ({ toString: () => crypto.createHash("md5").update(String(v)).digest("hex") }),
};

// axios stub using fetch; mirrors __mf_http: JSON-parse body when it looks like JSON,
// expose headers.get(k) case-insensitively.
function buildUrl(url, params) {
    if (!params) return url;
    const usp = new URLSearchParams();
    for (const [k, v] of Object.entries(params)) usp.append(k, v);
    return url + (url.includes("?") ? "&" : "?") + usp.toString();
}
async function http(method, url, options = {}) {
    const res = await fetch(buildUrl(url, options.params), {
        method,
        headers: { "User-Agent": "Mozilla/5.0", ...(options.headers || {}) },
        body: options.data != null ? String(options.data) : undefined,
        redirect: options.followRedirects === false ? "manual" : "follow",
        signal: AbortSignal.timeout(15000),
    });
    const text = await res.text();
    let data = text;
    const t = text.trim();
    if (t.startsWith("{") || t.startsWith("[")) { try { data = JSON.parse(text); } catch { } }
    const headers = {};
    res.headers.forEach((v, k) => { headers[k] = v; });
    // mirror the sandbox: set-cookie flattened to "a=1; b=2"
    const sc = res.headers.getSetCookie?.() || [];
    if (sc.length) headers["set-cookie"] = sc.map(c => c.split(";")[0].trim()).filter(Boolean).join("; ");
    headers.get = (k) => headers[String(k).toLowerCase()] || null;
    return { status: res.status, headers, data };
}
globalThis.axios = {
    get: (url, config) => http("GET", url, config),
    post: (url, data, config) => http("POST", url, { ...(config || {}), data }),
};

// ---- load plugin ----
const name = process.argv[2] || "kuwo";
const keyword = process.argv[3] || "周杰伦";
const src = fs.readFileSync(path.join(__dirname, `${name}.js`), "utf8");
new Function(src)(); // same wrap as the sandbox: function scope + module/exports
const plugin = globalThis.module.exports.default || globalThis.module.exports;

const ctx = { auth: null, quality: "auto", pluginId: plugin.id };

async function main() {
    console.log(`== plugin ${plugin.platform} (${plugin.id}) v${plugin.version}`);
    console.log(`   methods=${JSON.stringify(plugin.account?.methods)}`);

    // 1. search (may legitimately require login — qishui anonymous)
    let candidates = [];
    try {
        const s = await plugin.search(ctx, keyword, 1);
        console.log(`\n[search] rows=${s.data.length} isEnd=${s.isEnd}`);
        candidates = s.data;
        const item = s.data[0];
        if (item) console.log("   first:", JSON.stringify({ id: item.id, title: item.title, artist: item.artist, duration: item.duration, artwork: (item.artwork || "").slice(0, 80), mvId: item.mvId }));
    } catch (e) {
        console.log(`\n[search] skipped: ${e.message}`);
        if (plugin.getDailySongs) {
            candidates = await plugin.getDailySongs(ctx);
            console.log(`   [getDailySongs] ${candidates.length} items, first=${candidates[0]?.title}`);
        }
        if (!candidates.length && plugin.getRadio) {
            candidates = await plugin.getRadio(ctx, null);
            console.log(`   [getRadio] ${candidates.length} items, first=${candidates[0]?.title}`);
        }
    }
    const item = candidates[0];
    if (!item) throw new Error("没有任何可用曲目");

    // 2. media source — paid tracks legitimately fail; find the first playable item.
    let ms = null, played = null;
    for (const cand of candidates.slice(0, 12)) {
        try { ms = await plugin.getMediaSource(ctx, cand, "128k"); played = cand; break; }
        catch { /* restricted */ }
    }
    if (!ms) throw new Error("前 12 首全部无法解析");
    console.log(`\n[getMediaSource] (${played.title}) url=${String(ms.url).slice(0, 90)} label=${ms.qualityLabel} fmt=${ms.format}`);

    // 3. lyric (use the playable item so paywalled tracks don't skew the check)
    const l = await plugin.getLyric(ctx, played || item);
    console.log(`\n[getLyric] lrc ${l.rawLrc ? `${l.rawLrc.split("\n").length} lines: ${l.rawLrc.split("\n").find(x => x.includes("]")).slice(0, 60)}` : "(empty)"}`);

    // 4. collections
    const c = await plugin.getCollections(ctx);
    console.log(`\n[getCollections] favorites=${c.favorites.length} playlists=${c.playlists.length}`);
    for (const p of c.playlists.slice(0, 3)) console.log(`   - ${p.name}: ${p.songs.length} songs, first=${p.songs[0]?.title || "-"}`);

    // 5. recent plays (after the getMediaSource push)
    const rp = await plugin.getRecentPlays(ctx);
    console.log(`\n[getRecentPlays] ${rp.length} entries, first=${rp[0]?.item?.title}`);

    // 6. comments (if the plugin advertises them)
    if (plugin.capabilities?.comments && plugin.getComments) {
        try {
            const cp = await plugin.getComments(ctx, played || item, { pageNo: 1 });
            console.log(`\n[getComments] ${cp.comments.length} comments, total=${cp.totalCount} hasMore=${cp.hasMore}`);
        } catch (e) { console.log(`\n[getComments] failed: ${e.message}`); }
    }

    console.log("\nALL OK");
}

main().catch((e) => { console.error("FAILED:", e); process.exit(1); });
