"""Versioned real model renders. Light/weather presets only; no per-camera geometry edits."""
import bpy,sys,math,json,time,hashlib
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1];s=bpy.context.scene
args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [];gpu='--gpu' in args;layout='LH' if '--layout=LH' in args else 'RH';args=[a for a in args if a!='--gpu' and not a.startswith('--layout=')];state=args[0] if args else 'day';names=args[1:] or ['CAM_ENTRY_SEATED','CAM_LIBRARY_SHELF','CAM_COPY_VIEW','CAM_ISLAND_SEATED'];ver=s.get('version','unversioned');out=ROOT/'previews'/ver/(state+('-LH' if layout=='LH' else '')+('-gpu' if gpu else ''));out.mkdir(parents=True,exist_ok=True)
if layout=='LH':
 for cname,show in [('STATE_Z05_RH_PROPS',False),('STATE_Z05_LH_PROPS',True)]:
  c=bpy.data.collections.get(cname)
  if c:
   for ob in c.objects:ob.hide_render=not show;ob.hide_viewport=not show
active_devices=[]
if gpu:
 try:
  prefs=bpy.context.preferences.addons['cycles'].preferences;prefs.compute_device_type='METAL';prefs.get_devices()
  for d in prefs.devices:d.use=d.type=='METAL'
  active_devices=[d.name for d in prefs.devices if d.use];s.cycles.device='GPU' if active_devices else 'CPU'
 except Exception as error:print('GPU_SETUP_FALLBACK',str(error),flush=True);s.cycles.device='CPU'
cloud=s.objects.get('ENV_FAR_VALLEY_CLOUD_SEA')
if cloud:cloud.hide_render=state not in ['dawn','sunset']
sun=bpy.data.objects['DAY_SUN'];bg=next(n for n in s.world.node_tree.nodes if n.type=='BACKGROUND')
if state=='day':bg.inputs['Strength'].default_value=s.get('day_world_strength',.5);sun.data.energy=s.get('day_sun_energy',1.8);s.view_settings.exposure=s.get('day_exposure',.55)
else:
 for link in list(bg.inputs['Color'].links):s.world.node_tree.links.remove(link)
 if state in ['dusk','sunset','dawn']:
  direction=Vector((1,-.2,-.13)) if state in ['dusk','sunset'] else Vector((-1,-.3,-.13));sun.rotation_euler=direction.to_track_quat('-Z','Y').to_euler();sun.data.energy=.8;sun.data.color=(1,.55,.27);bg.inputs['Color'].default_value=(.24,.35,.52,1);bg.inputs['Strength'].default_value=.22;s.view_settings.exposure=.7
 elif state in ['night','off']:
  sun.rotation_euler=Vector((-.4,.2,-.7)).to_track_quat('-Z','Y').to_euler();sun.data.energy=.025;sun.data.color=(.4,.57,1);bg.inputs['Color'].default_value=(.12,.19,.32,1);bg.inputs['Strength'].default_value=.10;s.view_settings.exposure=1.1
 elif state=='rain':
  sun.data.energy=.2;sun.data.color=(.7,.8,1);bg.inputs['Color'].default_value=(.35,.44,.52,1);bg.inputs['Strength'].default_value=.45;s.view_settings.exposure=.8
if state in ['dawn','sunset']:
 nt=s.world.node_tree;sky=nt.nodes.new('ShaderNodeTexSky');sky.sky_type='MULTIPLE_SCATTERING';sky.sun_elevation=.10;sky.sun_rotation=math.pi/2-(math.atan2(.18,1) if state=='dawn' else math.atan2(.2,-1));sky.sun_disc=True;sky.sun_intensity=.65;sky.air_density=1;sky.aerosol_density=.6;sky.ozone_density=1;nt.links.new(sky.outputs['Color'],bg.inputs['Color']);bg.inputs['Strength'].default_value=.065;s.view_settings.exposure=.5;sun.data.energy=1.0;sun.data.color=(1,.72,.45)
for mat in bpy.data.materials:
 if mat.name.startswith('P4_FAR_FOREST_SPRITE_'):
  em=mat.node_tree.nodes.get('FAR_FOREST_EMISSION')
  if em:em.inputs['Strength'].default_value=.85 if state=='day' else .32 if state in ['dawn','dusk','sunset'] else .06 if state in ['night','off'] else .55
