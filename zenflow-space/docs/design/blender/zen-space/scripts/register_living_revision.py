import bpy,json
from pathlib import Path
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;p=R/'manifest/scene-manifest.json';m=json.loads(p.read_text());m['version']=s.get('version');m['color']['exposure']=s.get('day_exposure');known={r['name'] for r in m['cameras']}
for o in s.objects:
 if o.type=='CAMERA' and o.name not in known:m['cameras'].append({'name':o.name,'position':list(o.location),'rotation_euler':list(o.rotation_euler),'projection':o.data.type,'lens':o.data.lens,'purpose':'living-garden actual source inspection'})
m['living_garden']={'fish':5,'red_pattern_fish':3,'cats':2,'clip_registry':'living-garden-clips.json','active_clips':17,'fps':24};p.write_text(json.dumps(m,ensure_ascii=False,indent=2))

import csv
from mathutils import Vector
with (R/'manifest/camera-register.csv').open('w',newline='') as f:
 w=csv.DictWriter(f,fieldnames=['name','region','position','target','lens_mm','ortho_m','display_state','rotation_euler']);w.writeheader()
 for o in sorted((o for o in s.objects if o.type=='CAMERA'),key=lambda o:o.name):
  target=o.location+o.rotation_euler.to_matrix()@Vector((0,0,-10));w.writerow({'name':o.name,'region':o.get('region','WORLD' if o.name=='CAM_WORLD_FULL' else 'SOURCE'),'position':list(o.location),'target':list(target),'lens_mm':o.data.lens,'ortho_m':o.data.ortho_scale,'display_state':'LAYOUT_CUTAWAY' if o.name.startswith('CAM_LAYOUT') else 'WORLD_FULL' if o.name=='CAM_WORLD_FULL' else 'EYE_LEVEL','rotation_euler':list(o.rotation_euler)})
