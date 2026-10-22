"""Recompose shared garden massing from v8: continuous terrace shoulders, planted court, connected eaves."""
import bpy,math,random,json,sys
from pathlib import Path
from mathutils import Vector,Matrix,noise
from mathutils.bvhtree import BVHTree
R=Path(__file__).resolve().parents[1];sys.path.insert(0,str(R/'scripts'));from phase2_materials import box,beam
s=bpy.context.scene;out=R/'zen-space-p3-v040.blend'
if s.get('version')!='p3-v039' or out.exists():raise RuntimeError('Requires v039 and new v040')
rng=random.Random(211040);C=bpy.data.collections.new('P8_SHARED_GARDEN_COMPOSITION');s.collection.children.link(C);roofcol=bpy.data.collections.new('P8_EAST_GALLERY_ROOF');s.collection.children.link(roofcol);gatecol=bpy.data.collections.new('P8_COURT_GATE_ROOF');s.collection.children.link(gatecol);routes=json.loads((R/'manifest/scene-manifest.json').read_text())['routes'];stats={}
def distance(x,y,row):
 a,b=row['from'],row['to'];dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/max(1e-9,dx*dx+dy*dy)));return math.hypot(x-a[0]-t*dx,y-a[1]-t*dy),t

def clear(x,y,r=.5):return all(distance(x,y,row)[0]>row['width']/2+r+.28 for row in routes)
def smooth(t):t=max(0,min(1,t));return t*t*(3-2*t)
def makebvh(ob):return BVHTree.FromPolygons([ob.matrix_world@v.co for v in ob.data.vertices],[tuple(f.vertices) for f in ob.data.polygons])
terrain=s.objects['TERRAIN_CONTINUOUS_BASE'];oldbvh=makebvh(terrain)
def height(bvh,x,y):
 h,n,i,d=bvh.ray_cast(Vector((x,y,100)),Vector((0,0,-1)),250);return h.z if h else None
# Terrain shoulders meet the existing terrace tops. Do not build a cliff facade out of repeated rock slabs.
changed=0;maxraise=0
for v in terrain.data.vertices:
 x,y,z=v.co
 if not (-45<x<42 and 23.5<y<48) or z<-.75:continue
 if 24.5<x<33.5 and 20<y<28:continue
 if any(abs(x-cx)<w/2+.7 and abs(y-cy)<d/2+.7 for cx,cy,w,d in [(-34,33,5,3),(-32,29,5.5,3),(-30,25,6,3)]):continue
 target=z
 for cx,cy,top,flat,outer in [(-26,31,4.5,4.8,13.2),(0,36,7.5,4.3,14.8),(25,35,7.5,4.9,14.5)]:
  d=math.hypot(x-cx,(y-cy)*1.12);w=1-smooth((d-flat)/(outer-flat));target=max(target,z+(top-.19-z)*w)
 # Every route has an unchanged envelope and a graded shoulder outside it.
 for row in routes:
  d,t=distance(x,y,row);half=row['width']/2+.25
  if d<half+4:
   a,b=row['from'],row['to'];length=math.dist(a[:2],b[:2]);pad=row.get('landing_pad_m',0);u=max(0,min(1,(t*length-pad)/max(.001,length-2*pad))) if pad else t;walk=a[2]+(b[2]-a[2])*u
   if d<=half:target=min(target,z)
   else:target=min(target,max(z,walk-.20+(d-half)*.68))
 target=max(z,target)
 if target-z>.005:v.co.z=target;changed+=1;maxraise=max(maxraise,target-z)
terrain.data.update();newbvh=makebvh(terrain);stats['terrain_vertices_raised']=changed;stats['max_terrain_shoulder_raise_m']=maxraise
# Retire the previous tall stacked-rock disguise; the terrace is now physically embedded in its hill.
retired=[]
for o in s.objects:
 if o.name.startswith(('SM_P7_Z08_INTERLOCKED_CLIFF','SM_P7_CLIFF_ROOTED_UNDERSTORY')):
  o.hide_render=True;o.hide_viewport=True;retired.append(o.name)
