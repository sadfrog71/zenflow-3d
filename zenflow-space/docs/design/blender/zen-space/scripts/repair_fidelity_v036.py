"""Reference-led shared-world repair. Preserve source actors, paths, footprints and cameras."""
import bpy,math,random,json,sys
from pathlib import Path
from mathutils import Vector,noise
from mathutils.bvhtree import BVHTree
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v036.blend'
if s.get('version')!='p3-v035' or out.exists():raise RuntimeError('Requires v035 and unused v036')
rng=random.Random(211036);C=bpy.data.collections.new('P7_REFERENCE_LANDSCAPE_REPAIR');s.collection.children.link(C);routes=json.loads((R/'manifest/scene-manifest.json').read_text())['routes'];stats={}
def mesh(name,vs,fs,mat):
 me=bpy.data.meshes.new(name);me.from_pydata(vs,[],fs);me.update();me.materials.append(mat);o=bpy.data.objects.new(name,me);C.objects.link(o)
 for f in me.polygons:f.use_smooth=True
 return o
terrain=s.objects['TERRAIN_CONTINUOUS_BASE'];bvh=BVHTree.FromPolygons([terrain.matrix_world@v.co for v in terrain.data.vertices],[tuple(f.vertices) for f in terrain.data.polygons])
def ground(x,y):
 h,n,i,d=bvh.ray_cast(Vector((x,y,100)),Vector((0,0,-1)),250);return h.z if h else None

def distance(x,y,a,b):
 dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/max(1e-8,dx*dx+dy*dy)));return math.hypot(x-a[0]-t*dx,y-a[1]-t*dy)
def clear(x,y,r):return all(distance(x,y,row['from'],row['to'])>row['width']/2+r+.32 for row in routes)
rockmeshes=[]
for o in s.objects:
 if o.type=='MESH' and o.name.startswith('SM_Z02_NATURAL_BANK_STONE') and o.data not in rockmeshes:rockmeshes.append(o.data)
rockmeshes=rockmeshes[:7]
def rock(name,p,scale):
 o=bpy.data.objects.new(name,rng.choice(rockmeshes));C.objects.link(o);o.location=p;o.scale=scale;o.rotation_euler=(rng.uniform(-.15,.15),rng.uniform(-.15,.15),rng.random()*math.tau);return o
# Actual rubble support follows the exposed pond-facing path sidewalls; tops/route geometry unchanged.
path=s.objects['PUBLIC_EAST_ROUTE_RESOLVED_NETWORK'];count=0
for face in path.data.polygons:
 normal=(path.matrix_world.to_3x3()@face.normal).normalized();center=path.matrix_world@face.center
 if abs(normal.z)>.18 or not (-46<center.x<-15 and -17<center.y<25):continue
 if normal.dot(Vector((-29-center.x,7-center.y,0)))<=0:continue
 pts=[path.matrix_world@path.data.vertices[i].co for i in face.vertices];lo=min(p.z for p in pts);hi=max(p.z for p in pts)
 if hi>.15 or hi-lo<.25:continue
 tang=Vector((-normal.y,normal.x,0));ps=[p.dot(tang) for p in pts];a,b=min(ps),max(ps);steps=max(1,math.ceil((b-a)/.95));rows=max(1,math.ceil((hi-lo)/.65))
 for j in range(steps):
  for k in range(rows):
   q=center+tang*(a+(j+.5)*(b-a)/steps-center.dot(tang))+normal*rng.uniform(.15,.36);q.z=lo+(k+.5)*(hi-lo)/rows;rz=min(.38,(hi-lo)/rows*.56);q.z=min(q.z,hi-rz-.035);ob=rock('SM_P7_POND_PATH_RUBBLE_SUPPORT',q,(rng.uniform(.55,.78),rng.uniform(.45,.72),rz));count+=1
stats['pond_path_rubble_stones']=count
# Cascades: large, interlocking boulders break the planar cut beside the falling water, while preserving pool levels.
count=0
for cx,cy,sz in [(-26,24.5,1.2),(-23.5,25.5,1.25),(-20.5,26.2,1.0),(-37,26,1.15),(-36.6,29.6,1.3),(-39,31.7,1.2),(-29,29.2,1.0)]:
 for j in range(10):
  x=cx+rng.uniform(-1.25,1.25);y=cy+rng.uniform(-1.3,1.3);rad=rng.uniform(.7,1.4)*sz
  if not clear(x,y,rad):continue
  z=ground(x,y)
  if z is None:continue
  rock('SM_P7_CASCADE_ROCK_STRATA',(x,y,z+rad*.28),(rad,rng.uniform(.65,1.15)*rad,rng.uniform(.5,.85)*rad));count+=1
