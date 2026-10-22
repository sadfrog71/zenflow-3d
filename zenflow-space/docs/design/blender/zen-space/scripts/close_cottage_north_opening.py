"""Close the cottage's generic north opening facing public stairs, keep east garden window."""
import bpy,json,csv
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];VER='p1-v011';OUT=ROOT/f'zen-space-{VER}.blend'
if OUT.exists():raise RuntimeError('Version exists')
if not bpy.data.filepath.endswith('zen-space-p1-v010.blend'):raise RuntimeError('Expected v010')
s=bpy.context.scene;m=json.loads((ROOT/'manifest/scene-manifest.json').read_text());shell=bpy.data.collections['Z11_SHELL'];removed=[]
for o in list(shell.objects):
 if o.name.startswith('Z11_NS_WALL') and abs(o.location.y-26.25)<.02:removed.append(o.name);bpy.data.objects.remove(o,do_unlink=True)
bpy.ops.object.select_all(action='DESELECT');bpy.ops.mesh.primitive_cube_add(size=1,location=(29,26.25,1.925));o=bpy.context.object;o.name='Z11_NORTH_PRIVACY_WALL';o.dimensions=(5.4,.20,3.25);bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
for c in list(o.users_collection):c.objects.unlink(o)
shell.objects.link(o);o.data.materials.append(bpy.data.materials['Clay_Plaster']);o['reason']='opaque north enclosure screens public hill stairs; east garden window retained'
for o in s.objects:o['version']=VER
s['version']=VER;s['phase_status']='P1_VISUAL_REVIEW_PENDING';s.camera=bpy.data.objects['CAM_WORLD_FULL']
m['version']=VER;m['source_snapshot']=OUT.name;m['review_corrections']+=['Cottage north face is opaque enclosure facing public stairs; east garden window remains open to the private garden']
(ROOT/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2))
inst=json.loads((ROOT/'evidence/instancing-report.json').read_text());inst['source_version']=VER;inst['mesh_objects']=sum(o.type=='MESH' for o in s.objects);inst['unique_mesh_data']=len({o.data.name for o in s.objects if o.type=='MESH'});inst['linked_instances']=inst['mesh_objects']-inst['unique_mesh_data'];(ROOT/'evidence/instancing-report.json').write_text(json.dumps(inst,indent=2))
with (ROOT/'manifest/asset-register.csv').open('w',newline='') as f:
 w=csv.writer(f);w.writerow(['object','type','collection','material','source','license','interaction'])
 for o in s.objects:w.writerow([o.name,o.type,';'.join(c.name for c in o.users_collection),';'.join(a.name for a in o.data.materials) if hasattr(o.data,'materials') else '', 'concept image reference' if o.name.startswith('REF_') else 'procedural original','project-authored','anchor' if o.type=='EMPTY' and o.name.startswith(('ANCHOR','SELECT')) else 'static'])
(ROOT/'evidence/cottage-enclosure-correction.json').write_text(json.dumps({'version':VER,'removed_north_opening_objects':removed,'added':'Z11_NORTH_PRIVACY_WALL','reason':'north public stairs visible in seated review; east private garden view retained'},indent=2))
bpy.ops.wm.save_as_mainfile(filepath=str(OUT));print('COTTAGE_ENCLOSURE_SAVED',OUT)