stats['retired_stacked_cliff_objects']=len(retired)
# Existing near terrain assets are re-rooted consistently; actors, paths, floors and terrace tops do not move.
rooted=0
prefixes=('SM_CONTEXT_','SM_CC0_','SM_CASCADE_BANK_FERN','SM_NEAR_BANK_ROOTED_FERN','SM_P6_LOW_GARDEN_SHRUB','SM_P6_GARDEN_TRANSITION','SM_P7_LAYERED_SHRUB','SM_P7_PLANTED_OUTCROP','SM_P6_Z08_TERRACE_HILL_FOOT','SM_P6_Z09_TERRACE_HILL_FOOT','SM_P6_Z10_TERRACE_HILL_FOOT')
for o in s.objects:
 if o.hide_render or o.parent or not o.name.startswith(prefixes):continue
 x,y=o.location.x,o.location.y
 if not(-45<x<42 and 23.5<y<48):continue
 a=height(oldbvh,x,y);b=height(newbvh,x,y)
 if a is not None and b is not None and b-a>.02:o.location.z+=b-a;rooted+=1
stats['existing_ground_assets_rerooted']=rooted
# Curved planted court replaces broad uninterrupted pavement while preserving the central entry and bell route.
def bedshape(x,y):
 dx=(x+.6)/5.4;dy=(y-10.1)/5.8;a=math.atan2(dy,dx);return dx*dx+dy*dy<(1+.11*math.sin(3*a)+.06*math.sin(5*a))**2 and clear(x,y,.34)
vs=[];fs=[];step=.36
for j in range(35):
 y=3.9+j*step
 for i in range(34):
  x=-6.6+i*step
  if not all(bedshape(xx,yy) for xx,yy in [(x,y),(x+step,y),(x+step,y+step),(x,y+step)]):continue
  k=len(vs);vs.extend([(x,y,.025),(x+step,y,.025),(x+step,y+step,.025),(x,y+step,.025)]);fs.append((k,k+1,k+2,k+3))
me=bpy.data.meshes.new('P8_CURVED_COURT_PLANTING_SOIL');me.from_pydata(vs,[],fs);me.update();me.materials.append(bpy.data.materials['P4_PHOTOGRAPHED_FOREST_SOIL']);uv=me.uv_layers.new()
for p in me.polygons:
 for li in p.loop_indices:q=me.vertices[me.loops[li].vertex_index].co;uv.data[li].uv=(q.x*.5,q.y*.5)
o=bpy.data.objects.new('SM_P8_Z01_CURVED_PLANTING_BED',me);C.objects.link(o);pavers=0
for o in s.objects:
 if o.name.startswith('Z01_P2_COURT_PAVER') and bedshape(o.location.x,o.location.y):o.hide_render=True;o.hide_viewport=True;pavers+=1
# The old square tree well is incorporated into the larger garden rather than remaining a lone square in paving.
for o in s.objects:
 if o.name.startswith('SM_Z01_TREE_WELL_STONE_EDGE'):o.hide_render=True;o.hide_viewport=True
stats['court_pavers_retired_inside_planting']=pavers
shrubs=[bpy.data.meshes['P6_SHARED_LOW_SHRUB_'+str(i)] for i in range(3)]
def shrub(name,x,y,z,k):
 ob=bpy.data.objects.new(name,rng.choice(shrubs));C.objects.link(ob);ob.location=(x,y,z);ob.scale=(k,k*rng.uniform(.8,1.1),k*rng.uniform(.72,1.1));ob.rotation_euler.z=rng.random()*math.tau;return ob
plants=0
for i in range(180):
 x=rng.uniform(-5.7,4.7);y=rng.uniform(4.4,15.8);k=rng.uniform(.45,.9)
 if not bedshape(x,y) or not clear(x,y,k*1.1) or math.hypot(x,y-9)<1.15:continue
 shrub('SM_P8_Z01_COURT_GARDEN_CLUSTER',x,y,.03,k);plants+=1
# Hillsides use overlapping planting masses, with larger gaps at viewpoint edges and around paths.
for cx,cy,rx,ry in [(-34,27,5,3),(-20,34,5,3),(-10,34,4,4),(7,34,4,4),(22,31,4,3),(29,38,4,4),(33.5,23.7,2,3)]:
 for i in range(46):
  a=rng.random()*math.tau;d=math.sqrt(rng.random());x=cx+rx*d*math.cos(a);y=cy+ry*d*math.sin(a);k=rng.uniform(.8,1.65)
  if not clear(x,y,k*1.15):continue
  z=height(newbvh,x,y)
  if z is None or z<-.6 or (25.7<x<32.2 and 21.2<y<26.8):continue
  shrub('SM_P8_HILL_PLANTING_MASS',x,y,z,k);plants+=1
