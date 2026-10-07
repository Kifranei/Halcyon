import {useSyncExternalStore} from 'react';
import {player} from './player.ts';
import {Cover} from './components.tsx';
import {time} from './model.ts';
export function QueueView() {
  const state = useSyncExternalStore(player.subscribe, player.getSnapshot);
  return <><div className="queue-heading"><h3>播放队列 · {state.queue.length}</h3><button className="link" onClick={() => void player.play([])}>清空</button></div>
    <div className="queue">{state.queue.map((entry, index) => <div className={`queue-entry ${index === state.index ? 'active' : ''}`} key={`${entry.id}-${index}`}>
      <button className="queue-track" onClick={() => void player.play(state.queue, index)}><span>{index + 1}</span><Cover song={entry}/><span className="truncate"><strong>{entry.title}</strong><small>{entry.artist}</small></span><span>{time(entry.duration)}</span></button>
      <div className="queue-actions"><button aria-label={`队列上移 ${index + 1}`} disabled={index === 0} onClick={() => void player.move(index, index - 1)}>↑</button><button aria-label={`队列下移 ${index + 1}`} disabled={index === state.queue.length - 1} onClick={() => void player.move(index, index + 1)}>↓</button><button aria-label={`移除队列 ${index + 1}`} onClick={() => void player.remove(index)}>×</button></div>
    </div>)}</div></>;
}
