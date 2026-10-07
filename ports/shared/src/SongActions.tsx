import {useState} from 'react';
import {type Song, type State} from './model.ts';
import {host, invoke} from './bridge.ts';
import {player} from './player.ts';
import {Modal} from './components.tsx';
import {LyricEditor} from './LyricEditor.tsx';
import {songExtras} from './remote.ts';
import {parseLyrics} from './lyrics.ts';
const escapeXML = (s: string) => s.replace(/[<>&"']/g, c => ({'<':'&lt;','>':'&gt;','&':'&amp;','"':'&quot;',"'":'&apos;'})[c]!);
export function SongActions({song, state, update, close, remove, notify, onLibrary}: {song: Song; state: State; update: (fn: (state: State) => State) => void; close: () => void; remove: (song: Song) => Promise<void>; notify: (message: string) => void; onLibrary: (songs: Song[]) => void}) {
  const [edit, setEdit] = useState(false); const [timing, setTiming] = useState(false); const [busy, setBusy] = useState(false); const [video,setVideo] = useState('');
  function patch(values: Partial<Pick<Song,'title'|'artist'|'album'|'lyrics'|'artwork'>>) {update(state => ({...state, edits: {...state.edits, [song.id]: {...state.edits[song.id], ...values}}})); void player.refresh({...song,...values}).catch(e => notify(String(e)));}
  async function lyrics() {try {const text = await invoke<string>('text.import'); if (text) {patch({lyrics:text}); close();}} catch(e) {notify(String(e));}}
  if (video) return <Modal title="MV 播放" close={close}><video className="mv-video" src={video} controls autoPlay playsInline/></Modal>;
  if (timing) return <LyricEditor song={song} close={close} save={text => patch({lyrics:text})}/>;
  return <Modal title={song.title} close={close}>{edit ? <form onSubmit={event => {
    event.preventDefault(); const values = Object.fromEntries(new FormData(event.currentTarget)); patch({title:String(values.title),artist:String(values.artist),album:String(values.album)}); close();
  }}><label>歌曲名<input name="title" defaultValue={song.title} required/></label><label>艺术家<input name="artist" defaultValue={song.artist}/></label><label>专辑<input name="album" defaultValue={song.album}/></label><p className="muted">修改应用内显示信息，音频原文件标签保持原样。</p><button className="primary" type="submit">保存</button></form> : <div className="action-list">
    <p className="muted">{song.artist} · {song.album} · {song.format || '音频'}{song.offline ? ' · 已离线' : ''}<br/>{song.sampleRate ? `${song.sampleRate/1000} kHz · ` : ''}{song.bitDepth ? `${song.bitDepth} bit · ` : ''}{song.bitrate ? `${Math.round(song.bitrate/1000)} kbps · ` : ''}{song.size ? `${(song.size/1048576).toFixed(1)} MB` : ''}</p>
    <button onClick={() => {void player.enqueue(song,true); close();}}>下一首播放</button><button onClick={() => {void player.enqueue(song); close();}}>加入播放队列</button>
    {song.sourceId && !song.offline && host.platform !== 'browser' ? <button disabled={busy} onClick={async () => {setBusy(true); try {const cached = await invoke<Song>('library.download',song); onLibrary([cached]); await player.refresh(cached); notify('已下载，可断网播放');} catch(e) {notify(String(e));} finally {setBusy(false);}}}>{busy ? '正在下载…' : '下载到离线曲库'}</button> : null}
    {busy ? <button onClick={() => void invoke('download.cancel',{id:song.id})}>取消下载</button> : null}
    {song.sourceId ? <button disabled={busy} onClick={async () => {setBusy(true); try {const source = await invoke<import('./model.ts').Source[]>('sources.list').then(rows => rows.find(s => s.id === song.sourceId)); if (!source) return; const extra = await songExtras(source,song); patch(extra); notify(extra.lyrics || extra.artwork ? '已获取服务器封面 / 歌词' : '服务器未提供封面或歌词');} catch(e) {notify(String(e));} finally {setBusy(false);}}}>获取服务器封面与歌词</button> : null}
    <button onClick={() => void lyrics()}>导入同步歌词</button><button onClick={() => setTiming(true)}>歌词打轴器</button>
    {song.lyrics ? <><button onClick={async () => {try {await invoke('text.export',{name:`${song.title.replace(/[\\/:*?"<>|]/g,'_')}.${song.lyrics?.trim().startsWith('<') ? 'ttml' : 'lrc'}`,text:song.lyrics});} catch(e) {notify(String(e));}}}>导出歌词</button>
    {!song.sourceId || song.offline ? <button onClick={async () => {try {await invoke('lyrics.save',{id:song.id,text:song.lyrics}); notify('已保存同名外置歌词');} catch(e) {notify(String(e));}}}>保存同名外置歌词</button> : null}
    <button onClick={async () => {try {const lines = parseLyrics(song.lyrics || ''); const selected = lines.filter(l => l.time <= player.snapshot.position && (l.end || Infinity) > player.snapshot.position).map(l => l.text); const rows = (selected.length ? selected : lines.slice(0,3).map(l => l.text)).slice(0,4); const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1080" viewBox="0 0 1080 1080"><rect width="1080" height="1080" fill="#18181c"/><circle cx="880" cy="140" r="280" fill="${state.settings.accent}" opacity=".28"/><text x="80" y="120" fill="#aaa" font-size="28" font-family="sans-serif">HALCYON · LYRICS</text>${rows.map((line,n) => `<text x="80" y="${330+n*100}" fill="#fff" font-size="48" font-family="sans-serif">${escapeXML(line.slice(0,25))}</text>`).join('')}<text x="80" y="880" fill="#fff" font-size="32" font-family="sans-serif">${escapeXML(song.title.slice(0,35))}</text><text x="80" y="940" fill="#aaa" font-size="26" font-family="sans-serif">${escapeXML(song.artist.slice(0,35))}</text></svg>`; await invoke('text.export',{name:'Halcyon-lyrics-card.svg',text:svg});} catch(e) {notify(String(e));}}}>分享歌词卡片（SVG）</button></> : null}
    {host.platform !== 'browser' ? <button onClick={async () => {try {const artwork = await invoke<string>('image.import'); if (artwork) patch({artwork});} catch(e) {notify(String(e));}}}>设置歌曲封面</button> : null}
    {host.platform !== 'browser' && (!song.sourceId || song.offline) ? <><button onClick={async()=>{try{if(await invoke('video.import',{id:song.id}))notify('已关联本地 MV');}catch(e){notify(String(e));}}}>关联本地 MV</button><button onClick={async()=>{try{await player.toggle(false);const url=await invoke<string|null>('video.play',{id:song.id});if(url)setVideo(url);}catch(e){notify(String(e));}}}>播放关联 MV</button></> : null}
    <button onClick={() => setEdit(true)}>编辑显示信息</button>
    <label>加入歌单<select defaultValue="" onChange={e => {const id = e.target.value; if (!id) return; update(state => ({...state, playlists:state.playlists.map(p => p.id === id ? {...p,songIds:[...new Set([...p.songIds,song.id])]} : p)})); close();}}><option value="">选择歌单</option>{state.playlists.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></label>
    <button className="danger" onClick={async () => {try {await remove(song); close();} catch(e) {notify(String(e));}}}>从曲库移除</button>
  </div>}</Modal>;
}
