import { createHash, randomBytes } from 'node:crypto';
import { remoteURL, serverURL } from './security.mjs';
export function publicSource({password, token, ...source}) {return source;}
export function authenticatedRequest(source, relative, query = {}, headers = {}) {
  const url = remoteURL(source.url, relative, query); headers = {...headers};
  if (source.type === 'webdav' && source.username) headers.Authorization = `Basic ${Buffer.from(`${source.username}:${source.password || ''}`).toString('base64')}`;
  if (source.type === 'subsonic') {
    const salt = randomBytes(12).toString('hex');
    const auth = {u: source.username, t: createHash('md5').update((source.password || '') + salt).digest('hex'), s: salt, v: '1.16.1', c: 'Halcyon', f: 'json'};
    for (const [key, value] of Object.entries(auth)) url.searchParams.set(key, value || '');
  }
  if (source.type === 'emby') headers['X-Emby-Token'] = source.token || '';
  return {url, headers};
}
export async function fetchText(source, args) {
  if (!['GET', 'PROPFIND'].includes(args.method || 'GET')) throw new Error('不支持该请求方法');
  const {url, headers} = authenticatedRequest(source, args.path, args.query);
  if (args.method === 'PROPFIND') Object.assign(headers, {Depth: '1', 'Content-Type': 'application/xml; charset=utf-8'});
  const response = await fetch(url, {headers, method: args.method || 'GET', body: args.body, redirect: 'error', signal: AbortSignal.timeout(30000)});
  if (!response.ok) throw new Error(`服务器返回 HTTP ${response.status}`);
  const chunks = []; let length = 0;
  for await (const chunk of response.body) {length += chunk.length; if (length > 16000000) throw new Error('服务器响应超过 16 MB'); chunks.push(chunk);}
  return Buffer.concat(chunks).toString('utf8');
}
export async function prepareSource(source) {
  if (!['webdav', 'subsonic', 'emby', 'ai'].includes(source.type)) throw new Error('未知曲库来源');
  source.url = serverURL(source.url).toString();
  if (source.type === 'emby' && !source.token) {
    const response = await fetch(remoteURL(source.url, 'Users/AuthenticateByName'), {
      method: 'POST', redirect: 'error', signal: AbortSignal.timeout(30000),
      headers: {'Content-Type': 'application/json', 'X-Emby-Authorization': 'MediaBrowser Client="Halcyon", Device="Desktop", DeviceId="Halcyon-Desktop", Version="0.1.0"'},
      body: JSON.stringify({Username: source.username, Pw: source.password})
    });
    if (!response.ok) throw new Error(`Emby 登录失败 (HTTP ${response.status})`);
    const data = await response.json(); if (!data.AccessToken || !data.User?.Id) throw new Error('Emby 返回了无效的登录信息');
    source.token = data.AccessToken; source.userId = data.User.Id; delete source.password;
  }
  return source;
}
export function streamRequest(source, song, range) {
  let relative = song.remotePath, query = {};
  if (source.type === 'subsonic') {relative = 'rest/stream.view'; query = {id: song.remoteId};}
  if (source.type === 'emby') {relative = `Audio/${encodeURIComponent(song.remoteId)}/stream`; query = {static: 'true'};}
  if (!relative) throw new Error('缺少远程歌曲路径');
  return authenticatedRequest(source, relative, query, range ? {Range: range} : {});
}
