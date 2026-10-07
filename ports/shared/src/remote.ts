import { invoke } from './bridge.ts';
import { audioExtensions, filenameMetadata, type Song, type Source } from './model.ts';
export interface RemoteFolder {name: string; path: string}
export async function request(source: Source, path: string, query: Record<string, string> = {}, method = 'GET', body?: string): Promise<string> {
  return invoke('remote.request', {sourceId: source.id, path, query, method, body});
}
export function subsonicSongs(source: Source, rows: Record<string, any>[]): Song[] {
  return rows.filter(row => row.id != null).map(row => ({id: `${source.id}:${row.id}`, sourceId: source.id, remoteId: String(row.id),
    title: row.title || row.id, artist: row.artist || '未知艺术家', album: row.album || '未知专辑', albumArtist: row.albumArtist,
    duration: Number(row.duration) || 0, format: row.suffix, genre: row.genre, year: row.year, coverId: row.coverArt, bitrate: Number(row.bitRate) * 1000 || 0, size: Number(row.size) || 0, sampleRate: Number(row.samplingRate) || 0, bitDepth: Number(row.bitDepth) || 0, replayGain: Number(row.replayGain?.trackGain) || 0}));
}
export function embySongs(source: Source, rows: Record<string, any>[]): Song[] {
  return rows.filter(row => row.Id).map(row => ({id: `${source.id}:${row.Id}`, sourceId: source.id, remoteId: row.Id,
    title: row.Name || row.Id, artist: row.Artists?.join(' / ') || row.AlbumArtist || '未知艺术家', albumArtist: row.AlbumArtist,
    album: row.Album || '未知专辑', duration: (Number(row.RunTimeTicks) || 0) / 10000000, genre: row.Genres?.[0], year: row.ProductionYear, coverId: row.ImageTags?.Primary ? row.Id : row.AlbumId, format: row.Container || row.Path?.split('.').pop(), bitrate: row.MediaSources?.[0]?.Bitrate}));
}
export async function listRemote(source: Source, path = ''): Promise<{songs: Song[]; folders: RemoteFolder[]}> {
  if (source.type === 'webdav') {
    const text = await request(source, path, {}, 'PROPFIND', '<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:displayname/><d:getcontenttype/></d:prop></d:propfind>');
    const xml = new DOMParser().parseFromString(text, 'text/xml');
    if (xml.querySelector('parsererror')) throw new Error('WebDAV 返回了无效的 XML');
    const base = new URL(source.url.endsWith('/') ? source.url : `${source.url}/`);
    const here = new URL(path || './', base); const songs: Song[] = [], folders: RemoteFolder[] = [];
    for (const response of Array.from(xml.getElementsByTagNameNS('*', 'response'))) {
      const href = response.getElementsByTagNameNS('*', 'href')[0]?.textContent; if (!href) continue;
      const url = new URL(href, here);
      if (url.origin !== base.origin || !url.pathname.startsWith(base.pathname) || url.pathname.replace(/\/$/, '') === here.pathname.replace(/\/$/, '')) continue;
      const relative = url.pathname.slice(base.pathname.length);
      const name = decodeURIComponent(url.pathname.replace(/\/$/, '').split('/').pop() || '');
      if (response.getElementsByTagNameNS('*', 'collection').length) folders.push({name, path: relative});
      else if (audioExtensions.has(name.split('.').pop()?.toLowerCase() || '')) songs.push({id: `${source.id}:${relative}`, sourceId: source.id, remotePath: relative, ...filenameMetadata(name), fileName: name, format: name.split('.').pop()?.toLowerCase(), folder: decodeURIComponent(relative.replace(/[^/]+$/,'').replace(/\/$/,'')), duration: 0});
    }
    return {songs, folders};
  }
  const songs: Song[] = []; const seen = new Set<string>();
  for (let offset = 0; offset < 100000; offset += 500) {
    let page: Song[];
    if (source.type === 'subsonic') {
      const data = JSON.parse(await request(source, 'rest/search3.view', {query: '', songCount: '500', songOffset: String(offset), albumCount: '0', artistCount: '0'}))['subsonic-response'];
      if (data?.status !== 'ok') {if (offset === 0) return albumFallback(source); throw new Error(data?.error?.message || 'Subsonic 请求失败');}
      page = subsonicSongs(source, data.searchResult3?.song || []); if (!page.length && offset === 0) return albumFallback(source);
    } else {
      const data = JSON.parse(await request(source, `Users/${encodeURIComponent(source.userId || '')}/Items`, {Recursive: 'true', IncludeItemTypes: 'Audio', Fields: 'Genres,AlbumArtist', StartIndex: String(offset), Limit: '500', SortBy: 'SortName', SortOrder: 'Ascending'}));
      page = embySongs(source, data.Items || []);
    }
    let added = 0; for (const song of page) if (!seen.has(song.id)) {seen.add(song.id); songs.push(song); added++;}
    if (page.length < 500) return {songs, folders: []};
    if (!added) throw new Error('服务器未正确分页，停止扫描以避免重复加载');
  }
  throw new Error('曲库超过本轮 100000 首扫描上限，请缩小来源范围');
}

