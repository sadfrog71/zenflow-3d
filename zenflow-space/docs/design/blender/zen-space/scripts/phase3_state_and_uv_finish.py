import bpy,sys,math,json
from pathlib import Path
from mathutils import Vector,Matrix
from bpy_extras.object_utils import world_to_camera_view
R=Path(__file__).resolve().parents[1];sys.path.insert(0,str(R/'scripts'));from phase2_materials import material,beam,assign
s=bpy.context.scene;out=R/'zen-space-p3-v013.blend'
if s.get('version')!='p3-v012' or out.exists():raise RuntimeError('Requires new v013 from v012')
# Fixed page-aligned camera up vector avoids arbitrary roll when looking almost vertically down.
paper=[Vector((9.1+dx,-3+dy,.695)) for dx,dy in [(-.275,-.18),(.275,-.18),(.275,.18),(-.275,.18)]];occup=[]
def area(cam):
 q=[world_to_camera_view(s,cam,p) for p in paper];return abs(sum(q[i].x*q[(i+1)%4].y-q[(i+1)%4].x*q[i].y for i in range(4)))/2,q
for name,sign in [('CAM_COPY_RH',1),('CAM_COPY_LH',-1)]:
 cam=bpy.data.objects[name];cam.location=(9.1+sign*.025,-2.98,1.24);d=(Vector((9.1,-3,.695))-cam.location).normalized();right=d.cross(Vector((0,-1,0))).normalized();up=right.cross(d).normalized();cam.rotation_euler=Matrix((right,up,-d)).transposed().to_euler();lo,hi=5,80
 for _ in range(40):
  cam.data.lens=(lo+hi)/2;bpy.context.view_layer.update();a,q=area(cam)
  if a<.65:lo=cam.data.lens
  else:hi=cam.data.lens
 a,q=area(cam);occup.append({'camera':name,'paper_projected_area':a,'corners':[[p.x,p.y] for p in q],'lens_mm':cam.data.lens,'all_corners_inside':all(0<=p.x<=1 and 0<=p.y<=1 for p in q)})
# Cylindrical tube UV follows ring arc and centreline distance, so forks do not stretch into zigzags.
fixed=[]
for o in s.objects:
 if o.type!='MESH' or o.hide_viewport or not o.name.startswith(('SM_Z01_COURTYARD_TREE','SM_Z08_WISH_TREE','SM_Z09_LANDSCAPE_TREE','SM_Z11_GARDEN_TREE')) or '_CC0_' in o.name:continue
 seg=16 if '_TRUNK' in o.name else 10 if any(k in o.name for k in ['_ROOT','_BRANCH']) else 0
 if not seg or len(o.data.vertices)%seg:continue
 me=o.data;centers=[];radii=[]
 for i in range(0,len(me.vertices),seg):
  vs=[v.co for v in me.vertices[i:i+seg]];c=sum(vs,Vector())/seg;centers.append(c);radii.append(sum((v-c).length for v in vs)/seg)
 distances=[0]
 for a,b in zip(centers,centers[1:]):distances.append(distances[-1]+(b-a).length)
 uv=me.uv_layers.active or me.uv_layers.new()
 for face in me.polygons:
  ids=list(face.vertices);local=[i%seg for i in ids];seam=max(local)-min(local)>seg/2
  for li in face.loop_indices:
   ix=me.loops[li].vertex_index;ring,j=divmod(ix,seg);u=(1 if seam and j==0 else j/seg)*2*math.pi*radii[ring]/1.2;uv.data[li].uv=(u,distances[ring]/1.2)
  face.use_smooth=True
 fixed.append(o.name)
# Hollow bowl variant, same support height as the real stone seat.
o=bpy.data.objects['SM_Z01_BOWL_LAYOUT_VARIANT'];vs=[];fs=[];profile=[(.035,.42),(.082,.435),(.10,.47),(.093,.477),(.072,.449),(.03,.442)]
for rr,z in profile:
 for i in range(48):a=i*math.tau/48;vs.append((-14.85+rr*math.cos(a),8.45+rr*math.sin(a),z))
for j in range(len(profile)-1):
 for i in range(48):a=j*48+i;b=j*48+(i+1)%48;fs.append((a,b,b+48,a+48))
