"""Full-world landscape pass from approved layout. Explicit generated objects only.
All cameras share terrain/water/paths. No reference image planes or per-camera geometry.
"""
import bpy,sys,math,random,json,hashlib
from pathlib import Path
from mathutils import Vector,noise
from mathutils.bvhtree import BVHTree
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
from phase2_materials import box,beam,assign,material,light
from phase3_pbr import palette
s=bpy.context.scene;R=random.Random(202610203);OUT=ROOT/'zen-space-p3-v001.blend'
if s.get('version')!='p2-v002' or OUT.exists():raise RuntimeError('Requires p2-v002 and new p3-v001')
if not (ROOT/'evidence/phase2-runtime-pbr.json').exists():raise RuntimeError('New PBR sample must have actual desktop load evidence')
manifest=json.loads((ROOT/'manifest/scene-manifest.json').read_text());routes=manifest['routes'];P=palette();M={k:bpy.data.materials['P2_'+k] for k in ['Cedar','Darkwood','Stone','Paper','Leaf','Roof','Plaster','Soil','Water','Ink','Linen','Blossom','Glow']}
def collection(n,parent=None):
 c=bpy.data.collections.new(n);(parent or s.collection).children.link(c);return c
C=collection('P3_LANDSCAPE');VEG=collection('P3_PLANTS',C);ROCK=collection('P3_ROCKS',C);T=collection('P3_TERRAIN_DETAIL',C);LIB=collection('P3_MESH_LIBRARY',C)
def mesh(n,v,f,c,mat,uvs=None):
 me=bpy.data.meshes.new(n);me.from_pydata(v,[],f);me.update();o=bpy.data.objects.new(n,me);c.objects.link(o);assign(o,mat)
 if uvs:
  uv=me.uv_layers.active
  for face in me.polygons:
   for li in face.loop_indices:uv.data[li].uv=uvs[me.loops[li].vertex_index]
 return o
def instance(n,data,p,scale,c,rot=None):
 o=bpy.data.objects.new(n,data);c.objects.link(o);o.location=p;o.scale=scale
 if rot:o.rotation_euler=rot
 o['phase']='P3_WORLD';return o

def hide(o):o.hide_render=True;o.hide_viewport=True;o['retired_proxy']=True

def segdistance(x,y,r):
 a,b=r['from'],r['to'];dx=b[0]-a[0];dy=b[1]-a[1];dd=dx*dx+dy*dy;t=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/max(.0001,dd)));dist=math.hypot(x-a[0]-t*dx,y-a[1]-t*dy)
 return dist,t,math.sqrt(dd)
def clear(x,y,radius):return all(segdistance(x,y,r)[0]>r['width']/2+radius+.2 for r in routes)
pond=[(-42,-11),(-34,-15),(-23,-13),(-17,-8),(-16,6),(-18,17),(-23,24),(-34,27),(-41,20),(-44,5)]
def inpond(x,y):
 hit=False;j=len(pond)-1
 for i,(xi,yi) in enumerate(pond):
  xj,yj=pond[j]
  if (yi>y)!=(yj>y) and x<(xj-xi)*(y-yi)/(yj-yi)+xi:hit=not hit
  j=i
 return hit
def shore_distance(x,y):
 best=999
 for a,b in zip(pond,pond[1:]+pond[:1]):
  dx=b[0]-a[0];dy=b[1]-a[1];u=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/(dx*dx+dy*dy)));best=min(best,math.hypot(x-a[0]-u*dx,y-a[1]-u*dy))
 return best
