"""Replace raster soil edge with clipped curved solid, densify existing leaf-card shrubs, reshape actual terrace skirts."""
import bpy,math,random,json
from pathlib import Path
from mathutils import Vector
from mathutils.bvhtree import BVHTree
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v041.blend'
if s.get('version')!='p3-v040' or out.exists():raise RuntimeError('Requires new v041')
C=bpy.data.collections['P8_SHARED_GARDEN_COMPOSITION'];routes=json.loads((R/'manifest/scene-manifest.json').read_text())['routes'];soil=bpy.data.materials['P4_PHOTOGRAPHED_FOREST_SOIL'];stone=bpy.data.materials['P5_FINE_DRESSED_LIMESTONE'];n=144;vs=[];fs=[]
for z in [-.07,.025]:
 for i in range(n):a=i*math.tau/n;rad=1+.11*math.sin(3*a)+.06*math.sin(5*a);vs.append((-.6+5.4*rad*math.cos(a),10.1+5.8*rad*math.sin(a),z))
fs.extend([tuple(reversed(range(n))),tuple(n+i for i in range(n))])
for i in range(n):j=(i+1)%n;fs.append((i,j,j+n,i+n))
me=bpy.data.meshes.new('P8_CONTINUOUS_CURVED_COURT_SOIL');me.from_pydata(vs,[],fs);me.update();me.materials.append(soil);ob=s.objects['SM_P8_Z01_CURVED_PLANTING_BED'];ob.data=me;cuts=0
for row in routes:
 a=Vector(row['from']);b=Vector(row['to']);rad=row['width']/2+.58
 if max(a.x,b.x)+rad<-7 or min(a.x,b.x)-rad>6 or max(a.y,b.y)+rad<3.5 or min(a.y,b.y)-rad>17:continue
 d=b-a;length=math.hypot(d.x,d.y)
 if not length:continue
 cutters=[];bpy.ops.mesh.primitive_cube_add(size=1,location=((a.x+b.x)/2,(a.y+b.y)/2,.05));cu=bpy.context.object;cu.dimensions=(length,rad*2,1);cu.rotation_euler.z=math.atan2(d.y,d.x);bpy.ops.object.transform_apply(location=False,rotation=False,scale=True);cutters.append(cu)
 for p in [a,b]:bpy.ops.mesh.primitive_cylinder_add(vertices=40,radius=rad,depth=1,location=(p.x,p.y,.05));cutters.append(bpy.context.object)
 for cu in cutters:
  bpy.context.view_layer.objects.active=ob;mod=ob.modifiers.new('Unchanged route envelope','BOOLEAN');mod.operation='DIFFERENCE';mod.solver='EXACT';mod.object=cu;bpy.ops.object.modifier_apply(modifier=mod.name);bpy.data.objects.remove(cu,do_unlink=True);cuts+=1
uv=ob.data.uv_layers.new()
for p in ob.data.polygons:
 for li in p.loop_indices:q=ob.data.vertices[ob.data.loops[li].vertex_index].co;uv.data[li].uv=(q.x*.5,q.y*.5)
# Whole-card leaf coverage, using unchanged licensed leaf images and UVs. Two nested populations share one mesh per variant.
meshes=[]
for k in range(3):
 src=bpy.data.meshes['P6_SHARED_LOW_SHRUB_'+str(k)];uv0=src.uv_layers.active;vv=[];ff=[];coords=[]
 for layer in range(2):
  angle=layer*1.31;co,si=math.cos(angle),math.sin(angle)
  for f in src.polygons:
   centroid=sum((src.vertices[i].co for i in f.vertices),Vector())/len(f.vertices);center=centroid.copy()
   if layer:center.x,center.y=center.x*.67,center.y*.67;center.z=center.z*.77
   base=len(vv)
   for li in f.loop_indices:
    q=center+(src.vertices[src.loops[li].vertex_index].co-centroid)*2.15
    if layer:q.x,q.y=q.x*co-q.y*si,q.x*si+q.y*co
    vv.append(tuple(q));coords.append(tuple(uv0.data[li].uv))
   ff.append(tuple(base+i for i in range(len(f.vertices))))
 me=bpy.data.meshes.new('P8_SHARED_DENSE_SHRUB_'+str(k));me.from_pydata(vv,[],ff);me.update()
 for mat in src.materials:me.materials.append(mat)
 uv=me.uv_layers.new()
 for f in me.polygons:
  for li in f.loop_indices:uv.data[li].uv=coords[me.loops[li].vertex_index]
 meshes.append(me)
count=0
for o in s.objects:
 if o.name.startswith(('SM_P8_Z01_COURT_GARDEN_CLUSTER','SM_P8_HILL_PLANTING_MASS')):o.data=meshes[count%3];count+=1
# Reshape original high-platform skirts into broad irregular shoulders, with unchanged top edges and levels.
terrain=s.objects['TERRAIN_CONTINUOUS_BASE'];bvh=BVHTree.FromPolygons([terrain.matrix_world@v.co for v in terrain.data.vertices],[tuple(f.vertices) for f in terrain.data.polygons]);report=[]
for name,cx,cy in [('Z08_TERRACE',-26,31),('Z09_TERRACE',0,36)]:
 o=s.objects[name];me=o.data;assert len(me.vertices)==576;world=[o.matrix_world@v.co for v in me.vertices];tops=world[:96];inv=o.matrix_world.inverted()
 for j in range(1,6):
  t=j/5
  for i in range(96):
   p=tops[i];a=math.atan2(p.y-cy,p.x-cx);r=math.hypot(p.x-cx,p.y-cy);rr=r+t*(2.6+.45*math.sin(a*3)+.25*math.sin(a*8));x=cx+math.cos(a)*rr;y=cy+math.sin(a)*rr;hit,norm,idx,d=bvh.ray_cast(Vector((x,y,50)),Vector((0,0,-1)),100);base=min(p.z-.15,(hit.z if hit else 0)-.05);z=p.z+(base-p.z)*t**.75+.12*math.sin(a*7+j)*math.sin(math.pi*t);me.vertices[j*96+i].co=inv@Vector((x,y,z))
 me.update();me.materials.append(bpy.data.materials['P4_PHOTOGRAPHED_LEAFY_GROUND']);slot=len(me.materials)-1
 for f in me.polygons:
  if len(f.vertices)==4 and f.normal.z>.65:f.material_index=slot
 report.append(name)
s['version']='p3-v041';s['phase_status']='P8_COMPOSITION_RENDER_REVIEW';s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True);(R/'evidence/composition-finish-v041.json').write_text(json.dumps({'curved_soil_route_boolean_cuts':cuts,'dense_shrub_instances':count,'reshaped_terrace_skirts':report,'unchanged':'terrace top ring, routes, building positions, actors and first-person camera positions'},indent=2));print('COMPOSITION_FINISH',cuts,count,report)
