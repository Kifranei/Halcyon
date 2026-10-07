import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { parseFile } from 'music-metadata';
const extensions = new Set(['mp3', 'm4a', 'aac', 'alac', 'flac', 'wav', 'aiff', 'aif', 'ogg', 'opus', 'wma']);
export async function readJSON(file, fallback) {try {return JSON.parse(await fs.readFile(file, 'utf8'));} catch (e) {if (e.code === 'ENOENT') return fallback; throw e;}}
export async function atomicJSON(file, value) {
  await fs.mkdir(path.dirname(file), {recursive: true});
  await fs.writeFile(`${file}.tmp`, JSON.stringify(value), {mode: 0o600}); await fs.rename(`${file}.tmp`, file);
}
export class Library {
  constructor(directory) {this.file = path.join(directory, 'library.json'); this.entries = new Map(); this.writes = Promise.resolve();}
  async load() {this.entries = new Map((await readJSON(this.file, [])).map(row => [row.song.id, row])); return this.list();}
  list() {return [...this.entries.values()].map(row => row.song);}
  async persist() {const rows = [...this.entries.values()]; const task = this.writes.then(() => atomicJSON(this.file, rows)); this.writes = task.catch(() => {}); await task;}
  async scan(selected) {
    const files = []; const errors = [];
    async function walk(candidate) {
      if (files.length >= 100000) throw new Error('单次导入超过 100000 首，请分批选择');
      const stat = await fs.lstat(candidate);
      if (stat.isSymbolicLink()) return;
      if (stat.isDirectory()) {
        for (const entry of await fs.readdir(candidate, {withFileTypes: true})) {if (!entry.name.startsWith('.')) await walk(path.join(candidate, entry.name));}
      } else if (extensions.has(path.extname(candidate).slice(1).toLowerCase())) files.push(candidate);
    }
    for (const selectedPath of selected) {try {await walk(selectedPath);} catch (error) {errors.push(`${path.basename(selectedPath)}: ${error.message}`);}}
    // Bounded concurrency avoids opening a whole library's files simultaneously.
    const incoming = []; let cursor = 0;
    await Promise.all(Array.from({length: Math.min(4, files.length)}, async () => {
      while (cursor < files.length) {
        const selectedIndex = cursor++; const file = await fs.realpath(files[selectedIndex]); const id = createHash('sha256').update(file).digest('hex');
        const stem = path.basename(file, path.extname(file)); const parts = stem.split(' - ');
        const song = {id, title: parts.length > 1 ? parts.slice(1).join(' - ') : stem, artist: parts.length > 1 ? parts[0] : '未知艺术家', album: '未知专辑', duration: 0, fileName: path.basename(file), folder: path.basename(path.dirname(file)), format: path.extname(file).slice(1)};
        try {
          const {common, format} = await parseFile(file, {duration: true});
          Object.assign(song, {title: common.title || song.title, artist: common.artist || song.artist, album: common.album || song.album,
            albumArtist: common.albumartist, duration: format.duration || 0, bitrate: format.bitrate, sampleRate: format.sampleRate, bitDepth: format.bitsPerSample, size: (await fs.stat(file)).size, replayGain: common.replaygain_track_gain?.dB, genre: common.genre?.join(' / '), year: common.year});
          const cover = common.picture?.[0]; if (cover && cover.data.length < 4000000) song.artwork = `data:${cover.format};base64,${Buffer.from(cover.data).toString('base64')}`;
          const lyrics = common.lyrics?.[0]; if (lyrics) song.lyrics = typeof lyrics === 'string' ? lyrics : lyrics.text || '';
        } catch (error) {errors.push(`${song.fileName}: 标签读取失败，保留文件名信息 (${error.message})`);}
        for (const suffix of ['.ttml', '.elrc', '.lrc']) {
          try {const sidecar = file.slice(0, -path.extname(file).length) + suffix; const stat = await fs.stat(sidecar); if (stat.size < 2000000) {song.lyrics = await fs.readFile(sidecar, 'utf8'); break;}} catch {}
        }
        incoming[selectedIndex] = {song, path: file};
      }
    }));
    for (const entry of incoming) this.entries.set(entry.song.id, entry);
    await this.persist(); return {songs: incoming.map(entry => entry.song), errors};
  }
  async remove(id) {this.entries.delete(id); await this.persist();}
  path(id) {const row = this.entries.get(id); if (!row) throw new Error('歌曲不在已导入曲库中'); return row.path;}
}
