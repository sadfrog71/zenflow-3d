import { Asset } from 'expo-asset';
import { requiredGardenFiles, type PublicAssetCatalog } from '@/core/zenflow/garden-resource-plan';
import manifest from '../../../../assets/zenflow/life-garden/manifest.json';
import source from './source.generated.json';
import { publicGardenAssets } from './assets.generated';
export { manifest };
type Progress=(loaded:number,total:number)=>void;
type BrowserGarden={directory:string;html:string;resources:Record<string,ArrayBuffer>;catalog:PublicAssetCatalog;fallback:boolean};
async function prepareRegion(nodeId:string,resources:Record<string,ArrayBuffer>,progress:Progress,level:string){
  const entries=requiredGardenFiles(manifest,nodeId,level).filter(f=>f.file!=='index.html'),total=entries.reduce((s,f)=>s+f.bytes,0);let done=0;
  for(const entry of entries){if(!resources[entry.file]){const response=await fetch(Asset.fromModule(publicGardenAssets[entry.file]).uri);if(!response.ok)throw Error('PUBLIC_ASSET_UNAVAILABLE');const bytes=await response.arrayBuffer(),hash=await crypto.subtle.digest('SHA-256',bytes);if(Array.from(new Uint8Array(hash),b=>b.toString(16).padStart(2,'0')).join('')!==entry.sha256)throw Error('PUBLIC_ASSET_HASH_MISMATCH');resources[entry.file]=bytes;}done+=entry.bytes;progress(done,total);}
}
/** Public-only memory adapter; private offline data never enters browser storage. */
export async function preparePublicGarden(progress:Progress,nodeId='N00',level='WORLD_OVERVIEW'):Promise<BrowserGarden>{const resources={};await prepareRegion(nodeId,resources,progress,level);return {directory:'',html:source.html,resources,catalog:manifest,fallback:false};}
export async function ensurePublicGardenRegion(nodeId:string,prepared:{resources?:Record<string,ArrayBuffer>},progress:Progress,level='SPACE_OVERVIEW'):Promise<void>{if(!prepared.resources)throw Error('PUBLIC_ASSET_UNAVAILABLE');await prepareRegion(nodeId,prepared.resources,progress,level);}
