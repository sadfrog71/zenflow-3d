"""Export only assigned new living actions in an unsaved exchange copy."""
import bpy,runpy
from pathlib import Path
R=Path(__file__).resolve().parents[1];s=bpy.context.scene
for o in s.objects:
 if o.animation_data and (not o.animation_data.action or not o.animation_data.action.name.startswith('P6_')):o.animation_data_clear()
for datablocks in [bpy.data.materials,bpy.data.lights]:
 for d in datablocks:
  if d.animation_data:d.animation_data_clear()
  nt=getattr(d,'node_tree',None)
  if nt and nt.animation_data:nt.animation_data_clear()
# Preserve vertex-painted white bib rather than replacing it with a neutral fallback.
for m in bpy.data.materials:
 if m.use_nodes:
  p=next((n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED'),None)
  if p and p.inputs['Base Color'].links and p.inputs['Base Color'].links[0].from_node.type=='VERTEX_COLOR':
   src=p.inputs['Base Color'].links[0].from_socket;n=m.node_tree.nodes.new('ShaderNodeMixRGB');n.blend_type='MULTIPLY';n.inputs[0].default_value=1;n.inputs[2].default_value=(1,1,1,1);m.node_tree.links.new(src,n.inputs[1]);m.node_tree.links.new(n.outputs[0],p.inputs['Base Color'])
bronze=(R/'scripts/export_detail_candidate.py').read_text().split('runpy.run_path')[0].replace("['p3-v030','p3-v031']","['p3-v030','p3-v031','p3-v035','p3-v042']")
exec(compile(bronze,str(R/'scripts/export_detail_candidate.py'),'exec'))
code=(R/'scripts/export_full_candidate.py').read_text().replace('export_animations=sample','export_animations=True').replace('export_force_sampling=False','export_force_sampling=True').replace("export_image_format='AUTO'","export_image_format='AUTO',export_all_vertex_colors=True,export_active_vertex_color_when_no_material=True")
exec(compile(code,str(R/'scripts/export_full_candidate.py'),'exec'))

runpy.run_path(str(R/'scripts/fix_living_glb_colors_v042.py'),run_name='__main__')
