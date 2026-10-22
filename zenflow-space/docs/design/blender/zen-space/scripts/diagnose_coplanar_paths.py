import bpy,json
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1];s=bpy.context.scene;bpy.context.view_layer.update();out=[]
for row in json.loads((ROOT/'evidence/black-face-diagnosis.json').read_text()):
 if not row.get('point') or not any(t in row.get('object','') for t in ['STEP','LANDING']):continue
 p=Vector(row['point']);hits=[]
 for o in s.objects:
  if o.type!='MESH' or o.hide_viewport:continue
  bounds=[o.matrix_world@Vector(v) for v in o.bound_box]
  if not(min(v.x for v in bounds)-.01<=p.x<=max(v.x for v in bounds)+.01 and min(v.y for v in bounds)-.01<=p.y<=max(v.y for v in bounds)+.01):continue
  inv=o.matrix_world.inverted();hit,pos,n,idx=o.ray_cast(inv@Vector((p.x,p.y,p.z+.3)),inv.to_3x3()@Vector((0,0,-1)),distance=1)
  if hit and abs((o.matrix_world@pos).z-p.z)<.03:hits.append({'object':o.name,'z':(o.matrix_world@pos).z,'materials':[m.name for m in o.data.materials]})
 out.append({'pixel':row['pixel_xy'],'point':row['point'],'coincident_faces':hits})
(ROOT/'evidence/coplanar-path-diagnosis.json').write_text(json.dumps(out,indent=2));print(json.dumps(out,indent=2))
