"""Editable object states and independently addressable animation clips, no application logic."""
import bpy,math,json,sys,random
from pathlib import Path
from mathutils import Vector,Matrix
from bpy_extras.object_utils import world_to_camera_view
R=Path(__file__).resolve().parents[1];sys.path.insert(0,str(R/'scripts'));from phase2_materials import box,beam,material,assign
s=bpy.context.scene;out=R/'zen-space-p3-v012.blend'
if s.get('version')!='p3-v011' or out.exists():raise RuntimeError('Requires new v012 from v011')
C=bpy.data.collections.new('P3_INDEPENDENT_OBJECT_STATES');s.collection.children.link(C);registry=[];states=[]
def root(name,p):
 o=bpy.data.objects.new(name,None);C.objects.link(o);o.location=p;o.empty_display_type='PLAIN_AXES';o.empty_display_size=.12;return o
def parent_keep(o,r):
 bpy.context.view_layer.update();mat=o.matrix_world.copy();o.parent=r;o.matrix_world=mat

def sphere(name,p,scale,mat,parent=None):
 bpy.ops.mesh.primitive_uv_sphere_add(segments=20,ring_count=12,radius=1,location=p);o=bpy.context.object;o.name=name
 for c in list(o.users_collection):c.objects.unlink(o)
 C.objects.link(o);o.scale=scale;assign(o,mat)
 for f in o.data.polygons:f.use_smooth=True
 if parent:parent_keep(o,parent)
 return o

def clip(o,name,channel,keys,loop=False,kind='transform'):
 original=getattr(o,channel).copy() if hasattr(getattr(o,channel),'copy') else getattr(o,channel)
 o.animation_data_create();o.animation_data.action=None
 for frame,value in keys:setattr(o,channel,value);o.keyframe_insert(data_path=channel,frame=frame)
 action=o.animation_data.action;action.name=name;action.use_fake_user=True
 slot=o.animation_data.action_slot
 track=o.animation_data.nla_tracks.new();track.name='CLIP_LIBRARY_'+name;strip=track.strips.new(name,1,action)
 if slot:strip.action_slot=slot
 track.mute=True;o.animation_data.action=None;setattr(o,channel,original)
 registry.append({'name':name,'object':o.name,'channel':channel,'frames':[keys[0][0],keys[-1][0]],'seconds':(keys[-1][0]-keys[0][0])/24,'loop':loop,'default_playing':False,'type':kind})
 return name
# Sparrow: separate head/wing/tail parts and feet at real courtyard scale.
feather=material('P3_SPARROW_BACK',(.20,.135,.065),None,.88);breast=material('P3_SPARROW_BREAST',(.52,.44,.30),None,.95);dark=material('P3_SPARROW_DARK',(.025,.02,.014),None,.8);beak=material('P3_SPARROW_BEAK',(.15,.12,.075),None,.8)
bird=root('ANCHOR_Z01_SPARROW',(-14.8,9.55,.04));body=sphere('SM_Z01_SPARROW_BODY',(-14.8,9.55,.12),(.055,.080,.06),feather,bird);sphere('SM_Z01_SPARROW_BREAST',(-14.8,9.50,.115),(.045,.04,.049),breast,bird)
head=root('PIVOT_Z01_SPARROW_HEAD',(-14.8,9.495,.175));parent_keep(head,bird);sphere('SM_Z01_SPARROW_HEAD',(-14.8,9.49,.175),(.038,.038,.037),feather,head)
for dx in [-.029,.029]:sphere('SM_Z01_SPARROW_EYE',(-14.8+dx,9.466,.184),(.004,.004,.004),dark,head)
be=beam('SM_Z01_SPARROW_BEAK',(-14.8,9.455,.171),(-14.8,9.433,.166),.009,C,beak,6);parent_keep(be,head)
for side in [-1,1]:
 wing=sphere('SM_Z01_SPARROW_WING',(-14.8+side*.044,9.565,.13),(.016,.071,.037),dark,bird);wing.rotation_euler.x=-.25
 for k in range(3):
  line=beam('SM_Z01_WING_FEATHER',(-14.8+side*.055,9.53+k*.017,.147),(-14.8+side*.05,9.59+k*.014,.11),.002,C,breast,6);parent_keep(line,bird)
 for yy in [.0,.012]:
  foot=beam('SM_Z01_SPARROW_FOOT',(-14.8+side*.018,9.55,.095),(-14.8+side*.018,9.53+yy,.045),.0025,C,beak,6);parent_keep(foot,bird)
