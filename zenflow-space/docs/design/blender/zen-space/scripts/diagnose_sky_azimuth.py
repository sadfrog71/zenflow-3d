import bpy,math,json,numpy as np
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene
for o in s.objects:
 if o.type not in ['CAMERA']:o.hide_render=True
nt=s.world.node_tree;bg=next(n for n in nt.nodes if n.type=='BACKGROUND')
for l in list(bg.inputs['Color'].links):nt.links.remove(l)
sky=nt.nodes.new('ShaderNodeTexSky');sky.sky_type='MULTIPLE_SCATTERING';sky.sun_elevation=.1;nt.links.new(sky.outputs[0],bg.inputs['Color']);bg.inputs['Strength'].default_value=.18
cam=s.objects['CAM_LOOKOUT_E'];cam.location=(0,0,0);cam.data.lens=16;s.camera=cam;s.render.engine='CYCLES';s.cycles.samples=1;s.render.resolution_x=64;s.render.resolution_y=64;s.render.resolution_percentage=100;s.view_settings.exposure=-3;rows=[]
for rotation in [0,math.pi/2]:
 sky.sun_rotation=rotation
 for heading in range(0,360,45):
  a=math.radians(heading);cam.rotation_euler=(Vector((math.cos(a),math.sin(a),.1))).to_track_quat('-Z','Y').to_euler();s.render.filepath='/tmp/zen-sky-test.png';bpy.ops.render.render(write_still=True,layer='EYE_LEVEL');im=bpy.data.images.load('/tmp/zen-sky-test.png',check_existing=False);p=np.array(im.pixels[:]).reshape(-1,4);rows.append({'sky_rotation':rotation,'camera_heading_from_east_deg':heading,'maximum':float(p[:,:3].max()),'mean':float(p[:,:3].mean())});bpy.data.images.remove(im)
(R/'evidence/sky-azimuth-diagnosis.json').write_text(json.dumps(rows,indent=2));print('SKY_AZIMUTH',rows,flush=True)