stats['cascade_strata_stones']=count
shrubs=[bpy.data.meshes['P6_SHARED_LOW_SHRUB_'+str(i)] for i in range(3)];plants=0;bankrocks=0
# Composition uses irregular planted groups, with explicit open gaps and route buffers, not uniform scattering.
patches=[(-42,-3,3.0,7),(-40,14,2.8,5),(-37,24,4,2),(-23,23,3.0,2),(-27,29,5,3),(-13,28,3,3),(-5,31,3,2),(7,31,3.5,2),(20,32,2.5,4),(30,36,2,3),(-23,-17,2.0,2),(-18,-24.8,4.5,1.3),(16,-24.8,5,1.4),(33,23,1.8,3), (32,18,2.5,2.8)]
for cx,cy,rx,ry in patches:
 for j in range(28):
  a=rng.random()*math.tau;rad=math.sqrt(rng.random());x=cx+rx*rad*math.cos(a);y=cy+ry*rad*math.sin(a);k=rng.uniform(.65,1.35)
  if not clear(x,y,k*1.1):continue
  z=ground(x,y)
  if z is None or z<-.8:continue
  # All plant roots belong to actual terrain, never above a built floor.
  if (-21.5<x<24 and -14<y<1) or (-13<x<15 and -2<y<26) or (15<x<25 and 12<y<20) or (25<x<33 and 20<y<28):continue
  ob=bpy.data.objects.new('SM_P7_LAYERED_SHRUB_GROUP',rng.choice(shrubs));C.objects.link(ob);ob.location=(x,y,z-.015);ob.scale=(k,k*rng.uniform(.8,1.1),k*rng.uniform(.65,1.1));ob.rotation_euler.z=rng.random()*math.tau;plants+=1
  if j%9==0 and clear(x+1.3,y,1):rock('SM_P7_PLANTED_OUTCROP',(x+1.3,y,z+.2),(rng.uniform(.7,1.2),.75,.55));bankrocks+=1
stats['new_shrub_groups']=plants;stats['planted_outcrops']=bankrocks
# Lower the oversized foreground shore rocks slightly; preserve physical shore, reveal more water at seated eye.
lowered=[]
for o in s.objects:
 if o.type=='MESH' and not o.hide_render and o.name.startswith(('SM_P6_Z02_INNER_BANK_REVETMENT','SM_Z02_NATURAL_BANK_STONE')):
  bb=[o.matrix_world@Vector(v) for v in o.bound_box];p=o.matrix_world.translation
  if -20<p.x<-15.8 and 5<p.y<16 and max(v.z for v in bb)>.25:
   o.location.z-=.28;lowered.append(o.name)
stats['foreground_rocks_lowered']=len(lowered)
# Roof curvature and all visible derivatives receive one shared corner/rise correction.
roofstats=[]
for name in ['Z04_FULL_ROOF','Z05_FULL_ROOF','Z06_FULL_ROOF','Z11_FULL_ROOF']:
 roof=s.objects[name];pts=[roof.matrix_world@v.co for v in roof.data.vertices];xmin,xmax=min(v.x for v in pts),max(v.x for v in pts);ymin,ymax=min(v.y for v in pts),max(v.y for v in pts);zmin,zmax=min(v.z for v in pts),max(v.z for v in pts);cx=(xmin+xmax)/2;cy=(ymin+ymax)/2
 affected=[o for o in s.objects if o.type=='MESH' and not o.hide_render and (o==roof or o.name.startswith('SM_P6_'+name+'_'))]
 for o in affected:
  if o.data.users>1:o.data=o.data.copy()
  inv=o.matrix_world.inverted()
  for v in o.data.vertices:
   q=o.matrix_world@v.co;u=min(1.05,abs(q.x-cx)/((xmax-xmin)/2));w=min(1.05,abs(q.y-cy)/((ymax-ymin)/2));t=max(0,min(1,(q.z-zmin)/(zmax-zmin)));q.z+=.25*(u*w)**4+.18*t;v.co=inv@q
  o.data.update()
 roofstats.append({'roof':name,'shared_derivative_meshes':len(affected),'corner_lift_m':.25,'ridge_lift_m':.18})
# Thin round eave caps give readable termination at the two large front roofs.
clay=bpy.data.materials['P6_FIRED_CLAY_1'];vs=[];fs=[]
for name in ['Z04_FULL_ROOF','Z05_FULL_ROOF']:
 roof=s.objects[name];pts=[roof.matrix_world@v.co for v in roof.data.vertices[:4]];a,b=pts[0],pts[1];count=max(2,round((b-a).length/.235));direction=(b-a).normalized();normal=Vector((0,-1,0))
 for i in range(count):
  c=a.lerp(b,(i+.5)/count)+Vector((0,-.032,.038));base=len(vs)
  for depth in [0,.045]:
   for j in range(12):ang=j*math.tau/12;vs.append(tuple(c+normal*depth+direction*math.cos(ang)*.047+Vector((0,0,math.sin(ang)*.047))))
  for j in range(12):k=(j+1)%12;fs.append((base+j,base+k,base+k+12,base+j+12))
  fs.append(tuple(base+j+12 for j in range(12)))