def smooth(t):t=max(0,min(1,t));return t*t*(3-2*t)
# Continuous grading: natural shoulder instead of broad cuts that reveal tall cylindrical foundations.
N=201;verts=[];faces=[]
for j in range(N):
 y=-35+j*.75
 for i in range(N):
  x=-65+i*.75;h=7.5*smooth((y-25)/13)
  if y>42:h=7.38-19*smooth((y-42)/30)+.4*math.sin(x*.13)
  if y<43:
   d=shore_distance(x,y)
   if inpond(x,y):h=-.8-1.42*smooth(d/2.5)
   elif d<2:h=-.8*(1-smooth(d/2))
  h+=noise.noise_vector(Vector((x*.16,y*.16,1))).z*.07
  # Existing platform top and building floor heights remain fixed.
  for cx,cy,z,rr in [(-26,31,4.5,5),(0,36,7.5,4.4),(25,35,7.5,5)]:
   dd=math.hypot(x-cx,y-cy);w=smooth((rr+2-dd)/2)
   if w:h=h*(1-w)+(z-.14)*w
  for cx,cy,w,d in [(-11,-6.5,18,10.8),(12,-6.5,19.8,12.6),(20,16,7.2,5.4),(29,24,5.4,4.5)]:
   if abs(x-cx)<w/2+1 and abs(y-cy)<d/2+1:h=min(h,-.12)
  if -13.3<x<15.3 and -2.3<y<20.3:h=min(h,-.12)
  if -23<x<25 and -22.5<y<-12.3:h=min(h,-.12)
  for r in routes:
   dd,t,dist=segdistance(x,y,r);core=r['width']/2+.18
   if dd<core+.65 and 'BRIDGE' not in r['route']:
    a,b=r['from'],r['to'];pad=r.get('landing_pad_m',0);u=max(0,min(1,(t*dist-pad)/(dist-pad*2))) if pad else t;target=a[2]+(b[2]-a[2])*u-.23
    if dd>core:target=target+(h-target)*smooth((dd-core)/.65)
    h=min(h,target)
  verts.append((x,y,h))
for j in range(N-1):
 for i in range(N-1):a=j*N+i;faces.append((a,a+1,a+N+1,a+N))
terrain=bpy.data.objects['TERRAIN_CONTINUOUS_BASE'];me=bpy.data.meshes.new('P3_CONTINUOUS_GRADED_GROUND');me.from_pydata(verts,[],faces);me.update();terrain.data=me;assign(terrain,M['Soil'])
for f in me.polygons:f.use_smooth=True
bvh=BVHTree.FromPolygons(verts,faces)
def ground(x,y):
 p,n,i,d=bvh.ray_cast(Vector((x,y,70)),Vector((0,0,-1)),200);return p.z if p else -12
# Separate coincident road / courtyard surfaces physically; a 12mm surfacing allowance.
roadprefix=tuple({r['route']+'_S' for r in routes})
for o in s.objects:
 if o.type=='MESH' and o.name.startswith(roadprefix):o.location.z+=.018
# Apply the full-world material family without moving buildings or furnishings.
for o in list(s.objects):
 if o.type not in ['MESH','CURVE','FONT'] or not hasattr(o.data,'materials') or not o.data.materials:continue
 old=o.data.materials[0].name
 if old.startswith('Clay_'):
  mat=M['Cedar'] if 'Structure' in old else M['Roof'] if 'Roof' in old else P['Rock'] if 'Stone' in old or 'Path' in old else M['Plaster'] if 'Plaster' in old else M['Paper'] if 'Paper' in old else M['Linen'] if 'Seat' in old else M['Darkwood'] if 'Trunk' in old else M['Ink'] if 'Dark' in old else M['Soil']
  assign(o,mat)
 if o.type=='MESH' and 'COLUMN' in o.name:
  for f in o.data.polygons:f.use_smooth=True
 if o.type=='MESH' and 'FULL_ROOF' in o.name:
  o.data.materials.clear();o.data.materials.append(M['Roof']);o.data.materials.append(M['Cedar'])
  for mod in o.modifiers:
   if mod.type=='SOLIDIFY':mod.material_offset=1
# Photography-based floor normal/roughness maps, UVs constrained to one individual plank in source atlas.
for o in s.objects:
 if o.type=='MESH' and '_P2_FLOOR_PLANK' in o.name:
  o.data=o.data.copy();o.data.materials.clear();o.data.materials.append(P['Wood']);uv=o.data.uv_layers.active;row=R.randrange(11);w=o.dimensions.x
  for f in o.data.polygons:
   for li in f.loop_indices:
    v=o.data.vertices[o.data.loops[li].vertex_index].co;uv.data[li].uv=((v.y+o.location.y)/3,(row+.1+(v.x/w+.5)*.75)/11)
