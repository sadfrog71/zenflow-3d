"""Compose the living pond view, restore reflective depth, and integrate low shrubs/wall coping in the shared garden."""
import bpy,math,random,json,sys
from pathlib import Path
from mathutils import Vector
from mathutils.bvhtree import BVHTree
R=Path(__file__).resolve().parents[1];sys.path.insert(0,str(R/'scripts'));from phase2_materials import box,beam,camera
s=bpy.context.scene;out=R/'zen-space-p3-v034.blend'
if s.get('version')!='p3-v033' or out.exists():raise RuntimeError('Requires v033/new output')
rng=random.Random(211034);C=bpy.data.collections['P6_GARDEN_INTEGRATION'];stats={}
# A depth diagnosis confirmed no fish/terrain penetration; the original inspection camera pointed between the fish.
cam=s.objects['CAM_POND_LIFE'];cam.location=(-32.5,6.5,6.4);target=Vector((-32.2,8.7,-1.35));cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.lens=30
# Move one red-white route into the visible group; all five retain independent paths.
rt=s.objects['P6_KOI_RED_WHITE_A_ROOT'];a=rt.animation_data.action
for layer in a.layers:
 for st in layer.strips:
  for bag in st.channelbags:
   for fc in bag.fcurves:
    if fc.data_path=='location' and fc.array_index==0:
     for key in fc.keyframe_points:key.co.y-=5.5;key.handle_left.y-=5.5;key.handle_right.y-=5.5
