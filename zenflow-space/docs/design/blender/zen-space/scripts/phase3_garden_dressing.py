"""Natural plant clusters and existing CC0 material reuse, all in same shared world."""
import bpy,sys,math,random,json
from pathlib import Path
from mathutils import Vector
from mathutils.bvhtree import BVHTree
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
from phase2_materials import box,beam,assign,material
s=bpy.context.scene;OUT=ROOT/'zen-space-p3-v004.blend';R=random.Random(202610206)
if s.get('version')!='p3-v003' or OUT.exists():raise RuntimeError('Requires p3-v003/new output')
c=bpy.data.collections.new('P3_GARDEN_DRESSING');s.collection.children.link(c);lib=bpy.data.collections.new('P3_CC0_PLANT_LIBRARY');s.collection.children.link(lib);m=json.loads((ROOT/'manifest/scene-manifest.json').read_text());routes=m['routes'];rockmat=bpy.data.materials['P3_Weathered_Gray_Rock'];cedar=bpy.data.materials['P2_Cedar'];leafmat=bpy.data.materials['P2_Leaf']
# Reuse existing project's licensed maps, with unchanged checksums recorded separately.
def image(fn,noncolor=False):
 im=bpy.data.images.load(str(ROOT/'assets/polyhaven/reused-white-oak'/fn),check_existing=True);im.pack()
 if noncolor:im.colorspace_settings.name='Non-Color'
 return im
wood=bpy.data.materials['P3_Honey_Wood'];nodes=wood.node_tree.nodes
for n in nodes:
 if n.type=='TEX_IMAGE':
  old=n.image.name
  if 'diff' in old or 'derived' in old:n.image=image('diff.jpg')
  elif 'nor' in old:n.image=image('nor_gl.jpg',True)
  elif 'rough' in old:n.image=image('rough.jpg',True)
for n in cedar.node_tree.nodes:
 if n.type=='TEX_IMAGE':n.image=image('diff.jpg')
# Fine real leaf veins replace the synthetic oval map; same UV cards and existing geometry.
photo=bpy.data.images.load(str(ROOT/'assets/textures/leaf-photo-cc0.png'),check_existing=True);photo.pack()
for n in leafmat.node_tree.nodes:
 if n.type=='TEX_IMAGE':n.image=photo
# Grain follows each plank's long axis and each column's local axial geometry.
seen=set()
for o in s.objects:
 if o.type!='MESH' or not o.data.materials or o.data.name in seen:continue
 mat=o.data.materials[0]
 if mat not in [cedar,wood]:continue
 seen.add(o.data.name);me=o.data
 if not me.uv_layers:me.uv_layers.new()
 uv=me.uv_layers.active;ranges=[max(v.co[k] for v in me.vertices)-min(v.co[k] for v in me.vertices) for k in range(3)];longaxis=max(range(3),key=lambda k:ranges[k])
 for f in me.polygons:
  normalaxis=max(range(3),key=lambda k:abs(f.normal[k]));axes=[k for k in range(3) if k!=normalaxis];vaxis=longaxis if longaxis in axes else axes[-1];uaxis=next(k for k in axes if k!=vaxis)
  off=R.random()
  for li in f.loop_indices:
   p=me.vertices[me.loops[li].vertex_index].co;uv.data[li].uv=(p[uaxis]*.6+off,p[vaxis]*.35)
# Ink titles are printed surfaces rather than raised carved lettering.
for o in s.objects:
 if o.type=='FONT' and o.name.startswith('TITLE_'):o.data.extrude=0;o.data.bevel_depth=0
# Four real fern meshes, imported without executing embedded scripts. Local 1k maps are packed.
before=set(s.objects);bpy.ops.import_scene.gltf(filepath=str(ROOT/'assets/polyhaven/fern_02/fern_02_1k.gltf'));ferns=[]
for o in set(s.objects)-before:
 if o.type!='MESH':continue
 # Normalize each variant to its own stem base, retaining full frond shape/UV/material.
 o.data=o.data.copy();pts=[o.matrix_world@v.co for v in o.data.vertices];xmin=min(v.x for v in pts);xmax=max(v.x for v in pts);ymin=min(v.y for v in pts);ymax=max(v.y for v in pts);zmin=min(v.z for v in pts);center=Vector(((xmin+xmax)/2,(ymin+ymax)/2,zmin))
 for v,p in zip(o.data.vertices,pts):v.co=p-center
 o.matrix_world.identity()
 for cc in list(o.users_collection):cc.objects.unlink(o)
 lib.objects.link(o);o.hide_render=True;o.hide_viewport=True;o['source']='https://polyhaven.com/a/fern_02';o['license']='CC0';ferns.append(o.data)
