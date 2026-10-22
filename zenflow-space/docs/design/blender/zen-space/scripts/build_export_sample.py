"""Isolated minimum kit before core-area refinement."""
import bpy,sys,math,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
from phase2_materials import *
if bpy.data.filepath:raise RuntimeError('Use factory startup only')
OUT=ROOT/'exports/sample/material-kit-v001.blend'
if OUT.exists():raise RuntimeError('Refuse overwrite')
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
s=bpy.context.scene;s.unit_settings.system='METRIC';c=s.collection;M=palette();setup(s)
box('KIT_GROUND',(0,0,-.055),(6,4,.1),c,M['Plaster'])
beam('KIT_WOOD_COLUMN',(-1.9,.4,0),(-1.9,.4,2.1),.19,c,M['Cedar'],32)
box('KIT_GREY_STONE',(-.9,.2,.25),(.8,.7,.5),c,M['Stone'],.05)
book=box('BOOK_heart-sutra',(.25,.2,.31),(.46,.34,.1),c,M['Darkwood'],.015);book['book_id']='heart-sutra'
box('BOOK_CONTENT_FACE',(.25,.2,.365),(.42,.30,.008),c,M['Paper'])
a=bpy.data.objects.new('SELECT_BOOK_heart-sutra',None);c.objects.link(a);a.location=book.location;a['book_id']='heart-sutra'
box('KIT_LAMP_SHADE',(1.2,.3,.62),(.32,.32,.5),c,M['Paper'],.01)
box('KIT_LAMP_LIGHT',(1.2,.3,.65),(.24,.24,.36),c,M['Glow'])
light('KIT_LAMP_POINT',(1.2,.0,.8),30,(1,.55,.22),c,.1,'POINT')
leaf=box('KIT_ALPHA_LEAF',(2,.2,1.05),(.65,.01,.8),c,M['Leaf']);leaf.rotation_euler.z=.2
# UV on front leaf face spans [0,1] to exercise alpha edges.
for f in leaf.data.polygons:
 if abs(f.normal.y)>.9:
  for li in f.loop_indices:
   v=leaf.data.vertices[leaf.data.loops[li].vertex_index].co;leaf.data.uv_layers.active.data[li].uv=(v.x/.65+.5,v.z/.8+.5)
box('SCALE_1M',(0,-.8,.025),(1,.05,.05),c,M['Ink'])
for x in [-.5,.5]:box('SCALE_END',(x,-.8,.06),(.025,.15,.12),c,M['Ink'])
rot=box('KIT_ROTATION',(-.8,.2,.58),(.25,.25,.14),c,M['Cedar'],.01)
rot.rotation_euler.z=0;rot.keyframe_insert(data_path='rotation_euler',frame=1);rot.rotation_euler.z=math.tau;rot.keyframe_insert(data_path='rotation_euler',frame=121);rot.animation_data.action.name='KIT_TURN';s.frame_end=121;s.render.fps=30;s.frame_set(1)
s.camera=camera('CAM_KIT',(4,-7,3.6),(0,.2,.7),c,43);light('KIT_SOFTBOX',(0,-2,6),800,(1,.88,.72),c,5,target=(0,0,0));s['version']='sample-v001'
bpy.ops.wm.save_as_mainfile(filepath=str(OUT));s.render.filepath=str(ROOT/'previews/phase2/material-kit-source.png');bpy.ops.render.render(write_still=True)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports/sample/material-kit-v001.glb'),export_format='GLB',use_selection=True,export_animations=True,export_extras=True,export_cameras=True,export_lights=True,export_yup=True,export_apply=True)
print('SAMPLE_SAVED_EXPORTED_RENDERED',flush=True)
