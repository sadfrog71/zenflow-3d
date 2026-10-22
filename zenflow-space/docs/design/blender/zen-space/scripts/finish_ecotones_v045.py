"""Repair remap contacts and add clustered landscape transitions using shared existing meshes."""
import bpy,bmesh,math,json,random
from pathlib import Path
from mathutils import Vector,Matrix
from mathutils.bvhtree import BVHTree
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v045.blend';assert s.get('version')=='p3-v044' and not out.exists();rng=random.Random(211045)
# Use the coherent planar map on terrain; only the separate walk mesh protects authored stair heights.
ns={};text=(R/'scripts/recompose_scale_and_material_v044.py').read_text();exec('import math\nfrom mathutils import Vector\n'+text[text.index('def sm('):text.index('def distance(')],ns);warp=ns['warp']
with bpy.data.libraries.load(str(R/'zen-space-p3-v042.blend'),link=False) as (a,b):b.objects=['TERRAIN_CONTINUOUS_BASE']
old=b.objects[0];terrain=s.objects['TERRAIN_CONTINUOUS_BASE'];inv=terrain.matrix_world.inverted()
for dst,src in zip(terrain.data.vertices,old.data.vertices):dst.co=inv@warp(old.matrix_world@src.co)
bpy.data.objects.remove(old,do_unlink=True);terrain.data.update()
# Remove zero-area faces introduced by adaptive path refinement; preserve supported surface faces.
net=s.objects['PUBLIC_EAST_ROUTE_RESOLVED_NETWORK'];bm=bmesh.new();bm.from_mesh(net.data);bad=[f for f in bm.faces if f.calc_area()<1e-10];removed=len(bad);bmesh.ops.delete(bm,geom=bad,context='FACES_ONLY');bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=1e-7);bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.to_mesh(net.data);bm.free();net.data.update()
rock=s.objects['SM_P7_POND_PATH_RUBBLE_SUPPORT.087'];rock.location.z-=.08
m=json.loads((R/'manifest/scene-manifest.json').read_text());routes=m['routes'];C=bpy.data.collections.new('P9_ECOTONE_CLUSTERED_DRESSING');s.collection.children.link(C)
bvh=BVHTree.FromPolygons([terrain.matrix_world@v.co for v in terrain.data.vertices],[tuple(f.vertices) for f in terrain.data.polygons])
def ground(x,y):
 hit,n,i,d=bvh.ray_cast(Vector((x,y,50)),Vector((0,0,-1)),150);return hit.z if hit else None

def segdist(x,y,a,b):
 dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/max(1e-8,dx*dx+dy*dy)));return math.hypot(x-a[0]-t*dx,y-a[1]-t*dy)
def clear(x,y,r):
 if not all(segdist(x,y,row['from'],row['to'])>row['width']/2+r for row in routes):return False
 # Conservatively protect all existing architectural foundation rectangles and open courtyard.
 return not (-24<x<26 and -23<y<23) and not (-29<x<0 and -15<y<1)
bush=[bpy.data.meshes['P8_SHARED_DENSE_SHRUB_'+str(i)] for i in range(3)];fern=[bpy.data.meshes['P3_FERN_MODULE_'+str(i)] for i in range(4) if 'P3_FERN_MODULE_'+str(i) in bpy.data.meshes]
clusters=[(-27,-27,4,2.4),(25,-27,4.5,2.4),(33,-13,5,3.7),(36,1,5,4),(32,15,4.5,4),(-42,-15,4,3),(-47,1,3,5),(-40,28,5,3)]
placed=[]
for k,(cx,cy,rx,ry) in enumerate(clusters):
 for j in range(39):
  a=rng.random()*math.tau;t=math.sqrt(rng.random());x=cx+math.cos(a)*rx*t;y=cy+math.sin(a)*ry*t;z=ground(x,y)
  if z is None or z<-.45 or not clear(x,y,.65):continue
  meshes=fern if j%3==0 else bush;data=meshes[j%len(meshes)];o=bpy.data.objects.new('SM_P9_ECOTONE_UNDERSTORY',data);C.objects.link(o);o.location=(x,y,z+.01);size=rng.uniform(.75,1.25) if j%3==0 else rng.uniform(.65,1.25);o.scale=(size,size,size*rng.uniform(.65,.95));o.rotation_euler.z=rng.random()*math.tau;placed.append(o.name)
# Three restrained framing saplings reuse all original tree parts with a single root transform per tree.
sources=[s.objects['SM_Z11_GARDEN_TREE_ALIGNED_'+suffix] for suffix in ['TRUNK','BRANCHES','LEAVES']];pts=[sources[0].matrix_world@v.co for v in sources[0].data.vertices];zmin=min(p.z for p in pts);feet=[p for p in pts if p.z<zmin+.05];root=sum(feet,Vector())/len(feet);allpts=[o.matrix_world@Vector(v) for o in sources for v in o.bound_box];h=max(v.z for v in allpts)-root.z;trees=[]
for x,y,height in [(-29,-27,4.3),(29,-27,4.9),(36,2,5.2)]:
 z=ground(x,y)
 if z is None or not clear(x,y,1.5):continue
 scale=height/h;transform=Matrix.Translation(Vector((x,y,z)))@Matrix.Rotation(rng.random()*math.tau,4,'Z')@Matrix.Scale(scale,4)@Matrix.Translation(-root)
 for src in sources:
  o=bpy.data.objects.new('SM_P9_FRAMING_SAPLING_'+src.name.rsplit('_',1)[-1],src.data);C.objects.link(o);o.matrix_world=transform@src.matrix_world;trees.append(o.name)
s['version']='p3-v045';s['phase_status']='P9_SCALE_MATERIAL_ECOTONE_REVIEW';m['version']='p3-v045';m['source_version']='p3-v045';m['source_snapshot']=out.name;m['quality_review_v045']={'understory_instances':len(placed),'sapling_mesh_instances':len(trees),'zero_area_path_faces_removed':removed,'terrain_map':'coherent XY map, preserves all z values and outer boundary'};(R/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2));s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
(R/'evidence/ecotone-finish-v045.json').write_text(json.dumps({'version':'p3-v045','understory_instances':placed,'sapling_mesh_instances':trees,'removed_zero_area_faces':removed,'rock_contact_lowered_m':.08,'architecture_unchanged_in_this_finish':True},ensure_ascii=False,indent=2));print('ECOTONES',len(placed),len(trees),removed,flush=True)
