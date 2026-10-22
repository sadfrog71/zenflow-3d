"""Measured context planting outside walking envelopes; correct overly high close horizon."""
import bpy,math,random,json
from pathlib import Path
from mathutils import Vector,Matrix
from mathutils.bvhtree import BVHTree
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v014.blend'
if s.get('version')!='p3-v013' or out.exists():raise RuntimeError('Requires new v014 from v013')
rng=random.Random(20261020);m=json.loads((R/'manifest/scene-manifest.json').read_text());routes=m['routes'];C=bpy.data.collections.new('P3_CONTEXT_GROVES_AND_BANK_PLANTS');s.collection.children.link(C)
terrain=bpy.data.objects['TERRAIN_CONTINUOUS_BASE'];pts=[terrain.matrix_world@v.co for v in terrain.data.vertices];bvh=BVHTree.FromPolygons(pts,[tuple(p.vertices) for p in terrain.data.polygons])
def ground(x,y):
 p,n,i,d=bvh.ray_cast(Vector((x,y,100)),Vector((0,0,-1)),200);return p.z if p else None

def clear(x,y,radius):
 for r in routes:
  a,b=r['from'],r['to'];dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/max(.0001,dx*dx+dy*dy)))
  if math.hypot(x-a[0]-t*dx,y-a[1]-t*dy)<r['width']/2+radius+.3:return False
 return True
# Stretch the mountain approach horizontally in height space, retain original exact inner rim.
outer=s.objects['SM_CONTINUOUS_OUTER_MOUNTAIN_RANGE'];L=800
for i,v in enumerate(outer.data.vertices):
 j=i//L;dist=680*(j/48)**1.6;w=min(1,dist/180);w=w*w*(3-2*w)
 if j:
  edge=outer.data.vertices[i%L].co.z;v.co.z=edge*(1-w)+v.co.z*.66*w
# Whole photographed trees keep source mesh/material/UV alignment. Sparse old variants stay as edit sources.
parts={role:bpy.data.objects['CC0_TREE_'+role+'_FAR'].data for role in ['trunk','branches']};parts['leaves']=bpy.data.objects['CC0_LEAF_CARDS_FAR'].data
tr=parts['trunk'];zmin=min(v.co.z for v in tr.vertices);roots=[v.co for v in tr.vertices if v.co.z<zmin+.08];root=Vector((sum(v.x for v in roots)/len(roots),sum(v.y for v in roots)/len(roots),zmin));zmax=max(v.co.z for v in parts['leaves'].vertices);height=zmax-zmin;count=0;planted=[]
for attempt in range(1200):
 if count>=230:break
 x=rng.uniform(-62,72);y=rng.uniform(-27,93);z=ground(x,y)
 if z is None or z<-.55 or not clear(x,y,1.3):continue
 # Buildings, court and carefully framed private garden remain clear.
 if -23<x<34 and -22<y<24:continue
 if math.hypot(x-29,y-24)<7:continue
 if any(math.hypot(x-a,y-b)<2.8 for a,b in planted):continue
 if y<24 and x>-35:continue
 h=rng.uniform(3.2,6.0);sc=h/height;mat=Matrix.Translation(Vector((x,y,z-.025)))@Matrix.Rotation(rng.random()*math.tau,4,'Z')@Matrix.Diagonal((sc*rng.uniform(.9,1.2),sc,sc,1))@Matrix.Translation(-root)
 for role,data in parts.items():
  ob=bpy.data.objects.new('SM_CONTEXT_GROVE_'+str(count)+'_'+role.upper(),data);C.objects.link(ob);ob.matrix_world=mat;ob['license']='CC0';ob['source']='https://polyhaven.com/a/tree_small_02'
 planted.append((x,y));count+=1
ferns=[o for o in bpy.data.objects if o.type=='MESH' and o.name.startswith('CC0_FERN_VARIANT')]
if not ferns:ferns=[o for o in bpy.data.objects if o.type=='MESH' and o.name.startswith('SM_CC0_FERN_BANK')][:4]
ferncount=0
for attempt in range(1800):
 if not ferns or ferncount>=450:break
 # Decorate the actual upper cascade bank and wooded shoulder, not arbitrary lawn dots.
 x=rng.uniform(-44,-18);y=rng.uniform(15,39);z=ground(x,y)
 if z is None or z<-.5 or not clear(x,y,.45):continue
 if math.hypot(x+26,y-31)<4.7:continue
 source=ferns[ferncount%len(ferns)];ob=bpy.data.objects.new('SM_CASCADE_BANK_FERN_'+str(ferncount),source.data);C.objects.link(ob);ob.location=(x,y,z);sc=rng.uniform(.65,1.2);ob.scale=(sc,sc,sc);ob.rotation_euler.z=rng.random()*math.tau;ferncount+=1
# Physical material brightness correction: keep photographed grain, warm the existing timber family.
for name in ['P2_Cedar','P3_Honey_Wood']:
 mat=bpy.data.materials[name];nt=mat.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');old=p.inputs['Base Color'].links[0].from_socket if p.inputs['Base Color'].links else None
 if old:
  hsv=nt.nodes.new('ShaderNodeHueSaturation');hsv.inputs['Saturation'].default_value=.95;hsv.inputs['Value'].default_value=1.32;nt.links.new(old,hsv.inputs['Color']);nt.links.new(hsv.outputs[0],p.inputs['Base Color'])
# Source lighting remains coherent with every fixed view; material/sky/sun only.
sun=s.objects['DAY_SUN'];sun.data.energy=1.8;sun.data.color=(1,.90,.74);bg=next(n for n in s.world.node_tree.nodes if n.type=='BACKGROUND');bg.inputs['Strength'].default_value=.5;s.view_settings.exposure=.55
s['version']='p3-v014';s['phase_status']='P3_FULL_VIEW_REVIEW_PENDING';bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
(R/'evidence/lush-context-build.json').write_text(json.dumps({'version':'p3-v014','whole_photo_tree_instances':count,'bank_ferns':ferncount,'routes_clearance_m':'half route width + radius + .3m','protected_core_terrain_changed':False,'horizon':'outer approach eased over 180m, physical 3D mesh retained'},indent=2));print('LUSH_CONTEXT',count,ferncount,flush=True)
