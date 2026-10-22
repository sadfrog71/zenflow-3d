"""Final same-model visual corrections identified in the 13-view review sheet."""
import bpy,json,csv
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1];VER='p1-v010';OUT=ROOT/f'zen-space-{VER}.blend'
if OUT.exists():raise RuntimeError('Version exists')
if not bpy.data.filepath.endswith('zen-space-p1-v009.blend'):raise RuntimeError('Expected v009')
s=bpy.context.scene;m=json.loads((ROOT/'manifest/scene-manifest.json').read_text())
# The west view shares the east seat. Remove the extra third seat from the north sightline.
o=bpy.data.objects.get('Z10_WEST_STONE_SEAT')
if o:bpy.data.objects.remove(o,do_unlink=True)
changes={
 'CAM_LOOKOUT_W':((27.5,36,8.55),(-65,65,12),30),
 'CAM_CHAT_SEATED':((27.95,24,1.4),(34.5,25.8,1.15),24)}
for name,(pos,target,lens) in changes.items():
 o=bpy.data.objects[name];o.location=pos;o.rotation_euler=(Vector(target)-o.location).to_track_quat('-Z','Y').to_euler();o.data.lens=lens
 for c in m['cameras']:
  if c['name']==name:c.update(position=list(pos),target=list(target),lens_mm=lens)
# Blossom proxies sit on the outer surface of the green canopy, rather than hidden inside it.
for o in s.objects:
 if o.name.startswith('Z01_COURTYARD_TREE_SPARSE_RED'):o.location.z+=1.4
for o in s.objects:o['version']=VER
m['version']=VER;m['source_snapshot']=OUT.name;m['editing_font_dependency']='Blender Bfont Regular <builtin>; Chinese readability verified locally; cross-platform text consistency remains a phase4 check'
m['review_corrections']+=['North lookout has no extra foreground seat; west view shares the east stone seat','Dialogue eye point is inside the actual chair near the backrest, with arm edge and side table in view','Three small red blossom proxies placed on outside of main courtyard green canopy']
(ROOT/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2))
with (ROOT/'manifest/camera-register.csv').open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=['name','region','position','target','lens_mm','ortho_m','display_state'],extrasaction='ignore');w.writeheader();w.writerows(m['cameras'])
with (ROOT/'manifest/asset-register.csv').open('w',newline='') as f:
 w=csv.writer(f);w.writerow(['object','type','collection','material','source','license','interaction'])
 for o in s.objects:w.writerow([o.name,o.type,';'.join(c.name for c in o.users_collection),';'.join(a.name for a in o.data.materials) if hasattr(o.data,'materials') else '', 'concept image reference' if o.name.startswith('REF_') else 'procedural original','project-authored','anchor' if o.type=='EMPTY' and o.name.startswith(('ANCHOR','SELECT')) else 'static'])
inst=json.loads((ROOT/'evidence/instancing-report.json').read_text());inst['source_version']=VER;inst['mesh_objects']=sum(o.type=='MESH' for o in s.objects);inst['unique_mesh_data']=len({o.data.name for o in s.objects if o.type=='MESH'});inst['linked_instances']=inst['mesh_objects']-inst['unique_mesh_data'];(ROOT/'evidence/instancing-report.json').write_text(json.dumps(inst,indent=2))
s['version']=VER;s['phase_status']='P1_VISUAL_REVIEW_PENDING';s.camera=bpy.data.objects['CAM_WORLD_FULL'];bpy.ops.wm.save_as_mainfile(filepath=str(OUT));print('P1_VISUAL_REVIEW_SOURCE',OUT)
