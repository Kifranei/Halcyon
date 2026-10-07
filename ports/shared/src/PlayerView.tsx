import {QueueView} from './QueueView.tsx';
import {useEffect, useMemo, useRef, useSyncExternalStore} from 'react';
import {player} from './player.ts';
import {parseLyrics, lyricIndex, activeLyrics} from './lyrics.ts';
import {time, type Song} from './model.ts';
import {Cover, IconButton} from './components.tsx';
export function Lyrics({song, position, offset}: {song?: Song; position: number; offset: number}) {
  const lines = useMemo(() => {try {return parseLyrics(song?.lyrics || '');} catch {return [];}}, [song?.lyrics]);
  const current = lyricIndex(lines, position - offset); const activeLines = activeLyrics(lines, position - offset); const active = useRef<HTMLButtonElement>(null);
  useEffect(() => {const item = active.current; const container = item?.parentElement; if (item && container) container.scrollTo({top: item.offsetTop - container.offsetTop - container.clientHeight / 2 + item.clientHeight / 2, behavior: 'smooth'});}, [current]);
  if (!lines.length) return <p className="empty-lyrics">{song?.lyrics || '暂无同步歌词，可在歌曲菜单导入 LRC、ELRC 或 TTML。'}</p>;
  return <div className="lyrics-scroll">{lines.map((line, index) => <button ref={current === index ? active : undefined} className={`lyric ${activeLines.has(index) ? 'active' : ''} ${line.background ? 'background-vocal' : ''} ${line.agent ? 'agent-' + line.agent : ''}`} key={`${line.time}-${index}`} onClick={() => void player.seek(line.time + offset)}>
    {activeLines.has(index) && line.words.length ? line.words.map((word, n) => <span className="timed-word" style={{backgroundImage: `linear-gradient(to right,var(--accent) ${Math.min(100, Math.max(0, (position - offset - word.time) / Math.max(.1, (word.end || word.time + .5) - word.time) * 100))}%,var(--muted) 0)`}} key={n} onClick={event => {event.stopPropagation(); void player.seek(word.time + offset);}}>{word.text}</span>) : line.text}{line.translation ? <small className="lyric-translation">{line.translation}</small> : null}{line.romanization ? <small className="lyric-romanization">{line.romanization}</small> : null}
  </button>)}</div>;
}
export function PlayerBar({onExpand}: {onExpand: () => void}) {
  const state = useSyncExternalStore(player.subscribe, player.getSnapshot); const song = state.queue[state.index];
  return <footer className="player-bar">
    <button className="now-song" onClick={onExpand} aria-label="打开播放页"><Cover song={song}/><span><strong>{song?.title || '还没有正在播放的歌曲'}</strong><small>{song?.artist || '从音乐库选择一首歌曲'}</small></span></button>
    <div className="transport"><div className="transport-buttons">
      <IconButton name="shuffle" label="随机播放" active={state.shuffle} onClick={() => void player.modes(!state.shuffle, state.repeat)}/>
      <IconButton name="previous" label="上一首" disabled={!song} onClick={() => void player.skip(-1)}/>
      <button className="play-button" aria-label={state.playing ? '暂停' : '播放'} disabled={!song} onClick={() => void player.toggle()}><span>{state.playing ? 'Ⅱ' : '▶'}</span></button>
      <IconButton name="next" label="下一首" disabled={!song} onClick={() => void player.skip(1)}/>
      <div className="repeat-wrap"><IconButton name="repeat" label={`循环：${({off: '关闭', all: '列表', one: '单曲'})[state.repeat]}`} active={state.repeat !== 'off'} onClick={() => void player.modes(state.shuffle, state.repeat === 'off' ? 'all' : state.repeat === 'all' ? 'one' : 'off')}/>{state.repeat === 'one' ? <b>1</b> : null}</div>
    </div><div className="progress"><span>{time(state.position)}</span><input aria-label="播放进度" type="range" min="0" max={state.duration || 1} step="0.1" value={Math.min(state.position, state.duration || 1)} disabled={!state.duration} onChange={e => void player.seek(Number(e.target.value))}/><span>{time(state.duration)}</span></div></div>
    <button className="mobile-play icon-button" aria-label={state.playing ? '暂停' : '播放'} disabled={!song} onClick={() => void player.toggle()}>{state.playing ? 'Ⅱ' : '▶'}</button>
    <IconButton name="list" label="播放队列" onClick={onExpand}/>
  </footer>;
}
export function NowPlaying({song, offset, onClose, onMore, full = false}: {song?: Song; offset: number; onClose?: () => void; onMore: (song: Song) => void; full?: boolean}) {
  const state = useSyncExternalStore(player.subscribe, player.getSnapshot);
  return <aside className={`now-playing ${onClose ? 'expanded' : ''}`}>
    {onClose ? <div className="dialog-head"><h2>正在播放</h2><IconButton name="close" label="关闭播放页" onClick={onClose}/></div> : null}
    <Cover song={song} large/>
    <div className="now-title"><div><h2 aria-label={`正在播放 ${song?.title || 'Halcyon'}`}>{song?.title || 'Halcyon'}</h2><p>{song?.artist || '音乐，就在身边'}</p></div>{song ? <IconButton name="more" label="当前歌曲更多操作" onClick={() => onMore(song)}/> : null}</div>
    {full ? <div className="full-controls"><div className="progress"><span>{time(state.position)}</span><input aria-label="播放页进度" type="range" min="0" max={state.duration || 1} step="0.1" value={Math.min(state.position, state.duration || 1)} disabled={!state.duration} onChange={e => void player.seek(Number(e.target.value))}/><span>{time(state.duration)}</span></div><div className="transport-buttons"><IconButton name="shuffle" label="播放页随机" active={state.shuffle} onClick={() => void player.modes(!state.shuffle, state.repeat)}/><IconButton name="previous" label="播放页上一首" onClick={() => void player.skip(-1)}/><IconButton name={state.playing ? 'pause' : 'play'} label={state.playing ? '播放页暂停' : '播放页播放'} onClick={() => void player.toggle()}/><IconButton name="next" label="播放页下一首" onClick={() => void player.skip(1)}/><IconButton name="repeat" label={`播放页循环 ${state.repeat}`} active={state.repeat !== 'off'} onClick={() => void player.modes(state.shuffle, state.repeat === 'off' ? 'all' : state.repeat === 'all' ? 'one' : 'off')}/></div></div> : null}
    <Lyrics song={song} position={state.position} offset={offset}/>
    <QueueView/>
  </aside>;
}
