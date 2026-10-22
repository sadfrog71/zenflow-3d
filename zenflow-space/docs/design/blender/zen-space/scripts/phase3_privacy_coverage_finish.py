import bpy,json,shutil
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'))
from phase2_materials import box
s=bpy.context.scene;OUT=ROOT/'zen-space-p3-v008.blend'
if s.get('version')!='p3-v007' or OUT.exists():raise RuntimeError('Requires p3-v007/new output')
# A generic south-facing opening remained from the original reusable room block.
# This private room uses the real west side entrance and the east garden window instead.
for o in s.objects:
 if o.name.startswith('Z11_NS_WALL') and abs(o.location.y-21.75)<.1:o.hide_render=True;o.hide_viewport=True
box('SM_Z11_SOUTH_PRIVATE_ENCLOSURE',(29,21.75,1.925),(5.4,.18,3.25),bpy.data.collections['Z11_SHELL'],bpy.data.materials['P2_Plaster'])
box('SM_Z11_SOUTH_TIMBER_SKIRT',(29,21.64,.56),(5.4,.05,.5),bpy.data.collections['Z11_SHELL'],bpy.data.materials['P2_Cedar'])
s['version']='p3-v008';s['phase_status']='P3_EXPANDED_SELF_CHECK_PENDING';bpy.ops.wm.save_as_mainfile(filepath=str(OUT),compress=True)
p=ROOT/'manifest/scene-manifest.json';m=json.loads(p.read_text());m['version']='p3-v008';m['source_snapshot']=OUT.name;m.setdefault('review_corrections',[]).append('Close obsolete generic south opening of private cottage; west side-turn entrance and east garden window retained; expand privacy rays to all public route segments, not only hillside y>=20');p.write_text(json.dumps(m,ensure_ascii=False,indent=2));print('PRIVACY_COVERAGE_SOURCE',OUT,flush=True)
