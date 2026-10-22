"""Local P1 review corrections over v003. Changes only named cameras/paths/props.
Keeps v003 unchanged; refuses overwriting the reviewed version or master.
"""
import bpy,math,json,csv,hashlib
from pathlib import Path
from mathutils import Vector,Matrix
ROOT=Path(__file__).resolve().parents[1];VERSION='p1-v004';OUT=ROOT/f'zen-space-{VERSION}.blend'
if OUT.exists():raise RuntimeError('Reviewed version already exists')
if not bpy.data.filepath.endswith('zen-space-p1-v003.blend'):raise RuntimeError('Expected v003 snapshot')
s=bpy.context.scene;m=json.loads((ROOT/'manifest/scene-manifest.json').read_text())
TERR=bpy.data.collections['05_TERRAIN_WATER'];PROP=bpy.data.collections['20_PROPS_STATIC'];INT=bpy.data.collections['50_INTERACTION'];CAM=bpy.data.collections['70_CAMERAS'];LIB=bpy.data.collections['Z04_SHELL']
def cube(n,loc,size,c,mat):
 bpy.ops.mesh.primitive_cube_add(size=1,location=loc);o=bpy.context.object;o.name=n;o.dimensions=size;bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
 for cc in list(o.users_collection):cc.objects.unlink(o)
 c.objects.link(o);o.data.materials.append(bpy.data.materials[mat]);o['phase']='P1_GREYBOX';o['version']=VERSION;return o
# Stair risers require sufficient run, so the eastern and central slopes use two flights.
change_routes={
 'PUBLIC_EAST_ROUTE':[(14,9,0),(25,10,0),(25,22,0),(25,28,1.8),(32,30,4.65),(25,35,7.5)],
 'WEST_HILL_STAIRS':[(-19,18,0),(-21,23,0),(-15,28,2.25),(-25,30,4.5)],
 'BELL_TO_HILL':[(3,25,0),(8,28,1.8),(13,33,4.65),(8,38,7.5),(0,38,7.5)]}
for o in list(s.objects):
 if o.type=='MESH' and any(o.name.startswith(n+'_S') for n in change_routes):bpy.data.objects.remove(o,do_unlink=True)
m['routes']=[r for r in m['routes'] if r['route'] not in change_routes]
def path(n,points,w):
 for si,(aa,bb) in enumerate(zip(points,points[1:])):
  a=Vector(aa);b=Vector(bb);v=b-a;dist=Vector((v.x,v.y,0)).length
  count=max(1,math.ceil(round(abs(v.z)/.15,6))) if abs(v.z)>.18 else 1
  for j in range(count):
   p=a.lerp(b,(j+.5)/count);top=a.z+v.z*(j+1)/count
   grade=max(0,min(1,(p.y-25)/13))*7.5
   depth=max(.14,top-grade+.24)
   o=cube(f'{n}_S{si}_STEP{j}',(p.x,p.y,top-depth/2),(w,dist/count+.03,depth),TERR,'Clay_Path');o.rotation_euler.z=-math.atan2(v.x,v.y)
  m['routes'].append({'route':n,'from':list(a),'to':list(b),'width':w,'rise':abs(v.z),'steps':count,'max_riser':abs(v.z)/count,'tread_m':dist/count})
for n,p in change_routes.items():path(n,p,2.4 if n=='PUBLIC_EAST_ROUTE' else 2)
for r in m['routes']:
 if 'tread_m' not in r:
  a=Vector(r['from']);b=Vector(r['to']);r['tread_m']=Vector((b.x-a.x,b.y-a.y,0)).length/max(1,r['steps'])
# Align first reading desk with actual reading seat. Other desks stay unchanged.
# First desk is the first object family without Blender numeric suffix.
for o in list(LIB.objects):
 if (o.name.startswith('Z04_READING_DESK_') or o.name=='ANCHOR_Z04_READING_DESK') and not o.name.rsplit('.',1)[-1].isdigit():
  o.location.x-=.9;o.location.y+=1.8
for name,dx,tilt in [('LEFT',-.145,-.06),('RIGHT',.145,.06)]:
 o=cube('Z04_OPEN_BOOK_'+name,(-18.5+dx,-7.2,.701),(.285,.36,.018),LIB,'Clay_Paper');o.rotation_euler.y=tilt
