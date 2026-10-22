import bpy,json,csv,hashlib,math
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=Path(bpy.data.filepath)
rows=[];degenerate=0;tri=0;shared=set();negative=[];missing=[]
for o in s.objects:
 if o.type!='MESH' or o.hide_render or o.hide_viewport:continue
 me=o.data;me.calc_loop_triangles();nt=len(me.loop_triangles);tri+=nt
 if min(o.scale)<=0:negative.append(o.name)
 if me.name not in shared:degenerate+=sum(p.area<1e-10 for p in me.polygons);shared.add(me.name)
 rows.append({'object':o.name,'mesh':me.name,'vertices':len(me.vertices),'triangles':nt,'uv_layers':len(me.uv_layers),'materials':' | '.join(m.name for m in me.materials if m),'collection':' | '.join(c.name for c in o.users_collection),'interactive':o.name.startswith(('SELECT_','CONTENT_')) or bool(o.get('role'))})
with (R/'manifest/asset-register-current.csv').open('w',newline='') as f:
 w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
imgs=[]
for im in bpy.data.images:
 if im.type not in ['IMAGE','MULTILAYER'] or im.source in ['GENERATED','VIEWER']:continue
 packed=bool(im.packed_file) or bool(im.packed_files);exists=Path(bpy.path.abspath(im.filepath)).exists()
 if not packed and not exists:missing.append(im.name)
 imgs.append({'name':im.name,'size':list(im.size),'packed':packed,'external_exists':exists,'source':im.filepath})
clips=json.loads((R/'manifest/object-animation-clips.json').read_text())['clips'];cliprows=[]
for row in clips:
 a=bpy.data.actions.get(row['name']);o=s.objects.get(row['object']);cliprows.append({'name':row['name'],'action_exists':bool(a),'object_exists':bool(o),'range':list(a.frame_range) if a else None,'default_playing':bool(o and o.animation_data and o.animation_data.action==a)})
report={'version':s.get('version'),'source':out.name,'visible_mesh_objects':len(rows),'visible_base_triangles_instanced_total':tri,'unique_visible_meshes':len(shared),'negative_or_zero_scale':negative,'degenerate_unique_source_polygons':degenerate,'missing_images':missing,'images':imgs,'clips':cliprows,'notes':['Base triangles excludes evaluated modifiers; full export gets its own measured count','thin leaf/water/paper surfaces intentionally have open boundaries; no blanket watertightness claim','UV overlap is intentional for repeating timber, rock and leaf atlases','no private user text is included']}
(R/'evidence/final-candidate-source-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print('CANDIDATE_INVENTORY',len(rows),tri,len(clips),negative,missing,degenerate,flush=True)
