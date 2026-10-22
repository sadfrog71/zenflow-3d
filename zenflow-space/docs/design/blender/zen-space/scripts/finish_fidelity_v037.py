"""Resolve render-identified defects in candidate, keeping route envelopes and source actors."""
import bpy,math,random,json
from pathlib import Path
from mathutils import Vector
from mathutils.bvhtree import BVHTree
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v037.blend';rng=random.Random(211037)
if s.get('version')!='p3-v036' or out.exists():raise RuntimeError('Requires new v037')
C=bpy.data.collections['P7_REFERENCE_LANDSCAPE_REPAIR'];s.objects['SM_P7_MAIN_HALL_EAVE_TILE_ENDS'].hide_render=True;s.objects['SM_P7_MAIN_HALL_EAVE_TILE_ENDS'].hide_viewport=True
# Remove the visibly uniform stacked support, retaining the original walk geometry; replace it with varied nested stone groups.
obs=[o for o in s.objects if o.name.startswith('SM_P7_POND_PATH_RUBBLE_SUPPORT')]
for i,o in enumerate(obs):
 if i%4==1:o.hide_render=True;o.hide_viewport=True;continue
 top=max((o.matrix_world@Vector(v)).z for v in o.bound_box);o.scale=(rng.uniform(.55,1.0),rng.uniform(.55,.95),rng.uniform(.32,.6));o.rotation_euler=(rng.uniform(-.35,.35),rng.uniform(-.28,.28),rng.random()*math.tau);o.location.x+=rng.uniform(-.22,.22);o.location.y+=rng.uniform(-.18,.18);newtop=max((o.matrix_world@Vector(v)).z for v in o.bound_box);o.location.z+=min(0,top-newtop)
# The remaining big wall is Z08's skirt, diagnosed by camera rays. Break its front into interlocking outcrops.
terrain=s.objects['TERRAIN_CONTINUOUS_BASE'];bvh=BVHTree.FromPolygons([terrain.matrix_world@v.co for v in terrain.data.vertices],[tuple(f.vertices) for f in terrain.data.polygons]);routes=json.loads((R/'manifest/scene-manifest.json').read_text())['routes']
def dist(x,y,a,b):
 dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/max(1e-9,dx*dx+dy*dy)));return math.hypot(x-a[0]-t*dx,y-a[1]-t*dy)
def clear(x,y,r):return all(dist(x,y,row['from'],row['to'])>row['width']/2+r+.32 for row in routes)
meshes=[]
for o in s.objects:
 if o.name.startswith('SM_Z02_NATURAL_BANK_STONE') and o.data not in meshes:meshes.append(o.data)
meshes=meshes[:7];count=0
for i in range(80):
 a=math.pi+math.pi*i/79;radius=rng.uniform(4.6,6.0);x=-26+radius*math.cos(a);y=31+radius*math.sin(a)
 if not clear(x,y,1.0):continue
 hit,n,idx,d=bvh.ray_cast(Vector((x,y,50)),Vector((0,0,-1)),100)
 if not hit:continue
 for k in range(3):
  h=hit.z+(4.35-hit.z)*(k+.5)/3;o=bpy.data.objects.new('SM_P7_Z08_INTERLOCKED_CLIFF',rng.choice(meshes));C.objects.link(o);o.location=(x+rng.uniform(-.3,.3),y+rng.uniform(-.25,.15),h);o.scale=(rng.uniform(.95,1.65),rng.uniform(.9,1.4),rng.uniform(.6,1));o.rotation_euler=(rng.uniform(-.3,.3),rng.uniform(-.2,.2),rng.random()*math.tau);top=max((o.matrix_world@Vector(v)).z for v in o.bound_box)
  if top>4.4:o.location.z-=top-4.4
  count+=1
# Day world: desaturate the old strongly blue environment slightly, without switching skies by camera.
nt=s.world.node_tree;bg=next(n for n in nt.nodes if n.type=='BACKGROUND')
if bg.inputs['Color'].links:
 src=bg.inputs['Color'].links[0].from_socket;h=nt.nodes.new('ShaderNodeHueSaturation');h.name='P7_DAY_SKY_CHROMA';h.inputs['Saturation'].default_value=.68;h.inputs['Value'].default_value=1.15;nt.links.new(src,h.inputs['Color']);nt.links.new(h.outputs['Color'],bg.inputs['Color'])
s['version']='p3-v037';s['phase_status']='P7_REFERENCE_REPAIR_REVIEW';s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True);(R/'evidence/fidelity-finish-v037.json').write_text(json.dumps({'base':'p3-v036','floating_eave_caps':'retired','stacked_wall':'randomized sizes, pitches, placements; one quarter retired','diagnosed_wall_object':'Z08_TERRACE','new_interlocked_cliff_rocks':count,'source_paths':'unchanged'},indent=2));print('REFERENCE_FINISH_V037',count)
