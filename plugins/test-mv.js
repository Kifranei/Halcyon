// MV check: node test-mv.js <plugin> <mvId>
const fs = require("fs");
globalThis.module = { exports: {} }; globalThis.exports = module.exports;
globalThis.localStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} };
globalThis.he = { decode: s => s };
function buildUrl(u, p) { return p ? u + (u.includes("?") ? "&" : "?") + new URLSearchParams(p) : u; }
async function http(m, u, o = {}) {
    const res = await fetch(buildUrl(u, o.params), { method: m, headers: { "User-Agent": "Mozilla/5.0", ...(o.headers || {}) }, body: o.data, redirect: o.followRedirects === false ? "manual" : "follow", signal: AbortSignal.timeout(15000) });
    const t = await res.text(); let d = t; const s = t.trim();
    if (s.startsWith("{") || s.startsWith("[")) try { d = JSON.parse(t) } catch { }
    const h = {}; res.headers.forEach((v, k) => h[k] = v); h.get = k => h[String(k).toLowerCase()] || null;
    return { status: res.status, headers: h, data: d };
}
globalThis.axios = { get: (u, c) => http("GET", u, c), post: (u, d, c) => http("POST", u, { ...(c || {}), data: d }) };
new Function(fs.readFileSync(process.argv[2] + ".js", "utf8"))();
const p = globalThis.module.exports.default || globalThis.module.exports;
p.getMvUrl({}, process.argv[3], "hd")
    .then(r => console.log("MV OK:", String(r.url).slice(0, 120)))
    .then(() => p.getMvCover ? p.getMvCover({}, process.argv[3]).then(c => console.log("cover:", c)) : 0)
    .catch(e => { console.log("MV FAIL:", e.message); process.exit(1); });
