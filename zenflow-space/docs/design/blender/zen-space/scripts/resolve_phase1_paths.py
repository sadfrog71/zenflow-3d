"""Resolve measured route collisions in v004; save a new reviewed snapshot."""
import bpy,math,json,csv,hashlib,shutil
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1];VER='p1-v005';OUT=ROOT/f'zen-space-{VER}.blend'
if OUT.exists():raise RuntimeError('Target exists')
if not bpy.data.filepath.endswith('zen-space-p1-v004.blend'):raise RuntimeError('Expected v004')
s=bpy.context.scene;m=json.loads((ROOT/'manifest/scene-manifest.json').read_text());T=bpy.data.collections['05_TERRAIN_WATER']
# Preserve the previous executed failure evidence before final rechecks overwrite the active report.
if (ROOT/'evidence/structure-check.json').exists():shutil.copy2(ROOT/'evidence/structure-check.json',ROOT/'evidence/structure-check-v004-failed.json')
changes={
 'EAST_BANK_TO_UPPER':(2.2,[(-15.8,3,0),(-14,3,0),(-14,16,0),(-19,18,0)]),
 'COURT_TO_BELL':(2.4,[(6,9,0),(6,19,0),(5,23,0)]),
 'PUBLIC_EAST_ROUTE':(2.4,[(14,9,0),(25,10,0),(25,28.8,0),(35,28.8,3.75),(35,32.2,3.75),(35,42.2,7.5),(31,42.2,7.5),(31,35,7.5),(25,35,7.5)]),
 'CHAT_BRANCH':(1.8,[(25,24,0),(25.9,24,.3),(26.5,24,.3)]),
 'WEST_HILL_STAIRS':(2.0,[(-19,18,0),(-16.5,20,0),(-12,25,2.25),(-20,31,4.5),(-25,31,4.5)]),
 'HILL_WEST_TO_TREE':(2.0,[(-25,31,4.5),(-20,34,4.5),(-6,40,7.5),(0,38,7.5)]),
 'BELL_TO_HILL':(2.0,[(5,23,0),(12,23,0),(12,29,1.8),(18,35,4.65),(10,40,7.5),(0,38,7.5)])}
for o in list(s.objects):
 if o.type=='MESH' and any(o.name.startswith(n+'_S') for n in changes):bpy.data.objects.remove(o,do_unlink=True)
m['routes']=[r for r in m['routes'] if r['route'] not in changes]
def block(n,loc,size):
 bpy.ops.mesh.primitive_cube_add(size=1,location=loc);o=bpy.context.object;o.name=n;o.dimensions=size;bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
 for c in list(o.users_collection):c.objects.unlink(o)
 T.objects.link(o);o.data.materials.append(bpy.data.materials['Clay_Path']);o['phase']='P1_GREYBOX';return o
def path(name,w,points):
 for k,(aa,bb) in enumerate(zip(points,points[1:])):
  a=Vector(aa);b=Vector(bb);d=b-a;dist=Vector((d.x,d.y,0)).length;rise=d.z
  steps=max(1,math.ceil(round(abs(rise)/.15,6))) if abs(rise)>.18 else 1
  pad=min(w/2,dist*.15) if steps>1 else 0
  flight=dist-pad*2
  segments=[]
  if pad:segments.append((0,pad,a.z,'LANDING_A'))
  for i in range(steps):segments.append((pad+flight*i/steps,pad+flight*(i+1)/steps,a.z+rise*(i+1)/steps,'STEP'+str(i)))
  if pad:segments.append((dist-pad,dist,b.z,'LANDING_B'))
  for p0,p1,h,tag in segments:
   p=a.lerp(b,(p0+p1)/(2*dist));grade=max(0,min(1,(p.y-25)/13))*7.5
   if p.y>42:grade=7.5-min(1,(p.y-42)/23)*19
   depth=max(.16,h-grade+.3)
   o=block(f'{name}_S{k}_{tag}',(p.x,p.y,h-depth/2),(w,p1-p0+.01,depth));o.rotation_euler.z=-math.atan2(d.x,d.y)
  m['routes'].append({'route':name,'from':list(a),'to':list(b),'width':w,'rise':abs(rise),'steps':steps,'max_riser':abs(rise)/steps,'tread_m':flight/steps,'landing_pad_m':pad,'profile':'flat-landings-linear-flight'})
