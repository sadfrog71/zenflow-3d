import bpy,json
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;retired=[]
for o in s.objects:
 if o.type!='MESH' or o.hide_render or not o.name.startswith('SM_ROCK_REPLACEMENT'):continue
 ps=[o.matrix_world@Vector(v) for v in o.bound_box];lo=[min(p[i] for p in ps) for i in range(3)];hi=[max(p[i] for p in ps) for i in range(3)]
 if hi[0]>-20.2 and lo[0]<-1.8 and hi[1]>-12.1 and lo[1]<-.9 and hi[2]>.33 and lo[2]<1.5:o.hide_render=True;o.hide_viewport=True;retired.append({'name':o.name,'bounds':[lo,hi]})
s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(R/'zen-space-p3-v039.blend'),compress=True);(R/'evidence/existing-floor-contact-v039.json').write_text(json.dumps({'diagnosis':'camera rays identify pre-existing replacement rocks through library floor, revealed by new seat direction','retired':retired},indent=2));print('EXISTING_FLOOR_CONTACT',len(retired))
