import {AIPanel} from './AIPanel.tsx';
import {LibraryControls} from './LibraryControls.tsx';
import {useCallback, useDeferredValue, useEffect, useMemo, useRef, useState, useSyncExternalStore} from 'react';
import {host, invoke, importSongs} from './bridge.ts';
import {albumKey, emptyState, filterSongs, mergeSongs, restoreState, sortSongs, moveItem, type Song, type State, type Source, type Settings} from './model.ts';
import {player} from './player.ts';
import {Icon, IconButton, Modal, SongRow, Cover} from './components.tsx';
import {PlayerBar, NowPlaying} from './PlayerView.tsx';
import {RemoteView} from './RemoteView.tsx';
import {SettingsView} from './SettingsView.tsx';
import {SongActions} from './SongActions.tsx';
const pages = [{id: 'library', title: '音乐库', icon: 'music'}, {id: 'favorites', title: '收藏', icon: 'heart'}, {id: 'playlists', title: '歌单', icon: 'list'}, {id: 'history', title: '最近播放', icon: 'clock'}, {id: 'remote', title: '远程曲库', icon: 'server'}, {id: 'ai', title: 'AI 助手', icon: 'music'}, {id: 'settings', title: '设置', icon: 'settings'}];
export function App() {
  const [state, setState] = useState<State>(emptyState); const [local, setLocal] = useState<Song[]>([]); const [sources, setSources] = useState<Source[]>([]);
  const [loaded, setLoaded] = useState(false); const [page, setPage] = useState('library'); const [query, setQuery] = useState(''); const search = useDeferredValue(query);
  const [group, setGroup] = useState('songs'); const [selectedGroup, setSelectedGroup] = useState<string | null>(null); const [playlistId, setPlaylistId] = useState<string | null>(null);
  const [actionSong, setActionSong] = useState<Song | null>(null); const [expanded, setExpanded] = useState(false); const [newPlaylist, setNewPlaylist] = useState(false);
  const [renamePlaylist, setRenamePlaylist] = useState(false);
  const [busy, setBusy] = useState(false); const [notice, setNotice] = useState(''); const noticeTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const playback = useSyncExternalStore(player.subscribe, player.getSnapshot);
  const notify = useCallback((text: string) => {setNotice(text); clearTimeout(noticeTimer.current); noticeTimer.current = setTimeout(() => setNotice(''), 8000);}, []);
  const songs = useMemo(() => mergeSongs(local, state.remoteSongs).map(song => ({...song, ...state.edits[song.id]})), [local, state.remoteSongs, state.edits]);
  const byId = useMemo(() => new Map(songs.map(s => [s.id, s])), [songs]);
  const current = playback.queue[playback.index]; const currentSong = current ? byId.get(current.id) || current : undefined;
  useEffect(() => {
    // Dismiss after the tap target is chosen, so keyboard reflow cannot swallow button actions.
    const dismiss = (event: MouseEvent) => {
      const active = document.activeElement;
      if ((active instanceof HTMLInputElement || active instanceof HTMLTextAreaElement) && event.target instanceof Element && !event.target.closest('input,textarea,select,[contenteditable="true"]')) active.blur();
    };
    document.addEventListener('click', dismiss, true);
    return () => document.removeEventListener('click', dismiss, true);
  }, []);
  useEffect(() => {
    let cancelled = false;
    void Promise.allSettled([invoke<Song[]>('library.list'), invoke<State | null>('state.load'), invoke<Source[]>('sources.list')]).then(async results => {
      if (cancelled) return;
      const nativeSongs = results[0].status === 'fulfilled' ? results[0].value : [];
      let saved = emptyState(); if (results[1].status === 'fulfilled' && results[1].value) {try {saved = restoreState(results[1].value);} catch(e) {notify(String(e));}}
      saved.settings.sleepMinutes = 0; setLocal(nativeSongs); setState(saved);
      if (results[2].status === 'fulfilled') setSources(results[2].value);
      for (const result of results) if (result.status === 'rejected') notify(String(result.reason));
      const all = new Map(mergeSongs(nativeSongs, saved.remoteSongs).map(s => [s.id, {...s, ...saved.edits[s.id]}]));
      const queue = saved.queue.map(id => all.get(id)).filter((s): s is Song => !!s);
      await player.modes(saved.shuffle, saved.repeat);
      if (queue.length) await player.play(queue, Math.max(0, queue.findIndex(s => s.id === saved.queue[saved.index])), false);
      setLoaded(true);
    }).catch(e => {notify(String(e)); setLoaded(true);});
    return () => {cancelled = true;};
  }, [notify]);
  useEffect(() => host.onEvent(event => {
    if (event.type === 'notice') notify(String(event.message));
    if (event.type === 'playback' && event.counts && event.history) setState(s => {
      const counts = {...s.counts}; for (const [id,n] of Object.entries(event.counts as Record<string,number>)) counts[id] = Math.max(counts[id] || 0,n);
      const seen = new Set<string>(); const history = [...event.history as State['history'],...s.history].sort((a,b)=>b.at-a.at).filter(h=>{const key = `${h.id}:${h.at}`;if(seen.has(key))return false;seen.add(key);return true;}).slice(0,1000);
      return {...s,counts,history};
    });
  }), [notify]);
  useEffect(() => {if (playback.error) notify(playback.error);}, [playback.error, notify]);
  useEffect(() => {
    if (!loaded) return;
    setState(state => ({...state, queue: playback.queue.map(s => s.id), index: playback.index, shuffle: playback.shuffle, repeat: playback.repeat}));
  }, [loaded, playback.queue, playback.index, playback.shuffle, playback.repeat]);
  useEffect(() => {if (!loaded) return; const timer = setTimeout(() => {void invoke('state.save', state).catch(e => notify(String(e)));}, 250); return () => clearTimeout(timer);}, [state, loaded, notify]);
  useEffect(() => {
    const root = document.documentElement; root.dataset.theme = state.settings.theme;
    root.style.setProperty('--accent', state.settings.accent); root.style.setProperty('--lyric-size', `${state.settings.lyricSize}px`);
    root.dataset.translation = String(state.settings.showTranslation); root.dataset.romanization = String(state.settings.showRomanization); root.dataset.backdrop = String(state.settings.backdrop);
  }, [state.settings]);
  useEffect(() => {void player.configure(state.settings).catch(e => notify(String(e)));}, [state.settings, notify]);
  useEffect(() => {void player.setVolume(state.settings.volume).catch(e => notify(String(e)));}, [state.settings.volume, notify]);
  useEffect(() => {void player.sleep(state.settings.sleepMinutes, state.settings.finishTrack).catch(e => notify(String(e)));}, [state.settings.sleepMinutes, state.settings.finishTrack, notify]);
  const listening = useRef({key: '', position: 0, total: 0, counted: false});
  useEffect(() => {
    if (!current || host.platform === 'ios') return;
    const key = `${current.id}:${player.generation}`; let entry = listening.current;
    if (entry.key !== key) entry = listening.current = {key, position: playback.position, total: 0, counted: false};
    const delta = playback.position - entry.position; entry.position = playback.position;
    if (playback.playing && delta > 0 && delta < 3) entry.total += delta;
    if (!entry.counted && entry.total >= Math.max(1, Math.min(30, (playback.duration || 60) / 2))) {
      entry.counted = true; setState(s => ({...s, history: [{id: current.id, at: Date.now()}, ...s.history].slice(0, 1000), counts: {...s.counts, [current.id]: (s.counts[current.id] || 0) + 1}}));
    }
  }, [current?.id, playback.index, playback.position, playback.playing, playback.duration]);
  useEffect(() => {
    const keydown = (event: KeyboardEvent) => {
      if (event.code === 'Space' && !['INPUT', 'TEXTAREA', 'SELECT', 'BUTTON'].includes((event.target as HTMLElement).tagName) && !document.querySelector('dialog[open]')) {event.preventDefault(); void player.toggle();}
    }; window.addEventListener('keydown', keydown); return () => window.removeEventListener('keydown', keydown);
  }, []);
  const favorites = useMemo(() => new Set(state.favorites), [state.favorites]);
  const selectedPlaylist = state.playlists.find(p => p.id === playlistId);
  const visible = useMemo(() => {
    let rows = songs;
    if (page === 'favorites') rows = rows.filter(s => favorites.has(s.id));
    if (page === 'history') rows = [...new Set(state.history.map(h => h.id))].map(id => byId.get(id)).filter((s): s is Song => !!s);
    if (page === 'playlists' && selectedPlaylist) rows = selectedPlaylist.songIds.map(id => byId.get(id)).filter((s): s is Song => !!s);
    if (selectedGroup) rows = rows.filter(s => (group === 'albums' ? albumKey(s) : group === 'artists' ? s.artist : s.folder || '导入音乐') === selectedGroup);
    return filterSongs(selectedPlaylist || page === 'history' ? rows : sortSongs(rows, state.settings, state.counts), search);
  }, [songs, page, favorites, state.history, byId, selectedPlaylist, selectedGroup, group, search, state.settings, state.counts]);
  const groups = useMemo(() => {
    const result = new Map<string, Song[]>(); for (const song of visible) {const key = group === 'albums' ? albumKey(song) : group === 'artists' ? song.artist : song.folder || '导入音乐'; const rows = result.get(key) || []; rows.push(song); result.set(key, rows);} return [...result];
  }, [visible, group]);
  function setSettings(patch: Partial<Settings>) {setState(s => ({...s, settings: {...s.settings, ...patch}}));}
  async function importLocal(folder: boolean) {setBusy(true); try {const songs = await importSongs(folder); setLocal(current => mergeSongs(current, songs)); if (songs.length) notify(`已导入 ${songs.length} 首歌曲`);} catch(e) {notify(String(e));} finally {setBusy(false);}}
  function favorite(id: string) {setState(s => ({...s, favorites: s.favorites.includes(id) ? s.favorites.filter(v => v !== id) : [...s.favorites, id]}));}
  async function remove(song: Song) {
    if (!song.sourceId || song.offline) {await invoke('library.remove', {id: song.id}); setLocal(s => s.filter(item => item.id !== song.id));}
    setState(s => ({...s, remoteSongs: s.remoteSongs.filter(item => item.id !== song.id), favorites: s.favorites.filter(id => id !== song.id), playlists: s.playlists.map(p => ({...p, songIds: p.songIds.filter(id => id !== song.id)}))}));
    if (playback.queue.some(s => s.id === song.id)) await player.play(playback.queue.filter(s => s.id !== song.id), 0, false);
    notify('已从曲库移除，原始音频文件保留');
  }
  async function importPlaylist() {
    try {
      const text = await invoke<string>('text.import'); if (!text) return; let entries: Partial<Song>[]; let name = '导入歌单';
      if (text.trim().startsWith('{')) {const data = JSON.parse(text); if (data.schema !== 'halcyon-playlist-v1' || !Array.isArray(data.songs)) throw new Error('不支持的歌单格式'); entries = data.songs; name = data.name || name;}
      else entries = text.split(/\r?\n/).filter(row => row && !row.startsWith('#')).map(row => ({fileName: row.split(/[\\/]/).pop()}));
      const ids = entries.map(entry => songs.find(s => s.id === entry.id || (entry.fileName && s.fileName === entry.fileName) || (entry.title && s.title === entry.title && s.artist === entry.artist))?.id).filter((id): id is string => !!id);
      setState(s => ({...s, playlists: [...s.playlists, {id: crypto.randomUUID(), name, songIds: [...new Set(ids)]}]})); notify(`已导入歌单，匹配 ${ids.length}/${entries.length} 首歌曲`);
    } catch(e) {notify(String(e));}
  }
  const listPage = ['library', 'favorites', 'history'].includes(page) || (page === 'playlists' && !!selectedPlaylist);
  return <div className="app-shell">
    <nav className="sidebar"><div className="brand"><img src="./icon.svg" alt=""/><span>Halcyon</span></div><div className="navigation">{pages.map(item => <button className={page === item.id ? 'selected' : ''} onClick={() => {setPage(item.id); setSelectedGroup(null); setPlaylistId(null);}} key={item.id}><Icon name={item.icon}/><span>{item.title}</span></button>)}</div><small className="sidebar-note">音乐，就在身边</small></nav>
    <main><header><h1>{selectedPlaylist?.name || pages.find(p => p.id === page)?.title}</h1>{listPage ? <div className="toolbar"><label className="search"><Icon name="search"/><input placeholder="搜索歌曲、艺术家、专辑" value={query} onChange={e => setQuery(e.target.value)}/></label><div className="button-row"><button className="primary" disabled={busy || !loaded} onClick={() => void importLocal(false)}><Icon name="plus"/>{busy ? '导入中…' : '导入音乐'}</button><button disabled={busy || !loaded || host.platform === 'browser'} onClick={() => void importLocal(true)}><Icon name="folder"/>导入文件夹</button></div></div> : null}</header>
    {page === 'library' ? <div className="library-tabs">{[{id: 'songs', label: '歌曲'}, {id: 'albums', label: '专辑'}, {id: 'artists', label: '艺术家'}, {id: 'folders', label: '文件夹'}].map(tab => <button className={group === tab.id ? 'active' : ''} key={tab.id} onClick={() => {setGroup(tab.id); setSelectedGroup(null);}}>{tab.label}</button>)}</div> : null}
    {selectedGroup ? <button className="link back-link" onClick={() => setSelectedGroup(null)}><Icon name="back"/>返回分类</button> : null}
    {page === 'playlists' && !selectedPlaylist ? <><div className="button-row"><button className="primary" onClick={() => setNewPlaylist(true)}><Icon name="plus"/>新建歌单</button><button onClick={() => void importPlaylist()}>导入 JSON / M3U 歌单</button></div><div className="group-list">{state.playlists.map(p => <button key={p.id} onClick={() => setPlaylistId(p.id)}><Icon name="list"/><span><strong>{p.name}</strong><small>{p.songIds.length} 首歌曲</small></span></button>)}</div></> : null}
    {selectedPlaylist ? <div className="button-row playlist-controls"><button onClick={async () => {try {await invoke('text.export', {name: 'Halcyon-playlist.json', text: JSON.stringify({schema: 'halcyon-playlist-v1', name: selectedPlaylist.name, songs: selectedPlaylist.songIds.map(id => byId.get(id)).filter(Boolean)}, null, 2)});} catch(e) {notify(String(e));}}}>导出歌单</button><button onClick={() => setRenamePlaylist(true)}>重命名</button><button onClick={async () => {try {const allSongs = selectedPlaylist.songIds.map(id => byId.get(id)).filter((s): s is Song => !!s); await invoke('text.export', {name: 'Halcyon-playlist.m3u', text: '#EXTM3U\n' + allSongs.map(song => `#EXTINF:${Math.round(song.duration)},${song.artist} - ${song.title}\n${song.fileName || song.title + '.' + (song.format || 'mp3')}`).join('\n')});} catch(e) {notify(String(e));}}}>导出 M3U</button><button onClick={() => {setState(s => ({...s, playlists: s.playlists.filter(p => p.id !== playlistId)})); setPlaylistId(null);}}>删除歌单</button><button onClick={() => setPlaylistId(null)}>返回歌单</button></div> : null}
    {listPage && page === 'library' && group !== 'songs' && !selectedGroup ? <div className="group-list">{groups.map(([key, rows]) => <button onClick={() => setSelectedGroup(key)} key={key}><Cover song={rows[0]}/><span><strong>{group === 'albums' ? rows[0].album : key}</strong><small>{group === 'albums' ? `${rows[0].albumArtist || rows[0].artist} · ` : ''}{rows.length} 首歌曲</small></span></button>)}</div> : listPage ? <>
      {!selectedPlaylist && page !== 'history' ? <LibraryControls settings={state.settings} sources={sources} songs={songs} setSettings={setSettings}/> : null}
      <div className="button-row list-actions"><button disabled={!visible.length} onClick={() => void player.play(visible)}>播放全部</button><button disabled={!visible.length} onClick={() => {void player.modes(true, playback.repeat).then(() => player.play(visible, Math.floor(Math.random() * visible.length)));}}>随机播放全部</button><span className="muted">{visible.length} 首</span></div>
      <div className="list-heading"><span>#</span><span>标题</span><span>艺术家</span><span>专辑</span><Icon name="clock" size={15}/></div>
      <div className="song-list">{visible.map((song, index) => <div key={song.id} className="managed-song"><SongRow song={song} index={index} current={current?.id === song.id} favorite={favorites.has(song.id)} onPlay={() => void player.play(visible, index)} onFavorite={() => favorite(song.id)} onMore={() => setActionSong(song)}/>{selectedPlaylist ? <div className="playlist-row-actions"><button aria-label={`歌单上移 ${song.title}`} disabled={query.length > 0 || index === 0} onClick={() => setState(s => ({...s, playlists:s.playlists.map(p => p.id === playlistId ? {...p, songIds:moveItem(p.songIds,index,index-1)} : p)}))}>↑</button><button aria-label={`歌单下移 ${song.title}`} disabled={query.length > 0 || index === visible.length - 1} onClick={() => setState(s => ({...s, playlists:s.playlists.map(p => p.id === playlistId ? {...p, songIds:moveItem(p.songIds,index,index+1)} : p)}))}>↓</button><button aria-label={`从歌单移除 ${song.title}`} onClick={() => setState(s => ({...s, playlists:s.playlists.map(p => p.id === playlistId ? {...p,songIds:p.songIds.filter(id => id !== song.id)} : p)}))}>移除</button></div> : page === 'history' ? <button className="link" aria-label={`删除播放记录 ${song.title}`} onClick={() => {setState(s => ({...s, history:s.history.filter(h => h.id !== song.id)}));if(host.platform === 'ios')void invoke('history.remove',{id:song.id});}}>删除记录</button> : null}</div>)}</div>
      {!visible.length ? <div className="empty-state"><Icon name="music" size={48}/><h2>{!loaded ? '正在加载音乐库…' : query ? '没有找到匹配的歌曲' : '这里还没有音乐'}</h2><p>{query ? '试试歌曲名、艺术家或专辑。' : page === 'library' ? '导入音频文件，或连接你的远程音乐库。' : '收藏或加入歌单后，音乐会显示在这里。'}</p></div> : null}
      {selectedPlaylist ? <p className="muted">可在歌曲的更多菜单中加入此歌单。</p> : null}
    </> : null}
    {page === 'ai' ? <AIPanel songs={songs} sources={sources} setSources={setSources} current={currentSong} notify={notify} onPlaylist={ids => setState(s => ({...s,playlists:[...s.playlists,{id:crypto.randomUUID(),name:'AI 推荐歌单',songIds:ids}]}))}/> : null}
    {page === 'remote' ? <RemoteView sources={sources} setSources={setSources} notify={notify} onSongs={(incoming, sourceId, replace) => setState(s => ({...s, remoteSongs: mergeSongs(replace ? s.remoteSongs.filter(song => song.sourceId !== sourceId) : s.remoteSongs, incoming)}))}/> : null}
    {page === 'settings' ? <SettingsView state={state} songs={songs} setSettings={setSettings} notify={notify} restore={text => {
      try {
        const restored = restoreState(JSON.parse(text)); restored.settings.sleepMinutes = 0;
        const available = new Map(mergeSongs(local, restored.remoteSongs).map(s => [s.id, {...s, ...restored.edits[s.id]}]));
        const queue = restored.queue.map(id => available.get(id)).filter((s): s is Song => !!s);
        if(host.platform === 'ios')void invoke('history.restore',{counts:restored.counts,history:restored.history}).catch(e=>notify(String(e)));
        setState(restored);
        void player.modes(restored.shuffle, restored.repeat).then(() => player.play(queue, Math.max(0, queue.findIndex(s => s.id === restored.queue[restored.index])), false)).catch(e => notify(String(e)));
        notify('应用数据已恢复');
      } catch(e) {notify(String(e));}
    }}/>: null}
    </main>
    <NowPlaying song={currentSong} offset={state.settings.lyricOffset} onMore={setActionSong}/>
    <PlayerBar onExpand={() => setExpanded(true)}/>
    {notice ? <div className="toast" role="status"><span>{notice}</span><IconButton name="close" label="关闭提示" onClick={() => setNotice('')}/></div> : null}
    {actionSong ? <SongActions song={actionSong} onLibrary={incoming => setLocal(current => mergeSongs(current,incoming))} state={state} update={setState} close={() => setActionSong(null)} remove={remove} notify={notify}/> : null}
    {expanded ? <Modal title="播放与队列" close={() => setExpanded(false)}><NowPlaying song={currentSong} full offset={state.settings.lyricOffset} onMore={song => {setExpanded(false); setActionSong(song);}}/></Modal> : null}
    {renamePlaylist && selectedPlaylist ? <Modal title="重命名歌单" close={() => setRenamePlaylist(false)}><form onSubmit={e => {e.preventDefault(); const name = String(new FormData(e.currentTarget).get('name') || '').trim(); if (name) setState(s => ({...s,playlists:s.playlists.map(p => p.id === playlistId ? {...p,name} : p)})); setRenamePlaylist(false);}}><label>歌单名称<input name="name" defaultValue={selectedPlaylist.name} required maxLength={200}/></label><button type="submit">保存名称</button></form></Modal> : null}
    {newPlaylist ? <Modal title="新建歌单" close={() => setNewPlaylist(false)}><form onSubmit={e => {e.preventDefault(); const name = String(new FormData(e.currentTarget).get('name') || '').trim(); if (!name) return; setState(s => ({...s, playlists: [...s.playlists, {id: crypto.randomUUID(), name, songIds: []}]})); setNewPlaylist(false);}}><label>歌单名称<input name="name" required maxLength={200} autoFocus/></label><button className="primary" type="submit">创建</button></form></Modal> : null}
  </div>;
}