tail=box('SM_Z01_SPARROW_TAIL',(-14.8,9.645,.12),(.028,.085,.009),C,dark,.005);tail.rotation_euler.x=-.12;parent_keep(tail,bird)
p=bird.location.copy();clip(bird,'Z01_BIRD_IDLE','location',[(1,p),(240,p+Vector((0,0,.0015))),(480,p)],True);clip(bird,'Z01_BIRD_HOP','location',[(1,p),(6,p+Vector((0,-.08,.075))),(12,p+Vector((0,-.16,0)))]);clip(bird,'Z01_BIRD_APPROACH','location',[(1,p+Vector((.8,.6,0))),(50,p+Vector((.4,.3,0))),(96,p)]);clip(bird,'Z01_BIRD_LEAVE','location',[(1,p),(12,p+Vector((.1,.1,.15))),(48,p+Vector((2.5,2,2)))]);rot=Vector(head.rotation_euler);clip(head,'Z01_BIRD_PECK','rotation_euler',[(1,rot),(12,rot+Vector((.75,0,0))),(20,rot+Vector((.82,0,0))),(36,rot)])
# Fish near quiet water, a physical tapered body and independent tail.
fishmat=material('P3_POND_FISH',(.18,.13,.055),None,.35);finmat=material('P3_POND_FISH_FIN',(.22,.17,.10),None,.5)
fish=root('ANCHOR_Z02_FISH',(-24,9,-1.32));sphere('SM_Z02_FISH_BODY',(-24,9,-1.32),(.045,.18,.055),fishmat,fish);tail=sphere('SM_Z02_FISH_TAIL',(-24,9.20,-1.32),(.07,.05,.035),finmat,fish)
rot=Vector(fish.rotation_euler);clip(fish,'Z02_FISH_TURN','rotation_euler',[(1,rot),(72,rot+Vector((0,0,math.pi*.7))),(144,rot+Vector((0,0,math.pi)))]);rot=Vector(tail.rotation_euler);clip(tail,'Z02_FISH_TAIL_IDLE','rotation_euler',[(1,rot),(15,rot+Vector((0,0,.18))),(30,rot),(45,rot-Vector((0,0,.18))),(60,rot)],True)
# Ripple geometry remains a separate addressable object; no baked interaction.
ripplemat=material('P3_RIPPLE_LINE',(.31,.48,.42),None,.4)
for i,radius in enumerate([.18,.32,.46]):
 bpy.ops.mesh.primitive_torus_add(major_segments=64,minor_segments=6,location=(-25,9,-1.19),major_radius=radius,minor_radius=.002);o=bpy.context.object;o.name='SM_Z02_TOUCH_RIPPLE_'+str(i)
 for c in list(o.users_collection):c.objects.unlink(o)
 C.objects.link(o);assign(o,ripplemat);clip(o,'Z02_RIPPLE_EXPAND_'+str(i),'scale',[(1,(.05,.05,1)),(72,(2.2,2.2,.25)),(96,(2.8,2.8,.01))]);o.hide_render=True;o['state_variant']='touch feedback; hidden at rest'
stone=sphere('SM_Z02_THROW_STONE',(-24.8,8.8,-1.14),(.05,.065,.045),bpy.data.materials['P3_Weathered_Gray_Rock']);p=stone.location.copy();clip(stone,'Z02_STONE_DROP','location',[(1,p+Vector((0,0,1.4))),(15,p+Vector((0,0,.6))),(22,p),(38,p+Vector((0,0,-.45)))]);stone.hide_render=True
# Bell swing pivots at its actual hanger, not at ground level.
pivot=root('PIVOT_Z07_BELL',(-3,22,2.90))
for o in list(s.objects):
 if o.name.startswith(('SM_Z07_BRONZE_BELL','SM_Z07_BELL_BAND','SM_Z07_OVAL_WORN_STRIKE')):parent_keep(o,pivot)
r=Vector((0,0,0));clip(pivot,'Z07_BELL_LIGHT_TOUCH','rotation_euler',[(1,r),(10,(.012,0,0)),(28,(-.007,0,0)),(50,(.003,0,0)),(96,r)]);clip(pivot,'Z07_BELL_HEAVY_STRIKE','rotation_euler',[(1,r),(12,(.045,0,0)),(40,(-.028,0,0)),(80,(.012,0,0)),(160,r)])
# Wood tags move gently at each top edge; content face stays attached.
tagcount=0
for o in list(s.objects):
 if o.name.startswith('Z08_WISH_TREE_BLANK_TAG') and not o.hide_render:
  pivot=root('PIVOT_Z08_TAG_'+str(tagcount),(o.location.x,o.location.y,o.location.z+.13));parent_keep(o,pivot)
  for face in list(s.objects):
   if face.name.startswith('CONTENT_FACE_Z08_TAG_') and abs(face.location.x-o.location.x)<.015:parent_keep(face,pivot)
  clip(pivot,'Z08_TAG_GENTLE_WIND_'+str(tagcount),'rotation_euler',[(1,(0,0,0)),(73,(.018,.008,0)),(187,(-.01,-.005,0)),(360,(0,0,0))],True);tagcount+=1
