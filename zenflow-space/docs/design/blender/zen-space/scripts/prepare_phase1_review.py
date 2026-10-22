"""Prepare the P1 review file: bridge contact, useful opening view, packed references."""
import bpy,json,csv,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];V='p1-v009';OUT=ROOT/f'zen-space-{V}.blend'
if OUT.exists():raise RuntimeError('Target exists')
if not bpy.data.filepath.endswith('zen-space-p1-v008.blend'):raise RuntimeError('Expected v008')
s=bpy.context.scene;m=json.loads((ROOT/'manifest/scene-manifest.json').read_text())
# The same deck and rails move together; the island top and the full bridge width overlap.
for o in s.objects:
 if o.name=='Z02_LOWER_BRIDGE_DECK':
  for v in o.data.vertices:
   t=(v.co.x+25.8)/10;v.co.x-=.5*(1-t)
  o.data.update()
 if o.name.startswith('Z02_LOWER_BRIDGE_LOW_EDGE'):
  for spline in o.data.splines:
   for p in spline.points:
    t=(p.co.x+25.8)/10;p.co.x-=.5*(1-t)
for r in m['routes']:
 if r['route']=='Z02_LOWER_BRIDGE':r['from'][0]=-26.3;r['tread_m']=10.5
# Open the saved file directly in the complete-roof camera, retaining editable objects.
s.camera=bpy.data.objects['CAM_WORLD_FULL']
for screen in bpy.data.screens:
 for area in screen.areas:
  if area.type=='VIEW_3D':
   for space in area.spaces:
    if space.type=='VIEW_3D':
     space.region_3d.view_perspective='CAMERA';space.clip_start=.05;space.clip_end=500;space.shading.color_type='MATERIAL';space.overlay.show_extras=False
s['phase_status']='P1_VISUAL_REVIEW_PENDING';s['version']=V
for o in s.objects:o['version']=V
m['version']=V;m['source_snapshot']=OUT.name;m['review_corrections']+=['Lower bridge extends 0.50m onto the flat island coast, same deck and rails; no meditation pedestal added']
m['packed_reference_images']=len([i for i in bpy.data.images if i.packed_file]);m['editing_font_dependency']='Chinese text uses system font currently available on this Mac; cross-machine font packaging remains a phase4 item'
(ROOT/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2))
for fn,data,keys in [('path-register.csv',m['routes'],['route','from','to','width','rise','steps','max_riser','tread_m','landing_pad_m']),('layout-register.csv',m['zones'],['code','name','center','dimensions','neighbors'])]:
 with (ROOT/'manifest'/fn).open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=keys,extrasaction='ignore');w.writeheader();w.writerows(data)
report=json.loads((ROOT/'evidence/instancing-report.json').read_text());report['source_version']=V;(ROOT/'evidence/instancing-report.json').write_text(json.dumps(report,indent=2))
bpy.ops.wm.save_as_mainfile(filepath=str(OUT));print('P1_REVIEW_READY',OUT,'packed_images',m['packed_reference_images'])
