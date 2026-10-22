import bpy,sys,math,random,json,hashlib
from pathlib import Path
from mathutils import Vector,noise
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
from phase2_materials import *
s=bpy.context.scene;R=random.Random(202610202);V='p2-v002';OUT=ROOT/f'zen-space-{V}.blend'
if s.get('version')!='p2-v001' or OUT.exists():raise RuntimeError('Requires p2-v001, new output')
M={k:bpy.data.materials['P2_'+k] for k in ['Cedar','Darkwood','Stone','Paper','Leaf','Roof','Plaster','Soil','Water','Ink','Linen','Blossom','Glow']};c=bpy.data.collections['P2_CORE_DETAILS'];lights=bpy.data.collections['60_LIGHTING']
for k,fn in [('Cedar','cedar-soft.png'),('Linen','woven.png')]:
 tx=next(n for n in M[k].node_tree.nodes if n.type=='TEX_IMAGE');im=bpy.data.images.load(str(ROOT/'assets/textures'/fn),check_existing=True);im.pack();tx.image=im
# The original north/south entry bay is at x=13.65, and must remain open.
for o in s.objects:
 if o.name.startswith('Z05_P2_WINDOW_PAPER_NS') and abs(o.location.x-13.65)<.1:o.location.x=10.35
 if o.name.startswith('Z05_INDOOR_SEAT'):
  assign(o,M['Linen'])
  for f in o.data.polygons:f.use_smooth=True
 if o.type in ['MESH','CURVE'] and o.name.startswith(('Z01','Z04','Z05')) and 'ROOF' in o.name:
  if o.data.materials:o.data.materials[0]=M['Roof']
# Existing visible slopes retain measured geometry; change greybox appearance only.
assign(bpy.data.objects['TERRAIN_CONTINUOUS_BASE'],M['Soil'])
for f in bpy.data.objects['TERRAIN_CONTINUOUS_BASE'].data.polygons:f.use_smooth=True
leafproxy=bpy.data.materials['Proxy_Leaves'];leafproxy.diffuse_color=(.14,.22,.10,1);leafproxy.node_tree.nodes.get('Principled BSDF').inputs['Base Color'].default_value=(.14,.22,.10,1)
# Replace pyramid-only backdrop meshes with continuous irregular hills at the same positions.
backmats=[material('P2_BACKGROUND_'+str(i),(.20+i*.025,.29+i*.026,.27+i*.03),None,.95) for i in range(5)]
backdrops=[]
for o in list(s.objects):
 if o.type!='MESH' or not o.name.startswith(('DISTANT_MOUNTAIN','WEST_MOUNTAIN','EAST_MOUNTAIN')):continue
 vs=[o.matrix_world@v.co for v in o.data.vertices];xmin=min(v.x for v in vs);xmax=max(v.x for v in vs);ymin=min(v.y for v in vs);ymax=max(v.y for v in vs);zmin=min(v.z for v in vs);peak=max(v.z for v in vs)-zmin;verts=[];faces=[];N=23
 for j in range(N):
  for i in range(N):
   u=i/(N-1);v=j/(N-1);x=xmin+(xmax-xmin)*u;y=ymin+(ymax-ymin)*v
   profile=max(0,math.sin(math.pi*u)*math.sin(math.pi*v))**.65
   hh=profile*peak*(.82+.18*math.sin(u*12+v*6))+noise.noise_vector(Vector((x*.13,y*.13,2))).z*profile*3
   verts.append((x,y,zmin+hh))
 for j in range(N-1):
  for i in range(N-1):a=j*N+i;faces.append((a,a+1,a+N+1,a+N))
 me=bpy.data.meshes.new(o.name+'_RIDGE');me.from_pydata(verts,[],faces);me.update();o.data=me;assign(o,backmats[R.randrange(5)])
 for f in me.polygons:f.use_smooth=True
 backdrops.append(o.name)
# Finer leaf cards, random orientations; same shared cluster mesh for all instances.
old=bpy.data.meshes['P2_LEAF_CLUSTER_SHARED'];verts=[];faces=[]
for j in range(110):
 p=Vector((R.uniform(-.58,.58),R.uniform(-.44,.44),R.uniform(-.28,.28)));a=R.random()*math.tau;w=R.uniform(.02,.045);h=w*2.15;tilt=R.uniform(-1.0,1.0)
 u=Vector((math.cos(a)*w,math.sin(a)*w,.0));v=Vector((-math.sin(a)*h*math.cos(tilt),math.cos(a)*h*math.cos(tilt),h*math.sin(tilt)));k=len(verts);verts.extend([p-u-v,p+u-v,p+u+v,p-u+v]);faces.append((k,k+1,k+2,k+3))
me=bpy.data.meshes.new('P2_FINE_LEAF_CLUSTER_SHARED');me.from_pydata(verts,[],faces);me.materials.append(M['Leaf']);uv=me.uv_layers.new()
for f in me.polygons:
 for li,co in zip(f.loop_indices,[(0,0),(1,0),(1,1),(0,1)]):uv.data[li].uv=co
for o in list(s.objects):
 if o.type=='MESH' and o.data==old:o.data=me
