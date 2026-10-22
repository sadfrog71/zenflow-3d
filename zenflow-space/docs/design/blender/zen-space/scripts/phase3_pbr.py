import bpy
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def image_node(nt,fn,noncolor=False):
 im=bpy.data.images.load(str(ROOT/'assets/polyhaven'/fn),check_existing=True);im.pack()
 if noncolor:im.colorspace_settings.name='Non-Color'
 n=nt.nodes.new('ShaderNodeTexImage');n.image=im;return n
def pbr(name,diff,normal,rough,strength=.4):
 m=bpy.data.materials.get(name) or bpy.data.materials.new(name);m.use_nodes=True;nt=m.node_tree;nt.nodes.clear();p=nt.nodes.new('ShaderNodeBsdfPrincipled');o=nt.nodes.new('ShaderNodeOutputMaterial');nt.links.new(p.outputs['BSDF'],o.inputs['Surface'])
 d=image_node(nt,diff);n=image_node(nt,normal,True);r=image_node(nt,rough,True);nm=nt.nodes.new('ShaderNodeNormalMap');nm.inputs['Strength'].default_value=strength
 nt.links.new(d.outputs['Color'],p.inputs['Base Color']);nt.links.new(n.outputs['Color'],nm.inputs['Color']);nt.links.new(nm.outputs['Normal'],p.inputs['Normal']);nt.links.new(r.outputs['Color'],p.inputs['Roughness']);return m

def palette():
 return {'Wood':pbr('P3_Honey_Wood','wood-honey-derived.png','wood_floor_deck_nor_gl_2k.jpg','wood_floor_deck_rough_2k.jpg',.28),'Rock':pbr('P3_Weathered_Gray_Rock','rock-gray-derived.png','rock_boulder_cracked_nor_gl_2k.jpg','rock_boulder_cracked_rough_2k.jpg',.55)}
