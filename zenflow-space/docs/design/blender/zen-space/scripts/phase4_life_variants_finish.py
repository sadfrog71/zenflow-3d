import bpy,math,json,random
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[1];s=bpy.context.scene;out=R/'zen-space-p3-v021.blend'
if s.get('version')!='p3-v020' or out.exists():raise RuntimeError('Requires new v021 from v020')
C=bpy.data.collections.new('STATE_Z01_LIFE_TRACES');s.collection.children.link(C);rng=random.Random(21021);mat=bpy.data.materials['P3_CURVED_SHRUB_LEAF'].copy();mat.name='P4_FALLEN_DRY_LEAF';nt=mat.node_tree;p=next(n for n in nt.nodes if n.type=='BSDF_PRINCIPLED')
for l in list(p.inputs['Base Color'].links):nt.links.remove(l)
p.inputs['Base Color'].default_value=(.23,.14,.045,1);p.inputs['Roughness'].default_value=.94
vs=[];fs=[]
for j in range(7):
 t=j/6;w=max(.02,math.sin(math.pi*t))*.018
 for k in [-1,0,1]:vs.append((k*w,(t-.5)*.07,.003*math.sin(math.pi*t)+.003*k*k))
for j in range(6):
 for k in range(2):a=j*3+k;fs.append((a,a+1,a+4,a+3))
me=bpy.data.meshes.new('Z01_DRY_CURLED_LEAF_SHARED');me.from_pydata(vs,[],fs);me.materials.append(mat)
leaves=[]
for i in range(36):
 o=bpy.data.objects.new('SM_Z01_LIFE_FALLEN_LEAF_'+str(i),me);C.objects.link(o);o.location=(-14.2+rng.uniform(-.42,.42),7.65+rng.uniform(-.8,.8),.004);o.rotation_euler.z=rng.random()*math.tau;o.hide_render=True;o.hide_viewport=True;o['state_variant']='scattered leaves; half-swept state excludes leaves with half_swept_removed=true';o['half_swept_removed']=o.location.y<7.8 and o.location.x>-14.35;leaves.append(o)
o=leaves[0];base=o.location.copy();o.animation_data_create()
for fr,delta in [(1,(0,0,0)),(400,(0,0,0)),(520,(.15,.07,.018)),(760,(.23,.12,0)),(1441,(.23,.12,0))]:o.location=base+Vector(delta);o.keyframe_insert(data_path='location',frame=fr)
a=o.animation_data.action;a.name='Z01_LEAF_WIND_DRIFT_AND_REST';a.use_fake_user=True;slot=o.animation_data.action_slot;t=o.animation_data.nla_tracks.new();st=t.strips.new(a.name,1,a);st.action_slot=slot;t.mute=True;o.animation_data.action=None;o.location=base
reg=json.loads((R/'manifest/object-animation-clips.json').read_text());reg['clips'].append({'name':a.name,'object':o.name,'channel':'location','frames':[1,1441],'seconds':60,'loop':False,'default_playing':False,'state_variant':'optional leaf traces; source hidden at default clean visit'});reg['version']='p3-v021';reg['source_version']='p3-v021';(R/'manifest/object-animation-clips.json').write_text(json.dumps(reg,ensure_ascii=False,indent=2))
w=json.loads((R/'manifest/weather-and-layout-states.json').read_text());w['source_version']='p3-v021';w['life_trace_states']={'collection':C.name,'default':'clean visit, optional source variants hidden','scattered':'show all 36 leaf meshes','half_swept':'show leaf meshes with half_swept_removed=false','runtime_rule':'world time may move/replace traces; no cleaning obligation'};(R/'manifest/weather-and-layout-states.json').write_text(json.dumps(w,ensure_ascii=False,indent=2))
s.frame_set(1);s['version']='p3-v021';s['phase_status']='MODEL_SELF_CHECK_DELIVERY';t=bpy.data.texts.get('READ_ME_FIRST')
if t:t.from_string(t.as_string().replace('p3-v020','p3-v021')+'\nOptional life traces: STATE_Z01_LIFE_TRACES, source hidden at clean-visit default. Half-swept flag on each leaf.\n')
bpy.ops.wm.save_as_mainfile(filepath=str(out),compress=True)
m=json.loads((R/'manifest/scene-manifest.json').read_text());m['version']='p3-v021';m['source_version']='p3-v021';m['source_snapshot']=out.name;(R/'manifest/scene-manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2));(R/'evidence/life-trace-states.json').write_text(json.dumps({'version':'p3-v021','fallen_leaves':36,'half_swept_remaining':sum(not o['half_swept_removed'] for o in leaves),'default_hidden':True,'action':a.name,'visual_provenance':'default geometry identical to v020; new optional hidden state only'},indent=2));print('LIFE_TRACE_VARIANTS_FINISHED',flush=True)
