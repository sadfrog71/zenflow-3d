// Structural and integrity proof for the generated package, independent of the exporter.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'../../../assets/zenflow/life-garden'),manifest=JSON.parse(fs.readFileSync(path.join(root,'manifest.json')));
for(const file of manifest.files){
 const raw=fs.readFileSync(path.join(root,file.file));assert.equal(raw.length,file.bytes,file.file+' size');assert.equal(crypto.createHash('sha256').update(raw).digest('hex'),file.sha256,file.file+' hash');
 if(!file.file.endsWith('.glb'))continue;
 assert.equal(raw.readUInt32LE(0),0x46546c67);assert.equal(raw.readUInt32LE(8),raw.length);
 const jsonLength=raw.readUInt32LE(12),d=JSON.parse(raw.subarray(20,20+jsonLength).toString());const bytes=raw.readUInt32LE(20+jsonLength);
 for(const v of d.bufferViews)assert.ok(v.byteOffset+v.byteLength<=bytes,file.file+' buffer range');
 for(const a of d.accessors)assert.ok(d.bufferViews[a.bufferView],file.file+' accessor');
 for(const n of d.nodes){if(n.mesh!==undefined)assert.ok(d.meshes[n.mesh]);for(const c of n.children||[])assert.ok(d.nodes[c]);}
 for(const m of d.meshes)for(const p of m.primitives){for(const a of Object.values(p.attributes))assert.ok(d.accessors[a]);assert.ok(d.accessors[p.indices]);for(const target of p.targets||[])for(const a of Object.values(target))assert.ok(d.accessors[a]);}
 for(const a of d.animations||[])for(const c of a.channels){assert.ok(d.nodes[c.target.node]);assert.ok(a.samplers[c.sampler]);}
 for(const i of d.images)if(i.uri)assert.ok(fs.existsSync(path.join(root,i.uri)));
}
assert.equal(new Set(manifest.viewpoints.map(v=>v.id)).size,14);
for(const v of manifest.viewpoints.filter(v=>v.available)){
 const raw=fs.readFileSync(path.join(root,v.packageId+'.glb'));const d=JSON.parse(raw.subarray(20,20+raw.readUInt32LE(12)).toString());assert.ok(d.nodes.some(n=>n.name===v.camera&&n.camera!==undefined),v.id+' camera');
}
console.log('PASS:',manifest.files.length,'file hashes, GLB references, texture dependencies and 14 viewpoint mappings');
