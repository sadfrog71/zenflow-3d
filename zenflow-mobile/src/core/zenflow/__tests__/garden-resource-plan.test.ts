import { requiredGardenFiles } from '../garden-resource-plan';
const file=(name:string)=>({file:name,bytes:12,sha256:'a'.repeat(64)});
const catalog={schemaVersion:1,minBridgeProtocol:1,sceneVersion:'fixed',viewpoints:[{id:'N00',packageId:'veranda',available:true},{id:'N03',packageId:'tree',available:true}],packages:[{id:'textures',files:['texture.webp'],dependencies:[]},{id:'common',files:['common.glb'],dependencies:['textures']},{id:'veranda',files:['veranda.glb'],dependencies:['common']},{id:'tree',files:['tree.glb'],dependencies:['common']}],files:['index.html','probe.glb','texture.webp','common.glb','veranda.glb','tree.glb'].map(file)};
test('first region includes its dependencies but never forces the other region download',()=>{
 expect(requiredGardenFiles(catalog,'N00').map(x=>x.file).sort()).toEqual(['common.glb','index.html','probe.glb','texture.webp','veranda.glb']);
 expect(requiredGardenFiles(catalog,'N03').some(x=>x.file==='tree.glb')).toBe(true);
});
test('unavailable view and unsafe/cyclic resource catalogs fail before any file is opened',()=>{
 expect(()=>requiredGardenFiles(catalog,'N99')).toThrow('VIEW_UNAVAILABLE');
 expect(()=>requiredGardenFiles({...catalog,files:[...catalog.files,file('../private.db')]},'N00')).toThrow('INVALID_ASSET_CATALOG');
 expect(()=>requiredGardenFiles({...catalog,packages:[{id:'veranda',files:['veranda.glb'],dependencies:['veranda']}]},'N00')).toThrow('INVALID_ASSET_CATALOG');
});

test('world overview loads the map package without downloading a first-person region',()=>{
 const map={...catalog,overview:{packageId:'overview',camera:'CAM_LAYOUT_TOP'},packages:[...catalog.packages,{id:'overview',files:['overview.glb'],dependencies:['common']}],files:[...catalog.files,file('overview.glb')]};
 expect(requiredGardenFiles(map,'N00','WORLD_OVERVIEW').map(x=>x.file).sort()).toEqual(['common.glb','index.html','overview.glb','probe.glb','texture.webp']);
});
