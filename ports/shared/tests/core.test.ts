import test from 'node:test';
import assert from 'node:assert/strict';
import {emptyState, restoreState, nextIndex, filterSongs, albumKey, type Song} from '../src/model.ts';
import {parseLRC, lyricIndex, clock} from '../src/lyrics.ts';
const song: Song = {id: '1', title: '夜曲', artist: '周杰伦', album: '十一月的萧邦', duration: 240};
test('LRC handles repeated timestamps, millisecond offset and word timing', () => {
  const lines = parseLRC('[offset:-100]\n[00:01.20][00:03.00]<00:01.20>夜<00:01.70>曲\n[00:10.000]下一句');
  assert.equal(lines.length, 3); assert.ok(Math.abs(lines[0].time - 1.1) < 1e-9);
  assert.ok(Math.abs(lines[1].words[0].time - 2.9) < 1e-9); assert.equal(lines[0].text, '夜曲'); assert.equal(lines[0].words.length, 2);
  assert.equal(lyricIndex(lines, 1), -1); assert.equal(lyricIndex(lines, 3), 1); assert.equal(lyricIndex(lines, 100), 2);
});
test('time parsing supports TTML clock and duration notation', () => {
  assert.equal(clock('01:02:03.25'), 3723.25); assert.equal(clock('1500ms'), 1.5); assert.equal(clock('2.5s'), 2.5);
});
test('queue boundaries stop, wrap, and explicit skip bypasses repeat-one', () => {
  assert.equal(nextIndex(0, 0, 1, 'all', false), -1);
  assert.equal(nextIndex(3, 2, 1, 'off', false), -1);
  assert.equal(nextIndex(3, 2, 1, 'all', false), 0);
  assert.equal(nextIndex(3, 1, 1, 'one', false), 2);
  for (const n of [0, 0.2, 0.99]) assert.notEqual(nextIndex(5, 2, 1, 'off', true, () => n), 2);
});
test('search matches across metadata and keeps same-name albums separate', () => {
  assert.deepEqual(filterSongs([song], '夜曲 周杰伦'), [song]); assert.equal(filterSongs([song], '未知').length, 0);
  assert.notEqual(albumKey(song), albumKey({...song, albumArtist: '另一位歌手'}));
});
test('backup restoration sanitizes settings, unsupported fields and credentials', () => {
  const restored = restoreState({...emptyState(), favorites: ['1', 42], settings: {volume: 99, theme: 'bad', lyricOffset: Infinity}, password: 'secret',
    edits: {'1': {title: '测试', password: 'secret'}}, remoteSongs: [{...song, sourceId: 's', password: 'secret', url: 'https://example.com/?password=secret'}]});
  assert.deepEqual(restored.favorites, ['1']); assert.equal(restored.settings.volume, 1); assert.equal(restored.settings.theme, 'system');
  assert.ok(!JSON.stringify(restored).includes('secret')); assert.deepEqual(restored.edits['1'], {title: '测试'});
  assert.throws(() => restoreState({version: 2}));
});

import {moveItem,movedIndex,sortSongs,mergeSongs} from '../src/model.ts';
import {activeLyrics,serializeLRC} from '../src/lyrics.ts';
test('moving a queue preserves the current item in every direction',()=>{
 const queue=['a','b','c','d'];for(let current=0;current<4;current++)for(let from=0;from<4;from++)for(let to=0;to<4;to++)assert.equal(moveItem(queue,from,to)[movedIndex(current,from,to)],queue[current]);
 assert.deepEqual(queue,['a','b','c','d']);assert.equal(moveItem(queue,-1,1),queue);
});
test('sorting and source filters keep cached remote tracks available',()=>{
 const local={...song,id:'local',format:'wav',duration:20};const online={...song,id:'online',sourceId:'s',format:'mp3',duration:30};const cached={...online,id:'cached',offline:true,duration:10};
 const settings={...emptyState().settings,sort:'duration'};assert.deepEqual(sortSongs([local,online,cached],settings,{}).map(s=>s.id),['cached','local','online']);
 assert.deepEqual(sortSongs([local,online,cached],{...settings,sourceFilter:'offline'},{}),[cached]);assert.deepEqual(sortSongs([local,online,cached],{...settings,sourceFilter:'local'},{}),[local]);
 assert.equal(mergeSongs([cached],[{...cached,offline:false}])[0].offline,true);
});
test('new effect settings restore safely and old backups retain sensible defaults',()=>{
 assert.equal(restoreState({version:1,settings:{volume:.5}}).settings.rate,1);
 const restored=restoreState({version:1,settings:{rate:99,preamp:-99,eq:Array(10).fill(100),lyricSize:1,sleepMinutes:-1,accent:'url(evil)'}});assert.equal(restored.settings.rate,3);assert.equal(restored.settings.preamp,-24);assert.equal(restored.settings.lyricSize,14);assert.deepEqual(restored.settings.eq,Array(10).fill(12));assert.equal(restored.settings.sleepMinutes,-1);assert.equal(restored.settings.accent,'#8065c9');
});
test('overlapping vocal lines remain active together and edited word timing roundtrips',()=>{
 const lines=parseLRC('[00:01.000][v1]<00:01.000>你<00:01.500>好\n[00:01.000][v2]和声\n[00:03.000][v1]下一句');assert.deepEqual([...activeLyrics(lines,1.7)],[0,1]);assert.equal(lines[0].words[0].end,1.5);
 const restored=parseLRC(serializeLRC(lines));assert.equal(restored[0].text,'你好');assert.equal(restored[0].words[1].time,1.5);assert.equal(clock('75f',25),3);assert.equal(clock('100t',30,50),2);
});