async function albumFallback(source: Source): Promise<{songs: Song[]; folders: RemoteFolder[]}> {
 const songs = new Map<string,Song>(); const seen = new Set<string>();
 for (let offset=0; offset<100000; offset+=500) {
  const data=JSON.parse(await request(source,'rest/getAlbumList2.view',{type:'alphabeticalByName',size:'500',offset:String(offset)}))['subsonic-response'];
  if(data?.status!=='ok')throw new Error(data?.error?.message || '服务器不支持整库枚举');
  const albums=data.albumList2?.album || []; let added=0;
  for(const album of albums) {if(seen.has(String(album.id)))continue; seen.add(String(album.id));added++;
   const detail=JSON.parse(await request(source,'rest/getAlbum.view',{id:String(album.id)}))['subsonic-response'];
   if(detail?.status!=='ok')throw new Error(detail?.error?.message || '专辑读取失败');
   for(const song of subsonicSongs(source,detail.album?.song || [])) songs.set(song.id,song);
   if(songs.size>100000)throw new Error('曲库超过 100000 首');
  }
  if(albums.length<500)return {songs:[...songs.values()],folders:[]};
  if(!added)throw new Error('服务器未正确分页');
 }
 throw new Error('专辑数量超过扫描上限');
}
export async function scanWebDAV(source: Source, onProgress: (count: number) => void, cancelled: () => boolean): Promise<Song[]> {
 const paths=['']; const seen=new Set<string>();const songs=new Map<string,Song>();
 while(paths.length) {
  if(cancelled())throw new Error('扫描已取消');
  const path=paths.shift()!;if(seen.has(path))continue;seen.add(path);
  if(seen.size>10000)throw new Error('目录超过 10000 个，请缩小来源范围');
  const result=await listRemote(source,path);for(const song of result.songs)songs.set(song.id,song);
  if(songs.size>100000)throw new Error('曲库超过 100000 首');
  paths.push(...result.folders.map(folder=>folder.path));onProgress(songs.size);
 }
 return [...songs.values()];
}
export async function songExtras(source: Source, song: Song): Promise<{lyrics?: string; artwork?: string}> {
 const extra: {lyrics?: string; artwork?: string}={};
 const image=async(path: string, query: Record<string,string>={})=> {try{extra.artwork=await invoke<string>('remote.asset',{sourceId:source.id,path,query});}catch{}};
 if(source.type==='subsonic') {
  await image('rest/getCoverArt.view',{id:song.coverId || song.remoteId || '',size:'512'});
  try {
   const data=JSON.parse(await request(source,'rest/getLyricsBySongId.view',{id:song.remoteId || ''}))['subsonic-response'];
   const sets=data?.lyricsList?.structuredLyrics || [];
   extra.lyrics=sets.map((set: any)=>(set.line || []).map((line: any)=>{const ms=Math.max(0,Number(line.start)+(Number(set.offset)||0));return set.synced ? `[${String(Math.floor(ms/60000)).padStart(2,'0')}:${(ms/1000%60).toFixed(3).padStart(6,'0')}]${line.value}` : line.value;}).join('\n')).join('\n');
  }catch{}
  if(!extra.lyrics)try{const data=JSON.parse(await request(source,'rest/getLyrics.view',{artist:song.artist,title:song.title}))['subsonic-response'];extra.lyrics=data?.lyrics?.value;}catch{}
 } else if(source.type==='emby') {
  await image(`Items/${encodeURIComponent(song.coverId || song.remoteId || '')}/Images/Primary`,{MaxWidth:'512'});
 } else {
  const path=song.remotePath || '';for(const suffix of ['ttml','elrc','lrc'])try{extra.lyrics=await request(source,path.replace(/\.[^/.]+$/,'.'+suffix));if(extra.lyrics)break;}catch{}
  await image(path.replace(/[^/]+$/,'cover.jpg'));
 }
 return extra;
}