for im in bpy.data.images:
 if im.source=='FILE' and not im.packed_file:
  try:im.pack()
  except RuntimeError:pass
terrain=bpy.data.objects['TERRAIN_CONTINUOUS_BASE'];bvh=BVHTree.FromPolygons([v.co for v in terrain.data.vertices],[tuple(f.vertices) for f in terrain.data.polygons])
def ground(x,y):
 p,n,i,d=bvh.ray_cast(Vector((x,y,60)),Vector((0,0,-1)),160);return p.z if p else -12

def dist(x,y,r):
 a,b=r['from'],r['to'];dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/(dx*dx+dy*dy)));return math.hypot(x-a[0]-t*dx,y-a[1]-t*dy)
def clear(x,y,radius):return all(dist(x,y,r)>r['width']/2+radius+.16 for r in routes)
def inside_building(x,y):return any(abs(x-cx)<w/2+1 and abs(y-cy)<d/2+1 for cx,cy,w,d in [(-11,-6.5,18,10.8),(12,-6.5,19.8,12.6),(20,16,7.2,5.4),(29,24,5.4,4.5)])
def inst(n,me,p,sc,rot=0):
 o=bpy.data.objects.new(n,me);c.objects.link(o);o.location=p;o.scale=sc;o.rotation_euler.z=rot;return o
leaf=bpy.data.meshes['P2_FINE_LEAF_CLUSTER_SHARED'];rocks=[bpy.data.meshes.get('P3_ROCK_MODULE_'+str(i)) for i in range(7)]
# Dense intentional bank clusters, not uniform scattered confetti.
centers=[(-43,-6),(-46,7),(-39,23),(-22,24),(-18,14),(-18,-10),(-29,-15),(-40,29),(-26,27),(-33,35),(-18,30),(-8,28),(17,28),(34,18),(36,27),(-22,-15),(22,-15)]
ferncount=0
for cx,cy in centers:
 for i in range(34):
  a=R.random()*math.tau;rr=math.sqrt(R.random())*2.4;x=cx+math.cos(a)*rr;y=cy+math.sin(a)*rr;scale=R.uniform(.35,.8)
  if inside_building(x,y) or not clear(x,y,scale*.7):continue
  if any(math.hypot(x-px,y-py)<rad for px,py,rad in [(-26,31,4.7),(0,36,4.2),(25,35,4.7)]):continue
  z=ground(x,y)
  if z<-.5:continue
  inst('SM_CC0_FERN_BANK',ferns[i%len(ferns)],(x,y,z+.012),(scale,scale,scale),a);ferncount+=1
  if i%4==0:inst('SM_BANK_SHRUB_CLUSTER',leaf,(x,y,z+.48),(1.1,1.1,.9),a)
# Small nonfunctional planted stone islets enrich the water garden; never extra meditation sites.
for cx,cy in [(-35,11),(-25,13),(-40,1)]:
 if not clear(cx,cy,1.6):continue
 for j in range(7):
  a=j*math.tau/7;x=cx+math.cos(a)*.65;y=cy+math.sin(a)*.65;inst('SM_Z02_PLANTED_ROCK_ISLET',rocks[j%7],(x,y,-.95),(R.uniform(.65,.95),R.uniform(.55,.85),R.uniform(.45,.65)),a)
 for j in range(8):
  a=j*math.tau/8;sc=R.uniform(.45,.7);inst('SM_Z02_ISLET_FERN',ferns[j%len(ferns)],(cx+math.cos(a)*.5,cy+math.sin(a)*.5,-.35),(sc,sc,sc),a)
 inst('SM_Z02_ISLET_LOW_TREE',leaf,(cx,cy,.4),(1.4,1.4,1.2),0)
# Calm courtyard corners: low stone planters create a real soil boundary over paving.
soil=bpy.data.materials['P2_Soil']
for cx,cy in [(-9,3),(10,3),(-8,16),(10,16)]:
 if not clear(cx,cy,1.35):continue
 for xx in [cx-1.1,cx+1.1]:box('SM_Z01_PLANTER_SIDE',(xx,cy,.16),(.14,1.9,.32),c,rockmat,.035)
 for yy in [cy-.9,cy+.9]:box('SM_Z01_PLANTER_END',(cx,yy,.16),(2.2,.14,.32),c,rockmat,.035)
 box('SM_Z01_PLANTER_SOIL',(cx,cy,.25),(2.08,1.68,.10),c,soil)
 for j in range(7):
  x=cx+R.uniform(-.8,.8);y=cy+R.uniform(-.6,.6);sc=R.uniform(.4,.65);inst('SM_Z01_COURT_FERN',ferns[j%len(ferns)],(x,y,.305),(sc,sc,sc),R.random()*math.tau)
