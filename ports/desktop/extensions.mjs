import fs from 'node:fs/promises';
import path from 'node:path';
import {createWriteStream} from 'node:fs';
import {Readable, Transform} from 'node:stream';
import {pipeline} from 'node:stream/promises';
import {createHash} from 'node:crypto';
import {authenticatedRequest, streamRequest} from './remote.mjs';
export async function remoteAsset(source, args) {
 const {url,headers}=authenticatedRequest(source,args.path,args.query);
 const response=await fetch(url,{headers,redirect:'error',signal:AbortSignal.timeout(30000)});
 if(!response.ok)throw new Error(`封面服务器返回 HTTP ${response.status}`);
 const type=response.headers.get('content-type')?.split(';')[0];
 if(!['image/jpeg','image/png','image/webp','image/gif'].includes(type))throw new Error('服务器未返回支持的封面图片');
 const chunks=[];let size=0;for await(const chunk of response.body){size+=chunk.length;if(size>4000000)throw new Error('封面超过 4 MB');chunks.push(chunk);}
 return `data:${type};base64,${Buffer.concat(chunks).toString('base64')}`;
}
export async function downloadSong(library,directory,source,song,signal) {
 if(!song.id || song.sourceId!==source.id)throw new Error('无效歌曲来源');
 const hash=createHash('sha256').update(song.id).digest('hex');
 const extension=String(song.format || path.extname(song.remotePath || '').slice(1) || 'mp3').toLowerCase();
 if(!/^[a-z0-9]{1,8}$/.test(extension))throw new Error('无效音频格式');
 const destination=path.join(directory,'Downloads',hash,`audio.${extension}`); const temporary=destination+'.part';
 await fs.mkdir(path.dirname(destination),{recursive:true});
 const {url,headers}=streamRequest(source,song); if(source.type==='subsonic'){url.pathname=url.pathname.replace(/stream\.view$/,'download.view');}
 try {
  const response=await fetch(url,{headers,redirect:'error',signal:AbortSignal.any([signal,AbortSignal.timeout(300000)])});
  if(!response.ok)throw new Error(`下载失败 (HTTP ${response.status})`);
  if(Number(response.headers.get('content-length'))>2147483648)throw new Error('单曲下载上限 2 GB');
  let size=0;const limiter=new Transform({transform(chunk,encoding,callback){size+=chunk.length;callback(size>2147483648?new Error('单曲下载上限 2 GB'):null,chunk);}});
  await pipeline(Readable.fromWeb(response.body),limiter,createWriteStream(temporary,{mode:0o600}),{signal});
  if(!size)throw new Error('服务器返回了空音频'); await fs.rename(temporary,destination);
  const imported=await library.scan([destination]);const parsed=imported.songs[0];if(!parsed)throw new Error('下载文件无法导入');
  library.entries.delete(parsed.id);
  const cached={...parsed,...song,duration:parsed.duration || song.duration,size,offline:true};
  for(const key of ["sampleRate","bitDepth","bitrate","replayGain"])if(parsed[key]!==undefined)cached[key]=parsed[key];
  library.entries.set(song.id,{song:cached,path:destination});await library.persist();return cached;
 }catch(error){await fs.rm(temporary,{force:true});throw error;}
}
export async function saveLyrics(library,args) {
 if(typeof args.text!=='string' || args.text.length>2000000)throw new Error('无效歌词内容');
 const file=library.path(args.id);const suffix=args.text.trim().startsWith('<')?'.ttml':'.lrc';
 const target=file.slice(0,-path.extname(file).length)+suffix;
 await fs.writeFile(target+'.halcyon-tmp',args.text,{mode:0o600});await fs.rename(target+'.halcyon-tmp',target);
 const row=library.entries.get(args.id);row.song.lyrics=args.text;await library.persist();return true;
}

export async function aiRequest(provider,prompt) {
 if(provider.type!=='ai' || typeof prompt!=='string' || Buffer.byteLength(prompt)>300000 || !provider.model || !provider.password)throw new Error('请检查 AI 模型、密钥和请求内容');
 const anthropic=provider.protocol==='anthropic';const target=authenticatedRequest(provider,anthropic?'messages':'chat/completions').url;
 const body={model:provider.model,messages:[{role:'user',content:prompt}],max_tokens:1800,stream:false};
 const headers={'Content-Type':'application/json',...(anthropic?{'x-api-key':provider.password,'anthropic-version':'2023-06-01'}:{Authorization:`Bearer ${provider.password}`})};
 const response=await fetch(target,{method:'POST',headers,body:JSON.stringify(body),redirect:'error',signal:AbortSignal.timeout(90000)});
 if(!response.ok)throw new Error(`AI 服务返回 HTTP ${response.status}`);
 const chunks=[];let size=0;for await(const chunk of response.body){size+=chunk.length;if(size>16000000)throw new Error('AI 响应超过 16 MB');chunks.push(chunk);}
 const data=JSON.parse(Buffer.concat(chunks).toString('utf8'));
 const text=anthropic?(data.content || []).filter(x=>x.type==='text').map(x=>x.text).join('\n'):data.choices?.[0]?.message?.content;
 if(typeof text!=='string' || !text.trim())throw new Error('AI 服务未返回文本');return text;
}
