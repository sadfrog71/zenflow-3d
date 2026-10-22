import bpy,json,csv,hashlib,math
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v016.blend'
if s.get('version')!='p3-v015' or out.exists():raise RuntimeError('Requires new v016 from v015')
def find(l,n):
 if l.name==n:return l
 for c in l.children:
  r=find(c,n)
  if r:return r
cut=s.view_layers['LAYOUT_CUTAWAY'];lc=find(cut.layer_collection,'P3_CONTEXT_GROVES_AND_BANK_PLANTS')
if lc:lc.exclude=True
s['version']='p3-v016';s['phase_status']='P4_FINAL_REVIEW_CANDIDATE';s.camera=s.objects['CAM_ENTRY_SEATED'];s.frame_set(1)
for vl in s.view_layers:vl.use=vl.name=='EYE_LEVEL'
readme=bpy.data.texts.get('READ_ME_FIRST')
if readme:readme.from_string(readme.as_string().replace('p3-v015','p3-v016'))
bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
m=json.loads((R/'manifest/scene-manifest.json').read_text());m['version']='p3-v016';m['source_snapshot']=out.name;m['color']={'view_transform':s.view_settings.view_transform,'look':s.view_settings.look,'exposure':s.view_settings.exposure,'gamma':s.view_settings.gamma};m['animation_clips']='object-animation-clips.json';m['weather_and_layout_states']='weather-and-layout-states.json';m['limitations']=['reference fidelity requires rendered comparison; no invented percentage','runtime water/wetness/lighting reconstruction differs from Cycles source','iOS/device/application behavior not verified']
for c in m['cameras']:
 o=s.objects[c['name']];c['position']=list(o.location);c['rotation_euler']=list(o.rotation_euler);c['lens_mm']=o.data.lens;c['target']=list(o.location+o.rotation_euler.to_matrix()@Vector((0,0,-10)))
(R/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2))
for fn in ['object-animation-clips.json','weather-and-layout-states.json']:
 p=R/'manifest'/fn;x=json.loads(p.read_text());x['source_version']='p3-v016';p.write_text(json.dumps(x,ensure_ascii=False,indent=2))
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
report={'version':'p3-v016','source':out.name,'visible_mesh_objects':len(rows),'visible_base_triangles_instanced_total':tri,'unique_visible_meshes':len(shared),'negative_or_zero_scale':negative,'degenerate_unique_source_polygons':degenerate,'missing_images':missing,'images':imgs,'clips':cliprows,'notes':['Base triangles excludes evaluated modifiers; full export gets its own measured count','thin leaf/water/paper surfaces intentionally have open boundaries; no blanket watertightness claim','UV overlap is intentional for repeating timber, rock and leaf atlases','no private user text is included']}
(R/'evidence/final-candidate-source-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print('CANDIDATE_INVENTORY',len(rows),tri,len(clips),negative,missing,degenerate,flush=True)
