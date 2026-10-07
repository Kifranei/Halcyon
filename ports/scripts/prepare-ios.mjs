import {cp, mkdir, rm} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const from = fileURLToPath(new URL('../shared/dist/', import.meta.url));
const to = fileURLToPath(new URL('../ios/Halcyon/Web/', import.meta.url));
await rm(to, {recursive: true, force: true}); await mkdir(to, {recursive: true});
await cp(from, to, {recursive: true});
console.log('iOS web assets prepared. Open ports/ios/Halcyon.xcodeproj in Xcode.');
