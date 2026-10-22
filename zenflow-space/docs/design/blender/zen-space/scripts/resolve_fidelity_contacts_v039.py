"""Retire added landscape rocks intersecting actual building floor volumes; preserve pre-existing scene."""
import bpy,json
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v039.blend'
if s.get('version')!='p3-v038' or out.exists():raise RuntimeError('Requires new v039')
boxes=[(-20.5,-1.5,-12.4,-.6,.3),(1.6,22.4,-13.3,.3,.3),(15.9,24.1,12.8,19.2,.3),(25.8,32.2,21.25,26.75,.3),(-15.65,-12.35,-1.25,19.25,0)]
retired=[]
for o in s.objects:
 if o.type!='MESH' or o.hide_render or not o.name.startswith('SM_P7_') or 'ROOF' in o.name:continue
 ps=[o.matrix_world@Vector(v) for v in o.bound_box];lo=[min(p[i] for p in ps) for i in range(3)];hi=[max(p[i] for p in ps) for i in range(3)]
 if any(hi[0]>a and lo[0]<b and hi[1]>c and lo[1]<d and hi[2]>z+.01 and lo[2]<z+2 for a,b,c,d,z in boxes):o.hide_render=True;o.hide_viewport=True;retired.append(o.name)
# Reject the island camera experiment: it brought the bridge nearer, so keep the saved v035 orientation.
c=s.objects['CAM_ISLAND_SEATED'];c.rotation_euler=(1.4455187320709229,2.7141297209709592e-08,-2.402672529220581);c.data.lens=20
s['version']='p3-v039';s['phase_status']='P7_REFERENCE_REPAIR_DELIVERY_CANDIDATE';s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True);(R/'evidence/fidelity-contact-v039.json').write_text(json.dumps({'retired_building_contact_landscape':retired,'building_contact_count':len(retired),'island_camera':'restored v035 orientation; framing finding remains open'},indent=2));print('CONTACT_RESOLVED',len(retired))
