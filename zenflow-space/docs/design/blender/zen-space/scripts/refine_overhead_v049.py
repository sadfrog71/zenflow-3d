"""Shared-model overhead refinement: court cross-gallery, low island outline, roof corner finish and integrated lookout shoulder."""
import bpy,json,math,random,sys,shutil
from pathlib import Path
from mathutils import Vector,Matrix
from mathutils.bvhtree import BVHTree
R=Path(__file__).resolve().parents[1];sys.path.insert(0,str(R/'scripts'));from phase2_materials import box,beam
s=bpy.context.scene;out=R/'zen-space-p3-v049.blend';assert s.get('version')=='p3-v048' and not out.exists();rng=random.Random(221049);mfile=R/'manifest/scene-manifest.json';m=json.loads(mfile.read_text());backup=R/'manifest/scene-manifest-p3-v048.json'
if not backup.exists():shutil.copy2(mfile,backup)
C=bpy.data.collections.new('P11_OVERHEAD_FINISH');s.collection.children.link(C);RC=bpy.data.collections.new('P11_COURT_CROSS_GALLERY_ROOF');s.collection.children.link(RC)
# Reuse the actual fitted roof kit and all tiles, ridges, fascia and rafters.
sources=[o for o in s.objects if o.type=='MESH' and not o.hide_render and (o.name=='Z01_VERANDA_ROOF' or o.name.startswith('SM_P6_Z01_VERANDA_ROOF_'))];roofcount=0
for src in sources:
 o=src.copy();o.data=src.data.copy();o.name='SM_P11_CROSS_GALLERY_'+src.name;o.parent=None;o.matrix_world=Matrix.Identity(4)
 for v,sv in zip(o.data.vertices,src.data.vertices):
  q=src.matrix_world@sv.co;v.co=((q.y-9)*1.36,2.2-(q.x+14)*.72,q.z)
 RC.objects.link(o);roofcount+=1
wood=bpy.data.materials['P2_Cedar'];stone=bpy.data.materials['P5_FINE_DRESSED_LIMESTONE'];floor=box('SM_P11_COURT_CROSS_GALLERY_FLOOR',(0,2.2,.005),(27.4,2.4,.04),C,bpy.data.materials['P6_HAND_FINISHED_SATIN_FLOOR'],.012)
posts=0
for x in [-12,-7,-2.5,2.5,7,12]:
 for y in [1.2,3.2]:
  # Measured center-route clearance; no column in the principal courtyard gateway.
  beam('SM_P11_COURT_GALLERY_COLUMN',(x,y,.12),(x,y,3.39),.11,C,wood,16);box('SM_P11_COURT_GALLERY_FOOT',(x,y,.07),(.32,.32,.14),C,stone,.025);posts+=1
for y in [1.2,3.2]:beam('SM_P11_COURT_GALLERY_EAVE_BEAM',(-13.5,y,3.18),(13.5,y,3.18),.10,C,wood,12)
# Main roof corners lift locally, keeping main eaves and wall-head connections coherent.
roofs=[]
for name in ['Z04_FULL_ROOF','Z05_FULL_ROOF','Z06_FULL_ROOF','Z11_FULL_ROOF']:
 roof=s.objects[name];pts=[roof.matrix_world@v.co for v in roof.data.vertices];xmin,xmax=min(q.x for q in pts),max(q.x for q in pts);ymin,ymax=min(q.y for q in pts),max(q.y for q in pts);span=min(xmax-xmin,ymax-ymin);radius=min(1.6,span*.25)
 def lift(q):return .32*max(math.exp(-((q.x-x)/radius)**2-((q.y-y)/radius)**2) for x in [xmin,xmax] for y in [ymin,ymax])
 affected=[o for o in s.objects if o.type=='MESH' and not o.hide_render and (o==roof or o.name.startswith('SM_P6_'+name+'_') or o.name.startswith('SM_P10_'+name[:3]+'_FITTED_WALL_HEAD'))]
 for o in affected:
  if o.data.users>1:o.data=o.data.copy()
  inv=o.matrix_world.inverted()
  for v in o.data.vertices:
   q=o.matrix_world@v.co
   if not o.name.startswith('SM_P10_') or q.z>3.5:q.z+=lift(q)
   v.co=inv@q
  o.data.update()
 roofs.append({'roof':name,'component_count':len(affected),'max_corner_lift_m':.32})
# Low island: restore a compact real outline at the existing cushion, preserving the ground elevation.
island=s.objects['Z03_LOW_ISLAND_GROUND'];cx,cy=-25,3;N=96;vs=[];fs=[]
for layer in [0,1]:
 for i in range(N):
  a=math.tau*i/N;r=(2.95 if layer==0 else 3.30)*(1+.035*math.sin(3*a)+.025*math.sin(7*a));vs.append((cx+math.cos(a)*r,cy+math.sin(a)*r,-.3 if layer==0 else -1.7))
fs.append(tuple(range(N)))
for i in range(N):fs.append((i,N+i,N+(i+1)%N,(i+1)%N))
me=bpy.data.meshes.new('P11_COMPACT_LOW_ISLAND_GROUND');me.from_pydata(vs,[],fs);me.update();me.materials.append(bpy.data.materials['P3_Weathered_Gray_Rock']);uv=me.uv_layers.new()
for p in me.polygons:
 for li in p.loop_indices:q=me.vertices[me.loops[li].vertex_index].co;uv.data[li].uv=(q.x*.6,q.y*.6)
