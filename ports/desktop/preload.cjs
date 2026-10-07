const {contextBridge, ipcRenderer} = require('electron');
contextBridge.exposeInMainWorld('halcyon', {
  platform: 'desktop',
  invoke: (method, args) => ipcRenderer.invoke('halcyon:invoke', method, args),
  onEvent: listener => {const handler = (_, event) => listener(event); ipcRenderer.on('halcyon:event', handler); return () => ipcRenderer.removeListener('halcyon:event', handler);}
});
