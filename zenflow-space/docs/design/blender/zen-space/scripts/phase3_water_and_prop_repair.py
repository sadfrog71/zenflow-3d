import bpy,sys,math,json
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v011.blend'
if s.get('version')!='p3-v010' or out.exists():raise RuntimeError('Requires new v011 from v010')
# Measured support: stone top .42m, dish foot formerly .46m (40mm suspended).
for o in s.objects:
 if o.name.startswith(('SM_Z01_RICE_DISH','SM_Z01_RICE_GRAIN','ANCHOR_Z01_RICE')):o.location+=Vector((.13,-.12,-.04))
retired=[]
for o in s.objects:
 if o.name.startswith('SM_Z02_FOAM_TRAIL'):o.hide_render=True;o.hide_viewport=True;retired.append(o.name)
m=bpy.data.materials['P3_FALLING_WATER'];nt=m.node_tree;n=nt.nodes;l=nt.links;p=next(x for x in n if x.type=='BSDF_PRINCIPLED');p.inputs['Roughness'].default_value=.23;p.inputs['Metallic'].default_value=.0
tex=n.new('ShaderNodeTexCoord');mapping=n.new('ShaderNodeMapping');mapping.name='FLOW_MAPPING';mapping.inputs['Scale'].default_value=(25,2,.65);l.new(tex.outputs['Generated'],mapping.inputs['Vector']);no=n.new('ShaderNodeTexNoise');no.inputs['Scale'].default_value=3;no.inputs['Detail'].default_value=4;no.inputs['Roughness'].default_value=.7;l.new(mapping.outputs[0],no.inputs['Vector']);r=n.new('ShaderNodeValToRGB');r.color_ramp.elements[0].position=.32;r.color_ramp.elements[0].color=(.10,.20,.19,1);r.color_ramp.elements[1].position=.72;r.color_ramp.elements[1].color=(.68,.77,.72,1);mid=r.color_ramp.elements.new(.58);mid.color=(.29,.43,.40,1);l.new(no.outputs['Fac'],r.inputs[0]);l.new(r.outputs[0],p.inputs['Base Color']);b=n.new('ShaderNodeBump');b.inputs['Strength'].default_value=.24;b.inputs['Distance'].default_value=.08;l.new(no.outputs['Fac'],b.inputs['Height']);l.new(b.outputs[0],p.inputs['Normal'])
# Shader motion is clearly registered as runtime reconstruction, not claimed glTF animation.
for frame,z in [(1,0),(1441,-12)]:mapping.inputs['Location'].default_value.z=z;mapping.inputs['Location'].keyframe_insert('default_value',frame=frame)
if nt.animation_data and nt.animation_data.action:nt.animation_data.action.name='MAT_Z02_WATER_FLOW_60S_REBUILD_IN_RUNTIME'
# Terrain gets close-scale mineral/grass variation instead of one flat green swatch.
soil=bpy.data.materials['P2_Soil'];nt=soil.node_tree;n=nt.nodes;l=nt.links;p=next(x for x in n if x.type=='BSDF_PRINCIPLED');tc=n.new('ShaderNodeTexCoord');no=n.new('ShaderNodeTexNoise');no.inputs['Scale'].default_value=1.8;no.inputs['Detail'].default_value=5;l.new(tc.outputs['Object'],no.inputs['Vector']);r=n.new('ShaderNodeValToRGB');r.color_ramp.elements[0].position=.22;r.color_ramp.elements[0].color=(.065,.09,.033,1);r.color_ramp.elements[1].position=.8;r.color_ramp.elements[1].color=(.23,.25,.12,1);l.new(no.outputs[0],r.inputs[0]);l.new(r.outputs[0],p.inputs['Base Color']);b=n.new('ShaderNodeBump');b.inputs['Strength'].default_value=.23;b.inputs['Distance'].default_value=.035;l.new(no.outputs[0],b.inputs['Height']);l.new(b.outputs[0],p.inputs['Normal'])
# Fine shrub leaf cards keep their cluster footprint but correct visibly oversized leaf blades.
me=bpy.data.meshes.get('P2_FINE_LEAF_CLUSTER_SHARED')
if me:
 for face in me.polygons:
  center=sum((me.vertices[i].co for i in face.vertices),Vector())/len(face.vertices)
  for i in face.vertices:me.vertices[i].co=center+(me.vertices[i].co-center)*.60
s.frame_set(1);s['version']='p3-v011';s['phase_status']='P3_VISUAL_REVIEW_PENDING';bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
(R/'evidence/water-and-prop-repair.json').write_text(json.dumps({'version':'p3-v011','dish_support_before_m':.04,'dish_support_after_m':0,'dish_translation_m':[.13,-.12,-.04],'retired_foam_ribbons':len(retired),'water_shader_animation':'requires runtime reconstruction; explicitly not an exported mesh animation','shrub_leaf_linear_scale':.6},indent=2));print('VISUAL_REPAIR_SAVED',out,flush=True)
