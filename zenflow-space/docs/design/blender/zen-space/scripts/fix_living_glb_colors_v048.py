"""Use authored living colour attribute as glTF primary vertex colour (exporter emitted dummy COLOR_0)."""
import json,struct
from pathlib import Path
R=Path(__file__).resolve().parents[1];p=R/'exports/zen-space-p3-v048.glb';b=p.read_bytes();n=struct.unpack_from('<I',b,12)[0];g=json.loads(b[20:20+n]);fixed=[]
for node in g['nodes']:
 name=node.get('name','')
 if not (name.startswith('SM_P6_KOI_') or name=='SM_P6_CAT_WATCH_SCULPT') or 'mesh' not in node:continue
 for primitive in g['meshes'][node['mesh']]['primitives']:
  a=primitive['attributes']
  if 'COLOR_1' in a:a['COLOR_0'],a['COLOR_1']=a['COLOR_1'],a['COLOR_0'];fixed.append(name)
j=json.dumps(g,separators=(',',':')).encode();j+=b' '*((-len(j))%4);rest=b[20+n:];out=struct.pack('<III',0x46546c67,2,20+len(j)+len(rest))+struct.pack('<II',len(j),0x4e4f534a)+j+rest;p.write_bytes(out)
r=R/'evidence/full-glb-export-check.json';report=json.loads(r.read_text());report['bytes']=len(out);report['scope']='Desktop exchange with 17 active living clips; water and procedural materials approximate Blender';report['primary_vertex_colour_repairs']=fixed;r.write_text(json.dumps(report,ensure_ascii=False,indent=2));(R/'evidence/living-glb-colour-repair-v048.json').write_text(json.dumps({'objects':fixed,'reason':'authored colour exported to COLOR_1 beside white dummy COLOR_0; promote authored attribute for all glTF viewers'},indent=2));print('AUTHORED_COLORS',len(fixed))
