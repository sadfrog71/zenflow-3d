"""Second near-view pass: cast bronze, hewn stone, smooth striker and grounded understory."""
import bpy,sys,math,json,random
from pathlib import Path
from mathutils import Vector
from mathutils.bvhtree import BVHTree
R=Path(__file__).resolve().parents[1];sys.path.insert(0,str(R/'scripts'));from phase2_materials import beam,box
s=bpy.context.scene;out=R/'zen-space-p3-v030.blend'
if s.get('version')!='p3-v029' or out.exists():raise RuntimeError('Requires v029 and unused output')
C=bpy.data.collections['P5_NEAR_VIEW_CRAFT_20261021'];rng=random.Random(211030);stats={}
stone=bpy.data.materials['P5_FINE_DRESSED_LIMESTONE'];wood=bpy.data.materials['P2_Cedar'];dark=bpy.data.materials['P2_Darkwood']
# Keep stone grain fine on carved vessels; large fractures belong to unworked natural rocks.
o=s.objects['SM_Z07_HOLLOW_STONE_BOWL'];o.data=o.data.copy();o.data.materials.clear();o.data.materials.append(stone)
uv=o.data.uv_layers.active or o.data.uv_layers.new()
for f in o.data.polygons:
 for li in f.loop_indices:
  v=o.data.vertices[o.data.loops[li].vertex_index].co;uv.data[li].uv=(math.atan2(v.y,v.x)/math.tau*3,v.z*2)
stats['hewn_stone_bowl']=True
# Cast bronze microstructure and restrained weathering, no symbols or added inscription.
bronze=bpy.data.materials['P3_BRONZE'];nt=bronze.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Metallic'].default_value=.72
tex=nt.nodes.new('ShaderNodeTexNoise');tex.inputs['Scale'].default_value=3.5;tex.inputs['Detail'].default_value=3;coord=nt.nodes.new('ShaderNodeTexCoord');nt.links.new(coord.outputs['Generated'],tex.inputs['Vector']);r=nt.nodes.new('ShaderNodeValToRGB');r.color_ramp.elements[0].position=.28;r.color_ramp.elements[0].color=(.095,.065,.03,1);r.color_ramp.elements[1].position=.73;r.color_ramp.elements[1].color=(.22,.28,.18,1);r.color_ramp.elements.new(.51).color=(.28,.19,.075,1);nt.links.new(tex.outputs['Fac'],r.inputs['Fac']);nt.links.new(r.outputs[0],p.inputs['Base Color']);fine=nt.nodes.new('ShaderNodeTexNoise');fine.inputs['Scale'].default_value=165;fine.inputs['Detail'].default_value=2;nt.links.new(coord.outputs['Generated'],fine.inputs['Vector']);b=nt.nodes.new('ShaderNodeBump');b.inputs['Strength'].default_value=.22;b.inputs['Distance'].default_value=.0008;nt.links.new(fine.outputs['Fac'],b.inputs['Height']);nt.links.new(b.outputs[0],p.inputs['Normal']);p.inputs['Roughness'].default_value=.46
# The original mallet object and its motion remain, with a 64-sided, bevelled timber body.
o=s.objects['Z07_MALLET'];inv=o.matrix_world.inverted();center=o.matrix_world.translation.copy();n=64;verts=[];faces=[]
for xx,rr in [(-.4,.068),(-.388,.08),(.388,.08),(.4,.068)]:
 for i in range(n):a=i*math.tau/n;v=Vector((-3.3+xx,21.5+math.cos(a)*rr,1.2+math.sin(a)*rr));verts.append(tuple(inv@v))
for j in range(3):
 for i in range(n):a=j*n+i;b=j*n+(i+1)%n;faces.append((a,b,b+n,a+n))
faces.extend([tuple(reversed(range(n))),tuple(3*n+i for i in range(n))]);me=bpy.data.meshes.new('P5_SMOOTH_HARDWOOD_MALLET');me.from_pydata(verts,[],faces);me.update();o.data=me;me.materials.append(wood)
uv=me.uv_layers.new()
for f in me.polygons:
 f.use_smooth=len(f.vertices)==4
 for li in f.loop_indices:
  v=o.matrix_world@me.vertices[me.loops[li].vertex_index].co;uv.data[li].uv=(math.atan2(v.z-1.2,v.y-21.5)/math.tau,v.x*1.7)