me=bpy.data.meshes.new('Z01_HOLLOW_BOWL_VARIANT');me.from_pydata(vs,[],fs);me.update();o.data=me;o.location=(0,0,0);o.scale=(1,1,1);assign(o,bpy.data.materials['P2_Paper']);o['state_variant']='hollow ceramic bowl replaces cup; default hidden'
for f in me.polygons:f.use_smooth=True
# Authored correction strokes, hidden sample, no purported canonical or private words.
C=bpy.data.collections['P3_INDEPENDENT_OBJECT_STATES'];ink=bpy.data.materials['P3_DRY_INK']
for i in range(5):
 o=beam('SAMPLE_Z05_CORRECTION_STROKE',(9.02+i*.018,-3.02,.701),(9.13+i*.015,-2.98,.701),.0008,C,ink,6);o.hide_render=True;o.hide_viewport=True;o['sample_only']=True;o['state']='CORRECTED'
# Anonymous life shadow behind existing paper screen; no face, identity or online-user claim.
shade=material('P3_ANONYMOUS_DOOR_SHADOW',(.14,.12,.08),None,1)
pivot=bpy.data.objects.new('PIVOT_Z05_DOOR_SHADOW',None);C.objects.link(pivot);pivot.location=(14.5,-12.94,.6)
def ell(n,p,sc):
 bpy.ops.mesh.primitive_uv_sphere_add(segments=24,ring_count=12,radius=1,location=p);o=bpy.context.object;o.name=n
 for c in list(o.users_collection):c.objects.unlink(o)
 C.objects.link(o);o.scale=sc;assign(o,shade);bpy.context.view_layer.update();mw=o.matrix_world.copy();o.parent=pivot;o.matrix_world=mw
 for f in o.data.polygons:f.use_smooth=True
 return o
ell('SM_Z05_DOOR_SHADOW_HEAD',(14.5,-12.94,1.5),(.09,.008,.105));ell('SM_Z05_DOOR_SHADOW_BODY',(14.5,-12.94,1.22),(.16,.008,.23));arm=ell('SM_Z05_DOOR_SHADOW_ARM',(14.68,-12.94,1.2),(.13,.008,.035))
clips=json.loads((R/'manifest/object-animation-clips.json').read_text());
def clip(o,name,channel,keys):
 original=getattr(o,channel).copy();o.animation_data_create();o.animation_data.action=None
 for fr,val in keys:setattr(o,channel,val);o.keyframe_insert(data_path=channel,frame=fr)
 action=o.animation_data.action;action.name=name;action.use_fake_user=True;slot=o.animation_data.action_slot;t=o.animation_data.nla_tracks.new();t.name='CLIP_LIBRARY_'+name;st=t.strips.new(name,1,action);st.action_slot=slot;t.mute=True;o.animation_data.action=None;setattr(o,channel,original);clips['clips'].append({'name':name,'object':o.name,'channel':channel,'frames':[keys[0][0],keys[-1][0]],'seconds':(keys[-1][0]-keys[0][0])/24,'default_playing':False,'loop':False})
r=Vector(arm.rotation_euler);clip(arm,'Z05_SHADOW_SHORT_WRITING','rotation_euler',[(1,r),(30,r+Vector((0,.012,.02))),(66,r-Vector((0,.009,.01))),(96,r)]);r=Vector(pivot.rotation_euler);clip(pivot,'Z05_SHADOW_LONG_PAUSE','rotation_euler',[(1,r),(1441,r)]);clip(pivot,'Z05_SHADOW_POSTURE_SHIFT','rotation_euler',[(1,r),(96,r+Vector((0,.012,0))),(192,r)])
s.frame_set(1);s['version']='p3-v013';s['phase_status']='P3_STATES_AND_CAMERAS_REVIEW_PENDING';bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True);clips['version']='p3-v013';(R/'manifest/object-animation-clips.json').write_text(json.dumps(clips,ensure_ascii=False,indent=2));(R/'evidence/writing-paper-occupancy.json').write_text(json.dumps({'version':'p3-v013','method':'page-aligned projection, complete frame area; corners must stay inside','target':[.6,.7],'results':occup},indent=2));(R/'evidence/bark-uv-repair.json').write_text(json.dumps({'version':'p3-v013','method':'per-ring circumference and centreline-distance UV, seam unwrapped','objects':fixed},indent=2));print('STATE_UV_FINISHED',occup,flush=True)
