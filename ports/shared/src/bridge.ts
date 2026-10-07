import { filenameMetadata, audioExtensions, type Song, type Source } from './model.ts';
export interface Host {
  platform: 'ios' | 'desktop' | 'browser';
  invoke<T = unknown>(method: string, args?: unknown): Promise<T>;
  onEvent(listener: (event: {type: string; [key: string]: unknown}) => void): () => void;
}
declare global { interface Window { halcyon?: Host } }
const browserFiles = new Map<string, File>();
const browserSources = new Map<string, Source & {password?: string; token?: string}>();
function choose(accept: string, multiple = false): Promise<File[]> {
  return new Promise(resolve => {
    const input = document.createElement('input'); input.type = 'file'; input.accept = accept; input.multiple = multiple;
    input.onchange = () => resolve(Array.from(input.files || [])); input.oncancel = () => resolve([]); input.click();
  });
}
export const host: Host = window.halcyon || {
  platform: 'browser', onEvent: () => () => {},
  async invoke<T>(method: string, raw?: unknown): Promise<T> {
    const args = (raw || {}) as Record<string, any>; let result: unknown;
    switch (method) {
      case 'state.load': result = JSON.parse(localStorage.getItem('halcyon-state') || 'null'); break;
      case 'state.save': localStorage.setItem('halcyon-state', JSON.stringify(args)); break;
      case 'library.list': result = []; break;
      case 'library.import': {
        const files = await choose([...audioExtensions].map(e => `.${e}`).join(','), true);
        result = files.map(file => {const id = crypto.randomUUID(); browserFiles.set(id, file); return {id, ...filenameMetadata(file.name), fileName: file.name, duration: 0};}); break;
      }
      case 'library.remove': browserFiles.delete(args.id); break;
      case 'media.resolve': {
        const file = browserFiles.get(args.id); if (!file) throw new Error('网页预览仅支持本次导入的文件');
        result = URL.createObjectURL(file); break;
      }
      case 'text.import': result = await (await choose('.lrc,.elrc,.ttml,.txt,.json,.m3u,.m3u8'))[0]?.text() || ''; break;
      case 'text.export': {
        const url = URL.createObjectURL(new Blob([args.text], {type: 'application/json'}));
        const anchor = document.createElement('a'); anchor.href = url; anchor.download = args.name; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); break;
      }
      case 'sources.list': result = [...browserSources.values()].map(({password, token, ...source}) => source); break;
      case 'sources.save': browserSources.set(args.id, args as Source); result = args; break;
      case 'sources.remove': browserSources.delete(args.id); break;
      default: throw new Error('该功能需要 iOS 或桌面客户端');
    }
    return result as T;
  }
};
export const invoke = <T = unknown>(method: string, args?: unknown) => host.invoke<T>(method, args);
export async function importSongs(folder = false): Promise<Song[]> {return invoke('library.import', {folder});}
