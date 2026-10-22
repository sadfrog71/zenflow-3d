"""Render existing source without moving scene geometry. Run in a new Blender process."""
import bpy,json,sys,time,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
s=bpy.context.scene
args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
first=['CAM_WORLD_FULL','CAM_LAYOUT_TOP','CAM_ENTRY_SEATED','CAM_LIBRARY_SHELF']
names=first if 'first' in args else [c['name'] for c in json.loads((ROOT/'manifest/scene-manifest.json').read_text())['cameras']]
def signature():
 rows=[]
 for o in s.objects:
  if o.type=='MESH':rows.append((o.name,[round(v,6) for row in o.matrix_world for v in row],len(o.data.vertices),len(o.data.polygons),hashlib.sha256(repr([tuple(v.co) for v in o.data.vertices]).encode()).hexdigest()))
 return hashlib.sha256(json.dumps(sorted(rows)).encode()).hexdigest()
start=signature();results=[]
for name in names:
 state='LAYOUT_CUTAWAY' if name.startswith('CAM_LAYOUT') else 'WORLD_FULL' if name=='CAM_WORLD_FULL' else 'EYE_LEVEL'
 for vl in s.view_layers:vl.use=vl.name==state
 s.camera=bpy.data.objects[name]
 s.display.shading.show_shadows=state!='LAYOUT_CUTAWAY'
 s.render.filepath=str(ROOT/'previews/stills'/f'{name}.png')
 t=time.monotonic();bpy.ops.render.render(write_still=True,layer=state)
 results.append({'camera':name,'state':state,'file':f'previews/stills/{name}.png','seconds':round(time.monotonic()-t,2)})
 print('RENDERED',name,flush=True)
end=signature()
(ROOT/'evidence'/('render-first.json' if 'first' in args else 'render-all.json')).write_text(json.dumps({'source':bpy.data.filepath,'blender':bpy.app.version_string,'engine':s.render.engine,'resolution':[s.render.resolution_x,s.render.resolution_y],'geometry_signature_before':start,'geometry_signature_after':end,'geometry_unchanged':start==end,'renders':results},indent=2))
print('REOPEN_AND_RENDER_PASS',start==end,flush=True)
