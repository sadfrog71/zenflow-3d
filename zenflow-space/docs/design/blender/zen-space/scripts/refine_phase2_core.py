"""P2 core look development on existing P1. Never clear or rebuild the world."""
import bpy,sys,math,random,json,hashlib
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
from phase2_materials import *
R=random.Random(20261020);s=bpy.context.scene;V='p2-v001';OUT=ROOT/f'zen-space-{V}.blend'
if s.get('version')!='p1-v011' or OUT.exists():raise RuntimeError('Requires P1 v011 and new output')
if not (ROOT/'evidence/phase2-runtime-sample.json').exists():raise RuntimeError('Desktop sample required before core look development')
M=palette();setup(s);s.cycles.samples=24;s.render.resolution_x=1600;s.render.resolution_y=900
c=bpy.data.collections.new('P2_CORE_DETAILS');s.collection.children.link(c)
lights=bpy.data.collections['60_LIGHTING'];cache={};modified=[]
coreprefix=('Z01','Z02','Z03','Z04','Z05','BOOK_','TITLE_','BANK_ROCK','CASCADE_BANK','WATER_LOWER','ISLAND_','EAST_BANK','WEST_UPPER')
# Only core-area materials; other architecture deliberately stays greybox.
for o in list(s.objects):
 if not o.name.startswith(coreprefix) or not hasattr(o.data,'materials') or not o.data.materials:continue
 old=o.data.materials[0].name
 if 'LABEL' in o.name:mat=M['Paper']
 elif 'TITLE' in o.name:mat=M['Ink']
 elif 'Roof' in old:mat=M['Roof']
 elif 'Water' in old:mat=M['Water']
 elif 'Plaster' in old:mat=M['Plaster']
 elif 'Stone' in old or 'Path' in old:mat=M['Stone']
 elif 'Trunk' in old:mat=M['Darkwood']
 elif 'Seat' in old:mat=M['Linen']
 elif 'Paper' in old:mat=M['Paper']
 elif 'Dark' in old:mat=M['Ink']
 elif 'Ground' in old:mat=M['Soil']
 elif 'Leaves' in old:continue
 elif 'Blossom' in old:mat=M['Blossom']
 else:mat=M['Cedar']
 key=(o.data.name,mat.name)
 if o.type=='MESH' and key in cache:o.data=cache[key]
 else:
  assign(o,mat)
  if o.type=='MESH':cache[key]=o.data
 modified.append(o.name)
# Smooth core cylindrical supports and stone proxies; edge bevels are editable.
for o in list(s.objects):
 if o.type=='MESH' and o.name.startswith(coreprefix):
  if any(k in o.name for k in ['COLUMN','TRUNK','CUP','ROUND_CUSHION']):
   for f in o.data.polygons:f.use_smooth=True
  if 'ROCK' in o.name or 'CASCADE_BANK' in o.name:
   sub=o.modifiers.new('P2_soft_stone','SUBSURF');sub.levels=1;sub.render_levels=2
# Individual planks occupy existing floor surfaces, no layout change.
for zone,cx,cy,w,d,z in [('Z04',-11,-6.5,18,10.8,.3),('Z05',12,-6.5,19.8,12.6,.3),('Z01',-14,9,2.8,20,0)]:
 n=int(w/.24);bw=w/n
 for i in range(n):
  xx=cx-w/2+(i+.5)*bw;yy=cy-d/2
  while yy<cy+d/2-.01:
   length=min(R.uniform(1.7,3.0),cy+d/2-yy)
   o=box(zone+'_P2_FLOOR_PLANK',(xx,yy+length/2,z+.007),(bw-.006,length-.008,.014),c,M['Cedar'],.003)
   # run grain along plank, keep export UVs explicit
   offset=R.random()
   for f in o.data.polygons:
    for li in f.loop_indices:
     p=o.data.vertices[o.data.loops[li].vertex_index].co;o.data.uv_layers.active.data[li].uv=(p.x*2+offset,p.y*.25)
   yy+=length
print('P2_FLOORS_DONE',flush=True)
# Visible underside of veranda roof: modular beams and bracket arms.
for yy in [0,3.6,7.2,10.8,14.4,18]:
 for xx in [-15.2,-12.8]:
  for sign in [-1,1]:
   box('Z01_P2_BRACKET',(xx+sign*.22,yy,3.08),(.44,.22,.16),c,M['Cedar'],.025)
   beam('Z01_P2_BRACKET_DIAGONAL',(xx,yy,2.72),(xx+sign*.47,yy,3.16),.055,c,M['Cedar'])
for yy in [i*.36-.7 for i in range(59)]:
 beam('Z01_P2_VISIBLE_RAFTER',(-16.02,yy,3.35),(-14,yy,4.07),.055,c,M['Cedar'])
 beam('Z01_P2_VISIBLE_RAFTER',(-14,yy,4.07),(-11.98,yy,3.35),.055,c,M['Cedar'])
