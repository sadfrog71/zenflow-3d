"""Guarded promotion of reviewed v042 source, with prior source preserved."""
from pathlib import Path
import hashlib,json,shutil
R=Path(__file__).resolve().parents[1];E=R/'evidence';hashfile=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
old='98ad08fb268fd71bb76598cce45e17c1f145819d145e3172ac950481c671ec24'
assert hashfile(R/'zen-space.blend')==old,'Master changed; stop rather than overwrite WIP'
checks=json.loads((E/'structure-check-p3-v042.json').read_text());assert checks['version']=='p3-v042' and all(x['status']=='PASS' for x in checks['checks'])
export=json.loads((E/'full-glb-export-check-v042.json').read_text());assert export['source_version']=='p3-v042' and len(export['animations'])==17
views=json.loads((E/'repair-view-inventory-v042.json').read_text());assert len(views)==12
for row in views:assert (R/'previews'/row['image']).is_file()
assert (R/'previews/p3-v042/motion/living-garden.mp4').stat().st_size>10000
p=E/'current-work-manifest.json';d=json.loads(p.read_text());backup=E/'current-work-manifest-p3-v039.json'
if not backup.exists():shutil.copy2(p,backup)
sha=hashfile(R/'zen-space-p3-v042.blend');snapshot=R/'snapshots/20261021/zen-space-p3-v042.blend';snapshot.parent.mkdir(parents=True,exist_ok=True)
shutil.copy2(R/'zen-space-p3-v042.blend',snapshot);shutil.copy2(snapshot,R/'zen-space.blend')
d.update(version='p3-v042',source_snapshot='zen-space-p3-v042.blend',dated_snapshot=str(snapshot.relative_to(R)),sha256=sha,previous_master_sha256=old,rendered_views=12,cameras=34,report='fidelity-repair-review-v042.md',route_segments=91,scope='Shared scene composition: east gallery and courtyard gate, curved planting, terrace shoulders, actual west bank and path deformation; bridge endpoints and water levels preserved',exchange_bytes=export['bytes'])
p.write_text(json.dumps(d,ensure_ascii=False,indent=2));assert hashfile(R/'zen-space.blend')==sha
print('PROMOTED',sha)
