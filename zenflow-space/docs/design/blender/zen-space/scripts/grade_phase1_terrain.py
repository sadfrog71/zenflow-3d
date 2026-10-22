"""Cut real hillside beds below the resolved stair/road surfaces; relocate tag box.
Retains previous failed source and saves a new editable snapshot.
"""
import bpy,json,math,csv,hashlib,shutil
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1];V='p1-v006';OUT=ROOT/f'zen-space-{V}.blend'
if OUT.exists():raise RuntimeError('Target exists')
if not bpy.data.filepath.endswith('zen-space-p1-v005.blend'):raise RuntimeError('Expected v005')
s=bpy.context.scene;m=json.loads((ROOT/'manifest/scene-manifest.json').read_text())
if (ROOT/'evidence/structure-check.json').exists():shutil.copy2(ROOT/'evidence/structure-check.json',ROOT/'evidence/structure-check-v005-failed.json')
# Centreline height is the same flat-landing / linear-flight function used by route checks.
def closest(x,y,r):
 a=r['from'];b=r['to'];dx=b[0]-a[0];dy=b[1]-a[1];dist=math.hypot(dx,dy)
 t=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/max(.00001,dist*dist)))
 dd=math.hypot(x-a[0]-t*dx,y-a[1]-t*dy);pad=r.get('landing_pad_m',0)
 u=max(0,min(1,(t*dist-pad)/(dist-2*pad))) if pad else t
 h=a[2]+(b[2]-a[2])*u
 if 'BRIDGE' in r['route']:h+=(.65 if 'LOWER' in r['route'] else .85)*math.sin(math.pi*t)
 return dd,h
terrain=bpy.data.objects['TERRAIN_CONTINUOUS_BASE'];touched=0
for vertex in terrain.data.vertices:
 x,y,z=vertex.co;best=z
 for r in m['routes']:
  d,h=closest(x,y,r);core=r['width']/2+1.5;falloff=1.5
  if d<core+falloff:
   target=h-.38
   if d>core:target=target+(z-target)*(d-core)/falloff
   best=min(best,target)
 if best<z-.00001:vertex.co.z=best;touched+=1
terrain.data.update()
# Real stone foundation extends into the cut bed; do not leave thin floating treads.
basefix=0
prefixes=tuple(r['route']+'_S' for r in m['routes'])
for o in s.objects:
 if o.type!='MESH' or not o.name.startswith(prefixes) or 'BRIDGE' in o.name:continue
 if o.dimensions.z<1.2:
  top=o.location.z+o.dimensions.z/2
  o.data=o.data.copy();o.dimensions.z=1.2;o.location.z=top-.6
  bpy.context.view_layer.objects.active=o;o.select_set(True);bpy.ops.object.transform_apply(location=False,rotation=False,scale=True);o.select_set(False);basefix+=1
# Move the box and its complete roof together; low branches and wish tree stay fixed.
delta=Vector((-5,1,0))
for o in s.objects:
 if o.name.startswith('Z08_TAG_BOX'):o.location+=delta
# Four short support legs replace the original floating proxy box base.
prop=bpy.data.collections['20_PROPS_STATIC']
for dx in [-.25,.25]:
 for dy in [-.17,.17]:
  bpy.ops.mesh.primitive_cube_add(size=1,location=(-28+dx,33+dy,4.675));o=bpy.context.object;o.name='Z08_TAG_BOX_LEG';o.dimensions=(.06,.06,.35);bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
  for c in list(o.users_collection):c.objects.unlink(o)
  prop.objects.link(o);o.data.materials.append(bpy.data.materials['Clay_Structure'])
# Share data again after the local foundation edits, maintaining editable objects.
pool={};linked=0;meshcount=0
for o in s.objects:
 o['version']=V
 if o.type!='MESH':continue
 meshcount+=1;me=o.data;key=hashlib.sha256(repr(([tuple(round(a,6) for a in v.co) for v in me.vertices],[tuple(f.vertices) for f in me.polygons],[a.name for a in me.materials])).encode()).hexdigest()
 if key in pool:o.data=pool[key];linked+=1
 else:pool[key]=me
(ROOT/'evidence/instancing-report.json').write_text(json.dumps({'source_version':V,'mesh_objects':meshcount,'unique_mesh_data':len(pool),'linked_instances':linked,'method':'shared mesh datablocks; independent transforms and anchors','skill':'environment-artist'},indent=2))
m['version']=V;m['source_snapshot']=OUT.name;m['review_corrections']+=['Terrain physically graded below road and stairs; no hidden terrain through walking surfaces','Stone stair foundations extend into graded beds','Tag box moved northwest of wish tree clear of the hill path and supplied with support legs']
(ROOT/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2))
with (ROOT/'manifest/asset-register.csv').open('w',newline='') as f:
 w=csv.writer(f);w.writerow(['object','type','collection','material','source','license','interaction'])
 for o in s.objects:w.writerow([o.name,o.type,';'.join(c.name for c in o.users_collection),';'.join(a.name for a in o.data.materials) if hasattr(o.data,'materials') else '', 'concept image reference' if o.name.startswith('REF_') else 'procedural original','project-authored','anchor' if o.type=='EMPTY' and o.name.startswith(('ANCHOR','SELECT')) else 'static'])
(ROOT/'evidence/terrain-grading.json').write_text(json.dumps({'version':V,'edited_terrain_vertices':touched,'deepened_foundations':basefix,'method':'lower terrain to route profile with blended shoulder, never hide terrain by camera','tag_box_position':[-28,33,4.5]},indent=2))
s['version']=V;s.camera=bpy.data.objects['CAM_WORLD_FULL'];bpy.ops.wm.save_as_mainfile(filepath=str(OUT));print('GRADED_SOURCE_SAVED',OUT)
