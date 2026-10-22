"""Fixed-camera source-model motion, preview engine documented separately from Cycles stills."""
import bpy,json,sys,time,math
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [];seconds=int(args[0]) if args else 20;test='--test' in args;light='--light' in args;cam='CAM_ENTRY_SEATED' if seconds==20 else 'CAM_COPY_VIEW';ver=s.get('version');out=R/'previews'/ver/'motion';out.mkdir(parents=True,exist_ok=True)
s.render.engine='CYCLES';s.cycles.samples=4 if light else 8;s.cycles.use_denoising=True;s.render.use_persistent_data=True
prefs=bpy.context.preferences.addons['cycles'].preferences;prefs.compute_device_type='METAL';prefs.get_devices()
for d in prefs.devices:d.use=d.type=='METAL'
s.cycles.device='GPU';s.render.resolution_x=640 if light else 960;s.render.resolution_y=360 if light else 540;s.render.resolution_percentage=100;s.render.fps=12;s.frame_start=1;s.frame_end=seconds*12;s.camera=bpy.data.objects[cam]
for vl in s.view_layers:vl.use=vl.name=='EYE_LEVEL'
# Gentle independent source clips; no camera animation and no mandatory welcoming event.
clips=json.loads((R/'manifest/object-animation-clips.json').read_text())['clips'];selected=['Z01_BIRD_IDLE'] if seconds==20 else ['Z05_LAMP_FLICKER','Z05_LAMP_LIGHT_FLICKER','Z05_SHADOW_LONG_PAUSE']
for row in clips:
 if row['name'] not in selected:continue
 o=s.objects[row['object']];o=o.data if row.get('target_kind')=='LIGHT_DATA' else o;o.animation_data.action=bpy.data.actions[row['name']]
 # Keyframes authored24fps, output12fps: time scale through NLA ensures duration preserved.
 slot=o.animation_data.action_slot;action=o.animation_data.action;o.animation_data.action=None;t=o.animation_data.nla_tracks.new();t.name='PREVIEW_'+row['name'];st=t.strips.new(row['name'],1,action);st.action_slot=slot;st.scale=.5
 if row.get('loop'):st.repeat=math.ceil(seconds/max(.1,row['seconds']))
 s.frame_set(1)
# Shader waterfall mapping was24fps; slow timeline to preserve physical preview speed.
if seconds==20:
 for name in ['P3_FALLING_WATER']:
  nt=bpy.data.materials[name].node_tree
  if nt.animation_data and nt.animation_data.action:
   a=nt.animation_data.action;slot=nt.animation_data.action_slot;nt.animation_data.action=None;t=nt.animation_data.nla_tracks.new();st=t.strips.new('PREVIEW_WATER_FLOW',1,a);st.action_slot=slot;st.scale=.5
s.render.image_settings.file_format='PNG';frames=out/(f'{seconds}s-frames');frames.mkdir(exist_ok=True);start=time.monotonic()
for fr in ([1] if test else range(1,s.frame_end+1)):
 s.frame_set(fr);s.render.filepath=str(frames/f'{fr:05}.png');bpy.ops.render.render(write_still=True,layer='EYE_LEVEL')
 if fr%24==0:print('MOTION_FRAME',seconds,fr,s.frame_end,flush=True)
(R/'evidence'/f'motion-{ver}-{seconds}s.json').write_text(json.dumps({'version':ver,'camera':cam,'camera_fixed':True,'duration_s':seconds,'fps':12,'resolution':[s.render.resolution_x,s.render.resolution_y],'engine':s.render.engine,'samples':s.cycles.samples,'clips':selected,'source':bpy.data.filepath,'frames_rendered':1 if test else s.frame_end,'test_only':test,'seconds_elapsed':time.monotonic()-start},indent=2))
