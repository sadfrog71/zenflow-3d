"""Clear water, loosely phased koi paths, readable inspection views and final scene state."""
import bpy,math,json
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v035.blend'
if s.get('version')!='p3-v034' or out.exists():raise RuntimeError('Requires v034/new output')
p=next(n for n in bpy.data.materials['P2_Water'].node_tree.nodes if n.type=='BSDF_PRINCIPLED');p.inputs['Base Color'].default_value=(.88,.97,.94,1);p.inputs['Transmission Weight'].default_value=.98;p.inputs['Roughness'].default_value=.035
bed=bpy.data.materials['P6_SUBMERGED_PALE_MINERAL_BED'];nt=bed.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED');src=p.inputs['Base Color'].links[0].from_socket;mix=nt.nodes.new('ShaderNodeMixRGB');mix.blend_type='MULTIPLY';mix.inputs[0].default_value=1;mix.inputs[2].default_value=(.55,.74,.64,1);nt.links.new(src,mix.inputs[1]);nt.links.new(mix.outputs[0],p.inputs['Base Color'])
# Loose, phase-separated loops keep the fish in one visible water zone without a rigid shared transform.
labels=['BLACK','PEARL','RED_WHITE_A','RED_GOLD','RED_WHITE_B'];radii=[(3.2,2.5),(2.7,3.4),(3.7,2.7),(2.0,1.8),(2.9,2.7)];records=[]
for i,label in enumerate(labels):
 rt=s.objects['P6_KOI_'+label+'_ROOT'];rt.animation_data_clear();rx,ry=radii[i];previous=None;phase=i*math.tau/5
 for fr in range(1,1442,24):
  sec=(fr-1)/24;q=math.tau*sec/60+phase;a=q+.12*math.sin(2*q+i*.35);x=-31.5+rx*math.cos(a)+.16*math.sin(2*a+i);y=10.5+ry*math.sin(a)+.12*math.cos(3*a+i);aq=a+.0001;dx=rx*(math.cos(aq)-math.cos(a))+.16*(math.sin(2*aq+i)-math.sin(2*a+i));dy=ry*(math.sin(aq)-math.sin(a))+.12*(math.cos(3*aq+i)-math.cos(3*a+i));ang=math.atan2(dx,-dy)
  if previous is not None:
   while ang-previous>math.pi:ang-=math.tau
   while ang-previous<-math.pi:ang+=math.tau
  previous=ang;rt.location=(x,y,-1.43-.018*i+.012*math.sin(2*a));rt.rotation_euler.z=ang;rt.keyframe_insert(data_path='location',frame=fr);rt.keyframe_insert(data_path='rotation_euler',frame=fr)
 action=rt.animation_data.action;action.name='P6_KOI_'+label+'_SWIM_VISIBLE_LOOP';action.use_fake_user=True
 for layer in action.layers:
  for st in layer.strips:
   for bag in st.channelbags:
    for f in bag.fcurves:f.modifiers.new('CYCLES')
 records.append({'root':rt.name,'action':action.name,'period_s':60,'phase':phase,'radii_m':[rx,ry]})
cam=s.objects['CAM_POND_LIFE'];cam.location=(-32.0,6.0,6.0);target=Vector((-31.5,10.5,-1.35));cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.lens=28
s.frame_set(1);bpy.context.view_layer.update();p=s.objects['P6_KOI_BLACK_ROOT'].matrix_world.translation;cam=s.objects['CAM_KOI_CLOSE'];cam.location=(p.x-2.2,p.y-2.3,1.2);target=p+Vector((-.1,.05,.03));cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.lens=65
# Compact whisker pads and short inner-ear fur keep the small cats understated in the garden.
for ob in bpy.data.collections['P6_ORIGINAL_CATS'].objects:
 if 'MUZZLE' in ob.name:ob.scale*=.9
 if 'EAR_' in ob.name and 'INNER' not in ob.name:
  for mod in ob.modifiers:
   if mod.type=='BEVEL':mod.width=.005
s['version']='p3-v035';s['phase_status']='P6_LIVING_GARDEN_DELIVERY_CANDIDATE';s.render.fps=24;s.frame_start=1;s.frame_end=1441;s.frame_set(1);bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
reg=json.loads((R/'manifest/living-garden-clips.json').read_text());reg['source_version']='p3-v035';reg['clips']=[r for r in reg['clips'] if not r['name'].endswith('_SWIM_LOOP')]
for row in records:reg['clips'].append({'name':row['action'],'object':row['root'],'seconds':60,'loop':True})
(R/'manifest/living-garden-clips.json').write_text(json.dumps(reg,indent=2));(R/'evidence/living-garden-v035.json').write_text(json.dumps({'version':'p3-v035','base':'p3-v034','koi_total':5,'red_koi':3,'cats':2,'path_records':records,'water_material':{'IOR':1.333,'transmission':.98,'roughness':.035,'appearance':'clear thin water over actual sage mineral bed; retained source reflections; no animal image compositing'},'layout':'building positions and primary seated views retained; full roof/garden geometry shared'},indent=2));print('FINAL_LIVING_GARDEN_V035',len(records),'fish, 3 red, 2 cats',flush=True)
