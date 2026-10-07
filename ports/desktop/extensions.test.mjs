import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import {Library} from './library.mjs';
import {downloadSong, saveLyrics, remoteAsset, aiRequest} from './extensions.mjs';
function wav() {
 const b=Buffer.alloc(16044);b.write('RIFF');b.writeUInt32LE(b.length-8,4);b.write('WAVEfmt ',8);b.writeUInt32LE(16,16);b.writeUInt16LE(1,20);b.writeUInt16LE(1,22);b.writeUInt32LE(8000,24);b.writeUInt32LE(16000,28);b.writeUInt16LE(2,32);b.writeUInt16LE(16,34);b.write('data',36);b.writeUInt32LE(16000,40);return b;
}
async function listen(handler) {const server=http.createServer(handler);await new Promise(r=>server.listen(0,'127.0.0.1',r));return {server,url:`http://127.0.0.1:${server.address().port}/`};}
async function close(server){server.closeAllConnections();await new Promise(r=>server.close(r));}
test('authenticated download persists stable remote identity and plays locally after the server stops',async()=>{
 const directory=await fs.mkdtemp(path.join(os.tmpdir(),'halcyon-download-'));const audio=wav();let requested;
 const {server,url}=await listen((req,res)=>{requested=req;assert.equal(req.headers.authorization,'Basic '+Buffer.from('user:secret').toString('base64'));res.end(audio);});
 try{
  const lib=new Library(directory);await lib.load();const source={id:'source',type:'webdav',url,username:'user',password:'secret'};
  const song={id:'remote/song',sourceId:'source',remotePath:'track.wav',title:'Remote title',artist:'Remote artist',album:'Album',duration:999,format:'wav'};
  const cached=await downloadSong(lib,directory,source,song,new AbortController().signal);assert.equal(requested.url,'/track.wav');assert.equal(cached.id,song.id);assert.equal(cached.title,song.title);assert.equal(cached.duration,1);assert.equal(cached.offline,true);assert.equal(cached.sampleRate,8000);
  await close(server);const restored=new Library(directory);await restored.load();assert.deepEqual(await fs.readFile(restored.path(song.id)),audio);assert.equal(restored.list().length,1);
  await saveLyrics(restored,{id:song.id,text:'[00:00.000]离线歌词'});assert.equal(await fs.readFile(restored.path(song.id).replace(/\.wav$/,'.lrc'),'utf8'),'[00:00.000]离线歌词');
  assert.ok(!await fs.readFile(restored.file,'utf8').then(s=>s.includes('secret')));
  await assert.rejects(downloadSong(lib,directory,source,{...song,sourceId:'other'},new AbortController().signal),/来源/);
 }finally{if(server.listening)await close(server);await fs.rm(directory,{recursive:true,force:true});}
});
test('cancelled download cleans partial files without registering a song',async()=>{
 const directory=await fs.mkdtemp(path.join(os.tmpdir(),'halcyon-cancel-'));let began;const ready=new Promise(r=>began=r);
 const {server,url}=await listen((req,res)=>{res.write(wav());began();});
 try{const lib=new Library(directory);await lib.load();const controller=new AbortController();const task=downloadSong(lib,directory,{id:'s',type:'webdav',url},{id:'cancel',sourceId:'s',remotePath:'audio.wav',format:'wav'},controller.signal);await ready;controller.abort();await assert.rejects(task);assert.equal(lib.list().length,0);const folders=await fs.readdir(path.join(directory,'Downloads'));for(const folder of folders)assert.deepEqual(await fs.readdir(path.join(directory,'Downloads',folder)),[]);}
 finally{await close(server);await fs.rm(directory,{recursive:true,force:true});}
});
test('server images and both AI protocols use native auth and validate their responses',async()=>{
 const requests=[];const {server,url}=await listen(async(req,res)=>{
  if(req.url==='/cover.jpg'){res.setHeader('content-type','image/jpeg');res.end(Buffer.from([255,216,255,217]));return;}
  if(req.url==='/bad.svg'){res.setHeader('content-type','image/svg+xml');res.end('<svg/>');return;}
  const parts=[];for await(const chunk of req)parts.push(chunk);requests.push({headers:req.headers,path:req.url,body:JSON.parse(Buffer.concat(parts))});
  res.setHeader('content-type','application/json');res.end(JSON.stringify(req.url==='/messages'?{content:[{type:'text',text:'Native Claude'}]}:{choices:[{message:{content:'Native recommendation'}}]}));
 });
 try{
  assert.equal(await remoteAsset({type:'webdav',url},{path:'cover.jpg'}),'data:image/jpeg;base64,/9j/2Q==');await assert.rejects(remoteAsset({type:'webdav',url},{path:'bad.svg'}));
  const provider={type:'ai',url,model:'test-model',password:'fixture-key'};
  assert.equal(await aiRequest(provider,'recommend one song'),'Native recommendation');assert.equal(await aiRequest({...provider,protocol:'anthropic'},'explain lyrics'),'Native Claude');
  assert.equal(requests[0].headers.authorization,'Bearer fixture-key');assert.equal(requests[1].headers['x-api-key'],'fixture-key');assert.equal(requests[1].headers['anthropic-version'],'2023-06-01');assert.equal(requests[0].body.model,'test-model');assert.equal(requests[0].body.stream,false);assert.equal(requests[0].body.messages[0].content,'recommend one song');
  await assert.rejects(aiRequest({...provider,password:''},'request'));await assert.rejects(aiRequest(provider,'x'.repeat(300001)));
 }finally{await close(server);}
});
