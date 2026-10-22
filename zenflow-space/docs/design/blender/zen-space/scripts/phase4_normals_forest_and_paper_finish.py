"""Fix downward outer terrain normals, actual distant tree sprites and lantern light transport."""
import bpy,bmesh,math,json,random
from pathlib import Path
from mathutils import Vector
from mathutils.bvhtree import BVHTree
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v024.blend';rng=random.Random(21024)
if s.get('version')!='p3-v023' or out.exists():raise RuntimeError('Requires new v024 from v023')
outer=s.objects['SM_CONTINUOUS_OUTER_MOUNTAIN_RANGE'];bm=bmesh.new();bm.from_mesh(outer.data);bm.normal_update();bad=[f for f in bm.faces if f.normal.z<0];flipped=len(bad);bmesh.ops.reverse_faces(bm,faces=bad);bm.to_mesh(outer.data);bm.free();outer.data.update()
C=bpy.data.collections['P4_FAR_FOREST_CUTAWAY_EXCLUSION']
for o in list(C.objects):
 if o.name.startswith('SM_FAR_FOREST_IMPOSTORS_'):bpy.data.objects.remove(o,do_unlink=True)
pts=[outer.matrix_world@v.co for v in outer.data.vertices];bvh=BVHTree.FromPolygons(pts,[tuple(f.vertices) for f in outer.data.polygons]);vs=[[],[],[]];fs=[[],[],[]];uvs=[[],[],[]];count=0
for ix in range(76):
 for iy in range(69):
  x=-420+ix*13+rng.uniform(-4,4);y=-260+iy*13+rng.uniform(-4,4)
  if -110<x<130 and -80<y<160:continue
  hit,n,idx,d=bvh.ray_cast(Vector((x,y,180)),Vector((0,0,-1)),400)
  if not hit or hit.z<-5 or abs(n.z)<.6 or rng.random()<.15:continue
  slot=count%3;h=rng.uniform(5,9);w=h;angle=rng.random()*math.tau
  for a in [angle,angle+math.pi/2]:
   u=Vector((math.cos(a)*w/2,math.sin(a)*w/2,0));p=Vector((x,y,hit.z-h*.06));k=len(vs[slot]);vs[slot].extend([p-u,p+u,p+u+Vector((0,0,h)),p-u+Vector((0,0,h))]);fs[slot].append((k,k+1,k+2,k+3));uvs[slot].extend([(0,0),(1,0),(1,1),(0,1)])
  count+=1
if count<100:raise RuntimeError('Far forest placement failed')
for i in range(3):
 me=bpy.data.meshes.new('P4_FAR_FOREST_FIXED_'+str(i));me.from_pydata(vs[i],[],fs[i]);me.materials.append(bpy.data.materials['P4_FAR_FOREST_SPRITE_'+str(i)]);uv=me.uv_layers.new()
 for f in me.polygons:
  for li in f.loop_indices:uv.data[li].uv=uvs[i][me.loops[li].vertex_index]
 ob=bpy.data.objects.new('SM_FAR_FOREST_IMPOSTORS_'+str(i),me);C.objects.link(ob);ob['license']='CC0';ob['source']='https://polyhaven.com/a/tree_small_02';ob['lod_use']='paired source-tree sprite cards only outside functional site'
# Opaque emitting boxes trapped their own point lights. Thin-paper diffuse transmission is physical source shading.
materials=[]
for name,factor in [('P2_Glow',.7),('P3_COTTAGE_SHADE',.65)]:
 m=bpy.data.materials[name];nt=m.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');output=next(n for n in nt.nodes if n.type=='OUTPUT_MATERIAL');trans=nt.nodes.new('ShaderNodeBsdfTranslucent');trans.inputs['Color'].default_value=(.88,.76,.57,1);mix=nt.nodes.new('ShaderNodeMixShader');mix.inputs[0].default_value=factor;nt.links.new(p.outputs['BSDF'],mix.inputs[1]);nt.links.new(trans.outputs[0],mix.inputs[2]);nt.links.new(mix.outputs[0],output.inputs['Surface']);m['exchange_use_principled_fallback']=True;materials.append(name)
paper=bpy.data.materials['P2_Paper'].copy();paper.name='P4_WINDOW_PAPER_DIFFUSE_TRANSMISSION';nt=paper.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');output=next(n for n in nt.nodes if n.type=='OUTPUT_MATERIAL');trans=nt.nodes.new('ShaderNodeBsdfTranslucent');trans.inputs['Color'].default_value=(.85,.80,.68,1);mix=nt.nodes.new('ShaderNodeMixShader');mix.inputs[0].default_value=.65;nt.links.new(p.outputs['BSDF'],mix.inputs[1]);nt.links.new(trans.outputs[0],mix.inputs[2]);nt.links.new(mix.outputs[0],output.inputs['Surface']);paper['exchange_use_principled_fallback']=True
for ob in s.objects:
 if ob.type=='MESH' and ob.name.startswith('Z05_P2_WINDOW_PAPER'):ob.data=ob.data.copy();ob.data.materials.clear();ob.data.materials.append(paper)
s['version']='p3-v024';s['phase_status']='P4_REFINED_REFERENCE_SELF_CHECK';s.frame_set(1);t=bpy.data.texts.get('READ_ME_FIRST')
if t:t.from_string(t.as_string().replace('p3-v023','p3-v024')+'\nLantern and paper-window source use diffuse transmission. GLB fallback and runtime recreation are listed separately.\n')
bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
m=json.loads((R/'manifest/scene-manifest.json').read_text());m['version']='p3-v024';m['source_version']='p3-v024';m['source_snapshot']=out.name;(R/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2));(R/'evidence/normals-forest-and-paper-repair.json').write_text(json.dumps({'version':'p3-v024','outer_faces_reoriented_upward':flipped,'far_source_tree_impostors':count,'forest_mesh_nodes':3,'paper_transmission_materials':materials+[paper.name],'diagnoses':['outer ring face winding was downward, so forest placement normal filter rejected every sample','opaque lantern shells trapped internal point lights; source now uses diffuse paper transmission'],'exchange_policy':'use source Principled fallback for these 3 paper materials; recreate thin-paper light transport in runtime'},indent=2));print('NORMALS_FOREST_PAPER_FINISHED',flipped,count,flush=True)