for o in s.objects:
 if o.type=='LIGHT':
  if o.name.startswith('P2_WINDOW'):o.data.energy=230 if state=='day' else 70 if state in ['dusk','sunset','dawn'] else 60 if state in ['night','off'] else 170
  if o.name.startswith('P3_LIBRARY_NORTH'):o.data.energy=280 if state=='day' else 95 if state in ['dusk','sunset','dawn'] else 40 if state in ['night','off'] else 210
  if o.name.startswith(('P3_PRIVATE_WINDOW','P3_COTTAGE_WINDOW')):o.data.energy=165 if state=='day' else 45 if state in ['dusk','sunset','dawn'] else 5 if state in ['night','off'] else 100
  if o.name=='P3_COTTAGE_LAMP':o.data.energy=.8 if state=='day' else 1.8 if state in ['dusk','sunset','dawn'] else 3
  if o.name.startswith('P2_COPY_LAMP'):o.data.energy=0 if state=='off' else 4 if state=='day' else 9
  if o.name.startswith('P2_VERANDA'):o.data.energy=100 if state=='day' else 25 if state in ['dusk','sunset','dawn'] else 4
if state=='rain':
 for mat in bpy.data.materials:
  if mat.use_nodes and mat.get('rain_roughness') is not None:
   p=next((n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED'),None)
   if p:
    for link in list(p.inputs['Roughness'].links):mat.node_tree.links.remove(link)
    p.inputs['Roughness'].default_value=mat['rain_roughness']
 cushion=s.objects['Z03_ONLY_ROUND_CUSHION'];cushion.data=cushion.data.copy();mat=cushion.data.materials[0].copy();cushion.data.materials[0]=mat;p=next(n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
 if p.inputs['Base Color'].links:
  src=p.inputs['Base Color'].links[0].from_socket;h=mat.node_tree.nodes.new('ShaderNodeHueSaturation');h.inputs['Value'].default_value=.82;mat.node_tree.links.new(src,h.inputs['Color']);mat.node_tree.links.new(h.outputs[0],p.inputs['Base Color'])
if state=='off':
 flame=s.objects.get('SM_Z05_OIL_FLAME')
 if flame:flame.hide_render=True
 for material_name in ['P2_Glow','P5_WASHI_FIBRE_LIGHT']:
  material=bpy.data.materials.get(material_name)
  if material:
   p=next(n for n in material.node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Emission Strength'].default_value=0
s.render.resolution_x=1600;s.render.resolution_y=900;s.cycles.samples=32;s.render.image_settings.file_format='PNG';s.render.fps=24
missing=[n for n in names if n not in s.objects]
if missing:raise RuntimeError('Unknown render cameras: '+str(missing))
results=[]
for name in names:
 layer='LAYOUT_CUTAWAY' if name.startswith('CAM_LAYOUT') else 'WORLD_FULL' if name in ['CAM_WORLD_FULL','CAM_P2_CORE_OVERVIEW','CAM_GARDEN_COMPOSITION'] else 'EYE_LEVEL'
 for vl in s.view_layers:vl.use=vl.name==layer
 s.camera=bpy.data.objects[name];s.render.filepath=str(out/f'{name}.png');start=time.monotonic();bpy.ops.render.render(write_still=True,layer=layer)
 results.append({'camera':name,'layer':layer,'state':state,'file':str(Path(s.render.filepath).relative_to(ROOT)),'seconds':round(time.monotonic()-start,2),'position':list(s.camera.location),'rotation':list(s.camera.rotation_euler),'lens':s.camera.data.lens,'projection':s.camera.data.type});print('MODEL_RENDERED',ver,state,name,flush=True)
(ROOT/'evidence'/(f'render-{ver}-{state}-{names[0]}'+(('-LH' if layout=='LH' else '')+('-gpu.json' if gpu else '.json')))).write_text(json.dumps({'source':bpy.data.filepath,'version':ver,'engine':s.render.engine,'resolution':[1600,900],'view_transform':s.view_settings.view_transform,'look':s.view_settings.look,'exposure':s.view_settings.exposure,'sun_direction':list(sun.rotation_euler.to_matrix()@Vector((0,0,-1))),'samples':s.cycles.samples,'rig_lights':[{'name':o.name,'type':o.data.type,'energy':o.data.energy,'color':list(o.data.color)} for o in s.objects if o.type=='LIGHT'],'render_device':s.cycles.device,'active_devices':active_devices,'geometry_policy':'same saved building/landscape/paper; explicit handedness variant state only; no camera-specific object relocation or image compositing','handedness_layout':layout,'renders':results},ensure_ascii=False,indent=2))
