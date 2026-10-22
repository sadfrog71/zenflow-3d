import bpy,sys,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
from phase3_pbr import palette
s=bpy.context.scene;OUT=ROOT/'exports/sample/material-kit-v002.blend'
if not bpy.data.filepath.endswith('material-kit-v001.blend') or OUT.exists():raise RuntimeError('Use saved v001 and new v002')
M=palette()
for n,k in [('KIT_WOOD_COLUMN','Wood'),('KIT_GREY_STONE','Rock'),('KIT_ROTATION','Wood')]:
 o=bpy.data.objects[n];o.data=o.data.copy();o.data.materials.clear();o.data.materials.append(M[k])
 # Column photo UV only uses the interior of one source plank.
 if k=='Wood':
  uv=o.data.uv_layers.active
  for f in uv.data:f.uv=(f.uv.x*.4+.2,f.uv.y*.05+.115)
s['version']='sample-v002';bpy.ops.wm.save_as_mainfile(filepath=str(OUT));bpy.ops.export_scene.gltf(filepath=str(ROOT/'exports/sample/material-kit-v002.glb'),export_format='GLB',export_extras=True,export_cameras=True,export_lights=True,export_apply=True)
s.render.filepath=str(ROOT/'previews/phase2/material-kit-v002-source.png');bpy.ops.render.render(write_still=True)
print('PBR_KIT_V002_EXPORTED',flush=True)
