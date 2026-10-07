import { memo, useEffect, useRef, type ReactNode } from 'react';
import {time, type Song} from './model.ts';
const paths: Record<string, ReactNode> = {
  music: <><path d="M9 18V5l12-2v13M9 8l12-2"/><ellipse cx="6" cy="18" rx="3" ry="2"/><ellipse cx="18" cy="16" rx="3" ry="2"/></>,
  heart: <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z"/>,
  list: <><path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/></>,
  clock: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
  server: <><rect x="3" y="3" width="18" height="7" rx="2"/><rect x="3" y="14" width="18" height="7" rx="2"/><path d="M7 6.5h.01M7 17.5h.01"/></>,
  settings: <><circle cx="12" cy="12" r="3"/><path d="m9 3-1 3-3 1-2 3 2 2-1 3 3 2 3-1 2 2 3-2 1-3 3-1v-4l-3-1-1-3-3-1-1-2Z"/></>,
  search: <><circle cx="10.5" cy="10.5" r="7.5"/><path d="m16 16 5 5"/></>,
  plus: <path d="M12 5v14M5 12h14"/>,
  folder: <path d="M3 8V5h6l2 3h10v12H3V8h18"/>,
  more: <><circle cx="4" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="20" cy="12" r="1"/></>,
  play: <path d="m8 4 13 8-13 8Z"/>, pause: <><path d="M7 5v14M17 5v14"/></>,
  next: <><path d="m4 5 12 7-12 7ZM19 5v14"/></>, previous: <><path d="m20 5-12 7 12 7ZM5 5v14"/></>,
  shuffle: <><path d="M3 5h3l12 14h3M3 19h3L18 5h3M18 2l3 3-3 3M18 16l3 3-3 3"/></>,
  repeat: <><path d="m17 2 4 4-4 4M3 10V6h18M7 22l-4-4 4-4M21 14v4H3"/></>,
  volume: <><path d="M3 9h4l5-4v14l-5-4H3ZM16 8a6 6 0 0 1 0 8M19 5a10 10 0 0 1 0 14"/></>,
  close: <path d="m6 6 12 12M18 6 6 18"/>, back: <path d="m14 5-7 7 7 7"/>,
  album: <><rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1"/></>
};
export function Icon({name, size = 20}: {name: string; size?: number}) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name] || paths.music}</svg>;
}
export function IconButton({name, label, onClick, active, disabled}: {name: string; label: string; onClick: () => void; active?: boolean; disabled?: boolean}) {
  return <button className={`icon-button ${active ? 'active' : ''}`} aria-label={label} title={label} aria-pressed={active} onClick={onClick} disabled={disabled}><Icon name={name}/></button>;
}
export function Cover({song, large = false}: {song?: Song; large?: boolean}) {
  return <div className={`cover ${large ? 'large' : ''}`}>{song?.artwork?.startsWith('data:') ? <img alt="" src={song.artwork}/> : <Icon name="music" size={large ? 64 : 20}/>}</div>;
}
export const SongRow = memo(function SongRow({song, index, current, favorite, onPlay, onFavorite, onMore}: {song: Song; index: number; current: boolean; favorite: boolean; onPlay: () => void; onFavorite: () => void; onMore: () => void}) {
  return <div className={`song-row ${current ? 'current' : ''}`}>
    <span className="row-index">{current ? <Icon name="volume" size={14}/> : index + 1}</span>
    <button className="song-primary" onClick={onPlay} aria-label={`播放 ${song.title}`}><Cover song={song}/><span><strong>{song.title}</strong><small className="mobile-artist">{song.artist}</small></span></button>
    <span className="desktop-artist truncate">{song.artist}</span><span className="row-album truncate">{song.album}</span><span className="row-time">{time(song.duration)}</span>
    <IconButton name="heart" label={`${favorite ? '取消收藏' : '收藏'} ${song.title}`} active={favorite} onClick={onFavorite}/>
    <IconButton name="more" label={`${song.title} 更多操作`} onClick={onMore}/>
  </div>;
});
export function Modal({title, children, close}: {title: string; children: ReactNode; close: () => void}) {
  const ref = useRef<HTMLDialogElement>(null);
  const backdropPress = useRef(false);
  useEffect(() => {ref.current?.showModal();}, []);
  const outside = (dialog: HTMLDialogElement, x: number, y: number) => {
    const box = dialog.getBoundingClientRect();
    return x < box.left || x > box.right || y < box.top || y > box.bottom;
  };
  return <dialog ref={ref} onCancel={close}
    onPointerDown={event => {backdropPress.current = event.target === event.currentTarget && outside(event.currentTarget, event.clientX, event.clientY);}}
    onPointerCancel={() => {backdropPress.current = false;}}
    onClick={event => {
      const dismiss = backdropPress.current && event.target === event.currentTarget && outside(event.currentTarget, event.clientX, event.clientY);
      backdropPress.current = false;
      if (dismiss) close();
    }}>
    <div className="dialog-head"><h2>{title}</h2><IconButton name="close" label="关闭" onClick={close}/></div>{children}
  </dialog>;
}
