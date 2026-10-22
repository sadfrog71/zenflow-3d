"""Render actual assigned koi/cat actions; sample 24fps source at 12fps output."""
import bpy,json,time
from pathlib import Path
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;v=s.get('version');out=R/'previews'/v/'motion';out.mkdir(parents=True,exist_ok=True)
s.render.engine='CYCLES';s.cycles.samples=4;s.cycles.use_denoising=True;s.render.use_persistent_data=True
p=bpy.context.preferences.addons['cycles'].preferences;p.compute_device_type='METAL';p.get_devices()
for d in p.devices:d.use=d.type=='METAL'
s.cycles.device='GPU';s.render.resolution_x=640;s.render.resolution_y=360;s.render.resolution_percentage=100;s.render.image_settings.file_format='PNG'
next(n for n in s.world.node_tree.nodes if n.type=='BACKGROUND').inputs['Strength'].default_value=s.get('day_world_strength',.42)
s.view_settings.exposure=s.get('day_exposure',.32)
for vl in s.view_layers:vl.use=vl.name=='EYE_LEVEL'
rows=[]
for camera,seconds,slug in [('CAM_POND_LIFE',8,'pond'),('CAM_CATS_VERANDA',2,'cat-watch'),('CAM_CAT_SLEEP',2,'cat-sleep')]:
 folder=out/slug;folder.mkdir(exist_ok=True);s.camera=s.objects[camera];start=time.monotonic()
 for i in range(seconds*12):
  s.frame_set(1+2*i);s.render.filepath=str(folder/f'{i:05}.png');bpy.ops.render.render(write_still=True,layer='EYE_LEVEL')
  if i%24==0:print('LIVING_FRAME',slug,i,seconds*12,flush=True)
 rows.append({'camera':camera,'seconds':seconds,'frames':seconds*12,'fps':12,'source_fps':24,'source_frame_step':2,'seconds_elapsed':time.monotonic()-start})
(R/'evidence'/f'living-motion-{v}.json').write_text(json.dumps({'source':bpy.data.filepath,'clips':json.loads((R/'manifest/living-garden-clips.json').read_text())['clips'],'renders':rows,'engine':'Cycles METAL','samples':4,'resolution':[640,360]},indent=2))
