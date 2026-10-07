import type {Settings, Song, Source} from './model.ts';
export function LibraryControls({settings, sources, songs, setSettings}: {settings: Settings; sources: Source[]; songs: Song[]; setSettings: (patch: Partial<Settings>) => void}) {
  return <div className="library-controls">
    <select aria-label="曲库排序" value={settings.sort} onChange={e => setSettings({sort: e.target.value})}>{[['default','导入顺序'],['title','歌曲名'],['artist','艺术家'],['album','专辑'],['duration','时长'],['year','年份'],['count','播放次数']].map(([value,label]) => <option key={value} value={value}>{label}</option>)}</select>
    <button aria-label="倒序排序" aria-pressed={settings.descending} onClick={() => setSettings({descending: !settings.descending})}>{settings.descending ? '↓ 倒序' : '↑ 正序'}</button>
    <select aria-label="音源筛选" value={settings.sourceFilter} onChange={e => setSettings({sourceFilter: e.target.value})}><option value="all">全部音源</option><option value="local">本地导入</option><option value="offline">离线下载</option>{sources.filter(s => s.type !== 'ai').map(s => <option value={s.id} key={s.id}>{s.name}</option>)}</select>
    <select aria-label="格式筛选" value={settings.formatFilter} onChange={e => setSettings({formatFilter: e.target.value})}><option value="all">全部格式</option>{[...new Set(songs.map(s => s.format?.toLowerCase()).filter(Boolean))].sort().map(f => <option value={f} key={f}>{f?.toUpperCase()}</option>)}</select>
  </div>;
}
