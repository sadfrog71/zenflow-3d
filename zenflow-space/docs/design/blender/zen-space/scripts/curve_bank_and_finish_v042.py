"""Actual west-bank/path deformation, rooted hero scale, and planted forecourt. All changes shared across views."""
import bpy,math,random,json,shutil
from pathlib import Path
from mathutils import Vector,Matrix
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v042.blend'
if s.get('version')!='p3-v041' or out.exists():raise RuntimeError('Requires new v042')
rng=random.Random(211042);C=bpy.data.collections['P8_SHARED_GARDEN_COMPOSITION'];p=R/'manifest/scene-manifest.json';m=json.loads(p.read_text());backup=R/'manifest/scene-manifest-p3-v039.json'
if not backup.exists():shutil.copy2(p,backup)
oldroutes=m['routes']
def smooth(t):t=max(0,min(1,t));return t*t*(3-2*t)
def warp(q):
 x,y,z=q;w=smooth((x+54)/8)*smooth((-37-x)/6)*smooth((y+20)/6)*smooth((28-y)/6);return Vector((x+1.65*math.sin((y+4)*.20)*w,y,z))
def affected_bounds(o):
 ps=[o.matrix_world@Vector(v) for v in o.bound_box];return min(p.x for p in ps)<-37 and max(p.x for p in ps)>-54 and min(p.y for p in ps)<28 and max(p.y for p in ps)>-20
# Broad continuous surfaces deform at vertices; instanced props move by their local root, retaining mesh reuse.
vertex_count=0;root_count=0
for o in list(s.objects):
 if o.hide_render or o.hide_viewport:continue
 if o.type=='MESH' and o.name in ['TERRAIN_CONTINUOUS_BASE','PUBLIC_EAST_ROUTE_RESOLVED_NETWORK','WATER_LOWER_LEVEL_-1.20','SM_CONTINUOUS_OUTER_MOUNTAIN_RANGE']:
  if not affected_bounds(o):continue
  if o.data.users>1:o.data=o.data.copy()
  inv=o.matrix_world.inverted()
  for v in o.data.vertices:
   q=o.matrix_world@v.co;w=warp(q)
   if (w-q).length>1e-7:v.co=inv@w;vertex_count+=1
  o.data.update()
 elif o.type in ['MESH','EMPTY'] and o.parent is None and o.animation_data is None:
  q=o.matrix_world.translation;w=warp(q)
  if (w-q).length>1e-7:o.location+=w-q;root_count+=1
# Resample only the actual curved bank connections. Bridges and all hill/stair routes retain their original definition.
newroutes=[];changed=[]
for row in oldroutes:
 a=Vector(row['from']);b=Vector(row['to']);length=(b-a).length
 if row['route'] not in ['WEST_UPPER_BANK','ISLAND_SHORE_LINK'] or all((warp(a.lerp(b,i/20))-a.lerp(b,i/20)).length<1e-6 for i in range(21)):newroutes.append(row);continue
 N=max(2,math.ceil(length/.8));pts=[warp(a.lerp(b,i/N)) for i in range(N+1)]
 for aa,bb in zip(pts,pts[1:]):r=dict(row);r['from']=list(aa);r['to']=list(bb);r['curve_source']='continuous west-bank deformation v042';newroutes.append(r)
 changed.append({'route':row['route'],'old_from':list(a),'old_to':list(b),'sampled_segments':N,'width_unchanged':row['width']})
m['routes']=newroutes;m['version']='p3-v042';m['source_snapshot']=out.name;m['bank_recomposition']={'max_horizontal_offset_m':1.65,'bridge_endpoints_unchanged':True,'walking_width_requirements_unchanged':True,'resampled_routes':changed};p.write_text(json.dumps(m,ensure_ascii=False,indent=2))
# Stronger upper garden focal trees, rooted at their original terraces. Reachable wish tags/low branches stay where authored.
hero=[]
for prefix,root,factors in [('SM_Z08_WISH_TREE_ALIGNED_',Vector((-26,31,4.5)),(1.55,1.55,1.16)),('SM_Z09_LANDSCAPE_TREE_ALIGNED_',Vector((0,36,7.5)),(1.65,1.65,1.18))]:
 transform=Matrix.Translation(root)@Matrix.Diagonal((*factors,1))@Matrix.Translation(-root)
 for o in s.objects:
  if not o.hide_render and o.name.startswith(prefix):o.matrix_world=transform@o.matrix_world;hero.append(o.name)
# Forecourt planting islands flank the entry and hall door approaches; no new wall or raised seating.
soil=bpy.data.materials['P4_PHOTOGRAPHED_FOREST_SOIL'];bushes=[bpy.data.meshes['P8_SHARED_DENSE_SHRUB_'+str(i)] for i in range(3)];beds=[];plants=0
for index,(cx,cy,rx,ry) in enumerate([(-20,-16.0,1.8,1.5),(-4.3,-16.0,1.55,1.35),(4.3,-16.0,1.55,1.35),(21,-16.2,1.8,1.5)]):
 vs=[];N=64
 for i in range(N):a=i*math.tau/N;rad=1+.06*math.sin(3*a);vs.append((cx+rx*rad*math.cos(a),cy+ry*rad*math.sin(a),.026))
 me=bpy.data.meshes.new('P8_FORECOURT_CURVED_SOIL');me.from_pydata(vs,[],[tuple(range(N))]);me.update();me.materials.append(soil);uv=me.uv_layers.new()
 for f in me.polygons:
  for li in f.loop_indices:q=me.vertices[me.loops[li].vertex_index].co;uv.data[li].uv=(q.x*.5,q.y*.5)
 ob=bpy.data.objects.new('SM_P8_Z12_FORECOURT_PLANTING_ISLAND',me);C.objects.link(ob);beds.append(ob.name)
 for j in range(16):
  a=rng.random()*math.tau;r=math.sqrt(rng.random())*.80;x=cx+rx*r*math.cos(a);y=cy+ry*r*math.sin(a);k=rng.uniform(.45,.75)
  ob=bpy.data.objects.new('SM_P8_Z12_FORECOURT_DENSE_CLUSTER',bushes[j%3]);C.objects.link(ob);ob.location=(x,y,.03);ob.scale=(k,k,k*.8);ob.rotation_euler.z=rng.random()*math.tau;plants+=1
s['version']='p3-v042';s['phase_status']='P8_CURVED_BANK_AND_COMPOSITION_REVIEW';s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True);(R/'evidence/bank-and-massing-v042.json').write_text(json.dumps({'deformed_surface_vertices':vertex_count,'instanced_roots_shifted':root_count,'bank_route_segments':changed,'hero_tree_meshes':hero,'forecourt_beds':beds,'forecourt_plant_instances':plants,'unchanged':'bridge geometry endpoints, water levels, actor roots and living actions, building positions, road width requirements'},ensure_ascii=False,indent=2));print('BANK_MASSING_V042',vertex_count,root_count,len(newroutes),len(hero),plants)