stats['smooth_action_preserved_mallet']=True
# Subtle natural rock grain instead of deep, repeated carved cracks.
rock=bpy.data.materials['P3_Weathered_Gray_Rock']
for n in rock.node_tree.nodes:
 if n.type=='NORMAL_MAP':n.inputs['Strength'].default_value=.30
stats['natural_rock_normal_strength']=.30
# Add understated casting lines on the existing bell, sized to its real surface.
for z,rr in [(1.705,.465),(2.05,.448)]:
 n=96;k=6;vs=[];fs=[]
 for i in range(n):
  a=i*math.tau/n
  for j in range(k):b=j*math.tau/k;r=rr+.004*math.cos(b);vs.append((-3+r*math.cos(a),22+r*math.sin(a),z+.004*math.sin(b)))
 for i in range(n):
  for j in range(k):fs.append((i*k+j,((i+1)%n)*k+j,((i+1)%n)*k+(j+1)%k,i*k+(j+1)%k))
 me=bpy.data.meshes.new('P5_BELL_FINE_CASTING_RING');me.from_pydata(vs,[],fs);me.update();ob=bpy.data.objects.new('SM_P5_BELL_FINE_CASTING_RING',me);C.objects.link(ob);me.materials.append(bronze)
 for f in me.polygons:f.use_smooth=True
# Quiet understory clusters under the existing near trees; every root fits actual terrain.
terrain=s.objects['TERRAIN_CONTINUOUS_BASE'];bvh=BVHTree.FromPolygons([terrain.matrix_world@v.co for v in terrain.data.vertices],[tuple(f.vertices) for f in terrain.data.polygons]);routes=json.loads((R/'manifest/scene-manifest.json').read_text())['routes']
def dist(x,y,a,b):
 dx=b[0]-a[0];dy=b[1]-a[1];t=max(0,min(1,((x-a[0])*dx+(y-a[1])*dy)/max(1e-8,dx*dx+dy*dy)));return math.hypot(x-a[0]-t*dx,y-a[1]-t*dy)
meshes=[bpy.data.meshes['P5_SHARED_SHORE_LEAF_CLUSTER_'+str(i)] for i in range(3)];count=0
for cx,cy,rx,ry in [(-43,22,6,9),(-34,34,10,5),(-13,28,6,5),(10,40,8,4),(34,29,5,6),(24,50,13,5)]:
 for k in range(34):
  x=cx+rng.uniform(-rx,rx);y=cy+rng.uniform(-ry,ry)
  if any(dist(x,y,r['from'],r['to'])<r['width']/2+.35 for r in routes):continue
  hit,n,idx,d=bvh.ray_cast(Vector((x,y,70)),Vector((0,0,-1)),130)
  if not hit or hit.z<-1.05 or n.z<.7:continue
  # Exclude inhabited footprints, terraces and the wish/lookout platforms.
  if (-24<x<-12 and -16<y<2) or (19<x<29 and 13<y<21) or (26<x<34 and 20<y<28):continue
  if math.hypot(x+25,y-35)<5.5 or math.hypot(x-25,y-35)<5.5:continue
  ob=bpy.data.objects.new('SM_P5_NEAR_GROVE_UNDERSTORY',meshes[k%3]);C.objects.link(ob);ob.location=hit;ob.rotation_euler.z=rng.random()*math.tau;ob.scale=(rng.uniform(1.2,2.2),)*3;count+=1
stats['grounded_understory_clusters']=count
# Fine worn edges, not chipped destruction, on the exposed paper stack. Keep content surface identical.
for o in s.objects:
 if o.type=='MESH' and not o.hide_render and o.name.startswith('SM_Z05_PAPER_STACK_EDGE'):
  for m in o.modifiers:
   if m.type=='BEVEL':m.width=min(m.width,.0005)
s['version']='p3-v030';s['phase_status']='P5_DETAIL_RENDER_REVIEW';s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
(R/'evidence/detail-refinement-v030.json').write_text(json.dumps({'version':'p3-v030','base':'p3-v029','changes':stats,'cameras_and_layout':'unchanged','action_objects':'preserved; no source animation deleted','bronze_runtime_note':'procedural cast-bronze texture must be baked for GLB'},indent=2));print('DETAIL_PASS2_SAVED',stats,flush=True)
