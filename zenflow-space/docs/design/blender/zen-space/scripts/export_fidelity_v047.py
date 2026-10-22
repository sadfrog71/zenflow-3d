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
# Explicit portable material approximation; procedural world-space weathering remains in Blender.
portable=[]
for name,tint,rough in [('P4_PHOTOGRAPHED_LEAFY_GROUND',(.60,.68,.49,1),.9),('P4_MOSSY_BANK_ROCK',(.70,.74,.65,1),.65),('P5_FINE_DRESSED_LIMESTONE',(.68,.68,.61,1),.76),('P3_Weathered_Gray_Rock',(.29,.31,.28,1),.78),('P2_Cedar',(.88,.77,.60,1),.47)]:
 ma=bpy.data.materials[name];nt=ma.node_tree;pp=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');inp=pp.inputs['Base Color'];images=[n for n in nt.nodes if n.type=='TEX_IMAGE' and n.image and n.image.colorspace_settings.name=='sRGB'];preferred=[n for n in images if any(k in n.image.name.lower() for k in ['diff','base','cedar'])];im=(preferred or images or [None])[0]
 for link in list(inp.links):nt.links.remove(link)
 if im:
  mix=nt.nodes.new('ShaderNodeMixRGB');mix.blend_type='MULTIPLY';mix.inputs[0].default_value=1;mix.inputs[2].default_value=tint;nt.links.new(im.outputs['Color'],mix.inputs[1]);nt.links.new(mix.outputs[0],inp)
 else:inp.default_value=tint
 for link in list(pp.inputs['Roughness'].links):nt.links.remove(link)
 pp.inputs['Roughness'].default_value=rough
 for link in list(pp.inputs['Coat Weight'].links):nt.links.remove(link)
 pp.inputs['Coat Weight'].default_value=0
 # Existing exporter must not bypass this explicit simple image + constant factor adaptation.
 if 'exchange_base_image' in ma:del ma['exchange_base_image']
 portable.append({'material':name,'base_image':im.image.name if im else None,'tint':tint,'roughness':rough,'source_saved':False})
for ma in bpy.data.materials:
 if ma.name.startswith('P6_FIRED_CLAY_'):
  pp=next(n for n in ma.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
  for key,val in [('Base Color',(.064,.077,.081,1)),('Roughness',.54)]:
   inp=pp.inputs[key]
   for link in list(inp.links):ma.node_tree.links.remove(link)
   inp.default_value=val
(R/'evidence/portable-materials-v047.json').write_text(__import__('json').dumps(portable,indent=2))
bronze=(R/'scripts/export_detail_candidate.py').read_text().split('runpy.run_path')[0].replace("['p3-v030','p3-v031']","['p3-v030','p3-v031','p3-v035','p3-v047']")
exec(compile(bronze,str(R/'scripts/export_detail_candidate.py'),'exec'))
code=(R/'scripts/export_full_candidate.py').read_text().replace('export_animations=sample','export_animations=True').replace('export_force_sampling=False','export_force_sampling=True').replace("export_image_format='AUTO'","export_image_format='AUTO',export_all_vertex_colors=True,export_active_vertex_color_when_no_material=True")
exec(compile(code,str(R/'scripts/export_full_candidate.py'),'exec'))

runpy.run_path(str(R/'scripts/fix_living_glb_colors_v047.py'),run_name='__main__')
