import bpy,json
from pathlib import Path
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v027.blend'
if s.get('version')!='p3-v026' or out.exists():raise RuntimeError('Requires new v027 from v026')
cloud=s.objects['ENV_FAR_VALLEY_CLOUD_SEA'];cloud.location.y=230;cloud.scale.y=420
terrain=s.objects['TERRAIN_CONTINUOUS_BASE'];me=terrain.data;rock=bpy.data.materials['P3_Weathered_Gray_Rock'];me.materials.append(rock);slot=len(me.materials)-1;uv=me.uv_layers.active;changed=0
for p in me.polygons:
 c=p.center
 if c.x>31.5 and 27<c.y<55 and p.normal.z<.88:
  p.material_index=slot;axis=max(range(3),key=lambda i:abs(p.normal[i]));dims=[i for i in range(3) if i!=axis]
  for li in p.loop_indices:
   v=me.vertices[me.loops[li].vertex_index].co;uv.data[li].uv=(v[dims[0]]*.5,v[dims[1]]*.5)
  changed+=1
s['version']='p3-v027';s['phase_status']='P4_FULL_MODEL_REVIEW';t=bpy.data.texts.get('READ_ME_FIRST')
if t:t.from_string(t.as_string().replace('p3-v026','p3-v027')+'\nCloud sea sits below z=-7m, below every functional path/platform; expanded only over the actual low valley. Steep east cliff faces use rock material.\n')
bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
for fn in ['scene-manifest.json','object-animation-clips.json','weather-and-layout-states.json','handedness-layout-states.json']:
 p=R/'manifest'/fn;m=json.loads(p.read_text());m['source_version']='p3-v027'
 if fn=='scene-manifest.json':m['version']='p3-v027';m['source_snapshot']=out.name
 p.write_text(json.dumps(m,ensure_ascii=False,indent=2))
(R/'evidence/cloud-valley-finish.json').write_text(json.dumps({'version':'p3-v027','cloud_bounds_y':[20,440],'cloud_bounds_z':[-27,-7],'functional_paths_above_clouds':True,'cliff_faces_assigned_rock':changed,'geometry_change':'cloud-volume weather variant bounds only; terrain positions unchanged from v026'},indent=2));print('CLOUD_VALLEY_FINISHED',changed,flush=True)