# Source HDRI: actual CC0 sky, packed. Runtime world reconstruction remains explicit.
world=s.world.node_tree;env=next(n for n in world.nodes if n.type=='TEX_ENVIRONMENT');im=bpy.data.images.load(str(ROOT/'assets/polyhaven/kloofendal_48d_partly_cloudy_puresky_2k.hdr'),check_existing=True);im.pack();env.image=im
bg=next(n for n in world.nodes if n.type=='BACKGROUND');bg.inputs['Strength'].default_value=.45
sun=bpy.data.objects['DAY_SUN'];sun.rotation_euler=(.75,-.45,-.8);sun.data.energy=1.5;sun.data.color=(1,.91,.76);sun.data.angle=.10
s.view_settings.exposure=.3
# Photographic stone detail on existing close shore; retire spherical proxies, rebuild with low polygon rock modules.
rockmeshes=[]
for k in range(7):
 bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=3,radius=1);o=bpy.context.object;o.name='P3_ROCK_MODULE_'+str(k)
 for cc in list(o.users_collection):cc.objects.unlink(o)
 LIB.objects.link(o)
 for v in o.data.vertices:
  n=noise.noise_vector(v.co*2.4+Vector((k*1.7,0,0)));v.co*=1+.18*n.x;v.co.x+=.12*n.y;v.co.z=max(-.62,v.co.z+.15*n.z)
 assign(o,P['Rock'])
 for f in o.data.polygons:f.use_smooth=True
 rockmeshes.append(o.data);hide(o)
for o in list(s.objects):
 if o.name.startswith(('BANK_ROCK','CASCADE_BANK','Z03_P2_SHORE_STONE','Z11_EAST_GARDEN_ROCK')):
  if o.type!='MESH':continue
  p=o.location.copy();sc=o.scale.copy();instance('SM_ROCK_REPLACEMENT',rockmeshes[R.randrange(7)],p,sc,ROCK,(R.uniform(-.2,.2),R.uniform(-.2,.2),R.random()*math.tau));hide(o)
# Extra bank clusters follow shoreline and leave the actual path envelope clear.
for a,b in zip(pond,pond[1:]+pond[:1]):
 length=math.dist(a,b)
 for j in range(max(2,int(length/.75))):
  t=(j+.5)/max(2,int(length/.75));x=a[0]+t*(b[0]-a[0]);y=a[1]+t*(b[1]-a[1]);rr=R.uniform(.4,.8)
  if clear(x,y,rr):instance('SM_Z02_NATURAL_BANK_STONE',rockmeshes[R.randrange(7)],(x,y,ground(x,y)-.15),(rr,R.uniform(.4,.9),R.uniform(.35,.65)),ROCK,(0,0,R.random()*math.tau))
# Irregular low stone parapets replace tube-like bridge rails; same deck, span and walking clearance.
for o in s.objects:
 if o.name.startswith(('Z02_LOWER_BRIDGE_LOW_EDGE','Z02_UPPER_BRIDGE_LOW_EDGE')):hide(o)
for name in ['Z02_LOWER_BRIDGE_DECK','Z02_UPPER_BRIDGE_DECK']:
 deck=bpy.data.objects[name];assign(deck,P['Rock']);v=[deck.matrix_world@p.co for p in deck.data.vertices]
 for side in [0,1]:
  for i in range(24):
   a=v[i*4+side];b=v[(i+1)*4+side];mid=(a+b)/2;length=(b-a).length;o=box('SM_Z02_BRIDGE_STONE_PARAPET',(mid.x,mid.y,mid.z+.13),(length+.012,.16,.24),T,P['Rock'],.025);o.rotation_euler.y=-math.atan2(b.z-a.z,b.x-a.x)
# Water flow surfaces, not equal-spaced vertical bars.
for o in list(s.objects):
 if o.name.startswith(('Z02_P2_WATER_FLOW','Z02_P2_FLOW_FOAM')):hide(o)
for o in list(s.objects):
 if o.name.startswith('Z02_CASCADE_') and '_FACE' in o.name:hide(o)
