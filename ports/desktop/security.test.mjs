import test from 'node:test';
import assert from 'node:assert/strict';
import os from 'node:os';
import fs from 'node:fs/promises';
import path from 'node:path';
import {inside, remoteURL, serverURL, byteRange} from './security.mjs';
import {Library} from './library.mjs';
import {publicSource, authenticatedRequest, fetchText} from './remote.mjs';
import http from 'node:http';
test('file paths and remote paths cannot escape their configured root', () => {
  assert.equal(inside('/music', '/music/a.mp3'), true); assert.equal(inside('/music', '/music-other/a.mp3'), false);
  for (const relative of ['../secret', '%2e%2e/secret', 'https://evil.example/file', '//evil.example/file', '/outside', '..%2fsecret', 'a\\b']) assert.throws(() => remoteURL('https://example.com/music/', relative));
  assert.equal(remoteURL('https://example.com/music/', 'album/a%20b.mp3').pathname, '/music/album/a%20b.mp3');
  assert.throws(() => serverURL('file:///etc/passwd')); assert.throws(() => serverURL('https://user:pass@example.com/'));
});
test('byte ranges allow seeking, suffix requests and reject invalid ranges', () => {
  assert.deepEqual(byteRange('bytes=20-50', 100), {start: 20, end: 50, partial: true});
  assert.deepEqual(byteRange('bytes=-10', 100), {start: 90, end: 99, partial: true});
  assert.deepEqual(byteRange('bytes=95-', 100), {start: 95, end: 99, partial: true});
  assert.equal(byteRange('bytes=100-', 100), null); assert.equal(byteRange('bytes=10-5', 100), null);
});
test('Subsonic uses salted token auth and public source omits credentials', () => {
  const source = {type: 'subsonic', url: 'https://example.com/', username: 'user', password: 'secret', token: 'token'};
  const {url} = authenticatedRequest(source, 'rest/ping.view');
  assert.equal(url.searchParams.get('u'), 'user'); assert.match(url.searchParams.get('t'), /^[a-f0-9]{32}$/);
  assert.ok(!url.toString().includes('secret')); assert.equal('password' in publicSource(source), false); assert.equal('token' in publicSource(source), false);
});
test('remote metadata requests report HTTP errors and refuse redirects', async () => {
  const server = http.createServer((request, response) => {
    if (request.url === '/ok') {response.end('{"ok":true}'); return;}
    if (request.url === '/redirect') {response.writeHead(302, {Location: '/ok'}); response.end(); return;}
    response.writeHead(401); response.end();
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const source = {type: 'webdav', url: `http://127.0.0.1:${server.address().port}/`};
  try {assert.equal(await fetchText(source, {path: 'ok'}), '{"ok":true}'); await assert.rejects(fetchText(source, {path: 'missing'}), /401/); await assert.rejects(fetchText(source, {path: 'redirect'}));}
  finally {await new Promise(resolve => server.close(resolve));}
});
test('desktop imports real WAV and sidecar lyrics, deduplicates and survives restart', async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'halcyon-library-'));
  try {
    const folder = path.join(root, 'music'); await fs.mkdir(folder);
    const wav = Buffer.alloc(44 + 16000); wav.write('RIFF'); wav.writeUInt32LE(wav.length - 8, 4); wav.write('WAVEfmt ', 8);
    wav.writeUInt32LE(16, 16); wav.writeUInt16LE(1, 20); wav.writeUInt16LE(1, 22); wav.writeUInt32LE(8000, 24); wav.writeUInt32LE(16000, 28); wav.writeUInt16LE(2, 32); wav.writeUInt16LE(16, 34); wav.write('data', 36); wav.writeUInt32LE(16000, 40);
    await fs.writeFile(path.join(folder, 'Artist - Song.wav'), wav); await fs.writeFile(path.join(folder, 'Artist - Song.lrc'), '[00:00.00]hello');
    const lib = new Library(path.join(root, 'state')); await lib.load(); await lib.scan([folder]); await lib.scan([folder]);
    assert.equal(lib.list().length, 1); assert.equal(lib.list()[0].title, 'Song'); assert.equal(lib.list()[0].artist, 'Artist'); assert.equal(lib.list()[0].duration, 1); assert.match(lib.list()[0].lyrics, /hello/);
    const restored = new Library(path.join(root, 'state')); await restored.load(); assert.equal(restored.list().length, 1);
    await restored.remove(restored.list()[0].id); assert.equal(restored.list().length, 0); assert.ok(await fs.stat(path.join(folder, 'Artist - Song.wav')));
    assert.throws(() => restored.path('unknown'));
  } finally {await fs.rm(root, {recursive: true, force: true});}
});

test('simultaneous library mutations serialize atomic writes and preserve all entries', async () => {
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'halcyon-writes-'));
 try{const lib=new Library(root);await lib.load();const writes=[];for(let n=0;n<25;n++){lib.entries.set(String(n),{song:{id:String(n),title:'Song '+n},path:path.join(root,n+'.wav')});writes.push(lib.persist());}await Promise.all(writes);const restored=new Library(root);await restored.load();assert.equal(restored.list().length,25);}
 finally{await fs.rm(root,{recursive:true,force:true});}
});
