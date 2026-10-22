from pathlib import Path
import hashlib,json,shutil
R=Path(__file__).resolve().parents[1];E=R/'evidence';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest();previous='a9147efce6465d2bb50a48b7d36b84f78080add823b6d00dd5c4c772e0a44f61';assert sha(R/'zen-space.blend')==previous,'Master changed outside this work; stop'
check=json.loads((E/'structure-check-p3-v048.json').read_text());audit=json.loads((E/'source-audit-v048.json').read_text());assert check['technical_status']=='PASS' and len(check['checks'])==24 and audit['degenerate_unique_source_polygons']==0 and not audit['missing_images']
export=json.loads((E/'full-glb-export-check.json').read_text());assert export['source_version']=='p3-v048' and len(export['animations'])==17
(E/'full-glb-export-check-v048.json').write_bytes((E/'full-glb-export-check.json').read_bytes());views=json.loads((E/'repair-view-inventory-v048.json').read_text());assert len(views)==12
for r in views:assert (R/'previews'/r['image']).is_file()
assert (R/'previews/p3-v048/motion/living-garden.mp4').stat().st_size>10000
port=json.loads((E/'portability-check-p3-v048.json').read_text());assert port['render_exists'] and port['all_used_images_packed'] and port['all_used_fonts_packed_or_builtin']
m=E/'current-work-manifest.json';d=json.loads(m.read_text());backup=E/'current-work-manifest-p3-v042.json'
if not backup.exists():shutil.copy2(m,backup)
source=R/'zen-space-p3-v048.blend';snapshot=R/'snapshots/20261021/zen-space-p3-v048.blend';shutil.copy2(source,snapshot);shutil.copy2(source,R/'zen-space.blend');h=sha(source)
d.update(version='p3-v048',source_snapshot=source.name,dated_snapshot=str(snapshot.relative_to(R)),sha256=h,previous_master_sha256=previous,report='quality-retrospective-v048.md',route_segments=131,exchange_bytes=export['bytes'],scope='Shared scale and realism repair: shortened both bridges with shore/island/path deformation and physical route widening; coherent main roof massing; material roughness/wetness and rafter UVs; clustered ecotones; fitted wall-head friezes',quality_reference='Official Game Science images via Unreal Engine and PlayStation; v8 remains layout authority',benchmark_quality_status='PARTIAL_NOT_EQUIVALENT_TO_REFERENCE_GAME',rendered_views=12,cameras=34)
d['rejected_experiments']+=['v043 bridge pavement not following arch','v044 partial terrain folding and route contact','v047 wall-head daylight gap found in eye-level review and repaired in v048'];m.write_text(json.dumps(d,ensure_ascii=False,indent=2));assert sha(R/'zen-space.blend')==h
for name in ['README.md']:
 p=R/name;s=p.read_text().replace('p3-v042','p3-v048').replace('fidelity-repair-review-v042.md','quality-retrospective-v048.md');s=s.replace('fidelity-repair-review-v048.md','quality-retrospective-v048.md');s=s.replace('本轮补齐东侧连廊和院门，重做曲线种植与高台坡肩，并使西岸路面、岸线和地形同步弯曲。总览前图已用 v039 源文件按相同机位重渲染。桥跨、屋顶体量和规则草地仍有明显差距。','本轮缩短双桥并同步岸、岛和路面，调整四座屋顶体量，补齐墙顶封檐、椽木 UV、岸石干湿关系和成组林缘。总览与 v042 同机位对比；古建连接细节、池岸切面和远山空气层次仍存在差距。');p.write_text(s)
a=R/'manifest/asset-register.csv';backup=R/'manifest/asset-register-before-v048.csv'
if not backup.exists():shutil.copy2(a,backup)
shutil.copy2(R/'manifest/asset-register-current.csv',a)
print('PROMOTED_V048',h,export['bytes'])