# Closer, bank-mounted alternate camera shows body volume and scale detail in actual water.
camcol=bpy.data.collections['70_CAMERAS'];camera('CAM_KOI_CLOSE',(-33,7.5,1.9),(-29.8,10,-1.35),camcol,60)
# A reflective, partly transmissive pond rather than a flat, fully revealed pale floor.
p=next(n for n in bpy.data.materials['P2_Water'].node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Base Color'].default_value=(.15,.36,.31,1);p.inputs['Transmission Weight'].default_value=.62;p.inputs['Roughness'].default_value=.055
# Keep dry floor satin, not glossy; grazing sunlight provides modest differentiated highlights.
fm=bpy.data.materials['P6_HAND_FINISHED_SATIN_FLOOR'];p=next(n for n in fm.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Roughness'].default_value=.36;p.inputs['Coat Weight'].default_value=.10
# Original low shrubs derived from the packed CC0 leaf geometry, with coherent whole-card sampling.
source=bpy.data.meshes.get('CC0_LEAF_CARDS_FAR') or bpy.data.meshes.get('CC0_LEAF_CARDS_MID');clouds=[]
if source:
 uv0=source.uv_layers.active;faces=[f for f in source.polygons if len(f.vertices)==4];stride=max(1,len(faces)//950)
 for variant in range(3):
  selected=faces[variant%stride::stride][:950];used=[source.vertices[i].co for f in selected for i in f.vertices];lo=Vector(tuple(min(v[j] for v in used) for j in range(3)));hi=Vector(tuple(max(v[j] for v in used) for j in range(3)));center=(lo+hi)/2;span=hi-lo;vs=[];fs=[];uvs=[]
  for f in selected:
   base=len(vs)
   for li in f.loop_indices:
    v=source.vertices[source.loops[li].vertex_index].co;q=v-center;pos=Vector((q.x/max(span.x,.01)*2,q.y/max(span.y,.01)*1.65,q.z/max(span.z,.01)*1.15+.60));pos.x+=.10*math.sin(pos.z*4+variant);vs.append(tuple(pos));uvs.append(tuple(uv0.data[li].uv))
   fs.append(tuple(base+i for i in range(4)))
  me=bpy.data.meshes.new('P6_SHARED_LOW_SHRUB_'+str(variant));me.from_pydata(vs,[],fs);me.update()
  for m in source.materials:me.materials.append(m)
  uv=me.uv_layers.new()
  for f in me.polygons:
   for li in f.loop_indices:uv.data[li].uv=uvs[me.loops[li].vertex_index]
  clouds.append(me)
terrain=s.objects['TERRAIN_CONTINUOUS_BASE'];tbvh=BVHTree.FromPolygons([terrain.matrix_world@v.co for v in terrain.data.vertices],[tuple(f.vertices) for f in terrain.data.polygons]);routes=json.loads((R/'manifest/scene-manifest.json').read_text())['routes']
def dist(x,y,a,b):
 dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/max(1e-8,dx*dx+dy*dy)));return math.hypot(x-a[0]-t*dx,y-a[1]-t*dy)
def ground(x,y):
 h,n,i,d=tbvh.ray_cast(Vector((x,y,100)),Vector((0,0,-1)),200);return h.z if h else None
count=0
for cx,cy,rx,ry in [(-42,-7,3,5),(-41,17,3,5),(-33,26,5,2),(-18,19,2,3),(-30,32,2,4),(3,33,3,3),(29,33,2,5),(-22,-24,4,2),(20,-24,4,2),(-7,24,3,1.4)]:
 for j in range(14):
  x=cx+rng.uniform(-rx,rx);y=cy+rng.uniform(-ry,ry);scale=rng.uniform(.55,1.05)
  if any(dist(x,y,r['from'],r['to'])<r['width']/2+scale+.3 for r in routes):continue
  z=ground(x,y)
  if z is None or z<-.65:continue
  if clouds:
   ob=bpy.data.objects.new('SM_P6_LOW_GARDEN_SHRUB',clouds[j%3]);C.objects.link(ob);ob.location=(x,y,z);ob.rotation_euler.z=rng.random()*math.tau;ob.scale=(scale,)*3;count+=1
# Gray clay coping and a stone plinth finish the existing front wall, without changing its gate or alignment.
clay=bpy.data.materials['P6_FIRED_CLAY_0'];stone=bpy.data.materials['P5_FINE_DRESSED_LIMESTONE'];coping=0
for center,width in [(-12.75,22.5),(13.75,22.5)]:
 for sign in [-1,1]:
  vs=[(center-width/2-.05,-22.5+sign*.36,2.50),(center+width/2+.05,-22.5+sign*.36,2.50),(center+width/2+.05,-22.5,2.67),(center-width/2-.05,-22.5,2.67)];fs=[(0,1,2,3)] if sign==-1 else [(3,2,1,0)];me=bpy.data.meshes.new('P6_WALL_COPING_PANEL');me.from_pydata(vs,[],fs);me.update();me.materials.append(clay);ob=bpy.data.objects.new('SM_P6_Z12_CLAY_WALL_COPING',me);C.objects.link(ob);so=ob.modifiers.new('Clay edge thickness','SOLIDIFY');so.thickness=.045
 for i in range(round(width/.28)):
  x=center-width/2+(i+.5)*width/round(width/.28)
  for sign in [-1,1]:beam('SM_P6_Z12_WALL_COPING_CAP',(x,-22.5,2.69),(x,-22.5+sign*.36,2.53),.035,C,clay,8);coping+=1
 for row in range(2):
  for i in range(13):
   bw=width/13;offset=bw*.5 if row else 0;xx=center-width/2+(i+.5)*bw+offset
   if xx+bw/2>center+width/2:continue
   box('SM_P6_Z12_STONE_WALL_PLINTH',(xx,-22.72,.13+row*.24),(bw-.012,.07,.225),C,stone,.008)
# The cat close-up should show a whole cat, while the primary view keeps its real column occlusion.
cam=s.objects['CAM_CATS_VERANDA'];cam.location=(-16.4,10.3,.73);target=Vector((-14.85,12.1,.24));cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.lens=55
s['version']='p3-v034';s['phase_status']='P6_LIVING_GARDEN_COMPARISON';s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
(R/'evidence/living-garden-v034.json').write_text(json.dumps({'version':'p3-v034','base':'p3-v033','red_koi':3,'all_koi':5,'cats':2,'new_low_shrubs':count,'front_wall_coping_pieces':coping,'pond_diagnosis':'all fish were above the actual mineral bed and below the water; inspection view was between their paths','water':'IOR1.333, transmission .62, roughness .055; no fake fish compositing','source_reuse':'packed Zenflow atlas + new volume, per-vertex red/white/gold patches','first_person_layout':'unchanged; dedicated animal inspection cameras adjusted'},indent=2));print('LIVING_GARDEN_V034_SAVED',count,coping,flush=True)
