from pathlib import Path
import json,hashlib,shutil
R=Path(__file__).resolve().parents[1];E=R/'evidence';h=lambda p:hashlib.sha256(p.read_bytes()).hexdigest();oldsha='8454059971e5d5e023d4744a5908b32d56c323ed5d6350fecf45a739c6415c3f';assert h(R/'zen-space.blend')==oldsha,'Master changed outside this work; stop'
check=json.loads((E/'structure-check-p3-v050.json').read_text());audit=json.loads((E/'source-audit-v050.json').read_text());assert check['technical_status']=='PASS' and len(check['checks'])==24 and audit['degenerate_unique_source_polygons']==0 and not audit['negative_or_zero_scale'] and not audit['missing_images']
export=json.loads((E/'full-glb-export-check.json').read_text());assert export['source_version']=='p3-v050' and len(export['animations'])==17
views=json.loads((E/'repair-view-inventory-v050.json').read_text());assert len(views)==7
for r in views:
 assert (R/'previews'/r['image']).is_file()
 assert (R/'previews'/r['before']).is_file()
port=json.loads((E/'portability-check-p3-v050.json').read_text());assert port['render_exists'] and port['all_used_images_packed'] and port['all_used_fonts_packed_or_builtin']
(E/'full-glb-export-check-v050.json').write_bytes((E/'full-glb-export-check.json').read_bytes());p=E/'current-work-manifest.json';backup=E/'current-work-manifest-p3-v048.json'
if not backup.exists():shutil.copy2(p,backup)
d=json.loads(p.read_text());source=R/'zen-space-p3-v050.blend';snapshot=R/'snapshots/20261022/zen-space-p3-v050.blend';snapshot.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,snapshot);shutil.copy2(source,R/'zen-space.blend');sha=h(source)
d.update(version='p3-v050',source_snapshot=source.name,dated_snapshot=str(snapshot.relative_to(R)),sha256=sha,previous_master_sha256=oldsha,report='overhead-refinement-review-v050.md',rendered_views=7,cameras=35,route_segments=131,exchange_bytes=export['bytes'],scope='Shared overhead refinement: courtyard cross-gallery and supports; coherent roof corner uplift; compact low island with submerged stone shoulder; lookout terrain and cottage setback; separate 90m composition and unchanged 108m overview',motion_preview_source_version='p3-v048',new_motion_preview_rendered=False)
d['rejected_experiments']+=['v049 upright island side revised to submerged shoulders','v049 lookout skirt deformation reverted to protect original stair opening','v049 flower displacement reverted to original authored positions'];p.write_text(json.dumps(d,ensure_ascii=False,indent=2));assert h(R/'zen-space.blend')==sha
asset=R/'manifest/asset-register.csv';b=R/'manifest/asset-register-before-v050.csv'
if not b.exists():shutil.copy2(asset,b)
shutil.copy2(R/'manifest/asset-register-current.csv',asset)
readme=R/'README.md';s=readme.read_text();s=s.replace('当前交付版本 **p3-v048**','当前交付版本 **p3-v050**').replace('12 张本轮实际渲染，34 个相机；五条锦鲤（三条带红色）、两只猫及游动短片。','7 张本轮实际渲染，35 个相机；两张俯视及五个近景。五条锦鲤（三条带红色）、两只猫与17段生灵动作保留；短片为明确标注的v048历史版本。').replace('snapshots/20261021/zen-space-p3-v048.blend','snapshots/20261022/zen-space-p3-v050.blend').replace('evidence/quality-retrospective-v048.md','evidence/overhead-refinement-review-v050.md').replace('exports/zen-space-p3-v048.glb','exports/zen-space-p3-v050.glb');start=s.index('## 修理进度');end=s.index('## 修理前还原度审查',start);s=s[:start]+'## 修理进度（2026-10-22）\n\n本轮补院心横廊、修屋顶檐角、低岛石岸及右侧台地坡肩，保留原机位总览，并新增90m俯视构图及相同机位的v048前图。详见[本轮修理记录](evidence/overhead-refinement-review-v050.md)。高台侧壁、长直岸、院心植物和远山仍有差距，视觉为PARTIAL。\n\n'+s[end:];readme.write_text(s)
print('PROMOTED_OVERHEAD_V050',sha,export['bytes'])
