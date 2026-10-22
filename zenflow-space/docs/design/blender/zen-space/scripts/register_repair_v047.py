import bpy,json,csv
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;p=R/'manifest/scene-manifest.json';m=json.loads(p.read_text());m['version']=s.get('version');m['source_version']=s.get('version');m['source_snapshot']='zen-space-p3-v047.blend';m['color']['exposure']=s.get('day_exposure')
known={row['name'] for row in m['cameras']}
for o in s.objects:
 if o.type=='CAMERA' and o.name not in known:m['cameras'].append({'name':o.name,'region':'WORLD','display_state':'HISTORICAL' if 'ORIGINAL' in o.name else 'SOURCE'})
for row in m['cameras']:
 o=s.objects[row['name']];row['position']=list(o.location);row['rotation_euler']=list(o.rotation_euler);row['lens_mm']=o.data.lens;row['ortho_m']=o.data.ortho_scale;row['target']=list(o.location+o.rotation_euler.to_matrix()@Vector((0,0,-10)))
p.write_text(json.dumps(m,ensure_ascii=False,indent=2))
with (R/'manifest/camera-register.csv').open('w',newline='') as f:
 w=csv.DictWriter(f,fieldnames=['name','region','position','target','lens_mm','ortho_m','display_state','rotation_euler']);w.writeheader()
 for row in m['cameras']:w.writerow({k:row.get(k,'SOURCE') for k in w.fieldnames})
for fn in ['living-garden-clips.json','weather-and-layout-states.json','handedness-layout-states.json']:
 p=R/'manifest'/fn
 if p.exists():d=json.loads(p.read_text());d['source_version']=s.get('version');p.write_text(json.dumps(d,ensure_ascii=False,indent=2))
print('REGISTRY',s.get('version'),len(m['cameras']))

with (R/'manifest/path-register.csv').open('w',newline='') as f:
 rows=m['routes'];keys=list(dict.fromkeys(k for row in rows for k in row));w=csv.DictWriter(f,fieldnames=keys);w.writeheader();w.writerows(rows)
