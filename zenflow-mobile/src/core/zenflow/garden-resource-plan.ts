export type PublicAssetFile={file:string;bytes:number;sha256:string};
export type PublicAssetCatalog={schemaVersion:number;minBridgeProtocol:number;sceneVersion:string;files:PublicAssetFile[];overview?:{packageId:string;camera:string};packages:{id:string;files:string[];dependencies:string[]}[];viewpoints:{id:string;packageId:string|null;available:boolean}[]};
/** Resolve only the requested region. Catalog paths can never reach private app directories. */
export function requiredGardenFiles(catalog:PublicAssetCatalog,nodeId:string,level='FIRST_PERSON'):PublicAssetFile[]{
  const invalid=()=>{throw Error('INVALID_ASSET_CATALOG');};
  if(catalog.schemaVersion!==1||catalog.minBridgeProtocol!==1||typeof catalog.sceneVersion!=='string'||!Array.isArray(catalog.files)||!Array.isArray(catalog.packages)||!Array.isArray(catalog.viewpoints))invalid();
  const entries=new Map<string,PublicAssetFile>();
  for(const f of catalog.files){if(!f||! /^[a-zA-Z0-9][a-zA-Z0-9._-]*\.(glb|webp|html)$/.test(f.file)||! /^[a-f0-9]{64}$/.test(f.sha256)||!Number.isSafeInteger(f.bytes)||f.bytes<0||f.bytes>200*1024*1024||entries.has(f.file))invalid();entries.set(f.file,f);}
  const view=catalog.viewpoints.find(v=>v.id===nodeId&&v.available&&typeof v.packageId==='string');
  if(level!=='WORLD_OVERVIEW'&&!view)throw Error('VIEW_UNAVAILABLE');
  const selected=new Set(['index.html','probe.glb']),visited=new Set<string>(),visiting=new Set<string>();
  const packages=new Map(catalog.packages.map(p=>[p.id,p]));if(packages.size!==catalog.packages.length)invalid();
  function add(id:string){if(visiting.has(id))invalid();if(visited.has(id))return;const p=packages.get(id);if(!p||!Array.isArray(p.files)||!Array.isArray(p.dependencies))invalid();visiting.add(id);for(const dependency of p!.dependencies)add(dependency);for(const file of p!.files)selected.add(file);visiting.delete(id);visited.add(id);}
  if(level==='WORLD_OVERVIEW'){if(!catalog.overview)invalid();add(catalog.overview!.packageId);}else add(view!.packageId!);
  return [...selected].map(name=>{const entry=entries.get(name);if(!entry)invalid();return entry!;});
}