# Roof tile courses follow the original same profile, no camera-specific roof.
for zone,cx,cy,w,d,z,rise in [('Z04',-11,-6.5,20.3,13.2,3.65,2.6),('Z05',12,-6.5,22.1,15,3.65,2.6)]:
 for sign in [-1,1]:
  for row in range(12):
   t=(row+.5)/12;hx=w/2-1.4*t;yy=cy+sign*d/2*(1-t);zz=z+rise*(1-(1-t)**1.5)+.24*(1-t)**6+.05
   for i in range(int(2*hx/.28)):
    xx=cx-hx+(i+.5)*.28
    o=box(zone+'_P2_TILE_COURSE',(xx,yy,zz),(.26,d/24+.025,.055),c,M['Roof'],.016)
    deriv=rise*1.5*math.sqrt(1-t)-1.44*(1-t)**5;o.rotation_euler.x=-sign*math.atan2(deriv,d/2)
print('P2_ROOFS_DONE',flush=True)
# Paper window panels on Z05 south/east wall, entrances remain open.
for xx in [3.75,7.05,13.65,16.95,20.25]:box('Z05_P2_WINDOW_PAPER_NS',(xx,-12.8,2.05),(3.05,.012,1.95),c,M['Paper'])
for yy in [-10.7,-6.5,-2.3]:box('Z05_P2_WINDOW_PAPER_EW',(21.9,yy,2.05),(.012,4.05,1.95),c,M['Paper'])
# Individually framed lamps use the same low desk positions.
lamps=[]
for o in list(s.objects):
 if o.name.startswith('Z05_LAMP_PROXY'):
  p=o.location.copy();o.hide_render=True
  box('Z05_P2_LAMP_BASE',(p.x,p.y,p.z-.19),(.24,.24,.035),c,M['Darkwood'],.01)
  box('Z05_P2_LAMP_SHADE',p,(.20,.20,.30),c,M['Glow'],.007)
  for dx in [-.11,.11]:
   for dy in [-.11,.11]:box('Z05_P2_LAMP_FRAME',(p.x+dx,p.y+dy,p.z),(.018,.018,.34),c,M['Darkwood'],.003)
  box('Z05_P2_LAMP_CAP',(p.x,p.y,p.z+.18),(.26,.26,.035),c,M['Darkwood'],.01)
  lamps.append(light('P2_COPY_LAMP',(p.x,p.y,p.z+.05),9,(1,.52,.20),lights,.09,'POINT'))
# Floor runners and book contents on a selectable independent mesh.
for xx in [5.6,9.2,14.6,18.2]:box('Z05_P2_WOVEN_RUNNER',(xx,-6.5,.316),(2.15,10.3,.016),c,M['Linen'],.012)
for o in list(s.objects):
 if o.name.startswith('BOOK_') and o.get('book_id'):
  p=o.location;face=box('CONTENT_FACE_'+o['book_id'],(p.x-.086,p.y,p.z),(.004,.29,.44),c,M['Paper']);face['book_id']=o['book_id'];face['content_role']='read_only_reference';face['source']='public title only; no scripture body embedded'
# Near-shore rocks refined without changing path footprints.
for i in range(45):
 a=math.tau*i/45;x=-28+2.75*math.cos(a);y=3+2.25*math.sin(a)
 # lower bridge approach stays clear on east
 if x>-25.8 and abs(y-3)<1.4:continue
 bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=2,radius=1,location=(x,y,-.85+R.uniform(-.14,.12)));o=bpy.context.object;o.name='Z03_P2_SHORE_STONE';o.scale=(R.uniform(.32,.62),R.uniform(.26,.53),R.uniform(.25,.46));link(o,c);assign(o,M['Stone'])
 for v in o.data.vertices:v.co*=R.uniform(.88,1.12)
 for f in o.data.polygons:f.use_smooth=True
# Fine geometric water ripples retain export normals, rather than unsupported nodes.
water=bpy.data.objects.get('WATER_LOWER_LEVEL_-1.20')
if water:
 # edit only visible water surface, original shoreline polygon retained as boundary
 water.data=water.data.copy();bpy.context.view_layer.objects.active=water
 bpy.ops.object.select_all(action='DESELECT');water.select_set(True);bpy.ops.object.mode_set(mode='EDIT');bpy.ops.mesh.select_all(action='SELECT');bpy.ops.mesh.quads_convert_to_tris();bpy.ops.mesh.subdivide(number_cuts=14);bpy.ops.object.mode_set(mode='OBJECT')
 for v in water.data.vertices:v.co.z+=.008*math.sin(v.co.x*7+v.co.y*4)+.004*math.cos(v.co.y*11)
 for f in water.data.polygons:f.use_smooth=True
