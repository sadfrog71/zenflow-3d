import bpy,json
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;cam=s.objects['CAM_LOOKOUT_E'];bpy.context.view_layer.update();frame=cam.data.view_frame(scene=s);rows=[]
# Perspective frame corners: derive horizontal/vertical bounds in camera local view plane.
xx=[v.x for v in frame];yy=[v.y for v in frame];zz=frame[0].z
for u,v in [(.5,.4),(.6,.35),(.7,.3),(.58,.5),(.4,.4),(.9,.5),(.5,.05)]:
 local=Vector((min(xx)+(max(xx)-min(xx))*u,min(yy)+(max(yy)-min(yy))*v,zz));direction=cam.matrix_world.to_3x3()@local.normalized();hit,p,n,idx,obj,matrix=s.ray_cast(bpy.context.evaluated_depsgraph_get(),cam.location,direction,distance=2000);rows.append({'pixel_normalized':[u,v],'object':obj.name if hit else None,'location':list(p) if hit else None,'normal':list(n) if hit else None})
# Exact seam distances, independent of lighting.
a=s.objects['TERRAIN_CONTINUOUS_BASE'];b=s.objects['SM_CONTINUOUS_OUTER_MOUNTAIN_RANGE'];core=[a.matrix_world@v.co for v in a.data.vertices if abs(v.co.x-85)<.001];outer=[b.matrix_world@v.co for v in b.data.vertices[:800] if abs(v.co.x-85)<.001];seams=[]
for q in core:
 p=min(outer,key=lambda p:(p.xy-q.xy).length);d=(p-q).length
 if d>.01:seams.append({'xy':list(q.xy),'vertical_gap_m':p.z-q.z})
(R/'evidence/lookout-black-region-diagnosis.json').write_text(json.dumps({'rays':rows,'seam_mismatches':seams},indent=2));print('LOOKOUT_DIAGNOSIS',json.dumps(rows),len(seams),max(abs(x['vertical_gap_m']) for x in seams) if seams else 0,flush=True)
