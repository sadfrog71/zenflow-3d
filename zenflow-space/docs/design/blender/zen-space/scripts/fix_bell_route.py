"""Resolve last measured bell-route conflicts; preserve v006, save v007."""
import bpy,math,json,csv,hashlib,shutil
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1];VER='p1-v007';OUT=ROOT/f'zen-space-{VER}.blend'
if OUT.exists():raise RuntimeError('Target exists')
if not bpy.data.filepath.endswith('zen-space-p1-v006.blend'):raise RuntimeError('Expected v006')
s=bpy.context.scene;m=json.loads((ROOT/'manifest/scene-manifest.json').read_text());T=bpy.data.collections['05_TERRAIN_WATER']
shutil.copy2(ROOT/'evidence/structure-check.json',ROOT/'evidence/structure-check-v006-failed.json')
for o in list(s.objects):
 if o.name.startswith('BELL_TO_HILL_S'):bpy.data.objects.remove(o,do_unlink=True)
m['routes']=[r for r in m['routes'] if r['route']!='BELL_TO_HILL']
pts=[(5,23,0),(5,23.8,0),(12,23.8,0),(12,29.8,1.8),(18,35,4.65),(10,37,7.5),(10,39,7.5),(0,38,7.5)]
bpy.ops.object.select_all(action='DESELECT')
for k,(aa,bb) in enumerate(zip(pts,pts[1:])):
 a=Vector(aa);b=Vector(bb);d=b-a;dist=Vector((d.x,d.y,0)).length;steps=max(1,math.ceil(round(abs(d.z)/.15,6))) if abs(d.z)>.18 else 1;pad=min(1,dist*.15) if steps>1 else 0;flight=dist-2*pad
 seg=[]
 if pad:seg.append((0,pad,a.z,'LANDING_A'))
 for j in range(steps):seg.append((pad+flight*j/steps,pad+flight*(j+1)/steps,a.z+d.z*(j+1)/steps,'STEP'+str(j)))
 if pad:seg.append((dist-pad,dist,b.z,'LANDING_B'))
 for lo,hi,h,tag in seg:
  p=a.lerp(b,(lo+hi)/(2*dist));ground=max(0,min(1,(p.y-25)/13))*7.5;depth=max(1.2,h-ground+.4)
  bpy.ops.mesh.primitive_cube_add(size=1,location=(p.x,p.y,h-depth/2));o=bpy.context.object;o.name=f'BELL_TO_HILL_S{k}_{tag}';o.dimensions=(2,hi-lo+.01,depth);bpy.ops.object.transform_apply(location=False,rotation=False,scale=True);o.rotation_euler.z=-math.atan2(d.x,d.y)
  for c in list(o.users_collection):c.objects.unlink(o)
  T.objects.link(o);o.data.materials.append(bpy.data.materials['Clay_Path'])
 m['routes'].append({'route':'BELL_TO_HILL','from':list(a),'to':list(b),'width':2,'rise':abs(d.z),'steps':steps,'max_riser':abs(d.z)/steps,'tread_m':flight/steps,'landing_pad_m':pad,'profile':'flat-landings-linear-flight'})
# Grade new path corridor using actual mesh, keeping all upper routes at their established level.
terrain=bpy.data.objects['TERRAIN_CONTINUOUS_BASE']
for v in terrain.data.vertices:
 x,y,z=v.co;best=z
 for r in [r for r in m['routes'] if r['route']=='BELL_TO_HILL']:
  a=r['from'];b=r['to'];dx=b[0]-a[0];dy=b[1]-a[1];dist=math.hypot(dx,dy);t=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/max(.00001,dist*dist)));dd=math.hypot(x-a[0]-dx*t,y-a[1]-dy*t);pad=r['landing_pad_m'];u=max(0,min(1,(t*dist-pad)/(dist-2*pad))) if pad else t;h=a[2]+(b[2]-a[2])*u;core=r['width']/2+1.5
  if dd<core+1.5:
   target=h-.38
   if dd>core:target+=(z-target)*(dd-core)/1.5
   best=min(best,target)
 v.co.z=best
terrain.data.update()
pool={};count=0;linked=0
for o in s.objects:
 o['version']=VER
 if o.type!='MESH':continue
 count+=1;me=o.data;key=hashlib.sha256(repr(([tuple(round(a,6) for a in v.co) for v in me.vertices],[tuple(f.vertices) for f in me.polygons],[a.name for a in me.materials])).encode()).hexdigest()
 if key in pool:o.data=pool[key];linked+=1
 else:pool[key]=me
(ROOT/'evidence/instancing-report.json').write_text(json.dumps({'source_version':VER,'mesh_objects':count,'unique_mesh_data':len(pool),'linked_instances':linked,'method':'shared mesh datablocks; independent object transforms and anchors','skill':'environment-artist'},indent=2))
m['version']=VER;m['source_snapshot']=OUT.name;m['review_corrections']+=['Bell route crosses the side colonnade away from posts, reaches 7.50m before joining the rear traverse']
(ROOT/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2))
with (ROOT/'manifest/path-register.csv').open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=['route','from','to','width','rise','steps','max_riser','tread_m','landing_pad_m'],extrasaction='ignore');w.writeheader();w.writerows(m['routes'])
with (ROOT/'manifest/asset-register.csv').open('w',newline='') as f:
 w=csv.writer(f);w.writerow(['object','type','collection','material','source','license','interaction'])
 for o in s.objects:w.writerow([o.name,o.type,';'.join(c.name for c in o.users_collection),';'.join(a.name for a in o.data.materials) if hasattr(o.data,'materials') else '', 'concept image reference' if o.name.startswith('REF_') else 'procedural original','project-authored','anchor' if o.type=='EMPTY' and o.name.startswith(('ANCHOR','SELECT')) else 'static'])
s['version']=VER;s.camera=bpy.data.objects['CAM_WORLD_FULL'];bpy.ops.wm.save_as_mainfile(filepath=str(OUT));print('BELL_ROUTE_SAVED',OUT)
