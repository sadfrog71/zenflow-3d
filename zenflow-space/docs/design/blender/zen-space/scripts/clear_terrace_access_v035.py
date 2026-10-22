"""Carve actual access clearances into new outcrop skirts; keep measured routes unchanged."""
import bpy,json,math
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;routes=json.loads((R/'manifest/scene-manifest.json').read_text())['routes'];ob=s.objects['Z10_TERRACE'];cuts=[]
for row in routes:
 if row['route']!='PUBLIC_EAST_ROUTE':continue
 a=Vector(row['from']);b=Vector(row['to']);d=b-a
 if max(a.x,b.x)<24 or min(a.y,b.y)>34 or max(a.y,b.y)<26:continue
 bpy.ops.mesh.primitive_cube_add(size=1,location=((a.x+b.x)/2,(a.y+b.y)/2,min(a.z,b.z)+1.55));c=bpy.context.object;c.name='TEMP_TERRACE_ACCESS_CUT';c.dimensions=(math.hypot(d.x,d.y)+.8,row['width']+.7,3.0);c.rotation_euler.z=math.atan2(d.y,d.x);bpy.context.view_layer.objects.active=c;bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
 bpy.context.view_layer.objects.active=ob;mod=ob.modifiers.new('Route access through rock skirt','BOOLEAN');mod.operation='DIFFERENCE';mod.solver='EXACT';mod.object=c;bpy.ops.object.modifier_apply(modifier=mod.name);bpy.data.objects.remove(c,do_unlink=True);cuts.append({'from':list(a),'to':list(b)})
s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(R/'zen-space-p3-v035.blend'),compress=True);(R/'evidence/terrace-access-v035.json').write_text(json.dumps(cuts,indent=2));print('TERRACE_ACCESS_CUTS',len(cuts))