fallmat=material('P3_FALLING_WATER',(.28,.48,.44),None,.13,.15);foam=material('P3_AERATED_WATER',(.68,.82,.76),None,.3)
levels=[(-34,33,5,6,3.6),(-32,29,5.5,3.6,1.2),(-30,25,6,1.2,-1.2)]
for k,(cx,cy,w,top,bottom) in enumerate(levels):
 vs=[];fs=[];uv=[];nx,nz=30,22
 for j in range(nz+1):
  t=j/nz;z=top+(bottom-top)*t
  for i in range(nx+1):
   u=i/nx;x=cx+(u-.5)*w+.05*math.sin(t*18+u*30);y=cy-1.41-.07*math.sin(t*9+u*20);vs.append((x,y,z));uv.append((u,t))
 for j in range(nz):
  for i in range(nx):a=j*(nx+1)+i;fs.append((a,a+1,a+nx+2,a+nx+1))
 ob=mesh('SM_Z02_WATERFALL_SHEET_'+str(k),vs,fs,T,fallmat,uv)
 for f in ob.data.polygons:f.use_smooth=True
 for j in range(17):
  x=cx+R.uniform(-w*.48,w*.48);width=R.uniform(.025,.15);vs=[];fs=[]
  for ii in range(17):
   t=ii/16;xx=x+.05*math.sin(t*15+j)+.04*math.sin(t*35+j*2);z=top+(bottom-top)*t;ww=width*(.7+.4*math.sin(t*20+j));y=cy-1.51-.06*math.sin(t*9+j)
   vs.extend([(xx-ww,y,z),(xx+ww,y,z)])
  for ii in range(16):a=ii*2;fs.append((a,a+1,a+3,a+2))
  mesh('SM_Z02_FOAM_TRAIL',vs,fs,T,foam)
 if k<len(levels)-1:
  xx,yy,ww,tt,bb=levels[k+1];mesh('SM_Z02_STREAM_CONNECTOR',[(cx-w/2,cy-1.42,bottom+.012),(cx+w/2,cy-1.42,bottom+.012),(xx+ww/2,yy+1.6,bottom+.012),(xx-ww/2,yy+1.6,bottom+.012)],[(0,1,2,3)],T,M['Water'])
 # Rock ledges flank flowing faces, same water channel remains visible.
 for sign in [-1,1]:
  for j in range(4):
   x=cx+sign*(w/2+.25)+R.uniform(-.15,.15);y=cy-1.35;z=bottom+(top-bottom)*(j+.5)/4
   if clear(x,y,.5):instance('SM_Z02_CASCADE_LEDGE',rockmeshes[(j+k)%7],(x,y,z),(R.uniform(.6,1.0),.7,(top-bottom)/5+.15),ROCK,(0,0,R.random()*math.tau))
# Original geometric water ripples: non-metal water, fine normals remain in editable mesh.
water=next(n for n in M['Water'].node_tree.nodes if n.type=='BSDF_PRINCIPLED');water.inputs['Base Color'].default_value=(.045,.15,.13,1);water.inputs['Metallic'].default_value=.05;water.inputs['Roughness'].default_value=.085;water.inputs['IOR'].default_value=1.333
print('P3_GROUND_AND_WATER_DONE',flush=True)
# Small reusable ground plants: fern fronds and narrow grass blades, with actual silhouettes.
greens=[material('P3_GREEN_'+str(i),col,None,.78) for i,col in enumerate([(.13,.23,.055),(.23,.33,.075),(.31,.39,.10),(.10,.19,.065),(.30,.35,.12)])]
plantmeshes=[]
for variant in range(4):
 vs=[];fs=[]
 for k in range(8):
  a=k*math.tau/8+R.uniform(-.15,.15);length=R.uniform(.28,.55);end=Vector((math.cos(a)*length,math.sin(a)*length,R.uniform(.25,.50)))
  for j in range(1,10):
   t=j/10;p=Vector((end.x*t,end.y*t,end.z*math.sin(t*math.pi*.65)));size=.085*math.sin(t*math.pi)
   for sign in [-1,1]:
    side=Vector((-math.sin(a)*size*sign,math.cos(a)*size*sign,.01));tip=p+side-Vector((math.cos(a),math.sin(a),-.1))*size*.2;base=len(vs);vs.extend([p-Vector((math.cos(a),math.sin(a),0))*.025,tip,p+Vector((math.cos(a),math.sin(a),0))*.025]);fs.append((base,base+1,base+2))
 ob=mesh('P3_FERN_MODULE_'+str(variant),vs,fs,LIB,greens[variant]);plantmeshes.append(ob.data);hide(ob)
