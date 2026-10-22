import bpy,json
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;retired=[]
for o in s.objects:
 if o.type!='MESH' or o.hide_render or not o.name.startswith('SM_P6_Z02_INNER_BANK_REVETMENT'):continue
 ps=[o.matrix_world@Vector(v) for v in o.bound_box];lo=[min(p[i] for p in ps) for i in range(3)];hi=[max(p[i] for p in ps) for i in range(3)]
 if hi[0]>-20.2 and lo[0]<-1.8 and hi[1]>-12.1 and lo[1]<-.9 and hi[2]>.314 and lo[2]<1.5:o.hide_render=True;o.hide_viewport=True;retired.append(o.name)
s['phase_status']='P7_REFERENCE_REPAIR_TECHNICAL_PASS_VISUAL_PARTIAL';t=bpy.data.texts.get('READ_ME_FIRST')
if t:t.from_string(t.as_string()+'\nCurrent saved revision: p3-v039. Reference landscape/roof repair; 5 koi (3 red), 2 cats, 17 assigned living clips. Visual PARTIAL. Rejected ridge experiment restored v035 outer terrain and forest. See fidelity-repair-review-v039.md.\n')
s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(R/'zen-space-p3-v039.blend'),compress=True);(R/'evidence/old-revetment-contact-v039.json').write_text(json.dumps({'ray_identified_old_shore_rocks_crossing_finish_floor':retired},indent=2));print('FINAL_SOURCE_CONTACTS',len(retired))
