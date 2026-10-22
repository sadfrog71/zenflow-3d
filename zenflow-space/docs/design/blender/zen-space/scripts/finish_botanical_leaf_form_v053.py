"""Rounded leaf blades and thin-leaf light transport; coherent trunk curvature across all shared parts."""
import bpy,json,math
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;assert s.get('version')=='p3-v052';out=R/'zen-space-p3-v053.blend';assert not out.exists();meshes={o.data for o in s.objects if o.type=='MESH' and not o.hide_render and o.data.name.startswith('P12_') and 'FOLDED_LEAVES' in o.data.name};counts=[]
for old in meshes:
 vs=[];fs=[];mi=[];uvs=[]
 for k in range(len(old.vertices)//5):
  q=[old.vertices[k*5+j].co.copy() for j in range(5)];c=(q[0]+q[2])*.5;u=(q[2]-q[0]);length=u.length;u.normalize();v=q[1]-q[3];width=v.length;v.normalize();n=u.cross(v).normalized();fold=(q[4]-c).dot(n);b=len(vs)
  for x,y in [(-.5,0),(-.24,.5),(.16,.40),(.5,0),(.16,-.40),(-.24,-.5)]:
   vs.append(tuple(c+u*x*length+v*y*width+n*fold*max(0,1-abs(x)*2)*.12));uvs.append((x+.5,y+.5))
  vs.append(tuple(c+n*fold*.75));uvs.append((.5,.5))
  for j in range(6):fs.append((b+j,b+(j+1)%6,b+6));mi.append(old.polygons[k*4].material_index)
 me=bpy.data.meshes.new(old.name+'_OVATE');me.from_pydata(vs,[],fs);me.update()
 for m in old.materials:me.materials.append(m)
 for p,slot in zip(me.polygons,mi):p.material_index=slot;p.use_smooth=True
 uv=me.uv_layers.new(name='Leaf_blade_coordinates')
 for p in me.polygons:
  for li in p.loop_indices:uv.data[li].uv=uvs[me.loops[li].vertex_index]
 for o in s.objects:
  if o.type=='MESH' and o.data==old:o.data=me
 counts.append({'mesh':me.name,'leaves':len(vs)//7,'triangles_per_leaf':6})
# A shared coherent deformation, applied once to each unique tree mesh, preserves branch-to-foliage attachment.
allmeshes={o.data for o in s.objects if o.type=='MESH' and not o.hide_render and o.data.name.startswith('P12_')}
for me in allmeshes:
 for v in me.vertices:
  x,y,z=v.co;t=max(0,min(1,z/1.2));t=t*t*(3-2*t);v.co.x=x+t*(.20*math.sin(z*.8)+.09*math.sin(z*1.75));v.co.y=y+t*(.15*math.sin(z*.93)+.07*math.sin(z*2.1))
  if z<.75:
   f=.74+.26*max(0,z/.75);v.co.x*=f;v.co.y*=f
 me.update()
for i in range(6):
 m=bpy.data.materials['P12_LIVING_LEAF_'+str(i)];nt=m.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');output=next(n for n in nt.nodes if n.type=='OUTPUT_MATERIAL');trans=nt.nodes.new('ShaderNodeBsdfTranslucent');trans.inputs['Color'].default_value=p.inputs['Base Color'].default_value;mix=nt.nodes.new('ShaderNodeMixShader');mix.inputs[0].default_value=.18;nt.links.new(p.outputs['BSDF'],mix.inputs[1]);nt.links.new(trans.outputs[0],mix.inputs[2]);nt.links.new(mix.outputs[0],output.inputs['Surface']);m['exchange_use_principled_fallback']=True;m['source_material_note']='thin leaf diffuse transmission; portable glTF uses Principled approximation'
s['version']='p3-v053';s['phase_status']='P12_IMAGEGEN_TREE_REFERENCE_REPAIR';p=R/'manifest/scene-manifest.json';m=json.loads(p.read_text());m.update(version='p3-v053',source_version='p3-v053',source_snapshot=out.name);m['botanical_tree_revision']['leaf_form']='ovate six-edge folded blades with restrained thin-leaf translucency';p.write_text(json.dumps(m,ensure_ascii=False,indent=2));s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True);(R/'evidence/botanical-leaf-finish-v053.json').write_text(json.dumps({'leaf_prototypes':counts,'thin_leaf_mix_weight':.18,'coherent_branch_canopy_curvature':True,'root_flare_restrained':True,'layout_lighting_camera_unchanged':True},indent=2));print('LEAF_FORM_FINISHED',len(counts),flush=True)
