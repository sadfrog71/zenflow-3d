import { Asset } from 'expo-asset';
import { File } from 'expo-file-system';
import * as FS from 'expo-file-system/legacy';
import * as Crypto from 'expo-crypto';
import * as SecureStore from 'expo-secure-store';
import { requiredGardenFiles, type PublicAssetCatalog } from '@/core/zenflow/garden-resource-plan';
import manifest from '../../../../assets/zenflow/life-garden/manifest.json';
import source from './source.generated.json';
import { publicGardenAssets } from './assets.generated';
export { manifest };
type Progress=(loaded:number,total:number)=>void;
export type PreparedGarden={directory:string;catalog:PublicAssetCatalog;fallback:boolean};
const base=()=>{if(!FS.cacheDirectory)throw Error('PUBLIC_CACHE_UNAVAILABLE');return `${FS.cacheDirectory}invelle-public-garden/`;};
const trustKey=(revision:string)=>`invelle.public-garden.${revision}`;
let tail:Promise<unknown>=Promise.resolve();
function serial<T>(work:()=>Promise<T>):Promise<T>{const next=tail.catch(()=>{}).then(work);tail=next;return next;}
async function sha(bytes:Uint8Array<ArrayBuffer>){const result=await Crypto.digest(Crypto.CryptoDigestAlgorithm.SHA256,bytes);return Array.from(new Uint8Array(result),b=>b.toString(16).padStart(2,'0')).join('');}
async function valid(uri:string,hash:string){return (await FS.getInfoAsync(uri)).exists&&await sha(await new File(uri).bytes())===hash;}
async function catalogFromCache(directory:string,revision:string):Promise<PublicAssetCatalog>{
  const digest=await SecureStore.getItemAsync(trustKey(revision));
  if(!digest||!await valid(directory+'manifest.json',digest))throw Error('UNTRUSTED_PUBLIC_CATALOG');
  const cached=JSON.parse(await FS.readAsStringAsync(directory+'manifest.json')) as PublicAssetCatalog;
  if(cached.sceneVersion!==manifest.sceneVersion)throw Error('INCOMPATIBLE_SCENE_VERSION');
  return cached;
}
async function checkCached(directory:string,catalog:PublicAssetCatalog,nodeId:string,progress:Progress,level:string){
  const files=requiredGardenFiles(catalog,nodeId,level);let done=0;const total=files.reduce((s,f)=>s+f.bytes,0);
  for(const f of files){if(!await valid(directory+f.file,f.sha256))throw Error('PUBLIC_ASSET_NOT_CACHED');done+=f.bytes;progress(done,total);}
}
async function candidates(){const result:{revision:string;modified:number}[]=[];for(const name of await FS.readDirectoryAsync(base())){if(name===source.revision||! /^[a-f0-9]{16}$/.test(name))continue;const info=await FS.getInfoAsync(base()+name+'/verified.json');if(info.exists)result.push({revision:name,modified:info.modificationTime});}return result.sort((a,b)=>b.modified-a.modified);}
async function prepareCurrent(progress:Progress,nodeId:string,level:string):Promise<PreparedGarden>{
  const directory=base()+source.revision+'/';await FS.makeDirectoryAsync(directory,{intermediates:true});
  const files=requiredGardenFiles(manifest,nodeId,level);let done=0;const total=files.reduce((s,f)=>s+f.bytes,0);
  for(const entry of files){
    const uri=directory+entry.file;
    if(!await valid(uri,entry.sha256)){
      const temp=uri+'.pending';
      if(entry.file==='index.html')await FS.writeAsStringAsync(temp,source.html);
      else {const asset=Asset.fromModule(publicGardenAssets[entry.file]);await asset.downloadAsync();if(!asset.localUri)throw Error('PUBLIC_ASSET_UNAVAILABLE');await FS.copyAsync({from:asset.localUri,to:temp});}
      if(!await valid(temp,entry.sha256))throw Error('PUBLIC_ASSET_HASH_MISMATCH');await FS.moveAsync({from:temp,to:uri});
    }
    done+=entry.bytes;progress(done,total);
  }
  await FS.writeAsStringAsync(directory+'manifest.json',JSON.stringify(manifest));
  // Pin the public descriptor's digest in Keychain so corrupt cache metadata cannot bless itself.
  try{
    await SecureStore.setItemAsync(trustKey(source.revision),await sha(await new File(directory+'manifest.json').bytes()));
    await FS.writeAsStringAsync(directory+'verified.json',JSON.stringify({revision:source.revision,sceneVersion:manifest.sceneVersion}));
  }catch{
    // Current assets remain checked against the bundled manifest. Without a trusted
    // digest this directory must never become a previous-version fallback.
    await FS.deleteAsync(directory+'verified.json',{idempotent:true});
  }
  try{for(const old of (await candidates()).slice(1)){await FS.deleteAsync(base()+old.revision+'/',{idempotent:true});await SecureStore.deleteItemAsync(trustKey(old.revision));}}catch{/* Reclaiming rebuildable cache can wait; verified current files remain usable. */}
  return {directory,catalog:manifest,fallback:false};
}
export function preparePublicGarden(progress:Progress,nodeId='N00',level='WORLD_OVERVIEW'):Promise<PreparedGarden>{return serial(async()=>{
  try{return await prepareCurrent(progress,nodeId,level);}catch(error){
    if(!(await FS.getInfoAsync(base())).exists)throw error;
    for(const old of await candidates()){try{const directory=base()+old.revision+'/',catalog=await catalogFromCache(directory,old.revision);await checkCached(directory,catalog,nodeId,progress,level);return {directory,catalog,fallback:true};}catch{/* Try only a fully verified compatible previous region. */}}
    throw error;
  }
});}
export function ensurePublicGardenRegion(nodeId:string,prepared:PreparedGarden,progress:Progress,level='SPACE_OVERVIEW'):Promise<void>{return serial(async()=>{
  if(prepared.fallback){await checkCached(prepared.directory,prepared.catalog,nodeId,progress,level);return;}
  await prepareCurrent(progress,nodeId,level);
});}