for name,(w,pts) in changes.items():path(name,w,pts)
# Retreat only interfering greybox bank rocks, measured against the actual route envelopes.
def distseg(x,y,a,b):
 vx=b[0]-a[0];vy=b[1]-a[1];t=max(0,min(1,((x-a[0])*vx+(y-a[1])*vy)/max(.001,vx*vx+vy*vy)))
 return math.hypot(x-a[0]-t*vx,y-a[1]-t*vy)
removed=[]
for o in list(s.objects):
 if not o.name.startswith('BANK_ROCK_'):continue
 near=False
 for r in m['routes']:
  if distseg(o.location.x,o.location.y,r['from'],r['to']) < r['width']/2+max(o.scale.x,o.scale.y)+.15:
   near=True;break
 if near:removed.append(o.name);bpy.data.objects.remove(o,do_unlink=True)
 else:o.location.z=-.85;o.scale.z=1.3
# Move the three remaining legs of the first library desk (kept as independent objects).
legfix=[]
for o in bpy.data.collections['Z04_SHELL'].objects:
 if o.name.startswith('Z04_READING_DESK_LEG') and abs(o.location.x+17.6)<.9 and abs(o.location.y+9)<.5:
  o.location.x-=.9;o.location.y+=1.8;legfix.append(o.name)
# All viewing states still use the same geometric world.
for o in s.objects:o['version']=VER
pool={};linked=0;count=0
for o in s.objects:
 if o.type!='MESH':continue
 count+=1;me=o.data;key=hashlib.sha256(repr(([tuple(round(a,6) for a in v.co) for v in me.vertices],[tuple(f.vertices) for f in me.polygons],[a.name for a in me.materials])).encode()).hexdigest()
 if key in pool:o.data=pool[key];linked+=1
 else:pool[key]=me
(ROOT/'evidence/instancing-report.json').write_text(json.dumps({'source_version':VER,'mesh_objects':count,'unique_mesh_data':len(pool),'linked_instances':linked,'method':'shared mesh data; all objects/anchors independently placed','skill':'environment-artist'},indent=2))
m['version']=VER;m['source_snapshot']=OUT.name;m['review_corrections']+=['Landing pads keep overlapping flight corners at the same elevation','Hill paths reach terrace elevation before crossing retaining faces','Bell hill path goes around east end of retained background wall','Waterside path uses clear veranda centre; conflicting proxy bank rocks removed','Cottage entry joins level public path and climbs before floor boundary']
(ROOT/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2))
for fn,data,keys in [('camera-register.csv',m['cameras'],['name','region','position','target','lens_mm','ortho_m','display_state']),('path-register.csv',m['routes'],['route','from','to','width','rise','steps','max_riser','tread_m','landing_pad_m'])]:
 with (ROOT/'manifest'/fn).open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=keys,extrasaction='ignore');w.writeheader();w.writerows(data)
with (ROOT/'manifest/asset-register.csv').open('w',newline='') as f:
 w=csv.writer(f);w.writerow(['object','type','collection','material','source','license','interaction'])
 for o in s.objects:w.writerow([o.name,o.type,';'.join(c.name for c in o.users_collection),';'.join(a.name for a in o.data.materials) if hasattr(o.data,'materials') else '', 'concept image reference' if o.name.startswith('REF_') else 'procedural original','project-authored','anchor' if o.type=='EMPTY' and o.name.startswith(('ANCHOR','SELECT')) else 'static'])
(ROOT/'evidence/path-corrections.json').write_text(json.dumps({'version':VER,'changed_routes':list(changes),'removed_conflicting_proxy_rocks':removed,'reading_desk_legs_repositioned':legfix,'reason':'actual scene clearance failures in v004, not image similarity'},ensure_ascii=False,indent=2))
s['version']=VER;s.camera=bpy.data.objects['CAM_WORLD_FULL'];bpy.ops.wm.save_as_mainfile(filepath=str(OUT));print('RESOLVED_P1_SAVED',OUT)
