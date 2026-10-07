export type Provider = 'webdav' | 'subsonic' | 'emby' | 'ai';
export interface Song {
  id: string; title: string; artist: string; album: string; albumArtist?: string;
  duration: number; fileName?: string; format?: string; artwork?: string; lyrics?: string;
  sourceId?: string; remoteId?: string; remotePath?: string; genre?: string; year?: number; folder?: string;
  coverId?: string; offline?: boolean; bitrate?: number; sampleRate?: number; bitDepth?: number; size?: number; replayGain?: number;
}
export interface Source {
  id: string; name: string; type: Provider; url: string; username?: string; userId?: string; model?: string; protocol?: string;
}
export interface Playlist { id: string; name: string; songIds: string[] }
export type Repeat = 'off' | 'all' | 'one';
export interface Settings { theme: 'system' | 'light' | 'dark'; volume: number; lyricOffset: number; sleepMinutes: number; rate: number; preservePitch: boolean; finishTrack: boolean; lyricSize: number; showTranslation: boolean; showRomanization: boolean; accent: string; backdrop: boolean; eq: number[]; eqEnabled: boolean; preamp: number; replayGain: boolean; sort: string; descending: boolean; sourceFilter: string; formatFilter: string }
export interface State {
  version: 1; favorites: string[]; playlists: Playlist[];
  edits: Record<string, Partial<Pick<Song, 'title' | 'artist' | 'album' | 'lyrics' | 'artwork'>>>;
  history: { id: string; at: number }[]; counts: Record<string, number>;
  settings: Settings; queue: string[]; index: number; shuffle: boolean; repeat: Repeat; remoteSongs: Song[];
}
export const emptyState = (): State => ({version: 1, favorites: [], playlists: [], edits: {}, history: [], counts: {},
  settings: {theme: 'system', volume: 1, lyricOffset: 0, sleepMinutes: 0, rate: 1, preservePitch: true, finishTrack: false, lyricSize: 22, showTranslation: true, showRomanization: true, accent: '#8065c9', backdrop: false, eq: Array(10).fill(0), eqEnabled: false, preamp: 0, replayGain: false, sort: 'default', descending: false, sourceFilter: 'all', formatFilter: 'all'}, queue: [], index: 0, shuffle: false, repeat: 'off', remoteSongs: []});
