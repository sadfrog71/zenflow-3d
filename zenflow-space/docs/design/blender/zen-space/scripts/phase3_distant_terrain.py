"""Continuous terrain outside protected 150m garden: no changes to routes/buildings."""
import bpy,math,json
from pathlib import Path
from mathutils import Vector,noise
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v010.blend'
if s.get('version')!='p3-v009' or out.exists():raise RuntimeError('Requires new v010 from v009')
terrain=bpy.data.objects['TERRAIN_CONTINUOUS_BASE'];pts=[terrain.matrix_world@v.co for v in terrain.data.vertices];N=201
edge=[pts[i] for i in range(N)]+[pts[j*N+N-1] for j in range(1,N)]+[pts[(N-1)*N+i] for i in range(N-2,-1,-1)]+[pts[j*N] for j in range(N-2,0,-1)]
L=len(edge);v=[];f=[];rings=49
for j in range(rings):
 t=j/(rings-1);dist=680*t**1.6
 for q in edge:
  direction=Vector((q.x-10,q.y-40,0)).normalized();x=q.x+direction.x*dist;y=q.y+direction.y*dist
  n=noise.fractal(Vector((x*.008,y*.008,2.4)),1.1,2,5);detail=noise.fractal(Vector((x*.027,y*.027,7)),1.0,2,3)
  # Three natural uneven ridges with a north-facing valley and atmospheric distance.
  ridge=math.sin(min(1,dist/360)*math.pi)*(.7+.5*math.sin(math.atan2(y-40,x-10)*3+1))
  h=-9+75*ridge+45*n+7*detail
  if y>70 and abs(x-10)<60+dist*.15:h-=22*math.exp(-((x-10)/(50+dist*.2))**2)
  w=min(1,dist/25);w=w*w*(3-2*w);z=q.z*(1-w)+h*w
  if j==0:z=q.z
  v.append((x,y,z))
for j in range(rings-1):
 for i in range(L):a=j*L+i;b=j*L+(i+1)%L;f.append((a,b,b+L,a+L))
me=bpy.data.meshes.new('Continuous outer mountain mesh');me.from_pydata(v,[],f);me.update();o=bpy.data.objects.new('SM_CONTINUOUS_OUTER_MOUNTAIN_RANGE',me);s.collection.objects.link(o)
mat=bpy.data.materials.new('P3_MOUNTAIN_FOREST_AND_ROCK');mat.diffuse_color=(.10,.17,.13,1);mat.use_nodes=True;n=mat.node_tree.nodes;links=mat.node_tree.links;p=next(x for x in n if x.type=='BSDF_PRINCIPLED');p.inputs['Roughness'].default_value=.95
geom=n.new('ShaderNodeNewGeometry');tex=n.new('ShaderNodeTexNoise');tex.inputs['Scale'].default_value=.075;tex.inputs['Detail'].default_value=4;links.new(geom.outputs['Position'],tex.inputs['Vector']);ramp=n.new('ShaderNodeValToRGB');ramp.color_ramp.elements[0].position=.23;ramp.color_ramp.elements[0].color=(.038,.075,.045,1);ramp.color_ramp.elements[1].position=.78;ramp.color_ramp.elements[1].color=(.19,.23,.15,1);mid=ramp.color_ramp.elements.new(.52);mid.color=(.085,.14,.078,1);links.new(tex.outputs['Fac'],ramp.inputs[0]);links.new(ramp.outputs[0],p.inputs['Base Color']);me.materials.append(mat)
for face in me.polygons:face.use_smooth=True
retired=[]
for ob in s.objects:
 if ob.name.startswith(('DISTANT_','WEST_MOUNTAIN','EAST_MOUNTAIN','SM_DISTANT_','P2_DISTANT_')):
  if not ob.hide_render:retired.append(ob.name)
  ob.hide_render=True;ob.hide_viewport=True
s['version']='p3-v010';s['phase_status']='P3_TERRAIN_AND_ROOF_REVIEW_PENDING';bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
(R/'evidence/outer-terrain-construction.json').write_text(json.dumps({'version':'p3-v010','boundary_vertices':L,'boundary_exact_match':True,'protected_core_terrain_changed':False,'faces':len(f),'outer_extension_m':680,'retired_proxies':retired},indent=2));print('TERRAIN_RING_DONE',len(f),len(retired),flush=True)
