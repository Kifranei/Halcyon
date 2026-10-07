// Full Electron integration: real IPC, metadata, app/media protocols and persisted state.
// File dialogs are replaced only inside the test process with user-selected fixture paths.
import {_electron} from 'playwright-core';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';
import http from 'node:http';
import {randomUUID} from 'node:crypto';
import {spawnSync} from 'node:child_process';
const directory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const temporary = await fs.mkdtemp(path.join(os.tmpdir(), 'halcyon-smoke-'));
const wav = Buffer.alloc(44 + 16000 * 12);
wav.write('RIFF'); wav.writeUInt32LE(wav.length - 8, 4); wav.write('WAVEfmt ', 8); wav.writeUInt32LE(16, 16);
wav.writeUInt16LE(1, 20); wav.writeUInt16LE(1, 22); wav.writeUInt32LE(8000, 24); wav.writeUInt32LE(16000, 28);
wav.writeUInt16LE(2, 32); wav.writeUInt16LE(16, 34); wav.write('data', 36); wav.writeUInt32LE(wav.length - 44, 40);
for(let frame=0;frame<96000;frame++)wav.writeInt16LE(Math.round(Math.sin(frame/8000*2*Math.PI*440)*2000),44+frame*2);
const music = path.join(temporary, 'music'); await fs.mkdir(music);
const files = ['Artist - First.wav', 'Artist - Second.wav'].map(name => path.join(music, name));
for (const file of files) await fs.writeFile(file, wav);
await fs.writeFile(path.join(music, 'Artist - First.lrc'), '[00:00.00]First line\n[00:03.00]Second line');
const profile = path.join(temporary, 'profile'); await fs.mkdir(profile);
let application, page, server;
const launch = () => _electron.launch({
  ...(process.env.HALCYON_SMOKE_EXECUTABLE ? {executablePath:process.env.HALCYON_SMOKE_EXECUTABLE} : {}),
  args: [...(process.env.HALCYON_SMOKE_EXECUTABLE ? [] : [directory]),
    ...(process.env.HALCYON_SMOKE_NO_SANDBOX === '1' ? ['--no-sandbox'] : []),
    ...(process.env.HALCYON_SMOKE_HEADLESS === '1' ? ['--ozone-platform=headless'] : [])],
  env: {...process.env, HALCYON_USER_DATA_DIR:profile}, timeout:30000
});
  const persisted=async predicate=>{
    const deadline=Date.now()+6000;
    while(Date.now()<deadline){const state=JSON.parse(await fs.readFile(path.join(profile,'state.json'),'utf8'));if(predicate(state))return state;await new Promise(r=>setTimeout(r,100));}
    throw new Error('State did not reach the expected persisted value');
  };