# Cup layout alternatives are explicit source variants.
cup=bpy.data.objects['Z01_P2_CERAMIC_CUP'];alternate=cup.copy();alternate.data=cup.data.copy();alternate.name='SM_Z01_CUP_REMOVED_STATE';C.objects.link(alternate);alternate.hide_render=True;alternate.hide_viewport=True;alternate['state_variant']='cup removed uses hide of default cup; this object preserves placement'
bowl=sphere('SM_Z01_BOWL_LAYOUT_VARIANT',(-14.85,8.45,.46),(.09,.09,.035),bpy.data.materials['P2_Paper']);bowl.hide_render=True;bowl.hide_viewport=True;bowl['state_variant']='layout bowl; hollow replacement pending visual check'
# Wet/dry ink swatches are authored samples, hidden with sample paper.
wet=material('P3_WET_INK',(.012,.014,.012),None,.13);dry=material('P3_DRY_INK',(.022,.026,.019),None,.9)
for i,mat in enumerate([wet,dry]):
 o=box('SAMPLE_Z05_'+('WET' if i==0 else 'DRY')+'_INK',(9.04+i*.12,-3,.700),(.075,.006,.0006),C,mat,.001);o.hide_render=True;o.hide_viewport=True;o['sample_only']=True
# Flame/illuminance variants are independent clips. Ambient fills are never bound to these.
flame=material('P3_OIL_FLAME',(.95,.43,.09),None,.5,0,False,4)
fl=sphere('SM_Z05_OIL_FLAME',(9.85,-3,.86),(.008,.008,.022),flame)
clip(fl,'Z05_LAMP_STABLE','scale',[(1,fl.scale.copy()),(240,fl.scale.copy())],True);clip(fl,'Z05_LAMP_FLICKER','scale',[(1,(.008,.008,.022)),(18,(.007,.007,.019)),(41,(.008,.008,.024)),(80,(.008,.008,.022))],True);clip(fl,'Z05_LAMP_EXTINGUISH','scale',[(1,(.008,.008,.022)),(12,(.006,.006,.01)),(24,(0,0,0))]);clip(fl,'Z05_LAMP_RELIGHT','scale',[(1,(0,0,0)),(10,(.006,.006,.014)),(24,(.008,.008,.022))])
# Writing frame: binary-search lens by projected paper polygon area, same fixed model.
paper=[Vector((9.1+dx,-3+dy,.695)) for dx,dy in [(-.275,-.18),(.275,-.18),(.275,.18),(-.275,.18)]];occup=[]
def area(cam):
 q=[world_to_camera_view(s,cam,p) for p in paper];return abs(sum(q[i].x*q[(i+1)%4].y-q[(i+1)%4].x*q[i].y for i in range(4)))/2,q
for name,sign in [('CAM_COPY_RH',1),('CAM_COPY_LH',-1)]:
 cam=bpy.data.objects[name];cam.location=(9.1+sign*.025,-2.98,1.24);cam.rotation_euler=(Vector((9.1,-3,.695))-cam.location).to_track_quat('-Z','Y').to_euler();lo,hi=5,80
 for _ in range(40):
  cam.data.lens=(lo+hi)/2;bpy.context.view_layer.update();a,q=area(cam)
  if a<.65:lo=cam.data.lens
  else:hi=cam.data.lens
 a,q=area(cam);occup.append({'camera':name,'paper_projected_area':a,'corners':[[p.x,p.y] for p in q],'lens_mm':cam.data.lens,'all_corners_inside':all(0<=p.x<=1 and 0<=p.y<=1 for p in q)})
s.frame_set(1);s.render.fps=24;s.frame_start=1;s.frame_end=1441;s['version']='p3-v012';s['phase_status']='P3_OBJECT_STATES_REVIEW_PENDING';bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
(R/'manifest/object-animation-clips.json').write_text(json.dumps({'version':'p3-v012','fps':24,'clips':registry,'preview_rule':'clips inactive by default; application schedules independently; reduced motion disables clips without removing content','scope':'source model clips, not application touch/privacy/device proof'},ensure_ascii=False,indent=2));(R/'evidence/writing-paper-occupancy.json').write_text(json.dumps({'version':'p3-v012','method':'world_to_camera_view projected quadrilateral / complete frame area','target':[.6,.7],'results':occup},indent=2));print('OBJECT_STATES_SAVED',len(registry),tagcount,occup,flush=True)
