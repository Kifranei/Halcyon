import {host, invoke} from './bridge.ts';
import {nextIndex, moveItem, movedIndex, eqFrequencies, emptyState, type Song, type Repeat, type Settings} from './model.ts';
export interface Playback {queue: Song[]; index: number; playing: boolean; position: number; duration: number; shuffle: boolean; repeat: Repeat; error: string; rate: number}
class Player {
  snapshot: Playback = {queue: [], index: 0, playing: false, position: 0, duration: 0, shuffle: false, repeat: 'off', error: '', rate: 1};
  audio = new Audio(); listeners = new Set<() => void>(); generation = 0; volume = 1;
  settings = emptyState().settings; context?: AudioContext; filters: BiquadFilterNode[] = []; gain?: GainNode; analyser?: AnalyserNode;
  private timer: ReturnType<typeof setTimeout> | undefined; private finish = false;
  subscribe = (listener: () => void) => {this.listeners.add(listener); return () => {this.listeners.delete(listener);};};
  getSnapshot = () => this.snapshot;
  update(value: Partial<Playback>) {this.snapshot = {...this.snapshot, ...value}; this.listeners.forEach(listener => listener());}
  constructor() {
    this.audio.crossOrigin = 'anonymous';
    if (host.platform === 'ios') host.onEvent(event => {
      if (event.type === 'playback') this.update({index: Number(event.index) || 0, playing: event.playing === true, position: Number(event.position) || 0, duration: Number(event.duration) || 0, rate: Number(event.rate) || this.settings.rate, error: String(event.error || '')});
    });
    else {
      this.audio.addEventListener('timeupdate', () => this.update({position: this.audio.currentTime}));
      this.audio.addEventListener('durationchange', () => this.update({duration: Number.isFinite(this.audio.duration) ? this.audio.duration : 0}));
      this.audio.addEventListener('play', () => this.update({playing: true}));
      this.audio.addEventListener('pause', () => this.update({playing: false}));
      this.audio.addEventListener('ended', () => {if (this.finish) {this.finish = false; this.update({playing: false});} else if (this.snapshot.repeat === 'one') {this.audio.currentTime = 0; void this.audio.play().catch(e => this.fail(e));} else void this.skip(1, true);});
      this.audio.addEventListener('error', () => this.update({playing: false, error: '无法解码或读取音频，请检查文件、格式或服务器连接'}));
      if ('mediaSession' in navigator) {
        navigator.mediaSession.setActionHandler('play', () => void this.toggle(true)); navigator.mediaSession.setActionHandler('pause', () => void this.toggle(false));
        navigator.mediaSession.setActionHandler('nexttrack', () => void this.skip(1)); navigator.mediaSession.setActionHandler('previoustrack', () => void this.skip(-1));
        navigator.mediaSession.setActionHandler('seekto', event => void this.seek(event.seekTime || 0));
      }
    }
  }
  private async graph() {
    if (!this.context) {
      this.context = new AudioContext(); const source = this.context.createMediaElementSource(this.audio);
      this.filters = eqFrequencies.map(frequency => {const band = this.context!.createBiquadFilter(); band.type = 'peaking'; band.frequency.value = frequency; band.Q.value = 1.4; return band;});
      this.gain = this.context.createGain(); this.analyser = this.context.createAnalyser(); this.analyser.fftSize = 256;
      let node: AudioNode = source; for (const band of this.filters) {node.connect(band); node = band;}
      node.connect(this.gain); this.gain.connect(this.analyser); this.analyser.connect(this.context.destination);
      this.applyEffects();
    }
    if (this.context.state === 'suspended') await this.context.resume();
  }
  private applyEffects() {
    this.filters.forEach((band, n) => band.gain.value = this.settings.eqEnabled ? this.settings.eq[n] : 0);
    const song = this.snapshot.queue[this.snapshot.index];
    if (this.gain) this.gain.gain.value = Math.pow(10, ((this.settings.eqEnabled ? this.settings.preamp : 0) + (this.settings.replayGain ? song?.replayGain || 0 : 0)) / 20);
  }
  async configure(settings: Settings) {
    this.settings = settings; this.update({rate: settings.rate});
    if (host.platform === 'ios') await invoke('player.effects', settings);
    else {this.audio.playbackRate = settings.rate; this.audio.preservesPitch = settings.preservePitch; this.applyEffects();}
  }
  fail(error: unknown) {
    const message = error instanceof DOMException && ['NotSupportedError', 'NetworkError'].includes(error.name)
      ? '无法解码或读取音频，请检查文件、格式或服务器连接'
      : error instanceof Error ? error.message : String(error);
    this.update({error: message, playing: false});
  }
  async play(queue: Song[], index = 0, autoplay = true) {
    const generation = ++this.generation; this.audio.pause();
    if (!queue.length) {this.update({queue: [], index: 0, position: 0, duration: 0, playing: false}); this.audio.removeAttribute('src'); this.audio.load(); if (host.platform === 'ios') await invoke('player.stop'); return;}
    index = Math.max(0, Math.min(queue.length - 1, index)); const song = queue[index];
    this.update({queue, index, position: 0, duration: song.duration, playing: false, error: ''});
    try {
      if (host.platform === 'ios') await invoke('player.queue', {queue, index, autoplay, shuffle: this.snapshot.shuffle, repeat: this.snapshot.repeat, volume: this.volume});
      else {
        const url = await invoke<string>('media.resolve', song); if (generation !== this.generation) return;
        if (this.audio.src.startsWith('blob:')) URL.revokeObjectURL(this.audio.src);
        this.audio.src = url; this.audio.playbackRate = this.settings.rate; this.audio.preservesPitch = this.settings.preservePitch; this.applyEffects();
        if ('mediaSession' in navigator) navigator.mediaSession.metadata = new MediaMetadata({title: song.title, artist: song.artist, album: song.album, artwork: song.artwork ? [{src: song.artwork}] : []});
        if (autoplay) {await this.graph(); if (generation === this.generation) await this.audio.play();}
      }
    } catch (error) {if (generation === this.generation) this.fail(error);}
  }
  async toggle(force?: boolean) {
    if (!this.snapshot.queue.length) return; const playing = force ?? !this.snapshot.playing;
    try {if (host.platform === 'ios') await invoke('player.toggle', {playing}); else if (playing) {await this.graph(); await this.audio.play();} else this.audio.pause();} catch (error) {this.fail(error);}
  }
  async seek(position: number) {
    position = Math.max(0, Math.min(position, this.snapshot.duration || position));
    try {if (host.platform === 'ios') await invoke('player.seek', {position}); else this.audio.currentTime = position; this.update({position});} catch (error) {this.fail(error);}
  }
  async skip(direction: number, automatic = false) {
    if (direction < 0 && this.snapshot.position > 3) return this.seek(0);
    if (host.platform === 'ios') {try {await invoke('player.skip', {direction});} catch (e) {this.fail(e);} return;}
    const {queue, index, repeat, shuffle} = this.snapshot; const next = nextIndex(queue.length, index, direction, repeat, shuffle);
    if (next >= 0) await this.play(queue, next, automatic || this.snapshot.playing); else if (automatic) this.update({playing: false});
  }
  async modes(shuffle: boolean, repeat: Repeat) {this.update({shuffle, repeat}); if (host.platform === 'ios') await invoke('player.modes', {shuffle, repeat});}
  async setVolume(volume: number) {this.volume = volume; this.audio.volume = volume; if (host.platform === 'ios') await invoke('player.volume', {volume});}
  async sleep(minutes: number, finishTrack = false) {
    clearTimeout(this.timer); this.finish = minutes === -1;
    if (host.platform === 'ios') await invoke('player.sleep', {minutes, finishTrack});
    else if (minutes > 0) this.timer = setTimeout(() => {if (finishTrack && this.snapshot.playing) this.finish = true; else void this.toggle(false);}, minutes * 60000);
  }
  private async replace(queue: Song[], index: number) {
    this.update({queue, index}); if (host.platform === 'ios') {try {await invoke('player.editQueue', {queue, index});} catch (e) {this.fail(e);}}
  }
  async enqueue(song: Song, next = false) {
    if (!this.snapshot.queue.length) return this.play([song]);
    const queue = [...this.snapshot.queue]; queue.splice(next ? this.snapshot.index + 1 : queue.length, 0, song);
    await this.replace(queue, this.snapshot.index);
  }
  async move(from: number, to: number) {
    const {queue, index} = this.snapshot; const next = moveItem(queue, from, to); if (next === queue) return;
    await this.replace(next, movedIndex(index, from, to));
  }
  async remove(index: number) {
    const old = this.snapshot; const queue = old.queue.filter((_, n) => n !== index);
    if (index === old.index) {await this.play(queue, Math.min(index, queue.length - 1), old.playing); return;}
    await this.replace(queue, old.index - (index < old.index ? 1 : 0));
  }
  async refresh(song: Song) {
    const queue = this.snapshot.queue.map(item => item.id === song.id ? song : item);
    await this.replace(queue, this.snapshot.index);
  }
}
export const player = new Player();
