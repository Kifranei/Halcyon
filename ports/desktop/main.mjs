import {remoteAsset, downloadSong, saveLyrics, aiRequest} from './extensions.mjs';
import { app, BrowserWindow, dialog, ipcMain, protocol, safeStorage, session, Menu, net, nativeImage } from 'electron';
import path from 'node:path';
import fs from 'node:fs/promises';
import {createReadStream} from 'node:fs';
import { Readable } from 'node:stream';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createHash } from 'node:crypto';
import { Library, readJSON, atomicJSON } from './library.mjs';
import { inside, byteRange } from './security.mjs';
import { publicSource, prepareSource, fetchText, streamRequest, authenticatedRequest } from './remote.mjs';
const directory = path.dirname(fileURLToPath(import.meta.url)); const web = path.resolve(directory, '../shared/dist');
if (process.env.HALCYON_USER_DATA_DIR) app.setPath('userData', path.resolve(process.env.HALCYON_USER_DATA_DIR));
protocol.registerSchemesAsPrivileged([{scheme: 'halcyon', privileges: {standard: true, secure: true, supportFetchAPI: true, stream: true, corsEnabled: true}}]);
let window, library, userData; let videos = {}; let sources = []; let sourceLoadError = ''; const streams = new Map(); const downloads = new Map();
let writes = Promise.resolve();
function save(file, value) {const task = writes.then(() => atomicJSON(path.join(userData, file), value)); writes = task.catch(() => {}); return task;}
function cryptoReady() {return safeStorage.isEncryptionAvailable() && (process.platform !== 'linux' || safeStorage.getSelectedStorageBackend() !== 'basic_text');}
async function loadSources() {
  const rows = await readJSON(path.join(userData, 'sources.json'), []);
  sources = rows.map(row => {
    if (!row.secret) return row;
    if (!cryptoReady()) throw new Error('系统钥匙串不可用，无法解密远程曲库凭据');
    const {secret, ...source} = row; return {...source, ...JSON.parse(safeStorage.decryptString(Buffer.from(secret, 'base64')))};
  });
}
async function saveSources() {
  const rows = sources.map(({password, token, ...source}) => {
    if (!password && !token) return source;
    if (!cryptoReady()) throw new Error('请启用系统钥匙串后保存需要密码的远程曲库');
    return {...source, secret: safeStorage.encryptString(JSON.stringify({password, token})).toString('base64')};
  });
  await save('sources.json', rows);
}
function source(id) {const result = sources.find(s => s.id === id); if (!result) throw new Error('远程曲库不存在，请重新连接'); return result;}
async function localResponse(file, request) {
  const stat = await fs.stat(file); const range = byteRange(request.headers.get('Range'), stat.size);
  if (!range) return new Response(null, {status: 416, headers: {'Content-Range': `bytes */${stat.size}`}});
  const headers = {'Access-Control-Allow-Origin': 'halcyon://app', 'Accept-Ranges': 'bytes', 'Content-Length': String(range.end - range.start + 1), 'Content-Type': ({mp3: 'audio/mpeg', m4a: 'audio/mp4', aac: 'audio/aac', wav: 'audio/wav', flac: 'audio/flac', ogg: 'audio/ogg', opus: 'audio/ogg', aif: 'audio/aiff', aiff: 'audio/aiff', mp4:'video/mp4', webm:'video/webm', mov:'video/quicktime', mkv:'video/x-matroska'})[path.extname(file).slice(1).toLowerCase()] || 'application/octet-stream'};
  if (range.partial) headers['Content-Range'] = `bytes ${range.start}-${range.end}/${stat.size}`;
  return new Response(stat.size && request.method !== 'HEAD' ? Readable.toWeb(createReadStream(file, {start: range.start, end: range.end})) : null, {status: range.partial ? 206 : 200, headers});
}
async function invoke(method, args = {}) {
  switch (method) {
    case 'library.list': return library.list();
    case 'library.import': {
      const result = await dialog.showOpenDialog(window, {properties: args.folder ? ['openDirectory', 'multiSelections'] : ['openFile', 'multiSelections'], filters: args.folder ? [] : [{name: '音频', extensions: ['mp3', 'm4a', 'aac', 'alac', 'flac', 'wav', 'aif', 'aiff', 'ogg', 'opus', 'wma']}]});
      if (result.canceled) return [];
      const scan = await library.scan(result.filePaths);
      if (scan.errors.length) window.webContents.send('halcyon:event', {type: 'notice', message: `导入 ${scan.songs.length} 首，${scan.errors.length} 项需要检查。${scan.errors.slice(0, 3).join('\n')}`});
      return scan.songs;
    }
    case 'video.import': {
      const result=await dialog.showOpenDialog(window,{properties:['openFile'],filters:[{name:'MV',extensions:['mp4','webm','mov','mkv']}]});
      if(result.canceled)return false;library.path(args.id);videos[args.id]=await fs.realpath(result.filePaths[0]);await save('videos.json',videos);return true;
    }
    case 'video.play': if(!videos[args.id])throw new Error('请先关联 MV');return `halcyon://video/${encodeURIComponent(args.id)}`;
    case 'video.has': return !!videos[args.id];
    case 'library.download': {
      if(downloads.has(args.id))throw new Error('这首歌曲正在下载');
      const controller=new AbortController();downloads.set(args.id,controller);
      try {return await downloadSong(library,userData,source(args.sourceId),args,controller.signal);} finally {downloads.delete(args.id);}
    }
    case 'download.cancel': downloads.get(args.id)?.abort();return;
    case 'remote.asset': {const image=nativeImage.createFromDataURL(await remoteAsset(source(args.sourceId),args));if(image.isEmpty())throw new Error('无效服务器封面');const width=Math.min(512,image.getSize().width);return 'data:image/jpeg;base64,'+image.resize({width}).toJPEG(80).toString('base64');}
    case 'lyrics.save': return saveLyrics(library,args);
    case 'image.import': {
      const result=await dialog.showOpenDialog(window,{properties:['openFile'],filters:[{name:'封面（最大 1 MB）',extensions:['jpg','jpeg','png','webp','gif']}]});
      if(result.canceled)return '';const file=result.filePaths[0];if((await fs.stat(file)).size>1000000)throw new Error('封面超过 1 MB');
      const mime={jpg:'image/jpeg',jpeg:'image/jpeg',png:'image/png',webp:'image/webp',gif:'image/gif'}[path.extname(file).slice(1).toLowerCase()];
      if(!mime)throw new Error('不支持此封面格式');return `data:${mime};base64,${(await fs.readFile(file)).toString('base64')}`;
    }
    case 'library.remove': await library.remove(args.id); return;
    case 'media.resolve': {
      if (!args.sourceId || args.offline) {library.path(args.id); return `halcyon://media/${encodeURIComponent(args.id)}`;}
      source(args.sourceId); const key = createHash('sha256').update(JSON.stringify(args)).digest('hex'); streams.set(key, args); return `halcyon://remote/${key}`;
    }
    case 'state.load': return readJSON(path.join(userData, 'state.json'), null);
    case 'state.save': return save('state.json', args);
    case 'text.import': {
      const result = await dialog.showOpenDialog(window, {properties: ['openFile'], filters: [{name: '歌词 / 备份 / 歌单', extensions: ['lrc', 'elrc', 'ttml', 'txt', 'json', 'm3u', 'm3u8']}]});
      if (result.canceled) return '';
      if ((await fs.stat(result.filePaths[0])).size > 16000000) throw new Error('文本超过 16 MB');
      return fs.readFile(result.filePaths[0], 'utf8');
    }
    case 'text.export': {
      if (typeof args.text !== 'string' || args.text.length > 16000000) throw new Error('无效导出内容');
      const result = await dialog.showSaveDialog(window, {defaultPath: path.basename(String(args.name || 'Halcyon.json'))});
      if (!result.canceled) await fs.writeFile(result.filePath, args.text, {mode: 0o600}); return;
    }
    case 'sources.list': if (sourceLoadError) throw new Error(sourceLoadError); return sources.map(publicSource);
    case 'sources.save': {
      if (!/^[a-zA-Z0-9-]{1,100}$/.test(args.id || '')) throw new Error('无效曲库 ID');
      const previous=sources.find(s=>s.id===args.id); const retained=previous && previous.url===args.url && previous.username===(args.username || '') && previous.type===args.type ? previous : {};
      const prepared = await prepareSource({id: args.id, model: String(args.model || '').slice(0,200), protocol: args.protocol === 'anthropic' ? 'anthropic' : 'openai', name: String(args.name || '').slice(0, 200), type: args.type, url: args.url, username: args.username || '', password: args.password || retained.password || '', token: args.password ? '' : retained.token || '', userId: retained.userId});
      if ((prepared.password || prepared.token) && !cryptoReady()) throw new Error('系统钥匙串不可用，凭据未保存');
      const old = sources; sources = [...sources.filter(s => s.id !== prepared.id), prepared];
      try {await saveSources(); sourceLoadError = '';} catch (error) {sources = old; throw error;} return publicSource(prepared);
    }
    case 'sources.remove': {const old = sources; sources = sources.filter(s => s.id !== args.id); try {await saveSources();} catch(e) {sources = old; throw e;} return;}
    case 'ai.request': return aiRequest(source(args.sourceId), args.prompt);
    case 'remote.request': return fetchText(source(args.sourceId), args);
    default: throw new Error('未知客户端命令');
  }
}
async function createWindow() {
  window = new BrowserWindow({title: 'Halcyon', width: 1280, height: 820, minWidth: 680, minHeight: 500, backgroundColor: '#ffffff', webPreferences: {preload: path.join(directory, 'preload.cjs'), contextIsolation: true, nodeIntegration: false, sandbox: true, webSecurity: true, backgroundThrottling: false}});
  window.webContents.setWindowOpenHandler(() => ({action: 'deny'}));
  window.webContents.on('will-navigate', (event, url) => {if (url !== 'halcyon://app/') event.preventDefault();});
  Menu.setApplicationMenu(Menu.buildFromTemplate([...(process.platform === 'darwin' ? [{role: 'appMenu'}] : []), {role: 'fileMenu'}, {role: 'editMenu'}, {role: 'viewMenu'}, {role: 'windowMenu'}]));
  await window.loadURL('halcyon://app/');
}
app.whenReady().then(async () => {
  userData = app.getPath('userData'); library = new Library(userData); await library.load(); videos=await readJSON(path.join(userData,'videos.json'),{}); try {await loadSources();} catch(error) {sourceLoadError = error.message;}
  session.defaultSession.setPermissionRequestHandler((_, permission, callback) => callback(false));
  protocol.handle('halcyon', async request => {
    try {
      const url = new URL(request.url);
      if (url.host === 'app') {
        const file = path.resolve(web, `.${decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname)}`);
        if (!inside(web, file)) return new Response('Forbidden', {status: 403});
        return net.fetch(pathToFileURL(file).toString());
      }
      if (url.host === 'video') {const file=videos[decodeURIComponent(url.pathname.slice(1))];if(!file)throw new Error('未关联视频');return await localResponse(file,request);}
      if (url.host === 'media') return await localResponse(library.path(decodeURIComponent(url.pathname.slice(1))), request);
      if (url.host === 'remote') {
        const song = streams.get(url.pathname.slice(1)); if (!song) throw new Error('未知流媒体');
        const {url: stream, headers} = streamRequest(source(song.sourceId), song, request.headers.get('Range'));
        const response=await net.fetch(stream.toString(), {headers, redirect: 'error'}); const outgoing=new Headers(response.headers);outgoing.set('Access-Control-Allow-Origin','halcyon://app');return new Response(response.body,{status:response.status,headers:outgoing});
      }
      return new Response('Not found', {status: 404});
    } catch {return new Response('Media unavailable', {status: 404});}
  });
  ipcMain.handle('halcyon:invoke', (event, method, args) => {
    if (event.sender !== window?.webContents || event.senderFrame?.url !== 'halcyon://app/') throw new Error('非可信页面');
    return invoke(method, args);
  });
  await createWindow(); app.on('activate', () => {if (!BrowserWindow.getAllWindows().length) void createWindow();});
}).catch(error => {dialog.showErrorBox('Halcyon 无法启动', error.message); app.quit();});
app.on('window-all-closed', () => {if (process.platform !== 'darwin') app.quit();});
