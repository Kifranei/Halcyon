import path from 'node:path';
export function inside(root, candidate) {
  const relative = path.relative(root, candidate);
  return relative === '' || (!relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative));
}
export function serverURL(value) {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) throw new Error('请使用不含凭据、参数或片段的 HTTP(S) 服务器地址');
  if (!url.pathname.endsWith('/')) url.pathname += '/';
  return url;
}
export function remoteURL(base, relative = '', query = {}) {
  const root = serverURL(base);
  if (typeof relative !== 'string' || relative.startsWith('/') || relative.includes('\\')) throw new Error('无效远程路径');
  // Reject encoded slash and traversal before URL normalization.
  const decoded = decodeURIComponent(relative);
  if (decoded.split('/').some(part => part === '..' || part === '.') || /[\\]/.test(decoded)) throw new Error('无效远程路径');
  const url = new URL(relative, root);
  if (url.origin !== root.origin || !url.pathname.startsWith(root.pathname)) throw new Error('远程请求不能离开配置的服务器路径');
  for (const [key, value] of Object.entries(query)) if (typeof value === 'string') url.searchParams.set(key, value);
  return url;
}
export function byteRange(header, size) {
  if (!header) return {start: 0, end: size - 1, partial: false};
  const match = /^bytes=(\d*)-(\d*)$/.exec(header);
  if (!match || (!match[1] && !match[2]) || !size) return null;
  const start = match[1] ? Number(match[1]) : Math.max(0, size - Number(match[2]));
  const end = match[1] && match[2] ? Math.min(Number(match[2]), size - 1) : size - 1;
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start < 0 || start > end || start >= size) return null;
  return {start, end, partial: true};
}
