"""Bake the new cast bronze into PBR maps in an unsaved exchange copy, then reuse the established exporter."""
import bpy,sys,json,runpy
from pathlib import Path
R=Path(__file__).resolve().parents[1];s=bpy.context.scene
if s.get('version') not in ['p3-v030','p3-v031']:raise RuntimeError('Requires supported detail candidate')
for vl in s.view_layers:vl.use=vl.name=='WORLD_FULL'
bpy.context.window.view_layer=s.view_layers['WORLD_FULL'];s.render.engine='CYCLES';s.cycles.device='CPU';s.cycles.samples=1
O=R/'assets/textures/detail-20261021/bronze-bake';O.mkdir(exist_ok=True);baked=[]
objects=[o for o in s.objects if o.type=='MESH' and not o.hide_render and o.visible_get() and any(m and m.name=='P3_BRONZE' for m in o.data.materials)]
for i,o in enumerate(objects):
 o.data=o.data.copy();material=bpy.data.materials['P3_BRONZE'].copy();material.name='P5_BAKED_BRONZE_'+str(i);o.data.materials.clear();o.data.materials.append(material);nt=material.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');output=next(n for n in nt.nodes if n.type=='OUTPUT_MATERIAL');bpy.ops.object.select_all(action='DESELECT');o.select_set(True);bpy.context.view_layer.objects.active=o;bpy.ops.object.mode_set(mode='EDIT');bpy.ops.mesh.select_all(action='SELECT');bpy.ops.uv.smart_project(angle_limit=1.15,island_margin=.025);bpy.ops.object.mode_set(mode='OBJECT')
 base=bpy.data.images.new('bronze-'+str(i)+'-base',width=512,height=512,alpha=False);bn=nt.nodes.new('ShaderNodeTexImage');bn.image=base;nt.nodes.active=bn;em=nt.nodes.new('ShaderNodeEmission');nt.links.new(p.inputs['Base Color'].links[0].from_socket,em.inputs['Color']);nt.links.new(em.outputs[0],output.inputs['Surface']);s.render.bake.use_clear=True;s.render.bake.margin=8;bpy.ops.object.bake(type='EMIT');base.filepath_raw=str(O/(str(i)+'-base.png'));base.file_format='PNG';base.save();base.pack()
 nt.links.new(p.outputs['BSDF'],output.inputs['Surface']);normal=bpy.data.images.new('bronze-'+str(i)+'-normal',width=512,height=512,alpha=False);normal.colorspace_settings.name='Non-Color';nn=nt.nodes.new('ShaderNodeTexImage');nn.image=normal;nt.nodes.active=nn;bpy.ops.object.bake(type='NORMAL');normal.filepath_raw=str(O/(str(i)+'-normal.png'));normal.file_format='PNG';normal.save();normal.pack()
 for link in list(p.inputs['Base Color'].links):nt.links.remove(link)
 for link in list(p.inputs['Normal'].links):nt.links.remove(link)
 nt.links.new(bn.outputs['Color'],p.inputs['Base Color']);nm=nt.nodes.new('ShaderNodeNormalMap');nm.inputs['Strength'].default_value=1;nt.links.new(nn.outputs['Color'],nm.inputs['Color']);nt.links.new(nm.outputs['Normal'],p.inputs['Normal']);baked.append({'object':o.name,'material':material.name,'maps':[base.filepath_raw,normal.filepath_raw],'resolution':512})
(R/('evidence/detail-bronze-bake-'+s.get('version')+'.json')).write_text(json.dumps({'source_version':s.get('version'),'baked':baked,'source_saved':False},indent=2));print('DETAIL_BRONZE_BAKED',len(baked),flush=True)
runpy.run_path(str(R/'scripts/export_full_candidate.py'),run_name='__main__')