# Front edging, with the central entrance kept completely clear.
for x in [i*2.8-21 for i in range(16)]:
 if abs(x)<3.2:continue
 for y in [-21.5,-12.8]:
  if inside_building(x,y) or not clear(x,y,.8):continue
  sc=.55;inst('SM_Z12_GREEN_EDGE',ferns[int(abs(x))%len(ferns)],(x,y,.015),(sc,sc,sc),R.random()*math.tau)
# Midground grove modules combine few leaves and a timber trunk, retaining reasonable geometry.
# They are context vegetation; the three named hero trees remain separate and editable.
modules=[];bark=bpy.data.materials['P3_OLD_BARK']
for variant in range(3):
 vs=[];fs=[];uvs=[];matids=[]
 # Narrow tapered trunk and broad crown branches, all authored in local coordinates.
 for rr,z in [(.13,0),(.10,1.4),(.045,3.0)]:
  for j in range(10):a=j*math.tau/10;vs.append((rr*math.cos(a),rr*math.sin(a),z));uvs.append((j/10,z/3))
 for k in range(2):
  for j in range(10):a=k*10+j;b=k*10+(j+1)%10;fs.append((a,b,b+10,a+10));matids.append(0)
 for j in range(18):
  a=R.random()*math.tau;rr=math.sqrt(R.random())*1.4;pos=Vector((math.cos(a)*rr,math.sin(a)*rr,2.8+R.uniform(-.2,1.3)));offset=len(vs)
  # Reduce card count for context LOD while retaining full photographed leaves.
  for vv in leaf.vertices[:160]:vs.append(pos+vv.co*.9)
  for f in leaf.polygons[:40]:fs.append(tuple(offset+i for i in f.vertices));matids.append(1)
  uv=leaf.uv_layers.active
  for vv in range(160):uvs.append((0,0))
 me=bpy.data.meshes.new('P3_MID_TREE_LOD_'+str(variant));me.from_pydata(vs,[],fs);me.update();me.materials.append(bark);me.materials.append(leafmat);uv=me.uv_layers.new()
 for f,idx in zip(me.polygons,matids):
  f.material_index=idx
  if idx==1:
   for li,co in zip(f.loop_indices,[(0,0),(1,0),(1,1),(0,1)]):uv.data[li].uv=co
  else:
   for li in f.loop_indices:uv.data[li].uv=uvs[me.loops[li].vertex_index]
 modules.append(me)
count=0
for i in range(210):
 x=R.uniform(-51,41);y=R.uniform(24,50);sc=R.uniform(.85,1.55)
 if inside_building(x,y) or not clear(x,y,1.7*sc):continue
 if any(math.hypot(x-px,y-py)<rad+2 for px,py,rad in [(-26,31,5),(0,36,6),(25,35,5)]):continue
 z=ground(x,y)
 if z<-.5:continue
 inst('SM_MIDGROUND_TREE_LOD',modules[i%3],(x,y,z),(sc,sc,sc),R.random()*math.tau);count+=1
# Natural low boulder clusters around hills and approach edges; no encroachment on paths.
for i in range(95):
 x=R.uniform(-48,39);y=R.uniform(24,43);sc=R.uniform(.35,.9)
 if not clear(x,y,sc+.2) or inside_building(x,y):continue
 if any(math.hypot(x-px,y-py)<rad-.5 for px,py,rad in [(-26,31,5),(0,36,4.4),(25,35,5)]):continue
 z=ground(x,y)
 if z<-.5:continue
 inst('SM_SLOPE_NATURAL_STONE',rocks[i%7],(x,y,z-.15),(sc,sc*.8,sc*.65),R.random()*math.tau)
s['version']='p3-v004';s['phase_status']='P3_DENSE_GARDEN_REVIEW_PENDING';s.camera=bpy.data.objects['CAM_WORLD_FULL'];bpy.ops.wm.save_as_mainfile(filepath=str(OUT),compress=True)
(ROOT/'evidence/phase3-garden-dressing.json').write_text(json.dumps({'version':'p3-v004','base':'p3-v003','CC0_fern_variants':len(ferns),'near_bank_ferns':ferncount,'midground_context_trees':count,'new_objects':len(c.objects),'reused_materials':'manifest/reused-materials.json','asset_sources':['https://polyhaven.com/a/fern_02','https://polyhaven.com/a/tree_small_02','https://polyhaven.com/a/white_oak_veneer'],'geometry_budget':'midground uses 18 x 40 leaf cards per shared module; hero trees retained separately','pending':['re-render all reference views','check added plant clearances','weather/state animation and final export']},ensure_ascii=False,indent=2));print('GARDEN_DRESSING_SAVED',OUT,flush=True)
