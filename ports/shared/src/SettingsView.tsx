import {eqFrequencies, type State, type Settings, type Song} from './model.ts';
import {host, invoke} from './bridge.ts';
export function SettingsView({state, setSettings, restore, notify, songs}: {state: State; setSettings: (patch: Partial<Settings>) => void; restore: (text: string) => void; notify: (message: string) => void; songs: Song[]}) {
  const listens = Object.values(state.counts).reduce((a, b) => a + b, 0);
  return <div className="settings-view">
    <h3>播放与显示</h3>
    <label>外观<select aria-label="外观" value={state.settings.theme} onChange={e => setSettings({theme: e.target.value as Settings['theme']})}><option value="system">跟随系统</option><option value="light">浅色</option><option value="dark">深色</option></select></label>
    <label>音量<input aria-label="音量" type="range" min="0" max="1" step="0.01" value={state.settings.volume} onChange={e => setSettings({volume: Number(e.target.value)})}/></label>
    <label>播放速度<select aria-label="播放速度" value={state.settings.rate} onChange={e => setSettings({rate: Number(e.target.value)})}>{[.5,.75,1,1.25,1.5,1.75,2,2.5,3].map(rate => <option value={rate} key={rate}>{rate}×</option>)}</select></label>
    <label>倍速保持音高<input type="checkbox" checked={state.settings.preservePitch} onChange={e => setSettings({preservePitch: e.target.checked})}/></label>
    <label>定时到点后播完当前歌曲<input type="checkbox" checked={state.settings.finishTrack} onChange={e => setSettings({finishTrack: e.target.checked})}/></label>
    <label>歌词字号<input type="range" min="14" max="44" value={state.settings.lyricSize} onChange={e => setSettings({lyricSize: Number(e.target.value)})}/></label>
    <label>显示翻译<input type="checkbox" checked={state.settings.showTranslation} onChange={e => setSettings({showTranslation: e.target.checked})}/></label>
    <label>显示罗马音<input type="checkbox" checked={state.settings.showRomanization} onChange={e => setSettings({showRomanization: e.target.checked})}/></label>
    <label>强调色<input type="color" value={state.settings.accent} onChange={e => setSettings({accent: e.target.value})}/></label>
    <label>动态播放背景<input type="checkbox" checked={state.settings.backdrop} onChange={e => setSettings({backdrop: e.target.checked})}/></label>
    <h3>软件均衡器</h3><label>启用 10 段 EQ<input type="checkbox" checked={state.settings.eqEnabled} onChange={e => setSettings({eqEnabled: e.target.checked})}/></label>
    <p className="muted">桌面端支持 EQ 与总增益。iOS 需使用本地或已下载音频，在线流媒体请先下载。</p>
    <div className="button-row">{[{name:'平直',eq:[0,0,0,0,0,0,0,0,0,0]},{name:'流行',eq:[-1,0,2,3,2,0,-1,-1,1,2]},{name:'摇滚',eq:[4,3,1,-1,-2,-1,1,3,4,3]},{name:'人声',eq:[-3,-2,-1,1,3,4,3,1,0,-1]}].map(preset => <button key={preset.name} onClick={() => setSettings({eq:preset.eq,eqEnabled:true,preamp:-Math.max(0,...preset.eq)})}>{preset.name}</button>)}</div>
    <div className="equalizer">{eqFrequencies.map((frequency, n) => <label key={frequency}>{frequency >= 1000 ? `${frequency/1000}k` : frequency} Hz<input aria-label={`EQ ${frequency} Hz`} type="range" min="-12" max="12" step=".5" value={state.settings.eq[n]} onChange={e => setSettings({eq: state.settings.eq.map((v,index) => index === n ? Number(e.target.value) : v)})}/><small>{state.settings.eq[n]} dB</small></label>)}</div>
    <label>EQ 总增益（dB）<input type="number" min="-24" max="6" step=".5" value={state.settings.preamp} onChange={e => setSettings({preamp: Number(e.target.value)})}/></label>
    {state.settings.eqEnabled && state.settings.preamp + Math.max(...state.settings.eq) > 0 ? <p className="muted">正增益可能削波，可降低总增益。</p> : null}
    <label>使用可用的 ReplayGain<input type="checkbox" checked={state.settings.replayGain} onChange={e => setSettings({replayGain:e.target.checked})}/></label>
    {host.platform === 'ios' ? <button onClick={() => void invoke('player.route').catch(e => notify(String(e)))}>AirPlay / 音频输出</button> : null}
    <label>歌词偏移（秒，正值延后）<input type="number" min="-600" max="600" step="0.1" value={state.settings.lyricOffset} onChange={e => setSettings({lyricOffset: Number(e.target.value)})}/></label>
    <label>睡眠定时<select aria-label="睡眠定时" value={state.settings.sleepMinutes} onChange={e => setSettings({sleepMinutes: Number(e.target.value)})}><option value="0">关闭</option><option value="-1">播完当前歌曲</option>{[15, 30, 45, 60, 90, 120].map(n => <option value={n} key={n}>{n} 分钟后暂停</option>)}</select></label>
    <h3>音乐库统计</h3><p>曲库 {songs.length} 首 · 收藏 {state.favorites.length} 首 · 歌单 {state.playlists.length} 个 · 已计次播放 {listens} 次</p>
    <p className="muted">累计实际播放达到歌曲一半或 30 秒时计一次；每次播放最多计一次。</p>
    <h3>数据备份</h3><div className="button-row"><button onClick={async () => {try {await invoke('text.export', {name: 'Halcyon-backup.json', text: JSON.stringify(state, null, 2)}); notify('备份导出操作完成');} catch(e) {notify(String(e));}}}>导出应用数据</button>
    <button onClick={async () => {try {const text = await invoke<string>('text.import'); if (text) restore(text);} catch(e) {notify(String(e));}}}>恢复应用数据</button></div>
    <p className="muted">备份包含收藏、歌单、播放记录、显示信息修改和设置。恢复会替换这些应用数据；不包含音频文件或服务器凭据。跨设备迁移后需重新导入音频并核对歌单。</p>
    <h3>关于</h3><p>Halcyon · {host.platform === 'ios' ? 'iOS' : host.platform === 'desktop' ? '桌面' : '网页预览'}移植版 0.2.0</p>
    {host.platform === 'browser' ? <p className="muted">网页预览中的音频只在本次会话可用；完整文件管理和远程曲库请使用桌面或 iOS 容器。</p> : null}
  </div>;
}
