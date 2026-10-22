"""Restore source terrain and rooted distant forest after dawn view regressed; retain other repairs."""
import bpy,json
from pathlib import Path
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;names=['SM_CONTINUOUS_OUTER_MOUNTAIN_RANGE']+[f'SM_FAR_FOREST_IMPOSTORS_{i}' for i in range(3)];before={n:s.objects.get(n) for n in names}
with bpy.data.libraries.load(str(R/'zen-space-p3-v035.blend'),link=False) as (src,dst):dst.objects=[n for n in names if n in src.objects]
restored=[]
for old in dst.objects:
 if old is None:continue
 base=old.name.rsplit('.',1)[0] if old.name not in before else old.name
 current=before.get(base)
 if current is None:
  current=next((before[n] for n in names if old.name.startswith(n+'.')),None)
 if current is None:raise RuntimeError('Cannot identify restored mesh '+old.name)
 assert len(current.data.vertices)==len(old.data.vertices)
 for a,b in zip(current.data.vertices,old.data.vertices):a.co=b.co
 current.data.update();restored.append(current.name);bpy.data.objects.remove(old,do_unlink=True)
# Older replacement rocks with a few centimeters crossing the finish floor are also retired.
from mathutils import Vector
chips=[]
for o in s.objects:
 if o.type!='MESH' or o.hide_render or not o.name.startswith('SM_ROCK_REPLACEMENT'):continue
 ps=[o.matrix_world@Vector(v) for v in o.bound_box];lo=[min(p[i] for p in ps) for i in range(3)];hi=[max(p[i] for p in ps) for i in range(3)]
 if hi[0]>-20.2 and lo[0]<-1.8 and hi[1]>-12.1 and lo[1]<-.9 and hi[2]>.301 and lo[2]<1.5:o.hide_render=True;o.hide_viewport=True;chips.append(o.name)
s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(R/'zen-space-p3-v039.blend'),compress=True);(R/'evidence/rejected-mountain-experiment-v039.json').write_text(json.dumps({'reason':'new ridges blocked sunrise and became a near wall; source v035 positions restored','restored_vertex_positions':restored,'floor_chips_retired':chips,'remaining_audit_item':'F04 open'},indent=2));print('MOUNTAIN_EXPERIMENT_REJECTED',restored,chips)
