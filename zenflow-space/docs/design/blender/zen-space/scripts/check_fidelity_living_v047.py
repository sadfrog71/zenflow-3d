"""Inspect active living source, sampled movement, packed dependencies."""
import bpy,json,math
from pathlib import Path
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;rows=json.loads((R/'manifest/living-garden-clips.json').read_text())['clips'];checks=[]
for row in rows:
 o=s.objects[row['object']];target=o.data.shape_keys if row.get('target_kind')=='SHAPE_KEYS' else o
 checks.append({'action':row['name'],'assigned':bool(target.animation_data and target.animation_data.action and target.animation_data.action.name==row['name'])})
roots=[o for o in s.objects if o.name.startswith('P6_KOI_') and o.name.endswith('_ROOT')];samples=[];min_pair=999
for frame in range(1,1442,24):
 s.frame_set(frame);positions=[o.matrix_world.translation.copy() for o in roots]
 min_pair=min(min_pair,*( (a-b).length for i,a in enumerate(positions) for b in positions[i+1:] ))
 samples.append({'frame':frame,'positions':[list(p) for p in positions]})
s.frame_set(1)
report={'source':bpy.data.filepath,'fish_count':len(roots),'red_fish_count':sum('RED' in o.name for o in roots),'cats':len([o for o in s.objects if o.name in ['P6_CAT_WATCH_ROOT','P6_CAT_SLEEP_ROOT']]),'assigned_clips':checks,'all_assigned':all(r['assigned'] for r in checks),'minimum_root_separation_m':min_pair,'sampled_motion':samples,'packed_images':all(bool(i.packed_file) or bool(i.packed_files) for i in bpy.data.images if i.type=='IMAGE' and i.source=='FILE'),'linked_libraries':len(bpy.data.libraries),'cameras':len([o for o in s.objects if o.type=='CAMERA'])}
(R/'evidence/living-source-v047.json').write_text(json.dumps(report,indent=2));print('LIVING_SOURCE',report['fish_count'],report['red_fish_count'],report['cats'],report['all_assigned'],min_pair,report['linked_libraries'])