try {
  application = await launch();
  if (process.env.HALCYON_SMOKE_EXECUTABLE) assert.equal(await application.evaluate(({app})=>app.isPackaged),true);
  page = await application.firstWindow(); page.setDefaultTimeout(15000); const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.getByRole('button', {name: '导入音乐', exact: true}).waitFor();
  await application.evaluate(({dialog}, paths) => {dialog.showOpenDialog = async () => ({canceled: false, filePaths: paths});}, files);
  await page.getByRole('button', {name: '导入音乐', exact: true}).click();
  await page.getByRole('button', {name: '播放 First', exact: true}).waitFor();
  assert.equal(await page.locator('.song-row').count(), 2);
  assert.equal(await page.locator('.row-time').first().innerText(), '0:12');
  await page.evaluate(() => {const Base=window.AudioContext;window.AudioContext=class extends Base {createAnalyser(){const node=super.createAnalyser();window.__halcyonTestAnalyser=node;return node;}};});
  await page.getByRole('button', {name: '播放 First', exact: true}).click();
  await page.getByRole('button', {name: '暂停', exact: true}).first().waitFor();
  await page.waitForFunction(() => Number(document.querySelector('[aria-label="播放进度"]').value) > 0.5);
  await page.waitForFunction(() => {const node=window.__halcyonTestAnalyser;if(!node)return false;const values=new Uint8Array(node.frequencyBinCount);node.getByteFrequencyData(values);return Math.max(...values)>20;});
  await page.locator('.navigation').getByRole('button',{name:'设置',exact:true}).click();
  await page.getByRole('button',{name:'人声',exact:true}).click();
  await page.getByLabel('播放速度',{exact:true}).selectOption('1.5');
  await page.locator('.navigation').getByRole('button',{name:'音乐库',exact:true}).click();
  await page.getByRole('button',{name:'暂停',exact:true}).first().click();
  await page.getByRole('button', {name: '下一首', exact: true}).click();
  await page.locator('.now-song strong').filter({hasText: 'Second'}).waitFor();
  assert.equal(await page.getByRole('button',{name:'播放',exact:true}).first().isVisible(),true,'Paused skip must not resume audio');
  await page.getByRole('button', {name: '收藏 First', exact: true}).click();
  await page.getByRole('button', {name: '取消收藏 First', exact: true}).waitFor();
  let saved;
  const deadline = Date.now() + 5000;
  while (Date.now() < deadline) {
    try {saved = JSON.parse(await fs.readFile(path.join(profile, 'state.json'), 'utf8'));} catch {}
    if (saved?.favorites?.length === 1 && saved.index === 1) break;
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  assert.equal(saved?.favorites?.length, 1, 'Favorite must be written to disk before renderer restart');
  assert.equal(saved.index, 1, 'Current queue index must persist');
  await page.reload();await page.waitForFunction(()=>{const button=document.querySelector('button.primary');return button&&!button.disabled;});
  await page.waitForFunction(() => !document.querySelector('button.primary')?.disabled);
  await page.getByRole('button', {name: '取消收藏 First', exact: true}).waitFor();
  await page.getByRole('button', {name: '播放 First', exact: true}).click();
  await page.getByText('First line', {exact: true}).waitFor();
  await page.getByRole('button',{name:'暂停',exact:true}).first().click();
  await page.getByRole('button',{name:'队列下移 1',exact:true}).click();
  assert.equal(await page.locator('.now-song strong').innerText(),'First','Current song must survive queue reorder');
  await page.getByRole('button',{name:'移除队列 1',exact:true}).click();
  assert.equal(await page.locator('.queue-entry').count(),1);
  assert.equal(await page.locator('.now-song strong').innerText(),'First');
  const ttml=path.join(temporary,'vocal.ttml');await fs.writeFile(ttml,'<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div><p begin="00:00.000" end="00:04.000"><span begin="00:00.000" end="00:02.000">Hello</span><span ttm:role="x-translation">你好</span><span ttm:role="x-roman">ni hao</span><span ttm:role="x-bg" begin="00:01.000" end="00:03.000">Background</span></p></div></body></tt>');
  await application.evaluate(({dialog},file)=>{dialog.showOpenDialog=async()=>({canceled:false,filePaths:[file]});},ttml);
  await page.getByRole('button',{name:'First 更多操作',exact:true}).click();await page.getByRole('button',{name:'导入同步歌词',exact:true}).click();await page.getByText('Hello',{exact:true}).waitFor();
  assert.equal(await page.locator('.lyric-translation').innerText(),'你好');assert.equal(await page.locator('.lyric-romanization').innerText(),'ni hao');assert.equal(await page.locator('.background-vocal').innerText(),'Background');
  // Download through actual native IPC, disconnect the server, then use the media protocol.
  server=http.createServer((req,res)=>{
    const url=new URL(req.url,'http://fixture');const json=body=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify({'subsonic-response':{status:'ok',...body}}));};
    if(url.pathname==='/rest/search3.view')return json({searchResult3:{song:[{id:'中文?#',title:'Offline Download',artist:'Fixture',album:'Album',duration:12,suffix:'wav',path:'remote.wav',coverArt:'fixture-cover'}]}});
    if(url.pathname==='/rest/getLyricsBySongId.view')return json({lyricsList:{structuredLyrics:[{synced:true,line:[{start:0,value:'Native server lyric'}]}]}});
    if(url.pathname==='/rest/getCoverArt.view'){res.setHeader('Content-Type','image/png');res.end(Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNoSDjwHwAFpAKgaAA9lwAAAABJRU5ErkJggg==','base64'));return;}
    if(!['/rest/download.view','/rest/stream.view'].includes(url.pathname) || url.searchParams.get('id')!=='中文?#'){res.writeHead(404);res.end();return;}
    res.setHeader('Content-Type','audio/wav');res.end(wav);
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const sourceId=randomUUID();
  await page.evaluate(args=>window.halcyon.invoke('sources.save',args),{id:sourceId,type:'subsonic',name:'Test download',url:`http://127.0.0.1:${server.address().port}/`,username:'',password:''});
  await page.reload();await page.waitForFunction(()=>{const button=document.querySelector('button.primary');return button&&!button.disabled;});await page.getByRole('button',{name:'导入音乐',exact:true}).waitFor();
  await page.locator('.navigation').getByRole('button',{name:'远程曲库',exact:true}).click();await page.getByRole('button',{name:'刷新曲库',exact:true}).click();
  await page.getByRole('status').filter({hasText:'已加载 1 首歌曲'}).waitFor();
  await page.locator('.navigation').getByRole('button',{name:'音乐库',exact:true}).click();await page.getByRole('button',{name:'播放 Offline Download',exact:true}).click();
  await page.getByRole('button',{name:'暂停',exact:true}).first().waitFor();await page.waitForFunction(()=>Number(document.querySelector('[aria-label="播放进度"]').value)>.5);
  await page.getByRole('button',{name:'暂停',exact:true}).first().click();
  await page.getByRole('button',{name:'Offline Download 更多操作',exact:true}).click();await page.getByRole('button',{name:'获取服务器封面与歌词',exact:true}).click();
  await page.getByText('Native server lyric',{exact:true}).waitFor();
  await page.waitForFunction(()=>document.querySelector('.now-playing .cover img')?.src.startsWith('data:image/jpeg'));
  await page.getByRole('button',{name:'下载到离线曲库',exact:true}).click();await page.getByRole('status').filter({hasText:'已下载，可断网播放'}).waitFor();
  await page.getByRole('button',{name:'关闭',exact:true}).click();
  const cached=(await page.evaluate(()=>window.halcyon.invoke('library.list'))).find(song=>song.id===sourceId+':中文?#');
  assert.equal(cached.offline,true);
  await persisted(state=>state.remoteSongs.some(song=>song.id===cached.id)&&state.edits[cached.id]?.lyrics?.includes('Native server lyric'));
  server.closeAllConnections();await new Promise(resolve=>server.close(resolve));server=undefined;
  await page.reload();await page.waitForFunction(()=>{const button=document.querySelector('button.primary');return button&&!button.disabled;});await page.getByRole('button',{name:'播放 Offline Download',exact:true}).waitFor();await page.getByRole('button',{name:'播放 Offline Download',exact:true}).click();
  await page.getByRole('button',{name:'暂停',exact:true}).first().waitFor();await page.waitForFunction(()=>Number(document.querySelector('[aria-label="播放进度"]').value)>.5);
  // Removing a downloaded remote song must update BOTH library stores.
  await page.getByRole('button',{name:'暂停',exact:true}).first().click();
  const cachedPath = JSON.parse(await fs.readFile(path.join(profile,'library.json'),'utf8')).find(row=>row.song.id===cached.id)?.path;
  await page.getByRole('button',{name:'Offline Download 更多操作',exact:true}).click();
  await page.getByRole('button',{name:'从曲库移除',exact:true}).click();
  await page.getByRole('button',{name:'播放 Offline Download',exact:true}).waitFor({state:'detached'});
  await persisted(state=>!state.remoteSongs.some(song=>song.id===cached.id));
  await page.reload();await page.waitForFunction(()=>{const button=document.querySelector('button.primary');return button&&!button.disabled;});await page.getByRole('button',{name:'播放 First',exact:true}).waitFor();
  assert.equal(await page.getByRole('button',{name:'播放 Offline Download',exact:true}).count(),0);
  assert.ok(await fs.stat(cachedPath),'Removing a library record must retain the audio copy');
  const navigate=async name=>page.locator('.navigation').getByRole('button',{name,exact:true}).click();
  const openSong=async()=>page.getByRole('button',{name:'First 更多操作',exact:true}).click();
  const selectFile=async file=>application.evaluate(({dialog},file)=>{dialog.showOpenDialog=async()=>({canceled:false,filePaths:[file]});},file);
  const exportTo=async file=>application.evaluate(({dialog},file)=>{dialog.showSaveDialog=async()=>({canceled:false,filePath:file});},file);
  // Native folder import, duplicate import, groups and filters.
  await application.evaluate(({dialog},folder)=>{dialog.showOpenDialog=async()=>({canceled:false,filePaths:[folder]});},music);
  await page.getByRole('button',{name:'导入文件夹',exact:true}).click();
  await page.waitForFunction(()=>{const button=document.querySelector('button.primary');return button&&!button.disabled;});
  assert.equal(await page.locator('.song-row').count(),2);
  await page.getByPlaceholder('搜索歌曲、艺术家、专辑').fill('Second');
  await page.waitForFunction(()=>document.querySelectorAll('.song-row').length===1);
  await page.getByPlaceholder('搜索歌曲、艺术家、专辑').fill('');
  for(const group of ['专辑','艺术家','文件夹']){
    await page.locator('.library-tabs').getByRole('button',{name:group,exact:true}).click();
    assert.ok(await page.locator('.group-list button').count()>0);await page.locator('.group-list button').first().click();
    assert.equal(await page.locator('.song-row').count(),2);
  }
  await page.locator('.library-tabs').getByRole('button',{name:'歌曲',exact:true}).click();
  await page.getByLabel('曲库排序',{exact:true}).selectOption('title');
  const firstTitle=await page.locator('.song-row strong').first().innerText();
  await page.getByRole('button',{name:'倒序排序',exact:true}).click();
  await page.waitForFunction(title=>document.querySelector('.song-row strong')?.textContent!==title,firstTitle);
  await page.getByLabel('音源筛选',{exact:true}).selectOption('offline');
  await page.waitForFunction(()=>document.querySelectorAll('.song-row').length===0);
  await page.getByLabel('音源筛选',{exact:true}).selectOption('all');
  await page.getByLabel('曲库排序',{exact:true}).selectOption('default');
  await page.getByRole('button',{name:'倒序排序',exact:true}).click();
  // Playlist membership, order, rename, both export formats, import and removal.
  await navigate('歌单');await page.getByRole('button',{name:'新建歌单',exact:true}).click();
  await page.getByLabel('歌单名称',{exact:true}).fill('Integration Playlist');await page.getByRole('button',{name:'创建',exact:true}).click();
  await navigate('音乐库');for(const title of ['First','Second']){
    await page.getByRole('button',{name:`${title} 更多操作`,exact:true}).click();
    await page.getByLabel('加入歌单').selectOption({label:'Integration Playlist'});
  }
  await navigate('歌单');await page.getByRole('button',{name:'Integration Playlist 2 首歌曲',exact:true}).click();
  await page.getByRole('button',{name:'歌单下移 First',exact:true}).click();assert.equal(await page.locator('.song-row strong').first().innerText(),'Second');
  await page.getByRole('button',{name:'重命名',exact:true}).click();await page.getByLabel('歌单名称',{exact:true}).fill('Renamed Playlist');await page.getByRole('button',{name:'保存名称',exact:true}).click();
  const jsonFile=path.join(temporary,'playlist.json');await exportTo(jsonFile);await page.getByRole('button',{name:'导出歌单',exact:true}).click();
  const exported=JSON.parse(await fs.readFile(jsonFile,'utf8'));assert.equal(exported.schema,'halcyon-playlist-v1');assert.equal(exported.songs.length,2);
  const m3u=path.join(temporary,'playlist.m3u');await exportTo(m3u);await page.getByRole('button',{name:'导出 M3U',exact:true}).click();assert.match(await fs.readFile(m3u,'utf8'),/#EXTM3U/);
  await page.getByRole('button',{name:'从歌单移除 Second',exact:true}).click();assert.equal(await page.locator('.song-row').count(),1);
  await page.getByRole('button',{name:'返回歌单',exact:true}).click();await selectFile(jsonFile);await page.getByRole('button',{name:'导入 JSON / M3U 歌单',exact:true}).click();
  await page.getByRole('button',{name:'Renamed Playlist 2 首歌曲',exact:true}).waitFor();
  // Edit lyrics, undo/redo, save real sidecar, and export an SVG card.
  await navigate('音乐库');await openSong();await page.getByRole('button',{name:'歌词打轴器',exact:true}).click();
  // Padding is part of the dialog; a keyboard-induced move during a touch must not dismiss it.
  const timingDialog=page.getByRole('dialog');const dialogBox=await timingDialog.boundingBox();
  await page.mouse.click(dialogBox.x+4,dialogBox.y+4);assert.ok(await timingDialog.isVisible());
  const headingBox=await page.getByRole('heading',{name:'歌词打轴器',exact:true}).boundingBox();
  await page.mouse.move(headingBox.x+headingBox.width/2,headingBox.y+headingBox.height/2);await page.mouse.down();
  await timingDialog.evaluate(element=>{element.style.transform='translateY(80px)';});await page.mouse.up();
  assert.ok(await timingDialog.isVisible(),'A press inside the dialog survives layout movement');
  await timingDialog.evaluate(element=>{element.style.transform='';});
  await page.mouse.click(4,4);await timingDialog.waitFor({state:'hidden'});
  await openSong();await page.getByRole('button',{name:'歌词打轴器',exact:true}).click();
  await page.getByLabel('歌词文本',{exact:true}).fill('Timed first\nTimed second');await page.getByRole('button',{name:'应用文本',exact:true}).click();
  await page.getByLabel('第 1 行时间',{exact:true}).fill('1.25');await page.getByRole('button',{name:'撤销',exact:true}).click();assert.equal(await page.getByLabel('第 1 行时间',{exact:true}).inputValue(),'0');
  await page.getByRole('button',{name:'重做',exact:true}).click();assert.equal(await page.getByLabel('第 1 行时间',{exact:true}).inputValue(),'1.25');await page.getByRole('button',{name:'保存同步歌词',exact:true}).click();
  await openSong();await page.getByRole('button',{name:'保存同名外置歌词',exact:true}).click();assert.match(await fs.readFile(files[0].replace(/\.wav$/,'.lrc'),'utf8'),/Timed first/);
  const card=path.join(temporary,'card.svg');await exportTo(card);await page.getByRole('button',{name:'分享歌词卡片（SVG）',exact:true}).click();assert.match(await fs.readFile(card,'utf8'),/<svg/);
  await page.getByRole('button',{name:'关闭',exact:true}).click();
  // Imported cover and metadata edits survive a real application restart.
  const cover=path.join(temporary,'cover.png');await fs.writeFile(cover,Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNoSDjwHwAFpAKgaAA9lwAAAABJRU5ErkJggg==','base64'));
  await openSong();await selectFile(cover);await page.getByRole('button',{name:'设置歌曲封面',exact:true}).click();await persisted(s=>Object.values(s.edits).some(e=>e.artwork?.startsWith('data:image/png')));
  await page.getByRole('button',{name:'编辑显示信息',exact:true}).click();await page.getByLabel('歌曲名',{exact:true}).fill('Edited First');await page.getByRole('button',{name:'保存',exact:true}).click();
  await page.getByRole('button',{name:'播放 Edited First',exact:true}).waitFor();
  await navigate('设置');await page.getByLabel('外观',{exact:true}).selectOption('dark');await page.getByLabel('睡眠定时',{exact:true}).selectOption('-1');
  await persisted(s=>s.settings.theme==='dark'&&s.settings.sleepMinutes===-1);
  const backup=path.join(temporary,'backup.json');await exportTo(backup);await page.getByRole('button',{name:'导出应用数据',exact:true}).click();
  const backed=JSON.parse(await fs.readFile(backup,'utf8'));assert.equal(backed.playlists.length,2);assert.ok(!JSON.stringify(backed).includes('fixture-secret'));
  await page.getByLabel('外观',{exact:true}).selectOption('light');await selectFile(backup);await page.getByRole('button',{name:'恢复应用数据',exact:true}).click();
  await page.waitForFunction(()=>document.documentElement.dataset.theme==='dark');
  await persisted(s=>s.settings.theme==='dark'&&s.settings.sleepMinutes===0);
  // Terminate Electron rather than merely reloading its renderer.
  await application.close();application=await launch();
  page=await application.firstWindow();page.setDefaultTimeout(15000);page.on('pageerror',error=>errors.push(error.message));
  await page.getByRole('button',{name:'播放 Edited First',exact:true}).waitFor();assert.equal(await page.locator('html').getAttribute('data-theme'),'dark');
  await page.locator('.navigation').getByRole('button',{name:'歌单',exact:true}).click();assert.equal(await page.locator('.group-list > button').count(),2);
  await page.locator('.navigation').getByRole('button',{name:'音乐库',exact:true}).click();
  await page.getByRole('button',{name:'播放 Edited First',exact:true}).click();await page.getByRole('button',{name:'暂停',exact:true}).first().waitFor();
  // Real seek must not fabricate play counts; actual listening does count once.
  await page.locator('[aria-label="播放进度"]').evaluate(input=>{const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;setter.call(input,'8');input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));});
  await page.waitForFunction(()=>Number(document.querySelector('[aria-label="播放进度"]').value)>7);
  await page.locator('[aria-label="播放进度"]').evaluate(input=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,'0');input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));});
  await persisted(s=>s.history.length>0);
  await page.getByRole('button',{name:'暂停',exact:true}).first().click();
  await page.locator('.navigation').getByRole('button',{name:'最近播放',exact:true}).click();
  await page.getByRole('button',{name:'删除播放记录 Edited First',exact:true}).click();
  await page.getByRole('button',{name:'删除播放记录 Edited First',exact:true}).waitFor({state:'detached'});
  // Native video association and registered video byte-range playback.
  const video=path.join(temporary,'fixture.webm');
  const encoded=spawnSync('ffmpeg',['-y','-f','lavfi','-i','color=c=purple:s=160x90:d=2','-c:v','libvpx','-an',video],{stdio:'pipe'});
  if(encoded.error)throw encoded.error;assert.equal(encoded.status,0,encoded.stderr.toString());
  await page.locator('.navigation').getByRole('button',{name:'音乐库',exact:true}).click();
  await page.getByRole('button',{name:'Edited First 更多操作',exact:true}).click();await selectFile(video);await page.getByRole('button',{name:'关联本地 MV',exact:true}).click();
  await page.getByRole('button',{name:'播放关联 MV',exact:true}).click();await page.waitForFunction(()=>document.querySelector('video')?.currentTime>.2);
  assert.equal(await page.locator('video').evaluate(v=>v.videoWidth),160);await page.getByRole('button',{name:'关闭',exact:true}).click();
  // A missing original file fails visibly and leaves the interface operable.
  await fs.rename(files[1],files[1]+'.moved');await page.getByRole('button',{name:'播放 Second',exact:true}).click();
  await page.getByRole('status').filter({hasText:'无法解码或读取音频'}).waitFor();
  await page.getByRole('button',{name:'播放 Edited First',exact:true}).click();await page.getByRole('button',{name:'暂停',exact:true}).first().waitFor();
  assert.deepEqual(errors, []);
  console.log('PASS: Electron import/dedup/groups/search/filter; audible playback/EQ/rate/seek; paused skip/queue; favorites; playlist membership/order/rename/JSON/M3U; TTML; lyric editing/sidecar/card; cover/metadata; backup/restore; full process restart; listening history/removal; native server refresh/stream/cover/lyrics/download; offline playback/removal; native MV; missing-file recovery');
} catch (error) {
  if (page) console.error('Smoke failure interface:', (await page.locator('body').innerText()).slice(-4000));
  if (page) console.error('Smoke failure native state:', await page.evaluate(() => window.halcyon.invoke('state.load')));
  throw error;
} finally {if(server){server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}if (application) await application.close(); await fs.rm(temporary, {recursive: true, force: true});}
