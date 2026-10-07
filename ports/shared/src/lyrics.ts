export interface Word {time: number; end?: number; text: string}
export interface Lyric {time: number; end?: number; text: string; words: Word[]; translation?: string; romanization?: string; agent?: string; background?: boolean}
export function clock(value: string, frameRate = 30, tickRate = 1): number {
  const offset = value.match(/^([\d.]+)(h|m|s|ms|f|t)$/);
  if (offset) return Number(offset[1]) * ({h: 3600, m: 60, s: 1, ms: .001, f: 1 / frameRate, t: 1 / tickRate})[offset[2] as 's'];
  const frames = value.match(/^(\d+):(\d+):(\d+):(\d+(?:\.\d+)?)$/);
  if (frames) return Number(frames[1]) * 3600 + Number(frames[2]) * 60 + Number(frames[3]) + Number(frames[4]) / frameRate;
  if (!value.trim()) return NaN;
  const parts = value.split(':').map(Number); return parts.every(Number.isFinite) ? parts.reduce((total, n) => total * 60 + n, 0) : NaN;
}
export function parseLRC(text: string): Lyric[] {
  const offset = Number(text.match(/\[offset:([+-]?\d+)\]/i)?.[1] || 0) / 1000; const result: Lyric[] = [];
  for (const line of text.replace(/\r/g, '').split('\n')) {
    const stamps = [...line.matchAll(/\[(\d+:\d+(?:\.\d+)?)\]/g)]; const content = line.replace(/\[\d+:\d+(?:\.\d+)?\]/g, '').trim();
    const words = [...content.matchAll(/<(\d+:\d+(?:\.\d+)?)>([^<]*)/g)].filter(m => m[2].length).map(m => ({time: clock(m[1]) + offset, text: m[2]}));
    const clean = content.replace(/<\d+:\d+(?:\.\d+)?>/g, '').replace(/^\[v\d+\]/i, '');
    for (const stamp of stamps) if (clean) result.push({time: Math.max(0, clock(stamp[1]) + offset), text: clean, words: words.map(w => ({...w, time: Math.max(0, w.time + clock(stamp[1]) - clock(stamps[0][1]))})), agent: content.match(/^\[v(\d+)\]/i)?.[1]});
  }
  return finalize(result);
}
function finalize(lines: Lyric[]): Lyric[] {
  lines.sort((a, b) => a.time - b.time);
  lines.forEach(line => {
    if (!line.end) line.end = lines.find(next => next.time > line.time && next.agent === line.agent && next.background === line.background)?.time ?? (line.words.at(-1)?.time || line.time) + 8;
    line.words.forEach((word, n) => word.end ??= line.words[n + 1]?.time ?? line.end);
  }); return lines;
}
const role = (element: Element) => element.getAttribute('ttm:role') || element.getAttribute('role') || '';
export function parseLyrics(text: string): Lyric[] {
  if (!text.trim().startsWith('<')) return parseLRC(text);
  const xml = new DOMParser().parseFromString(text, 'text/xml'); if (xml.getElementsByTagName('parsererror').length) return [];
  const root = xml.documentElement; const multiplier = (root.getAttribute('ttp:frameRateMultiplier') || '1 1').split(/\s+/).map(Number);
  const frameRate = (Number(root.getAttribute('ttp:frameRate')) || 30) * (multiplier[0] / multiplier[1]); const tickRate = Number(root.getAttribute('ttp:tickRate')) || frameRate;
  const timings = new WeakMap<Element, {begin: number; end: number}>();
  function timing(element: Element): {begin: number; end: number} {
    const cached = timings.get(element); if (cached) return cached;
    const parent = element.parentElement; const inherited = parent ? timing(parent) : {begin: 0, end: Infinity};
    let base = inherited.begin;
    if (parent?.getAttribute('timeContainer') === 'seq' && element.previousElementSibling) base = Number.isFinite(timing(element.previousElementSibling).end) ? timing(element.previousElementSibling).end : inherited.begin;
    const b = element.getAttribute('begin'); const own = b ? clock(b, frameRate, tickRate) : 0;
    const begin = b?.includes(':') ? own : base + (Number.isFinite(own) ? own : 0);
    const e = element.getAttribute('end'); const d = element.getAttribute('dur');
    const end = e ? (e.includes(':') ? clock(e, frameRate, tickRate) : base + clock(e, frameRate, tickRate)) : d ? begin + clock(d, frameRate, tickRate) : inherited.end;
    const value = {begin, end}; timings.set(element, value); return value;
  }
  function visibleText(node: Node): string {
    if (node.nodeType === 3) return node.textContent || '';
    if (node.nodeType !== 1) return '';
    const element = node as Element;
    if (/translation|roman|x-bg|rubyText/.test(role(element)) || element.getAttribute('tts:ruby') === 'text') return '';
    if (element.localName === 'br') return '\n';
    return Array.from(node.childNodes).map(visibleText).join('');
  }
  const result: Lyric[] = [];
  for (const p of Array.from(xml.getElementsByTagNameNS('*', 'p'))) {
    const spans = Array.from(p.getElementsByTagNameNS('*', 'span')); const t = timing(p);
    if (!Number.isFinite(t.begin)) continue;
    const words = spans.filter(span => span.hasAttribute('begin') && !Array.from(span.getElementsByTagNameNS('*', 'span')).some(child => child.hasAttribute('begin')) && !/translation|roman|x-bg|rubyText/.test(role(span)) && !spans.some(parent => parent !== span && parent.contains(span) && /translation|roman|x-bg/.test(role(parent)))).map(span => {const time = timing(span); return {time: time.begin, end: Number.isFinite(time.end) ? time.end : undefined, text: visibleText(span)};}).filter(w => w.text);
    result.push({time: t.begin, end: Number.isFinite(t.end) ? t.end : undefined, text: visibleText(p).trim(), words,
      translation: spans.filter(span => /translation/.test(role(span))).map(span => span.textContent).join(' / '), romanization: spans.filter(span => /roman/.test(role(span))).map(span => span.textContent).join(' / '), agent: p.getAttribute('ttm:agent') || undefined});
    for (const bg of spans.filter(span => role(span).includes('x-bg') && !spans.some(ancestor => ancestor !== span && ancestor.contains(span) && role(ancestor).includes('x-bg')))) {
      const time = timing(bg); const bgWords = Array.from(bg.getElementsByTagNameNS('*', 'span')).filter(s => s.hasAttribute('begin')).map(s => ({time: timing(s).begin, end: timing(s).end, text: visibleText(s)}));
      result.push({time: time.begin, end: Number.isFinite(time.end) ? time.end : undefined, text: Array.from(bg.childNodes).map(visibleText).join('').trim(), words: bgWords, background: true, agent: p.getAttribute('ttm:agent') || undefined});
    }
  }
  return finalize(result.filter(l => l.text));
}
export function lyricIndex(lines: Lyric[], time: number): number {let low = 0, high = lines.length - 1, found = -1; while (low <= high) {const mid = (low + high) >>> 1; if (lines[mid].time <= time) {found = mid; low = mid + 1;} else high = mid - 1;} return found;}
export function activeLyrics(lines: Lyric[], position: number): Set<number> {return new Set(lines.flatMap((line, index) => position >= line.time && position < (line.end ?? Infinity) ? [index] : []));}
export function lrcTime(seconds: number): string {const n = Math.max(0, Math.round(seconds * 1000)); return `${String(Math.floor(n / 60000)).padStart(2, '0')}:${String(Math.floor(n / 1000) % 60).padStart(2, '0')}.${String(n % 1000).padStart(3, '0')}`;}
export function serializeLRC(lines: Lyric[]): string {return lines.map(l => `[${lrcTime(l.time)}]${l.words.length ? l.words.map(w => `<${lrcTime(w.time)}>${w.text}`).join('') : l.text}`).join('\n');}
