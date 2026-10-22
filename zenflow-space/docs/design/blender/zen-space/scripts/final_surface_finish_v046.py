"""Measured contact-preserving finish: remove collapsed path faces, real rafter UVs and height-based bank wetness."""
import bpy,bmesh,json,math
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v046.blend';assert s.get('version')=='p3-v045' and not out.exists();net=s.objects['PUBLIC_EAST_ROUTE_RESOLVED_NETWORK'];bm=bmesh.new();bm.from_mesh(net.data);bad=[f for f in bm.faces if f.calc_area()<1e-10];count=len(bad);bmesh.ops.delete(bm,geom=bad,context='FACES_ONLY');bm.to_mesh(net.data);bm.free();net.data.update()
uvs=[]
for o in s.objects:
 if o.type!='MESH' or o.hide_render or not o.name.startswith('SM_P6_') or 'UNDER_ROOF_RAFTERS' not in o.name:continue
 if o.data.users>1:o.data=o.data.copy()
 me=o.data;uv=me.uv_layers.active or me.uv_layers.new(name='Timber_grain_metres');rings=19;n=8;chunk=rings*n;distance={}
 for start in range(0,len(me.vertices),chunk):
  prev=None;running=0
  for k in range(rings):
   ids=range(start+k*n,min(start+(k+1)*n,len(me.vertices)));pts=[o.matrix_world@me.vertices[i].co for i in ids]
   if not pts:continue
   c=sum(pts,Vector())/len(pts)
   if prev is not None:running+=(c-prev).length
   prev=c
   for q,i in enumerate(ids):distance[i]=(q/n,running*.65)
 for poly in me.polygons:
  for li in poly.loop_indices:uv.data[li].uv=distance.get(me.loops[li].vertex_index,(0,0))
 uvs.append(o.name)
m=bpy.data.materials['P4_MOSSY_BANK_ROCK'];nt=m.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');g=nt.nodes.new('ShaderNodeNewGeometry');sep=nt.nodes.new('ShaderNodeSeparateXYZ');nt.links.new(g.outputs['Position'],sep.inputs[0]);wet=nt.nodes.new('ShaderNodeMapRange');wet.clamp=True;wet.inputs['From Min'].default_value=-1.45;wet.inputs['From Max'].default_value=-.68;wet.inputs['To Min'].default_value=1;wet.inputs['To Max'].default_value=0;nt.links.new(sep.outputs['Z'],wet.inputs['Value']);mix=nt.nodes.new('ShaderNodeMixRGB');mix.blend_type='MULTIPLY';mix.inputs[2].default_value=(.52,.59,.55,1);nt.links.new(wet.outputs['Result'],mix.inputs[0]);nt.links.new(p.inputs['Base Color'].links[0].from_socket,mix.inputs[1]);nt.links.new(mix.outputs[0],p.inputs['Base Color']);rough=nt.nodes.new('ShaderNodeMixRGB');rough.inputs[2].default_value=(.23,.23,.23,1);nt.links.new(wet.outputs['Result'],rough.inputs[0]);nt.links.new(p.inputs['Roughness'].links[0].from_socket,rough.inputs[1]);nt.links.new(rough.outputs[0],p.inputs['Roughness']);coat=nt.nodes.new('ShaderNodeMath');coat.operation='MULTIPLY';coat.inputs[1].default_value=.16;nt.links.new(wet.outputs['Result'],coat.inputs[0]);nt.links.new(coat.outputs[0],p.inputs['Coat Weight']);p.inputs['Coat Roughness'].default_value=.24
s['version']='p3-v046';s['phase_status']='P9_BLACK_MYTH_QUALITY_REFERENCE_REVIEW';mfile=R/'manifest/scene-manifest.json';d=json.loads(mfile.read_text());d.update(version='p3-v046',source_version='p3-v046',source_snapshot=out.name);d['surface_finish_v046']={'path_collapsed_faces_removed':count,'rafter_uv_meshes':len(uvs),'bank_wet_transition_z_m':[-1.45,-.68]};mfile.write_text(json.dumps(d,ensure_ascii=False,indent=2));s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True);(R/'evidence/surface-finish-v046.json').write_text(json.dumps({'version':'p3-v046','removed_zero_area_faces':count,'rafter_uv_meshes':uvs,'wetness_height_range_m':[-1.45,-.68],'unchanged':'bridge/route geometry, living animation, source object transforms'},indent=2));print('SURFACE_FINISH',count,len(uvs),flush=True)
