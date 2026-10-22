import bpy,json
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1];s=bpy.context.scene;cam=s.objects['CAM_WORLD_FULL'];s.camera=cam
bpy.context.view_layer.update();deps=bpy.context.evaluated_depsgraph_get();proj=cam.calc_matrix_camera(deps,x=1600,y=900,scale_x=1,scale_y=1);ip=proj.inverted();out=[]
for row in json.loads((ROOT/'evidence/black-face-pixels.json').read_text()):
 x,y=row['pixel_xy'];u=x/1600;v=1-y/900;aa=ip@Vector((u*2-1,v*2-1,-1,1));bb=ip@Vector((u*2-1,v*2-1,1,1));aa=cam.matrix_world@(aa.xyz/aa.w);bb=cam.matrix_world@(bb.xyz/bb.w);d=(bb-aa).normalized();hit,p,n,idx,o,m=s.ray_cast(deps,aa,d,distance=2500)
 if hit:
  ob=o.original;mat=ob.data.materials[ob.data.polygons[idx].material_index] if idx<len(ob.data.polygons) and ob.data.materials else None
  row.update(object=ob.name,point=list(p),normal=list(n),material=mat.name if mat else None,location=list(ob.location),scale=list(ob.scale),matrix_determinant=ob.matrix_world.determinant(),face_vertices=list(ob.data.polygons[idx].vertices) if idx<len(ob.data.polygons) else None)
 out.append(row)
(ROOT/'evidence/black-face-diagnosis.json').write_text(json.dumps(out,ensure_ascii=False,indent=2));print(json.dumps(out,ensure_ascii=False,indent=2))
