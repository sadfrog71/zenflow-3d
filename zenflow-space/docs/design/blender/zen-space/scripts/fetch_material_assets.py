"""Explicit CC0 art dependencies; fixed assets/resolutions only, no crawl."""
from pathlib import Path
import json,urllib.request,hashlib,concurrent.futures
ROOT=Path(__file__).resolve().parents[1];dest=ROOT/'assets/polyhaven';dest.mkdir(exist_ok=True)
tasks=[]
for asset in ['kloofendal_48d_partly_cloudy_puresky','wood_floor_deck','rock_boulder_cracked']:
 j=json.loads((ROOT/'evidence'/('polyhaven-'+asset+'.json')).read_text())
 keys=['hdri'] if 'hdri' in j else ['Diffuse','nor_gl','Rough','Displacement']
 for k in keys:
  kind='hdr' if k=='hdri' else 'jpg';res='2k' if k!='Displacement' else '1k';entry=j[k][res][kind]
  tasks.append((asset,k,res,entry))
def fetch(row):
 asset,k,res,e=row;p=dest/e['url'].split('/')[-1]
 if not p.exists():
  req=urllib.request.Request(e['url'],headers={'User-Agent':'ZenflowSpace Blender personal art project'});p.write_bytes(urllib.request.urlopen(req,timeout=90).read())
 md5=hashlib.md5(p.read_bytes()).hexdigest()
 if md5!=e['md5']:raise RuntimeError('Checksum mismatch: '+p.name)
 return {'asset':asset,'map':k,'resolution':res,'file':str(p.relative_to(ROOT)),'url':e['url'],'md5':md5,'size':p.stat().st_size,'license':'CC0','source_page':'https://polyhaven.com/a/'+asset}
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:rows=list(pool.map(fetch,tasks))
(ROOT/'manifest/third-party-assets.json').write_text(json.dumps({'license_page':'https://polyhaven.com/license','api_terms':'https://github.com/Poly-Haven/Public-API/blob/master/ToS.md','fetched':'2026-10-20','files':rows},indent=2))
print('CC0_FILES',len(rows),'TOTAL_BYTES',sum(r['size'] for r in rows))