export const audioExtensions = new Set(['mp3', 'm4a', 'aac', 'alac', 'flac', 'wav', 'aiff', 'aif', 'ogg', 'opus', 'wma']);
export function filenameMetadata(name: string): Pick<Song, 'title' | 'artist' | 'album'> {
  const stem = name.replace(/\.[^.]+$/, ''); const split = stem.indexOf(' - ');
  return {title: split < 0 ? stem : stem.slice(split + 3), artist: split < 0 ? '未知艺术家' : stem.slice(0, split), album: '未知专辑'};
}
export function mergeSongs(current: Song[], incoming: Song[]): Song[] {
  const songs = new Map(current.map(song => [song.id, song]));
  incoming.forEach(song => {const old = songs.get(song.id); songs.set(song.id, old?.offline ? {...song, ...old} : song);}); return [...songs.values()];
}
export function filterSongs(songs: Song[], query: string): Song[] {
  const parts = query.toLocaleLowerCase().trim().split(/\s+/).filter(Boolean);
  return songs.filter(song => parts.every(part => [song.title, song.artist, song.album, song.genre, song.fileName].join(' ').toLocaleLowerCase().includes(part)));
}
export function albumKey(song: Song): string { return JSON.stringify([song.album, song.albumArtist || song.artist]); }
export function nextIndex(length: number, index: number, direction: number, repeat: Repeat, shuffle: boolean, random = Math.random): number {
  if (!length) return -1;
  // Repeat-one applies to automatic completion, not an explicit skip.
  if (shuffle && length > 1) return (index + 1 + Math.floor(random() * (length - 1))) % length;
  const next = index + direction;
  if (next >= 0 && next < length) return next;
  return repeat === 'all' ? (next + length) % length : -1;
}
export function time(seconds: number): string {
  const value = Number.isFinite(seconds) ? Math.max(0, Math.floor(seconds)) : 0;
  return `${Math.floor(value / 60)}:${String(value % 60).padStart(2, '0')}`;
}
const strings = (x: unknown): string[] => Array.isArray(x) ? x.filter((v): v is string => typeof v === 'string').slice(0, 100000) : [];
/** Restore only our versioned app schema. Audio files and credentials never belong in this backup. */
export function restoreState(raw: unknown): State {
  if (!raw || typeof raw !== 'object' || (raw as State).version !== 1) throw new Error('不支持的备份格式');
  const data = raw as State; const out = emptyState();
  out.favorites = strings(data.favorites); out.queue = strings(data.queue);
  out.remoteSongs = Array.isArray(data.remoteSongs) ? data.remoteSongs.filter(s => s && typeof s.id === 'string' && typeof s.sourceId === 'string').map(s => ({
    id: s.id, sourceId: s.sourceId, remoteId: String(s.remoteId || ''), remotePath: String(s.remotePath || ''),
    title: String(s.title || ''), artist: String(s.artist || ''), album: String(s.album || ''), duration: Number.isFinite(s.duration) ? s.duration : 0,
    albumArtist: String(s.albumArtist || ''), format: String(s.format || ''), genre: String(s.genre || ''), fileName: String(s.fileName || ''), coverId: String(s.coverId || ''), folder: String(s.folder || ''), bitrate: Number(s.bitrate) || 0, sampleRate: Number(s.sampleRate) || 0, bitDepth: Number(s.bitDepth) || 0, size: Number.isFinite(s.size) ? s.size : 0, year: Number.isFinite(s.year) ? s.year : undefined, replayGain: Number.isFinite(s.replayGain) ? s.replayGain : undefined
  })).slice(0, 100000) : [];
  out.index = Math.min(Math.max(0, Number.isInteger(data.index) ? data.index : 0), Math.max(0, out.queue.length - 1));
  out.shuffle = data.shuffle === true; out.repeat = ['off', 'all', 'one'].includes(data.repeat) ? data.repeat : 'off';
  out.playlists = Array.isArray(data.playlists) ? data.playlists.filter(p => p && typeof p.id === 'string' && typeof p.name === 'string').map(p => ({id: p.id, name: p.name.slice(0, 200), songIds: strings(p.songIds)})) : [];
  out.history = Array.isArray(data.history) ? data.history.filter(h => typeof h?.id === 'string' && Number.isFinite(h.at)).slice(0, 1000) : [];
  for (const [key, count] of Object.entries(data.counts || {})) if (Number.isFinite(count) && count >= 0) out.counts[key] = count;
  for (const [key, edit] of Object.entries(data.edits || {})) {
    if (!edit || typeof edit !== 'object' || ['__proto__', 'constructor', 'prototype'].includes(key)) continue;
    out.edits[key] = {};
    for (const field of ['title', 'artist', 'album', 'lyrics', 'artwork'] as const) if (typeof edit[field] === 'string') out.edits[key][field] = edit[field].slice(0, field === 'artwork' ? 1500000 : 500000);
  }
  const s = data.settings;
  if (s) {
    if (['system', 'light', 'dark'].includes(s.theme)) out.settings.theme = s.theme;
    if (Number.isFinite(s.volume)) out.settings.volume = Math.min(1, Math.max(0, s.volume));
    if (Number.isFinite(s.lyricOffset)) out.settings.lyricOffset = Math.min(600, Math.max(-600, s.lyricOffset));
    if (Number.isFinite(s.sleepMinutes)) out.settings.sleepMinutes = Math.min(1440, Math.max(-1, s.sleepMinutes));
    for (const [key, min, max] of [['rate', .5, 3], ['lyricSize', 14, 44], ['preamp', -24, 6]] as const) if (Number.isFinite(s[key])) out.settings[key] = Math.max(min, Math.min(max, s[key]));
    for (const key of ['preservePitch', 'finishTrack', 'showTranslation', 'showRomanization', 'backdrop', 'eqEnabled', 'replayGain', 'descending'] as const) if (typeof s[key] === 'boolean') out.settings[key] = s[key];
    if (/^#[0-9a-f]{6}$/i.test(s.accent)) out.settings.accent = s.accent;
    if (Array.isArray(s.eq) && s.eq.length === 10) out.settings.eq = s.eq.map(n => Number.isFinite(n) ? Math.max(-12, Math.min(12, n)) : 0);
    if (['default', 'title', 'artist', 'album', 'duration', 'year', 'count'].includes(s.sort)) out.settings.sort = s.sort;
    if (typeof s.sourceFilter === 'string') out.settings.sourceFilter = s.sourceFilter;
    if (typeof s.formatFilter === 'string') out.settings.formatFilter = s.formatFilter;
  }
  return out;
}

export const eqFrequencies = [31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000];
export function moveItem<T>(items: T[], from: number, to: number): T[] {
  if (from < 0 || from >= items.length || to < 0 || to >= items.length) return items;
  const result = [...items]; const [entry] = result.splice(from, 1); result.splice(to, 0, entry); return result;
}
export function movedIndex(current: number, from: number, to: number): number {
  if (current === from) return to;
  if (from < current && to >= current) return current - 1;
  if (from > current && to <= current) return current + 1;
  return current;
}
export function sortSongs(songs: Song[], settings: Settings, counts: Record<string, number>): Song[] {
  const rows = songs.filter(s => (settings.sourceFilter === 'all' || (settings.sourceFilter === 'local' ? !s.sourceId : settings.sourceFilter === 'offline' ? s.offline : s.sourceId === settings.sourceFilter)) && (settings.formatFilter === 'all' || s.format?.toLowerCase() === settings.formatFilter));
  if (settings.sort === 'default') return settings.descending ? [...rows].reverse() : rows;
  return [...rows].sort((a, b) => {
    const key = settings.sort; let n: number;
    if (key === 'count') n = (counts[a.id] || 0) - (counts[b.id] || 0);
    else if (key === 'duration' || key === 'year') n = (a[key] || 0) - (b[key] || 0);
    else n = String(a[key as keyof Song] || '').localeCompare(String(b[key as keyof Song] || ''), 'zh-CN', {numeric: true});
    return settings.descending ? -n : n;
  });
}
