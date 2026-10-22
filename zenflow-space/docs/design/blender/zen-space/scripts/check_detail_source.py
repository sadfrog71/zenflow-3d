import bpy,json,runpy,csv,shutil
from pathlib import Path
R=Path(__file__).resolve().parents[1];s=bpy.context.scene
assert s.get('version')=='p3-v031'
for name in ['scene-manifest.json','object-animation-clips.json','camera-register.csv','asset-register.csv']:
 p=R/'manifest'/name;h=p.with_name(p.stem+'-p3-v028'+p.suffix)
 if not h.exists():shutil.copy2(p,h)
m=json.loads((R/'manifest/scene-manifest.json').read_text());m.update(version='p3-v031',source_version='p3-v031',source_snapshot='zen-space-p3-v031.blend')
for c in m['cameras']:
 o=s.objects[c['name']];c['position']=list(o.matrix_world.translation);c['lens_mm']=o.data.lens;c['ortho_m']=o.data.ortho_scale if o.data.type=='ORTHO' else None
 if c['name']=='CAM_ISLAND_SECTION':c['target']=[-27,5,-.7]
(R/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2))
with (R/'manifest/camera-register.csv').open('w',newline='') as f:
 w=csv.DictWriter(f,fieldnames=list(m['cameras'][0]));w.writeheader();w.writerows(m['cameras'])
runpy.run_path(str(R/'scripts/validate_model_geometry.py'),run_name='__main__');runpy.run_path(str(R/'scripts/audit_current_source.py'),run_name='__main__')
# Check source action targets and required new attachments without playing on or saving the source.
clips=json.loads((R/'manifest/object-animation-clips.json').read_text());clips['verified_source_version']='p3-v031';(R/'manifest/object-animation-clips.json').write_text(json.dumps(clips,ensure_ascii=False,indent=2));rings=[o for o in s.objects if o.name.startswith('SM_P5_BELL_FINE_CASTING_RING')];result={'version':'p3-v031','actions':len(bpy.data.actions),'registered_clips':len(clips['clips']),'all_registered_actions_present':all(bpy.data.actions.get(x['name']) for x in clips['clips']),'new_bell_rings_follow_existing_pivot':all(o.parent and o.parent.name=='PIVOT_Z07_BELL' for o in rings),'all_new_images_packed':all(i.packed_file for i in bpy.data.images if 'detail-20261021' in i.filepath),'structural_report':'structure-check-p3.json'};(R/'evidence/detail-action-and-packing-v031.json').write_text(json.dumps(result,indent=2));print('DETAIL_SOURCE_ACTION_PACKING',result,flush=True)