# Third stone seat is a west-northwest station, avoiding the existing landscape tree.
cube('Z10_WEST_STONE_SEAT',(23,38.5,7.71),(1.7,.55,.42),PROP,'Clay_Stone')
changes={
 'CAM_ISLAND_SEATED':((-28.3,2.9,.35),(-15,8,-3.3),18),
 'CAM_LIBRARY_SEATED':((-18.5,-8.3,1.15),(-20.2,1,.7),28),
 'CAM_BELL_COURT':((-5,18,1.4),(0,23,1.4),26),
 'CAM_WISH_APPROACH':((-27,27,5.95),(-25.5,31,5.8),28),
 'CAM_OLD_TREE_PATH':((4,34.5,9),(-1,36,8.9),24),
 'CAM_LOOKOUT_N':((24,33,8.55),(28,80,12),30),
 'CAM_LOOKOUT_E':((27.5,36,8.55),(90,48,12),30),
 'CAM_LOOKOUT_W':((23,38.5,8.55),(-65,65,12),30),
}
for name,(pos,target,lens) in changes.items():
 o=bpy.data.objects[name];o.location=pos;o.rotation_euler=(Vector(target)-o.location).to_track_quat('-Z','Y').to_euler();o.data.lens=lens
 for c in m['cameras']:
  if c['name']==name:c.update(position=list(pos),target=list(target),lens_mm=lens)
# Small plaque and an independent help anchor; this is geometry, not app wiring.
cube('Z01_HELP_PLAQUE',(-13.01,10.8,1.25),(.035,.28,.42),PROP,'Clay_Seat')
font=bpy.data.fonts.get('PingFang.ttc') or next((f for f in bpy.data.fonts if f.name!='Bfont'),None)
cv=bpy.data.curves.new('Z01_HELP_TITLE','FONT');cv.body='帮\n助';cv.size=.095;cv.align_x='CENTER';cv.align_y='CENTER';cv.extrude=.001
if font:cv.font=font
ob=bpy.data.objects.new('Z01_HELP_TITLE',cv);PROP.objects.link(ob);ob.location=(-13.032,10.8,1.25);ob.rotation_euler=Matrix(((0,0,-1),(-1,0,0),(0,1,0))).to_euler();cv.materials.append(bpy.data.materials['Label'])
e=bpy.data.objects.new('ANCHOR_HELP_READ',None);INT.objects.link(e);e.location=(-13.04,10.8,1.25);e.empty_display_type='SPHERE';e.empty_display_size=.1
cd=bpy.data.cameras.new('CAM_HELP_READ');co=bpy.data.objects.new('CAM_HELP_READ',cd);CAM.objects.link(co);co.location=(-13.8,10.3,1.2);target=(-13.02,10.8,1.25);co.rotation_euler=(Vector(target)-co.location).to_track_quat('-Z','Y').to_euler();cd.lens=45;cd.clip_start=.04
m['cameras'].append({'name':'CAM_HELP_READ','region':'Z01','position':list(co.location),'target':list(target),'lens_mm':45,'ortho_m':None,'display_state':'EYE_LEVEL'})
# Same geometry shared by every view; repeated local geometry uses shared mesh data.
pool={};linked=0;meshcount=0
for o in s.objects:
 o['version']=VERSION
 if o.type!='MESH':continue
 meshcount+=1;me=o.data;payload=([tuple(round(x,6) for x in v.co) for v in me.vertices],[tuple(f.vertices) for f in me.polygons],[a.name for a in me.materials]);key=hashlib.sha256(repr(payload).encode()).hexdigest()
 if key in pool:o.data=pool[key];linked+=1
 else:pool[key]=me
(ROOT/'evidence/instancing-report.json').write_text(json.dumps({'source_version':VERSION,'mesh_objects':meshcount,'unique_mesh_data':len(pool),'linked_instances':linked,'method':'shared mesh datablocks; independent object transforms and anchors','skill':'environment-artist'},indent=2))
m['version']=VERSION;m['source_snapshot']=OUT.name;m['review_corrections']=['stair tread minimum 0.30m using switchbacks','reading desk aligned to reading seat','island seated eye remains 0.65m above ground; wider lens includes cushion edge','old tree camera stands on actual terrace','lookout cameras aligned with actual stone seats; distant tree corridor opens north','single fixed model, no per-camera removals or relocations']
(ROOT/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2))
for fn,data,keys in [('layout-register.csv',m['zones'],['code','name','center','dimensions','neighbors']),('camera-register.csv',m['cameras'],['name','region','position','target','lens_mm','ortho_m','display_state']),('path-register.csv',m['routes'],['route','from','to','width','rise','steps','max_riser','tread_m'])]:
 with (ROOT/'manifest'/fn).open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=keys,extrasaction='ignore');w.writeheader();w.writerows(data)
with (ROOT/'manifest/asset-register.csv').open('w',newline='') as f:
 w=csv.writer(f);w.writerow(['object','type','collection','material','source','license','interaction'])
 for o in s.objects:w.writerow([o.name,o.type,';'.join(c.name for c in o.users_collection),';'.join(a.name for a in o.data.materials) if hasattr(o.data,'materials') else '', 'copied concept image reference' if o.name.startswith('REF_') else 'procedural original','project-authored','anchor' if o.type=='EMPTY' and o.name.startswith(('ANCHOR','SELECT')) else 'static'])
s['version']=VERSION;s.camera=bpy.data.objects['CAM_WORLD_FULL']
bpy.ops.wm.save_as_mainfile(filepath=str(OUT))
print('P1_REVIEWED_SAVED',OUT,'objects',len(s.objects),'cameras',len(m['cameras']))
