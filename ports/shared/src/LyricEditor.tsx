import {useState, useSyncExternalStore} from 'react';
import {parseLyrics, serializeLRC, type Lyric} from './lyrics.ts';
import {player} from './player.ts';
import {Modal} from './components.tsx';
import type {Song} from './model.ts';
export function LyricEditor({song, save, close}: {song: Song; save: (text: string) => void; close: () => void}) {
  const playback = useSyncExternalStore(player.subscribe, player.getSnapshot);
  const [text, setText] = useState(() => parseLyrics(song.lyrics || '').map(l => l.text).join('\n') || song.lyrics || '');
  const [versions, setVersions] = useState<Lyric[][]>(() => [parseLyrics(song.lyrics || '')]); const [version, setVersion] = useState(0);
  const [cursor, setCursor] = useState(0); const [word, setWord] = useState(0); const [mode, setMode] = useState('line'); const lines = versions[version];
  function commit(next: Lyric[]) {setVersions(v => [...v.slice(0, version + 1), next]); setVersion(version + 1);}
  function stamp() {
    if (!lines[cursor]) return;
    const next = structuredClone(lines); const line = next[cursor];
    if (mode === 'word') {
      if (!line.words.length) line.words = (line.text.includes(' ') ? line.text.split(/(?<=\s)/) : Array.from(line.text)).map(text => ({time: 0, text}));
      if (!line.words[word]) return; line.words[word].time = playback.position;
      if (word === 0) line.time = playback.position;
      if (word + 1 >= line.words.length) {setCursor(cursor + 1); setWord(0);} else setWord(word + 1);
    } else {line.time = playback.position; setCursor(cursor + 1);}
    commit(next);
  }
  return <Modal title="歌词打轴器" close={close}><p className="muted">编辑逐行或逐词时间。导出增强 LRC；复杂 TTML 角色与样式不会写入此格式。</p>
    <textarea aria-label="歌词文本" rows={5} value={text} onChange={e => setText(e.target.value)}/><button onClick={() => {commit(text.split(/\r?\n/).filter(Boolean).map((text, n) => ({text, time: lines[n]?.time || 0, words: []}))); setCursor(0); setWord(0);}}>应用文本</button>
    <div className="button-row"><select aria-label="打轴模式" value={mode} onChange={e => {setMode(e.target.value); setWord(0);}}><option value="line">逐行</option><option value="word">逐词 / 字</option></select><button disabled={version === 0} onClick={() => setVersion(version - 1)}>撤销</button><button disabled={version >= versions.length - 1} onClick={() => setVersion(version + 1)}>重做</button></div>
    <div className="timing-lines">{lines.map((line, n) => <div className={n === cursor ? 'active' : ''} key={n}><button onClick={() => {setCursor(n); setWord(0);}}>{n + 1}</button><input aria-label={`第 ${n + 1} 行时间`} type="number" min="0" step="0.01" value={line.time} onChange={e => {const next = structuredClone(lines); next[n].time = Math.max(0, Number(e.target.value)); commit(next);}}/><span>{line.text}</span></div>)}</div>
    <div className="timing-toolbar"><button onClick={() => void player.toggle()}>{playback.playing ? '暂停' : '播放'}</button><button disabled={!lines[cursor]} className="primary" onClick={stamp}>标记{mode === 'word' ? `第 ${word + 1} 词` : `第 ${cursor + 1} 行`} · {playback.position.toFixed(2)} s</button></div>
    <button className="primary" disabled={!lines.length} onClick={() => {save(serializeLRC(lines)); close();}}>保存同步歌词</button>
  </Modal>;
}