# Upper-bank tree is visible in veranda sample: replace its canopy proxies only.
for o in list(s.objects):
 if o.name.startswith('Z08_WISH_TREE_CANOPY'):
  o.hide_render=True;p=o.location;sc=o.scale
  for j in range(40):
   a=R.random()*math.tau;rr=math.sqrt(R.random());ob=bpy.data.objects.new('Z02_P2_UPSTREAM_CANOPY',me);c.objects.link(ob);ob.location=(p.x+math.cos(a)*sc.x*rr,p.y+math.sin(a)*sc.y*rr,p.z+R.uniform(-.55,.55)*sc.z);ob.rotation_euler=(R.random(),R.random(),a)
# Repeated paving, exact footprint of existing courtyard.
stonevariants=[material('P2_PAVING_'+str(i),(.36+i*.018,.38+i*.018,.34+i*.018),'stone.png',.86) for i in range(3)]
for i in range(28):
 for j in range(22):
  box('Z01_P2_COURT_PAVER',(-12.5+i, -1.5+j,.007),(.991,.991,.014),c,stonevariants[(i+j)%3],.006)
# Fine pale flow strips on the same waterfall faces; no new water levels.
flow=material('P2_CASCADE_FLOW',(.50,.65,.62),None,.19,.1);foam=material('P2_CASCADE_FOAM',(.71,.78,.72),None,.45)
for cx,cy,w,top,bottom in [(-34,33,5,6,3.6),(-32,29,5.5,3.6,1.2),(-30,25,6,1.2,-1.2)]:
 for i in range(22):
  xx=cx-w/2+(i+.5)*w/22;ww=R.uniform(.05,.16)
  box('Z02_P2_WATER_FLOW',(xx,cy-1.515,(top+bottom)/2),(ww,.012,top-bottom-.04),c,flow,.005)
 for i in range(16):
  xx=cx-w/2+R.random()*w;box('Z02_P2_FLOW_FOAM',(xx,cy-1.6,bottom+.025),(R.uniform(.1,.25),R.uniform(.12,.30),.015),c,foam,.01)
# More natural column bases and cup: preserve their positions and existing routes.
for o in list(s.objects):
 if o.name.startswith(('Z01_COLUMN_BASE','Z04_COLUMN_BASE','Z05_COLUMN_BASE')):
  for f in o.data.polygons:f.use_smooth=True
cup=bpy.data.objects['Z01_CUP_PROXY'];cup.hide_render=True;x,y,z=cup.location;v=[];f=[];profile=[(.043,-.065),(.055,.065),(.047,.067),(.037,-.045)]
for rr,zz in profile:
 for i in range(40):a=i*math.tau/40;v.append((x+rr*math.cos(a),y+rr*math.sin(a),z+zz))
for j in range(3):
 for i in range(40):a=j*40+i;b=j*40+(i+1)%40;f.append((a,b,b+40,a+40))
mc=bpy.data.meshes.new('Z01_P2_CUP');mc.from_pydata(v,[],f);ob=bpy.data.objects.new('Z01_P2_CERAMIC_CUP',mc);c.objects.link(ob);assign(ob,M['Paper'])
for p in mc.polygons:p.use_smooth=True
beam('Z01_P2_TEA_SURFACE',(x,y,z+.036),(x,y,z+.04),.044,c,M['Darkwood'],40)
# Paper foreground and aisle-facing view, using existing desk at (9.2,-3).
cam=bpy.data.objects['CAM_COPY_VIEW'];cam.location=(9.2,-1.6,1.10);cam.rotation_euler=(Vector((9.6,-10,1.3))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.lens=27
# Sky map is part of the Blender lighting reference, not exported as a GLB world.
nt=s.world.node_tree;nt.nodes.clear();env=nt.nodes.new('ShaderNodeTexEnvironment');im=bpy.data.images.load(str(ROOT/'assets/textures/sky-day.png'),check_existing=True);im.pack();env.image=im;bg=nt.nodes.new('ShaderNodeBackground');bg.inputs['Strength'].default_value=.45;out=nt.nodes.new('ShaderNodeOutputWorld');nt.links.new(env.outputs['Color'],bg.inputs['Color']);nt.links.new(bg.outputs[0],out.inputs[0])
# Deduplicate exact repeated geometry including UVs; independent object transforms retained.
pool={};reused=0
for o in c.objects:
 if o.type!='MESH':continue
 me=o.data;uv=me.uv_layers.active
 key=hashlib.sha256(repr(([tuple(v.co) for v in me.vertices],[tuple(f.vertices) for f in me.polygons],[m.name for m in me.materials],[tuple(p.uv) for p in uv.data] if uv else [])).encode()).hexdigest()
 if key in pool and o.data!=pool[key]:o.data=pool[key];reused+=1
 else:pool[key]=me
s['version']=V;s['phase_status']='P2_RENDER_REVIEW_PENDING';s.cycles.samples=32
bpy.ops.wm.save_as_mainfile(filepath=str(OUT))
(ROOT/'evidence/phase2-visual-refine.json').write_text(json.dumps({'version':V,'base':'p2-v001','backdrop_replacement':backdrops,'reused_meshes':reused,'changes':['entry paper panel moved clear of original doorway','finer leaf cards','woven seat/runner map','cedar grain softened','continuous backdrop ridges','visible waterfall flow/foam','existing copy desk camera composition','original packed sky reference'],'scope':'core samples and visible context; other buildings remain P1 greybox'},ensure_ascii=False,indent=2))
print('P2_VISUAL_SOURCE_SAVED',OUT,flush=True)