island.data=me;island.matrix_world=Matrix.Identity(4)
# A continuous land shoulder meets the lookout skirt, while all authored stairs and building foundations are protected.
terrain=s.objects['TERRAIN_CONTINUOUS_BASE'];oldbvh=BVHTree.FromPolygons([terrain.matrix_world@v.co for v in terrain.data.vertices],[tuple(f.vertices) for f in terrain.data.polygons]);routes=m['routes']
def dist(x,y,a,b):
 dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/max(1e-8,dx*dx+dy*dy)));return math.hypot(x-a[0]-t*dx,y-a[1]-t*dy)
def clear(x,y,r=.8):return all(dist(x,y,a['from'],a['to'])>a['width']/2+r for a in routes)
def sm(t):t=max(0,min(1,t));return t*t*(3-2*t)
changed=0
for v in terrain.data.vertices:
 q=terrain.matrix_world@v.co;x,y,z=q;d=math.hypot((x-25)*1.0,(y-35)*1.08)
 if d>=12.8 or not clear(x,y) or (25.7<x<32.2 and 21.2<y<27):continue
 w=sm((12.8-d)/6.4);target=z+(max(z,7.10)-z)*w
 if target>z+.001:q.z=target;v.co=terrain.matrix_world.inverted()@q;changed+=1
terrain.data.update();newbvh=BVHTree.FromPolygons([terrain.matrix_world@v.co for v in terrain.data.vertices],[tuple(f.vertices) for f in terrain.data.polygons])
def height(bvh,x,y):
 p,n,i,d=bvh.ray_cast(Vector((x,y,80)),Vector((0,0,-1)),180);return p.z if p else None
rooted=0
for o in s.objects:
 if o.type!='MESH' or o.hide_render or o.parent or o.animation_data:continue
 if not o.name.startswith(('SM_P8_HILL','SM_P6_GARDEN','SM_P7_GARDEN','SM_GROUND_','SM_LOW_SHRUB','SM_ROCK_REPLACEMENT','SM_P9_ECOTONE','SM_P6_Z10_','SM_P7_Z10_')):continue
 x,y,z=o.matrix_world.translation
 if math.hypot(x-25,y-35)>13:continue
 a,b=height(oldbvh,x,y),height(newbvh,x,y)
 if a is not None and b is not None and b>a+.01:o.location.z+=b-a;rooted+=1
# Break the remaining vertical skirt only below its safe top edge; keep route buffers intact.
terr=s.objects['Z10_TERRACE'];inv=terr.matrix_world.inverted();skirt=0
for v in terr.data.vertices:
 q=terr.matrix_world@v.co
 if q.z>=7.40 or not clear(q.x,q.y,.65):continue
 a=math.atan2(q.y-35,q.x-25);r=.32*sm((7.4-q.z)/2)*(math.sin(3*a)+.4*math.sin(8*a));q.x+=r*math.cos(a);q.y+=r*math.sin(a);v.co=inv@q;skirt+=1
terr.data.update()
# Bring the already authored sparse flowers outside the leaf silhouette, without creating a new species.
flowers=[o for o in s.objects if not o.hide_render and o.name.startswith('SM_Z01_SPARSE_RED_FLOWER')]
for o in flowers:
 p=o.location;p.x*=1.1;p.y=9+(p.y-9)*1.1;p.z+=.24;o.scale*=1.25
# A closer composition is additional; the established full-model overview remains unchanged for fair comparison.
cam=s.objects['CAM_WORLD_FULL'];new=cam.copy();new.data=cam.data.copy();new.name='CAM_GARDEN_COMPOSITION';cam.users_collection[0].objects.link(new);new.location=(8,-148,113);target=Vector((-6,9,2));new.rotation_euler=(target-new.location).to_track_quat('-Z','Y').to_euler();new.data.ortho_scale=84
# Only the new roof collection is excluded from the explicit layout cutaway.
def find(lc,name):
 if lc.name==name:return lc
 for c in lc.children:
  v=find(c,name)
  if v:return v
for vl in s.view_layers:
 lc=find(vl.layer_collection,RC.name)
 if lc:lc.exclude=vl.name=='LAYOUT_CUTAWAY'
s['version']='p3-v049';s['phase_status']='P11_OVERHEAD_REFERENCE_REFINEMENT';s.frame_set(1);m.update(version='p3-v049',source_version='p3-v049',source_snapshot=out.name);m['overhead_refinement_v049']={'gallery_roof_components_reused':roofcount,'gallery_columns':posts,'main_roofs':roofs,'island_top_m':-.3,'lookout_terrain_vertices':changed,'rerooted_garden_instances':rooted,'skirt_vertices':skirt,'existing_flower_instances_repositioned':len(flowers),'full_overview_camera_unchanged':True,'additional_composition_camera':new.name};mfile.write_text(json.dumps(m,ensure_ascii=False,indent=2));bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True);(R/'evidence/overhead-refinement-v049.json').write_text(json.dumps(m['overhead_refinement_v049'],ensure_ascii=False,indent=2));print('OVERHEAD_REFINED',roofcount,posts,changed,rooted,skirt,len(flowers),flush=True)