# Blade grass, distinct shape from broad leaf canopy.
vs=[];fs=[]
for k in range(30):
 a=R.random()*math.tau;rr=R.random()*.15;p=Vector((math.cos(a)*rr,math.sin(a)*rr,0));length=R.uniform(.18,.44);side=Vector((-math.sin(a),math.cos(a),0))*.012;tip=p+Vector((math.cos(a)*length*.3,math.sin(a)*length*.3,length));n=len(vs);vs.extend([p-side,p+side,tip]);fs.append((n,n+1,n+2))
ob=mesh('P3_GRASS_MODULE',vs,fs,LIB,greens[2]);grass=ob.data;hide(ob)
# Low shrubs reuse fine leaf cards; scale makes varied plant families, not carpet wallpaper.
leaf=bpy.data.meshes['P2_FINE_LEAF_CLUSTER_SHARED']
placements=0
for i in range(2200):
 x=R.uniform(-49,43);y=R.uniform(-24,46)
 if inpond(x,y) or not clear(x,y,.65):continue
 if -23<x<25 and -23<y<-12:continue
 if -13.6<x<15.4 and -2.6<y<20.6:continue
 if any(abs(x-cx)<w/2+1.2 and abs(y-cy)<d/2+1.2 for cx,cy,w,d in [(-11,-6.5,18,10.8),(12,-6.5,19.8,12.6),(20,16,7.2,5.4),(29,24,5.4,4.5)]):continue
 z=ground(x,y)
 if z<-.6:continue
 # Concentrate around water, building edges and slopes; preserve broad quiet openings.
 sd=shore_distance(x,y);chance=.78 if sd<4 else .45 if y>25 else .2
 if R.random()>chance:continue
 scale=R.uniform(.6,1.2);data=grass if i%3==0 else plantmeshes[i%4]
 instance('SM_GROUND_FERN' if i%3 else 'SM_GROUND_GRASS',data,(x,y,z+.02),(scale,scale,scale),VEG,(0,0,R.random()*math.tau));placements+=1
 if i%4==0:instance('SM_LOW_SHRUB',leaf,(x,y,z+.35),(R.uniform(.7,1.1),.8,.7),VEG,(0,0,R.random()*math.tau))
# Floating water leaves, excluding the island land and continuous walking surfaces.
lilymat=material('P3_WATER_LEAF',(.25,.38,.09),None,.35)
vs=[(0,0,.002)]+[(math.cos(a)*.12,math.sin(a)*.12,0) for a in [i*math.tau/14+.2 for i in range(14)]];fs=[(0,i,i+1) for i in range(1,14)]
ob=mesh('P3_LILY_MODULE',vs,fs,LIB,lilymat);lily=ob.data;hide(ob)
for i in range(460):
 x=R.uniform(-42,-17);y=R.uniform(-12,25)
 if inpond(x,y) and ground(x,y)<-1.35 and shore_distance(x,y)<4.5 and math.hypot(x+28,y-3)>3.3:
  instance('SM_Z02_FLOATING_LEAF',lily,(x,y,-1.182),(R.uniform(.65,1.3),R.uniform(.6,1.2),1),VEG,(0,0,R.random()*math.tau))
# Replace explicit tree proxies with a trunk/branch module and shared foliage. All roots remain fixed.
def tube(n,points,radii,c,mat,segments=10):
 vs=[];fs=[]
 for k,(p,rr) in enumerate(zip(points,radii)):
  p=Vector(p);t=(Vector(points[min(k+1,len(points)-1)])-Vector(points[max(k-1,0)])).normalized();u=t.cross(Vector((0,1,0))).normalized();v=t.cross(u).normalized()
  for j in range(segments):
   a=j*math.tau/segments;vs.append(p+(u*math.cos(a)+v*math.sin(a))*rr*(1+.055*math.sin(j*2.6+k)))
 for k in range(len(points)-1):
  for j in range(segments):a=k*segments+j;b=k*segments+(j+1)%segments;fs.append((a,b,b+segments,a+segments))
 ob=mesh(n,vs,fs,c,mat)
 for f in ob.data.polygons:f.use_smooth=True
 return ob
