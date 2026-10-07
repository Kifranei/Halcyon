import {useRef, useState} from 'react';
import {invoke} from './bridge.ts';
import {listRemote, scanWebDAV, type RemoteFolder} from './remote.ts';
import {type Song, type Source} from './model.ts';
import {Icon, Modal} from './components.tsx';
export function RemoteView({sources, setSources, onSongs, notify}: {sources: Source[]; setSources: (sources: Source[]) => void; onSongs: (songs: Song[], sourceId: string, replace: boolean) => void; notify: (text: string) => void}) {
  const cancelled = useRef(false); const [progress,setProgress] = useState(0);
  const [editing, setEditing] = useState<Source | 'new' | null>(null); const [busy, setBusy] = useState(false);
  const [browse, setBrowse] = useState<{source: Source; path: string; folders: RemoteFolder[]; songs: Song[]} | null>(null);
  async function scan(source: Source, path = '') {
    setBusy(true);
    try {const result = await listRemote(source, path); if (source.type === 'webdav') setBrowse({source, path, ...result}); onSongs(result.songs, source.id, source.type !== 'webdav'); notify(`已加载 ${result.songs.length} 首歌曲${result.folders.length ? `，${result.folders.length} 个文件夹` : ''}`);}
    catch (error) {notify(error instanceof Error ? error.message : String(error));} finally {setBusy(false);}
  }
  return <div className="remote-view">
    <p className="muted">连接你自己的 WebDAV、Navidrome / OpenSubsonic 或 Emby 音乐服务器。</p>
    <button className="primary" onClick={() => setEditing('new')}><Icon name="plus"/>添加远程曲库</button>
    {sources.filter(s => s.type !== 'ai').map(source => <div className="source-row" key={source.id}><Icon name="server"/><div><strong>{source.name}</strong><small>{source.type} · {source.url}</small></div><button disabled={busy} onClick={() => void scan(source)}>{busy ? '加载中…' : source.type === 'webdav' ? '浏览' : '刷新曲库'}</button>{source.type === 'webdav' ? <button disabled={busy} onClick={async () => {setBusy(true);cancelled.current=false;try{const songs=await scanWebDAV(source,setProgress,()=>cancelled.current);onSongs(songs,source.id,true);notify(`已扫描 ${songs.length} 首歌曲`);}catch(e){notify(String(e));}finally{setBusy(false);}}}>扫描全部目录</button> : null}<button disabled={busy} onClick={() => setEditing(source)}>编辑</button><button onClick={async () => {try {await invoke('sources.remove', {id: source.id}); setSources(sources.filter(s => s.id !== source.id)); onSongs([], source.id, true); if (browse?.source.id === source.id) setBrowse(null);} catch (e) {notify(String(e));}}}>断开</button></div>)}
    {busy ? <p role="status">处理中，已扫描 {progress} 首 <button onClick={() => {cancelled.current=true;}}>取消递归扫描</button></p> : null}
    {browse ? <div className="remote-browser"><h3>{browse.source.name} / {decodeURIComponent(browse.path)}</h3><button disabled={busy} onClick={() => void scan(browse.source, browse.path.replace(/[^/]+\/?$/, ''))}>上一级</button>
      {browse.folders.map(folder => <button className="folder-row" disabled={busy} key={folder.path} onClick={() => void scan(browse.source, folder.path)}><Icon name="folder"/>{folder.name}</button>)}
      <p className="muted">当前目录的音频已加入音乐库，可在音乐库播放。进入子目录加载更多音乐。</p></div> : null}
    {editing ? <SourceForm source={editing === 'new' ? undefined : editing} close={() => setEditing(null)} save={async values => {
      setBusy(true); try {const source = await invoke<Source>('sources.save', values); setSources([...sources.filter(s => s.id !== source.id), source]); setEditing(null); await scan(source);} finally {setBusy(false);}
    }} notify={notify} busy={busy}/> : null}
  </div>;
}
function SourceForm({source, close, save, notify, busy}: {source?: Source; close: () => void; save: (values: unknown) => Promise<void>; notify: (text: string) => void; busy: boolean}) {
  return <Modal title={source ? '编辑远程曲库' : '添加远程曲库'} close={close}><form onSubmit={async event => {
    event.preventDefault(); const values = Object.fromEntries(new FormData(event.currentTarget));
    try {await save({...values, id: source?.id || crypto.randomUUID()});} catch (e) {notify(e instanceof Error ? e.message : String(e));}
  }}>
    <label>名称<input name="name" defaultValue={source?.name} required maxLength={200}/></label>
    <label>类型<select name="type" defaultValue={source?.type || 'webdav'}><option value="webdav">WebDAV</option><option value="subsonic">Navidrome / OpenSubsonic</option><option value="emby">Emby</option></select></label>
    <label>服务器地址<input name="url" type="url" placeholder="https://music.example.com/" defaultValue={source?.url} required/></label>
    <label>用户名<input name="username" autoComplete="username" defaultValue={source?.username}/></label>
    <label>密码<input name="password" type="password" autoComplete="current-password" placeholder={source ? '留空保留原密码（地址和账号不变时）' : ''}/></label>
    <p className="muted">WebDAV 支持 Basic 认证；推荐 HTTPS。密码保存在系统安全存储，不进入应用数据备份。</p>
    <button className="primary" disabled={busy} type="submit">{busy ? '连接中…' : '保存并连接'}</button>
  </form></Modal>;
}