stats['new_grouped_plant_clusters']=plants
# Reuse the full existing roof module and every fitted derivative in world space.
def duplicate_roof(source_name,target_name,target_collection,transform):
 sources=[o for o in list(s.objects) if o.type=='MESH' and not o.hide_render and (o.name==source_name or o.name.startswith('SM_P6_'+source_name+'_'))];copies=[]
 for src in sources:
  ob=src.copy();ob.data=src.data.copy();ob.name=target_name+'_'+src.name;ob.parent=None;ob.matrix_world=Matrix.Identity(4)
  for v,sv in zip(ob.data.vertices,src.data.vertices):v.co=transform(src.matrix_world@sv.co)
  target_collection.objects.link(ob);copies.append(ob)
 return len(copies)
count=duplicate_roof('Z01_VERANDA_ROOF','SM_P8_Z01_EAST_GALLERY',roofcol,lambda p:Vector((p.x+28,(p.y-9)*.62+6,p.z)))
wood=bpy.data.materials['P2_Cedar'];stone=bpy.data.materials['P5_FINE_DRESSED_LIMESTONE'];box('SM_P8_Z01_EAST_GALLERY_FLOOR',(14,6,.005),(2.55,12.5,.04),C,bpy.data.materials['P6_HAND_FINISHED_SATIN_FLOOR'],.012)
posts=0
for x in [12.9,15.1]:
 for y in [.8,4.4,8.0,11.6]:
  # Column radius is checked against all public route envelopes; omit any conflicting support.
  if not clear(x,y,.13):continue
  beam('SM_P8_Z01_EAST_GALLERY_COLUMN',(x,y,.12),(x,y,3.39),.11,C,wood,16);box('SM_P8_Z01_EAST_GALLERY_COLUMN_FOOT',(x,y,.07),(.32,.32,.14),C,stone,.025);posts+=1
 beam('SM_P8_Z01_EAST_GALLERY_EAVE_BEAM',(x,-.15,3.22),(x,12.15,3.22),.105,C,wood,12)
stats['east_gallery_roof_derivatives_reused']=count;stats['east_gallery_columns']=posts
# Courtyard gateway supplies the missing axial frame without adding a wall across the open courtyard.
count=duplicate_roof('Z12_GATE_CAP','SM_P8_Z01_COURT_GATE',gatecol,lambda p:Vector((p.x,p.y+41,p.z)))
for x in [-1.95,1.95]:beam('SM_P8_Z01_COURT_GATE_COLUMN',(x,18.5,.12),(x,18.5,3.4),.14,C,wood,18);box('SM_P8_Z01_COURT_GATE_FOOT',(x,18.5,.07),(.4,.4,.14),C,stone,.025)
beam('SM_P8_Z01_COURT_GATE_LINTEL',(-2.15,18.5,3.25),(2.15,18.5,3.25),.12,C,wood,12);stats['court_gate_roof_derivatives_reused']=count
# Keep a true old camera, and make the main overview frontal enough to judge the approved roof/garden organisation.
cam=s.objects['CAM_WORLD_FULL'];oldcam=cam.copy();oldcam.data=cam.data.copy();oldcam.name='CAM_WORLD_FULL_V039_ORIGINAL';bpy.data.collections['70_CAMERAS'].objects.link(oldcam)
cam.location=(8,-148,120);target=Vector((-5,9,2));cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.ortho_scale=108
# Cutaway hides the new roof collections only; no second scene or per-camera furniture/landscape.
def exclude(layercoll):
 if layercoll.name in [roofcol.name,gatecol.name]:layercoll.exclude=True
 for child in layercoll.children:exclude(child)
exclude(s.view_layers['LAYOUT_CUTAWAY'].layer_collection)
s['version']='p3-v040';s['phase_status']='P8_SHARED_COMPOSITION_REVIEW';s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True);stats.update({'base':'p3-v039','buildings_moved':False,'routes_changed':False,'actors_moved':False,'new_architecture':'east gallery and open courtyard gateway, reused existing roof modules','camera_match_required':True});(R/'evidence/overview-recomposition-v040.json').write_text(json.dumps(stats,ensure_ascii=False,indent=2));print('OVERVIEW_RECOMPOSED',stats,flush=True)
