import bpy,sys,json,time,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];s=bpy.context.scene
args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
state=args[0] if args else 'day';camnames=args[1:] or ['CAM_ENTRY_SEATED','CAM_LIBRARY_SEATED','CAM_LIBRARY_SHELF','CAM_COPY_VIEW']
# Render-only correction after material classification inspection; no per-camera geometry changes.
roof=bpy.data.materials.get('P2_Roof')
for o in s.objects:
 if o.type in ['MESH','CURVE'] and o.name.startswith(('Z01','Z04','Z05')) and ('ROOF' in o.name or 'TILE_COURSE' in o.name):
  if o.data.materials:o.data.materials[0]=roof
sun=bpy.data.objects['DAY_SUN'];bg=next(n for n in s.world.node_tree.nodes if n.type=='BACKGROUND')
if state=='day':sun.data.energy=2;bg.inputs['Strength'].default_value=.45
elif state=='dusk':
 for l in list(bg.inputs['Color'].links):s.world.node_tree.links.remove(l)
 sun.rotation_euler=(1.4,-.15,-1.0);sun.data.energy=.7;sun.data.color=(1,.6,.32);bg.inputs['Color'].default_value=(.30,.40,.58,1);bg.inputs['Strength'].default_value=.22
elif state in ['night','off']:
 for l in list(bg.inputs['Color'].links):s.world.node_tree.links.remove(l)
 sun.data.energy=.035;sun.data.color=(.38,.55,1);bg.inputs['Color'].default_value=(.10,.16,.30,1);bg.inputs['Strength'].default_value=.10;s.view_settings.exposure=1.1
for o in s.objects:
 if o.type=='LIGHT' and o.name.startswith('P2_WINDOW'):
  o.data.energy=230 if state=='day' else 70 if state=='dusk' else 12
 if o.type=='LIGHT' and o.name.startswith('P2_COPY_LAMP'):o.data.energy=0 if state=='off' else 4 if state=='day' else 9
 if o.type=='LIGHT' and o.name.startswith('P2_VERANDA'):o.data.energy=100 if state=='day' else 25 if state=='dusk' else 4
if state=='off':
 p=next(n for n in bpy.data.materials['P2_Glow'].node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Emission Strength'].default_value=0
for vl in s.view_layers:vl.use=vl.name=='EYE_LEVEL'
results=[]
for n in camnames:
 s.camera=bpy.data.objects[n];s.render.filepath=str(ROOT/'previews/phase2'/f'{n}-{state}.png');start=time.monotonic();bpy.ops.render.render(write_still=True,layer='EYE_LEVEL');results.append({'camera':n,'state':state,'seconds':round(time.monotonic()-start,2),'path':s.render.filepath});print('P2_RENDERED',n,state,flush=True)
(ROOT/'evidence'/('phase2-render-'+state+'-'+camnames[0]+'.json')).write_text(json.dumps({'source':bpy.data.filepath,'version':s.get('version'),'engine':s.render.engine,'resolution':[s.render.resolution_x,s.render.resolution_y],'renders':results},indent=2))