print('P2_SHORE_DONE',flush=True)
# Leaf cards in branch clusters: editable meshes, shared data instances.
leafverts=[];leaffaces=[];uvs=[]
for j in range(45):
 x,y,z=R.uniform(-.6,.6),R.uniform(-.45,.45),R.uniform(-.25,.25);a=R.random()*math.tau;w=R.uniform(.06,.13);h=w*1.8;u=Vector((math.cos(a)*w,math.sin(a)*w,.02));v=Vector((-.2*math.sin(a)*h,.2*math.cos(a)*h,h));p=Vector((x,y,z));k=len(leafverts)
 leafverts.extend([p-u-v,p+u-v,p+u+v,p-u+v]);leaffaces.append((k,k+1,k+2,k+3))
me=bpy.data.meshes.new('P2_LEAF_CLUSTER_SHARED');me.from_pydata(leafverts,[],leaffaces);me.materials.append(M['Leaf']);uv=me.uv_layers.new()
for f in me.polygons:
 for li,co in zip(f.loop_indices,[(0,0),(1,0),(1,1),(0,1)]):uv.data[li].uv=co
# Replace ONLY main courtyard canopy proxy; other tree greyboxes remain.
for o in list(s.objects):
 if o.name.startswith('Z01_COURTYARD_TREE') and ('CANOPY' in o.name or 'SPARSE_RED' in o.name):o.hide_render=True
for i in range(140):
 a=R.random()*math.tau;rad=math.sqrt(R.random())*3.8;p=Vector((math.cos(a)*rad,9+math.sin(a)*rad,4.3+R.uniform(-.3,1.4)-rad*.12));o=bpy.data.objects.new('Z01_P2_LEAF_CLUSTER',me);c.objects.link(o);o.location=p;o.rotation_euler=(R.uniform(-.4,.4),R.uniform(-.4,.4),a)
 if i%7==0:
  beam('Z01_P2_FINE_BRANCH',(0,9,3.0),p,.035,c,M['Darkwood'])
 if i%8==0:
  for j in range(3):
   bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1,radius=.055,location=p+Vector((R.uniform(-.2,.2),R.uniform(-.2,.2),.1)));bl=bpy.context.object;bl.name='Z01_P2_SPARSE_BLOSSOM';link(bl,c);assign(bl,M['Blossom'])
# Sparse near-bank shrubs do not enter the island or public paths.
for x,y,z in [(-17,8,.1),(-18,12,.1),(-21,23,.1),(-39,12,0),(-38,20,.1),(-33,6,-.7)]:
 for j in range(7):
  o=bpy.data.objects.new('Z02_P2_BANK_FOLIAGE',me);c.objects.link(o);o.location=(x+R.uniform(-.8,.8),y+R.uniform(-.8,.8),z+R.uniform(.1,.4));o.rotation_euler.z=R.random()*math.tau;o.scale=(.8,.8,.8)
# Soft window fills; night state scales these down, lamps remain local.
for zone,x,y in [('Z04',-19,-7),('Z05',3,-6.5),('Z05',12,-12.5)]:light('P2_WINDOW_FILL_'+zone,(x,y,2.3),230,(.70,.81,1),lights,4,target=(x+4,y+2,.5))
light('P2_VERANDA_BOUNCE',(-15,6,2.7),100,(1,.82,.62),lights,5,target=(-14,7,0))
# Existing sun retained, source change is explicit.
sun=bpy.data.objects.get('DAY_SUN');sun.rotation_euler=(.55,-.4,-.65);sun.data.energy=2.0;sun.data.angle=.12
s['version']=V;s['phase_status']='P2_CORE_LOOKDEV_REVIEW_PENDING';s['phase2_sample']='material-kit-v001.glb';s['phase2_scope']='Z01 Z02 near shore Z03 Z04 Z05; other architecture remains P1'
s.camera=bpy.data.objects['CAM_ENTRY_SEATED']
for vl in s.view_layers:vl.use=vl.name=='EYE_LEVEL'
# Add a practical core overview, same geometry, no compositing reference cards.
camera('CAM_P2_CORE_OVERVIEW',(45,-65,47),(-8,4,0),bpy.data.collections['70_CAMERAS'],37)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT))
(ROOT/'evidence/phase2-core-build.json').write_text(json.dumps({'version':V,'base':'p1-v011','material_modified_objects':modified,'new_collection':c.name,'new_objects':len(c.objects),'leaf_cluster_instances':182,'textures':'5 original packed PNG maps','layout':'existing buildings and paths retained','pending':['visual inspection','day dusk night render','iOS WebView','full scene GLB']},ensure_ascii=False,indent=2))
print('P2_CORE_SAVED',OUT,flush=True)
