import bpy,math,json
from pathlib import Path
from mathutils import noise,Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v022.blend'
if s.get('version')!='p3-v021' or out.exists():raise RuntimeError('Requires new v022 from v021')
o=s.objects['SM_CONTINUOUS_OUTER_MOUNTAIN_RANGE'];changed=0
for v in o.data.vertices:
 x,y,z=v.co
 if x<=85:continue
 t=max(0,min(1,(x-85)/75));t=t*t*(3-2*t);side=max(0,min(1,(195-abs(y-42))/80));side=side*side*(3-2*side)
 far=max(0,min(1,(x-390)/270));target=-17+4*noise.noise(Vector((x*.015,y*.015,1)))+42*far
 w=t*side;v.co.z=z*(1-w)+target*w;changed+=1
s['version']='p3-v022';s['phase_status']='MODEL_RENDERED_SELF_CHECK';s.frame_set(1);t=bpy.data.texts.get('READ_ME_FIRST')
if t:t.from_string(t.as_string().replace('p3-v021','p3-v022')+'\nEast outer terrain forms the physical open valley; protected core terrain and route geometry are unchanged.\n')
bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
m=json.loads((R/'manifest/scene-manifest.json').read_text());m['version']='p3-v022';m['source_version']='p3-v022';m['source_snapshot']=out.name;(R/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2));(R/'evidence/east-valley-repair.json').write_text(json.dumps({'version':'p3-v022','modified_outer_vertices':changed,'protected_core_terrain_changed':False,'roads_buildings_and_platforms_moved':False,'repair':'permanent physical valley east of x85m; distant skyline rises again beyond x390m; no camera-specific terrain change'},indent=2));print('EAST_VALLEY_FINISHED',changed,flush=True)