bark=material('P3_OLD_BARK',(.18,.15,.10),'darkwood.png',.93)
def tree(prefix,x,y,z,height,radius,blossom=False):
 # Original generated proxy objects kept hidden for provenance; their roots/tags stay independently addressable.
 for o in list(s.objects):
  if o.name.startswith(prefix) and any(k in o.name for k in ['TRUNK','BRANCH','CANOPY','SPARSE_RED']):hide(o)
 tc=collection(prefix+'_P3',VEG);trunkr=.52 if height<7 else .85
 tube('SM_'+prefix+'_TRUNK',[(x,y,z),(x+.1,y-.1,z+height*.22),(x-.22,y+.1,z+height*.42),(x+.18,y,z+height*.63)], [trunkr,trunkr*.82,trunkr*.57,trunkr*.26],tc,bark,16)
 for k in range(6):
  a=k*math.tau/6;end=Vector((x+math.cos(a)*trunkr*2.1,y+math.sin(a)*trunkr*2.1,z+.03));tube('SM_'+prefix+'_ROOT',[(x,y,z+.5),end], [trunkr*.42,.035],tc,bark,10)
 foliage_positions=[]
 for k in range(11):
  a=k*2.399963;p0=Vector((x,y,z+height*.38));p1=Vector((x+math.cos(a)*radius*.4,y+math.sin(a)*radius*.4,z+height*(.6+R.random()*.1)));p2=Vector((x+math.cos(a)*radius*.8,y+math.sin(a)*radius*.8,z+height*(.66+R.random()*.15)))
  tube('SM_'+prefix+'_BRANCH',[p0,p1,p2],[trunkr*.36,.16,.035],tc,bark,10)
  for j in range(16):
   aa=R.random()*math.tau;rr=math.sqrt(R.random())*radius*.43;pp=p2+Vector((math.cos(aa)*rr,math.sin(aa)*rr,R.uniform(-.55,.8)));foliage_positions.append(pp)
   instance('SM_'+prefix+'_FOLIAGE',leaf,pp,(1.0,1.0,1.0),tc,(R.uniform(-.4,.4),R.uniform(-.4,.4),aa))
  for sign in [-1,1]:
   tip=p2+Vector((-math.sin(a)*radius*.3*sign,math.cos(a)*radius*.3*sign,.3));tube('SM_'+prefix+'_TWIG',[p1,tip],[.065,.015],tc,bark,8)
 if blossom:
  # Sparse red flower clusters only on the main court tree.
  for j,p in enumerate(foliage_positions[::13]):
   for k in range(4):
    pos=p+Vector((R.uniform(-.2,.2),R.uniform(-.2,.2),.1));ob=instance('SM_Z01_SPARSE_RED_FLOWER',rockmeshes[k%7],pos,(.035,.035,.035),tc,(0,0,k))
    ob.data=ob.data.copy();ob.data.materials.clear();ob.data.materials.append(M['Blossom'])
 return tc
# Retire earlier replacement canopy clusters before the coherent hero-tree pass.
for o in list(s.objects):
 if o.name.startswith(('Z01_P2_LEAF_CLUSTER','Z01_P2_FINE_BRANCH','Z01_P2_SPARSE_BLOSSOM','Z02_P2_UPSTREAM_CANOPY')):hide(o)
