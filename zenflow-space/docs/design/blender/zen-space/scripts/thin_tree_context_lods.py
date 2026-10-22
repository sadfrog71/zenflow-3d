import bpy,bmesh,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'assets/polyhaven/tree_small_02/tree-lods-v002.blend'
if OUT.exists():raise RuntimeError('Output exists')
rows=[]
for name,div in [('CC0_TREE_leaves_NEAR',1),('CC0_TREE_leaves_MID',2),('CC0_TREE_leaves_FAR',4)]:
 o=bpy.data.objects[name];bm=bmesh.new();bm.from_mesh(o.data);bm.faces.ensure_lookup_table();seen=set();remove=[];components=0
 for f in bm.faces:
  if f in seen:continue
  todo=[f];seen.add(f);group=[]
  while todo:
   q=todo.pop();group.append(q)
   for e in q.edges:
    for n in e.link_faces:
     if n not in seen:seen.add(n);todo.append(n)
  if components%div:remove.extend(group)
  components+=1
 if remove:bmesh.ops.delete(bm,geom=remove,context='FACES_ONLY')
 loose=[v for v in bm.verts if not v.link_faces]
 if loose:bmesh.ops.delete(bm,geom=loose,context='VERTS')
 bm.to_mesh(o.data);bm.free();rows.append({'object':name,'connected_leaf_components':components,'keep_every':div,'vertices':len(o.data.vertices),'faces':len(o.data.polygons)})
bpy.ops.wm.save_as_mainfile(filepath=str(OUT),compress=True);(ROOT/'evidence/tree-lod-v002.json').write_text(json.dumps({'method':'deterministic sampling of complete disconnected leaf components; UV/material unchanged; loose vertices removed','parts':rows},indent=2));print(rows,flush=True)