mesh('SM_P7_MAIN_HALL_EAVE_TILE_ENDS',vs,fs,clay);stats['roof_revisions']=roofstats
# Material hierarchy: less chalky stone bed, clean neutral-blue reflections, translucent foliage.
wp=next(n for n in bpy.data.materials['P2_Water'].node_tree.nodes if n.type=='BSDF_PRINCIPLED');wp.inputs['Base Color'].default_value=(.82,.94,.97,1);wp.inputs['Transmission Weight'].default_value=.96;wp.inputs['Roughness'].default_value=.045
bed=bpy.data.materials['P6_SUBMERGED_PALE_MINERAL_BED'];mix=[n for n in bed.node_tree.nodes if n.type=='MIX_RGB'][-1];mix.inputs[2].default_value=(.24,.40,.34,1)
for m in bpy.data.materials:
 if m.name.startswith('CC0_TREE_LEAF_CARDS') and m.use_nodes:
  p=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Subsurface Weight'].default_value=.12;p.inputs['Roughness'].default_value=.56
 if m.name.startswith('P6_FIRED_CLAY_'):
  p=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Roughness'].default_value=.56;p.inputs['Specular IOR Level'].default_value=.38
sun=s.objects['DAY_SUN'];sun.data.energy=2.0;sun.data.color=(1,.92,.79);sun.data.angle=.045;s['day_sun_energy']=2.;s['day_world_strength']=.62;s['day_exposure']=.52;next(n for n in s.world.node_tree.nodes if n.type=='BACKGROUND').inputs['Strength'].default_value=.62;s.view_settings.exposure=.52
# Actual outer terrain acquires separated ridgelines; protected 800-vertex garden boundary remains exact.
outer=s.objects['SM_CONTINUOUS_OUTER_MOUNTAIN_RANGE'];changed=0
for v in outer.data.vertices:
 x,y,z=v.co
 if x<=100:continue
 t=max(0,min(1,(x-100)/80));t=t*t*(3-2*t)
 ridges=[]
 for k,(xx,w,h) in enumerate([(175,34,62),(290,54,96),(450,72,135),(650,95,170)]):
  center=xx+30*math.sin(y*.011+k*1.7);profile=math.exp(-((x-center)/w)**2);crest=.45+.55*(.5+.5*math.sin(y*(.014+k*.002)+k))
  ridges.append(h*profile*crest)
 target=-23+max(ridges)+7*noise.fractal(Vector((x*.016,y*.016,3.2)),1.,2.,3)
 # Near valley remains lower; changing ridge shape, not putting a backdrop in a camera.
 v.co.z=z*(1-t)+target*t;changed+=1
outer.data.update();stats['outer_mountain_vertices_regraded']=changed
# Distant existing forest cards follow actual regraded terrain, preserving rooted silhouettes.
obvh=BVHTree.FromPolygons([outer.matrix_world@v.co for v in outer.data.vertices],[tuple(f.vertices) for f in outer.data.polygons]);spritecards=0
for o in s.objects:
 if o.type!='MESH' or o.hide_render or not o.name.startswith('SM_FAR_FOREST_IMPOSTORS_'):continue
 me=o.data;seen=set()
 for f in me.polygons:
  if len(f.vertices)!=4:continue
  inds=list(f.vertices)
  if any(i in seen for i in inds):continue
  seen.update(inds);ps=[o.matrix_world@me.vertices[i].co for i in inds];cx=sum(p.x for p in ps)/4;cy=sum(p.y for p in ps)/4
  if cx<=100:continue
  hit,n,i,d=obvh.ray_cast(Vector((cx,cy,250)),Vector((0,0,-1)),500)
  if not hit:continue
  old=min(p.z for p in ps);height=max(p.z for p in ps)-old;delta=hit.z-height*.06-old;inv=o.matrix_world.inverted()
  for j in inds:q=o.matrix_world@me.vertices[j].co;q.z+=delta;me.vertices[j].co=inv@q
  spritecards+=1
 me.update()
stats['far_forest_cards_rerooted']=spritecards
s['version']='p3-v036';s['phase_status']='P7_REFERENCE_REPAIR_CANDIDATE';s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True);(R/'evidence/fidelity-repair-v036.json').write_text(json.dumps(stats,ensure_ascii=False,indent=2));print('FIDELITY_REPAIR_V036',stats,flush=True)