tree('Z01_COURTYARD_TREE',0,9,0,6,4,True)
tree('Z08_WISH_TREE',-26,31,4.5,6,4)
tree('Z09_LANDSCAPE_TREE',0,36,7.5,9,6)
tree('Z11_GARDEN_TREE',35,25,0,4,2)
# Background pine silhouettes with real needle tiers, shared data instances per tree.
needle=material('P3_PINE_NEEDLE',(.095,.19,.075),None,.9);v=[];f=[]
for j in range(90):
 a=R.random()*math.tau;rr=R.random()*.5;z=R.uniform(-.1,.18);p=Vector((math.cos(a)*rr,math.sin(a)*rr,z));d=Vector((-math.sin(a),math.cos(a),.4))*.06;k=len(v);v.extend([p,p+d,p+Vector((math.cos(a)*.12,math.sin(a)*.12,.02))]);f.append((k,k+1,k+2))
ob=mesh('P3_PINE_TUFT_MODULE',v,f,LIB,needle);pine=ob.data;hide(ob)
# Old distant proxy canopies no longer render.
for o in s.objects:
 if o.name.startswith('DISTANT_TREE_') and any(t in o.name for t in ['CANOPY','TRUNK','BRANCH']):hide(o)
for i in range(52):
 x=R.uniform(-58,47);y=R.uniform(27,62)
 if y<43 and not clear(x,y,1.3):continue
 if y<43 and (math.hypot(x+26,y-31)<6.2 or math.hypot(x,y-36)<7 or math.hypot(x-25,y-35)<6.5):continue
 z=ground(x,y);h=R.uniform(3,6);r=R.uniform(1,1.7);beam('SM_BACKGROUND_PINE_TRUNK',(x,y,z),(x,y,z+h*.92),.08,VEG,bark,8)
 for tier in range(6):
  tz=z+h*(.26+tier*.115);rr=r*(1-tier*.14)
  for k in range(8):
   a=k*math.tau/8+tier*.4;instance('SM_BACKGROUND_PINE_TUFT',pine,(x+math.cos(a)*rr*.5,y+math.sin(a)*rr*.5,tz),(rr*1.7,rr*1.7,1.2),VEG,(.2*math.cos(a),.2*math.sin(a),a))
# Backdrop ridges recede behind the actual garden, do not touch functional terrain.
for o in s.objects:
 if o.type=='MESH' and o.name.startswith(('DISTANT_MOUNTAIN','WEST_MOUNTAIN','EAST_MOUNTAIN')):
  o.data=o.data.copy()
  for v in o.data.vertices:v.co.x*=2.5;v.co.y*=2.5;v.co.z*=1.25
for o in s.objects:
 if o.type=='CAMERA':o.data.clip_end=2000
# Recompose the island toward the actual two halls; bridge remains in its true shared position.
cam=bpy.data.objects['CAM_ISLAND_SEATED'];cam.location=(-28.3,2.9,.35);cam.rotation_euler=(Vector((-8,-8.0,1.5))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.lens=23
# Bookcase is lit from a real north-window opening; no camera-only spotlight.
light('P3_LIBRARY_NORTH_WINDOW',(-5.4,-1.0,2.4),280,(1,.90,.73),bpy.data.collections['60_LIGHTING'],2.5,target=(-2.7,-6.2,1.2))
s['version']='p3-v001';s['phase_status']='P3_LANDSCAPE_REVIEW_PENDING';s['visual_scope']='Full-world materials, natural grading and landscape; detailed props/states remain';s.camera=bpy.data.objects['CAM_WORLD_FULL'];s.cycles.samples=32
bpy.ops.wm.save_as_mainfile(filepath=str(OUT),compress=True)
(ROOT/'evidence/phase3-landscape-build.json').write_text(json.dumps({'source_version':'p3-v001','base':'p2-v002','terrain_vertices':len(verts),'route_geometry':'same routes, 18mm surfacing elevation to remove coincident faces','ground_plants':placements,'landscape_objects':len(C.all_objects),'visible_edits':['natural pond shore slope','continuous 3-tier stream connectors','stone parapets instead of rail tubes','coherent hero tree branches and foliage','finer ferns, grasses and floating water leaves','packed CC0 HDRI and wood/rock PBR','bookcase daylight from existing window','island camera directed to physically existing halls'],'pending':['all-region visual review','full-model path recheck','detailed props, state animation and clips','full scene export']},ensure_ascii=False,indent=2))
print('P3_LANDSCAPE_SAVED',OUT,flush=True)
